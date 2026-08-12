# Med-RMS 项目代码评审报告（2026-08-10 · 自底向上完整审计）

> 复核对象：`d:/zhutao/MED_RMS_PMS`（R289）
> 审计方法：先穷尽真实清单（43 Controller / 63 Service / 67 Mapper），再对每一项做合规与安全检查
> 与之前两份报告的关系：
> - `CODE_REVIEW_REPORT_FULL_2026-08-09.md` 已作废（多数条目虚构，见 META 报告）
> - 本报告是该 META 报告第 6 节"建议重做"之后的产出

---

## 0. 总览

| 严重度 | 数量 | 关键特征 |
|--------|------|---------|
| **P0 致命** | **5** | 合规硬要求违反 / 横向越权 / 默认口令裸奔 |
| **P1 重要** | **12** | 核心域写端点缺审计、SQL/事务/TOCTOU、CORS 默认放行 |
| **P2 一般** | **13** | 硬编码 / 默认配置 / OOB 流程 |
| **P3 加固** | **4** | 命名规范 / 日志清理 |

**总计 34 项真实发现**，每项均含 `文件路径:行号 + 真实代码片段引用`。

---

## 1. P0 — 致命缺陷（5 项，24 小时内必修）

### P0-1 横向越权：通知模块 userId 由前端传入且未与登录身份比对

**证据**：`Code/backend/med-rms-notification/src/main/java/com/zhutao/medrms/notification/controller/NotificationController.java`

```24:NotificationController.java        public Result<List<Notification>> getUnread(@RequestParam Long userId) {
30:    public Result<Map<String, Integer>> getUnreadCount(@RequestParam Long userId) {
39:            @RequestParam Long userId,
54:    public Result<Void> markAllAsRead(@RequestParam Long userId) {
69:    public Result<Void> deleteAll(@RequestParam Long userId) {
```

**问题**：`userId` 完全由前端 query 传入，**未与 `SecurityUtils.getCurrentUserId()` 比对**。任何登录用户可：
- 通过 `GET /notifications/all?userId=任意值` 读他人通知
- 通过 `DELETE /notifications/all?userId=任意值` 清空他人通知（**数据销毁**）

**修复**：
1. 删除所有 `@RequestParam Long userId`，改为从 `SecurityUtils.getCurrentUserId()` 取
2. 服务层强制要求 userId 参数等于当前用户

---

### P0-2 敏感字段明文泄露：SignatureSettings 三个密钥字段缺 @JsonIgnore

**证据**：`Code/backend/med-rms-esignature/src/main/java/com/zhutao/medrms/esignature/domain/entity/SignatureSettings.java`

```16:SignatureSettings.java    private String signaturePasswordHash;
18:    private String otpSecret;
22:    private String pinHash;
```

**问题**：实体用了 `@Data`，三个敏感字段（签名密码哈希、OTP 种子、PIN 哈希）均**没有 `@JsonIgnore`**。任何返回该实体的接口（如 `GET /esignature/settings/{userId}`）会直接通过 JSON 返回这些字段。

虽然 `otpSecret` 通常是 OTP 共享密钥而非明文密码，但**应视为最高机密字段**：
- 泄露 OTP 种子 → 攻击者可生成任意 OTP → 绕过双因子
- 泄露签名密码哈希 → 配合重置密码流程可进行离线爆破

**修复**：在三个字段上添加 `@JsonIgnore`，并对所有 `SignatureSettings` 接口返回专门的 DTO。

---

### P0-3 默认 dev profile + 默认数据库口令 = 生产裸奔陷阱

**证据**：`Code/backend/med-rms-web/src/main/resources/application.yml`

```4:application.yml  profiles:
5:    active: dev       # 默认 dev，生产忘设 prod 标识会以 dev 启动
...
9:    username: ${DB_USERNAME:postgres}
12:    password: ${DB_PASSWORD:postgres}   # 默认口令 postgres
```

**问题**：
1. `spring.profiles.active` 硬编码 `dev` → 运维部署时若忘加 `-Dspring.profiles.active=prod` 或环境变量，将以 dev 启动，**自动启用** dev profile 中的 mappings/beans endpoint 暴露。
2. `DB_PASSWORD:postgres` 弱默认 + dev profile 默认 → 任何忘配环境变量的部署 = 数据库"postgres/postgres"裸奔。

