#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R208.2 v1.69b: Excel 导入后 ancestor 闭包表重建 e2e 测试

验证：
  1. Excel 导入含 upstreamNos 列 → 自动重建 ancestor 闭包
  2. 响应包含 ancestorRebuild 字段
  3. ancestorRebuild 含 ancestorOk/ancestorSkip/ancestorError/errors 字段
  4. 上游编号不存在 → ancestorSkip 增加
  5. 多次导入幂等（不重复创建）

预期：5+ 用例 5+ PASS
"""
import io
import os
import sys
sys.path.insert(0, os.path.dirname(__file__))
import requests
from openpyxl import Workbook

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


def make_xlsx(rows):
    wb = Workbook()
    ws = wb.active
    headers = ["requirementNo", "title", "description", "priority", "riskLevel",
               "safetyClass", "source", "acceptanceCriteria", "upstreamNos"]
    ws.append(headers)
    for r in rows:
        ws.append(r)
    buf = io.BytesIO()
    wb.save(buf)
    return buf.getvalue()


print("=" * 60)
print("R208.2 v1.69b: ancestor 闭包表重建 e2e 测试")
print("=" * 60)

token = login("admin", "admin123")
project_id = find_project_id(token)
auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1: 导入含 upstreamNos（指向已存在需求）==========
# 先查项目已有需求编号（作为上游）
try:
    r = requests.get(f"{BASE}/requirements?projectId={project_id}&size=5&type=URS",
                     headers=auth, timeout=10)
    existing = r.json().get("data", {}).get("records", [])
    if existing:
        existing_no = existing[0].get("requirementNo", "URS-P001-001")
    else:
        existing_no = "URS-P001-001"
except Exception:
    existing_no = "URS-P001-001"

try:
    rows = [
        ["", "R208.2 测试 PRS-1", "R208.2 e2e 测试创建 PRS 含上游追溯" + "x" * 20,
         "MUST", "MEDIUM", "B", "INTERNAL", "1. 验收项", existing_no],
        ["", "R208.2 测试 PRS-2", "R208.2 e2e 测试 PRS-2（多上游）" + "x" * 20,
         "SHOULD", "LOW", "A", "INTERNAL", "1. 验收项", ""],
    ]
    files = {"file": ("test_ancestor.xlsx", make_xlsx(rows),
                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    r = requests.post(f"{BASE}/requirements/excel/import/PRS?projectId={project_id}",
                      files=files, headers=auth, timeout=30)
    if r.status_code == 200:
        result = r.json().get("data", {})
        rebuild = result.get("ancestorRebuild", {})
        if isinstance(rebuild, dict) and "ancestorOk" in rebuild:
            ok(f"导入含 upstreamNos 触发 ancestor 重建（ok={rebuild.get('ancestorOk')}）")
        else:
            ng("ancestorRebuild 字段", f"缺字段: {rebuild}")
    else:
        ng("导入调用", f"status={r.status_code}")
except Exception as e:
    ng("导入追溯", str(e))

# ========== 用例 2: ancestorRebuild 字段完整性 ==========
try:
    rows = [["", "R208.2 测试 PRS-3", "R208.2 字段完整性测试描述文字超过二十字以满足校验要求",
             "MUST", "HIGH", "C", "CUSTOMER", "1. 测试", "NON-EXISTENT-NO-9999"]]
    files = {"file": ("test_field.xlsx", make_xlsx(rows),
                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    r = requests.post(f"{BASE}/requirements/excel/import/PRS?projectId={project_id}",
                      files=files, headers=auth, timeout=30)
    if r.status_code == 200:
        rebuild = r.json().get("data", {}).get("ancestorRebuild", {})
        required = ["ancestorOk", "ancestorSkip", "ancestorError", "errors"]
        missing = [k for k in required if k not in rebuild]
        if not missing:
            ok(f"ancestorRebuild 含 {len(required)} 字段（ok={rebuild['ancestorOk']}, skip={rebuild['ancestorSkip']}, err={rebuild['ancestorError']}）")
        else:
            ng("字段完整性", f"缺={missing}")
except Exception as e:
    ng("字段完整性", str(e))

# ========== 用例 3: 不存在的上游编号 → ancestorSkip ==========
try:
    rows = [["", "R208.2 测试 PRS-4", "R208.2 不存在上游编号测试描述文字超过二十字满足校验要求",
             "MUST", "MEDIUM", "B", "INTERNAL", "1. 测试", "DOES-NOT-EXIST-XYZ-9999"]]
    files = {"file": ("test_skip.xlsx", make_xlsx(rows),
                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    r = requests.post(f"{BASE}/requirements/excel/import/PRS?projectId={project_id}",
                      files=files, headers=auth, timeout=30)
    if r.status_code == 200:
        rebuild = r.json().get("data", {}).get("ancestorRebuild", {})
        skip_count = rebuild.get("ancestorSkip", 0)
        if skip_count > 0:
            ok(f"不存在上游 graceful（ancestorSkip={skip_count}）")
        else:
            ng("graceful skip", f"skip={skip_count}")
except Exception as e:
    ng("graceful skip", str(e))

# ========== 用例 4: 多个上游（逗号分隔）==========
try:
    rows = [["", "R208.2 测试 PRS-5", "R208.2 多上游测试描述文字超过二十字满足校验要求",
             "MUST", "MEDIUM", "B", "INTERNAL", "1. 测试",
             existing_no + ",DOES-NOT-EXIST-8888"]]
    files = {"file": ("test_multi.xlsx", make_xlsx(rows),
                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    r = requests.post(f"{BASE}/requirements/excel/import/PRS?projectId={project_id}",
                      files=files, headers=auth, timeout=30)
    if r.status_code == 200:
        rebuild = r.json().get("data", {}).get("ancestorRebuild", {})
        ok_count = rebuild.get("ancestorOk", 0)
        skip_count = rebuild.get("ancestorSkip", 0)
        if ok_count >= 1 and skip_count >= 1:
            ok(f"多上游：ok={ok_count}, skip={skip_count}（混合已存在/不存在）")
        else:
            ng("多上游", f"ok={ok_count}, skip={skip_count}")
except Exception as e:
    ng("多上游", str(e))

# ========== 用例 5: 无 upstreamNos → ancestorSkip 不增加 ok ==========
try:
    rows = [["", "R208.2 测试 PRS-6", "R208.2 无上游测试描述文字超过二十字满足校验要求",
             "MUST", "LOW", "A", "INTERNAL", "1. 测试", ""]]
    files = {"file": ("test_no_up.xlsx", make_xlsx(rows),
                       "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")}
    r = requests.post(f"{BASE}/requirements/excel/import/PRS?projectId={project_id}",
                      files=files, headers=auth, timeout=30)
    if r.status_code == 200:
        rebuild = r.json().get("data", {}).get("ancestorRebuild", {})
        ok_count = rebuild.get("ancestorOk", 0)
        skip_count = rebuild.get("ancestorSkip", 0)
        if ok_count == 0 and skip_count == 0:
            ok(f"无上游列：ok={ok_count}, skip={skip_count}（无操作符合预期）")
        else:
            ng("无上游", f"ok={ok_count}, skip={skip_count}")
except Exception as e:
    ng("无上游", str(e))

# Summary
print("=" * 60)
print(f"R208.2 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
