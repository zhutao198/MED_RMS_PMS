package com.zhutao.medrms.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * R199 v1.62: 产品创建请求 DTO
 */
@Data
public class ProductCreateRequest {

    @NotBlank
    @Size(max = 50, message = "产品编码不超过 50 字符")
    private String productCode;

    @NotBlank
    @Size(max = 200, message = "产品名称不超过 200 字符")
    private String productName;

    /** 字典类型 product_line（统一小写） */
    private String productLine;

    @Pattern(regexp = "ACTIVE|DISCONTINUED|DEVELOPMENT",
             message = "status 必须是 ACTIVE / DISCONTINUED / DEVELOPMENT")
    private String status = "ACTIVE";

    private String description;
}