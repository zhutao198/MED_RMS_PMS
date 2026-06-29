package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ReportConfig;
import com.zhutao.medrms.compliance.mapper.ReportConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReportConfigService 单元测试（W12-D3）
 * v1.46 P1-后端-1 报表配置：参数校验 + 唯一性 + 软删除
 */
@ExtendWith(MockitoExtension.class)
class ReportConfigServiceTest {

    @Mock private ReportConfigMapper reportConfigMapper;

    @InjectMocks private ReportConfigService service;

    private ReportConfig newConfig() {
        ReportConfig c = new ReportConfig();
        c.setId(1L);
        c.setName("URS 风险概览");
        c.setReportType("RISK");
        c.setFieldsJson("[\"id\",\"title\"]");
        c.setIsShared(false);
        c.setIsDeleted(false);
        c.setCreatedBy(100L);
        return c;
    }

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("listByCreator-透传 mapper")
    void listByCreator() {
        when(reportConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newConfig()));
        assertEquals(1, service.listByCreator(100L, null, null).size());
    }

    @Test
    @DisplayName("getById-存在则返回")
    void getById_exists() {
        when(reportConfigMapper.selectById(1L)).thenReturn(newConfig());
        assertEquals(1L, service.getById(1L).getId());
    }

    @Test
    @DisplayName("getById-不存在抛 RC0101")
    void getById_notFound() {
        when(reportConfigMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getById(99L));
        assertEquals("RC0101", ex.getCode());
    }

    @Test
    @DisplayName("getById-已删除抛 RC0101")
    void getById_deleted() {
        ReportConfig c = newConfig();
        c.setIsDeleted(true);
        when(reportConfigMapper.selectById(1L)).thenReturn(c);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getById(1L));
        assertEquals("RC0101", ex.getCode());
    }

    // ============================================================
    // 2. 创建（参数校验 + 唯一性）
    // ============================================================

    @Test
    @DisplayName("create-成功：isShared 兜底 false + isDeleted=false")
    void create_ok() {
        when(reportConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ReportConfig input = new ReportConfig();
        input.setName("新配置");
        input.setReportType("TRACEABILITY");
        input.setFieldsJson("[\"id\"]");
        input.setCreatedBy(100L);

        ReportConfig result = service.create(input);

        assertNotNull(result.getIsShared());
        assertFalse(result.getIsShared());
        assertFalse(result.getIsDeleted());
        verify(reportConfigMapper).insert(input);
    }

    @Test
    @DisplayName("create-同名同用户已存在抛 SY0101")
    void create_duplicate() {
        when(reportConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        ReportConfig input = new ReportConfig();
        input.setName("重复");
        input.setReportType("RISK");
        input.setFieldsJson("[\"id\"]");
        input.setCreatedBy(100L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(input));
        assertEquals("SY0101", ex.getCode());
        verify(reportConfigMapper, never()).insert(any(ReportConfig.class));
    }

    @Test
    @DisplayName("create-name 为空抛 SY0101")
    void create_blankName() {
        ReportConfig input = new ReportConfig();
        input.setName(" ");
        input.setReportType("RISK");
        input.setFieldsJson("[\"id\"]");

        assertThrows(BusinessException.class, () -> service.create(input));
    }

    @Test
    @DisplayName("create-fieldsJson 为空抛 SY0101")
    void create_blankFieldsJson() {
        ReportConfig input = new ReportConfig();
        input.setName("x");
        input.setReportType("RISK");
        input.setFieldsJson(" ");

        assertThrows(BusinessException.class, () -> service.create(input));
    }

    // ============================================================
    // 3. 更新
    // ============================================================

    @Test
    @DisplayName("update-部分字段更新")
    void update_partial() {
        ReportConfig existing = newConfig();
        when(reportConfigMapper.selectById(1L)).thenReturn(existing);

        ReportConfig patch = new ReportConfig();
        patch.setName("新名称");
        patch.setDescription("新描述");
        patch.setIsShared(true);

        ReportConfig result = service.update(1L, patch);

        assertEquals("新名称", result.getName());
        assertEquals("新描述", result.getDescription());
        assertTrue(result.getIsShared());
        assertNotNull(result.getUpdatedAt());
        verify(reportConfigMapper).updateById(existing);
    }

    // ============================================================
    // 4. 删除
    // ============================================================

    @Test
    @DisplayName("delete-软删除：isDeleted=true + updatedAt 更新")
    void delete_ok() {
        ReportConfig existing = newConfig();
        when(reportConfigMapper.selectById(1L)).thenReturn(existing);

        service.delete(1L);

        assertTrue(existing.getIsDeleted());
        assertNotNull(existing.getUpdatedAt());
        verify(reportConfigMapper).updateById(existing);
    }
}
