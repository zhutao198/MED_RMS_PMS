# Med-RMS 项目上下文指南（新会话快速恢复）

> **用途**: 新会话开场引用此文件，5 分钟内恢复到完整上下文
> **更新**: 每次 R 节点完成时更新此文件
> **最后更新**: 2026-07-24（R220 v1.76 — Feature Flag 临时屏蔽电子签名）

---

## 🚀 30 秒恢复（最小启动集）

```bash
cd "D:/zhutao/MED_RMS_PMS"
git log --oneline -5                                    # 最新 commit
git tag -l "R1*" | tail -10                            # 最新 tag
netstat -ano | grep ":808.*LISTENING" | head -3        # 后端实例
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```

## 📊 当前状态（2026-07-24 R220 后）

| 维度 | 值 |
|------|-----|
| **HEAD commit** | `2152ea9` (R227 — P1 第二批) |
| **最新 R 节点** | R227（DATA-049-A @TableLogic + DATA-010~013 编号生成改 MAX + CONTRACT-009~011 待处理）|
| **PRD 版本** | v2.2（2026-07-11，新增 FR-2.11~FR-2.16） |
| **后端端口** | 8080（运行中） |
| **GitHub tag 数** | 70+ R tag |
| **数据库** | UTF-8 + 21 CFR Part 11 哈希链 + 审计日志按月分区 + 36 表 DELETE 阻止触发器 + 7 表 record_hash |
| **RBAC** | 9 角色 × 64 权限 × 260+ 关联 |
| **测试** | 11 个 e2e 87/97 状态（R217/R218/R219 写端点因 R220 屏蔽预期 FAIL） |
| **⚠️ R220 Feature Flag** | `compliance.modules.signature: false`（电子签名暂时禁用，恢复方式见 R220-FEATURE-FLAG.md） |
| **合规审计覆盖率** | ~85%（R197 完成 7 HIGH + 3 MEDIUM） |
| **R190 新增** | ProjectDetail 第 7 tab "需求任务追溯" |
| **R191 新增** | 追溯管理页"获取追溯数据失败"双根因修复 |
| **R192-R194** | 全局项目选择同步（store + ProjectSelector + 42 页面迁移 + 跨模块互通）|
| **R195** | 视觉验收通过（61 页 0 溢出 0 错误）|
| **R196** | 双签 e2e 修复（DDL r162 列宽不足 → VARCHAR(512)）|
| **R197** | 21 CFR Part 11 合规 7 HIGH 修复（G2/G7/G15/G16/G17/§1.2/G1）|
| **R198 v1.61** | MEDIUM 合规项 + 质量评分缓存 + TOCTOU 并发修复 + 2 个 e2e 脚本 |

## 📁 关键文件速查

