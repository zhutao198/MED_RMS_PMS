# Med-RMS 医疗器械全生命周期需求管理系统

# 系统架构设计文档

**System Architecture Design Document**

| 属性 | 值 |
|------|-----|
| 版本 | v1.1 |
| 日期 | 2026-05-22 |
| 作者 | 软件架构师团队 |
| 所属组织 | 武汉中旗生物医疗电子有限公司 |
| 保密级别 | 内部 |
| 基于 | v1.0 + 评审报告修订 |

> **文档说明**：本版本 v1.1 基于 2026-05-22 评审报告（6项严重+15项重要）进行修订。每处修订标注 `> **变更记录**：{日期} | {变更人} | {类型} | {原因}`，文档末尾附完整变更履历表。

---

## 第1章 架构概述与设计原则

### 1.1 文档目的与范围

本文档定义Med-RMS（医疗器械全生命周期需求管理系统）的整体软件架构。文档覆盖以下范畴：

- 系统宏观架构与技术栈选型
- 领域模型与限界上下文划分
- 分层架构设计与模块组织
- 核心数据模型与API设计
- 架构决策记录（ADR）
- 安全架构与部署架构
- 演进路线图与分阶段实施策略

关键约束条件：

- 用户规模：<200人，纯私有化本地部署
- 团队规模：后端2-3人，前端1-2人，测试1人
- 合规要求：21 CFR Part 11、ISO 13485、IEC 62304、NMPA
- 技术基线：Spring Boot + Vue 3，已确认使用Element Plus UI库
- 集成系统：仅泛微OA（单点登录+审批流对接）
- 分阶段交付：P0（116人天）→ P1（98人天）→ P2（81人天）

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据P-ORG-04评审意见，P0工期从80人天调整为116人天，P1从73人天调整为98人天

### 1.2 核心设计原则

本系统架构设计遵循以下八大原则：

1. **模块化单体优先 (Modular Monolith First)**
   以模块化单体作为起始架构，每个模块有明确边界和独立数据库Schema，未来可无痛拆分为微服务。适用于<200用户、小团队场景。

2. **领域驱动设计 (Domain-Driven Design)**
   以限界上下文为基本单元组织代码，核心领域逻辑与技术关注点隔离。使用事件驱动解决跨上下文协作。

3. **合规内置而非附加 (Compliance by Design)**
   审计追踪、电子签名等合规能力作为基础设施内置于架构层，而非业务层附加。21 CFR Part 11要求贯穿整个系统。

4. **数据不可篡改 (Data Immutability)**
   所有关键操作的审计追踪采用追加只写模式，审计日志不可删除不可修改，并通过哈希链保障完整性。需求备份通过基线快照实现。

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-RED-03评审意见，补充哈希链保障完整性描述

5. **变更闭环 (Change Closed-Loop)**
   变更管理作为跨模块枢纽功能，通过事务性发件箱模式实现影响自动传播、suspect自动标记、追溯链自动更新，保障事件可靠投递。

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-ORG-02评审意见，将事件驱动改为事务性发件箱模式

6. **前后端分离 (Frontend-Backend Separation)**
   前后端完全解耦，RESTful API通信，OpenAPI 3.0规范接口定义。前端独立构建和部署。

7. **渐进式交付 (Incremental Delivery)**
   架构支持分阶段交付，每个阶段的架构变化尽量向前兼容。预留扩展点但不过度设计。

8. **可观测性 (Observability)**
   从第一天起内置健康检查、应用指标和分布式追踪，保证系统可运维性。

---

## 第2章 领域模型与限界上下文

### 2.1 限界上下文总览

基于DDD方法，通过事件风暴将系统划分为9个限界上下文，每个上下文拥有独立的业务语言和数据存储。

| 上下文名称 | 英文标识 | 核心职责 | 交付阶段 |
|-----------|---------|---------|---------|
| 需求管理 | requirement | 四层需求CRUD、状态机、评审、基线 | P0 |
| 追溯管理 | traceability | 纵向/横向追溯、追溯矩阵、断裂检测 | P0 |
| 变更管理 | change-mgmt | 变更申请、影响评估、suspect标记、审批流 | P0 |
| 合规管理 | compliance | 审计追踪、法规库、DHF证据包 | P0 |
| 电子签名 | e-signature | 21 CFR Part 11电子签名、签名验证、签名显现 | P0 |
| 风险管理 | risk-mgmt | ISO 14971风险登记、FMEA、风险-需求追溯 | P1 |
| 项目管理 | project-mgmt | 任务、里程碑、甘特图、资源、工时 | P2 |
| 报表与仪表盘 | reporting | 指标驾驶舱、多视角视图、报表导出 | P1 |
| 系统管理 | system-admin | RBAC、模板、通知、API网关 | P0 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增"电子签名"限界上下文，从合规管理中独立

### 2.2 上下文映射关系

上下文之间的关系采用Context Mapping模式定义，确保依赖方向清晰、耦合最小化。

| 上游上下文 | 下游上下文 | 映射模式 | 数据流向 | 触发机制 |
|-----------|-----------|---------|---------|---------|
| 需求管理 | 追溯管理 | Shared Kernel | 需求ID + 关联关系 | 需求创建/拆解事件 |
| 需求管理 | 变更管理 | Upstream-Downstream | 变更申请单 + 需求快照 | 变更发起事件 |
| 变更管理 | 需求管理 | Upstream-Downstream | suspect标记 + 变更结果 | 变更审批通过事件 |
| 变更管理 | 追溯管理 | Upstream-Downstream | 追溯关系更新 | 变更执行事件 |
| 合规管理 | 全局 | Conformist | 审计日志 | 关键操作拦截 |
| 电子签名 | 需求管理/变更管理 | Upstream-Downstream | 签名记录 + 签名验证结果 | 审批/确认操作触发 |
| 需求管理 | 项目管理 | Upstream-Downstream | 需求→任务转化 | 任务拆解事件 |
| 风险管理 | 需求管理 | Partnership | 风险-需求双向关联 | 风险创建/更新事件 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增"电子签名→需求管理/变更管理"映射关系

### 2.3 核心领域对象模型

#### 2.3.1 需求管理上下文聚合根

需求管理上下文是系统核心，其聚合根设计如下：

| 聚合根 | 类型 | 不变条件 | 关联实体 |
|-------|------|---------|---------|
| Requirement | 抽象基类 | ID、层级、项目不变 | — |
| URS (UserReq) | 聚合根 | 状态机、法规关联完整性 | PRS列表 |
| PRS (ProductReq) | 聚合根 | 上游URS覆盖率≥100% | URS + SRS列表 |
| SRS (SystemReq) | 聚合根 | 上游PRS覆盖率≥100%、接口规格完整 | PRS + DRS列表 + TestCase列表 |
| DRS (DesignReq) | 聚合根 | 上游SRS覆盖率≥100%、实现负责人非空 | SRS + Task列表 |
| Baseline | 聚合根 | 基线内容不可修改 | 需求快照列表 |
| Review | 实体 | 审批人、时间、结论不可修改 | 关联需求 |
| RequirementVersion | 实体 | 版本号递增、变更摘要不可修改 | 关联Requirement |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05评审意见，新增RequirementVersion实体支持版本历史追踪

#### 2.3.2 变更管理上下文聚合根

| 聚合根 | 类型 | 不变条件 | 关联实体 |
|-------|------|---------|---------|
| ChangeRequest | 聚合根 | 变更原因、原始内容不可修改 | 变更记录、影响评估报告 |
| ChangeRecord | 实体 | 修改前后对比不可修改 | 关联ChangeRequest |
| ImpactAssessment | 值对象 | 评估时间不可修改 | — |

#### 2.3.3 合规管理上下文聚合根

| 聚合根 | 类型 | 不变条件 |
|-------|------|---------|
| AuditLog | 实体（追加只写） | 日志内容不可修改、不可删除、哈希链连续 |
| RegulationClause | 实体 | 条款编号、内容版本可追踪 |
| DHFEvidencePackage | 值对象 | 生成时间、包含模块列表 |

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-RED-03评审意见，AuditLog不变条件增加"哈希链连续"；移除ElectronicSignature（独立为电子签名限界上下文）

#### 2.3.4 电子签名上下文聚合根

| 聚合根 | 类型 | 不变条件 | 关联实体 |
|-------|------|---------|---------|
| ElectronicSignature | 聚合根 | 签名人、签名时间、签名含义、签名值、被签名文档哈希不可修改 | 关联实体（Requirement/ChangeRequest等） |
| SignatureMeaning | 值对象 | APPROVE/REJECT/REVIEW/CONFIRM，不可扩展 | — |
| SignatureVerification | 实体 | 验证结果、验证时间不可修改 | 关联ElectronicSignature |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增电子签名限界上下文聚合根

---

## 第3章 系统分层架构

### 3.1 宏观架构图

系统采用前后端分离的模块化单体架构，从外到内分为四层：

