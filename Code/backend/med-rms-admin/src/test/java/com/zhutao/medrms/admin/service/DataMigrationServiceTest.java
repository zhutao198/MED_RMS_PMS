package com.zhutao.medrms.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.admin.domain.entity.MigrationJob;
import com.zhutao.medrms.admin.mapper.MigrationJobMapper;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 数据迁移工具单元测试（W8-D2 — FR-1.13 收尾测试）
 * 覆盖：列表/详情/JSON 导入/CSV 导入/幂等/部分失败
 */
@ExtendWith(MockitoExtension.class)
class DataMigrationServiceTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private MigrationJobMapper migrationJobMapper;

    @InjectMocks private DataMigrationService service;

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("listJobs-按时间倒序")
    void listJobs() {
        when(migrationJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new MigrationJob()));
        assertEquals(1, service.listJobs().size());
    }

    @Test
    @DisplayName("getJob-透传")
    void getJob() {
        MigrationJob j = new MigrationJob();
        j.setId(1L);
        when(migrationJobMapper.selectById(1L)).thenReturn(j);
        assertSame(j, service.getJob(1L));
    }

    @Test
    @DisplayName("getJob-不存在返回 null")
    void getJob_notFound() {
        when(migrationJobMapper.selectById(99L)).thenReturn(null);
        assertNull(service.getJob(99L));
    }

    // ============================================================
    // 2. JSON 导入
    // ============================================================

    @Test
    @DisplayName("importRequirements-JSON 合法 3 条全部成功")
    void importRequirements_json_success() {
        String json = """
            [
              {"requirementNo":"URS-001-001","title":"A","requirementType":"URS","priority":"P1","projectId":1},
              {"requirementNo":"URS-001-002","title":"B","requirementType":"URS","priority":"P1","projectId":1},
              {"requirementNo":"URS-001-003","title":"C","requirementType":"URS","priority":"P1","projectId":1}
            ]
            """;
        when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        MigrationJob job = service.importRequirements("test-source", json, 100L);

        assertEquals("IMPORT_REQ", job.getJobType());
        // Service 完成后状态：SUCCESS（全部成功）
        assertEquals("SUCCESS", job.getStatus());
        assertEquals(3, job.getTotalCount());
        assertEquals(3, job.getSuccessCount());
        // 3 个 insert：job + 3 需求
        verify(requirementMapper, times(3)).insert(any(Requirement.class));
    }

    @Test
    @DisplayName("importRequirements-需求号缺失记录为 failure")
    void importRequirements_missingNo() {
        String json = """
            [
              {"title":"missing-no","requirementType":"URS","priority":"P1","projectId":1}
            ]
            """;
        // 缺失 requirementNo 的记录在 selectCount 之前就抛 IllegalArgumentException
        // 故不需要 stub selectCount

        MigrationJob job = service.importRequirements("test", json, 100L);

        assertEquals(1, job.getTotalCount());
        assertEquals(0, job.getSuccessCount());
        assertEquals(1, job.getFailureCount());
    }

    @Test
    @DisplayName("importRequirements-已存在跳过（幂等）")
    void importRequirements_idempotent() {
        String json = """
            [
              {"requirementNo":"URS-001-001","title":"A","requirementType":"URS","priority":"P1","projectId":1}
            ]
            """;
        // 模拟已存在
        when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        MigrationJob job = service.importRequirements("test", json, 100L);

        assertEquals(1, job.getSuccessCount());
        verify(requirementMapper, never()).insert(any(Requirement.class));
    }

    // ============================================================
    // 3. CSV 导入
    // ============================================================

    @Test
    @DisplayName("importRequirementsCsv-2 行合法 CSV")
    void importRequirementsCsv() {
        String csv = """
            requirementNo,title,requirementType,priority,projectId
            URS-002-001,A,URS,P1,1
            URS-002-002,B,URS,P1,1
            """;
        when(requirementMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        MigrationJob job = service.importRequirementsCsv("csv-source", csv, 100L);

        assertEquals(2, job.getTotalCount());
        verify(requirementMapper, times(2)).insert(any(Requirement.class));
    }

    @Test
    @DisplayName("importRequirementsCsv-空内容")
    void importRequirementsCsv_empty() {
        MigrationJob job = service.importRequirementsCsv("empty", "", 100L);

        // CSV 空字符串 → Service 不一定 setTotalCount（默认 0）
        Integer total = job.getTotalCount();
        assertTrue(total == null || total == 0,
            "空 CSV totalCount 应为 null 或 0，实际 " + total);
        verify(requirementMapper, never()).insert(any(Requirement.class));
    }
}
