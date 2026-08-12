package com.zhutao.medrms.common.result;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.zhutao.medrms.common.constant.GlobalConstants;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;
    private long timestamp;

    /** V-3 修复：扩展字段容器，用于承载业务错误码等非主字段 */
    private final Map<String, Object> ext = new HashMap<>();

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(GlobalConstants.SUCCESS_CODE);
        result.setMessage(GlobalConstants.SUCCESS_MESSAGE);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(GlobalConstants.SUCCESS_CODE);
        result.setMessage(message);
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    public static <T> Result<T> error(String code, String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        result.setTimestamp(System.currentTimeMillis());
        // V-3 修复：实际写入 ext 容器，错误响应 JSON 会展开 {"errorCode": "SY0401", ...}
        result.putExt("errorCode", code);
        return result;
    }

    /**
     * V-3 修复：原来仅 return this，ext 字段从未写入，导致 errorCode 等业务码全部丢失。
     * 现正确写入 Map 并支持链式调用。
     */
    public Result<T> putExt(String key, Object value) {
        this.ext.put(key, value);
        return this;
    }

    /**
     * V-3 修复：通过 @JsonAnyGetter 将 ext 平铺到 JSON 顶层，
     * 客户端仍可通过 result.errorCode 访问业务错误码，无需感知 ext 字段。
     */
    @JsonAnyGetter
    public Map<String, Object> getExt() {
        return ext;
    }
}