import request from './request'
import type { AxiosResponse } from 'axios'

// MyBatis-Plus IPage 通用分页结构
export interface IPage<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

// 21 CFR Part 11 §11.50 签名意图枚举（不可扩展）
export type SignatureIntentType = 'approve' | 'confirm' | 'review' | 'release'
export type SignatureMeaningType = 'approve' | 'reject' | 'review' | 'confirm' | 'release'
// 签名意图状态
export type SignatureIntentStatus = 'PENDING' | 'CONSUMED' | 'EXPIRED' | 'CANCELLED'
// 签名验证三态
export type VerifyStatus = 'valid' | 'invalid' | 'tampered'

// 签名意图常量
export const SIGNATURE_INTENTS: SignatureIntentType[] = ['approve', 'confirm', 'review', 'release']
export const SIGNATURE_MEANINGS: SignatureMeaningType[] = ['approve', 'reject', 'review', 'confirm', 'release']

export const INTENT_LABEL_ZH: Record<SignatureIntentType, string> = {
  approve: '审批通过',
  confirm: '确认',
  review: '审核',
  release: '发布'
}
export const MEANING_LABEL_ZH: Record<SignatureMeaningType, string> = {
  approve: '审批通过',
  reject: '审批拒绝',
  review: '技术评审',
  confirm: '确认',
  release: '发布'
}

export interface SignatureRecord {
  id: number
  signatureType?: string
  intent?: string
  signerId: number
  signerName: string
  signerRole?: string
  documentType: string
  documentId: number
  documentNo?: string
  signatureHash?: string
  signatureValue?: string // v1.47 实体哈希
  entityHash?: string // v1.47 实体哈希
  signatureMethod?: string
  signedAt: string
  reason?: string
  ipAddress?: string
  deviceInfo?: string
  isValid?: boolean
  // 兼容旧字段（部分调用方仍用）
  signType?: string
  signature?: string
  hashValue?: string
}

export interface SignatureIntent {
  id: number
  intentNo?: string
  requesterId: number
  documentType: string
  documentId: number
  intentCode?: string
  meaningCode?: string
  status: SignatureIntentStatus
  expiresAt?: string
  consumedAt?: string
  consumedBy?: number
  signatureId?: number
  createdAt?: string
}

export interface SignatureVerifyResult {
  valid: boolean
  message: string
  signTime: string
  signerName: string
}

export interface SignatureSettings {
  userId: number
  otpEnabled: boolean
  otpSecret?: string
  pinEnabled: boolean
  updatedAt?: string
}

/**
 * 完整 SignRequest 字段（对应后端 ElectronicSignatureController#sign）
 * 必填：intentId + signaturePassword + otpCode；
 * signerId/signerName/documentType/documentId/meaningCode 可由后端从 intentId 反查。
 * 这里允许传齐所有字段，便于不同调用方按需组合。
 */
export interface SignRequestPayload {
  documentType: string
  documentId: number
  signType: string
  // v1.47 完整三段式签名（intent + password + otp）所需字段
  intentId?: number
  meaningCode?: string
  signerId?: number
  signerName?: string
  documentNo?: string
  reason?: string
  signatureMethod?: string
  signaturePassword?: string
  otpCode?: string
}

