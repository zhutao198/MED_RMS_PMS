# 需求流转流程评审报告（基于需求流转流程梳理.md）

> 评审日期：2026-08-12
> 依据：`需求流转流程梳理.md`（2026-08-12，PRD v2.2）第十一章差异清单
> 范围：后端 `RequirementService` / `RequirementPoolService` / `RequirementTaskService` / `RequirementStatus` / `ProjectMember`
> 评审目标：核对文档差异清单是否已在近期修复中闭合，并指出仍存隐患。

---

## 一、差异清单逐项结论

| # | 文档差异 | 当前代码状态 | 评审结论 |
|---|---------|------------|---------|
| 11.3-#1 | 提交评审需先拆解（要求 DECOMPOSED） | `submitForReview` 已允许 `DRAFT` 或 `DECOMPOSED`（RequirementService 387-390） | ✅ **文档误判**：当前代码已支持草稿直接提交，文档描述已过时，需回写修正 |
| 11.3-#2 | 评审通过拆为 ReviewApproved + Approved 两步 | `InReview→ReviewApproved→Approved`，且有 FR-0.17 操作序列强制检查 | ⚠ **有意扩展**：符合"草稿→评审中→已通过"主链路，属产品增强，可接受；文档标 ⚠ 合理 |
| 11.3-#3 | 任务回流置 InTest（非 Implemented）+ 未通知测试 | `syncRequirementStatus` 全 DONE→`Implemented`，并调 `notifyTesterAndQaOnImplemented`（双通知 TESTER+QA） | ✅ **已闭合** |
| 11.3-#4 | 阻塞超 3 天阈值 + 风险标记缺失 | `BLOCKED` 记录 `blockedAt`；超 3 天 → `notifyPmBlockOverdue` + 置 `Suspect` | ✅ **已闭合**（阈值+通知到位）；见 §三 语义混用隐患 |
| 11.3-#5 | PARSED 入口未强制 | `parsePoolItem` 新增 PENDING→PARSED；`convertToUrs` 放宽 PENDING/PARSED；`rejectPoolItem`/`deletePoolItem` 允许 PARSED | ✅ **已闭合** |
| 11.3-#6 | 工时超支 150% 联动缺失 | `updateTaskActualHours` 中 `actualHours > estimated*1.5` → `notifyPmOvertime` + 置 `Suspect` | ✅ **已闭合**（按用户确认用 150% 而非 budgetAlarmPct） |
| 11.2 已变更态 | 缺独立「已变更」态 | 用 `Suspect` 标记覆盖下游追溯变更（RequirementStatus 22 行） | ⚠ **已知取舍**：PRD 有独立「已变更→评审中」循环，实现无；isSuspect 仅表达"受影响"，不表达"已变更"闭环 |
| 11.4 任务态命名 | TODO/DONE vs 待开始/已完成 | 实现用 TODO/IN_PROGRESS/DONE/BLOCKED/IN_TEST/CANCELLED | ⚠ **仅命名差异**：属展示层，文档已标低优先级 |

---

## 二、本次新增修复确认（#3b 双通知）

`RequirementTaskService.notifyTesterAndQaOnImplemented`（402-446 行）：
- 主送 `TESTER`（测试工程师）、抄送 `QA`（质量工程师），角色编码与前端 `ProjectMembersAdd.vue` 下拉一致。
- 方案 A 兜底：项目未配置已分配 userId 的 TESTER/QA → 回退通知项目经理，内容标注缺失角色，保证验证衔接不静默断裂。
- `ProjectMember.role` 注释已对齐前端真实枚举（`PROJECT_MANAGER/REQUIREMENT_ENGINEER/DEVELOPER/TESTER/QA`），测试 role 值已统一。

**结论**：#3b 实现正确，且已通过 `RequirementTaskServiceTest` 三用例（双通知 + 方案A兜底）验证。

---

## 三、评审发现的隐患 / 待办（新发现，文档未覆盖）

