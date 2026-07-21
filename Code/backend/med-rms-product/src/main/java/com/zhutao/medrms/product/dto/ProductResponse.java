package com.zhutao.medrms.product.dto;

import com.zhutao.medrms.product.domain.entity.Product;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * R199 v1.62: 产品响应 DTO（含时间戳，前端展示用）
 */
@Data
public class ProductResponse {
    private Long id;
    private String productCode;
    private String productName;
    private String productLine;
    private String status;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product p) {
        ProductResponse r = new ProductResponse();
        r.setId(p.getId());
        r.setProductCode(p.getProductCode());
        r.setProductName(p.getProductName());
        r.setProductLine(p.getProductLine());
        r.setStatus(p.getStatus());
        r.setDescription(p.getDescription());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}