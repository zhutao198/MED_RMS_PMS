package com.zhutao.medrms.requirement.service;

import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.domain.entity.RequirementVersion;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.zhutao.medrms.requirement.mapper.RequirementVersionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RequirementVersionService 单元测试（W2-D1）
 * 覆盖：版本查询/saveVersion 版本号语义化（v1.0→v1.1）/createVersionWithCti JSON 解析+白名单
 *      /bumpMinor 边界
 */
@ExtendWith(MockitoExtension.class)
class RequirementVersionServiceTest {

    @Mock private RequirementVersionMapper versionMapper;
    @Mock private RequirementMapper requirementMapper;

    @InjectMocks private RequirementVersionService service;

    // ============================================================
    // 1. 查询
    // ============================================================

    @Test
    @DisplayName("getVersionsByRequirementId-直接透传 mapper")
    void getVersionsByRequirementId() {
        when(versionMapper.selectByRequirementId(1L)).thenReturn(java.util.List.of(new RequirementVersion()));
        assertEquals(1, service.getVersionsByRequirementId(1L).size());
    }

    @Test
    @DisplayName("getLatestVersion-直接透传 mapper")
    void getLatestVersion() {
        RequirementVersion v = new RequirementVersion();
        v.setVersionNo("v1.5");
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(v);

        assertEquals("v1.5", service.getLatestVersion(1L).getVersionNo());
    }

    // ============================================================
    // 2. saveVersion - 版本号语义化
    // ============================================================

    @Test
    @DisplayName("saveVersion-首次保存 → v1.0")
    void saveVersion_firstTime() {
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(null);

        service.saveVersion(1L, "{}", "init", 100L);

        ArgumentCaptor<RequirementVersion> cap = ArgumentCaptor.forClass(RequirementVersion.class);
        verify(versionMapper).insert(cap.capture());
        assertEquals("v1.0", cap.getValue().getVersionNo());
    }

    @Test
    @DisplayName("saveVersion-已存在 v1.0 → 升 v1.1")
    void saveVersion_minorBump() {
        RequirementVersion latest = new RequirementVersion();
        latest.setVersionNo("v1.0");
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(latest);

        service.saveVersion(1L, "{}", "minor change", 100L);

        ArgumentCaptor<RequirementVersion> cap = ArgumentCaptor.forClass(RequirementVersion.class);
        verify(versionMapper).insert(cap.capture());
        assertEquals("v1.1", cap.getValue().getVersionNo());
    }

    @Test
    @DisplayName("saveVersion-显式指定版本号（major 变更）")
    void saveVersion_explicitVersionNo() {
        service.saveVersion(1L, "{}", "major", "diff", "v2.0", 100L);

        ArgumentCaptor<RequirementVersion> cap = ArgumentCaptor.forClass(RequirementVersion.class);
        verify(versionMapper).insert(cap.capture());
        assertEquals("v2.0", cap.getValue().getVersionNo());
    }

    // ============================================================
    // 3. bumpMinor 边界（包级可见 static 方法）
    // ============================================================

    @Test
    @DisplayName("bumpMinor-null/空 → v1.0")
    void bumpMinor_nullOrEmpty() {
        assertEquals("v1.0", RequirementVersionService.bumpMinor(null));
        assertEquals("v1.0", RequirementVersionService.bumpMinor(""));
    }

    @Test
    @DisplayName("bumpMinor-v1.0→v1.1, v1.9→v1.10, v2.5→v2.6")
    void bumpMinor_increment() {
        assertEquals("v1.1", RequirementVersionService.bumpMinor("v1.0"));
        assertEquals("v1.10", RequirementVersionService.bumpMinor("v1.9"));
        assertEquals("v2.6", RequirementVersionService.bumpMinor("v2.5"));
    }

    @Test
    @DisplayName("bumpMinor-非法格式回退 v1.0")
    void bumpMinor_invalid() {
        assertEquals("v1.0", RequirementVersionService.bumpMinor("vx.y"));
        // "1.0" 无 v 前缀，substring 后是 "1.0"，可正常解析为 major=1,minor=0 → v1.1
        assertEquals("v1.1", RequirementVersionService.bumpMinor("1.0"));
    }

    // ============================================================
    // 4. createVersionWithCti
    // ============================================================

    @Test
    @DisplayName("createVersionWithCti-需求不存在抛 REQ_0101")
    void createVersionWithCti_requirementNotFound() {
        when(requirementMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
            () -> service.createVersionWithCti(99L, "{\"summary\":\"x\"}", 100L));
        assertEquals("RQ0101", ex.getCode());
    }

    @Test
    @DisplayName("createVersionWithCti-顶层 JSON 解析 + 白名单过滤")
    void createVersionWithCti_validJson() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setTitle("test");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(null);

        String summary = "{\"summary\":\"新增 IEC 62304\",\"cti\":[\"IEC 62304\",\"BAD_STD\",\"ISO 14971\"]}";
        service.createVersionWithCti(1L, summary, 100L);

        ArgumentCaptor<RequirementVersion> cap = ArgumentCaptor.forClass(RequirementVersion.class);
        verify(versionMapper).insert(cap.capture());
        // 白名单内 2 个 + BAD_STD 过滤
        String snapshot = cap.getValue().getSnapshot();
        assertTrue(snapshot.contains("IEC 62304"));
        assertTrue(snapshot.contains("ISO 14971"));
        assertFalse(snapshot.contains("BAD_STD"));
        assertEquals("v1.0", cap.getValue().getVersionNo());
    }

    @Test
    @DisplayName("createVersionWithCti-纯文本 fallback 包成 summary")
    void createVersionWithCti_plainText() {
        Requirement r = new Requirement();
        r.setId(1L);
        r.setTitle("test");
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(null);

        service.createVersionWithCti(1L, "这是一段纯文本描述", 100L);

        ArgumentCaptor<RequirementVersion> cap = ArgumentCaptor.forClass(RequirementVersion.class);
        verify(versionMapper).insert(cap.capture());
        assertTrue(cap.getValue().getChangeSummary().contains("这是一段纯文本描述"));
    }

    @Test
    @DisplayName("createVersionWithCti-空 JSON 字符串")
    void createVersionWithCti_empty() {
        Requirement r = new Requirement();
        r.setId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(null);

        // 不应抛
        service.createVersionWithCti(1L, "{}", 100L);

        verify(versionMapper).insert(any(RequirementVersion.class));
    }

    @Test
    @DisplayName("createVersionWithCti-含 previousVersionNo 字段")
    void createVersionWithCti_inheritsPreviousVersion() {
        Requirement r = new Requirement();
        r.setId(1L);
        when(requirementMapper.selectById(1L)).thenReturn(r);

        RequirementVersion prev = new RequirementVersion();
        prev.setVersionNo("v3.2");
        when(versionMapper.selectLatestByRequirementId(1L)).thenReturn(prev);

        service.createVersionWithCti(1L, "{\"summary\":\"y\"}", 100L);

        ArgumentCaptor<RequirementVersion> cap = ArgumentCaptor.forClass(RequirementVersion.class);
        verify(versionMapper).insert(cap.capture());
        assertTrue(cap.getValue().getSnapshot().contains("v3.2"));
        assertEquals("v3.3", cap.getValue().getVersionNo());
    }
}
