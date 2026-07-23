-- R219 v1.75: 签名意图分级通知状态字段
-- 三个时间戳字段实现分级通知幂等（避免重复通知）

ALTER TABLE esign_schema.t_signature_intent
    ADD COLUMN IF NOT EXISTS notified_5min_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS notified_1min_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS notified_expired_at TIMESTAMP;

COMMENT ON COLUMN esign_schema.t_signature_intent.notified_5min_at IS 'R219 T-5min 通知发送时间（幂等）';
COMMENT ON COLUMN esign_schema.t_signature_intent.notified_1min_at IS 'R219 T-1min 通知发送时间（幂等）';
COMMENT ON COLUMN esign_schema.t_signature_intent.notified_expired_at IS 'R219 T+0min 过期通知发送时间（幂等）';
