import request from './request'
import type { AxiosResponse } from 'axios'

export interface Report {
  id: number
  reportType: string
  title: string
  projectId: number
  generatedAt: string
  generatedBy: number
}

// v1.46 P1-后端-1：报表配置持久化载体
export interface ReportConfig {
  id: number
  name: string
  description?: string
  reportType: string
  projectId?: number
  fieldsJson: string
  filtersJson?: string
  createdBy?: number
  createdByName?: string
  isShared: boolean
  createdAt?: string
  updatedAt?: string
}

export const reportApi = {
  list: (params?: { projectId?: number; reportType?: string }) =>
    request.get<any, AxiosResponse<{ code: number; data: Report[] }>>('/reports', { params }),

  generate: (data: { reportType: string; projectId: number }) =>
    request.post<any, AxiosResponse<{ code: number; data: Report }>>('/reports/generate', data),

  download: (id: number) =>
    request.get<any, AxiosResponse<Blob>>(`/reports/${id}/download`, { responseType: 'blob' }),

  // v1.46 P1-后端-1：报表配置 CRUD
  listConfigs: (params?: { userId?: number; reportType?: string; projectId?: number }) =>
    request.get<any, AxiosResponse<{ code: number; data: ReportConfig[] }>>('/reports/configs', { params }),

  getConfig: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: ReportConfig }>>(`/reports/configs/${id}`),

  createConfig: (data: Partial<ReportConfig>) =>
    request.post<any, AxiosResponse<{ code: number; data: ReportConfig }>>('/reports/configs', data),

  updateConfig: (id: number, data: Partial<ReportConfig>) =>
    request.put<any, AxiosResponse<{ code: number; data: ReportConfig }>>(`/reports/configs/${id}`, data),

  deleteConfig: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number; data: null }>>(`/reports/configs/${id}`),
}