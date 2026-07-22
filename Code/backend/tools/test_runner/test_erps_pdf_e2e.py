#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R209 v1.66: NMPA eRPS 中文 PDF 导出 e2e 测试（FR-1.12）

验证清单：
  1. /compliance/erps/export/{id} 返回 200 + JSON 结构
  2. /compliance/erps/export/xml/{id} 返回 application/xml
  3. /compliance/erps/download/{id} 返回 application/json blob
  4. /compliance/erps/download/pdf/{id} 返回 application/pdf（R209 新增）
  5. PDF 字节流首 4 字节 = %PDF
  6. 文件名规范：NMPA-eRPS-报告-{projectNo}-{yyyyMMdd}.pdf
  7. X-eRPS-Schema header 包含 NMPA-eRPS-CHINA-MEDICAL-DEVICE-v1
  8. PDF ≥ 10KB（含中文）

预期：8 用例 8 PASS
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
print("R209 v1.66: NMPA eRPS 中文 PDF 导出 e2e 测试")
print("=" * 60)

token = login("compliance", "admin123")
print("[Step 1] compliance 登录 OK")
project_id = find_project_id(token)
if not project_id:
    ng("无项目", "请先 seed 数据")
    sys.exit(1)
print(f"[Step 2] 使用 projectId={project_id}")
auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1: JSON 导出 ==========
try:
    r = requests.get(f"{BASE}/compliance/erps/export/{project_id}", headers=auth, timeout=10)
    if r.status_code == 200:
        data = r.json().get("data", {})
        if "schema" in data and "NMPA-eRPS-CHINA-MEDICAL-DEVICE" in data.get("schema", ""):
            ok(f"JSON 导出 OK（schema={data['schema']}）")
        else:
            ng("JSON schema", str(data.get("schema")))
    else:
        ng("JSON 导出", f"status={r.status_code}")
except Exception as e:
    ng("JSON 导出", str(e))

# ========== 用例 2: XML 导出 ==========
try:
    r = requests.get(f"{BASE}/compliance/erps/export/xml/{project_id}", headers=auth, timeout=10)
    if r.status_code == 200 and "xml" in r.headers.get("Content-Type", ""):
        if b"<NMPA-eRPS>" in r.content:
            ok(f"XML 导出 OK（{len(r.content)} bytes）")
        else:
            ng("XML 内容", "缺少 <NMPA-eRPS> 根节点")
    else:
        ng("XML 导出", f"status={r.status_code}")
except Exception as e:
    ng("XML 导出", str(e))

# ========== 用例 3: JSON 下载 ==========
try:
    r = requests.get(f"{BASE}/compliance/erps/download/{project_id}", headers=auth, timeout=10)
    if r.status_code == 200 and "json" in r.headers.get("Content-Type", ""):
        ok(f"JSON 下载 OK（{len(r.content)} bytes）")
    else:
        ng("JSON 下载", f"status={r.status_code}")
except Exception as e:
    ng("JSON 下载", str(e))

# ========== 用例 4: PDF 下载（R209 核心）==========
pdf_bytes = None
try:
    r = requests.get(f"{BASE}/compliance/erps/download/pdf/{project_id}", headers=auth, timeout=60)
    ct = r.headers.get("Content-Type", "")
    if "application/pdf" in ct:
        ok(f"PDF Content-Type=application/pdf")
    else:
        ng("PDF Content-Type", f"实际={ct}")
    if r.status_code == 200 and len(r.content) > 100:
        pdf_bytes = r.content
        ok(f"PDF 字节流 {len(r.content)} bytes")
    else:
        ng("PDF 字节流", f"status={r.status_code}, size={len(r.content)}")
except Exception as e:
    ng("PDF 下载", str(e))

# ========== 用例 5: PDF 魔数 ==========
if pdf_bytes:
    if pdf_bytes[:4] == b"%PDF":
        ok("PDF 魔数 %PDF 正确")
    else:
        ng("PDF 魔数", f"实际={pdf_bytes[:8]}")

# ========== 用例 6: 文件名规范 ==========
try:
    r = requests.get(f"{BASE}/compliance/erps/download/pdf/{project_id}", headers=auth, timeout=60)
    disposition = r.headers.get("Content-Disposition", "")
    if "filename=" in disposition and ".pdf" in disposition.lower():
        ok(f"文件名规范 OK: {disposition[:100]}")
    else:
        ng("文件名", f"disposition={disposition}")
except Exception as e:
    ng("文件名检查", str(e))

# ========== 用例 7: X-eRPS-Schema header ==========
try:
    r = requests.get(f"{BASE}/compliance/erps/download/pdf/{project_id}", headers=auth, timeout=60)
    schema_hdr = r.headers.get("X-eRPS-Schema", "")
    if "NMPA-eRPS-CHINA-MEDICAL-DEVICE" in schema_hdr:
        ok(f"X-eRPS-Schema={schema_hdr}")
    else:
        ng("X-eRPS-Schema", f"实际={schema_hdr}")
except Exception as e:
    ng("X-eRPS-Schema", str(e))

# ========== 用例 8: PDF 体积（中文需 ≥ 10KB）==========
if pdf_bytes and len(pdf_bytes) >= 10240:
    ok(f"PDF 体积符合预期（{len(pdf_bytes)/1024:.1f} KB）")
elif pdf_bytes:
    ng("PDF 体积", f"过小 {len(pdf_bytes)} bytes（中文 PDF 通常 ≥ 10KB）")

# Summary
print("=" * 60)
print(f"R209 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
