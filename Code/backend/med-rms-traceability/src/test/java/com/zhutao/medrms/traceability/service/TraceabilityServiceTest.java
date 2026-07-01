package com.zhutao.medrms.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.outbox.OutboxService;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementAncestor;
import com.zhutao.medrms.requirement.domain.entity.TestCase;
import com.zhutao.medrms.requirement.mapper.RequirementAncestorMapper;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementTestCase;
import com.zhutao.medrms.traceability.domain.entity.TraceGapIgnored;
import com.zhutao.medrms.traceability.domain.entity.TraceLink;
import com.zhutao.medrms.traceability.mapper.RequirementRelationMapper;
import com.zhutao.medrms.traceability.mapper.RequirementTestCaseMapper;
import com.zhutao.medrms.traceability.mapper.TraceGapIgnoredMapper;
import com.zhutao.medrms.traceability.mapper.TraceLinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TraceabilityService 单元测试（W2-D3）
 * 覆盖：矩阵/覆盖率/缺口 3 类/无环校验/TraceLink CRUD/导入 preview+commit/缺口忽略/横向关系/TC 关联/断裂
 */
@ExtendWith(MockitoExtension.class)
class TraceabilityServiceTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private RequirementAncestorMapper ancestorMapper;
    @Mock private RequirementRelationMapper relationMapper;
    @Mock private RequirementTestCaseMapper testCaseMapper;
    @Mock private TraceLinkMapper traceLinkMapper;
    @Mock private TraceGapIgnoredMapper gapIgnoredMapper;
    @Mock private TestCaseMapper tcMapper;
    @Mock private NotificationService notificationService;
    @Mock private OutboxService outboxService;

    @InjectMocks private TraceabilityService service;

    // ============================================================
    // 1. 追溯矩阵
    // ============================================================

    @Test
    @DisplayName("getTraceMatrix-空项目返回空列表")
    void getTraceMatrix_empty() {
        // R93 性能优化：空项目无需求就不会进 TC IN 查询分支，不需要 stub tcMapper/testCaseMapper
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        assertTrue(service.getTraceMatrix(1L).isEmpty());
    }

    @Test
    @DisplayName("getTraceMatrix-URS无子级只输出 URS 节点")
    void getTraceMatrix_ursOnly() {
        // R93 性能优化：URS 单节点，无 DRS 时不进 tcMapper/testCaseMapper 的 IN 分支
        Requirement urs = req(1L, "URS", "URS-001");
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(urs));
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> matrix = service.getTraceMatrix(1L);

        assertEquals(1, matrix.size());
        assertEquals(urs, matrix.get(0).get("urs"));
        assertNull(matrix.get(0).get("prs"));
    }

    @Test
    @DisplayName("getTraceMatrix-URS→PRS→SRS→DRS 完整链路 + TC 关联")
    void getTraceMatrix_fullChain() {
        Requirement urs = req(1L, "URS");
        Requirement prs = req(2L, "PRS");
        Requirement srs = req(3L, "SRS");
        Requirement drs = req(4L, "DRS");
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(urs, prs, srs, drs));

        // R93 性能优化：闭包表 ancestor 一次性批量查，IN (URS,PRS,SRS)，depth=1
        RequirementAncestor ursPrs = anc(1L, 2L, 1);
        RequirementAncestor prsSrs = anc(2L, 3L, 1);
        RequirementAncestor srsDrs = anc(3L, 4L, 1);
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(ursPrs, prsSrs, srsDrs));

        TestCase tc = new TestCase();
        tc.setId(100L);
        RequirementTestCase link = new RequirementTestCase();
        link.setRequirementId(4L);
        link.setTestCaseId(100L);
        link.setTraceType("VERIFIES");
        when(testCaseMapper.selectList(any())).thenReturn(List.of(link));
        // R93 性能优化：用 selectBatchIds 按 ID 批量取 TC，不再 selectList
        when(tcMapper.selectBatchIds(any())).thenReturn(List.of(tc));

        List<Map<String, Object>> matrix = service.getTraceMatrix(1L);

        assertEquals(1, matrix.size());
        Map<String, Object> row = matrix.get(0);
        assertEquals(urs, row.get("urs"));
        assertEquals(prs, row.get("prs"));
        assertEquals(srs, row.get("srs"));
        assertEquals(drs, row.get("drs"));
        assertEquals(tc, row.get("tc"));
        assertEquals("VERIFIES", row.get("traceType"));
    }

    // ============================================================
    // 2. 覆盖率统计
    // ============================================================

    @Test
    @DisplayName("getCoverageStats-4 层统计 + overall")
    void getCoverageStats_basic() {
        // count 返回 10/8/6/4 (URS/PRS/SRS/DRS)
        when(requirementMapper.selectCount(any(LambdaQueryWrapper.class)))
            .thenReturn(10L, 8L, 6L, 4L);
        // DRS 无 childType，countWithChildren 不会跑（但 mocked return 0L 兜底）

        Map<String, Object> stats = service.getCoverageStats(1L);

        assertNotNull(stats);
        Map<?,?> byType = (Map<?,?>) stats.get("byType");
        assertEquals(4, byType.size());
        assertNotNull(stats.get("overall"));
        assertNotNull(stats.get("total"));
    }

    // ============================================================
    // 3. 缺口检测
    // ============================================================

    @Test
    @DisplayName("getTraceGaps-空项目返回空")
    void getTraceGaps_empty() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getTraceGaps(1L).isEmpty());
    }

    @Test
    @DisplayName("getTraceGaps-ORPHAN 缺口（无上层）")
    void getTraceGaps_orphan() {
        Requirement prs = req(2L, "PRS", "PRS-002");
        // R93 性能优化后只剩 3 次 selectList 调用：
        //   findShouldHaveChildren  (1) 查所有 Approved URS/PRS/SRS
        //   findOrphanRequirements  (2) 查所有 PRS+SRS+DRS ← PRS 在这里返回
        //   findWithoutTestCase     (3) 查所有非 Draft SRS/DRS
        lenient().when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())  // 1 findShouldHaveChildren
            .thenReturn(List.of(prs))             // 2 findOrphanRequirements
            .thenReturn(Collections.emptyList()); // 3 findWithoutTestCase
        lenient().when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> gaps = service.getTraceGaps(1L);

        assertTrue(gaps.stream().anyMatch(g -> "ORPHAN".equals(g.get("type"))));
    }

    @Test
    @DisplayName("getTraceGaps-NO_TEST_CASE 缺口")
    void getTraceGaps_noTestCase() {
        Requirement srs = req(3L, "SRS", "SRS-003");
        srs.setStatus("InProgress");
        // R93 性能优化后只剩 3 次 selectList 调用（见 getTraceGaps_orphan 注释）
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())  // 1 findShouldHaveChildren
            .thenReturn(Collections.emptyList())  // 2 findOrphanRequirements
            .thenReturn(List.of(srs));            // 3 findWithoutTestCase

        List<Map<String, Object>> gaps = service.getTraceGaps(1L);

        assertTrue(gaps.stream().anyMatch(g -> "NO_TEST_CASE".equals(g.get("type"))));
    }

    @Test
    @DisplayName("getTraceGaps-MISSING_CHILDREN（URS 缺下层）")
    void getTraceGaps_missingChildren() {
        Requirement urs = req(1L, "URS", "URS-001");
        urs.setStatus("Approved");
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(List.of(urs))
            .thenReturn(Collections.emptyList())
            .thenReturn(Collections.emptyList());
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> gaps = service.getTraceGaps(1L);

        assertTrue(gaps.stream().anyMatch(g -> "MISSING_CHILDREN".equals(g.get("type"))));
    }

    // ============================================================
    // 4. 无环校验
    // ============================================================

    @Test
    @DisplayName("wouldCreateCycle-自身成环")
    void wouldCreateCycle_self() {
        assertTrue(service.wouldCreateCycle(1L, 1L));
    }

    @Test
    @DisplayName("wouldCreateCycle-无环（无路径）")
    void wouldCreateCycle_noPath() {
        when(traceLinkMapper.selectByTargetId(2L)).thenReturn(Collections.emptyList());
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        assertFalse(service.wouldCreateCycle(1L, 2L));
    }

    @Test
    @DisplayName("wouldCreateCycle-有环（target 通过 traceLink 回到 source）")
    void wouldCreateCycle_hasCycle() {
        TraceLink incoming = new TraceLink();
        incoming.setSourceId(1L);
        when(traceLinkMapper.selectByTargetId(2L)).thenReturn(List.of(incoming));
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        assertTrue(service.wouldCreateCycle(1L, 2L));
    }

    @Test
    @DisplayName("wouldCreateCycle-通过闭包表祖先成环")
    void wouldCreateCycle_viaAncestor() {
        when(traceLinkMapper.selectByTargetId(2L)).thenReturn(Collections.emptyList());
        RequirementAncestor anc = new RequirementAncestor();
        anc.setAncestorId(1L);
        anc.setDepth(1);
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(anc));

        assertTrue(service.wouldCreateCycle(1L, 2L));
    }

    // ============================================================
    // 5. TraceLink CRUD
    // ============================================================

    @Test
    @DisplayName("createTraceLink-参数校验 linkType")
    void createTraceLink_missingLinkType() {
        TraceLink link = new TraceLink();
        link.setSourceId(1L);
        link.setTargetId(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTraceLink(link));
        assertTrue(ex.getMessage().contains("linkType"));
    }

    @Test
    @DisplayName("createTraceLink-参数校验 source/target")
    void createTraceLink_missingSourceTarget() {
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_DEPENDS);

        assertThrows(BusinessException.class, () -> {
            link.setSourceId(null); link.setTargetId(2L);
            service.createTraceLink(link);
        });
        assertThrows(BusinessException.class, () -> {
            link.setSourceId(1L); link.setTargetId(null);
            service.createTraceLink(link);
        });
    }

    @Test
    @DisplayName("createTraceLink-源和目标相同抛错")
    void createTraceLink_sameSourceTarget() {
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_DEPENDS);
        link.setSourceId(1L);
        link.setTargetId(1L);

        assertThrows(BusinessException.class, () -> service.createTraceLink(link));
    }

    @Test
    @DisplayName("createTraceLink-DECOMPOSE 成环抛错")
    void createTraceLink_cycle() {
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_DECOMPOSE);
        link.setSourceId(1L);
        link.setTargetId(2L);

        when(traceLinkMapper.selectByTargetId(2L)).thenReturn(Collections.emptyList());
        RequirementAncestor anc = new RequirementAncestor();
        anc.setAncestorId(1L);
        anc.setDepth(1);
        when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(anc));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTraceLink(link));
        assertTrue(ex.getMessage().contains("追溯环"));
    }

    @Test
    @DisplayName("createTraceLink-重复检查")
    void createTraceLink_duplicate() {
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_DEPENDS);
        link.setSourceId(1L);
        link.setTargetId(2L);
        link.setSourceType("REQUIREMENT");
        link.setTargetType("REQUIREMENT");

        when(traceLinkMapper.countExists(1L, 2L, TraceLink.TYPE_DEPENDS)).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createTraceLink(link));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("createTraceLink-成功 + 补全 sourceNo/targetNo/projectId")
    void createTraceLink_success() {
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_DEPENDS);
        link.setSourceId(1L);
        link.setTargetId(2L);
        link.setSourceType("REQUIREMENT");
        link.setTargetType("REQUIREMENT");

        when(traceLinkMapper.countExists(any(), any(), any())).thenReturn(0L);
        Requirement src = req(1L, "URS", "URS-001");
        Requirement tgt = req(2L, "PRS", "PRS-002");
        when(requirementMapper.selectById(1L)).thenReturn(src);
        when(requirementMapper.selectById(2L)).thenReturn(tgt);

        service.createTraceLink(link);

        assertEquals("URS-001", link.getSourceNo());
        assertEquals("PRS-002", link.getTargetNo());
        assertEquals(1L, link.getProjectId());
        verify(traceLinkMapper).insert(link);
        verify(outboxService).append(eq("TraceLinkCreated"), eq("TraceLink"), any(), any());
    }

    @Test
    @DisplayName("createTraceLink-TC 作为 source/target 时从 tcMapper 取编号")
    void createTraceLink_withTestCase() {
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_VERIFIES);
        link.setSourceId(10L);
        link.setTargetId(20L);
        link.setSourceType("REQUIREMENT");
        link.setTargetType("TEST_CASE");

        when(traceLinkMapper.countExists(any(), any(), any())).thenReturn(0L);
        Requirement src = req(10L, "DRS", "DRS-010");
        TestCase tc = new TestCase();
        tc.setId(20L);
        tc.setTestCaseNo("TC-000020");
        when(requirementMapper.selectById(10L)).thenReturn(src);
        when(tcMapper.selectById(20L)).thenReturn(tc);

        service.createTraceLink(link);

        assertEquals("TC-000020", link.getTargetNo());
    }

    @Test
    @DisplayName("updateTraceLink-追溯不存在抛 TRACE_NOT_FOUND")
    void updateTraceLink_notFound() {
        when(traceLinkMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateTraceLink(99L, new TraceLink()));
        assertEquals("TRACE_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("updateTraceLink-部分字段更新")
    void updateTraceLink_partial() {
        TraceLink existing = new TraceLink();
        existing.setId(1L);
        existing.setLinkType("DEPENDS");
        when(traceLinkMapper.selectById(1L)).thenReturn(existing);
        when(traceLinkMapper.updateById(any(TraceLink.class))).thenReturn(1);

        TraceLink patch = new TraceLink();
        patch.setTraceContext("ctx");

        assertTrue(service.updateTraceLink(1L, patch));
        verify(traceLinkMapper).updateById(existing);
    }

    @Test
    @DisplayName("deleteTraceLink-不存在返回 false")
    void deleteTraceLink_notExists() {
        when(traceLinkMapper.selectById(99L)).thenReturn(null);
        assertFalse(service.deleteTraceLink(99L));
    }

    @Test
    @DisplayName("deleteTraceLink-存在软删除 + 事件")
    void deleteTraceLink_exists() {
        TraceLink existing = new TraceLink();
        existing.setId(1L);
        existing.setLinkType(TraceLink.TYPE_DEPENDS);
        existing.setSourceId(1L);
        existing.setTargetId(2L);
        when(traceLinkMapper.selectById(1L)).thenReturn(existing);

        assertTrue(service.deleteTraceLink(1L));
        assertTrue(existing.getIsDeleted());
        verify(traceLinkMapper).updateById(existing);
        verify(outboxService).append(eq("TraceLinkDeleted"), eq("TraceLink"), any(), any());
    }

    @Test
    @DisplayName("listTraceLinks-按 linkType 过滤")
    void listTraceLinks_byType() {
        when(traceLinkMapper.selectByProjectAndType(1L, "DEPENDS")).thenReturn(List.of(new TraceLink()));
        assertEquals(1, service.listTraceLinks(1L, "DEPENDS").size());
    }

    @Test
    @DisplayName("listTraceLinks-无 linkType 返回全部")
    void listTraceLinks_all() {
        when(traceLinkMapper.selectByProject(1L)).thenReturn(List.of(new TraceLink(), new TraceLink()));
        assertEquals(2, service.listTraceLinks(1L, null).size());
    }

    // ============================================================
    // 6. 导入 preview + commit
    // ============================================================

    @Test
    @DisplayName("previewImport-空列表返回 0/0")
    void previewImport_empty() {
        Map<String, Object> r = service.previewImport(1L, Collections.emptyList());
        assertEquals(0, r.get("validCount"));
        assertEquals(0, r.get("invalidCount"));
    }

    @Test
    @DisplayName("previewImport-行校验：合法 + 不合法 分类")
    void previewImport_validation() {
        Requirement r1 = req(1L, "URS", "URS-001");
        Requirement r2 = req(2L, "PRS", "PRS-002");
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2));
        when(tcMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> good = new LinkedHashMap<>();
        good.put("sourceNo", "URS-001");
        good.put("sourceType", "REQUIREMENT");
        good.put("targetNo", "PRS-002");
        good.put("targetType", "REQUIREMENT");
        good.put("linkType", "DECOMPOSE");
        items.add(good);
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("sourceNo", "XXX");
        bad.put("sourceType", "REQUIREMENT");
        bad.put("targetNo", "PRS-002");
        bad.put("targetType", "REQUIREMENT");
        bad.put("linkType", "BAD_TYPE");
        items.add(bad);

        Map<String, Object> r = service.previewImport(1L, items);

        assertEquals(1, r.get("validCount"));
        assertEquals(1, r.get("invalidCount"));
    }

    @Test
    @DisplayName("previewImport-TC 源/目标通过 tcNoToId 解析")
    void previewImport_testCase() {
        Requirement r1 = req(1L, "URS", "URS-001");
        TestCase tc = new TestCase();
        tc.setId(50L);
        tc.setTestCaseNo("TC-000050");
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1));
        when(tcMapper.selectList(any())).thenReturn(List.of(tc));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceNo", "URS-001");
        item.put("sourceType", "REQUIREMENT");
        item.put("targetNo", "TC-000050");
        item.put("targetType", "TEST_CASE");
        item.put("linkType", "VERIFIES");

        Map<String, Object> r = service.previewImport(1L, List.of(item));

        assertEquals(1, r.get("validCount"));
    }

    @Test
    @DisplayName("importBatch-成功/失败分桶")
    void importBatch_split() {
        // v2.1 修复：importBatch 内部解析 No → Id
        when(traceLinkMapper.countExists(any(), any(), any())).thenReturn(0L);
        Requirement r1 = req(1L, "URS", "URS-001");
        Requirement r2 = req(2L, "PRS", "PRS-002");
        // importBatch 4 次 selectOne：
        // 1) ok.sourceNo="URS-001" → r1  2) ok.targetNo="PRS-002" → r2
        // 3) bad.sourceNo="XXX" → null   4) bad.targetNo="PRS-002" → r2
        java.util.concurrent.atomic.AtomicInteger callIdx = new java.util.concurrent.atomic.AtomicInteger(0);
        when(requirementMapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            int idx = callIdx.getAndIncrement();
            // 0: ok.sourceNo "URS-001" → r1
            // 1: ok.targetNo "PRS-002" → r2
            // 2: bad.sourceNo "XXX" → null (源编号不存在 → 抛 TR_IMP_001)
            // 3: bad.targetNo "PRS-002" → r2 (不会到这里，前一步已抛)
            return switch (idx) {
                case 0 -> r1;
                case 1, 3 -> r2;
                case 2 -> null;
                default -> null;
            };
        });

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("sourceNo", "URS-001");
        ok.put("sourceType", "REQUIREMENT");
        ok.put("targetNo", "PRS-002");
        ok.put("targetType", "REQUIREMENT");
        ok.put("linkType", "DECOMPOSE");
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("sourceNo", "XXX");
        bad.put("sourceType", "REQUIREMENT");
        bad.put("targetNo", "PRS-002");
        bad.put("targetType", "REQUIREMENT");
        bad.put("linkType", "DECOMPOSE");

        Map<String, Object> r = service.importBatch(1L, List.of(ok, bad));

        assertEquals(1, r.get("success"));
        assertEquals(1, r.get("failed"));
    }

    // ============================================================
    // 7. 缺口忽略
    // ============================================================

    @Test
    @DisplayName("ignoreGap-参数校验")
    void ignoreGap_validation() {
        assertThrows(BusinessException.class, () -> service.ignoreGap(null, "ORPHAN", 1L, "x"));
        assertThrows(BusinessException.class, () -> service.ignoreGap(1L, null, 1L, "x"));
        assertThrows(BusinessException.class, () -> service.ignoreGap(1L, "ORPHAN", null, "x"));
    }

    @Test
    @DisplayName("ignoreGap-去重已存在直接返回 true")
    void ignoreGap_dedup() {
        TraceGapIgnored existing = new TraceGapIgnored();
        existing.setId(1L);
        when(gapIgnoredMapper.findUnique(1L, "ORPHAN", 100L)).thenReturn(existing);

        assertTrue(service.ignoreGap(1L, "ORPHAN", 100L, "x"));
        verify(gapIgnoredMapper, never()).insert(any(TraceGapIgnored.class));
    }

    @Test
    @DisplayName("ignoreGap-新插入 + 事件")
    void ignoreGap_new() {
        try (MockedStatic<com.zhutao.medrms.common.util.SecurityUtils> mocked =
                 Mockito.mockStatic(com.zhutao.medrms.common.util.SecurityUtils.class)) {
            mocked.when(com.zhutao.medrms.common.util.SecurityUtils::getCurrentUserId).thenReturn(50L);

            when(gapIgnoredMapper.findUnique(any(), any(), any())).thenReturn(null);

            assertTrue(service.ignoreGap(1L, "ORPHAN", 100L, "ignored by test"));
            verify(gapIgnoredMapper).insert(any(TraceGapIgnored.class));
            verify(outboxService).append(eq("TraceGapIgnored"), eq("TraceGap"), any(), any());
        }
    }

    @Test
    @DisplayName("getIgnoredGapIds-去重返回 requirementId 列表")
    void getIgnoredGapIds() {
        TraceGapIgnored r1 = new TraceGapIgnored();
        r1.setRequirementId(100L);
        TraceGapIgnored r2 = new TraceGapIgnored();
        r2.setRequirementId(100L); // 重复
        TraceGapIgnored r3 = new TraceGapIgnored();
        r3.setRequirementId(200L);
        when(gapIgnoredMapper.selectByProject(1L)).thenReturn(List.of(r1, r2, r3));

        List<Long> ids = service.getIgnoredGapIds(1L);

        assertEquals(2, ids.size());
        assertTrue(ids.contains(100L));
        assertTrue(ids.contains(200L));
    }

    // ============================================================
    // 8. 横向关系 / TC 关联（向后兼容）
    // ============================================================

    @Test
    @DisplayName("addHorizontalRelation-DEPENDS 映射到 TraceLink")
    void addHorizontalRelation_depends() {
        when(traceLinkMapper.countExists(any(), any(), any())).thenReturn(0L);
        Requirement r = req(1L, "URS");
        when(requirementMapper.selectById(any())).thenReturn(r);

        service.addHorizontalRelation(1L, 2L, "DEPENDS");

        verify(traceLinkMapper).insert(any(TraceLink.class));
        verify(relationMapper).insert(any(com.zhutao.medrms.traceability.domain.entity.RequirementRelation.class));
    }

    @Test
    @DisplayName("addTestCaseTrace-改走 TraceLink VERIFIES")
    void addTestCaseTrace() {
        when(traceLinkMapper.countExists(any(), any(), any())).thenReturn(0L);
        Requirement r = req(1L, "DRS");
        when(requirementMapper.selectById(any())).thenReturn(r);

        service.addTestCaseTrace(1L, 100L, "VERIFIES");

        verify(traceLinkMapper).insert(any(TraceLink.class));
        verify(testCaseMapper).insert(any(RequirementTestCase.class));
    }

    // ============================================================
    // 9. 断裂（getTraceBreakages）
    // ============================================================

    @Test
    @DisplayName("getTraceBreakages-无缺口返回空 + 无通知")
    void getTraceBreakages_empty() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertTrue(service.getTraceBreakages(1L).isEmpty());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("getTraceBreakages-有缺口 + 通知 owner")
    void getTraceBreakages_withGaps() {
        Requirement prs = req(2L, "PRS", "PRS-002");
        prs.setCreatedBy(99L);
        // R93 性能优化后只剩 3 次 selectList 调用（见 getTraceGaps_orphan 注释）
        lenient().when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
            .thenReturn(Collections.emptyList())  // 1 findShouldHaveChildren
            .thenReturn(List.of(prs))             // 2 findOrphanRequirements ← prs 是孤儿
            .thenReturn(Collections.emptyList()); // 3 findWithoutTestCase
        lenient().when(ancestorMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(requirementMapper.selectById(2L)).thenReturn(prs);

        List<Map<String, Object>> breakages = service.getTraceBreakages(1L);

        assertFalse(breakages.isEmpty());
        verify(notificationService).sendTraceBrokenNotification(eq(99L), eq(2L), eq("PRS-002"));
    }

    // ============================================================
    // helper
    // ============================================================

    private Requirement req(Long id, String type) {
        return req(id, type, type + "-" + String.format("%03d", id));
    }

    private Requirement req(Long id, String type, String no) {
        Requirement r = new Requirement();
        r.setId(id);
        r.setRequirementType(type);
        r.setRequirementNo(no);
        r.setStatus("Draft");
        r.setProjectId(1L);
        r.setTitle(type + "-title");
        return r;
    }

    private RequirementAncestor anc(Long ancestorId, Long descendantId, int depth) {
        RequirementAncestor a = new RequirementAncestor();
        a.setAncestorId(ancestorId);
        a.setDescendantId(descendantId);
        a.setDepth(depth);
        return a;
    }
}
