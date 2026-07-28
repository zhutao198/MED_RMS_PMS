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
            <ProjectSelector v-model="projectId" @change="fetchData" />
            <!-- R238：视图模式切换器（日/周/月/季）-->
            <el-radio-group v-model="viewMode" size="small">
              <el-radio-button label="day">日</el-radio-button>
              <el-radio-button label="week">周</el-radio-button>
              <el-radio-button label="month">月</el-radio-button>
              <el-radio-button label="quarter">季</el-radio-button>
            </el-radio-group>
            <el-button v-permission="'proj:update'" @click="recalcCritical" :disabled="tasks.length === 0">重算关键路径</el-button>
            <el-button type="primary" v-permission="'proj:create'" @click="showTaskDialog = true">新建任务</el-button>
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
          <div class="gantt-grid" :style="{ gridTemplateColumns: `220px repeat(${dateHeaders.length}, ${cellWidth}px)` }">
            <!-- R238：双层表头 — 上层月份/季度/年份 + 下层天/周/月 -->
            <!-- 上层表头（仅月/季/年视图显示） -->
            <template v-if="viewMode !== 'day'">
              <div class="grid-header sticky-col top-header">&nbsp;</div>
              <div v-for="(g, idx) in groupedHeaders" :key="`tg${idx}`"
                class="grid-header top-header date-cell"
                :class="{ 'is-month-start': g.isMonthStart, 'is-year-start': g.isYearStart }"
                :style="{ gridColumn: `span ${g.count}` }">
                <div class="top-label">{{ g.label }}</div>
              </div>
              <!-- 占位 spacer row（CSS Grid 需占位） -->
              <div class="sticky-col spacer"></div>
              <div v-for="(d, idx) in dateHeaders" :key="`sp${idx}`" class="grid-cell spacer"></div>
            </template>

            <!-- 下层表头：日期 -->
            <div class="grid-header sticky-col">任务 / 负责人</div>
            <div v-for="(d, idx) in dateHeaders" :key="`h${idx}`" class="grid-header date-cell"
              :class="{
                'is-weekend': d.isWeekend,
                'is-today': d.isToday,
                'is-month-start': d.isMonthStart,
                'is-year-start': d.isYearStart
              }">
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
                :class="{
                  'is-weekend': d.isWeekend,
                  'is-today': d.isToday,
                  'is-month-start': d.isMonthStart,
                  'is-year-start': d.isYearStart
                }"
                @dragover.prevent @drop="onDrop($event, task, d)">
                <el-tooltip
                  v-if="isTaskOnDay(task, d.dateStr)"
                  :content="getBarTooltip(task)"
                  placement="top"
                  :show-after="200"
                >
                  <div class="task-bar"
                    :class="['bar-' + getBarClass(task), { 'bar-critical': isCritical(task.id) }]"
                    :style="getBarStyle(task)" draggable="true"
                    @dragstart="onDragStart($event, task, d)"
                    @dragend="onDragEnd">
                    <span class="bar-text">{{ task.title }}</span>
                  </div>
                </el-tooltip>
              </div>
            </template>

            <!-- 里程碑行 -->
            <template v-for="ms in milestones" :key="`ms${ms.id}`">
              <div class="grid-cell sticky-col milestone-label">
                <span>📍 {{ ms.name }}</span>
                <el-tag size="small" :type="getGateTypeColor(ms.gateType)">{{ ms.gateType }}</el-tag>
              </div>
              <div v-for="(d, idx) in dateHeaders" :key="`mr${ms.id}-${idx}`" class="grid-cell date-cell"
                :class="{
                  'is-weekend': d.isWeekend,
                  'is-today': d.isToday,
                  'is-month-start': d.isMonthStart,
                  'is-year-start': d.isYearStart
                }">
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
        <el-button type="primary" v-permission="'proj:update'" @click="saveDepends">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useProject } from '@/composables/useProject'
