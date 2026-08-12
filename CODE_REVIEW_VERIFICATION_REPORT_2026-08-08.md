# Med-RMS 代码审核独立确认报告（2026-08-08）

> 本报告对 `CODE_REVIEW_REPORT_FULL_2026-08-08.md`（简称"原报告"）中列出的 **22 项缺陷**逐项独立核查，给出"完全确认 / 部分确认需修正 / 误报"的判定，并指出原报告中需要修正或补充的事实性错误。
> 审核人：本次独立代码审查（不依赖前轮子代理结论）
> 核查范围：后端 11 个模块 + 前端 Vue3 源码

---

## 0. 核查结论摘要

| 判定 | 数量 | 编号 |
|------|------|------|
| ✅ 完全确认属实 | **11** | H-2、H-4、H-5、M-1、M-9、C-1、I-3、L-6、L-7、M-2、M-6 |
| ⚠️ 部分确认 / 需修正描述 | **5** | H-1、H-3、M-3（上轮误判误报）、M-4、M-2 |
| ❌ 误报 | **4** | M-5（FeatureFlag 部分）、C-2、C-3（Impact 部分）、L-4（AuditAspect 部分） |
| 🆕 新发现（应新增） | **1** | **C-NEW**：前端 `impactAssessmentApi.create/update` 调用不存在的端点（实际是前端死代码） |

> 原报告总体方向正确，22 项中有 **16 项（含 5 项部分确认）成立**，**4 项误报**，**2 项路径/描述需修正**，并新发现 1 项前端死代码。
> ⚠️ **自我纠正**：上轮将 M-3 判为"误报"不准确——核心业务表已修复，但 `RequirementTestCaseMapper` 与 `TraceGapIgnoredMapper` 仍有 4 个 `@Select` 漏 `is_deleted`，原报告 M-3 **部分属实，应保留**。

---

## 1. HIGH 级（3 项）

### H-2【HIGH】RBAC 白名单裸前缀匹配（绕过鉴权）— ✅ **完全确认，但路径描述有误**

**核实证据**：
- 实际路径：`med-rms-admin/src/main/java/com/zhutao/medrms/admin/security/PermissionEnforceFilter.java`（**原报告写为 `med-rms-web` 是错的**）
- 行 40-50 白名单定义（含 `/actuator`、`/error`、`/auth/login`、`/v3/api-docs`、`/swagger-ui`、`/feature/flags`、`/api/health`）
- 行 104-111 `isWhitelisted` 方法：
  ```java
  for (String w : WHITELIST) {
      if (pathMatcher.matchStart(w, path) || path.startsWith(w)) {
          return true;
      }
  }
  ```
- **裸前缀匹配属实**：攻击者可构造 `/actuatorX`、`/errorX`、`/auth/loginAdminList` 等路径绕过鉴权
- **修复建议仍有效**：改用 `path.equals(w) || path.startsWith(w + "/")` 精确匹配，或用 Spring `AntPathMatcher.match("/actuator/**", path)`

> 🔧 **原报告路径需修正**：`med-rms-web` → `med-rms-admin`

---

### H-3【HIGH】JWT 密钥配置空默认值 — ⚠️ **部分确认，需修正描述**

**核实证据**：
- `application.yml:49`：`secret: ${MED_RMS_JWT_SECRET:${JWT_SECRET:}}`（空默认值）
- `JwtService.java:41`：`@Value("${med-rms.jwt.secret}") private String secret = DEFAULT_DEV_SECRET;`
- `JwtService.java:60-66` PostConstruct validateSecret：
  - **校验 1（行 62-66）**：若 `secret.length() < 32`，抛 `IllegalStateException` → **fail-closed，启动失败**
  - **校验 2（行 71-76）**：非 dev/test profile 下若 `DEFAULT_DEV_SECRET.equals(secret)`，抛异常 → **fail-closed**

**修正项**：
- ❌ **原报告"空值可绕过默认密钥检查"是误报**：实际代码会因长度 < 32 启动失败，**不是绕过，是启动失败**
- ✅ **真实风险（保留）**：
  - 长度 ≥32 但熵低的弱密钥不会被代码拦截，依赖运维规范
  - `JwtService.java:77-79` 日志打印密钥前 8 字符 → 真实密钥泄漏风险（已在 I-3 体现）
  - HS512 算法本身安全，但密钥强度是运维责任
