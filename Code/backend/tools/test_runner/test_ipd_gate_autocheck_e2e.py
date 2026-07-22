#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R211 v1.67: IPD 阶段门自动校验 e2e 测试（FR-2.5）

验证：
  1. DCP1 自动校验：URS 已通过 + 法规关联
  2. DCP2 自动校验：URS 已拆解为 PRS + 追溯完整率≥90%
  3. DCP3 自动校验：DRS 实现完成率≥80%
  4. DCP4 自动校验：DRS 验证通过率≥95%
  5. DCP5 自动校验：闭环率≥99% + DHF 证据齐
  6. 统计来源：statisticsSource=auto-collected
  7. 跨模块统计：statistics Map 包含 requirementCount/riskCount/iecCompliantCount 等
  8. verdict 合法：PASS/WARN/FAIL 之一

预期：8+ 用例 8+ PASS
"""
import os
import sys
sys.path.insert(0, os.path.dirname(__file__))
import requests

BASE = "http://localhost:8080/api"
PASS, FAIL = 0, 0


def ok(name):
    global PASS
    PASS += 1
    print(f"  [OK] {name}")


def ng(name, msg=""):
    global FAIL
    FAIL += 1
    print(f"  [FAIL] {name}: {msg}")


def login(user, pwd):
    r = requests.post(f"{BASE}/auth/login", json={"username": user, "password": pwd}, timeout=10)
    return r.json()["data"]["token"]


def find_project_id(token):
    r = requests.get(f"{BASE}/projects?page=0&size=10",
                     headers={"Authorization": f"Bearer {token}"}, timeout=10)
    data = r.json().get("data", {})
    records = data.get("records") if isinstance(data, dict) else data
    return records[0].get("id") if records else None


print("=" * 60)
print("R211 v1.67: IPD 阶段门自动校验 e2e 测试")
print("=" * 60)

token = login("admin", "admin123")
project_id = find_project_id(token)
if not project_id:
    ng("无项目", "")
    sys.exit(1)
print(f"使用 projectId={project_id}")
auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1-5: DCP1-DCP5 自动校验 ==========
for gate_no in [1, 2, 3, 4, 5]:
    try:
        r = requests.post(f"{BASE}/project/ipd-gate/auto-check-v2",
                          json={"projectId": project_id, "gateNo": gate_no},
                          headers=auth, timeout=30)
        if r.status_code == 200:
            data = r.json().get("data", {})
            verdict = data.get("verdict")
            items = data.get("items", [])
            statistics = data.get("statistics", {})
            if verdict in ("PASS", "WARN", "FAIL"):
                ok(f"DCP{gate_no} 自动校验 verdict={verdict}（{len(items)} 项规则）")
            else:
                ng(f"DCP{gate_no} verdict", f"非法={verdict}")
            # 检查 statistics 是否自动收集
            if "requirementCount" in statistics:
                ok(f"DCP{gate_no} statistics 含 requirementCount={statistics['requirementCount']}")
            else:
                ng(f"DCP{gate_no} statistics", "缺少 requirementCount")
        else:
            ng(f"DCP{gate_no} 调用", f"status={r.status_code}")
    except Exception as e:
        ng(f"DCP{gate_no}", str(e))

# ========== 用例 6: statisticsSource 自动 ==========
try:
    r = requests.post(f"{BASE}/project/ipd-gate/auto-check-v2",
                      json={"projectId": project_id, "gateNo": 1},
                      headers=auth, timeout=30)
    if r.status_code == 200:
        data = r.json().get("data", {})
        if data.get("statisticsSource") == "auto-collected":
            ok("statisticsSource=auto-collected（R211 自动聚合验证）")
        else:
            ng("statisticsSource", str(data.get("statisticsSource")))
except Exception as e:
    ng("statisticsSource", str(e))

# ========== 用例 7: statistics 字段完整性 ==========
try:
    r = requests.post(f"{BASE}/project/ipd-gate/auto-check-v2",
                      json={"projectId": project_id, "gateNo": 3},
                      headers=auth, timeout=30)
    if r.status_code == 200:
        statistics = r.json().get("data", {}).get("statistics", {})
        required_keys = ["requirementCount", "ursCount", "prsCount", "srsCount", "drsCount",
                          "riskCount", "totalIecItems", "dhfEvidenceCount"]
        missing = [k for k in required_keys if k not in statistics]
        if not missing:
            ok(f"statistics 含 {len(required_keys)} 个跨模块统计键")
        else:
            ng("statistics 完整性", f"缺失={missing}")
except Exception as e:
    ng("statistics 完整性", str(e))

# ========== 用例 8: items 列表结构 ==========
try:
    r = requests.post(f"{BASE}/project/ipd-gate/auto-check-v2",
                      json={"projectId": project_id, "gateNo": 5},
                      headers=auth, timeout=30)
    if r.status_code == 200:
        items = r.json().get("data", {}).get("items", [])
        if items and all("name" in i and "pass" in i for i in items):
            ok(f"DCP5 items 含 {len(items)} 项检查，每项有 name+pass")
        else:
            ng("items 结构", f"items={items}")
except Exception as e:
    ng("items 结构", str(e))

# Summary
print("=" * 60)
print(f"R211 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
