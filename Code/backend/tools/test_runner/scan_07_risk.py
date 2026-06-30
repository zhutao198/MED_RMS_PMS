#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 7: 风险管理"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/risk/register/list?projectId=1"),
    ("GET", "/risk/report/1"),
    ("GET", "/risk/requirement/1"),
    ("GET", "/risk/matrix/list/1"),
]
KEY_RBAC = [
    ("GET", "/risk/register/list?projectId=1"),
    ("GET", "/risk/report/1"),
    ("GET", "/risk/matrix/list/1"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/07-风险管理"
    scan_module("07-风险管理", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("07-风险管理", KEY_RBAC,
                    roles=["admin", "risk_mgr", "qa_mgr", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 7.")