export const esignatureApi = {
  sign: (data: SignRequestPayload) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureRecord }>>('/esignature/sign', data),

  // R94 修复：原 verify 调 GET /esignature/verify?documentType=&documentId=（端点不存在）
  // 正确端点是 POST /esignature/verify/{signatureId}（要签名记录 ID）
  verify: (signatureId: number) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureVerifyResult }>>(`/esignature/verify/${signatureId}`),

  getRecords: (params: { documentType?: string; documentId?: number }) =>
    request.get<any, AxiosResponse<{ code: number; data: SignatureRecord[] }>>('/esignature/signatures', { params }),

  // v1.46 P1-后端-3：按实体取签名（用于 IPD 门多签可视化）
  getByEntity: (entityType: string, entityId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: SignatureRecord[] }>>(`/esignature/entity/${entityType}/${entityId}`),

  // v1.43 P1 修复：签名设置/OTP/PIN 端点封装
  getSettings: (userId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: SignatureSettings }>>(`/esignature/settings/${userId}`),

  changeSignaturePassword: (userId: number, currentPwd: string, newPwd: string) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureSettings }>>(
      `/esignature/settings/${userId}/password`,
      null,
      { params: { currentPwd, newPwd } }
    ),

  // v1.51 P0 修复：电子签名域 5 页面（21 CFR Part 11 §11.50/§11.70 强制要求）
  // 列表（带分页 + entityType + signerId 过滤）— 对应后端 GET /esignature/signatures
  listSignatures: (params?: { signerId?: number; entityType?: string; page?: number; size?: number }) =>
    request.get<any, AxiosResponse<{ code: number; data: IPage<SignatureRecord> }>>(
      '/esignature/signatures',
      { params: { page: 0, size: 20, ...(params || {}) } }
    ),

  // 详情 — 对应后端 GET /esignature/signatures/{id}
  getSignatureById: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: SignatureRecord }>>(`/esignature/signatures/${id}`),

  // 验签 — 对应后端 POST /esignature/verify/{signatureId}（R96 修复：返回完整对象 {valid, signerName, signTime, message}）
  verifySignature: (signatureId: number) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureVerifyResult }>>(`/esignature/verify/${signatureId}`),

  // 创建签名意图 — 对应后端 POST /esignature/intents
  createIntent: (data: {
    requesterId?: number
    signerId?: number // v1.47 兼容别名
    documentType: string
    documentId: number
    intentCode?: string
    meaningCode: string
  }) => request.post<any, AxiosResponse<{ code: number; data: SignatureIntent }>>('/esignature/intents', data),

  // R97 新增：签名意图列表（按 signerId+status 过滤，支持分页）
  // 与 listSignatures 的区别：listIntents 查 SignatureIntent（含 PENDING/CONSUMED/EXPIRED/CANCELLED 状态）
  // 用于 Dashboard "待签字"计数 + SignatureList 默认 PENDING 过滤
  listIntents: (params?: { signerId?: number; status?: string; page?: number; size?: number }) =>
    request.get<any, AxiosResponse<{ code: number; data: IPage<SignatureIntent> }>>(
      '/esignature/intents',
      { params: { page: 0, size: 20, ...(params || {}) } }
    ),

  // 意图详情 — 对应后端 GET /esignature/intents/{id}
  // 注意：后端 v1.47 实现中只暴露 POST /intents 和 POST /intents/{id}/cancel，
  // GET /intents/{id} 端点未实现；前端 SignatureIntentDetail.vue 已做兜底
  getIntentById: (intentId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: SignatureIntent }>>(`/esignature/intents/${intentId}`),

  // 取消签名意图 — 对应后端 POST /esignature/intents/{id}/cancel
  cancelIntent: (intentId: number, operatorId: number) =>
    request.post<any, AxiosResponse<{ code: number; data: void }>>(
      `/esignature/intents/${intentId}/cancel`,
      null,
      { params: { operatorId } }
    ),

  // 重签 — 对应后端 POST /esignature/signatures/{id}/re-sign
  reSign: (signatureId: number, data: { signerId: number; newIntentId: number; reason: string }) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureRecord }>>(
      `/esignature/signatures/${signatureId}/re-sign`,
      data
    ),

  // 使签名失效 — 对应后端 POST /esignature/{signatureId}/invalidate
  invalidateSignature: (signatureId: number, operatorId: number, reason: string) =>
    request.post<any, AxiosResponse<{ code: number; data: void }>>(
      `/esignature/${signatureId}/invalidate`,
      null,
      { params: { operatorId, reason } }
    ),

  getOtpUri: (userId: number, account?: string) =>
    request.get<any, AxiosResponse<{ code: number; data: string }>>(`/esignature/settings/${userId}/otp/uri`, {
      params: { account }
    }),

  generateOtpSecret: (userId: number) =>
    request.post<any, AxiosResponse<{ code: number; data: string }>>(`/esignature/settings/${userId}/otp/generate`),

  enableOtp: (userId: number, otpSecret: string) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureSettings }>>(
      `/esignature/settings/${userId}/otp/enable`,
      null,
      { params: { otpSecret } }
    ),

  disableOtp: (userId: number) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureSettings }>>(
      `/esignature/settings/${userId}/otp/disable`
    ),

  updatePin: (userId: number, newPin: string) =>
    request.post<any, AxiosResponse<{ code: number; data: SignatureSettings }>>(
      `/esignature/settings/${userId}/pin`,
      null,
      { params: { newPin } }
    ),
}