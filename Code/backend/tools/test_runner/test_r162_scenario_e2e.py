#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R162 完整业务场景 e2e 测试：需求创建 → 追溯 → 变更 → 签字 → 审计验证

场景流（admin 视角，15 步）：
   1. 需求创建 (POST /requirements)
   2. 需求查询 (GET /requirements/{id})
   3. 追溯链接 (POST /trace-links)
   4. 追溯查询 (GET /traceability/matrix)
   5. 变更创建 (POST /changes)
   6. 变更查询 (GET /changes/{id})
   7. 合规指标 (GET /compliance/metrics/{projectId})
   8. AI 分析 (POST /ai/requirement/analyze)
   9. eRPS 预览 (GET /compliance/erps/export/{projectId})
  10. 审计日志 (GET /compliance/audit-logs)
  11. 通知 (GET /notifications/unread)
  12. Dashboard (GET /dashboard/view/requirements, /dashboard/view/risk)
  13. 基线列表 (GET /baselines)
  14. 追溯缺口 (GET /traceability/gaps)
  15. 审计哈希链验证 (POST /compliance/audit-logs/verify)

用法: python test_r162_scenario_e2e.py
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))

for mod_name in list(sys.modules.keys()):
    if 'common' in mod_name:
        del sys.modules[mod_name]
import common as _c
from common import login, http_request

TS = int(time.time())
PROJECT_ID = 8
USER_ID = 1  # admin
results = {"pass": 0, "fail": 0, "skip": 0}


def step(name, ok, detail=""):
    sym = "OK" if ok else "FAIL"
    line = f"  [{sym}] {name:55s}"
    if not ok and detail:
        line += f"\n         {detail}"
    print(line)
    if ok:
        results["pass"] += 1
    else:
        results["fail"] += 1


def api(method, path, t=None, body=None, params=None, timeout=5):
    s, b, l = http_request(method, path, token=t, body=body, params=params)
    code = b.get("code") if isinstance(b, dict) else None
    msg = b.get("message", "")[:120] if isinstance(b, dict) else ""
    return s, b, code, msg, l


def log_and_check(name, method, path, t, body=None, params=None, expect_code=200, extract=None):
    s, b, c, msg, lat = api(method, path, t, body, params)
    ok = (c == expect_code)
    step(name, ok, f"HTTP={s} code={c} {msg}")
    if ok and extract:
        val = b.get("data", {}).get(extract) if isinstance(b.get("data"), dict) else None
        return val
    if ok and extract == "__id__":
        data = b.get("data")
        if isinstance(data, dict):
            return data.get("id")
        if isinstance(data, list) and len(data) > 0 and isinstance(data[0], dict):
            return data[0].get("id")
    return None


