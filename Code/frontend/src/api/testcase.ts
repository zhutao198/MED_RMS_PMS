import request from './request'
import type { AxiosResponse } from 'axios'

/**
 * v1.48 P0 修复：测试用例 API 封装
 * 用于需求详情页"测试用例"Tab
 */
export interface TestCase {
  id: number
  testCaseNo: string
  title: string
  description?: string
  testType?: string
  testMethod?: string
  safetyClass?: string
  status: string
  preCondition?: string
  testSteps?: string
  expectedResult?: string
  requirementId?: number
  requirementNo?: string
  projectId?: number
  executedAt?: string
  createdAt?: string
  updatedAt?: string
}

export const testCaseApi = {
  /** 按需求 ID 获取关联测试用例列表（后端 /testcases/requirement/{id}） */
  listByRequirement: (requirementId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: TestCase[] }>>(
      `/testcases/requirement/${requirementId}`
    ),
}
