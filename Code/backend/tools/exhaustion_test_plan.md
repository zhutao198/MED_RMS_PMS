# Med-RMS 举一反三质量验证计划 v1.0

> **编制日期**：2026-06-15
> **版本**：v1.0（W29 收官——基于 R82-R86 发现的 15 个 bug 归纳）
> **基线**：R82 交付总览 v1.63（详见开发日志 R82-R86 节点）
> **目标**：以最小工作量穷举暴露 R82-R86 同类 bug，避免每类 bug 都要用户主动报告
> **配套**：与 `tools/test_plan.md`（v3.0 W11 覆盖率基线）形成互补——本计划聚焦**前端契约 + UI 完整性**，test_plan.md 聚焦**后端覆盖率**

---

## 变更记录

| 版本 | 日期 | 变更内容 | 修订人 |
|------|------|----------|--------|
| v1.0 | 2026-06-15 | 初版，基于 R82-R86 发现的 15 个 bug 归纳 10 类根因 + 5 阶段验证流程 | Claude |

---

## 〇、背景与现状

### 0.1 R82-R86 战果回顾

| 节点 | 范围 | bug 数 | 关键根因 |
|---|---|---|---|
| R82 | RequirementList + Dashboard 主统计卡 | 6 | URL 错 + 嵌套契约错 + 业务异常吞掉 |
| R83 | Dashboard 我的待办 | 4 | URL 错 + 端点缺失 + 枚举臆造 |
| R84 | 全 frontend 20 端点扫描 | 2 | URL 错 + 后端模块完全未实现 |
| R85 | 需求详情编辑页 | 1 | Vue import 命名错（`computed as _cmp`） |
| R86 | Dashboard 风险/管理视角 | 2 | 强制锁项目 + 游离数据被关联过滤 |
| **合计** | | **15** | **10 类根因** |

### 0.2 项目规模基线

| 维度 | 数量 | 当前覆盖 | 目标 |
|---|---|---|---|
| Vue 页面 | 91 | 0 单元测试 + 6 e2e 用例 | **关键 32 页 100% 巡检** |
| 后端 Controller | 32 | 部分 smoke test | **关键 20 端点契约断言** |
| 后端 Service | 48 | 30 测试类 / ~13% 行覆盖 | 维持基线，本计划不重复 |
| 端点（@RequestMapping） | 285 | 20 个端点扫描（R84） | **关键 80 个端点 100% 契约验证** |
| DDL 文件 | 48 | 无一致性校验 | **状态/枚举值 vs 前端引用对齐** |

### 0.3 当前测试体系薄弱点

1. **e2e 只验"页面能打开"** —— 从未断言数值/契约
2. **后端契约无前端侧校验** —— 字段名错（如 `coverage.covered` 应为 `traced`）直到 R82 才被发现
3. **数据库枚举值无前端引用一致性** —— `PENDING_REVIEW` 是前端臆造，无任何校验
4. **Dashboard 类"全局视图"组件无"全部"选项校验** —— R86 才被发现
5. **游离数据（projectId=null）无测试覆盖** —— 后端关联过滤会丢数据
6. **Vue setup 错误无早期检测** —— R85 ReferenceError 直到用户点击才发现

---

## 一、10 类 bug 类型与检测方法

### 1.1 类型矩阵

