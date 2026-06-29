package com.zhutao.medrms.admin.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限码校验注解（按钮/API 级别）
 * 用法：@RequiresPermission("baseline:lock")
 * 校验当前登录用户是否拥有该权限码
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {
    String value();
}
