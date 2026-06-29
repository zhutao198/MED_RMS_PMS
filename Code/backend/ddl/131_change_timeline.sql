-- DDL 131: 变更时间线表（v1.47 P0 修复 BUG #142）
-- 记录变更的完整生命周期事件（创建/提交/分析/审批/执行/完成/取消/签名/OA 调度）
-- 设计依据：chg-mgr-详细设计.md §2 TimelineEntry

CREATE TABLE IF NOT EXISTS chg_schema.t_change_timeline (
    id              BIGSERIAL PRIMARY KEY,
    change_id       BIGINT NOT NULL,
    event           VARCHAR(64) NOT NULL,
    operator_id     BIGINT,
    operator_name   VARCHAR(64),
    details         TEXT,
    signature_id    BIGINT,
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_timeline_change FOREIGN KEY (change_id)
        REFERENCES chg_schema.t_change_request(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_timeline_change ON chg_schema.t_change_timeline(change_id);
CREATE INDEX IF NOT EXISTS idx_timeline_event ON chg_schema.t_change_timeline(event);
CREATE INDEX IF NOT EXISTS idx_timeline_change_time ON chg_schema.t_change_timeline(change_id, timestamp);

COMMENT ON TABLE chg_schema.t_change_timeline IS '变更时间线（v1.47 P0 修复 BUG #142）';
COMMENT ON COLUMN chg_schema.t_change_timeline.event IS '事件类型：CREATED/SUBMITTED/IMPACT_ASSESSED/APPROVED/REJECTED/EXECUTED/EMERGENCY_EXECUTED/VERIFIED/CLOSED/CANCELLED/SIGNED/OA_DISPATCHED';
COMMENT ON COLUMN chg_schema.t_change_timeline.signature_id IS '关联签名 ID（Part 11 电子签名集成）';
