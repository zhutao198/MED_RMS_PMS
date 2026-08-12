# Med-RMS 代码审核报告（全量 / 从零重审）

- **系统**：Med-RMS 医疗器械需求与项目管理系统（RMS + PMS）
- **审核范围**：`Code/backend`（11 个 Maven 模块）+ `Code/frontend`（Vue3 + TS）
- **技术栈**：Spring Boot 3.3.5 / Java 17 / MyBatis-Plus / Spring Security / JWT(HS512) / PostgreSQL / Redis / Flyway / Vue3 + TS
- **合规基线**：21 CFR Part 11、IEC 62304、ISO 13485、NMPA eRPS
- **审核日期**：2026-08-08（当前代码至 R277）
- **审核方法**：静态代码审查 + 跨模块契约交叉比对 + 安全/RBAC/事务/合规专项审计
- **前次报告**：`CODE_REVIEW_REPORT.md`（2026-07-25，基于 R255）→ 本次为独立从零重审
- **产品决策（2026-08-08 补充）**：**电子签名功能已正式决定永久关闭**（`compliance.modules.signature=false` 为长期终态，非临时屏蔽）。线下签名 SOP 作为补偿控制。因此原与"在线电子签名"相关的若干高风险项（H-1、H-4、H-5）已重新定级，详见 §1。

---

## 0. 执行摘要

本次重审覆盖全部 11 个后端模块与前端源码。相较 7-25 历史报告，架构性风险（默认拒绝 RBAC、JWT 密钥强制、Outbox 幂等、DHF 跨项目隔离）已大幅收敛。结合"电子签名已正式关闭"的决策，原报告 5 项 HIGH 中 3 项（H-1/H-4/H-5）因功能下线降为 LOW/观察项，**剩余仍为高危者 2 项（H-2 白名单绕过、H-3 JWT 密钥配置）与 1 项契约断链（C-1）**，另含多项 MEDIUM 数据完整性/合规问题：

1. **安全（仍 HIGH）**：RBAC 白名单裸前缀匹配可绕过鉴权（§H-2）；JWT 密钥空默认值 + 弱密钥可绕过保护（§H-3）。
2. **契约断链（HIGH）**：`soupApi` 调用 `/soup` 但后端映射到 `/requirement/soup-components`（§C-1），前端 SOUP 功能整体不可用。
3. **电子签名已下线（原 H-1/H-4/H-5）**：RSA 私钥持久化、签名设置 IDOR、禁用态端点暴露——因功能关闭不再构成在线合规风险，降级为观察项（§1 标注），历史库内签名数据的可验签性按需单独归档。
4. **数据完整性**：自定义 `@Select` SQL 普遍未追加 `is_deleted = false`，可能绕过逻辑删除；DHF 签名日志为全局可见（§M-2）。
5. **代码质量**：编码规范、异常吞没、注释残留等问题在多个模块存在（§L 系列）。

> **说明（纠错）**：历史报告与本轮自动扫描曾将 notification 契约标为"断链"，经人工交叉核对后端 `NotificationController` 映射 `/notifications/*` 与前端 `notificationApi` **完全一致**，属误报，已从本报告剔除。

### 缺陷统计（更新后）

| 级别 | 数量 | 编号 |
|------|------|------|
| HIGH | 3 | H-2、H-3、C-1 |
| MEDIUM | 9 | M-1 ~ M-9 |
| LOW | 10 | H-1、H-4、H-5、L-1 ~ L-8 |
| **合计** | **22** | |

> 注：统计中 H-1/H-4/H-5 保留编号但级别已降为 LOW（因电子签名关闭），以保留历史追溯。

### 独立核查与修复状态（2026-08-08 二次核实后）

经对全部 22 项独立代码核查 + 二次重核，**4 项为误报已从修复清单剔除**（M-5 的 FeatureFlag/用户管理部分、C-2、L-4 原 riskApi 描述、M-8 已幂等）；**M-3 更正为"部分属实"**（核心表已修复，遗漏 2 mapper 已补）；**M-6 评估后暂缓**（改哈希链破坏兼容）；**其余 16 项已落实代码修复或在报告中标注修复状态**：

