# Med-RMS 演示数据 + 沙箱就绪清单（W9-D2）

> **生成日期**：2026-06-12
> **基线**：v1.56 + v2.7 文档
> **沙箱环境**：localhost（PG 5432 + Spring Boot 8080 + Vue 5173）

## 一、沙箱数据现状（实测 2026-06-12）

| 表 | 记录数 | 业务意义 |
|----|--------|----------|
| `req_schema.t_requirement` | **1228** | 4 层级（URS/PRS/SRS/DRS）需求 |
| `chg_schema.t_change_request` | **102** | 变更申请 |
| `proj_schema.t_project` | **20** | 项目档案 |
| `compliance_schema.t_soup_component` | **55** | SOUP 组件 |
| `risk_schema.t_risk_register` | **46** | 风险登记 |
| `trace_schema.t_trace_link` | **31** | 追溯关系 |
| `sys_schema.t_user` | **36** | 系统用户 |
| `proj_schema.t_milestone` | **5** | 里程碑 |
| `compliance_schema.t_problem_report` | **49** | 问题报告 |

**沙箱已就绪，无需额外造数据。**

## 二、用户账号清单

| 角色 | 用户名 | 密码 | 用途 |
|------|--------|------|------|
| 系统管理员 | **admin** | admin123 | 全系统管理 |
| 8 业务角色 | 8 个种子用户 | admin123 | RBAC 演示 |

**统一密码** `admin123`（v2.5 验证）

## 三、启动命令

```cmd
REM 1. 后端
cd D:\zhutao\MED_RMS_PMS\Code\backend
mvn spring-boot:run -pl med-rms-web -Dspring-boot.run.profiles=dev

REM 2. 前端
cd D:\zhutao\MED_RMS_PMS\Code\frontend
npm run dev

REM 3. 验证
curl http://localhost:8080/api/requirements -H "Authorization: Bearer <token>"
```

## 四、5 大演示剧本

### 剧本 1：仪表盘全模块一瞥（1 分钟）

| 步骤 | URL | 关键数据 |
|------|-----|----------|
| 1.1 登录 | /login | admin / admin123 |
| 1.2 仪表盘 | /dashboard | 248 需求 / 12 待评审 / 5 变更中 |
| 1.3 需求统计 | /requirements | 1228 需求 / 4 层级 |

**演示话术**："看，这是 Med-RMS 仪表盘。248 条需求分布在 4 个层级（URS/PRS/SRS/DRS），5 个变更正在处理中。整个系统 11 个后端微模块 + 88 个前端路由。"

### 剧本 2：需求 4 层拆解链（2 分钟）

| 步骤 | URL | 操作 |
|------|-----|------|
| 2.1 进入需求 | /requirements/1 | URS-001-001 心电监护 |
| 2.2 查看拆解 | /requirements/1/decompose | 看到 PRS/SRS/DRS 子需求 |
| 2.3 追溯矩阵 | /traceability | 完整 URS→PRS→SRS→DRS→TC 链 |
| 2.4 覆盖率 | /traceability/coverage?projectId=1 | 实时统计 |

**演示话术**："这是需求 4 层 CTI（Class Table Inheritance）模型。URS 心电监护需求被拆解为 PRS 产品需求，再到 SRS 系统需求，最后到 DRS 设计需求。追溯矩阵自动生成，覆盖率实时统计。"

### 剧本 3：变更影响 + suspect 标记（2 分钟）

| 步骤 | URL | 操作 |
|------|-----|------|
| 3.1 变更列表 | /changes | 102 条变更 |
| 3.2 待审批 | /changes/pending | 看审批流 |
| 3.3 影响评估 | /changes/:id/impact | 追溯链 + 风险评估 |
| 3.4 双签控制 | /changes/:id/approve | MAJOR 需 ≥2 approver |
| 3.5 suspect | /requirements（带 Suspect 状态）| 自动标记下游 |

**演示话术**："变更管理是 21 CFR Part 11 重点。所有变更走 6 状态机（DRAFT→ANALYZING→PENDING_APPROVAL→APPROVED→EXECUTING→VERIFIED）。MAJOR 变更需 ≥2 approver 双签，符合 Part 11 §11.200。影响评估自动追溯下游 + 标记 Suspect。"

### 剧本 4：电子签名 TOTP 双签（2 分钟）

| 步骤 | URL | 操作 |
|------|-----|------|
| 4.1 签名设置 | /esignature/settings | 启用 TOTP |
| 4.2 OTP URI | /signatures/settings/1/otp/uri | 二维码生成 |
| 4.3 签名记录 | /esignature | 历史签名 |
| 4.4 重签 | /signatures/:id/re-sign | 旧签置无效 |

