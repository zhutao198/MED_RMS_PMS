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
# 修正路径：ComplianceController line 48 → /compliance/audit-logs/entity/{entityType}/{entityId}
r = requests.get(f"{BASE}/compliance/audit-logs/entity/TASK/{task_id_a}",
                 headers=adm_hdr, timeout=10)
if r.status_code == 200 and r.json().get("code") == 200:
    records = r.json().get("data") or []
    found = False
    for rec in records:
        rid = rec.get("entityId") or rec.get("entity_id")
        if str(rid) == str(task_id_a):
            found = True
            print(f"  audit_log: op={rec.get('operation')} entityType={rec.get('entityType')} entityId={rid}")
            break
    if found:
        ok("场景B+: 审计日志已写入 compliance_schema.t_audit_log")
    else:
        ng("场景B+: 审计日志未找到该 task 记录",
           f"查到 {len(records)} 条 TASK 记录；response={r.text[:300]}")
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

# === Cleanup: 软删本轮创建的数据（R222.2 用户拍板加 cleanup） ===
# 范围: 上面 req_id (step 3 创建) + task_id_a (step 4 创建)
# 原因: 后端 Requirement/Task 均无 DELETE 端点；R197 G16 触发器阻止物理删；最稳是 SQL 软删
#       物理保留数据不影响 21 CFR 审计链 + 防数据被恶意清空
import subprocess

def soft_delete_sql(ids_req, ids_task):
    # R222.3 修正：原 'Z-RES-' prefix 多次 e2e 跑累计会 UNIQUE 冲突 → ROLLBACK，连带 task 软删回滚！
    # 改用 suffix '-id{n}'（含 id 字段），每次都保证唯一，幂等
    sql = f"""
BEGIN;
UPDATE req_schema.t_requirement
   SET requirement_no = SPLIT_PART(requirement_no, '-id', 1) || '-id' || id,
       is_deleted = TRUE, status = 'Closed',
       title = title || ' [R222-e2e]'
 WHERE id IN ({','.join(map(str, ids_req))}) AND is_deleted = FALSE;
UPDATE proj_schema.t_task
   SET is_deleted = TRUE
 WHERE id IN ({','.join(map(str, ids_task))}) AND is_deleted = FALSE;
COMMIT;
"""
    env = os.environ.copy()
    env["PGPASSWORD"] = "postgres"
    r = subprocess.run(
        ["psql", "-h", "localhost", "-U", "postgres", "-d", "med_rms_pms", "-p", "5432"],
        input=sql, capture_output=True, text=True, env=env, timeout=15
    )
    out = (r.stdout or "") + (r.stderr or "")
    return r.returncode == 0, out


try:
    # 收集本轮创建的所有 id
    created_req_ids = [req_id]
    created_task_ids = [task_id_a]
    ok_clean, out = soft_delete_sql(created_req_ids, created_task_ids)
    if ok_clean and "ERROR" not in out.upper():
        ok(f"Cleanup: 软删 req_ids={created_req_ids} task_ids={created_task_ids}")
    else:
        ng("Cleanup: SQL 执行失败", out[-500:])
except Exception as e:
    ng("Cleanup: 异常", str(e)[:300])

# === 汇总 ===
print(f"\n=== R222 e2e 汇总 ===  PASS={PASS} FAIL={FAIL}")
sys.exit(0 if FAIL == 0 else 1)
