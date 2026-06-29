package com.zhutao.medrms.traceability.controller;

import com.zhutao.medrms.traceability.service.TraceabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceabilityControllerTest {

    @Mock
    private TraceabilityService traceabilityService;

    @InjectMocks
    private TraceabilityController traceabilityController;

    @Test
    void testGetTraceMatrix() {
        when(traceabilityService.getTraceMatrix(1L)).thenReturn(List.of(
                Map.of("urs", Map.of("id", 1L, "title", "URS-001"))
        ));

        var result = traceabilityController.getTraceMatrix(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1, result.getData().size());
    }

    @Test
    void testGetCoverageStats() {
        when(traceabilityService.getCoverageStats(1L)).thenReturn(Map.of(
                "overallLinkedRate", 85
        ));

        var result = traceabilityController.getCoverageStats(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testGetTraceGaps() {
        when(traceabilityService.getTraceGaps(1L)).thenReturn(List.of());

        var result = traceabilityController.getTraceGaps(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
    }

    @Test
    void testGetTraceBreakages() {
        when(traceabilityService.getTraceBreakages(1L)).thenReturn(List.of(
                Map.of("gapType", "MISSING_CHILDREN"),
                Map.of("gapType", "ORPHAN")
        ));

        var result = traceabilityController.getTraceBreakages(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(2, result.getData().size());
    }
}