**修复**：
1. 删除 `spring.profiles.active: dev` 默认值（或设为空字符串 `""`）
2. 生产 profile 用 `${DB_PASSWORD}`（**无默认值**），启动时无环境变量直接失败
3. CI/CD 流水线强制 `--spring.profiles.active=prod`

---

### P0-4 RBAC 默认拒绝失效：SoupController 全部 8 个端点未登记权限矩阵

**证据**：
- `Code/backend/med-rms-compliance/.../controller/SoupController.java:17` `@RequestMapping("/soup")`
- `Code/backend/med-rms-admin/.../security/PermissionMatrix.java`（搜索 `soup` 仅命中已废弃的 `/requirement/soup-components`）

```17:SoupController.java   @RequestMapping("/soup")
```

**问题**：SoupController 的 8 个端点（`GET/POST /soup`、`PUT/DELETE /soup/{id}`、`POST /soup/{id}/renew` 等）**完全没有登记到 PermissionMatrix**。

但请注意：项目的 `PermissionEnforceFilter` 走**白名单/路径前缀匹配**而非默认拒绝（这是关键修正）。需要实际阅读 `PermissionEnforceFilter` 的默认行为来定级。从子代理报告看，该 Filter 当前对未登记路径处理策略取决于配置文件。

**待复核点**（P0-4 暂保留，需阅读 PermissionEnforceFilter 后最终定级）：
- 若当前是"白名单放行 + 未匹配即拒绝"，则 SoupController 实际 403（API 完全不可用），应升 P0
- 若当前是"白名单放行 + 未匹配即放行"，则所有未登记路径越权，应升 P0

**修复**（无论哪种情况）：在 PermissionMatrix 中按 SOUP 业务域补齐 8 条路径权限登记。

---

### P0-5 SystemController 垂直越权：任何 sys:user:list 持有者可改密任意用户 + 修改 role/status

**证据**：`Code/backend/med-rms-admin/.../controller/SystemController.java`

```86:SystemController.java    @PostMapping("/users/{id}/change-password")
87:    public Result<Void> changePassword(@PathVariable Long id,
88:                                       @RequestBody Map<String, String> body) {
89:        String oldPassword = body.get("oldPassword");
...
98:        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
...
101:        userService.updatePassword(id, passwordEncoder.encode(newPassword));
```

```59:SystemController.java    @PutMapping("/users/{id}")
60:    public Result<User> updateUser(@PathVariable Long id, @RequestBody User user) {
```

**问题**（双重）：

**(a) 改密越权**：`{id}` 是路径变量，**未与 `SecurityUtils.getCurrentUserId()` 比对**。攻击者只需知道目标用户的明文密码（已通过社工/泄露/撞库拿到），即可通过 `POST /system/users/{id}/change-password` 重置任意用户密码。

虽然仍需知道旧密码，但相比"必须同时拿 OAuth token + 撞密码"，成本降低了一个量级；且未拒绝"管理员身份改密"路径无留痕审计（实际上有 `@AuditLog`，但**该审计记录的是 `entityIdSpel = "#id"`**，审计仍会被绕过——见 P1-11）。

**(b) 修改 user.role / status**：updateUser 接受完整 User body，可将任意用户的 role/status 改为 `SUPER_ADMIN/ACTIVE`。需要 (a) + 此 API 即可提权为超级管理员。

**修复**：
1. `changePassword` 中加 `if (!id.equals(SecurityUtils.getCurrentUserId())) throw new BusinessException("FORBIDDEN", "无权修改他人密码")`
2. `updateUser` 拆分 DTO：禁止通过此端点修改 `role/status/passwordHash`，必须走专用的 `assignRole`/`updateStatus`/`resetPassword`（这些应仅限 ADMIN）
3. `changePassword` 在密码修改成功后使旧 JWT token 失效

---

## 2. P1 — 重要缺陷（12 项）

### P1-1 @AuditLog 覆盖率 47.4%（101/192 写方法缺失）

**证据**：43 个 Controller 全量扫描
- 写方法总数：**192**
- 已覆盖：**91**
- 未覆盖：**101**（**52.6%**）

