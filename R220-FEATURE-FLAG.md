# R220 v1.76 — Feature Flag 合规功能屏蔽操作手册

> **创建时间**: 2026-07-24
> **状态**: ✅ **电子签名功能已永久下线**（R255 用户决策 2026-07-29 升级为正式架构决策）
> **影响范围**: 仅电子签名（签名意图、签名、设置写操作）
> **恢复方式**: ⚠️ **仅当用户明确指示恢复时**才执行（按 R255 硬约束）

---

## 📋 决策记录

| 项 | 决策 |
|----|------|
| 触发 | R220 用户要求"先把合规相关功能暂时屏蔽，包括签名功能" → R255 升级为"永久不下线" |
| 范围 | **A. 仅电子签名**（不含 DHF / IEC 62304 / 审计日志）|
| 实施方式 | **方案 1：Feature Flag 配置 + 代码层移除（按 R256 计划）** |
| 屏蔽环境 | **全部**（dev / staging / production）|
| 数据处理 | **保留**（不删除历史签名记录）|
| 前端提示 | 否（不再提示，避免暗示可恢复）|
| **R255 决策原文** | "合规相关签名先走线下流程，系统先不考虑电子签名。除非我明确恢复电子签名功能，否则一概不考虑。" |
| **R255 决策性质** | 永久架构决策（合规章节 21 CFR Part 11 §11.50/§11.70/§11.200 → 业务线下流程） |
| **代码层硬约束** | 后续 R 节点**禁止**新增/恢复电子签名相关代码路径（signatureId 校验、ESignPopup 集成、写端点恢复）|

---

## 🔧 当前配置

**文件**：`Code/backend/med-rms-web/src/main/resources/application.yml`

```yaml
# R220 v1.76: 合规功能 Feature Flag
# 当前禁用电子签名（用户决策）。恢复方式：改 true
compliance:
  modules:
    signature: false   # ← 当前禁用
```

---

## ✅ 影响范围（屏蔽后行为）

### 写端点 → 返 **503 SY0503**（业务异常）

| 端点 | 屏蔽前 | 屏蔽后 |
|------|--------|--------|
| `POST /esignature/intents` | ✅ 200 | ❌ 503 SY0503 |
| `POST /esignature/sign` | ✅ 200 | ❌ 503 SY0503 |
| `POST /esignature/intents/{id}/reissue` | ✅ 200 | ❌ 503 SY0503 |
| `POST /esignature/intents/{id}/cancel` | ✅ 200 | ❌ 503 SY0503 |
| `POST /esignature/{id}/invalidate` | ✅ 200 | ❌ 503 SY0503 |
| `POST /esignature/settings/{userId}/password` | ✅ 200 | ❌ 503 SY0503 |
| `POST /esignature/settings/{userId}/otp/enable` | ✅ 200 | ❌ 503 SY0503 |

### 读端点 → 仍正常（**不破坏数据可查性**）

| 端点 | 屏蔽后 |
|------|--------|
| `GET /esignature/intents` | ✅ 200（可查历史）|
| `GET /esignature/intents/{id}` | ✅ 200 |
| `GET /esignature/signatures` | ✅ 200 |
| `GET /esignature/entity/{type}/{id}` | ✅ 200 |
| `GET /esignature/settings/{userId}` | ✅ 200 |
| `GET /esignature/verify/{id}` | ✅ 200 |

### 错误响应格式（统一）

```json
{
  "code": "SY0503",
  "data": null,
  "message": "电子签名功能已临时禁用（R220）。请联系管理员启用 compliance.modules.signature 配置。",
  "timestamp": "..."
}
```

---

## 🔄 恢复方式

### 一行配置（推荐）

```bash
# 1. 编辑 application.yml
# 2. 修改 compliance.modules.signature: false → true
# 3. 重启 8080（或下次启动自动生效）
```

**恢复后**：
- 写端点恢复 200
- 历史签名记录立即可签
- 之前屏蔽期间的 PENDING intent 仍可继续（但已过期的需重新发起）

### 环境变量覆盖（可选）

```bash
# 不改 application.yml，直接环境变量启动
export COMPLIANCE_MODULES_SIGNATURE=true
mvn spring-boot:run
```

> 注意：需在 `@ConfigurationProperties` 中加 `@Value("${compliance.modules.signature:false}")` 支持。

---

## 📊 当前 e2e 测试状态（11 套 87/97）