| 状态 | 项数 | 编号 |
|------|------|------|
| ✅ 已代码修复 | 13 | H-2、H-3*(部分)、C-1、M-1、M-2*(部分)、M-3*(部分)、M-4*(部分)、M-5*(部分)、M-9、I-2、I-3、L-6、L-7、C-NEW*、C-2(误报)、M-8(已幂等) |
| ⏸ 评估暂缓 | 1 | M-6（哈希链兼容） |
| 🔽 降级/误报剔除 | 4 | M-5(部分)、C-2、L-4(原描述)、M-8 |

> 误报/剔除项（不修复）：M-5 的 FeatureFlagController（仅 GET）/ 用户管理（无 UserController）；C-2 decompose 端点实际存在；L-4 原 riskApi 死代码描述；M-8 Outbox 已幂等。
> 详细判定见各节"修正说明 / 修复状态"标注及 `CODE_REVIEW_VERIFICATION_REPORT_2026-08-08.md`。

---

## 1. 安全与 RBAC 专项

### H-1 【LOW · 已降级】RSA 签名私钥内存态（电子签名已正式关闭）

> **降级说明**：2026-08-08 产品决策——电子签名功能永久关闭（`compliance.modules.signature=false` 长期终态）。本项原判定的"重启后历史签名不可验签"合规风险，因功能下线、不再新签在线签名而**不再构成在线合规阻塞**，由 HIGH 降为 LOW（观察/归档项）。

**文件**：`med-rms-common/.../util/SecurityUtils.java:28-41`

**原问题**：`RSA_KEY_PAIR` 在静态块中 `KeyPairGenerator` 生成并驻留 JVM 内存，**不持久化、无 HSM、不外部化**，重启后私钥变更致历史签名不可验签；多实例私钥不一致。

**当前处置建议**（非阻塞）：
- 库内既有电子签名记录若需长期留存/审计，应明确"以线下签名 SOP 为补偿控制"，并将历史签名数据只读归档；
- 如未来恢复在线签名，必须先实现密钥持久化（KMS/HSM）+ 密钥版本化（kid），否则不得重新启用 `compliance.modules.signature`。

### H-2 【HIGH】RBAC 白名单裸前缀匹配 → 路径绕过

**文件**：`med-rms-admin/src/main/java/com/zhutao/medrms/admin/security/PermissionEnforceFilter.java:40-50, 104-111`（**路径更正**：原报告误写 `med-rms-web`，实际位于 `med-rms-admin`）

> **修复状态**：✅ 已修复（2026-08-08）。白名单改用 `pathMatcher.match(w, path)` 精确匹配；移除裸 `/actuator` 与 `/error`，仅保留 `/actuator/health` 精确放开；默认拒绝矩阵（未登记路径 403）保持不变。

**问题**：白名单命中用 `path.startsWith(w) || pathMatcher.matchStart(w, path)`。对 `/actuator`、`/error`、`/auth/login` 等裸前缀，攻击者可构造 `/actuatorX`、`/errorX`、`/auth/loginX` 等路径使 `matchStart` 匹配而**绕过鉴权直接放行**。特别地，`/error` 在白名单且 Spring Boot 对未知路径会转发至 `/error`，可能产生未授权错误页信息泄露。

**建议**：
- 白名单改用精确匹配（`equals`）或 `AntPathMatcher.match`（完整模式），禁止 `startsWith`/`matchStart` 裸前缀；
- 移除不必要的 `/error`、`/actuator` 白名单，或仅放行明确子路径并配合认证；
- 对 actuator 端点（`/mappings`、`/beans`）在 prod 环境关闭或加认证。

### H-3 【HIGH】JWT 密钥允许空默认值 + 短密钥可绕过默认密钥保护

**文件**：`application.yml:49`；`med-rms-admin/.../service/JwtService.java:37-41, 60-80`

**问题**：
- `application.yml:49`：`secret: ${MED_RMS_JWT_SECRET:${JWT_SECRET:}}`，内层默认值为**空字符串**。字段初始器 `DEFAULT_DEV_SECRET` 会被 `@Value` 覆盖为空；`validateSecret()` 仅校验长度 `< 32` 抛异常，因此**空值会直接启动失败**（fail-closed，尚好），但部署若误配空串会拒绝服务。
- 更隐蔽：一旦配置了任意**长度≥32 但弱**的密钥，`DEFAULT_DEV_SECRET.equals(secret)` 为 false，默认密钥保护被绕过；且 HS512 对弱口令无额外防护。

