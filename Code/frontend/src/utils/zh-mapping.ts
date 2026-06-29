/**
 * 全局中文字典 - 把所有英文枚举值映射为中文显示
 * 后端存储仍用英文 enum，前端 UI 统一用中文标签
 */

// 变更状态
export const CHANGE_STATUS_ZH: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  ANALYZING: '影响分析中',
  PENDING_APPROVAL: '待审批',
  InReview: '审核中',
  APPROVED: '已批准',
  Approved: '已批准',
  REJECTED: '已拒绝',
  EXECUTING: '执行中',
  VERIFIED: '已验证',
  CLOSED: '已关闭',
  CANCELLED: '已取消'
}

// 变更类型
export const CHANGE_TYPE_ZH: Record<string, string> = {
  CORRECTIVE: '纠正性变更',
  ADAPTIVE: '适应性变更',
  PERFECTIVE: '完善性变更',
  EMERGENCY: '紧急变更',
  REQUIREMENT_UPDATE: '需求更新',
  FEATURE_UPDATE: '功能更新',
  DOCUMENT: '文档变更',
  NORMAL: '普通变更',
  MAJOR: '重大变更',
  MINOR: '微小变更'
}

// 紧急程度（4 档统一）
export const URGENCY_ZH: Record<string, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '紧急',
  NORMAL: '中',
  URGENT: '紧急',
  EMERGENCY: '紧急',
  MAJOR: '高',
  MINOR: '低',
  DOCUMENT: '中',
  REQUIREMENT_UPDATE: '高'
}

// 紧急程度 4 色
export const URGENCY_TAG_TYPE: Record<string, string> = {
  LOW: 'info',
  MEDIUM: 'warning',
  HIGH: 'warning',
  CRITICAL: 'danger',
  NORMAL: 'info',
  URGENT: 'danger'
}

// 需求层级
export const REQ_LEVEL_ZH: Record<string, string> = {
  URS: 'URS 用户需求',
  PRS: 'PRS 产品需求',
  SRS: 'SRS 软件需求',
  DRS: 'DRS 设计需求'
}

// 需求状态
export const REQ_STATUS_ZH: Record<string, string> = {
  Draft: '草稿',
  Submitted: '已提交',
  InReview: '评审中',
  Approved: '已批准',
  Rejected: '已拒绝',
  Implemented: '已实现',
  Verified: '已验证',
  Baseline: '已基线',
  Closed: '已关闭'
}

// 优先级
export const PRIORITY_ZH: Record<string, string> = {
  MUST: '必须',
  SHOULD: '应该',
  COULD: '可以',
  WONT: '不做'
}

// 风险等级
export const RISK_LEVEL_ZH: Record<string, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低',
  UNKNOWN: '未知'
}

// 风险严重度
export const RISK_SEVERITY_ZH: Record<string, string> = {
  CRITICAL: '灾难性',
  MAJOR: '严重',
  MINOR: '轻度',
  NEGLIGIBLE: '可忽略'
}

// 风险概率
export const RISK_PROBABILITY_ZH: Record<string, string> = {
  HIGH: '高',
  MEDIUM: '中',
  LOW: '低'
}

// 风险状态
export const RISK_STATUS_ZH: Record<string, string> = {
  OPEN: '开放',
  IN_PROGRESS: '处理中',
  CLOSED: '已关闭',
  ACCEPTED: '已接受'
}

// 安全分类
export const SAFETY_CLASS_ZH: Record<string, string> = {
  A: 'Class A',
  B: 'Class B',
  C: 'Class C'
}

// 测试用例状态
export const TEST_CASE_STATUS_ZH: Record<string, string> = {
  DRAFT: '草稿',
  PENDING: '待执行',
  PASSED: '已通过',
  FAILED: '未通过',
  BLOCKED: '已阻塞',
  NA: '不适用',
  ACTIVE: '执行中'
}

// 测试执行结果
export const TEST_RESULT_ZH: Record<string, string> = {
  PENDING: '待执行',
  PASSED: '通过',
  FAILED: '失败',
  NA: '不适用'
}

// 签名验证状态
export const SIGNATURE_VERIFY_ZH: Record<string, string> = {
  valid: '有效',
  invalid: '无效',
  tampered: '被篡改',
  unknown: '未知'
}

// 签名意图
export const SIGNATURE_INTENT_ZH: Record<string, string> = {
  approve: '审批通过',
  confirm: '确认',
  review: '审核',
  release: '发布',
  reject: '拒绝'
}

// 签名含义
export const SIGNATURE_MEANING_ZH: Record<string, string> = {
  approve: '审批通过',
  reject: '审批拒绝',
  review: '技术评审',
  confirm: '确认',
  release: '发布'
}

// CTI 满足程度
export const CTI_COMPLIANCE_ZH: Record<string, string> = {
  FULLY: '完全符合',
  PARTIALLY: '部分符合',
  NOT_APPLICABLE: '不适用'
}

// 基线状态
export const BASELINE_STATUS_ZH: Record<string, string> = {
  DRAFT: '草稿',
  LOCKED: '已锁定',
  RELEASED: '已发布',
  ARCHIVED: '已归档'
}

// 项目状态
export const PROJECT_STATUS_ZH: Record<string, string> = {
  PLANNING: '计划中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  TERMINATED: '已终止',
  ON_HOLD: '已暂停',
  CRITICAL: '极高风险'
}

// IPD Gate 状态
export const IPD_GATE_STATUS_ZH: Record<string, string> = {
  PENDING: '待评审',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  IN_PROGRESS: '评审中'
}

// 通知类型
export const NOTIFICATION_TYPE_ZH: Record<string, string> = {
  REVIEW_APPROVED: '评审通过',
  REVIEW_REJECTED: '评审拒绝',
  CHANGE_APPROVED: '变更批准',
  CHANGE_REJECTED: '变更拒绝',
  SIGNATURE_REQUIRED: '待签名',
  RISK_ALERT: '风险告警',
  COMPLIANCE_ALERT: '合规告警',
  SYSTEM: '系统通知'
}

// CAPA 状态
export const CAPA_STATUS_ZH: Record<string, string> = {
  OPEN: '已开启',
  IN_PROGRESS: '处理中',
  VERIFIED: '已验证',
  COMPLETED: '已完成',
  CLOSED: '已关闭'
}

// 需求池状态
export const REQ_POOL_STATUS_ZH: Record<string, string> = {
  PENDING: '待处理',
  PARSED: '已解析',
  CONVERTED: '已转换',
  REJECTED: '已拒绝'
}

// 用户状态
export const USER_STATUS_ZH: Record<string, string> = {
  ACTIVE: '正常',
  LOCKED: '已锁定',
  DISABLED: '已禁用'
}

// 影响层级
export const AFFECTED_LEVEL_ZH: Record<string, string> = {
  URS: 'URS 用户需求',
  PRS: 'PRS 产品需求',
  SRS: 'SRS 软件需求',
  DRS: 'DRS 设计需求',
  TEST_CASE: '测试用例'
}

// 通用兜底函数 - 给定字典+值，若无映射则返回原值
export function toZh(value: string | undefined | null, dict: Record<string, string>): string {
  if (value == null) return '-'
  return dict[value] ?? value
}
