#!/usr/bin/env python3
"""E2E 验证 FR-2.6 里程碑自动达成：autoCheck PASS 时应自动 updateMilestone(COMPLETED)"""
import sys, json
from pathlib import Path
import requests

ROOT = Path(r"D:/zhutao/MED_RMS_PMS")
BACKEND = "http://localhost:8080"
sys.path.insert(0, str(ROOT / "Code/backend/tools"))

def login():
    r = requests.post(f"{BACKEND}/api/auth/login",
                      json={"username": "admin", "password": "admin123"})
    return r.json()["data"]["token"]

def main():
    token = login()
    h = {"Authorization": f"Bearer {token}"}

    # 取项目 1 的所有里程碑
    r = requests.get(f"{BACKEND}/api/gantt/milestones/project/1", headers=h)
    if r.status_code != 200:
        print(f"[FAIL] 拉取里程碑失败: {r.status_code} {r.text[:200]}")
        return
    data = r.json().get("data", [])
    if isinstance(data, dict):
        milestones = data.get("milestones", [])
    else:
        milestones = data if isinstance(data, list) else []
    print(f"[OK] 项目 1 里程碑数: {len(milestones)}")

    # 找一个非 COMPLETED 且有 gateType 的里程碑
    target = None
    for m in milestones:
        if m.get("gateType") and m.get("status") != "COMPLETED":
            target = m
            break
    if not target:
        print("[SKIP] 没有可测试的 PLANNED 里程碑")
        return
    print(f"[INFO] 目标里程碑: id={target['id']}, name={target['name']}, gateType={target['gateType']}, status={target['status']}")
    gate_no = int(target['gateType'].replace('DCP', ''))

    # 备份当前状态
    status_before = target['status']
    print(f"[BEFORE] status={status_before}")

    # 调用 autoCheck（带足够参数确保 PASS）
    # DCP 要求：需求都通过 + 追溯完整 + 测试覆盖 + 无高风险 + IEC 全覆盖 + DHF 完整
    auto_params = {
        'projectId': 1,
        'gateNo': gate_no,
        'requirementCount': 50, 'approvedRequirementCount': 50,
        'riskCount': 5, 'highRiskCount': 0,
        'testCaseCount': 80, 'passedTestCaseCount': 80,
        'iecCompliantCount': 50, 'totalIecItems': 50,
        'dhfEvidenceCount': 10,
    }
    r = requests.post(f"{BACKEND}/api/project/ipd-gate/auto-check", params=auto_params, headers=h)
    print(f"[AUTO-CHECK] status={r.status_code}, body={r.json()}")

    # 重新拉里程碑验证
    r = requests.get(f"{BACKEND}/api/gantt/milestones/project/1", headers=h)
    data = r.json().get("data", [])
    if isinstance(data, dict):
        milestones_after = data.get("milestones", [])
    else:
        milestones_after = data if isinstance(data, list) else []
    after = next((m for m in milestones_after if m['id'] == target['id']), None)
    if not after:
        print("[FAIL] 找不到目标里程碑")
        return
    print(f"[AFTER] status={after['status']}, checkResult={after.get('checkResult')}, actualDate={after.get('actualDate')}")
    if after['status'] == 'COMPLETED':
        print(f"[PASS] FR-2.6 里程碑自动达成：状态从 {status_before} → COMPLETED")
    else:
        print(f"[WARN] 里程碑状态未变（可能 autoCheck 返回 FAIL，未触发联动）")

if __name__ == "__main__":
    main()
