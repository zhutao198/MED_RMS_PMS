package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    // v1.43 P1-9 修复：跨 schema SQL 聚合（不引入跨模块依赖）
    private final JdbcTemplate jdbcTemplate;

    public List<Project> list(String status) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getIsDeleted, false);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Project::getStatus, status);
        }
        wrapper.orderByDesc(Project::getCreatedAt);
        return projectMapper.selectList(wrapper);
    }

    public Project getById(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null || project.getIsDeleted()) {
            throw BusinessException.notFound("PJ0101", "项目不存在");
        }
        return project;
    }

    @Transactional
    public Project create(Project project) {
        String projectNo = generateProjectNo();
        project.setProjectNo(projectNo);
        project.setStatus("PLANNING");
        projectMapper.insert(project);
        return project;
    }

    @Transactional
    public Project update(Long id, Project updates) {
        Project project = getById(id);
        if (updates.getProjectName() != null) {
            project.setProjectName(updates.getProjectName());
        }
        if (updates.getDescription() != null) {
            project.setDescription(updates.getDescription());
        }
        if (updates.getStatus() != null) {
            project.setStatus(updates.getStatus());
        }
        if (updates.getStartDate() != null) {
            project.setStartDate(updates.getStartDate());
        }
        if (updates.getEndDate() != null) {
            project.setEndDate(updates.getEndDate());
        }
        projectMapper.updateById(project);
        return project;
    }

    // ===== v1.43 P1-9 修复：项目进度聚合（前端 ProjectsList 用）=====
    /**
     * 计算项目整体进度：
     *   - 该项目下需求：按状态加权（Draft 0, Submitted 30, InReview 50, Approved 80, Verified/Baseline 100）
     *   - 任务：按完成比 (DONE 100, IN_PROGRESS 50, 其他 0)
     *   - 取需求进度均值与任务进度均值的加权平均（需求 60%, 任务 40%）
     * 简化：只读取数据库，避免引入跨服务依赖；空数据返回 null 让前端降级。
     */
    public java.util.Map<String, Object> getProjectProgress(Long projectId) {
        if (projectId == null) {
            throw BusinessException.param("projectId 不能为空");
        }
        Project project = getById(projectId);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("projectId", project.getId());
        result.put("projectName", project.getProjectName());
        result.put("projectNo", project.getProjectNo());

        Double reqProgress = computeRequirementProgress(projectId);
        Double taskProgress = computeTaskProgress(projectId);

        if (reqProgress == null && taskProgress == null) {
            result.put("progress", null);
            result.put("completionRate", null);
            result.put("message", "项目暂无需求/任务数据");
            return result;
        }
        double rp = reqProgress == null ? 0 : reqProgress;
        double tp = taskProgress == null ? 0 : taskProgress;
        int overall = (int) Math.round(rp * 0.6 + tp * 0.4);
        result.put("progress", overall);
        result.put("completionRate", overall);
        result.put("requirementProgress", (int) Math.round(rp));
        result.put("taskProgress", (int) Math.round(tp));
        return result;
    }

    private Double computeRequirementProgress(Long projectId) {
        // 状态权重：Draft 0 / Submitted 30 / InReview 50 / Approved 80 / Verified/Baseline 100
        try {
            Double avg = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(CASE status " +
                            "  WHEN 'Draft' THEN 0 " +
                            "  WHEN 'Submitted' THEN 30 " +
                            "  WHEN 'InReview' THEN 50 " +
                            "  WHEN 'Approved' THEN 80 " +
                            "  WHEN 'Verified' THEN 100 " +
                            "  WHEN 'Baseline' THEN 100 " +
                            "  ELSE 0 END), NULL) " +
                            "FROM req_schema.t_requirement WHERE project_id = ? AND is_deleted = false",
                    Double.class, projectId);
            return avg;
        } catch (Exception e) {
            return null;
        }
    }

    private Double computeTaskProgress(Long projectId) {
        // 状态权重：DONE 100 / IN_PROGRESS 50 / 其他 0
        try {
            Double avg = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(AVG(CASE status " +
                            "  WHEN 'DONE' THEN 100 " +
                            "  WHEN 'COMPLETED' THEN 100 " +
                            "  WHEN 'IN_PROGRESS' THEN 50 " +
                            "  WHEN 'IN_TEST' THEN 75 " +
                            "  ELSE 0 END), NULL) " +
                            "FROM proj_schema.t_task WHERE project_id = ?",
                    Double.class, projectId);
            return avg;
        } catch (Exception e) {
            return null;
        }
    }

    private String generateProjectNo() {
        long count = projectMapper.selectCount(new LambdaQueryWrapper<Project>());
        return String.format("PRJ-%06d", count + 1);
    }
}