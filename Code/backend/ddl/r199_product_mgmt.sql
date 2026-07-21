-- R199 v1.62: 产品管理模块全量实施（应用 18 项评审修复）
-- 触发：现有"适用产品"下拉硬编码（心电监护仪 v3.0 / 脉搏血氧仪 v2.1）无法选 8333 等用户产品
-- 范围：
--   1. 新建 prd_schema + t_product（医疗器械型号字典）
--   2. 21 CFR Part 11 合规：trg_prevent_hard_delete + record_hash（G16/G17）
--   3. partial unique index（软删除后可重建同 product_code）
--   4. status CHECK 约束
--   5. 字典 product_line（统一小写，与 DictItem API 一致）
--   6. 现有 3 表加 product_id 列 + 数据迁移 SQL（反查回填）

-- ==========================================================
-- §1: 新建 Schema
-- ==========================================================
CREATE SCHEMA IF NOT EXISTS prd_schema;
COMMENT ON SCHEMA prd_schema IS '产品管理限界上下文（R199 v1.62）';

-- ==========================================================
-- §2: t_product 表（含合规字段）
-- ==========================================================
CREATE TABLE IF NOT EXISTS prd_schema.t_product (
    id              BIGSERIAL PRIMARY KEY,
    product_code    VARCHAR(50)  NOT NULL,
    product_name    VARCHAR(200) NOT NULL,
    product_line    VARCHAR(50),
    status          VARCHAR(20) DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','DISCONTINUED','DEVELOPMENT')),
    description     TEXT,
    record_hash     VARCHAR(64),             -- R197 G17：SHA-256 防篡改
    is_deleted      BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE  prd_schema.t_product IS '产品字典（医疗器械型号管理）';
COMMENT ON COLUMN prd_schema.t_product.product_code IS '产品型号编码，如 8333';
COMMENT ON COLUMN prd_schema.t_product.product_name IS '产品名称，如 8333 多参数监护仪';
COMMENT ON COLUMN prd_schema.t_product.product_line IS '产品线，关联 sys_schema.t_dict_item(dict_type=product_line)';
COMMENT ON COLUMN prd_schema.t_product.status IS 'ACTIVE=在产, DISCONTINUED=停产, DEVELOPMENT=开发中';
COMMENT ON COLUMN prd_schema.t_product.record_hash IS 'R197 G17：SHA-256 记录校验和';

-- ==========================================================
-- §3: 索引
-- ==========================================================

-- §3.1 partial unique index：仅未删除时强制唯一，软删除后可重建
CREATE UNIQUE INDEX IF NOT EXISTS uq_product_code_active
    ON prd_schema.t_product(product_code) WHERE NOT is_deleted;

-- §3.2 普通索引（仅未删除时生效）
CREATE INDEX IF NOT EXISTS idx_product_line
    ON prd_schema.t_product(product_line) WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_product_status
    ON prd_schema.t_product(status) WHERE NOT is_deleted;

-- ==========================================================
-- §4: 字典数据（product_line — 统一小写）
-- R199 修复：v1.0 设计写 PRODUCT_LINE（大写），与 DictItem API 不一致
-- ==========================================================
INSERT INTO sys_schema.t_dict_item (dict_type, item_code, item_name, sort_order) VALUES
('product_line', 'MONITOR',      '监护仪',   1),
('product_line', 'ECG',          '心电',     2),
('product_line', 'SPO2',         '血氧',     3),
('product_line', 'NIBP',         '血压',     4),
('product_line', 'ULTRASOUND',   '超声',     5)
ON CONFLICT DO NOTHING;

-- ==========================================================
-- §5: 21 CFR Part 11 合规触发器（G16/G17）
-- 复用 R197 定义的 public.fn_prevent_hard_delete / fn_compute_record_hash
-- ==========================================================

-- §5.1 G16：禁止硬删除触发器
DROP TRIGGER IF EXISTS trg_prevent_hard_delete ON prd_schema.t_product;
CREATE TRIGGER trg_prevent_hard_delete
    BEFORE DELETE ON prd_schema.t_product
    FOR EACH ROW EXECUTE FUNCTION public.fn_prevent_hard_delete();

-- §5.2 G17：record_hash 自动维护触发器
DROP TRIGGER IF EXISTS trg_record_hash ON prd_schema.t_product;
CREATE TRIGGER trg_record_hash
    BEFORE INSERT OR UPDATE ON prd_schema.t_product
    FOR EACH ROW EXECUTE FUNCTION public.fn_compute_record_hash();

-- ==========================================================
-- §6: Seed 数据（5 个产品）
-- ==========================================================
INSERT INTO prd_schema.t_product (product_code, product_name, product_line, status) VALUES
('8333',   '8333 多参数监护仪',  'MONITOR',    'ACTIVE'),
('iMEC15', 'iMEC 15 病人监护仪', 'MONITOR',    'ACTIVE'),
('ECG-3',  '心电监护仪 v3.0',    'ECG',        'ACTIVE'),
('SPO2-2', '脉搏血氧仪 v2.1',    'SPO2',       'ACTIVE'),
('NIBP-3', '无创血压监护模块',   'NIBP',       'ACTIVE')
ON CONFLICT DO NOTHING;

-- ==========================================================
-- §7: 现有表加 product_id 列（可空，保留历史数据）
-- ==========================================================

-- §7.1 t_project
ALTER TABLE proj_schema.t_project
    ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES prd_schema.t_product(id);
CREATE INDEX IF NOT EXISTS idx_proj_product ON proj_schema.t_project(product_id);

-- §7.2 t_requirement
ALTER TABLE req_schema.t_requirement
    ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES prd_schema.t_product(id);
CREATE INDEX IF NOT EXISTS idx_req_product ON req_schema.t_requirement(product_id);

-- §7.3 t_requirement_pool
ALTER TABLE req_schema.t_requirement_pool
    ADD COLUMN IF NOT EXISTS product_id BIGINT REFERENCES prd_schema.t_product(id);
CREATE INDEX IF NOT EXISTS idx_pool_product ON req_schema.t_requirement_pool(product_id);

-- ==========================================================
-- §8: 数据迁移 — 基于现有硬编码反查回填 product_id
-- 注：实施前需在测试环境验证匹配字符串
-- ==========================================================

-- §8.1 t_project：根据项目名模糊匹配
UPDATE proj_schema.t_project t
SET product_id = p.id
FROM prd_schema.t_product p
WHERE t.product_id IS NULL
  AND (
       (t.name LIKE '%8333%'                AND p.product_code = '8333')
    OR (t.name LIKE '%iMEC 15%'             AND p.product_code = 'iMEC15')
    OR (t.name LIKE '%iMEC15%'              AND p.product_code = 'iMEC15')
    OR (t.name LIKE '%心电监护仪 v3.0%'     AND p.product_code = 'ECG-3')
    OR (t.name LIKE '%脉搏血氧仪 v2.1%'     AND p.product_code = 'SPO2-2')
    OR (t.name LIKE '%无创血压%'             AND p.product_code = 'NIBP-3')
  );

-- §8.2 t_requirement：依赖现有 product_code 字段（如果存在，否则按 requirement_no 匹配）
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'req_schema'
      AND table_name = 't_requirement'
      AND column_name = 'product_code'
  ) THEN
    EXECUTE '
      UPDATE req_schema.t_requirement r
      SET product_id = p.id
      FROM prd_schema.t_product p
      WHERE r.product_id IS NULL
        AND r.product_code = p.product_code
        AND NOT p.is_deleted';
  ELSE
    RAISE NOTICE 't_requirement 无 product_code 列，跳过数据迁移';
  END IF;