| 文件 | 内容 | 行数 |
|------|------|------|
| `开发日志.md` | 56 个 R 节点完整记录（R190-R198） | 20000+ |
| `ddl/r164_audit_log_partition.sql` | 审计日志按月 RANGE 分区（已执行） | - |
| `ddl/r162_signature_field.sql` | R196 重新执行（扩展列宽至 VARCHAR(512)）| - |
| `ddl/r197_compliance_triggers.sql` | G16/G17 防篡改触发器 + 记录校验和 | - |
| `med-rms-admin/security/PermissionMatrix.java` | G7 — 新增 16 条 RBAC 规则 | - |
| `med-rms-common/util/TimedCache.java` | R198 v1.61 通用定时缓存工具类 | - |
| `med-rms-compliance/service/BaselineService.java` | R198 v1.61 lockBaseline 原子 UPDATE 修复 | - |
| `tools/test_runner/test_hashchain_injection.py` | R198 v1.61 哈希链断链注入 e2e | - |
| `tools/test_runner/test_concurrent_sign.py` | R198 v1.61 并发签名竞态 e2e | - |
| `tools/test_runner/test_cross_module_link_e.py` | R198 v1.61 跨模块链路 E e2e | - |
| `med-rms-admin/controller/OaSyncController.java` | G15 — requireAdmin + @AuditLog | - |
| `med-rms-esignature/service/ElectronicSignatureService.java` | G2 — JWT 身份重校验 | - |
| `med-rms-compliance/controller/ComplianceController.java` | §1.2 — 11 个 @AuditLog | - |
| `med-rms-compliance/controller/BaselineController.java` | §1.2 — 4 个 @AuditLog | - |
| `med-rms-project/controller/ProjectController.java` | §1.2 — 9 个 @AuditLog | - |
| `med-rms-risk/controller/RiskController.java` | §1.2 — 4 个 @AuditLog | - |
| `med-rms-risk/controller/RiskRegisterController.java` | §1.2 — 4 个 @AuditLog | - |
| `med-rms-admin/controller/SystemController.java` | §1.2 — 13 个 @AuditLog | - |
| `med-rms-admin/service/LoginAttemptService.java` | R198 — Redis 登录失败计数器 | 新文件 |
| `frontend/src/composables/useInactivityTracker.ts` | R198 — 空闲1小时自动登出 | 新文件 |
| `测试报告/视觉验收报告.md` | 61 页 Puppeteer 自动扫描结果 | - |
| `tools/visual_audit.js` | 视觉验收 Puppeteer 脚本 | - |
| `Code/frontend/src/components/ProjectSelector.vue` | R192 新建全局项目选择器组件 | - |
| `Code/frontend/src/composables/useSyncProjectId.ts` | R192 新建跨页面同步 composable | - |
| `Code/frontend/src/stores/project.ts` | R192 新增 currentProjectId ref + setCurrentProjectId | - |
| `Code/frontend/e2e/auth-helper.ts` | R165 共享 auth helper（loginAsAdmin + setupAuthForPage） | 25 |
| `Code/frontend/src/views/requirement/decompose/DecomposeWorkbench.vue` | R166 Bug 1 — 移除无效 type="dashed" | - |
| `Code/frontend/src/views/requirement/TestCaseList.vue` | R166 Bug 3 — 加"全部项目"选项 | - |
| `Code/frontend/src/views/risk/RiskRegister.vue` | R166 Bug 3 — 加"全部状态/类别/项目" | - |
| `Code/frontend/src/views/risk/RisksMatrix.vue` | R166 Bug 3 — 加"全部项目"选项 | - |
| `Code/frontend/src/views/project/GanttView.vue` | R166 Bug 3 + R175 甘特图拖拽调整日期 | - |
| `Code/frontend/src/views/project/IpdGate.vue` | R166 Bug 3 — 加"全部项目"选项 | - |
| `Code/frontend/src/views/project/ResourceManagement.vue` | R166 Bug 3 + R175 资源热力图 | - |
| `Code/frontend/src/views/project/ProjectsList.vue` | R175 项目克隆按钮 | - |
| `Code/frontend/src/views/project/TaskBoard.vue` | R175 新建任务看板 | 新文件 |
| `Code/frontend/src/views/project/ProjectActivity.vue` | R175 活动流时间线 | 新文件 |
| `Code/frontend/src/views/project/ProjectAuditLog.vue` | R175 项目级审计追踪 | 新文件 |
| `Code/frontend/src/views/project/ProjectDetail.vue` | R175 健康度评分卡 + Excel导出 + 活动流Tab | - |
| `Code/frontend/src/views/project/TemplateManagement.vue` | R175 scopeType 过滤 | - |
| `Code/frontend/src/views/project/WorklogView.vue` | R175 工时超预算提示 | - |
| `Code/frontend/src/views/dashboard/Dashboard.vue` | R175 健康度评分 Tab | - |
| `Code/frontend/src/App.vue` | R175 3 个新菜单 | - |
| `Code/frontend/src/router/index.ts` | R175 3 个新路由 | - |
| `Code/backend/med-rms-project/.../ProjectController.java` | R175 clone/health-score/export | - |
| `Code/backend/med-rms-project/.../ProjectActivityController.java` | R175 活动流 API | - |
| `Code/backend/med-rms-project/.../ProjectService.java` | R175 cloneProject/calculateHealthScore | - |
| `Code/backend/med-rms-project/.../GanttService.java` | R175 suggestAdjustments | - |
| `Code/backend/med-rms-project/.../ProjectActivityService.java` | R175 活动流 service | 新文件 |
| `Code/frontend/src/views/traceability/TraceCoverage.vue` | R166 Bug 3 — 加"全部项目"选项 | - |
| `Code/frontend/src/views/traceability/TraceGraph.vue` | R166 Bug 3 — 加"全部项目"选项 | - |
| `测试报告/00-汇总/README.md` | 全模块测试报告 + P0/P1 缺陷 | v2.0 |
| `测试报告/00-汇总/R162-PRDvs实现偏差分析报告.md` | PRD v2.2 vs 实现偏差 | ~46 FR |
| `R175-开发计划.md` | R175 项目管理增强开发计划 | 55 |
| `测试报告/00-汇总/R162-偏差修复计划.md` | 22 项 × 12 轮修复计划 | v3.0 |
| `Detailed/04-权限设计/RBAC矩阵.md` | 9 角色 × 64 权限完整矩阵 | 263 |
| `架构-实现偏差与文档同步/架构-实现偏差清单.md` | 设计 vs 实现偏差 | - |
| `架构-实现偏差与文档同步/DDL变更日志.md` | 48 个 DDL 文件登记（+4 R162）| - |
| `SESSION_SUMMARY.md` | 本次会话关键决策和教训 | - |
| `tools/restart_8080.ps1` | 8080 重启脚本（UAC 触发） | 156 |
| `tools/test_runner/` | 10 个 e2e 脚本（+2 R162 新脚本）| - |
| `.github/workflows/e2e-tests.yml` | R117 CI workflow | 126 |
| `.github/workflows/cd-deploy.yml` | R129 CD workflow | 154 |

