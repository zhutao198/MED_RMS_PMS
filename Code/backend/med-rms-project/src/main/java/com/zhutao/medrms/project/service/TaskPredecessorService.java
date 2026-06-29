package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.domain.entity.TaskPredecessor;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.project.mapper.TaskPredecessorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v1.43 P1-3 修复：任务前置依赖持久化
 * 之前依赖关系只存前端 localStorage，刷新/换设备/多用户协作时丢失。
 * 现在使用 t_task_predecessor 表持久化，并提供环检测保护。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPredecessorService {

    private final TaskPredecessorMapper predecessorMapper;
    private final TaskMapper taskMapper;

    /**
     * 查询某任务的所有前置任务 ID
     */
    public List<Long> listPredecessorIds(Long taskId) {
        return predecessorMapper.selectByTaskId(taskId).stream()
                .map(TaskPredecessor::getPredecessorId)
                .toList();
    }

    /**
     * 覆盖式更新某任务的前置依赖（PUT 语义）
     * 规则：
     *   1) 不允许自依赖
     *   2) 所有 predecessorId 必须存在
     *   3) 不允许形成环（DFS 检测）
     */
    @Transactional
    public List<Long> updatePredecessors(Long taskId, List<Long> predecessorIds) {
        if (taskId == null) {
            throw BusinessException.param("taskId 不能为空");
        }
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("SY0301", "任务不存在: id=" + taskId);
        }
        Set<Long> unique = predecessorIds == null ? Set.of() : new HashSet<>(predecessorIds);
        // 移除自依赖
        unique.remove(taskId);
        if (unique.isEmpty()) {
            predecessorMapper.deleteByTaskId(taskId);
            return List.of();
        }
        // 校验 predecessor 存在
        for (Long pid : unique) {
            if (taskMapper.selectById(pid) == null) {
                throw BusinessException.param("前置任务不存在: id=" + pid);
            }
        }
        // 环检测：从任何新 predecessor 出发能否回到 taskId
        for (Long pid : unique) {
            if (createsCycle(taskId, pid)) {
                throw BusinessException.param("存在循环依赖: task=" + taskId + " pred=" + pid);
            }
        }
        // 覆盖：先删后写
        predecessorMapper.deleteByTaskId(taskId);
        for (Long pid : unique) {
            TaskPredecessor tp = new TaskPredecessor();
            tp.setTaskId(taskId);
            tp.setPredecessorId(pid);
            tp.setCreatedAt(java.time.LocalDateTime.now());
            predecessorMapper.insert(tp);
        }
        log.info("更新任务依赖: taskId={}, predecessors={}", taskId, unique);
        return new ArrayList<>(unique);
    }

    /**
     * 从候选 predecessor 出发做 DFS，检测是否能到达 targetId
     */
    private boolean createsCycle(Long targetId, Long startId) {
        Set<Long> visited = new HashSet<>();
        return dfsReaches(startId, targetId, visited);
    }

    private boolean dfsReaches(Long current, Long target, Set<Long> visited) {
        if (current.equals(target)) return true;
        if (!visited.add(current)) return false;
        List<TaskPredecessor> next = predecessorMapper.selectByTaskId(current);
        for (TaskPredecessor tp : next) {
            if (dfsReaches(tp.getPredecessorId(), target, visited)) return true;
        }
        return false;
    }

    /**
     * 加载项目所有任务的依赖图（taskId -> [predecessorIds]）
     */
    public java.util.Map<Long, List<Long>> loadProjectGraph(Long projectId) {
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId)
        );
        java.util.Map<Long, List<Long>> graph = new java.util.HashMap<>();
        for (Task t : tasks) {
            graph.put(t.getId(), predecessorMapper.selectByTaskId(t.getId()).stream()
                    .map(TaskPredecessor::getPredecessorId).toList());
        }
        return graph;
    }
}
