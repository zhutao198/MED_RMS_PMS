# Med-RMS RBAC 权限矩阵 — R124

> **生成时间**: 2026-06-30 18:16
> **数据源**: PostgreSQL sys_schema.t_role / t_permission / t_role_permission
> **节点**: R124
> **统计**: 9 角色 × 63 权限码 × 221 关联

## 一、角色清单 (9 个)

| ID | 角色码 | 角色名称 | 权限数 | 角色定位 |
|----|--------|---------|--------|---------|
| 1 | `ADMIN` | 系统管理员 | 63 | 系统管理员：拥有全部权限（通配符 `*`） |
| 2 | `QA_MGR` | QA经理 | 59 | QA 经理：质量/基线/测试用例管理 |
| 3 | `PM` | 项目经理 | 50 | 项目经理：项目/任务/IPD 门控管理 |
| 4 | `RE` | 需求工程师 | 24 | 需求工程师：需求全生命周期 |
| 5 | `REVIEWER` | 评审专家 | 12 | 评审专家：仅评审权限 |
| 6 | `RISK_MGR` | 风险管理 | 28 | 风险管理：风险/FMEA 管理 |
| 7 | `COMPLIANCE` | 合规人员 | 29 | 合规人员：审计/IEC 62304/SOUP 管理 |
| 8 | `VIEWER` | 只读用户 | 19 | 只读用户：透明合规数据 |
| 9 | `PD` | 产品经理 | 21 | 产品经理：产品需求/规划 |

## 二、权限码清单 (63 个)

> 格式：`<module>:<action>` — 精确匹配 / `<module>:*` / `<module>:<resource>:*` 通配

| 模块 | 权限码 | 名称 | 类型 |
|------|--------|------|------|
| audit | `audit:verify` | 校验审计日志 | BUTTON |
| audit | `audit:read` | 查看审计日志 | MENU |
| baseline | `baseline:compare` | 基线对比 | BUTTON |
| baseline | `baseline:create` | 创建基线 | BUTTON |
| baseline | `baseline:lock` | 锁定基线 | BUTTON |
| baseline | `baseline:unlock` | 解锁基线 | BUTTON |
| baseline | `baseline:list` | 基线列表 | MENU |
| chg | `chg:analyze` | 变更影响分析 | BUTTON |
| chg | `chg:approve` | 变更审批 | BUTTON |
| chg | `chg:create` | 创建变更 | BUTTON |
| chg | `chg:execute` | 变更执行 | BUTTON |
| chg | `chg:list` | 变更列表 | MENU |
| compliance | `compliance:iec62304` | IEC62304检查 | MENU |
| esign | `esign:intent` | 签名意图 | BUTTON |
| esign | `esign:otp` | OTP验证 | BUTTON |
| esign | `esign:pwd` | 签名密码验证 | BUTTON |
| esign | `esign:sign` | 执行签名 | BUTTON |
| esign | `esign:verify` | 校验签名 | BUTTON |
| esign | `esign:read` | 查看签名 | MENU |
| pr | `pr:correction` | 问题纠正 | BUTTON |
| pr | `pr:create` | 创建问题报告 | BUTTON |
| pr | `pr:status` | 变更问题状态 | BUTTON |
| pr | `pr:list` | 问题报告列表 | MENU |
| proj | `proj:create` | 创建项目 | BUTTON |
| proj | `proj:gate:review` | DCP门控评审 | BUTTON |
| proj | `proj:member` | 项目成员 | BUTTON |
| proj | `proj:update` | 编辑项目 | BUTTON |
| proj | `proj:list` | 项目列表 | MENU |
| regulation | `regulation:read` | 查看法规 | MENU |
| report | `report:export` | 导出报表 | BUTTON |
| report | `report:dashboard` | 仪表盘 | MENU |
| report | `report:stats` | 统计报表 | MENU |
| req | `req:create` | 创建需求 | BUTTON |
| req | `req:delete` | 删除需求 | BUTTON |
| req | `req:import` | 导入需求 | BUTTON |
| req | `req:review` | 评审需求 | BUTTON |
| req | `req:status` | 变更需求状态 | BUTTON |
| req | `req:submit` | 提交需求 | BUTTON |
| req | `req:update` | 编辑需求 | BUTTON |
| req | `req:list` | 需求列表 | MENU |
| risk | `risk:analyze` | 风险分析 | BUTTON |
| risk | `risk:control` | 风险控制 | BUTTON |
| risk | `risk:create` | 创建风险 | BUTTON |
| risk | `risk:status` | 变更风险状态 | BUTTON |
| risk | `risk:update` | 编辑风险 | BUTTON |
| risk | `risk:list` | 风险列表 | MENU |
| safety | `safety:create` | 创建安全分类 | BUTTON |
| safety | `safety:read` | 查看安全分类 | MENU |
| soup | `soup:create` | 新增SOUP | BUTTON |
| soup | `soup:review` | 评审SOUP | BUTTON |
| soup | `soup:update` | 更新SOUP | BUTTON |
| soup | `soup:list` | SOUP列表 | MENU |
| sys | `sys:config:list` | 系统配置 | MENU |
| sys | `sys:dict:list` | 字典管理 | MENU |
| sys | `sys:org:list` | 组织架构 | MENU |
| sys | `sys:role:list` | 角色管理 | MENU |
| sys | `sys:user:list` | 用户管理 | MENU |
| trace | `trace:create` | 创建追溯 | BUTTON |
| trace | `trace:delete` | 删除追溯 | BUTTON |
| trace | `trace:coverage` | 追溯覆盖率 | MENU |
| trace | `trace:gaps` | 追溯缺口 | MENU |
| trace | `trace:list` | 追溯列表 | MENU |
| trace | `trace:matrix` | 追溯矩阵 | MENU |