| # | 类型 | R82-R86 实例 | 检测方法 | 检测阶段 |
|---|---|---|---|---|
| **T1** | URL 路由混淆（错路径命中错 handler） | R82 `/requirements/stats` → `/requirements/{id}`；R84 `/requirements/list` → `/requirements/{id}` | 端点 HTTP 状态扫描 + code 字段校验 | Phase 1 |
| **T2** | HTTP 方法不匹配（GET 实际只支持 POST） | R83 `/changes` GET → SY0101 | 端点 HTTP 状态扫描 + 方法维度 | Phase 1 |
| **T3** | 端点完全缺失（SY0301） | R83 `/esignature/pending`；R84 `/projects/{id}/deliverables`（整个模块未实现） | 端点 HTTP 状态扫描 + 后端 controller 存在性校验 | Phase 1+3 |
| **T4** | 字段名契约不一致（前端字段 vs 后端实际） | R82 `data.draft` → `data.byStatus.Draft`；R82 `coverage.covered` → `coverage.traced` | 响应结构 vs 前端使用 diff 扫描 | Phase 1+3 |
| **T5** | 业务异常被静默吞（HTTP 200 + SY01xx） | R82 SY0101 被 `data.total ?? 0` 兜底 | 所有 catch 块必须校验 `res.data.code === '0000'` | Phase 4 |
| **T6** | 枚举值臆造（前端 status 值 DB 不存在） | R83 `PENDING_REVIEW`、`Verifying` | DB 枚举值 vs 前端引用一致性校验 | Phase 3 |
| **T7** | Vue import/引用错误（setup 抛错） | R85 `import { computed as _cmp }` 但用 `computed` | Vite 编译 0 错 + e2e 关键页面渲染断言 | Phase 2+4 |
| **T8** | 默认参数锁死导致过滤过窄 | R86 Dashboard 默认锁第一个项目 → 风险视角全 0 | 全局视图组件必须有"全部"选项 | Phase 2 |
| **T9** | 游离数据被关联过滤丢失 | R86 3 条风险 `projectId=null` → requirementId 间接过滤全丢 | 后端 projectId=null 必须返回全量 | Phase 3 |
| **T10** | 功能完全未实现但前端有入口 | R84 ProjectDeliverables 整个页面 | 端点扫描发现 SY0301 + 列出缺失功能清单 | Phase 1 |

### 1.2 关键洞察

- **T1-T4 都是"前端假设的后端" ≠ "后端实际"** —— 缺一个端点契约测试层
- **T5** 是 R82 修过的"业务异常吞掉"模式 —— 一旦 catch 块缺 `code` 校验就重蹈覆辙
- **T6** 是**前端臆造枚举**——代码里硬编码字符串，与 DB 完全脱节
- **T7** 是**最隐蔽的**——Vite 不报错，运行时 ReferenceError 直到用户点击才暴露
- **T8-T9** 是**设计层 bug**——不是某行代码错，而是整体交互逻辑未考虑"全公司视图"场景

---

## 二、5 阶段验证流程

### 2.1 Phase 1：全 frontend 端点契约扫描（自动，预计 1-2 小时）

**目标**：发现 T1/T2/T3/T10（URL/方法/端点缺失/未实现模块）

**工具**：Python 脚本 `tools/endpoint_audit.py`（新建）

**步骤**：
1. 静态扫描 frontend `src/` 下所有 `request.{get,post,put,delete}(...)` 调用
2. 启动后端 + 前端 + 登录获取 token
3. 对每个端点：
   - HTTP 状态扫描
   - 响应 code 字段校验（必须 `'0000'` 或 `'200'`，否则 SY 异常）
   - HTTP 方法探测（GET/POST/PUT/DELETE 是否支持）
4. 静态扫描 backend `*/controller/*Controller.java` 提取真实端点 + HTTP 方法
5. **diff**：前端调用 vs 后端实际 → 列出 SY0301（端点缺失）/ SY0101（方法错）/ 200 但 SY01xx（业务异常）

**输出**：
- `tools/output/endpoint_audit_report.md`（人类可读）
- `tools/output/endpoint_audit.json`（机器可读，含每个端点 status/code）

**覆盖率目标**：285 端点 100% 扫描

**判定**：
- 🟢 200 + code=200 → 健康
- 🔴 SY0301 → 端点缺失或路径错
- 🟡 SY0101 → 方法错或参数类型不匹配
- 🟠 HTTP 200 但 code=SY01xx → 业务异常（前端可能误处理）

---

### 2.2 Phase 2：关键页面 UI 巡检（自动 + 人工，预计 4-6 小时）

**目标**：发现 T7（Vue import 错）/ T8（默认参数问题）/ 部分 T4

