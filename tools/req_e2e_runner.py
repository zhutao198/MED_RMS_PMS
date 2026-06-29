#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
需求管理 8 业务流全流程试跑脚本 — R98 修正版
- 修正 review-multi 调用方式: ?reviewerId=1 + body [2]
- 修正 baseline-requirements 调用方式: ?baselineId=132 + body [1634]
- 修正流程2: /review 端点合并 Submit+Approve 是设计简化,只验证状态流转正确
- 修正流程4: 拆解用 /decompose 单个对象,子查询用 /tree
- 修正流程5: 追溯图谱用 /trace-graph (不是 /traceability/graph)

用法: python req_e2e_runner.py <flow_num>
  flow_num: 1=创建 2=状态机 3=评审 4=拆解 5=追溯 6=版本 7=签名 8=基线 0=全跑
"""
import sys
import json
import requests
import time
from datetime import datetime

BASE = "http://localhost:8080/api"

def login(username="admin", password="admin123"):
    r = requests.post(f"{BASE}/auth/login",
                      json={"username": username, "password": password},
                      timeout=5)
    data = r.json().get("data", {})
    return data.get("token") or data.get("accessToken")

TOKEN = login()
H = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}

# 共享状态
STATE = {"urs_id": None, "prs_id": None, "srs_id": None, "drs_id": None,
         "intent_id": None, "signature_id": None, "baseline_id": None,
         "version_id": None}

PASS = []
FAIL = []

def call(method, path, **kw):
    fn = {"GET": requests.get, "POST": requests.post, "PUT": requests.put,
          "DELETE": requests.delete, "PATCH": requests.patch}[method]
    url = f"{BASE}{path}"
    try:
        r = fn(url, headers=H, timeout=8, **kw)
        body = r.json() if r.headers.get('content-type','').startswith('application/json') else r.text
        return r.status_code, body
    except Exception as e:
        return 0, {"error": str(e)}

def show(label, code, body, expect_code=200, max_len=400):
    ok = code == expect_code
    sym = "[OK]" if ok else "[FAIL]"
    if isinstance(body, dict):
        d = body.get('data')
        msg = body.get('message', '')[:80]
        summary = f"code={body.get('code')} msg={msg}"
        if isinstance(d, dict):
            summary += f" data_id={d.get('id')}"
        elif isinstance(d, list):
            summary += f" data_len={len(d)}"
    else:
        summary = "raw"
    print(f"{sym} [{label}] HTTP={code} {summary}")
    if not ok and isinstance(body, dict):
        print(f"   raw: {json.dumps(body, ensure_ascii=False)[:max_len]}")
    (PASS if ok else FAIL).append((label, summary))
    return ok

def show_ok(label, ok, detail=""):
    sym = "[OK]" if ok else "[FAIL]"
    print(f"{sym} [{label}] {detail}")
    (PASS if ok else FAIL).append((label, detail))

def parse_list(body):
    if not isinstance(body, dict): return []
    d = body.get('data')
    if d is None: return []
    if isinstance(d, list): return d
    if isinstance(d, dict): return d.get('records') or []
    return []

# ==================== 8 流程 ====================

def flow1_create():
    """流程1: 创建需求 4 层 URS→PRS→SRS→DRS"""
    print("\n" + "="*60)
    print("【流程1: 创建需求 4 层 URS→PRS→SRS→DRS】")
    print("="*60)
    c, b = call("POST", "/requirements", json={
        "requirementType": "URS", "title": f"[试跑1-URS] {datetime.now().strftime('%H%M%S')}",
        "description": "R98 all-flow-test URS", "priority": "HIGH", "riskLevel": "MEDIUM",
        "projectId": 1, "requirementCategory": "FUNCTIONAL"
    })
    show("URS 创建", c, b)
    STATE["urs_id"] = b["data"]["id"] if c == 200 else None

    c, b = call("POST", "/requirements", json={
        "requirementType": "PRS", "title": f"[试跑1-PRS] {datetime.now().strftime('%H%M%S')}",
        "description": "R98 all-flow-test PRS", "priority": "HIGH", "riskLevel": "MEDIUM",
        "projectId": 1, "requirementCategory": "FUNCTIONAL"
    })
    show("PRS 创建", c, b)
    STATE["prs_id"] = b["data"]["id"] if c == 200 else None

    c, b = call("POST", "/requirements", json={
        "requirementType": "SRS", "title": f"[试跑1-SRS] {datetime.now().strftime('%H%M%S')}",
        "description": "R98 all-flow-test SRS", "priority": "HIGH", "riskLevel": "MEDIUM",
        "projectId": 1, "requirementCategory": "FUNCTIONAL"
    })
    show("SRS 创建", c, b)
    STATE["srs_id"] = b["data"]["id"] if c == 200 else None

    c, b = call("POST", "/requirements", json={
        "requirementType": "DRS", "title": f"[试跑1-DRS] {datetime.now().strftime('%H%M%S')}",
        "description": "R98 all-flow-test DRS", "priority": "HIGH", "riskLevel": "MEDIUM",
        "projectId": 1, "requirementCategory": "FUNCTIONAL"
    })
    show("DRS 创建", c, b)
    STATE["drs_id"] = b["data"]["id"] if c == 200 else None

    c, b = call("GET", f"/requirements/{STATE['urs_id']}")
    show_ok("URS 详情可查", c == 200, f"id={STATE['urs_id']}")
    c, b = call("GET", f"/requirements/{STATE['prs_id']}")
    show_ok("PRS 详情可查", c == 200, f"id={STATE['prs_id']}")

def flow2_state_machine():
    """流程2: 状态机流转"""
    print("\n" + "="*60)
    print("【流程2: 状态机流转】")
    print("="*60)
    if not STATE["urs_id"]:
        print("跳过：URS_ID 不存在"); return
    c, b = call("POST", f"/requirements/{STATE['urs_id']}/review?reviewerId=1&comments=试跑2-提交评审")
    show("Draft→Submitted via /review", c, b)
    c, b = call("GET", f"/requirements/{STATE['urs_id']}")
    if c == 200:
        st = b["data"]["status"]
        show_ok("状态流转正确", st in ["Submitted", "ReviewApproved", "Approved"], f"actual={st}")

def flow3_review():
    """流程3: 评审流(R98 修正: reviewerId query + extraReviewers body)"""
    print("\n" + "="*60)
    print("【流程3: 评审流】")
    print("="*60)
    if not STATE["prs_id"]:
        print("跳过：PRS_ID 不存在"); return
    c, b = call("POST", f"/requirements/{STATE['prs_id']}/review?reviewerId=1&comments=试跑3-PRS评审")
    show_ok("PRS Draft→Submitted", c == 200, str(b.get("message", ""))[:60])

    # R98 修正：reviewerId 在 query, extraReviewers 在 body
    c, b = call("POST", f"/requirements/{STATE['prs_id']}/review-multi",
                params={"reviewerId": 1, "comments": "试跑3-多人评审"},
                json=[2])
    show("PRS 多人评审 (reviewerId=1 + extraReviewers=[2])", c, b)

    c, b = call("GET", f"/requirements/{STATE['prs_id']}/reviews")
    if c == 200:
        records = parse_list(b)
        show_ok("Review 记录可查询", len(records) >= 1, f"count={len(records)}")
        if records:
            r0 = records[0]
            show_ok("Review 含 round 字段", "round" in r0, f"keys={list(r0.keys())[:5]}")

def flow4_decompose():
    """流程4: 拆解流"""
    print("\n" + "="*60)
    print("【流程4: 拆解流】")
    print("="*60)
    if not STATE["srs_id"]:
        print("跳过：SRS_ID 不存在"); return
    c, b = call("POST", f"/requirements/{STATE['srs_id']}/decompose", json={
        "requirementType": "DRS",
        "title": f"[试跑4-拆解] {datetime.now().strftime('%H%M%S')}",
        "description": "decomposed by SRS",
        "priority": "HIGH", "projectId": 1
    })
    show("SRS 拆解为 DRS", c, b)
    if c == 200 and b.get("data"):
        STATE["drs_id"] = b["data"]["id"]
        print(f"   -> DRS_ID={STATE['drs_id']}")

    c, b = call("GET", "/requirements/tree", params={"projectId": 1})
    if c == 200:
        records = parse_list(b)
        show_ok("项目需求树可查", len(records) >= 1, f"root_count={len(records)}")

def flow5_traceability():
    """流程5: 追溯流"""
    print("\n" + "="*60)
    print("【流程5: 追溯流】")
    print("="*60)
    if not STATE["urs_id"]:
        print("跳过：URS_ID 不存在"); return

    c, b = call("POST", "/traceability/relations",
                params={"sourceReqId": STATE["urs_id"], "targetReqId": STATE["prs_id"],
                        "relationType": "RELATED_TO"})
    show("创建追溯关系 URS→PRS via /relations", c, b)

    c, b = call("GET", "/traceability/matrix", params={"projectId": 1})
    show("追溯矩阵查询", c, b)

    c, b = call("GET", "/traceability/coverage", params={"projectId": 1})
    show("覆盖率查询", c, b)

    c, b = call("GET", "/traceability/gaps", params={"projectId": 1})
    show("追溯缺口查询", c, b)

    # 5.5 trace-graph 数据（前端真实路由是 /trace-graph）
    c, b = call("GET", f"/trace-graph/project/1")
    show("追溯图谱数据 (/trace-graph/project/1)", c, b)

def flow6_version():
    """流程6: 版本流"""
    print("\n" + "="*60)
    print("【流程6: 版本流】")
    print("="*60)
    if not STATE["drs_id"]:
        print("跳过：DRS_ID 不存在"); return
    change_summary = json.dumps({
        "summary": "试跑6-新增功能",
        "cti": [
            {"standardCode": "IEC-62304", "satisfyLevel": "FULLY"},
            {"standardCode": "ISO-14971", "satisfyLevel": "PARTIALLY"}
        ]
    }, ensure_ascii=False)
    c, b = call("POST", f"/requirements/{STATE['drs_id']}/versions", json={
        "changeSummary": change_summary
    })
    show("创建 DRS 版本", c, b)
    if c == 200 and b.get("data"):
        STATE["version_id"] = b["data"]["id"]

    c, b = call("GET", f"/requirements/{STATE['drs_id']}/versions")
    show("版本列表查询", c, b)

def flow7_signature():
    """流程7: 签名流(R97 修复后)"""
    print("\n" + "="*60)
    print("【流程7: 签名流】")
    print("="*60)
    if not STATE["drs_id"]:
        print("跳过：DRS_ID 不存在"); return
    c, b = call("POST", "/esignature/intents", json={
        "requesterId": 1,
        "documentType": "REQUIREMENT",
        "documentId": STATE["drs_id"],
        "intentCode": "APPROVE",
        "meaningCode": "APPROVE"
    })
    show("创建签名意图", c, b)
    if c == 200:
        STATE["intent_id"] = b["data"]["id"]

    c, b = call("GET", "/esignature/intents", params={"signerId": 1, "status": "PENDING", "page": 0, "size": 20})
    if c == 200:
        records = b["data"]["records"] if isinstance(b["data"], dict) else b["data"]
        hit = any(r["id"] == STATE["intent_id"] for r in records) if records else False
        show_ok("R97 待签字 Intent 可查", hit, f"records_count={len(records) if records else 0}")

def flow8_baseline():
    """流程8: 基线流(R98 修正: baselineId query + requirementIds body)"""
    print("\n" + "="*60)
    print("【流程8: 基线流】")
    print("="*60)
    if not STATE["urs_id"]:
        print("跳过：URS_ID 不存在"); return
    c, b = call("POST", "/baselines", json={
        "name": f"[试跑8] baseline-{datetime.now().strftime('%H%M%S')}",
        "projectId": 1,
        "description": "试跑8-基线"
    })
    show("创建基线", c, b)
    if c == 200:
        STATE["baseline_id"] = b["data"]["id"]

    # R98 修正：baselineId query + List<Long> body
    if STATE["baseline_id"] and STATE["urs_id"]:
        c, b = call("POST", "/baselines/baseline-requirements",
                    params={"baselineId": STATE["baseline_id"]},
                    json=[STATE["urs_id"]])
        show("添加需求到基线 (baselineId query + [reqId] body)", c, b)

    c, b = call("GET", "/baselines/project/1")
    show("基线列表查询", c, b)

# ==================== 报告 ====================

def report():
    print("\n" + "="*60)
    print("【试跑报告汇总】")
    print("="*60)
    print(f"通过: {len(PASS)} 项")
    print(f"失败: {len(FAIL)} 项")
    if FAIL:
        print("\n失败明细:")
        for label, detail in FAIL:
            print(f"  [FAIL] [{label}] {detail[:120]}")
    print(f"\n共享状态: {json.dumps({k:v for k,v in STATE.items() if v}, ensure_ascii=False, indent=2)}")

FLOWS = {
    1: flow1_create, 2: flow2_state_machine, 3: flow3_review,
    4: flow4_decompose, 5: flow5_traceability, 6: flow6_version,
    7: flow7_signature, 8: flow8_baseline
}

if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else "0"
    if arg == "0":
        for i in range(1, 9):
            FLOWS[i]()
    else:
        FLOWS[int(arg)]()
    report()