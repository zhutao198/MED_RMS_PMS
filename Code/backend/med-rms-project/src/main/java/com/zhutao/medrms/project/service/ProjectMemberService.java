package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.ProjectMember;
import com.zhutao.medrms.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberMapper memberMapper;

    public List<ProjectMember> listByProject(Long projectId) {
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId)
               .eq(ProjectMember::getIsDeleted, false)
               .orderByAsc(ProjectMember::getRole);
        return memberMapper.selectList(wrapper);
    }

    public ProjectMember getById(Long id) {
        ProjectMember member = memberMapper.selectById(id);
        if (member == null || member.getIsDeleted()) {
            throw BusinessException.notFound("PJ0301", "项目成员不存在");
        }
        return member;
    }

    @Transactional
    public ProjectMember addMember(ProjectMember member) {
        member.setStatus("ACTIVE");
        member.setJoinedAt(LocalDate.now());
        memberMapper.insert(member);
        return member;
    }

    @Transactional
    public ProjectMember updateMember(Long id, ProjectMember updates) {
        ProjectMember member = getById(id);
        if (updates.getRole() != null) member.setRole(updates.getRole());
        if (updates.getRealName() != null) member.setRealName(updates.getRealName());
        if (updates.getDepartment() != null) member.setDepartment(updates.getDepartment());
        memberMapper.updateById(member);
        return member;
    }

    @Transactional
    public void removeMember(Long id) {
        ProjectMember member = getById(id);
        member.setIsDeleted(true);
        memberMapper.updateById(member);
    }

    @Transactional
    public ProjectMember switchRole(Long id, String newRole) {
        ProjectMember member = getById(id);
        member.setRole(newRole);
        memberMapper.updateById(member);
        return member;
    }
}