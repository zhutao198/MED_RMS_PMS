-- Med-RMS DDL v1.46 BUG #102-#106 修复
-- 1) t_signature_record 补 entity_hash 字段（21 CFR Part 11 §11.10(e) 文档哈希）
-- 2) 新建 t_signature_intent 签名意图表（含过期机制）

ALTER TABLE esign_schema.t_signature_record ADD COLUMN IF NOT EXISTS entity_hash VARCHAR(128);
ALTER TABLE esign_schema.t_signature_record ADD COLUMN IF NOT EXISTS signature_value VARCHAR(128);

CREATE TABLE IF NOT EXISTS esign_schema.t_signature_intent (
    id              BIGSERIAL PRIMARY KEY,
    intent_no       VARCHAR(64) NOT NULL UNIQUE,
    requester_id    BIGINT NOT NULL,
    document_type   VARCHAR(50) NOT NULL,
    document_id     BIGINT NOT NULL,
    intent_code     VARCHAR(50) NOT NULL,
    meaning_code    VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at      TIMESTAMP NOT NULL,
    consumed_at     TIMESTAMP,
    consumed_by     BIGINT,
    signature_id    BIGINT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sig_intent_status ON esign_schema.t_signature_intent(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_sig_intent_requester ON esign_schema.t_signature_intent(requester_id);
CREATE INDEX IF NOT EXISTS idx_sig_intent_entity ON esign_schema.t_signature_intent(document_type, document_id);
