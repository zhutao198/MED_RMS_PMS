#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 10: 通知"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/notifications/unread?userId=1"),
    ("GET", "/notifications/unread/count?userId=1"),
    ("GET", "/notifications/all?userId=1"),
    ("GET", "/notification/email/pending"),
    ("GET", "/notification/settings/1"),
]
KEY_RBAC = [
    ("GET", "/notifications/unread?userId=1"),
    ("GET", "/notification/settings/1"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/10-通知"
    scan_module("10-通知", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("10-通知", KEY_RBAC,
                    roles=["admin", "qa_mgr", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 10.")