- **建议**（比原报告更准确）：
  - 启动期增加弱口令字典检测（拒绝常见弱密钥如 `password123...`）
  - 至少 64 字符随机密钥而非 32 字符
  - 关键：**不能空启动**这一点代码已做到，文档化运维规范即可

---

### C-1【HIGH】SOUP 契约断链 — ✅ **完全确认属实**

**核实证据**：
- 前端 `compliance.ts`：`soupApi` 调用 `/soup`、`/soup/{id}`、`/soup/{id}/renew`（行 49, 52, 55, 58, 61, 64）
- 后端 `SoupController.java:17`：`@RequestMapping("/requirement/soup-components")`
- **路径不一致**：前端 `/soup/*` ↔ 后端 `/requirement/soup-components/*`，**所有 SOUP 前端调用均 404**
- 这是真实的功能不可用阻塞

> 💡 **修复方向（更明确）**：
> - 方案 A（前端改）：`compliance.ts` 将 `/soup` 改为 `/requirement/soup-components`
> - 方案 B（后端改）：新增一个 `@RequestMapping("/soup")` 的转发 Controller
> - 推荐方案 A，破坏面最小

---

## 2. MEDIUM 级（9 项）

### M-1【MEDIUM】`inSql` 字符串拼接 — ✅ **完全确认**

**核实证据**：
- `DhfEvidenceService.java:192`：
  ```java
  inSql("requirement_id",
        "SELECT id FROM req_schema.t_requirement WHERE project_id = "
        + (projectId != null ? projectId : "0") + " AND is_deleted = false")
  ```
- 应改为 `inSql("requirement_id", "SELECT ... WHERE project_id = ? AND is_deleted = false", projectId)`
- **真实风险有限**（projectId 是 Long，类型安全，无法 SQL 注入），但风格错误且维护性差

---

### M-2【MEDIUM】DHF 签名日志跨项目可见 — ✅ **确认属实，但需细分**

**核实证据**：
- `DhfEvidenceService.java:108`：`pkg.put("signatureLogs", listRecentSignatures());`
- `DhfEvidenceService.java:235-252` `listRecentSignatures()` 方法：
  ```java
  // R236.2 DATA-028: 按 projectId 限定；如无 projectId 字段则取全量最近 50 条
  public List<ElectronicSignature> listRecentSignatures() {
      return electronicSignatureMapper.selectList(null).stream()
          .limit(EVIDENCE_LIMIT).collect(Collectors.toList());
  }
  ```
- **真实风险**：`signature_logs` 表无 project_id 字段，强制全表扫描（GLOBAL）→ 任何项目用户生成 DHF 可看到其他项目的签名记录
- `auditLogs`（行 217-222）也是 `selectList(null)` + stream limit，技术上同问题
- 因电子签名已关闭，本项风险大幅降低

> 📝 **原报告描述基本正确**，可补充说明：`signature_logs` 表无 project_id 是数据库设计层面的限制，需后续 schema 改造才能根除

---

### M-3【MEDIUM】自定义 `@Select` 未追加 `is_deleted` — ⚠️ **部分属实（上轮误判"误报"，本次纠正）**

**【自我纠正】**：上轮核查仅抽查 7 个 mapper 即判定"全部已修复 → 误报"，**结论草率、不准确**。本次对全量自定义 `@Select` 重新扫描，发现原报告点名的 `Trace*Mapper` 范畴中仍有**两处确凿遗漏**：

| Mapper | SQL | 对应表 | 表是否有 is_deleted | 判定 |
|--------|-----|--------|---------------------|------|
| `RequirementTestCaseMapper:14` | `SELECT * FROM trace_schema.t_requirement_test_case WHERE requirement_id = ?` | `t_requirement_test_case` | ✅ 有（`init_database.sql:109`） | ❌ **漏过滤，确属 M-3** |
| `RequirementTestCaseMapper:18` | `... WHERE test_case_id = ?` | 同上 | ✅ 有 | ❌ **漏过滤，确属 M-3** |
| `TraceGapIgnoredMapper:14` | `SELECT * FROM trace_schema.t_trace_gap_ignored WHERE project_id = ?` | `t_trace_gap_ignored` | ✅ 有（`r160:22`） | ❌ **漏过滤，确属 M-3** |
| `TraceGapIgnoredMapper:17` | `... AND gap_type = ? AND requirement_id = ? LIMIT 1` | 同上 | ✅ 有 | ❌ **漏过滤，确属 M-3** |

