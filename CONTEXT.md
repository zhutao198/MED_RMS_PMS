# Med-RMS 项目上下文指南（新会话快速恢复）

> **用途**: 新会话开场引用此文件，5 分钟内恢复到完整上下文
> **更新**: 每次 R 节点完成时更新此文件
> **最后更新**: 2026-07-29（R247 v2.01 — 数据导出加固收尾）

---

## 🚀 30 秒恢复（最小启动集）

```bash
cd "D:/zhutao/MED_RMS_PMS"
git log --oneline -5                                    # 最新 commit
git tag -l "R1*" | tail -10                            # 最新 tag
netstat -ano | grep ":808.*LISTENING" | head -3        # 后端实例
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```

## 📊 当前状态（2026-07-29 R255 后）

| 维度 | 值 |
|------|-----|
| **HEAD commit** | `2908679` (R256 — R255 决策代码落地) |
| **最新 R 节点** | **R256**（R255 决策代码落地：移除签名校验 + 执行入口修复） |
| **PRD 版本** | v2.2（2026-07-11，新增 FR-2.11~FR-2.16） |
| **后端端口** | 8080（运行中） |
| **GitHub tag 数** | 71+ R tag（含 R250-R254 修复 + R255 决策） |
| **数据库** | UTF-8 + 21 CFR Part 11 哈希链 + 审计日志按月分区 + 36 表 DELETE 阻止触发器 + 7 表 record_hash |
| **RBAC** | 9 角色 × 64 权限 × 260+ 关联 |
| **测试** | 11 个 e2e 87/97 状态（R217/R218/R219 写端点因 R255 永久屏蔽预期 FAIL） |
| **🛑 R255 永久决策** | **电子签名走线下流程，系统不考虑**（除非用户明确恢复，否则不得新增/恢复相关代码） |
| **合规审计覆盖率** | ~85%（R197 完成 7 HIGH + 3 MEDIUM；签名相关已转移至线下） |
| **R190 新增** | ProjectDetail 第 7 tab "需求任务追溯" |
| **R191 新增** | 追溯管理页"获取追溯数据失败"双根因修复 |
| **R192-R194** | 全局项目选择同步（store + ProjectSelector + 42 页面迁移 + 跨模块互通）|
| **R195** | 视觉验收通过（61 页 0 溢出 0 错误）|
| **R196** | 双签 e2e 修复（DDL r162 列宽不足 → VARCHAR(512)）|
| **R197** | 21 CFR Part 11 合规 7 HIGH 修复（G2/G7/G15/G16/G17/§1.2/G1）|
| **R198 v1.61** | MEDIUM 合规项 + 质量评分缓存 + TOCTOU 并发修复 + 2 个 e2e 脚本 |
| **R199/R200** | 产品管理模块全量实现（med-rms-product、ProductSelector、CRUD+双签+Excel 导出）|
| **R235-R247** | 用户双 bug + P3 全批次 + Gantt 增强 + DDL 清理 + 数据导出加固 × 3 + Cache 审查 |

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
→ **R227** (v1.83 P1 第二批 — DATA-049-A @TableLogic/DATA-010~013 编号生成改 MAX/LockLog 修正)
→ **R228** (v1.84 P2 批次 — DATA-017 safeOutbox/DATA-015/016 selectList 旁路/DATA-040/041 @AuditLog 补全)
→ **R229** (v1.85 P2 第二批 — DATA-022 ProjectService 校验 + DATA-015 TraceGraph 全表 selectList 修复)
→ **R230** (v1.86 P3 批次 — DATA-002 ChangeTimeline 父校验 + DATA-014-A DuplicateKey + DATA-042 Outbox 并发 claim)
→ **R231** (v1.87 P3 第二批 — CONTRACT-010 ComplianceController total + CONTRACT-009 ProductList 修复 + 角色映射 sys:dept:list / sys:ai:list)
→ **R232** (v1.88 P3 第三批 — DDL 安全约束/N+1 优化)
→ **R233** (v1.89 详情页"标记为已拆解"按钮)
→ **R234** (v1.90 DecomposeWorkbench 批量标记已拆解，方案 C)
→ **R235** (v1.91 用户测双 bug：status 回退 / router.push 缺 await)
→ **R236** (v1.92 P3 第四批 — N+1 + DhfEvidence 跨项目隔离)
→ **R238** (v1.94 Gantt 视图模式切换，方案 A)
→ **R239** (v1.95 Gantt 任务条显示优化，A+B 组合)
→ **R240** (v1.96 P3 第五批 — 任务状态机 + 需求版本 FK)
→ **R241** (v1.97 P3 第六批 — ChangeService 白名单)
→ **R242.1** (v1.98 DDL 冲突修复：140 vs r160 trace_gap_ignored)
→ **R243** (v1.99 DDL 一致性评估，无需修改)
→ **R244** (v2.00 审计日志导出 OOM 防护 + UTF-8 文件名)
→ **R245** (v1.99 Cache 失效深度审查，无需修改)
→ **R246** (v2.00 报表导出加固 — format 白名单/50MB OOM/UTF-8)
→ **R247 ⬅️ [HEAD]** (v2.01 DHF 证据包导出加固 — 输入校验/50MB OOM/UTF-8 文件名)
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
- **R222.4 ~ R234 v1.74**（早期 P0/P1/P2 批次共 36 项修复，详见 git log R222.4..R234）
- **R235-R240**（用户测双 bug + P3 第四/五批 + Gantt 视图切换 + 任务条优化）
- **R241-R247**（P3 第六批白名单 + DDL 冲突修复 + 一致性评估 + 数据导出加固 × 3 + Cache 失效审查）