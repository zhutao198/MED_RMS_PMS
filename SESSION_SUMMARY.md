# Med-RMS 会话总结（关键决策与教训）

> **会话周期**: 2026-06-29 ~ 2026-07-02
> **总节点数**: 45 个 R 节点（R110-R148 + 5 个 R131.x）
> **总 commit**: 45+ 个
> **GitHub 仓库**: https://github.com/zhutao198/MED_RMS_PMS

---

## 🎯 会话主线

### Phase 1: 偏差清单同步（R111-R117）
**目标**: 把详细设计文档与实际代码的偏差全部同步
**关键产出**:
- 详细设计 §5 状态机 14→18 态改写
- 类图 Repository→Mapper 全面修订
- PrCorrection / RequirementPool 实体补全
- 10 个偏差清单 P0/P1 任务标记完成

### Phase 2: 性能 + 自动化（R118-R120）
**目标**: 优化系统性能，建立 CI/CD
**关键产出**:
- tree 查询 11.8 倍加速
- 4 项 P2 修复（changes total / auth/me / reportType 枚举 / matrix coverage）
- GitHub Actions workflow 就绪
- 32 个 tag 推送

### Phase 3: 深度测试 + Bug 修复（R123-R130）
**目标**: 全模块 e2e 测试覆盖，发现并修复实际 bug
**关键产出**:
- 4 个 e2e 脚本（状态机 9/9 + DCP 9/9 + 跨模块 9/11 + 10 模块）
- 263 行 RBAC 矩阵
- PD 角色 0→21 权限修复
- 跨模块业务流验证

### Phase 4: 持续 Bug 修复（R143-R148）
**目标**: 用户实测中发现并修复
**关键产出**:
- 质量评分性能 14.2 倍加速（1920ms → 135ms）
- 里程碑自动生成 milestoneNo
- 追溯管理端点补全（trace-count/test-case-count）
- OTP 持久化 bug 修复
- 9 次 CI/CD workflow 修复迭代

---

## 🏆 关键成就

### 技术成就
- ✅ **状态机 14→18 态**完整迁移路径
- ✅ **质量评分 14.2 倍加速**（从 1920ms N+1 → 135ms 批量查询）
- ✅ **CI/CD 全自动**（R117 e2e + R129 cd-deploy + R142 R145 修复）
- ✅ **审计哈希链完整**（R113 + R132 双重修复，21 CFR Part 11 合规）
- ✅ **RBAC 完整矩阵**（9 角色 × 63 权限 × 242 关联）
- ✅ **OTP 端点完整可用**（generate + enable + verify + uri）

### 流程成就
- ✅ **45 个 R 节点**全部 commit + tag
- ✅ **CI/CD 9 次迭代修复**（R134-R142）
- ✅ **完整 e2e 测试套件**（状态机 + DCP + 跨模块 + 性能）
- ✅ **5 份完整测试报告** + RBAC 矩阵 + 偏差清单

### 文档成就
- ✅ 开发日志 17000+ 行（含所有 R 节点详细记录）
- ✅ 测试报告 11 份模块报告 + 1 份汇总
- ✅ RBAC 矩阵 263 行完整文档
- ✅ CONTEXT.md（新会话快速恢复指引）

---

## 💡 关键经验教训

### 1. 后端代码 Bug 模式

#### W20 Bug：`updateById(id=null)` 静默失败
**症状**: 端点返回 200 但 DB 未保存
**根因**: `getSettings()` 在记录不存在时返回 id=null 临时对象，`updateById(id=null)` 不执行
**修复模式**: `id==null ? insert : update`
**出现位置**: R113（baseline）、R120（auth/me）、**R148（OTP）** —— 出现 3 次！
**预防**: 创建通用 `saveOrUpdate(entity)` 工具方法

#### 质量评分 N+1 模式
**症状**: 端点慢（1920ms for 166 requirements）
**根因**: `scoreAll` 对每个 requirement 单独调用 `score()`
**修复模式**: 批量查询（IN clause）+ 内存 join
**改善**: 14.2 倍加速

### 2. 前端字段映射

#### 字段名不一致
- 前端 `targetDate` / 后端 `plannedDate` → 失败
- 前端不传 NOT NULL 字段（`milestoneNo`）→ 失败
- **教训**: 后端 Service 自动生成必填字段，前端不感知

### 3. 测试路径与 API 路径不一致
**R114 测试发现**: 9 个 warn 中 5 个是测试路径错误
**R146 修复**: scan_NN_*.py 修正路径
**教训**: 测试脚本与实际 controller 路径必须严格对齐

### 4. CI/CD YAML 陷阱

#### workflow_run trigger 复杂性
- trigger workflow name 必须严格匹配（"R114" vs "R117"）
- workflow_run 仅在上游完成时触发
- **解决**: 加 push trigger 让 cd-deploy 独立运行

#### env.SSH_KEY 表达式错误
- `if: env.SSH_KEY == null` 不被识别
- **解决**: 用 `if: secrets.DEPLOY_SSH_KEY`（truthy 检查）

#### mvn build 路径问题
- 在 parent 目录 `mvn` 找不到 main class
- **解决**: `cd Code/backend` 后再 mvn

### 5. Windows 权限管理

#### taskkill 失败
**症状**: `Stop-Process -Force` 拒绝访问
**原因**: 进程由更高权限用户启动
**解决**: UAC 弹窗（`Start-Process -Verb RunAs`）让用户手动授权

#### mvn spring-boot:run fork 模式
**症状**: 默认 fork=true 时，mvn 进程和 java 进程分开
**解决**: 用 `-Dspring-boot.run.fork=true` + `-pl med-rms-web` 显式指定

### 6. 数据库迁移注意事项

