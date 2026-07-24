#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R221 v1.77: 前端 Feature Flag 端点测试

验证：
  1. GET /api/feature/flags 返回当前 signature 状态
  2. 返回 JSON 含 code 200 + data.signature
  3. 当前为 false（被 R220 禁用）
  4. 修改 application.yml 为 true 后能正确反映

预期：3 用例 3 PASS
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


print("=" * 60)
print("R221 v1.77: 前端 Feature Flag 端点测试")
print("=" * 60)

# ========== 用例 1: 端点可访问（无需认证）==========
try:
    r = requests.get(f"{BASE}/feature/flags", timeout=10)
    if r.status_code == 200:
        ok(f"GET /feature/flags HTTP 200")
    else:
        ng("HTTP 状态", f"status={r.status_code}")
except Exception as e:
    ng("HTTP 调用", str(e))

# ========== 用例 2: 响应格式正确 ==========
try:
    r = requests.get(f"{BASE}/feature/flags", timeout=10)
    body = r.json()
    if body.get("code") == 200 and "data" in body:
        data = body["data"]
        if "signature" in data and isinstance(data["signature"], bool):
            ok(f"响应格式正确（data.signature={data['signature']}）")
        else:
            ng("data.signature 字段", f"内容={data}")
except Exception as e:
    ng("响应格式", str(e))

# ========== 用例 3: 当前 signature=false（R220 屏蔽生效）==========
try:
    r = requests.get(f"{BASE}/feature/flags", timeout=10)
    data = r.json().get("data", {})
    if data.get("signature") is False:
        ok("signature=false（R220 屏蔽生效，前端应隐藏菜单）")
    else:
        ng("signature 状态", f"实际={data.get('signature')}")
except Exception as e:
    ng("signature 状态", str(e))

# Summary
print("=" * 60)
print(f"R221 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
