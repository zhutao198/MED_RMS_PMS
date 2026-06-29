package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.compliance.domain.entity.Iec62304ChecklistItem;
import com.zhutao.medrms.compliance.mapper.Iec62304ChecklistMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Iec62304ChecklistService 单元测试（W2-D5）
 */
@ExtendWith(MockitoExtension.class)
class Iec62304ChecklistServiceTest {

    @Mock private Iec62304ChecklistMapper mapper;

    @InjectMocks private Iec62304ChecklistService service;

    // ============================================================
    // 1. initForProject
    // ============================================================

    @Test
    @DisplayName("initForProject-已有条款跳过")
    void init_existing() {
        when(mapper.selectByProjectId(1L)).thenReturn(List.of(new Iec62304ChecklistItem()));

        int n = service.initForProject(1L);

        assertEquals(0, n);
        verify(mapper, never()).insert(any(Iec62304ChecklistItem.class));
    }

    @Test
    @DisplayName("initForProject-无条款 → 初始化 12 条")
    void init_empty() {
        when(mapper.selectByProjectId(1L)).thenReturn(Collections.emptyList());

        int n = service.initForProject(1L);

        assertEquals(12, n);
        verify(mapper, times(12)).insert(any(Iec62304ChecklistItem.class));
    }

    // ============================================================
    // 2. assess
    // ============================================================

    @Test
    @DisplayName("assess-条款不存在抛 IllegalArgumentException")
    void assess_notFound() {
        when(mapper.selectById(99L)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
            () -> service.assess(99L, "COMPLIANT", "ev", null, 1L, "alice"));
    }

    @Test
    @DisplayName("assess-COMPLIANT 清除 gaps")
    void assess_compliant() {
        Iec62304ChecklistItem item = new Iec62304ChecklistItem();
        item.setId(1L);
        item.setGaps("some gap");
        when(mapper.selectById(1L)).thenReturn(item);

        Iec62304ChecklistItem result = service.assess(1L, "COMPLIANT", "evidence", "old gap", 100L, "alice");

        assertEquals("COMPLIANT", result.getComplianceStatus());
        assertNull(result.getGaps());
        assertEquals(100L, result.getAssessorId());
    }

    @Test
    @DisplayName("assess-PARTIAL 保留 gaps")
    void assess_partial() {
        Iec62304ChecklistItem item = new Iec62304ChecklistItem();
        item.setId(1L);
        when(mapper.selectById(1L)).thenReturn(item);

        Iec62304ChecklistItem result = service.assess(1L, "PARTIAL", "ev", "need more", 100L, "alice");

        assertEquals("PARTIAL", result.getComplianceStatus());
        assertEquals("need more", result.getGaps());
    }

    @Test
    @DisplayName("assess-NOT_APPLICABLE 清除 gaps")
    void assess_notApplicable() {
        Iec62304ChecklistItem item = new Iec62304ChecklistItem();
        item.setId(1L);
        when(mapper.selectById(1L)).thenReturn(item);

        Iec62304ChecklistItem result = service.assess(1L, "NOT_APPLICABLE", "N/A", "old gap", 100L, "alice");

        assertNull(result.getGaps());
    }

    // ============================================================
    // 3. getStats
    // ============================================================

    @Test
    @DisplayName("getStats-空统计 → 0/0")
    void getStats_empty() {
        when(mapper.countByStatus(1L)).thenReturn(Collections.emptyList());

        Map<String, Object> stats = service.getStats(1L);

        assertEquals(0L, stats.get("total"));
        assertEquals(0L, stats.get("compliant"));
    }

    @Test
    @DisplayName("getStats-混合状态计算 complianceRate")
    void getStats_mixed() {
        List<Map<String, Object>> raw = new ArrayList<>();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("status", "COMPLIANT"); c.put("cnt", 5);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", "PARTIAL"); p.put("cnt", 2);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("status", "NON_COMPLIANT"); n.put("cnt", 1);
        when(mapper.countByStatus(1L)).thenReturn(raw);
        raw.add(c); raw.add(p); raw.add(n);

        Map<String, Object> stats = service.getStats(1L);

        // applicable = 5+2+1 = 8
        // rate = round(5*100/8) = 63
        assertEquals(8L, stats.get("total"));
        assertEquals(63L, stats.get("complianceRate"));
    }

    // ============================================================
    // 4. runFullCheck
    // ============================================================

    @Test
    @DisplayName("runFullCheck-PENDING → PARTIAL + 自动证据")
    void runFullCheck() {
        Iec62304ChecklistItem p1 = new Iec62304ChecklistItem();
        p1.setId(1L);
        p1.setComplianceStatus("PENDING");
        Iec62304ChecklistItem p2 = new Iec62304ChecklistItem();
        p2.setId(2L);
        p2.setComplianceStatus("COMPLIANT"); // 已合规，不更新
        when(mapper.selectByProjectId(1L)).thenReturn(List.of(p1, p2));

        Map<String, Object> result = service.runFullCheck(1L);

        assertEquals(1, result.get("updatedCount"));
        assertEquals(2, result.get("totalCount"));
        assertEquals("PARTIAL", p1.getComplianceStatus());
        assertEquals("系统自动", p1.getAssessorName());
        verify(mapper, times(1)).updateById(any(Iec62304ChecklistItem.class));
    }
}
