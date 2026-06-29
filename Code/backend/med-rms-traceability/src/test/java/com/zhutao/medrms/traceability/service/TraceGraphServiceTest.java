package com.zhutao.medrms.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementRelation;
import com.zhutao.medrms.traceability.mapper.RequirementRelationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * TraceGraphService 单元测试（W12-D4）
 * 追溯图构建：节点/边/孤立 + 质量评分
 */
@ExtendWith(MockitoExtension.class)
class TraceGraphServiceTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private RequirementRelationMapper relationMapper;

    @InjectMocks private TraceGraphService service;

    private Requirement newReq(Long id, String title, String type, String priority) {
        Requirement r = new Requirement();
        r.setId(id);
        r.setProjectId(1L);
        r.setRequirementNo("REQ-" + id);
        r.setTitle(title);
        r.setDescription("desc-" + id);
        r.setRequirementType(type);
        r.setStatus("DRAFT");
        r.setPriority(priority);
        return r;
    }

    private RequirementRelation newRel(Long id, Long src, Long tgt, String type) {
        RequirementRelation rel = new RequirementRelation();
        rel.setId(id);
        rel.setSourceReqId(src);
        rel.setTargetReqId(tgt);
        rel.setRelationType(type);
        rel.setProjectId(1L);
        return rel;
    }

    // ============================================================
    // 1. getTraceGraph
    // ============================================================

    @Test
    @DisplayName("getTraceGraph-基本：节点+边+孤立节点+统计")
    @SuppressWarnings("unchecked")
    void getTraceGraph_basic() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newReq(1L, "标题1", "URS", "MUST"),
                        newReq(2L, "标题2", "SRS", "SHOULD")));
        when(relationMapper.selectList(isNull()))
                .thenReturn(List.of(newRel(1L, 1L, 2L, "DEPENDS")));

        Map<String, Object> graph = service.getTraceGraph(1L);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        List<Map<String, Object>> orphans = (List<Map<String, Object>>) graph.get("orphans");
        Map<String, Object> stats = (Map<String, Object>) graph.get("stats");

        assertEquals(2, nodes.size());
        assertEquals(1, edges.size());
        assertEquals(0, orphans.size(), "节点 1↔2 互连，不应孤立");
        assertEquals(2, stats.get("totalNodes"));
        assertEquals(1, stats.get("totalEdges"));
        assertEquals(0, stats.get("orphanCount"));
        // Math.round() 返回 long，Map 值是 Long
        assertEquals(100L, stats.get("traceRate"));
    }

    @Test
    @DisplayName("getTraceGraph-无关系：所有节点都是孤立")
    @SuppressWarnings("unchecked")
    void getTraceGraph_allOrphans() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newReq(1L, "A", "URS", "MUST"),
                        newReq(2L, "B", "SRS", "MUST")));
        when(relationMapper.selectList(isNull())).thenReturn(List.of());

        Map<String, Object> graph = service.getTraceGraph(1L);

        List<Map<String, Object>> orphans = (List<Map<String, Object>>) graph.get("orphans");
        Map<String, Object> stats = (Map<String, Object>) graph.get("stats");
        assertEquals(2, orphans.size());
        // Math.round() 返回 long
        assertEquals(0L, stats.get("traceRate"));
    }

    @Test
    @DisplayName("getTraceGraph-空数据：traceRate=100（避免除 0）")
    @SuppressWarnings("unchecked")
    void getTraceGraph_empty() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        when(relationMapper.selectList(isNull())).thenReturn(List.of());

        Map<String, Object> graph = service.getTraceGraph(1L);

        Map<String, Object> stats = (Map<String, Object>) graph.get("stats");
        assertEquals(0, stats.get("totalNodes"));
        // Math.round() 返回 long
        assertEquals(100L, stats.get("traceRate"), "空数据时 traceRate 应兜底为 100");
    }

    @Test
    @DisplayName("getTraceGraph-边去重：同 source-target 多条只保留一条")
    @SuppressWarnings("unchecked")
    void getTraceGraph_edgeDedupe() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newReq(1L, "A", "URS", "MUST"),
                        newReq(2L, "B", "SRS", "MUST")));
        // 3 条同样 1->2 的关系
        when(relationMapper.selectList(isNull())).thenReturn(List.of(
                newRel(1L, 1L, 2L, "DEPENDS"),
                newRel(2L, 1L, 2L, "DEPENDS"),
                newRel(3L, 1L, 2L, "DEPENDS")
        ));

        Map<String, Object> graph = service.getTraceGraph(1L);

        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        assertEquals(1, edges.size(), "3 条重复边应去重为 1 条");
    }

    // ============================================================
    // 2. getQualityScore
    // ============================================================

    @Test
    @DisplayName("getQualityScore-需求不存在：score=0 + level=UNKNOWN")
    void getQualityScore_notFound() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        Map<String, Object> result = service.getQualityScore(99L);

        assertEquals(0, result.get("score"));
        assertEquals("UNKNOWN", result.get("level"));
    }

    @Test
    @DisplayName("getQualityScore-完整 MUST 需求：score=100 + level=EXCELLENT")
    void getQualityScore_excellent() {
        Requirement r = newReq(1L, "完整标题", "URS", "MUST");
        // 描述必须 > 50 字符才能在可测试性评分 +10
        r.setDescription("用户登录系统时应通过用户名密码验证身份，验证通过后跳转至主页面。" +
                "若密码错误三次则锁定账号 30 分钟。");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        Map<String, Object> result = service.getQualityScore(1L);

        assertEquals(100, result.get("score"));
        assertEquals("EXCELLENT", result.get("level"));
    }

    @Test
    @DisplayName("getQualityScore-空字段需求：score=50 + level=POOR")
    void getQualityScore_poor() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setTitle(null);
        r.setDescription(null);
        r.setPriority(null);
        r.setStatus(null);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        Map<String, Object> result = service.getQualityScore(1L);

        // 完整性 0（字段都空）+ 一致性 15（基础）+ 可测试性 15（基础）+ 合规性 20（基础）= 50
        assertEquals(50, result.get("score"));
        assertEquals("POOR", result.get("level"));
    }

    // ============================================================
    // 3. getBatchQualityScore
    // ============================================================

    @Test
    @DisplayName("getBatchQualityScore-项目下所有需求批量评分")
    void getBatchQualityScore() {
        when(requirementMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newReq(1L, "A", "URS", "MUST"),
                        newReq(2L, "B", "SRS", "SHOULD")));
        // getBatchQualityScore 内部循环调用 getQualityScore → 又调 selectById
        when(requirementMapper.selectById(1L)).thenReturn(newReq(1L, "A", "URS", "MUST"));
        when(requirementMapper.selectById(2L)).thenReturn(newReq(2L, "B", "SRS", "SHOULD"));

        List<Map<String, Object>> result = service.getBatchQualityScore(1L);

        assertEquals(2, result.size());
    }
}
