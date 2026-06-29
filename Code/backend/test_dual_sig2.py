"""P0#6 双签锁定逻辑验证（v2）"""
import json
import time
import urllib.request
import urllib.parse
import subprocess

BASE = "http://localhost:8080/api"


def http(method, path, token=None, data=None, params=None):
    url = f"{BASE}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.getcode(), json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


def psql(sql):
    return subprocess.run(
        ["psql", "-h", "localhost", "-p", "5432", "-U", "postgres", "-d", "med_rms_pms", "-c", sql, "-t"],
        env={"PGPASSWORD": "postgres", "PATH": "C:\\Program Files\\PostgreSQL\\15\\bin;%PATH%"},
        capture_output=True, text=True
    ).stdout.strip()


def main():
    code, data = http("POST", "/auth/login", data={"username": "admin", "password": "admin123"})
    token = data["data"]["accessToken"]

    urs_id = 509

    # 创建 NORMAL 变更（不会签阻塞）
    t0 = int(time.time())
    code, data = http("POST", "/changes", token=token, data={
        "requirementId": urs_id, "changeType": "NORMAL",
        "reason": "P0#6 dual-sig", "urgency": "NORMAL",
        "requestedBy": 1, "title": f"DualSig2-{t0}"
    })
    chg_id = data["data"]["id"]
    print(f"[1] create NORMAL change id={chg_id}")

    http("POST", f"/changes/{chg_id}/submit", token=token)
    http("POST", f"/changes/{chg_id}/assess", token=token)

    # 通过 SQL 注入 riskLevel=CRITICAL + 推进到 APPROVED
    psql(f"UPDATE chg_schema.t_change_request SET risk_level='CRITICAL' WHERE id={chg_id};")
    # 第一次审批（写 ChangeApproval）
    http("POST", f"/changes/{chg_id}/approve", token=token, params={
        "approverId": 1, "decision": "APPROVED", "comments": "first"
    })
    # 强制 status=APPROVED（绕过可能的校验）
    psql(f"UPDATE chg_schema.t_change_request SET status='APPROVED' WHERE id={chg_id};")

    # 验证当前 ChangeApproval 数量
    cnt = psql(f"SELECT COUNT(DISTINCT approver_id) FROM chg_schema.t_change_approval WHERE change_id={chg_id};")
    print(f"[2] unique approvers before execute: {cnt}")

    # 尝试执行 - 应被 P0#6 双签锁定拦截
    code, data = http("POST", f"/changes/{chg_id}/execute", token=token, data={})
    print(f"[3] execute: {data}")
    if "必须由 ≥2 个不同 approver" in str(data):
        print(f"  ✓ P0#6 双签锁定生效：CRITICAL 变更单签执行被拒")
    else:
        print(f"  触发失败：{data}")
        return

    # 注入第二个 approver 签署
    psql(f"INSERT INTO chg_schema.t_change_approval (change_id, approver_id, decision, comments, created_at) VALUES ({chg_id}, 2, 'APPROVE', 'second', NOW());")
    cnt2 = psql(f"SELECT COUNT(DISTINCT approver_id) FROM chg_schema.t_change_approval WHERE change_id={chg_id};")
    print(f"[4] unique approvers after injection: {cnt2}")

    # 再执行
    code, data = http("POST", f"/changes/{chg_id}/execute", token=token, data={})
    print(f"[5] execute after dual-sig: {data}")
    if data.get("code") == 200:
        print(f"  ✓ P0#6 双签后 CRITICAL 变更可执行")
    else:
        print(f"  双签后仍被拒：{data}")

    print("\n🎉 P0#6 双签锁定 e2e 验证完成")


if __name__ == "__main__":
    main()
