#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 3: 追溯管理 - 批量 API 扫描"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/traceability/matrix?projectId=1"),
    ("GET", "/traceability/coverage?projectId=1"),
    ("GET", "/traceability/gaps?projectId=1"),
    ("GET", "/traceability/gaps/ignored?projectId=1"),
    ("GET", "/trace-links?projectId=1"),
    ("GET", "/trace-links/by-source/1"),
    ("GET", "/trace-links/by-target/1"),
    ("GET", "/trace-graph/project/1"),
    ("GET", "/trace-graph/quality/1"),
    ("GET", "/requirements/1/trace-count"),
]

KEY_RBAC = [
    ("GET", "/traceability/matrix?projectId=1"),
    ("GET", "/traceability/gaps?projectId=1"),
    ("GET", "/trace-links?projectId=1"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/03-追溯管理"
    scan_module("03-追溯管理", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("03-追溯管理", KEY_RBAC,
                    roles=["admin", "qa_mgr", "re", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("\n[DONE] Module 3.")