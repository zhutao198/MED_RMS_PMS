package com.zhutao.medrms.product.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品字典（医疗器械型号管理）
 * R199 v1.62: 新增独立限界上下文，含 21 CFR Part 11 合规字段
 *
 * 表名: prd_schema.t_product
 * 合规：trg_prevent_hard_delete（G16）+ trg_record_hash（G17）
 */
@Data
@TableName("prd_schema.t_product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品型号编码（如 8333、iMEC15），partial unique index 仅未删除时强制唯一 */
    private String productCode;

    /** 产品名称（如 8333 多参数监护仪） */
    private String productName;

    /** 产品线（字典类型 product_line，R199 统一小写） */
    private String productLine;

    /** 状态：ACTIVE / DISCONTINUED / DEVELOPMENT（DB CHECK 约束） */
    private String status;

    /** 产品描述 */
    private String description;

    /** R197 G17：SHA-256 记录校验和（trigger 自动维护） */
    private String recordHash;

    /** 软删除标记 */
    @TableLogic
    private Boolean isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}