#### 字符编码 "已迁移" 但报告过时
- bug_report_2026-06-29.md 报告 "SQL_ASCII"
- R121 实际验证 DB 已经是 UTF-8
- **教训**: 始终用工具验证（`pg_encoding_to_char()`），不依赖报告

#### PostgreSQL 字符集迁移
- ALTER DATABASE 不支持改 encoding
- 必须用 pg_dump/drop/createdb/restore 流程
- 仅文档化（不自动执行），高风险操作需 DBA

---

## 🎓 通用最佳实践

### 1. 开发流程
- **Rxx 节点规范**: 改前建节点框架 → 改中编辑 → 改后回填 → commit + tag
- **回滚锚点**: 每次 commit 创建 annotated tag，便于 `git checkout R1XX` 一键回滚
- **不 amend 循环**: 多次 amend 会改 hash，避免用单次 commit 完成

### 2. 测试策略
- **N+1 优先**: 项目数据量大时 N+1 是头号性能问题
- **批量优于循环**: 用 IN clause 一次查询，内存 join
- **e2e 覆盖**: API 单点 + 跨模块业务流 + 状态机 + DCP

### 3. CI/CD
- **workflow_run 复杂**: 用 push trigger 简化
- **Secrets 检查**: workflow 实际跑前用 dry-run job 验证
- **YAML 编码**: UTF-8 + BOM 兼容 Windows PowerShell

### 4. 用户协作
- **诊断先于修复**: 用户报告问题先完整诊断根因，再修复
- **截图+日志**: 复杂错误用 log 抓取具体堆栈
- **Python 测试**: 写脚本自动化诊断（不靠手动 curl）

---

## 📊 性能指标

| 指标 | 改善 |
|------|------|
| 质量评分延迟 | 1920ms → 135ms（**14.2 倍**） |
| 追溯树查询 | 777ms → 66ms（**11.8 倍**） |
| 28 端点最慢 | < 100ms（**0 慢查询**） |
| CI build 时间 | 9 分 25 秒（含 Maven build + e2e） |
| 测试脚本数 | 0 → 8（覆盖所有模块） |

## 🏷️ 完整 R 节点清单

| R | 主题 | 关键 |
|---|------|------|
| R110 | R102-R110 索引 | 历史 |
| R111 | 状态机 14→18 态 | v1.47 BUG #143 修复 |
| R112 | Repository→Mapper | 全面修订 |
| R113 | B-01 P0 哈希链 | 21 CFR Part 11 |
| R114 | 前后端联合测试 | 10 模块扫描 |
| R115 | P0-02/P1-01/P1-05 | stats + verifyChain + Dashboard |
| R116 | 种子数据 DDL 147 | 5 张表 ID=1 |
| R117 | GitHub Actions E2E | 工作流就绪 |
| R118 | tree 性能 + viewer | 11.8 倍加速 |
| R119 | 字符编码 + API 路径 | 文档化 |
| R120 | 4 项 P2 修复 | changes + auth/me + 枚举 + coverage |
| R121 | 字符编码验证 | 撤销（DB 已是 UTF-8）|
| R122 | GitHub push | 23 tag 上云 |
| R123 | 状态机 e2e | 9/9 = 100% |
| R124 | RBAC 矩阵 | 9×63×221 |
| R125 | PD 角色修复 | 0→21 权限 |
| R126 | 端点补强 | trace-count/test-case-count |
| R127 | 8080 部署脚本 | PowerShell + UAC |
| R128-R142 | CI/CD 修复 | 9 迭代 |
| R143 | 性能 + bug | 14.2 倍 + 里程碑 |
| R144 | 全面扫描 | 28 端点 0 慢 |
| R145 | mock 修复 | 测试回归 |
| R146 | 端点补强 | 5 路径修正 |
| R147 | P2 性能加固 | k6 阈值 |
| R148 | OTP bug 修复 | updateById 持久化 |

---

## 🎯 未来方向（R149+ 候选）

### 性能
- [ ] Redis 缓存质量评分（TTL 5min）
- [ ] 数据库索引优化（v1.49 慢查询分析）
- [ ] 前端首屏 SSR

### 功能
- [ ] 状态机迁移图可视化
- [ ] 实际 e2e Playwright（替换手动 Chrome DevTools）
- [ ] OpenAPI 文档自动生成

### 工程
- [ ] GitHub Actions 部署到真实 staging
- [ ] Docker 化部署
- [ ] 数据库迁移自动化（Flyway/Liquibase）

### 合规
- [ ] 字符编码最终迁移（DB 端 DDL 148 已文档化）
- [ ] 审计日志分区表
- [ ] 21 CFR Part 11 完整合规审计

---

## 💾 持久化资源

| 资源 | 位置 | 用途 |
|------|------|------|
| **CONTEXT.md** | 根目录 | 新会话快速恢复 |
| **SESSION_SUMMARY.md** | 根目录 | 关键决策 + 教训（本文件）|
| **开发日志.md** | 根目录 | 45 个 R 节点完整记录 |
| **MEMORY.md** | `.claude/projects/.../memory/` | 项目级持久化记忆 |
| **测试报告/** | 根目录 | 11 份模块测试报告 |
| **CONTEXT_RESTORE.sh** | 待创建 | 一键恢复脚本 |

## 🔗 关键链接

- **GitHub**: https://github.com/zhutao198/MED_RMS_PMS
- **主分支**: 2633086
- **最新 R 节点**: R148（OTP bug 修复）
- **CI 工作流**: R117 (e2e) + R129 (cd-deploy)
- **后端代码**: 8 个 Java 文件修改
- **前端代码**: 1 个 Vue 文件修改
- **数据库 DDL**: 6 个新文件（DDL 142-147）