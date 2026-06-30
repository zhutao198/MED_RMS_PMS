# DDL 变更日志

> **文档用途**：记录 Med-RMS 数据库所有 DDL 变更的顺序、用途和执行状态，是数据库部署的唯一权威参考。
> **维护规则**：每次数据库表结构变更必须在此文档登记，登记后才能执行。重新部署时须按顺序执行所有文件。
> **最后更新**：2026-06-29
> **数据库**：PostgreSQL 16 | Schema 数：11（req / trace / chg / compliance / esign / risk / proj / report / sys / not / public）

---

## 一、执行顺序总览

> **⚠️ 注意**：序号不代表执行顺序。初始建表（`init_database.sql`）和完整建表（`med_rms_ddl.sql`）须先执行，再执行增量补丁。推荐使用本文档「二、推荐执行顺序」章节的顺序。

| 序号 | 文件名 | 用途 | 日期 | Schema | 状态 |
|------|--------|------|------|--------|------|
| — | `init_database.sql` | 初始化数据库 + 11 个 Schema | 2026-06-06 | ALL | ✅ 初始 |
| — | `med_rms_ddl.sql` | 初始完整建表（Detailed/02-DDL/） | 2026-05-22 | ALL | ✅ 初始 |
| 01 | `030_risk_project_tables.sql` | 风险模块：风险/风险登记/风险评估/风险接受 | 2026-05-25 | risk | ✅ |
| 02 | `040_notification_testcase_tables.sql` | 通知模块 + 测试用例表 | 2026-05-25 | not / req | ✅ |
| 03 | `050_gantt_milestone_task_tables.sql` | 项目管理：甘特图/里程碑/任务 | 2026-05-25 | proj | ✅ |
| 04 | `060_requirement_source_fields.sql` | 需求来源/来源编号字段（修复 FR-0.6） | 2026-06-02 | req | ✅ |
| 05 | `070_suspect_flag.sql` | 需求/测试用例 suspect 标记字段（FR-0.10） | 2026-06-02 | req | ✅ |
| 06 | `080_problem_report_table.sql` | 问题报告表（FR-0.14） | 2026-06-02 | compliance | ✅ |
| 07 | `090_iec62304_checklist.sql` | IEC 62304 检查清单（FR-0.15） | 2026-06-02 | compliance | ✅ |
| 08 | `100_compliance_template.sql` | 合规报告模板（FR-1.9） | 2026-06-02 | compliance | ✅ |
| 09 | `110_change_delegation.sql` | 变更委派/会签字段（FR-1.7） | 2026-06-02 | chg | ✅ |
| 10 | `111_fmea_fields.sql` | FMEA RPN 字段（FR-1.8） | 2026-06-02 | risk | ✅ |
| 11 | `112_migration_job.sql` | 数据迁移任务表（FR-1.13） | 2026-06-02 | compliance | ✅ |
| 12 | `113_worklog.sql` | 工时记录表（FR-2.9） | 2026-06-03 | proj | ✅ |
| 13 | `114_esignature_signature_intent.sql` | 签名意图表（e-sign） | 2026-06-06 | esign | ✅ |
| 14 | `115_outbox_message.sql` | 事件 Outbox 表（Phase 2 预备，暂未启用） | 2026-06-06 | compliance | ⚠️ 未启用 |
| 15 | `116_compliance_p0_tables.sql` | 合规 P0 补充表 | 2026-06-06 | compliance | ✅ |
| 16 | `117_change_p0_tables.sql` | 变更 P0 补充表 | 2026-06-06 | chg | ✅ |
| 17 | `120_rbac_tables.sql` | RBAC 权限表（sys_schema） | 2026-06-03 | sys | ✅ |
| 18 | `121_rbac_seed_data.sql` | RBAC 种子数据（8 角色 + 63 权限 + 284 关联 + 8 用户）| 2026-06-03 | sys | ✅ |
| 19 | `122_dict_seed_full.sql` | 字典表全量种子数据 | 2026-06-03 | sys | ✅ |
| 20 | `123_test_case_seed.sql` | 测试用例种子数据 | 2026-06-03 | req | ✅ |
| 21 | `124_review_version_tables.sql` | 评审版本表（t_review_version / t_review_record） | 2026-06-03 | req | ✅ |
| 22 | `125_audit_log_schema_sync.sql` | 审计日志字段重命名/补充（event_type/reason/jsonb→TEXT）| 2026-06-03 | compliance | ✅ |
| 23 | `126_requirement_created_by_backfill.sql` | `created_by` 回填（需求模块，updated_by 补全） | 2026-06-04 | req | ✅ |
| 24 | `127_requirement_pool_created_by_backfill.sql` | `created_by` 回填（需求池模块） | 2026-06-04 | req | ✅ |
| 25 | `128_requirement_p0_fields.sql` | 需求 P0 补充字段 | 2026-06-06 | req | ✅ |
| 26 | `129_cti_subtables.sql` | CTI 分层子表（User/Product/System/Design Requirement） | 2026-06-06 | req | ✅ |
| 27 | `130_trace_link_table.sql` | 追溯链接表（trace_schema） | 2026-06-06 | trace | ✅ |
| 28 | `131_change_timeline.sql` | 变更时间线记录表（变更历史） | 2026-06-06 | chg | ✅ |
| 29 | `132_baseline_dual_sig.sql` | 基线双人签名锁定表 | 2026-06-06 | compliance | ✅ |
| 30 | `133_report_dashboard_tables.sql` | 报表/仪表盘配置表 | 2026-06-08 | report | ✅ |
| 31 | `134_task_predecessor.sql` | 任务前置依赖表（甘特图 CPM） | 2026-06-08 | proj | ✅ |
| 32 | `135_change_attachment.sql` | 变更附件表 | 2026-06-08 | chg | ✅ |
| 33 | `136_report_config.sql` | 报表配置表 | 2026-06-08 | compliance | ✅ |
| 34 | `137_role_permission.sql` | 角色权限关联表补充 | 2026-06-08 | sys | ✅ |
| 35 | `138_notification_channels.sql` | 通知渠道表（站内信/邮件/微信） | 2026-06-08 | not | ✅ |
| 36 | `139_audit_log_rename.sql` | 审计日志字段重命名（hash_value/previous_hash/old_value/new_value）| 2026-06-08 | compliance | ✅ |
| 37 | `140_trace_gap_ignored.sql` | 追溯缺口忽略记录表 | 2026-06-09 | trace | ✅ |
| 38 | `141_requirement_category_field.sql` | 需求分类字段（硬件/软件/软硬件，FR-0.6 补全） | 2026-06-12 | req | ✅ |
| 39 | `142_project_deliverable_table.sql` | 项目交付物表 | 2026-06-16 | proj | ✅ |
| 40 | `143_organization_department.sql` | 组织架构/部门表 | 2026-06-18 | sys | ✅ |
| 41 | `144_add_risk_project_id.sql` | 风险表新增 projectId 外键 | 2026-06-29 | risk | ✅ |
| 42 | `145_backfill_risk_project_id.sql` | 风险表 projectId 回填 | 2026-06-29 | risk | ✅ |
| 43 | `146_audit_hash_chain_fix.sql` | 审计日志哈希链修复（B-01 P0 合规修复：历史 prev_hash 初始化 + CHAIN_ANCHOR + NOT NULL 约束）| 2026-06-29 | compliance | ✅ |
| 44 | `147_default_id1_data.sql` | 种子数据补全（ID=1 默认演示数据：项目/需求/变更/风险/交付物）| 2026-06-30 | 多 Schema | ✅ |
| — | `test_data_full_flow.sql` | 测试数据（全流程场景） | 2026-05-29 | ALL | ⚠️ 仅测试 |
| — | `fix_test_data.sql` | 测试数据修复 | 2026-05-29 | ALL | ⚠️ 仅测试 |
| — | `trace_test_data.sql` | 追溯模块测试数据 | 2026-05-31 | trace | ⚠️ 仅测试 |
| — | `create_8117_*.sql` | 追溯模块建表（8117 项目专用） | 2026-05-31 | trace | ⚠️ 仅测试 |

