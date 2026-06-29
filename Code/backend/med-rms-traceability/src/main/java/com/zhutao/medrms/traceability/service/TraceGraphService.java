package com.zhutao.medrms.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementRelation;
import com.zhutao.medrms.traceability.mapper.RequirementRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TraceGraphService {

    private final RequirementMapper requirementMapper;
    private final RequirementRelationMapper relationMapper;

    public Map<String, Object> getTraceGraph(Long projectId) {
        Map<String, Object> graph = new HashMap<>();

        // 获取所有需求
        List<Requirement> requirements = requirementMapper.selectList(
            new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eq(Requirement::getIsDeleted, false)
        );

        // 获取所有关系
        List<RequirementRelation> relations = relationMapper.selectList(null);

        // 构建节点
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Long, Requirement> reqMap = new HashMap<>();
        for (Requirement req : requirements) {
            reqMap.put(req.getId(), req);
            nodes.add(buildNode(req));
        }

        // 构建边
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> edgeSet = new HashSet<>();
        for (RequirementRelation rel : relations) {
            String edgeKey = rel.getSourceReqId() + "-" + rel.getTargetReqId();
            if (!edgeSet.contains(edgeKey)) {
                edgeSet.add(edgeKey);
                edges.add(buildEdge(rel));
            }
        }

        // 检测孤立节点（追溯断裂）
        List<Map<String, Object>> orphans = detectOrphans(nodes, edges);

        // W17 性能优化：节点超 500 时截断（前端按需 /requirements/{id} 获取 title）
        final int MAX_NODES = 500;
        boolean truncated = false;
        if (nodes.size() > MAX_NODES) {
            nodes = new ArrayList<>(nodes.subList(0, MAX_NODES));
            // 同步截断 edges（只保留两端都在 MAX_NODES 范围内的边）
            Set<Long> visibleIds = new HashSet<>();
            for (Map<String, Object> n : nodes) {
                visibleIds.add((Long) n.get("id"));
            }
            edges = edges.stream()
                .filter(e -> visibleIds.contains((Long) e.get("source")) && visibleIds.contains((Long) e.get("target")))
                .collect(Collectors.toList());
            truncated = true;
        }

        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("orphans", orphans);
        Map<String, Object> stats = buildStats(requirements, relations, orphans);
        stats.put("truncated", truncated);
        stats.put("maxNodes", MAX_NODES);
        graph.put("stats", stats);

        return graph;
    }

    public Map<String, Object> getQualityScore(Long requirementId) {
        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null) {
            return Map.of("score", 0, "level", "UNKNOWN");
        }

        int score = 0;
        int maxScore = 100;

        // 完整性评分 (25分)
        score += calculateCompletenessScore(req);

        // 一致性评分 (25分)
        score += calculateConsistencyScore(req);

        // 可测试性评分 (25分)
        score += calculateTestabilityScore(req);

        // 合规性评分 (25分)
        score += calculateComplianceScore(req);

        String level = score >= 90 ? "EXCELLENT" : score >= 75 ? "GOOD" : score >= 60 ? "FAIR" : "POOR";

        Map<String, Object> result = new HashMap<>();
        result.put("requirementId", requirementId);
        result.put("requirementNo", req.getRequirementNo());
        result.put("score", score);
        result.put("maxScore", maxScore);
        result.put("level", level);
        result.put("breakdown", Map.of(
            "completeness", calculateCompletenessScore(req),
            "consistency", calculateConsistencyScore(req),
            "testability", calculateTestabilityScore(req),
            "compliance", calculateComplianceScore(req)
        ));

        return result;
    }

    public List<Map<String, Object>> getBatchQualityScore(Long projectId) {
        List<Requirement> requirements = requirementMapper.selectList(
            new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eq(Requirement::getIsDeleted, false)
        );
        return requirements.stream().map(req -> getQualityScore(req.getId())).toList();
    }

    private Map<String, Object> buildNode(Requirement req) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", req.getId());
        node.put("requirementNo", req.getRequirementNo());
        // W17 性能优化：title 字段最大（中文 ~50 字节 × 638 节点 = 32K），按需通过 /requirements/{id} 获取
        // node.put("title", req.getTitle());
        node.put("type", req.getRequirementType());
        node.put("status", req.getStatus());
        node.put("priority", req.getPriority());
        node.put("isTraced", false); // 后续计算
        return node;
    }

    private Map<String, Object> buildEdge(RequirementRelation rel) {
        Map<String, Object> edge = new HashMap<>();
        edge.put("source", rel.getSourceReqId());
        edge.put("target", rel.getTargetReqId());
        edge.put("type", rel.getRelationType());
        edge.put("horizontalType", rel.getRelationType());
        return edge;
    }

    private List<Map<String, Object>> detectOrphans(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Set<Long> connectedIds = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            connectedIds.add((Long) edge.get("source"));
            connectedIds.add((Long) edge.get("target"));
        }

        List<Map<String, Object>> orphans = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Long id = (Long) node.get("id");
            if (!connectedIds.contains(id)) {
                orphans.add(node);
            }
        }
        return orphans;
    }

    private Map<String, Object> buildStats(List<Requirement> requirements, List<RequirementRelation> relations, List<Map<String, Object>> orphans) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalNodes", requirements.size());
        stats.put("totalEdges", relations.size());
        stats.put("orphanCount", orphans.size());
        stats.put("traceRate", requirements.isEmpty() ? 100 : Math.round((double)(requirements.size() - orphans.size()) / requirements.size() * 100));

        // 按类型统计
        Map<String, Integer> byType = new HashMap<>();
        for (Requirement req : requirements) {
            String type = req.getRequirementType();
            byType.put(type, byType.getOrDefault(type, 0) + 1);
        }
        stats.put("byType", byType);

        return stats;
    }

    private int calculateCompletenessScore(Requirement req) {
        int score = 0;
        if (req.getTitle() != null && !req.getTitle().isBlank()) score += 8;
        if (req.getDescription() != null && !req.getDescription().isBlank()) score += 8;
        if (req.getPriority() != null && !req.getPriority().isBlank()) score += 5;
        if (req.getStatus() != null && !req.getStatus().isBlank()) score += 4;
        return score;
    }

    private int calculateConsistencyScore(Requirement req) {
        // 简化：检查标题和描述是否一致
        int score = 15;
        if (req.getTitle() != null && req.getDescription() != null) {
            if (req.getTitle().length() <= req.getDescription().length()) {
                score += 10;
            }
        }
        return Math.min(25, score);
    }

    private int calculateTestabilityScore(Requirement req) {
        int score = 15;
        if (req.getDescription() != null && req.getDescription().length() > 50) {
            score += 10;
        }
        return Math.min(25, score);
    }

    private int calculateComplianceScore(Requirement req) {
        // 简化：检查优先级是否合理
        int score = 20;
        if ("MUST".equals(req.getPriority())) {
            score += 5;
        }
        return Math.min(25, score);
    }
}