#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""模块 5: 合规管理（最大模块 64 端点）"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from api_scan import scan_module, cross_role_test

ENDPOINTS = [
    # 合规检查
    ("GET", "/compliance/check/project/1"),
    # SOUP
    ("GET", "/soup?page=0&size=10"),
    ("GET", "/soup/1"),
    # IEC 62304
    ("GET", "/compliance/iec62304/checklist/1/stats"),
    # 基线 (R114 修复：实际端点是 /baselines/project/{id}，不是 /compliance/baselines)
    ("GET", "/baselines/project/1"),
    # 问题报告
    ("GET", "/compliance/problem-reports?status=Open&page=0&size=10"),
    ("GET", "/compliance/problem-reports/1"),
    # 审计日志
    ("GET", "/compliance/audit-logs?page=0&size=10"),
    ("GET", "/compliance/audit-logs/verify/detailed"),
    # DHF 证据
    ("GET", "/compliance/evidence/1"),
    # 法规
    ("GET", "/compliance/regulations"),
    # 安全分类
    ("GET", "/compliance/safety-classification?projectId=1"),
    # CAPA 纠正措施
    ("GET", "/compliance/capa?page=0&size=10"),
    # 报表配置
    ("GET", "/reports/configs"),
]
KEY_RBAC = [
    ("GET", "/compliance/audit-logs?page=0&size=10"),
    ("GET", "/compliance/problem-reports?status=Open"),
    ("GET", "/compliance/iec62304/checklist/1/stats"),
    ("GET", "/compliance/baselines"),
]


if __name__ == "__main__":
    OUT_DIR = "../../../../测试报告/05-合规管理"
    scan_module("05-合规管理", ENDPOINTS, role="admin",
                output_file=os.path.join(OUT_DIR, "api_scan_admin.json"))
    cross_role_test("05-合规管理", KEY_RBAC,
                    roles=["admin", "compliance", "qa_mgr", "viewer"],
                    output_file=os.path.join(OUT_DIR, "api_scan_rbac.json"))
    print("[DONE] Module 5.")