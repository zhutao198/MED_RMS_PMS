<template>
  <div class="gantt-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span style="font-size:16px;font-weight:600">📅 甘特图（FR-2.7 含依赖+关键路径）- {{ currentProjectName }}</span>
            <el-tag v-if="criticalPath.length > 0" type="danger" size="small" style="margin-left:8px">
              关键路径 {{ criticalPath.length }} 任务 / {{ criticalPathDays }} 天
            </el-tag>
          </div>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="projectId" placeholder="请选择项目" filterable style="width:240px" @change="fetchData">
              <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
            <el-button @click="recalcCritical" :disabled="tasks.length === 0">重算关键路径</el-button>
            <el-button type="primary" @click="showTaskDialog = true">新建任务</el-button>
          </div>
        </div>
      </template>

      <div v-if="!projectId" class="empty-tip">
        <el-empty description="请先选择项目" />
      </div>
      <div v-else>
        <!-- 图例 -->
        <div class="legend">
          <span class="legend-item"><span class="legend-bar" style="background:#409EFF"></span>普通任务</span>
          <span class="legend-item"><span class="legend-bar" style="background:#F56C6C"></span>关键路径</span>
          <span class="legend-item"><span class="legend-bar" style="background:#67C23A"></span>已完成</span>
          <span class="legend-item"><span class="legend-bar" style="background:#E6A23C"></span>进行中</span>
          <span class="legend-item">─→ 任务依赖</span>
        </div>

        <!-- 时间轴 -->
        <div class="gantt-wrapper" ref="wrapperRef">
          <div class="gantt-grid" :style="{ gridTemplateColumns: `220px repeat(${daysInRange}, ${cellWidth}px)` }">
            <!-- 表头：日期 -->
            <div class="grid-header sticky-col">任务 / 负责人</div>
            <div v-for="(d, idx) in dateHeaders" :key="`h${idx}`" class="grid-header date-cell"
              :class="{ 'is-weekend': d.isWeekend, 'is-today': d.isToday }">
              <div class="day-num">{{ d.day }}</div>
              <div class="day-label">{{ d.label }}</div>
            </div>

            <!-- 任务行 -->
            <template v-for="task in tasks" :key="task.id">
              <div class="grid-cell sticky-col task-label">
                <div class="task-title">{{ task.title }}</div>
                <div class="task-meta">
                  <span>{{ task.assigneeName || '未指派' }}</span>
                  <el-tag size="small" :type="getTaskStatusType(task.status)">{{ getTaskStatusLabel(task.status) }}</el-tag>
                </div>
                <div class="task-actions">
                  <el-button size="small" link type="primary" @click="openDepends(task)">依赖</el-button>
                </div>
              </div>
              <div v-for="(d, idx) in dateHeaders" :key="`r${task.id}-${idx}`" class="grid-cell date-cell"
                :class="{ 'is-weekend': d.isWeekend, 'is-today': d.isToday }">
                <div v-if="isTaskOnDay(task, d.dateStr)" class="task-bar"
                  :class="['bar-' + getBarClass(task), { 'bar-critical': isCritical(task.id) }]"
                  :style="getBarStyle(task)">
                  <span class="bar-text">{{ task.title }}</span>
                </div>
              </div>
            </template>

            <!-- 里程碑行 -->
            <template v-for="ms in milestones" :key="`ms${ms.id}`">
              <div class="grid-cell sticky-col milestone-label">
                <span>📍 {{ ms.name }}</span>
                <el-tag size="small" :type="getGateTypeColor(ms.gateType)">{{ ms.gateType }}</el-tag>
              </div>
              <div v-for="(d, idx) in dateHeaders" :key="`mr${ms.id}-${idx}`" class="grid-cell date-cell"
                :class="{ 'is-weekend': d.isWeekend, 'is-today': d.isToday }">
                <div v-if="ms.plannedDate === d.dateStr" class="milestone-marker" :title="ms.name">◆</div>
              </div>
            </template>
          </div>
        </div>

        <!-- 关键路径摘要 -->
        <el-card v-if="criticalPath.length > 0" shadow="never" style="margin-top: 16px">
          <template #header><b>🚨 关键路径（{{ criticalPathDays }} 天）</b></template>
          <div class="cp-chain">
            <template v-for="(t, i) in criticalPath" :key="t.id">
              <el-tag type="danger" effect="dark">{{ t.title }}</el-tag>
              <span v-if="i < criticalPath.length - 1" class="cp-arrow">→</span>
            </template>
          </div>
        </el-card>

        <el-empty v-if="tasks.length === 0 && milestones.length === 0" description="暂无甘特图数据" />
      </div>
    </el-card>

    <!-- 新建任务对话框 -->
    <el-dialog v-model="showTaskDialog" title="新建任务" width="500px">
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="任务标题" required>
          <el-input v-model="taskForm.title" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="taskForm.description" type="textarea" rows="2" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="taskForm.assigneeId" filterable>
            <el-option v-for="u in allUsers" :key="u.id" :label="`${u.username} (${u.realName || '-'})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" required>
          <el-date-picker v-model="taskForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期" required>
          <el-date-picker v-model="taskForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="预计工时">
          <el-input-number v-model="taskForm.estimatedHours" :min="1" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="taskForm.priority">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTaskDialog = false">取消</el-button>
        <el-button type="primary" @click="createTask">创建</el-button>
      </template>
    </el-dialog>

    <!-- 任务依赖对话框 -->
    <el-dialog v-model="dependDialogVisible" :title="`任务依赖 - ${currentTask?.title || ''}`" width="500px">
      <el-form label-width="100px">
        <el-form-item label="前置任务">
          <el-select v-model="predecessorIds" multiple filterable placeholder="选择前置任务" style="width:100%">
            <el-option v-for="t in otherTasks" :key="t.id" :label="`${t.title} (${t.startDate}~${t.endDate})`" :value="t.id" :disabled="createsCycle(t.id)" />
          </el-select>
          <div class="form-tip">关键路径会从这些依赖关系自动推算</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dependDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDepends">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

