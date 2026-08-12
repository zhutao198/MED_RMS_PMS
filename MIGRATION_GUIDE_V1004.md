# V1004 数据库迁移部署指南（2026-08-10）

> 关联代码修复：P1-4（21 CFR Part 11 §11.10(c) 防物理删除）
> 关联报告：`CODE_REVIEW_FULL_VERIFICATION_2026-08-10.md`
> Flyway 版本：V1004（位于 `med-rms-web/src/main/resources/db/migration/`）

---

## 迁移内容

为 4 个核心业务表添加 `is_deleted` 列 + DELETE 阻止触发器：

| 表 | Schema | 业务说明 |
|---|---|---|
| `t_task_predecessor` | prj_schema | 甘特图任务前置依赖 |
| `t_worklog` | prj_schema | 工时记录（含 PII / 财务核算） |
| `t_change_attachment` | chg_schema | 变更附件（含合规证据） |
| `statistics_snapshot` | report_schema | CQRS 读模型快照 |

## 触发器效果

每个表安装 `trg_v1004_prevent_hard_delete` 触发器（BEFORE DELETE → RAISE EXCEPTION）。

任何 `DELETE FROM <table>` 在 DB 层被立即拒绝：
```
ERROR: 禁止硬删除 (21 CFR Part 11 §11.10(c)): 请使用软删除 (UPDATE is_deleted=true)
HINT: 表 prj_schema.t_worklog 设置了 DELETE 触发器
```

业务层应改用：
```sql
UPDATE t_worklog SET is_deleted = true WHERE id = ?;
```

**MyBatis-Plus `@TableLogic` 注解会自动处理**：`mapper.deleteById(id)` 会被翻译为 `UPDATE ... WHERE id=? AND is_deleted=false`。本迁移执行后所有现有 `@TableLogic` 字段立即生效。

---

## 部署步骤

### 1. 备份（强制）

```bash
# 使用新修订的 backup.sh（已移除硬编码 PGPASSWORD）
export PGPASSWORD=<运维密码>
export DB_USER=postgres
./ops/backup.sh
```

### 2. 验证 Flyway 启动顺序

启动后端前，确认 Flyway 迁移日志：

```
[INFO] Flyway Community Edition 9.x.x by Redgate
[INFO] Database: jdbc:postgresql://localhost:5432/med_rms_pms (PostgreSQL 16.x)
[INFO] Successfully validated 5 migrations (execution time 00:00.015s)
[INFO] Current version of schema "public": 1003
[INFO] Migrating schema "public" to version "1004 - part11 soft delete"
[INFO] Successfully applied 1 migration to schema "public" (execution time 00:00.xxxs)
```

### 3. 验证迁移结果

执行以下查询确认 4 个表都已加列：

```sql
SELECT table_schema, table_name, column_name, data_type, column_default
FROM information_schema.columns
WHERE column_name = 'is_deleted'
  AND table_name IN (
    't_task_predecessor',
    't_worklog',
    't_change_attachment',
    'statistics_snapshot'
  )
ORDER BY table_schema, table_name;
```

预期返回 **4 行**。

验证触发器安装：

```sql
SELECT trigger_name, event_object_schema, event_object_table
FROM information_schema.triggers
WHERE trigger_name = 'trg_v1004_prevent_hard_delete'
ORDER BY event_object_schema, event_object_table;
```

预期返回 **4 行**。

### 4. 验证 DELETE 被阻止

```sql
-- 应该报错
DELETE FROM prj_schema.t_worklog WHERE id = 1;
-- ERROR: 禁止硬删除 (21 CFR Part 11 §11.10(c))

-- 应该成功
UPDATE prj_schema.t_worklog SET is_deleted = true WHERE id = 1;
-- UPDATE 1
```

### 5. 应用健康检查

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## 回滚方案

如果迁移后出现不可解决的回归（不应发生，因为仅添加列和触发器）：

```sql
-- 1. 删除触发器
DROP TRIGGER IF EXISTS trg_v1004_prevent_hard_delete ON prj_schema.t_task_predecessor;
DROP TRIGGER IF EXISTS trg_v1004_prevent_hard_delete ON prj_schema.t_worklog;
DROP TRIGGER IF EXISTS trg_v1004_prevent_hard_delete ON chg_schema.t_change_attachment;
DROP TRIGGER IF EXISTS trg_v1004_prevent_hard_delete ON report_schema.statistics_snapshot;

-- 2. 删除列（不推荐！会丢失软删除状态）
ALTER TABLE prj_schema.t_task_predecessor DROP COLUMN IF EXISTS is_deleted;
ALTER TABLE prj_schema.t_worklog DROP COLUMN IF EXISTS is_deleted;
ALTER TABLE chg_schema.t_change_attachment DROP COLUMN IF EXISTS is_deleted;
ALTER TABLE report_schema.statistics_snapshot DROP COLUMN IF EXISTS is_deleted;

-- 3. 清理 Flyway 历史（不建议，会破坏迁移链路）
-- DELETE FROM flyway_schema_history WHERE version = '1004';
```

**注意**：本迁移**不会**丢失数据（仅添加列并安装触发器），正常情况下不应需要回滚。

---

## 已修复代码对应的 Service 调用模式变更

### 通知模块（已修）
`NotificationService.deleteByUser` / `deleteNotification` 现已改为：
```java
notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
    .eq(Notification::getUserId, userId)
    .ne(Notification::getStatus, "DELETED")
    .set(Notification::getStatus, "DELETED")
);
```

`Notification` 实体本身已修复。

### 4 个待迁移实体（本次迁移解决）
- `TaskPredecessor`：业务层 `@TableLogic` 字段已就绪，DB 层触发器已就绪
- `Worklog`：同上
- `ChangeAttachment`：同上
- `StatisticsSnapshot`：同上

任何旧的 `mapper.deleteById(xxx)` 调用会自动转为 `UPDATE is_deleted=true`。

---

## 注意事项

1. **不要在生产环境前手动执行 ALTER TABLE**：Flyway 会自动执行。如果手动执行后再启动，Flyway 会报 "column already exists"，但因迁移脚本使用 `IF NOT EXISTS`，会幂等通过。

2. **不要修改 V1004 文件**：已应用的 Flyway 迁移禁止修改。如需调整请创建 V1005+。

3. **先于代码部署**：本迁移应在包含 P1-4 代码修复的版本部署**之前**应用。但即使顺序颠倒也不致命——`@TableLogic` 字段在没有 DB 列时 MyBatis-Plus 会忽略，反而导致硬删除。建议同时部署。

4. **报表/工时统计查询**：使用 MyBatis-Plus 内置 `selectList` 等会自动加 `is_deleted=false` 过滤。自定义 `@Select` SQL 请**自行审计**是否包含 `AND is_deleted = false`，参见 P1-5 待办项。

---

## 关联文件

- `med-rms-web/src/main/resources/db/migration/V1004__part11_soft_delete.sql`（Flyway 实际执行）
- `Code/backend/ddl/r240_part11_soft_delete.sql`（审计副本）
- `Code/backend/med-rms-project/.../domain/entity/TaskPredecessor.java`（实体修复）
- `Code/backend/med-rms-project/.../domain/entity/Worklog.java`（实体修复）
- `Code/backend/med-rms-compliance/.../domain/entity/StatisticsSnapshot.java`（实体修复）
- `Code/backend/med-rms-change/.../domain/entity/ChangeAttachment.java`（实体修复）

---

*迁移版本：V1004 · 关联代码版本：2026-08-10 · 21 CFR Part 11 §11.10(c) 合规修复*