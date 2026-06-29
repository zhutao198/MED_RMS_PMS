package com.zhutao.medrms.esignature.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhutao.medrms.common.result.Result;
import com.zhutao.medrms.esignature.domain.entity.ElectronicSignature;
import com.zhutao.medrms.esignature.domain.entity.SignatureIntent;
import com.zhutao.medrms.esignature.domain.entity.SignatureSettings;
import com.zhutao.medrms.esignature.service.ElectronicSignatureService;
import com.zhutao.medrms.esignature.service.SignatureIntentService;
import com.zhutao.medrms.esignature.service.SignatureSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "电子签名", description = "21 CFR Part 11 电子签名接口")
@RestController
@RequestMapping("/esignature")
@RequiredArgsConstructor
public class ElectronicSignatureController {

    private final ElectronicSignatureService signatureService;
    private final SignatureSettingsService settingsService;
    private final SignatureIntentService intentService;

    // ==================== v1.46 BUG #104：签名意图（Intent）端点 ====================

    @Operation(summary = "创建签名意图（签名前必须先创建）")
    @PostMapping("/intents")
    public Result<SignatureIntent> createIntent(@RequestBody CreateIntentRequest request) {
        // v1.47 兼容：前端旧版本发送 signerId（缺 requesterId）→ 复用为 requesterId
        Long requesterId = request.getRequesterId() != null
                ? request.getRequesterId()
                : request.getSignerId();
        // v1.47 兼容：intentCode 缺省回退到 meaningCode（避免 NOT NULL 违反）
        String intentCode = request.getIntentCode() != null && !request.getIntentCode().isBlank()
                ? request.getIntentCode()
                : request.getMeaningCode();
        return Result.success(intentService.createIntent(
                requesterId,
                request.getDocumentType(),
                request.getDocumentId(),
                intentCode,
                request.getMeaningCode()
        ));
    }

    /**
     * R97 新增：按 signerId(意向申请人) + status 过滤分页查询签名意图。
     * <p>用途：Dashboard "待签字"计数 / SignatureList 待签字列表。<br>
     * 与 /signatures 的区别：本端点查 SignatureIntent（签名意图，含 PENDING/CONSUMED/EXPIRED/CANCELLED 状态），
     * 而 /signatures 查 ElectronicSignature（已完成的签名记录，无 status 字段）。
     */
    @Operation(summary = "查询签名意图列表（按 signerId+status 过滤，支持分页）")
    @GetMapping("/intents")
    public Result<IPage<SignatureIntent>> listIntents(
            @RequestParam(required = false) Long signerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(intentService.listIntents(signerId, status, page, size));
    }

    // R103 A1 新增：按 ID 查询签名意图详情（前端 SignatureIntentDetail.vue 用）
    @Operation(summary = "查询签名意图详情")
    @GetMapping("/intents/{intentId}")
    public Result<SignatureIntent> getIntent(@PathVariable Long intentId) {
        return Result.success(intentService.getById(intentId));
    }

    @Operation(summary = "取消签名意图")
    @PostMapping("/intents/{intentId}/cancel")
    public Result<Void> cancelIntent(@PathVariable Long intentId, @RequestParam Long operatorId) {
        intentService.cancelIntent(intentId, operatorId);
        return Result.success();
    }

    // ==================== 签名端点 ====================

    @Operation(summary = "执行电子签名（必须先创建 Intent）")
    @PostMapping("/sign")
    public Result<ElectronicSignature> sign(@RequestBody SignRequest request) {
        return Result.success(signatureService.sign(
                request.getSignerId(),
                request.getSignerName(),
                request.getIntentId(),
                request.getMeaningCode(),
                request.getDocumentType(),
                request.getDocumentId(),
                request.getDocumentNo(),
                request.getReason(),
                request.getSignatureMethod(),
                request.getIpAddress(),
                request.getSignaturePassword(),
                request.getOtpCode()
        ));
    }

    @Operation(summary = "验证签名")
    @PostMapping("/verify/{signatureId}")
    public Result<Map<String, Object>> verifySignature(@PathVariable Long signatureId) {
        return Result.success(signatureService.verifySignatureWithDetail(signatureId));
    }