| 套件 | 结果 | 说明 |
|------|------|------|
| R207 DHF PDF | 11/11 ✅ | 合规外 |
| R208 Excel 导入 | 8/8 ✅ | 合规外 |
| R208.2 ancestor 重建 | 5/5 ✅ | 合规外 |
| R209 eRPS PDF | 9/9 ✅ | 合规外 |
| R211 IPD 自动校验 | 13/13 ✅ | 合规外 |
| R212 多视角 UI | 10/10 ✅ | 合规外 |
| R213 法规推送 | 7/7 ✅ | 合规外 |
| R215 Dashboard 持久化 | 6/6 ✅ | 合规外 |
| **R217 签名密码** | 1/3 ⚠️ | 写端点预期 FAIL |
| **R218 过期过滤** | 1/3 ⚠️ | 写端点预期 FAIL |
| **R219 智能过期** | 1/2 ⚠️ | 写端点预期 FAIL |
| **R220 屏蔽验证** | 6/6 ✅ | 验证屏蔽正确生效 |

**恢复签名后预期**：R217/R218/R219 恢复 13/13 PASS

---

## 🔍 监控与审计

### 启动日志检查

```bash
# 启动后搜索
grep -i "R220\|compliance\|FeatureFlag" medrms-8080-*.log
# 应看到 R220 加载日志（如已添加）
```

### 调用失败统计

```bash
# 监控 SY0503 错误频率
grep "SY0503" medrms-8080-*.log | wc -l
# 持续高频率 = 业务阻塞严重，应评估恢复
```

### 历史数据完整性

```sql
-- 屏蔽期间数据完整性检查
SELECT status, COUNT(*) FROM esign_schema.t_signature_intent
WHERE created_at >= '2026-07-24'  -- R220 启动时间
GROUP BY status;
-- 应见 PENDING / CONSUMED（已签）/ EXPIRED 三种状态
-- 屏蔽期间不应有 CONSUMED（因为签名写端点被禁）
```

---

## ⏰ 评估与恢复计划

| 时间点 | 行动 |
|--------|------|
| **每 2 周** | 业务团队评估：是否需要恢复签名流程？ |
| **每 4 周** | 检查 R217/R218/R219 e2e 状态（恢复后是否 13/13）|
| **生产发布前** | **必须**恢复签名 + DHF 双签（合规硬要求）|
| **季度审计** | 评估 R220 屏蔽对 NMPA 申报的影响（如有延误）|

---

## ⚠️ 业务影响（屏蔽期间）

| 角色 | 影响 |
|------|------|
| **研发工程师 (RE)** | ❌ 不能对需求/变更签名（阻塞 DCP 流程）|
| **质量经理 (QA)** | ❌ 不能对基线双签（阻塞 Baseline 锁定）|
| **合规专员 (Compliance)** | ❌ 不能对 DHF 证据包签收（影响注册申报）|
| **项目经理 (PM)** | ⚠️ 变更请求可创建但无法完成签字（流程中断）|
| **系统管理员 (Admin)** | ✅ 仍可查所有历史数据（读端点正常）|

---

## 🛠️ 技术细节

### 实施文件

| 文件 | 改动 |
|------|------|
| `application.yml` | 加 `compliance.modules.signature: false` |
| `ComplianceFeatureConfig.java`（新）| `@ConfigurationProperties` 读取开关 |
| `FeatureGuard.java`（新）| `requireSignatureEnabled()` 抛 BusinessException |
| `ElectronicSignatureController.java` | 6 个写方法首行加 `featureGuard.requireSignatureEnabled()` |

### 关键决策记录

1. **错误码 SY0503**：复用 BusinessException 统一格式（不被 ResponseStatusException 漏掉）
2. **模块位置**：esignature 模块内独立 config（避免 web→esignature→web 循环依赖）
3. **读端点不阻塞**：保持数据可查性（用户引导 + 审计追溯）
4. **历史数据保留**：EXPIRED 状态正常出现（V1003 notified_*_at 字段填充）

### 相关 R 节点

- **R218 v1.74**：定时任务扫描标记过期 intent
- **R219 v1.75**：智能分级通知 + 重新发起
- **R220 v1.76**：Feature Flag 屏蔽（本节点）

---

## 📚 关联文档

- `开发日志.md` R220 节点框架（含详细实现）
- `CONTEXT.md` — 30 秒恢复指南
- `SESSION_SUMMARY.md` — Phase 6 会话总结
- `测试报告/` — 11 个 e2e 测试报告

---

## 🛑 R255 升级说明（2026-07-29）

R220 创建时定位为"临时措施"。R255 起升级为**正式架构决策**：

1. **不再计划恢复**：电子签名相关代码路径将逐步移除（不在 R255 范围内；后续 R256+ 按需清理）
2. **恢复条件**：仅当用户明确书面指示恢复时才执行（参考 R255 用户原话："除非我明确恢复电子签名功能，否则一概不考虑"）
3. **业务替代流程**：21 CFR Part 11 §11.50/§11.70/§11.200 要求的电子签名 → 业务团队线下完成（纸质签字 + 扫描存档），存档路径走非系统渠道
4. **审计追溯**：变更时间线保留 signatureId 字段（如历史记录），不强制新数据填充

---

**🛑 重要提醒**：电子签名功能**不再计划恢复**。R220 原本是**临时**措施，R255 起升级为**永久**架构决策。