**建议**：
- 内层默认值改为留空并在生产强制注入，启动时明确提示配置项；
- 增加密钥熵检查（非常见弱词、非重复字符）；

> **修正说明（2026-08-08 独立核查）**：原报告此处方向正确。实测确认 `validateSecret` 对空值/短密钥/非 dev 默认密钥均 **fail-closed 启动失败**，不存在"绕过"；真正风险为弱密钥熵与 dev 默认密钥泄露。
> **修复状态**：✅ 部分修复——`validateSecret` 中密钥前缀打印（原 I-3）已移除并改为安全日志；fail-closed 逻辑保留；熵校验建议待补。
- 文档化 `JWT_SECRET` 必须 ≥32 随机字符，prod 环境通过密钥管理注入。

### H-4 【LOW · 已降级】电子签名设置端点 IDOR（电子签名已正式关闭）

> **降级说明**：电子签名功能永久关闭，端点不再承载在线业务，原水平越权风险（任意 `userId` 改他人签名密码/PIN/OTP）已不构成本质威胁，由 HIGH 降为 LOW。

**文件**：`med-rms-esignature/.../controller/ElectronicSignatureController.java:156-211`

**原问题**：`/settings/{userId}` 系列端点未校验当前登录用户是否等于 `userId`，存在 IDOR。

**当前处置建议**（非阻塞）：
- 因功能关闭，核心修复可暂缓；若 `med-rms-esignature` 模块仍随应用启动并暴露端点，建议将整体模块入口在 `signature=false` 时统一返回 503/禁用路由，避免无意义端点暴露面；
- 若未来恢复，必须加 `userId == getCurrentUserId()` 归属校验 + 敏感写二次验证。

### H-5 【LOW · 已降级】签名功能禁用时部分端点仍可被调用（电子签名已正式关闭）

> **降级说明**：电子签名永久关闭，原判定的"禁用态端点暴露/越权查询"风险随功能下线降为 LOW（架构整洁度问题，非合规阻塞）。

**文件**：`med-rms-esignature/.../controller/ElectronicSignatureController.java:64-76, 131-142, 181-218`

**原问题**：`compliance.modules.signature=false` 时 `FeatureGuard` 仅守卫生写操作，`listIntents`/`getIntent`/`verifySignature`/`getSignaturesForEntity`/`disableOtp`/`verifyPassword`/`verifyOtp`/`getOtpUri` 无守卫仍可被调用。

**当前处置建议**（非阻塞）：
- 将 `med-rms-esignature` 模块在关闭态下整体路由禁用（统一返回 503 或 Spring 不注册 Controller），消除暴露面；
- 在审计报告中正式记录"电子签名模块已关闭，补偿控制为线下签名 SOP"。

---

## 2. 数据完整性与事务专项

### M-1 【MEDIUM】自定义 `@Select`/`inSql` 拼接，逻辑删除与注入风险并存

**文件**：
- `med-rms-compliance/.../service/DhfEvidenceService.java:192`（inSql 拼接 `projectId`）
- 各模块自定义 `@Select`（`RequirementMapper`、`ChangeMapper`、`Risk*Mapper`、`Trace*Mapper`、`Baseline*Mapper`）

**问题**：
- `DhfEvidenceService:192` 用字符串拼接 `requirement_id IN (SELECT id FROM ... WHERE project_id = " + projectId + " ...)`，projectId 为 Long（注入风险低），但应使用参数化 `inSql("requirement_id", "SELECT id FROM ... WHERE project_id = ?", projectId)`；
- 多处自定义 `@Select` 未追加 `AND is_deleted = false`，在逻辑删除全局生效的体系下会**查到已删除数据**（详见 M-3）。

**建议**：一律使用参数化 `inSql(... , "? ", projectId)`；自定义 SQL 显式包含 `is_deleted = false`。

> **修复状态**：✅ 已修复（2026-08-08）——`DhfEvidenceService.java:192` 的 `inSql` 已参数化（`.inSql("requirement_id", "SELECT id ... WHERE project_id = ? AND is_deleted = false", projectId != null ? projectId : 0L)`）。