| 层级 | 名称 | 职责 | 技术栈 |
|-----|------|------|-------|
| L0 | 客户端 (Client) | Vue 3 SPA，浏览器运行时 | Vue 3 + TypeScript + Element Plus + Pinia |
| L1 | 网关层 (Gateway) | 请求路由、身份验证、限流、CORS | Spring Cloud Gateway / Nginx |
| L2 | 应用层 (Application) | REST API、DTO转换、事务管理、权限校验 | Spring Boot 3.x + Spring Security |
| L3 | 基础设施层 (Infrastructure) | 数据持久化、缓存、文件存储、事件发布 | PostgreSQL + Redis + MinIO + Debezium |

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-ORG-02评审意见，基础设施层事件发布组件从Spring ApplicationEvent改为Debezium CDC

### 3.2 后端模块组织

后端采用Spring Modulith模块化组织，每个模块包含独立的包结构和数据库Schema：

| 模块 | Maven Module | 包结构 | 数据库Schema | 交付阶段 |
|-----|-------------|-------|-------------|---------|
| 需求管理 | med-rms-requirement | domain + application + infrastructure + interfaces | schema_requirement | P0 |
| 追溯管理 | med-rms-traceability | domain + application + infrastructure + interfaces | schema_traceability | P0 |
| 变更管理 | med-rms-change | domain + application + infrastructure + interfaces | schema_change | P0 |
| 合规管理 | med-rms-compliance | shared-infrastructure (audit interceptor) | schema_compliance | P0 |
| 电子签名 | med-rms-esignature | domain + application + infrastructure + interfaces | schema_esignature | P0 |
| 风险管理 | med-rms-risk | domain + application + infrastructure + interfaces | schema_risk | P1 |
| 项目管理 | med-rms-project | domain + application + infrastructure + interfaces | schema_project | P2 |
| 报表与仪表盘 | med-rms-reporting | read-model + query-service | 读取其他Schema | P1 |
| 系统管理 | med-rms-admin | domain + application + infrastructure + interfaces | schema_admin | P0 |
| 公共组件 | med-rms-common | 共享DTO、异常、工具类 | N/A | P0 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增med-rms-esignature模块

### 3.3 模块内部分层 (Clean Architecture)

每个业务模块内部遵循清晰架构分层：

| 层级 | 目录 | 内容 | 依赖方向 |
|-----|------|------|---------|
| interfaces | controller/ | REST Controller、DTO、Request/Response Mapper | → application |
| application | service/ | Application Service、事务管理、用例编排 | → domain |
| domain | model/ + repository/ | 聚合根、实体、值对象、Repository接口、Domain Event | ← 被依赖 |
| infrastructure | persistence/ + messaging/ | Repository实现、MyBatis Mapper、Outbox Event Publisher | → domain (实现接口) |

### 3.4 前端组件架构

前端采用组件化架构，按功能模块组织代码：

| 目录 | 职责 | 核心组件 |
|-----|------|---------|
| src/views/requirement/ | 需求管理页面 | RequirementList、RequirementDetail、ReviewPanel、DecompositionWorkbench |
| src/views/traceability/ | 追溯管理页面 | TraceMatrix、TraceGraph、BreakageAlert |
| src/views/change/ | 变更管理页面 | ChangeRequestForm、ImpactAssessment、ApprovalWorkflow |
| src/views/compliance/ | 合规管理页面 | AuditLogViewer、SignatureDialog、DHFPackageGenerator |
| src/views/esignature/ | 电子签名页面 | SignatureConfirmDialog、SignatureHistoryPanel、SignatureVerificationView |
| src/views/dashboard/ | 仪表盘页面 | MetricsDashboard、ProjectHealth、MultiViewWorkspace |
| src/components/common/ | 通用组件 | RequirementCard、StateMachineBadge、SuspectBadge、AuditTimeline |
| src/stores/ | Pinia状态管理 | requirementStore、userStore、notificationStore |
| src/api/ | API请求层 | requirementApi、traceabilityApi、changeApi、complianceApi、esignatureApi |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增src/views/esignature/和esignatureApi

### 3.5 跨模块通信机制

#### 3.5.1 同步调用场景

跨模块查询通过Application Service直接调用，但严格限制为只读不写：

- 需求详情页查询关联风险列表（requirement → risk）
- 仪表盘查询各模块统计数据（reporting → 各模块）
- 电子签名验证查询签名记录（e-signature → requirement/change）

#### 3.5.2 异步事件驱动场景——事务性发件箱模式

跨模块写操作采用**事务性发件箱模式（Transactional Outbox + Debezium CDC）**，保证业务操作和事件发布的原子性，避免应用崩溃时事件丢失。

**架构方案**：

1. 业务操作和Outbox事件在**同一数据库事务**中写入
2. Debezium Connector监听Outbox表的变更，将事件发布到应用内部事件总线
3. 各模块订阅事件总线，处理跨模块副作用
4. 幂等性保障：每个事件包含唯一event_id，消费者通过去重表确保仅处理一次

**Outbox表设计**：

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| event_id | UUID | UNIQUE, NOT NULL | 事件唯一标识，用于幂等去重 |
| aggregate_type | VARCHAR(100) | NOT NULL | 聚合类型（Requirement/ChangeRequest等） |
| aggregate_id | BIGINT | NOT NULL | 聚合ID |
| event_type | VARCHAR(100) | NOT NULL | 事件类型 |
| payload | JSONB | NOT NULL | 事件载荷 |
| created_at | TIMESTAMP | NOT NULL | 事件创建时间 |
| published | BOOLEAN | DEFAULT FALSE | 是否已发布（CDC使用后标记） |

**核心事件列表**：

| 事件 | 发布者 | 订阅者 | 处理逻辑 |
|-----|-------|-------|---------|
| RequirementChangedEvent | 需求管理 | 变更管理 + 追溯管理 + 合规管理 | 记录变更日志、检测追溯断裂、写审计日志 |
| ChangeApprovedEvent | 变更管理 | 需求管理 + 追溯管理 | 标记suspect、更新追溯关系 |
| ReviewCompletedEvent | 需求管理 | 电子签名 + 合规管理 + 通知服务 | 记录电子签名、写审计日志、发送通知 |
| TraceBrokenEvent | 追溯管理 | 通知服务 + 报表与仪表盘 | 发送断裂预警、更新指标 |
| RiskHighEvent | 风险管理 | 通知服务 + 报表与仪表盘 | 发送高风险预警 |
| SignatureCompletedEvent | 电子签名 | 合规管理 + 需求管理/变更管理 | 写审计签名日志、更新审批状态 |

> **变更记录**：2026-05-22 | 架构师团队 | 重大修订 | 依据A-ORG-02评审意见，将Spring ApplicationEvent替换为事务性发件箱模式（Transactional Outbox + Debezium CDC），新增Outbox表设计、幂等性保障、SignatureCompletedEvent

---

## 第4章 技术栈选型

### 4.1 技术栈总览

| 层级 | 技术 | 版本 | 选型理由 |
|-----|------|------|---------|
| 后端框架 | Spring Boot | 3.3.x | 企业级生态、团队熟悉、强大的自动配置 |
| 语言 | Java | 17 LTS | 长期支持、性能优化、Record/Sealed Class等现代特性 |
| ORM | MyBatis-Plus | 3.5.x | 复杂查询友好、分页内置、较JPA更适合中国团队 |
| 数据库 | PostgreSQL | 16.x | 强ACID、JSONB支持、Schema隔离、丰富的索引类型 |
| 缓存 | Redis | 7.x | Session共享、通知队列、热数据缓存 |
| CDC | Debezium | 2.x | Outbox模式事件发布、可靠CDC、无需外部消息队列 |
| 文件存储 | MinIO | latest | S3兼容、私有化部署、设计文档附件存储 |
| 前端框架 | Vue 3 | 3.5.x | 已确认、Composition API、TypeScript支持 |
| UI组件库 | Element Plus | 2.9.x | 已确认、降低前端开发量、中文文档优秀 |
| 状态管理 | Pinia | 2.x | Vue 3官方推荐、TypeScript友好、模块化 |
| 构建工具 | Vite | 6.x | 极速HMR、原生ESM、优秀的DX |
| API文档 | SpringDoc OpenAPI | 2.6.x | OpenAPI 3.0规范、Swagger UI内置 |
| 数据库迁移 | Flyway | 10.x | 版本控制、SQL原生迁移、私有化友好 |
| 监控 | Spring Boot Actuator + Micrometer | — | 健康检查、Metrics采集、Prometheus对接 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-02评审意见，技术栈新增Debezium 2.x用于事务性发件箱模式的事件发布

### 4.2 数据库选型详解

#### 4.2.1 为什么选择PostgreSQL而非MySQL

① JSONB类型对审计日志的灵活存储至关重要——审计日志需要记录不同实体的字段级变更，JSONB可以存储变化前后的字段快照，无需预定义Schema。

② Schema隔离支持模块化单体——每个限界上下文使用独立Schema，未来拆分微服务时可直接独立数据库。

③ 更强的并发控制——MVCC实现更优雅，读写不互斥，适合审计日志高并发写入场景。

