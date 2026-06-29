# 合规管理域 — 交互原型 vs 前端实现偏差报告

**编制日期：** 2026-06-09
**比对范围：** Detailed/07-交互原型（合规 12 个 HTML）vs frontend/src/views/compliance/（8 个 Vue 组件）
**比对者：** Claude 自动比对

---

## 一、总体概览

| 项目 | 数量 | 说明 |
|---|---|---|
| 原型页面 | 12 | baselines/baseline-create/baseline-compare/baselines-detail/baselines-edit、iec62304-checklist、compliance-regulations、compliance-reports、compliance-safety、soup-management、soup-detail、soup-review、problem-report、problem-report-create、problem-report-detail（共 15） |
| 前端实现页面 | 8 | ComplianceList / Baselines / SoupManagement / Iec62304 / ProblemReport / DhfPackage / ErpsExport / RegulationImpact |
| 路由覆盖 | 8 | 12 个原型对应的独立路由仅 1 个（baselines） |

### 严重程度分类
- P0（缺失独立页面）：compliance-regulations、compliance-safety、soup-detail、soup-review、problem-report-create、problem-report-detail、baseline-compare、baselines-detail、baselines-edit、compliance-reports
- P1（功能/字段偏差）：baselines、soup-management、problem-report、iec62304
- P2（流程/UX 偏差）：细节

---

## 二、逐项偏差清单

### 1. baseline-compare（基线对比） — P0
**原型：** `baseline-compare-原型.html`（含左右两基线选择 + 新增/删除/修改/不变 4 种 diff 标签 + 变更说明列）
**实现：** 无独立页面，仅 Baselines.vue 中 `handleCompare` 用 `ElMessageBox.alert` 弹框显示统计数字（新增/移除/修改），没有差异明细表、没有"生成为新基线"按钮。
**差距：**
- 缺失：独立的 `/compliance/baselines/compare` 路由与对比页面
- 缺失：差异明细表格（diff-tag added/removed/modified/unchanged）
- 缺失：基线A/B 切换选择器
- 缺失：导出对比报告 / 生成为新基线 按钮

### 2. baselines-detail（基线详情） — P0
**原型：** `baselines-detail-原型.html`（含基线基本信息卡片 + 需求列表表格：编号/层级/标题/版本/创建人）
**实现：** 无独立页面，`handleView` 用 `ElMessageBox.alert` 弹框呈现 20 条快照。
**差距：**
- 缺失：独立详情页（应含 4 列 info-grid、需求列表 table）
- 缺失：需求版本/创建人列

### 3. baselines-edit（基线编辑） — P0
**原型：** `baselines-edit-原型.html`（含锁提示、基本信息表单、申请解锁按钮、解锁原因字段）
**实现：** 无独立页面，`handleEdit` 弹 `ElMessage.warning` 拒绝编辑（理由：21 CFR Part 11 不可修改）。
**差距：**
- 缺失：独立编辑页（即使仅展示锁定状态也应保留）
- 缺失：申请解锁按钮 + 解锁原因字段

### 4. baseline-create（创建基线） — P1
**原型：** 三步表单（基本信息 → 选择内容 → 确认提交），含基线类型（里程碑/发布/审计/自定义）、锁定方式（单/双签）、可选纳入内容（URS/PRS/SRS/DRS/追溯矩阵）、签名锁定提示。
**实现：** Baselines.vue 中 `el-dialog` 弹出 480px 表单，仅 2 字段：项目 + 基线名称。
**差距：**
- 缺失：基线类型字段
- 缺失：锁定方式字段
- 缺失：纳入内容勾选（URS/PRS/SRS/DRS/追溯矩阵）
- 缺失：步骤条（3 步）
- 缺失：基线描述字段
- 缺失：双人签名锁定提示卡

### 5. baselines（基线列表） — P1
**原型：** 3 张卡片式布局（已锁定/进行中/草稿），含签名次数、需求层级统计、签名锁定流程图
**实现：** 表格布局（`el-table`），含 4 统计卡 + 5 步锁定流程。
**差距：**
- 布局差异：原型是卡片式 + 嵌套需求列表；实现是表格
- 缺失：URS/PRS/SRS/DRS 各级别需求统计数字
- 缺失：每条基线内的"包含需求预览列表"
- 缺失：基线类型字段列

### 6. iec62304-checklist（IEC62304 检查） — P1
**原型：** 进度环 + 4 项统计 + 章节分组（§5/§7/§8/§9），含评估弹窗
**实现：** 进度环 + 4 项统计 + 章节分组 + 评估弹窗，结构基本一致。
**差距：**
- 新增：项目切换下拉框（原型无）
- 差异：状态枚举值 原型 `compliant/partial/non_compliant/not_applicable`，实现 `COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_APPLICABLE/PENDING`（新增 PENDING）
- 新增：一键合规检查按钮"将所有 PENDING 标记为部分合规"（原型仅消息提示）