---

## 二、推荐执行顺序

> 新环境部署或重新部署时，按以下顺序执行。

### Phase 0：初始建表（任选其一）

```
选项 A（推荐）：Detailed/02-DDL/med_rms_ddl.sql（133KB，初始完整建表）
选项 B：Code/backend/ddl/init_database.sql（25KB，初始建库+Schema）
```

> 两个文件功能有重叠，选一个执行即可。生产环境建议用 `med_rms_ddl.sql`。

### Phase 1：核心功能补充（按序号）

```
01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11 → 12 → 13
```

### Phase 2：合规与变更增强

```
14 → 15 → 16
```

### Phase 3：RBAC 权限体系

```
17 → 18 → 19 → 20 → 37
```

### Phase 4：审计日志规范化

```
21 → 22 → 25 → 26 → 39
```

### Phase 5：业务表完善

```
23 → 24 → 27 → 28 → 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 38 → 40 → 41 → 42
```

> ⚠️ 序号不连续（如跳过 112/113）是正常的，因为有 `112_notification_tables_columns.sql` 等特殊文件。参考「一、执行顺序总览」中的实际文件名。

---

## 三、关键变更说明

### 3.1 需求模块（req_schema）

| 文件 | 核心变更 |
|------|---------|
| `060_requirement_source_fields.sql` | 新增 `source`（需求来源：CUSTOMER/MARKET/REGULATION/INTERNAL/COMPETITOR）和 `source_no`（来源编号/法规条款号）。修复了原 DDL 中 `source Requirement,` 的 SQL 笔误。 |
| `070_suspect_flag.sql` | 新增 `is_suspect BOOLEAN` 字段（需求表和测试用例表），支持 FR-0.10 追溯断裂自动标记。 |
| `113_worklog.sql` | 工时记录表。**注意**：`task_id` 字段初版为 NOT NULL，导致 v1.24 INSERT 失败，改为可空。 |
| `124_review_version_tables.sql` | 新增 `t_review_version` 和 `t_review_record` 表（评审版本历史）。**注意**：这两个表在开发日志 v1.28 DDL 核查时被发现缺失（`ddl_sync_check.py` 检测）。 |
| `126_requirement_created_by_backfill.sql` | 回填 `created_by` / `updated_by` 字段（历史数据设置为 admin userId=1），解决 `updatedBy` 部分缺失问题。 |
| `128_requirement_p0_fields.sql` | 需求 P0 补充字段。 |
| `129_cti_subtables.sql` | CTI 分层子表（`t_req_v_urs / t_req_v_prs / t_req_v_srs / t_req_v_drs`），与 `t_requirement_version` 通过 `version_id` 关联。 |
| `141_requirement_category_field.sql` | 新增 `requirement_category`（需求分类：SOFTWARE/HARDWARE/BOTH），FR-0.6 补全。 |