④ 全文检索——内置中文分词支持（通过zhparser扩展），支持需求文本搜索。

⑤ 闭包表(Closure Table)高效支持——PostgreSQL递归查询(CTE)原生支持闭包表的高效追溯路径查询，完美匹配四层需求层级追溯。

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-02评审意见，补充PostgreSQL CTE支持闭包表查询的选型理由

#### 4.2.2 Redis使用场景

- Session共享：支持多实例部署时的会话保持
- 通知队列：基于Redis List实现站内信通知的异步发送
- 热数据缓存：法规条款库、模板配置、用户权限等读多写少数据
- 分布式锁：基线快照生成时的并发控制
- JWT黑名单：登出/强制下线时的令牌失效标记

---

## 第5章 架构决策记录 (ADR)

### ADR-001: 模块化单体优先于微服务

**状态**：已接受 (Accepted)

**背景**：系统面向<200用户，团队规模小（2-3后端+1-2前端），需求模块间存在强事务一致性要求。微服务带来的运维复杂度、分布式事务、网络延迟等问题，在当前阶段得不偿失。

**决策**：采用Spring Modulith构建模块化单体。每个模块拥有独立的数据库Schema、独立的事件通信、明确的API边界。未来可通过拆分Schema为独立数据库实现微服务迁移。

**后果**：
- ✔ 有利：部署简单（单一Jar）、事务简单（本地事务）、开发效率高、运维成本低
- ✖ 代价：模块间无法独立扩展、无法独立部署、故障隔离度低

### ADR-002: 事件驱动处理变更影响传播

**状态**：已修订 (Superseded by ADR-010)

**背景**：变更管理是跨模块枢纽功能：上游需求变更后，下游需求需要自动标记suspect、追溯关系需要自动更新、审计日志需要记录。同步调用导致模块耦合和性能瓶颈。

**原决策**：采用Spring ApplicationEvent + @TransactionalEventListener实现事件驱动。事件在事务提交后发布，保证事件与业务操作的最终一致性。

**修订原因**：Spring ApplicationEvent为进程内事件，应用崩溃时事件丢失；且无法保证业务操作与事件发布的原子性。

**新决策**：采用事务性发件箱模式（详见ADR-010）。

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-ORG-02评审意见，原决策被ADR-010替代

### ADR-003: 审计日志采用追加只写+哈希链模式

**状态**：已修订 (Originally Accepted, Revised)

**背景**：21 CFR Part 11要求审计日志不可删除、不可篡改（"cannot be altered in a way that obscures prior values"）。传统的UPDATE模式无法满足合规要求。

**决策**：审计日志表采用追加只写模式（Append-Only），每次操作产生新的日志记录。关键字段使用PostgreSQL JSONB存储变更前后的字段快照。对应用层披露只读接口。**额外增加哈希链保护和数据库层强制约束**：

1. **哈希链**：每条审计日志记录包含前一条记录的SHA-256哈希值（prev_hash），形成链式结构。任何中间记录的篡改将导致链断裂。
2. **数据库触发器**：在审计日志表上创建触发器，阻止UPDATE和DELETE操作。
3. **完整性校验**：定时任务每日校验哈希链完整性，异常时发送告警。
4. **保留期限**：审计日志至少保留15年（ISO 13485要求+医疗器械产品生命周期）。

**后果**：
- ✔ 有利：完全满足21 CFR Part 11防篡改要求、哈希链提供密码学完整性证明、数据库层强制约束双重保障
- ✖ 代价：写入性能略降（约5%，需计算哈希）、存储空间略增（每条记录多64字节prev_hash+current_hash字段）

> **变更记录**：2026-05-22 | 架构师团队 | 重大修订 | 依据A-RED-03评审意见，从"追加只写"升级为"追加只写+哈希链+数据库触发器+完整性校验"

### ADR-004: 四层需求模型采用闭包表+分层子表

**状态**：已修订 (Originally Accepted, Revised)

**背景**：URS/PRS/SRS/DRS四层需求有大量共享属性（ID、标题、状态、优先级、风险等级等），但每层又有独特字段。原方案采用单表继承(STI)将所有层级存储在同一张表中。

**原决策**：使用单表继承 (Single Table Inheritance) + 类型字段 (requirement_type) 存储四层需求。每层独特字段存储在JSONB扩展字段中。

**修订原因**：单表继承存在严重查询和性能风险：
- 追溯矩阵查询需要多层级自连接（4层），性能随数据量急剧恶化
- 不同层级需求字段约束不同，单表难以用NOT NULL约束
- 跨层级查询需要多跳索引扫描
- 单表膨胀（多产品线10万+行）+ JSONB extended_fields进一步增加行宽
- 四层需求状态机各有差异，单表继承需应用层类型判断分发

**新决策**：采用闭包表（Closure Table）+ 分层子表（Class Table Inheritance）：

1. **公共主表** `t_requirement`：存储所有层级共享字段（id、requirement_no、title、status、priority等）
2. **分层子表**：`t_user_requirement`、`t_product_requirement`、`t_system_requirement`、`t_design_requirement`，各存储层级特有字段，通过FK关联主表
3. **闭包表** `t_requirement_ancestor(descendant_id, ancestor_id, depth)`：存储所有祖先-后代关系，追溯矩阵查询时间复杂度O(1)
4. **消除JSONB extended_fields**：各层级特有字段在子表中以强类型列存储，可建立NOT NULL约束和独立索引

**数据模型示例**：

```
t_requirement (公共主表)
├── id, requirement_no, requirement_type, project_id, title, description,
│   priority, status, risk_level, version, created_by, created_at, ...
│
├── t_user_requirement (URS子表, FK→t_requirement)
│   ├── req_id (FK), regulation_refs, acceptance_criteria, safety_class
│   └── (URS特有字段)
│
├── t_product_requirement (PRS子表, FK→t_requirement)
│   ├── req_id (FK), performance_target, interface_spec_ref
│   └── (PRS特有字段)
│
├── t_system_requirement (SRS子表, FK→t_requirement)
│   ├── req_id (FK), module_name, api_spec, soup_component_id
│   └── (SRS特有字段)
│
├── t_design_requirement (DRS子表, FK→t_requirement)
│   ├── req_id (FK), implementer, code_repo_ref, test_case_ids
│   └── (DRS特有字段)
│
└── t_requirement_ancestor (闭包表)
    ├── descendant_id (FK→t_requirement)
    ├── ancestor_id (FK→t_requirement)
    └── depth (INTEGER, 0=自引用, 1=直接父级, 2=祖父级, ...)
```

**追溯矩阵查询**（利用闭包表）：

```sql
-- 查询某需求的所有祖先（向上追溯）
SELECT r.*, ra.depth
FROM t_requirement_ancestor ra
JOIN t_requirement r ON r.id = ra.ancestor_id
WHERE ra.descendant_id = :reqId AND ra.depth > 0
ORDER BY ra.depth;

-- 查询某需求的所有后代（向下追溯）
SELECT r.*, ra.depth
FROM t_requirement_ancestor ra
JOIN t_requirement r ON r.id = ra.descendant_id
WHERE ra.ancestor_id = :reqId AND ra.depth > 0
ORDER BY ra.depth;

-- 生成完整追溯矩阵（URS→DRS全链路）
SELECT u.requirement_no AS urs, p.requirement_no AS prs,
       s.requirement_no AS srs, d.requirement_no AS drs
FROM t_requirement u
JOIN t_requirement_ancestor ua ON ua.descendant_id = u.id AND ua.depth = 0
JOIN t_requirement_ancestor da ON da.ancestor_id = u.id
JOIN t_requirement d ON d.id = da.descendant_id AND d.requirement_type = 'DRS'
LEFT JOIN t_requirement p ON p.id = (
    SELECT ancestor_id FROM t_requirement_ancestor
    WHERE descendant_id = d.id AND depth = 2
)
LEFT JOIN t_requirement s ON s.id = (
    SELECT ancestor_id FROM t_requirement_ancestor
    WHERE descendant_id = d.id AND depth = 1
)
WHERE u.requirement_type = 'URS';
```

**后果**：
- ✔ 有利：追溯矩阵查询O(1)级（闭包表直接查）、各层级字段强类型约束、独立索引策略、状态机差异化实现简单
- ✖ 代价：写入时需维护闭包表（每插入一条需求需插入depth条祖先记录）、子表JOIN查询略复杂、数据迁移时闭包表需要重建

> **变更记录**：2026-05-22 | 架构师团队 | 重大修订 | 依据A-RED-02评审意见，从单表继承(STI)改为闭包表+分层子表(CTI)

### ADR-005: 仪表盘查询采用CQRS Lite

**状态**：已接受 (Accepted)

**背景**：仪表盘需要聚合多个模块的统计数据（合规率、追溯率、变更趋势等），直接查询业务表会导致性能问题。

**决策**：采用CQRS Lite模式：业务操作后通过事件异步更新统计表（物化视图），仪表盘直接查询统计表。不引入事件溯源，简化实现。

