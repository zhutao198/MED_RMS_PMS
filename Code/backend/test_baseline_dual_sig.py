"""P0#6 Baseline 双签锁定验证"""
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
    print("[1] login OK")

    t0 = int(time.time())
    # 创建基线（现在应该是 DRAFT 状态）
    code, data = http("POST", "/baselines", token=token, data={
        "projectId": 1, "name": f"DualSig-BL-{t0}",
        "requirementIds": [509]
    })
    assert code == 200 and data.get("code") == 200, f"create failed: {data}"
    bl = data["data"]
    print(f"[2] create baseline id={bl['id']} status={bl['status']}")
    assert bl["status"] == "DRAFT", f"expected DRAFT, got {bl['status']}"
    print(f"  ✓ create 后 status=DRAFT（不再是 LOCKED）")

    # 验证 1：同一人双签应被拒
    code, data = http("POST", f"/baselines/{bl['id']}/lock", token=token, params={
        "user1Id": 1, "signatureId1": 1, "user2Id": 1, "signatureId2": 2
    })
    assert "user1Id 与 user2Id 必须不同" in str(data), f"got: {data}"
    print(f"[3] ✓ 同一人双签被拒（user1Id == user2Id）")

    # 验证 2：相同 signatureId 应被拒
    code, data = http("POST", f"/baselines/{bl['id']}/lock", token=token, params={
        "user1Id": 1, "signatureId1": 1, "user2Id": 2, "signatureId2": 1
    })
    assert "signatureId1 与 signatureId2 必须不同" in str(data), f"got: {data}"
    print(f"[4] ✓ 相同 signatureId 双签被拒")

    # 验证 3：缺少 signatureId 应被拒
    code, data = http("POST", f"/baselines/{bl['id']}/lock", token=token, params={
        "user1Id": 1, "signatureId1": 1, "user2Id": 2
    })
    assert "signatureId" in str(data) or "缺少" in str(data) or "SY0101" in str(data), f"got: {data}"
    print(f"[5] ✓ 缺少 signatureId 被拒（{data.get('code')}）")

    # 验证 4：合法双签应通过
    code, data = http("POST", f"/baselines/{bl['id']}/lock", token=token, params={
        "user1Id": 1, "signatureId1": 100, "user2Id": 2, "signatureId2": 200
    })
    assert code == 200 and data.get("code") == 200, f"got: {data}"
    bl_locked = data["data"]
    assert bl_locked["status"] == "LOCKED", f"expected LOCKED, got {bl_locked['status']}"
    assert bl_locked.get("lockUser1Id") == 1
    assert bl_locked.get("lockUser2Id") == 2
    assert bl_locked.get("lockSignatureId1") == 100
    assert bl_locked.get("lockSignatureId2") == 200
    print(f"[6] ✓ 合法双签后 status=LOCKED，4 个字段正确写入")

    # 验证 5：重复锁定已 LOCKED 的基线应被拒
    code, data = http("POST", f"/baselines/{bl['id']}/lock", token=token, params={
        "user1Id": 1, "signatureId1": 3, "user2Id": 2, "signatureId2": 4
    })
    assert "DRAFT" in str(data) or "stateConflict" in str(data) or "状态" in str(data), f"got: {data}"
    print(f"[7] ✓ 重复锁定已 LOCKED 基线被拒")

    print("\n🎉 P0#6 Baseline 双签锁定完整验证通过！")


if __name__ == "__main__":
    main()