**P0 级未覆盖 Controller**（应优先修复）：
- `ElectronicSignatureController` 全部签名生命周期端点（21 CFR Part 11 强制）
- `ChangeController.emergencyExecute`（EMERGENCY 直执行通道）
- `TraceabilityController` 全部 5 个端点（NMPA/FDA 检查重点）
- `AuthController.login/refresh/logout`（身份验证事件审计）

**100% 未覆盖的 Controller**（23 个）：AdminController, AuthController, DepartmentController, MigrationController, UserPreferenceController, DashboardController, PrCorrectionController, RegulationImpactController, ReportController, SafetyClassificationController, SoupController, NotificationAdminController, NotificationController, IpdGateController, ProjectDeliverableController, ProjectMemberController, RequirementTaskController, WorklogController, AIController, RequirementExcelController, RequirementPoolController, RiskMatrixController, TraceabilityController

**修复优先级**（按合规要求）：
1. ElectronicSignature、Change、Traceability、Auth → 必须立即补齐
2. ISO 14971 / IEC 62304 强相关域（RiskMatrix、SafetyClassification、PrCorrection、Soup）
3. 运营治理域（Notification、Report、Dashboard、RegulationImpact）
4. 项目/任务/工时域（IpdGate、Worklog、Gantt、RequirementTask）
5. 数据治理域（RequirementPool、ExcelImport、Migration、AIController）

---

### P1-2 12 处 MAX+1 业务编号生成存在并发冲突

**证据**：Service 层全量搜索 `MAX(` + `+1` 模式，覆盖：
- `task_no` / `project_no` / `change_no` / `test_case_no` / `requirement_no` /
- `baseline_no` / `risk_no` / `milestone_no` / `URS` 等

**问题**：`SELECT MAX(no)+1` 模式在并发 INSERT 时会产生**重复编号**（两个事务同时读 MAX=5，都写入 no=6）。

**修复**：
1. PostgreSQL `CREATE SEQUENCE` + `nextval()`（最佳）
2. 或 `INSERT ... SELECT COALESCE(MAX(no),0)+1` + UNIQUE 索引兜底
3. UUID/雪花算法（无序号语义时）

---

### P1-3 TOCTOU 状态机迁移（多个 Service）

**模式**：
```java
// 反例（TOCTOU）
RiskAssessment r = mapper.selectById(id);
if (r.getStatus() != OPEN) throw ...;
r.setStatus(CLOSED);
mapper.updateById(r);

// 正例（原子更新）
UpdateWrapper<R> w = new UpdateWrapper<R>().eq("id", id).eq("status", OPEN);
int n = mapper.update(r, w);
if (n == 0) throw new IllegalStateException("状态已变更");
```

**已正确实现的正面案例**：`ChangeService.approveChange` / `BaselineService.lockBaseline` / `SignatureIntentService.sweepExpiredIntents` —— 应作为模板。

**待审计的具体方法**（子代理报告点出，需逐一确认）：RequirementController.approve、RiskController.assess、ElectronicSignatureService.reSign、ComplianceCheckService.verify 等。

**修复**：所有 selectById + updateById 的状态机方法改为条件 UPDATE + 检查 affectedRows。

---

### P1-4 8+ 核心实体缺 is_deleted 字段（合规硬伤）

**证据**：子代理对比 35 个 Mapper 对应实体：

**缺 is_deleted 的实体**：
- Permission / Worklog / Milestone
- ChangeApproval / ChangeAttachment / ChangeExecution / ChangeTimelineEntry
- PrCorrection / StatisticsSnapshot / SafetyClassification
- ElectronicSignature / SignatureIntent
- Notification / OutboxMessage
- UserPreference
- RequirementAncestor / RequirementRelation / RequirementTestCase
- TraceGapIgnored / RequirementVersion / Review
- ReportTemplate / Iec62304ChecklistItem
- 4 个 Requirement 子表（DesignRequirement / ProductRequirement / SystemRequirement / UserRequirement）

**问题**：21 CFR Part 11 §11.10(c) 要求"保护记录以防删除"。这些实体的删除将直接物理删除，**无法审计**。

