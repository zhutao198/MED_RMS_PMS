#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R125 PD 产品经理角色权限修复 — 0 → 21 权限
"""
import sys, os
sys.stdout.reconfigure(encoding='utf-8')
import psycopg2

# R125 PD 权限清单（产品经理合理范围）
PD_PERMS = [
    # 需求全生命周期（产品核心）
    'req:list', 'req:create', 'req:update', 'req:delete',
    'req:submit', 'req:review', 'req:status',
    # 项目可见性 + 成员管理
    'proj:list', 'proj:create', 'proj:update', 'proj:member',
    # 变更参与（可发起 + 审批）
    'chg:list', 'chg:create', 'chg:approve',
    # 风险查看
    'risk:list',
    # 报表/合规只读
    'report:dashboard', 'report:stats', 'report:export',
    'regulation:read', 'audit:read',
    # 系统（仅查看）
    'sys:org:list',
]

def main():
    conn = psycopg2.connect(host='localhost', port=5432, user='postgres', password='postgres', database='med_rms_pms')
    cur = conn.cursor()

    # PD 角色 ID
    cur.execute("SELECT id FROM sys_schema.t_role WHERE role_code='PD'")
    row = cur.fetchone()
    if not row:
        print('[FAIL] PD 角色不存在')
        return 1
    pd_id = row[0]

    # 现有 PD 权限
    cur.execute('''
        SELECT p.perm_code FROM sys_schema.t_role r
        JOIN sys_schema.t_role_permission rp ON r.id = rp.role_id
        JOIN sys_schema.t_permission p ON rp.perm_id = p.id
        WHERE r.role_code='PD'
    ''')
    existing = set(r[0] for r in cur.fetchall())
    print(f'[R125] PD 角色 id={pd_id}，现有 {len(existing)} 个权限')

    # 映射 perm_code → perm_id
    placeholders = ','.join(['%s'] * len(PD_PERMS))
    cur.execute(f'SELECT perm_code, id FROM sys_schema.t_permission WHERE perm_code IN ({placeholders})', PD_PERMS)
    perm_map = dict(cur.fetchall())

    # 计算新增
    to_add = [code for code in PD_PERMS if code in perm_map and code not in existing]
    print(f'[R125] 待新增 {len(to_add)} 个权限')

    # 批量插入
    for code in to_add:
        pid = perm_map[code]
        cur.execute('''
            INSERT INTO sys_schema.t_role_permission (role_id, perm_id, created_at)
            VALUES (%s, %s, NOW())
            ON CONFLICT (role_id, perm_id) DO NOTHING
        ''', (pd_id, pid))

    conn.commit()
    print(f'[R125] PD 角色权限修复完成: {len(existing)} → {len(existing) + len(to_add)}')

    # 验证
    cur.execute('''
        SELECT p.perm_code FROM sys_schema.t_role r
        JOIN sys_schema.t_role_permission rp ON r.id = rp.role_id
        JOIN sys_schema.t_permission p ON rp.perm_id = p.id
        WHERE r.role_code='PD'
        ORDER BY p.perm_code
    ''')
    final = [r[0] for r in cur.fetchall()]
    print(f'[R125] PD 最终权限: {len(final)} 个')
    for code in final:
        print(f'  - {code}')

    conn.close()
    return 0

if __name__ == "__main__":
    sys.exit(main())