**工具**：Playwright e2e + Chrome DevTools MCP

**步骤**：
1. 对 91 个页面按"路由可达性"分类：
   - **Tier 1（核心 20 页）**：必须 100% 巡检
     - Dashboard, RequirementList, RequirementDetail, ReqEdit, ReqCreate, DecomposeList
     - TestCaseList, ChangeList, ChangeRequest, ChangeApprovals
     - TraceGraph, TraceMatrix, TraceGaps
     - RiskRegister, RisksMatrix, FmeaEditor
     - ProjectList, ProjectDetail, GanttView, IpdGate
   - **Tier 2（次要 30 页）**：抽样 50% 巡检
     - Compliance*, ESig*, Notification, Report*, AuditLog
   - **Tier 3（后台管理 41 页）**：抽样 25% 巡检
     - System*, Dict, Organization, LoginLog, OperationLog
2. 每个 Tier 1 页面执行：
   - **goto(url)** + wait 2-3s
   - 抓取 `console.error` + `console.warn` → T7 早期发现
   - 抓取网络请求 4xx/5xx → T1/T2 兜底
   - 抓取统计卡数字（`.stat-card`）→ 断言"全为 0"时**报警**（除已知空表）
   - 抓取项目筛选器是否有"全部"选项 → T8 校验
   - 抓取 `Vue warn Failed to resolve component` → 组件引用错
3. 输出 `tools/output/page_audit.json`

**判定**：
- 🔴 console.error > 0 → T7（Vue 错）
- 🔴 统计卡全 0 且页面无空态文案 → T1-T5 重演
- 🟡 无"全部"选项 + 默认锁具体项目 → T8

---

### 2.3 Phase 3：数据契约一致性扫描（自动，预计 2-3 小时）

**目标**：发现 T4（字段名错）/ T6（枚举臆造）/ T9（游离数据）

**工具**：Python 脚本 `tools/contract_audit.py`（新建）

**步骤**：

#### 3.1 字段名契约 diff（T4）

1. 静态扫描 frontend `src/views/**/*.vue`，提取所有 `xxx?.<field>` 与 `xxx?.<field>?.[<sub>]` 引用
2. 对每个端点，运行时抓响应 sample，递归提取所有字段路径
3. **diff**：前端引用字段 vs 响应字段
   - 前端引用但响应无 → 🔴 字段名错（R82 模式）
   - 响应有但前端未用 → 🟡 可能漏接字段

#### 3.2 枚举值一致性（T6）

1. 静态扫描 frontend `src/views/**/*.vue`，提取所有 `status:`、`type:`、`severity:` 等枚举值字符串（必须硬编码或来自字典 API）
2. 从 DB 提取 `t_dict_item` 中 `dict_type` 对应实际值 + 主表 status 字段实际 distinct 值
3. **diff**：前端引用枚举 vs DB 实际枚举
   - 前端有 DB 无 → 🔴 臆造枚举（R83 模式）
   - DB 有前端无 → 🟡 可能漏处理状态

#### 3.3 游离数据扫描（T9）

1. 跑 SQL `SELECT COUNT(*) FROM <主表> WHERE project_id IS NULL` 找出游离数据
2. 对每张主表（risk/requirement/change/problem_report/soup/...），如 `count > 0`，跑"不带 projectId"的端点，确认能返回该数据
3. **判定**：游离数据存在但后端 projectId=null 过滤逻辑丢失 → 🔴

**输出**：
- `tools/output/contract_diff.md`（字段名错清单）
- `tools/output/enum_diff.md`（枚举臆造清单）
- `tools/output/orphan_data.md`（游离数据 + 后端过滤丢失清单）

---

### 2.4 Phase 4：统计卡数值断言（自动，预计 2-3 小时）

**目标**：发现 T5（业务异常吞）/ 巩固 T4 修复

**工具**：Playwright e2e `e2e/exhaustion-stat-cards.spec.ts`（新建）

**步骤**：