## 🏷️ R 节点全景（60 个 commit）

```
... → R165 → R166 (3 Bug) → R167 (仪表盘 API) → R168 (合规指标) → R169 (e2e 扩展, 135/135 ✅)
→ R170-R171 (上下文更新) → R172 (v-permission 通配符) → R173 (菜单角色大小写) → R174 (PRD v2.1→v2.2, 6 新 FR)
→ R175 (项目管理增强全量前后端) → R176 (左侧导航 el-menu 重构)
→ R177 (修复嵌套导航默认折叠 + isGroupEntry 去重) → R178 (响应式 roles 修复)
→ R179 (移除 Login.vue 多余 setUserInfo) → R180 (登录页隐藏侧栏/顶栏 + 移除 SSO)
→ R181 (ancestor 闭包表重建) → R182 (需求池导入) → R183 (需求池拒绝/删除) → R184 (需求池 UI) → R185 (ID 日期时间编号)
→ R186 (项目名称统一 + 内联工作台) → R187 (项目名称统一 30 页) → R188 (PRD 同步) → R189 (URS→任务违规 + 拆解列表 + TaskBoard 来源)
→ R190 (ProjectDetail 需求任务追溯 tab) → R191 (追溯页双根因修复) → R192 (全局项目选择同步 + ProjectSelector)
→ R193 (TaskBoard 同步 + N+1→批量) → R194 (跨模块互通) → R195 (视觉验收 v-loading) → R196 (双签 e2e DDL 列宽修复)
→ **R197** (21 CFR Part 11 合规 7 HIGH 修复)
→ **R198** (MEDIUM 合规 — 账号锁定/密码策略/Inactivity登出)
→ **R198b** (v1.61 性能 + 业务增强 — 质量评分缓存/TOCTOU修复/3个e2e + Dashboard持久化 + 需求池P0 + 变更管理P0+P1 + ESignPopup OTP移除)
→ **R222.4** (v1.78d 上一轮 CODE_REVIEW 修复收尾 — H1 SQL注入/H2 逻辑删除/H3 fail-closed/M2 DB口令/M3 异常泄露/M4 基线契约 + 5 测试清理)
→ **R223** (v1.79 P0 安全修复批次 — SEC-001 JWT密钥轮换/DATA-001 ChangeRequestMapper逻辑删除/DATA-004 BaselineMapper逻辑删除)
→ **R224** (v1.80 P0 架构修复 — SEC-002 OaSync 加鉴权/SEC-003 PermissionEnforce 默认拒绝 + 补齐 44 个未登记端点)
→ **R225** (v1.81 P0 前端契约断链修复 — CONTRACT-001~008 8 个前后端契约断链)
→ **R226** (v1.82 P1 批次修复 — SEC-005 密码/SEC-007 uploader/DATA-019 unlockBaseline 原子迁移/DATA-021 状态迁移原子/INTEG-003 reSign 守卫/INTEG-004~006 配置)
→ **R227 ⬅️ [HEAD]** (v1.83 P1 第二批 — DATA-049-A @TableLogic/DATA-010~013 编号生成改 MAX/LockLog 修正)
```

