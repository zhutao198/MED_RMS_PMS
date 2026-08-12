import request from './request'
import type { AxiosResponse } from 'axios'

export interface ChangeRequest {
  id?: number
  changeNo?: string
  changeType: string
  /** v1.53 P1-9 修复：统一使用 urgency 字段（LOW/MEDIUM/HIGH/CRITICAL） */
  urgency: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string
  /** 兼容旧字段（部分后端接口仍返回 priority） */
  priority?: string
  title: string
  description?: string
  status: string
  requesterId?: number
  requesterName?: number
  reviewerId?: number
  plannedStartDate?: string
  plannedEndDate?: string
  actualStartDate?: string
  actualEndDate?: string
  affectedItems?: string[]
  riskLevel?: string
  rollbackPlan?: string
}

export interface ImpactAssessment {
  id?: number
  changeRequestId: number
  itemNo: string
  itemName: string
  itemType: string
  impactLevel: string
  impactType: string
  impactDescription: string
  suggestedAction: string
  impactRatio?: number
}

export const changeApi = {
  list: (params?: { status?: string; changeType?: string; page?: number; size?: number }) =>
    request.get<any, AxiosResponse<{ code: number; data: ChangeRequest[] }>>('/changes/list', { params }),

  getPending: () =>
    request.get<any, AxiosResponse<{ code: number; data: ChangeRequest[] }>>('/changes/pending'),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}`),

  create: (data: ChangeRequest) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>('/changes', data),

  update: (id: number, data: Partial<ChangeRequest>) =>
    request.put<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}`, data),

  submit: (id: number) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/submit`),

  approve: (id: number, approverId: number, decision: string, comments?: string) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/approve`, null, {
      params: { approverId, decision, comments }
    }),

  reject: (id: number, reason: string) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/reject`, null, {
      params: { reason }
    }),

  execute: (id: number, data?: any) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/execute`, data),

  verify: (id: number) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/verify`),

  close: (id: number, closureNote?: string) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/close`, null, {
      params: { closureNote }
    }),

  assess: (id: number) =>
    request.post<any, AxiosResponse<{ code: number; data: ChangeRequest }>>(`/changes/${id}/assess`),
}

export const impactAssessmentApi = {
  listByChange: (changeRequestId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: ImpactAssessment[] }>>(
      `/changes/${changeRequestId}/impacts`
    ),

  // R274：批量影响评估（避免 ChangeList N+1）
  listByChanges: (changeIds: number[]) =>
    request.get<any, AxiosResponse<{ code: number; data: ImpactAssessment[] }>>(
      `/changes/impacts/batch`, { params: { ids: changeIds.join(',') } }
    ),

  // CODE_REVIEW C-NEW：create/update 已删除——后端无 /changes/impact POST/PUT 的 Controller（死代码）。
  // 影响评估的实际写入口为后端 ChangeController 的 /{id}/assess（C-2 已确认存在）。
}