### H1【中】Suspect 语义混用：变更嫌疑 vs 阻塞/超支风险
`#4`（阻塞超3天）与 `#6`（工时超支）均将需求置 `Suspect` + `setIsSuspect(true)`；而 `RequirementStatus` 22 行定义 `Suspect` 的语义是"**追溯变更触发**"（上游变更后下游标黄）。
- **问题**：PRD 中"阻塞超3天标风险""工时超支标超支"与"变更嫌疑"是**三种不同语义**，却共用同一个 `isSuspect` 布尔 + `Suspect` 状态。看板无法区分"此需求是因上游变更受影响"还是"因任务阻塞/超支需关注"，可能影响变更影响分析与风险统计准确性。
- **建议**：引入独立标记（如 `riskType` 枚举：CHANGE/BLOCK/OVERTIME）或在通知/看板中区分来源；至少补充 `isRisk`/`isOvertime` 独立布尔，避免 `isSuspect` 过载。

### H2【中】【合规缺口】DRS「已实现」标记缺审计日志
文档九.3 明确要求：DRS「已实现」标记需审计日志（21 CFR Part 11 §11.50 / 电子签名），关联测试用例覆盖需满足 §5.7 追溯性。
- **现状**：`syncRequirementStatus` 中 `updateById(req)` 直接改状态，**未走 `@AuditLog` 审计**（对比 `approveRequirement` 有 `@AuditLog` 写审计）。
- **风险**：自动化状态回流（任务全完成→已实现）未留合规审计轨迹，与 DRS「已实现」需电子签名/审计的强约束不符。
- **建议**：在 `syncRequirementStatus` 状态变更处补充审计日志（操作人取系统触发标识或操作员上下文），或显式豁免并文档说明自动回流的合规处理。

### H3【低】文档第十一章需回写修正
- 11.3-#1 描述（"要求 DECOMPOSED 才可提交"）与当前代码（允许 DRAFT）矛盾，且文档第十章已自述"已修复为提交后停留 InReview"。建议统一第十一章表述，避免后续评审者误判。
- 11.3-#3/#4/#5/#6 应标记为"已修复（2026-08-12 修复轮次）"，并补 #3b 双通知条目。

### H4【低】任务工时超支仅单任务判断，未聚合需求级
`#6` 在 `updateTaskActualHours` 内逐任务判断 `actualHours > estimated*1.5`，仅当该任务自身超支时触发。PRD 原文"任务实际工时>预估×150%"即单任务语义，**实现符合**。但若期望"需求整体工时超支"，当前无聚合统计——属可选项，非偏差。

---

## 四、整体结论

1. **流程断点已全部闭合**：文档第十一章标注为 ✗/⚠ 的四项流程差异（#3 状态回流、#4 阻塞阈值、#5 PARSED、#6 工时超支）均已在 2026-08-12 修复轮次中实现，#3b 双通知本日补齐，测试通过。
2. **文档本身需同步**：第十一章部分条目（尤其 #1）与当前代码及第十章自述矛盾，建议回写，否则会误导后续维护。
3. **两处需产品/合规确认**：
   - H1（Suspect 语义过载）：是否接受变更/阻塞/超支共用标记，还是拆分；
   - H2（DRS 已实现缺审计）：自动状态回流是否满足 21 CFR Part 11 审计要求，或需补审计日志。
4. **可接受的设计扩展**（无需改）：#2 评审拆两步（有 FR-0.17 序列校验兜底）、11.2 已变更态用 Suspect 表达（已知取舍）、11.4 任务态命名（展示层）。

---

## 五、建议后续动作（按优先级）

| 优先级 | 动作 | 说明 |
|--------|------|------|
| P1 | 回写 `需求流转流程梳理.md` 第十一章，标记已修复项、修正 #1 矛盾 | 消除文档误导 |
| P2 | H2：确认 `syncRequirementStatus` 状态回流是否需补审计日志 | 合规强约束 |
| P3 | H1：拆分 `isSuspect` 语义（变更 vs 阻塞风险 vs 超支） | 看板准确性 |
| P4 | H4（可选）：需求级工时聚合超支统计 | 增强监控 |
