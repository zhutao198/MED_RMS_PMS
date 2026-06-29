<template>
  <div class="operation-logs-container">
    <div class="page-header">
      <div class="page-title">操作日志（业务操作审计）</div>
      <div class="header-actions">
        <el-button type="primary" plain @click="$router.push('/audit-logs')">查看完整审计日志</el-button>
      </div>
    </div>
    <!-- R92 修复：操作日志功能由 AuditLog 模块承担，无独立后端模块。
         R87 当时误判为"功能开发中"。改为直接调用 /compliance/audit-logs -->
    <el-card v-loading="loading">
      <el-form :inline="true" class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索操作人/对象" clearable style="width: 220px;" @input="onKeywordInput" />
        <el-select v-model="entityFilter" placeholder="实体类型" clearable style="width: 160px;" @change="loadLogs">
          <el-option label="需求" value="REQUIREMENT" />
          <el-option label="变更" value="CHANGE_REQUEST" />
          <el-option label="基线" value="BASELINE" />
          <el-option label="电子签名" value="SIGNATURE_RECORD" />
          <el-option label="测试用例" value="TEST_CASE" />
        </el-select>
        <el-select v-model="actionFilter" placeholder="操作类型" clearable style="width: 140px;" @change="loadLogs">
          <el-option label="创建" value="CREATE" />
          <el-option label="修改" value="MODIFY" />
          <el-option label="删除" value="DELETE" />
          <el-option label="状态变更" value="STATUS_CHANGE" />
          <el-option label="签名" value="SIGN" />
        </el-select>
      </el-form>

      <el-table :data="filteredLogs" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="eventType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getEventType(row.eventType)">{{ row.eventType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entityType" label="实体" width="140" />
        <el-table-column prop="entityId" label="实体ID" width="100" />
        <el-table-column prop="operation" label="操作描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="140" />
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="filteredLogs.length === 0 && !loading" description="暂无操作日志" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const logs = ref<any[]>([])
const keyword = ref('')
const entityFilter = ref('')
const actionFilter = ref('')

const formatDate = (d?: string) => d ? d.replace('T', ' ').substring(0, 19) : '-'

const EVENT_TYPE_TAG: Record<string, string> = {
  CREATE: 'success', MODIFY: 'primary', DELETE: 'danger', STATUS_CHANGE: 'warning', SIGN: 'info', LOGIN: 'info', APPROVE: 'success',
}
const getEventType = (t: string) => EVENT_TYPE_TAG[t] || 'info'

const filteredLogs = computed(() => {
  return logs.value.filter(l => {
    if (keyword.value) {
      const k = keyword.value.toLowerCase()
      if (!(l.operatorName || '').toLowerCase().includes(k) &&
          !(l.entityType || '').toLowerCase().includes(k) &&
          !(l.operation || '').toLowerCase().includes(k)) return false
    }
    if (entityFilter.value && l.entityType !== entityFilter.value) return false
    if (actionFilter.value && l.eventType !== actionFilter.value) return false
    return true
  })
})

const loadLogs = async () => {
  loading.value = true
  try {
    // WHY: 复用审计日志接口，前端用 operation 字段过滤掉 LOGIN（避免 event_type 字段缺失问题）
    const res = await request.get('/compliance/audit-logs', { params: { size: 200 } })
    logs.value = (res.data?.data || []).filter((a: any) => a.operation !== 'LOGIN')
  } catch (e: any) {
    ElMessage.error('加载操作日志失败：' + (e?.response?.data?.message || e?.message))
    logs.value = []
  } finally {
    loading.value = false
  }
}

// R108 F2 修复：搜索框加 300ms 防抖，避免每次按键都触发后端请求（输入"zhang"从 5 次降到 1 次）
let keywordTimer: ReturnType<typeof setTimeout> | null = null
const onKeywordInput = () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => loadLogs(), 300)
}
onBeforeUnmount(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})

onMounted(loadLogs)
</script>

<style scoped>
.operation-logs-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; }
.header-actions { display: flex; gap: 8px; }
.filter-bar { margin-bottom: 12px; }
</style>
