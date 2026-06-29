# Med-RMS 医疗器械全生命周期需求管理系统

> **v1.56 + 12 周冲刺交付** | 文档 v4.3 | 状态：完整生产就绪
>
> 单测 798/798 | 集成 52/52 | e2e 16 路由 + 6 业务流 | k6 63851 请求 / 0 失败

## 概述

Med-RMS 是面向**医疗器械（IEC 62304 / 21 CFR Part 11 / NMPA / ISO 13485）**的全生命周期需求管理系统，覆盖：

- 需求管理（URS/PRS/SRS/DRS 四层 + CTI + 闭包表）
- 追溯管理（追溯图 + 缺口 + 覆盖率）
- 变更管理（CCB 审批 + 影响分析 + 签名）
- 合规管理（IEC 62304 清单 + DHF 证据 + NMPA eRPS）
- 电子签名（21 CFR Part 11 §11.50/§11.70）
- 风险管理（ISO 14971 FMEA + 风险矩阵 + 监控）
- 项目管理（IPD/DCP 门控 + 甘特图 + 资源）
- 审计日志（哈希链防篡改）

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 3.3 / Java 17 / MyBatis-Plus 3.5 / PostgreSQL 16 / Redis 7 |
| 前端 | Vue 3 / Vite 6 / Element Plus 2.9 / TypeScript |
| 测试 | JUnit 5 / Mockito / Spring Boot Test / k6 |
| 部署 | Maven / npm / Nginx / systemd / pg_dump / OpenAPI 3.0.1 |

## 快速启动

### 前置要求
- JDK 17+
- Maven 3.9+
- Node.js 20 LTS
- PostgreSQL 16（端口 5432）
- Redis 7（端口 6379）

### 启动步骤
```bash
# 1. 创建数据库（DDL 见 Code/backend/med-rms-web/src/main/resources/02-DDL/med_rms_ddl.sql）
psql -U postgres -c "CREATE DATABASE med_rms_pms;"

# 2. 启动后端
cd Code/backend
mvn spring-boot:run -pl med-rms-web

# 3. 启动前端（新终端）
cd Code/frontend
npm install
npm run dev
```

访问 http://localhost:5173（前端），admin/admin123 登录。

## 文档

| 文档 | 路径 | 用途 |
|------|------|------|
| **DEPLOY.md** | `Code/DEPLOY.md` | 生产部署完整步骤 |
| **00-交付总览** | `Detailed/00-交付总览.md` | 项目状态 v4.3 |
| **开发日志** | `开发日志.md` | 12 周冲刺每日记录 |
| **API 文档（Swagger）** | `http://localhost:8080/api/swagger-ui/index.html` | 253 个 endpoint |
| **OpenAPI 3.0.1** | `GET /api/api-docs` | 182K 机器可读规范 |
| **健康检查** | `GET /api/actuator/health` | 系统健康 |
| **架构设计** | `Med-RMS_System_Architecture_v1.1.md` | 系统架构 |
| **PRD** | `prd-med-rms-v2.1-2026-05-22.md` | 需求规格 |
| **数据字典** | `Detailed/08-数据字典/数据字典.md` | 枚举值 / 状态码 |
| **异常处理** | `Detailed/05-异常容错/异常容错设计.md` | 异常码体系 |
| **审计日志** | `Detailed/06-日志审计/日志审计设计.md` | 审计链设计 |
| **权限流程** | `Detailed/04-权限设计/权限流程设计.md` | RBAC |

## 测试

```bash
# 单元测试（798 用例 / 0 失败 / 0 错误 / 0 跳过）
cd Code/backend
mvn test -Djacoco.skip=true

# 集成测试（52 用例）
mvn -pl med-rms-web test

# 性能压测（k6 5min × 100VU / 63851 请求 / 0 失败）
Code/backend/tools/bin/k6/k6-v0.50.0-windows-amd64/k6.exe run \
    Code/backend/tools/perf/w19_long.js

# 完整 CI 流水线（4 阶段 / 113s）
bash Code/backend/ops/ci.sh
```

## 运维

### DB 备份（永久化）
```bash
bash Code/backend/ops/backup.sh
# crontab: 0 2 * * * /opt/medrms/ops/backup.sh
```