**后果**：
- ✔ 有利：仪表盘响应快（<100ms）、业务写操作不受影响、支持历史趋势查询
- ✖ 代价：统计数据最终一致性（延迟≤5秒）、需维护统计表同步逻辑

### ADR-006: 数据库迁移采用Flyway而非Liquibase

**状态**：已接受 (Accepted)

**背景**：需要版本化管理数据库Schema变更，支持多环境部署（开发/测试/生产）。

**决策**：使用Flyway，SQL原生迁移脚本。团队对SQL熟悉、迁移文件命名清晰、支持可重复迁移。

**后果**：
- ✔ 有利：简单直接、团队熟悉、审计追踪自动记录Schema变更
- ✖ 代价：无法自动回滚（需手动编写回滚脚本）、不支持XML/JSON格式定义

### ADR-007: JWT用于API访问认证，电子签名使用独立认证流程

**状态**：已修订 (Originally Accepted, Revised)

**背景**：需要支持泛微OA单点登录集成，后端可能多实例部署。JWT用于API访问认证，但**不等于**电子签名认证。

**原决策**：RESTful API + JWT无状态认证。JWT包含用户角色和权限，Redis存储JWT黑名单（登出/强制下线）。

**修订决策**：JWT与电子签名分离：

1. **JWT仅用于API访问认证**：
   - Access Token 有效期15分钟，Refresh Token 有效期24小时
   - JWT Payload仅含user_id（角色和权限实时从数据库查询，避免不同步）
   - JWT黑名单同时持久化到数据库表`t_jwt_blacklist`（非仅Redis），确保审计可追溯
   - 刷新Token时轮换Refresh Token（Rotation），防止Token重放

2. **电子签名使用独立认证流程**：
   - 关键操作（审批、确认、签发）弹出签名确认对话框
   - 用户输入签名密码（非登录密码）或动态口令（OTP）
   - 后端独立验证签名密码/OTP，验证通过后记录ElectronicSignature
   - 签名值=SHA-256(签名者ID + 签名含义 + 签名时间 + 被签名文档哈希)
   - 与JWT完全独立，JWT过期不影响已签署的电子签名

3. **泛微OA对接用OAuth2授权码模式**：
   - 泛微OA作为OAuth2 Client，Med-RMS作为Resource Server
   - OA用户点击菜单→重定向到Med-RMS授权页面→用户确认→回调OA携带授权码→OA换取Token
   - 不使用JWT直接传递OA Token（安全风险）

**后果**：
- ✔ 有利：JWT与签名职责分离、签名操作安全等级更高、JWT黑名单可审计、角色权限实时一致
- ✖ 代价：双重认证机制增加开发量、JWT黑名单持久化增加数据库写入

> **变更记录**：2026-05-22 | 架构师团队 | 重大修订 | 依据A-ORG-01评审意见，JWT与电子签名分离，JWT细化（Access/Refresh分离、Payload精简、黑名单持久化），泛微OA改用OAuth2授权码模式

### ADR-008: 21 CFR Part 11电子签名方案

**状态**：已接受 (Accepted)

**背景**：医疗器械软件中，需求的审批、变更的确认、文档的签发等关键操作必须使用符合21 CFR Part 11的电子签名。JWT只能证明"谁发的请求"，不等于"谁做了签名"。

**决策**：引入独立电子签名限界上下文，完整实现21 CFR Part 11电子签名要求：

| Part 11 要求 | 实现方案 |
|-------------|---------|
| 11.50 签署显现 | 签名后显示：签名者姓名、签名含义（APPROVE/REJECT/REVIEW/CONFIRM）、签名日期时间、签名原因 |
| 11.70 签名与记录链接 | 签名记录包含entity_type+entity_id+entity_hash，签名值绑定被签名文档内容哈希，文档篡改→签名验证失败 |
| 11.100 签名唯一性 | 每个签名有全局唯一signature_id，同一人对同一实体同一含义仅可签名一次 |
| 11.200 双组件签名 | 签名时要求二次认证：输入签名密码（非登录密码）+ 动态口令（OTP，P1阶段实现） |
| 11.300 令牌/密码控制 | 签名密码独立于登录密码、密码复杂度策略、定期更换提醒、失败锁定 |

**签名流程**：

1. 用户触发签名操作（点击审批/确认按钮）
2. 前端弹出签名确认对话框，显示签名含义和待签名文档摘要
3. 用户输入签名密码（+ OTP，P1阶段）
4. 后端验证签名密码/OTP
5. 计算签名值：`signature_value = SHA-256(signer_id + meaning + signed_at + entity_hash)`
6. 写入ElectronicSignature记录，同时通过Outbox发布SignatureCompletedEvent
7. 前端显示签署显现信息

**签名值计算公式**：

```
entity_hash = SHA-256(entity_type + entity_id + entity_content_json)
signature_value = SHA-256(signer_id + meaning_code + signed_at_iso8601 + entity_hash)
```

**后果**：
- ✔ 有利：完全满足21 CFR Part 11电子签名要求、签名与JWT独立、签名不可伪造（非对称加密）、签名与文档绑定
- ✖ 代价：签名操作增加一次网络往返、签名密码管理增加开发量、OTP需要额外基础设施（P1阶段）

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增ADR-008完整定义21 CFR Part 11电子签名方案

### ADR-009: 数据库设计策略——层级数据与JSONB选型

**状态**：已接受 (Accepted)

**背景**：四层需求模型(URS/PRS/SRS/DRS)是典型的层级数据，需要高效支持追溯矩阵查询。同时审计日志需要灵活记录不同实体的字段级变更。

**决策**：

1. **层级数据**：采用闭包表(Closure Table)模式（详见ADR-004），而非嵌套集(Nested Set)或物化路径(Materialized Path)
   - 闭包表适合读多写少的需求追溯场景
   - PostgreSQL递归查询(CTE)原生支持闭包表路径查询
   - 写入时维护闭包表开销可接受（需求创建频率低）

2. **JSONB使用边界**：
   - ✅ 审计日志的before_value/after_value：变更前后字段快照，Schema不固定
   - ✅ 变更影响评估的affected_requirements/affected_testcases：动态列表
   - ❌ 需求实体的层级特有字段：使用分层子表的强类型列（详见ADR-004）
   - ❌ 需求的extended_fields：已消除，迁移到分层子表

**后果**：
- ✔ 有利：层级查询高效、JSONB使用场景明确且受控、数据完整性有保障
- ✖ 代价：闭包表维护增加写入逻辑复杂度

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-03评审意见，新增ADR-009

### ADR-010: 事务性发件箱模式替代Spring ApplicationEvent

**状态**：已接受 (Accepted)

**背景**：原ADR-002采用Spring ApplicationEvent实现跨模块事件驱动，但存在以下问题：
- 进程内事件在应用崩溃时丢失
- 无法保证业务操作与事件发布的原子性
- 不支持未来微服务拆分

**决策**：采用事务性发件箱模式（Transactional Outbox + Debezium CDC）：

1. 业务操作和Outbox事件在同一数据库事务中写入
2. Debezium Connector监听Outbox表变更，将事件发布到应用内部事件总线
3. 各模块通过Spring ApplicationEvent订阅处理
4. 每个事件包含唯一event_id，消费者通过去重表确保幂等处理

**为什么不用外部消息队列（RabbitMQ/Kafka）**：
- 当前<200用户、单机部署，引入消息队列增加运维复杂度
- Debezium CDC利用PostgreSQL的逻辑复制，无需额外基础设施
- 未来拆分微服务时，Debezium可直接对接Kafka

**后果**：
- ✔ 有利：业务与事件原子性保障、应用崩溃不丢事件、无需外部消息队列、天然支持微服务拆分
- ✖ 代价：需部署Debezium Connector、Outbox表增加存储、事件延迟略增（CDC轮询间隔）

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-02/A-ORG-03评审意见，新增ADR-010

### ADR-011: 泛微OA集成方案

**状态**：已接受 (Accepted)

**背景**：PRD要求Med-RMS与泛微OA集成（单点登录+审批流对接），需明确技术方案。

**决策**：

1. **单点登录**：采用OAuth2授权码模式
   - Med-RMS作为OAuth2 Resource Server，泛微OA作为Client
   - 用户从OA点击菜单→重定向Med-RMS授权页→用户确认→回调OA携带授权码→OA换取Access Token
   - 不使用SAML（泛微OA对SAML支持有限）和CAS（老旧协议）

2. **审批流对接**：双向API集成
   - Med-RMS变更审批→调用泛微OA审批流API创建审批流程
   - 泛微OA审批完成→回调Med-RMS Webhook更新审批状态
   - 审批结果通过电子签名记录（符合21 CFR Part 11）

3. **组织架构同步**：定时增量同步
   - 每日定时从OA拉取组织架构变更（部门、人员）
   - 增量同步，避免全量数据传输
   - 同步日志记录在审计日志中

**后果**：
- ✔ 有利：标准化OAuth2协议、审批流双向联动、组织架构自动同步
- ✖ 代价：需泛微OA开放API权限、Webhook需内网可达、OAuth2配置需OA管理员配合

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-03评审意见，新增ADR-011

