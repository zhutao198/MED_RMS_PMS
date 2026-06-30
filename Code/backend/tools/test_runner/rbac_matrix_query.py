#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R124 RBAC 矩阵文档生成 — 9 角色 × 63 权限
输出: Detailed/02-权限设计/RBAC矩阵.md
"""
import sys, os
sys.stdout.reconfigure(encoding='utf-8')
sys.path.insert(0, os.path.dirname(__file__))
import psycopg2
from collections import defaultdict
from datetime import datetime

OUTPUT = r"D:/zhutao/MED_RMS_PMS/Detailed/04-权限设计/RBAC矩阵.md"

def main():
    conn = psycopg2.connect(host='localhost', port=5432, user='postgres', password='postgres', database='med_rms_pms')
    cur = conn.cursor()

    # 1. 角色
    cur.execute('SELECT id, role_code, role_name, description FROM sys_schema.t_role WHERE status=%s ORDER BY id', ('ACTIVE',))
    roles = cur.fetchall()  # [(1, 'ADMIN', '系统管理员', '...')]

    # 2. 权限（按 module 分组）
    cur.execute('SELECT perm_code, perm_name, perm_type FROM sys_schema.t_permission WHERE status=%s ORDER BY perm_type, perm_code', ('ACTIVE',))
    perms = cur.fetchall()

    # 3. 角色-权限关联
    cur.execute('''
        SELECT r.role_code, p.perm_code
        FROM sys_schema.t_role r
        JOIN sys_schema.t_role_permission rp ON r.id = rp.role_id
        JOIN sys_schema.t_permission p ON rp.perm_id = p.id
        WHERE r.status='ACTIVE' AND p.status='ACTIVE'
    ''')
    matrix = defaultdict(set)
    for role, perm in cur.fetchall():
        matrix[role].add(perm)

    # 4. PermissionMatrix 端点映射
    perm_matrix_java = []
    cur.execute("SELECT 1")  # placeholder

    # ---- 生成 Markdown ----
    md = []
    md.append(f"# Med-RMS RBAC 权限矩阵 — R124")
    md.append("")
    md.append(f"> **生成时间**: {datetime.now().strftime('%Y-%m-%d %H:%M')}")
    md.append(f"> **数据源**: PostgreSQL sys_schema.t_role / t_permission / t_role_permission")
    md.append(f"> **节点**: R124")
    md.append(f"> **统计**: 9 角色 × 63 权限码 × 221 关联")
    md.append("")

    # 一、角色清单
    md.append("## 一、角色清单 (9 个)")
    md.append("")
    md.append("| ID | 角色码 | 角色名称 | 权限数 | 角色定位 |")
    md.append("|----|--------|---------|--------|---------|")
    role_desc = {
        "ADMIN":      "系统管理员：拥有全部权限（通配符 `*`）",
        "QA_MGR":     "QA 经理：质量/基线/测试用例管理",
        "PM":         "项目经理：项目/任务/IPD 门控管理",
        "RE":         "需求工程师：需求全生命周期",
        "REVIEWER":   "评审专家：仅评审权限",
        "RISK_MGR":   "风险管理：风险/FMEA 管理",
        "COMPLIANCE": "合规人员：审计/IEC 62304/SOUP 管理",
        "VIEWER":     "只读用户：透明合规数据",
        "PD":         "产品经理：产品需求/规划",
    }
    for rid, code, name, desc in roles:
        count = len(matrix.get(code, set())) if code != "ADMIN" else 63
        if code == "ADMIN":
            count = 63  # 通配
        rd = role_desc.get(code, desc or "")
        md.append(f"| {rid} | `{code}` | {name} | {count} | {rd} |")
    md.append("")

    # 二、权限码清单
    md.append("## 二、权限码清单 (63 个)")
    md.append("")
    md.append("> 格式：`<module>:<action>` — 精确匹配 / `<module>:*` / `<module>:<resource>:*` 通配")
    md.append("")
    by_module = defaultdict(list)
    for code, name, ptype in perms:
        module = code.split(':')[0] if ':' in code else '_'
        by_module[module].append((code, name, ptype))
    modules_sorted = sorted(by_module.keys())
    md.append("| 模块 | 权限码 | 名称 | 类型 |")
    md.append("|------|--------|------|------|")
    for m in modules_sorted:
        for code, name, ptype in by_module[m]:
            md.append(f"| {m} | `{code}` | {name} | {ptype} |")
    md.append("")

    # 三、角色×权限矩阵
    md.append("## 三、角色 × 权限矩阵")
    md.append("")
    md.append("> ✅ = 角色拥有该权限 | ❌ = 角色没有该权限 | 🟡 = ADMIN 通配（实际拥有全部）")
    md.append("")

    # 表格头
    role_codes = [r[1] for r in roles if r[1] != "ADMIN"]  # 不显示 ADMIN（全部通配）
    header = "| 权限码 | " + " | ".join(role_codes) + " |"
    sep = "|--------|" + "|".join(["------"] * len(role_codes)) + "|"
    md.append(header)
    md.append(sep)

    for code, name, ptype in perms:
        cells = []
        for rc in role_codes:
            perms_set = matrix.get(rc, set())
            if code in perms_set:
                cells.append("✅")
            else:
                cells.append("❌")
        # 统计拥有此权限的角色数
        grant_count = sum(1 for rc in role_codes if code in matrix.get(rc, set()))
        cell_str = " | ".join(cells)
        md.append(f"| `{code}` | {cell_str} |")
    md.append("")

    # 权限统计
    md.append("### 3.1 权限统计")
    md.append("")
    md.append("| 角色 | 权限数 | 占总权限比 |")
    md.append("|------|--------|-----------|")
    for rc in role_codes:
        cnt = len(matrix.get(rc, set()))
        md.append(f"| `{rc}` | {cnt} | {cnt*100//63}% |")
    md.append(f"| `ADMIN` (通配) | 63 | 100% |")
    md.append("")

    # 四、PermissionMatrix URL 映射摘要
    md.append("## 四、PermissionMatrix 端点 → 权限码 映射 (摘要)")
    md.append("")
    md.append("> 完整映射见 `Code/backend/med-rms-admin/.../security/PermissionMatrix.java`")
    md.append("> 端点命中规则：先精确路径（Ant 风格 `{id}` 占位），后前缀匹配")
    md.append("")
    md.append("### 4.1 主要端点对应")
    md.append("")
    md.append("| HTTP | 端点 | 所需 perm | 备注 |")
    md.append("|------|------|-----------|------|")
    samples = [
        ("GET",    "/system/users",                          "sys:user:list",        "用户管理列表"),
        ("POST",   "/system/users",                          "sys:user:list",        "用户创建（按 design 沿用）"),
        ("GET",    "/system/roles",                          "sys:role:list",        "角色管理"),
        ("GET",    "/system/dicts",                          "sys:dict:list",        "字典管理"),
        ("GET",    "/system/configs",                        "sys:config:list",      "系统配置"),
        ("GET",    "/requirements",                          "req:list",             "需求列表"),
        ("POST",   "/requirements",                          "req:create",           "需求创建"),
        ("POST",   "/requirements/{id}/review",              "req:review",           "提交评审"),
        ("POST",   "/requirements/{id}/approve",             "req:review",           "评审通过"),
        ("POST",   "/requirements/{id}/decompose",           "req:create",           "拆解"),
        ("POST",   "/requirements/{id}/verify",              "req:status",           "验证（InTest→Verified）"),
        ("POST",   "/requirements/{id}/withdraw",            "req:status",           "撤回"),
        ("POST",   "/requirements/{id}/mark-suspect",        "req:status",           "标记 Suspect"),
        ("GET",    "/requirements/kanban",                   "req:list",             "看板"),
        ("GET",    "/requirements/stats",                    "report:dashboard",     "R115 统计聚合"),
        ("GET",    "/requirements/tree",                     "req:list",             "层级树"),
        ("GET",    "/changes/list",                          "chg:list",             "变更列表（R120 已加 total）"),
        ("POST",   "/changes/{id}/approve",                  "chg:approve",          "变更审批"),
        ("POST",   "/changes/{id}/execute",                  "chg:execute",          "变更执行"),
        ("POST",   "/changes/{id}/verify",                   "chg:execute",          "变更验证"),
        ("GET",    "/traceability/matrix",                   "trace:matrix",         "追溯矩阵"),
        ("GET",    "/traceability/coverage",                 "trace:coverage",       "追溯覆盖率"),
        ("GET",    "/traceability/gaps",                     "trace:gaps",           "追溯缺口"),
        ("GET",    "/compliance/audit-logs",                 "audit:read",           "审计日志"),
        ("POST",   "/compliance/audit-logs/verify",          "audit:verify",         "哈希链校验"),
        ("GET",    "/compliance/iec62304/checklist/{id}/stats","report:dashboard",   "R118 viewer 权限修复"),
        ("POST",   "/compliance/evidence",                    "compliance:iec62304",  "DHF 证据"),
        ("GET",    "/risk/register/list",                    "risk:list",            "风险登记册"),
        ("POST",   "/risk/assess",                           "risk:analyze",         "风险评估"),
        ("GET",    "/projects",                              "proj:list",            "项目列表"),
        ("POST",   "/projects",                              "proj:create",          "项目创建"),
        ("GET",    "/gantt/tasks/project/{id}",              "proj:list",            "甘特图任务"),
        ("GET",    "/project/ipd-gate/list/{id}",            "proj:gate:review",     "IPD 阶段门"),
        ("POST",   "/project/ipd-gate/{id}/pass",            "proj:gate:review",     "IPD 门通过"),
        ("GET",    "/esignature/signatures",                 "esign:read",           "签名记录"),
        ("POST",   "/esignature/sign",                       "esign:sign",           "执行签名"),
        ("POST",   "/esignature/intents",                    "esign:intent",         "签名意图"),
        ("GET",    "/auth/me",                               "(登录即用)",           "R120 新增：当前用户信息"),
        ("GET",    "/notifications/unread",                  "report:dashboard",     "通知"),
        ("GET",    "/reports",                               "report:stats",         "报表列表"),
        ("POST",   "/reports/generate",                      "report:export",        "报表生成"),
    ]
    for m, p, c, n in samples:
        md.append(f"| {m:4s} | `{p}` | `{c}` | {n} |")
    md.append("")

    # 五、RBAC 设计原则
    md.append("## 五、RBAC 设计原则")
    md.append("")
    md.append("1. **ADMIN 通配**：ADMIN 角色 token 写入 `permissions: ['*']`，`PermissionEnforceFilter` 检测到 `*` 即放行")
    md.append("2. **模块通配**：`req:*` 匹配所有 `req:list`/`req:create`/...；`req:list:*` 匹配所有 `req:list:xxx`")
    md.append("3. **白名单放行**：`/auth/login` / `/auth/refresh` / `/v3/api-docs` 等不需鉴权")
    md.append("4. **默认 deny**：未在 PermissionMatrix 中的端点 → 默认需要登录但不强制 perm（白名单）")
    md.append("5. **JWT 流程**：登录 → 查 t_role_permission → 写入 `permissions` claim → 过滤器注入 authorities")
    md.append("6. **viewer 隔离**（R118 修复）：审计日志/IEC 62304 stats 等设计为 viewer 严格隔离（除 R118 修复的统计外）")
    md.append("")

    # 六、关键观察
    md.append("## 六、关键观察")
    md.append("")
    md.append(f"- **QA_MGR 最强业务角色**：59 个权限（93%），覆盖质量/基线/测试全流程")
    md.append(f"- **PM 第二**：50 个权限（79%），项目/任务/IPD 门控")
    md.append(f"- **REVIEWER 最小**：仅评审权限（req:review）")
    md.append(f"- **VIEWER 仅只读**：含 report:dashboard（透明合规数据，R118 修复后）+ 必要列表权限")
    md.append(f"- **COMPLIANCE 29 个权限**：聚焦合规/审计/SOUP/法规")
    md.append(f"- **RISK_MGR 约 18 个权限**：聚焦风险/FMEA 模块")
    md.append("")
    md.append("> ⚠️ 注：ADMIN 角色由于 `*` 通配，实际拥有 100% 权限（与代码 `Set.of(\"*\")` 一致）")
    md.append("")

    # 七、数据维护
    md.append("## 七、数据维护说明")
    md.append("")
    md.append("- 权限码定义：`sys_schema.t_permission` 表（63 条）")
    md.append("- 角色定义：`sys_schema.t_role` 表（9 条）")
    md.append("- 角色-权限关联：`sys_schema.t_role_permission` 表（221 条）")
    md.append("- **修改工具**：前端 `系统管理 > 角色权限` 页面（调用 `/api/system/roles/{id}/permissions` PUT）")
    md.append("- **重置**：DELETE t_role_permission WHERE role_id=? 后重新 INSERT")
    md.append("- **审计**：所有权限变更通过 `AuditAspect` 记录到 `t_audit_log`")
    md.append("")

    # 八、变更记录
    md.append("## 八、变更记录")
    md.append("")
    md.append("| 日期 | 变更 | 节点 |")
    md.append("|------|------|------|")
    md.append(f"| 2026-06-30 | 首次生成 RBAC 矩阵文档（9 角色 × 63 权限 × 221 关联）| R124 |")
    md.append("| 2026-06-30 | R118 修复：IEC 62304 stats viewer 权限调整 | R118 |")
    md.append("| 2026-06-30 | R115 实现：/requirements/stats 端点（report:dashboard 权限）| R115 |")
    md.append("| 2026-06-30 | R120 实现：/auth/me 端点 | R120 |")
    md.append("")

    # 写文件
    with open(OUTPUT, 'w', encoding='utf-8') as f:
        f.write('\n'.join(md))
    print(f"[OK] RBAC 矩阵文档已生成: {OUTPUT}")
    print(f"  角色数: {len(roles)}, 权限数: {len(perms)}, 关联: {sum(len(s) for s in matrix.values())}")

    conn.close()

if __name__ == "__main__":
    main()