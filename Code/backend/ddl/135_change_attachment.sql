-- v1.43 P1-6 修复：变更单附件持久化
-- 详细设计: 变更域-详细设计.md §2.5 附件
-- 修复: 详细设计偏差分析报告 §3.6 P1-6
-- 日期: 2026-06-08

SET search_path TO chg_schema;

CREATE TABLE IF NOT EXISTS t_change_attachment (
    id BIGSERIAL PRIMARY KEY,
    change_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_by BIGINT,
    uploaded_by_name VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chg_attach_change ON t_change_attachment(change_id, created_at);
