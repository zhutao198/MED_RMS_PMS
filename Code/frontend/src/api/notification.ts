import request from './request'
import type { AxiosResponse } from 'axios'

export interface EmailQueue {
  id?: number
  toAddress: string
  ccAddress?: string
  subject: string
  body: string
  status: string
  retryCount?: number
  errorMessage?: string
  sentAt?: string
  scheduledAt?: string
}

export interface Notification {
  id: number
  userId: number
  userName?: string
  title: string
  content: string
  type: string
  status: string
  sourceType?: string
  sourceId?: number
  readAt?: string
  createdAt: string
}

export interface NotificationSettings {
  id?: number
  userId: number
  inAppEnabled: boolean
  emailEnabled: boolean
  smsEnabled: boolean
  wechatEnabled: boolean
  emailAddress?: string
  phoneNumber?: string
  digestMode: string
}

export const notificationApi = {
  // 未读通知列表
  getUnread: (userId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: Notification[] }>>('/notifications/unread', {
      params: { userId }
    }),

  // 未读数量
  getUnreadCount: (userId: number) =>
    request.get<any, AxiosResponse<{ code: number; data: { count: number } }>>('/notifications/unread/count', {
      params: { userId }
    }),

  // v1.43 全部通知（支持 status/type 过滤）
  getAll: (userId: number, status?: string, type?: string) =>
    request.get<any, AxiosResponse<{ code: number; data: Notification[] }>>('/notifications/all', {
      params: {
        userId,
        status: status || undefined,
        type: type || undefined,
      }
    }),

  // 标记单条已读
  markAsRead: (id: number) =>
    request.put<any, AxiosResponse<{ code: number }>>(`/notifications/${id}/read`),

  // 全部标记已读
  markAllAsRead: (userId: number) =>
    request.put<any, AxiosResponse<{ code: number }>>('/notifications/read/all', null, {
      params: { userId }
    }),

  // v1.43 删除单条
  deleteNotification: (id: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>(`/notifications/${id}`),

  // v1.43 清空所有
  deleteAll: (userId: number) =>
    request.delete<any, AxiosResponse<{ code: number }>>('/notifications/all', {
      params: { userId }
    }),
}

// CODE_REVIEW C-NEW：以下 notificationAdminApi 已删除——前端 0 处调用，
// 且路径 /notification/* 与后端 /notifications/* 不符（后端无对应 Controller），属死代码。

