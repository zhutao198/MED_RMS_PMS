package com.zhutao.medrms.esignature.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("esign_schema.t_signature_settings")
public class SignatureSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** P0-2 修复：签名密码 BCrypt 哈希，禁止 JSON 序列化 */
    @JsonIgnore
    private String signaturePasswordHash;

    /** P0-2 修复：OTP 共享密钥（TOTP / SMS-OTP 公共种子），禁止 JSON 序列化（泄露可绕过双因子） */
    @JsonIgnore
    private String otpSecret;

    private Boolean otpEnabled = false;

    /** P0-2 修复：签名 PIN 哈希，禁止 JSON 序列化 */
    @JsonIgnore
    private String pinHash;

    private Boolean pinEnabled = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted = false;
}