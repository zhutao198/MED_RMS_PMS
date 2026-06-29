package com.zhutao.medrms.requirement.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementVersion;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.RequirementVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v1.47 BUG #127 P0 修复：版本号改语义化字符串（v1.0 / v1.1 / v2.0）
 * 维护规则：默认情况下 minor 位 +1，重大变更时由调用方传 versionNo
 * v1.52 新增 createVersionWithCti：手动创建版本快照，入参 changeSummary 是 JSON 字符串，
 * 内含 {summary: 文本, cti: ["IEC 62304", "ISO 14971", "IEC 60601-1"] 子集}，由本方法统一打包
 * 并以当前需求主表 + CTI 子表作为 snapshot 落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementVersionService {

    private final RequirementVersionMapper versionMapper;
    private final RequirementMapper requirementMapper;

    public List<RequirementVersion> getVersionsByRequirementId(Long requirementId) {
        return versionMapper.selectByRequirementId(requirementId);
    }

    public RequirementVersion getLatestVersion(Long requirementId) {
        return versionMapper.selectLatestByRequirementId(requirementId);
    }

    @Transactional
    public RequirementVersion saveVersion(Long requirementId, String snapshot, String changeSummary, Long changedBy) {
        return saveVersion(requirementId, snapshot, changeSummary, null, null, changedBy);
    }

    @Transactional
    public RequirementVersion saveVersion(Long requirementId, String snapshot, String changeSummary,
                                          String diffSummary, String explicitVersionNo, Long changedBy) {
        RequirementVersion version = new RequirementVersion();
        version.setRequirementId(requirementId);
        version.setSnapshot(snapshot);
        version.setChangeSummary(changeSummary);
        version.setDiffSummary(diffSummary);
        version.setChangedBy(changedBy);
        version.setChangedAt(LocalDateTime.now());

        // BUG #127 修复：版本号语义化
        if (explicitVersionNo != null && !explicitVersionNo.isEmpty()) {
            version.setVersionNo(explicitVersionNo);
        } else {
            RequirementVersion latest = getLatestVersion(requirementId);
            version.setVersionNo(bumpMinor(latest == null ? null : latest.getVersionNo()));
        }

        versionMapper.insert(version);
        return version;
    }

    /**
     * v1.52 新增：手动创建需求版本（前端"新建版本"页调用）
     *
     * @param requirementId 需求ID
     * @param changeSummaryJson 前端传入的 changeSummary 字段，JSON 字符串
     *                          形如 {"summary":"...","cti":["IEC 62304", "ISO 14971", "IEC 60601-1"]}
     *                          其中 cti 数组可不传或传子集
     * @param operatorId 操作人（来自 JWT），可为 null
     * @return 新建的 RequirementVersion
     */
    @Transactional
    public RequirementVersion createVersionWithCti(Long requirementId, String changeSummaryJson, Long operatorId) {
        // 1) 校验需求存在
        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null) {
            throw BusinessException.notFound(BusinessException.REQ_0101, "需求不存在: id=" + requirementId);
        }

        // 2) 解析 changeSummary：兼容 ①顶层 JSON ②纯文本
        JSONObject summaryObj = parseChangeSummary(changeSummaryJson);
        String summaryText = summaryObj.getString("summary");
        if (summaryText == null || summaryText.isBlank()) {
            // 兜底：原文本当 summary
            summaryText = changeSummaryJson;
        }
        JSONArray ctiArr = summaryObj.getJSONArray("cti");
        if (ctiArr == null) {
            ctiArr = new JSONArray();
        }
        // 过滤非法标准（只允许白名单内的 3 个）
        JSONArray filteredCti = new JSONArray();
        for (int i = 0; i < ctiArr.size(); i++) {
            String c = ctiArr.getString(i);
            if (c == null) continue;
            String trimmed = c.trim();
            if (CTI_WHITELIST.contains(trimmed) && !filteredCti.contains(trimmed)) {
                filteredCti.add(trimmed);
            }
        }

        // 3) 拼装最终落库的 changeSummary（标准 JSON 形态，供前端展示）
        Map<String, Object> finalSummary = new LinkedHashMap<>();
        finalSummary.put("summary", summaryText);
        finalSummary.put("cti", filteredCti);
        finalSummary.put("ctiSource", "manual");
        finalSummary.put("createdAt", LocalDateTime.now().toString());
        String finalChangeSummaryJson = JSON.toJSONString(finalSummary);

        // 4) 构造 snapshot：主表 + CTI 子表 + 上一个版本号（用于追溯）
        RequirementVersion latest = getLatestVersion(requirementId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requirement", req);
        snapshot.put("ctiChecked", filteredCti);
        snapshot.put("previousVersionNo", latest == null ? null : latest.getVersionNo());
        String snapshotJson = JSON.toJSONString(snapshot);

        // 5) diffSummary 简单记一笔：CTI 标准勾选 + summary 摘要
        String diffSummary = "manual create | cti=" + filteredCti + " | summary=" + summaryText;

        // 6) 复用 saveVersion 写入；版本号走默认 minor +1 规则
        return saveVersion(requirementId, snapshotJson, finalChangeSummaryJson,
                diffSummary, null, operatorId);
    }

    /**
     * 解析 changeSummary 字符串。允许两种形态：
     *   - 顶层是 JSON（含 summary 字段）：直接返回 JSONObject
     *   - 顶层是纯文本：包成 {"summary": 原文本, "cti": []}
     */
    private JSONObject parseChangeSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return new JSONObject();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return JSON.parseObject(trimmed);
            } catch (Exception e) {
                log.warn("changeSummary 解析失败，按纯文本处理: {}", e.getMessage());
            }
        }
        JSONObject fallback = new JSONObject();
        fallback.put("summary", raw);
        fallback.put("cti", new JSONArray());
        return fallback;
    }

    /**
     * v1.52 新增：CTI 标准白名单（3 行默认勾选项）
     */
    private static final List<String> CTI_WHITELIST = List.of(
            "IEC 62304",      // 医疗器械软件生命周期
            "ISO 14971",      // 医疗器械风险管理
            "IEC 60601-1"     // 医用电气设备安全
    );

    /**
     * minor 位 +1：v1.0 -> v1.1, v1.9 -> v1.10, v2.5 -> v2.6
     * 缺省起始版本 v1.0
     */
    static String bumpMinor(String current) {
        if (current == null || current.isEmpty()) return "v1.0";
        String v = current.startsWith("v") ? current.substring(1) : current;
        int dot = v.indexOf('.');
        if (dot < 0) return "v1.0";
        try {
            int major = Integer.parseInt(v.substring(0, dot));
            int minor = Integer.parseInt(v.substring(dot + 1));
            return "v" + major + "." + (minor + 1);
        } catch (NumberFormatException e) {
            return "v1.0";
        }
    }
}
