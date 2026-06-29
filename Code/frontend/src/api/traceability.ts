import request from './request'
import type { AxiosResponse } from 'axios'

/**
 * v1.48 P0 修复：追溯链接实体（后端 TraceLink）
 * 用于需求详情页"追溯关系"Tab
 */
export interface TraceLink {
  id: number
  linkType: string
  sourceType: string
  sourceId: number
  sourceNo?: string
  targetType: string
  targetId: number
  targetNo?: string
  projectId?: number
  traceContext?: string
  createdBy?: number
  createdAt?: string
}

export interface TraceMatrixItem {
  ursId: number
  ursNo: string
  ursName: string
  prsId: number
  prsNo: string
  prsName: string
  srsId: number
  srsNo: string
  srsName: string
  drsId: number
  drsNo: string
  drsName: string
}

export interface CoverageStats {
  total: number
  traced: number
  untraced: number
  coverageRate: number
  byType: Record<string, { total: number; traced: number; coverageRate: number }>
}

export interface TraceGap {
  type: 'MISSING_CHILD' | 'ORPHAN' | 'NO_TEST_CASE'
  requirementId: number
  requirementNo: string
  requirementName: string
  parentId?: number
  parentNo?: string
  parentName?: string
  expectedParentType?: string
  // v1.55 新增：后端返回的优先级（不再前端推断）
  priority?: 'MUST' | 'SHOULD' | 'COULD'
  // v1.55 新增：缺口状态（PENDING 已忽略 IGNORED 已修复 FIXED）
  status?: 'PENDING' | 'IGNORED' | 'FIXED'
}

export interface IgnoredGapRecord {
  gapType: string
  requirementId: number
  reason?: string
  ignoredAt: string
}

export interface ImportPreviewRow {
  rowIndex: number
  sourceNo?: string
  sourceType?: string
  targetNo?: string
  targetType?: string
  linkType?: string
  comment?: string
  errors: string[]
  // 兼容后端在 preview 时补的 ID 字段
  sourceId?: number
  targetId?: number
}

export interface ImportPreviewResult {
  validCount: number
  invalidCount: number
  validRows: ImportPreviewRow[]
  invalidRows: ImportPreviewRow[]
}

export interface ImportCommitResult {
  success: number
  failed: number
  errors: Array<{ sourceNo?: string; targetNo?: string; message: string }>
}

export const traceabilityApi = {
  getTraceMatrix: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: TraceMatrixItem[] }>>('/traceability/matrix', { params: { projectId } }),

  getCoverageStats: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: CoverageStats }>>('/traceability/coverage', { params: { projectId } }),

  getTraceGaps: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: TraceGap[] }>>('/traceability/gaps', { params: { projectId } }),

  addHorizontalRelation: (data: { sourceReqId: number; targetReqId: number; relationType: string }) =>
    request.post('/traceability/relations', data),

  addTestCaseTrace: (data: { requirementId: number; testCaseId: number; traceType?: string }) =>
    request.post('/traceability/testcases', data),

  // v1.48 P0 修复：按需求 ID 拉取追溯链（上游 by-target + 下游 by-source）
  listByRequirement: async (requirementId: number) => {
    const [upstream, downstream] = await Promise.all([
      request.get<any, AxiosResponse<{ code: number; data: TraceLink[] }>>(
        `/trace-links/by-target/${requirementId}`
      ),
      request.get<any, AxiosResponse<{ code: number; data: TraceLink[] }>>(
        `/trace-links/by-source/${requirementId}`
      ),
    ])
    return {
      upstream: upstream.data?.data || [],
      downstream: downstream.data?.data || [],
    }
  },

  // v1.55 修复：按 (source, target) 对查询 TraceLink（TraceMatrix 详情对话框用）
  listByPair: (sourceId: number, targetId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: TraceLink[] }>>(
      '/trace-links/by-pair',
      { params: { sourceId, targetId } }
    ),

  // v1.55 修复：追溯缺口忽略
  ignoreGap: (projectId: number, gapType: string, requirementId: number, reason?: string) =>
    request.post('/traceability/gaps/ignore', { projectId, gapType, requirementId, reason }),

  getIgnoredGaps: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: IgnoredGapRecord[] }>>(
      '/traceability/gaps/ignored',
      { params: { projectId } }
    ),

  // v1.55 修复：追溯数据导入（preview + commit）
  previewImport: (projectId: number, items: any[]) =>
    request.post<any, AxiosResponse<{ code: number; data: ImportPreviewResult }>>(
      '/traceability/import/preview',
      { items },
      { params: { projectId } }
    ),

  commitImport: (projectId: number, items: any[]) =>
    request.post<any, AxiosResponse<{ code: number; data: ImportCommitResult }>>(
      '/traceability/import',
      { items },
      { params: { projectId } }
    ),
}