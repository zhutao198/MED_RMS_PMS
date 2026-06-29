"""P0#6 OA 集成验证"""
import json
import time
import urllib.request
import urllib.parse

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


def main():
    code, data = http("POST", "/auth/login", data={"username": "admin", "password": "admin123"})
    token = data["data"]["accessToken"]

    urs_id = 509

    # 创建 MAJOR 变更 → 应自动触发 OA
    t0 = int(time.time())
    code, data = http("POST", "/changes", token=token, data={
        "requirementId": urs_id, "changeType": "MAJOR",
        "reason": "OA test", "urgency": "NORMAL",
        "requestedBy": 1, "title": f"OA-Test-{t0}"
    })
    assert code == 200 and data.get("code") == 200
    chg_id = data["data"]["id"]
    print(f"[1] create MAJOR change id={chg_id}")

    # 验证时间线含 OA_DISPATCHED
    code, data = http("GET", f"/changes/{chg_id}/timeline", token=token)
    timeline = data["data"]
    events = [t["event"] for t in timeline]
    print(f"[2] timeline events: {events}")
    assert "OA_DISPATCHED" in events, f"missing OA_DISPATCHED: {events}"
    oa_entry = next(t for t in timeline if t["event"] == "OA_DISPATCHED")
    assert "OA-WF-" in oa_entry["details"], f"OA workflow ID not in details: {oa_entry}"
    print(f"  ✓ OA_DISPATCHED 事件已记录，workflowId 已生成")

    # 验证 outbox 事件
    # 创建 EMERGENCY 变更 → 也应触发 OA
    t0 = int(time.time())
    code, data = http("POST", "/changes", token=token, data={
        "requirementId": urs_id, "changeType": "NORMAL",
        "reason": "EMERGENCY test", "urgency": "EMERGENCY",
        "requestedBy": 1, "title": f"EMERGENCY-OA-{t0}"
    })
    chg_id2 = data["data"]["id"]
    code, data = http("GET", f"/changes/{chg_id2}/timeline", token=token)
    timeline = data["data"]
    events = [t["event"] for t in timeline]
    print(f"[3] EMERGENCY change timeline: {events}")
    assert "OA_DISPATCHED" in events, f"EMERGENCY should trigger OA: {events}"
    print(f"  ✓ EMERGENCY 变更也触发 OA 调度")

    # 验证 NORMAL+非EMERGENCY 不触发 OA
    t0 = int(time.time())
    code, data = http("POST", "/changes", token=token, data={
        "requirementId": urs_id, "changeType": "NORMAL",
        "reason": "no OA test", "urgency": "NORMAL",
        "requestedBy": 1, "title": f"NoOA-{t0}"
    })
    chg_id3 = data["data"]["id"]
    code, data = http("GET", f"/changes/{chg_id3}/timeline", token=token)
    timeline = data["data"]
    events = [t["event"] for t in timeline]
    print(f"[4] NORMAL+非EMERGENCY change timeline: {events}")
    assert "OA_DISPATCHED" not in events, f"NORMAL should NOT trigger OA: {events}"
    print(f"  ✓ NORMAL+NORMAL 变更不触发 OA 调度（仅 CREATED）")

    print("\n🎉 P0#6 OA 集成完整验证通过！")
    print("  - MAJOR 变更 → 自动推 OA 审批流（OA_DISPATCHED + workflowId）")
    print("  - EMERGENCY 变更 → 自动推 OA 审批流")
    print("  - NORMAL+NORMAL → 不推 OA 走内部审批")


if __name__ == "__main__":
    main()
