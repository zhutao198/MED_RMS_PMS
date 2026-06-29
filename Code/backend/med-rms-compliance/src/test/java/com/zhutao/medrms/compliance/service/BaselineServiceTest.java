package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.Baseline;
import com.zhutao.medrms.compliance.mapper.BaselineMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementStatus;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BaselineService 单元测试（W2-D5）
 * 覆盖：基线化（FR-0.17 审批前置）/创建/双签锁定/双签解锁/查询/对比
 */
@ExtendWith(MockitoExtension.class)
class BaselineServiceTest {

    @Mock private BaselineMapper baselineMapper;
    @Mock private RequirementMapper requirementMapper;

    @InjectMocks private BaselineService service;

    // ============================================================
    // 1. baselineRequirements
    // ============================================================

    @Test
    @DisplayName("baselineRequirements-空列表不抛错")
    void baselineRequirements_empty() {
        service.baselineRequirements(1L, Collections.emptyList());
        verify(requirementMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("baselineRequirements-非 APPROVED 抛 FR-0.17")
    void baselineRequirements_notApproved() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setStatus(RequirementStatus.DRAFT);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.baselineRequirements(10L, List.of(1L)));
        assertTrue(ex.getMessage().contains("FR-0.17"));
    }

    @Test
    @DisplayName("baselineRequirements-APPROVED → BASELINE 状态 + 写基线 ID")
    void baselineRequirements_ok() {
        Requirement r1 = new Requirement();
        r1.setId(1L);
        r1.setStatus(RequirementStatus.APPROVED);
        when(requirementMapper.selectById(1L)).thenReturn(r1);

        Baseline bl = new Baseline();
        bl.setId(10L);
        when(baselineMapper.selectById(10L)).thenReturn(bl);

        service.baselineRequirements(10L, List.of(1L));

        ArgumentCaptor<Requirement> cap = ArgumentCaptor.forClass(Requirement.class);
        verify(requirementMapper, atLeastOnce()).updateById(cap.capture());
        assertEquals(RequirementStatus.BASELINE, cap.getValue().getStatus());
        assertEquals(10L, cap.getValue().getBaselineId());
    }

    // ============================================================
    // 2. createBaseline
    // ============================================================

    @Test
    @DisplayName("createBaseline-空 ID 列表允许")
    void createBaseline_emptyIds() {
        when(baselineMapper.selectCount(null)).thenReturn(0L);

        Baseline bl = service.createBaseline(1L, "test", null);

        assertEquals("DRAFT", bl.getStatus());
        assertNotNull(bl.getBaselineNo());
        assertNotNull(bl.getSnapshotData());
    }

    @Test
    @DisplayName("createBaseline-带 IDs 生成快照")
    void createBaseline_withIds() {
        when(requirementMapper.selectBatchIds(List.of(1L, 2L)))
            .thenReturn(List.of(req(1L, "URS"), req(2L, "PRS")));
        when(baselineMapper.selectCount(null)).thenReturn(5L);

        Baseline bl = service.createBaseline(1L, "BL-1", List.of(1L, 2L));

        assertEquals("BL-1-0006", bl.getBaselineNo());
        assertTrue(bl.getSnapshotData().contains("URS-001"));
        assertTrue(bl.getSnapshotData().contains("PRS-002"));
    }

    // ============================================================
    // 3. lockBaseline（双签 Part 11 §11.200）
    // ============================================================

    @Test
    @DisplayName("lockBaseline-参数校验：user1Id/user2Id")
    void lockBaseline_paramUsers() {
        assertThrows(BusinessException.class, () -> service.lockBaseline(1L, null, 10L, 20L, 11L));
        assertThrows(BusinessException.class, () -> service.lockBaseline(1L, 100L, 10L, null, 11L));
    }

    @Test
    @DisplayName("lockBaseline-参数校验：signatureId")
    void lockBaseline_paramSignatures() {
        assertThrows(BusinessException.class, () -> service.lockBaseline(1L, 100L, null, 200L, 11L));
    }

