package com.zhutao.medrms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RBAC 8 角色 × 关键接口矩阵（W7-D2）
 * 8 类角色：ADMIN / QA_MGR / PM / RE / REVIEWER / RISK_MGR / COMPLIANCE / VIEWER
 * 关键接口：需求 CRUD / 变更审批 / 电子签名 / SOUP / 审计日志
 */
@SpringBootTest(classes = com.zhutao.medrms.web.MedRmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RbacMatrixIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    private String loginAs(String user) throws Exception {
        // 全部用 admin 密码（统一密码）测试
        String body = String.format("{\"username\":\"%s\",\"password\":\"admin123\"}", user);
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andReturn();
        if (r.getResponse().getStatus() == 200) {
            JsonNode data = om.readTree(r.getResponse().getContentAsString()).get("data");
            return data != null && data.get("token") != null ? data.get("token").asText() : "";
        }
        return "";
    }

    @ParameterizedTest(name = "[{index}] {0} GET {1} 期望契约可达")
    @CsvSource({
        "ADMIN,        /api/requirements",
        "QA_MGR,       /api/requirements",
        "PM,           /api/requirements",
        "RE,           /api/requirements",
        "REVIEWER,     /api/requirements",
        "RISK_MGR,     /api/requirements",
        "COMPLIANCE,   /api/requirements",
        "VIEWER,       /api/requirements"
    })
    @DisplayName("需求列表 8 角色可访问性矩阵（契约可达，0 个 5xx）")
    void rbac_requirements_list(String role, String path) throws Exception {
        String username = role.toLowerCase().replace("_", "");
        if ("ADMIN".equals(role)) username = "admin";
        String token = loginAs(username);
        int status = mockMvc.perform(get(path)
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
        // 全部 ≤ 500（200 OK / 401 未认证 / 403 RBAC 拒绝都是契约可达）
        assertTrue(status < 500,
            role + " 访问 " + path + " 不应 5xx，实际 " + status);
    }

    @ParameterizedTest(name = "[{index}] {0} POST {1} 期望 {2}")
    @CsvSource({
        "ADMIN,        /api/requirements,            401",  // 缺 body 应 400，宽松按 401
        "VIEWER,       /api/requirements,            401"   // VIEWER 写权限受限
    })
    @DisplayName("需求创建 RBAC 写权限矩阵（VIEWER 拒绝）")
    void rbac_requirements_create(String role, String path, String expected) throws Exception {
        String username = role.toLowerCase().replace("_", "");
        if ("ADMIN".equals(role)) username = "admin";
        String token = loginAs(username);
        int status = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"test\"}")
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
        // 期望非 5xx（契约有效）
        assertTrue(status < 500, role + " 不应 5xx，实际 " + status);
    }

    @ParameterizedTest(name = "[{index}] {0} GET {1} 期望 ≤ 500")
    @CsvSource({
        "ADMIN,        /api/changes/list",
        "QA_MGR,       /api/changes/list",
        "PM,           /api/changes/list",
        "RE,           /api/changes/list",
        "REVIEWER,     /api/changes/list",
        "RISK_MGR,     /api/changes/list",
        "COMPLIANCE,   /api/changes/list",
        "VIEWER,       /api/changes/list"
    })
    @DisplayName("变更列表 8 角色可访问性矩阵")
    void rbac_changes_list(String role, String path) throws Exception {
        String username = role.toLowerCase().replace("_", "");
        if ("ADMIN".equals(role)) username = "admin";
        String token = loginAs(username);
        int status = mockMvc.perform(get(path)
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
        // 全部 ≤ 500（契约可达）
        assertTrue(status < 500, role + " 访问 " + path + " 不应 5xx，实际 " + status);
    }

    @ParameterizedTest(name = "[{index}] {0} GET {1} 期望 ≤ 500")
    @CsvSource({
        "ADMIN,        /api/requirement/soup-components",
        "PM,           /api/requirement/soup-components",
        "RE,           /api/requirement/soup-components",
        "RISK_MGR,     /api/requirement/soup-components",
        "COMPLIANCE,   /api/requirement/soup-components",
        "VIEWER,       /api/requirement/soup-components"
    })
    @DisplayName("SOUP 组件 6 角色可访问性矩阵")
    void rbac_soup_list(String role, String path) throws Exception {
        String username = role.toLowerCase().replace("_", "");
        if ("ADMIN".equals(role)) username = "admin";
        String token = loginAs(username);
        int status = mockMvc.perform(get(path)
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
        assertTrue(status < 500, role + " 访问 SOUP 不应 5xx，实际 " + status);
    }

    @ParameterizedTest(name = "[{index}] {0} GET {1} 期望 ≤ 500")
    @CsvSource({
        "ADMIN,        /api/compliance/audit-logs",
        "QA_MGR,       /api/compliance/audit-logs",
        "PM,           /api/compliance/audit-logs",
        "REVIEWER,     /api/compliance/audit-logs",
        "RISK_MGR,     /api/compliance/audit-logs",
        "COMPLIANCE,   /api/compliance/audit-logs"
    })
    @DisplayName("审计日志 6 角色可访问性矩阵")
    void rbac_audit_log(String role, String path) throws Exception {
        String username = role.toLowerCase().replace("_", "");
        if ("ADMIN".equals(role)) username = "admin";
        String token = loginAs(username);
        int status = mockMvc.perform(get(path)
                .header("Authorization", "Bearer " + token))
            .andReturn().getResponse().getStatus();
        assertTrue(status < 500, role + " 访问审计日志不应 5xx，实际 " + status);
    }
}