**关键节点**：
- **R111-R117**: 偏差清单 + 状态机 18 态 + 前后端测试 + P0/P1 修复
- **R118-R120**: tree 性能 11.8 倍 + viewer 权限 + 4 项 P2 修复
- **R122**: GitHub push + 23 tag
- **R123**: 状态机 e2e 9/9 = 100%
- **R125**: PD 角色 0→21 修复
- **R126**: 端点补强（trace-count/test-case-count）
- **R127**: 8080 部署脚本
- **R133-R142**: CI/CD 修复（9 迭代）
- **R143**: 性能 14.2 倍 + 里程碑修复
- **R148**: OTP bug 修复
- **R149**: 上下文压缩（CONTEXT.md + SESSION_SUMMARY.md + 开发日志 90% 压缩）
- **R150**: 跨模块集成测试（链路 A/B/C/D 全 30 用例通过）
- **R151**: @AuditLog 注解 AOP 持久化修复（web/pom.xml 加 spring-boot-starter-aop）+ 链路 C+D 17/17=100%
- **R152**: CI 接入 R150+ 集成测试（Phase 2 test_*.py 循环 6 脚本）
- **R153**: 双签完整流程 e2e（admin + pm 锁定 baseline，21 CFR Part 11 §11.200 验证通过）
- **R156**: 8 个 service 方法加 @AuditLog（EsignService.reSign + invalidateSignature + RequirementService 6 状态机方法）
- **R158-R161**: 修复 opencode 测试发现 7 个 bug（F1 哈希链+F2 pd+F4 reviewer RBAC+F6 表+F7 性能+F8 审计语义+F9 种子）
- **R162**: PRD vs 实现偏差分析 + 12 轮修复（前端 RBAC/RSA 签名/追溯断裂/DB 约束/eRPS XML/AI 端点/合规指标）
- **R163**: Playwright 认证从 fake JWT 改为真实登录 API（POST /api/auth/login JWT）
- **R164**: 4 个前端组件渲染 Bug 修复（ChangeList Array.isArray / Iec62304 checklistLoading 互斥锁 / TestCaseList AbortController / RequirementTaskConvert 并行 chunk）
- **R165**: 修复 15 个 Pre-existing 测试失败（auth 注入 + 软断言 + 新建 auth-helper.ts 共享 helper），全量 135/135 ✅ 首次全绿
- **R166**: 修复 W30-5 扫描 3 个 Bug（8 页面加"全部项目"选项 / DecomposeWorkbench 无效 dashed 属性 / 硬编码 /requirements/1612 改为 750），W30-5 ISSUES: []，W30-2 withoutAllOption: []，135/135 ✅
- **R167**: 修复仪表盘 API 路径（MilestoneProgress/burndown/SoupStats 路径不匹配）+ 审计验证页 URL 修复
- **R168**: 补齐合规指标（仪表盘合规 tab 增加签名覆盖率/审计哈希链/SOUP 评估率/变更分析率）
- **R169**: 扩展 e2e 仪表盘测试（合规指标验证）+ 完成 PRD 偏差修复计划剩余分析项
- **R172**: 修复 v-permission 通配符 bug（ADMIN 权限数组 ["*"] 未匹配任何具体 perm code，所有按钮不可见）
- **R173**: 修复菜单侧边栏角色大小写不匹配（JWT 返回 "ADMIN" vs 菜单配置 "admin"），ADMIN 全部菜单不可见
- **R174**: PRD v2.1→v2.2，新增 6 项项目管理增强特性（FR-2.11~FR-2.16），更新交付计划 20→21 月
- **R175**: 项目管理增强全量实施（后端 A1-A10 + 前端 B1-B11），含甘特图拖拽/项目克隆/任务看板/活动流/健康度评分/资源热力图/审计追踪/Excel导出/BOM CRUD/工时超预算/合规模板扩展
- **R176**: 左侧导航 flat 列表 → 嵌套 el-menu 层次结构，支持展开/折叠/自动高亮/角色递归过滤
- **R177**: 修复 R176 嵌套导航默认折叠 + isGroupEntry 去重
- **R178**: 修复侧栏登录后不刷新（getRoles 非响应式 → userStore.userInfo?.roles）
- **R179**: 修复 Login.vue 多余 setUserInfo 覆盖 userInfo.roles
- **R180**: 登录页隐藏侧栏和顶栏 + 移除 SSO 按钮 + 修复概览 tab locator（role=tab）
- **R181**: 数据库清理脚本增加 ancestor 闭包表重建步骤（修复追溯页面获取数据失败）
- **R191**: 追溯管理页"获取追溯数据失败"双根因修复（API 列名 + DDL 补缺列）
- **R192**: 全局项目选择同步：store + ProjectSelector 组件 + useSyncProjectId + 42 页面迁移
- **R193**: TaskBoard 同步修复（useSyncProjectId 替代 ref(null)）+ N+1 性能优化
- **R194**: 项目列表/详情页显式调用 setCurrentProjectId 跨模块互通
- **R195**: 视觉验收 — TraceMatrix/TaskBoard 增加 v-loading
- **R196**: 双签 e2e 修复 — 缺失 DDL(r162) 导致列宽不足（signature_hash/signature_value/entity_hash VARCHAR(512)）
- **R197**: 21 CFR Part 11 合规差距修复（7 HIGH: G2 JWT校验 / G7 RBAC补全 / G15 OaSync权限 / G16 DELETE阻止触发器 / G17 record_hash / §1.2 @AuditLog / G1 Lombok配置）
- **R198**: MEDIUM 合规（M1 账号锁定10次30min / M2 密码最小6位 / M3 Inactivity 1h自动登出）
- **R198b v1.61**: 性能优化（TimedCache 质量评分 5min TTL）+ TOCTOU 修复（BaselineService 原子 UPDATE）+ 3 个 e2e 脚本（hashchain/concurrent/link-e）+ Dashboard 全部项目持久化 5 commit + 需求池 P0 + 变更管理 P0+P1 + ESignPopup OTP 完全移除 5 commit
- **R207-R209**: PRD v2.2 剩余 FR 全部完成（DHF PDF / Excel 导入 / eRPS 中文 PDF）
- **R211-R213**: IPD 自动校验 / 多视角 UI / 法规推送（3 e2e 全绿）
- **R214-R216**: 工程基础（Flyway V1000-V1002 / Dashboard 持久化 / 前端集成）
- **R217-R219**: 签名密码验证 / 过期过滤 / 智能过期通知（V1003 + 分级 T-5/T-1/T+0 通知）
- **R220 v1.76**: Feature Flag 屏蔽电子签名（用户决策，compliance.modules.signature=false，详见 R220-FEATURE-FLAG.md）
- **R222.4 v1.78d**: 上一轮 CODE_REVIEW 修复收尾（H1 SQL注入修复/H2 RequirementRelationMapper 逻辑删除/H3 DhfEvidenceService fail-closed/M2 DB口令外部化/M3 异常信息泄露收敛/M4 Baselines.vue 契约修复 + 5 个测试清理 UserServiceTest/StatisticsServiceTest/BaselineServiceTest/DhfEvidenceServiceTest/RequirementAuditIntegrationTest）
- **R223 v1.79**: P0 安全修复批次
  - **R223.1 SEC-001**: JwtService 删除源码默认值 + 强制环境变量注入 + @PostConstruct 启动期校验（非 dev/test profile 下使用默认密钥直接启动失败）
  - **R223.2 DATA-001**: ChangeRequestMapper 3 个 @Select 追加 `AND is_deleted = false`
  - **R223.3 DATA-004**: BaselineMapper 2 个 @Select 追加 `AND is_deleted = false`
  - 评审依据：`CODE_REVIEW_REPORT_FULL_2026-07-25.md` 第 6/7 节
  - 测试：med-rms-admin 113/113、med-rms-change 45/45、med-rms-compliance 168/168 全部通过
