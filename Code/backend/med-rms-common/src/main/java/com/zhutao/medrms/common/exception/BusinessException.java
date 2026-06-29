package com.zhutao.medrms.common.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 错误码格式：模块(2位) + 类别(2位) + 序号(2位)
 * 例如：RQ0101 = Requirement模块 01类型 01序号
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final String message;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    // ========== 通用错误码 ==========
    public static final String SYS_0000 = "SY0000"; // 系统异常
    public static final String SYS_0101 = "SY0101"; // 参数校验失败
    public static final String SYS_0201 = "SY0201"; // 未登录
    public static final String SYS_0202 = "SY0202"; // 无权限
    public static final String SYS_0301 = "SY0301"; // 资源不存在
    public static final String SYS_0401 = "SY0401"; // 状态冲突

    // ========== 需求管理错误码 ==========
    public static final String REQ_0101 = "RQ0101"; // 需求不存在
    public static final String REQ_0102 = "RQ0102"; // 需求状态不允许此操作
    public static final String REQ_0103 = "RQ0103"; // 需求编号已存在
    public static final String REQ_0104 = "RQ0104"; // 上游需求覆盖率不足
    public static final String REQ_0105 = "RQ0105"; // 禁止删除已基线化的需求

    // ========== 变更管理错误码 ==========
    public static final String CHG_0101 = "CH0101"; // 变更申请不存在
    public static final String CHG_0102 = "CH0102"; // 变更状态不允许此操作

    // ========== 电子签名错误码 ==========
    public static final String SIG_0101 = "SG0101"; // 签名密码错误
    public static final String SIG_0102 = "SG0102"; // 签名已存在（重复签名）
    public static final String SIG_0103 = "SG0103"; // 签名验证失败

    // ========== 合规管理错误码 ==========
    public static final String CPL_0101 = "CPL0101"; // 审计日志不存在
    public static final String CPL_0102 = "CPL0102"; // 哈希链校验失败

    // ========== 通用工厂方法 ==========
    public static BusinessException sys(String message) {
        return new BusinessException(SYS_0000, message);
    }

    public static BusinessException param(String message) {
        return new BusinessException(SYS_0101, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(SYS_0201, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(SYS_0202, message);
    }

    public static BusinessException notFound(String code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException stateConflict(String message) {
        return new BusinessException(SYS_0401, message);
    }
}