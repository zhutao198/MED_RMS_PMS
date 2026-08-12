package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.domain.entity.ProjectMember;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import com.zhutao.medrms.project.mapper.ProjectMemberMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementStatus;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 需求→任务转化服务 - FR-1.10
 * 功能：
 *  1. 将 SRS/DRS 需求拆解为多个任务
 *  2. 自动按需求类型/优先级生成任务草稿
 *  3. 任务状态更新时双向同步到需求状态
 *  4. 任务阻塞/工时超支联动（PRD §7.7.1 差异 #4/#6）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementTaskService {

    private final TaskMapper taskMapper;
    private final RequirementMapper requirementMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final NotificationService notificationService;

    /** 阻塞超期阈值（天）：PRD §7.7.1 差异 #4 */
    private static final int BLOCK_OVERDUE_DAYS = 3;
    /** 工时超支阈值（实际/预估）：PRD §7.7.1 差异 #6（用户确认用 150% 而非项目 budgetAlarmPct） */
    private static final double OVERTIME_RATIO = 1.5;
    /** 项目成员角色：测试工程师（PRD §7.7.1 通知对象，差异 #3b）。编码须与前端 ProjectMembersAdd.vue 下拉值一致 */
    private static final String ROLE_TESTER = "TESTER";
    /** 项目成员角色：质量工程师（PRD 监督/验证双签，差异 #3b 抄送）。编码须与前端 ProjectMembersAdd.vue 下拉值一致 */
    private static final String ROLE_QA = "QA";

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
        if (RequirementStatus.BASELINE.equals(req.getStatus())) {
            throw BusinessException.stateConflict("已基线化需求不允许再拆解为任务（FR-0.17）");
        }

        // FR-1.10 类型校验：仅 SRS/DRS 可拆解为任务
        String type = req.getRequirementType();
        if (!"SRS".equals(type) && !"DRS".equals(type)) {
            throw BusinessException.param("仅 SRS/DRS 需求可以拆解为任务（FR-1.10），当前类型：" + type);
        }

        // 防重复：检查该需求是否已有任务（R222.3 修正：过滤 is_deleted，软删 task 后允许重新转化）
        // 直接用 QueryWrapper + 字符串 column name（不依赖 Lombok 生成 getter 的 lambda 反射）
        Long existing = taskMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Task>()
                        .eq("requirement_id", requirementId)
                        .eq("is_deleted", false));
        if (existing > 0) {
            throw BusinessException.stateConflict("该需求已存在 " + existing + " 个任务，请勿重复拆解");
        }

        if (taskDrafts == null || taskDrafts.isEmpty()) {
            throw BusinessException.stateConflict("至少需要 1 个任务草稿");
        }

        List<Task> created = new ArrayList<>();
        // R222.3 修复：用自定义原生 SQL 统计「所有」记录（含逻辑删除）的 task_no 数字最大值，
        // 避免生成的编号与已软删但 UNIQUE 仍占用的编号冲突。
        Integer maxNo = taskMapper.selectMaxTaskNoSuffix();
        long baseCount = (maxNo == null) ? 0L : maxNo.longValue();
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

    /**
     * R268：批量需求进度统计（避免 N+1）
     * 一次查 N 个需求的所有 task，内存里按 requirementId 分组计算
     */
    public Map<Long, Map<String, Object>> getRequirementProgressBatch(java.util.List<Long> requirementIds) {
        Map<Long, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        if (requirementIds == null || requirementIds.isEmpty()) return result;
        List<Task> allTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().in(Task::getRequirementId, requirementIds));
        // 按 requirementId 分组
        Map<Long, List<Task>> grouped = allTasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(Task::getRequirementId));
        for (Long rid : requirementIds) {
            List<Task> tasks = grouped.getOrDefault(rid, java.util.Collections.emptyList());
            long total = tasks.size();
            long done = tasks.stream().filter(t -> "DONE".equals(t.getStatus())).count();
            double progress = total == 0 ? 0 : Math.round(done * 100.0 / total);
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("requirementId", rid);
            m.put("totalTasks", total);
            m.put("done", done);
            m.put("progress", progress);
            result.put(rid, m);
        }
        return result;
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
                        .ne(Requirement::getStatus, RequirementStatus.BASELINE)
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
     *  - 任务 DONE 时检查兄弟任务，若全部完成则推进需求状态为 Implemented
     *  - 任务 BLOCKED 时记录 blockedAt（超 3 天未解除则通知 PM + 需求标 Suspect，差异 #4）
     *  - 离开 BLOCKED 时清空 blockedAt
     */
    @Transactional
    public Task updateTaskStatus(Long taskId, String newStatus) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("TSK0101", "任务不存在: id=" + taskId);
        }
        // R240.1 DATA-023：状态机白名单 + 合法迁移校验
        java.util.Set<String> ALLOWED_STATUS = java.util.Set.of("TODO", "IN_PROGRESS", "IN_TEST", "DONE", "BLOCKED", "CANCELLED");
        if (!ALLOWED_STATUS.contains(newStatus)) {
            throw BusinessException.param("非法的任务状态: " + newStatus + "（允许: " + ALLOWED_STATUS + "）");
        }
        // from-state 校验：DONE/CANCELLED 是终态，不可再迁移
        java.util.Set<String> TERMINAL = java.util.Set.of("DONE", "CANCELLED");
        if (TERMINAL.contains(task.getStatus())) {
            throw BusinessException.stateConflict(
                "任务已是终态（" + task.getStatus() + "），不能再变更状态");
        }

        // 差异 #4：进入 BLOCKED 记录起始时间；离开 BLOCKED 清空
        if ("BLOCKED".equals(newStatus) && !"BLOCKED".equals(task.getStatus())) {
            task.setBlockedAt(LocalDateTime.now());
        } else if (!"BLOCKED".equals(newStatus) && "BLOCKED".equals(task.getStatus())) {
            task.setBlockedAt(null);
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

        // 差异 #4：若本次进入 BLOCKED 且已超期（异常场景），立即补通知
        if ("BLOCKED".equals(newStatus) && task.getBlockedAt() != null
                && task.getBlockedAt().plusDays(BLOCK_OVERDUE_DAYS).isBefore(LocalDateTime.now())) {
            markRequirementBlockOverdue(task.getRequirementId());
        }

        log.info("任务状态更新: id={}, status={}, requirementId={}",
                taskId, newStatus, task.getRequirementId());
        return task;
    }

    /**
     * 同步需求状态（FR-1.10）
     *  - 全部任务 DONE → 需求状态 Implemented（已实现，PRD §7.1.9：全部实现后进入"已实现"，
     *    待验证 InTest 由测试工程师在 startTest 中显式触发，而非自动跳过 Implemented）
     *  - 任一任务 BLOCKED → 需求状态 Suspect（变更影响标记）
     *  - 任一任务 IN_PROGRESS 或 DONE → 需求状态 InProgress
     */
    @Transactional
    public void syncRequirementStatus(Long requirementId) {
        List<Task> siblings = getTasksByRequirement(requirementId);
        if (siblings.isEmpty()) return;

        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null || RequirementStatus.BASELINE.equals(req.getStatus())) return; // 基线后不再自动改

        long doneCount = siblings.stream().filter(t -> "DONE".equals(t.getStatus())).count();
        long blockedCount = siblings.stream().filter(t -> "BLOCKED".equals(t.getStatus())).count();
        long inProgressCount = siblings.stream().filter(t -> "IN_PROGRESS".equals(t.getStatus())).count();

        String oldStatus = req.getStatus();
        String newStatus;
        if (blockedCount > 0) {
            newStatus = "Suspect";
        } else if (doneCount == siblings.size()) {
            // P1（流程对齐）：全部任务完成 → 已实现(Implemented)，而非直接 InTest；
            // 待验证(InTest)由测试工程师 startTest 显式触发（FR-1.11），符合 PRD §7.1.9 状态迁移
            newStatus = "Implemented";
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

            // 差异 #4：任务阻塞导致需求标 Suspect 时，若存在超 3 天未解除的阻塞，通知 PM
            if ("Suspect".equals(newStatus)) {
                boolean overdue = siblings.stream()
                        .anyMatch(t -> "BLOCKED".equals(t.getStatus())
                                && t.getBlockedAt() != null
                                && t.getBlockedAt().plusDays(BLOCK_OVERDUE_DAYS).isBefore(LocalDateTime.now()));
                if (overdue) {
                    notifyPmBlockOverdue(req, blockedCount);
                }
            }
            // 差异 #3a+#3b：全部任务完成 → 已实现(Implemented)，通知测试工程师 + 抄送质量工程师
            else if ("Implemented".equals(newStatus)) {
                notifyTesterAndQaOnImplemented(req);
            }
        }
    }

    /**
     * 差异 #3b：需求全部任务完成、状态转为"已实现"(Implemented) 时，
     * 通知测试工程师(TESTER) 安排验证，并抄送质量工程师(QA)（PRD §7.7.1 验证衔接双签）。
     * 兜底策略（方案 A）：若项目未配置已分配 userId 的 TESTER/QA 成员，则回退通知项目经理
     * 并在通知内容中标注"未配置测试工程师/质量工程师"，保证验证衔接不静默断裂。
     */
    private void notifyTesterAndQaOnImplemented(Requirement req) {
        Long projectId = req.getProjectId();
        Project project = projectId != null ? projectMapper.selectById(projectId) : null;
        Long managerId = project != null ? project.getManagerId() : null;
        String managerName = project != null ? project.getManagerName() : "项目经理";

        // 查项目内已分配 userId 的 TESTER / QA 成员（ACTV 状态）
        List<ProjectMember> testers = projectId != null
                ? projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getRole, ROLE_TESTER)
                    .eq(ProjectMember::getStatus, "ACTIVE").isNotNull(ProjectMember::getUserId))
                : List.of();
        List<ProjectMember> qas = projectId != null
                ? projectMemberMapper.selectList(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, projectId).eq(ProjectMember::getRole, ROLE_QA)
                    .eq(ProjectMember::getStatus, "ACTIVE").isNotNull(ProjectMember::getUserId))
                : List.of();

        String reqTitle = req.getTitle();
        String reqNo = req.getRequirementNo();
        String base = String.format(
                "需求【%s】(编号 %s) 的全部任务已完成，状态已更新为「已实现」，待安排验证。", reqTitle, reqNo);

        // 通知测试工程师
        if (!testers.isEmpty()) {
            for (ProjectMember m : testers) {
                notificationService.sendSystemNotification(m.getUserId(), "需求已实现待验证：" + reqNo,
                        base, "REQUIREMENT", req.getId());
            }
        }
        // 抄送质量工程师
        if (!qas.isEmpty()) {
            for (ProjectMember m : qas) {
                notificationService.sendSystemNotification(m.getUserId(), "需求已实现待质量验证：" + reqNo,
                        base, "REQUIREMENT", req.getId());
            }
        }
        // 方案 A 兜底：TESTER / QA 任一缺失 → 回退通知项目经理并标注
        if (testers.isEmpty() || qas.isEmpty()) {
            if (managerId != null) {
                StringBuilder missing = new StringBuilder();
                if (testers.isEmpty()) missing.append("测试工程师");
                if (qas.isEmpty()) {
                    if (missing.length() > 0) missing.append("、");
                    missing.append("质量工程师");
                }
                String content = String.format("%s%n⚠ 项目未配置%s，已转通知%s跟进验证。",
                        base, missing, managerName);
                notificationService.sendSystemNotification(managerId, "需求已实现待验证(兜底)：" + reqNo,
                        content, "REQUIREMENT", req.getId());
                log.warn("需求 {} 已实现，但项目缺失{}，已回退通知 PM {}",
                        req.getId(), missing, managerId);
            } else {
                log.warn("需求 {} 已实现，但项目既未配置测试工程师/质量工程师，也未找到项目经理，跳过通知",
                        req.getId());
            }
        }
    }

    /**
     * 差异 #4：任务阻塞超阈值（默认 3 天）时，将需求标 Suspect 并通知 PM（PRD §7.7.1）。
     * 供 updateTaskStatus / 定时任务在阻塞超期后补触发。
     */
    @Transactional
    public void markRequirementBlockOverdue(Long requirementId) {
        Requirement req = requirementMapper.selectById(requirementId);
        if (req == null) return;
        boolean changed = false;
        if (!"Suspect".equals(req.getStatus())) {
            req.setStatus("Suspect");
            changed = true;
        }
        if (!Boolean.TRUE.equals(req.getIsSuspect())) {
            req.setIsSuspect(true);
            changed = true;
        }
        if (changed) {
            requirementMapper.updateById(req);
        }
        notifyPmBlockOverdue(req, countBlocked(requirementId));
    }

    private void notifyPmBlockOverdue(Requirement req, long blockedCount) {
        Project project = req.getProjectId() != null ? projectMapper.selectById(req.getProjectId()) : null;
        Long managerId = project != null ? project.getManagerId() : null;
        String managerName = project != null ? project.getManagerName() : null;
        if (managerId == null) {
            log.warn("需求 {} 阻塞超期，但未找到对应项目经理，跳过通知", req.getId());
            return;
        }
        String title = "需求阻塞超期预警";
        String content = String.format("需求【%s】(编号 %s) 存在 %d 个任务阻塞已超过 %d 天，请 %s 及时处理。",
                req.getTitle(), req.getRequirementNo(), blockedCount, BLOCK_OVERDUE_DAYS,
                managerName != null ? managerName : "项目经理");
        notificationService.sendSystemNotification(managerId, title, content, "REQUIREMENT", req.getId());
        log.info("需求 {} 阻塞超期已通知 PM {} {}", req.getId(), managerId, managerName);
    }

    private long countBlocked(Long requirementId) {
        return getTasksByRequirement(requirementId).stream()
                .filter(t -> "BLOCKED".equals(t.getStatus())).count();
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
     * 差异 #6：填报/更新任务实际工时（PRD §7.7.1 工时超支联动）。
     * 此前 actualHours 字段存在但全系统无写入入口；本方法提供最小填报入口，
     * 并在 actualHours > estimatedHours × 150% 时将该需求标 Suspect 并通知 PM。
     *
     * @param taskId     任务 ID
     * @param actualHours 实际工时（小时），可为 null 表示清除
     */
    @Transactional
    public Task updateTaskActualHours(Long taskId, Integer actualHours) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BusinessException.notFound("TSK0102", "任务不存在: id=" + taskId);
        }
        task.setActualHours(actualHours);
        taskMapper.updateById(task);

        // 工时超支联动：仅当关联需求且预估工时存在时判断
        if (task.getRequirementId() != null && actualHours != null
                && task.getEstimatedHours() != null && task.getEstimatedHours() > 0) {
            if (actualHours > task.getEstimatedHours() * OVERTIME_RATIO) {
                Requirement req = requirementMapper.selectById(task.getRequirementId());
                if (req != null && !RequirementStatus.BASELINE.equals(req.getStatus())) {
                    boolean changed = false;
                    if (!"Suspect".equals(req.getStatus())) {
                        req.setStatus("Suspect");
                        changed = true;
                    }
                    if (!Boolean.TRUE.equals(req.getIsSuspect())) {
                        req.setIsSuspect(true);
                        changed = true;
                    }
                    if (changed) {
                        requirementMapper.updateById(req);
                    }
                    notifyPmOvertime(req, task, actualHours);
                }
            }
        }
        log.info("任务实际工时更新: id={}, actualHours={}", taskId, actualHours);
        return task;
    }

    private void notifyPmOvertime(Requirement req, Task task, Integer actualHours) {
        Project project = req.getProjectId() != null ? projectMapper.selectById(req.getProjectId()) : null;
        Long managerId = project != null ? project.getManagerId() : null;
        String managerName = project != null ? project.getManagerName() : null;
        if (managerId == null) {
            log.warn("任务 {} 工时超支，但未找到对应项目经理，跳过通知", task.getId());
            return;
        }
        String title = "任务工时超支预警";
        String content = String.format("需求【%s】(编号 %s) 的任务【%s】实际工时 %d 已超过预估 %d 的 %.0f%%，请 %s 关注排期与预算。",
                req.getTitle(), req.getRequirementNo(), task.getTitle(),
                actualHours, task.getEstimatedHours(), OVERTIME_RATIO * 100,
                managerName != null ? managerName : "项目经理");
        notificationService.sendSystemNotification(managerId, title, content, "TASK", task.getId());
        log.info("任务 {} 工时超支已通知 PM {} {}", task.getId(), managerId, managerName);
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
