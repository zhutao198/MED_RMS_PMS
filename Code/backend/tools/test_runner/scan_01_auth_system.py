#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 1: 认证与系统 - 批量 API 扫描"""
import sys, os, json
sys.path.insert(0, os.path.dirname(__file__))
from common import login, http_request, check_endpoint, summary, print_summary
from api_scan import scan_module, cross_role_test

# 认证与系统模块端点（基于 Phase 1 探索结果）
# R114 修复：移除 /auth/logout 和 /auth/refresh，避免 token 被加入黑名单后影响后续测试
ENDPOINTS = [
    # 认证 (3)
    ("POST", "/auth/login"),  # 无 token
    ("POST", "/auth/refresh"),
    ("GET",  "/auth/me"),
    # 用户管理 (6)
    ("GET",  "/system/users"),
    ("GET",  "/system/users/1"),
    ("GET",  "/system/roles"),
    ("GET",  "/system/permissions"),
    ("GET",  "/system/roles/1/permissions"),
    # 字典 (4)
    ("GET",  "/system/dicts?type=priority"),
    ("GET",  "/system/dicts/all"),
    ("GET",  "/system/dicts?type=status"),
    ("GET",  "/system/dicts?type=category"),
    # 部门 (3)
    ("GET",  "/system/departments?parentId=0"),
    ("GET",  "/system/departments/tree"),
    ("GET",  "/system/org/tree"),
    # 配置 (2)
    ("GET",  "/system/configs"),
    # 仪表盘 (4)
    ("GET",  "/dashboard/view/requirements"),
    ("GET",  "/dashboard/view/risk"),
    ("GET",  "/dashboard/view/compliance"),
    ("GET",  "/dashboard/view/management"),
    # 通知 (3)
    ("GET",  "/notifications/unread?userId=1"),
    ("GET",  "/notifications/unread/count?userId=1"),
    ("GET",  "/notifications/all?userId=1"),
    # 审计日志 (3)
    ("GET",  "/compliance/audit-logs?page=0&size=10"),
    ("GET",  "/compliance/audit-logs/verify/detailed"),
    ("GET",  "/compliance/audit-logs/operator/1"),
]

# 关键 RBAC 端点（用于跨角色测试）
KEY_RBAC = [
    ("GET", "/system/users"),       # admin 可，viewer 不可
    ("GET", "/system/roles"),       # admin 可
    ("POST", "/system/users"),      # 仅 admin
    ("GET", "/dashboard/view/risk"),# admin 可
    ("GET", "/compliance/audit-logs?page=0&size=10"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/01-认证与系统"
    out = scan_module("01-认证与系统", ENDPOINTS, role="admin",
                       output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross = cross_role_test("01-认证与系统", KEY_RBAC,
                             roles=["admin", "qa_mgr", "viewer"],
                             output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("\n[DONE] Module 1 scan completed.")