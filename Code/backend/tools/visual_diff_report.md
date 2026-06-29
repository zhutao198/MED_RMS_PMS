# Med-RMS 视觉/交互验收报告

> 生成时间：2026-06-10 | 工具：`tools/visual_diff_scan.py` | 模式：structural | 阈值：match<3% < minor<8% < moderate<15% < major

## 一、总体统计

| 维度 | 数量 | 占比 |
|------|------|------|
| 前端路由总数 | 88 | 100% |
| 交互原型总数 | 75 | - |
| 成功建立映射 | 84 | 95.5% |
| 未建立映射（无对应原型） | 4 | 4.5% |
| 🟢 视觉匹配 (match, <3%) | 46 | 54.8% |
| 🟡 轻度偏差 (minor, 3-8%) | 21 | 25.0% |
| 🟠 中度偏差 (moderate, 8-15%) | 11 | 13.1% |
| 🔴 重度偏差 (major, ≥15%) | 6 | 7.1% |
| 缺失截图 | 0 | - |

## 二、未映射路由清单（无对应原型）

- `/esignature`
- `/projects/:id/gantt`
- `/milestones`
- `/testcases`

## 三、未使用原型清单（无对应路由）

- `baseline-create`
- `change-approvals`
- `change-edit`
- `change-impact-result`
- `dict-management`
- `e-sign-popup`
- `login`
- `req-import`
- `risks-register-create`
- `safety-classification-detail`
- `signatures`
- `system-admin`
- `user-profile`

## 四、Top 20 视觉偏差（按 diff_ratio 降序）