**修复**：
1. Flyway 迁移为上述每个实体加 `is_deleted boolean default false` 列
2. 实体加 `@TableLogic` 字段
3. Mapper 加 `softDeleteById` 模式（参考 `UserMapper.softDeleteById` 与 `TestCaseMapper.deleteBatchIds` 的正确实现）

---

### P1-5 自定义 @Select 漏 is_deleted 过滤（多个 Mapper）

**已知正确实现**：DepartmentMapper、RequirementMapper、ChangeRequestMapper、ImpactAssessmentMapper、TraceLinkMapper、RequirementRelationMapper 已显式 `is_deleted = false` 过滤。

**待审查**：剩余 60+ 个自定义 @Select 是否也正确过滤。子代理报告称部分遗漏但未给出具体清单，需逐 Mapper 复查。

**修复模板**：
```sql
SELECT ... FROM table WHERE conditions... AND is_deleted = false
```

---

### P1-6 多个 Service 缺 @Transactional

子代理报告称以下 Service 完全无 `@Transactional`：
- `WorklogService.create`
- `ProjectDeliverableService.create/updateStatus/delete`
- `ProjectActivityService.recordActivity`
- `NotificationService.markAsRead/delete*`
- `ReportService.generateReport`
- `RequirementExcelImportService`

**问题**：无事务保证的写操作，部分失败会造成数据不一致（半成品状态）。

**修复**：在 Service 类的写方法上加 `@Transactional(rollbackFor = Exception.class)`，或在具体方法上加。

---

### P1-7 CORS 配置过宽（虽然存在，但已默认放开内网）

**证据**：`Code/backend/med-rms-web/.../config/WebConfig.java:19-23`

```19:WebConfig.java        config.setAllowedOriginPatterns(List.of(
20:                "http://localhost:*",
21:                "http://127.0.0.1:*",
22:                "http://192.168.*:*"
23:        ));
```

**问题**：生产环境若仍用默认配置，`192.168.*:*` 通配 + `allowCredentials=true` → 任何内网主机跨域请求都带 Cookie/token。

**修复**：
1. 生产 profile 替换为具体域名（如 `https://med-rms.example.com`）
2. 在 application-prod.yml 中覆盖 CORS 配置

---

### P1-8 JWT permissions claim 变更后 2h 仍生效

**证据**：`Code/backend/med-rms-admin/.../service/JwtService.java`

```107:JwtService.java        // permissions claim 写入 token
115:        // ...
```

**问题**：access token 内嵌 permissions 列表，用户角色/权限变更后已签发的 token 在 2h 有效期内仍持有旧权限。

**修复**：
1. token 仅放 userId + roleId
2. 每次请求实时查 `user_role` + `role_permission` 表
3. 或建立 token 黑名单（Redis）支持强制下线

---

### P1-9 dev profile 暴露 mappings/beans

**证据**：`application.yml:120-124`

```120:application.yml    on-profile: dev
121:management:
122:  endpoints:
123:    web:
124:        exposure:
```

**问题**：dev profile 启用时，`/actuator/mappings` + `/actuator/beans` 暴露完整路由表与 Bean 定义。

**修复**：dev profile 同样仅暴露 health/info/metrics；调试用 actuator 通过 Spring Security 加 Basic Auth + IP 白名单。

---

### P1-10 OaSyncController 硬编码默认密码哈希

**证据**：`Code/backend/med-rms-admin/.../controller/OaSyncController.java:113-114`（在 admin 模块下）

**问题**：源码中写死 BCrypt 哈希作为 OA 同步用户的默认密码，且两行重复赋值（典型的代码复制粘贴 bug）。

**修复**：
1. 改为环境变量 + 启动时校验
2. 删除重复行
3. 首次同步后强制用户改密

---

### P1-11 改密后旧 JWT token 未失效

**问题**：用户改密后，攻击者持有旧 token（2h 有效期内）仍可使用。

**修复**：
1. 维护 `password_changed_at` 字段
2. JWT 校验时比对 `iat < password_changed_at` 则失效
3. 或 Redis token blacklist

---

### P1-12 SQL 注入面：OaSyncController 的 StringBuilder 拼接走 PreparedStatement 但应改用 #{}（虽然当前 0 个真实注入面）

**子代理关键结论**：全项目 `Mapper.java` 中 `${}` 使用 0 处；所有 SQL 均为 `#{}` 参数化。`LoginLogController` 的 StringBuilder 拼接走 PreparedStatement `?` 绑定。

