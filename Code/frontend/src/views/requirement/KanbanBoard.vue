<template>
  <div class="kanban-container">
    <div class="page-title">
      <h2>需求看板（FR-1.1）</h2>
      <div class="header-actions">
        <el-select v-model="filterProject" placeholder="选择项目" clearable style="width: 260px;" @change="loadAll">
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button @click="loadAll">刷新</el-button>
      </div>
    </div>

    <div class="kanban-board" v-loading="loading">
      <div
        class="kanban-column"
        v-for="col in columns"
        :key="col.key"
        @dragover.prevent
        @drop="onDrop($event, col.key)"
      >
        <div class="column-header" :style="{ background: col.color }">
          <span>{{ col.label }}</span>
          <el-tag size="small" effect="dark">{{ (grouped[col.key] || []).length }}</el-tag>
        </div>
        <div class="column-body">
          <el-card
            v-for="r in (grouped[col.key] || [])"
            :key="r.id"
            class="req-card"
            shadow="hover"
            draggable="true"
            @dragstart="onDragStart($event, r)"
            @click="$router.push(`/requirements/${r.id}`)"
          >
            <div class="rc-row1">
              <span class="rc-no">{{ r.requirementNo }}</span>
              <el-tag size="small" :type="typeTag(r.requirementType)">{{ r.requirementType }}</el-tag>
            </div>
            <div class="rc-title">{{ r.title }}</div>
            <div class="rc-row2">
              <el-tag v-if="r.priority" size="small" :type="priorityTag(r.priority)">{{ r.priority }}</el-tag>
              <span v-if="r.isSuspect" class="rc-suspect">⚠ Suspect</span>
            </div>
          </el-card>
          <el-empty v-if="(grouped[col.key] || []).length === 0" :image-size="40" description="无需求" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const columns = [
  { key: 'Draft', label: '草稿', color: '#909399' },
  { key: 'Submitted', label: '已提交', color: '#E6A23C' },
  { key: 'InReview', label: '评审中', color: '#FFB300' },
  { key: 'ReviewApproved', label: '评审通过', color: '#8BC34A' },
  { key: 'ReviewRejected', label: '评审拒绝', color: '#FF7043' },
  { key: 'Approved', label: '已批准', color: '#67C23A' },
  { key: 'Rejected', label: '已拒绝', color: '#F56C6C' },
  { key: 'InProgress', label: '进行中', color: '#409EFF' },
  { key: 'InTest', label: '测试中', color: '#9C27B0' },
  { key: 'Verified', label: '已验证', color: '#00B894' },
  { key: 'Baseline', label: '基线化', color: '#1A1A2E' },
  { key: 'Decomposed', label: '已拆解', color: '#607D8B' },
  { key: 'Suspect', label: 'Suspect', color: '#FF5722' },
  { key: 'Withdrawn', label: '已撤回', color: '#795548' }
]

const projectList = ref<any[]>([])
const filterProject = ref<number | null>(null)
const grouped = ref<Record<string, any[]>>({})
const loading = ref(false)

const typeTag = (t: string) => ({ URS: 'primary', PRS: 'success', SRS: 'warning', DRS: 'info' } as any)[t] || ''
const priorityTag = (p: string) => ({ MUST: 'danger', SHOULD: 'warning', MAY: 'info' } as any)[p] || 'info'

// ===== 拖拽状态切换（HTML5 拖放 API）=====
let draggingReq: any = null
const onDragStart = (e: DragEvent, r: any) => {
  draggingReq = r
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(r.id))
  }
}
const onDrop = async (e: DragEvent, targetStatus: string) => {
  e.preventDefault()
  if (!draggingReq) return
  const r = draggingReq
  draggingReq = null
  if (r.status === targetStatus) return
  const oldStatus = r.status
  r.status = targetStatus
  try {
    await request.put(`/requirements/${r.id}/status`, { status: targetStatus })
    ElMessage.success(`${r.requirementNo} 状态已更新为 ${targetStatus}`)
    await loadAll()
  } catch (err: any) {
    r.status = oldStatus
    ElMessage.error('状态更新失败：' + (err?.response?.data?.message || err?.message || '未知错误'))
  }
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const data = res.data?.data
    projectList.value = Array.isArray(data) ? data : (data?.records || [])
    if (projectList.value.length > 0 && filterProject.value == null) {
      filterProject.value = projectList.value[0].id
    }
  } catch (e) {}
}

const loadAll = async () => {
  loading.value = true
  try {
    const res = await request.get('/requirements/kanban', { params: { projectId: filterProject.value } })
    grouped.value = res.data?.data || {}
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await fetchProjects()
  await loadAll()
})
</script>

<style scoped>
.kanban-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
.kanban-board { display: flex; gap: 12px; overflow-x: auto; padding-bottom: 12px; }
.kanban-column { flex: 0 0 240px; background: #f4f4f5; border-radius: 6px; display: flex; flex-direction: column; max-height: calc(100vh - 180px); }
.column-header { color: #fff; padding: 8px 12px; font-size: 13px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; border-radius: 6px 6px 0 0; }
.column-body { padding: 8px; overflow-y: auto; flex: 1; }
.req-card { margin-bottom: 8px; cursor: pointer; }
.rc-row1 { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.rc-no { font-size: 12px; color: #909399; font-family: monospace; }
.rc-title { font-size: 13px; line-height: 1.4; color: #303133; margin: 4px 0; }
.rc-row2 { display: flex; gap: 4px; align-items: center; }
.rc-suspect { font-size: 11px; color: #FF5722; font-weight: 600; }
</style>
