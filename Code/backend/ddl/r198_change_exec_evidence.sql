-- P0-1.1 执行证据持久化：ChangeExecution 表加 evidence 字段
ALTER TABLE chg_schema.t_change_execution
    ADD COLUMN IF NOT EXISTS evidence TEXT;

COMMENT ON COLUMN chg_schema.t_change_execution.evidence IS '执行证据 JSON 数组：[{"fileName":"xxx","storagePath":"xxx","contentType":"pdf","fileSize":123}]';