# Med-RMS 项目级规范

> **范围**: 仅 Med-RMS 项目（`D:\zhutao\MED_RMS_PMS\`）
> **关系**: 项目级规范，**继承自全局规范** `~/.claude/CLAUDE.md`
> **更新**: 项目级 R 节点完成时同步更新

---

## 📋 项目元信息

| 维度 | 值 |
|------|-----|
| **项目名** | Med-RMS 医疗器械需求管理系统 |
| **后端** | Spring Boot 3.3.5 + Java 17 + MyBatis-Plus |
| **前端** | Vue 3 + Vite + Element Plus |
| **数据库** | PostgreSQL 16（11 个 Schema）|
| **缓存** | Redis 7 |
| **GitHub** | https://github.com/zhutao198/MED_RMS_PMS |
| **合规标准** | 21 CFR Part 11 / IEC 62304 / ISO 13485 / NMPA eRPS |

## 🚀 3 类核心文件（项目根目录）

| 文件 | 行数 | 用途 |
|------|------|------|
| **`CONTEXT.md`** | 130 | 新会话快速恢复（30 秒指南）|
| **`SESSION_SUMMARY.md`** | 248 | 关键决策 + 教训 |
| **`开发日志.md`** | 17060（压缩后）| 45 个 R 节点详细记录 |

**强制要求**：每次 R 节点 commit 后检查并更新这 3 类文件。

## 🔧 项目特定命令

```bash
# 启动 8080 后端（需管理员 PowerShell UAC）
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','D:\zhutao\MED_RMS_PMS\tools\restart_8080.ps1'"

# 手动 build + 启动
cd D:\zhutao\MED_RMS_PMS\Code\backend
mvn -B -DskipTests -Dspring-boot.repackage.skip=true install
cd med-rms-web
mvn -B -q -DskipTests spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"

# 跑 e2e 测试
cd D:\zhutao\MED_RMS_PMS\Code\backend\tools\test_runner
set PYTHONIOENCODING=utf-8
python scan_01_认证与系统.py

# 压缩开发日志
python D:\zhutao\MED_RMS_PMS\tools\compress_dev_log.py
```

## 👤 测试账号

| 用户名 | 密码 | 角色 | 用途 |
|--------|------|------|------|
| admin | admin123 | ADMIN | 系统管理员（通配 `*`） |
| qa_mgr | admin123 | QA_MGR | 质量/基线/测试 |
| pm | admin123 | PM | 项目/任务/IPD |
| re | admin123 | RE | 需求工程师 |
| reviewer | admin123 | REVIEWER | 评审 |
| risk_mgr | admin123 | RISK_MGR | 风险 |
| compliance | admin123 | COMPLIANCE | 合规 |
| viewer | admin123 | VIEWER | 只读 |
| pd | admin123 | PD | 产品经理 |

## 📁 项目特定目录

| 目录 | 用途 |
|------|------|
| `Code/backend/med-rms-{module}/` | 后端 11 个模块 |
| `Code/backend/ddl/` | 44 个 DDL 文件（030-148） |
| `Code/backend/tools/test_runner/` | 8 个 e2e 测试脚本 |
| `Code/frontend/src/views/` | 50+ Vue 组件 |
| `Detailed/04-权限设计/RBAC矩阵.md` | 9×63×242 RBAC |
| `测试报告/` | 11 份模块测试报告 |
| `架构-实现偏差与文档同步/` | 偏差清单 + DDL 变更日志 |
| `tools/` | 部署脚本 + 压缩脚本 |

## 🔐 项目特定约束

- **数据敏感**: 涉及医疗合规，所有审计日志必须留痕（21 CFR Part 11）
- **多 Schema**: 跨模块查询必须用 `JdbcTemplate`（避免循环依赖）
- **里程碑 NO**: NOT NULL 无 default，Service 自动生成
- **OT 验证码**: TOTP（RFC 6238），必须 6 位数字，30 秒窗口
- **审计哈希链**: 修改后必须通过 `verifyChainDetailed` 验证

## 📞 项目紧急联系人

- **GitHub Issues**: https://github.com/zhutao198/MED_RMS_PMS/issues
- **本地文档**: `D:\zhutao\MED_RMS_PMS\开发日志.md`

## 🔗 继承自全局规范

本文件**继承**自 `~/.claude/CLAUDE.md` 的所有规则：
- 语言：简体中文
- 工程铁律：改前先确认
- 变更追溯：每次代码修改后更新文档
- 工具使用：先读项目日志
- 回滚节点：Rxx 编号规范
- 新会话管理：3 类核心文件维护

**项目级 vs 全局**：
- 全局规范（`~/.claude/CLAUDE.md`）：跨项目通用
- 项目级（本文件）：Med-RMS 特定技术栈 + 命令 + 联系人

## 📝 项目级 R 节点规范补充

除全局 Rxx 规范外，本项目补充：

- **R 节点命名**: `R1xx v1.x <简述>` （v1.x 是当前主版本）
- **状态机相关**: R111-R123 集中处理（14→18 态、DCP、跨模块 e2e）
- **CI/CD 相关**: R117/R122/R129/R134-R142 集中处理
- **性能优化**: R118/R143 集中处理（tree 11.8 倍 + quality 14.2 倍）
- **bug 修复**: 每次都建 R 节点（不跳过小修复）