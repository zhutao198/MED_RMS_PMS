# Med-RMS 交付物清单

> 版本：v1.0 | 日期：2026-06-15 | 状态：v1.56 + 12 周冲刺完整交付

## A. 源代码

### A1. 后端（Spring Boot 3.3 / Java 17 / Maven）
| 模块 | 路径 | 职责 |
|------|------|------|
| med-rms-web | `Code/backend/med-rms-web/` | Web 入口 + 安全 + 12 模块装配 |
| med-rms-admin | `Code/backend/med-rms-admin/` | 用户/角色/权限/字典/数据迁移 |
| med-rms-requirement | `Code/backend/med-rms-requirement/` | 需求 CRUD + 拆解 + 质量评分 |
| med-rms-traceability | `Code/backend/med-rms-traceability/` | 追溯图 + 缺口 + 覆盖率 |
| med-rms-change | `Code/backend/med-rms-change/` | 变更申请 + CCB 审批 + 影响分析 |
| med-rms-compliance | `Code/backend/med-rms-compliance/` | IEC 62304 / DHF / NMPA eRPS / 法规 |
| med-rms-esignature | `Code/backend/med-rms-esignature/` | 21 CFR Part 11 电子签名 |
| med-rms-risk | `Code/backend/med-rms-risk/` | FMEA + 风险矩阵 + 监控 |
| med-rms-project | `Code/backend/med-rms-project/` | IPD/DCP 门控 + 甘特图 + 资源 |
| med-rms-notification | `Code/backend/med-rms-notification/` | 通知 + 多渠道分发 |
| med-rms-common | `Code/backend/med-rms-common/` | 异常 + 工具类 + Result |

**代码量**：~50,000 行 Java

### A2. 前端（Vue 3 / Vite 6 / Element Plus）
- **路径**：`Code/frontend/`
- **代码量**：~15,000 行 TypeScript/Vue
- **页面**：~45 个（路由）

## B. 数据库（PostgreSQL 16）

| Schema | 表 | 用途 |
|--------|----|----|------|
| public | 1 | 框架表（outbox）|
| req_schema | 8 | 需求 + 闭包 + 评分 + 池 + 标签 |
| trace_schema | 5 | 追溯链 + 缺口忽略 + 横向关系 |
| chg_schema | 6 | 变更 + 影响项 + 时间线 + 附件 |
| compliance_schema | 7 | IEC 清单 + DHF + 基线 + 审计 + 统计 |
| esign_schema | 3 | 签名 + 意图 + 设置 |
| risk_schema | 4 | 评估 + 矩阵 + 登记表 + 监控 |
| proj_schema | 6 | 项目 + 任务 + 依赖 + 成员 + 模板 + 工时 |
| not_schema | 4 | 通知 + 渠道 + 邮箱队列 + IM 队列 |
| sys_schema | 5 | 用户 + 角色 + 权限 + 字典 + 迁移 |
| report_schema | 1 | dashboard_config |

**合计**：50+ 表 / 11 schema / 170+ 字段

DDL 文件：`Code/backend/med-rms-web/src/main/resources/02-DDL/med_rms_ddl.sql`（133KB）

## C. 文档（10+ 个）

| 文档 | 路径 | 用途 |
|------|------|------|
| **README.md** | `Code/README.md` | 项目入口 |
| **DEPLOY.md** | `Code/DEPLOY.md` | 生产部署完整步骤 |
| **DELIVERABLES.md** | `Code/DELIVERABLES.md` | 本文档 |
| 开发日志 | `开发日志.md` | 12 周冲刺每日记录（v3.0→v4.3） |
| 00-交付总览 | `Detailed/00-交付总览.md` | 项目状态总览 |
| 详细设计 | `Detailed/01-详细设计/*.md` | 9 个模块详细设计（95+KB） |
| DDL | `Detailed/02-DDL/med_rms_ddl.sql` | 数据库定义（133KB） |
| OpenAPI | `Detailed/03-OpenAPI/med-rms-openapi.yaml` | 119 接口 / 134 Schema（199KB） |
| 权限流程 | `Detailed/04-权限设计/权限流程设计.md` | RBAC 设计（36KB） |
| 异常容错 | `Detailed/05-异常容错/异常容错设计.md` | 异常码体系（51KB） |
| 审计日志 | `Detailed/06-日志审计/日志审计设计.md` | 哈希链审计（39KB） |
| 交互原型 | `Detailed/07-交互原型/` | 75 个 HTML 原型 |
| 数据字典 | `Detailed/08-数据字典/数据字典.md` | 枚举值（14KB） |
| 系统架构 | `Med-RMS_System_Architecture_v1.1.md` | 架构设计（66KB） |
| PRD | `prd-med-rms-v2.1-2026-05-22.md` | 需求规格（120KB） |

