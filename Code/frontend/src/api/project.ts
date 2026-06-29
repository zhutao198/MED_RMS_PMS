import request from './request'
import type { AxiosResponse } from 'axios'

export interface Project {
  id: number
  projectNo: string
  projectName: string
  description: string
  status: string
  managerId: number
  managerName?: string
  startDate: string
  endDate: string
}

export interface IpdGate {
  id?: number
  projectId: number
  projectNo?: string
  gateNo: number
  gateName: string
  gateType: string
  status: string
  plannedDate?: string
  actualDate?: string
  reviewer?: string
  decision?: string
  comment?: string
}

export interface ProjectMember {
  id?: number
  projectId: number
  projectNo?: string
  userId: number
  username?: string
  realName: string
  role: string
  department?: string
  joinedAt?: string
  status: string
}

export const projectApi = {
  list: (params?: { status?: string }) =>
    request.get<any, AxiosResponse<{ code: number; data: Project[] }>>('/projects', { params }),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: Project }>>(`/projects/${id}`),

  create: (data: Omit<Project, 'id'>) =>
    request.post<any, AxiosResponse<{ code: number; data: Project }>>('/projects', data),

  update: (id: number, data: Partial<Project>) =>
    request.put<any, AxiosResponse<{ code: number; data: Project }>>(`/projects/${id}`, data),

  delete: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/projects/${id}`),
}

export const ipdGateApi = {
  listByProject: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: IpdGate[] }>>(`/project/ipd-gate/list/${projectId}`),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: IpdGate }>>(`/project/ipd-gate/${id}`),

  create: (data: IpdGate) =>
    request.post<any, AxiosResponse<{ code: number; data: IpdGate }>>('/project/ipd-gate', data),

  update: (id: number, data: Partial<IpdGate>) =>
    request.put<any, AxiosResponse<{ code: number; data: IpdGate }>>(`/project/ipd-gate/${id}`, data),

  passGate: (id: number, decision: string, comment?: string) =>
    request.post<any, AxiosResponse<{ code: number; data: IpdGate }>>(`/project/ipd-gate/${id}/pass`, null, {
      params: { decision, comment }
    }),

  failGate: (id: number, comment: string) =>
    request.post<any, AxiosResponse<{ code: number; data: IpdGate }>>(`/project/ipd-gate/${id}/fail`, null, {
      params: { comment }
    }),
}

export const projectMemberApi = {
  listByProject: (projectId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: ProjectMember[] }>>(`/project/member/list/${projectId}`),

  get: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: ProjectMember }>>(`/project/member/${id}`),

  add: (data: ProjectMember) =>
    request.post<any, AxiosResponse<{ code: number; data: ProjectMember }>>('/project/member', data),

  update: (id: number, data: Partial<ProjectMember>) =>
    request.put<any, AxiosResponse<{ code: number; data: ProjectMember }>>(`/project/member/${id}`, data),

  remove: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/project/member/${id}`),

  switchRole: (id: number, role: string) =>
    request.post<any, AxiosResponse<{ code: number; data: ProjectMember }>>(`/project/member/${id}/switch-role`, null, {
      params: { role }
    }),
}