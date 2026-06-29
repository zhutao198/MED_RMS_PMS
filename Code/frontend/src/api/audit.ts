import request from './request'
import type { AxiosResponse } from 'axios'

/**
 * 审计日志 API（21 CFR Part 11 §11.10(e) 强制要求）
 * 后端 Controller: ComplianceController，路径前缀 /compliance/audit-logs
 */

// 后端 AuditLog 实体直接透传
export interface AuditLog {
  id: number
  prevHash: string | null
  currentHash: string | null
  /** CREATE / MODIFY / DELETE / STATUS_CHANGE / SIGN / LOGIN */
  eventType: string
  entityType: string
  entityId: number | null
  operatorId: number | null
  operatorName: string | null
  operation: string | null
  oldValue: string | null
  newValue: string | null
  reason: string | null
  ipAddress: string | null
  createdAt: string | null
}

// 后端 HashChainVerifyResult
export interface HashChainVerifyResult {
  valid: boolean
  totalChecked: number
  firstFailureId: number | null
  firstFailureType: string | null
  lastValidId: number | null
  message: string
}

export interface AuditLogQuery {
  eventType?: string
  entityType?: string
  entityId?: number
  operatorId?: number
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export interface AuditExportQuery {
  startTime?: string
  endTime?: string
  entityType?: string
  format?: 'csv' | 'xlsx' | 'pdf'
}

export const auditApi = {
  /** 分页查询审计日志 */
  list: (params: AuditLogQuery) =>
    request.get<any, AxiosResponse<{ code: number; data: AuditLog[]; message?: string }>>(
      '/compliance/audit-logs',
      { params }
    ),

  /** 详细验证（v1.46 P0-后端-8）：返回首个断裂点 ID 与类型 */
  verifyDetailed: () =>
    request.get<any, AxiosResponse<{ code: number; data: HashChainVerifyResult }>>(
      '/compliance/audit-logs/verify/detailed'
    ),

  /** 分段验证（跳过历史断裂点） */
  verifyFrom: (startId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: HashChainVerifyResult }>>(
      `/compliance/audit-logs/verify/from/${startId}`
    ),

  /** 后端 CSV 导出（直接 response 流，前端用 blob 下载） */
  exportCsv: (params: { startTime?: string; endTime?: string; entityType?: string }) =>
    request.get('/compliance/audit-logs/export', { params, responseType: 'blob' }),

  /** 按实体查询 */
  getByEntity: (entityType: string, entityId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: AuditLog[] }>>(
      `/compliance/audit-logs/entity/${entityType}/${entityId}`
    ),

  /** 按操作人查询 */
  getByOperator: (operatorId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: AuditLog[] }>>(
      `/compliance/audit-logs/operator/${operatorId}`
    ),
}

/**
 * 事件类型 → 操作动作 映射
 * 后端 eventType: CREATE / MODIFY / DELETE / STATUS_CHANGE / SIGN / LOGIN
 * 前端展示 5 类（create/update/delete/approve/sign）
 */
export const ACTION_TYPE_MAP: Record<string, string> = {
  CREATE: 'create',
  MODIFY: 'update',
  STATUS_CHANGE: 'update',
  SIGN: 'sign',
  DELETE: 'delete',
  LOGIN: 'update',
  APPROVE: 'approve',
}

/**
 * 实体类型 → 模块 映射（5 大模块）
 * 后端实际存的是大写：REQUIREMENT / CHANGE_REQUEST / BASELINE / SIGNATURE_RECORD / TEST_CASE
 * 前端按小写传递，调用前转大写
 */
export const MODULE_OPTIONS: { value: string; label: string }[] = [
  { value: 'requirement', label: '需求' },
  { value: 'change_request', label: '变更' },
  { value: 'baseline', label: '基线' },
  { value: 'signature_record', label: '电子签名' },
  { value: 'test_case', label: '测试用例' },
]

/** 前端小写 entityType → 后端大写 entityType */
export const toBackendEntityType = (v: string): string => (v ? v.toUpperCase() : v)

export const ACTION_OPTIONS: { value: string; label: string }[] = [
  { value: 'create', label: 'CREATE（创建）' },
  { value: 'update', label: 'UPDATE（更新）' },
  { value: 'delete', label: 'DELETE（删除）' },
  { value: 'approve', label: 'APPROVE（审批）' },
  { value: 'sign', label: 'SIGN（签名）' },
]
