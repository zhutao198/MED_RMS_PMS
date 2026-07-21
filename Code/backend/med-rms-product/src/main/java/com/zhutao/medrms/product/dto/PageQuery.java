package com.zhutao.medrms.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * R199 v1.62: 通用分页参数（暂放 product 模块，后续可下沉 med-rms-common）
 */
@Data
public class PageQuery {

    @Min(value = 0, message = "page 必须 >= 0")
    private int page = 0;

    @Min(value = 1, message = "size 必须 >= 1")
    @Max(value = 100, message = "size 必须 <= 100")
    private int size = 20;
}