package com.zhutao.medrms.compliance.exception;

import com.zhutao.medrms.common.exception.BusinessException;

/**
 * R207: DHF 证据包 PDF 渲染失败异常（FR-1.4）
 */
public class DhfRenderException extends BusinessException {
    public DhfRenderException(String message) {
        super("DHF5001", message);
    }

    public DhfRenderException(String message, Throwable cause) {
        super("DHF5001", message, cause);
    }
}