**演示话术**："电子签名支持 SHA-256 + TOTP 双签。签名值计算公式 SHA-256(documentType + documentId + entityHash + meaningCode + signerId + timestamp)，与 Part 11 §11.70 签名与记录链接要求一致。"

### 剧本 5：DHF 证据包导出（1 分钟）

| 步骤 | URL | 产出 |
|------|-----|------|
| 5.1 DHF 证据 | /compliance/dhf/1 | 完整证据包 |
| 5.2 eRPS 报告 | /compliance/erps/1 | NMPA eRPS 结构化包 |
| 5.3 审计日志 | /compliance/audit-logs | 哈希链校验 |

**演示话术**："DHF 证据包按 NMPA eRPS 格式生成，含产品信息、需求追溯、风险管理、变更控制、问题报告、IEC 62304 合规状态。审计日志 SHA-256 哈希链防篡改，符合 21 CFR Part 11 §11.10(e)。"

## 五、关键数据查询（PostgreSQL）

```sql
-- 心电监护仪项目（id=1）的需求统计
SELECT requirement_type, status, COUNT(*)
FROM req_schema.t_requirement
WHERE project_id = 1 AND is_deleted = false
GROUP BY requirement_type, status
ORDER BY requirement_type, status;

-- 变更状态分布
SELECT status, COUNT(*)
FROM chg_schema.t_change_request
WHERE is_deleted = false
GROUP BY status
ORDER BY status;

-- 风险等级
SELECT risk_level, COUNT(*)
FROM risk_schema.t_risk_register
WHERE is_deleted = false
GROUP BY risk_level;

-- SOUP 异常
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE license_expired IS NOT NULL) AS license_expired
FROM compliance_schema.t_soup_component
WHERE is_deleted = false;
```

## 六、6 份 W5 报告回顾

| 报告 | 路径 | 关键结论 |
|------|------|----------|
| W5 性能 | `tools/perf_reports/W5-PERF-REPORT.md` | k6 3 场景 P95 全部超阈值 |
| W5 安全 | `tools/sec_reports/W5-SEC-REPORT.md` | sqlmap 0 注入 / ZAP 0 告警 |
| k6 列表 | `tools/perf_reports/20260612/requirements-list-60s.json` | P95=33ms |
| k6 矩阵 | `tools/perf_reports/20260612/traceability-matrix-60s.json` | P95=2.5s |
| k6 影响 | `tools/perf_reports/20260612/impact-assessment-60s.json` | P95=6ms |
| Locust | `tools/perf_reports/20260612/jmeter-locust-50u-60s.html` | 0 失败 / 1288 req |

## 七、演示常见问题 Q&A

| Q | A |
|---|---|
| 系统支持多少用户？| 当前 36 用户 / 1 项目（沙箱），生产级可扩展到 1000+ |
| 数据如何迁移？| 已有 `DataMigrationService` 支持 JSON/CSV 导入 + 任务跟踪 + 幂等 |
| 21 CFR Part 11 怎么满足？| 9 项核心要求：签署显现、记录链接、唯一性、双组件签名、密码策略、哈希链、不可篡改、保留、操作序列 |
| 14 状态机在哪看？| 需求状态机 + 变更状态机 + 验收状态机 |
| 性能如何？| 仪表盘 < 0.5s / 需求列表 P95=33ms / 追溯矩阵 P95=2.5s / 影响评估 P95=6ms |
| 安全保障？| sqlmap 0 注入 / ZAP 0 告警 / bandit 0 高危 / RBAC 8 角色矩阵 |

## 八、沙箱就绪清单

- [x] PG 数据库 med_rms_pms 运行中（端口 5432）
- [x] 9 大表数据完整（1228 需求 / 102 变更 / 55 SOUP 等）
- [x] 8 角色用户就位
- [x] 后端 8080 跑通（admin 登录返回 JWT）
- [x] 33 e2e 通过（前端路由可达）
- [x] 6 份 W5 报告归档
- [x] 准入报告 v1.0 就位
- [x] CI 脚本 + 8 工具就位

**沙箱就绪 ✅**

## 九、推荐演示顺序（总时长约 10 分钟）

1. **登录**（30s）— admin / admin123
2. **剧本 1 仪表盘**（1min）
3. **剧本 2 需求拆解**（2min）
4. **剧本 3 变更管理**（2min）
5. **剧本 4 电子签名**（2min）
6. **剧本 5 DHF 证据**（1min）
7. **Q&A**（2min）

**演示就绪 ✅**