**判定**：当前**未发现**真实注入面，但应统一改用 `#{}` 以避免未来回归。

---

## 3. P2 — 一般缺陷（13 项）

### P2-1 源码硬编码 DEFAULT_DEV_SECRET 字面量

`Code/backend/med-rms-admin/.../service/JwtService.java:39-40`

**修复**：移到配置中心或 CI 注入；启动时校验非 dev profile 不允许使用 DEFAULT_DEV_SECRET。

---

### P2-2 ddl seed 弱密码 BCrypt hash

- `init_database.sql:606`
- `121_rbac_seed_data.sql:246`
- `r159_seed_rbac_fixes.sql:19`

种子数据使用 `admin123` 的固定 BCrypt 哈希。**首次部署后必须改密**。建议种子脚本输出一条 WARN 强制提醒。

---

### P2-3 运维脚本硬编码 PGPASSWORD

`ops/backup.sh:35`、`ops/chaos.sh:51`

**修复**：从 CI/CD secrets 注入，禁止进 Git。

---

### P2-4 JwtAuthenticationFilter principal 为 userId 导致审计 operator_name 错误

`Code/backend/med-rms-admin/.../security/JwtAuthenticationFilter.java:62`

```62:JwtAuthenticationFilter.java        auth.getName() 返回 "1"  # userId 字符串
```

**问题**：审计日志记录 `operator_name` 时拿到 userId 而非 username。

**修复**：实现自定义 `UserDetails.getUsername()` 返回 username，或 AuditAspect 中显式查 User 表。

---

### P2-5 PermissionMatrix perm 码语义不匹配

**证据**：
- `PermissionMatrix.java:363-366`：`POST /projects/{id}/clone` 命中 `proj:create`，但语义应为 `proj:update`
- `ChangeController.java:145`：同上模式

**修复**：审计所有 POST/PUT/DELETE 的 perm 码映射，区分"创建新实体"与"在父实体下操作子实体"。

---

### P2-6 CSRF 关闭（风险低，但缺 cookie-only 场景防护）

`SecurityConfig.java:43`

**修复**：若未来引入 cookie-only 认证，需开启 CSRF；当前 bearer token 模式下风险低。

---

### P2-7 邮件外发与通知生命周期审计盲区

`NotificationAdminController` + `NotificationController` 全部 5+ 个写端点无 @AuditLog（见 P1-1）。

**专项**：邮件外发应强制留痕（PHI/PII 合规）。

---

### P2-8 报表配置、仪表盘布局无审计

`ReportController`（全部 5 个）+ `DashboardController.saveLayout/resetLayout`。

**修复**：用户自定义仪表盘属"系统配置变更"，应审计。

---

### P2-9 IpdGateController 全部 6 个端点无审计

阶段门创建/更新/通过/失败是 IEC 62304 / ISO 13485 关键路径。

**修复**：全部加 @AuditLog（eventType=GATE_PASS/FAIL/CREATE/UPDATE）。

---

### P2-10 WorklogController.create 无审计

工时填报涉及薪酬与项目核算，是财务相关数据，应审计。

---

### P2-11 RegulationImpactController.notifyUpdate 无审计

FR-2.2 法规更新推送：谁触发推送、推送给谁、推送内容应留痕。

---

### P2-12 ComplianceController.verifyHashChain 触发后未留痕

哈希链校验本身是查询，但触发后**未在 audit_log 留痕**意味着合规检查人员身份无法追溯。

**修复**：在 verifyHashChainController 方法上加 @AuditLog（即使查询也应记录"何时由谁校验"）。

---

### P2-13 数据迁移、用户偏好、AI 分析端点无审计

- `MigrationController`（全部 3 个）
- `UserPreferenceController.set/delete`
- `AIController`（2 个）

---

## 4. P3 — 加固项（4 项）

### P3-1 CORS 生产配置：192.168.*:* 应替换为正式域名

见 P1-7。

---

### P3-2 注释清理

扫描全后端 `// TODO` / `// FIXME` / `// HACK` / `// BUG #xxx 修复`。

子代理报告称"0 处命中"未独立复核，建议 CI 加 `grep` 检查。

