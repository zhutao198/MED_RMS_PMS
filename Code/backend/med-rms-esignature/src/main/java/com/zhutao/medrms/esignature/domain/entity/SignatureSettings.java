package com.zhutao.medrms.esignature.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("esign_schema.t_signature_settings")
public class SignatureSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String signaturePasswordHash;

    private String otpSecret;

    private Boolean otpEnabled = false;

    private String pinHash;

    private Boolean pinEnabled = false;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Boolean isDeleted = false;
}