- **R224 v1.80 ⬅️ [HEAD]**: P0 架构修复批次
  - **R224.1 SEC-002**: 删除 web 模块下冗余 OaSyncController（无鉴权覆盖 admin 模块版本），让 admin 模块 requireAdmin + @AuditLog 版本生效（R197 G15 实际未生效修复）
  - **R224.1**: PermissionMatrix 加 /oa-sync 前缀规则
  - **R224.2 SEC-003**: PermissionEnforceFilter 改默认拒绝（requiredPerm==null → 403 + 日志告警）；白名单路径支持 context-path 去除
  - **R224.2**: 渐进修复 — 补齐 44 个真实未登记端点（占位符标准化后扫描：DepartmentController×6 / UserPreferenceController×4 / SystemController×3 / StatisticsController×6 / TraceLinkController×9 / RegulationImpactController×3 / DashboardController×2 / ChangeController×2 / GanttController×1 / ProjectActivityController×2 / IpdGateController×1 / RequirementTaskController×1 / AIController×2 / RequirementPoolController×1 / FeatureFlagController×1）
  - **R224.2**: 新增 perm 码 `sys:dept:list` / `sys:ai:list`（需后续 R 节点在 RBAC seed 数据中分配角色）
  - **R224.2**: PermissionEnforceFilterTest 改默认拒绝场景 + 修白名单匹配 bug（/api/auth/login 路径）
  - 测试：med-rms-admin 113/113 全过；web 模块 52 个集成测试因 H2/Flyway 预存在问题失败（已在 R224 前 baseline 同样失败，非本次回归）

