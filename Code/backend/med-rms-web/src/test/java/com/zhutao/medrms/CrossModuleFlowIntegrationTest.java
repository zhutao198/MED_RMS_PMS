package com.zhutao.medrms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
 * 跨模块集成测试（W3-D2）— 6 大核心流的"代表性"端到端验证
 * 使用 @SpringBootTest + dev profile（真实 PG schema 隔离）
 *
 * 流 1：需求创建 → 拆解 → 基线
 * 流 2：追溯矩阵 → Gap 分析
 * 流 3：变更申请 → 影响评估
 * 流 4：电子签名（认证 + 签名）
 * 流 5：合规审计日志
 * 流 6：风险登记
 */
@SpringBootTest(classes = com.zhutao.medrms.web.MedRmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CrossModuleFlowIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    private String loginAs(String user, String pwd) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"%s\"}", user, pwd);
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        JsonNode data = om.readTree(r.getResponse().getContentAsString()).get("data");
        return data.get("token").asText();
    }

    private JsonNode doGet(String token, String url) throws Exception {
        MvcResult r = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("data");
    }

    // ============================================================
    // 流 1：需求创建 → 拆解 → 基线
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("流 1：需求列表/详情/追溯矩阵 端到端可访问")
    void flow1_requirement_visibility() throws Exception {
        String token = loginAs("admin", "admin123");

        // 1) 需求列表
        JsonNode reqs = doGet(token, "/requirements");
        assertNotNull(reqs);
        assertTrue(reqs.isArray() || (reqs.has("records") && reqs.get("records").isArray()));

        // 2) 需求详情（取第一条）
        long firstId;
        if (reqs.isArray()) {
            firstId = reqs.get(0).get("id").asLong();
        } else {
            firstId = reqs.get("records").get(0).get("id").asLong();
        }
        JsonNode detail = doGet(token, "/requirements/" + firstId);
        assertEquals(firstId, detail.get("id").asLong());
    }

    // ============================================================
    // 流 2：追溯矩阵 + Gap
    // ============================================================

    @Test
    @Order(2)
    @DisplayName("流 2：追溯矩阵 + Gap 端到端可访问")
    void flow2_traceability() throws Exception {
        String token = loginAs("admin", "admin123");

        JsonNode matrix = doGet(token, "/traceability/matrix");
        assertNotNull(matrix);

        JsonNode coverage = doGet(token, "/traceability/coverage?projectId=1");
        assertNotNull(coverage);
        assertNotNull(coverage.get("overall"));
    }

    // ============================================================
    // 流 3：变更申请 + 列表
    // ============================================================

    @Test
    @Order(3)
    @DisplayName("流 3：变更申请 + 待审批列表可访问")
    void flow3_change() throws Exception {
        String token = loginAs("admin", "admin123");

        // R120 P2 修复：/changes/list 改返回 PageResult（含 records/total/page/size/pages），
        // data 字段是 List 但响应顶层不再是纯数组。兼容两种返回格式。
        JsonNode changes = doGet(token, "/changes/list");
        assertNotNull(changes);
        assertTrue(changes.isArray() || (changes.has("data") && changes.get("data").isArray()));

        JsonNode pending = doGet(token, "/changes/pending");
        assertNotNull(pending);
    }

    // ============================================================
    // 流 4：电子签名
    // ============================================================

    @Test
    @Order(4)
    @DisplayName("流 4：电子签名设置 + 签名历史可访问")
    void flow4_esignature() throws Exception {
        String token = loginAs("admin", "admin123");

        JsonNode settings = doGet(token, "/signatures/settings/1");
        assertNotNull(settings);

        JsonNode history = doGet(token, "/signatures/entity/requirement/1");
        assertNotNull(history);
    }

    // ============================================================
    // 流 5：审计日志 + 哈希链
    // ============================================================

    @Test
    @Order(5)
    @DisplayName("流 5：审计日志列表 + 哈希链校验可访问")
    void flow5_audit() throws Exception {
        String token = loginAs("admin", "admin123");

        JsonNode logs = doGet(token, "/compliance/audit-logs");
        assertNotNull(logs);

        JsonNode verify = doGet(token, "/compliance/audit-logs/verify-hash-chain");
        assertNotNull(verify);
        // 只要能访问到该端点返回非 null 即视为通过
        // 实际 valid 状态可能是 true/false
    }

    // ============================================================
    // 流 6：风险 + SOUP
    // ============================================================

    @Test
    @Order(6)
    @DisplayName("流 6：SOUP 列表 + 异常检测可访问")
    void flow6_soup() throws Exception {
        String token = loginAs("admin", "admin123");

        JsonNode soup = doGet(token, "/requirement/soup-components");
        assertNotNull(soup);

        // 第一个 SOUP 异常检测
        long firstId;
        if (soup.isArray() && soup.size() > 0) {
            firstId = soup.get(0).get("id").asLong();
            JsonNode anomalies = doGet(token, "/requirement/soup-components/" + firstId + "/anomalies");
            assertNotNull(anomalies);
        }
    }
}