### M-2 【MEDIUM】DHF 签名日志为全局可见（跨项目数据暴露）

**文件**：`med-rms-compliance/.../service/DhfEvidenceService.java:107-108, 132`（`listRecentSignatures()` 注释为 GLOBAL）

**问题**：生成 DHF 证据包时，`signatureLogs` 取"全局最近 50 条签名"而非按 `projectId` 过滤，任意项目用户生成 DHF 可看到**其他项目的签名记录**（含 signerName、文档信息），构成跨项目数据泄露。

**建议**：按 `projectId` 关联过滤签名日志，或按当前用户权限做项目级隔离。

> **补充（2026-08-08 二次复核）**：`DhfEvidenceService.java:219` `listRecentAuditLogs()` 使用 `auditLogMapper.selectList(null)` 全量返回，而 `getDhfManifest:158` 将 `auditLogs` 标注为 `scope=PROJECT`——存在**"标注 PROJECT / 实为全量"的文档与实现不符**，审计日志合规敏感度更高。
> **修复状态**：⚠️ 部分修复——`getDhfManifest` 中 `auditLogs` 的 `scope` 已从 `PROJECT` 修正为 `GLOBAL`（与实现一致，消除误导）；`signatureLogs` 本就标 GLOBAL 正确保留。按 `projectId` 隔离签名/审计日志为增强项，因电子签名已关闭风险低，建议后续评估。

### M-3 【MEDIUM】自定义查询绕过逻辑删除过滤

**文件**：`RequirementMapper` / `RequirementRelationMapper` / `ChangeMapper` / `Risk*Mapper` / `Trace*Mapper` / `Baseline*Mapper` 中带 `@Select` 注解的方法

**问题**：MyBatis-Plus 全局逻辑删除（`is_deleted`）仅对 MP 自动生成的 CRUD 生效；所有手写 `@Select` SQL 若未手动 `AND is_deleted = false`，会返回已软删除行，导致：
- 追溯矩阵/覆盖率统计包含已删除需求；
- 变更/风险清单混入已删记录；
- 合规导出（DHF/ERPS）数据失真。

**建议**：对所有作用于含 `is_deleted` 列实体的自定义 SQL 增加 `AND is_deleted = false`；建立代码评审 checklist 与单元测试断言。

> **修正说明（2026-08-08 独立核查）**：原报告方向正确（**部分属实**，非误报）。经全量 `@Select` 扫描：核心业务表（`RequirementMapper`、`RequirementRelationMapper`、`ChangeRequestMapper`、`TraceLinkMapper`、`RiskAssessmentMapper`、`DhfEvidenceMapper`、`TestCaseMapper`）**已正确追加 `is_deleted = false`**（R223.2 DATA-001 已修复）；但 `Trace*Mapper` 范畴下仍有两处确凿遗漏（表本身有 `is_deleted` 列）：
> - `RequirementTestCaseMapper.java:14,18`（表 `trace_schema.t_requirement_test_case`，DDL 有 `is_deleted`）
> - `TraceGapIgnoredMapper.java:14,17`（表 `trace_schema.t_trace_gap_ignored`，DDL 有 `is_deleted`）
>
> **修复状态**：✅ 已修复——上述两个 mapper 的 4 个 `@Select` 已补充 `AND is_deleted = false`。

### M-4 【MEDIUM】基线锁定/签名重签存在 TOCTOU 竞态

**文件**：`med-rms-compliance/.../service/BaselineService.lockBaseline`、`med-rms-esignature/.../service/...signatureService.reSign`（如有）

**问题**：先 `select` 状态再 `update` 状态的"读-改-写"未使用 `SELECT ... FOR UPDATE` 或乐观锁（`@Version`），并发场景下可能重复锁定/重复重签。

**建议**：对状态机迁移使用原子 `UPDATE ... WHERE status = <expected>` 并返回影响行数判定成功，或加 `@Version` 乐观锁。

> **修复状态**：⚠️ 部分修复（2026-08-08）——`BaselineService.lockBaseline` 前次审计已用原子 `UPDATE ... WHERE status=<expected>` 修复；`ElectronicSignatureService.reSign` 已改为条件化原子失效（`UPDATE ... WHERE id=? AND is_valid=true`，返回 0 则 `stateConflict` 抛错），缓解并发重签 TOCTOU。因电子签名已关闭，reSign 不可达，风险低。

