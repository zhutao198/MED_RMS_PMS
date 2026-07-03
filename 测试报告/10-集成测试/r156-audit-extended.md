# R156 扩展验证报告 — 8 个 service 方法 @AuditLog 覆盖

| 维度 | 值 |
|------|-----|
| **测试日期** | 2026-07-03 |
| **关联 R 节点** | R155 (前置) + R156 (扩展) |
| **测试脚本** | `Code/backend/tools/test_runner/test_r156_audit_extended_e2e.py` |
| **测试端口** | 8080（R156 jar） |
| **覆盖范围** | EsignService (2) + RequirementService (6) = 8 个 service 方法 |

## 🎯 覆盖目标

| Service | 方法 | eventType | operation |
|---------|------|-----------|-----------|
| **EsignService** | `invalidateSignature` | INVALIDATE | 作废签名 |
| **EsignService** | `reSign` | RESIGN | 电子签名重签 |
| **RequirementService** | `approveRequirement` | APPROVE | 审批需求 |
| **RequirementService** | `startProgress` | STATUS_CHANGE | 开始实施需求 |
| **RequirementService** | `startTest` | STATUS_CHANGE | 开始测试需求 |
| **RequirementService** | `verifyRequirement` | VERIFY | 验证需求 |
| **RequirementService** | `withdrawRequirement` | STATUS_CHANGE | 撤回需求 |
| **RequirementService** | `markSuspect` | STATUS_CHANGE | 标记Suspect |

## 📊 测试结果

| 指标 | 值 |
|------|-----|
| **用例总数** | 11 |
| **通过** | 11 |
| **失败** | 0 |
| **跳过** | 0 |
| **通过率** | **100%** |

## 📈 audit_log event_type 分布（R156 跑完后）

| event_type | entity_type | count | R156 来源 |
|------------|-------------|-------|-----------|
| INVALIDATE | ELECTRONIC_SIGNATURE | 1 | ✅ EsignService.invalidateSignature |
| RESIGN | ELECTRONIC_SIGNATURE | 1 | ✅ EsignService.reSign |
| APPROVE | REQUIREMENT | 2 | ✅ RequirementService.approveRequirement |
| VERIFY | REQUIREMENT | 1 | ✅ RequirementService.verifyRequirement |
| STATUS_CHANGE | REQUIREMENT | 8 | ✅ startProgress / startTest / withdraw / markSuspect（+ R155 双签锁定）|
| REVIEW | REQUIREMENT | 1 | controller R146 既有（链路 B1 准备步骤）|
| SIGN | ELECTRONIC_SIGNATURE | 2 | ✅ R155 ElectronicSignatureService.sign |
| CREATE | REQUIREMENT | 5 | controller R146 既有 |
| (USER/LOGIN) | USER | 1 | 既有 |

## ✅ 关键成就

- **AOP 编织验证**：8 个新方法全部触发审计写入（之前链路 R155 已验证）
- **位置参数 `#p0` 复用 R155 修复**：所有 entityIdSpel 都是 `#p0` 即第 1 个参数
- **captureArgs=false**：EsignService 2 个方法加上，避免 reason 字段写入审计（合规边界）

## 🎯 R155+R156 累计覆盖矩阵

| 维度 | R155 | R156 | 总计 |
|------|------|------|------|
| 服务方法加 @AuditLog | 2 | 8 | **10** |
| event_type 类别 | 2（SIGN/STATUS_CHANGE） | 5（+INVALIDATE/RESIGN/APPROVE/VERIFY）| **7** |
| 合规事件覆盖 | 双签锁定 + sign | 全状态机 + 电子签名生命周期 | **完整链路** |

## 🚀 运行命令

```bash
# 前置：DB 重置 + 后端 R156 jar 已起
PGPASSWORD=postgres psql -U postgres -h localhost -d med_rms_pms \
  -f Code/backend/ddl/r150_supplement_truncate.sql

# 跑 R156
cd Code/backend/tools/test_runner
set PYTHONIOENCODING=utf-8
python test_r156_audit_extended_e2e.py
```
