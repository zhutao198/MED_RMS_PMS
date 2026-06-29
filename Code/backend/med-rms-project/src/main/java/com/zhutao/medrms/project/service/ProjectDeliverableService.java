package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.project.domain.entity.ProjectDeliverable;
import com.zhutao.medrms.project.mapper.ProjectDeliverableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 项目交付物服务（R92 新增）
 */
@Service
@RequiredArgsConstructor
public class ProjectDeliverableService {

    private final ProjectDeliverableMapper deliverableMapper;

    public List<ProjectDeliverable> listByProject(Long projectId) {
        return deliverableMapper.selectList(
                new LambdaQueryWrapper<ProjectDeliverable>()
                        .eq(ProjectDeliverable::getProjectId, projectId)
                        .eq(ProjectDeliverable::getIsDeleted, false)
                        .orderByAsc(ProjectDeliverable::getDueDate));
    }

    public ProjectDeliverable create(Long projectId, ProjectDeliverable d) {
        d.setProjectId(projectId);
        if (d.getStatus() == null) d.setStatus("TODO");
        deliverableMapper.insert(d);
        return d;
    }

    public ProjectDeliverable updateStatus(Long projectId, Long id, String status) {
        ProjectDeliverable exist = deliverableMapper.selectById(id);
        if (exist == null || !exist.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("交付物不存在或不属于该项目");
        }
        exist.setStatus(status);
        deliverableMapper.updateById(exist);
        return exist;
    }

    public void delete(Long projectId, Long id) {
        ProjectDeliverable exist = deliverableMapper.selectById(id);
        if (exist == null || !exist.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("交付物不存在或不属于该项目");
        }
        exist.setIsDeleted(true);
        deliverableMapper.updateById(exist);
    }
}
