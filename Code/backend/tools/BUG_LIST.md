# Med-RMS 缺陷跟踪清单（本地 GitHub Issues 替代）

> **作用**：替代 GitHub Issues，作为测试阶段 P0/P1/P2/P3 缺陷的统一登记与跟踪
> **维护人**：测试人员发现 bug 即登记；开发人员修复后更新状态
> **状态机**：`NEW → TRIAGED → IN_PROGRESS → FIXED → VERIFIED → CLOSED`（NEW 也可直转 `WONT_FIX`）
> **关联文件**：本计划 v1.0 第八节"严重度分级" + R63 节点

---

## 使用说明

1. **登记 bug**：复制下方模板，新增一行
2. **分类严重度**：P0 Blocker / P1 Major / P2 Minor / P3 Trivial（按 R63 第八节标准）
3. **状态流转**：测试发现填 `NEW` → 站会判定转 `TRIAGED` → 开发认领转 `IN_PROGRESS` → 修复后 `FIXED` → 测试验证 `VERIFIED` → 关闭 `CLOSED`
4. **修复原则**：
   - P0 Blocker：发现后立即修复（按 R63 第八节）
   - P1 Major：每天 17:00 站会后批量修
   - P2/P3：阶段末批量修
5. **检索**：用 `Ctrl+F` 按 ID/模块/状态 搜索

---

## 当前缺陷清单

| ID | 日期 | 模块 | 严重度 | 标题 | 复现步骤 | 状态 | 责任人 | 修复版本 | 备注 |
|----|------|------|--------|------|---------|------|--------|---------|------|
| - | - | - | - | - | - | - | - | - | **（无开放缺陷）** |
| **FIXED-W2D3-01** | 2026-06-11 | traceability | P1 | importBatch 缺失 No→Id 解析 | 调用 POST /api/traceability/import 任意行 | **FIXED** | Claude | v2.1 | W2-D3 测试发现即时修。修复：新增 `resolveNoToId(type, no)` 私有方法；importBatch 循环开头解析 sourceNo/targetNo → sourceId/targetId，null 时抛 TR_IMP_001/TR_IMP_002。 |

---

## 已关闭缺陷（按 ID 倒序）

| ID | 关闭日期 | 模块 | 严重度 | 标题 | 修复版本 | 关闭原因 |
|----|---------|------|--------|------|---------|---------|---------|
| - | - | - | - | - | - | - |

---

## 模板

复制以下行粘贴到"当前缺陷清单"表：

```
| BUG-001 | 2026-06-11 | requirement | P1 | 需求列表分页错误 | 1. 打开 /requirements 2. 选第 3 页 3. 期望显示 41-60 条，实际显示 21-40 | NEW | @tester1 | - | 见 PRD 7.2.1 |
```

**字段说明**：
- `ID`：`BUG-NNN`（3 位递增）
- `日期`：发现日期 YYYY-MM-DD
- `模块`：req / change / trace / compliance / risk / project / sys / e-sign / notif / common
- `严重度`：P0/P1/P2/P3
- `状态`：NEW / TRIAGED / IN_PROGRESS / FIXED / VERIFIED / CLOSED / WONT_FIX / DUPLICATE
- `责任人`：测试时填发现人，修复时填修复人
- `修复版本`：填 v1.62/v1.63 等

---

## 统计

- 本周新增：0
- 本周修复：0
- 累计开放：0
- 累计关闭：0

## 关联文件

- 测试计划：`Code/backend/tools/test_plan.md`
- 开发日志：`开发日志.md`（R60-R63 历史修复）
- E2E 工具：`Code/backend/tools/verify_*.py`
- 视觉验收报告：`Code/backend/tools/visual_diff_report.md`