END $$;

-- §8.3 t_requirement_pool：同上
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'req_schema'
      AND table_name = 't_requirement_pool'
      AND column_name = 'product_code'
  ) THEN
    EXECUTE '
      UPDATE req_schema.t_requirement_pool r
      SET product_id = p.id
      FROM prd_schema.t_product p
      WHERE r.product_id IS NULL
        AND r.product_code = p.product_code
        AND NOT p.is_deleted';
  ELSE
    RAISE NOTICE 't_requirement_pool 无 product_code 列，跳过数据迁移';
  END IF;
END $$;

-- ==========================================================
-- §9: 验证查询（实施后人工检查）
-- ==========================================================
-- SELECT '产品总数', COUNT(*) FROM prd_schema.t_product WHERE NOT is_deleted;
-- SELECT '已回填项目数', COUNT(*) FROM proj_schema.t_project WHERE product_id IS NOT NULL;
-- SELECT '已回填需求数', COUNT(*) FROM req_schema.t_requirement WHERE product_id IS NOT NULL;
-- SELECT '已回填池条目数', COUNT(*) FROM req_schema.t_requirement_pool WHERE product_id IS NOT NULL;

-- ==========================================================
-- §10: RBAC 种子数据 — product:* 权限码 + 角色授权
-- 依赖：sys_schema.t_permission / t_role_permission（DDL 121）
-- ADMIN 通配 '*' 自动获得所有 product:* 权限
-- ==========================================================

-- §10.1 插入 6 条 product:* 权限码（如果不存在）
INSERT INTO sys_schema.t_permission (perm_code, perm_name, perm_type, status) VALUES
('product:list',     '查看产品列表',     'MENU',   'ACTIVE'),
('product:create',   '创建产品',         'BUTTON', 'ACTIVE'),
('product:update',   '编辑产品',         'BUTTON', 'ACTIVE'),
('product:delete',   '删除产品',         'BUTTON', 'ACTIVE'),
('product:export',   '导出产品Excel',    'BUTTON', 'ACTIVE')
ON CONFLICT (perm_code) DO NOTHING;

-- §10.2 角色授权：参考 Detailed/04-权限设计/RBAC矩阵.md
-- PD（产品经理）：list + create + update + export（不可 delete）
-- QA_MGR / PM：list + export（read-only）
-- 其他角色：list（仅查看）
-- ADMIN：通配 '*' 自动覆盖

-- PD 角色（role_code='PD'）
INSERT INTO sys_schema.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'PD'
  AND p.perm_code IN ('product:list', 'product:create', 'product:update', 'product:export')
ON CONFLICT DO NOTHING;

-- QA_MGR 角色
INSERT INTO sys_schema.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'QA_MGR'
  AND p.perm_code IN ('product:list', 'product:export')
ON CONFLICT DO NOTHING;

-- PM 角色
INSERT INTO sys_schema.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code = 'PM'
  AND p.perm_code IN ('product:list', 'product:export')
ON CONFLICT DO NOTHING;

-- RE / REVIEWER / RISK_MGR / COMPLIANCE / VIEWER：仅 list
INSERT INTO sys_schema.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_schema.t_role r, sys_schema.t_permission p
WHERE r.role_code IN ('RE','REVIEWER','RISK_MGR','COMPLIANCE','VIEWER')
  AND p.perm_code = 'product:list'
ON CONFLICT DO NOTHING;

-- 注：ADMIN 不需要显式授权，PermissionService 检测到 'ADMIN' 角色返回 Set.of('*') 通配

-- ==========================================================
-- §11: 关联 DDL（按依赖顺序）
-- 必须在 r197_compliance_triggers.sql 之后执行（依赖 public.fn_* 函数）
-- 必须在 r164_audit_log_partition.sql 之前执行（避免触发器冲突）
-- 必须 sys_schema.t_permission / t_role_permission 存在（DDL 121）
-- ==========================================================