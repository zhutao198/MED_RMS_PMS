package com.zhutao.medrms.common.exception;

import com.zhutao.medrms.common.constant.GlobalConstants;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Map<String, Object> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: code={}, message={}, path={}", e.getCode(), e.getMessage(), request.getRequestURI());
        return buildResponse(400, e.getCode(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, Object> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("参数校验失败: errors={}, path={}", errors, request.getRequestURI());
        return buildResponse(400, "SY0101", "参数校验失败", errors);
    }

    @ExceptionHandler(BindException.class)
    public Map<String, Object> handleBindException(BindException e, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        e.getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        log.warn("参数绑定失败: errors={}, path={}", errors, request.getRequestURI());
        return buildResponse(400, "SY0101", "参数绑定失败", errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Map<String, Object> handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少请求参数: parameter={}, path={}", e.getParameterName(), request.getRequestURI());
        return buildResponse(400, "SY0101", "缺少必要参数: " + e.getParameterName(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Map<String, Object> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型不匹配: name={}, value={}, expectedType={}, path={}",
                e.getName(), e.getValue(), e.getRequiredType(), request.getRequestURI());
        return buildResponse(400, "SY0101", "参数类型不匹配: " + e.getName(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Map<String, Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方法不支持: method={}, path={}", e.getMethod(), request.getRequestURI());
        return buildResponse(405, "SY0101", "不支持的请求方法: " + e.getMethod(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Map<String, Object> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在: path={}", request.getRequestURI());
        return buildResponse(404, "SY0301", "资源不存在", null);
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e, HttpServletRequest request) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String stackTrace = sw.toString();
        log.error("系统异常: path={}\n{}", request.getRequestURI(), stackTrace);
        return buildResponse(500, "SY0000", "系统异常，请稍后重试", null);
    }

    private Map<String, Object> buildResponse(int httpStatus, String code, String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put(GlobalConstants.RESPONSE_CODE, httpStatus);
        response.put(GlobalConstants.RESPONSE_MESSAGE, message);
        response.put(GlobalConstants.RESPONSE_DATA, data);
        response.put(GlobalConstants.RESPONSE_TIMESTAMP, System.currentTimeMillis());
        response.put("code", code); // 业务错误码
        return response;
    }
}