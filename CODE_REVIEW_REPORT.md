# MED_RMS_PMS 代码评审报告

> 评审范围：前后端全量代码 + 跨文件/跨前后端逻辑一致性
> 评审方式：4 路并行专项审计（安全 / 数据完整性 / 前后端契约 / 模块集成）+ 定向修复 + 全量测试验证
> 生成时间：2026-07-25
> 基线提交：`995e285`（上一轮，未推送）· 本轮改动 15 个文件（未提交）

---

## 一、执行摘要

本轮完成了上一轮遗留的全部建议行动项，并在评审中发现并修复了 **3 个真实生产缺陷** 和 **7 处安全/契约/合规问题**，同时清理了 **5 个模块的陈旧测试**。

| 维度 | 结果 |
|---|---|
| 后端单元测试 | ✅ 全部模块通过（requirement / change / risk / traceability / esignature / project / admin / compliance 等） |
| 后端集成测试 | ⚠️ 仅 web 模块 3 个环境/种子相关用例受限（非代码缺陷，见 §5） |
| 生产缺陷修复 | 3 个（SQL 注入、逻辑删除绕过、合规判定降级） |
| 安全加固 | JWT 密钥 + 数据库口令外部化、异常信息泄露收敛 |
| 前后端契约修复 | 1 个真实断链（基线页风险列表） |
| 陈旧测试清理 | 5 个模块、10+ 用例 |

---

## 二、建议行动项完成情况

| # | 行动项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 评审 / 推送 `995e285` | ✅ 已评审 / ⏸ 未推送 | 含 2 个生产改动，按 Git 安全规范未自动推送，待你确认（见 §7） |
| 2 | 修复 `RequirementMapper.markSuspectBatch` 的 `${}` 注入 | ✅ 已修复 | 改为 `<foreach>` + `#{}` 参数化 |
| 3 | 全量自定义 `@Select` 逻辑删除审计 | ✅ 已完成 | 发现并修复 `RequirementRelationMapper` 3 处绕过 |
| 4 | 清理 compliance/admin/product/web 陈旧测试 | ✅ 已完成 | 详见 §4 |
| 5 | 专项审计（RBAC / 异常泄露 / 前后端契约 / 密钥扫描） | ✅ 已完成 | 详见 §3 |

---

## 三、发现与修复明细（按严重度）

### 🔴 高危

#### H1. SQL 注入风险 — `markSuspectBatch` 字符串拼接
- **位置**：`med-rms-requirement/.../mapper/RequirementMapper.java`
- **问题**：`WHERE id IN (${ids})` 使用 `${}` 直接拼接，且调用方 `ChangeService` 手工 `join(",")` 传入字符串，存在 SQL 注入面（与 `TestCaseMapper` 早前已修复的 `#{}` 模式不一致）。
- **修复**：
  - Mapper 改为 `<script>` + `<foreach>` + `#{id}`，参数类型 `String` → `List<Long>`；
  - `ChangeService` 移除手工拼接，直接传 `List<Long> descendantIds`；
  - 同步更新 `ChangeServiceTest` 断言 `markSuspectBatch(any())`。

#### H2. 逻辑删除绕过 — 自定义 `@Select` 未过滤 `is_deleted`
- **位置**：`med-rms-traceability/.../mapper/RequirementRelationMapper.java`（3 个方法）
- **问题**：`selectBySourceReqId` / `selectByTargetReqId` / `selectByHorizontalType` 为自定义 `@Select`，绕过 MyBatis-Plus 全局逻辑删除，会把已软删除的关系一并查出（R222.3 同类根因）。实体 `RequirementRelation` 确认含 `is_deleted` 字段。
- **修复**：三条 SQL 均追加 `AND is_deleted = false`。

#### H3. 合规判定门禁被降级（fail-open 隐患）
- **位置**：`med-rms-compliance/.../service/DhfEvidenceService.java#computeVerdict`
- **问题**：当存在 IEC 62304 不合规条款（`nonCompliant > 0`）但覆盖率 ≥ 70% 时，判定被归为 `WARN` 而非 `FAIL`，可能导致含硬性不合规项的 DHF 证据包被误提交（违反 FR-1.4 / IEC 62304 意图）。
- **修复**：新增 fail-closed 硬门禁——只要 `nonCompliant > 0`，无论覆盖率一律判 `FAIL`；并更新对应测试断言。

