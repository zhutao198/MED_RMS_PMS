import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/dashboard/Dashboard.vue')
  },
  {
    path: '/requirements',
    name: 'RequirementList',
    component: () => import('../views/requirement/RequirementList.vue')
  },
  {
    path: '/requirements/create',
    name: 'ReqCreate',
    component: () => import('../views/requirement/ReqCreate.vue')
  },
  {
    path: '/requirements/:id',
    name: 'RequirementDetail',
    component: () => import('../views/requirement/RequirementDetail.vue')
  },
  {
    path: '/requirements/:id/edit',
    name: 'ReqEdit',
    component: () => import('../views/requirement/ReqEdit.vue')
  },
  {
    path: '/traceability',
    name: 'TraceMatrix',
    component: () => import('../views/traceability/TraceMatrix.vue')
  },
  {
    path: '/traceability/gaps',
    name: 'TraceGaps',
    component: () => import('../views/traceability/TraceGaps.vue')
  },
  {
    path: '/traceability/coverage',
    name: 'TraceCoverage',
    component: () => import('../views/traceability/TraceCoverage.vue')
  },
  // v1.53 P1-13 修复：追溯数据导入
  {
    path: '/traceability/import',
    name: 'TraceImport',
    component: () => import('../views/traceability/TraceImport.vue')
  },
  {
    path: '/trace-graph',
    name: 'TraceGraph',
    component: () => import('../views/traceability/TraceGraph.vue')
  },
  {
    path: '/changes',
    name: 'ChangeList',
    component: () => import('../views/change/ChangeList.vue')
  },
  // v1.53 P1-8 修复：我的审批工作台
  {
    path: '/changes/approvals',
    name: 'ChangeApprovals',
    component: () => import('../views/change/ChangeApprovals.vue')
  },
  {
    path: '/changes/create',
    name: 'ChangeCreate',
    component: () => import('../views/change/ChangeCreate.vue')
  },
  {
    path: '/changes/:id',
    name: 'ChangeRequest',
    component: () => import('../views/change/ChangeRequest.vue')
  },
  {
    path: '/changes/:id/impact',
    name: 'ChangeImpactAnalysis',
    component: () => import('../views/change/ChangeImpactAnalysis.vue')
  },
  {
    path: '/changes/:id/execute',
    name: 'ChangeExecute',
    component: () => import('../views/change/ChangeExecute.vue')
  },
  {
    path: '/changes/:id/verify',
    name: 'ChangeVerify',
    component: () => import('../views/change/ChangeVerify.vue')
  },
  {
    path: '/compliance',
    name: 'ComplianceList',
    component: () => import('../views/compliance/ComplianceList.vue')
  },
  {
    path: '/compliance/baselines',
    name: 'Baselines',
    component: () => import('../views/compliance/Baselines.vue')
  },
  {
    path: '/compliance/soup',
    name: 'SoupManagement',
    component: () => import('../views/compliance/SoupManagement.vue')
  },
  {
    path: '/compliance/problem-report',
    name: 'ProblemReport',
    component: () => import('../views/compliance/ProblemReport.vue')
  },
  {
    path: '/compliance/iec62304',
    name: 'Iec62304',
    component: () => import('../views/compliance/Iec62304.vue')
  },
  {
    path: '/esignature',
    name: 'SignatureList',
    component: () => import('../views/esignature/SignatureList.vue')
  },
  // v1.51 P0 修复：电子签名域 5 页面（21 CFR Part 11 §11.50/§11.70 强制要求）
  {
    path: '/signatures',
    name: 'Signatures',
    component: () => import('../views/esignature/Signatures.vue')
  },
  {
    path: '/signature-history',
    name: 'SignatureHistory',
    component: () => import('../views/esignature/SignatureHistory.vue')
  },
  {
    path: '/signature-history/:id',
    name: 'SignatureHistoryDetail',
    component: () => import('../views/esignature/SignatureHistoryDetail.vue')
  },
  {
    path: '/signature-intent/create',
    name: 'SignatureIntentCreate',
    component: () => import('../views/esignature/SignatureIntentCreate.vue')
  },
  {
    path: '/signature-intent/:id',
    name: 'SignatureIntentDetail',
    component: () => import('../views/esignature/SignatureIntentDetail.vue')
  },
  {
    path: '/esignature/settings',
    name: 'ESignSettings',
    component: () => import('../views/system/ESignSettings.vue')
  },
  {
    path: '/risk',
    name: 'RiskReport',
    component: () => import('../views/risk/RiskReport.vue')
  },
  {
    path: '/risk/register',
    name: 'RiskRegister',
    component: () => import('../views/risk/RiskRegister.vue')
  },
  {
    path: '/risk/fmea',
    name: 'FmeaEditor',
    component: () => import('../views/risk/FmeaEditor.vue')
  },
  // v1.53 P1-17 修复：风险矩阵热力图 + 风险监控
  {
    path: '/risks/matrix',
    name: 'RisksMatrix',
    component: () => import('../views/risk/RisksMatrix.vue')
  },
  {
    path: '/risks/monitoring',
    name: 'RisksMonitoring',
    component: () => import('../views/risk/RisksMonitoring.vue')
  },
  {
    path: '/projects',
    name: 'ProjectsList',
    component: () => import('../views/project/ProjectsList.vue')
  },
  {
    path: '/projects/templates',
    name: 'TemplateManagement',
    component: () => import('../views/project/TemplateManagement.vue')
  },
  {
    path: '/projects/:id',
    name: 'ProjectDetail',
    component: () => import('../views/project/ProjectDetail.vue')
  },
  {
    path: '/projects/gantt',
    name: 'GanttView',
    component: () => import('../views/project/GanttView.vue')
  },
  {
    path: '/projects/ipd',
    name: 'IpdGate',
    component: () => import('../views/project/IpdGate.vue')
  },
  // v1.53 P1-19：项目域 6 路由 — 静态路由优先于动态路由
  {
    path: '/projects/create',
    name: 'ProjectCreate',
    component: () => import('../views/project/ProjectCreate.vue')
  },
  // v1.53 P1-19：项目域 6 路由 — 动态路由（必须放在静态路由之后）
  {
    path: '/projects/:id/edit',
    name: 'ProjectEdit',
    component: () => import('../views/project/ProjectEdit.vue')
  },
  {
    path: '/projects/:id/members',
    name: 'ProjectMembers',
    component: () => import('../views/project/ProjectMembers.vue')
  },
  {
    path: '/projects/:id/members/add',
    name: 'ProjectMembersAdd',
    component: () => import('../views/project/ProjectMembersAdd.vue')
  },
  {
    path: '/projects/:id/deliverables',
    name: 'ProjectDeliverables',
    component: () => import('../views/project/ProjectDeliverables.vue')
  },
  {
    path: '/projects/:id/gates',
    name: 'ProjectGates',
    component: () => import('../views/project/ProjectGates.vue')
  },
  {
    path: '/projects/:id/gantt',
    name: 'ProjectGantt',
    component: () => import('../views/project/GanttView.vue')
  },
  {
    path: '/milestones',
    name: 'MilestoneList',
    component: () => import('../views/project/milestone/MilestoneList.vue')
  },
  {
    path: '/projects/resources',
    name: 'ResourceManagement',
    component: () => import('../views/project/ResourceManagement.vue')
  },
  {
    path: '/projects/worklog',
    name: 'WorklogView',
    component: () => import('../views/project/WorklogView.vue')
  },
  {
    path: '/system',
    name: 'SystemManagement',
    component: () => import('../views/system/SystemManagement.vue')
  },
  {
    path: '/system/users',
    name: 'UserManage',
    component: () => import('../views/system/UserManage.vue')
  },
  {
    path: '/system/dicts',
    name: 'DictManage',
    component: () => import('../views/system/DictManage.vue')
  },
  {
    path: '/system/migration',
    name: 'DataMigration',
    component: () => import('../views/system/DataMigration.vue')
  },
  // v1.53 P1-22：系统域 5 路由 — roles/:id/edit 静态优先于潜在通配
  {
    path: '/system/roles/:id/edit',
    name: 'RoleEdit',
    component: () => import('../views/system/RoleEdit.vue')
  },
  {
    path: '/system/login-logs',
    name: 'LoginLogs',
    component: () => import('../views/system/LoginLogs.vue')
  },
  {
    path: '/system/operation-logs',
    name: 'OperationLogs',
    component: () => import('../views/system/OperationLogs.vue')
  },
  {
    path: '/system/profile',
    name: 'Profile',
    component: () => import('../views/system/Profile.vue')
  },
  {
    path: '/system/organization',
    name: 'Organization',
    component: () => import('../views/system/Organization.vue')
  },
  {
    path: '/reports',
    name: 'ReportCenter',
    component: () => import('../views/report/ReportCenter.vue')
  },
  {
    path: '/reports/custom',
    name: 'ReportsCustom',
    component: () => import('../views/report/ReportsCustom.vue')
  },
  // v1.53 P1-25：报告导出
  {
    path: '/reports/export',
    name: 'ReportExport',
    component: () => import('../views/report/ReportExport.vue')
  },
  {
    path: '/decompose',
    name: 'DecomposeList',
    component: () => import('../views/requirement/decompose/DecomposeList.vue')
  },
  {
    path: '/requirements/:id/decompose',
    name: 'DecomposeWorkbench',
    component: () => import('../views/requirement/decompose/DecomposeWorkbench.vue')
  },
  {
    path: '/requirements/:id/versions',
    name: 'RequirementVersions',
    component: () => import('../views/requirement/RequirementVersions.vue')
  },
  // v1.52 新增：新建需求版本页（独立完整页面）
  {
    path: '/requirements/:id/versions/create',
    name: 'ReqVersionCreate',
    component: () => import('../views/requirement/ReqVersionCreate.vue')
  },
  {
    path: '/testcases',
    name: 'TestCaseList',
    component: () => import('../views/requirement/TestCaseList.vue')
  },
  {
    path: '/reviews',
    name: 'ReviewManagement',
    component: () => import('../views/requirement/ReviewManagement.vue')
  },
  {
    path: '/notifications',
    name: 'NotificationList',
    component: () => import('../views/system/NotificationList.vue')
  },
  {
    path: '/requirement-pool',
    name: 'RequirementPool',
    component: () => import('../views/requirement/RequirementPool.vue')
  },
  {
    path: '/requirement-tasks',
    name: 'RequirementTaskConvert',
    component: () => import('../views/requirement/RequirementTaskConvert.vue')
  },
  {
    path: '/requirements/kanban',
    name: 'RequirementKanban',
    component: () => import('../views/requirement/KanbanBoard.vue')
  },
  {
    path: '/requirements/quality',
    name: 'QualityScore',
    component: () => import('../views/requirement/QualityScore.vue')
  },
  {
    path: '/requirements/ai-assist',
    name: 'AIRequirementAssist',
    component: () => import('../views/requirement/AIRequirementAssist.vue')
  },
  {
    path: '/compliance/dhf',
    name: 'DhfPackage',
    component: () => import('../views/compliance/DhfPackage.vue')
  },
  {
    path: '/compliance/erps',
    name: 'ErpsExport',
    component: () => import('../views/compliance/ErpsExport.vue')
  },
  {
    path: '/compliance/regulation-impact',
    name: 'RegulationImpact',
    component: () => import('../views/compliance/RegulationImpact.vue')
  },
  // v1.52 P0 修复：合规域 9 个缺失独立页面
  {
    path: '/compliance/regulations',
    name: 'Regulations',
    component: () => import('../views/compliance/Regulations.vue')
  },
  {
    path: '/compliance/safety',
    name: 'SafetyClassification',
    component: () => import('../views/compliance/SafetyClassification.vue')
  },
  {
    path: '/compliance/reports',
    name: 'ComplianceReports',
    component: () => import('../views/compliance/ComplianceReports.vue')
  },
  {
    path: '/compliance/soup/:id',
    name: 'SoupDetail',
    component: () => import('../views/compliance/SoupDetail.vue')
  },
  {
    path: '/compliance/soup/:id/review',
    name: 'SoupReview',
    component: () => import('../views/compliance/SoupReview.vue')
  },
  {
    path: '/compliance/problem-report/create',
    name: 'ProblemReportCreate',
    component: () => import('../views/compliance/ProblemReportCreate.vue')
  },
  {
    path: '/compliance/problem-report/:id',
    name: 'ProblemReportDetail',
    component: () => import('../views/compliance/ProblemReportDetail.vue')
  },
  {
    path: '/compliance/baselines/:id',
    name: 'BaselineDetail',
    component: () => import('../views/compliance/BaselineDetail.vue')
  },
  {
    path: '/compliance/baselines/compare',
    name: 'BaselineCompare',
    component: () => import('../views/compliance/BaselineCompare.vue')
  },
  {
    path: '/compliance/baselines/:id/edit',
    name: 'BaselineEdit',
    component: () => import('../views/compliance/BaselineEdit.vue')
  },
  {
    path: '/audit-logs',
    name: 'AuditLogs',
    component: () => import('../views/audit/AuditLogs.vue')
  },
  {
    path: '/audit-logs/export',
    name: 'AuditLogsExport',
    component: () => import('../views/audit/AuditLogsExport.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router