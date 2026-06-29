-- ============================================================================
-- Med-RMS DDL v1.1 | 2026-05-22 | PostgreSQL 16
-- 医疗器械需求管理系统 - 数据库定义语言脚本
-- 基于：数据库概要设计 v1.2 | PRD v2.1 | 系统架构 v1.1
-- ============================================================================

-- ============================================================================
-- 0. 数据库创建
-- ============================================================================
-- 注意：在已连接的会话中无法CREATE DATABASE，需在独立连接中执行
-- CREATE DATABASE med_rms_pms WITH ENCODING 'UTF8' LC_COLLATE 'zh_CN.UTF-8' LC_CTYPE 'zh_CN.UTF-8' TEMPLATE template0;
-- 或：CREATE DATABASE med_rms_pms WITH ENCODING 'UTF8' TEMPLATE template0;

-- ============================================================================
-- 1. Schema 创建（9个限界上下文）
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS req_schema;
CREATE SCHEMA IF NOT EXISTS trace_schema;
CREATE SCHEMA IF NOT EXISTS chg_schema;
CREATE SCHEMA IF NOT EXISTS compliance_schema;
CREATE SCHEMA IF NOT EXISTS esign_schema;
CREATE SCHEMA IF NOT EXISTS risk_schema;
CREATE SCHEMA IF NOT EXISTS proj_schema;
CREATE SCHEMA IF NOT EXISTS report_schema;
CREATE SCHEMA IF NOT EXISTS sys_schema;

COMMENT ON SCHEMA req_schema        IS '需求管理限界上下文';
COMMENT ON SCHEMA trace_schema      IS '追溯管理限界上下文';
COMMENT ON SCHEMA chg_schema        IS '变更管理限界上下文';
COMMENT ON SCHEMA compliance_schema IS '合规管理限界上下文';
COMMENT ON SCHEMA esign_schema      IS '电子签名限界上下文';
COMMENT ON SCHEMA risk_schema       IS '风险管理限界上下文';
COMMENT ON SCHEMA proj_schema       IS '项目管理限界上下文';
COMMENT ON SCHEMA report_schema     IS '报表仪表盘限界上下文';
COMMENT ON SCHEMA sys_schema        IS '系统管理限界上下文';

-- ============================================================================
-- 2. 序列创建
-- ============================================================================

-- 需求编号序列（用于触发器自动生成 requirement_no 的序号部分）
CREATE SEQUENCE IF NOT EXISTS req_schema.seq_req_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;

COMMENT ON SEQUENCE req_schema.seq_req_code IS '需求编号自动生成序列';

-- ============================================================================
-- 3. 扩展类型（Domain / ENUM-like）
-- ============================================================================

-- 使用 PostgreSQL DOMAIN 模拟枚举约束（比原生ENUM更灵活，便于扩展）

-- 需求层级
CREATE DOMAIN IF NOT EXISTS req_schema.req_level_type AS TEXT
    CHECK (VALUE IN ('URS', 'PRS', 'SRS', 'DRS'));

-- 需求状态
CREATE DOMAIN IF NOT EXISTS req_schema.req_status_type AS TEXT
    CHECK (VALUE IN ('Draft', 'PendingDecompose', 'Decomposed', 'Submitted', 'InReview',
                     'Approved', 'Rejected', 'PendingVerify', 'Implemented', 'Verified',
                     'Baseline', 'Changed', 'Closed', 'Retired'));

-- 需求优先级（MoSCoW）
CREATE DOMAIN IF NOT EXISTS req_schema.req_priority_type AS TEXT
    CHECK (VALUE IN ('MUST', 'SHOULD', 'COULD', 'WONT'));

-- 安全分类
CREATE DOMAIN IF NOT EXISTS compliance_schema.safety_class_type AS TEXT
    CHECK (VALUE IN ('A', 'B', 'C'));

-- 基线状态
CREATE DOMAIN IF NOT EXISTS compliance_schema.baseline_status_type AS TEXT
    CHECK (VALUE IN ('Draft', 'Locked', 'Unlocked', 'Archived'));

-- 风险等级
CREATE DOMAIN IF NOT EXISTS risk_schema.risk_level_type AS TEXT
    CHECK (VALUE IN ('HIGH', 'MEDIUM', 'LOW'));

-- 变更类型
CREATE DOMAIN IF NOT EXISTS chg_schema.chg_type_type AS TEXT
    CHECK (VALUE IN ('MAJOR', 'NORMAL', 'DOCUMENT', 'EMERGENCY'));

-- 变更状态
CREATE DOMAIN IF NOT EXISTS chg_schema.chg_status_type AS TEXT
    CHECK (VALUE IN ('Draft', 'Analyzing', 'PendingApproval', 'Approved', 'Rejected',
                     'Executing', 'Completed', 'Cancelled'));

-- 变更紧急度
CREATE DOMAIN IF NOT EXISTS chg_schema.chg_urgency_type AS TEXT
    CHECK (VALUE IN ('HIGH', 'MEDIUM', 'LOW'));

-- 影响等级
CREATE DOMAIN IF NOT EXISTS chg_schema.impact_level_type AS TEXT
    CHECK (VALUE IN ('CRITICAL', 'MAJOR', 'MINOR', 'NONE'));

-- 签名意图
CREATE DOMAIN IF NOT EXISTS esign_schema.sign_intent_type AS TEXT
    CHECK (VALUE IN ('approve', 'confirm', 'review', 'release'));

-- 认证方式
CREATE DOMAIN IF NOT EXISTS esign_schema.auth_method_type AS TEXT
    CHECK (VALUE IN ('password', 'otp', 'both'));

-- OTP渠道
CREATE DOMAIN IF NOT EXISTS esign_schema.otp_channel_type AS TEXT
    CHECK (VALUE IN ('email', 'sms'));

-- DCP阶段
CREATE DOMAIN IF NOT EXISTS proj_schema.dcp_stage_type AS TEXT
    CHECK (VALUE IN ('DCP1', 'DCP2', 'DCP3', 'DCP4', 'DCP5'));

-- 项目状态
CREATE DOMAIN IF NOT EXISTS proj_schema.project_status_type AS TEXT
    CHECK (VALUE IN ('Active', 'OnHold', 'Completed', 'Archived'));

-- SOUP状态
CREATE DOMAIN IF NOT EXISTS compliance_schema.soup_status_type AS TEXT
    CHECK (VALUE IN ('active', 'deprecated', 'retired', 'anomaly_reviewed'));

-- 问题报告来源
CREATE DOMAIN IF NOT EXISTS compliance_schema.pr_source_type AS TEXT
    CHECK (VALUE IN ('internal', 'external', 'regulatory'));

-- 问题严重度
CREATE DOMAIN IF NOT EXISTS compliance_schema.pr_severity_type AS TEXT
    CHECK (VALUE IN ('CRITICAL', 'MAJOR', 'MINOR'));

-- 问题状态
CREATE DOMAIN IF NOT EXISTS compliance_schema.pr_status_type AS TEXT
    CHECK (VALUE IN ('Open', 'Analyzing', 'Correcting', 'Verifying', 'Closed'));

-- 纠正措施类型
CREATE DOMAIN IF NOT EXISTS compliance_schema.correction_type AS TEXT
    CHECK (VALUE IN ('corrective', 'preventive'));

-- 纠正措施状态
CREATE DOMAIN IF NOT EXISTS compliance_schema.correction_status_type AS TEXT
    CHECK (VALUE IN ('Pending', 'InProgress', 'Completed'));

-- IEC62304合规状态
CREATE DOMAIN IF NOT EXISTS compliance_schema.iec_compliance_type AS TEXT
    CHECK (VALUE IN ('compliant', 'partial', 'non_compliant', 'not_applicable'));

-- 组织类型
CREATE DOMAIN IF NOT EXISTS sys_schema.org_type_type AS TEXT
    CHECK (VALUE IN ('department', 'team'));

-- 权限类型
CREATE DOMAIN IF NOT EXISTS sys_schema.perm_type_type AS TEXT
    CHECK (VALUE IN ('menu', 'button', 'api'));

-- 字典类别
CREATE DOMAIN IF NOT EXISTS sys_schema.dict_category_type AS TEXT
    CHECK (VALUE IN ('system', 'business'));

-- 追溯类型
CREATE DOMAIN IF NOT EXISTS trace_schema.trace_type_type AS TEXT
    CHECK (VALUE IN ('satisfies', 'satisfied_by', 'verifies', 'verified_by'));

-- 追溯链接状态
CREATE DOMAIN IF NOT EXISTS trace_schema.trace_status_type AS TEXT
    CHECK (VALUE IN ('active', 'suspended', 'invalid'));

-- 风险严重度 S1-S5
CREATE DOMAIN IF NOT EXISTS risk_schema.severity_type AS SMALLINT
    CHECK (VALUE BETWEEN 1 AND 5);

-- 风险概率 P1-P5
CREATE DOMAIN IF NOT EXISTS risk_schema.probability_type AS SMALLINT
    CHECK (VALUE BETWEEN 1 AND 5);

-- 风险可检测度 D1-D5
CREATE DOMAIN IF NOT EXISTS risk_schema.detectability_type AS SMALLINT
    CHECK (VALUE BETWEEN 1 AND 5);

-- 签名记录状态
CREATE DOMAIN IF NOT EXISTS esign_schema.signature_status_type AS TEXT
    CHECK (VALUE IN ('valid', 'invalid', 'revoked'));

-- 签名意义码
CREATE DOMAIN IF NOT EXISTS esign_schema.meaning_code_type AS TEXT
    CHECK (VALUE IN ('author', 'reviewer', 'approver', 'second_approver'));

-- 测试类型
CREATE DOMAIN IF NOT EXISTS req_schema.test_type_type AS TEXT
    CHECK (VALUE IN ('unit', 'integration', 'system', 'acceptance'));

-- 测试用例状态
CREATE DOMAIN IF NOT EXISTS req_schema.test_case_status_type AS TEXT
    CHECK (VALUE IN ('Draft', 'Active', 'Deprecated'));

-- 基线对比算法
CREATE DOMAIN IF NOT EXISTS compliance_schema.compare_algo_type AS TEXT
    CHECK (VALUE IN ('md5', 'sha256'));

-- IPD阶段门状态
CREATE DOMAIN IF NOT EXISTS proj_schema.gate_status_type AS TEXT
    CHECK (VALUE IN ('Pending', 'InReview', 'Passed', 'Failed'));

-- IPD阶段门评审结果
CREATE DOMAIN IF NOT EXISTS proj_schema.gate_result_type AS TEXT
    CHECK (VALUE IN ('Pass', 'ConditionalPass', 'Fail'));

-- 交付物状态
CREATE DOMAIN IF NOT EXISTS proj_schema.deliverable_status_type AS TEXT
    CHECK (VALUE IN ('Draft', 'Submitted', 'Approved', 'Rejected'));

-- 风险控制类型
CREATE DOMAIN IF NOT EXISTS risk_schema.control_type_type AS TEXT
    CHECK (VALUE IN ('DESIGN', 'PROCESS', 'INFORMATION'));

-- 风险监控状态变更
CREATE DOMAIN IF NOT EXISTS risk_schema.risk_status_change_type AS TEXT
    CHECK (VALUE IN ('Open', 'Mitigating', 'Controlled', 'Closed', 'Escalated'));

-- 评审结论
CREATE DOMAIN IF NOT EXISTS req_schema.review_result_type AS TEXT
    CHECK (VALUE IN ('APPROVED', 'CONDITIONAL', 'REJECTED'));

-- 用户状态
CREATE DOMAIN IF NOT EXISTS sys_schema.user_status_type AS TEXT
    CHECK (VALUE IN ('active', 'inactive', 'locked'));

-- 登录状态
CREATE DOMAIN IF NOT EXISTS sys_schema.login_status_type AS TEXT
    CHECK (VALUE IN ('success', 'failed'));

-- 登录类型
CREATE DOMAIN IF NOT EXISTS sys_schema.login_type_type AS TEXT
    CHECK (VALUE IN ('password', 'sso', 'otp'));

-- 发件箱发布状态
CREATE DOMAIN IF NOT EXISTS esign_schema.publish_status_type AS BOOLEAN;

-- 资源类型
CREATE DOMAIN IF NOT EXISTS sys_schema.resource_type_type AS TEXT
    CHECK (VALUE IN ('menu', 'api', 'page', 'component'));

-- 评审轮次
CREATE DOMAIN IF NOT EXISTS req_schema.review_round_type AS SMALLINT
    CHECK (VALUE > 0);

-- ============================================================================
-- 4. 建表语句
-- ============================================================================

