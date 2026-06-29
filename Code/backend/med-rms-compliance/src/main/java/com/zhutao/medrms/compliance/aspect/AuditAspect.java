package com.zhutao.medrms.compliance.aspect;

import com.zhutao.medrms.common.annotation.AuditLog;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.compliance.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * @AuditLog 切面
 * - 在 controller 方法执行后自动写入审计日志（哈希链已由 AuditLogService 保证）
 * - 异常吞掉：审计失败不应阻塞业务
 * - 同步写：保证可观察性；生产高并发可改为 @Async 或消息队列
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    @Pointcut("@annotation(com.zhutao.medrms.common.annotation.AuditLog)")
    public void auditLogPointcut() {}

    private final AuditLogService auditLogService;
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Around("auditLogPointcut() && @annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        Object result = pjp.proceed();
        try {
            recordAuditLog(pjp, auditLog, result);
            log.debug("审计日志写入: method={}, eventType={}, entityType={}",
                    pjp.getSignature().getName(), auditLog.eventType(), auditLog.entityType());
        } catch (Exception e) {
            log.warn("审计日志写入失败（非阻塞）: method={}, err={}",
                    pjp.getSignature().getName(), e.getMessage());
        }
        return result;
    }

    private void recordAuditLog(ProceedingJoinPoint pjp, AuditLog auditLog, Object result) {
        Long operatorId = currentUserId();
        String operatorName = currentUserName();
        String ip = currentIp();

        Long entityId = resolveEntityId(pjp, auditLog, result);
        Object newValue = resolveNewValue(pjp, auditLog, result);
        String operation = buildOperation(auditLog.operation(), pjp, result);

        // 同步写入（测试可见；生产可改为 @Async 或队列）
        auditLogService.recordAuditLog(
                auditLog.eventType(),
                auditLog.entityType(),
                entityId,
                operatorId,
                operatorName,
                operation,
                null,
                newValue,
                null,
                ip
        );
    }

    private Long resolveEntityId(ProceedingJoinPoint pjp, AuditLog auditLog, Object result) {
        String spel = auditLog.entityIdSpel();
        if (spel != null && !spel.isBlank()) {
            return evalLong(spel, pjp, result);
        }
        // 默认从 Result.data.id 取
        if (result instanceof Result<?> r && r.getData() != null) {
            Object data = r.getData();
            try {
                Method m = data.getClass().getMethod("getId");
                Object id = m.invoke(data);
                if (id instanceof Number n) return n.longValue();
            } catch (Exception ignored) {
                // 没有 getId 方法，忽略
            }
        }
        return null;
    }

    private Object resolveNewValue(ProceedingJoinPoint pjp, AuditLog auditLog, Object result) {
        if (!auditLog.captureArgs()) return null;
        if (result instanceof Result<?> r) {
            return r.getData();
        }
        // 回退到方法入参
        return pjp.getArgs();
    }

    private String buildOperation(String op, ProceedingJoinPoint pjp, Object result) {
        if (op == null || op.isBlank()) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            return sig.getName();
        }
        return op;
    }

    private Long evalLong(String spel, ProceedingJoinPoint pjp, Object result) {
        try {
            Expression exp = spelParser.parseExpression(spel);
            EvaluationContext ctx = buildContext(pjp, result);
            Object v = exp.getValue(ctx);
            if (v instanceof Number n) return n.longValue();
        } catch (Exception e) {
            log.debug("SpEL 解析失败: {} -> {}", spel, e.getMessage());
        }
        return null;
    }

    private EvaluationContext buildContext(ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < names.length; i++) {
            ctx.setVariable(names[i], args[i]);
        }
        ctx.setVariable("result", result);
        if (result instanceof Result<?> r) {
            ctx.setVariable("data", r.getData());
        }
        return ctx;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof Number n) return n.longValue();
        if (p instanceof String s) {
            try { return Long.parseLong(s); } catch (Exception e) {
                // v1.49 P2 修复：空 catch 改为 log.debug 便于诊断
                log.debug("解析 principal 失败: {}", e.getMessage());
            }
        }
        return null;
    }

    private String currentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return auth.getName();
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                String xff = req.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
                return req.getRemoteAddr();
            }
        } catch (Exception e) {
            // v1.49 P2 修复：空 catch 改为 log.debug 便于诊断
            log.debug("解析客户端 IP 失败: {}", e.getMessage());
        }
        return null;
    }
}
