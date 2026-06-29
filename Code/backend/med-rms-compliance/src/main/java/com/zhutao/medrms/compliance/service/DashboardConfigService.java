package com.zhutao.medrms.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhutao.medrms.common.exception.BusinessException;
import com.zhutao.medrms.common.util.SecurityUtils;
import com.zhutao.medrms.compliance.domain.entity.DashboardConfig;
import com.zhutao.medrms.compliance.mapper.DashboardConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 仪表盘配置服务
 * 详细设计: 支撑域与通用域-详细设计.md §3.2 DashboardController.saveLayout
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardConfigService {

    private final DashboardConfigMapper dashboardConfigMapper;
    private static final ObjectMapper OM = new ObjectMapper();

    public DashboardConfig getCurrentUserLayout() {
        Long userId = currentUserIdOrZero();
        DashboardConfig config = selectByUserIdRaw(userId);
        if (config == null) {
            config = selectDefaultRaw();
        }
        if (config == null) {
            throw BusinessException.notFound("DC0404", "未找到用户仪表盘配置");
        }
        return config;
    }

    public List<DashboardConfig> listAll() {
        List<DashboardConfig> result = new ArrayList<>();
        try {
            List<DashboardConfigMapper.DashboardConfigRaw> raws = dashboardConfigMapper.selectRawAll();
            for (DashboardConfigMapper.DashboardConfigRaw raw : raws) {
                DashboardConfig c = toEntity(raw);
                if (c != null) result.add(c);
            }
        } catch (Exception e) {
            log.warn("listAll failed: {}", e.getMessage());
        }
        return result;
    }

    @Transactional
    public DashboardConfig saveLayout(DashboardConfig config) {
        if (config == null) {
            throw BusinessException.param("config 不能为空");
        }
        if (config.getLayoutJson() == null && config.getWidgetsJson() == null) {
            throw BusinessException.param("layoutJson/widgetsJson 至少传一个");
        }
        Long userId = currentUserIdOrZero();
        DashboardConfig existing = selectByUserIdRaw(userId);
        if (existing == null) {
            existing = new DashboardConfig();
            existing.setUserId(userId);
            existing.setIsDefault(false);
        }
        if (config.getLayoutJson() != null) existing.setLayoutJson(config.getLayoutJson());
        if (config.getWidgetsJson() != null) existing.setWidgetsJson(config.getWidgetsJson());
        OffsetDateTime now = LocalDateTime.now().atOffset(ZoneOffset.UTC);
        existing.setUpdatedAt(now);
        if (existing.getCreatedAt() == null) {
            existing.setCreatedAt(now);
        }

        String layoutStr = serializeToJson(existing.getLayoutJson());
        String widgetsStr = serializeToJson(existing.getWidgetsJson());

        try {
            if (existing.getId() == null) {
                dashboardConfigMapper.insertRaw(
                    existing.getUserId(), layoutStr, widgetsStr,
                    Boolean.TRUE.equals(existing.getIsDefault()),
                    existing.getUpdatedAt(), existing.getCreatedAt());
            } else {
                dashboardConfigMapper.updateRaw(
                    existing.getId(), layoutStr, widgetsStr,
                    Boolean.TRUE.equals(existing.getIsDefault()),
                    existing.getUpdatedAt());
            }
        } catch (Exception e) {
            log.error("saveLayout DB error: {}", e.getMessage(), e);
            throw e;
        }
        log.info("Dashboard layout saved: userId={}, id={}", userId, existing.getId());
        return existing;
    }

    @Transactional
    public DashboardConfig resetToDefault() {
        DashboardConfig def = selectDefaultRaw();
        if (def == null) {
            throw BusinessException.notFound("DC0404", "未配置默认仪表盘");
        }
        Long userId = currentUserIdOrZero();
        DashboardConfig userCfg = selectByUserIdRaw(userId);
        if (userCfg == null) {
            userCfg = new DashboardConfig();
            userCfg.setUserId(userId);
        }
        userCfg.setLayoutJson(def.getLayoutJson());
        userCfg.setWidgetsJson(def.getWidgetsJson());
        userCfg.setIsDefault(false);
        OffsetDateTime now = LocalDateTime.now().atOffset(ZoneOffset.UTC);
        userCfg.setUpdatedAt(now);
        if (userCfg.getCreatedAt() == null) {
            userCfg.setCreatedAt(now);
        }
        String layoutStr = serializeToJson(userCfg.getLayoutJson());
        String widgetsStr = serializeToJson(userCfg.getWidgetsJson());
        try {
            if (userCfg.getId() == null) {
                dashboardConfigMapper.insertRaw(
                    userCfg.getUserId(), layoutStr, widgetsStr,
                    false, userCfg.getUpdatedAt(), userCfg.getCreatedAt());
            } else {
                dashboardConfigMapper.updateRaw(
                    userCfg.getId(), layoutStr, widgetsStr,
                    false, userCfg.getUpdatedAt());
            }
        } catch (Exception e) {
            log.error("resetToDefault DB error: {}", e.getMessage(), e);
            throw e;
        }
        return userCfg;
    }

    private DashboardConfig selectByUserIdRaw(Long userId) {
        try {
            DashboardConfigMapper.DashboardConfigRaw raw = dashboardConfigMapper.selectRawByUserId(userId);
            return toEntity(raw);
        } catch (Exception e) {
            log.warn("selectByUserIdRaw failed: {}", e.getMessage());
            return null;
        }
    }

    private DashboardConfig selectDefaultRaw() {
        try {
            DashboardConfigMapper.DashboardConfigRaw raw = dashboardConfigMapper.selectRawDefault();
            return toEntity(raw);
        } catch (Exception e) {
            log.warn("selectDefaultRaw failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private DashboardConfig toEntity(DashboardConfigMapper.DashboardConfigRaw raw) {
        if (raw == null) return null;
        DashboardConfig c = new DashboardConfig();
        c.setId(raw.id);
        c.setUserId(raw.userId);
        c.setIsDefault(raw.isDefault);
        c.setUpdatedAt(raw.updatedAt);
        c.setCreatedAt(raw.createdAt);
        try {
            if (raw.layoutJson != null) {
                c.setLayoutJson(OM.readValue(raw.layoutJson, List.class));
            }
            if (raw.widgetsJson != null) {
                c.setWidgetsJson(OM.readValue(raw.widgetsJson, List.class));
            }
        } catch (Exception e) {
            log.warn("layout/widgets deserialize failed: {}", e.getMessage());
        }
        return c;
    }

    private String serializeToJson(Object o) {
        if (o == null) return "[]";
        try {
            return OM.writeValueAsString(o);
        } catch (Exception e) {
            log.warn("serializeToJson failed: {}", e.getMessage());
            return "[]";
        }
    }

    private Long currentUserIdOrZero() {
        Long uid = SecurityUtils.getCurrentUserId();
        return uid == null ? 0L : uid;
    }
}