## 🎯 用户偏好（CLAUDE.md 已记录）

- 全程简体中文
- Rxx 节点规范（每次修改前建节点框架 + commit + tag）
- 报告不立即修复，记录后统一评估
- 模块化测试（test_runner/ 工具集）
- 决策前主动询问确认

## 🛠️ 服务信息

| 端口 | 进程 | 状态 |
|------|------|------|
| 8080 | PID 动态 | 后端主实例（用户环境，需 admin 权限重启）|
| 8081 | 16148 | 后端验证实例 |
| 5173 | - | 前端 Vite（用户浏览器访问） |
| 5432 | postgres | PostgreSQL 16 |
| 6379 | redis | Redis 7 |

## 🐛 已知未解决

| # | 问题 | 状态 |
|---|------|------|
| 后端 8080 重启需 admin | 用户操作 | R127 脚本 |
| 字符编码 SQL_ASCII | 已验证 DB 实际是 UTF-8 | R121 |
| API 路径不一致 | 文档化（保持现状）| R119 |
| 8080 实例有 4 个 java 进程 | 资源竞争 | 性能正常 |
| GitHub Actions 默认 secrets 缺失 | CD 仅 build 不部署 | 需配置 SSH |
| ~~@AuditLog 注解未持久化到 audit_log 表~~ | **R151 已修复** | web/pom.xml + spring-boot-starter-aop |
| ~~前端 RBAC 缺失~~ | **R162 R4-R5 已修复** | 路由守卫 + v-permission |
| ~~签名算法 SHA256withRSA~~ | **R162 R3 已修复** | SecurityUtils + ElectronicSignatureService |
| ~~追溯断裂非实时~~ | **R162 R2 已修复** | TraceabilityService @Scheduled |
| ~~Suspect 非自动标记~~ | **R162 R1 已修复** | ChangeService.submitChange() |
| ~~DB 触发器~~ | **R162 R1 已修复** | r162_triggers.sql |
| ~~全部项目过滤选项缺失（8 页面）~~ | **R166 已修复** | 8 Vue 组件 el-select |
| ~~el-button type="dashed" Vue warn~~ | **R166 已修复** | DecomposeWorkbench.vue |
| ~~硬编码 /requirements/1612 404~~ | **R166 已修复** | 改为 /requirements/750 |
| ~~URS 可转化为任务（PRD 违规）~~ | **R189 已修复** | convertRequirementToTasks() 加类型校验 |
| ~~需求拆解列表为空~~ | **R189 已修复** | DecomposeList.vue 删除硬编码 status |
| ~~TaskBoard 无需求来源标识~~ | **R189 已修复** | 卡片加编号 tag + 详情弹窗来源行 |
| ~~全局项目选择不同步~~ | **R192-R194 已修复** | store + ProjectSelector + useSyncProjectId |
| ~~TraceMatrix/TaskBoard 无 v-loading~~ | **R195 已修复** | 增加加载状态提示 |
| ~~双签 e2e E4.1 SY0000~~ | **R196 已修复** | DDL r162 列宽不足 → VARCHAR(512) |
| ~~OaSyncController 无权限控制~~ | **R197 已修复** | requireAdmin + @AuditLog |
| ~~PermissionMatrix 遗漏路由~~ | **R197 已修复** | 新增 16 条规则 |
| ~~sign() 无 JWT 身份校验~~ | **R197 已修复** | SecurityUtils.getCurrentUserId 校验 |
| ~~账号无锁定策略~~ | **R198 已修复** | LoginAttemptService Redis 10次30min |
| ~~Inactivity 超时登出~~ | **R198 已修复** | useInactivityTracker 1h 无操作登出 |
| ~~质量评分无缓存（每次重算）~~ | **R198b v1.61 已修复** | TimedCache 5min TTL + create/update 失效 |
| ~~BaselineService TOCTOU 并发漏洞~~ | **R198b v1.61 已修复** | 原子 UPDATE WHERE id=? AND status='DRAFT' |
| ~~Dashboard "全部项目" -1 污染其他页面~~ | **R198b 已修复（5 commit）** | onMounted 守卫 + HMR 类型保护 + syncToStore 显式传参 |
| ~~ESignPopup OTP 字段未默认关闭~~ | **R198b 已修复（5 commit）** | ESignPopup + SignatureDialog 双组件移除 OTP + ChangeRequest 文案清理 |
| ~~需求池 proposer/OA 来源缺失~~ | **R198b 已修复** | RequirementPoolService P0 8 项增强 |
| ~~变更管理执行证据/委派缺失~~ | **R198b 已修复** | ChangeRequestService P0 修复 + P1 增强 |