**已正确追加 `is_deleted = false` 的 mapper（确认已修复，不属缺陷）**：
- `RequirementMapper`（行 12/15/18）、`RequirementRelationMapper`（行 15/18/21，含注释"DATA-001 修复"）
- `ChangeRequestMapper`、`TraceLinkMapper`、`RiskAssessmentMapper`、`DhfEvidenceMapper`、`TestCaseMapper`

**表本身无 `is_deleted` 列、因此未过滤属合理（非缺陷）的 mapper**：
- `Iec62304ChecklistMapper` → `t_iec62304_checklist` 无该列（`090_iec62304_checklist.sql`）
- `ChangeApprovalMapper` → `t_change_approval` 无该列（`117_change_p0_tables.sql`）
- `ChangeTimelineMapper` → `t_change_timeline` 无该列（`131_change_timeline.sql`）
- `ElectronicSignatureMapper` → `t_signature_record` 无该列，用 `is_valid`（`init_database.sql:245`）

> 🔧 **修正上轮结论**：M-3 **不是误报，而是"部分属实"**——核心业务表（`RequirementMapper` 等）已全面修复，但 `RequirementTestCaseMapper` 与 `TraceGapIgnoredMapper` 仍有 4 个 `@Select` 绕过逻辑删除。原报告方向正确，应保留 M-3 并追加具体遗漏清单。

---

### M-4【MEDIUM】并发 TOCTOU — ⚠️ **部分确认（lockBaseline 已修复，但 reSign 仍有风险）**

**核实证据**：
- **`BaselineService.lockBaseline`（行 113-180）已用原子 UPDATE 替代 read-then-write**（行 137-148）：
  ```java
  // R198-4 修复：原子 UPDATE 替代 read-then-write
  // 防止并发竞态：300 并发锁同 baseline 时只有 1 次成功
  int updated = baselineMapper.update(null, new UpdateWrapper<Baseline>()
      .eq("id", baselineId)
      .eq("status", "DRAFT")
      .set("status", "LOCKED")
      .set("lock_by", currentUserId)
      .set("lock_at", LocalDateTime.now()));
  if (updated == 0) {
      throw BusinessException.conflict("BS0301", "基线已被其他用户锁定或状态非 DRAFT");
  }
  ```
  - ✅ **lockBaseline TOCTOU 已修复**
- **`unlockBaseline`（R226.3 DATA-019）同样已用原子 UPDATE**
- ❌ **`ElectronicSignatureService.reSign`（行 273-327）仍有 TOCTOU 风险**：
  - 行 283：先 `selectById(oldSignatureId)`（read）
  - 行 292-295：`oldSig.setIsValid(false); signatureMapper.updateById(oldSig)`（无乐观锁/原子条件）
  - 行 320：`signatureMapper.insert(newSig)`
  - 两个并发 reSign 可能产生两个新签名

> 🔧 **修正原报告**：
> - M-4 关于 lockBaseline/unlockBaseline 部分应**删除**
> - 应新增"M-4-RE：reSign TOCTOU"作为遗留风险

---

### M-5【MEDIUM】`@AuditLog` 缺失 — ❌ **误报（部分）**

**核实证据**：
- **`FeatureFlagController`**（`med-rms-web`）只有 **1 个 GET 端点**（`/feature/flags`），**没有修改入口** → 原报告"FeatureFlagController 可未授权修改 flag"**是误报**
- **系统中没有 `UserController`**（用户增删改入口）：
  - 只有 `UserPreferenceController.java`（用户偏好）
  - `AuthController.java` 只有登录/登出/refresh/me/has-perm 端点
- **`RiskController` / `RequirementController` / `TraceLinkController` / `ChangeController` 等的写操作均有 `@AuditLog` 覆盖**

> 🔧 **修正原报告**：
> - M-5 中"FeatureFlagController 守卫"部分应删除（不存在风险）
> - 用户管理审计缺失应改为"系统中无用户管理入口，暂无需审计；若未来引入 UserController，须强制 @AuditLog"

---

### M-6【MEDIUM】`record_hash` 覆盖字段不全 — ✅ **确认属实**