### ADR-012: 数据备份与灾难恢复策略

**状态**：已接受 (Accepted)

**背景**：医疗器械合规系统数据不可丢失，需明确备份和灾难恢复策略。

**决策**：

1. **数据库备份**：
   - 每日全量备份（pg_dump）+ WAL归档 → 支持PITR（时间点恢复）
   - 备份保留策略：日备份7天、周备份4周、月备份12个月
   - 备份目标：本地NAS + 异地NAS（如可用）

2. **灾难恢复**：
   - RTO（恢复时间目标）≤ 30分钟
   - RPO（恢复点目标）≤ 5分钟（WAL归档间隔）
   - PostgreSQL流复制：1主1备，自动故障转移（Patroni，P1阶段部署）
   - 应用层：Docker容器快速拉起，无状态设计

3. **备份验证**：
   - 每月执行一次备份恢复演练（测试环境）
   - 验证备份完整性和恢复时间
   - 演练结果记录在审计日志中

**后果**：
- ✔ 有利：数据安全保障、合规要求满足、PITR支持精确恢复
- ✖ 代价：WAL归档增加存储需求、Patroni增加运维复杂度（P1阶段）

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-03评审意见，新增ADR-012

---

## 第6章 核心数据模型设计

### 6.1 需求实体模型 (requirement)

#### 6.1.1 t_requirement 公共主表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键，自增 |
| requirement_no | VARCHAR(50) | UNIQUE, NOT NULL | 需求编号，如URS-P001-001 |
| requirement_type | VARCHAR(3) | NOT NULL, INDEX | 类型：URS/PRS/SRS/DRS |
| project_id | BIGINT | FK, NOT NULL | 所属项目 |
| title | VARCHAR(200) | NOT NULL | 标题，≤50字 |
| description | TEXT | NOT NULL | 详细描述 |
| priority | VARCHAR(10) | NOT NULL | MUST/SHOULD/COULD |
| status | VARCHAR(20) | NOT NULL, INDEX | 当前状态 |
| risk_level | VARCHAR(10) | DEFAULT 'MEDIUM' | HIGH/MEDIUM/LOW |
| safety_class | VARCHAR(1) | DEFAULT NULL | 软件安全分类：A/B/C（IEC 62304 Clause 4.3） |
| requirement_category | VARCHAR(10) | DEFAULT 'SOFTWARE' | 需求分类：SOFTWARE/HARDWARE/BOTH |
| baseline_id | BIGINT | FK, DEFAULT NULL | 关联基线（基线化后填写） |
| version | INTEGER | NOT NULL DEFAULT 1 | 乐观锁版本号 |
| is_deleted | BOOLEAN | NOT NULL DEFAULT FALSE | 软删除标记 |
| created_by | BIGINT | NOT NULL | 创建人ID |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_by | BIGINT | | 最后修改人 |
| updated_at | TIMESTAMP | | 最后修改时间 |

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-RED-02/A-ORG-05评审意见：(1)移除extended_fields(JSONB)，各层级特有字段迁移到分层子表；(2)新增safety_class字段支持IEC 62304软件安全分类；(3)新增requirement_category字段支持硬件/软件/软硬件分类；(4)新增baseline_id字段支持基线关联；(5)新增is_deleted字段支持软删除

#### 6.1.2 t_user_requirement (URS子表)

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| req_id | BIGINT | PK, FK→t_requirement | 关联主表 |
| regulation_refs | JSONB | | 关联法规条款编号列表 |
| acceptance_criteria | TEXT | | URS验收标准 |
| origin | VARCHAR(20) | DEFAULT 'INTERNAL' | 来源：INTERNAL/REGULATORY/USER |

#### 6.1.3 t_product_requirement (PRS子表)

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| req_id | BIGINT | PK, FK→t_requirement | 关联主表 |
| performance_target | TEXT | | 性能目标描述 |
| interface_spec_ref | VARCHAR(200) | | 接口规格文档引用 |
| verification_method | VARCHAR(20) | | 验证方法：TEST/ANALYSIS/INSPECTION/DEMONSTRATION |

#### 6.1.4 t_system_requirement (SRS子表)

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| req_id | BIGINT | PK, FK→t_requirement | 关联主表 |
| module_name | VARCHAR(100) | | 所属软件模块名 |
| api_spec | TEXT | | API规格描述 |
| soup_component_id | BIGINT | FK, DEFAULT NULL | 关联SOUP组件（如有） |
| test_case_ids | BIGINT[] | | 关联测试用例ID列表 |

#### 6.1.5 t_design_requirement (DRS子表)

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| req_id | BIGINT | PK, FK→t_requirement | 关联主表 |
| implementer | VARCHAR(100) | | 实现负责人 |
| code_repo_ref | VARCHAR(200) | | 代码仓库引用 |
| code_branch | VARCHAR(100) | | 代码分支 |

#### 6.1.6 t_requirement_ancestor 闭包表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| descendant_id | BIGINT | FK→t_requirement, NOT NULL | 后代需求ID |
| ancestor_id | BIGINT | FK→t_requirement, NOT NULL | 祖先需求ID |
| depth | INTEGER | NOT NULL | 层级深度：0=自引用，1=直接父级，2=祖父级... |

**索引**：`UNIQUE(descendant_id, ancestor_id)`, `INDEX(ancestor_id)`, `INDEX(descendant_id, depth)`

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-02评审意见，新增闭包表支持O(1)级追溯矩阵查询

#### 6.1.7 t_requirement_relation 横向关联表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| source_req_id | BIGINT | FK, NOT NULL, INDEX | 源需求ID |
| target_req_id | BIGINT | FK, NOT NULL, INDEX | 目标需求ID |
| relation_type | VARCHAR(20) | NOT NULL | HORIZONTAL |
| horizontal_type | VARCHAR(20) | | DEPENDS/CONFLICTS/REUSES |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

#### 6.1.8 t_requirement_version 版本历史表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| requirement_id | BIGINT | FK, NOT NULL, INDEX | 关联需求 |
| version_no | INTEGER | NOT NULL | 版本号 |
| snapshot | JSONB | NOT NULL | 该版本需求完整快照 |
| change_summary | VARCHAR(500) | | 变更摘要 |
| changed_by | BIGINT | NOT NULL | 变更人 |
| changed_at | TIMESTAMP | NOT NULL | 变更时间 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05评审意见，新增版本历史表支持需求变更全版本追踪

#### 6.1.9 t_test_case 测试用例表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| test_case_no | VARCHAR(50) | UNIQUE, NOT NULL | 测试用例编号 |
| title | VARCHAR(200) | NOT NULL | 测试用例标题 |
| description | TEXT | | 详细描述 |
| test_type | VARCHAR(20) | NOT NULL | UNIT/INTEGRATION/SYSTEM/ACCEPTANCE |
| safety_class | VARCHAR(1) | | 关联软件安全分类 |
| pre_condition | TEXT | | 前置条件 |
| test_steps | JSONB | NOT NULL | 测试步骤（步骤列表） |
| expected_result | TEXT | NOT NULL | 预期结果 |
| status | VARCHAR(20) | NOT NULL | DRAFT/ACTIVE/OBSOLETE |
| project_id | BIGINT | FK, NOT NULL | 所属项目 |
| created_by | BIGINT | NOT NULL | 创建人 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05评审意见，新增测试用例实体支持横向追溯（需求→测试用例）

#### 6.1.10 t_requirement_testcase 需求-测试用例关联表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| requirement_id | BIGINT | FK, NOT NULL | 关联需求 |
| test_case_id | BIGINT | FK, NOT NULL | 关联测试用例 |
| trace_type | VARCHAR(20) | NOT NULL | VERIFICATION/VALIDATION |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05评审意见，新增需求-测试用例关联表

### 6.2 变更实体模型 (change_mgmt)

#### 6.2.1 t_change_request 变更申请表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| change_no | VARCHAR(50) | UNIQUE | CR-{PROJECT}-{SEQ} |
| requirement_id | BIGINT | FK, NOT NULL | 变更的需求ID |
| change_type | VARCHAR(20) | NOT NULL | CORRECTIVE/ADAPTIVE/PERFECTIVE/EMERGENCY |
| reason | TEXT | NOT NULL | 变更原因 |
| before_snapshot | JSONB | NOT NULL | 变更前需求快照 |
| after_snapshot | JSONB | | 变更后需求快照（执行后填写） |
| status | VARCHAR(20) | NOT NULL | SUBMITTED/ASSESSING/APPROVED/EXECUTING/VERIFIED/CLOSED |
| urgency | VARCHAR(10) | NOT NULL | NORMAL/URGENT |
| requested_by | BIGINT | NOT NULL | 申请人 |
| requested_at | TIMESTAMP | NOT NULL | 申请时间 |

#### 6.2.2 t_impact_assessment 影响评估表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| change_id | BIGINT | FK, NOT NULL | 关联变更申请 |
| affected_requirements | JSONB | | 受影响的下游需求ID列表 |
| affected_testcases | JSONB | | 受影响的测试用例ID列表 |
| regulation_impact | JSONB | | 受影响的法规条款列表 |
| impact_ratio | DECIMAL(5,2) | | 影响范围百分比 |
| assessed_at | TIMESTAMP | | 评估时间 |