const route = useRoute()

interface Task {
  id: number
  taskNo: string
  title: string
  assigneeId: number
  assigneeName: string
  startDate: string
  endDate: string
  estimatedHours: number
  actualHours: number
  status: string
  priority: string
  milestoneId: number
}

interface Milestone {
  id: number
  name: string
  gateType: string
  status: string
  plannedDate: string
}

const projectId = ref<number>(Number(route.params.id) || 0)
const projectList = ref<any[]>([])
const tasks = ref<Task[]>([])
const milestones = ref<Milestone[]>([])
const allUsers = ref<any[]>([])
const showTaskDialog = ref(false)
const loading = ref(false)
const cellWidth = ref(36)

// v1.43 P1-3 修复：任务依赖改为后端持久化（prj_schema.t_task_predecessor）
const depStore = ref<Record<number, Record<number, number[]>>>({}) // projectId -> { taskId -> [predecessorIds] }
const dependDialogVisible = ref(false)
const currentTask = ref<Task | null>(null)
const predecessorIds = ref<number[]>([])

const taskForm = ref({
  title: '', description: '', assigneeId: null, startDate: '', endDate: '',
  estimatedHours: 8, priority: 'MEDIUM', projectId: 0
})

const currentProjectName = computed(() => {
  const p = projectList.value.find(p => p.id === projectId.value)
  return p?.projectName || ''
})

const dateRange = computed(() => {
  if (tasks.value.length === 0 && milestones.value.length === 0) {
    const start = new Date()
    const end = new Date()
    end.setDate(end.getDate() + 14)
    return { start, end }
  }
  let minDate = new Date()
  let maxDate = new Date()
  minDate.setFullYear(9999)
  maxDate.setFullYear(0)
  for (const t of tasks.value) {
    if (t.startDate) {
      const s = new Date(t.startDate)
      if (s < minDate) minDate = s
    }
    if (t.endDate) {
      const e = new Date(t.endDate)
      if (e > maxDate) maxDate = e
    }
  }
  for (const m of milestones.value) {
    if (m.plannedDate) {
      const d = new Date(m.plannedDate)
      if (d < minDate) minDate = d
      if (d > maxDate) maxDate = d
    }
  }
  // 前后各扩展 3 天
  minDate.setDate(minDate.getDate() - 3)
  maxDate.setDate(maxDate.getDate() + 3)
  return { start: minDate, end: maxDate }
})