    @Test
    @DisplayName("lockBaseline-同 user 抛 Part 11")
    void lockBaseline_sameUser() {
        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.lockBaseline(1L, 100L, 10L, 100L, 11L));
        assertTrue(ex.getMessage().contains("Part 11"));
    }

    @Test
    @DisplayName("lockBaseline-同 signature 抛 param")
    void lockBaseline_sameSignature() {
        assertThrows(BusinessException.class,
            () -> service.lockBaseline(1L, 100L, 10L, 200L, 10L));
    }

    @Test
    @DisplayName("lockBaseline-基线不存在")
    void lockBaseline_notFound() {
        when(baselineMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class,
            () -> service.lockBaseline(99L, 100L, 10L, 200L, 11L));
    }

    @Test
    @DisplayName("lockBaseline-非 DRAFT 状态抛 stateConflict")
    void lockBaseline_invalidState() {
        Baseline bl = new Baseline();
        bl.setId(1L);
        bl.setStatus("LOCKED");
        when(baselineMapper.selectById(1L)).thenReturn(bl);

        assertThrows(BusinessException.class,
            () -> service.lockBaseline(1L, 100L, 10L, 200L, 11L));
    }

    @Test
    @DisplayName("lockBaseline-DRAFT → LOCKED + 双签记录")
    void lockBaseline_ok() {
        Baseline bl = new Baseline();
        bl.setId(1L);
        bl.setStatus("DRAFT");
        when(baselineMapper.selectById(1L)).thenReturn(bl);

        Baseline result = service.lockBaseline(1L, 100L, 10L, 200L, 11L);

        assertEquals("LOCKED", result.getStatus());
        assertEquals(100L, result.getLockUser1Id());
        assertEquals(200L, result.getLockUser2Id());
        assertEquals(10L, result.getLockSignatureId1());
        assertEquals(11L, result.getLockSignatureId2());
    }

    // ============================================================
    // 4. unlockBaseline
    // ============================================================

    @Test
    @DisplayName("unlockBaseline-LOCKED → DRAFT")
    void unlockBaseline_ok() {
        Baseline bl = new Baseline();
        bl.setId(1L);
        bl.setStatus("LOCKED");
        bl.setLockUser1Id(100L);
        bl.setLockUser2Id(200L);
        when(baselineMapper.selectById(1L)).thenReturn(bl);

        Baseline result = service.unlockBaseline(1L, 100L, 10L, 200L, 11L, "rollback");

        assertEquals("DRAFT", result.getStatus());
        assertNull(result.getLockUser1Id());
    }

    @Test
    @DisplayName("unlockBaseline-非 LOCKED 抛 stateConflict")
    void unlockBaseline_invalidState() {
        Baseline bl = new Baseline();
        bl.setId(1L);
        bl.setStatus("DRAFT");
        when(baselineMapper.selectById(1L)).thenReturn(bl);

        assertThrows(BusinessException.class,
            () -> service.unlockBaseline(1L, 100L, 10L, 200L, 11L, "x"));
    }

    // ============================================================
    // 5. 查询 / 对比
    // ============================================================

    @Test
    @DisplayName("getById-透传")
    void getById() {
        Baseline bl = new Baseline();
        when(baselineMapper.selectById(1L)).thenReturn(bl);
        assertSame(bl, service.getById(1L));
    }

    @Test
    @DisplayName("getByProject-按项目 ID 过滤")
    void getByProject() {
        when(baselineMapper.selectList(any())).thenReturn(List.of(new Baseline()));
        assertEquals(1, service.getByProject(1L).size());
    }

    @Test
    @DisplayName("compare-任一基线不存在抛 RQ0101")
    void compare_notFound() {
        when(baselineMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.compare(1L, 2L));
    }

    @Test
    @DisplayName("compare-snapshot 大小差 → added/removed count")
    void compare_ok() {
        Baseline b1 = new Baseline();
        b1.setId(1L);
        b1.setBaselineName("BL-1");
        b1.setSnapshotData("[{\"id\":1},{\"id\":2}]");
        Baseline b2 = new Baseline();
        b2.setId(2L);
        b2.setBaselineName("BL-2");
        b2.setSnapshotData("[{\"id\":1},{\"id\":2},{\"id\":3}]");
        when(baselineMapper.selectById(1L)).thenReturn(b1);
        when(baselineMapper.selectById(2L)).thenReturn(b2);

        Map<String, Object> result = service.compare(1L, 2L);

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertEquals(1, summary.get("addedCount"));
        assertEquals(0, summary.get("removedCount"));
    }

    private Requirement req(Long id, String type) {
        Requirement r = new Requirement();
        r.setId(id);
        r.setRequirementType(type);
        r.setRequirementNo(type + "-" + String.format("%03d", id));
        return r;
    }
}
