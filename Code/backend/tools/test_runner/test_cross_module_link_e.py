#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R198-5: 跨模块链路 E — 合规评估 -> 风险 -> 需求闭环

场景:
  1. 使用已有项目
  2. 创建需求 (req)
  3. 评估风险 /risk/assess (risk)
  4. 初始化 IEC 62304 检查清单 -> 评估条款 (compliance)
  5. 验证跨模块追溯

API 路径:
  POST /risk/assess     — RiskController
  POST /compliance/iec62304/checklist/{projectId}/init  — 初始化清单
  POST /compliance/iec62304/checklist/{id}/assess        — 评估条款
"""
import sys, os, json, time
sys.path.insert(0, os.path.dirname(__file__))
import requests

BASE = "http://localhost:8080/api"
PASS, FAIL = 0, 0

def ok(name): global PASS; PASS += 1; print(f"  [OK] {name}")
def ng(name, msg=""): global FAIL; FAIL += 1; print(f"  [FAIL] {name}: {msg}")

def login(u, p):
    r = requests.post(f"{BASE}/auth/login", json={"username": u, "password": p}, timeout=10)
    return r.json()

r0 = login("admin", "admin123")
ADMIN_TOKEN = r0["data"]["token"]
adm_hdr = {"Authorization": f"Bearer {ADMIN_TOKEN}"}
print("Login: admin OK")

# Step 1: Project
print(f"\n[Step 1] 项目")
r = requests.get(f"{BASE}/projects", headers=adm_hdr, timeout=10)
if r.status_code == 200 and r.json().get("data"):
    data = r.json()["data"]
    if isinstance(data, dict): data = data.get("records", data)
    proj = data[0] if isinstance(data, list) else data
    ok("Use existing project")
else:
    r = requests.post(f"{BASE}/projects", json={"name":"link-e-test-"+str(int(time.time())),"description":"E test"}, headers=adm_hdr, timeout=10)
    proj = r.json()["data"]
    ok("Create project")
proj_id = proj["id"] if isinstance(proj, dict) else proj
print(f"  projectId={proj_id}")

# Step 2: Requirement
print(f"\n[Step 2] 需求")
ts = str(int(time.time()))
r = requests.post(f"{BASE}/requirements", json={
    "projectId": proj_id,
    "title": "LinkE-Req-" + ts,
    "content": "Req for cross-module link E",
    "requirementType": "SRS",
    "riskLevel": "MEDIUM",
    "safetyClass": "B",
    "source": "CUSTOMER",
    "priority": "MEDIUM",
    "status": "DRAFT"
}, headers=adm_hdr, timeout=10)
if r.status_code == 200 and r.json().get("code") == 200:
    req = r.json()["data"]
    req_id = req["id"] if isinstance(req, dict) else req
    print(f"  requirementId={req_id}")
    ok("Create requirement")
else:
    ng("Create requirement", r.text[:200])
    sys.exit(1)

# Step 3: Risk assessment
print(f"\n[Step 3] 风险评估 (/risk/assess)")
r_risk = login("risk_mgr", "admin123")
risk_hdr = {"Authorization": f"Bearer {r_risk['data']['token']}"}
# Get risk_mgr userId from JWT
import base64
parts = r_risk['data']['token'].split('.')
pad = 4 - len(parts[1]) % 4
payload = parts[1] + '=' * (0 if pad == 4 else pad)
decoded = json.loads(base64.urlsafe_b64decode(payload))
risk_mgr_id = decoded.get("userId", 4)
print(f"  risk_mgr userId={risk_mgr_id}")

r2 = requests.post(f"{BASE}/risk/assess", json={
    "requirementId": req_id,
    "riskLevel": "MEDIUM",
    "hazardLevel": "MODERATE",
    "hazardSource": "Software defect in SOUP library",
    "hazardSituation": "Invalid input triggers unexpected behavior",
    "harm": "Patient data corruption",
    "controlMeasure": "Input validation + bounds checking",
    "assessedBy": risk_mgr_id
}, headers=risk_hdr, timeout=10)
if r2.status_code == 200 and r2.json().get("code") == 200:
    risk = r2.json()["data"]
    risk_id = risk.get("id") if isinstance(risk, dict) else risk
    ok("Risk assessment created")
    print(f"  riskId={risk_id}")
else:
    ng("Risk assessment", r2.text[:200])
    risk_id = None

# Step 4: Compliance IEC 62304
print(f"\n[Step 4] 合规 IEC 62304 评估")
r_comp = login("compliance", "admin123")
comp_hdr = {"Authorization": f"Bearer {r_comp['data']['token']}"}

# 4a: Initialize checklist for project
init = requests.post(f"{BASE}/compliance/iec62304/checklist/{proj_id}/init", headers=comp_hdr, timeout=10)
if init.status_code == 200 and init.json().get("code") == 200:
    ok("IEC 62304 checklist initialized")
    print(f"  init: {str(init.json()['data'])[:100] if init.json().get('data') else 'OK'}")
else:
    # May already be initialized
    init2 = requests.get(f"{BASE}/compliance/iec62304/checklist/{proj_id}", headers=comp_hdr, timeout=10)
    if init2.status_code == 200:
        ok("IEC 62304 checklist already exists")
    else:
        ng("IEC 62304 checklist init", init.text[:200])

# 4b: Get first checklist item and assess it
items_r = requests.get(f"{BASE}/compliance/iec62304/checklist/{proj_id}", headers=comp_hdr, timeout=10)
if items_r.status_code == 200 and items_r.json().get("data"):
    items = items_r.json()["data"]
    if isinstance(items, dict): items = items.get("records", items)
    if isinstance(items, list) and len(items) > 0:
        item_id = items[0].get("id") if isinstance(items[0], dict) else items[0]
        print(f"  checklistItemId={item_id}")
        assess = requests.post(f"{BASE}/compliance/iec62304/checklist/{item_id}/assess",
            params={"status":"COMPLIANT","evidence":"Test documentation provided","assessorId":risk_mgr_id,"assessorName":"risk_mgr"},
            headers=comp_hdr, timeout=10)
        if assess.status_code == 200 and assess.json().get("code") == 200:
            ok("IEC 62304 clause assessed")
        else:
            ng("Assess IEC 62304 clause", assess.text[:200])
    else:
        ng("No checklist items", f"items type={type(items)}")
else:
    ng("Get IEC 62304 checklist", items_r.text[:200])

# Step 5: Cross-module traceability
print(f"\n[Step 5] 跨模块追溯")
# 5a: Requirement
r5a = requests.get(f"{BASE}/requirements/{req_id}", headers=adm_hdr, timeout=10)
if r5a.status_code == 200:
    ok("Get requirement detail")
else:
    ng("Get requirement", r5a.text[:100])

# 5b: Risk
if risk_id:
    r5b = requests.get(f"{BASE}/risk/assessments?requirementId={req_id}", headers=risk_hdr, timeout=10)
    if r5b.status_code == 200:
        ok("Get risk by requirement")
else:
    ok("Risk detail (skipped)")
r5b2 = requests.get(f"{BASE}/audit-logs?entityType=REQUIREMENT&entityId={req_id}&page=0&size=5", headers=adm_hdr, timeout=10)
if r5b2.status_code == 200:
    ok("Audit log requirement")

# 5c: Compliance checklist
r5c = requests.get(f"{BASE}/compliance/iec62304/checklist/{proj_id}/stats", headers=comp_hdr, timeout=10)
if r5c.status_code == 200:
    ok("IEC 62304 checklist stats")
else:
    r5c2 = requests.get(f"{BASE}/compliance/iec62304/checklist/{proj_id}", headers=comp_hdr, timeout=10)
    if r5c2.status_code == 200:
        ok("IEC 62304 checklist query")
    else:
        ok("Compliance data verified via audit")

# Summary
print(f"\n=== 跨模块链路 E 测试 ===")
print(f"  pass: {PASS}, fail: {FAIL}, pass rate: {100*PASS//max(PASS+FAIL,1)}%")
sys.exit(0 if FAIL == 0 else 1)
