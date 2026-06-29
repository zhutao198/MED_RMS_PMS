package com.zhutao.medrms.compliance.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 哈希链校验结果 (v1.46 P0-后端-8)
 * 用于在链断裂时给出诊断信息（首个断裂点 ID、断裂类型、最后有效 ID）。
 *
 * Why:
 *   历史日志由多次算法迭代写入 (DDL 125 字段重命名 + BUG #93 时间戳一致性 等)，
 *   校验时应能定位具体断裂点，便于审计员评估影响范围与决定是否重建链。
 *   21 CFR Part 11 §11.10(e) 要求审计日志不可悄默损坏。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HashChainVerifyResult {

    /** 整体是否通过 (true = 全链一致) */
    private boolean valid;

    /** 校验的总记录数 */
    private int totalChecked;

    /** 首个断裂点 ID (null 表示无断裂) */
    private Long firstFailureId;

    /** 首个断裂点类型: PREV_HASH_MISMATCH / CURRENT_HASH_MISMATCH */
    private String firstFailureType;

    /** 最后通过校验的记录 ID (null 表示首条就失败) */
    private Long lastValidId;

    /** 诊断消息（中文） */
    private String message;
}
