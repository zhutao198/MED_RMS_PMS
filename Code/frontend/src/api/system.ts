import request from './request'
import type { AxiosResponse } from 'axios'

export interface User {
  id: number
  username: string
  realName: string
  email: string
  phone: string
  department: string
  role: string
  status: string
  lastLogin?: string
}

export interface Role {
  id: number
  roleName: string
  roleCode: string
  description: string
  userCount: number
  permissions?: string[]
}

export interface DictItem {
  id: number
  dictType: string
  dictCode: string
  dictName: string
  sortOrder?: number
}

export interface SystemConfig {
  id: number
  configName: string
  configKey: string
  configValue: string
  description?: string
}

export interface OrgNode {
  id: number
  label: string
  userCount: number
  children?: OrgNode[]
}

// R99 部门 CRUD DTO
export interface DepartmentDTO {
  id?: number
  parentId: number
  name: string
  code?: string
  sortOrder?: number
  level?: number
  path?: string
  leaderId?: number
}

// v1.46 P1-后端-2：角色权限矩阵
export interface Permission {
  id: number
  permCode: string
  permName: string
  permType: string  // MENU / BUTTON / API
  resourcePath?: string
  description?: string
  status?: string
}

export const systemApi = {
  getUsers: (params?: { department?: string; role?: string; status?: string }) =>
    request.get<any, AxiosResponse<{ code: number; data: User[] }>>('/system/users', { params }),

  getUser: (id: number) =>
    request.get<any, AxiosResponse<{ code: number; data: User }>>(`/system/users/${id}`),

  createUser: (data: Omit<User, 'id'>) =>
    request.post<any, AxiosResponse<{ code: number; data: User }>>('/system/users', data),

  updateUser: (id: number, data: Partial<User>) =>
    request.put<any, AxiosResponse<{ code: number; data: User }>>(`/system/users/${id}`, data),

  deleteUser: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/system/users/${id}`),

  resetPassword: (id: number) =>
    request.post<any, AxiosResponse<{ code: number }>>(`/system/users/${id}/reset-password`),

  getRoles: () =>
    request.get<any, AxiosResponse<{ code: number; data: Role[] }>>('/system/roles'),

  createRole: (data: Role) =>
    request.post<any, AxiosResponse<{ code: number; data: Role }>>('/system/roles', data),

  updateRole: (id: number, data: Partial<Role>) =>
    request.put<any, AxiosResponse<{ code: number; data: Role }>>(`/system/roles/${id}`, data),

  deleteRole: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/system/roles/${id}`),

  getDicts: (type: string) =>
    request.get<any, AxiosResponse<{ code: number; data: DictItem[] }>>('/system/dicts', { params: { type } }),

  getAllDicts: () =>
    request.get<any, AxiosResponse<{ code: number; data: DictItem[] }>>('/system/dicts/all'),

  createDict: (data: DictItem) =>
    request.post<any, AxiosResponse<{ code: number; data: DictItem }>>('/system/dicts', data),

  updateDict: (id: number, data: Partial<DictItem>) =>
    request.put<any, AxiosResponse<{ code: number; data: DictItem }>>(`/system/dicts/${id}`, data),

  deleteDict: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/system/dicts/${id}`),

  // R99 部门 CRUD（新版基于 t_department 表，支持任意层级）
  listDepartmentsByParent: (parentId?: number) =>
    request.get<any, AxiosResponse<{ code: number; data: DepartmentDTO[] }>>('/system/departments', { params: { parentId } }),

  getDepartmentTree: () =>
    request.get<any, AxiosResponse<{ code: number; data: OrgNode[] }>>('/system/departments/tree'),

  createDepartment: (data: DepartmentDTO) =>
    request.post<any, AxiosResponse<{ code: number; data: DepartmentDTO }>>('/system/departments', data),

  updateDepartment: (id: number, data: Partial<DepartmentDTO>) =>
    request.put<any, AxiosResponse<{ code: number; data: DepartmentDTO }>>(`/system/departments/${id}`, data),

  deleteDepartment: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/system/departments/${id}`),

  updateDepartmentSort: (id: number, sortOrder: number) =>
    request.post<any, AxiosResponse<{ code: number }>>(`/system/departments/${id}/sort`, null, { params: { sortOrder } }),

  getConfigs: () =>
    request.get<any, AxiosResponse<{ code: number; data: SystemConfig[] }>>('/system/configs'),

  updateConfig: (id: number, data: Partial<SystemConfig>) =>
    request.put<any, AxiosResponse<{ code: number; data: SystemConfig }>>(`/system/configs/${id}`, data),

  getOrgTree: () =>
    request.get<any, AxiosResponse<{ code: number; data: OrgNode[] }>>('/system/org/tree'),

  // v1.46 P1-后端-2：角色权限矩阵
  getAllPermissions: () =>
    request.get<any, AxiosResponse<{ code: number; data: Permission[] }>>('/system/permissions'),

  getRolePermissions: (roleId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: string[] }>>(`/system/roles/${roleId}/permissions`),

  updateRolePermissions: (roleId: number, permCodes: string[]) =>
    request.put<any, AxiosResponse<{ code: number; data: string[] }>>(`/system/roles/${roleId}/permissions`, { permCodes }),
}