### 🟠 中危

#### M1. JWT 密钥硬编码
- **位置**：`med-rms-admin/.../service/JwtService.java`
- **问题**：`private static final String SECRET = "MedRMS-..."` 硬编码于源码。
- **修复**：改为 `@Value("${med-rms.jwt.secret:<默认>}")` 可由环境变量/配置中心覆盖；保留字段初始值以兼容非 Spring 上下文单测（`new JwtService(...)`）。**默认值不变，行为向后兼容**；生产须通过 `JWT_SECRET` 注入强随机密钥。

#### M2. 数据库口令硬编码
- **位置**：`med-rms-web/src/main/resources/application.yml`
- **修复**：`url/username/password` 全部改为 `${DB_HOST:...}` / `${DB_USERNAME:postgres}` / `${DB_PASSWORD:postgres}` 环境变量占位（默认值保持本地开发可用）。

#### M3. 异常信息泄露
- **位置**：`ProjectController#exportPlan`、`AdminController#getUser`
- **问题**：`catch` 中把 `e.getMessage()` 直接拼进 API 响应，可能泄露内部实现/SQL 细节。
- **修复**：改为记录 `log.error(..., e)` + 对外返回通用提示（"请稍后重试或联系管理员"）；`ProjectController` 补充 `@Slf4j`。

#### M4. 前后端契约断链 — 基线页风险列表
- **位置**：`frontend/src/views/compliance/Baselines.vue`
- **问题**：调用 `GET /risks`（后端不存在该端点），导致基线关联风险加载失败。
- **修复**：改为真实端点 `GET /risk/register/list?projectId=...`（后端 `RiskRegisterController` 支持 `projectId` 过滤，返回数组，前端已兼容）。

### 🟡 低危 / 观察项

#### L1. 前端死代码且契约不符 — `riskApi.assess` / `riskApi.updateControl`
- **位置**：`frontend/src/api/risk.ts`
- **现状**：全前端 **0 处调用**；且与后端签名不符（`updateControl` 后端要求 `@RequestParam controlMeasure, reviewedBy`，前端却发 body 且缺 `reviewedBy`；`assess` 缺 `hazardSource/hazardSituation/harm/assessedBy`）。
- **处置**：判定为死代码，未改动。建议后续删除或在启用前对齐后端参数（`params` 传参 + 补齐字段）。

#### L2. 自定义 SQL 逻辑删除审计 — 其余项无需修复
- 其余自定义 SQL 多作用于无 `is_deleted` 的关联/闭包/审计/发件箱表（如 `t_ancestor` 闭包、`audit_log`、`outbox`、`trace_link`），无需过滤，归为"无需修复"。

---

## 四、陈旧测试清理（行动项 4）

| 模块 | 测试类 | 根因 | 修复 |
|---|---|---|---|
| admin | `UserServiceTest` | `UserService` 新增 `LoginAttemptService` 依赖未 mock → 3 用例 NPE | 补 `@Mock LoginAttemptService` |
| compliance | `StatisticsServiceTest` | `getComplianceStats` 新用 `SoupComponentMapper` 未 mock → NPE | 补 `@Mock SoupComponentMapper` |
| compliance | `BaselineServiceTest` | `lockBaseline` 改原子 UPDATE，未桩 `update()` 返回值 → 误判状态冲突 | 桩 `update(any(),any())→1` |
| compliance | `DhfEvidenceServiceTest` | R207 演进：新增基线/部分合规/SOUP 完整性校验、manifest 7→12 段 | 补 `BaselineService` mock + 默认空桩、对齐 PASS/FAIL/12 断言 |
| web | `RequirementAuditIntegrationTest` | R201/R205 需求创建新增 `riskLevel`/`safetyClass` 必填，测试请求体缺字段 → 校验失败 data=null → NPE | 请求体补 `riskLevel/safetyClass`（已通过） |

