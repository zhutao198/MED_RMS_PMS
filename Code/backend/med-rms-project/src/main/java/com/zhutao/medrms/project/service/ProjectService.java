package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.ComplianceTemplate;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final MilestoneMapper milestoneMapper;
    private final ProjectActivityService activityService;
    private final ComplianceTemplateService templateService;
    // v1.43 P1-9 修复：跨 schema SQL 聚合（不引入跨模块依赖）
    private final JdbcTemplate jdbcTemplate;

    /**
     * R161 修复（F7）：加 page/size 分页参数，避免全表返回
     *   默认 size=20（与 controller 兜底一致），status 可选过滤
     */
    public List<Project> list(String status, int page, int size) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Project::getIsDeleted, false);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Project::getStatus, status);
        }
        wrapper.orderByDesc(Project::getCreatedAt);
        // MyBatis-Plus last("LIMIT X OFFSET Y") 拼接（大小写不敏感 OK）
        wrapper.last("limit " + size + " offset " + (page * size));
        return projectMapper.selectList(wrapper);
    }

    /** R161 兼容旧调用（无分页） */
    public List<Project> list(String status) {
        return list(status, 0, 1000);  // 旧接口给个宽容上限
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
        if (project.getProjectName() == null || project.getProjectName().isBlank()) {
            throw BusinessException.param("项目名称不能为空");
        }
        String projectNo = generateProjectNo();
        project.setProjectNo(projectNo);
        project.setStatus("PLANNING");
        project.setIsDeleted(false);
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

    // ===== R175 FR-2.12: Excel 导出（JSON 格式，前端/BFF 转 Excel）=====
    @Transactional(readOnly = true)
    public String exportProjectPlanAsJson(Long projectId) {
        Project project = getById(projectId);
        List<Milestone> milestones = milestoneMapper.selectList(
            new LambdaQueryWrapper<Milestone>().eq(Milestone::getProjectId, projectId));
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId));
        Map<String, Object> plan = new HashMap<>();
        plan.put("project", project);
        plan.put("milestones", milestones);
        plan.put("tasks", tasks);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(plan);
        } catch (Exception e) {
            throw new RuntimeException("导出序列化失败", e);
        }
    }

    @Transactional
    public void importTasks(Long projectId, List<Map<String, Object>> taskList, Long operatorId, String operatorName) {
        for (Map<String, Object> t : taskList) {
            Task task = new Task();
            task.setProjectId(projectId);
            task.setTitle((String) t.get("title"));
            task.setDescription((String) t.get("description"));
            Object assigneeId = t.get("assigneeId");
            if (assigneeId != null) task.setAssigneeId(Long.valueOf(assigneeId.toString()));
            task.setAssigneeName((String) t.get("assigneeName"));
            Object estimatedHours = t.get("estimatedHours");
            if (estimatedHours != null) task.setEstimatedHours(Integer.valueOf(estimatedHours.toString()));
            Object startDate = t.get("startDate");
            if (startDate != null) task.setStartDate(java.time.LocalDate.parse(startDate.toString()));
            Object endDate = t.get("endDate");
            if (endDate != null) task.setEndDate(java.time.LocalDate.parse(endDate.toString()));
            task.setPriority((String) t.getOrDefault("priority", "MEDIUM"));
            task.setStatus("TODO");
            long count = taskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>());
            task.setTaskNo(String.format("TASK-%06d", count + 1));
            taskMapper.insert(task);
        }
        activityService.recordActivity(projectId, "PROJECT_CONFIG_CHANGED",
            operatorName + " 导入了 " + taskList.size() + " 条任务",
            null, operatorId, operatorName, "PROJECT", projectId);
    }

    // ===== R175 FR-2.12: Excel 导出 (.xlsx) =====
    @Transactional(readOnly = true)
    public byte[] exportExcelAsXlsx(Long projectId) {
        Project project = getById(projectId);
        List<Milestone> milestones = milestoneMapper.selectList(
            new LambdaQueryWrapper<Milestone>().eq(Milestone::getProjectId, projectId));
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // Sheet 1: 项目信息
            Sheet infoSheet = wb.createSheet("项目信息");
            String[][] infoData = {
                {"项目编号", project.getProjectNo()},
                {"项目名称", project.getProjectName()},
                {"项目描述", project.getDescription()},
                {"状态", project.getStatus()},
                {"开始日期", project.getStartDate() != null ? project.getStartDate().toString() : ""},
                {"结束日期", project.getEndDate() != null ? project.getEndDate().toString() : ""},
                {"负责人ID", project.getManagerId() != null ? project.getManagerId().toString() : ""},
                {"负责人", project.getManagerName()},
                {"合规模板", project.getTemplateCode()},
                {"预算告警阈值", project.getBudgetAlarmPct() != null ? project.getBudgetAlarmPct() + "%" : ""},
            };
            for (int i = 0; i < infoData.length; i++) {
                Row r = infoSheet.createRow(i);
                Cell k = r.createCell(0);
                k.setCellValue(infoData[i][0]);
                k.setCellStyle(headerStyle);
                r.createCell(1).setCellValue(infoData[i][1] != null ? infoData[i][1] : "");
            }
            infoSheet.autoSizeColumn(0);
            infoSheet.autoSizeColumn(1);

            // Sheet 2: 里程碑
            Sheet msSheet = wb.createSheet("里程碑");
            String[] msHeaders = {"名称", "描述", "门类型", "计划日期", "实际日期", "状态"};
            Row msHeaderRow = msSheet.createRow(0);
            for (int i = 0; i < msHeaders.length; i++) {
                Cell c = msHeaderRow.createCell(i);
                c.setCellValue(msHeaders[i]);
                c.setCellStyle(headerStyle);
            }
            int msRowIdx = 1;
            for (Milestone m : milestones) {
                Row r = msSheet.createRow(msRowIdx++);
                r.createCell(0).setCellValue(m.getName());
                r.createCell(1).setCellValue(m.getDescription() != null ? m.getDescription() : "");
                r.createCell(2).setCellValue(m.getGateType() != null ? m.getGateType() : "");
                r.createCell(3).setCellValue(m.getPlannedDate() != null ? m.getPlannedDate().format(dtf) : "");
                r.createCell(4).setCellValue(m.getActualDate() != null ? m.getActualDate().format(dtf) : "");
                r.createCell(5).setCellValue(m.getStatus() != null ? m.getStatus() : "");
            }
            for (int i = 0; i < msHeaders.length; i++) msSheet.autoSizeColumn(i);

            // Sheet 3: 任务
            Sheet taskSheet = wb.createSheet("任务");
            String[] taskHeaders = {"编号", "标题", "描述", "负责人", "优先级", "状态", "预计工时", "开始日期", "结束日期"};
            Row taskHeaderRow = taskSheet.createRow(0);
            for (int i = 0; i < taskHeaders.length; i++) {
                Cell c = taskHeaderRow.createCell(i);
                c.setCellValue(taskHeaders[i]);
                c.setCellStyle(headerStyle);
            }
            int taskRowIdx = 1;
            for (Task t : tasks) {
                Row r = taskSheet.createRow(taskRowIdx++);
                r.createCell(0).setCellValue(t.getTaskNo() != null ? t.getTaskNo() : "");
                r.createCell(1).setCellValue(t.getTitle() != null ? t.getTitle() : "");
                r.createCell(2).setCellValue(t.getDescription() != null ? t.getDescription() : "");
                r.createCell(3).setCellValue(t.getAssigneeName() != null ? t.getAssigneeName() : "");
                r.createCell(4).setCellValue(t.getPriority() != null ? t.getPriority() : "");
                r.createCell(5).setCellValue(t.getStatus() != null ? t.getStatus() : "");
                r.createCell(6).setCellValue(t.getEstimatedHours() != null ? t.getEstimatedHours().toString() : "");
                r.createCell(7).setCellValue(t.getStartDate() != null ? t.getStartDate().format(dtf) : "");
                r.createCell(8).setCellValue(t.getEndDate() != null ? t.getEndDate().format(dtf) : "");
            }
            for (int i = 0; i < taskHeaders.length; i++) taskSheet.autoSizeColumn(i);

            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel 导出失败", e);
        }
    }

    // ===== R175 FR-2.11: 项目保存为模板 =====
    @Transactional
    public ComplianceTemplate saveProjectAsTemplate(Long projectId, String templateName, Long operatorId, String operatorName) {
        Project project = getById(projectId);
        ComplianceTemplate t = new ComplianceTemplate();
        t.setCode("PROJECT_TPL_" + projectId);
        t.setName(templateName != null ? templateName : project.getProjectName() + " 模板");
        t.setType("CUSTOM");
        t.setCategory("PROJECT");
        t.setDescription("从项目 " + project.getProjectName() + " 生成的模板");
        t.setCreatedBy(operatorId);
        t.setCreatedByName(operatorName);
        t.setIsActive(true);
        String cfg = "{\"sourceProjectId\": " + projectId
            + ", \"description\": \"" + (project.getDescription() != null ? project.getDescription().replace("\"", "\\\"") : "")
            + "\", \"templateCode\": \"" + (project.getTemplateCode() != null ? project.getTemplateCode() : "")
            + "\", \"budgetAlarmPct\": " + (project.getBudgetAlarmPct() != null ? project.getBudgetAlarmPct() : 120) + "}";
        t.setConfigJson(cfg);
        templateService.createCustom(t, operatorId, operatorName);
        return t;
    }

    private String generateProjectNo() {
        long count = projectMapper.selectCount(new LambdaQueryWrapper<Project>());
        return String.format("PRJ-%06d", count + 1);
    }

    // ===== R175 FR-2.11: 项目模板克隆 =====
    @Transactional
    public Project cloneProject(Long sourceId, String newName, Long operatorId, String operatorName) {
        Project source = getById(sourceId);
        Project clone = new Project();
        clone.setProjectName(newName != null ? newName : source.getProjectName() + " (副本)");
        clone.setDescription(source.getDescription());
        clone.setTemplateId(source.getTemplateId());
        clone.setTemplateCode(source.getTemplateCode());
        clone.setStartDate(LocalDate.now());
        clone.setEndDate(null);
        clone.setStatus("PLANNING");
        clone.setBudgetAlarmPct(source.getBudgetAlarmPct());
        Project created = create(clone);

        // 克隆里程碑
        List<Milestone> milestones = milestoneMapper.selectList(
            new LambdaQueryWrapper<Milestone>().eq(Milestone::getProjectId, sourceId));
        for (Milestone ms : milestones) {
            Milestone m = new Milestone();
            m.setProjectId(created.getId());
            m.setName(ms.getName());
            m.setDescription(ms.getDescription());
            m.setGateType(ms.getGateType());
            m.setPlannedDate(ms.getPlannedDate());
            m.setStatus("PLANNED");
            milestoneMapper.insert(m);
        }

        activityService.recordActivity(created.getId(), "PROJECT_CONFIG_CHANGED",
            operatorName + " 从项目 " + source.getProjectName() + " 克隆创建该项目",
            null, operatorId, operatorName, "PROJECT", created.getId());
        return created;
    }

    // ===== R175 FR-2.16: 项目健康度评分 =====
    public Map<String, Object> calculateHealthScore(Long projectId) {
        Project project = getById(projectId);
        Map<String, Object> score = new HashMap<>();

        // 进度维度（30%）
        double progressScore = computeProgressHealth(projectId);
        // 风险维度（25%）
        double riskScore = computeRiskHealth(projectId);
        // 质量维度（25%）
        double qualityScore = computeQualityHealth(projectId);
        // 合规维度（20%）
        double complianceScore = computeComplianceHealth(projectId);

        double total = progressScore * 0.30 + riskScore * 0.25
                     + qualityScore * 0.25 + complianceScore * 0.20;

        String level = total >= 85 ? "GREEN" : total >= 60 ? "YELLOW" : "RED";
        score.put("totalScore", Math.round(total * 10.0) / 10.0);
        score.put("level", level);
        score.put("dimensions", Map.of(
            "progress", Math.round(progressScore * 10.0) / 10.0,
            "risk", Math.round(riskScore * 10.0) / 10.0,
            "quality", Math.round(qualityScore * 10.0) / 10.0,
            "compliance", Math.round(complianceScore * 10.0) / 10.0
        ));
        return score;
    }

    private double computeProgressHealth(Long projectId) {
        try {
            Double ms = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(CASE WHEN actual_date IS NOT NULL THEN 100 ELSE 0 END), 0) " +
                "FROM proj_schema.t_milestone WHERE project_id = ?", Double.class, projectId);
            return ms != null ? ms : 0;
        } catch (Exception e) {
            return 50;
        }
    }

    private double computeRiskHealth(Long projectId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COALESCE(100 - AVG(CASE WHEN severity >= 4 THEN 30 WHEN severity >= 3 THEN 15 ELSE 0 END), 100) " +
                "FROM risk_schema.t_risk WHERE project_id = ?", Double.class, projectId);
        } catch (Exception e) {
            return 80;
        }
    }

    private double computeQualityHealth(Long projectId) {
        try {
            Double taskDone = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(CASE WHEN status = 'DONE' THEN 100 WHEN status = 'IN_PROGRESS' THEN 50 ELSE 0 END), 0) " +
                "FROM proj_schema.t_task WHERE project_id = ?", Double.class, projectId);
            return taskDone != null ? taskDone : 0;
        } catch (Exception e) {
            return 50;
        }
    }

    private double computeComplianceHealth(Long projectId) {
        try {
            Double trace = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(CASE WHEN is_deleted = false AND requirement_type IN ('URS','PRS','SRS','DRS') THEN 100 ELSE 0 END), 0) " +
                "FROM req_schema.t_requirement WHERE project_id = ?", Double.class, projectId);
            return trace != null ? trace : 0;
        } catch (Exception e) {
            return 50;
        }
    }
}