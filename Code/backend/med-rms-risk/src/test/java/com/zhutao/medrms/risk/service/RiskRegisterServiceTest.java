package com.zhutao.medrms.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.risk.domain.entity.RiskRegister;
import com.zhutao.medrms.risk.mapper.RiskRegisterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RiskRegisterService 单元测试（W2-D7）
 * 覆盖：列表/getById/create/编号生成/风险等级/高风险告警/update/close/acceptRisk
 */
@ExtendWith(MockitoExtension.class)
class RiskRegisterServiceTest {

    @Mock private RiskRegisterMapper riskRegisterMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private RiskRegisterService service;

    // ============================================================
    // 1. list
    // ============================================================

    @Test
    @DisplayName("list-无过滤透传")
    void list_noFilter() {
        when(riskRegisterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(new RiskRegister()));
        assertEquals(1, service.list(null, null, null).size());
    }

    @Test
    @DisplayName("list-空 status/category 不加条件")
    void list_blankFilter() {
        when(riskRegisterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        assertEquals(0, service.list("", "").size());
    }

    // ============================================================
    // 2. getById
    // ============================================================

    @Test
    @DisplayName("getById-不存在抛 RS0101")
    void getById_notFound() {
        when(riskRegisterMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getById(99L));
        assertEquals("RS0101", ex.getCode());
    }

    @Test
    @DisplayName("getById-软删抛 RS0101")
    void getById_deleted() {
        RiskRegister r = new RiskRegister();
        r.setId(1L);
        r.setIsDeleted(true);
        when(riskRegisterMapper.selectById(1L)).thenReturn(r);
        assertThrows(BusinessException.class, () -> service.getById(1L));
    }

    // ============================================================
    // 3. create
    // ============================================================

    @Test
    @DisplayName("create-生成 RSK-XXXXXX 编号 + OPEN 状态")
    void create_ok() {
        RiskRegister r = risk("HIGH", "HIGH", "HIGH", "owner1", 100L);
        when(riskRegisterMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        RiskRegister result = service.create(r);

        ArgumentCaptor<RiskRegister> cap = ArgumentCaptor.forClass(RiskRegister.class);
        verify(riskRegisterMapper).insert(cap.capture());
        RiskRegister saved = cap.getValue();
        assertEquals("RSK-000001", saved.getRiskNo());
        assertEquals("OPEN", saved.getStatus());
        assertEquals("HIGH", saved.getRiskLevel());
        assertEquals("HIGH", result.getRiskLevel());
    }

    @Test
    @DisplayName("create-高风险 owner 触发告警")
    void create_highRisk_alert() {
        RiskRegister r = risk("HIGH", "HIGH", "HIGH", null, 100L);
        when(riskRegisterMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.create(r);

        verify(notificationService).sendRiskAlertNotification(eq(100L), anyString(), eq("HIGH"));
    }

    @Test
    @DisplayName("create-低风险不告警（1*1*1=1 < 8 → LOW）")
    void create_lowNoAlert() {
        RiskRegister r = risk("LOW", "LOW", "LOW", null, 100L);
        when(riskRegisterMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        service.create(r);

        verifyNoInteractions(notificationService);
    }

    // ============================================================
    // 4. update
    // ============================================================

    @Test
    @DisplayName("update-非 HIGH→HIGH 触发告警")
    void update_escalateToHigh() {
        RiskRegister existing = risk("MEDIUM", "MEDIUM", "MEDIUM", null, 100L);
        existing.setId(1L);
        existing.setRiskLevel("MEDIUM");
        when(riskRegisterMapper.selectById(1L)).thenReturn(existing);

        RiskRegister patch = new RiskRegister();
        patch.setSeverity("HIGH");
        patch.setProbability("HIGH");
        patch.setDetectability("HIGH");

        service.update(1L, patch);

        verify(notificationService).sendRiskAlertNotification(eq(100L), any(), eq("HIGH"));
    }

    @Test
    @DisplayName("update-已经是 HIGH 不重复告警")
    void update_alreadyHigh() {
        RiskRegister existing = risk("HIGH", "HIGH", "HIGH", null, 100L);
        existing.setId(1L);
        existing.setRiskLevel("HIGH");
        when(riskRegisterMapper.selectById(1L)).thenReturn(existing);

        RiskRegister patch = new RiskRegister();
        patch.setSeverity("HIGH");

        service.update(1L, patch);

        verifyNoInteractions(notificationService);
    }

    // ============================================================
    // 5. close / acceptRisk
    // ============================================================

    @Test
    @DisplayName("close-状态置 CLOSED + 写 closedAt + closureNote")
    void close_ok() {
        RiskRegister r = risk("MEDIUM", "MEDIUM", "MEDIUM", null, 100L);
        r.setId(1L);
        when(riskRegisterMapper.selectById(1L)).thenReturn(r);

        RiskRegister result = service.close(1L, "已缓解");

        assertEquals("CLOSED", result.getStatus());
        assertEquals("已缓解", result.getClosureNote());
        assertNotNull(result.getClosedAt());
    }

    @Test
    @DisplayName("acceptRisk-状态置 ACCEPTED")
    void acceptRisk_ok() {
        RiskRegister r = risk("HIGH", "HIGH", "HIGH", null, 100L);
        r.setId(1L);
        when(riskRegisterMapper.selectById(1L)).thenReturn(r);

        RiskRegister result = service.acceptRisk(1L);

        assertEquals("ACCEPTED", result.getStatus());
    }

    // ============================================================
    // helper
    // ============================================================

    private RiskRegister risk(String sev, String prob, String det, String title, Long ownerId) {
        RiskRegister r = new RiskRegister();
        r.setRiskTitle(title);
        r.setSeverity(sev);
        r.setProbability(prob);
        r.setDetectability(det);
        r.setOwnerId(ownerId);
        r.setCategory("SOFTWARE");
        return r;
    }
}
