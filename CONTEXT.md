# Med-RMS 项目上下文指南（新会话快速恢复）

> **用途**: 新会话开场引用此文件，5 分钟内恢复到完整上下文
> **更新**: 每次 R 节点完成时更新此文件
> **最后更新**: 2026-07-11（R175 项目管理增强前后端）

---

## 🚀 30 秒恢复（最小启动集）

```bash
cd "D:/zhutao/MED_RMS_PMS"
git log --oneline -5                                    # 最新 commit
git tag -l "R1*" | tail -10                            # 最新 tag
netstat -ano | grep ":808.*LISTENING" | head -3        # 后端实例
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```

## 📊 当前状态（2026-07-09 R162 后）

| 维度 | 值 |
|------|-----|
| **HEAD commit** | `e52e80b` (R175: 项目管理增强全量实施) |
| **最新 R 节点** | R175（项目管理增强：甘特图拖拽/项目克隆/任务看板/活动流/健康度评分/资源热力图/审计追踪/Excel导出/BOM CRUD/工时超预算/合规模板扩展） |
| **PRD 版本** | v2.2（2026-07-11，新增 FR-2.11~FR-2.16） |
| **后端端口** | 8080（运行中） |
| **GitHub tag 数** | 46+ R tag |
| **数据库** | UTF-8 + 21 CFR Part 11 哈希链 |
| **RBAC** | 9 角色 × 64 权限（新增 sys:*） × 245+ 关联 |
| **测试** | 146 Playwright 测试全通过（135 原始 + 11 R175 新增） |

## 📁 关键文件速查

| 文件 | 内容 | 行数 |
|------|------|------|
| `开发日志.md` | 48 个 R 节点完整记录 | 18960+ |
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

## 🏷️ R 节点全景（53 个 commit）

```
... → R165 → R166 (3 Bug) → R167 (仪表盘 API) → R168 (合规指标) → R169 (e2e 扩展, 135/135 ✅)
→ R170-R171 (上下文更新) → R172 (v-permission 通配符) → R173 (菜单角色大小写) → R174 (PRD v2.1→v2.2, 6 新 FR)
→ **R175 ⬅️ [HEAD]** (项目管理增强全量前后端)
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

- [开发日志.md](开发日志.md) - 51 个 R 节点详细记录（含 R162-R174）
- [SESSION_SUMMARY.md](SESSION_SUMMARY.md) - 本次会话关键决策和教训
- [.claude/projects/.../memory/MEMORY.md](.claude/projects/.../memory/MEMORY.md) - 项目级持久化记忆
- [测试报告/00-汇总/README.md](测试报告/00-汇总/README.md) - 全模块测试报告
- [测试报告/00-汇总/R162-PRDvs实现偏差分析报告.md](测试报告/00-汇总/R162-PRDvs实现偏差分析报告.md) - PRD vs 实现偏差
- [测试报告/00-汇总/R162-偏差修复计划.md](测试报告/00-汇总/R162-偏差修复计划.md) - 22 项 × 12 轮修复计划
- [Detailed/04-权限设计/RBAC矩阵.md](Detailed/04-权限设计/RBAC矩阵.md) - 完整 RBAC 矩阵