### 3.2 合规模块（compliance_schema）

| 文件 | 核心变更 |
|------|---------|
| `080_problem_report_table.sql` | 问题报告表（ISO 13485 纠正预防措施）。 |
| `090_iec62304_checklist.sql` | IEC 62304 合规检查清单（Clause 5-9 各条款状态）。 |
| `100_compliance_template.sql` | 合规报告模板（NMPA/FDA/CE）。 |
| `114_esignature_signature_intent.sql` | 签名意图表（`t_signature_intent`）。 |
| `115_outbox_message.sql` | 事件 Outbox 表。**⚠️ 表已建但代码未启用**（Phase 2 事件驱动架构预备）。 |
| `116_compliance_p0_tables.sql` | 合规 P0 补充表。 |
| `125_audit_log_schema_sync.sql` | 审计日志字段规范化：新增 `event_type` / `reason` 列，`old_value` / `new_value` 从 jsonb 改为 TEXT，`hash_value` / `previous_hash` 重命名对齐。**重要**：与 `139_audit_log_rename.sql` 有关联，需按顺序执行。 |
| `132_baseline_dual_sig.sql` | 基线双人签名锁定（21 CFR Part 11）。 |

### 3.3 变更管理模块（chg_schema）

| 文件 | 核心变更 |
|------|---------|
| `110_change_delegation.sql` | 新增委派/会签 8 个字段（`delegate_user_id` / `delegate_reason` / `sign_user_ids` / `sign_meanings` 等）。**注意**：v1.43 R110 发现前端委派会签 userId 硬编码（已在 R110 修复）。 |
| `117_change_p0_tables.sql` | 变更 P0 补充表。 |
| `131_change_timeline.sql` | 变更时间线记录表（记录每个变更的生命周期事件）。 |
| `135_change_attachment.sql` | 变更附件表。 |

