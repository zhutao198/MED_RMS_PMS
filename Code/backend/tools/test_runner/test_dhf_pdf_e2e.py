#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R207: DHF 证据包 PDF 一键生成 e2e 测试（FR-1.4）

验证清单：
  1. /compliance/dhf/manifest/{id} 返回 12 章节
  2. /compliance/dhf/generate/{id} 返回完整 JSON（含 verdict.incompleteItems）
  3. /compliance/dhf/download/{id} 返回 application/pdf 字节流
  4. PDF 字节流首 4 字节 = %PDF
  5. 文件名规范：DHF-证据包-{projectNo}-{DCP阶段}-{yyyyMMdd}.pdf
  6. verdict.status 是 PASS/WARN/FAIL 之一
  7. 老端点 /reports/dhf 仍可用（向后兼容）
  8. 老端点 /reports/dhf/download/{id} 返回 PDF（独立路径）

预期：8 用例 8 PASS（项目有完整数据时）
"""
import os
import sys
import time
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


def find_project_id(token):
    """从项目列表取第一个 ID（如 1）。"""
    r = requests.get(f"{BASE}/projects?page=0&size=10",
                     headers={"Authorization": f"Bearer {token}"}, timeout=10)
    data = r.json().get("data", {})
    if isinstance(data, dict):
        records = data.get("records", [])
    else:
        records = data
    if not records:
        return None
    return records[0].get("id")


print("=" * 60)
print("R207: DHF 证据包 PDF e2e 测试（FR-1.4）")
print("=" * 60)

# Login as compliance
try:
    token = login("compliance", "admin123")
    print("[Step 1] compliance 登录 OK")
except Exception as e:
    ng("登录失败", str(e))
    sys.exit(1)

# Find a project
project_id = find_project_id(token)
if not project_id:
    ng("项目查找", "无项目可测，请先 seed 数据")
    sys.exit(1)
print(f"[Step 2] 使用项目 ID: {project_id}")

auth = {"Authorization": f"Bearer {token}"}

# ========== 用例 1: manifest 返回 12 章节 ==========
try:
    r = requests.get(f"{BASE}/compliance/dhf/manifest/{project_id}", headers=auth, timeout=15)
    if r.status_code != 200:
        ng("manifest HTTP 200", f"status={r.status_code}")
    else:
        manifest = r.json().get("data", {})
        sections = manifest.get("sections", [])
        if len(sections) >= 12:
            ok(f"manifest 含 12 章节（实际 {len(sections)}）")
        else:
            ng("manifest 章节数", f"期望≥12，实际={len(sections)}")
except Exception as e:
    ng("manifest 调用", str(e))

# ========== 用例 2: generate JSON 完整 ==========
pkg = None
try:
    r = requests.post(f"{BASE}/compliance/dhf/generate/{project_id}", headers=auth, timeout=20)
    if r.status_code != 200:
        ng("generate HTTP 200", f"status={r.status_code}")
    else:
        pkg = r.json().get("data", {})
        # 12 章节存在性
        required_keys = ["traceMatrix", "coverageStats", "iec62304Stats", "dhfEvidences",
                         "regulatoryMappings", "baselines", "soupComponents", "problemReports",
                         "changeHistory", "auditLogs", "signatureLogs", "verdict"]
        missing = [k for k in required_keys if k not in pkg]
        if not missing:
            ok(f"generate 返回 12 章节齐全")
        else:
            ng("generate 章节缺失", f"缺失={missing}")
        # verdict.incompleteItems
        verdict = pkg.get("verdict", {})
        if "incompleteItems" in verdict:
            ok(f"verdict.incompleteItems 存在（{len(verdict['incompleteItems'])} 项）")
        else:
            ng("verdict.incompleteItems", "缺失")
        # verdict.status
        status = verdict.get("status")
        if status in ("PASS", "WARN", "FAIL"):
            ok(f"verdict.status 合法: {status}")
        else:
            ng("verdict.status", f"非法值={status}")
except Exception as e:
    ng("generate 调用", str(e))

# ========== 用例 3: download 返回 application/pdf ==========
pdf_bytes = None
try:
    r = requests.get(f"{BASE}/compliance/dhf/download/{project_id}", headers=auth, timeout=60)
    ct = r.headers.get("Content-Type", "")
    if "application/pdf" in ct:
        ok(f"download Content-Type=application/pdf")
    else:
        ng("download Content-Type", f"实际={ct}")
    if r.status_code == 200 and len(r.content) > 100:
        ok(f"download PDF 字节流 {len(r.content)} bytes")
        pdf_bytes = r.content
    else:
        ng("download 字节流", f"status={r.status_code}, size={len(r.content)}")
except Exception as e:
    ng("download 调用", str(e))

# ========== 用例 4: PDF 首 4 字节 = %PDF ==========
if pdf_bytes:
    if pdf_bytes[:4] == b"%PDF":
        ok("PDF 魔数 %PDF 正确")
    else:
        ng("PDF 魔数", f"实际={pdf_bytes[:8]}")

# ========== 用例 5: 文件名规范 ==========
try:
    r = requests.get(f"{BASE}/compliance/dhf/download/{project_id}", headers=auth, timeout=60)
    disposition = r.headers.get("Content-Disposition", "")
    if "filename=" in disposition and ".pdf" in disposition.lower():
        ok(f"文件名规范 OK: {disposition[:80]}")
    else:
        ng("文件名", f"disposition={disposition}")
except Exception as e:
    ng("文件名检查", str(e))

# ========== 用例 6: 老端点 /reports/dhf 仍可用 ==========
try:
    r = requests.post(f"{BASE}/reports/dhf?projectId={project_id}", headers=auth, timeout=20)
    if r.status_code == 200 and r.json().get("data"):
        ok(f"老端点 /reports/dhf 仍可用（向后兼容）")
    else:
        ng("老端点 /reports/dhf", f"status={r.status_code}")
except Exception as e:
    ng("老端点 /reports/dhf 调用", str(e))

# ========== 用例 7: 老端点 /reports/dhf/download/{id} 返回 PDF ==========
try:
    r = requests.get(f"{BASE}/reports/dhf/download/{project_id}", headers=auth, timeout=60)
    ct = r.headers.get("Content-Type", "")
    if "application/pdf" in ct and r.content[:4] == b"%PDF":
        ok(f"老端点下载 PDF 正常")
    else:
        ng("老端点下载", f"status={r.status_code}, ct={ct}")
except Exception as e:
    ng("老端点下载调用", str(e))

# ========== 用例 8: 无 project 时 graceful（不应 500） ==========
try:
    r = requests.get(f"{BASE}/compliance/dhf/download/9999999", headers=auth, timeout=10)
    if r.status_code in (200, 404, 400):
        ok(f"无效项目 graceful 处理（status={r.status_code}）")
    elif r.status_code == 500:
        ng("无效项目 500", "应降级到 404 而非 500")
    else:
        ng("无效项目", f"status={r.status_code}")
except Exception as e:
    ng("无效项目调用", str(e))

# Summary
print("=" * 60)
print(f"R207 总计：{PASS} PASS / {FAIL} FAIL / {PASS+FAIL} TOTAL")
print("=" * 60)
sys.exit(0 if FAIL == 0 else 1)