### 7. compliance-regulations（法规映射） — P0（缺失）
**原型：** 12 条原型列出 NMPA/FDA/CE/IEC 4 类法规条款映射，列：法规/条款编号/条款描述/关联需求/映射数量/操作
**实现：** 无此独立页面。RegulationImpact.vue 是"法规更新影响分析"，不是"法规条款映射表"；字段是 code/name/currentVer/newVer，不含 `clause`/`linkedReqs`/`count`。
**差距：**
- 缺失：独立 `/compliance/regulations` 页面
- 缺失：法规类型过滤、层级过滤、关键字搜索
- 缺失：regType 标签（nmpa/fda/ce/iec 4 种颜色徽章）

### 8. compliance-reports（合规报告与证据包） — P0（缺失）
**原型：** 3 报告模板卡片（NMPA eRPS / FDA 21 CFR Part 11 / CE MDD/MDR）+ 报告配置（项目/周期/需求/覆盖率/基线）+ 6 项 includeSections 复选 + 5 步生成进度 + 报告预览（追溯矩阵/签名记录/审计日志三大表）
**实现：** 无独立"合规报告"页面。DhfPackage.vue / ErpsExport.vue 各覆盖一小部分（Dhf 证据包清单 + eRPS 结构），但缺模板选择、配置、生成进度、签名/审计汇总等。
**差距：**
- 缺失：3 个报告模板选择卡
- 缺失：报告配置面板（项目/周期/需求总数/基线版本）
- 缺失：6 项 includeSections 复选
- 缺失：5 步生成进度步骤条
- 缺失：报告预览（追溯矩阵汇总表、电子签名汇总表、审计追踪汇总）

### 9. compliance-safety（安全分类） — P0（缺失）
**原型：** 4 张统计卡（Class A/B/C/D）+ 安全分类表（分类/标准/典型应用/数量/IEC 要求）
**实现：** 无此页面。
**差距：**
- 缺失：独立安全分类页面
- 缺失：Class A/B/C/D 4 级分类

### 10. soup-management（SOUP 管理） — P1
**原型：** 4 统计卡 + 4 过滤器（关键字/类型/风险/状态）+ 表格 9 列 + 详情抽屉 + 登记弹窗（8 字段：名称/版本/类型/供应商/风险/用途/许可证）。
**实现：** 4 统计卡 + 4 过滤器（关键字/分类/风险/状态）+ 表格 10 列 + 详情对话框 + 登记弹窗（10 字段）。
**差距：**
- 新增：组件代号、供应商国家、维护人字段
- 新增：许可证到期日期字段
- 差异：分类字段 原型用 `open-source/commercial/proprietary`（中文：开源/商业/专有），实现用 `LIBRARY/FRAMEWORK/TOOL/OS`（英文枚举）
- 差异：风险等级 原型用 `low/medium/high/critical`，实现用 `LOW/MEDIUM/HIGH`
- 差异：原型的"已知异常"列实现为 `anomalies.length` 远程拉取（实现更复杂，支持 FR-1.11 异常检测对话框）
- 缺失：原型的"添加异常记录"按钮（无对应功能）
- 缺失：编辑时原型打开完整编辑表单，实现仅允许修改 version 字段
- 新增：实现独有"续期"按钮（许可证续期 1 年）

### 11. soup-detail（SOUP 组件详情） — P0
**原型：** 独立页面，含基本信息卡 + 详细信息表（许可证/类型/一二级分类/官方地址/CVE状态/维护状态/追溯级别/首次引入/风险等级）+ 版本历史列表 + 安全审查状态表 + 编辑/关联需求按钮
**实现：** 仅有 SoupManagement.vue 中的 el-dialog 详情弹窗（640px），无版本历史、无安全审查表、无 CVE 状态字段。
**差距：**
- 缺失：版本历史列表
- 缺失：安全审查状态表（含审查人/日期/结论）
- 缺失：CVE 状态、维护状态、官方地址字段
- 缺失：独立路由 `/compliance/soup/:id`

### 12. soup-review（SOUP 审查记录） — P0
**原型：** 独立页面，当前审查状态卡 + 历史审查记录表（日期/审查人/类型/结论/CVE检查）+ 新建审查按钮
**实现：** 完全缺失。
**差距：**
- 缺失：独立路由 `/compliance/soup/:id/review`
- 缺失：审查记录 CRUD 全部功能