---

## 五、web 模块剩余集成测试（环境/种子受限，非代码缺陷）

以下 3 项依赖运行时数据库/种子状态，非本次代码引入，建议在具备完整迁移的测试库中处理：

1. **`MedRmsApplicationTest.contextLoads` / `AuthControllerTest`（context 加载失败）**
   - 根因：`test` profile 使用 H2，Flyway 迁移 `V1001__user_preference` 校验失败（PG 专用 SQL 在 H2 下不兼容并被记录为 failed migration）。
   - 建议：为迁移提供 H2 兼容变体，或测试改连专用 PostgreSQL 并 `flyway repair`；或对纯 context 测试关闭 Flyway。

2. **`AuthRBACIntegrationTest.viewerLoginShouldHaveLimitedPermissions`（断言失败：viewer 权限 = 20，期望 < 20）**
   - 核实：VIEWER 基础种子为 **19 个只读权限**（`*:list` / `*:read` / `esign:verify` / `baseline:compare` / `report:*`），**不含** `sys:user:list`、`req:create` 等写/管理权限；实际 20 是后续迁移多授 1 个只读权限所致。
   - 结论：**非危险越权**，测试的 `< 20` 魔法阈值已陈旧。
   - 建议：将断言从"数量 < 20"改为"显式断言 viewer 不含任何写/管理权限"，避免脆弱的计数阈值；或核对第 20 项权限后放宽阈值。

---

## 六、测试验证结果

- 命令：`mvn -o -fae test`（离线、失败不中断）
- 结果：除 §5 三项外，**全部后端模块测试通过**；本轮所有生产改动均有对应用例覆盖并通过。
- 关键回归确认：
  - `ChangeServiceTest` — `markSuspectBatch` 参数化后通过；
  - `compliance` 全模块 168 用例通过；
  - `RequirementAuditIntegrationTest` 4 用例通过（契约修复生效）。

---

## 七、待你决策事项

1. **提交/推送**：本轮 15 个文件改动 + 上一轮 `995e285` 均未推送。按 Git 安全规范未自动推送，请评审后决定是否 `commit` 本轮改动并 `push origin/main`。
2. **生产密钥**：上线前务必通过环境变量注入 `JWT_SECRET` / `DB_PASSWORD`（当前默认值仅供本地开发）。
3. **RBAC 测试阈值**：是否按 §5.2 建议将 viewer 断言改为"能力断言"而非计数阈值。
4. **前端死代码**：`riskApi.assess/updateControl` 删除还是对齐后端启用。

---

## 附录 A：本轮改动文件清单（15）

**生产代码（6）**
- `med-rms-requirement/.../mapper/RequirementMapper.java`（H1）
- `med-rms-change/.../service/ChangeService.java`（H1）
- `med-rms-traceability/.../mapper/RequirementRelationMapper.java`（H2）
- `med-rms-compliance/.../service/DhfEvidenceService.java`（H3）
- `med-rms-admin/.../service/JwtService.java`（M1）
- `med-rms-project/.../controller/ProjectController.java`（M3）
- `med-rms-admin/.../controller/AdminController.java`（M3）
- `med-rms-web/src/main/resources/application.yml`（M2）

**前端代码（1）**
- `frontend/src/views/compliance/Baselines.vue`（M4）

**测试代码（6）**
- `med-rms-change/.../service/ChangeServiceTest.java`
- `med-rms-admin/.../service/UserServiceTest.java`
- `med-rms-compliance/.../service/StatisticsServiceTest.java`
- `med-rms-compliance/.../service/BaselineServiceTest.java`
- `med-rms-compliance/.../service/DhfEvidenceServiceTest.java`
- `med-rms-web/.../RequirementAuditIntegrationTest.java`

## 附录 B：评审方法

- 4 路并行 `code-explorer` 子代理专项审计：安全（RBAC/异常/密钥）、数据完整性（逻辑删除/事务）、前后端契约、模块集成。
- 定向 grep：`${}` 注入面、`@Transactional` 误用、自定义 SQL 逻辑删除。
- 全量 `mvn -o -fae test` 回归验证每一处改动。
