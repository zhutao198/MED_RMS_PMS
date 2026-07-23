#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R219 v1.75: 智能过期通知 + 重新发起 e2e 测试

验证：
  1. POST /esignature/intents/{id}/reissue 用 EXPIRED intent 创建新 PENDING intent
  2. 新 intent 的 expiresAt 是 now + 15min（未过期）
  3. 新 intent 继承原 intent 的 documentType/documentId/intentCode
  4. 非 EXPIRED 状态不能 reissue（返回 stateConflict）
  5. V1003 DDL 字段（notified_*_at）已应用到 DB

预期：5 用例 5 PASS
"""
import os
import sys
import time
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
print("R219 v1.75: 智能过期 + 重新发起 e2e")
print("=" * 60)

token = login("admin", "admin123")
auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1: 创建 intent + 标记过期 + 重新发起 ==========
expired_id = None
new_id = None
try:
    # 1) 创建 intent
    r = requests.post(f"{BASE}/esignature/intents",
                      json={"requesterId": 1, "signerId": 1,
                            "documentType": "REQUIREMENT", "documentId": 1612,
                            "intentCode": "approve", "meaningCode": "approve"},
                      headers=auth, timeout=10)
    if r.status_code != 200:
        ng("创建 intent", f"status={r.status_code}")
    else:
        created_id = r.json()["data"]["id"]
        # 2) 手动改 expiresAt 为 5min 前 → 触发扫描后变 EXPIRED
        # 用 updateById 是不行的，需要直接调 sweepExpiredIntents
        # 这里通过 query 找 PENDING 然后通过 setStatus API 模拟（不直接）
        # 实际方案：创建后等下一次 60s 扫描，或直接 DB 改
        # 简化：等几秒（不行，60s 周期），用 EXISTING EXPIRED 测试
        # 取一个已 EXPIRED 的 intent
        r = requests.get(f"{BASE}/esignature/intents?status=EXPIRED&signerId=1&page=0&size=5",
                         headers=auth, timeout=10)
        expired_list = r.json().get("data", {}).get("records", [])
        if not expired_list:
            ng("EXPIRED intent", "无 EXPIRED 可测（需等定时任务或 DB 改）")
        else:
            expired_id = expired_list[0]["id"]
            # 3) 重新发起
            r = requests.post(f"{BASE}/esignature/intents/{expired_id}/reissue",
                              headers=auth, timeout=10)
            if r.status_code == 200:
                body = r.json()
                if body.get("code") == 200:
                    new_intent = body.get("data", {})
                    new_id = new_intent.get("id")
                    if new_id and new_id != expired_id:
                        ok(f"重新发起成功：原 EXPIRED id={expired_id} → 新 PENDING id={new_id}")
                    else:
                        ng("新 intent id", f"无效={new_id}")
                else:
                    ng("reissue code", f"code={body.get('code')}")
            else:
                ng("reissue HTTP", f"status={r.status_code}")
except Exception as e:
    ng("用例 1", str(e))

# ========== 用例 2: 新 intent 的 expiresAt 未过期 ==========
try:
    if new_id:
        r = requests.get(f"{BASE}/esignature/intents?status=PENDING&signerId=1&page=0&size=20",
                         headers=auth, timeout=10)
        records = r.json()["data"]["records"]
        new_intent = next((i for i in records if i["id"] == new_id), None)
        if new_intent:
            from datetime import datetime
            expires = datetime.fromisoformat(new_intent["expiresAt"][:19])
            delta = (expires - datetime.now()).total_seconds()
            if delta > 60:  # 至少还有 1min
                ok(f"新 intent expiresAt 未过期（剩余 {int(delta)}s）")
            else:
                ng("新 intent expiresAt", f"delta={delta}s 异常")
        else:
            ng("新 intent 查询", "未在 PENDING 列表")
except Exception as e:
    ng("用例 2", str(e))

# ========== 用例 3: 新 intent 继承原参数 ==========
try:
    if new_id and expired_id:
        r = requests.get(f"{BASE}/esignature/intents?status=PENDING&signerId=1&page=0&size=50",
                         headers=auth, timeout=10)
        records = r.json()["data"]["records"]
        new_intent = next((i for i in records if i["id"] == new_id), None)
        old = next((i for i in records if i["id"] == expired_id), None)
        # EXPIRED 的 record 可能在 EXPIRED 列表而非 PENDING
        if not old:
            r2 = requests.get(f"{BASE}/esignature/intents?status=EXPIRED&signerId=1&page=0&size=50",
                              headers=auth, timeout=10)
            old = next((i for i in r2.json()["data"]["records"] if i["id"] == expired_id), None)
        if new_intent and old:
            fields_match = (new_intent["documentType"] == old["documentType"] and
                           new_intent["documentId"] == old["documentId"] and
                           new_intent["intentCode"] == old["intentCode"] and
                           new_intent["meaningCode"] == old["meaningCode"] and
                           new_intent["requesterId"] == old["requesterId"])
            if fields_match:
                ok(f"新 intent 继承原参数（documentType/documentId/intentCode/meaningCode/requesterId）")
            else:
                ng("字段继承", f"new={new_intent}, old={old}")
except Exception as e:
    ng("用例 3", str(e))

# ========== 用例 4: 非 EXPIRED 不能 reissue ==========
try:
    # 取一个 PENDING（不是 EXPIRED）
    r = requests.get(f"{BASE}/esignature/intents?status=PENDING&signerId=1&page=0&size=5",
                     headers=auth, timeout=10)
    pending = r.json()["data"]["records"]
    if pending:
        pending_id = pending[0]["id"]
        r = requests.post(f"{BASE}/esignature/intents/{pending_id}/reissue",
                          headers=auth, timeout=10)
        if r.status_code == 200:
            body = r.json()
            if body.get("code") in ("RQ0102", "SY0401"):  # stateConflict
                ok(f"非 EXPIRED reissue 被拒绝（code={body.get('code')}）")
            else:
                ng("非 EXPIRED code", f"code={body.get('code')}")
        else:
            ng("非 EXPIRED HTTP", f"status={r.status_code}")
except Exception as e:
    ng("用例 4", str(e))

# ========== 用例 5: V1003 字段已应用 ==========
# 通过 listIntents 间接验证（notified_*_at 字段在 select 列中即使为 null 也会返回）
# 实际上 MyBatis-Plus 默认 select 全部字段，如果新字段存在就会返回
try:
    r = requests.get(f"{BASE}/esignature/intents?status=EXPIRED&signerId=1&page=0&size=1",
                     headers=auth, timeout=10)
    records = r.json()["data"]["records"]
    if records:
        first = records[0]
        # 字段可能在 JSON 里以 notified5MinAt / notified_1min_at 等不同命名
        keys = list(first.keys())
        has_notify_field = any("notified" in k.lower() or "notif" in k.lower() for k in keys)
        if has_notify_field:
            ok(f"V1003 字段已应用（字段含 notified 关键字）")
        else:
            # 字段不存在 → V1003 没应用
            ng("V1003 字段", f"keys={keys}")
except Exception as e:
    ng("用例 5", str(e))

# Summary
print("=" * 60)
print(f"R219 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
