package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ComplianceCheck;
import com.zhutao.medrms.compliance.domain.entity.DhfEvidence;
import com.zhutao.medrms.compliance.mapper.ComplianceCheckMapper;
import com.zhutao.medrms.compliance.mapper.DhfEvidenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCheckService {

    private final ComplianceCheckMapper complianceCheckMapper;
    private final DhfEvidenceMapper dhfEvidenceMapper;

    public List<ComplianceCheck> listByRequirement(Long requirementId) {
        return complianceCheckMapper.selectByRequirementId(requirementId);
    }

    public List<ComplianceCheck> listByProject(Long projectId) {
        return complianceCheckMapper.selectByProjectId(projectId);
    }

    @Transactional
    public ComplianceCheck createCheck(ComplianceCheck check) {
        check.setStatus("PENDING");
        check.setCheckedAt(LocalDateTime.now());
        complianceCheckMapper.insert(check);
        log.info("创建合规检查记录: requirementId={}, checkItem={}", check.getRequirementId(), check.getCheckItem());
        return check;
    }

    @Transactional
    public ComplianceCheck completeCheck(Long id, String checkResult, String remarks) {
        ComplianceCheck check = complianceCheckMapper.selectById(id);
        if (check == null) {
            throw BusinessException.notFound("CP0101", "合规检查记录不存在");
        }
        check.setCheckResult(checkResult);
        check.setRemarks(remarks);
        check.setStatus("COMPLETED");
        check.setCheckedAt(LocalDateTime.now());
        complianceCheckMapper.updateById(check);
        log.info("完成合规检查: id={}, result={}", id, checkResult);
        return check;
    }

    public List<DhfEvidence> listEvidenceByProject(Long projectId) {
        return dhfEvidenceMapper.selectByProjectId(projectId);
    }

    public List<DhfEvidence> listEvidenceByType(Long projectId, String evidenceType) {
        return dhfEvidenceMapper.selectByType(projectId, evidenceType);
    }

    @Transactional
    public DhfEvidence uploadEvidence(DhfEvidence evidence) {
        evidence.setStatus("UPLOADED");
        evidence.setCreatedAt(LocalDateTime.now());
        dhfEvidenceMapper.insert(evidence);
        log.info("上传DHF证据: projectId={}, evidenceType={}", evidence.getProjectId(), evidence.getEvidenceType());
        return evidence;
    }

    @Transactional
    public void deleteEvidence(Long id) {
        DhfEvidence evidence = dhfEvidenceMapper.selectById(id);
        if (evidence == null) {
            throw BusinessException.notFound("CP0102", "DHF证据不存在");
        }
        evidence.setIsDeleted(true);
        dhfEvidenceMapper.updateById(evidence);
        log.info("删除DHF证据: id={}", id);
    }
}