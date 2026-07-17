-- R198b 需求池增强：新增 proposer 提出人字段
-- 客户要求：提出人（必填），优先通讯录同步，可文本输入
-- 执行日期：2026-07-17

ALTER TABLE req_schema.t_requirement_pool
    ADD COLUMN IF NOT EXISTS proposer VARCHAR(100) NOT NULL DEFAULT '';

COMMENT ON COLUMN req_schema.t_requirement_pool.proposer IS '提出人（必填），优先通讯录同步，可文本输入';

-- 新增来源枚举注释
COMMENT ON COLUMN req_schema.t_requirement_pool.source IS '来源：CUSTOMER/MARKET/REGULATION/INTERNAL/COMPETITOR/EMAIL/FEEDBACK/SUPPORT/OA_REQUIREMENT/OA_COMPLAINT';