### 6.3 审计日志实体模型 (compliance)

#### 6.3.1 t_audit_log 审计日志表（追加只写+哈希链）

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| prev_hash | CHAR(64) | NOT NULL | 前一条记录的SHA-256哈希值（首条记录为全0） |
| current_hash | CHAR(64) | NOT NULL | 本条记录的SHA-256哈希值 |
| event_type | VARCHAR(50) | NOT NULL, INDEX | CREATE/MODIFY/DELETE/STATUS_CHANGE/SIGN/LOGIN |
| entity_type | VARCHAR(50) | NOT NULL | 实体类型：Requirement/ChangeRequest/ElectronicSignature/... |
| entity_id | BIGINT | NOT NULL, INDEX | 实体ID |
| operator_id | BIGINT | NOT NULL, INDEX | 操作人ID |
| operator_name | VARCHAR(100) | | 操作人姓名（冗余便于查询） |
| operation | VARCHAR(100) | NOT NULL | 操作描述 |
| before_value | JSONB | | 变更前值 |
| after_value | JSONB | | 变更后值 |
| reason | VARCHAR(500) | | 操作原因（变更/签名时必填） |
| ip_address | VARCHAR(45) | | 操作IP地址 |
| created_at | TIMESTAMP | NOT NULL, INDEX | 日志创建时间 |

**数据库强制保护**：

```sql
-- 触发器1：阻止UPDATE操作
CREATE OR REPLACE FUNCTION fn_prevent_audit_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'AUDIT_LOG_UPDATE_PROHIBITED: Audit log records cannot be updated';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_update
BEFORE UPDATE ON t_audit_log
FOR EACH ROW EXECUTE FUNCTION fn_prevent_audit_update();

-- 触发器2：阻止DELETE操作
CREATE OR REPLACE FUNCTION fn_prevent_audit_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'AUDIT_LOG_DELETE_PROHIBITED: Audit log records cannot be deleted';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_delete
BEFORE DELETE ON t_audit_log
FOR EACH ROW EXECUTE FUNCTION fn_prevent_audit_delete();
```

**哈希链完整性校验**（每日定时任务）：

```sql
-- 校验哈希链完整性
SELECT a.id, a.current_hash, b.prev_hash
FROM t_audit_log a
JOIN t_audit_log b ON b.id = a.id + 1
WHERE b.prev_hash != a.current_hash;
-- 结果为空=完整性正常，有记录=链断裂
```

**保留期限**：审计日志至少保留15年（ISO 13485要求+医疗器械产品生命周期）。

> **变更记录**：2026-05-22 | 架构师团队 | 重大修订 | 依据A-RED-03评审意见：(1)新增prev_hash/current_hash字段实现哈希链；(2)新增reason字段；(3)新增数据库触发器阻止UPDATE/DELETE；(4)新增哈希链完整性校验SQL；(5)保留期限从"未明确"改为"至少15年"

### 6.4 电子签名实体模型 (e-signature)

#### 6.4.1 t_electronic_signature 电子签名表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| signature_id | UUID | UNIQUE, NOT NULL | 签名唯一标识 |
| signer_id | BIGINT | NOT NULL, INDEX | 签名者用户ID |
| signer_name | VARCHAR(100) | NOT NULL | 签名者姓名（冗余） |
| meaning_code | VARCHAR(20) | NOT NULL | 签名含义：APPROVE/REJECT/REVIEW/CONFIRM |
| meaning_display | VARCHAR(50) | NOT NULL | 签名含义显示文本 |
| signed_at | TIMESTAMP | NOT NULL | 签名时间（可信时间源） |
| signature_value | CHAR(64) | NOT NULL | 签名值：SHA-256(signer_id + meaning + signed_at + entity_hash) |
| entity_type | VARCHAR(50) | NOT NULL | 被签名实体类型 |
| entity_id | BIGINT | NOT NULL, INDEX | 被签名实体ID |
| entity_hash | CHAR(64) | NOT NULL | 被签名实体内容哈希 |
| reason | VARCHAR(500) | | 签名原因 |
| auth_method | VARCHAR(20) | NOT NULL | 认证方式：PASSWORD/OTP |
| ip_address | VARCHAR(45) | | 签名IP地址 |
| is_valid | BOOLEAN | NOT NULL DEFAULT TRUE | 签名是否有效（被撤回时为FALSE） |

**唯一约束**：`UNIQUE(entity_type, entity_id, signer_id, meaning_code)` — 同一人对同一实体同一含义仅可签名一次

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01评审意见，新增电子签名数据模型，完整实现21 CFR Part 11电子签名要求

#### 6.4.2 t_signature_verification 签名验证记录表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| signature_id | UUID | FK, NOT NULL | 关联电子签名 |
| verifier_id | BIGINT | NOT NULL | 验证人ID |
| verification_result | VARCHAR(20) | NOT NULL | VALID/INVALID/TAMPERED |
| verification_at | TIMESTAMP | NOT NULL | 验证时间 |
| detail | TEXT | | 验证详情 |

### 6.5 SOUP管理实体模型

#### 6.5.1 t_soup_component SOUP组件登记表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| component_name | VARCHAR(200) | NOT NULL | 组件名称 |
| supplier | VARCHAR(200) | NOT NULL | 供应商 |
| version | VARCHAR(50) | NOT NULL | 版本号 |
| license_type | VARCHAR(50) | | 开源协议类型 |
| known_anomalies | TEXT | | 已知异常列表 |
| safety_impact | VARCHAR(10) | | 安全影响评估：HIGH/MEDIUM/LOW/NA |
| project_id | BIGINT | FK, NOT NULL | 所属项目 |
| last_reviewed_at | TIMESTAMP | | 最近审查时间 |
| created_by | BIGINT | NOT NULL | 创建人 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05/P-ORG-01评审意见，新增SOUP管理数据模型（IEC 62304 Clause 5.3.3/7.1.2/8.1.2）

### 6.6 基线实体模型

#### 6.6.1 t_baseline 基线表

| 字段名 | 类型 | 约束 | 说明 |
|-------|------|------|------|
| id | BIGSERIAL | PK | 主键 |
| baseline_no | VARCHAR(50) | UNIQUE, NOT NULL | 基线编号 |
| baseline_type | VARCHAR(20) | NOT NULL | 功能基线/分配基线/产品基线 |
| project_id | BIGINT | FK, NOT NULL | 所属项目 |
| description | VARCHAR(500) | | 基线描述 |
| status | VARCHAR(20) | NOT NULL | DRAFT/APPROVED/LOCKED |
| snapshot_count | INTEGER | NOT NULL DEFAULT 0 | 包含需求快照数量 |
| created_by | BIGINT | NOT NULL | 创建人 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| approved_by | BIGINT | | 审批人 |
| approved_at | TIMESTAMP | | 审批时间 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05评审意见，新增基线数据模型支持基线管理（IEC 62304 Clause 8）

---

## 第7章 API设计规范

### 7.1 RESTful API设计原则

① URL结构：/api/v1/{module}/{resource}[/{id}][/{sub-resource}]

② HTTP方法语义：GET(查询)、POST(创建)、PUT(全量更新)、PATCH(部分更新)、DELETE(逻辑删除)

③ 统一响应格式：{ "code": 200, "message": "success", "data": {...}, "timestamp": 1716364800000 }

④ 分页规范：?page=0&size=20&sort=createdAt,desc

⑤ 版本化：URL路径包含版本号 /api/v1/

⑥ 合规操作必须包含reason字段：变更请求、电子签名、状态变更等操作必须附带操作原因

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01/A-RED-03评审意见，新增第⑥条合规操作reason要求

### 7.2 核心API列表

#### 7.2.1 需求管理API

| 模块 | 方法 | 路径 | 说明 | 权限 |
|-----|------|------|------|------|
| 需求管理 | GET | /api/v1/requirement/requirements | 分页查询需求列表 | 所有登录用户 |
| 需求管理 | POST | /api/v1/requirement/requirements | 创建需求 | 产品经理/架构师 |
| 需求管理 | GET | /api/v1/requirement/requirements/{id} | 查询需求详情 | 所有登录用户 |
| 需求管理 | PUT | /api/v1/requirement/requirements/{id} | 更新需求 | 需求创建人/管理员 |
| 需求管理 | POST | /api/v1/requirement/requirements/{id}/review | 发起评审 | 需求创建人 |
| 需求管理 | POST | /api/v1/requirement/requirements/{id}/decompose | 拆解为下层需求 | 产品经理/架构师 |

#### 7.2.2 追溯管理API

