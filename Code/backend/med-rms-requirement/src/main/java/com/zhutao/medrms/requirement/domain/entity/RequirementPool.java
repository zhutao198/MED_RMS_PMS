package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("req_schema.t_requirement_pool")
public class RequirementPool {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String source; // CUSTOMER/MARKET/REGULATION/INTERNAL/COMPETITOR

    private String sourceNo; // 原始需求编号/法规条款号

    private String rawDescription; // 原始需求描述

    private String title; // 解析后标题

    private String parsedDescription; // 解析后描述

    private String priority; // MoSCoW: MUST/SHOULD/COULD/WONT

    private String status; // PENDING/PARSED/CONVERTED/REJECTED

    private Long projectId;

    private Long createdBy;

    private LocalDateTime createdAt;

    private Long convertedToId; // 转换后的URS ID

    private String conversionNotes;
}