### 13. problem-report（问题报告列表） — P1
**原型：** 状态流转（Open → Analyzing → Correcting → Verifying → Closed）+ 筛选 + 列表 + 详情面板（CAPA 纠正措施时间线）
**实现：** 状态流转 + 筛选 + 列表 + 详情面板（CAPA 纠正措施列表）— 基本结构一致。
**差距：**
- 缺失：原型的"新建"按钮跳转到独立创建页（实现是 el-dialog 内嵌）
- 缺失：状态枚举 中文显示（实现有"开放/分析中/纠正中/验证中/已关闭"标签，但详情面板 status 仍展示英文枚举）

### 14. problem-report-create（新建问题报告） — P0
**原型：** 独立页面，3 步表单（填写信息 → 关联需求 → 提交）+ 字段：问题编号/类型/严重程度/来源/标题/描述/根本原因/发现日期/期望关闭日期/关联需求
**实现：** 无独立创建页面，使用 el-dialog（600px）表单，仅 5 字段（标题/严重度/来源/描述/关联需求），无问题类型/根本原因/发现日期/期望关闭日期字段。
**差距：**
- 缺失：独立路由 `/compliance/problem-reports/create`
- 缺失：步骤条
- 缺失：问题类型字段（NC/Obs/不良事件/客户投诉/审计发现）
- 缺失：根本原因字段
- 缺失：发现日期、期望关闭日期字段
- 缺失：CAPA 自动推荐提示卡
- 缺失：关联需求列表（原型支持多选，实现仅文本输入编号）

### 15. problem-report-detail（问题报告详情） — P0
**原型：** 独立页面，基本信息网格 + 5-Why 根本原因分析 + CAPA 措施列表 + 处理流程时间线（5 节点：提交→质量经理确认→RCA→CAPA 执行→验证关闭）
**实现：** 无独立页面，详情面板内嵌在 ProblemReport.vue 中，结构与原型相似但内容字段有差异。
**差距：**
- 缺失：独立路由 `/compliance/problem-reports/:id`
- 缺失：5-Why 分析展示
- 缺失：处理流程时间线（5 节点带状态）
- 缺失：导出 PDF 按钮
- 缺失：报告人/关联项目 字段

---

## 三、关键字段/枚举值不一致汇总

| 字段 | 原型 | 实现 | 影响 |
|---|---|---|---|
| 严重度（PR） | `CRITICAL/MAJOR/MINOR` | `CRITICAL/MAJOR/MINOR` | 一致 |
| 严重度（PR 创建） | `critical/major/minor` | `CRITICAL/MAJOR/MINOR` | 命名规范不一致（小写 vs 大写） |
| 来源 | `internal/external/regulatory` | `internal/external/regulatory` | 一致 |
| 状态（PR） | `Open/Analyzing/Correcting/Verifying/Closed` | `Open/Analyzing/Correcting/Verifying/Closed` | 一致 |
| 风险等级（SOUP） | `low/medium/high/critical` | `LOW/MEDIUM/HIGH` | 命名规范不一致，少一档 critical |
| 组件类型（SOUP） | `open-source/commercial/proprietary` | `LIBRARY/FRAMEWORK/TOOL/OS` | **完全不同的维度**（来源 vs 用途） |
| IEC 合规状态 | `compliant/partial/non_compliant/not_applicable` | `COMPLIANT/PARTIAL/NON_COMPLIANT/NOT_APPLICABLE/PENDING` | 多 PENDING |
| 法规类型 | `nmpa/fda/ce/iec` | `IEC62304/ISO14971/21CFR11/GMP` | **完全不同维度** |
| 基线状态 | `Locked/Unlocked/Draft` | `LOCKED/DRAFT`（缺 UNLOCKED 实际语义） | 实现用 stat 字段做 unlocked 计数但未单独建枚举 |
| 基线类型 | `milestone/release/audit/custom` | （实现中无此字段） | **字段缺失** |

---

## 四、总结

合规域存在 **严重的页面缺失** 问题：
- 12 个原型对应的 15 个独立页面中，前端**仅完整实现 4 个**（Baselines/SoupManagement/ProblemReport/Iec62304），其余 11 个均为"部分覆盖"或"完全缺失"。
- 缺失最严重：compliance-regulations、compliance-safety、soup-review、soup-detail、problem-report-create、problem-report-detail、baselines-detail、baselines-edit、baseline-compare（共 9 个独立页面）。
- 命名规范不统一：小写/大写枚举混用（如 SOUP 风险等级、PR 严重度）。
- 部分字段语义被简化（如 SOUP 组件类型维度从"来源"改为"用途"、法规类型从"机构"改为"标准号"），可能导致后端模型与原型不符。

### 建议优先级
1. **P0（必须补齐）**：补齐 9 个缺失独立页面（路由 + Vue 组件）
2. **P1（建议修复）**：对齐枚举值（CRITICAL/critical、open-source/LIBRARY 等）；补齐创建/详情/编辑字段
3. **P2（优化）**：卡片式布局改为表格（基线列表）、新增导出 PDF/Excel 按钮
