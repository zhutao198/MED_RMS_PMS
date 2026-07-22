package com.zhutao.medrms.compliance.service;

import com.zhutao.medrms.compliance.domain.entity.RegulatoryMapping;
import com.zhutao.medrms.notification.service.NotificationService;
import com.zhutao.medrms.requirement.domain.entity.Requirement;
import com.zhutao.medrms.requirement.mapper.RequirementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R213 v1.69: 法规更新自动推送影响分析（FR-2.2）
 *
 * 工作流（PRD §7.5.3）：
 *  1. 法规条款被更新 → 触发 notifyRegulationUpdate()
 *  2. 通过 RegulatoryMapping 找出关联的需求（fail-safe，表缺失返回空）
 *  3. 通过 RequirementMapper 找项目创建者
 *  4. NotificationService 发通知
 *  5. 标记受影响需求为"法规影响待评估"（如果有动态字段支持）
 *
 * 法规库使用内存 ConcurrentHashMap（演示版）：
 *  - 实际生产环境应接入独立的法规管理服务（法规部维护）
 *  - 当前实现聚焦影响分析+通知链路，端到端可演示
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegulationImpactService {

    private final RegulatoryMappingService regulatoryMappingService;
    private final RequirementMapper requirementMapper;
    private final NotificationService notificationService;

    // 内存法规库：regulationType → clauseNumber → {title, version, updatedAt}
    private final Map<String, Map<String, RegulationInfo>> regulationLibrary = new ConcurrentHashMap<>();

    /** 法规条目信息 */
    public record RegulationInfo(String title, String version, String description,
                                  LocalDateTime updatedAt, String updatedBy) {}

    /** 影响分析结果 */
    public record ImpactResult(String regulationType, String clauseNumber,
                                 List<Long> affectedRequirementIds, int affectedProjectCount,
                                 List<NotificationRecord> notifications) {}

    /** 通知记录 */
    public record NotificationRecord(Long projectId, Long recipientId, String recipientName,
                                      String title, String content) {}

    /**
     * 初始化内置法规库（NMPA/ISO/IEC/CFR/ISO 14971 等）
     */
    public void initBuiltinRegulations() {
        Map<String, RegulationInfo> nmpa = new ConcurrentHashMap<>();
        nmpa.put("CH5.1", new RegulationInfo("医疗器械软件注册技术审查指导原则", "v2022",
                "软件开发策划要求", LocalDateTime.now(), "法规部"));
        nmpa.put("CH5.2", new RegulationInfo("软件需求分析", "v2022",
                "软件需求分析与可追溯性矩阵要求", LocalDateTime.now(), "法规部"));
        nmpa.put("CH5.3", new RegulationInfo("软件架构设计", "v2022",
                "软件架构设计 + SOUP 组件管理", LocalDateTime.now(), "法规部"));
        regulationLibrary.put("NMPA-2022", nmpa);

        Map<String, RegulationInfo> iso13485 = new ConcurrentHashMap<>();
        iso13485.put("7.3.3", new RegulationInfo("设计开发输入", "v2016",
                "设计输入要求", LocalDateTime.now(), "法规部"));
        iso13485.put("7.3.7", new RegulationInfo("设计开发确认", "v2016",
                "设计确认（含 DHF）要求", LocalDateTime.now(), "法规部"));
        regulationLibrary.put("ISO13485", iso13485);

        Map<String, RegulationInfo> iec62304 = new ConcurrentHashMap<>();
        iec62304.put("5.1", new RegulationInfo("软件开发策划", "v2006+A1:2015",
                "软件开发计划要求", LocalDateTime.now(), "法规部"));
        iec62304.put("5.2", new RegulationInfo("软件需求分析", "v2006+A1:2015",
                "SRS + 追溯矩阵要求", LocalDateTime.now(), "法规部"));
        iec62304.put("7.1", new RegulationInfo("软件风险管理", "v2006+A1:2015",
                "危险源识别 + 风险控制", LocalDateTime.now(), "法规部"));
        regulationLibrary.put("IEC62304", iec62304);

        log.info("R213 初始化内置法规库：{} 个分类，{} 个条款",
                regulationLibrary.size(),
                regulationLibrary.values().stream().mapToInt(Map::size).sum());
    }

    /**
     * 列出法规库（按类型）
     */
    public Map<String, Map<String, RegulationInfo>> listRegulations() {
        return regulationLibrary;
    }

    /**
     * 获取指定法规
     */
    public RegulationInfo getRegulation(String regulationType, String clauseNumber) {
        Map<String, RegulationInfo> clauses = regulationLibrary.get(regulationType);
        return clauses != null ? clauses.get(clauseNumber) : null;
    }

    /**
     * 法规更新触发（FR-2.2 核心方法）
     *
     * @param regulationType  法规类型
     * @param clauseNumber    条款编号
     * @param newVersion      新版本
     * @param updatedBy       更新人
     * @return 影响分析结果（含通知发送记录）
     */
    public ImpactResult notifyRegulationUpdate(String regulationType, String clauseNumber,
                                                 String newVersion, String updatedBy) {
        log.info("R213 法规更新推送: type={}, clause={}, version={}, by={}",
                regulationType, clauseNumber, newVersion, updatedBy);

        // 1. 更新内存法规库
        RegulationInfo oldInfo = getRegulation(regulationType, clauseNumber);
        RegulationInfo newInfo = new RegulationInfo(
            oldInfo != null ? oldInfo.title() : regulationType + "/" + clauseNumber,
            newVersion, "条款内容已更新（演示）", LocalDateTime.now(), updatedBy);
        regulationLibrary.computeIfAbsent(regulationType, k -> new ConcurrentHashMap<>())
                          .put(clauseNumber, newInfo);

        // 2. 影响分析：找出关联的项目需求
        List<Long> affectedReqIds = new ArrayList<>();
        Set<Long> affectedProjectIds = new HashSet<>();
        List<NotificationRecord> notifications = new ArrayList<>();

        try {
            List<RegulatoryMapping> mappings = regulatoryMappingService.listByRegulationType(regulationType);
            for (RegulatoryMapping mapping : mappings) {
                // mappedRequirementIds 字符串，逗号分隔
                String mappedIds = mapping.getMappedRequirementIds();
                if (mappedIds == null || mappedIds.isBlank()) continue;
                for (String s : mappedIds.split(",")) {
                    try {
                        Long reqId = Long.parseLong(s.trim());
                        affectedReqIds.add(reqId);
                        // 查需求获取项目 ID
                        Requirement req = requirementMapper.selectById(reqId);
                        if (req != null && req.getProjectId() != null) {
                            affectedProjectIds.add(req.getProjectId());
                        }
                    } catch (NumberFormatException ignored) { /* skip invalid */ }
                }
            }
        } catch (Exception e) {
            // RegulatoryMapping 表可能不存在，fail-safe
            log.warn("R213 影响分析失败（fail-safe）: {}", e.getMessage());
        }

        // 3. 给每个受影响项目的创建者发通知
        for (Long projectId : affectedProjectIds) {
            try {
                Requirement anyReq = requirementMapper.selectList(
                    new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
                        .eq(Requirement::getIsDeleted, false)
                        .last("LIMIT 1")
                ).stream().findFirst().orElse(null);

                if (anyReq != null && anyReq.getCreatedBy() != null) {
                    String title = "📜 法规更新通知：" + regulationType + " §" + clauseNumber;
                    String content = String.format(
                        "法规条款 [%s %s] 已更新到版本 %s，更新人：%s。请评估对项目的影响并更新相关需求。影响需求数：%d",
                        regulationType, clauseNumber, newVersion, updatedBy, affectedReqIds.size());
                    try {
                        notificationService.sendSystemNotification(
                            anyReq.getCreatedBy(), title, content, "REGULATION", null);
                        notifications.add(new NotificationRecord(
                            projectId, anyReq.getCreatedBy(),
                            anyReq.getCreatedBy().toString(), title, content));
                        log.info("R213 通知已发送: project={}, recipient={}", projectId, anyReq.getCreatedBy());
                    } catch (Exception ne) {
                        log.warn("R213 通知发送失败: {}", ne.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("R213 项目 {} 处理失败: {}", projectId, e.getMessage());
            }
        }

        return new ImpactResult(regulationType, clauseNumber,
                                affectedReqIds, affectedProjectIds.size(),
                                notifications);
    }
}