import ProjectSelector from '@/components/ProjectSelector.vue'
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
const tasks = ref<Task[]>([])
const milestones = ref<Milestone[]>([])
const allUsers = ref<any[]>([])
const showTaskDialog = ref(false)
const loading = ref(false)
// R238：视图模式（day / week / month / quarter）+ 每模式宽度
const viewMode = ref<'day' | 'week' | 'month' | 'quarter'>('week')
const cellWidth = computed(() => ({ day: 60, week: 140, month: 180, quarter: 220 }[viewMode.value]))

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
    end.setDate(end.getDate() + 30)
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
  if (minDate > maxDate) {
    const now = new Date()
    return { start: new Date(now.getFullYear(), now.getMonth(), 1), end: new Date(now.getFullYear(), now.getMonth() + 1, 0) }
  }
  // R238 Bug 修复：按 viewMode 加 padding + 包含"今天"
  // 用户反馈"季视图只看到当季、月视图只看到7-8月"——因为原 dateRange 只有任务 min~max
  const today = new Date()
  // 任务 min/max 在过去/当前，看不到"未来"：确保包含 today
  if (today < minDate) minDate = new Date(today)
  if (today > maxDate) maxDate = new Date(today)
  const mode = viewMode.value
  if (mode === 'quarter') {
    // 季视图：start 对齐季度首月 1 号；end + 整 1 年 padding
    const startQ = Math.floor(minDate.getMonth() / 3) * 3
    minDate = new Date(minDate.getFullYear(), startQ, 1)
    const endQ = Math.floor(maxDate.getMonth() / 3) * 3
    maxDate = new Date(maxDate.getFullYear(), endQ + 3, 0)
    // 至少显示 1 年（4 季）
    const monthDiff = (maxDate.getFullYear() - minDate.getFullYear()) * 12 + (maxDate.getMonth() - minDate.getMonth())
    if (monthDiff < 12) {
      maxDate = new Date(minDate.getFullYear() + 1, minDate.getMonth(), 0)
    }
  } else if (mode === 'month') {
    // 月视图：start 对齐月首；end + 6 月 padding
    minDate = new Date(minDate.getFullYear(), minDate.getMonth(), 1)
    maxDate = new Date(maxDate.getFullYear(), maxDate.getMonth() + 7, 0)
    const monthDiff = (maxDate.getFullYear() - minDate.getFullYear()) * 12 + (maxDate.getMonth() - minDate.getMonth())
    if (monthDiff < 6) {
      maxDate = new Date(minDate.getFullYear(), minDate.getMonth() + 6, 0)
    }
  } else if (mode === 'week') {
    // 周视图：start 对齐周一；end + 8 周 padding
    const dow = minDate.getDay()
    const offset = dow === 0 ? -6 : 1 - dow
    minDate = new Date(minDate.getFullYear(), minDate.getMonth(), minDate.getDate() + offset)
    const endD = new Date(maxDate.getFullYear(), maxDate.getMonth(), maxDate.getDate() + 56)
    if (endD > maxDate) maxDate = endD
  } else { // day
    // 日视图：end + 14 天 padding
    if ((maxDate.getTime() - minDate.getTime()) < 30 * 24 * 3600 * 1000) {
      maxDate = new Date(minDate.getTime() + 30 * 24 * 3600 * 1000)
    }
  }
  return { start: minDate, end: maxDate }
})

const daysInRange = computed(() => {
  // R238：根据 viewMode 计算"格子数"（每天/周/月/季 是一格）
  if (!dateRange.value) return 1
  const start = dateRange.value.start
  const end = dateRange.value.end
  const mode = viewMode.value
  if (mode === 'day') {
    return Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1)
  } else if (mode === 'week') {
    return Math.max(1, Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24 * 7)) + 1)
  } else if (mode === 'month') {
    return Math.max(1, (end.getFullYear() - start.getFullYear()) * 12 + (end.getMonth() - start.getMonth()) + 1)
  } else { // quarter
    return Math.max(1, Math.floor((end.getFullYear() - start.getFullYear()) * 4 + Math.floor((end.getMonth() - start.getMonth()) / 3)) + 1)
  }
})

