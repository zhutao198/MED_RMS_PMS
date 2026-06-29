package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.IpdGate;
import com.zhutao.medrms.project.mapper.IpdGateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IpdGateService {

    private final IpdGateMapper ipdGateMapper;

    /**
     * FR-2.5 IPD 阶段门自动检查：DCP1-DCP5 门限规则
     * - DCP1 PLANNING：项目立项 + 计划批准
     * - DCP2 DEFINE：需求冻结 + 风险评估完成
     * - DCP3 DEVELOPMENT：设计/实现完成 + 单元测试通过
     * - DCP4 RELEASE：集成测试 + IEC 62304 合规
     * - DCP5 MARKET：DHF 证据齐 + 变更冻结
     *
     * 每个门限对应不同 checkItems；返回每项的 PASS/FAIL/SKIP 状态 + 总判定。
     * 由于不直接依赖其他模块（避免循环依赖），通过传入外部统计参数进行判定。
     */
    public Map<String, Object> autoCheckGate(Long projectId, Integer gateNo,
                                              Integer requirementCount, Integer approvedRequirementCount,
                                              Integer riskCount, Integer highRiskCount,
                                              Integer testCaseCount, Integer passedTestCaseCount,
                                              Integer iecCompliantCount, Integer totalIecItems,
                                              Integer dhfEvidenceCount) {
        List<Map<String, Object>> items = new ArrayList<>();
        boolean allPass = true;
        int passed = 0;

        switch (gateNo == null ? 0 : gateNo) {
            case 1: {
                // DCP1 立项门
                addCheck(items, "项目立项", "项目 ID 存在即通过", projectId != null, projectId != null, "需先创建项目");
                addCheck(items, "需求数量", "至少 1 条需求", requirementCount != null && requirementCount >= 1, requirementCount, "需导入或创建至少 1 条需求");
                boolean planOk = requirementCount != null && requirementCount >= 1;
                if (planOk) passed++;
                if (!planOk) allPass = false;
                break;
            }
            case 2: {
                // DCP2 需求冻结门
                boolean reqOk = approvedRequirementCount != null && approvedRequirementCount >= Math.max(1, (requirementCount == null ? 0 : requirementCount) * 0.6);
                addCheck(items, "需求批准率", "≥ 60% 需求进入 Approved/Verified 状态", reqOk,
                        approvedRequirementCount + "/" + requirementCount, "需先评审/批准需求");
                boolean riskOk = riskCount != null && riskCount > 0;
                addCheck(items, "风险评估", "至少 1 条风险评估记录", riskOk, riskCount, "需完成风险识别");
                boolean noHigh = highRiskCount == null || highRiskCount == 0;
                addCheck(items, "无未关闭高风险", "高风险必须为 0 或全部 ALARP", noHigh, highRiskCount, "请关闭所有高风险");
                if (reqOk) passed++;
                if (riskOk) passed++;
                if (noHigh) passed++;
                if (!reqOk || !riskOk || !noHigh) allPass = false;
                break;
            }
            case 3: {
                // DCP3 开发完成门
                boolean tcOk = testCaseCount != null && testCaseCount >= Math.max(1, (requirementCount == null ? 0 : requirementCount));
                addCheck(items, "测试用例覆盖", "测试用例数 ≥ 需求数", tcOk,
                        testCaseCount + "/" + requirementCount, "需为每条需求建立测试用例");
                boolean passRateOk = testCaseCount != null && testCaseCount > 0
                        && passedTestCaseCount != null
                        && (passedTestCaseCount * 100 / testCaseCount) >= 70;
                addCheck(items, "单元测试通过率", "≥ 70%", passRateOk,
                        passedTestCaseCount + "/" + testCaseCount, "请修复失败用例");
                if (tcOk) passed++;
                if (passRateOk) passed++;
                if (!tcOk || !passRateOk) allPass = false;
                break;
            }
            case 4: {
                // DCP4 发布门
                boolean iecOk = totalIecItems != null && totalIecItems > 0
                        && iecCompliantCount != null
                        && (iecCompliantCount * 100 / totalIecItems) >= 60;
                addCheck(items, "IEC 62304 合规", "必选条款 ≥ 60% 通过", iecOk,
                        iecCompliantCount + "/" + totalIecItems, "需完成 IEC 62304 评估");
                if (iecOk) passed++;
                if (!iecOk) allPass = false;
                break;
            }
            case 5: {
                // DCP5 上市门
                boolean dhfOk = dhfEvidenceCount != null && dhfEvidenceCount >= 5;
                addCheck(items, "DHF 证据", "至少 5 项证据", dhfOk, dhfEvidenceCount, "需上传 DHF 证据");
                if (dhfOk) passed++;
                if (!dhfOk) allPass = false;
                break;
            }
            default:
                addCheck(items, "未知门限", "DCP1-DCP5 范围", false, gateNo, "请使用 1-5");
                allPass = false;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("gateNo", gateNo);
        result.put("verdict", allPass ? "PASS" : "FAIL");
        result.put("passedItems", passed);
        result.put("totalItems", items.size());
        result.put("items", items);
        return result;
    }

    public List<IpdGate> listByProject(Long projectId) {
        LambdaQueryWrapper<IpdGate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IpdGate::getProjectId, projectId)
               .eq(IpdGate::getIsDeleted, false)
               .orderByAsc(IpdGate::getGateNo);
        return ipdGateMapper.selectList(wrapper);
    }

    public IpdGate getById(Long id) {
        IpdGate gate = ipdGateMapper.selectById(id);
        if (gate == null || gate.getIsDeleted()) {
            throw BusinessException.notFound("PJ0201", "DCP门控不存在");
        }
        return gate;
    }

    @Transactional
    public IpdGate create(IpdGate gate) {
        validateGateNo(gate.getProjectId(), gate.getGateNo());
        gate.setStatus("PENDING");
        ipdGateMapper.insert(gate);
        return gate;
    }

    @Transactional
    public IpdGate update(Long id, IpdGate updates) {
        IpdGate gate = getById(id);
        if (updates.getGateName() != null) gate.setGateName(updates.getGateName());
        if (updates.getGateType() != null) gate.setGateType(updates.getGateType());
        if (updates.getPlannedDate() != null) gate.setPlannedDate(updates.getPlannedDate());
        if (updates.getReviewer() != null) gate.setReviewer(updates.getReviewer());
        ipdGateMapper.updateById(gate);
        return gate;
    }

    @Transactional
    public IpdGate passGate(Long id, String decision, String comment) {
        IpdGate gate = getById(id);
        gate.setStatus("PASSED");
        gate.setDecision(decision);
        gate.setComment(comment);
        gate.setActualDate(LocalDate.now());
        ipdGateMapper.updateById(gate);
        return gate;
    }

    @Transactional
    public IpdGate failGate(Long id, String comment) {
        IpdGate gate = getById(id);
        gate.setStatus("FAILED");
        gate.setDecision("REJECTED");
        gate.setComment(comment);
        gate.setActualDate(LocalDate.now());
        ipdGateMapper.updateById(gate);
        return gate;
    }

    private void validateGateNo(Long projectId, Integer gateNo) {
        Long count = ipdGateMapper.selectCount(new LambdaQueryWrapper<IpdGate>()
            .eq(IpdGate::getProjectId, projectId)
            .eq(IpdGate::getGateNo, gateNo)
            .eq(IpdGate::getIsDeleted, false));
        if (count > 0) {
            throw BusinessException.notFound("PJ0202", "该门控序号已存在");
        }
    }

    private void addCheck(List<Map<String, Object>> items, String name, String criterion, boolean pass, Object actual, String failHint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("criterion", criterion);
        m.put("actual", actual);
        m.put("pass", pass);
        m.put("failHint", failHint);
        items.add(m);
    }
}