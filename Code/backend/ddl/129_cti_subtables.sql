-- =========================================================================
-- 129_cti_subtables.sql
-- v1.47 BUG #131 P0 修复：补齐 CTI 子表（4 张 URS/PRS/SRS/DRS）
-- 设计：标准 CTI 模式：id 主键 + requirementId 外键
-- =========================================================================

CREATE TABLE IF NOT EXISTS req_schema.t_user_requirement (
    id                  BIGSERIAL PRIMARY KEY,
    requirement_id      BIGINT NOT NULL,
    regulation_refs     TEXT,
    acceptance_criteria TEXT,
    origin              VARCHAR(32),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_requirement FOREIGN KEY (requirement_id)
        REFERENCES req_schema.t_requirement(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_user_req_req ON req_schema.t_user_requirement(requirement_id);

CREATE TABLE IF NOT EXISTS req_schema.t_product_requirement (
    id                   BIGSERIAL PRIMARY KEY,
    requirement_id       BIGINT NOT NULL,
    performance_target   TEXT,
    interface_spec_ref   TEXT,
    verification_method  VARCHAR(32),
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_requirement FOREIGN KEY (requirement_id)
        REFERENCES req_schema.t_requirement(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_product_req_req ON req_schema.t_product_requirement(requirement_id);

CREATE TABLE IF NOT EXISTS req_schema.t_system_requirement (
    id                BIGSERIAL PRIMARY KEY,
    requirement_id    BIGINT NOT NULL,
    module_name       VARCHAR(128),
    api_spec          TEXT,
    soup_component_id BIGINT,
    test_case_ids     TEXT,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_system_requirement FOREIGN KEY (requirement_id)
        REFERENCES req_schema.t_requirement(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_system_req_req ON req_schema.t_system_requirement(requirement_id);

CREATE TABLE IF NOT EXISTS req_schema.t_design_requirement (
    id              BIGSERIAL PRIMARY KEY,
    requirement_id  BIGINT NOT NULL,
    implementer     VARCHAR(64),
    code_repo_ref   VARCHAR(256),
    code_branch     VARCHAR(128),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_design_requirement FOREIGN KEY (requirement_id)
        REFERENCES req_schema.t_requirement(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_design_req_req ON req_schema.t_design_requirement(requirement_id);

COMMENT ON TABLE req_schema.t_user_requirement    IS 'URS 用户需求子表（v1.47 BUG #131 标准 CTI）';
COMMENT ON TABLE req_schema.t_product_requirement IS 'PRS 产品需求子表（v1.47 BUG #131 标准 CTI）';
COMMENT ON TABLE req_schema.t_system_requirement  IS ' SRS 软件需求子表（v1.47 BUG #131 标准 CTI）';
COMMENT ON TABLE req_schema.t_design_requirement  IS 'DRS 设计需求子表（v1.47 BUG #131 标准 CTI）';
