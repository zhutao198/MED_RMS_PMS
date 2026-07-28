-- R240.2 DATA-035：需求版本/评审表补 FK 约束
-- 原 124_review_version_tables.sql 没有 FK，导致孤儿记录
-- 修复：补 requirement_id → req_schema.t_requirement，reviewer_id → sys_schema.t_user

-- 1. 删除已存在的同名约束（如果有）
ALTER TABLE req_schema.t_requirement_version
    DROP CONSTRAINT IF EXISTS fk_requirement_version_req;
ALTER TABLE req_schema.t_requirement_version
    DROP CONSTRAINT IF EXISTS fk_requirement_version_changer;
ALTER TABLE req_schema.t_review
    DROP CONSTRAINT IF EXISTS fk_review_requirement;
ALTER TABLE req_schema.t_review
    DROP CONSTRAINT IF EXISTS fk_review_reviewer;

-- 2. 添加 FK 约束
-- 需求版本表：requirement_id → req_schema.t_requirement
ALTER TABLE req_schema.t_requirement_version
    ADD CONSTRAINT fk_requirement_version_req
    FOREIGN KEY (requirement_id)
    REFERENCES req_schema.t_requirement(id)
    ON DELETE RESTRICT;

-- 需求版本表：changed_by → sys_schema.t_user
ALTER TABLE req_schema.t_requirement_version
    ADD CONSTRAINT fk_requirement_version_changer
    FOREIGN KEY (changed_by)
    REFERENCES sys_schema.t_user(id)
    ON DELETE SET NULL;

-- 评审表：requirement_id → req_schema.t_requirement
ALTER TABLE req_schema.t_review
    ADD CONSTRAINT fk_review_requirement
    FOREIGN KEY (requirement_id)
    REFERENCES req_schema.t_requirement(id)
    ON DELETE RESTRICT;

-- 评审表：reviewer_id → sys_schema.t_user
ALTER TABLE req_schema.t_review
    ADD CONSTRAINT fk_review_reviewer
    FOREIGN KEY (reviewer_id)
    REFERENCES sys_schema.t_user(id)
    ON DELETE SET NULL;

COMMENT ON CONSTRAINT fk_requirement_version_req ON req_schema.t_requirement_version IS
    'R240.2: 需求被物理删除时阻止（应软删除 is_deleted=true）';
COMMENT ON CONSTRAINT fk_review_requirement ON req_schema.t_review IS
    'R240.2: 需求被物理删除时阻止（应软删除 is_deleted=true）';

SELECT 'R240.2 DDL FK 约束修复完成' AS status;