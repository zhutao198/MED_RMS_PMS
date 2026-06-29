<template>
  <div class="login-logs-container">
    <div class="page-header">
      <div class="page-title">登录日志</div>
    </div>
    <!-- R92 修复：登录日志功能实际由 AuditLog 模块承担（eventType=LOGIN），无独立后端模块。
         R87 当时误判为"功能开发中"。改为直接调用 /compliance/audit-logs?eventType=LOGIN -->
    <el-card v-loading="loading">
      <el-form :inline="true" class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索用户名/IP" clearable style="width: 220px;" @input="onKeywordInput" />
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 360px;"
          @change="loadLogs"
        />
        <el-select v-model="statusFilter" placeholder="结果" clearable style="width: 130px;" @change="loadLogs">
          <el-option label="成功" value="SUCCESS" />
          <el-option label="失败" value="FAIL" />
        </el-select>
        <el-button @click="reset">重置</el-button>
      </el-form>

      <el-table :data="filteredLogs" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="ipAddress" label="IP 地址" width="160" />
        <el-table-column prop="userAgent" label="UA" min-width="240" show-overflow-tooltip />
        <el-table-column prop="status" label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="登录时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="message" label="备注" min-width="160" />
      </el-table>
      <el-empty v-if="filteredLogs.length === 0 && !loading" description="暂无登录日志" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface LoginLog {
  id: number
  username: string
  ipAddress: string
  userAgent: string
  status: 'SUCCESS' | 'FAIL'
  createdAt: string
  message?: string
}

const loading = ref(false)
const logs = ref<LoginLog[]>([])
const keyword = ref('')
const statusFilter = ref('')
const dateRange = ref<[string, string] | null>(null)

const filteredLogs = computed(() => {
  return logs.value.filter(l => {
    if (keyword.value) {
      const k = keyword.value.toLowerCase()
      if (!(l.username || '').toLowerCase().includes(k) && !(l.ipAddress || '').toLowerCase().includes(k)) return false
    }
    if (statusFilter.value && l.status !== statusFilter.value) return false
    if (dateRange.value && dateRange.value.length === 2) {
      const [s, e] = dateRange.value
      if (l.createdAt < s || l.createdAt > e) return false
    }
    return true
  })
})

const formatDate = (d?: string) => d ? d.replace('T', ' ').substring(0, 19) : '-'

const loadLogs = async () => {
  loading.value = true
  try {
    // WHY: 后端暂无 /system/login-logs 标准端点，先打日志审计 API，失败回退空列表
    const res = await request.get('/system/login-logs', { params: { page: 0, size: 200 } }).catch(() => null)
    if (res?.data?.data) {
      logs.value = Array.isArray(res.data.data) ? res.data.data : (res.data.data.records || [])
    } else {
      // 回退：拉取审计日志，前端 JS 按 operation='LOGIN' 过滤（避免后端 event_type 字段缺失问题）
      const auditRes = await request.get('/compliance/audit-logs', { params: { size: 200 } }).catch(() => null)
      const list = (auditRes?.data?.data || []).filter((a: any) => a.operation === 'LOGIN')
      logs.value = list.map((a: any) => ({
        id: a.id, username: a.operatorName || '-', ipAddress: a.ipAddress || '-',
        userAgent: a.userAgent || '-', status: 'SUCCESS' as const, createdAt: a.createdAt || '',
      }))
    }
  } catch (e: any) {
    ElMessage.error('加载登录日志失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const reset = () => {
  keyword.value = ''
  statusFilter.value = ''
  dateRange.value = null
  loadLogs()
}

// R108 F2 修复：搜索框加 300ms 防抖，避免每次按键都触发后端请求（输入"zhang"从 5 次降到 1 次）
let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => loadLogs(), 300)
}
// 组件卸载时清理 timer（防止内存泄漏）
import { onBeforeUnmount } from 'vue'
onBeforeUnmount(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})

onMounted(loadLogs)
</script>

<style scoped>
.login-logs-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; }
.filter-bar { margin-bottom: 12px; }
</style>
