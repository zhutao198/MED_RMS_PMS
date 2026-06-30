#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 6: 电子签名"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/esignature/signatures?page=0&size=10"),
    ("GET", "/esignature/intents?signerId=1&status=PENDING&page=0&size=10"),
    ("GET", "/esignature/settings/1"),
    ("GET", "/esignature/settings/1/otp/uri"),
]
KEY_RBAC = [
    ("GET", "/esignature/signatures?page=0&size=10"),
    ("GET", "/esignature/intents?signerId=1&status=PENDING&page=0&size=10"),
    ("GET", "/esignature/settings/1"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/06-电子签名"
    scan_module("06-电子签名", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("06-电子签名", KEY_RBAC,
                    roles=["admin", "compliance", "qa_mgr", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 6.")