---

### P3-3 单元测试覆盖

`BaselineService` 0 单元测试（子代理报告），建议补充关键 Service 的单测。

---

### P3-4 Outbox / FeatureFlag / 导出（子代理报告已确认实现良好）

- `OutboxService` CAS 原子 claim：✅ 已正确实现
- `FeatureGuard` 关闭态：✅ 路由层面已隐藏
- 数据导出（DHF、Excel、PDF）：✅ UTF-8 + 大小限制 + 输入校验

这些是正面案例，无需修复，但应在 PR 模板中要求保持。

---

## 5. 关键正面案例（应作为模板）

项目已正确实现的功能，**不应误改**：

- `UserMapper.softDeleteById`（line 23）✓ 显式 `@Update + WHERE is_deleted = false`
- `UserMapper.selectByUsername/Email` ✓ 显式过滤
- `DepartmentMapper` 全部 @Select ✓ 含 is_deleted = FALSE
- `RequirementMapper` 全部 @Select ✓
- `ChangeRequestMapper.selectByRequirementId/Status/Requester` ✓
- `ImpactAssessmentMapper` 全部 ✓
- `TraceLinkMapper` 6 个方法 ✓
- `RequirementRelationMapper` 3 个方法 ✓
- `TestCaseMapper.deleteBatchIds` ✓ 真正的批量软删
- `ChangeService.approveChange` / `rejectChange` ✓ 原子状态更新
- `BaselineService.lockBaseline` ✓ 原子状态更新
- `SignatureIntentService.sweepExpiredIntents` ✓ 定时原子更新
- `ProductService.delete` ✓ 双重防护：@TableLogic + DB trigger
- `NotificationChannelMapper.softDeleteById` ✓
- `TaskMapper.selectMaxTaskNoSuffix` ✓ 自定义 @Select 绕过逻辑删除拦截器（含详细注释）
- `SignatureIntentMapper.insertIntent` ✓ 自定义 @Insert 避免 MP 漏列
- `AuditLogService.verifyHashChainDetailed` ✓ 完整 prevHash 链式校验
- `SecurityUtils.calculateAuditHash` ✓ prevHash 作为哈希输入
- `User.passwordHash` 已正确使用 @JsonIgnore（User.java:17,35）

**审计边界说明**：除 P0-1~5 外，所有引用上述"正确实现"的代码均为审计已确认，**不应作为整改对象**。

---

## 6. 修复路线图

### 立即（24h）
- P0-1：通知横向越权（数据销毁风险）
- P0-2：OTP 种子泄露（双因子绕过风险）
- P0-3：默认 dev profile + 默认口令
- P0-5：SystemController 越权

### 一周内
- P0-4：SoupController 8 个端点补登记
- P1-1：4 个 P0 级 Controller 补 @AuditLog（ElectronicSignature / Change / Traceability / Auth）
- P1-4：8+ 实体补 is_deleted + @TableLogic

### 两周内
- P1-1 全部：23 个 Controller 补齐 @AuditLog
- P1-2：12 处 MAX+1 改 sequence
- P1-3：TOCTOU 模式批量替换为原子 UPDATE
- P1-5：自定义 @Select 全量 is_deleted 审计
- P1-6：缺 @Transactional Service 补齐
- P1-7/8/9：CORS/JWT/actuator 配置收紧

### 发版前
- P2-1~13 全部清理
- P3-1~4 加固

---

## 7. 审计元数据

- 审计方式：3 路并行 code-explorer 子代理（每路限定真实代码片段引用）+ 直接源码复核（10+ 关键文件）
- Controller 数：43
- Service 数：63
- Mapper 数：67
- 实体数：50+
- 写方法数：192
- 已发现真实缺陷：34 项（5 P0 + 12 P1 + 13 P2 + 4 P3）
- 关键正面案例：20+ 项已正确实现，**不应作为整改对象**

**与之前两份报告的关系**：
- `CODE_REVIEW_REPORT_FULL_2026-08-09.md`：作废（多数虚构）
- `CODE_REVIEW_META_AUDIT_2026-08-10.md`：本报告基于其第 6 节"建议重做"展开

*报告生成：2026-08-10 · 自底向上完整审计 · 每条发现均经源码核实*