| 模块 | 方法 | 路径 | 说明 | 权限 |
|-----|------|------|------|------|
| 追溯管理 | GET | /api/v1/traceability/matrix | 生成追溯矩阵 | 所有登录用户 |
| 追溯管理 | GET | /api/v1/traceability/breakages | 查询追溯断裂列表 | 项目经理/质量工程师 |
| 追溯管理 | GET | /api/v1/traceability/coverage | 追溯覆盖率统计 | 项目经理/质量工程师 |
| 追溯管理 | GET | /api/v1/traceability/gaps | 追溯缺口分析 | 质量工程师/合规专员 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-04评审意见，新增追溯覆盖率和缺口分析API

#### 7.2.3 变更管理API

| 模块 | 方法 | 路径 | 说明 | 权限 |
|-----|------|------|------|------|
| 变更管理 | POST | /api/v1/change/change-requests | 发起变更申请 | 需求负责人 |
| 变更管理 | POST | /api/v1/change/change-requests/{id}/assess | 执行影响评估 | 系统自动 |
| 变更管理 | POST | /api/v1/change/change-requests/{id}/approve | 审批变更 | 项目经理/研发总监 |
| 变更管理 | GET | /api/v1/change/change-requests/{id}/impact | 变更影响分析 | 所有登录用户 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-04评审意见，新增变更影响分析API

#### 7.2.4 合规管理API

| 模块 | 方法 | 路径 | 说明 | 权限 |
|-----|------|------|------|------|
| 合规管理 | GET | /api/v1/compliance/audit-logs | 查询审计日志 | 合规专员/质量工程师 |
| 合规管理 | POST | /api/v1/compliance/audit-logs/verify | 审计日志哈希链完整性校验 | 系统管理员 |
| 合规管理 | GET | /api/v1/compliance/audit-logs/export | 导出审计日志 | 合规专员/质量工程师 |
| 合规管理 | POST | /api/v1/compliance/evidence-packages | 生成DHF证据包 | 合规专员 |
| 合规管理 | GET | /api/v1/compliance/reports/traceability | 追溯性报告 | 合规专员/质量工程师 |
| 合规管理 | GET | /api/v1/compliance/reports/audit-trail | 审计追踪报告 | 合规专员/质量工程师 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-04评审意见，新增审计日志校验/导出API、合规报告API

#### 7.2.5 电子签名API

| 模块 | 方法 | 路径 | 说明 | 权限 |
|-----|------|------|------|------|
| 电子签名 | POST | /api/v1/esignature/sign | 执行电子签名 | 所有用户（关键操作时） |
| 电子签名 | POST | /api/v1/esignature/verify | 验证电子签名 | 所有登录用户 |
| 电子签名 | GET | /api/v1/esignature/signatures | 查询签名记录 | 合规专员/质量工程师 |
| 电子签名 | GET | /api/v1/esignature/signatures/{id} | 查询签名详情 | 合规专员/质量工程师 |
| 电子签名 | POST | /api/v1/esignature/signatures/{id}/re-sign | 重签（签名失效后） | 原签名者 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-04/A-RED-01评审意见，新增5个电子签名API端点

#### 7.2.6 SOUP管理API

| 模块 | 方法 | 路径 | 说明 | 权限 |
|-----|------|------|------|------|
| 需求管理 | GET | /api/v1/requirement/soup-components | 查询SOUP组件列表 | 所有登录用户 |
| 需求管理 | POST | /api/v1/requirement/soup-components | 登记SOUP组件 | 产品经理/架构师 |
| 需求管理 | PUT | /api/v1/requirement/soup-components/{id} | 更新SOUP组件信息 | 产品经理/架构师 |
| 需求管理 | GET | /api/v1/requirement/soup-components/{id}/anomalies | 查询SOUP已知异常 | 所有登录用户 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-05/P-ORG-01评审意见，新增SOUP管理API

---

## 第8章 安全架构

### 8.1 身份认证与访问控制

#### 8.1.1 认证流程

**常规API访问认证**：

1. 用户通过泛微OA单点登录进入系统（OAuth2授权码模式）
2. 后端验证OA传递的授权码，换取用户信息，生成本系统JWT
3. JWT仅包含：userId、exp（角色和权限实时从数据库查询，避免不同步）
4. 每次API请求携带Authorization: Bearer {jwt}
5. Access Token有效期15分钟，Refresh Token有效期24小时
6. JWT黑名单同时写入Redis和数据库表t_jwt_blacklist，确保审计可追溯

**电子签名认证**（独立于JWT）：

1. 关键操作触发签名确认对话框
2. 用户输入签名密码（独立于登录密码）
3. 后端验证签名密码（P1阶段增加OTP动态口令）
4. 验证通过后记录ElectronicSignature（包含签名值、签名含义、签名时间、被签名文档哈希）
5. 签名值不可伪造、与被签名文档绑定

> **变更记录**：2026-05-22 | 架构师团队 | 重大修订 | 依据A-ORG-01/A-RED-01评审意见，将认证流程拆分为"API访问认证"和"电子签名认证"两个独立流程

#### 8.1.2 RBAC角色权限模型

按PRD定义的8类角色，权限采用RBAC模型：User → Role → Permission → Resource

权限校验采用Spring Security Method Security注解：

```java
@PreAuthorize("hasRole('PRODUCT_MANAGER') and hasPermission(#projectId, 'REQUIREMENT_CREATE')")
```

#### 8.1.3 21 CFR Part 11合规矩阵

| Part 11 条款 | 要求 | 实现方案 | 验证方式 |
|-------------|------|---------|---------|
| 11.10(a) | 系统验证 | CSV验证计划，占开发工期20-30% | IQ/OQ/PQ验证协议 |
| 11.10(e) | 审计追踪 | 追加只写+哈希链+数据库触发器 | 哈希链完整性校验 |
| 11.10(f) | 操作序列检查 | 审批前必须有评审记录，评审前必须有需求创建 | 应用层状态机前置检查 |
| 11.50 | 签署显现 | 签名后显示签名者、含义、时间 | 签名详情页面 |
| 11.70 | 签名与记录链接 | entity_type+entity_id+entity_hash绑定 | 签名验证API |
| 11.100 | 签名唯一性 | UNIQUE约束(entity_type, entity_id, signer_id, meaning_code) | 数据库约束+应用层校验 |
| 11.200 | 双组件签名 | 签名密码+OTP（P1阶段） | 二次认证对话框 |
| 11.300 | 令牌/密码控制 | 签名密码独立策略、定期更换、失败锁定 | 密码策略配置 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-RED-01/P-ORG-10评审意见，新增21 CFR Part 11合规矩阵

### 8.2 数据安全

#### 8.2.1 传输安全

- 生产环境强制HTTPS
- 对外API采用API Key + HMAC签名验证
- 敏感数据加密传输（密码使用BCrypt加盐哈希）

#### 8.2.2 存储安全

- 审计日志表通过数据库触发器强制限制为INSERT+SELECT只读，阻止UPDATE和DELETE
- 审计日志哈希链提供密码学完整性证明
- 需求关键字段通过应用层控制不可物理删除（软删除，is_deleted标记）
- 数据库连接使用加密连接字符串，凭证通过环境变量注入
- 电子签名密码独立存储，加盐哈希，与登录密码分离

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-RED-03/A-ORG-01评审意见，补充审计日志触发器强制保护、哈希链、软删除、签名密码分离

### 8.3 常见安全防护

- XSS防护：前端输出编码，后端输入过滤（Spring HtmlUtils）
- CSRF防护：JWT无状态认证天然抵御（不依赖Cookie）
- SQL注入防护：MyBatis-Plus参数化查询，禁止字符串拼接SQL
- 限流防护：网关层实现基于IP和用户的请求限流
- 敏感操作确认：电子签名对话框二次认证关键操作（独立于JWT）
- 操作序列强制检查：审批前必须有评审记录，评审前需求必须处于正确状态

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据P-ORG-10评审意见，新增操作序列强制检查

---

## 第9章 部署架构

### 9.1 部署拓扑图

基于纯私有化本地部署要求，采用单机主备方案：

| 组件 | 配置 | 说明 |
|------|------|------|
| Nginx | 1台，反向代理+静态资源 | 网关层，TLS终止，静态文件服务 |
| Med-RMS Backend | 1台主 + 1台备（可选） | Spring Boot Fat Jar，内网部署，支持多实例 |
| PostgreSQL | 1台主 + 1台备（流复制） | 数据库主节点，备节点只读 |
| Debezium | 1个Connector实例 | 监听Outbox表，发布事件到应用总线 |
| Redis | 1台 | Session + 缓存 + 通知队列 + JWT黑名单 |
| MinIO | 1台 | 附件存储，挂载本地磁盘 |
| 备份 | Cron任务 + NAS | 每日全量备份+WAL归档，保留30天 |

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-ORG-02评审意见，部署拓扑新增Debezium Connector实例

### 9.2 环境规划

| 环境 | 用途 | 数据库 | 部署方式 |
|------|------|--------|---------|
| dev | 开发环境 | 独立PostgreSQL实例 | 本地/Docker |
| test | 测试环境 | 独立PostgreSQL实例 | 内网服务器 |
| staging | 预发布环境 | 生产同步数据集 | 内网服务器 |
| prod | 生产环境 | 主备方案 | 内网服务器 |