### 3.4 追溯管理模块（trace_schema）

| 文件 | 核心变更 |
|------|---------|
| `130_trace_link_table.sql` | 追溯链接表（`t_trace_link`），支持需求↔需求、需求↔测试用例双向追溯。 |
| `140_trace_gap_ignored.sql` | 追溯缺口忽略记录表（允许用户忽略特定 Gap）。 |

### 3.5 项目管理模块（proj_schema）

| 文件 | 核心变更 |
|------|---------|
| `050_gantt_milestone_task_tables.sql` | 甘特图/里程碑/任务表（`t_gantt_task` / `t_milestone` / `t_project_task`）。 |
| `113_worklog.sql` | 工时记录表（`t_worklog`）。**注意**：`task_id` 字段可空。 |
| `134_task_predecessor.sql` | 任务前置依赖表（甘特图 CPM 关键路径计算）。 |
| `142_project_deliverable_table.sql` | 项目交付物表。 |

### 3.6 系统管理模块（sys_schema）

| 文件 | 核心变更 |
|------|---------|
| `120_rbac_tables.sql` | RBAC 权限体系（`t_permission` / `t_role` / `t_role_permission` / `t_user_role`）。 |
| `121_rbac_seed_data.sql` | 种子数据：8 角色（admin/qa_mgr/pm/re/reviewer/risk_mgr/compliance/viewer）+ 63 权限码 + 284 关联 + 8 测试用户（密码统一 admin123）。**注意**：这与开发日志中「RBAC 端点从 4/210 提升到 210/210」对应。 |
| `122_dict_seed_full.sql` | 字典表全量种子数据。 |
| `137_role_permission.sql` | 角色权限关联表补充。 |
| `143_organization_department.sql` | 组织架构/部门表。 |

### 3.7 风险管理模块（risk_schema）

| 文件 | 核心变更 |
|------|---------|
| `030_risk_project_tables.sql` | 风险/风险登记/风险评估/风险接受表。 |
| `111_fmea_fields.sql` | FMEA RPN 字段（S/O/D → RPN）。 |
| `144_add_risk_project_id.sql` | 风险表新增 `project_id` 外键（2026-06-29 最新）。 |
| `145_backfill_risk_project_id.sql` | 风险表 `project_id` 回填（2026-06-29 最新）。 |

### 3.8 通知模块（not_schema）

| 文件 | 核心变更 |
|------|---------|
| `040_notification_testcase_tables.sql` | 通知表 + 测试用例关联表。 |
| `112_notification_tables_columns.sql` | 通知表字段补充（2026-06-05）。 |
| `138_notification_channels.sql` | 通知渠道表（站内信/邮件/微信/企微）。 |

---

## 四、已知问题与注意事项

### ⚠️ 4.1 `isDeleted` 软删除字段

| 项目 | 详情 |
|------|------|
| **问题** | 多数业务表在 `init_database.sql` 创建时未包含 `is_deleted` 字段 |
| **影响** | MyBatis-Plus `@TableLogic` 注解在 `updateById` 中自动忽略软删除字段，直接调用 `deleteById` 不会软删除 |
| **正确做法** | Service 删除方法必须显式写 `UPDATE SET is_deleted = true`，禁止出现 DELETE 语句 |
| **涉及表** | 需全量核查，确认所有业务表已补充 `is_deleted` 字段 |

