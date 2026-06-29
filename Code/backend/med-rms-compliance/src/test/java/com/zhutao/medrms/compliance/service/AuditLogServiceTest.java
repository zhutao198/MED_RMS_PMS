package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditLogServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    private List<AuditLog> createTestHashChain(int count) {
        List<AuditLog> logs = new ArrayList<>();
        String prevHash = "0".repeat(64);

        for (int i = 1; i <= count; i++) {
            AuditLog log = new AuditLog();
            log.setId((long) i);
            log.setPrevHash(prevHash);
            log.setCurrentHash(String.format("%064d", i));
            log.setEventType("CREATE");
            log.setEntityType("Requirement");
            log.setEntityId((long) i);
            log.setOperatorId(1L);
            log.setOperatorName("测试用户");
            log.setOperation("创建需求");
            log.setCreatedAt(LocalDateTime.now());
            prevHash = log.getCurrentHash();
            logs.add(log);
        }
        return logs;
    }

    @Test
    void testVerifyHashChainEmpty() {
        when(auditLogMapper.selectList(any())).thenReturn(List.of());

        boolean result = auditLogService.verifyHashChain();

        assertTrue(result);
    }

    @Test
    void testGetAuditLogsForEntity() {
        AuditLog log = new AuditLog();
        log.setEntityType("Requirement");
        log.setEntityId(100L);
        when(auditLogMapper.selectByEntity("Requirement", 100L)).thenReturn(List.of(log));

        List<AuditLog> result = auditLogService.getAuditLogsForEntity("Requirement", 100L);

        assertEquals(1, result.size());
        assertEquals("Requirement", result.get(0).getEntityType());
    }

    @Test
    void testGetAuditLogsByOperator() {
        AuditLog log = new AuditLog();
        log.setOperatorId(1L);
        when(auditLogMapper.selectByOperator(1L)).thenReturn(List.of(log));

        List<AuditLog> result = auditLogService.getAuditLogsByOperator(1L);

        assertEquals(1, result.size());
    }

    @Test
    void testGetLogsForExportWithTimeRange() {
        LocalDateTime startTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 28, 23, 59);
        when(auditLogMapper.selectByTimeRange(startTime, endTime)).thenReturn(List.of());

        List<AuditLog> result = auditLogService.getLogsForExport(startTime, endTime, null);

        assertNotNull(result);
    }

    @Test
    void testGenerateCsvEmpty() {
        List<AuditLog> logs = List.of();
        String csv = auditLogService.generateCsv(logs);

        assertTrue(csv.contains("ID,EventType,EntityType"));
    }

    @Test
    void testGenerateCsvWithData() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setEventType("CREATE");
        log.setEntityType("Requirement");
        log.setEntityId(100L);
        log.setOperatorName("测试用户");
        log.setOperation("创建需求");
        log.setReason("测试原因");
        log.setCreatedAt(LocalDateTime.of(2026, 5, 28, 10, 30, 0));

        String csv = auditLogService.generateCsv(List.of(log));

        assertTrue(csv.contains("CREATE"));
        assertTrue(csv.contains("Requirement"));
        assertTrue(csv.contains("测试用户"));
    }

    @Test
    void testRecordAuditLog() {
        when(auditLogMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            AuditLog log = invocation.getArgument(0);
            log.setId(1L);
            return 1;
        }).when(auditLogMapper).insert(any(AuditLog.class));

        AuditLog result = auditLogService.recordAuditLog(
                "CREATE",
                "Requirement",
                100L,
                1L,
                "测试用户",
                "创建需求",
                null,
                null,
                "测试原因",
                "127.0.0.1"
        );

        assertNotNull(result);
        assertEquals("CREATE", result.getEventType());
        assertEquals("0".repeat(64), result.getPrevHash());
        assertNotNull(result.getCurrentHash());
    }

    @Test
    void testListAuditLogsPagination() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        when(auditLogMapper.selectList(any())).thenReturn(List.of(log));

        List<AuditLog> result = auditLogService.listAuditLogs(
                "CREATE", "Requirement", 100L, 1L,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                0, 100
        );

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}