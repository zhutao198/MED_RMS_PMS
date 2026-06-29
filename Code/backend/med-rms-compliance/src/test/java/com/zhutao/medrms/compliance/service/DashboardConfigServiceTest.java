package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.DashboardConfig;
import com.zhutao.medrms.compliance.mapper.DashboardConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DashboardConfigService 单元测试（W12-D3）
 * 仪表盘布局持久化：raw JSON ↔ entity 转换
 * SecurityUtils.getCurrentUserId() 在无 SecurityContext 时返回 null → Service 兜底 0L
 */
@ExtendWith(MockitoExtension.class)
class DashboardConfigServiceTest {

    @Mock private DashboardConfigMapper dashboardConfigMapper;

    @InjectMocks private DashboardConfigService service;

    private DashboardConfigMapper.DashboardConfigRaw newRaw(Long id, Long userId, boolean isDefault) {
        DashboardConfigMapper.DashboardConfigRaw raw =
                new DashboardConfigMapper.DashboardConfigRaw();
        raw.id = id;
        raw.userId = userId;
        raw.layoutJson = "[{\"x\":0,\"y\":0,\"w\":4,\"h\":3,\"i\":\"widget-1\"}]";
        raw.widgetsJson = "[\"kpi-card\",\"risk-matrix\"]";
        raw.isDefault = isDefault;
        raw.updatedAt = OffsetDateTime.now();
        raw.createdAt = OffsetDateTime.now();
        return raw;
    }

    // ============================================================
    // 1. getCurrentUserLayout
    // ============================================================

    @Test
    @DisplayName("getCurrentUserLayout-用户有自己的配置")
    void getCurrentUserLayout_userConfig() {
        when(dashboardConfigMapper.selectRawByUserId(anyLong()))
                .thenReturn(newRaw(1L, 0L, false));

        DashboardConfig result = service.getCurrentUserLayout();

        assertEquals(1L, result.getId());
        assertNotNull(result.getLayoutJson());
        assertEquals(1, result.getLayoutJson().size());
    }

    @Test
    @DisplayName("getCurrentUserLayout-用户无配置但有 default")
    void getCurrentUserLayout_fallbackToDefault() {
        when(dashboardConfigMapper.selectRawByUserId(anyLong())).thenReturn(null);
        when(dashboardConfigMapper.selectRawDefault())
                .thenReturn(newRaw(99L, 0L, true));

        DashboardConfig result = service.getCurrentUserLayout();

        assertEquals(99L, result.getId());
        assertTrue(result.getIsDefault());
    }

    @Test
    @DisplayName("getCurrentUserLayout-无配置无 default 抛 DC0404")
    void getCurrentUserLayout_notFound() {
        when(dashboardConfigMapper.selectRawByUserId(anyLong())).thenReturn(null);
        when(dashboardConfigMapper.selectRawDefault()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getCurrentUserLayout());
        assertEquals("DC0404", ex.getCode());
    }

    // ============================================================
    // 2. listAll
    // ============================================================

    @Test
    @DisplayName("listAll-转换 raw 列表为 entity")
    void listAll() {
        when(dashboardConfigMapper.selectRawAll())
                .thenReturn(List.of(newRaw(1L, 0L, false), newRaw(2L, 100L, true)));

        List<DashboardConfig> result = service.listAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("listAll-空列表")
    void listAll_empty() {
        when(dashboardConfigMapper.selectRawAll()).thenReturn(List.of());
        assertEquals(0, service.listAll().size());
    }

    // ============================================================
    // 3. saveLayout
    // ============================================================

    @Test
    @DisplayName("saveLayout-config 为 null 抛 SY0101")
    void saveLayout_null() {
        assertThrows(BusinessException.class, () -> service.saveLayout(null));
    }

    @Test
    @DisplayName("saveLayout-layoutJson/widgetsJson 都为空抛 SY0101")
    void saveLayout_bothBlank() {
        DashboardConfig input = new DashboardConfig();
        assertThrows(BusinessException.class, () -> service.saveLayout(input));
    }

    @Test
    @DisplayName("saveLayout-新建路径：existing=null → insertRaw")
    void saveLayout_insert() {
        when(dashboardConfigMapper.selectRawByUserId(anyLong())).thenReturn(null);
        when(dashboardConfigMapper.insertRaw(anyLong(), anyString(), anyString(),
                anyBoolean(), any(), any())).thenReturn(1);

        DashboardConfig input = new DashboardConfig();
        input.setLayoutJson(List.of(Map.of("x", 0, "y", 0)));

        DashboardConfig result = service.saveLayout(input);

        verify(dashboardConfigMapper).insertRaw(anyLong(), anyString(), anyString(),
                anyBoolean(), any(), any());
        verify(dashboardConfigMapper, org.mockito.Mockito.never()).updateRaw(anyLong(),
                anyString(), anyString(), anyBoolean(), any());
        assertNotNull(result.getUpdatedAt());
    }

    @Test
    @DisplayName("saveLayout-更新路径：existing 有 id → updateRaw")
    void saveLayout_update() {
        DashboardConfigMapper.DashboardConfigRaw existing = newRaw(10L, 0L, false);
        when(dashboardConfigMapper.selectRawByUserId(anyLong())).thenReturn(existing);
        when(dashboardConfigMapper.updateRaw(anyLong(), anyString(), anyString(),
                anyBoolean(), any())).thenReturn(1);

        DashboardConfig input = new DashboardConfig();
        input.setLayoutJson(List.of(Map.of("x", 1, "y", 1)));

        service.saveLayout(input);

        verify(dashboardConfigMapper).updateRaw(anyLong(), anyString(), anyString(),
                anyBoolean(), any());
        verify(dashboardConfigMapper, org.mockito.Mockito.never()).insertRaw(anyLong(),
                anyString(), anyString(), anyBoolean(), any(), any());
    }

    // ============================================================
    // 4. resetToDefault
    // ============================================================

    @Test
    @DisplayName("resetToDefault-无 default 抛 DC0404")
    void resetToDefault_noDefault() {
        when(dashboardConfigMapper.selectRawDefault()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resetToDefault());
        assertEquals("DC0404", ex.getCode());
    }

    @Test
    @DisplayName("resetToDefault-成功：用户配置复制 default")
    void resetToDefault_ok() {
        when(dashboardConfigMapper.selectRawDefault())
                .thenReturn(newRaw(99L, 0L, true));
        when(dashboardConfigMapper.selectRawByUserId(anyLong())).thenReturn(null);
        when(dashboardConfigMapper.insertRaw(anyLong(), anyString(), anyString(),
                anyBoolean(), any(), any())).thenReturn(1);

        DashboardConfig result = service.resetToDefault();

        assertNotNull(result.getLayoutJson());
        assertFalse(result.getIsDefault()); // 重置后用户配置 isDefault=false
        verify(dashboardConfigMapper).insertRaw(anyLong(), anyString(), anyString(),
                anyBoolean(), any(), any());
    }
}