1. 对所有有统计卡的页面（已巡检发现的 6+ 页：RequirementList / Dashboard 4 视角 / RiskRegister / TraceCoverage / ProjectDetail / IpdGate）枚举每张卡
2. 跑数据初始化脚本 `tools/seed_test_data.py`，保证数据集：
   - 至少 10 个项目
   - 至少 100 条需求（覆盖所有 status）
   - 至少 10 条风险（覆盖所有 level）
   - 至少 5 条变更（覆盖所有 status）
   - 至少 10 条问题报告（覆盖所有 status）
3. e2e 断言：
   - "需求总数"卡 > 0
   - 至少 1 个分桶卡 > 0（如草稿/已批准）
   - "已追溯" > 0（用 R82 修复后的契约）
   - "覆盖率"含 % 且数字 > 0
   - **关键**：切到不同项目时，数字应刷新（≤ 全局值）
4. 输出 `e2e/output/stat_card_failures.md`

**判定**：
- 🔴 全 0 且无空态文案 → T1-T5 重演
- 🟡 切项目不刷新 → T8

---

### 2.5 Phase 5：游离数据 / 默认值 / 边界场景（人工 + 自动，预计 3-4 小时）

**目标**：发现 T9（游离数据丢失）+ 边界 case

**工具**：Python + SQL 脚本 + 人工用例

**步骤**：

#### 5.1 边界场景用例（人工，按角色跑）

| 角色 | 关键场景 | 预期 |
|---|---|---|
| ADMIN | 登录后看 Dashboard 默认视图 | 不锁项目，显示全公司数据 |
| ADMIN | 切到具体项目，再切回全部 | 数据应正确刷新 |
| ADMIN | 创建游离数据（projectId=null） | 在 Dashboard 全部视图能看到 |
| 项目经理 | 进入具体项目，只看本项目数据 | 风险/需求/变更 全部筛项目 |
| 系统审计员 | 审计日志能查到游离数据的变更 | 不丢日志 |
| 只读用户 | 进入所有页面无报错 | 权限控制正常 |

#### 5.2 默认值/边界自动检查

| 检查项 | 判定 |
|---|---|
| 全部项目/全部状态/全部类型 下拉是否提供"全部"选项 | T8 |
| 分页默认 size 是否合理（不要 1000） | 性能 |
| 时间筛选默认值是否合理 | 体验 |
| 必填项是否前端校验 | UX |
| 空列表是否有"暂无数据"文案 | UX |
| 错误状态是否有"重试"按钮 | UX |
| 数字 > 999 是否显示 k/w | UX |

**输出**：
- `tools/output/edge_case_report.md`

---

## 三、关键页面测试矩阵（91 页 → 抽样 32 页）

### 3.1 Tier 1 必测 20 页（按 Phase 2 + Phase 4 完整覆盖）

| # | 页面 | 路由 | T1-T3 | T4 | T5 | T6 | T7 | T8 | T9 |
|---|---|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| 1 | Dashboard | /dashboard | ✅ | ✅ | ✅ | - | ✅ | ✅ | ✅ |
| 2 | RequirementList | /requirements | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 3 | RequirementDetail | /requirements/:id | ✅ | ✅ | - | ✅ | ✅ | - | - |
| 4 | ReqEdit | /requirements/:id/edit | ✅ | ✅ | - | - | ✅ | - | - |
| 5 | ReqCreate | /requirements/create | ✅ | - | - | - | ✅ | - | - |
| 6 | DecomposeList | /requirements/:id/decompose | ✅ | ✅ | - | - | ✅ | - | - |
| 7 | TestCaseList | /testcases | ✅ | ✅ | - | ✅ | ✅ | - | - |
| 8 | ChangeList | /changes | ✅ | ✅ | - | ✅ | ✅ | - | - |
| 9 | ChangeRequest | /changes/:id | ✅ | ✅ | - | - | ✅ | - | - |
| 10 | ChangeApprovals | /changes/approvals | ✅ | ✅ | - | ✅ | ✅ | - | - |
| 11 | TraceGraph | /trace-graph | ✅ | - | - | - | ✅ | - | - |
| 12 | TraceMatrix | /traceability/coverage | ✅ | ✅ | - | - | ✅ | ✅ | - |
| 13 | TraceGaps | /traceability/gaps | ✅ | ✅ | - | - | ✅ | - | - |
| 14 | RiskRegister | /risk/register | ✅ | ✅ | - | ✅ | ✅ | - | - |
| 15 | RisksMatrix | /risks/matrix | ✅ | - | - | - | ✅ | ✅ | - |
| 16 | FmeaEditor | /risk/fmea | ✅ | ✅ | - | - | ✅ | - | - |
| 17 | ProjectList | /projects | ✅ | ✅ | - | - | ✅ | - | - |
| 18 | ProjectDetail | /projects/:id | ✅ | ✅ | - | - | ✅ | - | - |
| 19 | GanttView | /projects/gantt | ✅ | ✅ | - | - | ✅ | - | - |
| 20 | IpdGate | /projects/ipd | ✅ | ✅ | - | - | ✅ | - | - |

