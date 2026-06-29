import request from './request'
import type { AxiosResponse } from 'axios'

export interface RiskAssessment {
  id: number
  requirementId: number
  riskLevel: string
  hazardLevel: string
  riskScore: number
  controlMeasure: string
  residualRisk: string
  assessedBy: number
  assessedAt: string
}

export interface RiskRegister {
  id?: number
  riskNo?: string
  riskTitle: string
  category: string
  severity: string
  probability: string
  detectability: string
  riskLevel: string
  description?: string
  rootCause?: string
  controlMeasure?: string
  responseStrategy?: string
  status: string
  ownerId?: number
  ownerName?: string
  dueDate?: string
  closedAt?: string
  closureNote?: string
}

export interface RiskMatrix {
  id?: number
  projectId: number
  projectNo?: string
  matrixType: string
  severity: string
  probability: string
  detectability: string
  rpn?: number
  riskLevel?: string
  riskZone?: string
  description?: string
  mitigationMeasure?: string
  residualRisk?: string
  residualRpn?: number
  assessedAt?: string
  assessedBy?: number
}

export interface RiskReport {
  totalRisks: number
  highRisks: number
  mediumRisks: number
  lowRisks: number
  uncontrolledRisks: number
}

export const riskApi = {
  assess: (data: { requirementId: number; riskLevel: string; hazardLevel: string; controlMeasure: string }) =>
    request.post<any, AxiosResponse<{ code: number; data: RiskAssessment }>>('/risk/assess', data),

  getByRequirement: (requirementId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskAssessment[] }>>(`/risk/requirement/${requirementId}`),

  getReport: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskReport }>>(`/risk/report/${projectId}`),

  updateControl: (id: number, controlMeasure: string) =>
    request.put<any, AxiosResponse<{ code: number; data: RiskAssessment }>>(`/risk/${id}/control`, { controlMeasure }),
}

export const riskRegisterApi = {
  list: (params?: { status?: string; category?: string }) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskRegister[] }>>('/risk/register/list', { params }),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskRegister }>>(`/risk/register/${id}`),

  create: (data: RiskRegister) =>
    request.post<any, AxiosResponse<{ code: number; data: RiskRegister }>>('/risk/register', data),

  update: (id: number, data: Partial<RiskRegister>) =>
    request.put<any, AxiosResponse<{ code: number; data: RiskRegister }>>(`/risk/register/${id}`, data),

  close: (id: number, closureNote: string) =>
    request.post<any, AxiosResponse<{ code: number; data: RiskRegister }>>(`/risk/register/${id}/close`, null, {
      params: { closureNote }
    }),

  accept: (id: number) =>
    request.post<any, AxiosResponse<{ code: number; data: RiskRegister }>>(`/risk/register/${id}/accept`),
}

export const riskMatrixApi = {
  listByProject: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskMatrix[] }>>(`/risk/matrix/list/${projectId}`),

  listByType: (matrixType: string) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskMatrix[] }>>('/risk/matrix/list', {
      params: { matrixType }
    }),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: RiskMatrix }>>(`/risk/matrix/${id}`),

  create: (data: RiskMatrix) =>
    request.post<any, AxiosResponse<{ code: number; data: RiskMatrix }>>('/risk/matrix', data),

  update: (id: number, data: Partial<RiskMatrix>) =>
    request.put<any, AxiosResponse<{ code: number; data: RiskMatrix }>>(`/risk/matrix/${id}`, data),

  calculateResidual: (id: number, mitigationMeasure: string) =>
    request.post<any, AxiosResponse<{ code: number; data: RiskMatrix }>>(`/risk/matrix/${id}/residual`, null, {
      params: { mitigationMeasure }
    }),
}