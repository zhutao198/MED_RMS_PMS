package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.project.domain.entity.ProjectActivity;
import com.zhutao.medrms.project.mapper.ProjectActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectActivityService {

    private final ProjectActivityMapper activityMapper;

    public void recordActivity(Long projectId, String activityType, String summary,
                                String detail, Long operatorId, String operatorName,
                                String sourceType, Long sourceId) {
        ProjectActivity a = new ProjectActivity();
        a.setProjectId(projectId);
        a.setActivityType(activityType);
        a.setSummary(summary);
        a.setDetail(detail);
        a.setOperatorId(operatorId);
        a.setOperatorName(operatorName);
        a.setSourceType(sourceType);
        a.setSourceId(sourceId);
        activityMapper.insert(a);
        log.debug("记录项目活动: projectId={}, type={}", projectId, activityType);
    }

    public List<ProjectActivity> listByProject(Long projectId, int page, int size) {
        LambdaQueryWrapper<ProjectActivity> w = new LambdaQueryWrapper<>();
        w.eq(ProjectActivity::getProjectId, projectId);
        w.orderByDesc(ProjectActivity::getCreatedAt);
        w.last("limit " + size + " offset " + (page * size));
        return activityMapper.selectList(w);
    }
}
