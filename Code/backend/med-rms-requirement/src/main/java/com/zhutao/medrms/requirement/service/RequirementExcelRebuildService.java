package com.zhutao.medrms.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementAncestor;
import com.zhutao.medrms.requirement.mapper.RequirementAncestorMapper;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * R208.2: Excel 导入后追溯闭包表重建服务（FR-1.13 增强）
 *
 * 仅重建 t_requirement_ancestor 闭包表（requirement 模块内操作）：
 *  1. 上游编号 → 上游 ID 映射（requirementMapper.selectByRequirementNo）
 *  2. 写 (child, parent, depth=1) 直系父闭包
 *  3. 递归闭包：child → parent 的所有祖先 depth=d+1
 *
 * TraceLink 重建由 traceability 模块的 TraceabilityService.rebuildFromImport 负责（独立调用链，
 * 避免 requirement ↔ traceability 循环依赖，CLAUDE.md 项目铁律）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementExcelRebuildService {

    private final RequirementMapper requirementMapper;
    private final RequirementAncestorMapper ancestorMapper;

    /**
     * 重建导入需求的 ancestor 闭包表
     *
     * @param created      刚入库的需求列表（含自引用闭包 depth=0，由 createRequirement 自动写入）
     * @param upstreamMap 按 created 索引对齐的上游 requirementNo 列表
     * @return 重建统计 {ancestorOk, ancestorSkip, ancestorError, errors}
     */
    @Transactional
    public Map<String, Object> rebuildAncestorFromImport(
            List<Requirement> created, Map<Integer, List<String>> upstreamMap) {
        log.info("R208.2 ancestor 闭包重建: created={}, upstreamMap={}",
                created == null ? 0 : created.size(),
                upstreamMap == null ? 0 : upstreamMap.size());

        Map<String, Object> result = new LinkedHashMap<>();
        if (created == null || created.isEmpty() || upstreamMap == null || upstreamMap.isEmpty()) {
            result.put("ancestorOk", 0);
            result.put("ancestorSkip", 0);
            result.put("ancestorError", 0);
            result.put("errors", List.of());
            return result;
        }

        // 1. 收集所有上游编号 → 查 ID
        Set<String> upstreamNos = new HashSet<>();
        for (List<String> nos : upstreamMap.values()) {
            if (nos != null) upstreamNos.addAll(nos);
        }
        Map<String, Long> noToId = new HashMap<>();
        for (String no : upstreamNos) {
            if (no == null || no.isBlank()) continue;
            try {
                Requirement r = requirementMapper.selectByRequirementNo(no.trim());
                if (r != null) noToId.put(no.trim(), r.getId());
            } catch (Exception e) {
                log.debug("R208.2 查上游编号 {} 失败: {}", no, e.getMessage());
            }
        }

        int ok = 0, skip = 0, err = 0;
        List<String> errors = new ArrayList<>();

        // 2. 遍历每条创建的需求，写 (child→parent, depth=1) + (child→parent的祖先, depth=d+1)
        for (int i = 0; i < created.size(); i++) {
            Requirement child = created.get(i);
            if (child == null || child.getId() == null) continue;
            List<String> ups = upstreamMap.get(i);
            if (ups == null || ups.isEmpty()) {
                skip++;
                continue;
            }
            for (String upNo : ups) {
                if (upNo == null || upNo.isBlank()) continue;
                Long upId = noToId.get(upNo.trim());
                if (upId == null) {
                    skip++;
                    log.debug("R208.2 上游编号 {} 不存在，跳过", upNo);
                    continue;
                }
                try {
                    // 2a. 直系父闭包 depth=1
                    insertAncestorSafe(child.getId(), upId, 1);
                    // 2b. 递归闭包：parent 的所有祖先（含 parent 自身 depth=0 + 其祖先 depth=d）
                    List<RequirementAncestor> parentAncestors = ancestorMapper.selectByDescendant(upId);
                    if (parentAncestors != null) {
                        for (RequirementAncestor pa : parentAncestors) {
                            if (pa.getAncestorId() == null) continue;
                            insertAncestorSafe(child.getId(), pa.getAncestorId(),
                                    pa.getDepth() + 1);
                        }
                    }
                    ok++;
                } catch (Exception e) {
                    err++;
                    errors.add("row " + (i + 2) + " upstream=" + upNo + ": " + e.getMessage());
                    log.warn("R208.2 ancestor 重建失败: child={}, upstream={}, err={}",
                            child.getRequirementNo(), upNo, e.getMessage());
                }
            }
        }

        result.put("ancestorOk", ok);
        result.put("ancestorSkip", skip);
        result.put("ancestorError", err);
        result.put("errors", errors);
        result.put("upstreamResolved", noToId.size());
        log.info("R208.2 ancestor 重建完成: ok={}, skip={}, error={}",
                ok, skip, err);
        return result;
    }

    /**
     * 安全插入 ancestor（重复时跳过，避免破坏已有闭包表）
     */
    private void insertAncestorSafe(Long descendantId, Long ancestorId, Integer depth) {
        try {
            if (ancestorMapper.selectByPair(ancestorId, descendantId) != null) return;
        } catch (Exception ignore) { /* 不存在则插入 */ }
        RequirementAncestor ra = new RequirementAncestor();
        ra.setDescendantId(descendantId);
        ra.setAncestorId(ancestorId);
        ra.setDepth(depth);
        ancestorMapper.insert(ra);
    }
}
