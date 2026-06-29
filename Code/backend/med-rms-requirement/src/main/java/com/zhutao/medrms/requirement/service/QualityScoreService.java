package com.zhutao.medrms.requirement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.TestCase;
import com.zhutao.medrms.requirement.domain.entity.RequirementVersion;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.TestCaseMapper;
import com.zhutao.medrms.requirement.mapper.RequirementVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * FR-2.4 需求质量智能评分
 * 4 维度评分：完整性(25) + 一致性(25) + 可测试性(25) + 合规性(25) = 100
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityScoreService {

    private final RequirementMapper requirementMapper;
    private final TestCaseMapper testCaseMapper;
    private final RequirementVersionMapper versionMapper;

    public Map<String, Object> score(Long requirementId) {
        Requirement r = requirementMapper.selectById(requirementId);
        if (r == null) {
            return Map.of("error", "需求不存在", "requirementId", requirementId);
        }

        // 完整性
        int completeness = 0;
        List<String> completenessReasons = new ArrayList<>();
        if (notBlank(r.getTitle())) { completeness += 5; } else { completenessReasons.add("缺少标题"); }
        if (notBlank(r.getDescription())) { completeness += 6; } else { completenessReasons.add("缺少描述"); }
        if (notBlank(r.getRequirementType())) { completeness += 4; } else { completenessReasons.add("缺少类型"); }
        if (notBlank(r.getPriority())) { completeness += 4; } else { completenessReasons.add("缺少优先级"); }
        if (notBlank(r.getStatus())) { completeness += 3; } else { completenessReasons.add("缺少状态"); }
        if (r.getProjectId() != null) { completeness += 3; } else { completenessReasons.add("未关联项目"); }

        // 一致性
        int consistency = 0;
        List<String> consistencyReasons = new ArrayList<>();
        if (!Boolean.TRUE.equals(r.getIsSuspect())) { consistency += 15; } else { consistencyReasons.add("Suspect 标记未清除"); }
        if ("Approved".equals(r.getStatus()) || "Baseline".equals(r.getStatus()) || "Verified".equals(r.getStatus())) {
            consistency += 10;
        } else {
            consistencyReasons.add("需求未达 Approved 状态");
        }

        // 可测试性
        int testability = 0;
        List<String> testabilityReasons = new ArrayList<>();
        long tcCount = testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getRequirementId, requirementId));
        if (tcCount > 0) { testability += 15; } else { testabilityReasons.add("无关联测试用例"); }
        long passed = testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getRequirementId, requirementId).eq(TestCase::getStatus, "PASSED"));
        if (tcCount > 0 && passed > 0) { testability += 10; } else { testabilityReasons.add("无通过测试用例"); }

        // 合规性
        int compliance = 0;
        List<String> complianceReasons = new ArrayList<>();
        // 风险评估通过 HTTP 调用（避免循环依赖）
        long riskCount = 0;
        try {
            riskCount = countRiskViaHttp(requirementId);
        } catch (Exception e) {
            // v1.49 P2 修复：空 catch 改为 log.warn 便于诊断
            log.warn("风险评估 HTTP 调用失败: requirementId={}, err={}", requirementId, e.getMessage());
        }
        if (riskCount > 0) { compliance += 13; } else { complianceReasons.add("未做风险评估"); }
        long versionCount = versionMapper.selectCount(
                new LambdaQueryWrapper<RequirementVersion>().eq(RequirementVersion::getRequirementId, requirementId));
        if (versionCount > 0) { compliance += 7; } else { complianceReasons.add("无版本记录"); }
        if (notBlank(r.getSafetyClass())) { compliance += 5; } else { complianceReasons.add("未设置安全等级"); }

        int total = completeness + consistency + testability + compliance;
        String grade = total >= 90 ? "A" : total >= 80 ? "B" : total >= 60 ? "C" : "D";
        boolean qualified = total >= 60;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirementId", requirementId);
        result.put("requirementNo", r.getRequirementNo());
        result.put("title", r.getTitle());
        result.put("totalScore", total);
        result.put("grade", grade);
        result.put("qualified", qualified);
        result.put("dimensions", Map.of(
                "completeness", completeness, "consistency", consistency,
                "testability", testability, "compliance", compliance));
        result.put("issues", Map.of(
                "completeness", completenessReasons, "consistency", consistencyReasons,
                "testability", testabilityReasons, "compliance", complianceReasons));
        return result;
    }

    /**
     * 批量评分
     */
    public List<Map<String, Object>> scoreAll(Long projectId) {
        LambdaQueryWrapper<Requirement> w = new LambdaQueryWrapper<>();
        w.eq(Requirement::getIsDeleted, false);
        if (projectId != null) w.eq(Requirement::getProjectId, projectId);
        List<Requirement> all = requirementMapper.selectList(w);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Requirement r : all) {
            result.add(score(r.getId()));
        }
        return result;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 通过 HTTP 远程调用 risk 服务统计风险评估数（避免模块循环依赖）
     */
    private long countRiskViaHttp(Long requirementId) {
        // 简化处理：实际环境可注入 RestTemplate / WebClient
        // 暂不依赖外部服务，标记为 0
        return 0;
    }
}
