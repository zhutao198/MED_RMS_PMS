package com.zhutao.medrms.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.outbox.OutboxService;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementAncestor;
import com.zhutao.medrms.requirement.domain.entity.TestCase;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.RequirementAncestorMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import com.zhutao.medrms.traceability.domain.entity.RequirementRelation;
import com.zhutao.medrms.traceability.domain.entity.RequirementTestCase;
import com.zhutao.medrms.traceability.domain.entity.TraceGapIgnored;
import com.zhutao.medrms.traceability.domain.entity.TraceLink;
import com.zhutao.medrms.traceability.mapper.RequirementRelationMapper;
import com.zhutao.medrms.traceability.mapper.RequirementTestCaseMapper;
import com.zhutao.medrms.traceability.mapper.TraceGapIgnoredMapper;
import com.zhutao.medrms.traceability.mapper.TraceLinkMapper;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    private final RequirementMapper requirementMapper;
    private final RequirementAncestorMapper ancestorMapper;
    private final RequirementRelationMapper relationMapper;
    private final RequirementTestCaseMapper testCaseMapper;
    private final TraceLinkMapper traceLinkMapper;
    private final TraceGapIgnoredMapper gapIgnoredMapper;
    private final TestCaseMapper tcMapper;
    // v1.44 BUG #66 修复：跨模块通知依赖
    private final NotificationService notificationService;
    // v1.47 BUG #137 P0 修复：领域事件
    private final OutboxService outboxService;

    /**
     * 获取追溯矩阵 (URS → PRS → SRS → DRS → TC 全链路)
     * v1.47 BUG #136 P0 修复：使用单条 JOIN SQL 一次拿全，O(N) 而非 N+1
     */
    public List<Map<String, Object>> getTraceMatrix(Long projectId) {
        // 一次拉全本项目所有需求 + 闭包表关联，按 ancestor 关系组
        List<Requirement> allReqs = requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
                        .eq(Requirement::getIsDeleted, false)
                        .in(Requirement::getRequirementType, List.of("URS", "PRS", "SRS", "DRS"))
                        .orderByAsc(Requirement::getId));
        Map<Long, Requirement> reqMap = new HashMap<>();
        for (Requirement r : allReqs) reqMap.put(r.getId(), r);

        // R93 性能修复：原 N+1 查询（每个 URS/PRS/SRS 都 ancestorMapper.selectList 一次）
        // 改为一次性批量查所有 ancestor_id IN (ids) AND depth=1
        List<Long> parentIds = new ArrayList<>();
        for (Requirement r : allReqs) {
            String t = r.getRequirementType();
            if ("URS".equals(t) || "PRS".equals(t) || "SRS".equals(t)) parentIds.add(r.getId());
        }
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            List<RequirementAncestor> allLinks = ancestorMapper.selectList(
                    new LambdaQueryWrapper<RequirementAncestor>()
                            .in(RequirementAncestor::getAncestorId, parentIds)
                            .eq(RequirementAncestor::getDepth, 1));
            for (RequirementAncestor link : allLinks) {
                childrenMap.computeIfAbsent(link.getAncestorId(), k -> new ArrayList<>())
                        .add(link.getDescendantId());
            }
        }

        // R93 性能修复：原 testCaseMapper.selectList(null) 全表扫描
        // 改为按项目需求 ID 过滤（IN 本项目所有 DRS 需求 ID）
        List<Long> projectReqIds = new ArrayList<>();
        for (Requirement r : allReqs) projectReqIds.add(r.getId());
        Map<Long, List<RequirementTestCase>> tcMap = new HashMap<>();
        Map<Long, TestCase> tcById = new HashMap<>();
        if (!projectReqIds.isEmpty()) {
            List<RequirementTestCase> projectTcLinks = testCaseMapper.selectList(
                    new LambdaQueryWrapper<RequirementTestCase>()
                            .in(RequirementTestCase::getRequirementId, projectReqIds));
            Set<Long> tcIds = new HashSet<>();
            for (RequirementTestCase t : projectTcLinks) {
                tcMap.computeIfAbsent(t.getRequirementId(), k -> new ArrayList<>()).add(t);
                tcIds.add(t.getTestCaseId());
            }
            if (!tcIds.isEmpty()) {
                List<TestCase> tcs = tcMapper.selectBatchIds(tcIds);
                for (TestCase tc : tcs) tcById.put(tc.getId(), tc);
            }
        }

        List<Map<String, Object>> matrix = new ArrayList<>();
        for (Requirement urs : allReqs) {
            if (!"URS".equals(urs.getRequirementType())) continue;
            Requirement prs = firstChildOfType(urs.getId(), "PRS", childrenMap, reqMap);
            Requirement srs = prs == null ? null : firstChildOfType(prs.getId(), "SRS", childrenMap, reqMap);
            Requirement drs = srs == null ? null : firstChildOfType(srs.getId(), "DRS", childrenMap, reqMap);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("urs", urs);
            row.put("prs", prs);
            row.put("srs", srs);
            row.put("drs", drs);
            if (drs != null) {
                List<RequirementTestCase> tcLinks = tcMap.getOrDefault(drs.getId(), List.of());
                if (!tcLinks.isEmpty()) {
                    TestCase tc = tcById.get(tcLinks.get(0).getTestCaseId());
                    row.put("tc", tc);
                    row.put("traceType", tcLinks.get(0).getTraceType());
                }
            }
            matrix.add(row);
        }
        return matrix;
    }

    private Requirement firstChildOfType(Long parentId, String childType,
                                         Map<Long, List<Long>> childrenMap,
                                         Map<Long, Requirement> reqMap) {
        List<Long> children = childrenMap.getOrDefault(parentId, List.of());
        for (Long cid : children) {
            Requirement c = reqMap.get(cid);
            if (c != null && childType.equals(c.getRequirementType())) return c;
        }
        return null;
    }

    /**
     * 获取需求覆盖率统计
     */
    public Map<String, Object> getCoverageStats(Long projectId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        Map<String, Object> byType = new LinkedHashMap<>();
        long totalAll = 0, tracedAll = 0;

        for (String type : List.of("URS", "PRS", "SRS", "DRS")) {
            Map<String, Object> level = getLevelCoverage(projectId, type);
            stats.put(type.toLowerCase(), level);
            byType.put(type, level);
            totalAll += ((Number) level.get("total")).longValue();
            tracedAll += ((Number) level.get("traced")).longValue();
        }

        long overall = totalAll > 0 ? (tracedAll * 100 / totalAll) : 0;
        stats.put("byType", byType);
        stats.put("overall", overall);
        stats.put("total", totalAll);
        stats.put("traced", tracedAll);
        stats.put("untraced", totalAll - tracedAll);

        return stats;
    }

    public List<Map<String, Object>> getTraceGaps(Long projectId) {
        List<Map<String, Object>> gaps = new ArrayList<>();

        for (Requirement req : findShouldHaveChildren(projectId)) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("type", "MISSING_CHILDREN");
            gap.put("requirement", req);
            gap.put("message", req.getRequirementType() + " 需求编号 " + req.getRequirementNo() + " 缺少下层拆解");
            gaps.add(gap);
        }
        List<Requirement> orphans = findOrphanRequirements(projectId);
        // R93 性能修复：原 N+1 查询（每个 ORPHAN 都 ancestorMapper.selectList + requirementMapper.selectById）
        // 改为批量查：一次查所有 ancestors + 一次批量查 parents
        Map<Long, Requirement> orphanParents = new HashMap<>();
        Map<Long, RequirementAncestor> firstByDesc = new HashMap<>();
        if (!orphans.isEmpty()) {
            List<Long> orphanIds = new ArrayList<>();
            for (Requirement r : orphans) orphanIds.add(r.getId());
            List<RequirementAncestor> allAncestors = ancestorMapper.selectList(
                    new LambdaQueryWrapper<RequirementAncestor>()
                            .in(RequirementAncestor::getDescendantId, orphanIds)
                            .gt(RequirementAncestor::getDepth, 0)
                            .orderByAsc(RequirementAncestor::getDepth));
            // 按 descendant_id 分组，取第一个（depth 最小）
            for (RequirementAncestor a : allAncestors) {
                firstByDesc.putIfAbsent(a.getDescendantId(), a);
            }
            // 批量查所有 parent ids
            Set<Long> parentIds = new HashSet<>();
            for (RequirementAncestor a : firstByDesc.values()) parentIds.add(a.getAncestorId());
            if (!parentIds.isEmpty()) {
                List<Requirement> parents = requirementMapper.selectBatchIds(parentIds);
                for (Requirement p : parents) orphanParents.put(p.getId(), p);
            }
        }
        for (Requirement req : orphans) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("type", "ORPHAN");
            gap.put("requirement", req);
            // R93 性能修复：用前面构建的 firstByDesc 查父级（避免再次 selectList）
            RequirementAncestor first = firstByDesc.get(req.getId());
            if (first != null) {
                gap.put("parent", orphanParents.get(first.getAncestorId()));
            } else {
                gap.put("expectedParentType", getExpectedParentType(req.getRequirementType()));
            }
            gap.put("message", req.getRequirementType() + " 需求编号 " + req.getRequirementNo() + " 缺少上层追溯");
            gaps.add(gap);
        }
        for (Requirement req : findWithoutTestCase(projectId)) {
            Map<String, Object> gap = new LinkedHashMap<>();
            gap.put("type", "NO_TEST_CASE");
            gap.put("requirement", req);
            gap.put("message", req.getRequirementType() + " 需求编号 " + req.getRequirementNo() + " 缺少测试用例追溯");
            gaps.add(gap);
        }

        return gaps;
    }

    // ===== v1.47 BUG #134 P0 修复：TraceLink 9 个 CRUD 端点支撑方法 =====

    /**
     * 创建追溯链接（带无环校验 + 领域事件）
     * v1.47 BUG #135 P0 修复：无环校验
     * v1.47 BUG #137 P0 修复：领域事件
     */
    @Transactional
    public TraceLink createTraceLink(TraceLink link) {
        if (link.getLinkType() == null) {
            throw BusinessException.param("linkType 不能为空");
        }
        if (link.getSourceId() == null || link.getTargetId() == null) {
            throw BusinessException.param("sourceId 和 targetId 不能为空");
        }
        if (link.getSourceId().equals(link.getTargetId())) {
            throw BusinessException.param("源和目标不能相同");
        }
        // 无环校验（仅对纵向 DECOMPOSE/REFINES 生效）
        if (TraceLink.TYPE_DECOMPOSE.equals(link.getLinkType())
                || TraceLink.TYPE_REFINES.equals(link.getLinkType())) {
            if (wouldCreateCycle(link.getSourceId(), link.getTargetId())) {
                throw BusinessException.stateConflict("添加此关系会形成追溯环（" + link.getSourceId() + "→...→" + link.getTargetId() + "→" + link.getSourceId() + "）");
            }
        }
        // 重复检查
        if (traceLinkMapper.countExists(link.getSourceId(), link.getTargetId(), link.getLinkType()) > 0) {
            throw BusinessException.stateConflict("已存在相同追溯链接");
        }
        // 补全 source_no / target_no / project_id / createdBy
        if (link.getSourceNo() == null) {
            link.setSourceNo(resolveNo(link.getSourceType(), link.getSourceId()));
        }
        if (link.getTargetNo() == null) {
            link.setTargetNo(resolveNo(link.getTargetType(), link.getTargetId()));
        }
        if (link.getProjectId() == null) {
            Requirement r = requirementMapper.selectById(link.getSourceId());
            if (r != null) link.setProjectId(r.getProjectId());
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (link.getCreatedBy() == null) link.setCreatedBy(currentUserId);
        link.setCreatedAt(java.time.LocalDateTime.now());
        link.setIsDeleted(false);
        traceLinkMapper.insert(link);
        log.info("创建追溯链接: id={}, type={}, source={}->target={}",
                link.getId(), link.getLinkType(), link.getSourceId(), link.getTargetId());

        // 领域事件
        safeOutbox("TraceLinkCreated", "TraceLink", link.getId(), Map.of(
                "linkType", link.getLinkType(),
                "sourceId", link.getSourceId(),
                "targetId", link.getTargetId(),
                "projectId", link.getProjectId() == null ? 0 : link.getProjectId()));
        return link;
    }

    /**
     * BUG #135 修复：DFS 检测加新边 source→target 是否会形成环
     * 即判断 target 能否通过现有 TraceLink 反向到达 source
     */
    public boolean wouldCreateCycle(Long sourceId, Long targetId) {
        if (Objects.equals(sourceId, targetId)) return true;
        Set<Long> visited = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(targetId);
        // 反向遍历：从 target 看能否通过已有 traceLink 到达 source
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (!visited.add(cur)) continue;
            if (Objects.equals(cur, sourceId)) return true;
            // cur 的入边（指向 cur 的 link）
            List<TraceLink> incoming = traceLinkMapper.selectByTargetId(cur);
            for (TraceLink t : incoming) {
                if (t.getSourceId() != null) stack.push(t.getSourceId());
            }
            // 还要查闭包表关系（拆解是闭包表维护的祖先-子孙关系）
            List<RequirementAncestor> ancestors = ancestorMapper.selectList(
                    new LambdaQueryWrapper<RequirementAncestor>()
                            .eq(RequirementAncestor::getDescendantId, cur)
                            .gt(RequirementAncestor::getDepth, 0));
            for (RequirementAncestor a : ancestors) {
                if (a.getAncestorId() != null) stack.push(a.getAncestorId());
            }
        }
        return false;
    }

    @Transactional
    public boolean updateTraceLink(Long id, TraceLink patch) {
        TraceLink existing = traceLinkMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.notFound("TRACE_NOT_FOUND", "追溯链接不存在");
        }
        if (patch.getTraceContext() != null) existing.setTraceContext(patch.getTraceContext());
        if (patch.getLinkType() != null) existing.setLinkType(patch.getLinkType());
        int rows = traceLinkMapper.updateById(existing);
        safeOutbox("TraceLinkUpdated", "TraceLink", id, Map.of("patch", patch.toString()));
        return rows > 0;
    }

    @Transactional
    public boolean deleteTraceLink(Long id) {
        TraceLink existing = traceLinkMapper.selectById(id);
        if (existing == null) return false;
        existing.setIsDeleted(true);
        traceLinkMapper.updateById(existing);
        safeOutbox("TraceLinkDeleted", "TraceLink", id, Map.of(
                "linkType", existing.getLinkType(),
                "sourceId", existing.getSourceId(),
                "targetId", existing.getTargetId()));
        log.info("软删除追溯链接: id={}", id);
        return true;
    }

    public TraceLink getTraceLinkById(Long id) {
        return traceLinkMapper.selectById(id);
    }

    public List<TraceLink> listTraceLinks(Long projectId, String linkType) {
        if (linkType != null && !linkType.isEmpty()) {
            return traceLinkMapper.selectByProjectAndType(projectId, linkType);
        }
        return traceLinkMapper.selectByProject(projectId);
    }

    public List<TraceLink> listBySource(Long sourceId) {
        return traceLinkMapper.selectBySourceId(sourceId);
    }

    public List<TraceLink> listByTarget(Long targetId) {
        return traceLinkMapper.selectByTargetId(targetId);
    }

    // v1.55 修复：按 (source, target) 对查询 TraceLink
    public List<TraceLink> listByPair(Long sourceId, Long targetId) {
        return traceLinkMapper.selectByPair(sourceId, targetId);
    }

    // ===== v1.55 修复：追溯数据导入（preview + commit） =====

    /**
     * 预览/校验：按行校验 source/target 编号是否存在，linkType 是否合法
     * 返回：validRows（可导入）、invalidRows（带错误信息）、validCount、invalidCount
     */
    public Map<String, Object> previewImport(Long projectId, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            result.put("validCount", 0);
            result.put("invalidCount", 0);
            result.put("validRows", List.of());
            result.put("invalidRows", List.of());
            return result;
        }

        // 预加载本项目所有需求 + 测试用例
        Map<String, Long> reqNoToId = new HashMap<>();
        for (Requirement r : requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
                        .eq(Requirement::getIsDeleted, false))) {
            if (r.getRequirementNo() != null) reqNoToId.put(r.getRequirementNo(), r.getId());
        }
        Map<String, Long> tcNoToId = new HashMap<>();
        for (TestCase tc : tcMapper.selectList(null)) {
            if (tc.getTestCaseNo() != null) tcNoToId.put(tc.getTestCaseNo(), tc.getId());
        }

        Set<String> validLinkTypes = Set.of(
                TraceLink.TYPE_DECOMPOSE, TraceLink.TYPE_REFINES,
                TraceLink.TYPE_DEPENDS, TraceLink.TYPE_CONFLICTS,
                TraceLink.TYPE_REUSES, TraceLink.TYPE_VERIFIES);

        List<Map<String, Object>> validRows = new ArrayList<>();
        List<Map<String, Object>> invalidRows = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> item : items) {
            idx++;
            List<String> errors = new ArrayList<>();
            String sourceNo = strVal(item.get("sourceNo"));
            String sourceType = strVal(item.get("sourceType"));
            String targetNo = strVal(item.get("targetNo"));
            String targetType = strVal(item.get("targetType"));
            String linkType = strVal(item.get("linkType"));

            if (sourceNo == null) errors.add("源编号缺失");
            if (targetNo == null) errors.add("目标编号缺失");
            if (linkType == null || !validLinkTypes.contains(linkType)) {
                errors.add("追溯类型不合法: " + linkType);
            }

            Long sourceId = null, targetId = null;
            if ("TEST_CASE".equalsIgnoreCase(sourceType)) {
                sourceId = tcNoToId.get(sourceNo);
                if (sourceId == null && sourceNo != null) errors.add("源测试用例不存在: " + sourceNo);
            } else {
                sourceId = reqNoToId.get(sourceNo);
                if (sourceId == null && sourceNo != null) errors.add("源需求不存在: " + sourceNo);
            }
            if ("TEST_CASE".equalsIgnoreCase(targetType)) {
                targetId = tcNoToId.get(targetNo);
                if (targetId == null && targetNo != null) errors.add("目标测试用例不存在: " + targetNo);
            } else {
                targetId = reqNoToId.get(targetNo);
                if (targetId == null && targetNo != null) errors.add("目标需求不存在: " + targetNo);
            }

            if (sourceId != null && targetId != null && sourceId.equals(targetId)) {
                errors.add("源和目标不能相同");
            }

            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("rowIndex", idx);
            row.put("errors", errors);
            if (errors.isEmpty()) {
                validRows.add(row);
            } else {
                invalidRows.add(row);
            }
        }

        result.put("validCount", validRows.size());
        result.put("invalidCount", invalidRows.size());
        result.put("validRows", validRows);
        result.put("invalidRows", invalidRows);
        return result;
    }

    /**
     * 批量提交：循环调 createTraceLink（自带无环校验 + 审计日志 + 领域事件）
     * 返回：success 成功条数 / failed 失败条数 / errors 失败原因列表
     */
    @Transactional
    public Map<String, Object> importBatch(Long projectId, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            result.put("success", 0);
            result.put("failed", 0);
            result.put("errors", List.of());
            return result;
        }
        int success = 0, failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (Map<String, Object> item : items) {
            try {
                String linkType = strVal(item.get("linkType"));
                String sourceType = strVal(item.get("sourceType"));
                String targetType = strVal(item.get("targetType"));
                String sourceNo = strVal(item.get("sourceNo"));
                String targetNo = strVal(item.get("targetNo"));

                // 解析 sourceNo → sourceId（v2.1 BUG 修复：原代码只 setSourceNo，createTraceLink 校验 sourceId 必填）
                Long sourceId = resolveNoToId(sourceType, sourceNo);
                Long targetId = resolveNoToId(targetType, targetNo);
                if (sourceId == null) {
                    throw BusinessException.notFound("TR_IMP_001", "源编号不存在: " + sourceNo);
                }
                if (targetId == null) {
                    throw BusinessException.notFound("TR_IMP_002", "目标编号不存在: " + targetNo);
                }

                TraceLink link = new TraceLink();
                link.setLinkType(linkType);
                link.setSourceType("TEST_CASE".equalsIgnoreCase(sourceType) ? "TEST_CASE" : "REQUIREMENT");
                link.setSourceId(sourceId);
                link.setSourceNo(sourceNo);
                link.setTargetType("TEST_CASE".equalsIgnoreCase(targetType) ? "TEST_CASE" : "REQUIREMENT");
                link.setTargetId(targetId);
                link.setTargetNo(targetNo);
                link.setProjectId(projectId);
                link.setTraceContext(strVal(item.get("comment")));
                createTraceLink(link);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("sourceNo", item.get("sourceNo"));
                err.put("targetNo", item.get("targetNo"));
                err.put("message", e.getMessage());
                errors.add(err);
            }
        }
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    /**
     * v2.1 修复：importBatch 解析 No → Id
     * TEST_CASE 类型走 tcMapper；其他走 requirementMapper
     */
    private Long resolveNoToId(String type, String no) {
        if (no == null) return null;
        if ("TEST_CASE".equalsIgnoreCase(type)) {
            TestCase tc = tcMapper.selectOne(new LambdaQueryWrapper<TestCase>()
                    .eq(TestCase::getTestCaseNo, no).last("LIMIT 1"));
            return tc == null ? null : tc.getId();
        }
        Requirement r = requirementMapper.selectOne(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getRequirementNo, no).last("LIMIT 1"));
        return r == null ? null : r.getId();
    }

    private static String strVal(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    // ===== v1.55 修复：追溯缺口忽略 =====

    /**
     * 忽略一个追溯缺口（去重插入）
     */
    @Transactional
    public boolean ignoreGap(Long projectId, String gapType, Long requirementId, String reason) {
        if (projectId == null || gapType == null || requirementId == null) {
            throw BusinessException.param("projectId/gapType/requirementId 必填");
        }
        // 去重：已忽略则直接返回 true
        TraceGapIgnored existing = gapIgnoredMapper.findUnique(projectId, gapType, requirementId);
        if (existing != null) return true;

        TraceGapIgnored rec = new TraceGapIgnored();
        rec.setProjectId(projectId);
        rec.setGapType(gapType);
        rec.setRequirementId(requirementId);
        rec.setReason(reason);
        rec.setIgnoredBy(SecurityUtils.getCurrentUserId());
        rec.setIgnoredAt(java.time.LocalDateTime.now());
        gapIgnoredMapper.insert(rec);
        safeOutbox("TraceGapIgnored", "TraceGap", rec.getId(), Map.of(
                "projectId", projectId,
                "gapType", gapType,
                "requirementId", requirementId));
        return true;
    }

    /**
     * 获取项目下所有已忽略的缺口（去重后：同一 (gapType, requirementId) 只算一次）
     */
    public List<Long> getIgnoredGapIds(Long projectId) {
        List<TraceGapIgnored> records = gapIgnoredMapper.selectByProject(projectId);
        // 前端传过来的"缺口 ID"实际是 requirementId + gapType 的复合键
        // 简化处理：返回所有 requirementId，前端按 (gapType, requirementId) 二次过滤
        return records.stream().map(TraceGapIgnored::getRequirementId).distinct().toList();
    }

    /**
     * 获取项目下所有已忽略的缺口完整记录
     */
    public List<Map<String, Object>> getIgnoredGapRecords(Long projectId) {
        List<TraceGapIgnored> records = gapIgnoredMapper.selectByProject(projectId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TraceGapIgnored r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gapType", r.getGapType());
            m.put("requirementId", r.getRequirementId());
            m.put("reason", r.getReason());
            m.put("ignoredAt", r.getIgnoredAt());
            result.add(m);
        }
        return result;
    }

    /**
     * 安全发 outbox 事件（不影响主流程）
     */
    private void safeOutbox(String eventType, String aggregateType, Long aggregateId, Map<String, Object> payload) {
        try {
            outboxService.append(eventType, aggregateType, aggregateId, new java.util.HashMap<>(payload));
        } catch (Exception e) {
            log.warn("发 outbox 事件失败: type={}, err={}", eventType, e.getMessage());
        }
    }

    private String resolveNo(String type, Long id) {
        if (id == null) return null;
        if ("TEST_CASE".equalsIgnoreCase(type)) {
            TestCase tc = tcMapper.selectById(id);
            return tc != null ? tc.getTestCaseNo() : null;
        }
        Requirement r = requirementMapper.selectById(id);
        return r != null ? r.getRequirementNo() : null;
    }

    // ===== 老 API 保留（向后兼容）=====

    public RequirementRelation addHorizontalRelation(Long sourceReqId, Long targetReqId, String relationType) {
        // 改走 TraceLink（DEPENDS/CONFLICTS/REUSES）
        String mappedType = switch (relationType) {
            case "DEPENDS" -> TraceLink.TYPE_DEPENDS;
            case "CONFLICTS" -> TraceLink.TYPE_CONFLICTS;
            case "REUSES" -> TraceLink.TYPE_REUSES;
            case "HORIZONTAL" -> TraceLink.TYPE_DEPENDS;
            default -> relationType;
        };
        TraceLink link = new TraceLink();
        link.setLinkType(mappedType);
        link.setSourceType("REQUIREMENT");
        link.setSourceId(sourceReqId);
        link.setTargetType("REQUIREMENT");
        link.setTargetId(targetReqId);
        createTraceLink(link);
        // 同步写回老表（如果调用方依赖）
        RequirementRelation rel = new RequirementRelation();
        rel.setSourceReqId(sourceReqId);
        rel.setTargetReqId(targetReqId);
        rel.setRelationType(relationType);
        rel.setCreatedAt(java.time.LocalDateTime.now());
        relationMapper.insert(rel);
        return rel;
    }

    public RequirementTestCase addTestCaseTrace(Long requirementId, Long testCaseId, String traceType) {
        // 改走 TraceLink VERIFIES
        TraceLink link = new TraceLink();
        link.setLinkType(TraceLink.TYPE_VERIFIES);
        link.setSourceType("REQUIREMENT");
        link.setSourceId(requirementId);
        link.setTargetType("TEST_CASE");
        link.setTargetId(testCaseId);
        link.setTraceContext(traceType);
        createTraceLink(link);
        RequirementTestCase trace = new RequirementTestCase();
        trace.setRequirementId(requirementId);
        trace.setTestCaseId(testCaseId);
        trace.setTraceType(traceType);
        trace.setCreatedAt(java.time.LocalDateTime.now());
        testCaseMapper.insert(trace);
        return trace;
    }

    public List<Map<String, Object>> getTraceBreakages(Long projectId) {
        // 直接复用 getTraceGaps，但加 gapType 字段
        List<Map<String, Object>> breakages = new ArrayList<>();
        for (Map<String, Object> g : getTraceGaps(projectId)) {
            g.put("gapType", g.get("type"));
            breakages.add(g);
        }

        if (!breakages.isEmpty()) {
            try {
                Map<String, Object> first = breakages.get(0);
                Long reqId = ((Number) first.get("requirementId") != null)
                        ? ((Number) first.get("requirementId")).longValue() : null;
                String reqNo = (String) first.get("requirementNo");
                if (reqId == null && first.get("requirement") instanceof Requirement r) reqId = r.getId();
                if (reqNo == null && first.get("requirement") instanceof Requirement r2) reqNo = r2.getRequirementNo();
                if (reqId != null) {
                    Requirement req = requirementMapper.selectById(reqId);
                    Long ownerId = req != null && req.getCreatedBy() != null ? req.getCreatedBy() : 1L;
                    notificationService.sendTraceBrokenNotification(ownerId, reqId, reqNo);
                }
            } catch (Exception e) {
                log.warn("发送追溯断裂通知失败: err={}", e.getMessage());
            }
        }

        return breakages;
    }

    private List<Requirement> getChildren(Long parentId, String childType) {
        LambdaQueryWrapper<RequirementAncestor> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RequirementAncestor::getAncestorId, parentId)
               .eq(RequirementAncestor::getDepth, 1);
        List<RequirementAncestor> ancestors = ancestorMapper.selectList(wrapper);
        if (ancestors.isEmpty()) return new ArrayList<>();

        List<Long> childIds = ancestors.stream().map(RequirementAncestor::getDescendantId).toList();
        LambdaQueryWrapper<Requirement> reqWrapper = new LambdaQueryWrapper<>();
        reqWrapper.in(Requirement::getId, childIds)
                  .eq(Requirement::getRequirementType, childType)
                  .eq(Requirement::getIsDeleted, false);
        return requirementMapper.selectList(reqWrapper);
    }

    private Map<String, Object> getLevelCoverage(Long projectId, String type) {
        Map<String, Object> coverage = new LinkedHashMap<>();
        // R90 修复：原 .eq(field, null) 在 MyBatis-Plus 中产生 WHERE field = null → 永远 0 行
        // 改为 .eq(projectId != null, ...) → projectId 为 null 时不附加条件
        long total = requirementMapper.selectCount(new LambdaQueryWrapper<Requirement>()
                .eq(projectId != null, Requirement::getProjectId, projectId)
                .eq(Requirement::getRequirementType, type)
                .eq(Requirement::getIsDeleted, false));
        String childType = getNextType(type);
        long traced = (childType == null) ? 0 : countWithChildren(projectId, type, childType);
        coverage.put("total", total);
        coverage.put("traced", traced);
        coverage.put("untraced", total - traced);
        coverage.put("coverageRate", total > 0 ? (traced * 100 / total) : 0);
        return coverage;
    }

    /**
     * v1.47 BUG #138 P0 修复：SQL 注入修复
     * 原代码使用 inSql + 字符串拼接 childType，改为 EXISTS 子查询参数化
     */
    private long countWithChildren(Long projectId, String parentType, String childType) {
        // R90 修复：原 .eq(field, null) → WHERE field = null → 永远 0 行
        return requirementMapper.selectCount(new LambdaQueryWrapper<Requirement>()
                .eq(projectId != null, Requirement::getProjectId, projectId)
                .eq(Requirement::getRequirementType, parentType)
                .eq(Requirement::getIsDeleted, false)
                .and(w -> w.exists("SELECT 1 FROM req_schema.t_requirement_ancestor ra " +
                        "JOIN req_schema.t_requirement r2 ON r2.id = ra.descendant_id " +
                        "WHERE ra.ancestor_id = req_schema.t_requirement.id " +
                        "AND ra.depth = 1 AND r2.requirement_type = {0}", childType)));
    }

    private String getNextType(String type) {
        return switch (type) {
            case "URS" -> "PRS";
            case "PRS" -> "SRS";
            case "SRS" -> "DRS";
            default -> null;
        };
    }

    private String getExpectedParentType(String type) {
        return switch (type) {
            case "PRS" -> "URS";
            case "SRS" -> "PRS";
            case "DRS" -> "SRS";
            default -> null;
        };
    }

    private List<Requirement> findShouldHaveChildren(Long projectId) {
        // R93 性能修复：原 N+1（每个 req 都 getChildren 一次）
        // 改为：一次查所有 Approved 需求 + 一次查所有 ancestors，在内存中判断
        List<Requirement> result = new ArrayList<>();
        Map<String, String> nextTypeMap = Map.of("URS", "PRS", "PRS", "SRS", "SRS", "DRS");
        // 一次查所有可能需要子级的 Approved 需求
        List<Requirement> approvedReqs = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eq(Requirement::getStatus, "Approved")
                .eq(Requirement::getIsDeleted, false)
                .in(Requirement::getRequirementType, List.of("URS", "PRS", "SRS")));
        if (approvedReqs.isEmpty()) return result;
        // 批量查所有这些需求的子级关系
        List<Long> approvedIds = new ArrayList<>();
        for (Requirement r : approvedReqs) approvedIds.add(r.getId());
        List<RequirementAncestor> allAncestors = ancestorMapper.selectList(
                new LambdaQueryWrapper<RequirementAncestor>()
                        .in(RequirementAncestor::getAncestorId, approvedIds)
                        .eq(RequirementAncestor::getDepth, 1));
        // 收集所有子级 id
        Set<Long> childIds = new HashSet<>();
        for (RequirementAncestor a : allAncestors) childIds.add(a.getDescendantId());
        // 批量查子级类型
        Map<Long, String> childTypeMap = new HashMap<>();
        if (!childIds.isEmpty()) {
            List<Requirement> children = requirementMapper.selectBatchIds(childIds);
            for (Requirement c : children) childTypeMap.put(c.getId(), c.getRequirementType());
        }
        // 内存判断每个 Approved 需求是否缺少期望子级
        for (Requirement req : approvedReqs) {
            String expectedChild = nextTypeMap.get(req.getRequirementType());
            if (expectedChild == null) continue;
            boolean hasChild = false;
            for (RequirementAncestor a : allAncestors) {
                if (a.getAncestorId().equals(req.getId())) {
                    String ct = childTypeMap.get(a.getDescendantId());
                    if (expectedChild.equals(ct)) { hasChild = true; break; }
                }
            }
            if (!hasChild) result.add(req);
        }
        return result;
    }

    private List<Requirement> findOrphanRequirements(Long projectId) {
        // R93 性能修复：原 N+1（每个 req 都 ancestorMapper.selectList）
        // 改为：一次查所有 PRS+SRS+DRS + 一次查所有 ancestors
        List<Requirement> result = new ArrayList<>();
        List<Requirement> reqs = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eq(Requirement::getIsDeleted, false)
                .in(Requirement::getRequirementType, List.of("PRS", "SRS", "DRS")));
        if (reqs.isEmpty()) return result;
        List<Long> reqIds = new ArrayList<>();
        for (Requirement r : reqs) reqIds.add(r.getId());
        List<RequirementAncestor> allAncestors = ancestorMapper.selectList(
                new LambdaQueryWrapper<RequirementAncestor>()
                        .in(RequirementAncestor::getDescendantId, reqIds)
                        .gt(RequirementAncestor::getDepth, 0));
        Set<Long> hasAncestor = new HashSet<>();
        for (RequirementAncestor a : allAncestors) hasAncestor.add(a.getDescendantId());
        for (Requirement r : reqs) {
            if (!hasAncestor.contains(r.getId())) result.add(r);
        }
        return result;
    }

    private List<Requirement> findWithoutTestCase(Long projectId) {
        // R93 性能修复：原 N+1（每个 req 都 testCaseMapper.selectByRequirementId）
        // 改为：一次查所有 SRS+DRS（非 Draft）+ 一次查所有关联
        List<Requirement> result = new ArrayList<>();
        List<Requirement> reqs = requirementMapper.selectList(new LambdaQueryWrapper<Requirement>()
                .eq(Requirement::getProjectId, projectId)
                .eq(Requirement::getIsDeleted, false)
                .ne(Requirement::getStatus, "Draft")
                .in(Requirement::getRequirementType, List.of("SRS", "DRS")));
        if (reqs.isEmpty()) return result;
        List<Long> reqIds = new ArrayList<>();
        for (Requirement r : reqs) reqIds.add(r.getId());
        List<RequirementTestCase> allLinks = testCaseMapper.selectList(
                new LambdaQueryWrapper<RequirementTestCase>()
                        .in(RequirementTestCase::getRequirementId, reqIds));
        Set<Long> hasTc = new HashSet<>();
        for (RequirementTestCase t : allLinks) hasTc.add(t.getRequirementId());
        for (Requirement r : reqs) {
            if (!hasTc.contains(r.getId())) result.add(r);
        }
        return result;
    }
}
