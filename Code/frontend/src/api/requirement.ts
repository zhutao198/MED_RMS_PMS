import api from './request'
import type { AxiosResponse } from 'axios'

export interface Requirement {
  id?: number
  requirementNo?: string
  requirementType: string
  projectId: number
  title: string
  description: string
  priority: string
  status: string
  riskLevel?: string
  safetyClass?: string
  createdAt?: string
}

interface PageData {
  records: Requirement[]
  total: number
  size: number
  current: number
  pages: number
}

interface PageResponse {
  code: number
  message: string
  data: PageData
  timestamp: number
}

export const requirementApi = {
  list(params: { projectId?: number; type?: string; status?: string; page?: number; size?: number }) {
    return api.get<any, AxiosResponse<PageResponse>>('/requirements', { params })
  },

  get(id: number) {
    return api.get<any, AxiosResponse<{ code: number; data: Requirement }>>(`/requirements/${id}`)
  },

  create(data: Requirement) {
    return api.post('/requirements', data)
  },

  // v1.43 P1-2 修复：批量创建（Excel/CSV 导入用）
  createBatch(data: Requirement[]) {
    return api.post('/requirements/batch', data)
  },

  update(id: number, data: Requirement) {
    return api.put(`/requirements/${id}`, data)
  },

  decompose(id: number, childRequirement: Requirement) {
    return api.post(`/requirements/${id}/decompose`, childRequirement)
  },

  submitReview(id: number, reviewerId: number, comments?: string) {
    return api.post(`/requirements/${id}/review`, null, { params: { reviewerId, comments } })
  },

  approve(id: number, decision: string, approverId: number, comments?: string) {
    return api.post(`/requirements/${id}/approve`, null, { params: { decision, approverId, comments } })
  },

  // v1.52 新增：手动创建需求版本快照
  // changeSummary 字段是 JSON 字符串，包含 summary + cti
  createVersion(id: number, changeSummary: string, versionNo?: string) {
    const body: Record<string, string> = { changeSummary }
    if (versionNo) body.versionNo = versionNo
    return api.post(`/requirements/${id}/versions`, body)
  },

  // v1.52 P1-1 新增：追溯计数（上游+下游）
  getTraceCount(id: number) {
    return api.get<any, AxiosResponse<{ code: number; data: { upstream: number; downstream: number } }>>(
      `/requirements/${id}/trace-count`
    )
  },

  // v1.52 P1-1 新增：测试用例计数
  getTestCaseCount(id: number) {
    return api.get<any, AxiosResponse<{ code: number; data: { count: number } }>>(
      `/requirements/${id}/test-case-count`
    )
  }
}