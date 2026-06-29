package com.zhutao.medrms.project.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.project.domain.entity.ComplianceTemplate;
import com.zhutao.medrms.project.domain.entity.Project;
import com.zhutao.medrms.project.mapper.ComplianceTemplateMapper;
import com.zhutao.medrms.project.mapper.ProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ComplianceTemplateService 单元测试（W12-D2）
 * FR-1.9 合规模板：4 预设 + 自定义 + 应用到项目
 */
@ExtendWith(MockitoExtension.class)
class ComplianceTemplateServiceTest {

    @Mock private ComplianceTemplateMapper templateMapper;
    @Mock private ProjectMapper projectMapper;

    @InjectMocks private ComplianceTemplateService service;

    private ComplianceTemplate newPreset(String code) {
        ComplianceTemplate t = new ComplianceTemplate();
        t.setId(1L);
        t.setCode(code);
        t.setName("NMPA注册项目模板");
        t.setType("PRESET");
        t.setIsActive(true);
        return t;
    }

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("listAll-透传 mapper")
    void listAll() {
        when(templateMapper.selectAllActive())
                .thenReturn(List.of(newPreset("NMPA"), newPreset("ISO13485")));
        assertEquals(2, service.listAll().size());
    }

    @Test
    @DisplayName("getById-存在则返回")
    void getById_exists() {
        when(templateMapper.selectById(1L)).thenReturn(newPreset("NMPA"));
        assertEquals("NMPA", service.getById(1L).getCode());
    }

    @Test
    @DisplayName("getById-不存在抛 TPL0101")
    void getById_notFound() {
        when(templateMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getById(99L));
        assertEquals("TPL0101", ex.getCode());
    }

    @Test
    @DisplayName("getByCode-透传 mapper")
    void getByCode() {
        when(templateMapper.findByCode("NMPA")).thenReturn(newPreset("NMPA"));
        assertNotNull(service.getByCode("NMPA"));
    }

    // ============================================================
    // 2. 自定义模板 CRUD
    // ============================================================

    @Test
    @DisplayName("createCustom-成功：type=CUSTOM + createdBy/createdByName + isActive=true")
    void createCustom_ok() {
        when(templateMapper.findByCode("CUSTOM_001")).thenReturn(null);

        ComplianceTemplate input = new ComplianceTemplate();
        input.setCode("CUSTOM_001");
        input.setName("自定义模板");

        ComplianceTemplate result = service.createCustom(input, 100L, "张三");

        assertEquals("CUSTOM", result.getType());
        assertEquals(100L, result.getCreatedBy());
        assertEquals("张三", result.getCreatedByName());
        assertTrue(result.getIsActive());
        assertNull(result.getId(), "新建时 ID 应被清空以便 INSERT 自增");
        verify(templateMapper).insert(input);
    }