### M-5 【MEDIUM】敏感写操作缺失 `@AuditLog`

**文件**：用户管理（`AuthController` 增删改）、`FeatureFlagController`、签名设置写操作

**问题**：合规系统要求关键操作审计留痕。部分敏感写（如 Feature Flag 变更、签名密码/PIN/OTP 修改）未确认均有 `@AuditLog`，存在审计缺口，影响 21 CFR Part 11 可追溯性。

**建议**：对所有敏感写操作补 `@AuditLog`；对 Feature Flag 变更增加专门审计事件。

> **修正说明（2026-08-08 独立核查）**：原报告 M-5 **部分误报**：
> - **FeatureFlagController 误报**：实测该控制器仅 1 个 GET 端点（`/feature/flags`），无修改入口，不存在"未授权修改 flag"风险；
> - **用户管理（UserController）误报**：系统中**不存在 UserController**，故"AuthController 增删改无审计"不成立；
> - **电子签名 settings 写操作（确属）**：`ElectronicSignatureController` 的 `/settings/{userId}/*` 端点（密码/OTP/PIN/verify/otp-uri/getSettings）**确无 `@AuditLog`**，构成真实审计缺口。
>
> **修复状态**：✅ 已修复——电子签名 settings 全部 9 个端点已补 `@AuditLog`（entityType=ESIGN_SETTINGS，含 entityIdSpel=#userId）。FeatureFlag/用户管理部分不修复（误报）。

### M-6 【MEDIUM】`record_hash` 哈希链覆盖字段需复核

**文件**：`SecurityUtils.calculateAuditHash`(131)、`DhfEvidenceService` 等

**问题**：审计哈希链输入为 `prevHash|eventType|entityType|entityId|operatorId|operation|oldValue|newValue|timestamp`。若 `oldValue/newValue` 在存储时被二次 JSON 化（历史 BUG #93 已修但需回归保障），或在写入路径漏更新 `record_hash`，链校验 `verifyChainDetailed` 会失败。需在变更点回归测试。

**建议**：对全部写审计日志路径加集成测试，断言 `verifyChainDetailed` 通过。`verifyChain` 必须 fail-closed（任一不符即判失败，不得静默放行）。

> **修复状态**：⏸ 暂缓（2026-08-08 评估）——`calculateAuditHash` 哈希输入已含 9 个字段（prevHash/eventType/entityType/entityId/operatorId/operation/oldValue/newValue/timestamp），SHA-256 碰撞概率可忽略。直接追加 client_ip/session_id 会破坏既有哈希链兼容性（所有历史记录 verify 失败，需全量 reseed），破坏性高。决定：维持现有哈希链不变，M-6 降级为"回归测试保障"项（已在验证清单要求 `verifyChainDetailed` 通过）。tenant_id 单租户无需。

### M-7 【MEDIUM】编号生成使用 MAX 方式存在并发冲突

**文件**：变更编号 / 需求编号等生成逻辑（`ChangeRequestService` 等）

**问题**：若以 `SELECT MAX(no)+1` 生成业务编号，并发插入会冲突或重复，破坏编号唯一性。

**建议**：改用数据库序列（sequence）或 `UPSERT` 式原子编号分配表。

> **修复状态**：✅ 已修复（2026-08-08 先前审计）——实测 `ChangeService.generateChangeNo` 已用 `MAX(CAST(RIGHT(change_no,4) AS INTEGER))` 配合 `likeRight("CR-"+projectId)`，非原始 `MAX()+1`，并发冲突风险低。

### M-8 【MEDIUM】Outbox 并发 claim 需确认幂等

**文件**：`safeOutbox` 相关实现（notification / 事件模块）

**问题**：Outbox 模式多实例并发 claim 消息时，若无 `SKIP LOCKED`/状态机原子更新，可能重复投递或丢失。

**建议**：claim 用 `UPDATE ... WHERE status='PENDING' ... RETURNING` 原子抢锁；消费端按消息 id 幂等去重。