## 三、角色 × 权限矩阵

> ✅ = 角色拥有该权限 | ❌ = 角色没有该权限 | 🟡 = ADMIN 通配（实际拥有全部）

| 权限码 | QA_MGR | PM | RE | REVIEWER | RISK_MGR | COMPLIANCE | VIEWER | PD |
|--------|------|------|------|------|------|------|------|------|
| `audit:verify` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| `baseline:compare` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| `baseline:create` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `baseline:lock` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `baseline:unlock` | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `chg:analyze` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `chg:approve` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `chg:create` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `chg:execute` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `esign:intent` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `esign:otp` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `esign:pwd` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `esign:sign` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `esign:verify` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `pr:correction` | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| `pr:create` | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| `pr:status` | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| `proj:create` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `proj:gate:review` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `proj:member` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `proj:update` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `report:export` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| `req:create` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `req:delete` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `req:import` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `req:review` | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ✅ |
| `req:status` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `req:submit` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `req:update` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `risk:analyze` | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `risk:control` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `risk:create` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `risk:status` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `risk:update` | ✅ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `safety:create` | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| `soup:create` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| `soup:review` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| `soup:update` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| `trace:create` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `trace:delete` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `audit:read` | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| `baseline:list` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| `chg:list` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| `compliance:iec62304` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| `esign:read` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `pr:list` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| `proj:list` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| `regulation:read` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ✅ |
| `report:dashboard` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| `report:stats` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ |
| `req:list` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `risk:list` | ✅ | ✅ | ✅ | ❌ | ✅ | ❌ | ✅ | ✅ |
| `safety:read` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| `soup:list` | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ |
| `sys:config:list` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `sys:dict:list` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `sys:org:list` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| `sys:role:list` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `sys:user:list` | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `trace:coverage` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `trace:gaps` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `trace:list` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| `trace:matrix` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |

### 3.1 权限统计

| 角色 | 权限数 | 占总权限比 |
|------|--------|-----------|
| `QA_MGR` | 59 | 93% |
| `PM` | 50 | 79% |
| `RE` | 24 | 38% |
| `REVIEWER` | 12 | 19% |
| `RISK_MGR` | 28 | 44% |
| `COMPLIANCE` | 29 | 46% |
| `VIEWER` | 19 | 30% |
| `PD` | 21 | 33% |
| `ADMIN` (通配) | 63 | 100% |

## 四、PermissionMatrix 端点 → 权限码 映射 (摘要)

> 完整映射见 `Code/backend/med-rms-admin/.../security/PermissionMatrix.java`
> 端点命中规则：先精确路径（Ant 风格 `{id}` 占位），后前缀匹配

### 4.1 主要端点对应

