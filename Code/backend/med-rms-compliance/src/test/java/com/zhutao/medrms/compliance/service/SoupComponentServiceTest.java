package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.compliance.domain.entity.SoupComponent;
import com.zhutao.medrms.compliance.mapper.SoupComponentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SoupComponentServiceTest {

    @Mock
    private SoupComponentMapper soupComponentMapper;

    @InjectMocks
    private SoupComponentService soupComponentService;

    @Test
    void testGetAnomaliesEmpty() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("Test Component");
        component.setComponentCode("TC-001");
        component.setVersion("1.0");
        component.setSupplier("Test Supplier");
        component.setStatus("ACTIVE");
        component.setRiskLevel("LOW");
        component.setCertificationDoc("cert.pdf");
        component.setLastSecurityUpdate(LocalDateTime.now().minusMonths(1));
        component.setLicenseExpiry(LocalDateTime.now().plusYears(1));
        when(soupComponentMapper.selectById(1L)).thenReturn(component);

        List<Map<String, Object>> anomalies = soupComponentService.getAnomalies(1L);

        assertNotNull(anomalies);
        assertTrue(anomalies.isEmpty());
    }

    @Test
    void testGetAnomaliesLicenseExpired() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("Expired Component");
        component.setComponentCode("EC-001");
        component.setStatus("ACTIVE");
        component.setLicenseExpiry(LocalDateTime.now().minusDays(1));
        when(soupComponentMapper.selectById(1L)).thenReturn(component);

        List<Map<String, Object>> anomalies = soupComponentService.getAnomalies(1L);

        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.stream().anyMatch(a -> "LICENSE_EXPIRED".equals(a.get("type"))));
    }

    @Test
    void testGetAnomaliesLicenseExpiringSoon() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("Expiring Component");
        component.setComponentCode("ERC-001");
        component.setStatus("ACTIVE");
        component.setLicenseExpiry(LocalDateTime.now().plusMonths(1));
        when(soupComponentMapper.selectById(1L)).thenReturn(component);

        List<Map<String, Object>> anomalies = soupComponentService.getAnomalies(1L);

        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.stream().anyMatch(a -> "LICENSE_EXPIRING".equals(a.get("type"))));
    }

    @Test
    void testGetAnomaliesSecurityOutdated() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("High Risk Component");
        component.setComponentCode("HRC-001");
        component.setStatus("ACTIVE");
        component.setRiskLevel("HIGH");
        component.setLastSecurityUpdate(LocalDateTime.now().minusMonths(7));
        when(soupComponentMapper.selectById(1L)).thenReturn(component);

        List<Map<String, Object>> anomalies = soupComponentService.getAnomalies(1L);

        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.stream().anyMatch(a -> "SECURITY_OUTDATED".equals(a.get("type"))));
    }

    @Test
    void testGetAnomaliesMissingCertification() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("No Cert Component");
        component.setComponentCode("NCC-001");
        component.setStatus("ACTIVE");
        component.setCertificationDoc(null);
        when(soupComponentMapper.selectById(1L)).thenReturn(component);

        List<Map<String, Object>> anomalies = soupComponentService.getAnomalies(1L);

        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.stream().anyMatch(a -> "MISSING_CERTIFICATION".equals(a.get("type"))));
    }

    @Test
    void testGetAnomaliesMultiple() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("Multi Anomaly Component");
        component.setComponentCode("MAC-001");
        component.setStatus("ACTIVE");
        component.setRiskLevel("HIGH");
        component.setLicenseExpiry(LocalDateTime.now().minusDays(1));
        component.setLastSecurityUpdate(LocalDateTime.now().minusYears(1));
        when(soupComponentMapper.selectById(1L)).thenReturn(component);

        List<Map<String, Object>> anomalies = soupComponentService.getAnomalies(1L);

        assertFalse(anomalies.isEmpty());
        assertTrue(anomalies.size() >= 2);
    }

    @Test
    void testCreateSoupComponent() {
        SoupComponent component = new SoupComponent();
        component.setComponentName("New Component");
        component.setComponentCode("NC-001");
        component.setSupplier("Test Supplier");
        doAnswer(invocation -> {
            SoupComponent c = invocation.getArgument(0);
            c.setId(1L);
            return 1;
        }).when(soupComponentMapper).insert(any(SoupComponent.class));

        SoupComponent result = soupComponentService.create(component);

        assertNotNull(result);
        assertEquals("ACTIVE", result.getStatus());
        verify(soupComponentMapper).insert(any(SoupComponent.class));
    }

    @Test
    void testUpdateSoupComponent() {
        SoupComponent existing = new SoupComponent();
        existing.setId(1L);
        existing.setComponentName("Old Name");
        existing.setStatus("ACTIVE");
        when(soupComponentMapper.selectById(1L)).thenReturn(existing);
        doAnswer(invocation -> 1).when(soupComponentMapper).updateById(any(SoupComponent.class));

        SoupComponent updates = new SoupComponent();
        updates.setComponentName("New Name");

        SoupComponent result = soupComponentService.update(1L, updates);

        assertEquals("New Name", result.getComponentName());
    }

    @Test
    void testRenewLicense() {
        SoupComponent component = new SoupComponent();
        component.setId(1L);
        component.setComponentName("License Component");
        component.setStatus("ACTIVE");
        component.setLicenseExpiry(LocalDateTime.now().minusDays(1));
        when(soupComponentMapper.selectById(1L)).thenReturn(component);
        doAnswer(invocation -> {
            SoupComponent c = invocation.getArgument(0);
            c.setLicenseExpiry(LocalDateTime.now().plusYears(1));
            return 1;
        }).when(soupComponentMapper).updateById(any(SoupComponent.class));

        SoupComponent result = soupComponentService.renewLicense(1L);

        assertNotNull(result.getLicenseExpiry());
        assertTrue(result.getLicenseExpiry().isAfter(LocalDateTime.now()));
    }
}