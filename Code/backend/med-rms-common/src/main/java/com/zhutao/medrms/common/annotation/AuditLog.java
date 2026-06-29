package com.zhutao.medrms.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解
 * 标注在 controller/service 方法上，AuditAspect 会自动写入审计日志（含 SHA-256 哈希链）
 *
 * 用法：
 *   @AuditLog(eventType = "CREATE", entityType = "REQUIREMENT")
 *   public Result<Requirement> create(@RequestBody Requirement req) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 事件类型：CREATE / MODIFY / DELETE / STATUS_CHANGE / SIGN / LOGIN / REVIEW / APPROVE / EXECUTE */
    String eventType();

    /** 实体类型：REQUIREMENT / CHANGES / RISK / BASELINE / USER / ROLE / DICT / DHF / PROBLEM_REPORT ... */
    String entityType();

    /**
     * 实体 ID 解析 SpEL 表达式
     *  - 默认从方法返回值取 id（要求返回类型有 getId() 方法）
     *  - 也可指定形参名："#id" 从 @PathVariable id 取
     *  - 留空时使用 defaultExpressionFromReturnValue
     */
    String entityIdSpel() default "";

    /** 操作描述（人类可读），可包含 {method} 等占位符 */
    String operation() default "";

    /** SpEL 表达式用于 beforeValue：方法执行前捕获变更前值（异步场景预留给 controller 层处理） */
    String beforeSpel() default "";

    /** SpEL 表达式用于 afterValue：方法执行后捕获返回值（要求返回 Result<T>） */
    String afterSpel() default "";

    /** 是否捕获完整方法入参作为 afterValue（默认 true） */
    boolean captureArgs() default true;
}
