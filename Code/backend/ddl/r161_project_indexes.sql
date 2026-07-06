-- =============================================================================
-- R161 修复（F7）：/projects 性能优化
-- 加索引：idx_proj_active（部分索引，覆盖 is_deleted=false 主流查询）
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_proj_active_created
    ON proj_schema.t_project(created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_proj_status_active
    ON proj_schema.t_project(status, created_at DESC)
    WHERE is_deleted = FALSE;

-- 验证
SELECT 'idx_proj_active_created' AS index_name,
       indexdef
FROM pg_indexes
WHERE schemaname='proj_schema'
  AND tablename='t_project'
  AND indexname='idx_proj_active_created';