## 🔧 用户实际操作模式

- 用其他工具（IDEA/VSCode/Postman）直接修改文件后让我检查
- 浏览器手动验证页面功能
- 关注实际运行问题（不只看代码）
- 期望完整工作流：诊断 → 修复 → 验证 → commit → push

## 📞 紧急恢复命令

```bash
# 8080 跑新代码
cd D:\zhutao\MED_RMS_PMS
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','D:\zhutao\MED_RMS_PMS\tools\restart_8080.ps1'"

# 手动 build + 启动
cd D:\zhutao\MED_RMS_PMS\Code\backend
mvn -B -DskipTests -Dspring-boot.repackage.skip=true install
cd med-rms-web
mvn -B -q -DskipTests spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"

# 看 log
tail -f C:/temp/medrms-8080-r133-*.log | tail -100

# 跑全量 Playwright e2e（前端 5173 + 后端 8080 需运行中）
cd D:\zhutao\MED_RMS_PMS\Code\frontend
npx playwright test --reporter=list
```

## 📚 关联文档

- [开发日志.md](开发日志.md) - 68 个 R 节点详细记录（R001-R199）
- [SESSION_SUMMARY.md](SESSION_SUMMARY.md) - 本次会话关键决策和教训
- [.claude/projects/.../memory/MEMORY.md](.claude/projects/.../memory/MEMORY.md) - 项目级持久化记忆
- [测试报告/00-汇总/README.md](测试报告/00-汇总/README.md) - 全模块测试报告
- [测试报告/00-汇总/R162-PRDvs实现偏差分析报告.md](测试报告/00-汇总/R162-PRDvs实现偏差分析报告.md) - PRD vs 实现偏差
- [测试报告/00-汇总/R162-偏差修复计划.md](测试报告/00-汇总/R162-偏差修复计划.md) - 22 项 × 12 轮修复计划
- [Detailed/04-权限设计/RBAC矩阵.md](Detailed/04-权限设计/RBAC矩阵.md) - 完整 RBAC 矩阵
---

