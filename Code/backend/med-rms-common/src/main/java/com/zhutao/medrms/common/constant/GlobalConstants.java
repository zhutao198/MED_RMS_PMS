package com.zhutao.medrms.common.constant;

public final class GlobalConstants {

    private GlobalConstants() {}

    public static final String RESPONSE_CODE = "code";
    public static final String RESPONSE_MESSAGE = "message";
    public static final String RESPONSE_DATA = "data";
    public static final String RESPONSE_TIMESTAMP = "timestamp";

    // 成功响应
    public static final int SUCCESS_CODE = 200;
    public static final String SUCCESS_MESSAGE = "success";

    // 分页参数
    public static final String PAGE_PARAM = "page";
    public static final String SIZE_PARAM = "size";
    public static final String SORT_PARAM = "sort";
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    // JWT常量
    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";
    public static final String JWT_USER_ID = "userId";
    public static final String JWT_ROLE = "role";

    // Redis Key前缀
    public static final String REDIS_PREFIX = "medrms:";
    public static final String REDIS_JWT_BLACKLIST = REDIS_PREFIX + "jwt:blacklist:";
    public static final String REDIS_SESSION = REDIS_PREFIX + "session:";
    public static final String REDIS_CAPTCHA = REDIS_PREFIX + "captcha:";
    public static final String REDIS_OTP = REDIS_PREFIX + "otp:";

    // 需求层级
    public static final String REQ_TYPE_URS = "URS";
    public static final String REQ_TYPE_PRS = "PRS";
    public static final String REQ_TYPE_SRS = "SRS";
    public static final String REQ_TYPE_DRS = "DRS";

    // 需求优先级
    public static final String PRIORITY_MUST = "MUST";
    public static final String PRIORITY_SHOULD = "SHOULD";
    public static final String PRIORITY_COULD = "COULD";
    public static final String PRIORITY_WONT = "WONT";

    // 签名含义
    public static final String MEANING_APPROVE = "APPROVE";
    public static final String MEANING_REJECT = "REJECT";
    public static final String MEANING_REVIEW = "REVIEW";
    public static final String MEANING_CONFIRM = "CONFIRM";

    // 变更类型
    public static final String CHANGE_CORRECTIVE = "CORRECTIVE";
    public static final String CHANGE_ADAPTIVE = "ADAPTIVE";
    public static final String CHANGE_PERFECTIVE = "PERFECTIVE";
    public static final String CHANGE_EMERGENCY = "EMERGENCY";

    // 安全分类
    public static final String SAFETY_CLASS_A = "A";
    public static final String SAFETY_CLASS_B = "B";
    public static final String SAFETY_CLASS_C = "C";
}