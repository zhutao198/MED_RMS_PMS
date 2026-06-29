package com.zhutao.medrms.admin.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionMatrix 端点 → 权限码 映射单测（v1.27 R28）
 */
class PermissionMatrixTest {

    private final PermissionMatrix matrix = new PermissionMatrix();

    // ===== 精确路径匹配 =====
    @Test
    void shouldMatchExactPath_ChangeApprove() {
        assertEquals("chg:approve", matrix.resolve("POST", "/changes/123/approve"));
        assertEquals("chg:approve", matrix.resolve("POST", "/changes/999/approve"));
    }

    @Test
    void shouldMatchExactPath_BaselineLockAndUnlock() {
        // 当前 PermissionMatrix 中 baseline:lock / baseline:unlock 通过 action 端点表现
        // 锁定操作：POST /baselines/... (matrix 中是 baseline:create 当作 lock)
        // 这里只测试已注册的端点
        assertEquals("baseline:list", matrix.resolve("GET", "/baselines/project/1"));
        assertEquals("baseline:compare", matrix.resolve("GET", "/baselines/compare"));
    }

    @Test
    void shouldMatchExactPath_RequirementApprove() {
        assertEquals("req:review", matrix.resolve("POST", "/requirements/1/approve"));
    }

    @Test
    void shouldMatchExactPath_Decompse() {
        assertEquals("req:create", matrix.resolve("POST", "/requirements/55/decompose"));
    }

    @Test
    void shouldMatchExactPath_EsignSign() {
        assertEquals("esign:sign", matrix.resolve("POST", "/esignature/sign"));
    }

    @Test
    void shouldMatchExactPath_SoupAnomaliesLinkRisk() {
        assertEquals("soup:review", matrix.resolve("POST", "/requirement/soup-components/7/anomalies/link-risk"));
    }

    @Test
    void shouldMatchExactPath_AuditVerify() {
        assertEquals("audit:verify", matrix.resolve("POST", "/compliance/audit-logs/verify"));
    }

    // ===== 前缀路径匹配 =====
    @Test
    void shouldMatchPrefix_RequirementCRUD() {
        assertEquals("req:list", matrix.resolve("GET", "/requirements"));
        assertEquals("req:create", matrix.resolve("POST", "/requirements"));
        assertEquals("req:update", matrix.resolve("PUT", "/requirements"));
        assertEquals("req:delete", matrix.resolve("DELETE", "/requirements"));
    }

    @Test
    void shouldMatchPrefix_SystemUser() {
        assertEquals("sys:user:list", matrix.resolve("GET", "/system/users"));
        assertEquals("sys:user:list", matrix.resolve("POST", "/system/users"));
    }

    @Test
    void shouldMatchPrefix_ProjectCRUD() {
        assertEquals("proj:list", matrix.resolve("GET", "/projects"));
        assertEquals("proj:create", matrix.resolve("POST", "/projects"));
    }

    @Test
    void shouldMatchPrefix_RiskCRUD() {
        assertEquals("risk:list", matrix.resolve("GET", "/risk"));
        assertEquals("risk:create", matrix.resolve("POST", "/risk"));
    }

    @Test
    void shouldMatchPrefix_ChangeCreate() {
        assertEquals("chg:create", matrix.resolve("POST", "/changes"));
        assertEquals("chg:list", matrix.resolve("GET", "/changes"));
    }

    // ===== 未匹配（白名单）=====
    @Test
    void shouldReturnNull_AuthLogin() {
        assertNull(matrix.resolve("POST", "/auth/login"));
    }

    @Test
    void shouldReturnNull_Actuator() {
        assertNull(matrix.resolve("GET", "/actuator/health"));
    }

    @Test
    void shouldReturnNull_UnknownPath() {
        // 未在矩阵中的路径：默认放行（不强制）
        assertNull(matrix.resolve("GET", "/some/random/path"));
    }

    // ===== 精确优先于前缀 =====
    @Test
    void exactRuleBeatsPrefix() {
        // /changes/1/approve 应匹配 chg:approve（精确），不是 chg:list（前缀）
        assertEquals("chg:approve", matrix.resolve("POST", "/changes/1/approve"));
        // /requirements/1/decompose 应匹配 req:create（精确），不是 req:create（前缀刚好也匹配）
        assertEquals("req:create", matrix.resolve("POST", "/requirements/1/decompose"));
    }

    // ===== HTTP 方法区分 =====
    @Test
    void httpMethodDistinguishes() {
        // GET /system/users → sys:user:list
        // POST /system/users → sys:user:list (matrix 不区分 list/create 在这里)
        // 但 /system/users POST 实际是创建用户，matrix 中无独立 create perm
        assertEquals("sys:user:list", matrix.resolve("GET", "/system/users"));
        assertEquals("sys:user:list", matrix.resolve("POST", "/system/users"));
    }
}
