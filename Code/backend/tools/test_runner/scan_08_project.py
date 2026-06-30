#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 8: 项目管理"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    ("GET", "/projects?page=0&size=10"),
    ("GET", "/projects/1"),
    ("GET", "/projects/templates"),
    ("GET", "/project/ipd-gate/list/1"),
    ("GET", "/project/member/list/1"),
    ("GET", "/gantt/tasks/project/1"),
    ("GET", "/gantt/milestones/project/1"),
    ("GET", "/gantt/dependencies/project/1"),
    ("GET", "/worklog/summary?projectId=1"),
    ("GET", "/project/deliverables?projectId=1"),
]
KEY_RBAC = [
    ("GET", "/projects?page=0&size=10"),
    ("GET", "/project/ipd-gate/list/1"),
    ("GET", "/gantt/tasks/project/1"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/08-项目管理"
    scan_module("08-项目管理", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("08-项目管理", KEY_RBAC,
                    roles=["admin", "pm", "qa_mgr", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 8.")