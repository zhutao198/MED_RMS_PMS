package com.zhutao.medrms.requirement.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class AIService {

    public Map<String, Object> analyzeRequirement(String title, String description, String acceptanceCriteria,
                                                   String requirementType, String priority, String safetyClass) {
        List<Map<String, Object>> issues = new ArrayList<>();
        int score = 100;

        if (title == null || title.trim().isEmpty()) {
            issues.add(Map.of("type", "error", "field", "title", "message", "需求标题不能为空"));
            score -= 20;
        } else if (title.length() < 5) {
            issues.add(Map.of("type", "warning", "field", "title", "message", "需求标题过短（建议至少5个字符）"));
            score -= 5;
        }

        if (title != null) {
            boolean hasShall = title.toLowerCase().contains("shall") || title.toLowerCase().contains("必须");
            boolean hasShould = title.toLowerCase().contains("should") || title.toLowerCase().contains("应");
            if (!hasShall && !hasShould) {
                issues.add(Map.of("type", "info", "field", "title", "message", "建议使用\"必须\"/\"应\"等规范性措辞"));
                score -= 3;
            }
        }

        if (description == null || description.trim().isEmpty()) {
            issues.add(Map.of("type", "error", "field", "description", "message", "需求描述不能为空"));
            score -= 25;
        } else if (description.length() < 20) {
            issues.add(Map.of("type", "warning", "field", "description", "message", "需求描述过于简略（建议至少20个字符）"));
            score -= 10;
        }

        if (acceptanceCriteria == null || acceptanceCriteria.trim().isEmpty()) {
            issues.add(Map.of("type", "warning", "field", "acceptanceCriteria", "message", "缺少验收标准"));
            score -= 15;
        }

        if (safetyClass == null || safetyClass.trim().isEmpty()) {
            issues.add(Map.of("type", "warning", "field", "safetyClass", "message", "未选择安全分级"));
            score -= 5;
        }

        if (priority == null || priority.trim().isEmpty()) {
            issues.add(Map.of("type", "info", "field", "priority", "message", "未设置优先级"));
            score -= 2;
        }

        if (requirementType == null || requirementType.trim().isEmpty()) {
            issues.add(Map.of("type", "info", "field", "requirementType", "message", "未指定需求类型"));
            score -= 2;
        }

        score = Math.max(0, Math.min(100, score));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("grade", score >= 90 ? "A" : score >= 70 ? "B" : score >= 50 ? "C" : "D");
        result.put("issues", issues);
        result.put("summary", issues.size() + " 个待改进项，评分 " + score + "/100");
        return result;
    }

    public Map<String, Object> computeSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return Map.of("similarity", 0.0, "message", "标题不能为空");
        }
        Set<String> set1 = new HashSet<>(Arrays.asList(title1.toLowerCase().split("[\\s,，。.、]+")));
        Set<String> set2 = new HashSet<>(Arrays.asList(title2.toLowerCase().split("[\\s,，。.、]+")));

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        double jaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

        String message;
        if (jaccard > 0.8) message = "高度相似（可能重复）";
        else if (jaccard > 0.5) message = "中度相似";
        else if (jaccard > 0.2) message = "轻度相似";
        else message = "基本不相似";

        return Map.of("similarity", Math.round(jaccard * 100.0) / 100.0, "message", message);
    }
}
