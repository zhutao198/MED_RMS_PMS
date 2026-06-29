# Endpoint Audit Report (W30 Phase 1)

**扫描时间**：2026-06-15 18:00:18

**前端调用 unique**：145
**后端端点 unique**：291

## 问题汇总

| 类型 | 数量 | 说明 |
|---|---|---|
| BE_SY0101_BADMETHOD | 6 | 后端端点 SY0101 方法/参数不匹配 |
| BE_SY0301_NOTFOUND | 1 | 后端端点 SY0301 资源不存在 |
| BE_SY_OTHER | 7 | 后端其他业务异常 |
| FE_SY0101_BADMETHOD | 2 | 前端调用 SY0101（方法错） |
| FE_SY0301_NOTFOUND | 7 | 前端调用 SY0301（端点不存在） |
| FE_SY_OTHER | 2 | 前端调用其他业务异常 |

## 详细问题（按类型）

### BE_SY0101_BADMETHOD (6 个)

- `POST /admin/users/{id}/verify-signature-password` → code=SY0101 status=200: 缺少必要参数: signaturePassword
- `GET /auth/has-perm` → code=SY0101 status=200: 缺少必要参数: code
- `GET /statistics/snapshots` → code=SY0101 status=200: 缺少必要参数: projectId
- `GET /trace-links` → code=SY0101 status=200: 缺少必要参数: projectId
- `GET /trace-links/check-cycle` → code=SY0101 status=200: 缺少必要参数: sourceId
- `GET /trace-links/by-pair` → code=SY0101 status=200: 缺少必要参数: sourceId

### BE_SY0301_NOTFOUND (1 个)

- `DELETE /changes/attachments/{attId}` → code=SY0301 status=200: 附件不存在: id=1

### BE_SY_OTHER (7 个)

- `POST /auth/login` → code=SY0000 status=200: 系统异常，请稍后重试
- `POST /auth/refresh` → code=SY0000 status=200: 系统异常，请稍后重试
- `GET /auth/admin-demo` → code=SY0000 status=200: 系统异常，请稍后重试
- `POST /dashboard/layout` → code=SY0000 status=200: 系统异常，请稍后重试
- `PUT /gantt/tasks/{id}/predecessors` → code=SY0000 status=200: 系统异常，请稍后重试
- `POST /trace-links` → code=SY0000 status=200: 系统异常，请稍后重试
- `PUT /trace-links/{id}` → code=SY0000 status=200: 系统异常，请稍后重试

### FE_SY0101_BADMETHOD (2 个)

- `PUT /changes/{id}` → code=SY0101 status=200: 不支持的请求方法: PUT
  - 调用方：frontend\src\views\change\ChangeRequest.vue
- `GET /trace-links` → code=SY0101 status=200: 缺少必要参数: projectId
  - 调用方：frontend\src\views\traceability\TraceMatrix.vue

### FE_SY0301_NOTFOUND (7 个)

- `DELETE /changes/attachments/{id}` → code=SY0301 status=200: 附件不存在: id=1
  - 调用方：frontend\src\views\change\ChangeRequest.vue
- `GET /requirement-tasks/by-project/{id}` → code=SY0301 status=200: 资源不存在
  - 调用方：frontend\src\views\project\ResourceManagement.vue
- `PUT /gantt/milestones/{id}` → code=SY0301 status=200: 资源不存在
  - 调用方：frontend\src\views\project\milestone\MilestoneList.vue
- `GET /api/statistics/requirements` → code=SY0301 status=200: 资源不存在
  - 调用方：frontend\src\views\requirement\RequirementList.vue
- `GET /system/login-logs` → code=SY0301 status=200: 资源不存在
  - 调用方：frontend\src\views\system\LoginLogs.vue
- `GET /system/profile` → code=SY0301 status=200: 资源不存在
  - 调用方：frontend\src\views\system\Profile.vue
- `POST /system/users/{id}/change-password` → code=SY0301 status=200: 资源不存在
  - 调用方：frontend\src\views\system\Profile.vue

### FE_SY_OTHER (2 个)

- `PUT /gantt/tasks/{id}/predecessors` → code=SY0000 status=200: 系统异常，请稍后重试
  - 调用方：frontend\src\views\project\GanttView.vue
- `POST /trace-links` → code=SY0000 status=200: 系统异常，请稍后重试
  - 调用方：frontend\src\views\traceability\TraceGaps.vue, frontend\src\views\traceability\TraceMatrix.vue
