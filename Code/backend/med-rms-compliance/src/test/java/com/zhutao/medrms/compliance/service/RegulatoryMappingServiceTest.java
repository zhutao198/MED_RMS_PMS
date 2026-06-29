package com.zhutao.medrms.compliance.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.RegulatoryMapping;
import com.zhutao.medrms.compliance.mapper.RegulatoryMappingMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RegulatoryMappingService 单元测试（W10-D3）
 */
@ExtendWith(MockitoExtension.class)
class RegulatoryMappingServiceTest {

    @Mock private RegulatoryMappingMapper mapper;

    @InjectMocks private RegulatoryMappingService service;

    @Test
    @DisplayName("listByProjectId-透传")
    void listByProjectId() {
        when(mapper.selectByProjectId(1L)).thenReturn(List.of(new RegulatoryMapping()));
        assertEquals(1, service.listByProjectId(1L).size());
    }

    @Test
    @DisplayName("listByRegulationType-透传")
    void listByRegulationType() {
        when(mapper.selectByRegulationType("ISO-13485")).thenReturn(List.of());
        assertEquals(0, service.listByRegulationType("ISO-13485").size());
    }

    @Test
    @DisplayName("create-插入")
    void create() {
        RegulatoryMapping m = new RegulatoryMapping();
        m.setRegulationType("ISO-13485");
        m.setClauseNumber("4.1.1");

        RegulatoryMapping result = service.create(m);

        verify(mapper).insert(m);
        assertNotNull(result);
    }

    @Test
    @DisplayName("delete-存在则删除")
    void delete() {
        when(mapper.deleteById(1L)).thenReturn(1);
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }
}
