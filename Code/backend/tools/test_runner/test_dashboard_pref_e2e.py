#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R215 v1.71: Dashboard 持久化增强 e2e 测试

验证：
  1. GET /api/user/preferences 列出当前用户所有偏好
  2. PUT /api/user/preferences/{key} 设置偏好
  3. GET /api/user/preferences/{key} 读取偏好（upsert 后能取到）
  4. GET /api/user/preferences?keys=a,b 批量获取
  5. DELETE /api/user/preferences/{key} 删除
  6. 跨用户隔离（admin 设的偏好，pm 看不到）

预期：6+ 用例 6+ PASS
"""
import os
import sys
import json
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
print("R215 v1.71: Dashboard 持久化增强 e2e 测试")
print("=" * 60)

token_admin = login("admin", "admin123")
token_pm = login("pm", "admin123")
auth_admin = {"Authorization": f"Bearer {token_admin}"}
auth_pm = {"Authorization": f"Bearer {token_pm}"}

# ========== 用例 1: 列出所有偏好（初始为空）==========
try:
    r = requests.get(f"{BASE}/user/preferences", headers=auth_admin, timeout=10)
    if r.status_code == 200:
        data = r.json().get("data", {})
        if isinstance(data, dict):
            ok(f"列出所有偏好 OK（当前 {len(data)} 项）")
        else:
            ng("列表", f"data 非 dict: {type(data)}")
    else:
        ng("列表", f"status={r.status_code}")
except Exception as e:
    ng("列表", str(e))

# ========== 用例 2: 设置偏好 ==========
try:
    r = requests.put(f"{BASE}/user/preferences/dashboard.layout",
                     json={"value": json.dumps({"widgets": ["milestone", "dcp", "risk"]})},
                     headers=auth_admin, timeout=10)
    if r.status_code == 200:
        ok("设置 dashboard.layout")
    else:
        ng("设置", f"status={r.status_code}")
except Exception as e:
    ng("设置", str(e))

# ========== 用例 3: 读取偏好 ==========
try:
    r = requests.get(f"{BASE}/user/preferences/dashboard.layout", headers=auth_admin, timeout=10)
    if r.status_code == 200:
        data = r.json().get("data", {})
        if "dashboard.layout" in data:
            ok(f"读取偏好 OK（value 非空={len(data['dashboard.layout']) > 0}）")
        else:
            ng("读取", f"keys={list(data.keys())}")
    else:
        ng("读取", f"status={r.status_code}")
except Exception as e:
    ng("读取", str(e))

# ========== 用例 4: 批量获取 ==========
try:
    requests.put(f"{BASE}/user/preferences/dashboard.refreshInterval",
                 json={"value": "60"}, headers=auth_admin, timeout=10)
    requests.put(f"{BASE}/user/preferences/dashboard.perspective",
                 json={"value": "management"}, headers=auth_admin, timeout=10)
    r = requests.get(f"{BASE}/user/preferences?keys=dashboard.layout,dashboard.refreshInterval",
                     headers=auth_admin, timeout=10)
    if r.status_code == 200:
        data = r.json().get("data", {})
        if "dashboard.layout" in data and "dashboard.refreshInterval" in data:
            ok(f"批量获取 OK（{len(data)} 项）")
        else:
            ng("批量", f"keys={list(data.keys())}")
    else:
        ng("批量", f"status={r.status_code}")
except Exception as e:
    ng("批量", str(e))

# ========== 用例 5: 删除偏好 ==========
try:
    r = requests.delete(f"{BASE}/user/preferences/dashboard.refreshInterval",
                        headers=auth_admin, timeout=10)
    if r.status_code == 200:
        result = r.json().get("data")
        if result is True:
            ok("删除偏好 OK")
        else:
            ng("删除", f"data={result}")
    else:
        ng("删除", f"status={r.status_code}")
except Exception as e:
    ng("删除", str(e))

# ========== 用例 6: 跨用户隔离 ==========
try:
    # admin 设 dashboard.color=red
    requests.put(f"{BASE}/user/preferences/dashboard.color",
                 json={"value": "red"}, headers=auth_admin, timeout=10)
    # pm 不应能看到 admin 的偏好
    r = requests.get(f"{BASE}/user/preferences/dashboard.color", headers=auth_pm, timeout=10)
    if r.status_code == 200:
        data = r.json().get("data", {})
        val = data.get("dashboard.color", "not-set")
        if val in (None, "", "not-set"):
            ok(f"跨用户隔离 OK（pm 看不到 admin 的偏好）")
        else:
            ng("跨用户隔离", f"pm 看到了 admin 的值={val}")
    else:
        ng("跨用户隔离", f"status={r.status_code}")
except Exception as e:
    ng("跨用户隔离", str(e))

# Summary
print("=" * 60)
print(f"R215 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