const daysInRange = computed(() => {
  const diff = dateRange.value.end.getTime() - dateRange.value.start.getTime()
  return Math.max(1, Math.ceil(diff / (1000 * 60 * 60 * 24)) + 1)
})

const dateHeaders = computed(() => {
  const headers: any[] = []
  const today = new Date().toISOString().split('T')[0]
  const d = new Date(dateRange.value.start)
  for (let i = 0; i < daysInRange.value; i++) {
    const dateStr = d.toISOString().split('T')[0]
    const dow = d.getDay()
    headers.push({
      dateStr,
      day: d.getDate(),
      label: ['日', '一', '二', '三', '四', '五', '六'][dow],
      isWeekend: dow === 0 || dow === 6,
      isToday: dateStr === today
    })
    d.setDate(d.getDate() + 1)
  }
  return headers
})

const isTaskOnDay = (task: Task, dateStr: string) => {
  return task.startDate && task.endDate && dateStr >= task.startDate && dateStr <= task.endDate
}

const getBarClass = (task: Task) => {
  if (task.status === 'DONE' || task.status === 'COMPLETED') return 'done'
  if (task.status === 'IN_PROGRESS') return 'progress'
  return 'normal'
}

const getBarStyle = (task: Task) => {
  const start = new Date(task.startDate).getTime()
  const end = new Date(task.endDate).getTime()
  const rangeStart = dateRange.value.start.getTime()
  const dayMs = 1000 * 60 * 60 * 24
  const offset = Math.round((start - rangeStart) / dayMs)
  const span = Math.max(1, Math.round((end - start) / dayMs) + 1)
  return { left: `${offset * cellWidth.value}px`, width: `${span * cellWidth.value - 2}px` }
}

// 关键路径算法：基于依赖图的最长路径（CPM）
const criticalPath = computed(() => {
  if (tasks.value.length === 0) return []
  const projectDeps = depStore.value[projectId.value] || {}
  const deps: Record<number, number[]> = {}
  for (const t of tasks.value) {
    deps[t.id] = projectDeps[t.id] || []
  }
  // 计算每个任务的最早完成时间（ES, EF）和最晚开始时间（LS, LF）
  const es: Record<number, number> = {}
  const ef: Record<number, number> = {}
  const ls: Record<number, number> = {}
  const lf: Record<number, number> = {}
  const taskDuration: Record<number, number> = {}
  const topoOrder: number[] = []
  const inDegree: Record<number, number> = {}
  for (const t of tasks.value) {
    inDegree[t.id] = deps[t.id]?.length || 0
    taskDuration[t.id] = Math.max(1, daysBetween(t.startDate, t.endDate))
  }
  // 拓扑排序
  const queue: number[] = tasks.value.filter(t => inDegree[t.id] === 0).map(t => t.id)
  while (queue.length > 0) {
    const id = queue.shift()!
    topoOrder.push(id)
    for (const t of tasks.value) {
      if (deps[t.id]?.includes(id)) {
        inDegree[t.id]--
        if (inDegree[t.id] === 0) queue.push(t.id)
      }
    }
  }
  if (topoOrder.length !== tasks.value.length) return [] // 有环

  // 前向：ES, EF
  for (const id of topoOrder) {
    const preds = deps[id] || []
    es[id] = preds.length > 0 ? Math.max(...preds.map(p => ef[p] || 0)) : 0
    ef[id] = es[id] + taskDuration[id]
  }
  const projectFinish = Math.max(...Object.values(ef))

  // 后向：LS, LF
  for (let i = topoOrder.length - 1; i >= 0; i--) {
    const id = topoOrder[i]
    const successors = tasks.value.filter(t => deps[t.id]?.includes(id)).map(t => t.id)
    lf[id] = successors.length > 0 ? Math.min(...successors.map(s => ls[s] || projectFinish)) : projectFinish
    ls[id] = lf[id] - taskDuration[id]
  }

  // 关键路径：浮动 = 0 的任务
  const criticalIds = topoOrder.filter(id => (ls[id] - es[id]) === 0)
  return criticalIds.map(id => tasks.value.find(t => t.id === id)!).filter(Boolean)
})