    @Operation(summary = "获取实体的签名历史")
    @GetMapping("/entity/{entityType}/{entityId}")
    public Result<List<ElectronicSignature>> getSignaturesForEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        return Result.success(signatureService.getSignaturesForEntity(entityType, entityId));
    }

    @Operation(summary = "使签名失效（需操作人和原因）")
    @PostMapping("/{signatureId}/invalidate")
    public Result<Void> invalidateSignature(@PathVariable Long signatureId,
                                             @RequestParam Long operatorId,
                                             @RequestParam String reason) {
        signatureService.invalidateSignature(signatureId, operatorId, reason);
        return Result.success();
    }

    // ==================== 签名设置端点 ====================

    @Operation(summary = "获取签名设置")
    @GetMapping("/settings/{userId}")
    public Result<SignatureSettings> getSettings(@PathVariable Long userId) {
        return Result.success(settingsService.getSettings(userId));
    }

    @Operation(summary = "更新签名密码")
    @PostMapping("/settings/{userId}/password")
    public Result<SignatureSettings> updatePassword(@PathVariable Long userId,
                                                    @RequestParam(required = false) String currentPwd,
                                                    @RequestParam String newPwd) {
        return Result.success(settingsService.updateSignaturePassword(userId, currentPwd, newPwd));
    }

    @Operation(summary = "启用OTP")
    @PostMapping("/settings/{userId}/otp/enable")
    public Result<SignatureSettings> enableOtp(@PathVariable Long userId, @RequestParam String otpSecret) {
        return Result.success(settingsService.enableOtp(userId, otpSecret));
    }

    @Operation(summary = "禁用OTP")
    @PostMapping("/settings/{userId}/otp/disable")
    public Result<SignatureSettings> disableOtp(@PathVariable Long userId) {
        return Result.success(settingsService.disableOtp(userId));
    }

    @Operation(summary = "更新PIN")
    @PostMapping("/settings/{userId}/pin")
    public Result<SignatureSettings> updatePin(@PathVariable Long userId, @RequestParam String newPin) {
        return Result.success(settingsService.updatePin(userId, newPin));
    }

    @Operation(summary = "验证签名密码")
    @PostMapping("/settings/{userId}/verify-password")
    public Result<Boolean> verifyPassword(@PathVariable Long userId, @RequestParam String password) {
        return Result.success(settingsService.verifySignaturePassword(userId, password));
    }

    @Operation(summary = "验证OTP")
    @PostMapping("/settings/{userId}/verify-otp")
    public Result<Boolean> verifyOtp(@PathVariable Long userId, @RequestParam String otpCode) {
        return Result.success(settingsService.verifyOtp(userId, otpCode));
    }

    @Operation(summary = "生成OTP密钥")
    @PostMapping("/settings/{userId}/otp/generate")
    public Result<String> generateOtpSecret(@PathVariable Long userId) {
        return Result.success(settingsService.generateOtpSecret(userId));
    }

    @Operation(summary = "获取OTP URI")
    @GetMapping("/settings/{userId}/otp/uri")
    public Result<String> getOtpUri(@PathVariable Long userId,
                                     @RequestParam(required = false) String account) {
        return Result.success(settingsService.generateOtpUri(userId, account));
    }

    // ==================== 签名查询/重签端点 ====================

    @Operation(summary = "获取签名列表")
    @GetMapping("/signatures")
    public Result<IPage<ElectronicSignature>> listSignatures(
            @RequestParam(required = false) Long signerId,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(signatureService.listSignatures(signerId, entityType, page, size));
    }

    @Operation(summary = "获取签名详情")
    @GetMapping("/signatures/{id}")
    public Result<ElectronicSignature> getSignature(@PathVariable Long id) {
        return Result.success(signatureService.getSignatureById(id));
    }

    @Operation(summary = "重签（需新 Intent）")
    @PostMapping("/signatures/{id}/re-sign")
    public Result<ElectronicSignature> reSign(@PathVariable Long id, @RequestBody ReSignRequest request) {
        return Result.success(signatureService.reSign(
                id,
                request.getSignerId(),
                request.getNewIntentId(),
                request.getReason()
        ));
    }

    // ==================== DTOs ====================

    @lombok.Data
    public static class CreateIntentRequest {
        private Long requesterId;
        /** v1.47 兼容字段：旧版本前端用 signerId，等同 requesterId */
        private Long signerId;
        private String documentType;
        private Long documentId;
        private String intentCode;
        private String meaningCode;
    }

    @lombok.Data
    public static class ReSignRequest {
        private Long signerId;
        private Long newIntentId;
        private String reason;
    }

    @lombok.Data
    public static class SignRequest {
        private Long signerId;
        private String signerName;
        private Long intentId;
        private String meaningCode;
        private String documentType;
        private Long documentId;
        private String documentNo;
        private String reason;
        private String signatureMethod;
        private String ipAddress;
        private String signaturePassword;
        private String otpCode;
    }
}
