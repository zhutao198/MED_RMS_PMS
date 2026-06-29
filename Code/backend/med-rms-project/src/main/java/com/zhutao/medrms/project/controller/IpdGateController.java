package com.zhutao.medrms.project.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.project.domain.entity.IpdGate;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.service.IpdGateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project/ipd-gate")
@RequiredArgsConstructor
public class IpdGateController {

    private final IpdGateService ipdGateService;
    // v1.44 BUG #66 修复：跨模块通知依赖
    private final NotificationService notificationService;
    // R62 FR-2.6：自动检查通过时联动更新对应里程碑状态
    private final MilestoneMapper milestoneMapper;

    @GetMapping("/list/{projectId}")
    public Result<List<IpdGate>> listByProject(@PathVariable Long projectId) {
        return Result.success(ipdGateService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public Result<IpdGate> getById(@PathVariable Long id) {
        return Result.success(ipdGateService.getById(id));
    }

    @PostMapping
    public Result<IpdGate> create(@RequestBody IpdGate gate) {
        return Result.success(ipdGateService.create(gate));
    }

    @PutMapping("/{id}")
    public Result<IpdGate> update(@PathVariable Long id, @RequestBody IpdGate updates) {
        return Result.success(ipdGateService.update(id, updates));
    }

    @PostMapping("/{id}/pass")
    public Result<IpdGate> passGate(@PathVariable Long id,
                                     @RequestParam String decision,
                                     @RequestParam(required = false) String comment) {
        return Result.success(ipdGateService.passGate(id, decision, comment));
    }

    @PostMapping("/{id}/fail")
    public Result<IpdGate> failGate(@PathVariable Long id, @RequestParam String comment) {
        return Result.success(ipdGateService.failGate(id, comment));
    }

    /**
     * FR-2.5 IPD 阶段门自动检查
     * 入参为外部统计（避免跨模块依赖），服务端做规则判定
     */
    @PostMapping("/auto-check")
    public Result<java.util.Map<String, Object>> autoCheck(
            @RequestParam Long projectId,
            @RequestParam Integer gateNo,
            @RequestParam(required = false) Integer requirementCount,
            @RequestParam(required = false) Integer approvedRequirementCount,
            @RequestParam(required = false) Integer riskCount,
            @RequestParam(required = false) Integer highRiskCount,
            @RequestParam(required = false) Integer testCaseCount,
            @RequestParam(required = false) Integer passedTestCaseCount,
            @RequestParam(required = false) Integer iecCompliantCount,
            @RequestParam(required = false) Integer totalIecItems,
            @RequestParam(required = false) Integer dhfEvidenceCount) {
        Result<java.util.Map<String, Object>> result = Result.success(ipdGateService.autoCheckGate(projectId, gateNo,
                requirementCount, approvedRequirementCount, riskCount, highRiskCount,
                testCaseCount, passedTestCaseCount, iecCompliantCount, totalIecItems, dhfEvidenceCount));

        // v1.44 BUG #66 修复：门控自动检查 FAIL 时向项目管理员发 SYSTEM 通知
        // R62 FR-2.6 增强：检查通过时自动更新对应里程碑状态为 COMPLETED，并通知研发总监
        try {
            Map<String, Object> data = result.getData();
            if (data != null && "FAIL".equals(data.get("verdict"))) {
                notificationService.sendSystemNotification(
                    1L, // 暂推送给 admin（v1.44 暂未做 project owner 反查）
                    "DCP" + gateNo + " 门控检查未通过",
                    "项目 " + projectId + " 在 DCP" + gateNo + " 阶段门自动检查中未通过，请查看详情并处理。",
                    "GATE", projectId);
            } else if (data != null && "PASS".equals(data.get("verdict"))) {
                // 查找该项目对应 DCP 的里程碑，自动标"可达成"
                Milestone milestone = milestoneMapper.selectOne(
                    new LambdaQueryWrapper<Milestone>()
                        .eq(Milestone::getProjectId, projectId)
                        .eq(Milestone::getGateType, "DCP" + gateNo)
                );
                if (milestone != null && !"COMPLETED".equals(milestone.getStatus())) {
                    milestone.setStatus("COMPLETED");
                    milestone.setCheckResult("PASS");
                    milestone.setActualDate(LocalDate.now());
                    milestoneMapper.updateById(milestone);
                    // 通知研发总监（暂用 admin 1L，v1.62 后续做 project owner 反查）
                    notificationService.sendSystemNotification(
                        1L,
                        "里程碑自动达成：DCP" + gateNo,
                        "项目 " + projectId + " 在 DCP" + gateNo + " 阶段门自动检查通过，里程碑【" + milestone.getName() + "】已自动标记为可达成。",
                        "MILESTONE", milestone.getId());
                }
            }
        } catch (Exception e) {
            // 通知/更新失败不影响主流程
        }

        return result;
    }
}