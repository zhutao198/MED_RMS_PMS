package com.zhutao.medrms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v1.27 跨模块集成测试
 *
 * 覆盖两条链路：
 * 1. 需求→追溯矩阵（创建需求后追溯矩阵能查到该需求）
 * 2. 审计日志服务→读取链路（写入后能读到）
 *
 * v1.29 新增链路 4：业务模块 @AuditLog 切面自动写审计日志
 */
@SpringBootTest(classes = com.zhutao.medrms.web.MedRmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RequirementAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditLogMapper auditLogMapper;

    private final ObjectMapper om = new ObjectMapper();

    private String loginAs(String user) throws Exception {
        String body = String.format("{\"username\":\"%s\",\"password\":\"admin123\"}", user);
        MvcResult r = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("data").get("token").asText();
    }

    /**
     * 链路 1：创建需求 → 立刻查列表能查到 → 追溯矩阵端点不报错
     */
    @Test
    void createRequirement_appearsInList_andMatrixDoesNotError() throws Exception {
        String token = loginAs("admin");

        String uniqueTitle = "INTEG-TEST-" + System.currentTimeMillis();
        String createBody = String.format(
            "{\"title\":\"%s\",\"projectId\":1,\"requirementType\":\"URS\",\"priority\":\"HIGH\"}", uniqueTitle);
        MvcResult createResult = mockMvc.perform(post("/requirements")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode created = om.readTree(createResult.getResponse().getContentAsString()).get("data");
        long newReqId = created.get("id").asLong();
        assertTrue(newReqId > 0, "创建后应返回新需求 ID");

        // 1. 列表中应能查到
        MvcResult listResult = mockMvc.perform(get("/requirements?projectId=1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode listData = om.readTree(listResult.getResponse().getContentAsString()).get("data");
        assertNotNull(listData);
        boolean found = false;
        for (JsonNode r : listData.get("records")) {
            if (r.get("id").asLong() == newReqId) {
                found = true;
                assertEquals(uniqueTitle, r.get("title").asText());
                break;
            }
        }
        assertTrue(found, "新建的需求应在列表中出现");

        // 2. 追溯矩阵端点不报错（即使无追溯关系也应 200）
        MvcResult traceResult = mockMvc.perform(get("/traceability/matrix")
                .param("requirementId", String.valueOf(newReqId))
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        assertNotNull(traceResult.getResponse().getContentAsString());
    }

    /**
     * 链路 2：admin 查审计日志端点能访问
     */
    @Test
    void admin_canListAuditLogs() throws Exception {
        String token = loginAs("admin");
        MvcResult r = mockMvc.perform(get("/compliance/audit-logs")
                .param("page", "0")
                .param("size", "10")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        // 200 即视为通过；data 可能为 null（无日志）也可能为 List
        assertNotNull(r.getResponse().getContentAsString());
    }

    /**
     * 链路 3：admin 创建用户 → 列表能查到（user + admin 跨模块）
     */
    @Test
    void createUser_appearsInUserList() throws Exception {
        String token = loginAs("admin");
        String username = "integ_test_" + System.currentTimeMillis();
        String body = String.format(
            "{\"username\":\"%s\",\"password\":\"Test123!\",\"email\":\"t@e.com\",\"realName\":\"集成测试\",\"status\":\"ACTIVE\"}",
            username);
        MvcResult cr = mockMvc.perform(post("/system/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode created = om.readTree(cr.getResponse().getContentAsString()).get("data");
        long newId = created.get("id").asLong();
        assertTrue(newId > 0);

        // 查列表
        MvcResult lr = mockMvc.perform(get("/system/users")
                .param("username", username)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode listData = om.readTree(lr.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode u : listData) {
            if (u.get("id").asLong() == newId) {
                found = true;
                break;
            }
        }
        assertTrue(found, "新建用户应出现在用户列表中");
    }

    /**
     * 链路 4（v1.29）：@AuditLog 切面自动写审计日志
     * 创建需求 → 等异步写完成 → 审计日志表能查到该需求
     */
    @Test
    void createRequirement_writesAuditLog_viaAspect() throws Exception {
        String token = loginAs("admin");
        String uniqueTitle = "AUDIT-AOP-" + System.currentTimeMillis();

        // 基线：当前审计日志数
        long before = auditLogMapper.selectList(null).size();

        String createBody = String.format(
            "{\"title\":\"%s\",\"projectId\":1,\"requirementType\":\"URS\",\"priority\":\"HIGH\"}", uniqueTitle);
        MvcResult createResult = mockMvc.perform(post("/requirements")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode created = om.readTree(createResult.getResponse().getContentAsString()).get("data");
        long newReqId = created.get("id").asLong();
        assertTrue(newReqId > 0);

        // 等待异步切面写入（默认 ForkJoinPool，5s 内必完成）
        List<AuditLog> logs = null;
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            logs = auditLogMapper.selectByEntity("REQUIREMENT", newReqId);
            if (logs != null && !logs.isEmpty()) break;
        }
        assertNotNull(logs);
        assertFalse(logs.isEmpty(), "应至少有一条该需求的审计日志");

        AuditLog log = logs.get(0);
        assertEquals("CREATE", log.getEventType());
        assertEquals("REQUIREMENT", log.getEntityType());
        assertEquals(newReqId, log.getEntityId());
        assertEquals(Long.valueOf(1L), log.getOperatorId(), "operatorId 应为 admin 用户 (id=1)");
        assertNotNull(log.getCurrentHash(), "哈希链 currentHash 应写入");
        assertEquals(64, log.getCurrentHash().length(), "SHA-256 哈希长度 64");

        long after = auditLogMapper.selectList(null).size();
        assertTrue(after > before, "审计日志总数应增加 (before=" + before + " after=" + after + ")");
    }
}