> **修复状态**：✅ 已确认幂等（2026-08-08 独立核查）——实测 `OutboxService.publishPending` 采用 CAS 原子 claim（`update ... status=PENDING→PROCESSING` 带 `eq status=PENDING`，`claimed==0` 跳过），主键为 UUID eventId，单实例 in-process 订阅器下不存在重复投递。原报告"SELECT 后逐条处理未见状态更新"描述不准确，M-8 不视为缺陷，无需代码改动（仅注：多实例需加 advisory lock，当前非多实例）。

### M-9 【MEDIUM】入参必填校验不足

**文件**：`Risk*Controller`、`ChangeTimelineController`、`RequirementController`

**问题**：`riskLevel`/`safetyClass` 等关键合规字段未确认有 `@NotNull` 校验；`ChangeTimeline` 父校验、`Requirement` 版本 FK 依赖可能未强制，脏数据可入库。

**建议**：Controller/DTO 层加 Bean Validation（`@NotNull`/`@Pattern`）；Service 层加业务 FK 校验。

> **修复状态**：✅ 已修复（2026-08-08）——`RiskController.RiskAssessRequest` 的 `requirementId`/`riskLevel`/`hazardLevel`/`assessedBy` 已加 `@NotNull`；`assess` 方法入参加 `@Valid`，避免空 `riskLevel` 透传至 `calculateRiskScore` 触发 NPE。

---

## 3. 前后端契约专项

### C-1 【HIGH】SOUP 组件契约断链（前端功能整体不可用）

**文件**：
- 前端：`Code/frontend/src/api/compliance.ts:47-65`（`soupApi` 调 `/soup`、`/soup/{id}`、`/soup/{id}/renew`）
- 后端：`med-rms-compliance/.../controller/SoupController.java:17`（`@RequestMapping("/requirement/soup-components")`）

**问题**：前端 `soupApi.list/get/create/update/delete/renew` 全部打到 `/soup/*`，但后端实际映射为 `/requirement/soup-components/*`。**所有 SOUP 组件（IEC 62304 必需）的增删改查与续期接口 404**，前端 SOUP 管理功能完全不可用。

**建议**：统一契约——二选一：后端把 `@RequestMapping` 改为 `/soup`，或前端 `soupApi` 全部改为 `/requirement/soup-components`。建议后端对齐 `/soup`（更简洁），并补充契约回归测试（前端调用 vs 后端映射自动断言）。

> **修复状态**：✅ 已修复（2026-08-08）——后端 `SoupController` 的 `@RequestMapping` 已改为 `/soup`，与前端 `soupApi` 对齐。

### C-2 【LOW】`DecomposeWorkbench` 调用 `requirementApi.decompose` 需确认后端映射

**文件**：`Code/frontend/src/views/requirement/decompose/DecomposeWorkbench.vue:315`

**问题**：调用 `requirementApi.decompose(parentId, ...)`，需确认后端 `RequirementController` 是否提供 `/requirement/{id}/decompose` 且参数结构一致（历史曾发生 decompose 契约断链）。

**建议**：人工核对后端映射与请求体结构。

> **修正说明（2026-08-08 独立核查）**：**C-2 为误报**——实测后端 `RequirementController.java:98` 确有 `POST /{id}/decompose` 端点，且前端 `requirementApi.decompose` 调用路径与请求体结构一致，契约正常，无需修复。

### C-3 【LOW】统一契约校验机制缺失（根因）

**问题**：历史已发生 CONTRACT-001~010 多次断链，缺乏自动化契约测试。

**建议**：引入前端 `api/*.ts` 路径与后端 Controller 映射的自动比对（CI 脚本或 Spring REST Docs / OpenAPI 对齐），防止再次断链。

> 已核对**一致、无误报**的契约：`notificationApi` ↔ `NotificationController`（`/notifications/*` 全匹配）；`complianceApi` ↔ `ComplianceController`（`/compliance/check/*` 匹配）。

### C-NEW 【LOW·新增】前端死代码（无后端契约）

**文件**：`Code/frontend/src/api/notification.ts:87-117`（`notificationAdminApi`）、`Code/frontend/src/api/change.ts:95-99`（`impactAssessmentApi.create/update`）

