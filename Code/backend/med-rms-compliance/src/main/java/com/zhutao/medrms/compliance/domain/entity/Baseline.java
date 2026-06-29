package com.zhutao.medrms.compliance.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基线表（v1.48 P0 #2 修复：迁移到合规域，对齐 compliance-详细设计.md §1）
 */
@Data
@TableName("compliance_schema.t_baseline")
public class Baseline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String baselineNo;

    private String baselineName;

    private String baselineType;

    private Long projectId;

    private String status;

    private Long lockedBy;

    private LocalDateTime lockedAt;

    /** v1.47 BUG #119 P0 修复：双人签名锁定（Part 11 §11.200） */
    private Long lockUser1Id;

    private Long lockSignatureId1;

    private Long lockUser2Id;

    private Long lockSignatureId2;

    private String snapshotData;

    private Boolean isDeleted = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