const criticalPathDays = computed(() => {
  if (criticalPath.value.length === 0) return 0
  const last = criticalPath.value[criticalPath.value.length - 1]
  return daysBetween(criticalPath.value[0].startDate, last.endDate) + 1
})

const isCritical = (id: number) => criticalPath.value.some(t => t.id === id)

const daysBetween = (s: string, e: string) => {
  if (!s || !e) return 0
  return Math.round((new Date(e).getTime() - new Date(s).getTime()) / (1000 * 60 * 60 * 24))
}

const otherTasks = computed(() => {
  if (!currentTask.value) return []
  return tasks.value.filter(t => t.id !== currentTask.value!.id)
})

// 检测添加此依赖是否会形成环
const createsCycle = (predecessorId: number): boolean => {
  if (!currentTask.value) return false
  // 从 predecessorId 出发能否到达 currentTask.id
  const projectDeps = depStore.value[projectId.value] || {}
  const visited = new Set<number>()
  const stack = [predecessorId]
  while (stack.length > 0) {
    const id = stack.pop()!
    if (id === currentTask.value.id) return true
    if (visited.has(id)) continue
    visited.add(id)
    for (const next of projectDeps[id] || []) stack.push(next)
  }
  return false
}

const getGateTypeColor = (gt: string) => {
  const map: Record<string, string> = { DCP1: 'primary', DCP2: 'success', DCP3: 'warning', DCP4: 'danger', DCP5: 'info' }
  return map[gt] || 'info'
}

const getTaskStatusType = (s: string) => {
  const map: Record<string, string> = { TODO: 'info', IN_PROGRESS: 'warning', DONE: 'success', BLOCKED: 'danger' }
  return map[s] || 'info'
}

const taskStatusLabels: Record<string, string> = {
  TODO: '待做', IN_PROGRESS: '进行中', DONE: '已完成', BLOCKED: '已阻塞',
  PLANNED: '已计划', COMPLETED: '已完成'
}
const getTaskStatusLabel = (s: string) => taskStatusLabels[s] || s

// v1.43 P1-3 修复：从后端拉取项目依赖图，不再读 localStorage
const loadDepStore = async (pid?: number) => {
  const p = pid ?? projectId.value
  if (!p) { depStore.value = {}; return }
  try {
    const res = await request.get(`/gantt/dependencies/project/${p}`)
    const graph = (res.data as any)?.data || {}
    depStore.value[projectId.value] = graph
  } catch {
    depStore.value[projectId.value] = {}
  }
}

const saveDepStore = () => {
  // v1.43 P1-3 修复：单个任务的依赖走 PUT /gantt/tasks/{id}/predecessors，
  // 此方法保留以兼容旧调用，但不再做持久化
  // （实际写入在 saveDepends 中通过 API 完成）
}

const openDepends = async (t: Task) => {
  currentTask.value = t
  // v1.43 P1-3 修复：实时从服务器拉取最新依赖（避免本地缓存过期）
  try {
    const res = await request.get(`/gantt/tasks/${t.id}/predecessors`)
    predecessorIds.value = (res.data as any)?.data || []
  } catch {
    const projectDeps = depStore.value[projectId.value] || {}
    predecessorIds.value = [...(projectDeps[t.id] || [])]
  }
  dependDialogVisible.value = true
}

