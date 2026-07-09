<template>
  <div class="soup-status-card">
    <div class="card-header">
      <span class="card-title">SOUP 合规状态</span>
      <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
    </div>
    <div v-if="loading && !stats" class="card-body skeleton">
      <div v-for="i in 4" :key="i" class="stat-box">
        <div class="skeleton-line skeleton-line--label" />
        <div class="skeleton-line skeleton-line--value" />
      </div>
    </div>
    <div v-else class="card-body">
      <div class="stat-box assessed">
        <span class="stat-label">已评估</span>
        <span class="stat-value">{{ stats?.assessed ?? '-' }}</span>
      </div>
      <div class="stat-box pending">
        <span class="stat-label">待评估</span>
        <span class="stat-value">{{ stats?.pending ?? '-' }}</span>
      </div>
      <div class="stat-box anomalies">
        <span class="stat-label">异常组件</span>
        <span class="stat-value">{{ stats?.anomalies ?? '-' }}</span>
      </div>
      <div class="stat-box total">
        <span class="stat-label">总组件数</span>
        <span class="stat-value">{{ stats?.total ?? '-' }}</span>
      </div>
    </div>
    <div v-if="error" class="card-error">
      <el-icon><WarningFilled /></el-icon>
      <span>数据加载失败</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { requestFetch } from '@/api/request'
import { Loading, WarningFilled } from '@element-plus/icons-vue'

interface SoupStats {
  total: number
  assessed: number
  pending: number
  anomalies: number
}

const props = defineProps<{ projectId: number }>()

const stats = ref<SoupStats | null>(null)
const loading = ref(false)
const error = ref(false)

async function fetchStats() {
  loading.value = true
  error.value = false
  try {
    const resp = await requestFetch(`/compliance/soup/stats?projectId=${props.projectId}`)
    if (resp && resp.ok) {
      const json = await resp.json()
      if (json.code === '0000' && json.data) {
        stats.value = json.data
      } else {
        stats.value = null
      }
    } else {
      stats.value = null
    }
  } catch {
    error.value = true
    stats.value = null
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchStats())
watch(() => props.projectId, () => fetchStats())
</script>

<style scoped>
.soup-status-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.card-body {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px 8px;
  border-radius: 6px;
  background: #f5f7fa;
  border-left: 4px solid #dcdfe6;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.assessed {
  border-left-color: #67c23a;
}
.assessed .stat-value {
  color: #67c23a;
}

.pending {
  border-left-color: #e6a23c;
}
.pending .stat-value {
  color: #e6a23c;
}

.anomalies {
  border-left-color: #f56c6c;
}
.anomalies .stat-value {
  color: #f56c6c;
}

.total {
  border-left-color: #409eff;
}
.total .stat-value {
  color: #409eff;
}

.card-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  padding: 8px 12px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 4px;
  font-size: 13px;
}

.skeleton {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.skeleton-line {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s ease-in-out infinite;
  border-radius: 4px;
}

.skeleton-line--label {
  height: 12px;
  width: 60%;
  margin: 0 auto 10px;
}

.skeleton-line--value {
  height: 28px;
  width: 40%;
  margin: 0 auto;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.is-loading {
  animation: rotating 2s linear infinite;
  color: #409eff;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