## 🚀 R222 v1.78（2026-07-24）— 任务负责人功能修复

| 维度 | 值 |
|------|-----|
| **HEAD commit** | `a9e1a4e` (R222) |
| **新增 tag** | R222 v1.78 |
| **改动文件** | 4 个（前端 2 + 后端 2）+ 1 个 e2e 脚本 |
| **diff stat** | 6 files, +312/-3 |
| **关联回滚** | `git checkout R222` |

### R222 改动一览
| 文件 | 改动 | 行为 |
|------|------|------|
| `frontend/RequirementTaskConvert.vue` | +26 行 | 草稿表新增"负责人"列 el-select（filterable+clearable）|
| `frontend/TaskBoard.vue` | +55 行 | 详情弹窗"负责人"行新增"修改"按钮 + el-select |
| `backend/GanttController.java` | +17 行 | C1 @AuditLog 留痕 + C2 支持清空（-1L → null）|
| `backend/PermissionMatrix.java` | +2 行 | C3 加 PUT /gantt/tasks/{id} → proj:update RBAC |
| `test_runner/test_r222_task_assignee_e2e.py` | 新增 | 4 场景 e2e：转化/详情改/清空/RBAC 拒 VIEWER |

### R222 用户后续操作
1. **重启 8080**（加载新后端代码）：
   ```bash
   powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','D:\zhutao\MED_RMS_PMS\tools\restart_8080.ps1'"
   ```
2. **跑 R222 e2e**：
   ```bash
   cd D:/zhutao/MED_RMS_PMS/Code/backend/tools/test_runner
   python test_r222_task_assignee_e2e.py
   ```
3. **浏览器手动验收**：
   - 需求管理 → 需求转化（草稿表出现"负责人"列）
   - 项目管理 → 任务看板 → 任务详情（"修改"按钮可改/清空）

### R222 e2e 实跑结果（2026-07-24）
| 验证项 | 期望 | 实际 |
|--------|------|------|
| 场景 A: 转化时设负责人 | admin(1) | ✅ PASS |
| 场景 B: PUT 改负责人 | re(7) | ✅ PASS |
| 场景 B+: 审计日志写入 | entityType=TASK op=更新任务 | ✅ PASS |
| 场景 C: -1L 清空负责人 | DB 写 null | ✅ PASS |
| 场景 D: VIEWER RBAC | 403 | ✅ PASS |
| **总计** | 5/5 | **8/8 PASS（含审计验证 + 字段子项）**|

R222 修复 100% 通过验证（含 R222.1 e2e 路径修正）。R222 tag 已指向完整状态 commit。
