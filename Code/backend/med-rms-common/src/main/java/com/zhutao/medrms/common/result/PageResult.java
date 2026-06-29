package com.zhutao.medrms.common.result;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PageResult<T> extends Result<List<T>> {

    private long total;
    private int page;
    private int size;
    private int pages;

    public static <T> PageResult<T> of(List<T> data, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setPages((int) Math.ceil((double) total / size));
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(List.of(), 0, page, size);
    }
}