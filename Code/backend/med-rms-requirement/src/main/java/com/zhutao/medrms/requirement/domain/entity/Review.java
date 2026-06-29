package com.zhutao.medrms.requirement.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v1.47 BUG #132 P0 修复：需求评审记录
 * 支持多轮评审（round）、最新生效标记（isLatest）、最终结论（finalDecision）
 * 多 reviewer 通过 round 区分；同一需求可能有 N 轮评审，每轮有 K 个 reviewer 各自投票
 */
@Data
@TableName("req_schema.t_review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联需求ID */
    private Long requirementId;

    /** 评审人ID */
    private Long reviewerId;

    /** 评审人姓名（冗余） */
    private String reviewerName;

    /** 评审轮次（从 1 开始；FR-1.4 多轮评审） */
    private Integer round;

    /** 是否当前轮最新评审（仅本轮最后一条 review 为 true） */
    private Boolean isLatest;

    /** 最终结论：APPROVED/REJECTED（仅 round 末位本评审者最终决定后写入） */
    private String finalDecision;

    /** 单次评审结论：APPROVED/REJECTED/PENDING */
    private String decision;

    /** 评审意见 */
    private String comments;

    /** 评审时间 */
    private LocalDateTime reviewedAt;

    /** v1.47 BUG #132 配套：是否系统自动提交（单 reviewer 兼容老逻辑时为 true） */
    private Boolean autoSubmitted;
}