| 路由 | 原型 | 差异比 | 等级 | 路由截图 | 原型截图 | Diff 图 |
|------|------|--------|------|----------|----------|---------|
| `/projects` | `projects` | 34.44% | 🔴 major | [projects.png](Code/backend/tools/visual_baselines/routes/projects.png) | [projects.png](Code/backend/tools/visual_baselines/prototypes/projects.png) | [projects__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects__vs__projects_struct.png) |
| `/projects/templates` | `projects` | 34.17% | 🔴 major | [projects_templates.png](Code/backend/tools/visual_baselines/routes/projects_templates.png) | [projects.png](Code/backend/tools/visual_baselines/prototypes/projects.png) | [projects_templates__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_templates__vs__projects_struct.png) |
| `/projects/gantt` | `projects` | 34.17% | 🔴 major | [projects_gantt.png](Code/backend/tools/visual_baselines/routes/projects_gantt.png) | [projects.png](Code/backend/tools/visual_baselines/prototypes/projects.png) | [projects_gantt__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_gantt__vs__projects_struct.png) |
| `/projects/ipd` | `projects` | 34.17% | 🔴 major | [projects_ipd.png](Code/backend/tools/visual_baselines/routes/projects_ipd.png) | [projects.png](Code/backend/tools/visual_baselines/prototypes/projects.png) | [projects_ipd__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_ipd__vs__projects_struct.png) |
| `/projects/resources` | `projects` | 34.17% | 🔴 major | [projects_resources.png](Code/backend/tools/visual_baselines/routes/projects_resources.png) | [projects.png](Code/backend/tools/visual_baselines/prototypes/projects.png) | [projects_resources__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_resources__vs__projects_struct.png) |
| `/projects/worklog` | `projects` | 34.17% | 🔴 major | [projects_worklog.png](Code/backend/tools/visual_baselines/routes/projects_worklog.png) | [projects.png](Code/backend/tools/visual_baselines/prototypes/projects.png) | [projects_worklog__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_worklog__vs__projects_struct.png) |
| `/risk` | `risks-matrix` | 12.50% | 🟠 moderate | [risk.png](Code/backend/tools/visual_baselines/routes/risk.png) | [risks-matrix.png](Code/backend/tools/visual_baselines/prototypes/risks-matrix.png) | [risk__vs__risks-matrix_struct.png](Code/backend/tools/visual_diffs/risk__vs__risks-matrix_struct.png) |
| `/risks/matrix` | `risks-matrix` | 12.50% | 🟠 moderate | [risks_matrix.png](Code/backend/tools/visual_baselines/routes/risks_matrix.png) | [risks-matrix.png](Code/backend/tools/visual_baselines/prototypes/risks-matrix.png) | [risks_matrix__vs__risks-matrix_struct.png](Code/backend/tools/visual_diffs/risks_matrix__vs__risks-matrix_struct.png) |
| `/reports` | `reports` | 11.39% | 🟠 moderate | [reports.png](Code/backend/tools/visual_baselines/routes/reports.png) | [reports.png](Code/backend/tools/visual_baselines/prototypes/reports.png) | [reports__vs__reports_struct.png](Code/backend/tools/visual_diffs/reports__vs__reports_struct.png) |
| `/changes/:id/impact` | `change-impact-analysis` | 10.56% | 🟠 moderate | [changes_1_impact.png](Code/backend/tools/visual_baselines/routes/changes_1_impact.png) | [change-impact-analysis.png](Code/backend/tools/visual_baselines/prototypes/change-impact-analysis.png) | [changes__id_impact__vs__change-impact-analysis_struct.png](Code/backend/tools/visual_diffs/changes__id_impact__vs__change-impact-analysis_struct.png) |
| `/esignature/settings` | `e-sign-settings` | 9.17% | 🟠 moderate | [esignature_settings.png](Code/backend/tools/visual_baselines/routes/esignature_settings.png) | [e-sign-settings.png](Code/backend/tools/visual_baselines/prototypes/e-sign-settings.png) | [esignature_settings__vs__e-sign-settings_struct.png](Code/backend/tools/visual_diffs/esignature_settings__vs__e-sign-settings_struct.png) |
| `/reports/export` | `report-export` | 9.17% | 🟠 moderate | [reports_export.png](Code/backend/tools/visual_baselines/routes/reports_export.png) | [report-export.png](Code/backend/tools/visual_baselines/prototypes/report-export.png) | [reports_export__vs__report-export_struct.png](Code/backend/tools/visual_diffs/reports_export__vs__report-export_struct.png) |
| `/notifications` | `notifications` | 8.89% | 🟠 moderate | [notifications.png](Code/backend/tools/visual_baselines/routes/notifications.png) | [notifications.png](Code/backend/tools/visual_baselines/prototypes/notifications.png) | [notifications__vs__notifications_struct.png](Code/backend/tools/visual_diffs/notifications__vs__notifications_struct.png) |
| `/system` | `system` | 8.06% | 🟠 moderate | [system.png](Code/backend/tools/visual_baselines/routes/system.png) | [system.png](Code/backend/tools/visual_baselines/prototypes/system.png) | [system__vs__system_struct.png](Code/backend/tools/visual_diffs/system__vs__system_struct.png) |
| `/system/dicts` | `system` | 8.06% | 🟠 moderate | [system_dicts.png](Code/backend/tools/visual_baselines/routes/system_dicts.png) | [system.png](Code/backend/tools/visual_baselines/prototypes/system.png) | [system_dicts__vs__system_struct.png](Code/backend/tools/visual_diffs/system_dicts__vs__system_struct.png) |
| `/system/migration` | `system` | 8.06% | 🟠 moderate | [system_migration.png](Code/backend/tools/visual_baselines/routes/system_migration.png) | [system.png](Code/backend/tools/visual_baselines/prototypes/system.png) | [system_migration__vs__system_struct.png](Code/backend/tools/visual_diffs/system_migration__vs__system_struct.png) |
| `/system/profile` | `system` | 8.06% | 🟠 moderate | [system_profile.png](Code/backend/tools/visual_baselines/routes/system_profile.png) | [system.png](Code/backend/tools/visual_baselines/prototypes/system.png) | [system_profile__vs__system_struct.png](Code/backend/tools/visual_diffs/system_profile__vs__system_struct.png) |
| `/requirements/:id/versions/create` | `req-version-create` | 7.22% | 🟡 minor | [requirements_1_versions_create.png](Code/backend/tools/visual_baselines/routes/requirements_1_versions_create.png) | [req-version-create.png](Code/backend/tools/visual_baselines/prototypes/req-version-create.png) | [requirements__id_versions_create__vs__req-version-create_struct.png](Code/backend/tools/visual_diffs/requirements__id_versions_create__vs__req-version-create_struct.png) |
| `/traceability/import` | `trace-import` | 6.67% | 🟡 minor | [traceability_import.png](Code/backend/tools/visual_baselines/routes/traceability_import.png) | [trace-import.png](Code/backend/tools/visual_baselines/prototypes/trace-import.png) | [traceability_import__vs__trace-import_struct.png](Code/backend/tools/visual_diffs/traceability_import__vs__trace-import_struct.png) |
| `/changes/approvals` | `change-approve` | 6.67% | 🟡 minor | [changes_approvals.png](Code/backend/tools/visual_baselines/routes/changes_approvals.png) | [change-approve.png](Code/backend/tools/visual_baselines/prototypes/change-approve.png) | [changes_approvals__vs__change-approve_struct.png](Code/backend/tools/visual_diffs/changes_approvals__vs__change-approve_struct.png) |

## 五、按等级分组的全部偏差

### 🔴 MAJOR (6 个)