### 3.2 Tier 2 抽样 12 页

RiskMonitoring, Iec62304, DhfPackage, ErpsExport, RegulationImpact, Safety, SOUPManagement, ReviewManagement, Notifications, RequirementPool, RequirementKanban, ResourceManagement

### 3.3 Tier 3 抽样 10 页

WorklogView, ReportCenter, AuditLogs, LoginLogs, OperationLogs, DictManage, UserManage, RoleManage, Organization, Profile

---

## 四、自动化测试工具集

### 4.1 新建工具

| 工具 | 路径 | 用途 | 阶段 |
|---|---|---|---|
| `tools/endpoint_audit.py` | 静态扫描 + 运行时探测 | 端点契约扫描 | Phase 1 |
| `tools/contract_audit.py` | 静态 + DB 查询 | 字段名/枚举/游离数据 | Phase 3 |
| `tools/seed_test_data.py` | 数据库初始化 | 保证数据集 | Phase 4 |
| `tools/orphan_data_query.sql` | 游离数据查询 | Phase 5 辅助 | Phase 5 |
| `e2e/exhaustion-stat-cards.spec.ts` | Playwright | 统计卡数值断言 | Phase 4 |
| `e2e/exhaustion-page-render.spec.ts` | Playwright | 全页 console.error 检测 | Phase 2 |

### 4.2 复用工具

| 现有工具 | 复用方式 |
|---|---|
| `tools/smoke_e2e.py` | 抽取核心健康检查 |
| `tools/full_coverage_test.py` | Phase 1 端点扫描可合并 |
| `tools/openapi_check.py` | OpenAPI 文档 vs 实际端点一致性 |
| `tools/ddl_sync_check.py` | DDL 同步校验 |

---

## 五、执行时间表

| 阶段 | 工作量 | 依赖 | 输出 |
|---|---|---|---|
| **W30 D1** | Phase 1 脚本 + 端点全扫描 | 后端 + 前端在线 | endpoint_audit_report.md（预计 8-15 个新 bug）|
| **W30 D2** | Phase 3 脚本 + 契约 diff | 数据库可读 | contract_diff.md + enum_diff.md（预计 10-20 个新 bug）|
| **W30 D3** | Phase 2 Playwright + 关键 20 页 | 前端稳定 | page_audit.json（预计 5-10 个新 bug）|
| **W30 D4** | Phase 4 统计卡断言 + 种子数据 | 测试 DB | stat_card_failures.md（预计 3-5 个新 bug）|
| **W30 D5** | Phase 5 边界 + 文档收尾 | 人工 | edge_case_report.md + v2.0 更新 |

**预计发现 bug 总量**：30-50 个（R82-R86 同类 + 新类）

**预计总工时**：12-18 人天（W30 一周内可闭环）

---

## 六、报告模板

### 6.1 bug 报告格式

