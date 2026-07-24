<template>
  <div class="task-board">
    <el-card>
      <template #header>
        <div class="card-header">
          <span style="font-size:16px;font-weight:600">📋 任务看板</span>
          <div style="display:flex;gap:8px;align-items:center">
            <ProjectSelector v-model="projectId" @change="loadTasks" />
            <el-button @click="clearSelection" :disabled="selectedTasks.size === 0">清除选中 ({{ selectedTasks.size }})</el-button>
            <el-button type="primary" @click="showCreateDialog = true">新建任务</el-button>
          </div>
        </div>
      </template>

      <div class="board-columns" v-loading="loading">
        <div v-for="col in columns" :key="col.key" class="board-col" @dragover.prevent @drop="onDrop($event, col.key)">
          <div class="col-header">
            <span>{{ col.label }}</span>
            <el-tag size="small" :type="col.type">{{ tasksByStatus[col.key]?.length || 0 }}</el-tag>
          </div>
          <div class="col-body">
            <div v-for="t in (tasksByStatus[col.key] || [])" :key="t.id"
              class="task-card" :class="{ 'task-selected': selectedTasks.has(t.id) }"
              draggable="true"
              @dragstart="onDragStart($event, t, col.key)"
              @click.ctrl="toggleSelected(t)"
              @click.exact="showDetail(t)">
              <div class="task-title">{{ t.title }}
                <el-tag v-if="requirementMap[t.requirementId]" size="small" type="primary" effect="plain" style="margin-left: 6px; vertical-align: middle; cursor: pointer;" @click.stop="showDetail(t)">
                  {{ requirementMap[t.requirementId].requirementNo }}
                </el-tag>
              </div>
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

    <el-dialog v-model="showDetailDialog" title="任务详情" width="520px">
      <el-descriptions v-if="detailTask" :column="1" border>
        <el-descriptions-item label="标题">{{ detailTask.title }}</el-descriptions-item>
        <el-descriptions-item label="描述">{{ detailTask.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="statusType(detailTask.status)" size="small">{{ detailTask.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="优先级"><el-tag :type="priorityType(detailTask.priority)" size="small">{{ detailTask.priority }}</el-tag></el-descriptions-item>
        <!-- R222: 负责人行加"修改"按钮 → 切换 el-select → 保存（支持清空） -->
        <el-descriptions-item label="负责人">
          <span v-if="!editingAssignee">{{ detailTask.assigneeName || '-' }}</span>
          <el-select v-else v-model="editingAssigneeId" size="small" filterable clearable placeholder="未分配" style="width: 200px;"
            @change="(v: number | null) => editingAssigneeName = (allUsers.find(u => u.id === v)?.username + ' (' + (allUsers.find(u => u.id === v)?.realName || '-') + ')') || null">
            <el-option v-for="u in allUsers" :key="u.id" :label="`${u.username} (${u.realName || '-'})`" :value="u.id" />
          </el-select>
          <el-button v-if="!editingAssignee" link type="primary" size="small" @click="startEditAssignee">修改</el-button>
          <template v-else>
            <el-button type="primary" size="small" @click="saveAssignee" :loading="savingAssignee">保存</el-button>
            <el-button size="small" @click="cancelEditAssignee">取消</el-button>
          </template>
        </el-descriptions-item>
        <el-descriptions-item label="日期">{{ detailTask.startDate }} ~ {{ detailTask.endDate }}</el-descriptions-item>
        <el-descriptions-item v-if="requirementMap[detailTask.requirementId]" label="来源需求">
          {{ requirementMap[detailTask.requirementId].requirementNo }} - {{ requirementMap[detailTask.requirementId].title }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { useProject } from '@/composables/useProject'
import { useSyncProjectId } from '@/composables/useSyncProjectId'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { requirementApi } from '@/api/requirement'

const projectId = useSyncProjectId()
const loading = ref(false)
const allUsers = ref<any[]>([])
const allTasks = ref<any[]>([])
const requirementMap = ref<Record<number, { title: string; requirementNo: string }>>({})
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const detailTask = ref<any>(null)
const dragTask = ref<any>(null)
// R222: 详情弹窗"修改负责人"暂存态
const editingAssignee = ref(false)
const editingAssigneeId = ref<number | null>(null)
const editingAssigneeName = ref<string | null>(null)
const savingAssignee = ref(false)
const selectedTasks = ref<Set<number>>(new Set())
const dragSourceCol = ref<string | null>(null)

// 有效状态流转映射
const validTransitionMap: Record<string, string[]> = {
  TODO: ['IN_PROGRESS'],
  IN_PROGRESS: ['DONE', 'BLOCKED'],
  BLOCKED: ['TODO', 'IN_PROGRESS'],
  DONE: ['IN_PROGRESS'],
}

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

const fetchUsers = async () => {
  try {
    const res = await request.get('/system/users')
    allUsers.value = res.data?.data || []
  } catch {}
}

const fetchTasks = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await request.get(`/gantt/tasks/project/${projectId.value}`)
    allTasks.value = res.data?.data || []
    const reqRes = await requirementApi.list({ projectId: projectId.value, page: 1, size: 999999 })
    const map: Record<number, { title: string; requirementNo: string }> = {}
    for (const r of (reqRes.data?.data?.records || [])) {
      if (r.id) map[r.id] = { title: r.title, requirementNo: r.requirementNo || '' }
    }
    requirementMap.value = map
  } catch {
    allTasks.value = []
  } finally {
    loading.value = false
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

const onDragStart = (e: DragEvent, t: any, sourceCol: string) => {
  dragTask.value = t
  dragSourceCol.value = sourceCol
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move'
}

const getValidTransitions = (task: any): string[] => {
  return [...(validTransitionMap[task.status] || [])]
}

const onDrop = async (e: DragEvent, newStatus: string) => {
  if (!dragTask.value) return
  if (dragTask.value.status === newStatus) { dragTask.value = null; return }

  // 收集要更新的任务（选中的 + 拖拽的）
  const taskIds = new Set<number>(selectedTasks.value)
  taskIds.add(dragTask.value.id)
  const tasksToUpdate = allTasks.value.filter(t => taskIds.has(t.id))

  // 验证每个任务是否允许此流转
  const invalidTasks = tasksToUpdate.filter(t => !getValidTransitions(t).includes(newStatus))
  if (invalidTasks.length > 0) {
    ElMessage.warning(`不允许的流转：${invalidTasks.map(t => t.title).join(', ')}`)
    dragTask.value = null
    return
  }

  let successCount = 0
  for (const t of tasksToUpdate) {
    try {
      await request.put(`/gantt/tasks/${t.id}`, { status: newStatus })
      successCount++
    } catch (err: any) {
      ElMessage.warning(`任务 "${t.title}" 更新失败：${err?.response?.data?.message || err.message}`)
    }
  }
  if (successCount > 0) {
    ElMessage.success(`${successCount} 个任务已移至 ${columns.find(c => c.key === newStatus)?.label}`)
    await fetchTasks()
  }
  dragTask.value = null
  selectedTasks.value = new Set()
}

const toggleSelected = (t: any) => {
  const s = new Set(selectedTasks.value)
  if (s.has(t.id)) s.delete(t.id)
  else s.add(t.id)
  selectedTasks.value = s
}

const clearSelection = () => {
  selectedTasks.value = new Set()
}

const showDetail = (t: any) => {
  detailTask.value = t
  showDetailDialog.value = true
}

// R222: 启动修改负责人（写入编辑态，el-select 初始值同步）
const startEditAssignee = () => {
  editingAssigneeId.value = detailTask.value?.assigneeId ?? null
  editingAssigneeName.value = detailTask.value?.assigneeName ?? null
  editingAssignee.value = true
}
const cancelEditAssignee = () => {
  editingAssignee.value = false
  editingAssigneeId.value = null
  editingAssigneeName.value = null
}
const saveAssignee = async () => {
  if (!detailTask.value) return
  savingAssignee.value = true
  try {
    // 约定：assigneeId=null 表示"未分配"，后端识别 -1L 写 null
    const payload: any = {
      assigneeId: editingAssigneeId.value === null ? -1 : editingAssigneeId.value,
    }
    if (payload.assigneeId !== -1) {
      payload.assigneeName = editingAssigneeName.value
    }
    await request.put(`/gantt/tasks/${detailTask.value.id}`, payload)
    // 本地即时刷新 + 拉服务端
    detailTask.value.assigneeId = editingAssigneeId.value
    detailTask.value.assigneeName = editingAssigneeName.value
    editingAssignee.value = false
    ElMessage.success('负责人已更新')
    await fetchTasks()
  } catch (e: any) {
    ElMessage.error('更新负责人失败：' + (e?.response?.data?.message || e.message))
  } finally {
    savingAssignee.value = false
  }
}

const { ensureLoaded } = useProject()

onMounted(async () => {
  await ensureLoaded()
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
.task-selected { border: 2px solid #409eff; background: #ecf5ff; }
.task-title { font-size: 13px; font-weight: 600; margin-bottom: 6px; color: #303133; }
.task-meta { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #909399; }
</style>