**核实证据**：
- `SecurityUtils.java:131-146` `calculateAuditHash` 包含字段：
  ```java
  String.join("|", nullToEmpty(prevHash), nullToEmpty(eventType),
              nullToEmpty(entityType), String.valueOf(entityId),
              String.valueOf(operatorId), nullToEmpty(operation),
              oldValueJson, newValueJson, nullToEmpty(timestamp));
  ```
- **缺失字段**：`client_ip`、`session_id`、`tenant_id`、`user_agent`
- 攻击者篡改审计日志时，绕过这些上下文字段的哈希验证更容易

---

### M-7【MEDIUM】编号生成 MAX+1 并发不安全 — ✅ **确认属实**

**核实证据**：
- `ChangeService.java:837-847` `generateChangeNo` 用 `SELECT MAX(CAST(RIGHT(change_no, 4) AS INTEGER))` + 拼接
- `RequirementPoolService.java`、`ReviewMapper.java`、`TaskMapper.java` 同样用 MAX 模式
- `BaselineService.java:94` 用 `String.format("BL-%d-%04d", projectId, count + 1)`（count 是 selectCount 结果，并发不安全）
- 真实风险：两个并发请求同时读到 max=5，都生成 0006 → 编号冲突

---

### M-8【MEDIUM】Outbox 并发 claim — ✅ **已修复（保留为历史修复记录）**

**核实证据**：
- `OutboxService.java:86-98` 已有原子 UPDATE（CAS PENDING → PROCESSING）：
  ```java
  // R230.4 DATA-042 修复：CAS UPDATE 避免重复 claim
  int updated = outboxMapper.claim(...)
  ```
- 单实例下原子 UPDATE 已避免重复；多实例需 advisory lock 或 SKIP LOCKED（已知约束）

> 📝 **建议**：原报告 M-8 应标注"R230.4 已修复"，仅留作历史追溯，可降为 LOW

---

### M-9【MEDIUM】Risk 入参校验缺失 — ✅ **完全确认**

**核实证据**：
- `RiskController.java:64-73` `RiskAssessRequest` 8 个字段**全部裸字段，无 `@NotBlank`/`@Valid` 注解**：
  ```java
  public static class RiskAssessRequest {
      private Long requirementId;
      private String riskLevel;     // null 时 calculateRiskScore 抛 NPE
      private String hazardLevel;
      private String hazardSource;
      private String hazardSituation;
      private String harm;
      private String controlMeasure;
      private Long assessedBy;
  }
  ```
- `RiskController.java:98-106` `FmeaRequest` 同样裸字段
- `RiskAssessmentService.java:37` `calculateRiskScore(riskLevel, hazardLevel)` 中 `switch (riskLevel)` 在 riskLevel 为 null 时 NPE
- 前端 `riskApi.assess` 漏传 `assessedBy` 等字段（数据完整性问题，但与入参校验无关）

---

## 3. LOW 级（10 项）

### H-1【LOW 已降级】RSA 私钥内存态 — ✅ **确认属实**

**核实证据**：
- `SecurityUtils.java:31-41` 静态块生成 RSA_KEY_PAIR，不持久化
- 重启后历史签名不可验签；多实例私钥不一致
- 因电子签名已正式关闭，降为 LOW 合理（仅历史数据归档考虑）

---

### H-4【LOW 已降级】签名设置 IDOR — ✅ **确认属实，但实际威胁低**

**核实证据**：
- `ElectronicSignatureController.java:157-214` 所有 `/settings/{userId}` 端点无归属校验
- **但前端 UI 不主动越权**：
  - `ESignSettings.vue:141` `userId = Number(userStore.userInfo?.id || 0)`（用当前用户 id）
  - `ESignSettings.vue:158,184` 调用 `getSettings(userId)` 和 `changeSignaturePassword(userId, ...)` 都是当前用户
- **真实威胁**：需要构造特殊 HTTP 请求才能利用，UI 不会主动暴露攻击入口
- 因电子签名已关闭，降为 LOW 合理

> 📝 **建议保留**：即使关闭，接口设计的 IDOR 仍是隐患，应在控制器加 `userId == getCurrentUserId()` 校验作为代码卫生

---

### H-5【LOW 已降级】签名禁用时端点暴露 — ✅ **确认属实**

**核实证据**：
- `ElectronicSignatureController` 中有 10 处调用 `featureGuard.requireSignatureEnabled()`
- 无守卫的端点（11 个）：listIntents, getIntent, verifySignature, getSignaturesForEntity, disableOtp, verifyPassword, verifyOtp, getOtpUri, getSettings, listSignatures, getSignature
- 因电子签名已关闭，降为 LOW

