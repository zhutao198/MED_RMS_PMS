#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 4: 变更管理"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/changes/list?page=0&size=10"),
    ("GET", "/changes/pending"),
    ("GET", "/changes/1"),
    ("GET", "/changes/1/impacts"),
    ("GET", "/changes/1/attachments"),
    ("GET", "/changes/1/timeline"),
]
KEY_RBAC = [
    ("GET", "/changes/list?page=0&size=10"),
    ("GET", "/changes/pending"),
    ("GET", "/changes/1"),
]

if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/04-变更管理"
    scan_module("04-变更管理", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("04-变更管理", KEY_RBAC,
                    roles=["admin", "qa_mgr", "pm"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 4.")