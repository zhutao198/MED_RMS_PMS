# 仪表盘数据完整性审计报告（2026-08-10）

> 审计对象：4 个视角（研发 / 风险 / 合规 / 管理）共 11 个数据指标
> 审计方式：前后端逐字段比对 + SQL 逻辑验证
> 审计结论：**10 项发现，其中 7 项数据错误，3 项字段不对齐**

---

## 0. 总结：用户提到的"需求完成率数据不对"已确认

| 编号 | 数据点 | 严重度 | 类型 |
|------|--------|--------|------|
| **D-1** | **管理视角"需求完成率"实际是"测试用例覆盖率"，且数值永远为 0** | **P0** | 用户投诉 |
| D-2 | 管理视角"高风险项"未按 projectId 过滤 | P0 | 逻辑错误 |
| D-3 | `getTrends()` 用 `Math.random()` 生成数据 | **P0** | 数据伪造 |
| D-4 | 合规视角"签名覆盖率"算式无意义（全系统签名 / 单项目需求） | P0 | 算式错误 |
| D-5 | 合规视角"审计日志通过率"是二值化（100% 或 0%） | P1 | 算式错误 |
| D-6 | 合规视角字段路径全部错位（`complianceView.iec62304?.total` 等不存在） | P0 | 字段不对齐 |
| D-7 | `changeTrend` 是 mock 数据 `[3, 5, 2, 8, 6, 4, 7]` | P1 | 数据伪造 |
| D-8 | 合规视角`changes`、`problems` 数据后端未返回 | P1 | 字段缺失 |
| D-9 | 需求状态枚举大小写不一致（`"Draft"` vs `"DRAFT"`） | P1 | 命名不一致 |
| D-10 | 仪表盘数据完整性无单元测试 | P2 | 缺失 |

---

## 1. 用户投诉：D-1"需求完成率"严重错误

### 现象
管理视角"需求完成率"圆环图始终显示 **0%**，或显示非预期的数值。

### 根因（双重错误）

**前端逻辑错误**（`Dashboard.vue:555-560`）：
```javascript
// P1-27: 需求完成率（基于 coverage 覆盖数 / total）
const reqCompletionRate = computed(() => {
  const total = reqView.total || 0
  const covered = reqView.coverage?.covered || 0
  return total > 0 ? Math.round((covered / total) * 100) : 0
})
```

错误 1：**`reqView.coverage` 永远为 undefined** — `/dashboard/view/requirements` 端点不返回 `coverage` 字段。`coverage` 只在 `/dashboard/view/management` 端点。

错误 2：**"覆盖" ≠ "完成"** — 即便 `coverage.covered` 存在，它表示"被测试用例追溯的需求数"，不是"已完成的需求数"。

### 修复

完成率应基于状态计算：
```javascript
const reqCompletionRate = computed(() => {
  const total = reqView.total || 0
  if (total === 0) return 0
  const byStatus = reqView.byStatus || {}
  // "完成"语义：VERIFIED + DONE + CLOSED + RELEASED + APPROVED
  const completed =
    (byStatus['VERIFIED'] || 0) +
    (byStatus['DONE'] || 0) +
    (byStatus['CLOSED'] || 0) +
    (byStatus['RELEASED'] || 0) +
    (byStatus['APPROVED'] || 0)
  return Math.round((completed / total) * 100)
})
```

---

## 2. 关键 P0 bug：D-2 highRiskCount 未按 projectId 过滤

**证据**（`DashboardController.java:169-171`）：
```java
LambdaQueryWrapper<RiskAssessment> riskW = new LambdaQueryWrapper<>();
riskW.eq(RiskAssessment::getRiskLevel, "HIGH").eq(RiskAssessment::getIsDeleted, false);
long highRiskCount = riskAssessmentMapper.selectCount(riskW);
```

**问题**：当用户在项目下拉框选了具体项目，`projectId != null`，但这段查询**完全忽略 projectId**，导致管理视角的"高风险项"始终显示全系统数量。

**前后对比**：
| 场景 | 应该显示 | 实际显示 |
|------|---------|----------|
| 不选项目 | 全系统 HIGH 风险数 | ✓ 全系统 |
| 选了项目 A | 仅项目 A 下 HIGH 风险数 | ✗ 全系统（错误） |

**修复**：增加 `if (projectId != null) riskW.eq(RiskAssessment::getProjectId, projectId);`

---

## 3. P0 bug：D-3 `getTrends()` 用 `Math.random()` 生成数据

**证据**（`StatisticsService.java:208`）：
```java
point.put("value", 50L + (long) (Math.random() * 50));
```

**问题**：仪表盘的"趋势图"每次刷新都返回**随机数**，完全没有真实数据。

**后果**：
- 用户看到的"6 个月趋势"是**伪数据**
- 21 CFR Part 11 视角：合规性灾难——仪表盘提供随机数据作为决策依据
- 调试障碍：开发人员看到的是随机波动，无法定位真实问题

**修复**：用真实时序聚合（按月统计 `requirement_created` 数量）。

---

## 4. P0 bug：D-4 `signatureCoverage` 算式无意义

**证据**（`StatisticsService.java:155-159`）：
```java
long reqCount = requirementMapper.selectCount(
    new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId));
long sigCount = electronicSignatureMapper.selectCount(null);   // ⚠️ 全系统
stats.put("signatureCount", sigCount);
stats.put("signatureCoverage", reqCount > 0 ? Math.round((sigCount * 100.0 / reqCount) * 100.0) / 100.0 : 0);
```