---

### L-4【LOW】异常吞没 — ❌ **误报（部分）**

**核实证据**：
- `AuditAspect.java:50` `catch (Throwable businessError)` → 抛出去，不吞
- `AuditAspect.java:63` `catch (Exception e)` → 吞 + log.warn，**有意识的设计**（"审计失败不应阻塞业务"）
- `BaselineService.java:72-76, 175-177, 233-235, 276-278` 全部 catch (Exception) + log.warn，**有意识的设计**（基线锁定联动失败不应阻塞主流程）

> 🔧 **修正原报告**：这些 catch 都是**合理设计**，不属 bug。建议 L-4 重新表述为"全局异常处理已存在；个别非关键联动吞异常属可接受设计"。

---

### L-5【LOW】`anyRequest().permitAll()` — ⚠️ **部分确认（是有意识的设计）**

**核实证据**：
- `SecurityConfig.java:55` `anyRequest().permitAll()`
- 注释明确说明：JWT 过滤器代替 Spring Security 默认鉴权，授权靠 `PermissionEnforceFilter`
- 这不是 bug，是有意识的设计选择
- **但若 JwtAuthenticationFilter 被绕过，整个认证体系会 fail-open** → 真实依赖链脆弱

> � **建议保留**为 LOW，强调"双过滤器链路脆弱性"作为风险记录

---

### L-6【LOW】DB 默认口令 `postgres` — ✅ **完全确认**

**核实证据**：
- `application.yml:10` `password: ${DB_PASSWORD:postgres}` — 默认弱口令
- `application.yml:9` `username: ${DB_USERNAME:postgres}` — 默认用户
- 生产应通过环境变量覆盖（已支持）

---

### L-7【LOW】`logging.file.name` 硬编码 Windows 路径 — ✅ **完全确认**

**核实证据**：
- `application.yml:86` `name: C:/temp/medrms-app.log` — **Windows 路径硬编码**
- 跨平台部署（Linux/macOS）会失败
- 应改为 `${LOG_PATH:logs/medrms-app.log}` 或纯相对路径

---

### I-3【LOW】日志打印密钥前缀 — ✅ **完全确认**

**核实证据**：
- `JwtService.java:77-79`：
  ```java
  System.out.println("[JwtService] 密钥校验通过 profile=" + Arrays.toString(activeProfiles)
      + ", secret 前缀=" + (secret.length() > 8 ? secret.substring(0, 8) + "..." : secret));
  ```
- 真实密钥前缀泄漏到 stdout/容器日志
- 应改为只打印长度不打印内容

---

### C-2【LOW】`decompose` 端点不存在 — ❌ **误报**

**核实证据**：
- 后端 `RequirementController.java:97-104` 实际存在 `POST /{id}/decompose` 端点
- 前端 `requirement.ts:71-72` 调用 `api.post('/requirements/${id}/decompose', childRequirement)`
- **完全一致，不存在契约断链**

---

### C-3【LOW】契约校验缺失 / 死代码 — �️ **部分确认**

**核实证据**：
- **前端 `notificationAdminApi`**（notification.ts:87-115）：
  - 全 views 代码库中**0 处调用** → **死代码**，但不是契约断链
  - 端点前缀用单数 `/notification/email/*`，与后端复数 `/notifications/email/*` 不一致（但前端不调用，影响为零）
- **前端 `impactAssessmentApi.create/update`**（change.ts:95-99）：
  - 全 views 代码库中**0 处调用** → **死代码**
  - 调用 `/changes/impact` POST 和 `/changes/impact/{id}` PUT，但后端**无 ImpactAssessmentController**
  - 是**真实的契约断链**，但前端不调用不触发

> 🆕 **新增发现 C-NEW（LOW）**：应清理前端 `impactAssessmentApi.create/update` 和 `notificationAdminApi` 全部方法

---

## 4. I 级（合规/集成）

### I-1【MEDIUM】电子签名已正式永久关闭 — ✅ **决策已确认**

**核实证据**：
- `application.yml:90-92` `compliance.modules.signature: false`
- `FeatureGuard.java` 正确抛 SY0503
- 决策已记录在报告中

---

### I-2【MEDIUM】`actuator` 暴露 mappings/beans — ✅ **完全确认**

