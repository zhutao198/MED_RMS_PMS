#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 9: 报表"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/reports?page=0&size=10"),
    ("GET", "/reports/configs?page=0&size=10"),
    ("GET", "/dashboard/view/management"),
    ("GET", "/dashboard/view/compliance"),
]
KEY_RBAC = [
    ("GET", "/reports?page=0&size=10"),
    ("GET", "/reports/configs?page=0&size=10"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/09-报表"
    scan_module("09-报表", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("09-报表", KEY_RBAC,
                    roles=["admin", "compliance", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 9.")