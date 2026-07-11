<template>
  <div class="task-board">
    <el-card>
      <template #header>
        <div class="card-header">
          <span style="font-size:16px;font-weight:600">📋 任务看板</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="projectId" placeholder="选择项目" filterable style="width:220px" @change="fetchTasks">
              <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
            <el-button type="primary" @click="showCreateDialog = true">新建任务</el-button>
          </div>
        </div>
      </template>

      <div class="board-columns">
        <div v-for="col in columns" :key="col.key" class="board-col" @dragover.prevent @drop="onDrop($event, col.key)">
          <div class="col-header">
            <span>{{ col.label }}</span>
            <el-tag size="small" :type="col.type">{{ tasksByStatus[col.key]?.length || 0 }}</el-tag>
          </div>
          <div class="col-body">
            <div v-for="t in (tasksByStatus[col.key] || [])" :key="t.id" class="task-card"
              draggable="true"
              @dragstart="onDragStart($event, t)"
              @click="showDetail(t)">
              <div class="task-title">{{ t.title }}</div>
              <div class="task-meta">
                <span>{{ t.assigneeName || '未分配' }}</span>
                <el-tag size="small" :type="priorityType(t.priority)" effect="plain">{{ t.priority }}</el-tag>
              </div>
            </div>
            <el-empty v-if="!(tasksByStatus[col.key]?.length)" :image-size="40" description="空" />
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="showCreateDialog" title="新建任务" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" rows="2" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="form.assigneeId" filterable>
            <el-option v-for="u in allUsers" :key="u.id" :label="`${u.username} (${u.realName || '-'})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createTask">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" title="任务详情" width="480px">
      <el-descriptions v-if="detailTask" :column="1" border>
        <el-descriptions-item label="标题">{{ detailTask.title }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailTask.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detailTask.status)" size="small">{{ detailTask.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="优先级"><el-tag :type="priorityType(detailTask.priority)" size="small">{{ detailTask.priority }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detailTask.assigneeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detailTask.startDate }} ~ {{ detailTask.endDate }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const projectId = ref<number | null>(null)
const projectList = ref<any[]>([])
const allUsers = ref<any[]>([])
const allTasks = ref<any[]>([])
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const detailTask = ref<any>(null)
const dragTask = ref<any>(null)

const columns = [
  { key: 'TODO', label: '待办', type: 'info' as const },
  { key: 'IN_PROGRESS', label: '进行中', type: 'warning' as const },
  { key: 'DONE', label: '已完成', type: 'success' as const },
  { key: 'BLOCKED', label: '已阻塞', type: 'danger' as const },
]

const form = ref({ title: '', description: '', assigneeId: null as number | null, priority: 'MEDIUM', startDate: '', endDate: '' })

const tasksByStatus = computed(() => {
  const grouped: Record<string, any[]> = {}
  for (const c of columns) grouped[c.key] = []
  for (const t of allTasks.value) {
    const key = t.status || 'TODO'
    if (grouped[key]) grouped[key].push(t)
    else grouped[key] = [t]
  }
  return grouped
})

const priorityType = (p?: string) => {
  const map: Record<string, string> = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
  return map[p || ''] || 'info'
}

const statusType = (s?: string) => {
  const map: Record<string, string> = { TODO: 'info', IN_PROGRESS: 'warning', DONE: 'success', BLOCKED: 'danger' }
  return map[s || ''] || 'info'
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
    if (projectList.value.length > 0 && !projectId.value) projectId.value = projectList.value[0].id
  } catch {}
}

const fetchUsers = async () => {
  try {
    const res = await request.get('/system/users')
    allUsers.value = res.data?.data || []
  } catch {}
}

const fetchTasks = async () => {
  if (!projectId.value) return
  try {
    const res = await request.get(`/gantt/tasks/project/${projectId.value}`)
    allTasks.value = res.data?.data || []
  } catch {
    allTasks.value = []
  }
}

const createTask = async () => {
  if (!form.value.title) { ElMessage.warning('请填写标题'); return }
  try {
    await request.post('/gantt/tasks', { ...form.value, projectId: projectId.value, status: 'TODO' })
    ElMessage.success('任务已创建')
    showCreateDialog.value = false
    form.value = { title: '', description: '', assigneeId: null, priority: 'MEDIUM', startDate: '', endDate: '' }
    await fetchTasks()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e.message))
  }
}

const onDragStart = (e: DragEvent, t: any) => {
  dragTask.value = t
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

const onDrop = async (e: DragEvent, newStatus: string) => {
  if (!dragTask.value) return
  if (dragTask.value.status === newStatus) return
  try {
    await request.put(`/gantt/tasks/${dragTask.value.id}`, { status: newStatus })
    ElMessage.success(`任务移至 ${columns.find(c => c.key === newStatus)?.label}`)
    await fetchTasks()
  } catch (err: any) {
    ElMessage.error('更新失败：' + (err?.response?.data?.message || err.message))
  } finally {
    dragTask.value = null
  }
}

const showDetail = (t: any) => {
  detailTask.value = t
  showDetailDialog.value = true
}

onMounted(async () => {
  await fetchProjects()
  await fetchUsers()
  await fetchTasks()
})
</script>

<style scoped>
.task-board { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.board-columns { display: flex; gap: 16px; min-height: 400px; overflow-x: auto; }
.board-col { flex: 1; min-width: 220px; background: #f5f7fa; border-radius: 8px; padding: 12px; }
.col-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-weight: 600; font-size: 14px; }
.col-body { display: flex; flex-direction: column; gap: 8px; }
.task-card { background: #fff; border-radius: 6px; padding: 10px 12px; cursor: pointer; box-shadow: 0 1px 2px rgba(0,0,0,0.08); transition: box-shadow 0.2s; }
.task-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,0.12); }
.task-title { font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #303133; }
.task-meta { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #909399; }
</style>
