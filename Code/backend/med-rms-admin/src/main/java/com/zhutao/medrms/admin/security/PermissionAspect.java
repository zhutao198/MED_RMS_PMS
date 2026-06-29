package com.zhutao.medrms.admin.security;

import com.zhutao.medrms.admin.service.PermissionService;
import com.zhutao.medrms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * @RequiresPermission 切面
 * 渐进式收口：仅对标注的 API 强制鉴权，未标注仍走 permitAll
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionService permissionService;

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint pjp, RequiresPermission requiresPermission) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException("SY0400", "未认证（Token缺失或过期）");
        }
        Long userId = (Long) auth.getPrincipal();
        String permCode = requiresPermission.value();

        if (!permissionService.hasPermission(userId, permCode)) {
            log.warn("Permission denied: userId={} permCode={}", userId, permCode);
            throw new BusinessException("SY0401", "无权限：" + permCode);
        }
        return pjp.proceed();
    }
}
