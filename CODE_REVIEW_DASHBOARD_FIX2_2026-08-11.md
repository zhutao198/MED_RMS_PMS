# 仪表盘修复第二批（2026-08-11）

## 修复汇总

| 编号 | 问题 | 修复 |
|------|------|------|
| **D-19** | 燃尽图理想线与实际线完全重合 | 重写 actual 算法，用每个任务的 updatedAt 作为完成时间累加真实完成量 |
| **D-20** | 变更趋势"空"的视觉问题 | 柱高最小 20px（0 值也可见）、加 loading 占位、标题修正（7 周 → 6 个月） |

---

## D-19 燃尽图算法 bug 详解

### 原代码（`GanttController.getBurndown` line 209-216）
```java
if (d.isAfter(today) && !d.equals(today)) {
    actual.add(null);
} else {
    double progress = totalDays > 0 ? (double) ChronoUnit.DAYS.between(start, d) / totalDays : 0;
    long completed = Math.round(totalEffort * Math.min(progress, 1.0));   // ← 线性插值
    long remaining = d.isEqual(today) ? (totalEffort - doneEffort) : Math.max(totalEffort - completed, 0);
    actual.add((int) Math.max(remaining, 0));
}
```

### 为什么会重合
对于 d < today（非今日）：
- `progress = (d-start) / totalDays`
- `completed = totalEffort * progress = totalEffort * (d-start) / totalDays = dailyRate * (d-start)`
- `remaining = totalEffort - completed = totalEffort - dailyRate * (d-start)`

而 ideal：
- `idealRemaining = totalEffort - dailyRate * i` 其中 `i = d-start`

→ **数学上完全相同**！

只有 `d.isEqual(today)` 那一行用了真实 doneEffort，但其它点都是线性的。

### 修复后
```java
Map<LocalDate, Long> completedByDate = new TreeMap<>();
for (Task t : tasks) {
    if (!"DONE".equals(t.getStatus())) continue;
    LocalDate completionDate = (t.getUpdatedAt() != null ? t.getUpdatedAt() : t.getCreatedAt()).toLocalDate();
    completedByDate.merge(completionDate, t.getEstimatedHours(), Long::sum);
}
// 转累计和并查表填充 actual
for (long i = 0; i <= totalDays; i++) {
    ...
    long cumulativeDone = ...;  // 真实累计完成量
    actual.add((int) Math.max(totalEffort - cumulativeDone, 0));
}
```

### 现在 actual 应该呈现真实"燃尽"曲线：
- 项目开始前（d < 最早 DONE 任务日期）：actual = totalEffort（最顶水平线）
- 有 DONE 任务完成的日子：actual 阶梯式下降
- 今天（today）：actual = totalEffort - doneEffort
- 今天之后（d > today）：actual = null（折线断开）

### ⚠️ 如果仍然看到重合
说明数据库中：
1. **没有 DONE 状态的任务**（doneEffort = 0，actual 全程 = totalEffort）
2. **DONE 任务都集中在同一天**（形成单点跳变，类似重合）
3. **DONE 任务的 updatedAt 都被回填到 createdAt**（参考 `113_backfill_created_at.sql` 的回填行为）

需要检查：
```sql
SELECT status, COUNT(*) FROM prj_schema.t_task WHERE is_deleted = false GROUP BY status;
SELECT id, status, estimated_hours, updated_at FROM prj_schema.t_task 
  WHERE status = 'DONE' ORDER BY updated_at;
```

---

## D-20 变更趋势视觉修复

### 原问题
- 6 根柱全部为 0 时，每根柱高 4px，几乎看不见
- 用户感知为"完全空"
- 标题"最近 7 周"与实际 6 个月不符

### 修复
- 最小柱高 20px（0 值也可见）
- 加 `v-if="changeTrend.length === 0"` 显示"加载中或暂无变更数据"占位
- 标题改为"最近 6 个月"

### 数据验证
接口调用：`GET /api/dashboard/trends?projectId=X&metric=CHANGE_REQUESTS_PER_MONTH`

后端逻辑（`StatisticsService.getTrends`）：
1. 取最近 6 个月（每月 1 号到下月 1 号）
2. `changeRequestMapper.selectCount(...)` 按月统计
3. 返回 `{series: [{month, value}], metric: "CHANGE_REQUESTS_PER_MONTH"}`

如果接口成功调用但 chart 还是空：
- 打开浏览器 Console 看是否有 `console.warn('loadChangeTrend failed')`
- 在 Network 面板看 `/api/dashboard/trends` 的实际响应
- 后端日志看 `StatisticsService.getTrends` 是否被调用

---

## 已知数据问题（不在本次修复范围）

### ProjectFilter 默认值
- `Dashboard.vue:513` `filterProject = useProjectStore().currentProjectId ?? -1`
- 如果用户从未选过项目，filterProject = -1
- loadChangeTrend 收到 undefined，后端查询所有项目
- 可能因为权限不足被 PermissionEnforceFilter 拦截（403）

### 权限矩阵覆盖检查
- `/dashboard/trends` 由 `addPrefix(GET, "/dashboard", "report:dashboard")` 覆盖
- 但要求用户有 `report:dashboard` perm
- 121_rbac_seed_data.sql 显示该 perm 在多个角色中（line 169/186/210/227/242）
- 普通用户应该都有

如果仍然 403，请检查：
```sql
SELECT u.username, r.code AS role, p.code AS perm
FROM sys_schema.t_user u
JOIN sys_schema.t_user_role ur ON u.id = ur.user_id
JOIN sys_schema.t_role r ON ur.role_id = r.id
JOIN sys_schema.t_role_permission rp ON r.id = rp.role_id
JOIN sys_schema.t_permission p ON rp.permission_id = p.id
WHERE p.code = 'report:dashboard';
```

---

*修复日期：2026-08-11 · BUILD SUCCESS · 1 个后端文件 + 1 个前端文件改动*