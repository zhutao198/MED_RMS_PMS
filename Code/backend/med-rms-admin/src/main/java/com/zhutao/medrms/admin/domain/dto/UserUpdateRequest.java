package com.zhutao.medrms.admin.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户请求 DTO（P0-5 修复）。
 *
 * <p>与 {@link com.zhutao.medrms.admin.domain.entity.User} 实体相比，
 * 本 DTO 显式排除以下敏感字段以避免越权修改：
 * <ul>
 *     <li>{@code passwordHash} — 密码只能通过 {@code /users/{id}/change-password} 走 BCrypt 修改</li>
 *     <li>{@code role} — 角色变更须走专用授权端点（待实现 assign-role）</li>
 *     <li>{@code status} — 用户停/启用须走专用端点（避免 admin 改密时无意解锁用户）</li>
 *     <li>{@code username} — 用户名一旦创建不可修改</li>
 *     <li>{@code isDeleted} — 软删除由专用 deleteUser 处理</li>
 *     <li>{@code createdBy} / {@code createdAt} / {@code updatedBy} / {@code updatedAt} — 系统字段</li>
 *     <li>{@code signaturePasswordHash} — 签名密码仅供签名模块调用</li>
 * </ul>
 *
 * @author CodeBuddy P0-5 修复
 */
@Data
public class UserUpdateRequest {

    @Size(max = 64, message = "姓名长度不能超过 64")
    private String realName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128")
    private String email;

    @Size(max = 32, message = "手机号长度不能超过 32")
    private String phone;

    @Size(max = 64, message = "部门长度不能超过 64")
    private String department;

    /** R99 组织架构-部门外键 */
    private Long deptId;
}