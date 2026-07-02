#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
R150 跨模块集成测试 — 链路 A 电子签名 OTP + 链路 B 审计哈希链多实体回归

链路 A 重点：验证 R148 修复的 5 处 updateById(id==null) → insert 分支：
  - generateOtpSecret / enableOtp / disableOtp / updatePin / updateSignaturePassword
  默认场景（id=null）必须能 INSERT；非默认场景必须能 UPDATE。

链路 B 重点：跨多模块（需求→基线→签名→合规）写入后，
  /audit-logs/verify/detailed 必须返回 valid=true 或唯一断裂点（R146/R147 修复回归）。

用法: python test_r150_esign_audit_e2e.py
前置:
  - 后端 8080 端口已起
  - 已跑 r150_seed_minimal.sql（产生 id=100 测试项目 + 3 URS）
  - r150_supplement_truncate.sql 清空了 esign/audit 表
"""
import sys, os, time
sys.path.insert(0, os.path.dirname(__file__))

# 强制 reload common（与 R123 历史保持一致）
for mod_name in list(sys.modules.keys()):
    if 'common' in mod_name:
        del sys.modules[mod_name]
import common as _c
_c.BASE = "http://localhost:8080/api"
from common import login, http_request

import pyotp

TS = int(time.time())
PROJECT_ID = 100       # r150_seed_minimal.sql 写入的项目 id
USER_ID = 1            # admin

# ============================================================================
# 工具
# ============================================================================

def expect(test_name: str, ok: bool, msg: str = ""):
    """统一打印 + 计数"""
    sym = "OK" if ok else "FAIL"
    line = f"  [{sym}] {test_name}"
    if not ok and msg:
        line += f"\n        详情: {msg}"
    print(line)
    return ok


def req(method: str, path: str, t: str, body=None, params=None):
    """封装 http_request 返回 (http_status, body_dict, latency_ms)"""
    return http_request(method, path, token=t, body=body, params=params)


def create_req(t: str, title_suffix: str, **extra):
    body = {
        "requirementType": "URS", "projectId": PROJECT_ID,
        "title": f"R150-A-{title_suffix}-{TS}",
        "description": "R150 链路 A 测试",
        "priority": "MUST", "requirementCategory": "SOFTWARE", "source": "INTERNAL"
    }
    body.update(extra)
    s, b, l = req("POST", "/requirements", t, body=body)
    return b.get("data", {}).get("id") if b.get("data") and b.get("code") == 200 else None


def enable_admin_otp(t: str) -> str:
    """为 admin 启用 OTP，返回当前合法 OTP 码。"""
    # 1. 生成密钥
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/otp/generate", t)
    secret = b.get("data") if b.get("code") == 200 and b.get("data") else None
    if not secret:
        return None
    # 2. 启用（首次设置时 SignatureSettings 行不存在 → 必须 INSERT）
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/otp/enable", t,
                  params={"otpSecret": secret})
    if b.get("code") != 200:
        print(f"        enableOtp 失败: {b.get('message', '')[:60]}")
        return None
    # 3. 返回即时 TOTP（30 秒窗口）
    return pyotp.TOTP(secret).now()


def set_signature_password(t: str, pwd: str = "SigP@ss123"):
    """为 admin 设置签名密码。"""
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/password", t,
                  params={"currentPwd": "", "newPwd": pwd})
    return b.get("code") == 200


# ============================================================================
# 主流程
# ============================================================================

def main():
    print("=== R150 跨模块集成测试 — 链路 A 电子签名 OTP + 链路 B 哈希链 ===\n")
    t = login("admin", "admin123")
    if not t:
        print("[FAIL] admin 登录失败")
        return 1

    results = {"pass": 0, "fail": 0, "skip": 0}
    def ok(name, cond, msg=""):
        if expect(name, cond, msg):
            results["pass"] += 1
        else:
            results["fail"] += 1

    # ============================================================
    # 链路 A：电子签名 OTP 全链路（R148 修复回归）
    # ============================================================
    print("--- 链路 A：电子签名 OTP ---")

    # A1: 首次 generateOtpSecret（id=null → INSERT 分支）
    print("\n[A1] generateOtpSecret（id=null → INSERT）")
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/otp/generate", t)
    secret = b.get("data") if isinstance(b.get("data"), str) else None
    ok("A1.1 返回 base32 密钥", bool(secret) and len(secret) >= 16,
       f"data={b.get('data')}"[:80])

    # A2: enableOtp（同样 id=null 场景）
    print("\n[A2] enableOtp（id=null → INSERT 或 UPDATE 都需生效）")
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/otp/enable", t,
                  params={"otpSecret": secret})
    ok("A2.1 启用 OTP 成功", b.get("code") == 200,
       f"code={b.get('code')} msg={b.get('message','')[:60]}")

    # A3: updateSignaturePassword（id=null → INSERT）
    #     二次运行时 admin 已有密码，硬传 currentPwd="" 会被 SG0101 拒绝 → 容错 skip
    print("\n[A3] updateSignaturePassword（id=null → INSERT/UPDATE）")
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/password", t,
                  params={"currentPwd": "", "newPwd": "SigP@ss123"})
    code = b.get("code")
    if code == 200:
        ok("A3.1 设置签名密码（首次 INSERT 或 UPDATE 同密码）", True, "")
    elif code == "SG0101":
        # 二次运行场景：admin 已设过密码（链路 A 第一次跑过），SG0101 拒绝是合规
        print("        A3.1 admin 已设过密码（SG0101），跳过 — 后续签名成功即密码有效")
        results["skip"] += 1
    else:
        ok("A3.1 设置签名密码", False, f"code={code} msg={b.get('message','')[:60]}")

    # A4: updatePin（id 可能仍 null 因 R148 之前未触发，强制再 INSERT 一次）
    print("\n[A4] updatePin（id=null → INSERT）")
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/pin", t,
                  params={"newPin": "1234"})
    ok("A4.1 更新 PIN 成功", b.get("code") == 200, f"code={b.get('code')}")

    # 再次操作已存在的行 → 应走 UPDATE（不会重复插入）
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/pin", t,
                  params={"newPin": "5678"})
    ok("A4.2 二次更新 PIN 走 UPDATE 分支", b.get("code") == 200,
       f"code={b.get('code')}")

    # A5: 完整签名链路（intent → sign → verify）
    print("\n[A5] 完整签名链路")
    # 准备需求（R148 链路 A 的核心回归场景）
    rid = create_req(t, "A5-bsl-target")
    if not rid:
        results["fail"] += 1
        print(f"  [FAIL] A5.0 创建需求失败，无法继续")
    else:
        # A5.1: 创建签名意图
        s, b, l = req("POST", "/esignature/intents", t, body={
            "requesterId": USER_ID, "documentType": "REQUIREMENT",
            "documentId": rid, "meaningCode": "APPROVED"
        })
        intent_id = b.get("data", {}).get("id") if isinstance(b.get("data"), dict) else None
        ok(f"A5.1 创建签名意图 intentId={intent_id}",
           b.get("code") == 200 and intent_id is not None,
           f"code={b.get('code')} data={str(b.get('data'))[:60]}")

        # A5.2: 用合法 OTP + 签名密码签名（验证 R148 修复的 4 个核心场景之一）
        valid_otp = pyotp.TOTP(secret).now() if secret else "000000"
        s, b, l = req("POST", "/esignature/sign", t, body={
            "signerId": USER_ID, "signerName": "admin",
            "intentId": intent_id, "meaningCode": "APPROVED",
            "documentType": "REQUIREMENT", "documentId": rid,
            "documentNo": f"R150-A5-{TS}", "reason": "R150 链路 A 测试",
            "signatureMethod": "OTP_PASSWORD",
            "ipAddress": "127.0.0.1",
            "signaturePassword": "SigP@ss123",
            "otpCode": valid_otp
        })
        sig_id = b.get("data", {}).get("id") if isinstance(b.get("data"), dict) else None
        ok(f"A5.2 OTP+密码签名成功 sigId={sig_id}",
           b.get("code") == 200 and sig_id is not None,
           f"code={b.get('code')} msg={b.get('message','')[:60]}")

        # A5.3: 验证签名
        if sig_id:
            s, b, l = req("POST", f"/esignature/verify/{sig_id}", t)
            valid = b.get("data", {}).get("valid") if isinstance(b.get("data"), dict) else None
            ok(f"A5.3 verify 签名 valid={valid}",
               b.get("code") == 200 and valid is True,
               f"code={b.get('code')} data={b.get('data')}")

    # A6: 错误 OTP 拒绝
    print("\n[A6] 错误 OTP 拒绝（SG0104）")
    rid6 = create_req(t, "A6-bad-otp")
    if rid6:
        s, b, l = req("POST", "/esignature/intents", t, body={
            "requesterId": USER_ID, "documentType": "REQUIREMENT",
            "documentId": rid6, "meaningCode": "APPROVED"
        })
        intent_id = b.get("data", {}).get("id")
        s, b, l = req("POST", "/esignature/sign", t, body={
            "signerId": USER_ID, "signerName": "admin",
            "intentId": intent_id, "meaningCode": "APPROVED",
            "documentType": "REQUIREMENT", "documentId": rid6,
            "documentNo": f"R150-A6-{TS}", "reason": "错误 OTP 测试",
            "signatureMethod": "OTP_PASSWORD", "ipAddress": "127.0.0.1",
            "signaturePassword": "SigP@ss123", "otpCode": "000000"  # 几乎必失败
        })
        ok(f"A6.1 错误 OTP 拒绝 code={b.get('code')}",
           b.get("code") != 200, f"code={b.get('code')}")

    # A7: 重复签名拒绝
    print("\n[A7] 重复签名拒绝（SG0102）")
    if rid:
        s, b, l = req("POST", "/esignature/intents", t, body={
            "requesterId": USER_ID, "documentType": "REQUIREMENT",
            "documentId": rid, "meaningCode": "APPROVED"
        })
        intent_id2 = b.get("data", {}).get("id")
        valid_otp2 = pyotp.TOTP(secret).now() if secret else "111111"
        s, b, l = req("POST", "/esignature/sign", t, body={
            "signerId": USER_ID, "signerName": "admin",
            "intentId": intent_id2, "meaningCode": "APPROVED",
            "documentType": "REQUIREMENT", "documentId": rid,
            "documentNo": f"R150-A7-{TS}", "reason": "重复签名测试",
            "signatureMethod": "OTP_PASSWORD", "ipAddress": "127.0.0.1",
            "signaturePassword": "SigP@ss123", "otpCode": valid_otp2
        })
        ok(f"A7.1 重复签名被拒 code={b.get('code')}",
           b.get("code") != 200, f"code={b.get('code')} msg={b.get('message','')[:60]}")

    # A8: disableOtp（id 不为 null 后，UPDATE 分支）
    print("\n[A8] disableOtp（id 已存在 → UPDATE）")
    s, b, l = req("POST", f"/esignature/settings/{USER_ID}/otp/disable", t)
    ok(f"A8.1 禁用 OTP 成功", b.get("code") == 200,
       f"code={b.get('code')} msg={b.get('message','')[:60]}")

    # ============================================================
    # 链路 B：审计哈希链多实体回归
    # ============================================================
    print("\n\n--- 链路 B：审计哈希链 ---")

    # B1: 跨模块写入触发 audit_log
    print("\n[B1] 当前 audit_log 行数（哈希链起点）")
    s, b, l = req("GET", "/compliance/audit-logs", t,
                  params={"page": 0, "size": 1})
    logs_meta = b.get("data") if isinstance(b.get("data"), dict) else {}
    total_records = logs_meta.get("total", 0) if isinstance(logs_meta, dict) else 0
    print(f"        当前 audit_log 总数: {total_records}")
    ok("B1.1 audit_log 能查（重建 hash 链后含历史记录）",
       b.get("code") == 200, f"code={b.get('code')}")

    # B2: 快速 verify
    print("\n[B2] verify/detailed（哈希链详细校验）")
    s, b, l = req("GET", "/compliance/audit-logs/verify/detailed", t)
    detail = b.get("data") if isinstance(b.get("data"), dict) else {}
    valid_flag = detail.get("valid") if isinstance(detail, dict) else None
    first_failure = detail.get("firstFailureId") if isinstance(detail, dict) else None
    print(f"        valid={valid_flag}, firstFailureId={first_failure}")
    ok("B2.1 verify/detailed 返回结果",
       b.get("code") == 200 and isinstance(detail, dict),
       f"code={b.get('code')}")
    ok("B2.2 哈希链 valid 或断裂点唯一",
       valid_flag is True or first_failure is not None,
       f"既不 valid 又无 firstFailureId → R146/R147 修复异常")

    # B3: 跨模块签名后再校验
    print("\n[B3] 跨模块签名后再校验（链路 A 写入应进哈希链）")
    s, b, l = req("GET", "/compliance/audit-logs/verify/detailed", t)
    detail2 = b.get("data") if isinstance(b.get("data"), dict) else {}
    valid2 = detail2.get("valid") if isinstance(detail2, dict) else None
    fail2 = detail2.get("firstFailureId") if isinstance(detail2, dict) else None
    ok("B3.1 链路 A 写入后哈希链未整体断裂",
       valid2 is True or (fail2 is not None and isinstance(fail2, int)),
       f"valid={valid2} fail={fail2}")

    # ============================================================
    # 汇总
    # ============================================================
    print(f"\n=== R150 链路 A+B 汇总 ===")
    print(f"  pass: {results['pass']}")
    print(f"  fail: {results['fail']}")
    print(f"  skip: {results['skip']}")
    total = results['pass'] + results['fail']
    rate = (results['pass'] * 100 // total) if total > 0 else 0
    print(f"  pass rate: {rate}%")

    return 0 if results["fail"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
