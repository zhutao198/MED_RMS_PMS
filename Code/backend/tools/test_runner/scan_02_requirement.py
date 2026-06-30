#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 2: 需求管理 - 批量 API 扫描"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from common import login, http_request, check_endpoint, summary
from api_scan import scan_module, cross_role_test

# 需求管理端点
ENDPOINTS = [
    # 列表/详情
    ("GET", "/requirements?page=0&size=10"),
    ("GET", "/requirements/1"),
    ("GET", "/requirements/1/trace-count"),
    ("GET", "/requirements/1/test-case-count"),
    ("GET", "/requirements/stats"),
    ("GET", "/requirements/tree"),
    # 看板
    ("GET", "/requirements/kanban"),
    # 测试用例
    ("GET", "/testcases?page=0&size=10"),
    ("GET", "/testcases/project/1"),
    # 需求池
    ("GET", "/requirement-pool?page=0&size=10"),
    # 评审
    ("GET", "/requirements/1/reviews"),
    ("GET", "/requirements/1/versions"),
    # SOUP 组件（部分在 requirement 模块）
    ("GET", "/soup?page=0&size=10"),
    # 需求→任务转化（实际在 project 模块，但端点暴露）
    ("GET", "/project/requirements/1/tasks"),
    ("GET", "/project/requirements/list-convertible?projectId=1"),
]

KEY_RBAC = [
    ("GET", "/requirements?page=0&size=10"),
    ("GET", "/requirements/1"),
    ("GET", "/requirements/stats"),
    ("GET", "/testcases/project/1"),
    ("GET", "/requirement-pool?page=0&size=10"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/02-需求管理"
    out = scan_module("02-需求管理", ENDPOINTS, role="admin",
                       output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross = cross_role_test("02-需求管理", KEY_RBAC,
                             roles=["admin", "qa_mgr", "re", "viewer"],
                             output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("\n[DONE] Module 2 scan completed.")