| # | 路由 | 原型 | 差异比 | Diff 图 |
|---|------|------|--------|---------|
| 1 | `/projects` | `projects` | 34.44% | [projects__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects__vs__projects_struct.png) |
| 2 | `/projects/templates` | `projects` | 34.17% | [projects_templates__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_templates__vs__projects_struct.png) |
| 3 | `/projects/gantt` | `projects` | 34.17% | [projects_gantt__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_gantt__vs__projects_struct.png) |
| 4 | `/projects/ipd` | `projects` | 34.17% | [projects_ipd__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_ipd__vs__projects_struct.png) |
| 5 | `/projects/resources` | `projects` | 34.17% | [projects_resources__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_resources__vs__projects_struct.png) |
| 6 | `/projects/worklog` | `projects` | 34.17% | [projects_worklog__vs__projects_struct.png](Code/backend/tools/visual_diffs/projects_worklog__vs__projects_struct.png) |

### 🟠 MODERATE (11 个)

| # | 路由 | 原型 | 差异比 | Diff 图 |
|---|------|------|--------|---------|
| 1 | `/risk` | `risks-matrix` | 12.50% | [risk__vs__risks-matrix_struct.png](Code/backend/tools/visual_diffs/risk__vs__risks-matrix_struct.png) |
| 2 | `/risks/matrix` | `risks-matrix` | 12.50% | [risks_matrix__vs__risks-matrix_struct.png](Code/backend/tools/visual_diffs/risks_matrix__vs__risks-matrix_struct.png) |
| 3 | `/reports` | `reports` | 11.39% | [reports__vs__reports_struct.png](Code/backend/tools/visual_diffs/reports__vs__reports_struct.png) |
| 4 | `/changes/:id/impact` | `change-impact-analysis` | 10.56% | [changes__id_impact__vs__change-impact-analysis_struct.png](Code/backend/tools/visual_diffs/changes__id_impact__vs__change-impact-analysis_struct.png) |
| 5 | `/esignature/settings` | `e-sign-settings` | 9.17% | [esignature_settings__vs__e-sign-settings_struct.png](Code/backend/tools/visual_diffs/esignature_settings__vs__e-sign-settings_struct.png) |
| 6 | `/reports/export` | `report-export` | 9.17% | [reports_export__vs__report-export_struct.png](Code/backend/tools/visual_diffs/reports_export__vs__report-export_struct.png) |
| 7 | `/notifications` | `notifications` | 8.89% | [notifications__vs__notifications_struct.png](Code/backend/tools/visual_diffs/notifications__vs__notifications_struct.png) |
| 8 | `/system` | `system` | 8.06% | [system__vs__system_struct.png](Code/backend/tools/visual_diffs/system__vs__system_struct.png) |
| 9 | `/system/dicts` | `system` | 8.06% | [system_dicts__vs__system_struct.png](Code/backend/tools/visual_diffs/system_dicts__vs__system_struct.png) |
| 10 | `/system/migration` | `system` | 8.06% | [system_migration__vs__system_struct.png](Code/backend/tools/visual_diffs/system_migration__vs__system_struct.png) |
| 11 | `/system/profile` | `system` | 8.06% | [system_profile__vs__system_struct.png](Code/backend/tools/visual_diffs/system_profile__vs__system_struct.png) |

### 🟡 MINOR (21 个)

