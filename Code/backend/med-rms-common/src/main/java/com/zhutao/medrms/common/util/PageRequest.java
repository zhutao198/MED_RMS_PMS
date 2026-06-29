package com.zhutao.medrms.common.util;

import lombok.Data;

/**
 * 分页请求参数
 */
@Data
public class PageRequest {

    private Integer page = 0;
    private Integer size = 20;
    private String sort;

    public int getOffset() {
        return page * size;
    }

    public int getLimit() {
        return size;
    }

    public void normalize() {
        if (page == null || page < 0) {
            page = 0;
        }
        if (size == null || size <= 0) {
            size = 20;
        }
        if (size > 2000) {
            size = 2000;
        }
    }
}