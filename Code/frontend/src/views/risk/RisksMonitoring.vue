<!--
  RisksMonitoring.vue - 风险监控列表
  v1.53 P1-17 修复：实时状态、最近评审、即将到期
-->
<template>
  <div class="risks-monitoring-container">
    <div class="page-header">
      <div class="page-title">风险监控中心</div>
      <div class="header-actions">
        <el-button @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <el-card>
          <el-statistic title="实时开放风险" :value="stats.open" :value-style="{ color: '#f56c6c' }" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <el-statistic title="7 天内到期" :value="stats.due7" :value-style="{ color: '#e6a23c' }" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <el-statistic title="超期未关闭" :value="stats.overdue" :value-style="{ color: '#f56c6c' }" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <el-statistic title="本周已评审" :value="stats.reviewedThisWeek" :value-style="{ color: '#67c23a' }" />
        </el-card>
      </el-col>
    </el-row>

    <el-card>
      <template #header>
        <span>即将到期风险（30 天内）</span>
      </template>
      <el-table :data="upcomingDue" border size="small" v-loading="loading">
        <el-table-column prop="riskNo" label="编号" width="140" />
        <el-table-column prop="riskTitle" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="getSevType(row.severity)" size="small">
              {{ getSevLabel(row.severity) }} ({{ sevNum(row.severity) }})
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="截止日期" width="120" />
        <el-table-column label="剩余天数" width="100">
          <template #default="{ row }">
            <el-tag :type="getDaysType(row.daysLeft)" size="small">{{ row.daysLeft }} 天</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="责任人" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <span>实时风险状态</span>
      </template>
      <el-table :data="risks" border size="small">
        <el-table-column prop="riskNo" label="编号" width="140" />
        <el-table-column prop="riskTitle" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="severity" label="严重度" width="120">
          <template #default="{ row }">
            <el-tag :type="getSevType(row.severity)" size="small">
              {{ getSevLabel(row.severity) }} ({{ sevNum(row.severity) }})
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="probability" label="概率" width="80" />
        <el-table-column prop="riskLevel" label="等级" width="80">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="ownerName" label="责任人" width="100" />
        <el-table-column prop="lastReviewAt" label="最近评审" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface RiskItem {
  id: number; riskNo: string; riskTitle: string; severity: string; probability: string;
  riskLevel: string; status: string; ownerName?: string; dueDate?: string; lastReviewAt?: string
}

const loading = ref(false)
const risks = ref<RiskItem[]>([])

const SEV_LABELS: Record<string, string> = { CRITICAL: '灾难性', MAJOR: '严重', MINOR: '轻度', NEGLIGIBLE: '可忽略' }
const SEV_TYPES: Record<string, string> = { CRITICAL: 'danger', MAJOR: 'danger', MINOR: 'warning', NEGLIGIBLE: 'info' }
const SEV_NUMS: Record<string, number> = { CRITICAL: 4, MAJOR: 3, MINOR: 2, NEGLIGIBLE: 1 }
const getSevLabel = (s: string) => SEV_LABELS[s] || s
const getSevType = (s: string) => SEV_TYPES[s] || 'info'
const sevNum = (s: string) => SEV_NUMS[s] || 0
const getLevelType = (l: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }[l] || 'info')
const getDaysType = (d: number) => d < 0 ? 'danger' : d <= 7 ? 'danger' : d <= 14 ? 'warning' : 'info'

const daysUntil = (d?: string) => {
  if (!d) return 9999
  const target = new Date(d).getTime()
  const now = Date.now()
  return Math.ceil((target - now) / (1000 * 60 * 60 * 24))
}

const upcomingDue = computed(() => {
  return risks.value
    .map(r => ({ ...r, daysLeft: daysUntil(r.dueDate) }))
    .filter(r => r.daysLeft <= 30 && r.status !== 'CLOSED' && r.status !== 'ACCEPTED')
    .sort((a, b) => a.daysLeft - b.daysLeft)
})

const stats = computed(() => {
  const open = risks.value.filter(r => r.status === 'OPEN' || r.status === 'IN_PROGRESS').length
  const due7 = upcomingDue.value.filter(r => r.daysLeft <= 7).length
  const overdue = upcomingDue.value.filter(r => r.daysLeft < 0).length
  const oneWeekAgo = Date.now() - 7 * 24 * 3600 * 1000
  const reviewedThisWeek = risks.value.filter(r => r.lastReviewAt && new Date(r.lastReviewAt).getTime() > oneWeekAgo).length
  return { open, due7, overdue, reviewedThisWeek }
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get('/risk/register/list')
    risks.value = (res.data?.data || []) as RiskItem[]
  } catch (e: any) {
    ElMessage.error('加载风险数据失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.risks-monitoring-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; }
.header-actions { display: flex; gap: 10px; }
</style>
