<template>
  <div class="change-impact-container" v-loading="loading">
    <div class="page-header">
      <div class="page-title">变更影响分析</div>
      <div class="header-actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" @click="handleStartAnalysis" :loading="assessing" :disabled="!changeInfo.id">开始分析</el-button>
        <el-button type="success" @click="handleExport" :disabled="!impactDetails.length">导出报告</el-button>
      </div>
    </div>

    <div class="impact-info">
      <el-card class="info-card">
        <template #header>
          <span>变更单信息</span>
        </template>
        <el-descriptions :column="3" border size="small" v-if="changeInfo.changeNo">
          <el-descriptions-item label="变更单号">{{ changeInfo.changeNo }}</el-descriptions-item>
          <el-descriptions-item label="变更类型">
            <el-tag :type="getChangeTypeTag(changeInfo.changeType)" size="small">
              {{ changeInfo.changeType || '-' }}
            </el-tag>
          </el-descriptions-item>
 <!-- P3-4: urgency紧急度（与 priority并列；3档 NORMAL/URGENT/CRITICAL） -->
 <el-descriptions-item label="变更紧迫度">
 <el-tag :type="getUrgencyTag(changeInfo.urgency)" size="small">
 {{ getUrgencyLabel(changeInfo.urgency) }}
 </el-tag>
 </el-descriptions-item>
 <el-descriptions-item label="优先级">
 <el-tag :type="getPriorityTag(changeInfo.priority)" size="small">
 {{ changeInfo.priority || '-' }}
 </el-tag>
 </el-descriptions-item>

          <el-descriptions-item label="状态">
            <el-tag size="small">{{ changeInfo.status || '-' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请人">{{ changeInfo.requesterName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联需求 ID">{{ changeInfo.requirementId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="变更标题" :span="3">{{ changeInfo.title || '（无标题）' }}</el-descriptions-item>
          <el-descriptions-item label="变更原因" :span="3">{{ changeInfo.reason || '（未填写）' }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else description="变更单信息为空" :image-size="60" />
      </el-card>
    </div>

    <div class="analysis-options">
      <div class="section-title">影响分析维度</div>
      <div class="options-grid">
        <div
          v-for="option in analysisOptions"
          :key="option.id"
          class="option-card"
          :class="{ selected: selectedOptions.includes(option.id) }"
          @click="toggleOption(option.id)"
        >
          <div class="option-icon">{{ option.icon }}</div>
          <div class="option-name">{{ option.name }}</div>
          <div class="option-desc">{{ option.description }}</div>
        </div>
      </div>
    </div>

    <div class="result-panel">
      <div class="section-title">分析结果</div>
      <div class="result-summary">
        <div class="summary-card high">
          <div class="summary-value">{{ analysisResult.highCount }}</div>
          <div class="summary-label">高影响</div>
        </div>
        <div class="summary-card medium">
          <div class="summary-value">{{ analysisResult.mediumCount }}</div>
          <div class="summary-label">中影响</div>
        </div>
        <div class="summary-card low">
          <div class="summary-value">{{ analysisResult.lowCount }}</div>
          <div class="summary-label">低影响</div>
        </div>
        <div class="summary-card total">
          <div class="summary-value">{{ analysisResult.totalCount }}</div>
          <div class="summary-label">总计</div>
        </div>
      </div>

      <el-card class="result-details">
        <template #header>
          <div class="card-header-row">
            <span>影响详情（基于变更关联需求/追溯链/基线/测试用例）</span>
            <el-button size="small" text type="primary" @click="goChangeDetail" v-if="changeInfo.id">查看变更单</el-button>
          </div>
        </template>
        <el-table :data="impactDetails" border size="small" v-loading="loading">
          <el-table-column prop="itemNo" label="影响项" width="160">
            <template #default="{ row }">
              <span style="color:#909399" v-if="!row.itemNo">-</span>
              <span v-else>{{ row.itemNo }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="itemName" label="名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="itemType" label="类型" width="100" />
          <el-table-column prop="impactLevel" label="影响程度" width="100">
            <template #default="{ row }">
              <el-tag :type="getImpactLevelTag(row.impactLevel)" size="small">
                {{ row.impactLevel || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="impactType" label="影响类型" width="120" />
          <el-table-column prop="impactDescription" label="影响说明" min-width="200" show-overflow-tooltip />
          <el-table-column prop="suggestedAction" label="建议操作" width="150" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!loading && !impactDetails.length" description="尚未分析或无影响项" :image-size="80" />
      </el-card>
    </div>

    <div class="actions-footer">
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="handleStartAnalysis" :loading="assessing" :disabled="!changeInfo.id">重新分析</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const assessing = ref(false)

const changeInfo = ref<any>({})
const impactDetails = ref<any[]>([])

const analysisOptions = ref([
  { id: 1, name: '需求追溯', icon: '🔗', description: '分析对需求追溯链的影响' },
  { id: 2, name: '测试用例', icon: '🧪', description: '分析对测试用例的影响' },
  { id: 3, name: '基线影响', icon: '📋', description: '分析对已锁定基线的影响' },
  { id: 4, name: 'SOUP组件', icon: '📦', description: '分析对SOUP组件的影响' }
])

const selectedOptions = ref([1, 2, 3, 4])

const analysisResult = computed(() => {
  const list = impactDetails.value
  return {
    highCount: list.filter((x: any) => (x.impactLevel || '').toUpperCase() === 'HIGH').length,
    mediumCount: list.filter((x: any) => (x.impactLevel || '').toUpperCase() === 'MEDIUM').length,
    lowCount: list.filter((x: any) => (x.impactLevel || '').toUpperCase() === 'LOW').length,
    totalCount: list.length
  }
})

const toggleOption = (id: number) => {
  const index = selectedOptions.value.indexOf(id)
  if (index > -1) {
    selectedOptions.value.splice(index, 1)
  } else {
    selectedOptions.value.push(id)
  }
}

const getChangeTypeTag = (type: string) => {
  const map: Record<string, string> = {
    CORRECTIVE: 'danger',
    ADAPTIVE: 'warning',
    PERFECTIVE: 'success',
    EMERGENCY: 'danger'
  }
  return map[type] || 'info'
}

// P3-4：紧急度（与优先级并列：普通5d /紧急2d /关键立即）
// 使用 NORMAL/URGENT/CRITICAL3档枚举，与 ChangeCreate/ChangeList/ChangeApprovals保持一致
const URGENCY_TAG: Record<string, string> = {
 NORMAL: 'info', // 普通：5天 SLA
 URGENT: 'warning', //紧急：2天 SLA
 CRITICAL: 'danger', //关键：立即处理
}
const URGENCY_LABEL: Record<string, string> = {
 NORMAL: '普通(5d)',
 URGENT: '紧急(2d)',
 CRITICAL: '关键(立即)',
}
const getUrgencyTag = (u?: string) => URGENCY_TAG[u || ''] || 'info'
const getUrgencyLabel = (u?: string) => URGENCY_LABEL[u || ''] || (u || '-')

const getPriorityTag = (priority: string) => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info',
    URGENT: 'danger',
    NORMAL: 'info'
  }
  return map[priority] || 'info'
}

const getImpactLevelTag = (level: string) => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return map[level] || 'info'
}

const loadChange = async () => {
  const id = Number(route.params.id)
  if (!id || Number.isNaN(id)) {
    ElMessage.error('变更单 ID 缺失')
    router.replace('/changes')
    return
  }
  loading.value = true
  try {
    const res = await request.get(`/changes/${id}`)
    if (res.data?.data) changeInfo.value = res.data.data
  } catch (e: any) {
    ElMessage.error('加载变更单失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
  await loadImpacts()
}

const loadImpacts = async () => {
  const id = Number(route.params.id)
  if (!id) return
  try {
    const res = await request.get(`/changes/${id}/impacts`)
    impactDetails.value = res.data?.data || []
  } catch {
    impactDetails.value = []
  }
}

const handleStartAnalysis = async () => {
  const id = changeInfo.value.id
  if (!id) return
  assessing.value = true
  try {
    await request.post(`/changes/${id}/assess`)
    ElMessage.success('影响分析已触发，正在刷新结果')
    await loadImpacts()
  } catch (e: any) {
    ElMessage.error('影响分析失败：' + (e?.response?.data?.message || e.message))
  } finally {
    assessing.value = false
  }
}

const handleExport = () => {
  if (!impactDetails.value.length) {
    ElMessage.warning('暂无影响项可导出')
    return
  }
  const headers = ['编号', '名称', '类型', '影响程度', '影响类型', '影响说明', '建议操作']
  const lines = [headers.join(',')]
  impactDetails.value.forEach((r: any) => {
    const cells = [
      r.itemNo || '-',
      `"${(r.itemName || '').replace(/"/g, '""')}"`,
      r.itemType || '-',
      r.impactLevel || '-',
      r.impactType || '-',
      `"${(r.impactDescription || '').replace(/"/g, '""')}"`,
      `"${(r.suggestedAction || '').replace(/"/g, '""')}"`
    ]
    lines.push(cells.join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `变更影响分析报告-${changeInfo.value.changeNo || id}-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${impactDetails.value.length} 条影响项`)
}

const goChangeDetail = () => {
  const id = changeInfo.value.id
  if (id) router.push(`/changes/${id}`)
}

onMounted(loadChange)
</script>

<style scoped>
.change-impact-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.impact-info {
  margin-bottom: 20px;
}

.options-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.option-card {
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.option-card:hover {
  border-color: #409eff;
}

.option-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.option-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.option-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.option-desc {
  font-size: 12px;
  color: #909399;
}

.scope-panel {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.scope-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.result-panel {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.summary-card {
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.summary-card.high {
  background: linear-gradient(135deg, #f56c6c, #d44040);
  color: #fff;
}

.summary-card.medium {
  background: linear-gradient(135deg, #e6a23c, #c77c24);
  color: #fff;
}

.summary-card.low {
  background: linear-gradient(135deg, #67c23a, #529b2e);
  color: #fff;
}

.summary-card.total {
  background: linear-gradient(135deg, #409eff, #337ecc);
  color: #fff;
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
}

.summary-label {
  font-size: 12px;
  margin-top: 4px;
  opacity: 0.9;
}

.actions-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
</style>