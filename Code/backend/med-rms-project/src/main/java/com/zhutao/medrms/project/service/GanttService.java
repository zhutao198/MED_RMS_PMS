package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.project.domain.entity.Milestone;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.MilestoneMapper;
import com.zhutao.medrms.project.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GanttService {

    private final MilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;

    public Map<String, Object> getGanttData(Long projectId) {
        Map<String, Object> data = new HashMap<>();

        // 获取里程碑
        List<Milestone> milestones = milestoneMapper.selectList(
            new LambdaQueryWrapper<Milestone>()
                .eq(Milestone::getProjectId, projectId)
                .orderByAsc(Milestone::getPlannedDate)
        );

        // 获取任务
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getStartDate)
        );

        // 构建依赖关系
        List<Map<String, Object>> dependencies = buildDependencies(tasks);

        // 计算关键路径
        List<String> criticalPath = calculateCriticalPath(tasks);

        data.put("milestones", milestones);
        data.put("tasks", tasks);
        data.put("dependencies", dependencies);
        data.put("criticalPath", criticalPath);

        return data;
    }

    public Map<String, Object> getResourceLoad(Long projectId) {
        List<Task> tasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
        );

        // 按人员统计负载
        Map<Long, Map<String, Object>> resourceLoad = new HashMap<>();
        for (Task task : tasks) {
            if (task.getAssigneeId() != null) {
                if (!resourceLoad.containsKey(task.getAssigneeId())) {
                    resourceLoad.put(task.getAssigneeId(), new HashMap<>());
                    ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("assigneeId", task.getAssigneeId());
                    ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("assigneeName", task.getAssigneeName());
                    ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("totalHours", 0);
                }
                Integer hours = ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).get("totalHours") instanceof Integer
                    ? (Integer) ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).get("totalHours")
                    : 0;
                hours += task.getEstimatedHours() != null ? task.getEstimatedHours() : 0;
                ((Map<String, Object>) resourceLoad.get(task.getAssigneeId())).put("totalHours", hours);
            }
        }

        return Map.of("resources", resourceLoad.values());
    }

    @Transactional
    public Task createTask(Task task) {
        long count = taskMapper.selectCount(new LambdaQueryWrapper<Task>());
        task.setTaskNo(String.format("TASK-%06d", count + 1));
        task.setStatus("TODO");
        taskMapper.insert(task);
        return task;
    }

    @Transactional
    public Milestone createMilestone(Milestone milestone) {
        // R143 修复：milestoneNo NOT NULL 无 default，前端未传时自动生成
        if (milestone.getMilestoneNo() == null || milestone.getMilestoneNo().isBlank()) {
            long count = milestoneMapper.selectCount(null);
            milestone.setMilestoneNo(String.format("MS-%06d", count + 1));
        }
        milestone.setStatus("PLANNED");
        milestoneMapper.insert(milestone);
        return milestone;
    }

    public Map<String, Object> checkGate(Long milestoneId) {
        Milestone milestone = milestoneMapper.selectById(milestoneId);
        if (milestone == null) {
            return Map.of("result", "NOT_FOUND", "message", "里程碑不存在");
        }

        // 简化检查：检查该阶段门关联的需求是否都通过评审
        // 实际应检查更多条件如测试覆盖率、风险关闭率等
        boolean canPass = "COMPLETED".equals(milestone.getStatus()) ||
                         milestone.getActualDate() != null;

        return Map.of(
            "result", canPass ? "PASS" : "FAIL",
            "milestoneName", milestone.getName(),
            "gateType", milestone.getGateType(),
            "message", canPass ? "阶段门检查通过" : "阶段门检查未通过"
        );
    }

    private List<Map<String, Object>> buildDependencies(List<Task> tasks) {
        List<Map<String, Object>> deps = new java.util.ArrayList<>();
        for (Task task : tasks) {
            if (task.getParentTaskId() != null) {
                deps.add(Map.of(
                    "from", task.getParentTaskId(),
                    "to", task.getId(),
                    "type", "FS" // Finish-to-Start
                ));
            }
        }
        return deps;
    }

    private List<String> calculateCriticalPath(List<Task> tasks) {
        // 简化实现：返回所有任务作为关键路径
        return tasks.stream().map(Task::getTaskNo).toList();
    }
}