const dateHeaders = computed(() => {
  const headers: any[] = []
  if (!dateRange.value) return headers
  const today = new Date().toISOString().split('T')[0]
  const mode = viewMode.value
  const d = new Date(dateRange.value.start)
  // R238：根据 viewMode 决定每格的日期范围/标签
  for (let i = 0; i < daysInRange.value; i++) {
    const dateStr = d.toISOString().split('T')[0]
    const dow = d.getDay()
    let day = '', label = ''
    if (mode === 'day') {
      day = String(d.getDate())
      label = ['日', '一', '二', '三', '四', '五', '六'][dow]
    } else if (mode === 'week') {
      // 第几周（简化：取本月第几周）
      const dom = d.getDate()
      const weekInMonth = Math.ceil(dom / 7)
      day = `W${weekInMonth}`
      label = `${d.getMonth() + 1}月`
    } else if (mode === 'month') {
      // 每格代表一个月
      day = String(d.getMonth() + 1)
      label = `${d.getFullYear()}`
    } else { // quarter
      const m = d.getMonth()
      day = `Q${Math.floor(m / 3) + 1}`
      label = `${d.getFullYear()}`
    }
    // 是否是月初/年初（用于分割线）
    const isMonthStart = (mode === 'day')
      ? d.getDate() === 1
      : (mode === 'week' ? d.getDate() === 1 : true)
    const isYearStart = d.getMonth() === 0 && (d.getDate() === 1 || mode !== 'day')
    headers.push({
      dateStr,
      day,
      label,
      isWeekend: dow === 0 || dow === 6,
      isToday: dateStr === today,
      isMonthStart,
      isYearStart,
      mode: viewMode.value  // R238：保存 viewMode 供 isTaskOnDay 等使用
    })
    // R238：根据 viewMode 推进游标
    if (mode === 'day') d.setDate(d.getDate() + 1)
    else if (mode === 'week') d.setDate(d.getDate() + 7)
    else if (mode === 'month') d.setMonth(d.getMonth() + 1)
    else /* quarter */ d.setMonth(d.getMonth() + 3)
  }
  return headers
})

// R238：上层表头（按月份/季度/年份分组合并）
const groupedHeaders = computed(() => {
  const groups: { label: string; count: number; isMonthStart: boolean; isYearStart: boolean }[] = []
  const mode = viewMode.value
  if (mode === 'day') return groups
  let cur: any = null
  for (const h of dateHeaders.value) {
    // 按 viewMode 决定 group key
    const d = new Date(h.dateStr)
    let groupKey = ''
    if (mode === 'week') groupKey = `${d.getFullYear()}-W${Math.ceil(d.getDate() / 7)}`
    else if (mode === 'month') groupKey = `${d.getFullYear()}-${d.getMonth() + 1}`
    else /* quarter */ groupKey = `${d.getFullYear()}-Q${Math.floor(d.getMonth() / 3) + 1}`
    if (!cur || cur.key !== groupKey) {
      if (cur) groups.push(cur)
      // R239 修复：按月视图上层表头不重复显示月份（已在下层显示）
      // month 模式：上层=年份（避免与下层"月份数字"重复）
      // week 模式：上层=月份（与下层"周号 W1/W2"区分）
      // quarter 模式：上层=年份（与下层"Q1/Q2"区分）
      const labelMap: Record<string, string> = {
        week: `${d.getMonth() + 1}月`,
        month: `${d.getFullYear()}`,  // 仅年份（月份在下层）
        quarter: `${d.getFullYear()}`  // 仅年份（季度在下层）
      }
      cur = { key: groupKey, label: labelMap[mode] || '', count: 1, isMonthStart: true, isYearStart: d.getMonth() === 0 }
    } else {
      cur.count++
    }
  }
  if (cur) groups.push(cur)
  return groups
})

const isTaskOnDay = (task: Task, dateStr: string) => {
  // R238：day 模式按日匹配；week/month/quarter 模式按范围覆盖判断
  if (!task.startDate || !task.endDate) return false
  if (viewMode.value === 'day') {
    return dateStr >= task.startDate && dateStr <= task.endDate
  }
  // 其它模式：dateStr 是这一格的"起始日"，任务覆盖该格（启发式：周/月/季）
  return task.endDate >= dateStr
}

