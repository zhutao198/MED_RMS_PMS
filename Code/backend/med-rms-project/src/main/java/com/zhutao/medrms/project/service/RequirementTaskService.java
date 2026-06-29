package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 需求→任务转化服务 - FR-1.10
 * 功能：
 *  1. 将 SRS/DRS 需求拆解为多个任务
 *  2. 自动按需求类型/优先级生成任务草稿
 *  3. 任务状态更新时双向同步到需求状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementTaskService {

    private final TaskMapper taskMapper;
    private final RequirementMapper requirementMapper;

    /**
     * 将一个需求拆解为多个任务（FR-1.10 核心）
     * @param requirementId 需求 ID
     * @param taskDrafts 任务草稿（前端填写）
     * @return 创建的任务列表
     */
    @Transactional
    public List<Task> convertRequirementToTasks(Long requirementId, List<TaskDraft> taskDrafts) {
        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null) {
            throw BusinessException.notFound("REQ0101", "需求不存在: id=" + requirementId);
        }

        // 需求已基线化后不允许再拆解（FR-0.17 操作序列保护）
        if ("Baseline".equals(req.getStatus())) {
            throw BusinessException.stateConflict("已基线化需求不允许再拆解为任务（FR-0.17）");
        }

        // 防重复：检查该需求是否已有任务
        Long existing = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>().eq(Task::getRequirementId, requirementId));
        if (existing > 0) {
            throw BusinessException.stateConflict("该需求已存在 " + existing + " 个任务，请勿重复拆解");
        }

        if (taskDrafts == null || taskDrafts.isEmpty()) {
            throw BusinessException.stateConflict("至少需要 1 个任务草稿");
        }

        List<Task> created = new ArrayList<>();
        long baseCount = taskMapper.selectCount(new LambdaQueryWrapper<>());
        for (int i = 0; i < taskDrafts.size(); i++) {
            TaskDraft d = taskDrafts.get(i);
            Task t = new Task();
            t.setTaskNo(String.format("TASK-%06d", baseCount + i + 1));
            t.setTitle(d.title);
            t.setDescription(d.description);
            t.setProjectId(req.getProjectId());
            t.setRequirementId(requirementId);
            t.setAssigneeId(d.assigneeId);
            t.setAssigneeName(d.assigneeName);
            t.setStartDate(d.startDate);
            t.setEndDate(d.endDate);
            t.setEstimatedHours(d.estimatedHours);
            t.setPriority(d.priority != null ? d.priority : req.getPriority());
            t.setStatus("TODO");
            t.setParentTaskId(d.parentTaskId);
            t.setMilestoneId(d.milestoneId);
            taskMapper.insert(t);
            created.add(t);
        }

        // 需求状态推进：Draft → InProgress（如拆解成功）
        if ("Draft".equals(req.getStatus()) || "Approved".equals(req.getStatus())) {
            req.setStatus("InProgress");
            requirementMapper.updateById(req);
        }

        log.info("需求 {} 拆解为 {} 个任务", requirementId, created.size());
        return created;
    }

    /**
     * 根据需求类型自动生成任务草稿（FR-1.10 智能拆解）
     * 仅生成草稿，不直接写入，由用户在 UI 上确认后调用 convertRequirementToTasks
     */
    public List<TaskDraft> generateDrafts(Long requirementId) {
        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null) {
            throw BusinessException.notFound("REQ0101", "需求不存在: id=" + requirementId);
        }

        List<TaskDraft> drafts = new ArrayList<>();
        String type = req.getRequirementType();
        LocalDate base = LocalDate.now();

        // URS/PRS：仅做需求分析任务
        if ("URS".equals(type) || "PRS".equals(type)) {
            drafts.add(makeDraft("需求分析：" + req.getTitle(),
                    "完成需求细化、评审与确认", base, base.plusDays(3), "HIGH", 16));
        }
        // SRS：拆分为设计/实现/测试 3 个标准任务
        else if ("SRS".equals(type)) {
            drafts.add(makeDraft("SRS 设计：" + req.getTitle(),
                    "完成软件需求规格说明", base, base.plusDays(2), "HIGH", 16));
            drafts.add(makeDraft("SRS 实现：" + req.getTitle(),
                    "按 SRS 进行编码实现", base.plusDays(3), base.plusDays(10), "HIGH", 56));
            drafts.add(makeDraft("SRS 单元测试：" + req.getTitle(),
                    "完成单元测试用例编写与执行", base.plusDays(8), base.plusDays(12), "MEDIUM", 24));
        }
        // DRS：拆分为详细设计/单元实现/单元验证 3 个任务
        else if ("DRS".equals(type)) {
            drafts.add(makeDraft("DRS 详细设计：" + req.getTitle(),
                    "完成详细设计文档", base, base.plusDays(2), "HIGH", 12));
            drafts.add(makeDraft("DRS 单元实现：" + req.getTitle(),
                    "完成代码实现", base.plusDays(3), base.plusDays(8), "HIGH", 40));
            drafts.add(makeDraft("DRS 单元验证：" + req.getTitle(),
                    "完成单元测试与集成测试", base.plusDays(7), base.plusDays(10), "MEDIUM", 20));
        }
        // 未知类型：仅生成一个通用任务
        else {
            drafts.add(makeDraft("实现：" + req.getTitle(),
                    "按需求完成实现", base, base.plusDays(5),
                    req.getPriority() != null ? req.getPriority() : "MEDIUM", 32));
        }

        return drafts;
    }

    private TaskDraft makeDraft(String title, String desc, LocalDate start, LocalDate end,
                                String priority, int hours) {
        return new TaskDraft(title, desc, start, end, hours, priority, null, null, null, null);
    }

    /**
     * 查询需求关联的所有任务
     */
    public List<Task> getTasksByRequirement(Long requirementId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getRequirementId, requirementId)
                        .orderByAsc(Task::getStartDate));
    }

    // R92 新增：按项目聚合任务（FR-2.8 资源管理依赖）
    public List<Task> listTasksByProject(Long projectId) {
        return taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                        // 任务关联需求，需求关联项目——用 IN 子查询
                        .inSql(Task::getRequirementId,
                                "SELECT id FROM req_schema.t_requirement WHERE project_id = " + projectId + " AND is_deleted = false")
                        .orderByAsc(Task::getStartDate));
    }

    /**
     * P2 新增：列出项目下"可转化为任务"的需求
     *  条件：项目匹配 + 类型 SRS/DRS + 未基线化 + 尚未拆解过任务 + 未逻辑删除
     *  用于需求→任务转化页前端选单
     */
    public List<Map<String, Object>> listConvertibleRequirements(Long projectId) {
        if (projectId == null) {
            return java.util.Collections.emptyList();
        }
        // 1) 查询所有需求，限定项目 + 类型 + 未基线化
        List<Requirement> reqs = requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
                        .in(Requirement::getRequirementType, "SRS", "DRS")
                        .ne(Requirement::getStatus, "Baseline")
                        .orderByDesc(Requirement::getId));
        if (reqs == null || reqs.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // 2) 收集候选需求 ID，批量判断哪些已拆解过任务
        List<Long> candidateIds = new ArrayList<>();
        for (Requirement r : reqs) {
            if (r.getId() != null) candidateIds.add(r.getId());
        }
        Set<Long> convertedIds = new HashSet<>();
        if (!candidateIds.isEmpty()) {
            List<Task> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>()
                            .select(Task::getRequirementId)
                            .in(Task::getRequirementId, candidateIds));
            if (tasks != null) {
                for (Task t : tasks) {
                    if (t.getRequirementId() != null) {
                        convertedIds.add(t.getRequirementId());
                    }
                }
            }
        }

        // 3) 组装结果
        List<Map<String, Object>> result = new ArrayList<>();
        for (Requirement r : reqs) {
            if (r.getId() == null || convertedIds.contains(r.getId())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("reqCode", r.getRequirementNo());
            m.put("title", r.getTitle());
            m.put("type", r.getRequirementType());
            m.put("status", r.getStatus());
            m.put("priority", r.getPriority());
            result.add(m);
        }
        return result;
    }

    /**
     * 更新任务状态（FR-1.10 双向同步）
     *  - 任务 DONE 时检查兄弟任务，若全部完成则推进需求状态为 InTest
     *  - 任务 BLOCKED 时将需求标记为 Suspect（需关注）
     */
    @Transactional
    public Task updateTaskStatus(Long taskId, String newStatus) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("TSK0101", "任务不存在: id=" + taskId);
        }
        if (task.getRequirementId() == null) {
            // 与需求无关联的任务，直接更新
            task.setStatus(newStatus);
            taskMapper.updateById(task);
            return task;
        }

        task.setStatus(newStatus);
        taskMapper.updateById(task);

        // 双向同步：检查所有兄弟任务
        syncRequirementStatus(task.getRequirementId());

        log.info("任务状态更新: id={}, status={}, requirementId={}",
                taskId, newStatus, task.getRequirementId());
        return task;
    }

    /**
     * 同步需求状态（FR-1.10）
     *  - 全部任务 DONE → 需求状态 InTest
     *  - 任一任务 BLOCKED → 需求状态 Suspect（变更影响标记）
     *  - 任一任务 IN_PROGRESS 或 DONE → 需求状态 InProgress
     */
    @Transactional
    public void syncRequirementStatus(Long requirementId) {
        List<Task> siblings = getTasksByRequirement(requirementId);
        if (siblings.isEmpty()) return;

        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null || "Baseline".equals(req.getStatus())) return; // 基线后不再自动改

        long doneCount = siblings.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long blockedCount = siblings.stream().filter(t -> "BLOCKED".equals(t.getStatus())).count();
        long inProgressCount = siblings.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();

        String oldStatus = req.getStatus();
        String newStatus;
        if (blockedCount > 0) {
            newStatus = "Suspect";
        } else if (doneCount == siblings.size()) {
            newStatus = "InTest";
        } else if (inProgressCount > 0 || doneCount > 0) {
            newStatus = "InProgress";
        } else {
            newStatus = oldStatus;
        }

        if (!oldStatus.equals(newStatus)) {
            req.setStatus(newStatus);
            if ("Suspect".equals(newStatus)) {
                req.setIsSuspect(true);
            }
            requirementMapper.updateById(req);
            log.info("需求状态同步: id={}, {} → {} ({} 任务完成 / {} 任务阻塞)",
                    requirementId, oldStatus, newStatus, doneCount, blockedCount);
        }
    }

    /**
     * 需求转化进度统计
     */
    public Map<String, Object> getRequirementProgress(Long requirementId) {
        List<Task> tasks = getTasksByRequirement(requirementId);
        long total = tasks.size();
        long done = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long inProgress = tasks.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();
        long blocked = tasks.stream().filter(t -> "BLOCKED".equals(t.getStatus())).count();
        long todo = tasks.stream().filter(t -> "TODO".equals(t.getStatus())).count();
        long totalEstimated = tasks.stream().mapToLong(t -> t.getEstimatedHours() != null ? t.getEstimatedHours() : 0).sum();
        long totalActual = tasks.stream().mapToLong(t -> t.getActualHours() != null ? t.getActualHours() : 0).sum();
        double progress = total == 0 ? 0 : Math.round(done * 100.0 / total);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requirementId", requirementId);
        result.put("totalTasks", total);
        result.put("done", done);
        result.put("inProgress", inProgress);
        result.put("blocked", blocked);
        result.put("todo", todo);
        result.put("progress", progress);
        result.put("totalEstimatedHours", totalEstimated);
        result.put("totalActualHours", totalActual);
        return result;
    }

    /**
     * 任务草稿 DTO
     */
    public record TaskDraft(
            String title,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            Integer estimatedHours,
            String priority,
            Long assigneeId,
            String assigneeName,
            Long parentTaskId,
            Long milestoneId
    ) {}
}
