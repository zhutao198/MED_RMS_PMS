-- v1.47 BUG #140 P0 修复：Outbox 事件发件箱表
-- 跨模块事件通过 outbox 模式可靠传输（事务内写 + 异步发布）
CREATE TABLE IF NOT EXISTS public.t_outbox_message (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BIGINT,
    payload TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    last_error TEXT
);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON public.t_outbox_message(status, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON public.t_outbox_message(event_type);
