#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R222 v1.78 任务负责人功能 e2e 测试
覆盖场景:
  1. 需求转化时设置负责人 → 写入 Task.assigneeId/Name
  2. 详情弹窗 PUT /gantt/tasks/{id} 改负责人 → 写入 + 校验
  3. PUT 传 -1L 清空负责人 → DB 写 null
  4. RBAC：VIEWER 无 proj:update → 期望 403

API 路径:
  POST /requirement-tasks/convert/{reqId}      写 taskDrafts 草稿
  GET  /requirement-tasks/by-requirement/{id}  查询需求下所有 Task
  PUT  /gantt/tasks/{id}                       更新 Task（assigneeId/Name + 审计 + RBAC）
  GET  /gantt/tasks/project/{projectId}       按项目查任务
  GET  /system/users                           用户列表（取 admin 用户 id）
"""
import sys, os, json, time
sys.path.insert(0, os.path.dirname(__file__))
import requests

BASE = "http://localhost:8080/api"
PASS, FAIL = 0, 0


def ok(name): global PASS; PASS += 1; print(f"  [OK] {name}")
def ng(name, msg=""): global FAIL; FAIL += 1; print(f"  [FAIL] {name}: {msg}")


def login(u, p):
    r = requests.post(f"{BASE}/auth/login", json={"username": u, "password": p}, timeout=10)
    return r.json()


# === 登录测试用户 ===
adm = login("admin", "admin123")
ADMIN_TOKEN = adm["data"]["token"]
adm_hdr = {"Authorization": f"Bearer {ADMIN_TOKEN}"}
print("Login: admin OK")

vw = login("viewer", "admin123")
VIEWER_TOKEN = vw["data"]["token"]
vw_hdr = {"Authorization": f"Bearer {VIEWER_TOKEN}"}
print("Login: viewer OK")

# === Step 1: 取一个现有项目（admin 已能看所有） ===
print(f"\n[Step 1] 取现有项目")
r = requests.get(f"{BASE}/projects", headers=adm_hdr, params={"page": 0, "size": 5}, timeout=10)
proj_list = r.json().get("data", {}).get("records") if isinstance(r.json().get("data"), dict) else r.json().get("data")
if not proj_list:
    ng("取项目", "无项目可用"); sys.exit(1)
proj_id = proj_list[0]["id"]
ok(f"取项目 id={proj_id}")

# === Step 2: 取 admin 用户 id（作为负责人基准） ===
print(f"\n[Step 2] 取 admin 用户 id")
r = requests.get(f"{BASE}/system/users", headers=adm_hdr, params={"page": 0, "size": 200}, timeout=10)
users = r.json().get("data", {}).get("records") if isinstance(r.json().get("data"), dict) else r.json().get("data")
admin_user = next((u for u in users if u.get("username") == "admin"), None)
re_user = next((u for u in users if u.get("username") == "re"), None)
if not admin_user or not re_user:
    ng("取用户", f"admin={bool(admin_user)} re={bool(re_user)}"); sys.exit(1)
ok(f"admin.id={admin_user['id']} re.id={re_user['id']}")

# === Step 3: 新建 SRS 需求 ===
print(f"\n[Step 3] 新建 SRS 需求")
ts = str(int(time.time()))
r = requests.post(f"{BASE}/requirements", json={
    "projectId": proj_id,
    "title": "R222-Test-Req-" + ts,
    "content": "R222 task assignee e2e",
    "requirementType": "SRS",
    "riskLevel": "MEDIUM",
    "safetyClass": "B",
    "source": "CUSTOMER",
    "priority": "MEDIUM",
    "status": "Draft"
}, headers=adm_hdr, timeout=10)
if r.status_code != 200 or r.json().get("code") != 200:
    ng("新建需求", r.text[:200]); sys.exit(1)
req_id = r.json()["data"]["id"]
ok(f"新建 SRS 需求 id={req_id}")

# === Step 4: 转化时设置负责人 admin.id ===
print(f"\n[Step 4] 转化任务 + 设置负责人 admin")
today = time.strftime("%Y-%m-%d")
end_dt = time.strftime("%Y-%m-%d", time.localtime(time.time() + 5 * 86400))
drafts = [{
    "title": "R222-Task-A-" + ts,
    "description": "R222 e2e task A",
    "startDate": today,
    "endDate": end_dt,
    "estimatedHours": 8,
    "priority": "MEDIUM",
    "assigneeId": admin_user["id"],
    "assigneeName": "admin (Admin)",
    "parentTaskId": None,
    "milestoneId": None,
}]
r = requests.post(f"{BASE}/requirement-tasks/convert/{req_id}", json=drafts,
                  headers=adm_hdr, timeout=10)
if r.status_code != 200 or r.json().get("code") != 200:
    ng("转化需求", r.text[:200]); sys.exit(1)
tasks = r.json()["data"]
task_id_a = tasks[0]["id"]
saved_assignee_id = tasks[0].get("assigneeId")
saved_assignee_name = tasks[0].get("assigneeName")
if saved_assignee_id != admin_user["id"]:
    ng("场景A: 转化时写入 assigneeId",
       f"期望={admin_user['id']} 实际={saved_assignee_id}")
else:
    ok(f"场景A: 转化时写入 assigneeId={saved_assignee_id} name={saved_assignee_name!r}")

# === Step 5: 场景B - 详情弹窗 PUT /gantt/tasks/{id} 改负责人为 re ===
print(f"\n[Step 5] 场景B: 详情弹窗改负责人")
payload = {"assigneeId": re_user["id"], "assigneeName": "re (RE)"}
r = requests.put(f"{BASE}/gantt/tasks/{task_id_a}", json=payload, headers=adm_hdr, timeout=10)
if r.status_code != 200 or r.json().get("code") != 200:
    ng("PUT 改负责人", r.text[:200])
else:
    after = r.json()["data"]
    if after.get("assigneeId") == re_user["id"]:
        ok(f"场景B: PUT 后 assigneeId={after['assigneeId']} name={after.get('assigneeName')!r}")
    else:
        ng("场景B: PUT 后 assigneeId 不匹配", f"实际={after.get('assigneeId')}")

# === Step 5.5: 验证审计日志（@AuditLog 持久化） ===
print(f"\n[Step 5.5] 验证审计日志写入")
r = requests.get(f"{BASE}/audit/logs", headers=adm_hdr,
                 params={"entityType": "TASK", "page": 0, "size": 5}, timeout=10)
if r.status_code == 200:
    audit_data = r.json().get("data", {})
    records = audit_data.get("records") if isinstance(audit_data, dict) else audit_data
    found = False
    for rec in (records or []):
        if rec.get("entityId") == task_id_a or str(rec.get("entityId")) == str(task_id_a):
            found = True
            print(f"  audit_log: {rec.get('operation')} entityId={rec.get('entityId')} ts={rec.get('createdAt')}")
            break
    if found:
        ok("场景B+: 审计日志已写入 compliance_schema.t_audit_log")
    else:
        ng("场景B+: 审计日志未找到对应记录", f"查询响应={r.text[:200]}")
else:
    ng("场景B+: 拉审计日志失败", f"status={r.status_code} body={r.text[:200]}")

# === Step 6: 场景C - 传 -1L 清空负责人 ===
print(f"\n[Step 6] 场景C: 传 -1L 清空负责人")
r = requests.put(f"{BASE}/gantt/tasks/{task_id_a}", json={"assigneeId": -1, "assigneeName": None},
                  headers=adm_hdr, timeout=10)
if r.status_code != 200 or r.json().get("code") != 200:
    ng("PUT 清空负责人", r.text[:200])
else:
    after = r.json()["data"]
    if after.get("assigneeId") is None and after.get("assigneeName") is None:
        ok("场景C: 传 -1L 后 DB 写 null（清空成功）")
    else:
        ng("场景C: 清空失败", f"assigneeId={after.get('assigneeId')} name={after.get('assigneeName')}")

# === Step 7: 场景D - VIEWER 无权限 ===
print(f"\n[Step 7] 场景D: VIEWER 无 proj:update 权限（RBAC 验证）")
r = requests.put(f"{BASE}/gantt/tasks/{task_id_a}",
                  json={"assigneeId": admin_user["id"]}, headers=vw_hdr, timeout=10)
if r.status_code == 403 or r.json().get("code") == 403:
    ok(f"场景D: VIEWER 被拒 status={r.status_code}")
else:
    ng("场景D: VIEWER 应被拒", f"实际 status={r.status_code} body={r.text[:150]}")

# === 汇总 ===
print(f"\n=== R222 e2e 汇总 ===  PASS={PASS} FAIL={FAIL}")
sys.exit(0 if FAIL == 0 else 1)
