package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.Task;
import com.zhutao.medrms.project.mapper.TaskMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RequirementTaskService 单元测试（W2-D7）
 * 覆盖：convertRequirementToTasks（FR-0.17 基线保护/防重复/状态推进）
 *      /generateDrafts（URS/PRS/SRS/DRS 4 类草稿生成）
 *      /updateTaskStatus（TODO→IN_PROGRESS→DONE）
 *      /syncRequirementStatus / getRequirementProgress
 */
@ExtendWith(MockitoExtension.class)
class RequirementTaskServiceTest {

    @Mock private TaskMapper taskMapper;
    @Mock private RequirementMapper requirementMapper;

    @InjectMocks private RequirementTaskService service;

    // ============================================================
    // 1. convertRequirementToTasks
    // ============================================================

    @Test
    @DisplayName("convertRequirementToTasks-需求不存在抛 REQ0101")
    void convert_requirementNotFound() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.convertRequirementToTasks(99L, List.of(makeDraft("t"))));
        assertEquals("REQ0101", ex.getCode());
    }

    @Test
    @DisplayName("convertRequirementToTasks-已 Baseline 禁止拆解（FR-0.17）")
    void convert_baselineForbidden() {
        Requirement r = req(1L, "SRS", "Baseline");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.convertRequirementToTasks(1L, List.of(makeDraft("t"))));
        assertTrue(ex.getMessage().contains("FR-0.17"));
    }

    @Test
    @DisplayName("convertRequirementToTasks-已存在任务禁止重复")
    void convert_duplicateTasks() {
        Requirement r = req(1L, "SRS", "Approved");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.convertRequirementToTasks(1L, List.of(makeDraft("t"))));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("convertRequirementToTasks-空 drafts 抛错")
    void convert_emptyDrafts() {
        Requirement r = req(1L, "SRS", "Approved");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThrows(BusinessException.class, () -> service.convertRequirementToTasks(1L, null));
        assertThrows(BusinessException.class, () -> service.convertRequirementToTasks(1L, List.of()));
    }

    @Test
    @DisplayName("convertRequirementToTasks-成功 + Draft→InProgress 推进")
    void convert_ok() {
        Requirement r = req(1L, "SRS", "Draft");
        r.setProjectId(10L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        List<Task> result = service.convertRequirementToTasks(1L, List.of(makeDraft("task1"), makeDraft("task2")));

        assertEquals(2, result.size());
        verify(taskMapper, times(2)).insert(any(Task.class));
        verify(requirementMapper).updateById(r);
        assertEquals("InProgress", r.getStatus());
    }

    @Test
    @DisplayName("convertRequirementToTasks-任务编号 TASK-XXXXXX 自增")
    void convert_taskNo() {
        Requirement r = req(1L, "SRS", "Approved");
        r.setProjectId(10L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 99L);

        service.convertRequirementToTasks(1L, List.of(makeDraft("t1"), makeDraft("t2")));

        ArgumentCaptor<Task> cap = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper, times(2)).insert(cap.capture());
        assertEquals("TASK-000100", cap.getAllValues().get(0).getTaskNo());
        assertEquals("TASK-000101", cap.getAllValues().get(1).getTaskNo());
    }

    // ============================================================
    // 2. generateDrafts（4 类）
    // ============================================================

    @Test
    @DisplayName("generateDrafts-URS → 1 个任务草稿")
    void generate_urs() {
        Requirement r = req(1L, "URS", "Draft");
        when(requirementMapper.selectById(1L)).thenReturn(r);

        List<RequirementTaskService.TaskDraft> drafts = service.generateDrafts(1L);

        assertEquals(1, drafts.size());
        assertTrue(drafts.get(0).title().contains("需求分析"));
    }

    @Test
    @DisplayName("generateDrafts-SRS → 3 个任务草稿")
    void generate_srs() {
        Requirement r = req(2L, "SRS", "Draft");
        when(requirementMapper.selectById(2L)).thenReturn(r);

        List<RequirementTaskService.TaskDraft> drafts = service.generateDrafts(2L);

        assertEquals(3, drafts.size());
        assertTrue(drafts.stream().anyMatch(d -> d.title().contains("设计")));
        assertTrue(drafts.stream().anyMatch(d -> d.title().contains("实现")));
        assertTrue(drafts.stream().anyMatch(d -> d.title().contains("单元测试")));
    }

    @Test
    @DisplayName("generateDrafts-DRS → 3 个任务草稿")
    void generate_drs() {
        Requirement r = req(3L, "DRS", "Draft");
        when(requirementMapper.selectById(3L)).thenReturn(r);

        List<RequirementTaskService.TaskDraft> drafts = service.generateDrafts(3L);

        assertEquals(3, drafts.size());
        assertTrue(drafts.stream().anyMatch(d -> d.title().contains("详细设计")));
        assertTrue(drafts.stream().anyMatch(d -> d.title().contains("单元实现")));
        assertTrue(drafts.stream().anyMatch(d -> d.title().contains("单元验证")));
    }

    // ============================================================
    // 3. updateTaskStatus
    // ============================================================

    @Test
    @DisplayName("updateTaskStatus-任务不存在抛 REQ0101")
    void updateTask_notFound() {
        when(taskMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.updateTaskStatus(99L, "DONE"));
    }

    @Test
    @DisplayName("updateTaskStatus-正常状态切换")
    void updateTask_ok() {
        Task t = new Task();
        t.setId(1L);
        t.setStatus("TODO");
        when(taskMapper.selectById(1L)).thenReturn(t);

        Task result = service.updateTaskStatus(1L, "IN_PROGRESS");

        assertEquals("IN_PROGRESS", result.getStatus());
    }

    // ============================================================
    // 4. syncRequirementStatus / getRequirementProgress
    // ============================================================

    @Test
    @DisplayName("syncRequirementStatus-所有任务 DONE → 需求 InTest")
    void sync_allDone() {
        Requirement r = req(1L, "SRS", "InProgress");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            task("DONE"), task("DONE")
        ));

        service.syncRequirementStatus(1L);

        verify(requirementMapper).updateById(r);
        assertEquals("InTest", r.getStatus());
    }

    @Test
    @DisplayName("getRequirementProgress-返回 done/inProgress/total 统计")
    void getProgress() {
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
            task("DONE"), task("IN_PROGRESS"), task("TODO")
        ));

        Map<String, Object> progress = service.getRequirementProgress(1L);

        assertEquals(3L, progress.get("totalTasks"));
        assertEquals(1L, progress.get("done"));
        assertEquals(1L, progress.get("inProgress"));
    }

    // ============================================================
    // helper
    // ============================================================

    private Requirement req(Long id, String type, String status) {
        Requirement r = new Requirement();
        r.setId(id);
        r.setRequirementType(type);
        r.setStatus(status);
        r.setTitle("test-" + id);
        r.setPriority("P1");
        return r;
    }

    private RequirementTaskService.TaskDraft makeDraft(String title) {
        return new RequirementTaskService.TaskDraft(
            title, "desc",
            LocalDate.now(), LocalDate.now().plusDays(2),
            8, "HIGH",
            100L, "alice", null, null
        );
    }

    private Task task(String status) {
        Task t = new Task();
        t.setStatus(status);
        t.setTitle("t");
        return t;
    }
}