### ⚠️ 4.2 跨 Schema 外键缺失

| 项目 | 详情 |
|------|------|
| **设计决策** | 跨 Schema 引用「不建立外键约束」，通过业务层（Service）保证一致性 |
| **风险** | 数据库层无法阻止悬挂引用（如删除 project 后 requirement.project_id 成为孤儿外键） |
| **建议** | 业务层对高频跨引用添加级联检查；低频引用保持弱引用 |

### ✅ 4.3 Outbox 表已启用（R111 修订）

| 项目 | 详情 |
|------|------|
| **表** | `115_outbox_message.sql`（public.t_outbox_message） |
| **状态** | ✅ **已启用**（R111 验证）：`med-rms-common/outbox/OutboxService.java` 已实现 `append()` / `subscribe()` / `publishPending()` 三个核心方法；`MedRmsApplication.java` 已加 `@EnableScheduling`；3 个模块已调用（ChangeService / OaIntegrationService / TraceabilityService） |
| **模式** | in-process subscriber 模式（30 秒定时器，非 Debezium CDC） |
| **说明** | 原 v1.46「未启用」描述过时；当前采用务实简化方案（无 Kafka 部署） |

### ⚠️ 4.4 哈希链校验依赖字段规范化【R113 升级：完整可用】

| 项目 | 详情 |
|------|------|
| **依赖** | 审计日志哈希链校验依赖 `hash_value` / `previous_hash` 字段（`125` / `139` 定义） |
| **注意** | `125_audit_log_schema_sync.sql` 和 `139_audit_log_rename.sql` 须按顺序执行，否则哈希链校验会失败 |
| **R113 升级** | DDL 146 历史 prev_hash 修复 + AuthController LOGIN 哈希链接入 + AuditLogService LIMIT 大小写 bug 修复后，哈希链校验已**完整可用**（合规可追溯） |

### ⚠️ 4.5 Worklog task_id 字段

| 项目 | 详情 |
|------|------|
| **文件** | `113_worklog.sql` |
| **问题** | 初版 DDL `task_id BIGINT NOT NULL` 导致 v1.24 INSERT 失败 |
| **修复** | 已在 DDL 中改为可空：`ALTER TABLE prj_schema.t_worklog ALTER COLUMN task_id DROP NOT NULL` |
| **建议** | 如从旧 DDL 初始化，先执行 `ALTER` 再启动应用 |

---

## 五、Schema 清单与当前状态

| Schema | 用途 | 核心表数 | 关键文件 |
|--------|------|---------|---------|
| `req_schema` | 需求管理 | 11+ | `060`/`070`/`113`/`124`/`126`/`127`/`128`/`129`/`141` |
| `trace_schema` | 追溯管理 | 2+ | `130`/`140` |
| `chg_schema` | 变更管理 | 4+ | `110`/`117`/`131`/`135` |
| `compliance_schema` | 合规管理 | 9+ | `080`/`090`/`100`/`114`/`115`/`116`/`125`/`132`/`136`/`139` |
| `esign_schema` | 电子签名 | 3+ | `114` |
| `risk_schema` | 风险管理 | 4+ | `030`/`111`/`144`/`145` |
| `proj_schema` | 项目管理 | 4+ | `050`/`113`/`134`/`142` |
| `report_schema` | 报表与仪表盘 | 3+ | `133` |
| `sys_schema` | 系统管理 | 12+ | `120`/`121`/`122`/`137`/`143` |
| `not_schema` | 通知管理 | 2+ | `040`/`112`/`138` |
| `public` | 公共/字典 | — | `122` |

---

## 六、DDL 文件快速定位

