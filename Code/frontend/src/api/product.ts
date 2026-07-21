import request from './request'

/**
 * R199 v1.62: 产品管理 API
 * 与后端 med-rms-product ProductController 对齐（8 个端点）
 */

export interface Product {
  id?: number
  productCode: string
  productName: string
  productLine?: string
  status?: 'ACTIVE' | 'DISCONTINUED' | 'DEVELOPMENT'
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface ProductCreateRequest {
  productCode: string
  productName: string
  productLine?: string
  status?: 'ACTIVE' | 'DISCONTINUED' | 'DEVELOPMENT'
  description?: string
}

export interface ProductUpdateRequest {
  productName: string
  productLine?: string
  status?: 'ACTIVE' | 'DISCONTINUED' | 'DEVELOPMENT'
  description?: string
}

export const productApi = {
  /** 分页查询 */
  list(params?: { keyword?: string; productLine?: string; status?: string; page?: number; size?: number }) {
    return request.get('/products', { params })
  },
  /** 获取所有 ACTIVE 产品（下拉框使用，5min 缓存） */
  all() {
    return request.get<Product[]>('/products/all')
  },
  /** 详情 */
  get(id: number) {
    return request.get<Product>(`/products/${id}`)
  },
  /** 创建（需双签：secondSignerId 头部） */
  create(data: ProductCreateRequest, secondSignerId: number) {
    return request.post<Product>('/products', data, {
      headers: { 'X-Second-Signer-Id': String(secondSignerId) }
    })
  },
  /** 编辑（需双签） */
  update(id: number, data: ProductUpdateRequest, secondSignerId: number) {
    return request.put<Product>(`/products/${id}`, data, {
      headers: { 'X-Second-Signer-Id': String(secondSignerId) }
    })
  },
  /** 删除（需双签，软删除） */
  delete(id: number, secondSignerId: number) {
    return request.delete(`/products/${id}`, {
      headers: { 'X-Second-Signer-Id': String(secondSignerId) }
    })
  },
  /** Excel 导出 */
  export(params?: { keyword?: string; productLine?: string }) {
    return request.get('/products/export', { params, responseType: 'blob' })
  }
}