    @Test
    @DisplayName("createCustom-编号已存在抛 SY0401")
    void createCustom_duplicateCode() {
        when(templateMapper.findByCode("NMPA")).thenReturn(newPreset("NMPA"));

        ComplianceTemplate input = new ComplianceTemplate();
        input.setCode("NMPA");
        input.setName("重复");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createCustom(input, 1L, "u"));
        assertEquals("SY0401", ex.getCode());
    }

    @Test
    @DisplayName("updateCustom-自定义模板可更新 name/description/configJson")
    void updateCustom_ok() {
        ComplianceTemplate existing = newPreset("CUSTOM_001");
        existing.setType("CUSTOM");
        when(templateMapper.selectById(1L)).thenReturn(existing);

        ComplianceTemplate patch = new ComplianceTemplate();
        patch.setName("新名称");
        patch.setDescription("新描述");
        patch.setConfigJson("{\"x\":1}");

        ComplianceTemplate result = service.updateCustom(1L, patch);

        assertEquals("新名称", result.getName());
        assertEquals("新描述", result.getDescription());
        assertEquals("{\"x\":1}", result.getConfigJson());
        verify(templateMapper).updateById(existing);
    }

    @Test
    @DisplayName("updateCustom-PRESET 模板不可修改抛 SY0401")
    void updateCustom_presetForbidden() {
        ComplianceTemplate existing = newPreset("NMPA");
        existing.setType("PRESET");
        when(templateMapper.selectById(1L)).thenReturn(existing);

        ComplianceTemplate patch = new ComplianceTemplate();
        patch.setName("试图修改");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateCustom(1L, patch));
        assertEquals("SY0401", ex.getCode());
        verify(templateMapper, never()).updateById(any(ComplianceTemplate.class));
    }

    @Test
    @DisplayName("updateCustom-不存在抛 TPL0101")
    void updateCustom_notFound() {
        when(templateMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateCustom(99L, new ComplianceTemplate()));
        assertEquals("TPL0101", ex.getCode());
    }

    @Test
    @DisplayName("deleteCustom-软删除：isActive=false")
    void deleteCustom_ok() {
        ComplianceTemplate existing = newPreset("CUSTOM_001");
        existing.setType("CUSTOM");
        existing.setIsActive(true);
        when(templateMapper.selectById(1L)).thenReturn(existing);

        service.deleteCustom(1L);

        assertFalse(existing.getIsActive());
        verify(templateMapper).updateById(existing);
    }

    @Test
    @DisplayName("deleteCustom-PRESET 模板不可删除抛 SY0401")
    void deleteCustom_presetForbidden() {
        ComplianceTemplate existing = newPreset("NMPA");
        existing.setType("PRESET");
        when(templateMapper.selectById(1L)).thenReturn(existing);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.deleteCustom(1L));
        assertEquals("SY0401", ex.getCode());
    }

    // ============================================================
    // 3. 应用模板到项目
    // ============================================================

    @Test
    @DisplayName("applyTemplateToProject-成功：关联 templateId + description 加前缀")
    void applyTemplateToProject_ok() {
        Project project = new Project();
        project.setId(1L);
        project.setProjectName("P-001");
        project.setDescription("原描述");
        when(projectMapper.selectById(1L)).thenReturn(project);

        ComplianceTemplate template = newPreset("NMPA");
        template.setId(2L); // mock 返回的模板 ID 应为 2L
        template.setName("NMPA注册项目模板");
        when(templateMapper.selectById(2L)).thenReturn(template);

        Project result = service.applyTemplateToProject(1L, 2L);

        assertEquals(2L, result.getTemplateId());
        assertEquals("NMPA", result.getTemplateCode());
        assertTrue(result.getDescription().startsWith("【NMPA注册项目模板】"));
        verify(projectMapper).updateById(project);
    }

    @Test
    @DisplayName("applyTemplateToProject-项目不存在抛 PRJ0101")
    void applyTemplateToProject_projectNotFound() {
        when(projectMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.applyTemplateToProject(99L, 1L));
        assertEquals("PRJ0101", ex.getCode());
    }

    // ============================================================
    // 4. 预设模板初始化
    // ============================================================

    @Test
    @DisplayName("initPresetTemplates-4 个预设一次性插入")
    void initPresetTemplates() {
        when(templateMapper.findByCode(anyString())).thenReturn(null);

        service.initPresetTemplates();

        // 4 个预设：NMPA / ISO13485 / IEC62304 / FDA510K
        ArgumentCaptor<ComplianceTemplate> captor =
                ArgumentCaptor.forClass(ComplianceTemplate.class);
        verify(templateMapper, times(4)).insert(captor.capture());
        List<ComplianceTemplate> created = captor.getAllValues();
        assertEquals(4, created.size());
        assertTrue(created.stream().allMatch(t -> "PRESET".equals(t.getType())));
    }

    @Test
    @DisplayName("initPresetTemplates-已存在则跳过（幂等）")
    void initPresetTemplates_idempotent() {
        when(templateMapper.findByCode("NMPA")).thenReturn(newPreset("NMPA"));
        when(templateMapper.findByCode("ISO13485")).thenReturn(null);
        when(templateMapper.findByCode("IEC62304")).thenReturn(null);
        when(templateMapper.findByCode("FDA510K")).thenReturn(null);

        service.initPresetTemplates();

        // NMPA 已存在跳过 → 只插入 3 个
        verify(templateMapper, times(3)).insert(any(ComplianceTemplate.class));
    }
}
