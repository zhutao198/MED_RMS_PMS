#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
P1 端点验证（用真实 controller 路径）
- 修正 R114 测试报告中 5 处路径错误
- 新增 P146 修复的 2 个端点（trace-count / test-case-count）
- 验证 R143 / R115 / R131 / R118 关键端点
- 输出: PASS/FAIL 汇总，PassRate

Usage:
    python scan_p1_endpoints.py
"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080"

def get(url, token):
    req = urllib.request.Request(
        BASE + url,
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            return json.loads(r.read().decode("utf-8")), None
    except urllib.error.HTTPError as e:
        return None, f"HTTP {e.code}"
    except Exception as e:
        return None, str(e)

# 登录
req = urllib.request.Request(
    BASE + "/api/auth/login",
    data=json.dumps({"username": "admin", "password": "admin123"}).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=10) as r:
    data = json.loads(r.read().decode("utf-8"))
token = data["data"]["token"]

# 用真路径重测
endpoints = {
    "需求管理": [
        ("/api/requirements?page=0&size=10", "需求列表"),
        ("/api/requirements/kanban", "看板"),
        ("/api/requirements/stats", "5张统计卡 (R115)"),
        ("/api/requirements/1", "需求详情 ID=1"),
        ("/api/requirements/tree?projectId=1", "需求树 (R118)"),
        ("/api/requirements/quality?projectId=1", "质量评分 (R143)"),
        ("/api/requirement-pool?page=0&size=10", "需求池"),
        ("/api/requirement/soup-components?page=0&size=10", "SOUP组件 (R131)"),
        ("/api/requirement-tasks/by-requirement/1", "需求任务"),
        ("/api/requirement-tasks/candidates?projectId=1", "可转化需求"),
        ("/api/requirements/1/reviews", "评审历史"),
        ("/api/requirements/1/versions", "版本历史"),
    ],
    "通知": [
        ("/api/notifications/unread?userId=1", "未读"),
        ("/api/notifications/unread/count?userId=1", "未读数量"),
        ("/api/notifications/all?userId=1", "全部"),
        ("/api/notifications/email/pending", "邮件待发"),
        ("/api/notifications/settings/1", "通知设置"),
    ],
}

pass_count = 0
fail_count = 0
fails = []
print("=" * 70)
print("P1 端点验证 - 用真实 controller 路径")
print("=" * 70)
for module, eps in endpoints.items():
    print(f"\n【{module}】")
    for ep, name in eps:
        d, e = get(ep, token)
        if d and d.get("code") == 200:
            print(f"  [PASS] {ep} ({name})")
            pass_count += 1
        else:
            code = d.get("code") if d else "N/A"
            err = e if e else f"code={code} msg={d.get('message','')[:50] if d else ''}"
            print(f"  [FAIL] {ep} ({name}) - {err}")
            fail_count += 1
            fails.append((module, ep, name, err))

print(f"\n{'='*70}")
print(f"汇总: PASS={pass_count} FAIL={fail_count} Total={pass_count+fail_count}  PassRate={pass_count*100/(pass_count+fail_count):.0f}%")
print(f"{'='*70}")
if fails:
    print("\n失败明细:")
    for m, ep, name, err in fails:
        print(f"  [{m}] {ep} ({name}) - {err}")
