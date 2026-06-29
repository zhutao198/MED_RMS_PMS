package com.zhutao.medrms.project.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.IpdGate;
import com.zhutao.medrms.project.mapper.IpdGateMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IpdGateService 单元测试（W10-D1）
 */
@ExtendWith(MockitoExtension.class)
class IpdGateServiceTest {

    @Mock private IpdGateMapper gateMapper;

    @InjectMocks private IpdGateService service;

    @Test
    @DisplayName("listByProject-透传")
    void listByProject() {
        when(gateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new IpdGate()));
        assertEquals(1, service.listByProject(1L).size());
    }

    @Test
    @DisplayName("getById-不存在抛 BusinessException")
    void getById_notFound() {
        when(gateMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.getById(99L));
    }

    @Test
    @DisplayName("getById-存在返回")
    void getById_exists() {
        IpdGate g = new IpdGate();
        g.setId(1L);
        when(gateMapper.selectById(1L)).thenReturn(g);
        assertSame(g, service.getById(1L));
    }

    @Test
    @DisplayName("create-插入")
    void create() {
        IpdGate g = new IpdGate();
        g.setProjectId(1L);
        g.setGateNo(1);

        IpdGate result = service.create(g);

        verify(gateMapper).insert(g);
        assertNotNull(result);
    }

    @Test
    @DisplayName("autoCheckGate-返回检查结果 Map（用 0/1 默认参数）")
    void autoCheckGate() {
        // Service 实际签名 11 个参数（Long projectId + Integer 10 个），用全 0 调用
        // autoCheckGate 内部可能不调 gateMapper，无需 stub
        Map<String, Object> result = service.autoCheckGate(1L, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertNotNull(result);
    }
}