**问题**：
1. `reqCount` 是当前项目，但 `sigCount` 是全系统——分子分母语义不匹配
2. `selectCount(null)` 没加 `is_deleted=false`，可能包含已软删除的签名
3. 即便匹配项目，"已签署的需求 / 全部需求" 才是合理的指标

**修复**：
- `sigCount` 也按 projectId 过滤
- 加 `is_deleted=false`
- 计数逻辑改为"该项目下有电子签名的需求数"而非"该项目的电子签名记录数"

---

## 5. P0 bug：D-6 合规视角字段路径全部错位

**前端**（`Dashboard.vue:261-345`）读取路径：
```vue
{{ complianceView.iec62304?.total || 0 }}
{{ complianceView.iec62304?.passCount || 0 }}
{{ complianceView.iec62304?.completionRate || 0 }}
{{ complianceView.iec62304?.mandatoryCount || 0 }}
{{ complianceView.iec62304?.completedCount || 0 }}
{{ complianceView.changes?.total || 0 }}
{{ complianceView.changes?.byStatus || {} }}
{{ complianceView.problems?.total || 0 }}
{{ complianceView.problems?.bySeverity || {} }}
```

**后端**（`StatisticsService.getComplianceStats`）实际返回结构：
```java
stats.put("iec62304Total", iecStats.get("total"));        // 顶层
stats.put("iec62304ComplianceRate", iecStats.get("complianceRate"));  // 顶层
stats.put("signatureCount", sigCount);                    // 顶层
stats.put("signatureCoverage", ...);                       // 顶层
stats.put("auditLogTotal", totalLogs);                    // 顶层
stats.put("auditLogPassRate", ...);                       // 顶层
stats.put("changeTotal", totalChanges);                   // 顶层
stats.put("changeAnalysisRate", ...);                     // 顶层
stats.put("soupTotal", totalSoups);                       // 顶层
stats.put("soupAssessmentRate", ...);                     // 顶层
```

**结果**：合规视角**所有字段**显示为 0，因为前端读 `complianceView.iec62304?.total`，但后端是 `complianceView.iec62304Total`（顶层）。

**修复**：要么后端嵌套返回 `iec62304: { total, complianceRate, ... }`，要么前端改读路径。建议**前端改读路径**（向后兼容，避免破坏现有 API 消费者）。

---

## 6. P1 bug：D-5 auditLogPassRate 二值化

**证据**（`StatisticsService.java:165-167`）：
```java
HashChainVerifyResult verifyResult = auditLogService.verifyHashChainDetailed();
totalLogs = verifyResult.getTotalChecked();
validLogs = verifyResult.isValid() ? totalLogs : 0;
```

**问题**：要么全通过（100%），要么全失败（0%）。99% 个体哈希正确但链路一处断裂也会显示 0%。

**修复**：逐条 log 校验 `calculateAuditHash`，计数 valid/total。

---

## 7. P1 bug：D-7 changeTrend 是 mock 数据

**证据**（`Dashboard.vue:563`）：
```javascript
// P1-27: 变更趋势（7 周，mock 数据；后端如能提供 change-trend 接口可替换）
// WHY: 暂用 mock 保证 UI 可视化，后续可接入 /dashboard/changes/trend
const changeTrend = ref<number[]>([3, 5, 2, 8, 6, 4, 7])
```

**问题**：注释自己都说了是 mock 数据。注释还说"后续可接入 /dashboard/changes/trend"，但后端从未实现。

**修复**：调用 `/dashboard/trends`（后端需实现 change-trend 子集）或调用现有 `/changes` 端点聚合计数。

---

## 8. P1 bug：D-8 changes/problems 数据未返回

**前端**读 `complianceView.changes?.total`、`complianceView.problems?.total`。

**后端** `getComplianceStats` 完全不返回这些字段。

**修复**：后端补 `changes: { total, byStatus }` 和 `problems: { total, bySeverity }`。

---

## 9. P1 bug：D-9 状态枚举大小写不一致

**`StatisticsService.java:73`**：`byStatus.merge(r.getStatus() == null ? "Draft" : r.getStatus(), 1L, Long::sum);`

**前端**：`reqView.byStatus?.['IN_PROGRESS']`、`reqView.byStatus?.['DRAFT']`

**问题**：如果 DB 里实际存的是 `"Draft"` / `"PendingReview"`（首字母大写），前端按大写 `IN_PROGRESS` 查找全部为 0。

**修复**：统一大小写（推荐**全大写**，与 `getChangeStats` 保持一致）。

---

## 10. P2 bug：D-10 无单元测试

`StatisticsService` 无单测覆盖聚合算式。当聚合逻辑变动时，无法保证 `highRiskCount` 等指标正确性。

---

## 修复优先级

| 优先级 | 项 | 工作量 |
|--------|-----|--------|
| **立即** | D-1（用户投诉） + D-6（合规视角全 0） | 1h |
| **立即** | D-2（highRiskCount） + D-3（Math.random） + D-4（signatureCoverage） | 2h |
| 一周内 | D-5 + D-7 + D-8 + D-9 | 4h |
| 发版前 | D-10 单测 | 8h |

---

*审计日期：2026-08-10 · 10 项发现（7 P0 + 3 P1） · 修复详情见后续提交*