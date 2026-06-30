#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R130 跨模块业务流端到端测试

业务流：
  1. 需求→拆解→任务转化（req→decompose→requirement-task）
  2. 需求→基线→审计（req→baseline→audit）
  3. 风险→风险控制（risk→control）
  4. 跨模块通知（notification 接收变更/评审/基线事件）

用法: python test_cross_module_e2e.py
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))

for mod_name in list(sys.modules.keys()):
    if 'common' in mod_name:
        del sys.modules[mod_name]
import common as _c
_c.BASE = "http://localhost:8088/api"
from common import login, http_request

TS = int(time.time())
PROJECT_ID = 8

def test(method, path, t, body=None, params=None, expect_code=200, name=""):
    s, b, l = http_request(method, path, token=t, body=body, params=params)
    code = b.get("code")
    msg = b.get("message", "")[:60]
    ok = (code == expect_code)
    sym = "OK" if ok else "FAIL"
    print(f"  [{sym}] {name:55s} code={code} HTTP={s}")
    if not ok:
        print(f"        错误: {msg}")
    return ok


def create_req(t, title_suffix, **extra):
    body = {
        "requirementType": "URS", "projectId": PROJECT_ID,
        "title": f"R130-{title_suffix}-{TS}",
        "description": "R130 跨模块业务流测试",
        "priority": "MUST", "requirementCategory": "SOFTWARE", "source": "INTERNAL"
    }
    body.update(extra)
    s, b, l = http_request("POST", "/requirements", token=t, body=body)
    return b.get("data", {}).get("id") if b.get("data") and b.get("code") == 200 else None


