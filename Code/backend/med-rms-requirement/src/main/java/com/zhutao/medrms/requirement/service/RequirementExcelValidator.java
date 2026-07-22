package com.zhutao.medrms.requirement.service;

import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * R208 v1.65: Excel 行数据校验服务（FR-1.13）
 *
 * 必填字段按 FR-0.6（PR-RED 验收）：
 *  - title, description, priority, riskLevel, safetyClass, projectId, requirementType
 *  - acceptanceCriteria（URS 必填，PRD §7.1.2）
 *
 * 枚举校验：priority / riskLevel / safetyClass / source / requirementCategory
 * 类型转换：productId / assigneeId（String → Long）
 */
@Slf4j
@Service
public class RequirementExcelValidator {

    private static final Set<String> PRIORITIES = Set.of("MUST", "SHOULD", "COULD", "WONT");
    private static final Set<String> RISK_LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> SAFETY_CLASSES = Set.of("A", "B", "C");
    private static final Set<String> REQUIREMENT_CATEGORIES = Set.of("SOFTWARE", "HARDWARE", "BOTH");
    private static final Set<String> SOURCES = Set.of("CUSTOMER", "MARKET", "REGULATION", "INTERNAL", "COMPETITOR");
    private static final Set<String> TEST_STRATEGIES = Set.of("UNIT", "INTEGRATION", "SYSTEM", "VERIFICATION");

    /**
     * 校验单行 → 生成 Requirement 实体 + 错误信息
     *
     * @param rowIndex  Excel 行号（从 2 开始，跳过表头）
     * @param row       Excel 行数据
     * @param projectId 必填项目 ID（前端传入）
     * @param type      必填层级（URS/PRS/SRS/DRS）
     * @return 校验结果
     */
    public ValidationResult validate(int rowIndex, Map<String, String> row, Long projectId, String type) {
        List<String> errors = new ArrayList<>();

        // 必填：title
        String title = row.getOrDefault("title", "").trim();
        if (title.isEmpty()) errors.add("title 不能为空");

        // 必填：description
        String description = row.getOrDefault("description", "").trim();
        if (description.isEmpty()) errors.add("description 不能为空");
        else if (description.length() < 20) errors.add("description 至少 20 字（当前 " + description.length() + "）");

        // 必填 + 枚举：priority
        String priority = row.getOrDefault("priority", "").trim().toUpperCase();
        if (priority.isEmpty()) errors.add("priority 不能为空");
        else if (!PRIORITIES.contains(priority)) errors.add("priority 必须是 " + PRIORITIES);

        // 必填 + 枚举：riskLevel
        String riskLevel = row.getOrDefault("riskLevel", "").trim().toUpperCase();
        if (riskLevel.isEmpty()) errors.add("riskLevel 不能为空");
        else if (!RISK_LEVELS.contains(riskLevel)) errors.add("riskLevel 必须是 " + RISK_LEVELS);

        // 必填 + 枚举：safetyClass（R202 IEC 62304）
        String safetyClass = row.getOrDefault("safetyClass", "").trim().toUpperCase();
        if (safetyClass.isEmpty()) errors.add("safetyClass 不能为空");
        else if (!SAFETY_CLASSES.contains(safetyClass)) errors.add("safetyClass 必须是 A/B/C");

        // 必填 + 枚举：source
        String source = row.getOrDefault("source", "").trim().toUpperCase();
        if (source.isEmpty()) errors.add("source 不能为空");
        else if (!SOURCES.contains(source)) errors.add("source 必须是 " + SOURCES);

        // 可选 + 枚举：requirementCategory
        String requirementCategory = row.getOrDefault("requirementCategory", "").trim().toUpperCase();
        if (!requirementCategory.isEmpty() && !REQUIREMENT_CATEGORIES.contains(requirementCategory)) {
            errors.add("requirementCategory 必须是 " + REQUIREMENT_CATEGORIES);
        }

        // 可选 + 枚举：testStrategy（SRS 层级）
        String testStrategy = row.getOrDefault("testStrategy", "").trim().toUpperCase();
        if (!testStrategy.isEmpty() && !TEST_STRATEGIES.contains(testStrategy)) {
            errors.add("testStrategy 必须是 " + TEST_STRATEGIES);
        }

        // 必填（URS）：acceptanceCriteria
        if ("URS".equals(type)) {
            String acceptance = row.getOrDefault("acceptanceCriteria", "").trim();
            if (acceptance.isEmpty()) {
                errors.add("URS 层级 acceptanceCriteria 必填");
            }
        }

        // 类型转换：productId（String → Long，可空）
        Long productId = null;
        String productIdStr = row.getOrDefault("productId", "").trim();
        if (!productIdStr.isEmpty()) {
            try { productId = Long.parseLong(productIdStr); }
            catch (NumberFormatException e) { errors.add("productId 必须是数字（当前=" + productIdStr + "）"); }
        }

        // 类型转换：assigneeId（DRS 层级）
        Long assigneeId = null;
        String assigneeIdStr = row.getOrDefault("assigneeId", "").trim();
        if (!assigneeIdStr.isEmpty()) {
            try { assigneeId = Long.parseLong(assigneeIdStr); }
            catch (NumberFormatException e) { errors.add("assigneeId 必须是数字（当前=" + assigneeIdStr + "）"); }
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(rowIndex, null, null, null, errors);
        }

        // 构造 Requirement 实体
        Requirement r = new Requirement();
        r.setRequirementType(type);
        r.setProjectId(projectId);
        r.setProductId(productId);
        r.setTitle(title);
        r.setDescription(description);
        r.setPriority(priority);
        r.setRiskLevel(riskLevel);
        r.setSafetyClass(safetyClass);
        r.setSource(source);
        r.setSourceNo(row.getOrDefault("sourceNo", "").trim());
        r.setRequirementCategory(requirementCategory.isEmpty() ? "SOFTWARE" : requirementCategory);
        // 用户提供的 requirementNo（可选）
        String requirementNo = row.getOrDefault("requirementNo", "").trim();
        if (!requirementNo.isEmpty()) {
            r.setRequirementNo(requirementNo);
        }

        // 提取追溯关系（逗号分隔）
        String upstreamNos = row.getOrDefault("upstreamNos", "").trim();
        List<String> upstreamList = upstreamNos.isEmpty() ? List.of()
            : Arrays.stream(upstreamNos.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        return new ValidationResult(rowIndex, r, upstreamList, assigneeId, List.of());
    }

    /** 校验结果 */
    public record ValidationResult(
        int rowIndex,
        Requirement requirement,         // null = 校验失败
        List<String> upstreamNos,        // 上游追溯编号
        Long assigneeId,                 // DRS 特有
        List<String> errors              // 非空 = 校验失败
    ) {
        public boolean isValid() { return errors.isEmpty() && requirement != null; }
    }
}