```markdown
### BUG-{N}
- **类型**：T{n}
- **页面**：{URL}
- **复现路径**：用户操作 → 现象
- **根因**：{文件}:{行号} + {具体原因}
- **影响范围**：{哪类用户/页面}
- **优先级**：P0/P1/P2
- **修复方案**：{简要}
- **回归断言**：{e2e 用例名}
```

### 6.2 阶段报告模板

```markdown
## Phase {N} 报告（{日期}）

**扫描范围**：{页面数/端点数}
**发现 bug**：{N} 个
**按类型分布**：T1=x, T2=y, ...
**严重度分布**：P0=x, P1=y, P2=z
**下一阶段建议**：...
```

---

## 七、长期维护建议

### 7.1 CI 集成

把以下脚本集成到 GitHub Actions：
- `endpoint_audit.py`：每次 PR 跑一次，阻截端点契约错
- `contract_audit.py`：每周一次全量 diff
- `exhaustion-stat-cards.spec.ts`：每次 PR 跑 Playwright

### 7.2 编码规范补充

把 R82-R86 教训固化为：
1. **CLAUDE.md 7 节**：补充"前端必须校验 res.data.code"
2. **CLAUDE.md 4 节**：补充"前端禁止臆造枚举值，需查 DB 或字典 API"
3. **前端 ESLint 规则**：禁止 `xxx ?? 0` 模式用于 catch 块 fallback（强制 code 校验）

### 7.3 R 系列节点规范

按 CLAUDE.md "8. 回滚节点规范"，每次发现同类 bug 立即追加到最近 R 节点的"后续变更记录"，避免新开 R 节点导致节点膨胀。

---

## 八、附录：R82-R86 完整 bug 清单

| 编号 | 节点 | 文件 | 类型 | 现象 |
|---|---|---|---|---|
| BUG-R82-1 | R82 | RequirementList.vue:263 | T1 | URL `/requirements/stats` 命中 `/requirements/{id}` |
| BUG-R82-2 | R82 | RequirementList.vue:274 | T4 | 期望扁平 `draft/approved` 实际嵌套 `byStatus.Draft` |
| BUG-R82-3 | R82 | RequirementList.vue:267 | T5 | SY0101 被 `data.total ?? 0` 兜底 |
| BUG-R82-4 | R82 | Dashboard.vue:50 | T4 | `coverage.covered` 应为 `traced` |
| BUG-R82-5 | R82 | Dashboard.vue:54 | T4 | `coverage.coverageRate` 应为 `overall` |
| BUG-R82-6 | R82 | Dashboard.vue:98 | T4 | `riskView.avgScore` 应为 `avgRpn` |
| BUG-R83-1 | R83 | Dashboard.vue:399 | T1 | `/changes` GET 不支持 |
| BUG-R83-2 | R83 | Dashboard.vue:404 | T3 | `/esignature/pending` 不存在 |
| BUG-R83-3 | R83 | Dashboard.vue:410 | T6 | status `PENDING_REVIEW` 臆造 |
| BUG-R83-4 | R83 | Dashboard.vue:415 | T6 | status `Verifying` 臆造 |
| BUG-R84-1 | R84 | ProjectDetail.vue:325 | T1 | `/requirements/list` URL 错 |
| BUG-R84-2 | R84 | ProjectDeliverables.vue | T3+T10 | 整个后端模块未实现 |
| BUG-R85-1 | R85 | ReqEdit.vue:232 | T7 | `import { computed as _cmp }` 但用 `computed` |
| BUG-R86-1 | R86 | Dashboard.vue:434 | T8 | fetchProjects 默认锁第一个项目 |
| BUG-R86-2 | R86 | StatisticsService.java:100-107 | T9 | 游离数据被关联表间接过滤丢失 |

**类型分布**：T1=3, T3=2, T4=4, T5=1, T6=2, T7=1, T8=1, T9=1, T10=1（T2 未发现）

---

> **下一步**：用户确认本计划后，W30 按时间表执行 5 阶段，每阶段结束产出报告 + bug 列表 + 修复 + R 节点更新。