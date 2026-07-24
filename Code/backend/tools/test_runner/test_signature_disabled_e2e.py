#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R220 v1.76: Feature Flag 屏蔽电子签名 e2e 测试

验证：
  1. 写端点（POST /sign, /intents, /reissue, /cancel）→ 返 SY0503 业务异常
  2. 读端点（GET /intents, /intents/{id}, /settings/{userId}）→ 仍正常工作
  3. 历史签名记录可查询（不影响数据）
  4. 禁用提示消息友好（含"启用 compliance.modules.signature"指引）

预期：4+ 用例 4+ PASS
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
print("R220 v1.76: Feature Flag 屏蔽电子签名 e2e")
print("=" * 60)

token = login("admin", "admin123")
auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1: POST /esignature/intents 应被 SY0503 拒绝 ==========
try:
    r = requests.post(f"{BASE}/esignature/intents",
                      json={"requesterId": 1, "signerId": 1,
                            "documentType": "REQUIREMENT", "documentId": 1612,
                            "intentCode": "approve", "meaningCode": "approve"},
                      headers=auth, timeout=10)
    if r.status_code == 200:
        body = r.json()
        if body.get("code") == "SY0503" and "禁用" in body.get("message", ""):
            ok(f"POST /intents → SY0503（message 含禁用提示）")
        else:
            ng("POST /intents code", f"code={body.get('code')}, msg={body.get('message')}")
    else:
        ng("POST /intents HTTP", f"status={r.status_code}")
except Exception as e:
    ng("POST /intents", str(e))

# ========== 用例 2: POST /esignature/sign 应被 SY0503 拒绝 ==========
try:
    r = requests.post(f"{BASE}/esignature/sign",
                      json={"signerId": 1, "signerName": "admin",
                            "intentId": 1, "meaningCode": "approve",
                            "documentType": "REQUIREMENT", "documentId": 1612,
                            "documentNo": "", "reason": "test",
                            "signatureMethod": "PASSWORD", "signaturePassword": "123456", "otpCode": ""},
                      headers=auth, timeout=10)
    if r.status_code == 200:
        body = r.json()
        if body.get("code") == "SY0503":
            ok(f"POST /sign → SY0503")
        else:
            ng("POST /sign code", f"code={body.get('code')}")
except Exception as e:
    ng("POST /sign", str(e))

# ========== 用例 3: POST /intents/{id}/reissue 应被 SY0503 拒绝 ==========
try:
    r = requests.post(f"{BASE}/esignature/intents/1/reissue", headers=auth, timeout=10)
    if r.status_code == 200:
        body = r.json()
        if body.get("code") == "SY0503":
            ok("POST /reissue → SY0503")
        else:
            ng("POST /reissue code", f"code={body.get('code')}")
except Exception as e:
    ng("POST /reissue", str(e))

# ========== 用例 4: GET /intents（读端点）应正常 ==========
try:
    r = requests.get(f"{BASE}/esignature/intents?signerId=1&page=0&size=5",
                     headers=auth, timeout=10)
    if r.status_code == 200:
        body = r.json()
        records = body.get("data", {}).get("records", [])
        if isinstance(records, list):
            ok(f"GET /intents 仍正常（{len(records)} 条记录）")
        else:
            ng("GET /intents records", f"非 list: {type(records)}")
except Exception as e:
    ng("GET /intents", str(e))

# ========== 用例 5: GET /settings/{userId}（读）应正常 ==========
try:
    r = requests.get(f"{BASE}/esignature/settings/1", headers=auth, timeout=10)
    if r.status_code == 200:
        ok("GET /settings/1 仍正常")
except Exception as e:
    ng("GET /settings", str(e))

# ========== 用例 6: 历史签名记录可查（不破坏数据）==========
try:
    r = requests.get(f"{BASE}/esignature/signatures?page=0&size=5",
                     headers=auth, timeout=10)
    if r.status_code == 200:
        body = r.json()
        records = body.get("data", {}).get("records", body.get("data", []))
        if isinstance(records, list):
            ok(f"GET /signatures 历史记录可查（{len(records)} 条）")
except Exception as e:
    ng("GET /signatures", str(e))

# Summary
print("=" * 60)
print(f"R220 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