const getBarClass = (task: Task) => {
  if (task.status === 'DONE' || task.status === 'COMPLETED') return 'done'
  if (task.status === 'IN_PROGRESS') return 'progress'
  return 'normal'
}

// R239 B：tooltip 完整信息（任务标题 + 负责人 + 时间段 + 状态）
const getBarTooltip = (task: Task) => {
  const lines = [
    task.title,
    `负责人: ${task.assigneeName || '未指派'}`,
    `时间: ${task.startDate} ~ ${task.endDate}`,
    `状态: ${task.status}`
  ]
  return lines.join('\n')
}

const getBarStyle = (task: Task) => {
  const start = new Date(task.startDate).getTime()
  const end = new Date(task.endDate).getTime()
  const rangeStart = dateRange.value.start.getTime()
  const dayMs = 1000 * 60 * 60 * 24
  // R238 Bug 修复：按 viewMode 计算每格代表的"单位毫秒数"
  // 原来固定按"1 天 = 1 格"，导致 week/month/quarter 视图下任务条错位
  let unitMs: number
  switch (viewMode.value) {
    case 'day': unitMs = dayMs; break
    case 'week': unitMs = 7 * dayMs; break
    case 'month': {
      // 月模式：每格代表"该月"—— unitMs = 该月起止天数（28-31 不等）
      const sD = new Date(task.startDate)
      const eD = new Date(task.endDate)
      const sMonthDays = new Date(sD.getFullYear(), sD.getMonth() + 1, 0).getDate()
      const eMonthDays = new Date(eD.getFullYear(), eD.getMonth() + 1, 0).getDate()
      // 简化：用任务起始月天数（精度可接受）
      unitMs = sMonthDays * dayMs
      break
    }
    case 'quarter': {
      // 季模式：每格代表 1 个季度（3 个月，约 90 天）
      unitMs = 90 * dayMs
      break
    }
    default: unitMs = dayMs
  }
  const offset = Math.round((start - rangeStart) / unitMs)
  const span = Math.max(1, Math.round((end - start) / unitMs) + 1)
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

// R175：甘特图拖拽状态
const dragState = ref<{ task: any; startDate: string } | null>(null)

const onDragStart = (e: DragEvent, task: any, day: any) => {
  dragState.value = { task, startDate: day.dateStr }
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', task.id.toString())
  }
}

const onDragEnd = () => {
  dragState.value = null
}

const onDrop = async (e: DragEvent, task: any, day: any) => {
  if (!dragState.value) return
  const draggedTask = dragState.value.task
  if (draggedTask.id !== task.id) return // 只处理拖到同任务行的场景

  const oldStart = draggedTask.startDate
  const oldEnd = draggedTask.endDate
  const daysDiff = daysBetween(dragState.value.startDate, day.dateStr)
  if (daysDiff === 0) return

  const newStart = shiftDate(oldStart, daysDiff)
  const newEnd = shiftDate(oldEnd, daysDiff)

  try {
    await request.put(`/gantt/tasks/${draggedTask.id}`, {
      startDate: newStart,
      endDate: newEnd
    })
    ElMessage.success(`已调整任务日期: ${newStart} → ${newEnd}`)
    await fetchData()
  } catch (err: any) {
    ElMessage.error('调整日期失败：' + (err?.response?.data?.message || err.message))
  } finally {
    dragState.value = null
  }
}

const shiftDate = (dateStr: string, days: number): string => {
  const d = new Date(dateStr)
  d.setDate(d.getDate() + days)
  return d.toISOString().split('T')[0]
}

const { projectList, ensureLoaded } = useProject()

onMounted(async () => {
  await ensureLoaded()
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
/* R238：月份/年份分割线 */
.date-cell.is-month-start { border-left: 1px solid #c0c4cc; }
.date-cell.is-year-start { border-left: 2px solid #303133; }
.top-header { font-weight: 600; color: #303133; padding: 4px 6px; background: #fafafa; }
.top-label { font-size: 13px; }
.spacer { height: 0; padding: 0; margin: 0; border: none; }
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
