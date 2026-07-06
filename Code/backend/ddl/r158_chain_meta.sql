-- =============================================================================
-- R158 修复：DML 不修改（§11.10(e) 审计证据保留）+ META 记录声明历史重建
-- 触发：opencode 测试发现 F1 哈希链在 id=29 + id=32 两处断裂
-- 根因：getLastHash() 并发竞态（已用 synchronized 在源码修复）
-- 此 DML：仅追加 META 记录说明情况，原始 audit_log 字段不动
-- =============================================================================

-- META 记录（R158）：声明当前链存在历史算法迭代残留
-- eventType=META 表示这是一条元数据，不是业务操作
INSERT INTO compliance_schema.t_audit_log
    (prev_hash, current_hash, event_type, entity_type, entity_id,
     operator_id, operator_name, operation, reason, ip_address, created_at)
VALUES
    (NULL, NULL, 'META', 'AUDIT_CHAIN', 0,
     0, 'R158_system', 'R158 历史链重建声明（F1 修复）',
     'opencode 测试发现 F1 链在 id=29+id=32 两处断裂，由 R147 之前 getLastHash() 并发竞态导致。源码已加 synchronized 杜绝未来竞态。历史链按 21 CFR Part 11 §11.10(e) 保留原始证据。',
     '127.0.0.1', NOW());

-- 不修改现有记录的 prev_hash / current_hash（合规硬约束：审计记录不可改）
-- 验证：调用 /compliance/audit-logs/verify/from/{断裂点+1} 验证断裂点之后链完整