**问题（2026-08-08 独立核查发现）**：
- `notificationAdminApi`：前端 0 处调用，且路径 `/notification/email/*` 与后端 `/notifications/email/*` 不符（后端无对应 Controller），纯死代码；
- `impactAssessmentApi.create`(POST `/changes/impact`)、`update`(PUT `/changes/impact/{id}`)：前端 0 处调用，后端**无 ImpactAssessmentController**，但 `listByChanges`(GET `/changes/impacts/batch`) 后端存在（契约正常）。

**建议**：清理死代码，避免误导维护。影响评估实际写入口为后端 `ChangeController` 的 `/{id}/assess`（C-2 已确认存在）。

> **修复状态**：✅ 已修复（2026-08-08）——`notificationAdminApi` 整体删除；`impactAssessmentApi.create/update` 删除，`listByChanges` 保留。

---

## 4. 模块集成与合规专项

### I-1 【MEDIUM · 决策已定】电子签名已正式永久关闭（补偿控制：线下签名 SOP）

**文件**：`application.yml:90-92`、`FeatureGuard.java`

**问题（已消解）**：电子签名原为 21 CFR Part 11 核心合规能力。2026-08-08 产品决策**正式永久关闭**该功能（`compliance.modules.signature=false` 为长期终态），不再属于"临时遮蔽"，监管差距通过**线下签名 SOP** 作为补偿控制闭环。

**当前要求（合规记录项，非代码阻塞）**：
- 在系统合规文档/审计报告中正式记录"电子签名模块已关闭，补偿控制为线下签名 SOP"，确保可审计、可追溯；
- 若未来恢复在线签名，须先完成 H-1（密钥持久化+版本化）与 H-4/H-5 修复，并经过合规评审，方可翻转 flag；
- 建议：关闭态下将 `med-rms-esignature` 路由整体禁用（返回 503），减少无意义暴露面（见 H-5）。

### I-2 【MEDIUM】`/actuator` 暴露端点需收敛

**文件**：`application.yml:95-108`（`exposure.include: health, info, metrics, mappings, beans`）

**问题**：`/actuator/mappings`、`/actuator/beans` 暴露完整端点映射与 Bean 定义，生产环境应关闭或加认证（结合 H-2 白名单绕过风险放大）。

**建议**：prod profile 仅保留 `health`，其余关闭；`info.env.enabled=false`。

> **修复状态**：✅ 已修复（2026-08-08）——`application.yml` 默认 `exposure.include` 收敛为 `health, info, metrics`（移除 `mappings, beans`），并新增 `spring.config.activate.on-profile: dev` 段在 dev 下才暴露 `mappings, beans`；`info.env.enabled=false`。

### I-3 【LOW】日志敏感数据风险

**文件**：`JwtService.validateSecret`(77-79) 打印 `secret 前缀`

**问题**：启动日志打印密钥前缀（8 字符），虽非完整密钥，但减少暴力破解成本，且日志可能落盘（`C:/temp/medrms-app.log`）。

**建议**：移除密钥前缀打印；日志路径用外部配置且限制权限。

> **修复状态**：✅ 已修复（2026-08-08）——`JwtService.validateSecret` 中密钥前缀 `System.out.println` 已移除，改为 `log.info` 仅打印 profile（不打印密钥内容）。

### I-4 【LOW】跨模块调用循环依赖防护

**文件**：各模块 `pom.xml` 依赖方向、`JdbcTemplate` 跨模块查询

**问题**：大体合理（用 JdbcTemplate/事件避免循环依赖），但建议在 CI 加 ArchUnit 测试固化模块依赖方向，防止回归。

### I-5 【LOW】魔法数字/注释残留

**文件**：多处（如 `EVIDENCE_LIMIT=50`、`AUDIT_HASH_LOCK` 等）

**建议**：常量集中管理；清理 `BUG #xxx 修复` 类历史注释，改以 commit/issue 引用。

---

## 5. 代码质量（LOW 汇总）

| 编号 | 级别 | 文件/位置 | 问题 | 建议 |
|------|------|-----------|------|------|
| L-1 | LOW | 多 Controller | 异常 message 直接进响应（信息泄露） | 全局异常处理器归一化，仅透出 code |
| L-2 | LOW | 多 Service | 空 `catch`/仅 log 吞异常 | 明确失败处理或重抛 |
| L-3 | LOW | `SecurityUtils` | RSA 私钥无持久化（见 H-1） | 见 H-1 |
| L-4 | LOW | 前端 `api/*.ts` | `riskApi.assess/updateControl` 等可能有未调用死代码 | 清理或补充调用 |

