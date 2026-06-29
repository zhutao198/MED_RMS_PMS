-- v1.43 P1-3 修复：任务依赖（前置任务）持久化
-- 详细设计: 项目域-详细设计.md §2.7 甘特图任务依赖
-- 修复: 详细设计偏差分析报告 §3.6 P1-3
-- 日期: 2026-06-08

SET search_path TO prj_schema;

-- 任务前置依赖（多对多：task <- predecessor）
CREATE TABLE IF NOT EXISTS t_task_predecessor (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    predecessor_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_task_predecessor UNIQUE (task_id, predecessor_id),
    CONSTRAINT chk_no_self_dep CHECK (task_id <> predecessor_id)
);

CREATE INDEX idx_tp_task ON t_task_predecessor(task_id);
CREATE INDEX idx_tp_pred ON t_task_predecessor(predecessor_id);
