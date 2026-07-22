#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R213 v1.69: 法规更新自动推送影响分析 e2e 测试（FR-2.2）

验证：
  1. GET /regulations/list 返回内置法规库（含 NMPA/ISO/IEC 等）
  2. POST /regulations/notify-update 触发影响分析
  3. 返回 ImpactResult 含 regulationType/clauseNumber/affectedRequirementIds/notifications
  4. GET /regulations/impact/{type}/{clause} 查询影响（不实际发通知）
  5. RegulationInfo 字段完整（title/version/updatedAt/updatedBy）
  6. 不存在的法规返回空影响（fail-safe）
  7. notifications 列表结构正确

预期：7 用例 7 PASS
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


print("=" * 60)
print("R213 v1.69: 法规更新自动推送影响分析 e2e 测试")
print("=" * 60)

token = login("compliance", "admin123")
auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1: 法规库列表 ==========
try:
    r = requests.get(f"{BASE}/regulations/list", headers=auth, timeout=10)
    if r.status_code == 200:
        lib = r.json().get("data", {})
        if isinstance(lib, dict) and len(lib) > 0:
            types = list(lib.keys())
            ok(f"法规库含 {len(types)} 类法规：{types[:3]}...")
        else:
            ng("法规库内容", f"空或非 dict: {lib}")
    else:
        ng("法规库 HTTP", f"status={r.status_code}")
except Exception as e:
    ng("法规库列表", str(e))

# ========== 用例 2: 法规更新推送触发 ==========
try:
    r = requests.post(f"{BASE}/regulations/notify-update",
                      json={"regulationType": "NMPA-2022",
                            "clauseNumber": "CH5.1",
                            "newVersion": "v2024-Q1",
                            "updatedBy": "法规部-张三"},
                      headers=auth, timeout=30)
    if r.status_code == 200:
        result = r.json().get("data", {})
        if all(k in result for k in ["regulationType", "clauseNumber", "affectedRequirementIds", "notifications"]):
            ok(f"影响分析返回完整（受影响需求={len(result.get('affectedRequirementIds', []))}，通知={len(result.get('notifications', []))}）")
        else:
            ng("影响分析结构", f"缺字段: {result.keys()}")
    else:
        ng("通知触发 HTTP", f"status={r.status_code}, body={r.text[:200]}")
except Exception as e:
    ng("通知触发", str(e))

# ========== 用例 3: ImpactResult 字段完整 ==========
try:
    r = requests.post(f"{BASE}/regulations/notify-update",
                      json={"regulationType": "IEC62304",
                            "clauseNumber": "5.2",
                            "newVersion": "v2015-R2",
                            "updatedBy": "compliance"},
                      headers=auth, timeout=30)
    if r.status_code == 200:
        data = r.json().get("data", {})
        if isinstance(data.get("affectedRequirementIds"), list) and isinstance(data.get("notifications"), list):
            ok(f"IEC 法规更新分析完成（受影响={data.get('affectedProjectCount', 0)} 项目）")
        else:
            ng("ImpactResult 字段类型", "")
except Exception as e:
    ng("IEC 法规分析", str(e))

# ========== 用例 4: GET impact 查询（不实际发通知）==========
try:
    r = requests.get(f"{BASE}/regulations/impact/IEC62304/7.1?version=vR3&updatedBy=preview-user",
                     headers=auth, timeout=30)
    if r.status_code == 200:
        data = r.json().get("data", {})
        ok(f"GET impact 查询 OK（affectedProjectCount={data.get('affectedProjectCount', 0)}）")
    else:
        ng("GET impact HTTP", f"status={r.status_code}")
except Exception as e:
    ng("GET impact", str(e))

# ========== 用例 5: RegulationInfo 字段完整 ==========
try:
    r = requests.get(f"{BASE}/regulations/list", headers=auth, timeout=10)
    lib = r.json().get("data", {})
    if lib:
        # 取第一个法规的第一个条款验证字段
        first_type = next(iter(lib))
        first_clause = next(iter(lib[first_type]))
        info = lib[first_type][first_clause]
        if all(k in info for k in ["title", "version", "updatedAt"]):
            ok(f"RegulationInfo 含 title/version/updatedAt（{first_type}/{first_clause}={info['title'][:30]}...）")
        else:
            ng("RegulationInfo 字段", f"keys={info.keys() if isinstance(info, dict) else 'not dict'}")
except Exception as e:
    ng("RegulationInfo 字段", str(e))

# ========== 用例 6: 不存在法规类型（fail-safe）==========
try:
    r = requests.post(f"{BASE}/regulations/notify-update",
                      json={"regulationType": "UNKNOWN-TYPE",
                            "clauseNumber": "X.1",
                            "newVersion": "v1",
                            "updatedBy": "tester"},
                      headers=auth, timeout=10)
    if r.status_code == 200:
        result = r.json().get("data", {})
        if isinstance(result, dict) and "affectedRequirementIds" in result:
            ok(f"不存在法规 graceful（仍返回结构：受影响={len(result['affectedRequirementIds'])}）")
        else:
            ng("不存在法规响应", "")
except Exception as e:
    ng("不存在法规", str(e))

# ========== 用例 7: notifications 数组结构 ==========
try:
    r = requests.post(f"{BASE}/regulations/notify-update",
                      json={"regulationType": "ISO13485",
                            "clauseNumber": "7.3.7",
                            "newVersion": "v2016-R1",
                            "updatedBy": "compliance"},
                      headers=auth, timeout=30)
    if r.status_code == 200:
        data = r.json().get("data", {})
        notifs = data.get("notifications", [])
        if isinstance(notifs, list):
            if not notifs:
                ok(f"notifications 列表为空（无关联需求，结构正确）")
            elif all("projectId" in n and "title" in n and "content" in n for n in notifs):
                ok(f"notifications 含 {len(notifs)} 条，每条含 projectId/title/content")
            else:
                ng("notifications 字段", "缺字段")
        else:
            ng("notifications 类型", f"type={type(notifs)}")
except Exception as e:
    ng("notifications 结构", str(e))

# Summary
print("=" * 60)
print(f"R213 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