```
Code/backend/ddl/
├── init_database.sql               # 初始建库（Phase 0 备选）
├── med_rms_ddl.sql                 # 初始完整建表（Phase 0 推荐）
│
├── # ─── 核心业务 ───
├── 030_risk_project_tables.sql     # 风险模块
├── 040_notification_testcase_tables.sql  # 通知+测试用例
├── 050_gantt_milestone_task_tables.sql    # 甘特图/里程碑/任务
├── 060_requirement_source_fields.sql       # 需求来源/编号（修复 FR-0.6）
├── 070_suspect_flag.sql            # suspect 标记（FR-0.10）
├── 080_problem_report_table.sql    # 问题报告
├── 090_iec62304_checklist.sql     # IEC 62304 检查清单
├── 100_compliance_template.sql     # 合规报告模板
├── 110_change_delegation.sql       # 变更委派/会签
├── 111_fmea_fields.sql             # FMEA RPN 字段
├── 112_migration_job.sql           # 数据迁移任务
├── 113_worklog.sql                 # 工时记录 ⚠️ task_id 可空
├── 114_esignature_signature_intent.sql     # 签名意图
├── 115_outbox_message.sql          # Outbox 表 ⚠️ 未启用
├── 116_compliance_p0_tables.sql    # 合规 P0 补充
├── 117_change_p0_tables.sql         # 变更 P0 补充
│
├── # ─── RBAC 权限 ───
├── 120_rbac_tables.sql             # RBAC 表结构
├── 121_rbac_seed_data.sql          # RBAC 种子数据
├── 137_role_permission.sql        # 角色权限关联补充
│
├── # ─── 种子数据 ───
├── 122_dict_seed_full.sql          # 字典数据
├── 123_test_case_seed.sql          # 测试用例数据
├── 138_notification_channels.sql   # 通知渠道
│
├── # ─── 审计与合规 ───
├── 124_review_version_tables.sql   # 评审版本表 ⚠️ 曾被遗漏
├── 125_audit_log_schema_sync.sql   # 审计日志规范化
├── 126_requirement_created_by_backfill.sql  # created_by 回填
├── 127_requirement_pool_created_by_backfill.sql  # 需求池回填
├── 128_requirement_p0_fields.sql   # 需求 P0 补充字段
├── 129_cti_subtables.sql           # CTI 分层子表
├── 130_trace_link_table.sql        # 追溯链接表
├── 131_change_timeline.sql         # 变更时间线
├── 132_baseline_dual_sig.sql       # 基线双人签名
├── 133_report_dashboard_tables.sql # 报表/仪表盘
├── 134_task_predecessor.sql        # 任务前置依赖
├── 135_change_attachment.sql        # 变更附件
├── 136_report_config.sql           # 报表配置
├── 139_audit_log_rename.sql        # 审计日志字段重命名
├── 140_trace_gap_ignored.sql       # 追溯缺口忽略
├── 141_requirement_category_field.sql  # 需求分类（FR-0.6）
├── 142_project_deliverable_table.sql   # 项目交付物
├── 143_organization_department.sql     # 组织架构
├── 144_add_risk_project_id.sql     # 风险 projectId 外键
├── 145_backfill_risk_project_id.sql    # 风险 projectId 回填
├── 146_audit_hash_chain_fix.sql   # 审计日志哈希链修复（B-01 P0）
│
├── # ─── 测试数据（仅测试环境）───
├── test_data_full_flow.sql         # 全流程测试数据
├── fix_test_data.sql               # 测试数据修复
├── trace_test_data.sql             # 追溯测试数据
└── create_8117_*.sql               # 8117 项目追溯数据
```

---

## 七、PRD 变更记录

| 日期 | 变更内容 | 变更人 | 依据 |
|------|---------|--------|------|
| 2026-06-29 | 新建本变更日志，整合所有 DDL 文件登记 | QClaw | 项目根目录扫描 |
| 2026-06-29 | 新建 `144_add_risk_project_id.sql` / `145_backfill_risk_project_id.sql` | — | 最新变更 |
| 2026-06-29 | **R111 修订**：§4.3「Outbox 表未启用」状态修订为「已启用」（in-process 模式） | QClaw | 偏差清单缺陷 1 验证 |
| 2026-06-29 | **R113 修订**：新增 `146_audit_hash_chain_fix.sql`（B-01 P0 合规修复）；§4.4 哈希链校验从「依赖字段规范化」→「完整可用」 | QClaw | B-01 P0 合规修复 |
