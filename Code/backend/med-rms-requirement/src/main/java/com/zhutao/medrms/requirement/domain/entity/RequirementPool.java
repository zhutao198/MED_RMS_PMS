package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("req_schema.t_requirement_pool")
public class RequirementPool {

    @TableId
    private String id;

    private String source; // CUSTOMER/MARKET/REGULATION/INTERNAL/COMPETITOR

    private String sourceNo; // 原始需求编号/法规条款号

    private String rawDescription; // 原始需求描述

    private String title; // 解析后标题

    private String parsedDescription; // 解析后描述

    private String priority; // MoSCoW: MUST/SHOULD/COULD/WONT

    private String status; // PENDING/PARSED/CONVERTED/REJECTED

    private Long projectId;

    /** R199 v1.62: 适用产品 ID（关联 prd_schema.t_product.id，可空） */
    private Long productId;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long convertedToId; // 转换后的URS ID

    private String conversionNotes;

    private String businessScenario; // 业务场景

    private String competitiveAnalysis; // 竞争分析

    private String rejectionReason; // 拒绝理由

    private String proposer; // 提出人（必填）
}