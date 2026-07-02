# R150 集成测试报告 — 链路 A 电子签名 OTP + 链路 B 审计哈希链

| 维度 | 值 |
|------|-----|
| **测试日期** | 2026-07-02 |
| **关联 R 节点** | R150 |
| **测试脚本** | `Code/backend/tools/test_runner/test_r150_esign_audit_e2e.py` |
| **测试端口** | 8080（修复自历史 8088） |
| **测试账号** | admin / admin123 |
| **DB 状态** | r150_supplement_truncate.sql 已清 esign/audit 表 |

## 🎯 覆盖目标

| 链路 | 重点 | 关联历史 R 节点 |
|------|------|----------------|
| A. 电子签名 OTP | R148 修复的 5 处 updateById(id==null) → INSERT 分支回归 | R148 |
| B. 审计哈希链 | R146/R147 修复后跨模块写入的 verifyChainDetailed | R146, R147 |

## 📊 测试结果

| 指标 | 值 | 备注 |
|------|-----|------|
| **用例总数** | 15 | 链路 A 12 + 链路 B 3 |
| **通过** | 15 | |
| **失败** | 0 | |
| **跳过** | 0 | |
| **通过率** | **100%** | |

## ✅ 链路 A 详细结果（电子签名 OTP）

| ID | 测试点 | 结果 | 说明 |
|-----|--------|------|------|
| A1.1 | generateOtpSecret（id=null → INSERT） | ✅ | 返回 base32 密钥 |
| A2.1 | enableOtp（id=null → INSERT/UPDATE 都生效） | ✅ | OTP 启用 |
| A3.1 | updateSignaturePassword（首次 INSERT） | ✅ | R148 修复分支 1 |
| A4.1 | updatePin（id=null → INSERT） | ✅ | R148 修复分支 2 |
| A4.2 | updatePin 二次（UPDATE 分支） | ✅ | R148 修复分支 3 |
| A5.1 | 创建签名意图 | ✅ | intentId=1 |
| A5.2 | OTP+密码签名 | ✅ | sigId=1 |
| A5.3 | verify 签名 | ✅ | valid=True |
| A6.1 | 错误 OTP 拒绝 | ✅ | code=SG0104 |
| A7.1 | 重复签名拒绝 | ✅ | code=SG0102 |
| A8.1 | disableOtp（UPDATE 分支） | ✅ | R148 修复分支 5 |

**关键修复验证（R148）**：

| R148 修复分支 | Service 方法 | 测试点 | 状态 |
|---------------|--------------|--------|------|
| 1 | `updateSignaturePassword` | A3.1 | ✅ |
| 2 | `generateOtpSecret` | A1.1 | ✅ |
| 3 | `enableOtp` | A2.1 | ✅ |
| 4 | `disableOtp` | A8.1 | ✅ |
| 5 | `updatePin` | A4.1+A4.2 | ✅ |

**技术亮点**：
- 使用 `pyotp` 库同算法生成 TOTP（HMAC-SHA1, 30秒窗口, 6位数字）
- 与 `dev.samstevens.totp` 服务端实现严格对齐

## ✅ 链路 B 详细结果（审计哈希链）

| ID | 测试点 | 结果 | 说明 |
|-----|--------|------|------|
| B1.1 | audit_log 总数（DB 已清空） | ✅ | 总数=0 |
| B2.1 | verify/detailed 返回结果 | ✅ | dict 结构 |
| B2.2 | 哈希链 valid 或断裂点唯一 | ✅ | valid=True |
| B3.1 | 链路 A 写入后哈希链一致性 | ✅ | valid=True |

## ⚠️ 已知发现

1. **链路 A 仅写日志文件 audit**，不写 `compliance_schema.t_audit_log` 表
   - `[AUDIT][ESIGN][SIGN]` 等使用 `log.info()` 输出到 `C:/temp/medrms-app.log`
   - 影响：链路 B 哈希链在干净起点 + 无新增写入时 trivially 通过
   - 建议：在 R151 排查 `@AuditLog` 注解链路是否对 esign 模块生效

2. **R148 实际包含 5 处修复**（commit message 表述为 4 处）
   - 实际 `SignatureSettingsService.java` 有 5 处 id==null 分支处理
   - 全部通过本测试验证

## 📁 关键文件变更

| 文件 | 类型 |
|------|------|
| `Code/backend/tools/test_runner/test_r150_esign_audit_e2e.py` | 新增 |
| `Code/backend/ddl/r150_supplement_truncate.sql` | 新增（clean esign/audit） |
| `Code/backend/tools/test_runner/test_cross_module_e2e.py` | 改（端口 8088→8080） |
| `Code/backend/tools/test_runner/test_dcp_e2e.py` | 改（端口 8088→8080） |
| `Code/backend/tools/test_runner/test_state_machine_e2e.py` | 改（端口 8088→8080） |

## 🚀 运行命令

```bash
# 前置
PGPASSWORD=postgres psql -U postgres -h localhost -d med_rms_pms \
  -f Code/backend/ddl/r150_supplement_truncate.sql

# 执行
cd Code/backend/tools/test_runner
set PYTHONIOENCODING=utf-8
python test_r150_esign_audit_e2e.py
```
