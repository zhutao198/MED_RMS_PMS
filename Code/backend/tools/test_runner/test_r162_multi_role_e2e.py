#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R162 多角色 RBAC 端到端测试 — 9 角色 × 关键端点

根据 RBAC 矩阵（9 角色 × 63 权限码 × 221 关联）验证：
  - ADMIN: 全通（通配 `*`）
  - 各业务角色: 应放行（200）和应拒绝（403）的端点
"""

import sys, os, time, json
from typing import List, Tuple, Dict
sys.path.insert(0, os.path.dirname(__file__))
from common import login, check_endpoint, TEST_USERS

BASE = "http://localhost:8080"

# ── 端点定义 ──────────────────────────────────────────────────────
# 每个条目: (method, path, [body])
# GET 无 body；POST 有 body

ENDPOINTS_ALL = [
    # (key, method, path, body_or_None)
    # ─ 认证
    ("auth_me", "GET", "/auth/me", None),
    # ─ 项目
    ("proj_list", "GET", "/projects?page=0&size=10", None),
    ("proj_create", "POST", "/projects", None),  # 不传 body → 验证能否到达端点（400 vs 403）
    ("proj_ipd_gate", "GET", "/project/ipd-gate/list/1", None),
    # ─ 需求
    ("req_list", "GET", "/requirements?page=0&size=10", None),
    ("req_create", "POST", "/requirements", None),
    ("req_review", "POST", "/requirements/1/review", None),
    ("req_stats", "GET", "/requirements/stats", None),
    ("req_kanban", "GET", "/requirements/kanban", None),
    # ─ 变更
    ("chg_list", "GET", "/changes/list?page=0&size=10", None),
    ("chg_create", "POST", "/changes", None),
    # ─ 追溯
    ("trace_matrix", "GET", "/traceability/matrix?projectId=1", None),
    ("trace_coverage", "GET", "/traceability/coverage?projectId=1", None),
    ("trace_gaps", "GET", "/traceability/gaps?projectId=1", None),
    # ─ 风险
    ("risk_list", "GET", "/risk/register/list?projectId=1", None),
    ("risk_report", "GET", "/risk/report/1", None),
    # ─ 合规
    ("audit_logs", "GET", "/compliance/audit-logs?page=0&size=10", None),
    ("audit_verify", "GET", "/compliance/audit-logs/verify/detailed", None),
    ("iec62304_stats", "GET", "/compliance/iec62304/checklist/1/stats", None),
    ("soup_list", "GET", "/soup?page=0&size=10", None),
    # ─ 电子签名
    ("esign_list", "GET", "/esignature/signatures", None),
    ("esign_intent", "POST", "/esignature/intents", None),
    # ─ 报表
    ("dashboard_risk", "GET", "/dashboard/view/risk", None),
    ("dashboard_compliance", "GET", "/dashboard/view/compliance", None),
    # ─ 通知
    ("notif_unread", "GET", "/notifications/unread?userId=1", None),
    # ─ 系统管理
    ("sys_users", "GET", "/system/users", None),
    ("sys_roles", "GET", "/system/roles", None),
    ("sys_dicts", "GET", "/system/dicts?type=priority", None),
    ("sys_org_tree", "GET", "/system/org/tree", None),
]

# ── 角色预期矩阵 ──────────────────────────────────────────────────
# key: role name in TEST_USERS
# value: set of endpoint keys expected to PASS (200)
# 未列出的端点 → 预期 403

ROLE_EXPECT_PASS = {
    "admin": {item[0] for item in ENDPOINTS_ALL},  # 通配 *

    "qa_mgr": {
        "auth_me", "proj_list", "proj_ipd_gate",
        "req_list", "req_create", "req_stats", "req_kanban",
        "chg_list", "chg_create",
        "trace_matrix", "trace_coverage", "trace_gaps",
        "risk_list", "risk_report",
        "audit_logs", "audit_verify", "iec62304_stats", "soup_list",
        "esign_list", "esign_intent",
        "notif_unread",
        "sys_org_tree",
    },

    "pm": {
        "auth_me", "proj_list", "proj_create", "proj_ipd_gate",
        "req_list", "req_create", "req_review", "req_stats", "req_kanban",
        "chg_list", "chg_create",
        "trace_matrix", "trace_coverage", "trace_gaps",
        "risk_list", "risk_report",
        "esign_list", "esign_intent",
        "notif_unread",
        "sys_org_tree",
    },

    "re": {
        "auth_me", "proj_list", "proj_ipd_gate",
        "req_list", "req_create", "req_stats", "req_kanban",
        "chg_list",
        "trace_matrix", "trace_coverage", "trace_gaps",
        "esign_list",
        "notif_unread",
        "soup_list",
    },

    "reviewer": {
        "auth_me",
        "req_list", "req_review", "req_kanban",
        "trace_matrix", "trace_coverage", "trace_gaps",
        "esign_list",
        "notif_unread",
    },

    "risk_mgr": {
        "auth_me", "proj_list", "proj_ipd_gate",
        "req_list",
        "chg_list",
        "risk_list", "risk_report",
        "trace_matrix", "trace_coverage", "trace_gaps",
        "esign_list",
        "notif_unread",
        "soup_list",
    },

    "compliance": {
        "auth_me", "proj_list",
        "req_list",
        "chg_list",
        "audit_logs", "audit_verify", "iec62304_stats", "soup_list",
        "esign_list",
        "notif_unread",
        "sys_org_tree",
        "trace_matrix", "trace_coverage", "trace_gaps",
    },

    "viewer": {
        "auth_me", "proj_list",
        "req_list", "req_kanban",
        "chg_list",
        "risk_list",
        "esign_list",
        "notif_unread",
        "trace_matrix", "trace_coverage", "trace_gaps",
        "soup_list",
    },

    "pd": {
        "auth_me", "proj_list", "proj_create",
        "req_list", "req_create", "req_review", "req_stats", "req_kanban",
        "chg_list", "chg_create",
        "esign_list",
        "notif_unread",
        "sys_org_tree",
        "trace_matrix", "trace_coverage", "trace_gaps",
    },
}

ROLE_DISPLAY = {
    "admin":      "ADMIN",
    "qa_mgr":     "QA_MGR",
    "pm":         "PM",
    "re":         "RE",
    "reviewer":   "REVIEWER",
    "risk_mgr":   "RISK_MGR",
    "compliance": "COMPLIANCE",
    "viewer":     "VIEWER",
    "pd":         "PD",
}


def role_test(role: str, token: str) -> List[Dict]:
    """Test a single role against all endpoints.

    关键：RBAC 授权通过与否只看 HTTP 状态码。
    - 有权限的角色 → 不返回 403 即算授权通过
      （业务错误如 400/422 是允许的，说明请求通过了权限层）
    - 无权限的角色 → 严格返回 403
    """
    expect_pass = ROLE_EXPECT_PASS[role]
    results = []

    for key, _method, _path, _body in ENDPOINTS_ALL:
        expect_ok = key in expect_pass

        r = check_endpoint(_method, _path, token, body=_body)
        http = r["http"]

        # 判定：有权限 → 不能 403；无权限 → 必须 403
        if expect_ok:
            actual_ok = (http != 403)
        else:
            actual_ok = (http == 403)

        result = {
            "key": key, "method": _method, "path": _path,
            "http": http, "code": r["code"],
            "expect_pass": expect_ok,
            "ok": actual_ok,
            "latency_ms": r["latency_ms"],
            "msg": r["msg"],
        }
        results.append(result)

        sym = "[OK]" if actual_ok else "[FAIL]"
        exp_label = "no-403" if expect_ok else "403"
        note = ""
        if expect_ok and http == 200 and r["code"] not in (200, None):
            note = f" (biz={r['code']})"  # 业务失败但授权通过，ok
        print(f"  {sym} {_method:6s} {_path:55s} HTTP={http:>3} (expect {exp_label}){note:>15} {r['latency_ms']:>4}ms")

    return results


def print_role_summary(role: str, results: List[Dict]):
    total = len(results)
    passed = sum(1 for r in results if r["ok"])
    failed = total - passed
    rate = f"{passed * 100 // total}%" if total else "0%"
    print(f"  [{role:12s}] {total:>3} total | {passed:>3} pass | {failed:>3} fail | {rate:>4}")


def print_final_table(all_stats: List[Dict]):
    print()
    print("=" * 90)
    print("  R162 多角色 RBAC e2e 测试 — 最终汇总")
    print("=" * 90)
    print(f"  {'角色名':<14s} {'总测试数':>8s} {'通过':>6s} {'失败':>6s} {'通过率':>8s}")
    print("  " + "-" * 44)
    total_all = 0
    pass_all = 0
    fail_all = 0
    for s in all_stats:
        total_all += s["total"]
        pass_all += s["pass"]
        fail_all += s["fail"]
        print(f"  {s['role']:<14s} {s['total']:>8d} {s['pass']:>6d} {s['fail']:>6d} {s['rate']:>8s}")
    print("  " + "-" * 50)
    overall_rate = f"{pass_all * 100 // total_all}%" if total_all else "N/A"
    print(f"  {'总计':<14s} {total_all:>8d} {pass_all:>6d} {fail_all:>6d} {overall_rate:>8s}")
    print("=" * 90)


def print_failed_details(all_role_results: Dict[str, List[Dict]]):
    any_fail = False
    for role, results in all_role_results.items():
        failed = [r for r in results if not r["ok"]]
        if failed:
            any_fail = True
            print(f"\n  >>> [{ROLE_DISPLAY[role]}] 失败端点 ({len(failed)}):")
            for r in failed:
                expect_label = "200" if r["expect_pass"] else "403"
                print(f"      {r['method']:6s} {r['path']:55s} got={r['http']:>3} expect={expect_label}")
    if not any_fail:
        print(f"\n  >>> 所有角色端点测试均符合预期 ✓")


def main():
    print("=" * 90)
    print("  R162 多角色 RBAC 端到端测试")
    print(f"  RBAC 矩阵: 9 角色 × {len(ENDPOINTS_ALL)} 端点")
    print(f"  后端地址: {BASE}")
    print("=" * 90)

    # 1. 健康检查
    try:
        import requests
        hr = requests.get(f"{BASE}/api/auth/me", timeout=3)
        if hr.status_code not in (200, 401, 403):
            print(f"\n  [FAIL] 后端 {BASE} 未就绪 (HTTP={hr.status_code})")
            print(f"         请先启动后端服务。")
            return 1
    except requests.exceptions.ConnectionError:
        print(f"\n  [FAIL] 无法连接到后端 {BASE}")
        print(f"         请先启动后端服务: http://localhost:8080")
        return 1
    except Exception as e:
        print(f"\n  [FAIL] 健康检查失败: {e}")
        return 1

    print(f"\n  后端连接正常 ✓\n")

    # 2. 全部角色登录
    tokens = {}
    for role in TEST_USERS:
        t = login(role, TEST_USERS[role]["password"])
        tokens[role] = t
        sym = "[OK]" if t else "[FAIL]"
        print(f"  {sym} {role:12s} login: {'ok' if t else 'fail'}")

    login_failed = [r for r, t in tokens.items() if not t]
    if login_failed:
        print(f"\n  [FAIL] 以下角色登录失败: {', '.join(login_failed)}")
        print(f"         请检查测试账号密码是否正确。")
        return 1
    print()

    # 3. 预热 token
    for role, t in tokens.items():
        import requests as _req
        try:
            _req.get(f"{BASE}/api/auth/me", headers={"Authorization": f"Bearer {t}"}, timeout=3)
        except Exception:
            pass

    # 4. 各角色逐一测试
    all_role_results = {}
    all_stats = []

    for role in TEST_USERS:
        display = ROLE_DISPLAY[role]
        print(f"\n  >>> 测试角色: {display} ({role})")
        print("  " + "-" * 70)

        t = tokens[role]
        results = role_test(role, t)
        all_role_results[role] = results

        total = len(results)
        passed = sum(1 for r in results if r["ok"])
        failed = total - passed
        rate = f"{passed * 100 // total}%" if total else "0%"
        all_stats.append({"role": display, "total": total, "pass": passed, "fail": failed, "rate": rate})
        print_role_summary(display, results)

    # 5. 最终汇总
    print_final_table(all_stats)
    print_failed_details(all_role_results)

    # 6. 总体判定
    grand_total = sum(s["total"] for s in all_stats)
    grand_pass = sum(s["pass"] for s in all_stats)
    print(f"\n  通过率: {grand_pass}/{grand_total} = {grand_pass * 100 // grand_total}%")
    if grand_pass == grand_total:
        print("  [PASS] 所有角色 RBAC 测试通过 ✓")
    else:
        print(f"  [FAIL] 存在 {grand_total - grand_pass} 个未通过端点, 请检查以上详情")

    return 0


if __name__ == "__main__":
    sys.exit(main())