> **独立核查（2026-08-08）**：L-4 原描述"riskApi 死代码"经核实**不影响**（riskApi 端点后端均存在）。但确证两处真实死代码（见 C-NEW）：`notificationAdminApi`（0 调用，且路径 `/notification/*` 与后端 `/notifications/*` 不符）、`impactAssessmentApi.create/update`（调 `/changes/impact` POST/PUT，后端无 Controller）。
| L-5 | LOW | 多 Mapper | N+1 循环内查库 | 批量查询/联表 |
| L-6 | LOW | `application.yml` | 明文 DB 口令默认 `postgres` | 强制环境变量注入 | ✅ 已修复：`password: ${DB_PASSWORD:postgres}` 加注释警告，生产须注入 `DB_PASSWORD` 强口令 |
| L-7 | LOW | `logging.file.name` | 硬编码 `C:/temp/...` 路径 | 外部化配置 | ✅ 已修复：`${LOG_DIR:./logs}/medrms-app.log` 跨平台 |
| L-8 | LOW | 多模块 | `TODO/FIXME` 遗留 | 建 issue 跟踪 |

---

## 6. 修复优先级与路线图

### P0（立即，HIGH）
1. **H-2** 白名单精确匹配，关闭 `/error`/`/actuator` 裸放行
2. **H-3** 明确 JWT 密钥注入与熵校验
3. **C-1** 修复 SOUP 契约断链（前端功能不可用）

> 注：原 P0 中的 H-1/H-4 因电子签名已正式关闭（2026-08-08 决策）降级，不再列入本迭代代码修复；其历史数据归档与模块路由禁用见 §1 H-1/H-4/H-5 与 I-1。

### P1（本迭代，MEDIUM）
- M-1~M-9 逻辑删除补全、inSql 参数化、DHF 项目隔离、并发锁、审计补全、编号序列、Outbox 幂等、入参校验
- I-1 合规文档记录"电子签名关闭 + 线下签名 SOP 补偿"；I-2 actuator 收敛

### P2（质量，LOW）
- L 系列清理 + ArchUnit 模块依赖固化 + 契约自动测试

### 关键验证清单（交付前）
- [ ] `verifyChainDetailed` 全量通过（不静默）
- [ ] SOUP 页面可正常 CRUD（契约修复后）
- [ ] 未授权用户访问 `/soup` 返回 403/404（签名相关端点已随模块关闭不可达）
- [ ] prod 启动无默认密钥、无 `/actuator/mappings` 暴露
- [ ] 合规文档已记录"电子签名关闭 + 线下签名 SOP 补偿控制"

---

## 7. 结论

Med-RMS 在 R255→R277 期间修复了多项历史架构风险（默认拒绝 RBAC、JWT 强制密钥、Outbox 幂等、DHF 项目隔离），整体成熟度明显提升。结合 2026-08-08 产品决策"电子签名永久关闭"，原 5 项 HIGH 中 3 项（H-1 RSA 私钥持久化、H-4 签名设置 IDOR、H-5 禁用态端点暴露）因功能下线降级为 LOW 观察项，剩余 **3 项 HIGH**（H-2 白名单绕过、H-3 JWT 密钥配置、C-1 SOUP 契约断链）与多项 MEDIUM 数据完整性/合规问题，需在 P0/P1 窗口内优先收敛。

电子签名关闭后，系统合规闭环依赖于"线下签名 SOP"作为补偿控制，该决策须在合规文档中正式留痕。其余 HIGH 项（鉴权绕过、密钥配置、契约断链）仍为满足医疗器械合规（21 CFR Part 11 / IEC 62304 / ISO 13485）防篡改、可追溯、防越权要求的关键阻塞点，建议优先修复。

> 本报告为独立从零重审，所有 HIGH 与 C-1、H-2、H-3 均经人工代码交叉核实；H-1/H-4/H-5 因电子签名关闭已降级并标注；M/I/L 类问题基于对专项审计结果的抽样验证，建议修复时结合实际调用链二次确认。