def main():
    print("=== R162 完整业务场景 e2e 测试（admin 视角）===")
    t = login("admin", "admin123")
    if not t:
        print("[FAIL] admin 登录失败，终止")
        return 1

    # =========================================================
    # Step 1: 需求创建
    # =========================================================
    print("\n--- Step 1~2: 需求创建 & 查询 ---")
    req_title = f"R162-scenario-{TS}"
    s, b, c, msg, lat = api("POST", "/requirements", t, body={
        "requirementType": "URS", "projectId": PROJECT_ID,
        "title": req_title, "description": "R162 完整场景测试需求",
        "priority": "MUST", "requirementCategory": "SOFTWARE", "source": "INTERNAL"
    })
    rid = None
    if c == 200 and isinstance(b.get("data"), dict):
        rid = b["data"].get("id")
    step("1. 需求创建", c == 200 and rid is not None, f"code={c} rid={rid}")
    if not rid:
        print("[WARN] 需求创建失败，后续步骤依赖需求 ID，跳过")
        print(f"\n=== R162 汇总: pass={results['pass']} fail={results['fail']} skip={results['pass']+results['fail']} ===")
        return 1

    # Step 2: 需求查询
    log_and_check("2. 需求查询 GET /requirements/{id}", "GET", f"/requirements/{rid}", t,
                  expect_code=200)

    # =========================================================
    # Step 3~4: 追溯链接 & 追溯查询
    # =========================================================
    print("\n--- Step 3~4: 追溯链接 & 追溯查询 ---")
    log_and_check("3. 追溯链接 POST /trace-links", "POST", "/trace-links", t, body={
        "sourceId": rid, "targetId": rid,
        "linkType": "COVERS", "projectId": PROJECT_ID
    }, expect_code=200)

    log_and_check("4. 追溯查询 GET /traceability/matrix", "GET", "/traceability/matrix", t,
                  params={"projectId": PROJECT_ID}, expect_code=200)

    # =========================================================
    # Step 5~6: 变更创建 & 变更查询
    # =========================================================
    print("\n--- Step 5~6: 变更创建 & 查询 ---")
    cid = log_and_check("5. 变更创建 POST /changes", "POST", "/changes", t, body={
        "requirementId": rid, "changeType": "UPDATE",
        "reason": f"R162 场景测试变更-{TS}", "urgency": "MEDIUM",
        "requestedBy": USER_ID, "title": f"变更-{req_title}"
    }, expect_code=200, extract="__id__")
    if cid:
        log_and_check("6. 变更查询 GET /changes/{id}", "GET", f"/changes/{cid}", t,
                      expect_code=200)

    # =========================================================
    # Step 7: 合规指标
    # =========================================================
    print("\n--- Step 7: 合规指标 ---")
    log_and_check("7. 合规指标 GET /compliance/metrics/{id}", "GET",
                  f"/compliance/metrics/{PROJECT_ID}", t, expect_code=200)

    # =========================================================
    # Step 8: AI 分析
    # =========================================================
    print("\n--- Step 8: AI 分析 ---")
    log_and_check("8. AI 分析 POST /ai/requirement/analyze", "POST",
                  "/ai/requirement/analyze", t, body={
                      "title": req_title, "description": "R162 场景测试",
                      "requirementType": "URS", "priority": "MUST"
                  }, expect_code=200)

    # =========================================================
    # Step 9: eRPS 预览
    # =========================================================
    print("\n--- Step 9: eRPS 预览 ---")
    log_and_check("9. eRPS 预览 GET /compliance/erps/export/{id}", "GET",
                  f"/compliance/erps/export/{PROJECT_ID}", t, expect_code=200)

    # =========================================================
    # Step 10: 审计日志
    # =========================================================
    print("\n--- Step 10: 审计日志 ---")
    log_and_check("10. 审计日志 GET /compliance/audit-logs", "GET",
                  "/compliance/audit-logs", t, params={"page": 0, "size": 10},
                  expect_code=200)

    # =========================================================
    # Step 11: 通知
    # =========================================================
    print("\n--- Step 11: 通知 ---")
    log_and_check("11. 通知 GET /notifications/unread", "GET",
                  "/notifications/unread", t, params={"userId": USER_ID},
                  expect_code=200)

    # =========================================================
    # Step 12: Dashboard
    # =========================================================
    print("\n--- Step 12: Dashboard ---")
    log_and_check("12a. Dashboard 需求视图", "GET", "/dashboard/view/requirements", t,
                  params={"projectId": PROJECT_ID}, expect_code=200)
    log_and_check("12b. Dashboard 风险视图", "GET", "/dashboard/view/risk", t,
                  params={"projectId": PROJECT_ID}, expect_code=200)

    # =========================================================
    # Step 13: 基线列表
    # =========================================================
    print("\n--- Step 13: 基线列表 ---")
    log_and_check("13. 基线列表 GET /baselines", "GET", "/baselines", t,
                  params={"projectId": PROJECT_ID}, expect_code=200)

    # =========================================================
    # Step 14: 追溯缺口
    # =========================================================
    print("\n--- Step 14: 追溯缺口 ---")
    log_and_check("14. 追溯缺口 GET /traceability/gaps", "GET",
                  "/traceability/gaps", t, params={"projectId": PROJECT_ID},
                  expect_code=200)

    # =========================================================
    # Step 15: 审计哈希链验证
    # =========================================================
    print("\n--- Step 15: 审计哈希链验证 ---")
    log_and_check("15. 审计哈希链验证 POST /compliance/audit-logs/verify", "POST",
                  "/compliance/audit-logs/verify", t, body={}, expect_code=200)

    # =========================================================
    # 汇总
    # =========================================================
    print(f"\n=== R162 完整业务场景 e2e 汇总 ===")
    print(f"  pass: {results['pass']}")
    print(f"  fail: {results['fail']}")
    total = results['pass'] + results['fail']
    rate = (results['pass'] * 100 // total) if total > 0 else 0
    print(f"  pass rate: {rate}%")

    return 0 if results['fail'] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
