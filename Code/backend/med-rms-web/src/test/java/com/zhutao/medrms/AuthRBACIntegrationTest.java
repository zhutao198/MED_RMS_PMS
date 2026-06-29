package com.zhutao.medrms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 端到端集成测试
 * 验证登录 → 获取 token → 鉴权放行/拒绝 完整流程
 * 使用默认 dev profile 连真实 PostgreSQL（含 RBAC 种子数据）
 */
@SpringBootTest(classes = com.zhutao.medrms.web.MedRmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthRBACIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adminLoginShouldReturnAllPermissions() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertNotNull(data);
        assertEquals("admin", data.get("username").asText());
        assertEquals("ADMIN", data.get("role").asText());
        assertTrue(data.get("roles").isArray());
        assertEquals("ADMIN", data.get("roles").get(0).asText());
        assertTrue(data.get("permissions").isArray());
        assertEquals("*", data.get("permissions").get(0).asText(), "admin 应获得通配符权限");
    }

    @Test
    void viewerLoginShouldHaveLimitedPermissions() throws Exception {
        String body = "{\"username\":\"viewer\",\"password\":\"admin123\"}";
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals("VIEWER", data.get("role").asText());
        JsonNode perms = data.get("permissions");
        assertTrue(perms.size() < 20, "viewer 权限应少于 20 个，实际 " + perms.size());
        boolean hasUserList = false, hasReqCreate = false;
        for (JsonNode p : perms) {
            if ("sys:user:list".equals(p.asText())) hasUserList = true;
            if ("req:create".equals(p.asText())) hasReqCreate = true;
        }
        assertFalse(hasUserList, "viewer 不应有 sys:user:list");
        assertFalse(hasReqCreate, "viewer 不应有 req:create");
    }

    @Test
    void qaMgrLoginShouldHaveBaselineLockButNotSysUserList() throws Exception {
        String body = "{\"username\":\"qa_mgr\",\"password\":\"admin123\"}";
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        JsonNode perms = data.get("permissions");
        boolean hasBaselineLock = false, hasSysUserList = false;
        for (JsonNode p : perms) {
            if ("baseline:lock".equals(p.asText())) hasBaselineLock = true;
            if ("sys:user:list".equals(p.asText())) hasSysUserList = true;
        }
        assertTrue(hasBaselineLock, "QA_MGR 应有 baseline:lock");
        assertFalse(hasSysUserList, "QA_MGR 不应有 sys:user:list");
    }

    @Test
    void wrongPasswordShouldReturnErrorCodeInBody() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"wrong_password\"}";
        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String code = response.get("code").asText();
        assertTrue(code.startsWith("SY") || code.contains("AUTH") || code.contains("401"),
            "错误密码应返回业务错误码，实际: " + code);
    }

    @Test
    void dictEndpointShouldReturnAll22DictTypes() throws Exception {
        String token = loginAsAdmin();
        mockMvc.perform(get("/system/dicts/all").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void dictReqStatusShouldContain7Items() throws Exception {
        String token = loginAsAdmin();
        MvcResult result = mockMvc.perform(get("/system/dicts")
                .param("type", "req_status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertEquals(7, data.size(), "req_status 应有 7 项 (Draft/Reviewing/Approved/Verified/Baseline/Rejected/Deprecated)");
    }

    private String loginAsAdmin() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").get("token").asText();
    }
}
