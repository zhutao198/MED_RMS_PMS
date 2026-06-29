<template>
  <div class="change-execute-container">
    <div class="page-header">
      <div>
        <el-button text @click="$router.back()">← 返回</el-button>
        <span class="page-title">执行变更 · {{ change?.changeCode || changeId }}</span>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="submitting" :disabled="!allDone" @click="handleSubmit">全部完成，确认执行</el-button>
      </div>
    </div>

    <el-card v-loading="loading" class="info-card">
      <template #header>
        <div class="card-header">
          <span>变更基本信息</span>
          <el-tag v-if="change" :type="statusType(change.status)">{{ STATUS_ZH[change.status] || change.status }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="变更编号">{{ change?.changeCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="变更类型">{{ change?.changeType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">{{ change?.urgency || '-' }}</el-descriptions-item>
        <el-descriptions-item label="影响范围" :span="3">{{ change?.impactScope || '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="3">{{ change?.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="progress-card">
      <template #header>
        <div class="card-header">
          <span>执行进度</span>
          <span class="progress-text">{{ completedCount }} / {{ tasks.length }}（{{ percent }}%）</span>
        </div>
      </template>
      <el-progress :percentage="percent" :stroke-width="14" :status="percent === 100 ? 'success' : ''" />
    </el-card>

    <el-card class="task-card">
      <template #header>
        <div class="card-header">
          <span>执行清单（基于影响评估自动生成）</span>
          <span class="hint">提示：勾选后状态自动切换</span>
        </div>
      </template>
      <el-table :data="tasks" border stripe>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="affectedItem" label="影响项" min-width="180" />
        <el-table-column prop="impactLevel" label="影响等级" width="120">
          <template #default="{ row }">
            <el-tag :type="impactType(row.impactLevel)">{{ row.impactLevel || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="execStatusType(row.execStatus)">{{ execStatusLabel(row.execStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="负责人" width="140">
          <template #default="{ row }">
            <span>{{ row.ownerName || '未指派' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划日期" width="140">
          <template #default="{ row }">
            <span>{{ row.plannedDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button v-if="row.execStatus !== 'DONE'" size="small" type="success" @click="setStatus(row, 'DONE')">标记完成</el-button>
            <el-button v-else size="small" @click="setStatus(row, 'IN_PROGRESS')">重做</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { request } from '@/api/request'
import { CHANGE_STATUS_ZH } from '@/utils/zh-mapping'

const route = useRoute()
const router = useRouter()
const changeId = Number(route.params.id)
const loading = ref(false)
const submitting = ref(false)
const change = ref<any>(null)
const tasks = ref<any[]>([])
const STATUS_ZH = CHANGE_STATUS_ZH

const loadData = async () => {
  loading.value = true
  try {
    const [c, im] = await Promise.all([
      request.get(`/changes/${changeId}`),
      request.get(`/changes/${changeId}/impacts`)
    ])
    change.value = c.data?.data || null
    const impacts = im.data?.data || []
    tasks.value = impacts.map((it: any) => ({
      ...it,
      execStatus: 'PENDING',
      ownerName: it.ownerName || '未指派',
      plannedDate: it.plannedDate || '-'
    }))
  } catch (e: any) {
    ElMessage.error(`加载失败：${e?.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

const setStatus = (row: any, status: string) => {
  row.execStatus = status
}

const completedCount = computed(() => tasks.value.filter(t => t.execStatus === 'DONE').length)
const percent = computed(() => tasks.value.length === 0 ? 0 : Math.round(completedCount.value / tasks.value.length * 100))
const allDone = computed(() => tasks.value.length > 0 && completedCount.value === tasks.value.length)

const statusType = (s: string) => ({
  DRAFT: 'info', SUBMITTED: 'primary', ANALYZING: 'warning', PENDING_APPROVAL: 'warning',
  APPROVED: 'success', EXECUTING: 'warning', VERIFIED: 'success', CLOSED: 'info', CANCELLED: 'danger'
} as any)[s] || ''

const impactType = (l: string) => ({ HIGH: 'danger', MAJOR: 'danger', MEDIUM: 'warning', MINOR: 'info', LOW: 'success' } as any)[l] || ''
const execStatusType = (s: string) => ({ PENDING: 'info', IN_PROGRESS: 'warning', DONE: 'success' } as any)[s] || ''
const execStatusLabel = (s: string) => ({ PENDING: '待办', IN_PROGRESS: '进行中', DONE: '完成' } as any)[s] || s

const handleSubmit = async () => {
  submitting.value = true
  try {
    await request.post(`/changes/${changeId}/execute`, null)
    ElMessage.success('执行完成')
    router.push(`/changes/${changeId}`)
  } catch (e: any) {
    ElMessage.error(`提交失败：${e?.message || '未知错误'}`)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.change-execute-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: #303133; margin-left: 12px; }
.header-actions { display: flex; gap: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-size: 15px; font-weight: 600; }
.progress-text { font-size: 13px; color: #909399; }
.info-card, .progress-card, .task-card { margin-bottom: 16px; border-radius: 8px; }
.hint { font-size: 12px; color: #909399; }
</style>