### 9.3 容器化部署 (Docker Compose)

推荐使用Docker Compose进行一键部署，主要服务包括：

1. med-rms-backend：Spring Boot应用，暴露：8080
2. med-rms-frontend：Nginx + Vue 3静态文件，暴露：80/443
3. postgres：PostgreSQL 16，暴露：5432
4. redis：Redis 7，暴露：6379
5. minio：MinIO，暴露：9000/9001
6. debezium：Debezium Connector，监听PostgreSQL Outbox表

后端通过环境变量配置数据库连接，支持一键启动：docker compose up -d

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据A-ORG-02评审意见，Docker Compose新增Debezium Connector服务

---

## 第10章 架构演进路线图

### 10.1 分阶段架构演进概述

架构演进与PRD分阶段交付计划保持一致，分为P0、P1、P2三个阶段，每个阶段的架构变化尽量向前兼容。

| 阶段 | 交付内容 | 架构重点 | 关键决策 |
|------|---------|---------|---------|
| P0 | 基本型需求 (116人天) | 模块化单体核心、审计追踪基座+哈希链、四层需求模型(闭包表+分层子表)、变更闭环(事务性发件箱)、电子签名基座 | 闭包表+CTI、追加只写+哈希链审计日志、事务性发件箱、21 CFR Part 11电子签名 |
| P1 | 期望型需求 (98人天) | 可视化拆解工作台、风险管理模块、泛微OA集成(OAuth2)、CQRS Lite、OTP动态口令 | 引入统计表、OAuth2授权码模式、WebSocket通知、Patroni主备 |
| P2 | 兴奋型需求 (81人天) | 项目管理模块、AI辅助、可视化追溯图谱、完整报表体系 | D3.js图谱、Drools规则引擎、异步任务调度 |

> **变更记录**：2026-05-22 | 架构师团队 | 修订 | 依据P-ORG-04评审意见，P0工期从80人天调整为116人天，P1从73人天调整为98人天；架构重点和关键决策更新为修订后方案

### 10.2 技术债管理策略

- 每个Sprint结束后预留半天重构时间，处理积累的技术债
- 关键技术债项：API版本化策略、数据库查询优化、单元测试覆盖率
- 架构评审：每个阶段结束后进行一次架构评审，检查模块边界是否完好
- 重构触发器：同一模块内重复代码超过3次→抽取公共组件；跨模块直接访问数据库→通过API或事件
- 每个ADR记录技术债和已知限制

### 10.3 演进到微服务的前置条件

当以下条件同时满足时，可考虑从模块化单体拆分为微服务：

1. 用户规模超过500人，单机无法满足性能要求
2. 团队规模扩大到6人以上，需要独立迭代速度
3. 某个模块需要独立扩展（如审计日志写入压力大）
4. 需要独立部署节奏（如报表模块可独立升级）

拆分路径：首先独立数据库→ 抽取为独立服务→ 引入API网关路由→ 最终微服务架构。

事务性发件箱模式天然支持微服务拆分：Debezium可直接对接Kafka替代应用内事件总线，无需修改业务代码。

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据A-ORG-02评审意见，补充事务性发件箱对微服务拆分的天然支持说明

---

## 第11章 附录

### 11.1 术语表

| 术语 | 全称 | 说明 |
|------|------|------|
| DDD | Domain-Driven Design | 领域驱动设计 |
| CQRS | Command Query Responsibility Segregation | 命令查询职责分离 |
| ADR | Architecture Decision Record | 架构决策记录 |
| RBAC | Role-Based Access Control | 基于角色的访问控制 |
| JWT | JSON Web Token | JSON Web令牌 |
| SSO | Single Sign-On | 单点登录 |
| SPA | Single Page Application | 单页应用 |
| ORM | Object-Relational Mapping | 对象关系映射 |
| DHF | Design History File | 设计历史文档 |
| FMEA | Failure Mode and Effects Analysis | 失效模式与影响分析 |
| STI | Single Table Inheritance | 单表继承 |
| CTI | Class Table Inheritance | 类表继承（分层子表） |
| CDC | Change Data Capture | 变更数据捕获 |
| OTP | One-Time Password | 一次性密码/动态口令 |
| SOUP | Software of Unknown Provenance | 未知来源软件 |
| CSV | Computer System Validation | 计算机系统验证 |
| PITR | Point-In-Time Recovery | 时间点恢复 |

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 依据评审意见，新增STI、CTI、CDC、OTP、SOUP、CSV、PITR术语

### 11.2 参考文献

1. ISO 13485:2016 - 医疗器械质量管理体系
2. IEC 62304:2006+A1:2015 - 医疗器械软件生命周期过程
3. 21 CFR Part 11 - 电子记录与电子签名
4. Eric Evans, Domain-Driven Design (2003)
5. Sam Newman, Building Microservices (2nd Edition, 2021)
6. Spring Modulith Reference Documentation
7. Vue 3 Composition API Documentation
8. Debezium Documentation - Outbox Pattern
9. PostgreSQL CTE (Common Table Expressions) Documentation
10. The Transactional Outbox Pattern - Microservices.io

> **变更记录**：2026-05-22 | 架构师团队 | 新增 | 新增Debezium Outbox、PostgreSQL CTE、Transactional Outbox Pattern参考文献

### 11.3 文档变更履历

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|---------|
| v1.0 | 2026-05-22 | 软件架构师团队 | 初版发布，完整架构设计文档 |
| v1.1 | 2026-05-22 | 软件架构师团队 | 基于2026-05-22评审报告修订，详见下方详细变更履历 |
| v1.2 | 2026-06-01 | Claude | 明确变更管理前置条件：已基线化需求才能发起变更，未基线化需求直接编辑 |
| v1.3 | 2026-06-01 | Claude | 测试用例新增project_id字段及按项目筛选API，需求-测试用例双重追溯模式（需求↔用例+项目维度） |

### 11.4 详细变更履历

| # | 评审编号 | 变更章节 | 变更类型 | 变更说明 |
|---|---------|---------|---------|---------|
| 1 | A-RED-01 | §2.1, §2.2, §2.3.4, §3.2, §3.4, §5(ADR-008), §6.4, §7.2.5, §8.1, §8.1.3 | 新增/重大修订 | 新增"电子签名"限界上下文、聚合根、模块、前端组件、ADR-008、数据模型、5个API、认证流程、21 CFR Part 11合规矩阵 |
| 2 | A-RED-02 | §2.3.1, §3.1, §4.2.1, §5(ADR-004), §6.1 | 重大修订 | 单表继承(STI)改为闭包表+分层子表(CTI)，新增4个子表、闭包表、版本历史表，移除extended_fields |
| 3 | A-RED-03 | §1.2, §2.3.3, §5(ADR-003), §6.3.1, §8.2.2 | 重大修订 | 审计日志新增哈希链(prev_hash/current_hash)、数据库触发器、完整性校验、保留15年 |
| 4 | A-ORG-01 | §3.5.2, §5(ADR-007), §8.1.1 | 重大修订 | JWT与电子签名分离，JWT细化(Access 15min+Refresh 24h+Payload精简+黑名单持久化)，泛微OA改用OAuth2授权码模式 |
| 5 | A-ORG-02 | §1.2, §3.1, §3.5.2, §4.1, §5(ADR-002→ADR-010), §9.1, §9.3, §10.3 | 重大修订 | Spring ApplicationEvent改为事务性发件箱(Transactional Outbox + Debezium CDC)，新增Outbox表、Debezium组件 |
| 6 | A-ORG-03 | §5(ADR-008~ADR-012) | 新增 | 新增5个ADR：电子签名方案、数据库设计策略、事务性发件箱、泛微OA集成、数据备份与灾难恢复 |
| 7 | A-ORG-04 | §7.2.2~7.2.6 | 新增 | 新增5类关键API：电子签名(5个)、审计日志(校验/导出)、追溯(覆盖率/缺口)、变更影响分析、合规报告、SOUP管理 |
| 8 | A-ORG-05 | §2.3.1, §6.1.1, §6.1.8~6.1.10, §6.5, §6.6 | 新增 | PRD-架构对齐：新增baseline_id字段、is_deleted软删除、RequirementVersion版本历史表、TestCase实体、SOUP管理、基线管理 |
| 9 | P-ORG-01 | §6.5, §7.2.6 | 新增 | SOUP管理数据模型和API（IEC 62304 Clause 5.3.3/7.1.2/8.1.2） |
| 10 | P-ORG-04 | §1.1, §10.1 | 修订 | P0工期从80人天→116人天，P1从73人天→98人天 |
| 11 | P-ORG-10 | §7.1, §8.1.3, §8.3 | 新增/修订 | 新增21 CFR Part 11合规矩阵、操作序列强制检查、reason字段要求 |
| 12 | — | §7.4 | 澄清 | 变更管理前置条件：已基线化需求才能发起变更，未基线化需求直接编辑后重新评审 |

---

*本文档由软件架构师团队编写，基于2026-05-22评审报告进行v1.1修订。*

*文档版本：v1.1（2026-05-22）*
