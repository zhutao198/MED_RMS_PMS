# 数据契约一致性扫描报告 (W30 Phase 3)

**扫描时间**：2026-06-15 18:10:49

## 枚举值 diff

### 前端硬编码 status 值

```
ACTIVE, COMPLETED, DEFINE, DEVELOPMENT, DOC, DONE, FIXED, IGNORED, IN_PROGRESS, MAJOR, MARKET, MEDIUM, MUST, PASS, PENDING, PENDING_APPROVAL, PLANNED, PLANNING, RELEASE, SHOULD, SUCCESS, TODO, URS
```

### 后端实际枚举值

- **requirement.status**: ['Baseline', 'Decomposed', 'Draft', 'InProgress', 'InTest', 'PendingDecompose', 'ReviewApproved']
- **risk.level**: ['HIGH', 'MEDIUM']
- **risk.status**: ['ACCEPTED', 'MONITORING', 'OPEN']
- **problem_report.status**: ['Analyzing', 'Open']
- **problem_report.severity**: ['HIGH', 'MAJOR']
- **change.status**: ['ANALYZING', 'APPROVED', 'Approved', 'CANCELLED', 'CLOSED', 'COMPLETED', 'DRAFT', 'EXECUTING', 'InReview', 'PENDING_APPROVAL', 'SUBMITTED']

### 枚举值不匹配（前端硬编码但后端无）

| 前端值 | 推测类别 | 实际后端选项 |
|---|---|---|
| ACTIVE | unknown | 见 actual_enum_values |
| DEFINE | unknown | 见 actual_enum_values |
| DEVELOPMENT | unknown | 见 actual_enum_values |
| DOC | unknown | 见 actual_enum_values |
| FIXED | unknown | 见 actual_enum_values |
| IGNORED | unknown | 见 actual_enum_values |
| MARKET | unknown | 见 actual_enum_values |
| MUST | unknown | 见 actual_enum_values |
| PASS | unknown | 见 actual_enum_values |
| PENDING | unknown | 见 actual_enum_values |
| PLANNED | unknown | 见 actual_enum_values |
| PLANNING | unknown | 见 actual_enum_values |
| RELEASE | unknown | 见 actual_enum_values |
| SHOULD | unknown | 见 actual_enum_values |
| SUCCESS | unknown | 见 actual_enum_values |
| URS | unknown | 见 actual_enum_values |

## 游离数据扫描

| 模块 | 全量 | 各项目加和 | 游离数 | DB 中 projectId=null 数 |
|---|---|---|---|---|
| ✅ risk | 3 | 12 | -9 | 3 |
| ✅ requirement | 200 | 338 | -138 | 0 |
| ✅ change | 103 | 412 | -309 | 103 |
| ⚠️ problem_report | 49 | 28 | 21 | 21 |

## 关键端点字段采样

- `/dashboard/view/requirements?projectId=1` → 字段数 20: ['byStatus', 'byStatus.Approved', 'byStatus.Baseline', 'byStatus.Decomposed', 'byStatus.Draft', 'byStatus.InProgress', 'byStatus.InTest', 'byStatus.PendingDecompose', 'byStatus.ReviewApproved', 'byStatus.Submitted', 'byStatus.Suspect', 'byType', 'byType.DRS', 'byType.PRS', 'byType.SRS', 'byType.Software', 'byType.System', 'byType.URS', 'coverage', 'coverage.byType']
- `/dashboard/view/risk?projectId=1` → 字段数 5: ['avgRpn', 'byLevel', 'byStatus', 'highCount', 'total']
- `/dashboard/view/management?projectId=1` → 字段数 20: ['alerts', 'alerts.highRiskCount', 'alerts.pendingReviewCount', 'alerts.suspectCount', 'alerts.total', 'byStatus', 'byStatus.IN_PROGRESS', 'coverage', 'coverage.byType', 'coverage.byType.DRS', 'coverage.byType.DRS.coverageRate', 'coverage.byType.DRS.total', 'coverage.byType.DRS.traced', 'coverage.byType.DRS.untraced', 'coverage.byType.PRS', 'coverage.byType.PRS.coverageRate', 'coverage.byType.PRS.total', 'coverage.byType.PRS.traced', 'coverage.byType.PRS.untraced', 'coverage.byType.SRS']
- `/dashboard/view/compliance?projectId=1` → 字段数 20: ['changes', 'changes.byStatus', 'changes.byStatus.ANALYZING', 'changes.byStatus.APPROVED', 'changes.byStatus.Approved', 'changes.byStatus.CANCELLED', 'changes.byStatus.CLOSED', 'changes.byStatus.COMPLETED', 'changes.byStatus.DRAFT', 'changes.byStatus.EXECUTING', 'changes.byStatus.InReview', 'changes.byStatus.PENDING_APPROVAL', 'changes.byStatus.SUBMITTED', 'changes.total', 'iec62304', 'iec62304.complianceRate', 'iec62304.compliant', 'iec62304.nonCompliant', 'iec62304.notApplicable', 'iec62304.partial']
- `/statistics/requirements` → 字段数 20: ['byStatus', 'byStatus.Approved', 'byStatus.Baseline', 'byStatus.Decomposed', 'byStatus.Draft', 'byStatus.InProgress', 'byStatus.InTest', 'byStatus.PendingDecompose', 'byStatus.ReviewApproved', 'byStatus.Submitted', 'byStatus.Suspect', 'byType', 'byType.DRS', 'byType.FUNCTIONAL', 'byType.PRS', 'byType.SRS', 'byType.Software', 'byType.System', 'byType.URS', 'suspectCount']
- `/changes/pending` → 字段数 20: ['[].affectedItems', '[].approvalComments', '[].approvedAt', '[].approvedBy', '[].assigneeId', '[].assigneeName', '[].changeNo', '[].changeType', '[].countersignProgress', '[].countersignRequired', '[].countersigners', '[].createdAt', '[].delegatedAt', '[].delegatedFromId', '[].delegatedFromName', '[].description', '[].id', '[].isDeleted', '[].reason', '[].requestedAt']
- ❌ `/requirement-tasks/by-project/1` → 无数据/不可达
- `/projects` → 字段数 14: ['[].createdAt', '[].description', '[].endDate', '[].id', '[].isDeleted', '[].managerId', '[].managerName', '[].projectName', '[].projectNo', '[].startDate', '[].status', '[].templateCode', '[].templateId', '[].updatedAt']