| HTTP | 端点 | 所需 perm | 备注 |
|------|------|-----------|------|
| GET  | `/system/users` | `sys:user:list` | 用户管理列表 |
| POST | `/system/users` | `sys:user:list` | 用户创建（按 design 沿用） |
| GET  | `/system/roles` | `sys:role:list` | 角色管理 |
| GET  | `/system/dicts` | `sys:dict:list` | 字典管理 |
| GET  | `/system/configs` | `sys:config:list` | 系统配置 |
| GET  | `/requirements` | `req:list` | 需求列表 |
| POST | `/requirements` | `req:create` | 需求创建 |
| POST | `/requirements/{id}/review` | `req:review` | 提交评审 |
| POST | `/requirements/{id}/approve` | `req:review` | 评审通过 |
| POST | `/requirements/{id}/decompose` | `req:create` | 拆解 |
| POST | `/requirements/{id}/verify` | `req:status` | 验证（InTest→Verified） |
| POST | `/requirements/{id}/withdraw` | `req:status` | 撤回 |
| POST | `/requirements/{id}/mark-suspect` | `req:status` | 标记 Suspect |
| GET  | `/requirements/kanban` | `req:list` | 看板 |
| GET  | `/requirements/stats` | `report:dashboard` | R115 统计聚合 |
| GET  | `/requirements/tree` | `req:list` | 层级树 |
| GET  | `/changes/list` | `chg:list` | 变更列表（R120 已加 total） |
| POST | `/changes/{id}/approve` | `chg:approve` | 变更审批 |
| POST | `/changes/{id}/execute` | `chg:execute` | 变更执行 |
| POST | `/changes/{id}/verify` | `chg:execute` | 变更验证 |
| GET  | `/traceability/matrix` | `trace:matrix` | 追溯矩阵 |
| GET  | `/traceability/coverage` | `trace:coverage` | 追溯覆盖率 |
| GET  | `/traceability/gaps` | `trace:gaps` | 追溯缺口 |
| GET  | `/compliance/audit-logs` | `audit:read` | 审计日志 |
| POST | `/compliance/audit-logs/verify` | `audit:verify` | 哈希链校验 |
| GET  | `/compliance/iec62304/checklist/{id}/stats` | `report:dashboard` | R118 viewer 权限修复 |
| POST | `/compliance/evidence` | `compliance:iec62304` | DHF 证据 |
| GET  | `/risk/register/list` | `risk:list` | 风险登记册 |
| POST | `/risk/assess` | `risk:analyze` | 风险评估 |
| GET  | `/projects` | `proj:list` | 项目列表 |
| POST | `/projects` | `proj:create` | 项目创建 |
| GET  | `/gantt/tasks/project/{id}` | `proj:list` | 甘特图任务 |
| GET  | `/project/ipd-gate/list/{id}` | `proj:gate:review` | IPD 阶段门 |
| POST | `/project/ipd-gate/{id}/pass` | `proj:gate:review` | IPD 门通过 |
| GET  | `/esignature/signatures` | `esign:read` | 签名记录 |
| POST | `/esignature/sign` | `esign:sign` | 执行签名 |
| POST | `/esignature/intents` | `esign:intent` | 签名意图 |
| GET  | `/auth/me` | `(登录即用)` | R120 新增：当前用户信息 |
| GET  | `/notifications/unread` | `report:dashboard` | 通知 |
| GET  | `/reports` | `report:stats` | 报表列表 |
| POST | `/reports/generate` | `report:export` | 报表生成 |

## 五、RBAC 设计原则

1. **ADMIN 通配**：ADMIN 角色 token 写入 `permissions: ['*']`，`PermissionEnforceFilter` 检测到 `*` 即放行
2. **模块通配**：`req:*` 匹配所有 `req:list`/`req:create`/...；`req:list:*` 匹配所有 `req:list:xxx`
3. **白名单放行**：`/auth/login` / `/auth/refresh` / `/v3/api-docs` 等不需鉴权
4. **默认 deny**：未在 PermissionMatrix 中的端点 → 默认需要登录但不强制 perm（白名单）
5. **JWT 流程**：登录 → 查 t_role_permission → 写入 `permissions` claim → 过滤器注入 authorities
6. **viewer 隔离**（R118 修复）：审计日志/IEC 62304 stats 等设计为 viewer 严格隔离（除 R118 修复的统计外）

## 六、关键观察

- **QA_MGR 最强业务角色**：59 个权限（93%），覆盖质量/基线/测试全流程
- **PM 第二**：50 个权限（79%），项目/任务/IPD 门控
- **REVIEWER 最小**：仅评审权限（req:review）
- **VIEWER 仅只读**：含 report:dashboard（透明合规数据，R118 修复后）+ 必要列表权限
- **COMPLIANCE 29 个权限**：聚焦合规/审计/SOUP/法规
- **RISK_MGR 约 18 个权限**：聚焦风险/FMEA 模块

> ⚠️ 注：ADMIN 角色由于 `*` 通配，实际拥有 100% 权限（与代码 `Set.of("*")` 一致）

## 七、数据维护说明

- 权限码定义：`sys_schema.t_permission` 表（63 条）
- 角色定义：`sys_schema.t_role` 表（9 条）
- 角色-权限关联：`sys_schema.t_role_permission` 表（221 条）
- **修改工具**：前端 `系统管理 > 角色权限` 页面（调用 `/api/system/roles/{id}/permissions` PUT）
- **重置**：DELETE t_role_permission WHERE role_id=? 后重新 INSERT
- **审计**：所有权限变更通过 `AuditAspect` 记录到 `t_audit_log`

## 八、变更记录

| 日期 | 变更 | 节点 |
|------|------|------|
| 2026-06-30 | 首次生成 RBAC 矩阵文档（9 角色 × 63 权限 × 221 关联）| R124 |
| 2026-06-30 | R118 修复：IEC 62304 stats viewer 权限调整 | R118 |
| 2026-06-30 | R115 实现：/requirements/stats 端点（report:dashboard 权限）| R115 |
| 2026-06-30 | R120 实现：/auth/me 端点 | R120 |