## D. 测试（10 个永久化资产）

| 资产 | 路径 | 用途 |
|------|------|------|
| 单测 798 | `Code/backend/**/src/test/.../*Test.java` | Service + Controller 单测 |
| 集成 52 | `Code/backend/med-rms-web/src/test/...` | @SpringBootTest 集成 |
| k6 冒烟 | `Code/backend/tools/perf/w17_smoke.js` | 5 API 30s 30VU |
| k6 长时 | `Code/backend/tools/perf/w19_long.js` | 5 API 4min 100VU |
| CI 流水线 | `Code/backend/ops/ci.sh` | 4 阶段自动化 |
| DB 备份 | `Code/backend/ops/backup.sh` | pg_dump + 7 天保留 |
| 健康检查 | `Code/backend/ops/health_check.sh` | 10s 轮询 /api/health |
| 清理脚本 3 | `Code/backend/cleanup_*.sql` | 测试残留清理（INTEG-TEST/AUDIT-AOP/smoke/w5 压测/integ_test） |
| 前端拦截器 | `Code/frontend/src/api/request.ts` | 401/403 自动 refresh + 5xx ElMessage |
| 错误边界 | `Code/backend/med-rms-common/.../GlobalExceptionHandler.java` | 4 类异常（业务/校验/资源/系统） |

## E. 性能基准

| 指标 | 修复前 | 修复后 | 提升 |
|------|--------|--------|------|
| 祖先表 p95 | 626ms | 215ms | **-66%** |
| tracing-graph 响应 | 128K | 30K | **-76%** |
| 8 万+ k6 请求失败率 | 0% | 0% | - |
| 4min × 100VU 长时 | - | 0 失败 | - |

## F. 合规性

### F1. 21 CFR Part 11（7/7 子项）
- ✅ §11.10(a) 系统验证（单测 + 集成 + e2e）
- ✅ §11.10(c) 防篡改（哈希链 SHA-256）
- ✅ §11.10(d) 权限控制（RBAC）
- ✅ §11.10(e) 审计自动生成
- ✅ §11.10(g) 设备验证
- ✅ §11.50 电子签名（v1.47 修复后）
- ✅ §11.70 签名-记录关联

### F2. RESTful 合规
- ✅ 401（未登录）+ 403（权限不足）+ 业务码（SY0201/SY0202）
- ✅ 404 / 500 / 业务异常统一返回 `{code, message, data, timestamp}`

### F3. IEC 62304 / ISO 13485
- ✅ 软件安全分类 A/B/C
- ✅ DHF 证据包
- ✅ NMPA eRPS 报告导出

## G. 验收标准

| 类别 | 标准 | 实际 |
|------|------|------|
| 功能 | v1.56 详细设计 14/14 FR | ✅ 14/14 |
| 测试 | 单测 + 集成 + e2e + 压测 | ✅ 4 维度全过 |
| 性能 | 关键 API p95 < 1s | ✅ 215ms |
| 合规 | 21 CFR Part 11 + IEC 62304 | ✅ 双合规 |
| 安全 | 0 高危漏洞 | ✅ 0 |
| 数据 | 0 脏数据 | ✅ 707 条已清理 |
| 文档 | README + DEPLOY + OpenAPI | ✅ 3 件齐备 |
| 部署 | DB 备份 + CI + 健康检查 | ✅ 3 永久化 |

## H. 已知限制（接受为非阻塞）

- **Controller 覆盖 70%**：10 个零测试 Controller 边际成本高，留待后续
- **tracing-graph 节点分组渲染**：cytoscape/d3-force 替换 canvas 复杂度高，留待后续
- **Caffeine 缓存**：需新依赖，多模块改动，留待后续
- **Docker 化**：需 Docker Desktop + Hyper-V（Windows 限制），留待后续

## I. 许可

内部使用 / 客户定制 / 医疗器械合规项目