-- ============================================================================
-- 4.1 req_schema（需求管理）- 11张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.1.1 requirement — 需求主表（公共属性）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.requirement (
    id                      BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requirement_no          VARCHAR(50)     UNIQUE NOT NULL,  -- 需求编号，如URS-P001-001
    requirement_type        VARCHAR(3)      NOT NULL,         -- 类型：URS/PRS/SRS/DRS
    project_id              BIGINT          NOT NULL,         -- 弱引用 proj_schema.project.id
    title                   VARCHAR(200)    NOT NULL,         -- 标题，≤50字
    description             TEXT            NOT NULL,         -- 详细描述
    priority                req_schema.req_priority_type NOT NULL DEFAULT 'SHOULD',
    status                  req_schema.req_status_type NOT NULL DEFAULT 'Draft',
    risk_level              VARCHAR(10)     DEFAULT 'MEDIUM', -- HIGH/MEDIUM/LOW
    safety_class            VARCHAR(1)      DEFAULT NULL,     -- 软件安全分类：A/B/C（IEC 62304 Clause 4.3）
    requirement_category    VARCHAR(10)     DEFAULT 'SOFTWARE', -- 需求分类：SOFTWARE/HARDWARE/BOTH
    baseline_id             BIGINT          DEFAULT NULL,     -- 弱引用 compliance_schema.baseline.id
    version                 INTEGER         NOT NULL DEFAULT 1, -- 乐观锁版本号
    is_deleted              BOOLEAN         NOT NULL DEFAULT FALSE, -- 软删除标记
    created_by              BIGINT          NOT NULL,         -- 弱引用 sys_schema.user.id
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by              BIGINT,                             -- 弱引用 sys_schema.user.id
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.requirement IS '需求主表（公共属性）';
COMMENT ON COLUMN req_schema.requirement.id IS '需求ID，自增主键';
COMMENT ON COLUMN req_schema.requirement.requirement_no IS '需求编号（自动生成：层级前缀-项目编号-序号），如URS-P001-001';
COMMENT ON COLUMN req_schema.requirement.requirement_type IS '需求类型：URS/PRS/SRS/DRS';
COMMENT ON COLUMN req_schema.requirement.project_id IS '归属项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN req_schema.requirement.title IS '需求标题，≤50字';
COMMENT ON COLUMN req_schema.requirement.description IS '需求详细描述';
COMMENT ON COLUMN req_schema.requirement.priority IS '需求优先级（MoSCoW：MUST/SHOULD/COULD/WONT）';
COMMENT ON COLUMN req_schema.requirement.status IS '需求状态';
COMMENT ON COLUMN req_schema.requirement.risk_level IS '风险等级：HIGH/MEDIUM/LOW';
COMMENT ON COLUMN req_schema.requirement.safety_class IS '软件安全分类：A/B/C（IEC 62304 Clause 4.3）';
COMMENT ON COLUMN req_schema.requirement.requirement_category IS '需求分类：SOFTWARE/HARDWARE/BOTH';
COMMENT ON COLUMN req_schema.requirement.baseline_id IS '关联基线ID（基线化后填写，弱引用 compliance_schema.baseline.id）';
COMMENT ON COLUMN req_schema.requirement.version IS '乐观锁版本号';
COMMENT ON COLUMN req_schema.requirement.is_deleted IS '软删除标记（FALSE=正常，TRUE=已删除）';
COMMENT ON COLUMN req_schema.requirement.created_by IS '创建人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN req_schema.requirement.created_at IS '创建时间';
COMMENT ON COLUMN req_schema.requirement.updated_by IS '最后修改人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN req_schema.requirement.updated_at IS '最后更新时间';

-- ---------------------------------------------------------------------------
-- 4.1.2 requirement_version — 需求版本公共主表（CTI基表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.requirement_version (
    id              BIGINT          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requirement_id  BIGINT          NOT NULL,
    version_no      INTEGER         NOT NULL DEFAULT 1,
    level           req_schema.req_level_type NOT NULL,
    change_summary  TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_by      BIGINT,         -- 弱引用 sys_schema.user.id
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.requirement_version IS '需求版本公共主表（CTI基表）';
COMMENT ON COLUMN req_schema.requirement_version.id IS '版本ID，自增主键';
COMMENT ON COLUMN req_schema.requirement_version.requirement_id IS '归属需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN req_schema.requirement_version.version_no IS '版本号';
COMMENT ON COLUMN req_schema.requirement_version.level IS '需求层级（URS/PRS/SRS/DRS）';
COMMENT ON COLUMN req_schema.requirement_version.change_summary IS '变更摘要';
COMMENT ON COLUMN req_schema.requirement_version.created_at IS '创建时间';
COMMENT ON COLUMN req_schema.requirement_version.updated_by IS '最后更新人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN req_schema.requirement_version.updated_at IS '最后更新时间';

-- ---------------------------------------------------------------------------
-- 4.1.3 req_v_urs — URS分层子表（用户需求规格）
-- 与requirement_version一对一关系：version_id UNIQUE FK
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.req_v_urs (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id          BIGINT      NOT NULL UNIQUE,
    user_scenario       TEXT,
    acceptance_criteria TEXT,
    stakeholder         TEXT,
    business_context    TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.req_v_urs IS 'URS分层子表（用户需求规格），与requirement_version一对一';
COMMENT ON COLUMN req_schema.req_v_urs.id IS 'URS子表ID，自增主键';
COMMENT ON COLUMN req_schema.req_v_urs.version_id IS '版本ID（UNIQUE FK -> requirement_version.id，一对一）';
COMMENT ON COLUMN req_schema.req_v_urs.user_scenario IS '用户场景描述';
COMMENT ON COLUMN req_schema.req_v_urs.acceptance_criteria IS '验收标准';
COMMENT ON COLUMN req_schema.req_v_urs.stakeholder IS '利益相关方';
COMMENT ON COLUMN req_schema.req_v_urs.business_context IS '业务背景';
COMMENT ON COLUMN req_schema.req_v_urs.created_at IS '创建时间';

ALTER TABLE req_schema.req_v_urs
    ADD CONSTRAINT fk_urs_version
    FOREIGN KEY (version_id) REFERENCES req_schema.requirement_version(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.1.4 req_v_prs — PRS分层子表（产品需求规格）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.req_v_prs (
    id                      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id              BIGINT      NOT NULL UNIQUE,
    functional_desc         TEXT,
    performance_criteria    TEXT,
    usability_requirements  TEXT,
    interface_requirements  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.req_v_prs IS 'PRS分层子表（产品需求规格），与requirement_version一对一';
COMMENT ON COLUMN req_schema.req_v_prs.id IS 'PRS子表ID，自增主键';
COMMENT ON COLUMN req_schema.req_v_prs.version_id IS '版本ID（UNIQUE FK -> requirement_version.id，一对一）';
COMMENT ON COLUMN req_schema.req_v_prs.functional_desc IS '功能描述';
COMMENT ON COLUMN req_schema.req_v_prs.performance_criteria IS '性能指标';
COMMENT ON COLUMN req_schema.req_v_prs.usability_requirements IS '可用性需求';
COMMENT ON COLUMN req_schema.req_v_prs.interface_requirements IS '接口需求';
COMMENT ON COLUMN req_schema.req_v_prs.created_at IS '创建时间';

ALTER TABLE req_schema.req_v_prs
    ADD CONSTRAINT fk_prs_version
    FOREIGN KEY (version_id) REFERENCES req_schema.requirement_version(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.1.5 req_v_srs — SRS分层子表（软件需求规格）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.req_v_srs (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id          BIGINT      NOT NULL UNIQUE,
    software_function   TEXT,
    input_output_spec   TEXT,
    data_requirements   TEXT,
    constraint_spec     TEXT,
    test_criteria       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.req_v_srs IS 'SRS分层子表（软件需求规格），与requirement_version一对一';
COMMENT ON COLUMN req_schema.req_v_srs.id IS 'SRS子表ID，自增主键';
COMMENT ON COLUMN req_schema.req_v_srs.version_id IS '版本ID（UNIQUE FK -> requirement_version.id，一对一）';
COMMENT ON COLUMN req_schema.req_v_srs.software_function IS '软件功能描述';
COMMENT ON COLUMN req_schema.req_v_srs.input_output_spec IS '输入输出规格';
COMMENT ON COLUMN req_schema.req_v_srs.data_requirements IS '数据需求';
COMMENT ON COLUMN req_schema.req_v_srs.constraint_spec IS '约束条件';
COMMENT ON COLUMN req_schema.req_v_srs.test_criteria IS '测试标准';
COMMENT ON COLUMN req_schema.req_v_srs.created_at IS '创建时间';

ALTER TABLE req_schema.req_v_srs
    ADD CONSTRAINT fk_srs_version
    FOREIGN KEY (version_id) REFERENCES req_schema.requirement_version(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.1.6 req_v_drs — DRS分层子表（设计需求规格）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.req_v_drs (
    id                      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version_id              BIGINT      NOT NULL UNIQUE,
    design_spec             TEXT,
    technical_constraint    TEXT,
    architecture_decision   TEXT,
    implementation_note     TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.req_v_drs IS 'DRS分层子表（设计需求规格），与requirement_version一对一';
COMMENT ON COLUMN req_schema.req_v_drs.id IS 'DRS子表ID，自增主键';
COMMENT ON COLUMN req_schema.req_v_drs.version_id IS '版本ID（UNIQUE FK -> requirement_version.id，一对一）';
COMMENT ON COLUMN req_schema.req_v_drs.design_spec IS '设计规格';
COMMENT ON COLUMN req_schema.req_v_drs.technical_constraint IS '技术约束';
COMMENT ON COLUMN req_schema.req_v_drs.architecture_decision IS '架构决策';
COMMENT ON COLUMN req_schema.req_v_drs.implementation_note IS '实现说明';
COMMENT ON COLUMN req_schema.req_v_drs.created_at IS '创建时间';

ALTER TABLE req_schema.req_v_drs
    ADD CONSTRAINT fk_drs_version
    FOREIGN KEY (version_id) REFERENCES req_schema.requirement_version(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.1.7 requirement_ancestor — 闭包表（需求层级关系）
-- 字段：descendant_id, ancestor_id, depth（无id，无path_length）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.requirement_ancestor (
    descendant_id   BIGINT  NOT NULL,
    ancestor_id     BIGINT  NOT NULL,
    depth           INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (descendant_id, ancestor_id)
);

COMMENT ON TABLE req_schema.requirement_ancestor IS '闭包表（需求层级关系），存储所有祖先-后代路径';
COMMENT ON COLUMN req_schema.requirement_ancestor.descendant_id IS '后代需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN req_schema.requirement_ancestor.ancestor_id IS '祖先需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN req_schema.requirement_ancestor.depth IS '路径深度（0=自身，1=直接父级，2=祖父级...）';

-- ---------------------------------------------------------------------------
-- 4.1.8 review_record — 评审记录表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.review_record (
    id              BIGINT                      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requirement_id  BIGINT                      NOT NULL,
    version_id      BIGINT                      NOT NULL,
    reviewer_id     BIGINT                      NOT NULL,   -- 弱引用 sys_schema.user.id
    round_no        req_schema.review_round_type NOT NULL DEFAULT 1,
    result          req_schema.review_result_type NOT NULL,
    comments        TEXT,
    signed_at       TIMESTAMPTZ
);

COMMENT ON TABLE req_schema.review_record IS '评审记录表';
COMMENT ON COLUMN req_schema.review_record.id IS '评审记录ID，自增主键';
COMMENT ON COLUMN req_schema.review_record.requirement_id IS '需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN req_schema.review_record.version_id IS '版本ID（弱引用 req_schema.requirement_version.id）';
COMMENT ON COLUMN req_schema.review_record.reviewer_id IS '评审人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN req_schema.review_record.round_no IS '评审轮次';
COMMENT ON COLUMN req_schema.review_record.result IS '评审结论（APPROVED/CONDITIONAL/REJECTED）';
COMMENT ON COLUMN req_schema.review_record.comments IS '评审意见';
COMMENT ON COLUMN req_schema.review_record.signed_at IS '签名时间';

-- ---------------------------------------------------------------------------
-- 4.1.9 test_case — 测试用例表（12字段）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.test_case (
    id              BIGINT                              GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    test_case_no    TEXT                                NOT NULL,
    title           TEXT                                NOT NULL,
    description     TEXT,
    test_type       req_schema.test_type_type           NOT NULL,
    safety_class    compliance_schema.safety_class_type,
    pre_condition   TEXT,
    test_steps      JSONB,
    expected_result TEXT,
    status          req_schema.test_case_status_type    NOT NULL DEFAULT 'Draft',
    project_id      BIGINT                              NOT NULL,   -- 弱引用 proj_schema.project.id
    created_by      BIGINT                              NOT NULL,   -- 弱引用 sys_schema.user.id
    created_at      TIMESTAMPTZ                         NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.test_case IS '测试用例表';
COMMENT ON COLUMN req_schema.test_case.id IS '测试用例ID，自增主键';
COMMENT ON COLUMN req_schema.test_case.test_case_no IS '测试用例编号（唯一）';
COMMENT ON COLUMN req_schema.test_case.title IS '测试用例标题';
COMMENT ON COLUMN req_schema.test_case.description IS '测试用例描述';
COMMENT ON COLUMN req_schema.test_case.test_type IS '测试类型（unit/integration/system/acceptance）';
COMMENT ON COLUMN req_schema.test_case.safety_class IS '安全分类（A/B/C）';
COMMENT ON COLUMN req_schema.test_case.pre_condition IS '前置条件';
COMMENT ON COLUMN req_schema.test_case.test_steps IS '测试步骤（JSONB格式）';
COMMENT ON COLUMN req_schema.test_case.expected_result IS '预期结果';
COMMENT ON COLUMN req_schema.test_case.status IS '用例状态';
COMMENT ON COLUMN req_schema.test_case.project_id IS '归属项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN req_schema.test_case.created_by IS '创建人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN req_schema.test_case.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.1.10 requirement_testcase — 需求-测试用例关联表（5字段）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.requirement_testcase (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requirement_id  BIGINT      NOT NULL,
    test_case_id    BIGINT      NOT NULL,
    trace_type      trace_schema.trace_type_type NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE req_schema.requirement_testcase IS '需求-测试用例关联表';
COMMENT ON COLUMN req_schema.requirement_testcase.id IS '关联ID，自增主键';
COMMENT ON COLUMN req_schema.requirement_testcase.requirement_id IS '需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN req_schema.requirement_testcase.test_case_id IS '测试用例ID（弱引用 req_schema.test_case.id）';
COMMENT ON COLUMN req_schema.requirement_testcase.trace_type IS '追溯类型（satisfies/satisfied_by/verifies/verified_by）';
COMMENT ON COLUMN req_schema.requirement_testcase.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.1.11 requirement_tag — 需求标签关联表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS req_schema.requirement_tag (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requirement_id  BIGINT      NOT NULL,
    tag_id          BIGINT      NOT NULL
);

COMMENT ON TABLE req_schema.requirement_tag IS '需求标签关联表';
COMMENT ON COLUMN req_schema.requirement_tag.id IS '关联ID，自增主键';
COMMENT ON COLUMN req_schema.requirement_tag.requirement_id IS '需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN req_schema.requirement_tag.tag_id IS '标签ID';

-- Schema内主键外键约束
ALTER TABLE req_schema.requirement_version
    ADD CONSTRAINT fk_ver_requirement
    FOREIGN KEY (requirement_id) REFERENCES req_schema.requirement(id) ON DELETE CASCADE;

-- review_record引用同schema内的requirement
-- (reviewer_id为跨schema弱引用，不建FK)

-- ============================================================================
-- 4.2 trace_schema（追溯管理）- 2张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.2.1 trace_link — 追溯链接表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trace_schema.trace_link (
    id              BIGINT                              GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_req_id   BIGINT                              NOT NULL,
    target_req_id   BIGINT                              NOT NULL,
    trace_type      trace_schema.trace_type_type        NOT NULL,
    status          trace_schema.trace_status_type       NOT NULL DEFAULT 'active',
    creator_id      BIGINT                              NOT NULL,   -- 弱引用 sys_schema.user.id
    created_at      TIMESTAMPTZ                         NOT NULL DEFAULT now()
);

COMMENT ON TABLE trace_schema.trace_link IS '追溯链接表';
COMMENT ON COLUMN trace_schema.trace_link.id IS '追溯链接ID，自增主键';
COMMENT ON COLUMN trace_schema.trace_link.source_req_id IS '源需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN trace_schema.trace_link.target_req_id IS '目标需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN trace_schema.trace_link.trace_type IS '追溯类型';
COMMENT ON COLUMN trace_schema.trace_link.status IS '链接状态';
COMMENT ON COLUMN trace_schema.trace_link.creator_id IS '创建人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN trace_schema.trace_link.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.2.2 trace_matrix_snapshot — 追溯矩阵快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS trace_schema.trace_matrix_snapshot (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT      NOT NULL,   -- 弱引用 proj_schema.project.id
    matrix_data_json JSONB      NOT NULL DEFAULT '{}',
    coverage_rate   NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE trace_schema.trace_matrix_snapshot IS '追溯矩阵快照';
COMMENT ON COLUMN trace_schema.trace_matrix_snapshot.id IS '快照ID，自增主键';
COMMENT ON COLUMN trace_schema.trace_matrix_snapshot.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN trace_schema.trace_matrix_snapshot.matrix_data_json IS '矩阵数据（JSONB）';
COMMENT ON COLUMN trace_schema.trace_matrix_snapshot.coverage_rate IS '覆盖率（百分比）';
COMMENT ON COLUMN trace_schema.trace_matrix_snapshot.calculated_at IS '计算时间';

-- ============================================================================
-- 4.3 chg_schema（变更管理）- 4张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.3.1 change_request — 变更请求表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chg_schema.change_request (
    id              BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT                          NOT NULL,
    cr_code         TEXT                            NOT NULL,
    title           TEXT                            NOT NULL,
    reason          TEXT                            NOT NULL,
    urgency         chg_schema.chg_urgency_type     NOT NULL DEFAULT 'MEDIUM',
    change_type     chg_schema.chg_type_type        NOT NULL DEFAULT 'NORMAL',
    status          chg_schema.chg_status_type      NOT NULL DEFAULT 'Draft',
    initiator_id    BIGINT                          NOT NULL,   -- 弱引用 sys_schema.user.id
    created_at      TIMESTAMPTZ                     NOT NULL DEFAULT now()
);

COMMENT ON TABLE chg_schema.change_request IS '变更请求表';
COMMENT ON COLUMN chg_schema.change_request.id IS '变更请求ID，自增主键';
COMMENT ON COLUMN chg_schema.change_request.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN chg_schema.change_request.cr_code IS '变更请求编号（唯一）';
COMMENT ON COLUMN chg_schema.change_request.title IS '变更标题';
COMMENT ON COLUMN chg_schema.change_request.reason IS '变更原因';
COMMENT ON COLUMN chg_schema.change_request.urgency IS '紧急度';
COMMENT ON COLUMN chg_schema.change_request.change_type IS '变更类型';
COMMENT ON COLUMN chg_schema.change_request.status IS '变更状态';
COMMENT ON COLUMN chg_schema.change_request.initiator_id IS '发起人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN chg_schema.change_request.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.3.2 impact_analysis — 影响分析表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chg_schema.impact_analysis (
    id                  BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    change_request_id   BIGINT                          NOT NULL,
    affected_req_id     BIGINT                          NOT NULL,   -- 弱引用 req_schema.requirement.id
    impact_level        chg_schema.impact_level_type    NOT NULL,
    impact_description  TEXT,
    confirmed           BOOLEAN                         NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE chg_schema.impact_analysis IS '影响分析表';
COMMENT ON COLUMN chg_schema.impact_analysis.id IS '影响分析ID，自增主键';
COMMENT ON COLUMN chg_schema.impact_analysis.change_request_id IS '变更请求ID（FK -> change_request.id）';
COMMENT ON COLUMN chg_schema.impact_analysis.affected_req_id IS '受影响需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN chg_schema.impact_analysis.impact_level IS '影响等级';
COMMENT ON COLUMN chg_schema.impact_analysis.impact_description IS '影响描述';
COMMENT ON COLUMN chg_schema.impact_analysis.confirmed IS '是否已确认';

ALTER TABLE chg_schema.impact_analysis
    ADD CONSTRAINT fk_impact_change_request
    FOREIGN KEY (change_request_id) REFERENCES chg_schema.change_request(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.3.3 change_approval — 变更审批表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chg_schema.change_approval (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    change_request_id   BIGINT      NOT NULL,
    approver_id         BIGINT      NOT NULL,   -- 弱引用 sys_schema.user.id
    result              TEXT        NOT NULL,   -- Approved / Rejected
    comments            TEXT,
    oa_process_id       TEXT,
    approved_at         TIMESTAMPTZ
);

COMMENT ON TABLE chg_schema.change_approval IS '变更审批表';
COMMENT ON COLUMN chg_schema.change_approval.id IS '审批ID，自增主键';
COMMENT ON COLUMN chg_schema.change_approval.change_request_id IS '变更请求ID（FK -> change_request.id）';
COMMENT ON COLUMN chg_schema.change_approval.approver_id IS '审批人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN chg_schema.change_approval.result IS '审批结果';
COMMENT ON COLUMN chg_schema.change_approval.comments IS '审批意见';
COMMENT ON COLUMN chg_schema.change_approval.oa_process_id IS 'OA流程ID';
COMMENT ON COLUMN chg_schema.change_approval.approved_at IS '审批时间';

ALTER TABLE chg_schema.change_approval
    ADD CONSTRAINT fk_approval_change_request
    FOREIGN KEY (change_request_id) REFERENCES chg_schema.change_request(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.3.4 change_execution — 变更执行记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chg_schema.change_execution (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    change_request_id   BIGINT      NOT NULL,
    requirement_id      BIGINT      NOT NULL,   -- 弱引用 req_schema.requirement.id
    old_version_id      BIGINT,     -- 弱引用 req_schema.requirement_version.id
    new_version_id      BIGINT,     -- 弱引用 req_schema.requirement_version.id
    executed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE chg_schema.change_execution IS '变更执行记录';
COMMENT ON COLUMN chg_schema.change_execution.id IS '执行记录ID，自增主键';
COMMENT ON COLUMN chg_schema.change_execution.change_request_id IS '变更请求ID（FK -> change_request.id）';
COMMENT ON COLUMN chg_schema.change_execution.requirement_id IS '需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN chg_schema.change_execution.old_version_id IS '原版本ID（弱引用 req_schema.requirement_version.id）';
COMMENT ON COLUMN chg_schema.change_execution.new_version_id IS '新版本ID（弱引用 req_schema.requirement_version.id）';
COMMENT ON COLUMN chg_schema.change_execution.executed_at IS '执行时间';

ALTER TABLE chg_schema.change_execution
    ADD CONSTRAINT fk_execution_change_request
    FOREIGN KEY (change_request_id) REFERENCES chg_schema.change_request(id) ON DELETE CASCADE;

-- ============================================================================
-- 4.4 compliance_schema（合规管理）- 8张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.4.1 audit_log — 审计日志表（追加只写）
-- 分区策略：按月范围分区（Phase 2实现，当前仅建基础表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.audit_log (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type     TEXT        NOT NULL,
    entity_id       BIGINT      NOT NULL,
    event_type      TEXT        NOT NULL,
    operator_id     BIGINT      NOT NULL,       -- 弱引用 sys_schema.user.id
    operator_name   TEXT        NOT NULL,
    old_value_json  JSONB,
    new_value_json  JSONB,
    reason          TEXT,
    prev_hash       CHAR(64)    NOT NULL,   -- SHA-256固定64位十六进制，链首记录用全0占位
    current_hash    CHAR(64)    NOT NULL,   -- SHA-256固定64位十六进制
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.audit_log IS '审计日志表（追加只写，哈希链保护，不可UPDATE/DELETE）';
COMMENT ON COLUMN compliance_schema.audit_log.id IS '日志ID，自增主键';
COMMENT ON COLUMN compliance_schema.audit_log.entity_type IS '实体类型';
COMMENT ON COLUMN compliance_schema.audit_log.entity_id IS '实体ID';
COMMENT ON COLUMN compliance_schema.audit_log.event_type IS '事件类型';
COMMENT ON COLUMN compliance_schema.audit_log.operator_id IS '操作人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.audit_log.operator_name IS '操作人姓名';
COMMENT ON COLUMN compliance_schema.audit_log.old_value_json IS '变更前值（JSONB）';
COMMENT ON COLUMN compliance_schema.audit_log.new_value_json IS '变更后值（JSONB）';
COMMENT ON COLUMN compliance_schema.audit_log.reason IS '操作原因';
COMMENT ON COLUMN compliance_schema.audit_log.prev_hash IS '前一条记录哈希（链式校验）';
COMMENT ON COLUMN compliance_schema.audit_log.current_hash IS '当前记录哈希';
COMMENT ON COLUMN compliance_schema.audit_log.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.4.2 soup_record — SOUP登记表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.soup_record (
    id                      BIGINT                                  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id              BIGINT                                  NOT NULL,
    name                    TEXT                                    NOT NULL,
    version                 TEXT                                    NOT NULL,
    supplier                TEXT,
    purpose                 TEXT,
    license                 TEXT,
    safety_class            compliance_schema.safety_class_type,
    status                  compliance_schema.soup_status_type       NOT NULL DEFAULT 'active',
    related_req_ids         BIGINT[],                               -- 关联需求ID数组
    last_review_date        DATE,
    review_result           TEXT,
    anomaly_review_record   TEXT,
    created_at              TIMESTAMPTZ                             NOT NULL DEFAULT now(),
    updated_by              BIGINT,                                 -- 弱引用 sys_schema.user.id
    updated_at              TIMESTAMPTZ                             NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.soup_record IS 'SOUP（现成软件）登记表';
COMMENT ON COLUMN compliance_schema.soup_record.id IS 'SOUP记录ID，自增主键';
COMMENT ON COLUMN compliance_schema.soup_record.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN compliance_schema.soup_record.name IS 'SOUP名称';
COMMENT ON COLUMN compliance_schema.soup_record.version IS 'SOUP版本';
COMMENT ON COLUMN compliance_schema.soup_record.supplier IS '供应商';
COMMENT ON COLUMN compliance_schema.soup_record.purpose IS '用途';
COMMENT ON COLUMN compliance_schema.soup_record.license IS '许可证类型';
COMMENT ON COLUMN compliance_schema.soup_record.safety_class IS '安全分类（A/B/C）';
COMMENT ON COLUMN compliance_schema.soup_record.status IS '状态（active/deprecated/retired/anomaly_reviewed）';
COMMENT ON COLUMN compliance_schema.soup_record.related_req_ids IS '关联需求ID数组';
COMMENT ON COLUMN compliance_schema.soup_record.last_review_date IS '最近评审日期';
COMMENT ON COLUMN compliance_schema.soup_record.review_result IS '评审结果';
COMMENT ON COLUMN compliance_schema.soup_record.anomaly_review_record IS '异常审查记录';
COMMENT ON COLUMN compliance_schema.soup_record.created_at IS '创建时间';
COMMENT ON COLUMN compliance_schema.soup_record.updated_by IS '更新人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.soup_record.updated_at IS '更新时间';

-- ---------------------------------------------------------------------------
-- 4.4.3 safety_classification — 安全分类表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.safety_classification (
    id              BIGINT                                  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT                                  NOT NULL,
    class_level     compliance_schema.safety_class_type      NOT NULL,
    justification   TEXT                                    NOT NULL,
    classified_by   BIGINT                                  NOT NULL,   -- 弱引用 sys_schema.user.id
    classified_at   TIMESTAMPTZ                             NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.safety_classification IS '安全分类表';
COMMENT ON COLUMN compliance_schema.safety_classification.id IS '分类ID，自增主键';
COMMENT ON COLUMN compliance_schema.safety_classification.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN compliance_schema.safety_classification.class_level IS '安全等级（A/B/C）';
COMMENT ON COLUMN compliance_schema.safety_classification.justification IS '分类依据';
COMMENT ON COLUMN compliance_schema.safety_classification.classified_by IS '分类人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.safety_classification.classified_at IS '分类时间';

-- ---------------------------------------------------------------------------
-- 4.4.4 baseline — 基线表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.baseline (
    id                          BIGINT                                      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id                  BIGINT                                      NOT NULL,
    name                        TEXT                                        NOT NULL,
    description                 TEXT,
    status                      compliance_schema.baseline_status_type       NOT NULL DEFAULT 'Draft',
    locked_by                   BIGINT,                                     -- 弱引用 sys_schema.user.id
    locked_at                   TIMESTAMPTZ,
    lock_signature_id           BIGINT,                                     -- 弱引用 esign_schema.signature_record.id
    second_locker_id            BIGINT,                                     -- 弱引用 sys_schema.user.id
    second_lock_signature_id    BIGINT,                                     -- 弱引用 esign_schema.signature_record.id
    comparison_algorithm        compliance_schema.compare_algo_type          NOT NULL DEFAULT 'sha256',
    created_at                  TIMESTAMPTZ                                 NOT NULL DEFAULT now(),
    updated_by                  BIGINT,                                     -- 弱引用 sys_schema.user.id
    updated_at                  TIMESTAMPTZ                                 NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.baseline IS '基线表（含双人签名锁定机制）';
COMMENT ON COLUMN compliance_schema.baseline.id IS '基线ID，自增主键';
COMMENT ON COLUMN compliance_schema.baseline.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN compliance_schema.baseline.name IS '基线名称';
COMMENT ON COLUMN compliance_schema.baseline.description IS '基线描述';
COMMENT ON COLUMN compliance_schema.baseline.status IS '基线状态（Draft/Locked/Unlocked/Archived）';
COMMENT ON COLUMN compliance_schema.baseline.locked_by IS '锁定人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.baseline.locked_at IS '锁定时间';
COMMENT ON COLUMN compliance_schema.baseline.lock_signature_id IS '锁定人签名ID（弱引用 esign_schema.signature_record.id）';
COMMENT ON COLUMN compliance_schema.baseline.second_locker_id IS '第二锁定人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.baseline.second_lock_signature_id IS '第二锁定人签名ID（弱引用 esign_schema.signature_record.id）';
COMMENT ON COLUMN compliance_schema.baseline.comparison_algorithm IS '对比算法（md5/sha256）';
COMMENT ON COLUMN compliance_schema.baseline.created_at IS '创建时间';
COMMENT ON COLUMN compliance_schema.baseline.updated_by IS '更新人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.baseline.updated_at IS '更新时间';

-- ---------------------------------------------------------------------------
-- 4.4.5 baseline_item — 基线条目表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.baseline_item (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    baseline_id     BIGINT      NOT NULL,
    requirement_id  BIGINT      NOT NULL,   -- 弱引用 req_schema.requirement.id
    version_id      BIGINT      NOT NULL,   -- 弱引用 req_schema.requirement_version.id
    included_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.baseline_item IS '基线条目表';
COMMENT ON COLUMN compliance_schema.baseline_item.id IS '条目ID，自增主键';
COMMENT ON COLUMN compliance_schema.baseline_item.baseline_id IS '基线ID（FK -> baseline.id）';
COMMENT ON COLUMN compliance_schema.baseline_item.requirement_id IS '需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN compliance_schema.baseline_item.version_id IS '版本ID（弱引用 req_schema.requirement_version.id）';
COMMENT ON COLUMN compliance_schema.baseline_item.included_at IS '纳入时间';

ALTER TABLE compliance_schema.baseline_item
    ADD CONSTRAINT fk_baseline_item_baseline
    FOREIGN KEY (baseline_id) REFERENCES compliance_schema.baseline(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.4.6 regulatory_mapping — 法规映射表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.regulatory_mapping (
    id                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regulation_code     TEXT        NOT NULL,
    clause              TEXT        NOT NULL,
    system_function     TEXT,
    compliance_status   TEXT        NOT NULL DEFAULT 'not_assessed',
    remarks             TEXT
);

COMMENT ON TABLE compliance_schema.regulatory_mapping IS '法规映射表';
COMMENT ON COLUMN compliance_schema.regulatory_mapping.id IS '映射ID，自增主键';
COMMENT ON COLUMN compliance_schema.regulatory_mapping.regulation_code IS '法规编号';
COMMENT ON COLUMN compliance_schema.regulatory_mapping.clause IS '条款号';
COMMENT ON COLUMN compliance_schema.regulatory_mapping.system_function IS '对应系统功能';
COMMENT ON COLUMN compliance_schema.regulatory_mapping.compliance_status IS '合规状态';
COMMENT ON COLUMN compliance_schema.regulatory_mapping.remarks IS '备注';

-- ---------------------------------------------------------------------------
-- 4.4.7 problem_report — 问题报告表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.problem_report (
    id                  BIGINT                                  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id          BIGINT                                  NOT NULL,
    pr_code             TEXT                                    NOT NULL,
    title               TEXT                                    NOT NULL,
    description         TEXT,
    source              compliance_schema.pr_source_type         NOT NULL,
    severity            compliance_schema.pr_severity_type       NOT NULL,
    status              compliance_schema.pr_status_type         NOT NULL DEFAULT 'Open',
    related_req_id      BIGINT,                                         -- 弱引用 req_schema.requirement.id
    related_soup_id     BIGINT,                                         -- 弱引用 compliance_schema.soup_record.id
    root_cause          TEXT,
    corrective_action   TEXT,
    reporter_id         BIGINT                                  NOT NULL,   -- 弱引用 sys_schema.user.id
    owner_id            BIGINT,                                         -- 弱引用 sys_schema.user.id
    reported_at         TIMESTAMPTZ                             NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ                             NOT NULL DEFAULT now(),
    updated_by          BIGINT,                                         -- 弱引用 sys_schema.user.id
    updated_at          TIMESTAMPTZ                             NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.problem_report IS '问题报告表';
COMMENT ON COLUMN compliance_schema.problem_report.id IS '问题报告ID，自增主键';
COMMENT ON COLUMN compliance_schema.problem_report.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN compliance_schema.problem_report.pr_code IS '问题报告编号';
COMMENT ON COLUMN compliance_schema.problem_report.title IS '问题标题';
COMMENT ON COLUMN compliance_schema.problem_report.description IS '问题描述';
COMMENT ON COLUMN compliance_schema.problem_report.source IS '来源（internal/external/regulatory）';
COMMENT ON COLUMN compliance_schema.problem_report.severity IS '严重度（CRITICAL/MAJOR/MINOR）';
COMMENT ON COLUMN compliance_schema.problem_report.status IS '状态（Open/Analyzing/Correcting/Verifying/Closed）';
COMMENT ON COLUMN compliance_schema.problem_report.related_req_id IS '关联需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN compliance_schema.problem_report.related_soup_id IS '关联SOUP ID（弱引用 compliance_schema.soup_record.id）';
COMMENT ON COLUMN compliance_schema.problem_report.root_cause IS '根本原因';
COMMENT ON COLUMN compliance_schema.problem_report.corrective_action IS '纠正措施';
COMMENT ON COLUMN compliance_schema.problem_report.reporter_id IS '报告人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.problem_report.owner_id IS '负责人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.problem_report.reported_at IS '报告时间';
COMMENT ON COLUMN compliance_schema.problem_report.closed_at IS '关闭时间';
COMMENT ON COLUMN compliance_schema.problem_report.created_at IS '创建时间';
COMMENT ON COLUMN compliance_schema.problem_report.updated_by IS '更新人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.problem_report.updated_at IS '更新时间';

-- ---------------------------------------------------------------------------
-- 4.4.8 pr_correction — 纠正措施表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.pr_correction (
    id                  BIGINT                                      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    problem_report_id   BIGINT                                      NOT NULL,
    action_type         compliance_schema.correction_type            NOT NULL,
    description         TEXT                                        NOT NULL,
    assignee_id         BIGINT                                      NOT NULL,   -- 弱引用 sys_schema.user.id
    due_date            DATE,
    status              compliance_schema.correction_status_type     NOT NULL DEFAULT 'Pending',
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ                                 NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.pr_correction IS '纠正措施表';
COMMENT ON COLUMN compliance_schema.pr_correction.id IS '措施ID，自增主键';
COMMENT ON COLUMN compliance_schema.pr_correction.problem_report_id IS '问题报告ID（FK -> problem_report.id）';
COMMENT ON COLUMN compliance_schema.pr_correction.action_type IS '措施类型（corrective/preventive）';
COMMENT ON COLUMN compliance_schema.pr_correction.description IS '措施描述';
COMMENT ON COLUMN compliance_schema.pr_correction.assignee_id IS '负责人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.pr_correction.due_date IS '截止日期';
COMMENT ON COLUMN compliance_schema.pr_correction.status IS '状态（Pending/InProgress/Completed）';
COMMENT ON COLUMN compliance_schema.pr_correction.completed_at IS '完成时间';
COMMENT ON COLUMN compliance_schema.pr_correction.created_at IS '创建时间';

ALTER TABLE compliance_schema.pr_correction
    ADD CONSTRAINT fk_correction_problem_report
    FOREIGN KEY (problem_report_id) REFERENCES compliance_schema.problem_report(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.4.9 iec62304_checklist — IEC 62304合规检查清单
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS compliance_schema.iec62304_checklist (
    id                  BIGINT                                      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id          BIGINT                                      NOT NULL,
    clause_no           TEXT                                        NOT NULL,
    clause_title        TEXT                                        NOT NULL,
    compliance_status   compliance_schema.iec_compliance_type        NOT NULL DEFAULT 'not_applicable',
    evidence            TEXT,
    gaps                TEXT,
    assessor_id         BIGINT                                      NOT NULL,   -- 弱引用 sys_schema.user.id
    assessed_at         TIMESTAMPTZ                                 NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ                                 NOT NULL DEFAULT now(),
    updated_by          BIGINT,                                                 -- 弱引用 sys_schema.user.id
    updated_at          TIMESTAMPTZ                                 NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_schema.iec62304_checklist IS 'IEC 62304合规检查清单';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.id IS '检查项ID，自增主键';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.clause_no IS '条款号';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.clause_title IS '条款标题';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.compliance_status IS '合规状态（compliant/partial/non_compliant/not_applicable）';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.evidence IS '合规证据';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.gaps IS '差距分析';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.assessor_id IS '评估人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.assessed_at IS '评估时间';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.created_at IS '创建时间';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.updated_by IS '更新人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN compliance_schema.iec62304_checklist.updated_at IS '更新时间';

-- ============================================================================
-- 4.5 esign_schema（电子签名）- 5张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.5.1 signature_intent — 签名意图表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS esign_schema.signature_intent (
    id              BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type     TEXT                            NOT NULL,
    entity_id       BIGINT                          NOT NULL,
    intent          esign_schema.sign_intent_type   NOT NULL,
    requester_id    BIGINT                          NOT NULL,   -- 弱引用 sys_schema.user.id
    created_at      TIMESTAMPTZ                     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ
);

COMMENT ON TABLE esign_schema.signature_intent IS '签名意图表';
COMMENT ON COLUMN esign_schema.signature_intent.id IS '意图ID，自增主键';
COMMENT ON COLUMN esign_schema.signature_intent.entity_type IS '实体类型';
COMMENT ON COLUMN esign_schema.signature_intent.entity_id IS '实体ID';
COMMENT ON COLUMN esign_schema.signature_intent.intent IS '签名意图（approve/confirm/review/release）';
COMMENT ON COLUMN esign_schema.signature_intent.requester_id IS '请求人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN esign_schema.signature_intent.created_at IS '创建时间';
COMMENT ON COLUMN esign_schema.signature_intent.expires_at IS '过期时间';

-- ---------------------------------------------------------------------------
-- 4.5.2 signature_record — 签名记录表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS esign_schema.signature_record (
    id              BIGINT                              GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    signature_id    TEXT                                NOT NULL,
    intent_id       BIGINT,
    signer_id       BIGINT                              NOT NULL,   -- 弱引用 sys_schema.user.id
    signer_name     TEXT                                NOT NULL,
    meaning_code    esign_schema.meaning_code_type      NOT NULL,
    signature_value TEXT                                NOT NULL,
    document_hash   TEXT,
    entity_hash     TEXT,
    auth_method     esign_schema.auth_method_type       NOT NULL,
    ip_address      INET,
    is_valid        BOOLEAN                             NOT NULL DEFAULT TRUE,
    intent_reason   TEXT,
    signed_at       TIMESTAMPTZ                         NOT NULL DEFAULT now(),
    status          esign_schema.signature_status_type   NOT NULL DEFAULT 'valid'
);

COMMENT ON TABLE esign_schema.signature_record IS '签名记录表（21 CFR Part 11合规）';
COMMENT ON COLUMN esign_schema.signature_record.id IS '记录ID，自增主键';
COMMENT ON COLUMN esign_schema.signature_record.signature_id IS '签名唯一标识';
COMMENT ON COLUMN esign_schema.signature_record.intent_id IS '签名意图ID（弱引用 esign_schema.signature_intent.id）';
COMMENT ON COLUMN esign_schema.signature_record.signer_id IS '签名人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN esign_schema.signature_record.signer_name IS '签名人姓名';
COMMENT ON COLUMN esign_schema.signature_record.meaning_code IS '签名意义码（author/reviewer/approver/second_approver）';
COMMENT ON COLUMN esign_schema.signature_record.signature_value IS '签名值';
COMMENT ON COLUMN esign_schema.signature_record.document_hash IS '文档哈希';
COMMENT ON COLUMN esign_schema.signature_record.entity_hash IS '实体哈希';
COMMENT ON COLUMN esign_schema.signature_record.auth_method IS '认证方式（password/otp/both）';
COMMENT ON COLUMN esign_schema.signature_record.ip_address IS '签名IP地址';
COMMENT ON COLUMN esign_schema.signature_record.is_valid IS '是否有效';
COMMENT ON COLUMN esign_schema.signature_record.intent_reason IS '签名原因';
COMMENT ON COLUMN esign_schema.signature_record.signed_at IS '签名时间';
COMMENT ON COLUMN esign_schema.signature_record.status IS '签名状态（valid/invalid/revoked）';

-- ---------------------------------------------------------------------------
-- 4.5.3 otp_challenge — OTP挑战表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS esign_schema.otp_challenge (
    id              BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT                          NOT NULL,   -- 弱引用 sys_schema.user.id
    challenge_code  TEXT                            NOT NULL,
    channel         esign_schema.otp_channel_type   NOT NULL,
    expires_at      TIMESTAMPTZ                     NOT NULL,
    used            BOOLEAN                         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ                     NOT NULL DEFAULT now()
);

COMMENT ON TABLE esign_schema.otp_challenge IS 'OTP动态码挑战表';
COMMENT ON COLUMN esign_schema.otp_challenge.id IS 'OTP ID，自增主键';
COMMENT ON COLUMN esign_schema.otp_challenge.user_id IS '用户ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN esign_schema.otp_challenge.challenge_code IS '挑战码';
COMMENT ON COLUMN esign_schema.otp_challenge.channel IS '发送渠道（email/sms）';
COMMENT ON COLUMN esign_schema.otp_challenge.expires_at IS '过期时间';
COMMENT ON COLUMN esign_schema.otp_challenge.used IS '是否已使用';
COMMENT ON COLUMN esign_schema.otp_challenge.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.5.4 outbox — 事务性发件箱表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS esign_schema.outbox (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type  TEXT        NOT NULL,
    aggregate_id    BIGINT      NOT NULL,
    event_type      TEXT        NOT NULL,
    payload_json    JSONB       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published       BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMPTZ
);

COMMENT ON TABLE esign_schema.outbox IS '事务性发件箱表（Eventual Consistency）';
COMMENT ON COLUMN esign_schema.outbox.id IS '发件箱ID，自增主键';
COMMENT ON COLUMN esign_schema.outbox.aggregate_type IS '聚合类型';
COMMENT ON COLUMN esign_schema.outbox.aggregate_id IS '聚合ID';
COMMENT ON COLUMN esign_schema.outbox.event_type IS '事件类型';
COMMENT ON COLUMN esign_schema.outbox.payload_json IS '事件载荷（JSONB）';
COMMENT ON COLUMN esign_schema.outbox.created_at IS '创建时间';
COMMENT ON COLUMN esign_schema.outbox.published IS '是否已发布';
COMMENT ON COLUMN esign_schema.outbox.published_at IS '发布时间';

-- ---------------------------------------------------------------------------
-- 4.5.5 jwt_blacklist — JWT黑名单表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS esign_schema.jwt_blacklist (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jti         TEXT        NOT NULL,
    user_id     BIGINT      NOT NULL,   -- 弱引用 sys_schema.user.id
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE esign_schema.jwt_blacklist IS 'JWT令牌黑名单表';
COMMENT ON COLUMN esign_schema.jwt_blacklist.id IS '黑名单ID，自增主键';
COMMENT ON COLUMN esign_schema.jwt_blacklist.jti IS 'JWT唯一标识（JTI）';
COMMENT ON COLUMN esign_schema.jwt_blacklist.user_id IS '用户ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN esign_schema.jwt_blacklist.expires_at IS '令牌过期时间';
COMMENT ON COLUMN esign_schema.jwt_blacklist.revoked_at IS '撤销时间';
COMMENT ON COLUMN esign_schema.jwt_blacklist.reason IS '撤销原因';
COMMENT ON COLUMN esign_schema.jwt_blacklist.created_at IS '创建时间';

-- ============================================================================
-- 4.6 risk_schema（风险管理）- 4张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.6.1 risk_item — 风险项表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_schema.risk_item (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT      NOT NULL,   -- 弱引用 proj_schema.project.id
    req_id          BIGINT,     -- 弱引用 req_schema.requirement.id
    title           TEXT        NOT NULL,
    hazard_scenario TEXT,
    status          TEXT        NOT NULL DEFAULT 'Open',
    created_by      BIGINT      NOT NULL,   -- 弱引用 sys_schema.user.id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE risk_schema.risk_item IS '风险项表';
COMMENT ON COLUMN risk_schema.risk_item.id IS '风险项ID，自增主键';
COMMENT ON COLUMN risk_schema.risk_item.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN risk_schema.risk_item.req_id IS '关联需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN risk_schema.risk_item.title IS '风险标题';
COMMENT ON COLUMN risk_schema.risk_item.hazard_scenario IS '危险场景';
COMMENT ON COLUMN risk_schema.risk_item.status IS '风险状态';
COMMENT ON COLUMN risk_schema.risk_item.created_by IS '创建人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN risk_schema.risk_item.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.6.2 risk_analysis — 风险分析表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_schema.risk_analysis (
    id              BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_item_id    BIGINT                          NOT NULL,
    severity        risk_schema.severity_type       NOT NULL,
    probability     risk_schema.probability_type    NOT NULL,
    detectability   risk_schema.detectability_type  NOT NULL,
    rpn             INTEGER                         NOT NULL,   -- S*P*D
    risk_level      risk_schema.risk_level_type     NOT NULL,
    analyzed_by     BIGINT                          NOT NULL,   -- 弱引用 sys_schema.user.id
    analyzed_at     TIMESTAMPTZ                     NOT NULL DEFAULT now()
);

COMMENT ON TABLE risk_schema.risk_analysis IS '风险分析表（FMEA）';
COMMENT ON COLUMN risk_schema.risk_analysis.id IS '分析ID，自增主键';
COMMENT ON COLUMN risk_schema.risk_analysis.risk_item_id IS '风险项ID（FK -> risk_item.id）';
COMMENT ON COLUMN risk_schema.risk_analysis.severity IS '严重度（S1-S5）';
COMMENT ON COLUMN risk_schema.risk_analysis.probability IS '概率（P1-P5）';
COMMENT ON COLUMN risk_schema.risk_analysis.detectability IS '可检测度（D1-D5）';
COMMENT ON COLUMN risk_schema.risk_analysis.rpn IS '风险优先数（S*P*D）';
COMMENT ON COLUMN risk_schema.risk_analysis.risk_level IS '风险等级（HIGH/MEDIUM/LOW）';
COMMENT ON COLUMN risk_schema.risk_analysis.analyzed_by IS '分析人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN risk_schema.risk_analysis.analyzed_at IS '分析时间';

ALTER TABLE risk_schema.risk_analysis
    ADD CONSTRAINT fk_analysis_risk_item
    FOREIGN KEY (risk_item_id) REFERENCES risk_schema.risk_item(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.6.3 risk_control — 风险控制措施表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_schema.risk_control (
    id              BIGINT                              GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_item_id    BIGINT                              NOT NULL,
    control_type    risk_schema.control_type_type       NOT NULL,
    description     TEXT                                NOT NULL,
    related_req_id  BIGINT,                                     -- 弱引用 req_schema.requirement.id
    residual_rpn    INTEGER,
    effectiveness   TEXT
);

COMMENT ON TABLE risk_schema.risk_control IS '风险控制措施表';
COMMENT ON COLUMN risk_schema.risk_control.id IS '控制措施ID，自增主键';
COMMENT ON COLUMN risk_schema.risk_control.risk_item_id IS '风险项ID（FK -> risk_item.id）';
COMMENT ON COLUMN risk_schema.risk_control.control_type IS '控制类型（DESIGN/PROCESS/INFORMATION）';
COMMENT ON COLUMN risk_schema.risk_control.description IS '措施描述';
COMMENT ON COLUMN risk_schema.risk_control.related_req_id IS '关联需求ID（弱引用 req_schema.requirement.id）';
COMMENT ON COLUMN risk_schema.risk_control.residual_rpn IS '残余RPN';
COMMENT ON COLUMN risk_schema.risk_control.effectiveness IS '有效性评估';

ALTER TABLE risk_schema.risk_control
    ADD CONSTRAINT fk_control_risk_item
    FOREIGN KEY (risk_item_id) REFERENCES risk_schema.risk_item(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.6.4 risk_monitor — 风险监控记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS risk_schema.risk_monitor (
    id              BIGINT                                      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    risk_item_id    BIGINT                                      NOT NULL,
    monitor_date    DATE                                        NOT NULL,
    status_change   risk_schema.risk_status_change_type         NOT NULL,
    remarks         TEXT,
    monitored_by    BIGINT                                      NOT NULL    -- 弱引用 sys_schema.user.id
);

COMMENT ON TABLE risk_schema.risk_monitor IS '风险监控记录';
COMMENT ON COLUMN risk_schema.risk_monitor.id IS '监控ID，自增主键';
COMMENT ON COLUMN risk_schema.risk_monitor.risk_item_id IS '风险项ID（FK -> risk_item.id）';
COMMENT ON COLUMN risk_schema.risk_monitor.monitor_date IS '监控日期';
COMMENT ON COLUMN risk_schema.risk_monitor.status_change IS '状态变更';
COMMENT ON COLUMN risk_schema.risk_monitor.remarks IS '备注';
COMMENT ON COLUMN risk_schema.risk_monitor.monitored_by IS '监控人ID（弱引用 sys_schema.user.id）';

ALTER TABLE risk_schema.risk_monitor
    ADD CONSTRAINT fk_monitor_risk_item
    FOREIGN KEY (risk_item_id) REFERENCES risk_schema.risk_item(id) ON DELETE CASCADE;

-- ============================================================================
-- 4.7 proj_schema（项目管理）- 4张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.7.1 project — 项目表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proj_schema.project (
    id              BIGINT                                  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            TEXT                                    NOT NULL,
    code            TEXT                                    NOT NULL,
    description     TEXT,
    status          proj_schema.project_status_type          NOT NULL DEFAULT 'Active',
    start_date      DATE,
    end_date        DATE,
    safety_class    compliance_schema.safety_class_type,
    created_by      BIGINT                                  NOT NULL,   -- 弱引用 sys_schema.user.id
    created_at      TIMESTAMPTZ                             NOT NULL DEFAULT now()
);

COMMENT ON TABLE proj_schema.project IS '项目表';
COMMENT ON COLUMN proj_schema.project.id IS '项目ID，自增主键';
COMMENT ON COLUMN proj_schema.project.name IS '项目名称';
COMMENT ON COLUMN proj_schema.project.code IS '项目编号（唯一）';
COMMENT ON COLUMN proj_schema.project.description IS '项目描述';
COMMENT ON COLUMN proj_schema.project.status IS '项目状态（Active/OnHold/Completed/Archived）';
COMMENT ON COLUMN proj_schema.project.start_date IS '开始日期';
COMMENT ON COLUMN proj_schema.project.end_date IS '结束日期';
COMMENT ON COLUMN proj_schema.project.safety_class IS '安全分类（A/B/C）';
COMMENT ON COLUMN proj_schema.project.created_by IS '创建人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN proj_schema.project.created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.7.2 project_member — 项目成员表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proj_schema.project_member (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,   -- 弱引用 sys_schema.user.id
    role_in_project TEXT        NOT NULL,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE proj_schema.project_member IS '项目成员表';
COMMENT ON COLUMN proj_schema.project_member.id IS '成员ID，自增主键';
COMMENT ON COLUMN proj_schema.project_member.project_id IS '项目ID（FK -> project.id）';
COMMENT ON COLUMN proj_schema.project_member.user_id IS '用户ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN proj_schema.project_member.role_in_project IS '项目角色';
COMMENT ON COLUMN proj_schema.project_member.joined_at IS '加入时间';

ALTER TABLE proj_schema.project_member
    ADD CONSTRAINT fk_member_project
    FOREIGN KEY (project_id) REFERENCES proj_schema.project(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.7.3 ipd_gate — IPD阶段门表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proj_schema.ipd_gate (
    id              BIGINT                              GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT                              NOT NULL,
    dcp_stage       proj_schema.dcp_stage_type          NOT NULL,
    status          proj_schema.gate_status_type         NOT NULL DEFAULT 'Pending',
    review_date     DATE,
    review_result   proj_schema.gate_result_type,
    reviewer_id     BIGINT                                      -- 弱引用 sys_schema.user.id
);

COMMENT ON TABLE proj_schema.ipd_gate IS 'IPD阶段门表';
COMMENT ON COLUMN proj_schema.ipd_gate.id IS '阶段门ID，自增主键';
COMMENT ON COLUMN proj_schema.ipd_gate.project_id IS '项目ID（FK -> project.id）';
COMMENT ON COLUMN proj_schema.ipd_gate.dcp_stage IS 'DCP阶段（DCP1-5）';
COMMENT ON COLUMN proj_schema.ipd_gate.status IS '阶段门状态';
COMMENT ON COLUMN proj_schema.ipd_gate.review_date IS '评审日期';
COMMENT ON COLUMN proj_schema.ipd_gate.review_result IS '评审结果';
COMMENT ON COLUMN proj_schema.ipd_gate.reviewer_id IS '评审人ID（弱引用 sys_schema.user.id）';

ALTER TABLE proj_schema.ipd_gate
    ADD CONSTRAINT fk_gate_project
    FOREIGN KEY (project_id) REFERENCES proj_schema.project(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.7.4 deliverable — 交付物表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proj_schema.deliverable (
    id              BIGINT                                  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT                                  NOT NULL,
    gate_id         BIGINT,                                         -- 弱引用 proj_schema.ipd_gate.id
    name            TEXT                                    NOT NULL,
    type            TEXT                                    NOT NULL,
    status          proj_schema.deliverable_status_type      NOT NULL DEFAULT 'Draft',
    related_req_ids BIGINT[],                                       -- 关联需求ID数组
    submitted_at    TIMESTAMPTZ
);

COMMENT ON TABLE proj_schema.deliverable IS '交付物表';
COMMENT ON COLUMN proj_schema.deliverable.id IS '交付物ID，自增主键';
COMMENT ON COLUMN proj_schema.deliverable.project_id IS '项目ID（FK -> project.id）';
COMMENT ON COLUMN proj_schema.deliverable.gate_id IS '阶段门ID（弱引用 proj_schema.ipd_gate.id）';
COMMENT ON COLUMN proj_schema.deliverable.name IS '交付物名称';
COMMENT ON COLUMN proj_schema.deliverable.type IS '交付物类型';
COMMENT ON COLUMN proj_schema.deliverable.status IS '状态';
COMMENT ON COLUMN proj_schema.deliverable.related_req_ids IS '关联需求ID数组';
COMMENT ON COLUMN proj_schema.deliverable.submitted_at IS '提交时间';

ALTER TABLE proj_schema.deliverable
    ADD CONSTRAINT fk_deliverable_project
    FOREIGN KEY (project_id) REFERENCES proj_schema.project(id) ON DELETE CASCADE;

-- ============================================================================
-- 4.8 report_schema（报表仪表盘）- 3张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.8.1 dashboard_config — 仪表盘配置表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_schema.dashboard_config (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,   -- 弱引用 sys_schema.user.id
    layout_json JSONB       NOT NULL DEFAULT '{}',
    widgets_json JSONB      NOT NULL DEFAULT '{}',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE report_schema.dashboard_config IS '仪表盘配置表';
COMMENT ON COLUMN report_schema.dashboard_config.id IS '配置ID，自增主键';
COMMENT ON COLUMN report_schema.dashboard_config.user_id IS '用户ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN report_schema.dashboard_config.layout_json IS '布局配置（JSONB）';
COMMENT ON COLUMN report_schema.dashboard_config.widgets_json IS '组件配置（JSONB）';
COMMENT ON COLUMN report_schema.dashboard_config.updated_at IS '更新时间';

-- ---------------------------------------------------------------------------
-- 4.8.2 statistics_snapshot — 统计快照表（CQRS Lite）
-- 分区策略：按季度范围分区（Phase 2实现，当前仅建基础表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_schema.statistics_snapshot (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id      BIGINT      NOT NULL,       -- 弱引用 proj_schema.project.id
    metric_type     TEXT        NOT NULL,
    metric_key      TEXT        NOT NULL,
    metric_value    NUMERIC     NOT NULL,
    dimension_json  JSONB       NOT NULL DEFAULT '{}',
    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE report_schema.statistics_snapshot IS '统计快照表（CQRS Lite）';
COMMENT ON COLUMN report_schema.statistics_snapshot.id IS '快照ID，自增主键';
COMMENT ON COLUMN report_schema.statistics_snapshot.project_id IS '项目ID（弱引用 proj_schema.project.id）';
COMMENT ON COLUMN report_schema.statistics_snapshot.metric_type IS '指标类型';
COMMENT ON COLUMN report_schema.statistics_snapshot.metric_key IS '指标键';
COMMENT ON COLUMN report_schema.statistics_snapshot.metric_value IS '指标值';
COMMENT ON COLUMN report_schema.statistics_snapshot.dimension_json IS '维度数据（JSONB）';
COMMENT ON COLUMN report_schema.statistics_snapshot.calculated_at IS '计算时间';

-- ---------------------------------------------------------------------------
-- 4.8.3 report_template — 报告模板表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_schema.report_template (
    id                      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                    TEXT        NOT NULL,
    type                    TEXT        NOT NULL,   -- 弱引用字典 REPORT_TYPE
    template_config_json    JSONB       NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE report_schema.report_template IS '报告模板表';
COMMENT ON COLUMN report_schema.report_template.id IS '模板ID，自增主键';
COMMENT ON COLUMN report_schema.report_template.name IS '模板名称';
COMMENT ON COLUMN report_schema.report_template.type IS '报告类型';
COMMENT ON COLUMN report_schema.report_template.template_config_json IS '模板配置（JSONB）';
COMMENT ON COLUMN report_schema.report_template.created_at IS '创建时间';

-- ============================================================================
-- 4.9 sys_schema（系统管理）- 11张表
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 4.9.1 "user" — 用户表（使用双引号，因user是PG保留字）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema."user" (
    id                      BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username                TEXT                            NOT NULL,
    real_name               TEXT,
    email                   TEXT,
    phone                   TEXT,
    password_hash           TEXT                            NOT NULL,
    esign_password_hash     TEXT,
    status                  sys_schema.user_status_type     NOT NULL DEFAULT 'active',
    oa_user_id              TEXT,
    org_id                  BIGINT,                         -- 弱引用 sys_schema.organization.id
    created_at              TIMESTAMPTZ                     NOT NULL DEFAULT now()
);

COMMENT ON TABLE sys_schema."user" IS '用户表';
COMMENT ON COLUMN sys_schema."user".id IS '用户ID，自增主键';
COMMENT ON COLUMN sys_schema."user".username IS '用户名（唯一）';
COMMENT ON COLUMN sys_schema."user".real_name IS '真实姓名';
COMMENT ON COLUMN sys_schema."user".email IS '邮箱（唯一）';
COMMENT ON COLUMN sys_schema."user".phone IS '手机号';
COMMENT ON COLUMN sys_schema."user".password_hash IS '密码哈希';
COMMENT ON COLUMN sys_schema."user".esign_password_hash IS '电子签名密码哈希';
COMMENT ON COLUMN sys_schema."user".status IS '用户状态（active/inactive/locked）';
COMMENT ON COLUMN sys_schema."user".oa_user_id IS 'OA系统用户ID';
COMMENT ON COLUMN sys_schema."user".org_id IS '组织ID（弱引用 sys_schema.organization.id）';
COMMENT ON COLUMN sys_schema."user".created_at IS '创建时间';

-- ---------------------------------------------------------------------------
-- 4.9.2 role — 角色表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.role (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_code   TEXT        NOT NULL,
    role_name   TEXT        NOT NULL,
    description TEXT,
    built_in    BOOLEAN     NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE sys_schema.role IS '角色表';
COMMENT ON COLUMN sys_schema.role.id IS '角色ID，自增主键';
COMMENT ON COLUMN sys_schema.role.role_code IS '角色编码（唯一）';
COMMENT ON COLUMN sys_schema.role.role_name IS '角色名称';
COMMENT ON COLUMN sys_schema.role.description IS '角色描述';
COMMENT ON COLUMN sys_schema.role.built_in IS '是否内置角色';

-- ---------------------------------------------------------------------------
-- 4.9.3 permission — 权限表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.permission (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    perm_code   TEXT        NOT NULL,
    perm_name   TEXT        NOT NULL,
    perm_type   sys_schema.perm_type_type NOT NULL,
    parent_id   BIGINT      -- 自引用：父权限ID
);

COMMENT ON TABLE sys_schema.permission IS '权限表';
COMMENT ON COLUMN sys_schema.permission.id IS '权限ID，自增主键';
COMMENT ON COLUMN sys_schema.permission.perm_code IS '权限编码（唯一）';
COMMENT ON COLUMN sys_schema.permission.perm_name IS '权限名称';
COMMENT ON COLUMN sys_schema.permission.perm_type IS '权限类型（menu/button/api）';
COMMENT ON COLUMN sys_schema.permission.parent_id IS '父权限ID（自引用）';

ALTER TABLE sys_schema.permission
    ADD CONSTRAINT fk_permission_parent
    FOREIGN KEY (parent_id) REFERENCES sys_schema.permission(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 4.9.4 resource — 资源表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.resource (
    id              BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    resource_code   TEXT                            NOT NULL,
    resource_name   TEXT                            NOT NULL,
    resource_type   sys_schema.resource_type_type   NOT NULL,
    path            TEXT
);

COMMENT ON TABLE sys_schema.resource IS '资源表';
COMMENT ON COLUMN sys_schema.resource.id IS '资源ID，自增主键';
COMMENT ON COLUMN sys_schema.resource.resource_code IS '资源编码（唯一）';
COMMENT ON COLUMN sys_schema.resource.resource_name IS '资源名称';
COMMENT ON COLUMN sys_schema.resource.resource_type IS '资源类型';
COMMENT ON COLUMN sys_schema.resource.path IS '资源路径';

-- ---------------------------------------------------------------------------
-- 4.9.5 user_role — 用户角色关联表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.user_role (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    role_id     BIGINT      NOT NULL,
    granted_by  BIGINT,     -- 弱引用 sys_schema.user.id
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE sys_schema.user_role IS '用户角色关联表';
COMMENT ON COLUMN sys_schema.user_role.id IS '关联ID，自增主键';
COMMENT ON COLUMN sys_schema.user_role.user_id IS '用户ID（FK -> user.id）';
COMMENT ON COLUMN sys_schema.user_role.role_id IS '角色ID（FK -> role.id）';
COMMENT ON COLUMN sys_schema.user_role.granted_by IS '授权人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN sys_schema.user_role.granted_at IS '授权时间';

ALTER TABLE sys_schema.user_role
    ADD CONSTRAINT fk_user_role_user
    FOREIGN KEY (user_id) REFERENCES sys_schema."user"(id) ON DELETE CASCADE;

ALTER TABLE sys_schema.user_role
    ADD CONSTRAINT fk_user_role_role
    FOREIGN KEY (role_id) REFERENCES sys_schema.role(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.9.6 role_permission — 角色权限关联表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.role_permission (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id         BIGINT      NOT NULL,
    permission_id   BIGINT      NOT NULL
);

COMMENT ON TABLE sys_schema.role_permission IS '角色权限关联表';
COMMENT ON COLUMN sys_schema.role_permission.id IS '关联ID，自增主键';
COMMENT ON COLUMN sys_schema.role_permission.role_id IS '角色ID（FK -> role.id）';
COMMENT ON COLUMN sys_schema.role_permission.permission_id IS '权限ID（FK -> permission.id）';

ALTER TABLE sys_schema.role_permission
    ADD CONSTRAINT fk_role_permission_role
    FOREIGN KEY (role_id) REFERENCES sys_schema.role(id) ON DELETE CASCADE;

ALTER TABLE sys_schema.role_permission
    ADD CONSTRAINT fk_role_permission_permission
    FOREIGN KEY (permission_id) REFERENCES sys_schema.permission(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.9.7 organization — 组织架构表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.organization (
    id          BIGINT                      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id   BIGINT,                     -- 自引用：父组织ID
    org_name    TEXT                        NOT NULL,
    org_code    TEXT                        NOT NULL,
    org_type    sys_schema.org_type_type    NOT NULL,
    oa_org_id   TEXT
);

COMMENT ON TABLE sys_schema.organization IS '组织架构表';
COMMENT ON COLUMN sys_schema.organization.id IS '组织ID，自增主键';
COMMENT ON COLUMN sys_schema.organization.parent_id IS '父组织ID（自引用）';
COMMENT ON COLUMN sys_schema.organization.org_name IS '组织名称';
COMMENT ON COLUMN sys_schema.organization.org_code IS '组织编码（唯一）';
COMMENT ON COLUMN sys_schema.organization.org_type IS '组织类型（department/team）';
COMMENT ON COLUMN sys_schema.organization.oa_org_id IS 'OA系统组织ID';

ALTER TABLE sys_schema.organization
    ADD CONSTRAINT fk_organization_parent
    FOREIGN KEY (parent_id) REFERENCES sys_schema.organization(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 4.9.8 dict_type — 字典类型表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.dict_type (
    id          BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_code   TEXT                            NOT NULL,
    dict_name   TEXT                            NOT NULL,
    category    sys_schema.dict_category_type   NOT NULL DEFAULT 'system',
    status      TEXT                            NOT NULL DEFAULT 'active'
);

COMMENT ON TABLE sys_schema.dict_type IS '字典类型表';
COMMENT ON COLUMN sys_schema.dict_type.id IS '字典类型ID，自增主键';
COMMENT ON COLUMN sys_schema.dict_type.dict_code IS '字典编码（唯一）';
COMMENT ON COLUMN sys_schema.dict_type.dict_name IS '字典名称';
COMMENT ON COLUMN sys_schema.dict_type.category IS '类别（system/business）';
COMMENT ON COLUMN sys_schema.dict_type.status IS '状态';

-- ---------------------------------------------------------------------------
-- 4.9.9 dict_entry — 字典项表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.dict_entry (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_type_id    BIGINT      NOT NULL,
    entry_code      TEXT        NOT NULL,
    entry_label     TEXT        NOT NULL,
    sort_order      INTEGER     NOT NULL DEFAULT 0,
    status          TEXT        NOT NULL DEFAULT 'active'
);

COMMENT ON TABLE sys_schema.dict_entry IS '字典项表';
COMMENT ON COLUMN sys_schema.dict_entry.id IS '字典项ID，自增主键';
COMMENT ON COLUMN sys_schema.dict_entry.dict_type_id IS '字典类型ID（FK -> dict_type.id）';
COMMENT ON COLUMN sys_schema.dict_entry.entry_code IS '字典项编码';
COMMENT ON COLUMN sys_schema.dict_entry.entry_label IS '字典项标签';
COMMENT ON COLUMN sys_schema.dict_entry.sort_order IS '排序号';
COMMENT ON COLUMN sys_schema.dict_entry.status IS '状态';

ALTER TABLE sys_schema.dict_entry
    ADD CONSTRAINT fk_dict_entry_type
    FOREIGN KEY (dict_type_id) REFERENCES sys_schema.dict_type(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4.9.10 sys_config — 系统配置表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.sys_config (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_key  TEXT        NOT NULL,
    config_value TEXT       NOT NULL,
    config_type TEXT,
    description TEXT
);

COMMENT ON TABLE sys_schema.sys_config IS '系统配置表';
COMMENT ON COLUMN sys_schema.sys_config.id IS '配置ID，自增主键';
COMMENT ON COLUMN sys_schema.sys_config.config_key IS '配置键（唯一）';
COMMENT ON COLUMN sys_schema.sys_config.config_value IS '配置值';
COMMENT ON COLUMN sys_schema.sys_config.config_type IS '配置类型';
COMMENT ON COLUMN sys_schema.sys_config.description IS '配置说明';

-- ---------------------------------------------------------------------------
-- 4.9.11 operation_log — 操作日志表
-- 分区策略：按月范围分区（Phase 2实现，当前仅建基础表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.operation_log (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT,     -- 弱引用 sys_schema.user.id
    module      TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    method      TEXT,
    params      TEXT,
    ip          INET,
    duration    INTEGER,    -- 执行耗时（毫秒）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE sys_schema.operation_log IS '操作日志表';
COMMENT ON COLUMN sys_schema.operation_log.id IS '日志ID，自增主键';
COMMENT ON COLUMN sys_schema.operation_log.user_id IS '操作人ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN sys_schema.operation_log.module IS '操作模块';
COMMENT ON COLUMN sys_schema.operation_log.action IS '操作动作';
COMMENT ON COLUMN sys_schema.operation_log.method IS '请求方法';
COMMENT ON COLUMN sys_schema.operation_log.params IS '请求参数';
COMMENT ON COLUMN sys_schema.operation_log.ip IS '操作IP';
COMMENT ON COLUMN sys_schema.operation_log.duration IS '执行耗时（毫秒）';
COMMENT ON COLUMN sys_schema.operation_log.created_at IS '操作时间';

-- ---------------------------------------------------------------------------
-- 4.9.12 login_log — 登录日志表
-- 分区策略：按月范围分区（Phase 2实现，当前仅建基础表）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_schema.login_log (
    id          BIGINT                          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT,                         -- 弱引用 sys_schema.user.id
    login_type  sys_schema.login_type_type      NOT NULL DEFAULT 'password',
    ip          INET,
    location    TEXT,
    browser     TEXT,
    os          TEXT,
    status      sys_schema.login_status_type    NOT NULL DEFAULT 'success',
    created_at  TIMESTAMPTZ                     NOT NULL DEFAULT now()
);

COMMENT ON TABLE sys_schema.login_log IS '登录日志表';
COMMENT ON COLUMN sys_schema.login_log.id IS '日志ID，自增主键';
COMMENT ON COLUMN sys_schema.login_log.user_id IS '用户ID（弱引用 sys_schema.user.id）';
COMMENT ON COLUMN sys_schema.login_log.login_type IS '登录类型（password/sso/otp）';
COMMENT ON COLUMN sys_schema.login_log.ip IS '登录IP';
COMMENT ON COLUMN sys_schema.login_log.location IS '登录地点';
COMMENT ON COLUMN sys_schema.login_log.browser IS '浏览器';
COMMENT ON COLUMN sys_schema.login_log.os IS '操作系统';
COMMENT ON COLUMN sys_schema.login_log.status IS '登录状态（success/failed）';
COMMENT ON COLUMN sys_schema.login_log.created_at IS '登录时间';

-- ============================================================================
-- 5. 索引创建
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 5.1 req_schema 索引
-- ---------------------------------------------------------------------------

-- 需求主表：项目+状态筛选
CREATE INDEX IF NOT EXISTS idx_req_project_status
    ON req_schema.requirement (project_id, status);

-- 需求编号唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_requirement_no
    ON req_schema.requirement (requirement_no);

-- 我的创建+时间排序
CREATE INDEX IF NOT EXISTS idx_req_created_by
    ON req_schema.requirement (created_by, created_at);

-- 软件安全分类筛选（IEC 62304）
CREATE INDEX IF NOT EXISTS idx_req_safety_class
    ON req_schema.requirement (safety_class) WHERE safety_class IS NOT NULL;

-- 基线关联查询
CREATE INDEX IF NOT EXISTS idx_req_baseline
    ON req_schema.requirement (baseline_id) WHERE baseline_id IS NOT NULL;

-- 需求类型+状态筛选
CREATE INDEX IF NOT EXISTS idx_req_type_status
    ON req_schema.requirement (requirement_type, status);

-- 需求版本：需求下按层级查版本
CREATE INDEX IF NOT EXISTS idx_ver_req_level
    ON req_schema.requirement_version (requirement_id, level);

-- CTI分层子表唯一索引（与requirement_version一对一关系）
CREATE UNIQUE INDEX IF NOT EXISTS idx_urs_version
    ON req_schema.req_v_urs (version_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_prs_version
    ON req_schema.req_v_prs (version_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_srs_version
    ON req_schema.req_v_srs (version_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_drs_version
    ON req_schema.req_v_drs (version_id);

-- 闭包表索引（3个）
CREATE UNIQUE INDEX IF NOT EXISTS idx_ancestor_desc_anc
    ON req_schema.requirement_ancestor (descendant_id, ancestor_id);

CREATE INDEX IF NOT EXISTS idx_ancestor_anc
    ON req_schema.requirement_ancestor (ancestor_id);

CREATE INDEX IF NOT EXISTS idx_ancestor_desc_depth
    ON req_schema.requirement_ancestor (descendant_id, depth);

-- 评审记录：评审轮次查询
CREATE INDEX IF NOT EXISTS idx_review_req_round
    ON req_schema.review_record (requirement_id, round_no);

-- 测试用例编号唯一
CREATE UNIQUE INDEX IF NOT EXISTS idx_tc_no
    ON req_schema.test_case (test_case_no);

-- 测试用例：项目下按状态筛选
CREATE INDEX IF NOT EXISTS idx_tc_project_status
    ON req_schema.test_case (project_id, status);

-- 需求-测试用例关联：按需求查
CREATE INDEX IF NOT EXISTS idx_rtc_req
    ON req_schema.requirement_testcase (requirement_id);

-- 需求-测试用例关联：按测试用例查
CREATE INDEX IF NOT EXISTS idx_rtc_tc
    ON req_schema.requirement_testcase (test_case_id);

-- ---------------------------------------------------------------------------
-- 5.2 trace_schema 索引
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_trace_source
    ON trace_schema.trace_link (source_req_id);

CREATE INDEX IF NOT EXISTS idx_trace_target
    ON trace_schema.trace_link (target_req_id);

CREATE INDEX IF NOT EXISTS idx_trace_type_status
    ON trace_schema.trace_link (trace_type, status);

-- ---------------------------------------------------------------------------
-- 5.3 chg_schema 索引
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_chg_project
    ON chg_schema.change_request (project_id, status);

CREATE INDEX IF NOT EXISTS idx_impact_chg
    ON chg_schema.impact_analysis (change_request_id);

-- ---------------------------------------------------------------------------
-- 5.4 compliance_schema 索引
-- ---------------------------------------------------------------------------

-- 审计日志：按实体查询
CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON compliance_schema.audit_log (entity_type, entity_id);

-- 审计日志：按操作人查询
CREATE INDEX IF NOT EXISTS idx_audit_operator
    ON compliance_schema.audit_log (operator_id, created_at);

-- 审计日志：时间范围查询（BRIN索引，适合追加只写表）
CREATE INDEX IF NOT EXISTS idx_audit_time
    ON compliance_schema.audit_log USING BRIN (created_at);

-- 审计日志：哈希链校验（唯一）
CREATE UNIQUE INDEX IF NOT EXISTS idx_audit_hash
    ON compliance_schema.audit_log (current_hash);

-- 基线条目：基线内查需求
CREATE INDEX IF NOT EXISTS idx_baseline_req
    ON compliance_schema.baseline_item (baseline_id, requirement_id);

-- ---------------------------------------------------------------------------
-- 5.5 esign_schema 索引
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_sign_entity
    ON esign_schema.signature_record (entity_type, entity_id);

CREATE INDEX IF NOT EXISTS idx_otp_user_expired
    ON esign_schema.otp_challenge (user_id, expires_at);

-- ---------------------------------------------------------------------------
-- 5.6 risk_schema 索引
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_risk_project
    ON risk_schema.risk_item (project_id, status);

-- ---------------------------------------------------------------------------
-- 5.7 proj_schema 索引
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS idx_project_code
    ON proj_schema.project (code);

-- ---------------------------------------------------------------------------
-- 5.8 report_schema 索引
-- ---------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_stat_metric
    ON report_schema.statistics_snapshot (project_id, metric_type, metric_key);

-- ---------------------------------------------------------------------------
-- 5.9 sys_schema 索引
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_username
    ON sys_schema."user" (username);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_email
    ON sys_schema."user" (email);

CREATE INDEX IF NOT EXISTS idx_ur_user
    ON sys_schema.user_role (user_id);

CREATE INDEX IF NOT EXISTS idx_rp_role
    ON sys_schema.role_permission (role_id);

CREATE INDEX IF NOT EXISTS idx_dict_type
    ON sys_schema.dict_entry (dict_type_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_oplog_user_time
    ON sys_schema.operation_log (user_id, created_at);

-- 额外唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS idx_role_code
    ON sys_schema.role (role_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_perm_code
    ON sys_schema.permission (perm_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_resource_code
    ON sys_schema.resource (resource_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_type_code
    ON sys_schema.dict_type (dict_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_org_code
    ON sys_schema.organization (org_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_config_key
    ON sys_schema.sys_config (config_key);

CREATE UNIQUE INDEX IF NOT EXISTS idx_cr_code
    ON chg_schema.change_request (cr_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_pr_code
    ON compliance_schema.problem_report (pr_code);

CREATE UNIQUE INDEX IF NOT EXISTS idx_signature_id
    ON esign_schema.signature_record (signature_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_jwt_jti
    ON esign_schema.jwt_blacklist (jti);

-- ============================================================================
-- 6. 触发器
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 6.1 audit_log 保护触发器 — 阻止 UPDATE 和 DELETE
-- ---------------------------------------------------------------------------

-- 阻止 UPDATE
CREATE OR REPLACE FUNCTION compliance_schema.fn_prevent_audit_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log records are immutable: UPDATE not allowed';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_audit_update ON compliance_schema.audit_log;
CREATE TRIGGER trg_prevent_audit_update
    BEFORE UPDATE ON compliance_schema.audit_log
    FOR EACH ROW EXECUTE FUNCTION compliance_schema.fn_prevent_audit_update();

COMMENT ON FUNCTION compliance_schema.fn_prevent_audit_update IS '审计日志保护：阻止UPDATE操作';

-- 阻止 DELETE
CREATE OR REPLACE FUNCTION compliance_schema.fn_prevent_audit_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit log records are immutable: DELETE not allowed';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_audit_delete ON compliance_schema.audit_log;
CREATE TRIGGER trg_prevent_audit_delete
    BEFORE DELETE ON compliance_schema.audit_log
    FOR EACH ROW EXECUTE FUNCTION compliance_schema.fn_prevent_audit_delete();

COMMENT ON FUNCTION compliance_schema.fn_prevent_audit_delete IS '审计日志保护：阻止DELETE操作';

-- ---------------------------------------------------------------------------
-- 6.2 需求编号自动生成触发器
-- 编号格式：{层级前缀}-{项目编号}-{序号}
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION req_schema.fn_auto_req_code()
RETURNS TRIGGER AS $$
DECLARE
    v_prefix TEXT;
    v_project_code TEXT;
BEGIN
    IF NEW.requirement_no IS NULL THEN
        -- 根据requirement_type确定层级前缀
        CASE NEW.requirement_type
            WHEN 'URS' THEN v_prefix := 'URS';
            WHEN 'PRS' THEN v_prefix := 'PRS';
            WHEN 'SRS' THEN v_prefix := 'SRS';
            WHEN 'DRS' THEN v_prefix := 'DRS';
            ELSE v_prefix := 'REQ';
        END CASE;
        -- 获取项目编号（跨Schema弱引用查询）
        SELECT code INTO v_project_code FROM proj_schema.project WHERE id = NEW.project_id;
        -- 生成编号：层级前缀-项目编号-序号
        NEW.requirement_no := v_prefix || '-' || COALESCE(v_project_code, 'TMP') || '-' ||
                        lpad(nextval('req_schema.seq_req_code')::text, 4, '0');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_auto_req_code ON req_schema.requirement;
CREATE TRIGGER trg_auto_req_code
    BEFORE INSERT ON req_schema.requirement
    FOR EACH ROW EXECUTE FUNCTION req_schema.fn_auto_req_code();

COMMENT ON FUNCTION req_schema.fn_auto_req_code IS '需求编号自动生成触发器：层级前缀-项目编号-序号';

-- ============================================================================
-- 7. 初始数据
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 7.1 字典类型初始数据（system级）
-- ---------------------------------------------------------------------------

INSERT INTO sys_schema.dict_type (dict_code, dict_name, category, status) VALUES
    ('REQ_TYPE',        '需求类型',     'system', 'active'),
    ('REQ_LEVEL',       '需求层级',     'system', 'active'),
    ('REQ_STATUS',      '需求状态',     'system', 'active'),
    ('REQ_PRIORITY',    '需求优先级',   'system', 'active'),
    ('REQ_SOURCE',      '需求来源',     'system', 'active'),
    ('TRACE_TYPE',      '追溯类型',     'system', 'active'),
    ('CHG_TYPE',        '变更类型',     'system', 'active'),
    ('CHG_STATUS',      '变更状态',     'system', 'active'),
    ('CHG_URGENCY',     '变更紧急度',   'system', 'active'),
    ('RISK_LEVEL',      '风险等级',     'system', 'active'),
    ('SAFETY_CLASS',    '安全分类',     'system', 'active'),
    ('BASELINE_STATUS', '基线状态',     'system', 'active'),
    ('DCP_STAGE',       'DCP阶段',      'system', 'active'),
    ('SIGN_INTENT',     '签名意图',     'system', 'active'),
    ('IMPACT_LEVEL',    '影响等级',     'system', 'active'),
    ('PROJECT_STATUS',  '项目状态',     'system', 'active')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7.2 字典类型初始数据（business级）
-- ---------------------------------------------------------------------------

INSERT INTO sys_schema.dict_type (dict_code, dict_name, category, status) VALUES
    ('SOUP_LICENSE',    'SOUP许可证类型',  'business', 'active'),
    ('REVIEW_RESULT',   '评审结论',        'business', 'active'),
    ('CONTROL_TYPE',    '控制措施类型',    'business', 'active'),
    ('REPORT_TYPE',     '报告类型',        'business', 'active'),
    ('NOTIFY_CHANNEL',  '通知渠道',        'business', 'active')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7.3 字典项初始数据
-- ---------------------------------------------------------------------------

-- REQ_TYPE: 需求类型
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'URS', '用户需求规格', 1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_TYPE'
UNION ALL
SELECT id, 'PRS', '产品需求规格', 2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_TYPE'
UNION ALL
SELECT id, 'SRS', '软件需求规格', 3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_TYPE'
UNION ALL
SELECT id, 'DRS', '设计需求规格', 4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_TYPE'
ON CONFLICT DO NOTHING;

-- REQ_LEVEL: 需求层级
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'L1', '用户需求', 1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_LEVEL'
UNION ALL
SELECT id, 'L2', '产品需求', 2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_LEVEL'
UNION ALL
SELECT id, 'L3', '系统需求', 3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_LEVEL'
UNION ALL
SELECT id, 'L4', '设计需求', 4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_LEVEL'
ON CONFLICT DO NOTHING;

-- REQ_STATUS: 需求状态（14项）
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'Draft',              '草稿',     1,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'PendingDecompose',   '待分解',   2,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Decomposed',         '已分解',   3,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Submitted',          '已提交',   4,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'InReview',           '评审中',   5,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Approved',           '已批准',   6,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Rejected',           '已驳回',   7,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'PendingVerify',      '待验证',   8,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Implemented',        '已实现',   9,  'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Verified',           '已验证',   10, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Baseline',           '已基线',   11, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Changed',            '已变更',   12, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Closed',             '已关闭',   13, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
UNION ALL
SELECT id, 'Retired',            '已退役',   14, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_STATUS'
ON CONFLICT DO NOTHING;

-- REQ_PRIORITY: 需求优先级（MoSCoW）
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'MUST',    '必须',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_PRIORITY'
UNION ALL
SELECT id, 'SHOULD',  '应该',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_PRIORITY'
UNION ALL
SELECT id, 'COULD',   '可以',     3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_PRIORITY'
UNION ALL
SELECT id, 'WONT',    '暂不',     4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_PRIORITY'
ON CONFLICT DO NOTHING;

-- REQ_SOURCE: 需求来源（9项）
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'USER_NEED',       '用户需求',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'REGULATION',      '法规要求',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'STANDARD',        '标准/指南',    3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'RISK_CONTROL',    '风险控制',     4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'MARKET_FEEDBACK', '市场反馈',     5, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'INTERNAL_IMPROVE','内部改进',     6, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'CUSTOMER_COMPLAINT','客户投诉',   7, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'CLINICAL_EVAL',   '临床评价',     8, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
UNION ALL
SELECT id, 'REVIEW_OPINION',  '审评意见',     9, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REQ_SOURCE'
ON CONFLICT DO NOTHING;

-- TRACE_TYPE: 追溯类型
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'satisfies',     '满足',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'TRACE_TYPE'
UNION ALL
SELECT id, 'satisfied_by',  '被满足',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'TRACE_TYPE'
UNION ALL
SELECT id, 'verifies',      '验证',     3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'TRACE_TYPE'
UNION ALL
SELECT id, 'verified_by',   '被验证',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'TRACE_TYPE'
ON CONFLICT DO NOTHING;

-- CHG_TYPE: 变更类型
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'MAJOR',     '重大变更',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_TYPE'
UNION ALL
SELECT id, 'NORMAL',    '一般变更',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_TYPE'
UNION ALL
SELECT id, 'DOCUMENT',  '文档变更',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_TYPE'
UNION ALL
SELECT id, 'EMERGENCY', '紧急变更',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_TYPE'
ON CONFLICT DO NOTHING;

-- CHG_STATUS: 变更状态（8项）
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'Draft',            '草稿',       1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'Analyzing',        '分析中',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'PendingApproval',  '待审批',     3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'Approved',         '已批准',     4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'Rejected',         '已驳回',     5, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'Executing',        '执行中',     6, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'Completed',        '已完成',     7, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
UNION ALL
SELECT id, 'Cancelled',        '已取消',     8, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_STATUS'
ON CONFLICT DO NOTHING;

-- CHG_URGENCY: 变更紧急度
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'HIGH',   '高',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_URGENCY'
UNION ALL
SELECT id, 'MEDIUM', '中',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_URGENCY'
UNION ALL
SELECT id, 'LOW',    '低',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CHG_URGENCY'
ON CONFLICT DO NOTHING;

-- RISK_LEVEL: 风险等级
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'HIGH',   '高',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'RISK_LEVEL'
UNION ALL
SELECT id, 'MEDIUM', '中',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'RISK_LEVEL'
UNION ALL
SELECT id, 'LOW',    '低',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'RISK_LEVEL'
ON CONFLICT DO NOTHING;

-- SAFETY_CLASS: 安全分类
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'A', 'A类（非伤害）',       1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SAFETY_CLASS'
UNION ALL
SELECT id, 'B', 'B类（非严重伤害）',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SAFETY_CLASS'
UNION ALL
SELECT id, 'C', 'C类（死亡或严重伤害）', 3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SAFETY_CLASS'
ON CONFLICT DO NOTHING;

-- BASELINE_STATUS: 基线状态
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'Draft',    '草稿',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'BASELINE_STATUS'
UNION ALL
SELECT id, 'Locked',   '已锁定',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'BASELINE_STATUS'
UNION ALL
SELECT id, 'Unlocked', '已解锁',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'BASELINE_STATUS'
UNION ALL
SELECT id, 'Archived', '已归档',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'BASELINE_STATUS'
ON CONFLICT DO NOTHING;

-- DCP_STAGE: DCP阶段
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'DCP1', '概念阶段',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'DCP_STAGE'
UNION ALL
SELECT id, 'DCP2', '计划阶段',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'DCP_STAGE'
UNION ALL
SELECT id, 'DCP3', '开发阶段',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'DCP_STAGE'
UNION ALL
SELECT id, 'DCP4', '验证阶段',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'DCP_STAGE'
UNION ALL
SELECT id, 'DCP5', '发布阶段',   5, 'active' FROM sys_schema.dict_type WHERE dict_code = 'DCP_STAGE'
ON CONFLICT DO NOTHING;

-- SIGN_INTENT: 签名意图
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'approve', '批准',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SIGN_INTENT'
UNION ALL
SELECT id, 'confirm', '确认',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SIGN_INTENT'
UNION ALL
SELECT id, 'review',  '评审',     3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SIGN_INTENT'
UNION ALL
SELECT id, 'release', '发布',     4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SIGN_INTENT'
ON CONFLICT DO NOTHING;

-- IMPACT_LEVEL: 影响等级
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'CRITICAL', '严重',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'IMPACT_LEVEL'
UNION ALL
SELECT id, 'MAJOR',    '重大',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'IMPACT_LEVEL'
UNION ALL
SELECT id, 'MINOR',    '轻微',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'IMPACT_LEVEL'
UNION ALL
SELECT id, 'NONE',     '无影响', 4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'IMPACT_LEVEL'
ON CONFLICT DO NOTHING;

-- PROJECT_STATUS: 项目状态
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'Active',    '进行中',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'PROJECT_STATUS'
UNION ALL
SELECT id, 'OnHold',    '已暂停',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'PROJECT_STATUS'
UNION ALL
SELECT id, 'Completed', '已完成',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'PROJECT_STATUS'
UNION ALL
SELECT id, 'Archived',  '已归档',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'PROJECT_STATUS'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7.4 业务级字典项
-- ---------------------------------------------------------------------------

-- SOUP_LICENSE: SOUP许可证类型
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'MIT',         'MIT',            1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
UNION ALL
SELECT id, 'Apache-2.0',  'Apache-2.0',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
UNION ALL
SELECT id, 'GPL',         'GPL',            3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
UNION ALL
SELECT id, 'LGPL',        'LGPL',           4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
UNION ALL
SELECT id, 'BSD',         'BSD',            5, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
UNION ALL
SELECT id, 'Commercial',  'Commercial',     6, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
UNION ALL
SELECT id, 'Other',       'Other',          7, 'active' FROM sys_schema.dict_type WHERE dict_code = 'SOUP_LICENSE'
ON CONFLICT DO NOTHING;

-- REVIEW_RESULT: 评审结论
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'APPROVED',    '批准',         1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REVIEW_RESULT'
UNION ALL
SELECT id, 'CONDITIONAL', '有条件批准',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REVIEW_RESULT'
UNION ALL
SELECT id, 'REJECTED',    '驳回',         3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REVIEW_RESULT'
ON CONFLICT DO NOTHING;

-- CONTROL_TYPE: 控制措施类型
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'DESIGN',      '设计控制',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CONTROL_TYPE'
UNION ALL
SELECT id, 'PROCESS',     '过程控制',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CONTROL_TYPE'
UNION ALL
SELECT id, 'INFORMATION', '信息控制',     3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'CONTROL_TYPE'
ON CONFLICT DO NOTHING;

-- REPORT_TYPE: 报告类型
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'TRACEABILITY', '追溯报告',   1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REPORT_TYPE'
UNION ALL
SELECT id, 'CHANGE',       '变更报告',   2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REPORT_TYPE'
UNION ALL
SELECT id, 'RISK',         '风险报告',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REPORT_TYPE'
UNION ALL
SELECT id, 'COMPLIANCE',   '合规报告',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REPORT_TYPE'
UNION ALL
SELECT id, 'PROJECT',      '项目报告',   5, 'active' FROM sys_schema.dict_type WHERE dict_code = 'REPORT_TYPE'
ON CONFLICT DO NOTHING;

-- NOTIFY_CHANNEL: 通知渠道
INSERT INTO sys_schema.dict_entry (dict_type_id, entry_code, entry_label, sort_order, status)
SELECT id, 'EMAIL',   '邮件',     1, 'active' FROM sys_schema.dict_type WHERE dict_code = 'NOTIFY_CHANNEL'
UNION ALL
SELECT id, 'SMS',     '短信',     2, 'active' FROM sys_schema.dict_type WHERE dict_code = 'NOTIFY_CHANNEL'
UNION ALL
SELECT id, 'IN_APP',  '站内信',   3, 'active' FROM sys_schema.dict_type WHERE dict_code = 'NOTIFY_CHANNEL'
UNION ALL
SELECT id, 'OA',      'OA系统',   4, 'active' FROM sys_schema.dict_type WHERE dict_code = 'NOTIFY_CHANNEL'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7.5 超级管理员初始角色和权限
-- ---------------------------------------------------------------------------

-- 超级管理员角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('SUPER_ADMIN', '超级管理员', '系统超级管理员，拥有所有权限', TRUE)
ON CONFLICT DO NOTHING;

-- 系统管理员角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('ADMIN', '系统管理员', '系统管理员，拥有大部分管理权限', TRUE)
ON CONFLICT DO NOTHING;

-- 项目经理角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('PROJECT_MANAGER', '项目经理', '项目经理，负责项目整体管理', TRUE)
ON CONFLICT DO NOTHING;

-- 需求工程师角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('REQ_ENGINEER', '需求工程师', '负责需求的创建、编辑和评审', TRUE)
ON CONFLICT DO NOTHING;

-- 评审人员角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('REVIEWER', '评审人员', '负责需求的评审和批准', TRUE)
ON CONFLICT DO NOTHING;

-- 测试工程师角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('TEST_ENGINEER', '测试工程师', '负责测试用例的创建和执行', TRUE)
ON CONFLICT DO NOTHING;

-- 合规管理员角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('COMPLIANCE_MANAGER', '合规管理员', '负责合规管理和审计', TRUE)
ON CONFLICT DO NOTHING;

-- 只读用户角色
INSERT INTO sys_schema.role (role_code, role_name, description, built_in)
VALUES ('VIEWER', '只读用户', '仅查看权限', TRUE)
ON CONFLICT DO NOTHING;

-- 核心权限定义
INSERT INTO sys_schema.permission (perm_code, perm_name, perm_type, parent_id) VALUES
    -- 一级菜单权限
    ('req:menu',         '需求管理菜单',     'menu', NULL),
    ('trace:menu',       '追溯管理菜单',     'menu', NULL),
    ('chg:menu',         '变更管理菜单',     'menu', NULL),
    ('compliance:menu',  '合规管理菜单',     'menu', NULL),
    ('esign:menu',       '电子签名菜单',     'menu', NULL),
    ('risk:menu',        '风险管理菜单',     'menu', NULL),
    ('proj:menu',        '项目管理菜单',     'menu', NULL),
    ('report:menu',      '报表仪表盘菜单',   'menu', NULL),
    ('sys:menu',         '系统管理菜单',     'menu', NULL)
ON CONFLICT DO NOTHING;

-- 需求管理操作权限
INSERT INTO sys_schema.permission (perm_code, perm_name, perm_type, parent_id) VALUES
    ('req:create',       '创建需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:edit',         '编辑需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:delete',       '删除需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:review',       '评审需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:approve',      '批准需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:view',         '查看需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:export',       '导出需求',         'button', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu'))
ON CONFLICT DO NOTHING;

-- API权限
INSERT INTO sys_schema.permission (perm_code, perm_name, perm_type, parent_id) VALUES
    ('req:api:read',     '需求读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('req:api:write',    '需求写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'req:menu')),
    ('trace:api:read',   '追溯读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'trace:menu')),
    ('trace:api:write',  '追溯写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'trace:menu')),
    ('chg:api:read',     '变更读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'chg:menu')),
    ('chg:api:write',    '变更写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'chg:menu')),
    ('compliance:api:read',  '合规读取API',  'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'compliance:menu')),
    ('compliance:api:write', '合规写入API',  'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'compliance:menu')),
    ('esign:api:read',   '签名读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'esign:menu')),
    ('esign:api:write',  '签名写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'esign:menu')),
    ('risk:api:read',    '风险读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'risk:menu')),
    ('risk:api:write',   '风险写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'risk:menu')),
    ('proj:api:read',    '项目读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'proj:menu')),
    ('proj:api:write',   '项目写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'proj:menu')),
    ('report:api:read',  '报表读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'report:menu')),
    ('sys:api:read',     '系统读取API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'sys:menu')),
    ('sys:api:write',    '系统写入API',      'api', (SELECT id FROM sys_schema.permission WHERE perm_code = 'sys:menu'))
ON CONFLICT DO NOTHING;

-- 超级管理员拥有所有权限
INSERT INTO sys_schema.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_schema.role r
CROSS JOIN sys_schema.permission p
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- 系统配置初始数据
INSERT INTO sys_schema.sys_config (config_key, config_value, config_type, description) VALUES
    ('system.name',           'Med-RMS',                     'string', '系统名称'),
    ('system.version',        '1.0.0',                       'string', '系统版本'),
    ('otp.expire_seconds',    '300',                         'integer','OTP过期时间（秒）'),
    ('otp.max_retry',         '3',                           'integer','OTP最大重试次数'),
    ('esign.password_policy', '8,1,1,1',                     'string', '签名密码策略（长度,大写,小写,数字,特殊字符）'),
    ('jwt.expire_minutes',    '480',                         'integer','JWT过期时间（分钟）'),
    ('audit.hash_algorithm',  'sha256',                      'string', '审计日志哈希算法'),
    ('baseline.compare_algo', 'sha256',                      'string', '基线对比算法'),
    ('login.max_attempts',    '5',                           'integer','登录最大尝试次数'),
    ('login.lock_minutes',    '30',                          'integer','账户锁定时间（分钟）')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 脚本结束
-- Med-RMS DDL v1.1 | PostgreSQL 16
-- 总计：9个Schema | 1个序列 | 45+张表 | 43+索引 | 2个触发器 | 初始数据
--
-- 变更记录：
-- | 版本 | 日期       | 变更内容                                                                 | 变更原因             |
-- |------|-----------|-------------------------------------------------------------------------|---------------------|
-- | v1.0 | 2026-05-22 | 初始版本                                                                 | 详细设计交付          |
-- | v1.1 | 2026-05-22 | requirement主表字段对齐系统架构§6.1.1：req_code→requirement_no(VARCHAR50), | C-01：主表字段与系统  |
-- |      |           | type→requirement_type(VARCHAR3), creator_id→created_by, 删除level/source, | 架构偏离             |
-- |      |           | 新增description/risk_level/safety_class/requirement_category/baseline_id/ |                     |
-- |      |           | version/is_deleted/updated_by; 触发器/索引同步更新; 补充4条新索引          |                     |
-- | v1.1 | 2026-05-22 | audit_log.prev_hash类型从TEXT改为CHAR(64) NOT NULL                         | C-02：哈希值类型缺失  |
-- |      |           |                                                                          | 长度约束和NOT NULL   |
-- ============================================================================
