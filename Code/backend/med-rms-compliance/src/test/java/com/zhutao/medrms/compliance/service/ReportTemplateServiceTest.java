package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.compliance.domain.entity.ReportTemplate;
import com.zhutao.medrms.compliance.mapper.ReportTemplateMapper;
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
 * ReportTemplateService 单元测试（W2-D5）
 */
@ExtendWith(MockitoExtension.class)
class ReportTemplateServiceTest {

    @Mock private ReportTemplateMapper reportTemplateMapper;

    @InjectMocks private ReportTemplateService service;

    @Test
    @DisplayName("listActive-透传 mapper")
    void listActive() {
        when(reportTemplateMapper.selectActive()).thenReturn(List.of(new ReportTemplate()));
        assertEquals(1, service.listActive().size());
    }

    @Test
    @DisplayName("getByType-null/空 抛 param")
    void getByType_nullOrEmpty() {
        assertThrows(BusinessException.class, () -> service.getByType(null));
        assertThrows(BusinessException.class, () -> service.getByType(""));
        assertThrows(BusinessException.class, () -> service.getByType("   "));
    }

    @Test
    @DisplayName("getByType-不存在抛 RT0404")
    void getByType_notFound() {
        when(reportTemplateMapper.selectByType("NMPA")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getByType("NMPA"));
        assertEquals("RT0404", ex.getCode());
    }

    @Test
    @DisplayName("getByType-找到返回")
    void getByType_ok() {
        ReportTemplate tpl = new ReportTemplate();
        tpl.setType("NMPA");
        when(reportTemplateMapper.selectByType("NMPA")).thenReturn(tpl);

        assertSame(tpl, service.getByType("NMPA"));
    }
}