const saveDepends = async () => {
  if (!currentTask.value || !projectId.value) return
  try {
    const res = await request.put(`/gantt/tasks/${currentTask.value.id}/predecessors`, predecessorIds.value)
    const saved: number[] = (res.data as any)?.data || []
    if (!depStore.value[projectId.value]) depStore.value[projectId.value] = {}
    depStore.value[projectId.value][currentTask.value.id] = [...saved]
    ElMessage.success('依赖已保存到服务器')
    dependDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const recalcCritical = () => {
  ElMessage.info(`关键路径：${criticalPath.value.length} 个任务，共 ${criticalPathDays.value} 天`)
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
    if (projectList.value.length > 0 && !projectId.value) {
      projectId.value = projectList.value[0].id
    }
  } catch {}
}

const fetchUsers = async () => {
  try {
    const res = await request.get('/system/users')
    allUsers.value = res.data?.data || []
  } catch {}
}

const fetchData = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const [mRes, tRes] = await Promise.allSettled([
      request.get(`/gantt/milestones/project/${projectId.value}`),
      request.get(`/gantt/tasks/project/${projectId.value}`)
    ])
    milestones.value = mRes.status === 'fulfilled' ? (mRes.value.data?.data || []) : []
    tasks.value = tRes.status === 'fulfilled' ? (tRes.value.data?.data || []) : []
    taskForm.value.projectId = projectId.value
    // v1.43 P1-3 修复：拉取项目依赖图
    await loadDepStore(projectId.value)
  } finally {
    loading.value = false
  }
}

const createTask = async () => {
  if (!taskForm.value.title) { ElMessage.warning('请填写任务标题'); return }
  try {
    await request.post('/gantt/tasks', taskForm.value)
    ElMessage.success('任务创建成功')
    showTaskDialog.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(async () => {
  await fetchProjects()
  await fetchUsers()
  if (projectId.value) await fetchData()
})
</script>

<style scoped>
.gantt-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.empty-tip { padding: 40px 0; }
.legend { display: flex; gap: 16px; margin-bottom: 12px; font-size: 13px; align-items: center; flex-wrap: wrap; }
.legend-item { display: inline-flex; align-items: center; gap: 4px; }
.legend-bar { display: inline-block; width: 20px; height: 10px; border-radius: 2px; }
.gantt-wrapper { overflow-x: auto; border: 1px solid #e4e7ed; border-radius: 4px; max-height: 70vh; overflow-y: auto; position: relative; }
.gantt-grid { display: grid; background: #fff; }
.grid-header, .grid-cell { border-right: 1px solid #ebeef5; border-bottom: 1px solid #ebeef5; min-height: 40px; }
.grid-header { background: #f5f7fa; font-size: 12px; text-align: center; padding: 4px 0; position: sticky; top: 0; z-index: 3; }
.grid-header.sticky-col { left: 0; z-index: 4; }
.date-cell { position: relative; padding: 0; }
.date-cell.is-weekend { background: #fafafa; }
.date-cell.is-today { background: #ecf5ff; }
.day-num { font-weight: 600; }
.day-label { font-size: 10px; color: #909399; }
.sticky-col { position: sticky; left: 0; background: #fff; z-index: 2; }
.task-label { padding: 6px 8px; min-width: 220px; }
.task-title { font-size: 13px; font-weight: 600; color: #303133; }
.task-meta { display: flex; gap: 6px; align-items: center; margin-top: 2px; font-size: 11px; color: #909399; }
.task-actions { margin-top: 2px; }
.milestone-label { padding: 6px 8px; background: #f0f9ff; font-weight: 600; }
.task-bar { position: absolute; top: 6px; height: 28px; border-radius: 4px; padding: 4px 8px; color: #fff; font-size: 12px; line-height: 20px; overflow: hidden; white-space: nowrap; cursor: pointer; box-shadow: 0 1px 2px rgba(0,0,0,0.1); z-index: 1; }
.bar-normal { background: #409EFF; }
.bar-progress { background: #E6A23C; }
.bar-done { background: #67C23A; }
.bar-critical { background: #F56C6C !important; box-shadow: 0 2px 4px rgba(245,108,108,0.4); }
.bar-text { font-size: 11px; }
.milestone-marker { color: #F56C6C; font-size: 24px; line-height: 40px; text-align: center; }
.cp-chain { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cp-arrow { color: #F56C6C; font-size: 18px; font-weight: bold; }
.form-tip { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
