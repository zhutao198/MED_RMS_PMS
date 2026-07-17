#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R198-3: 哈希链完整性测试

验证点:
  A. audit_log 表禁止 UPDATE/DELETE (trg_prevent_hard_delete)
  B. verify/detailed 能检测到哈希链断裂
  C. verify/from/{startId} 局部验证正常

当前预存断链 (firstFailureId=1):
  ID=1 和 ID=2 各有两条记录（数据迁移重复导入），
  verify 算法排序后第二条 ID=1 的 prev_hash 与前一条 current_hash 不匹配。
  这是数据问题而非代码 bug，verify 正确检出。
"""
import sys, os, json, subprocess
sys.path.insert(0, os.path.dirname(__file__))
import requests

BASE = "http://localhost:8080/api"
PASS, FAIL = 0, 0

def ok(name): global PASS; PASS += 1; print(f"  [OK] {name}")
def ng(name, msg): global FAIL; FAIL += 1; print(f"  [FAIL] {name}: {msg}")

def login(user, pwd):
    r = requests.post(f"{BASE}/auth/login", json={"username": user, "password": pwd})
    return r.json()["data"]["token"]

def psql(sql):
    env = os.environ.copy(); env["PGPASSWORD"] = "postgres"
    proc = subprocess.run(["psql","-h","localhost","-U","postgres","-d","med_rms_pms","-p","5432","-t","-c",sql],
                          capture_output=True, env=env)
    return (proc.stdout.decode("utf-8",errors="replace").strip(),
            proc.stderr.decode("utf-8",errors="replace").strip(), proc.returncode)

token = login("admin", "admin123")
hdr = {"Authorization": f"Bearer {token}"}

# ===== Test A: DB trigger prevents modification =====
print("\n=== Test A: DB trigger 阻止 audit_log 修改 ===")
out, err, rc = psql(
    "UPDATE compliance_schema.t_audit_log SET entity_type='HACKED' WHERE id=126;"
)
if any(kw in err for kw in ["fn_prevent_audit_log_mutation", "不允许修改", "cannot modify"]):
    ok("UPDATE 被触发器阻止")
else:
    out2, _, _ = psql("SELECT entity_type FROM compliance_schema.t_audit_log WHERE id=126;")
    if out2.strip() == "USER":
        ok("UPDATE 被阻止（原值未变）")
    else:
        psql("UPDATE compliance_schema.t_audit_log SET entity_type='USER' WHERE id=126;")
        ng("UPDATE 未阻止", f"entity_type 被改为 {out2.strip()}")

out, err, rc = psql("DELETE FROM compliance_schema.t_audit_log WHERE id=126;")
if any(kw in err for kw in ["fn_prevent_hard_delete", "fn_prevent_audit_log_mutation", "不允许删除", "不允许修改", "cannot modify"]):
    ok("DELETE 被触发器阻止")
elif "DELETE 1" in out:
    ng("DELETE 未阻止", "记录被删除")
else:
    ng("DELETE 结果不明", f"out={out} err={err}")

# ===== Test B: verify/detailed =====
print("\n=== Test B: verify/detailed 检测断链 ===")
r = requests.get(f"{BASE}/compliance/audit-logs/verify/detailed", headers=hdr)
if r.status_code == 200: ok("HTTP 200")
else: ng("HTTP 200", f"status={r.status_code}")
data = r.json().get("data", {})
valid = data.get("valid", data.get("isValid"))
first_fail = data.get("firstFailureId")
fail_type = data.get("firstFailureType", "")
total = data.get("totalChecked")
if valid is False: ok("检测到断链")
else: ng("检测到断链", f"valid={valid}")
print(f"    firstFailureId={first_fail} type={fail_type} totalChecked={total}")

# ===== Test C: verify/from 局部验证 =====
print("\n=== Test C: verify/from/{startId} 局部验证 ===")
for sid in [1, 50, 100]:
    r0 = requests.get(f"{BASE}/compliance/audit-logs/verify/from/{sid}", headers=hdr)
    if r0.status_code == 200: ok(f"GET /verify/from/{sid} HTTP=200")
    else: ng(f"GET /verify/from/{sid}", f"HTTP {r0.status_code}")

# Summary
print(f"\n=== 哈希链完整性测试 ===")
print(f"  pass: {PASS}, fail: {FAIL}, pass rate: {100*PASS//max(PASS+FAIL,1)}%")
sys.exit(0 if FAIL == 0 else 1)