**核实证据**：
- `application.yml:99` `include: health, info, metrics, mappings, beans` — 全部暴露
- `mappings` 会泄露所有 controller 路径（攻击侦察便利）
- `beans` 会泄露应用结构
- 应至少排除 `mappings`/`beans`，或加 Spring Security 控制

---

## 5. 修正总览（原报告需更新）

### 5.1 应从报告中删除的误报（4 项）

| 编号 | 误报内容 | 修正建议 |
|------|----------|----------|
| **M-5**（FeatureFlagController） | 守卫缺失导致未授权修改 | ❌ 控制器只有 GET，无修改入口 |
| **M-5**（用户管理） | 用户增删改无审计 | ⚠️ 系统中无 UserController，暂不存在 |
| **C-2** | decompose 端点不存在 | ❌ 后端 RequirementController.java:97-104 实际有端点 |
| **L-4** | 异常吞没（AuditAspect/BaselineService） | ⚠️ 全部是有意识的设计（审计失败/联动失败不应阻塞主流程） |

> ⚠️ **M-3 已从误报列表移除**：经二次全量扫描，原报告 M-3 方向正确（部分属实），详情见正文 M-3 节。

### 5.2 应修正描述（4 项）

| 编号 | 原描述 | 修正后 |
|------|--------|--------|
| **H-2** | 路径 `med-rms-web/.../config/security/PermissionEnforceFilter.java` | 路径：`med-rms-admin/.../security/PermissionEnforceFilter.java` |
| **H-3** | "空值可绕过默认密钥检查" | ❌ 改为：空值实际 fail-closed（启动失败）；真正风险是弱密钥长度 ≥32 但熵低 |
| **M-4** | lockBaseline/unlockBaseline TOCTOU | ❌ 已用原子 UPDATE 修复；reSign 仍有风险 |
| **M-2** | DHF 签名日志全局可见 | ✅ 确认，但细分：`signature_logs` 无 project_id 是 schema 设计限制 |

### 5.3 新发现（1 项）

| 编号 | 新发现 |
|------|--------|
| **C-NEW（LOW）** | 前端 `impactAssessmentApi.create/update`（change.ts:95-99）调用后端不存在的 `/changes/impact` POST 和 `/changes/impact/{id}` PUT；前端 0 处调用，属死代码 + 契约不一致 |

---

## 6. 最终确认清单（按优先级）

### P0 仍为 HIGH（3 项）— 与原报告一致

| # | 编号 | 标题 | 备注 |
|---|------|------|------|
| 1 | **H-2** | RBAC 白名单裸前缀匹配 | 路径已修正 |
| 2 | **H-3** | JWT 密钥配置 | 描述已修正（非绕过而是启动失败，但需弱密钥检测） |
| 3 | **C-1** | SOUP 契约断链 | 完全确认 |

### P1 MEDIUM（7 项有效）

| # | 编号 | 标题 | 备注 |
|---|------|------|------|
| 1 | M-1 | inSql 拼接 projectId | 风格问题 |
| 2 | M-2 | DHF 签名日志跨项目 | 电子签名关闭后风险降低 |
| 3 | **M-4-RE** | reSign TOCTOU | **新增**：仅 reSign 仍有风险 |
| 4 | M-6 | record_hash 缺上下文字段 | 真实风险 |
| 5 | M-7 | 编号 MAX+1 并发不安全 | 真实并发风险 |
| 6 | M-9 | Risk 入参校验缺失 | 真实 NPE 风险 |
| 7 | I-1 | 电子签名关闭 + 线下 SOP | 合规记录 |
| 8 | I-2 | actuator 暴露 mappings/beans | 真实暴露面 |

### P2 LOW（10 项有效）

| # | 编号 | 标题 | 备注 |
|---|------|------|------|
| 1 | H-1 | RSA 私钥内存态 | 电子签名关闭后已降级 |
| 2 | H-4 | 签名设置 IDOR | 实际威胁低，代码卫生问题 |
| 3 | H-5 | 签名禁用时端点暴露 | 电子签名关闭后已降级 |
| 4 | L-5 | anyRequest().permitAll() 设计意图 | 有意识设计，双链路脆弱 |
| 5 | L-6 | DB 默认口令 postgres | 运维覆盖即可 |
| 6 | L-7 | logging.file.name Windows 路径 | 跨平台 bug |
| 7 | I-3 | 日志打印密钥前缀 | 真实密钥泄漏 |
| 8 | C-NEW | impactAssessment 死代码 + 契约不一致 | 前端应清理 |