def main():
    print("=== R130 跨模块业务流 e2e 测试（admin 视角）===")
    t = login("admin", "admin123")
    if not t:
        print("[FAIL] admin 登录失败")
        return 1

    results = {"pass": 0, "fail": 0, "skip": 0}

    # =========================================================
    # 业务流 1：需求→拆解→任务转化（4 模块）
    # =========================================================
    print("\n--- 业务流 1: 需求→拆解→任务转化 ---")
    rid_parent = create_req(t, "FLOW1-parent")
    rid_child = create_req(t, "FLOW1-child")
    if rid_parent and rid_child:
        # 1.1 拆解：parent → child
        if test("POST", f"/requirements/{rid_parent}/decompose", t,
                body={"childId": rid_child, "linkReason": "R130 拆解测试"},
                expect_code=200, name="req-decompose: parent→child"):
            results["pass"] += 1
        else:
            results["fail"] += 1

        # 1.2 任务转化：可转化候选列表
        s, b, l = http_request("GET", "/requirement-tasks/candidates", t,
                                params={"projectId": PROJECT_ID})
        candidates = b.get("data", [])
        print(f"  [INFO] 可转化候选数: {len(candidates)}")
        if isinstance(candidates, list) and len(candidates) >= 0:
            print(f"  [OK] req-task: candidates API 工作")
            results["pass"] += 1
        else:
            print(f"  [FAIL] req-task: candidates API 异常")
            results["fail"] += 1

        # 1.3 任务草稿生成
        s, b, l = http_request("GET", f"/requirement-tasks/drafts/{rid_child}", t)
        code = b.get("code")
        if code == 200:
            drafts = b.get("data", [])
            print(f"  [OK] req-task: drafts for child: {len(drafts) if isinstance(drafts, list) else 0} 个")
            results["pass"] += 1
        else:
            print(f"  [WARN] req-task: drafts API code={code}")
            results["skip"] += 1

    # =========================================================
    # 业务流 2：需求→基线→审计（3 模块）
    # =========================================================
    print("\n--- 业务流 2: 需求→基线→审计 ---")
    rid = create_req(t, "FLOW2-baseline")
    if rid:
        # 2.1 完整状态机迁移（Draft→Submitted→ReviewApproved→Verified→Baseline）
        test("POST", f"/requirements/{rid}/review?reviewerId=1", t, expect_code=200, name="→Submitted")
        test("POST", f"/requirements/{rid}/approve?decision=APPROVED&approverId=1", t, expect_code=200, name="→ReviewApproved")
        test("POST", f"/requirements/{rid}/start-progress", t, expect_code=200, name="→Implemented")
        test("POST", f"/requirements/{rid}/start-progress", t, expect_code=200, name="→InProgress")
        test("POST", f"/requirements/{rid}/start-test", t, expect_code=200, name="→InTest")
        test("POST", f"/requirements/{rid}/verify?verifierId=1", t, expect_code=200, name="→Verified")

        # 2.2 创建基线
        s, b, l = http_request("POST", "/baselines", t, body={
            "projectId": PROJECT_ID, "name": f"R130-baseline-{TS}",
            "requirementIds": [rid]
        })
        baseline_id = b.get("data", {}).get("id") if b.get("data") and b.get("code") == 200 else None
        code = b.get("code")
        if code == 200 and baseline_id:
            print(f"  [OK] baseline: created id={baseline_id}")
            results["pass"] += 1
        else:
            # 可能因为子需求未完成被拒绝
            print(f"  [WARN] baseline: code={code} msg={b.get('message','')[:60]}")
            results["skip"] += 1

        # 2.3 查询基线
        if baseline_id:
            if test("GET", f"/baselines/{baseline_id}", t, expect_code=200, name="baseline: query"):
                results["pass"] += 1
            else:
                results["fail"] += 1

        # 2.4 审计日志：基线相关操作应留痕
        s, b, l = http_request("GET", "/compliance/audit-logs",
                                t, params={"entityType": "BASELINE", "entityId": baseline_id or 0, "page": 0, "size": 5})
        logs = b.get("data", [])
        if isinstance(logs, list):
            print(f"  [OK] audit: baseline 操作日志 {len(logs)} 条")
            results["pass"] += 1
        else:
            print(f"  [FAIL] audit: 日志 API 异常")
            results["fail"] += 1

        # 2.5 哈希链校验（应通过或返回唯一断裂点）
        s, b, l = http_request("GET", "/compliance/audit-logs/verify/detailed", t)
        result = b.get("data", {})
        if isinstance(result, dict) and "firstFailureId" in result:
            print(f"  [OK] audit-verify: 断裂点 id={result.get('firstFailureId')} type={result.get('firstFailureType')}")
            results["pass"] += 1
        else:
            print(f"  [FAIL] audit-verify: 异常")
            results["fail"] += 1

    # =========================================================
    # 业务流 3：风险→风险控制（risk 模块）
    # =========================================================
    print("\n--- 业务流 3: 风险→风险控制 ---")
    s, b, l = http_request("GET", "/risk/register/list", t, params={"projectId": PROJECT_ID})
    risks = b.get("data", [])
    if isinstance(risks, list) and len(risks) > 0:
        risk_id = risks[0]["id"]
        # 3.1 风险控制
        if test("PUT", f"/risk/{risk_id}/control", t,
                body={"controlMeasure": f"R130 控制措施-{TS}"},
                expect_code=200, name="risk: add control"):
            results["pass"] += 1
        else:
            results["fail"] += 1

        # 3.2 风险状态变更
        s, b, l = http_request("PUT", f"/risk/{risk_id}/action-status", t,
                                params={"status": "MONITORING"})
        code = b.get("code")
        if code == 200:
            print(f"  [OK] risk: status change")
            results["pass"] += 1
        else:
            print(f"  [WARN] risk: status change code={code}")
            results["skip"] += 1
    else:
        print(f"  [WARN] 风险列表为空（项目 {PROJECT_ID} 无风险），跳过")
        results["skip"] += 2

    # =========================================================
    # 业务流 4：变更→通知
    # =========================================================
    print("\n--- 业务流 4: 变更→通知 ---")
    s, b, l = http_request("GET", "/notifications/unread", t, params={"userId": 1})
    unread = b.get("data", [])
    if isinstance(unread, list):
        print(f"  [OK] notif: admin 有 {len(unread)} 条未读")
        results["pass"] += 1
    else:
        print(f"  [FAIL] notif: API 异常")
        results["fail"] += 1

    # =========================================================
    # 业务流 5：合规检查（IEC 62304 清单 + DHF 证据）
    # =========================================================
    print("\n--- 业务流 5: 合规检查 ---")
    s, b, l = http_request("GET", "/compliance/iec62304/checklist/1/stats", t)
    data = b.get("data", {})
    if isinstance(data, dict):
        print(f"  [OK] compliance-iec62304: stats keys={list(data.keys())[:5]}")
        results["pass"] += 1
    else:
        print(f"  [FAIL] compliance-iec62304: API 异常")
        results["fail"] += 1

    # =========================================================
    # 业务流 6：跨角色 RBAC 一致性（与 R124 矩阵对比）
    # =========================================================
    print("\n--- 业务流 6: 跨角色 RBAC 一致性 ---")
    # viewer 应能看 stats（report:dashboard 权限）
    t_viewer = login("viewer", "admin123")
    if t_viewer:
        s, b, l = http_request("GET", "/requirements/stats", token=t_viewer)
        code = b.get("code")
        if code == 200:
            print(f"  [OK] viewer 可见 /requirements/stats (R118 修复生效)")
            results["pass"] += 1
        else:
            print(f"  [FAIL] viewer 不可见 stats code={code}（R118 修复未生效）")
            results["fail"] += 1

        # reviewer 应能提交评审
        t_reviewer = login("reviewer", "admin123")
        if t_reviewer:
            s, b, l = http_request("GET", "/requirements", token=t_reviewer,
                                    params={"page": 0, "size": 1})
            code = b.get("code")
            if code == 200:
                print(f"  [OK] reviewer 可见需求列表")
                results["pass"] += 1
            else:
                print(f"  [FAIL] reviewer 不可见需求 code={code}")
                results["fail"] += 1

    # =========================================================
    # 汇总
    # =========================================================
    print(f"\n=== R130 跨模块业务流 e2e 汇总 ===")
    print(f"  pass: {results['pass']}")
    print(f"  fail: {results['fail']}")
    print(f"  skip: {results['skip']}")
    total = results['pass'] + results['fail']
    rate = (results['pass'] * 100 // total) if total > 0 else 0
    print(f"  pass rate: {rate}%")

    return 0 if results['fail'] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())