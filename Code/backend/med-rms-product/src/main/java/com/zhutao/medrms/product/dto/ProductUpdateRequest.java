package com.zhutao.medrms.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * R199 v1.62: 产品更新请求 DTO
 * 注：productCode 不可改（partial unique index 限制）
 */
@Data
public class ProductUpdateRequest {

    @NotBlank
    @Size(max = 200, message = "产品名称不超过 200 字符")
    private String productName;

    private String productLine;

    @Pattern(regexp = "ACTIVE|DISCONTINUED|DEVELOPMENT",
             message = "status 必须是 ACTIVE / DISCONTINUED / DEVELOPMENT")
    private String status;

    private String description;
}