### 已删除（原报告误报，不计入）

| 原编号 | 删除原因 |
|--------|----------|
| M-5（FeatureFlag） | 控制器只有 GET，无修改入口 |
| M-5（用户管理） | 系统无 UserController |
| C-2 | decompose 端点实际存在 |
| L-4（AuditAspect/BaselineService） | 全部是有意识的设计 |

> ⚠️ **M-3 已移出删除列表**：二次核查确认 `RequirementTestCaseMapper`、`TraceGapIgnoredMapper` 仍漏 `is_deleted`，原报告 M-3 部分属实，应保留并细化。

---

## 7. 结论

本次独立核查覆盖原报告 22 项缺陷：
- **16 项确认属实**（含 5 项需修正描述）
- **4 项误报**（M-5 部分、C-2、L-4 部分）
- **1 项新发现**（C-NEW 前端死代码 + 契约不一致）

**真正阻塞交付的 HIGH 项**：仅 3 项（H-2、H-3、C-1），与原报告一致。  
**MEDIUM 真正有效**：8 项（原 9 项中仅 M-5 部分删除，M-3 保留为部分属实）。  
**LOW 真正有效**：8 项（含新发现 C-NEW）。

总体而言，原报告审计质量较高，主要问题集中在：
1. **少数误报**（M-5 部分、C-2、L-4 部分）
2. **路径描述错误**（H-2 实际在 `med-rms-admin` 而非 `med-rms-web`）
3. **上轮自我纠正**：M-3 曾误判为"误报"，二次全量扫描确认原报告方向正确（`RequirementTestCaseMapper`、`TraceGapIgnoredMapper` 仍漏 `is_deleted`）

**建议下一步**：
1. 按修正后 P0/P1/P2 修复路线图推进
2. 优先修复 P0 三项 + 清理前端死代码（C-NEW）
3. 删除或降级原报告中 4 项误报，避免误导后续维护

---

## 8. 二次复核（重新核实）结论 — 2026-08-08 第二轮

**复核方法**：不预设本报告结论正确，逐项回到真实源码重新读取（PermissionEnforceFilter / JwtService / SoupController / DhfEvidenceService / RiskController / SecurityUtils / AuditAspect / FeatureFlagController / ChangeController / application.yml / change.ts 等），对报告每一条判定复核。

### 8.1 经重核依然成立的判定（无变化）

| 编号 | 结论 | 复核关键证据 |
|------|------|--------------|
| H-1 | ✅ 确认 | `SecurityUtils.java:29-41` RSA 私钥静态块生成、不持久化 |
| H-2 | ✅ 确认（路径已修正） | `PermissionEnforceFilter.java:75-79` 未登记路径默认 403；`isWhitelisted` 用 `matchStart` 前缀匹配 → `/actuatorX` 类未登记路径被白名单放行 |
| H-3 | ⚠️ 部分确认 | `JwtService.validateSecret` 空值/短密钥/非 dev 默认密钥均**启动失败**（fail-closed），不存在"绕过"；风险为弱密钥熵 |
| H-4 | ✅ 确认 | `ElectronicSignatureController:157-214` 所有 `/settings/{userId}` 无归属校验 |
| H-5 | ✅ 确认 | `ElectronicSignatureController` 11 个端点无 `featureGuard.requireSignatureEnabled()` |
| C-1 | ✅ 确认 | 前端 `soupApi` → `/soup/*`；后端 `SoupController` → `/requirement/soup-components/*`，完全 404 |
| M-1 | ✅ 确认 | `DhfEvidenceService.java:192` 字符串拼接 `project_id = " + (projectId!=null?projectId:"0")` |
| M-4 | ⚠️ 部分确认 | `reSign`（ElectronicSignatureService.java:283-320）selectById→update/insert 无乐观锁；`lockBaseline` 已原子 UPDATE 修复 |
| M-6 | ✅ 确认 | `SecurityUtils.calculateAuditHash:131-145` 无 client_ip/session_id/tenant_id/user_agent |
| M-7 | ⚠️ 部分确认 | `ChangeService.generateChangeNo:838-841` 已改 `MAX(...)`，非原子但并发极低 |
| M-9 | ✅ 确认 | `RiskController.RiskAssessRequest:64-73` 全裸字段；`assess` 调 `calculateRiskScore` 时 riskLevel=null 会 NPE |
| L-4 | ❌ 误报 | `AuditAspect:50` 抛、`:63` 吞属有意识设计 |
| L-5 | ⚠️ 部分确认 | `SecurityConfig:55` permitAll 是有意识设计，但双过滤器链路脆弱 |
| L-6 | ✅ 确认 | `application.yml:9-10` postgres/postgres 默认口令 |
| L-7 | ✅ 确认 | `application.yml:86` `C:/temp/medrms-app.log` Windows 硬编码 |
| I-2 | ✅ 确认 | `application.yml:99` mappings/beans 暴露 |
| C-2 | ❌ 误报 | `RequirementController.java:98` 有 `POST /{id}/decompose` |
| M-5 | ❌ 误报 | `FeatureFlagController` 仅 GET；系统无 UserController |
| M-3 | ⚠️ 部分属实（上轮纠正） | `RequirementTestCaseMapper`(14/18)、`TraceGapIgnoredMapper`(14/17) 漏 `is_deleted` |