| # | 路由 | 原型 | 差异比 | Diff 图 |
|---|------|------|--------|---------|
| 1 | `/requirements/:id/versions/create` | `req-version-create` | 7.22% | [requirements__id_versions_create__vs__req-version-create_struct.png](Code/backend/tools/visual_diffs/requirements__id_versions_create__vs__req-version-create_struct.png) |
| 2 | `/traceability/import` | `trace-import` | 6.67% | [traceability_import__vs__trace-import_struct.png](Code/backend/tools/visual_diffs/traceability_import__vs__trace-import_struct.png) |
| 3 | `/changes/approvals` | `change-approve` | 6.67% | [changes_approvals__vs__change-approve_struct.png](Code/backend/tools/visual_diffs/changes_approvals__vs__change-approve_struct.png) |
| 4 | `/changes/:id/execute` | `change-execute` | 6.67% | [changes__id_execute__vs__change-execute_struct.png](Code/backend/tools/visual_diffs/changes__id_execute__vs__change-execute_struct.png) |
| 5 | `/changes/:id/verify` | `change-verify` | 6.67% | [changes__id_verify__vs__change-verify_struct.png](Code/backend/tools/visual_diffs/changes__id_verify__vs__change-verify_struct.png) |
| 6 | `/signature-history/:id` | `signature-history-detail` | 6.67% | [signature-history__id__vs__signature-history-detail_struct.png](Code/backend/tools/visual_diffs/signature-history__id__vs__signature-history-detail_struct.png) |
| 7 | `/signature-intent/create` | `signature-intent-create` | 6.67% | [signature-intent_create__vs__signature-intent-create_struct.png](Code/backend/tools/visual_diffs/signature-intent_create__vs__signature-intent-create_struct.png) |
| 8 | `/signature-intent/:id` | `signature-intent-detail` | 6.67% | [signature-intent__id__vs__signature-intent-detail_struct.png](Code/backend/tools/visual_diffs/signature-intent__id__vs__signature-intent-detail_struct.png) |
| 9 | `/projects/:id/edit` | `project-edit` | 6.67% | [projects__id_edit__vs__project-edit_struct.png](Code/backend/tools/visual_diffs/projects__id_edit__vs__project-edit_struct.png) |
| 10 | `/projects/:id/members/add` | `project-members-add` | 6.67% | [projects__id_members_add__vs__project-members-add_struct.png](Code/backend/tools/visual_diffs/projects__id_members_add__vs__project-members-add_struct.png) |
| 11 | `/system/users` | `user-management` | 6.67% | [system_users__vs__user-management_struct.png](Code/backend/tools/visual_diffs/system_users__vs__user-management_struct.png) |
| 12 | `/system/roles/:id/edit` | `role-edit` | 6.67% | [system_roles__id_edit__vs__role-edit_struct.png](Code/backend/tools/visual_diffs/system_roles__id_edit__vs__role-edit_struct.png) |
| 13 | `/system/organization` | `organization` | 6.67% | [system_organization__vs__organization_struct.png](Code/backend/tools/visual_diffs/system_organization__vs__organization_struct.png) |
| 14 | `/requirements/:id/versions` | `req-versions` | 6.67% | [requirements__id_versions__vs__req-versions_struct.png](Code/backend/tools/visual_diffs/requirements__id_versions__vs__req-versions_struct.png) |
| 15 | `/compliance/soup/:id` | `soup-detail` | 6.67% | [compliance_soup__id__vs__soup-detail_struct.png](Code/backend/tools/visual_diffs/compliance_soup__id__vs__soup-detail_struct.png) |
| 16 | `/compliance/soup/:id/review` | `soup-review` | 6.67% | [compliance_soup__id_review__vs__soup-review_struct.png](Code/backend/tools/visual_diffs/compliance_soup__id_review__vs__soup-review_struct.png) |
| 17 | `/compliance/problem-report/create` | `problem-report-create` | 6.67% | [compliance_problem-report_create__vs__problem-report-create_struct.png](Code/backend/tools/visual_diffs/compliance_problem-report_create__vs__problem-report-create_struct.png) |
| 18 | `/compliance/problem-report/:id` | `problem-report-detail` | 6.67% | [compliance_problem-report__id__vs__problem-report-detail_struct.png](Code/backend/tools/visual_diffs/compliance_problem-report__id__vs__problem-report-detail_struct.png) |
| 19 | `/compliance/baselines/compare` | `baseline-compare` | 6.67% | [compliance_baselines_compare__vs__baseline-compare_struct.png](Code/backend/tools/visual_diffs/compliance_baselines_compare__vs__baseline-compare_struct.png) |
| 20 | `/compliance/baselines/:id/edit` | `baselines-edit` | 6.67% | [compliance_baselines__id_edit__vs__baselines-edit_struct.png](Code/backend/tools/visual_diffs/compliance_baselines__id_edit__vs__baselines-edit_struct.png) |
| 21 | `/audit-logs/export` | `audit-logs-export` | 6.67% | [audit-logs_export__vs__audit-logs-export_struct.png](Code/backend/tools/visual_diffs/audit-logs_export__vs__audit-logs-export_struct.png) |

## 六、说明

1. **数据**：用现有 DB 真实数据渲染，admin 登录态
2. **视口**：1440×900 桌面端
3. **分级阈值**：
   - 🟢 match: < match_below
   - 🟡 minor: match_below - minor_below
   - 🟠 moderate: minor_below - moderate_below
   - 🔴 major: ≥ moderate_below
   - 当前模式阈值见报告顶部（pixel 5/20/50，structural 3/8/15）
4. **限制**：
   - 原型为静态 HTML，与 Element Plus 实现的字体/颜色/抗锯齿天然差异
   - 时间戳、用户名等动态内容会推高 diff_ratio
   - 空数据态 vs 有数据态差异未区分（v1.58 可扩展双快照）
5. **建议行动**：
   - 🔴 major 优先：18 个，需人工逐个复核 diff 图（`tools/visual_diffs/`）
   - 🟠 moderate 抽样：62 个，按业务关键度选 10-20 个复核
   - 🟡 minor 暂缓：4 个，多为字体细节
   - 缺失映射的 4 个路由建议补原型或确认是否需原型