### 健康检查（永久化）
```bash
bash Code/backend/ops/health_check.sh
# nohup background
```

### 数据清理
```bash
# 测试残留数据清理（3 个脚本）
psql -U postgres -d med_rms_pms -f Code/backend/cleanup_test_data.sql
psql -U postgres -d med_rms_pms -f Code/backend/cleanup_w5_data.sql
psql -U postgres -d med_rms_pms -f Code/backend/cleanup_users_risks.sql
```

## 项目结构

```
Code/
├── backend/                       # Spring Boot 后端 (Maven 多模块)
│   ├── med-rms-web/              # Web 入口（包含 12 模块 + 控制器）
│   ├── med-rms-admin/            # 用户/角色/权限/字典
│   ├── med-rms-requirement/       # 需求管理
│   ├── med-rms-traceability/      # 追溯管理
│   ├── med-rms-change/            # 变更管理
│   ├── med-rms-compliance/        # 合规管理
│   ├── med-rms-esignature/        # 电子签名
│   ├── med-rms-risk/              # 风险管理
│   ├── med-rms-project/           # 项目管理
│   ├── med-rms-notification/      # 通知
│   ├── med-rms-common/            # 公共模块
│   ├── ops/                       # 运维脚本（备份 / CI / 健康检查）
│   └── tools/perf/                # k6 压测脚本
├── frontend/                      # Vue 3 前端
├── DEPLOY.md                      # 部署文档
├── Detailed/                      # v1.56 详细设计
└── 开发日志.md                     # 12 周冲刺日志
```

## 12 周冲刺成就

| 维度 | W12 初 | W24 末 | 变化 |
|------|--------|--------|------|
| 单测 | 0 | **798** | +798 |
| 集成测试 | 0 | 52 | +52 |
| e2e 路由 | 0 | 16 | +16 |
| 业务流 e2e | 0 | 6 步 | +6 |
| k6 请求 | 0 | 63851 | +63851 |
| Service 覆盖 | 0% | 75% | +75% |
| Controller 覆盖 | 0% | 70% | +70% |
| 数据清理 | 0 | 707 条 | -707 脏数据 |
| 永久化资产 | 0 | 10 个 | +10 |
| 性能 p95 | 626ms | 215ms | -66% |
| 性能（tracing-graph）| 128K | 30K | -75% |
| 文档 | v3.0 | v4.3 | +1.3 |

### 关键 bug 修复

| # | 周 | bug | 影响 |
|---|----|-----|------|
| 1 | W15 | Dashboard 11 API 403 | 前端 token 过期无自动 refresh |
| 2 | W20 | **电子签名密码设置无效** | 21 CFR Part 11 合规失效（整套签名系统不可用）|
| 3 | W21 | RESTful 401/403 不友好 | 无 token 返 403 应 401 |
| 4 | W21 | 权限拒绝无业务码 | 缺 SY0201/SY0202 |

### 关键性能优化

- **W17**：tracing-graph 响应从 128K → 30K（移除 title 字段 + MAX_NODES 截断）
- **W19**：祖先表缺索引导致 p95 626ms → 加索引后 **215ms**（-66%）

### 合规审计

- **21 CFR Part 11 全 7 子项合规**（W20）：系统验证/防篡改/权限/审计/设备/电子签名/记录关联

## 永久化资产清单（10 个）

| 类别 | 文件 |
|------|------|
| SQL 清理 | cleanup_test_data.sql / cleanup_w5_data.sql / cleanup_users_risks.sql |
| 压测 | tools/perf/w17_smoke.js / w19_long.js |
| 前端 | src/api/request.ts（自动 refresh）|
| 性能 | TraceGraphService.java + 祖先表索引 |
| 错误处理 | GlobalExceptionHandler.java + SecurityConfig.java |
| 备份 | ops/backup.sh |
| CI | ops/ci.sh |
| 健康检查 | ops/health_check.sh |
| 可观测性 | application.yml（Actuator）|
| UX | TraceGraph.vue（截断提示）|

## License

内部使用 / 客户定制 / 医疗器械合规项目
