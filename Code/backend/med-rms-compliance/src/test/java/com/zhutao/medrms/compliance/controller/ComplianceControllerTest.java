package com.zhutao.medrms.compliance.controller;

import com.zhutao.medrms.compliance.domain.entity.AuditLog;
import com.zhutao.medrms.compliance.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceControllerTest {

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ComplianceController complianceController;

    @Test
    void testListAuditLogs() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setEventType("CREATE");
        log.setEntityType("Requirement");
        log.setEntityId(100L);
        when(auditLogService.listAuditLogs(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(log));

        var result = complianceController.listAuditLogs("CREATE", "Requirement", 100L, 1L,
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 0, 100);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void testVerifyHashChain() {
        when(auditLogService.verifyHashChain()).thenReturn(true);

        var result = complianceController.verifyHashChain();

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(true, result.getData());
    }

    @Test
    void testVerifyHashChainFailure() {
        when(auditLogService.verifyHashChain()).thenReturn(false);

        var result = complianceController.verifyHashChain();

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(false, result.getData());
    }

    @Test
    void testGetAuditLogsForEntity() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setEntityType("Requirement");
        log.setEntityId(100L);
        when(auditLogService.getAuditLogsForEntity("Requirement", 100L)).thenReturn(List.of(log));

        var result = complianceController.getAuditLogsForEntity("Requirement", 100L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void testGetAuditLogsByOperator() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setOperatorId(1L);
        when(auditLogService.getAuditLogsByOperator(1L)).thenReturn(List.of(log));

        var result = complianceController.getAuditLogsByOperator(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testGetAuditLogsByTimeRange() {
        when(auditLogService.getAuditLogsByTimeRange(any(), any())).thenReturn(List.of());

        var result = complianceController.getAuditLogsByTimeRange(
                LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testExportAuditLogs() throws Exception {
        when(auditLogService.getLogsForExport(any(), any(), any())).thenReturn(List.of());
        when(auditLogService.generateCsv(any())).thenReturn("ID,EventType\n");

        MockHttpServletResponse response = new MockHttpServletResponse();
        complianceController.exportAuditLogs(
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), null, response);

        assertEquals("text/csv", response.getContentType());
    }
}