### 8.2 经重核需修正/补充的判定

**(a) C-3 / C-NEW 描述不严谨（修正）**
- 报告 C-3 称 `impactAssessmentApi.*` "前端 0 处调用"——**不准确**。重读 `change.ts`：
  - `listByChanges`(行 91-93) 调 `/changes/impacts/batch` → **后端 ChangeController 有此端点**（存在）
  - `create`(96) 调 `/changes/impact` POST、`update`(99) 调 `/changes/impact/{id}` PUT → **后端无控制器**（死代码 + 契约断链）
- **修正**：C-NEW 应精确表述为"仅 `impactAssessmentApi.create/update` 是死代码（无后端），`listByChanges` 正常"；`notificationAdminApi` 确为 0 调用死代码。

**(b) M-2 应补充"审计日志标注与实现不符"（新证据）**
- `DhfEvidenceService.java:219` `listRecentAuditLogs` 用 `selectList(null)` **全表扫描**（无 projectId 过滤）
- 但 `getDhfManifest:158` 将 `auditLogs` **标注 scope=PROJECT**
- **真实缺陷**：审计日志本应按项目隔离却返回全量，且 manifest 误导为 PROJECT。比原报告描述的"签名日志 GLOBAL"更严重（审计日志合规敏感度更高）。建议 M-2 细化加入此条。

**(c) L-6 应补充"dev SQL 调试日志开启"（新证据）**
- `application.yml:83` `com.zhutao.medrms: DEBUG` → MyBatis `Slf4jImpl`(行 61) 会打印**全部 SQL 及参数**。dev 下可能含敏感字段，且 DEBUG 级生产若沿用会泄密。建议 L-6 关联此点。

**(d) H-4 关联 M-5 缺漏（新证据）**
- `ElectronicSignatureController:157 getSettings` **无 `@AuditLog`**，而 H-5 所列 11 个无守卫端点同样多数无审计注解。原报告 M-5 聚焦 FeatureFlag/用户管理，但 esignature settings 端点（含未授权读取他人设置）**既无鉴权也无审计**——属 M-5 范畴的延伸，建议补入。

### 8.3 二次复核最终统计

| 类别 | 数量 | 编号 |
|------|------|------|
| ✅ 确认属实 | 11 | H-1, H-2, H-4, H-5, C-1, M-1, M-6, M-9, L-6, L-7, I-2 |
| ⚠️ 部分确认/需修正 | 7 | H-3, M-3, M-4, M-7, L-5, M-2（补充审计日志不符）, C-3/C-NEW（精确化） |
| ❌ 误报 | 4 | M-5, C-2, L-4, （M-3 已移出） |
| 🆕 新发现/细化 | 4 | M-2 审计日志标注不符、L-6 dev SQL 调试日志、H-4×M-5 签名设置无审计、C-NEW 精确化 |

> **结论**：原报告 22 项经独立 + 二次两轮核查，**方向正确**。P0 三项（H-2/H-3/C-1）仍成立；真正需从报告剔除的误报仅 4 项（M-5 全部、C-2、L-4）。其余均应按修正后描述保留并推进修复。

---

> 本报告所有结论均通过实际代码读取验证，引用文件路径均经过实测。二次复核（第 8 节）于 2026-08-08 完成，对原判定做了 4 处修正/补充。
