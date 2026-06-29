-- R99 实施：组织架构 CRUD + 树形支持
-- 背景：原 SystemService.getOrgTree() 仅从 t_user.department 字符串字段聚合为 1 层"伪树"，不满足 PRD §7.9.4 OA 同步组织架构 / 部门层级管理需求
-- 方案：新建 sys_schema.t_department 表（树形）+ t_user 加 dept_id 外键 + 预置顶级部门

-- 1. 新建部门表
CREATE TABLE IF NOT EXISTS sys_schema.t_department (
    id           BIGSERIAL PRIMARY KEY,
    parent_id    BIGINT NOT NULL DEFAULT 0,
    name         VARCHAR(100) NOT NULL,
    code         VARCHAR(50) UNIQUE,
    sort_order   INT DEFAULT 0,
    level        INT DEFAULT 1,
    path         VARCHAR(500),
    leader_id    BIGINT,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted   BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_dept_parent ON sys_schema.t_department(parent_id, is_deleted, sort_order);
CREATE INDEX IF NOT EXISTS idx_dept_code ON sys_schema.t_department(code);
CREATE INDEX IF NOT EXISTS idx_dept_path ON sys_schema.t_department(path);
CREATE INDEX IF NOT EXISTS idx_dept_leader ON sys_schema.t_department(leader_id);

COMMENT ON TABLE  sys_schema.t_department IS '组织架构-部门表（支持任意层级树，R99）';
COMMENT ON COLUMN sys_schema.t_department.parent_id  IS '父部门ID，0 表示顶级根';
COMMENT ON COLUMN sys_schema.t_department.code      IS '部门唯一编码，跨层级唯一';
COMMENT ON COLUMN sys_schema.t_department.path      IS '物化路径 /a/b/c，便于按 ancestor 高效查询子树';
COMMENT ON COLUMN sys_schema.t_department.level     IS '层级深度（1=顶级，2=二级...）';
COMMENT ON COLUMN sys_schema.t_department.sort_order IS '同级排序，asc';
COMMENT ON COLUMN sys_schema.t_department.leader_id  IS '部门负责人 user_id（不强制 FK）';
COMMENT ON COLUMN sys_schema.t_department.is_deleted IS '软删除标记';

-- 2. t_user 增加 dept_id 外键列（不强制 FK，保持双轨：旧数据 department 字符串仍可用）
ALTER TABLE sys_schema.t_user ADD COLUMN IF NOT EXISTS dept_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_user_dept_id ON sys_schema.t_user(dept_id);
COMMENT ON COLUMN sys_schema.t_user.dept_id IS '主部门ID（外键 sys_schema.t_department.id，双轨：旧字段 department 字符串仍可用）';

-- 3. 预置顶级部门（5 个一级部门）
INSERT INTO sys_schema.t_department (parent_id, name, code, sort_order, level, path)
VALUES
    (0, '研发中心',       'RND',  1, 1, '/1'),
    (0, '测试部',         'QA',   2, 1, '/1'),
    (0, '合规部',         'RA',   3, 1, '/1'),
    (0, '项目管理办公室', 'PMO',  4, 1, '/1'),
    (0, '质量管理部',     'QM',   5, 1, '/1')
ON CONFLICT (code) DO NOTHING;

-- 4. 预置二级部门（研发中心下的子部门，演示层级）
DO $$
DECLARE
    rnd_id BIGINT;
BEGIN
    SELECT id INTO rnd_id FROM sys_schema.t_department WHERE code = 'RND' AND is_deleted = FALSE LIMIT 1;
    IF rnd_id IS NOT NULL THEN
        INSERT INTO sys_schema.t_department (parent_id, name, code, sort_order, level, path)
        VALUES
            (rnd_id, '嵌入式组',   'RND-EMB',  1, 2, '/' || rnd_id || '/'),
            (rnd_id, '应用软件组', 'RND-APP',  2, 2, '/' || rnd_id || '/'),
            (rnd_id, '算法组',     'RND-ALG',  3, 2, '/' || rnd_id || '/')
        ON CONFLICT (code) DO NOTHING;
    END IF;
END $$;

-- 5. 将现有用户按 department 字符串映射到 dept_id（双轨兼容）
UPDATE sys_schema.t_user u
SET dept_id = d.id
FROM sys_schema.t_department d
WHERE u.is_deleted = FALSE
  AND u.dept_id IS NULL
  AND u.department IS NOT NULL
  AND u.department <> ''
  AND (
       (u.department = '研发部'        AND d.code = 'RND')
    OR (u.department = '测试部'        AND d.code = 'QA')
    OR (u.department = '合规部'        AND d.code = 'RA')
    OR (u.department = '项目管理部'    AND d.code = 'PMO')
    OR (u.department = '质量管理部'    AND d.code = 'QM')
    OR (u.department LIKE '%研发%'     AND d.code = 'RND')
    OR (u.department LIKE '%测试%'     AND d.code = 'QA')
    OR (u.department LIKE '%合规%'     AND d.code = 'RA')
  );

-- 6. 验证
SELECT 't_department 顶级部门' AS info, COUNT(*) AS cnt FROM sys_schema.t_department WHERE parent_id = 0 AND is_deleted = FALSE
UNION ALL
SELECT 't_department 总部门数', COUNT(*) FROM sys_schema.t_department WHERE is_deleted = FALSE
UNION ALL
SELECT 't_user 已关联 dept_id 数', COUNT(*) FROM sys_schema.t_user WHERE dept_id IS NOT NULL AND is_deleted = FALSE;