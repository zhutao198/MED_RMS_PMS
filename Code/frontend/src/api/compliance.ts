import request from './request'
import type { AxiosResponse } from 'axios'

export interface ComplianceRecord {
  id: number
  requirementId: number
  regulationType: string
  checkItem: string
  checkResult: string
  status: string
  checkedBy: number
  checkedAt: string
}

export interface SoupComponent {
  id?: number
  componentName: string
  componentCode: string
  version: string
  supplier: string
  supplierCountry: string
  softwareCategory: string
  complianceStandard: string
  usageScenario: string
  integrationLevel: string
  riskLevel: string
  certificationDoc?: string
  licenseType?: string
  licenseExpiry?: string
  status: string
  securityDisclosure?: string
  maintainedBy?: string
  lastSecurityUpdate?: string
}

export const complianceApi = {
  getRecords: (params: { projectId?: number; requirementId?: number; status?: string }) =>
    request.get<any, AxiosResponse<{ code: number; data: ComplianceRecord[] }>>('/compliance/check/project/' + (params.projectId || '0'), { params }),

  getByRequirement: (requirementId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: ComplianceRecord[] }>>(`/compliance/check/list/${requirementId}`),

  check: (data: { requirementId: number; regulationType: string; checkItem: string; checkResult: string }) =>
    request.post<any, AxiosResponse<{ code: number; data: ComplianceRecord }>>('/compliance/check', data),
}

export const soupApi = {
  list: (params?: { status?: string; riskLevel?: string }) =>
    request.get<any, AxiosResponse<{ code: number; data: SoupComponent[] }>>('/soup', { params }),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: SoupComponent }>>(`/soup/${id}`),

  create: (data: SoupComponent) =>
    request.post<any, AxiosResponse<{ code: number; data: SoupComponent }>>('/soup', data),

  update: (id: number, data: Partial<SoupComponent>) =>
    request.put<any, AxiosResponse<{ code: number; data: SoupComponent }>>(`/soup/${id}`, data),

  delete: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/soup/${id}`),

  renew: (id: number) =>
    request.post<any, AxiosResponse<{ code: number; data: SoupComponent }>>(`/soup/${id}/renew`),
}