<template>
  <div class="req-task-container">
    <div class="page-title">
      <h2>需求→任务转化（FR-1.10）</h2>
      <el-button @click="loadAll">刷新</el-button>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      支持 SRS/DRS/URS/PRS 四种类型需求拆解为可执行任务，任务状态变更自动反向同步到需求状态。
    </el-alert>

    <div class="filters">
      <el-select v-model="filterProject" placeholder="项目" clearable style="width: 200px;" @change="loadAll">
        <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-select v-model="filterType" placeholder="需求类型" clearable style="width: 130px;" @change="loadAll">
        <el-option label="URS" value="URS" />
        <el-option label="PRS" value="PRS" />
        <el-option label="SRS" value="SRS" />
        <el-option label="DRS" value="DRS" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 130px;" @change="loadAll">
        <el-option label="Draft" value="Draft" />
        <el-option label="Approved" value="Approved" />
        <el-option label="InProgress" value="InProgress" />
        <el-option label="InTest" value="InTest" />
        <el-option label="Suspect" value="Suspect" />
      </el-select>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover" header="待拆解需求">
          <el-table :data="availableRequirements" stripe height="500" v-loading="loading.left">
            <el-table-column prop="requirementNo" label="编号" width="140" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="requirementType" label="类型" width="70" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain @click="openConvert(row)">拆解</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover" header="已拆解需求（带进度）">
          <el-table :data="convertedRequirements" stripe height="500" v-loading="loading.right" @row-click="showTasksByRow">
            <el-table-column prop="requirementNo" label="编号" width="140" />
            <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
            <el-table-column label="进度" width="180">
              <template #default="{ row }">
                <el-progress :percentage="row.progress" :status="row.progress === 100 ? 'success' : (row.status === 'Suspect' ? 'exception' : '')" :stroke-width="14" />
                <div style="font-size: 11px; color: #909399; margin-top: 2px;">{{ row.done }}/{{ row.totalTasks }} 任务已完成</div>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="需求状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 拆解对话框 -->
    <el-dialog v-model="showConvert" title="需求拆解为任务" width="720px">
      <el-descriptions :column="3" border size="small" v-if="currentReq">
        <el-descriptions-item label="编号">{{ currentReq.requirementNo }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentReq.requirementType }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentReq.status }}</el-descriptions-item>
        <el-descriptions-item :span="3" label="标题">{{ currentReq.title }}</el-descriptions-item>
      </el-descriptions>

      <div class="draft-toolbar">
        <el-button size="small" @click="regenerate" :loading="loading.draft">🔄 重新生成草稿</el-button>
        <el-button size="small" type="primary" plain @click="addDraftRow">+ 添加任务</el-button>
        <span class="draft-tip">提示：可调整开始/结束日期、负责人、工时等</span>
      </div>

      <el-table :data="draftList" border size="small" max-height="320">
        <el-table-column type="index" width="50" />
        <el-table-column label="任务标题" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.title" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="开始日期" width="140">
          <template #default="{ row }">
            <el-date-picker v-model="row.startDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 130px;" />
          </template>
        </el-table-column>
        <el-table-column label="结束日期" width="140">
          <template #default="{ row }">
            <el-date-picker v-model="row.endDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 130px;" />
          </template>
        </el-table-column>
        <el-table-column label="工时" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.estimatedHours" :min="1" :max="999" size="small" controls-position="right" style="width: 70px;" />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-select v-model="row.priority" size="small">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60">
          <template #default="{ $index }">
            <el-button size="small" type="danger" text @click="draftList.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <el-button @click="showConvert = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmConvert">确认拆解</el-button>
      </template>
    </el-dialog>

    <!-- 任务列表 + 状态切换 -->
    <el-dialog v-model="showTasks" title="需求关联任务" width="780px">
      <el-descriptions :column="2" border size="small" v-if="progress">
        <el-descriptions-item label="进度">{{ progress.progress }}%</el-descriptions-item>
        <el-descriptions-item label="总工时">{{ progress.totalEstimatedHours }} 小时（实际 {{ progress.totalActualHours }}）</el-descriptions-item>
        <el-descriptions-item label="任务总数">{{ progress.totalTasks }}</el-descriptions-item>
        <el-descriptions-item label="已完成/阻塞/进行中/待办">
          {{ progress.done }} / {{ progress.blocked }} / {{ progress.inProgress }} / {{ progress.todo }}
        </el-descriptions-item>
      </el-descriptions>

      <el-table :data="taskList" border size="small" style="margin-top: 12px;">
        <el-table-column prop="taskNo" label="任务编号" width="120" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-select :model-value="row.status" size="small" @change="changeStatus(row, $event)">
              <el-option label="待办" value="TODO" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="已完成" value="DONE" />
              <el-option label="阻塞" value="BLOCKED" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始" width="110" />
        <el-table-column prop="endDate" label="结束" width="110" />
        <el-table-column prop="estimatedHours" label="工时" width="70" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface Requirement {
  id: number
  requirementNo: string
  requirementType: string
  title: string
  status: string
  projectId: number
}

interface Task {
  id: number
  taskNo: string
  title: string
  status: string
  startDate: string
  endDate: string
  estimatedHours: number
  requirementId: number
}

interface Draft {
  title: string
  description: string
  startDate: string
  endDate: string
  estimatedHours: number
  priority: string
  assigneeId: number | null
  assigneeName: string | null
  parentTaskId: number | null
  milestoneId: number | null
}

const requirements = ref<Requirement[]>([])
const taskList = ref<Task[]>([])
const progress = ref<any>(null)
const projectList = ref<{ id: number; projectName: string }[]>([])
const filterProject = ref<number | null>(null)
const filterType = ref('')
const filterStatus = ref('')

const showConvert = ref(false)
const showTasks = ref(false)
const saving = ref(false)
const currentReq = ref<Requirement | null>(null)
const draftList = ref<Draft[]>([])

const loading = ref({ left: false, right: false, draft: false })

const availableRequirements = computed(() =>
  requirements.value.filter(r =>
    !['Baseline', 'Verified'].includes(r.status) &&
    (filterProject.value ? r.projectId === filterProject.value : true) &&
    (filterType.value ? r.requirementType === filterType.value : true) &&
    (filterStatus.value ? r.status === filterStatus.value : true)
  )
)

const convertedRequirements = ref<(Requirement & { progress: number; totalTasks: number; done: number })[]>([])

const statusType = (s: string) => ({
  Draft: 'info', Approved: '', InProgress: 'warning', InTest: 'primary', Suspect: 'danger', Baseline: 'success', Verified: 'success'
} as any)[s] || ''

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    const data = res.data?.data
    projectList.value = Array.isArray(data) ? data : (data?.records || [])
  } catch (e) {}
}

const loadAll = async () => {
  loading.value.left = true
  loading.value.right = true
  try {
    const res = await request.get('/requirements', {
      params: { page: 0, size: 200, ...(filterProject.value ? { projectId: filterProject.value } : {}) }
    })
    const data = res.data?.data
    const records = Array.isArray(data) ? data : (data?.records || [])
    requirements.value = records

    // 查每个 SRS/DRS/URS/PRS 是否有任务
    const conv: (Requirement & { progress: number; totalTasks: number; done: number })[] = []
    for (const r of records) {
      try {
        const p = await request.get(`/requirement-tasks/progress/${r.id}`)
        const pd = p.data?.data
        if (pd && pd.totalTasks > 0) {
          conv.push({ ...r, progress: pd.progress, totalTasks: pd.totalTasks, done: pd.done })
        }
      } catch (e) {}
    }
    convertedRequirements.value = conv
  } catch (e) {
    ElMessage.error('加载需求失败')
  } finally {
    loading.value.left = false
    loading.value.right = false
  }
}

const openConvert = async (req: Requirement) => {
  currentReq.value = req
  showConvert.value = true
  await regenerate()
}

const regenerate = async () => {
  if (!currentReq.value) return
  loading.value.draft = true
  try {
    const res = await request.get(`/requirement-tasks/drafts/${currentReq.value.id}`)
    draftList.value = (res.data?.data || []).map((d: any) => ({ ...d }))
    if (draftList.value.length === 0) {
      draftList.value.push({
        title: '实现：' + currentReq.value.title,
        description: '按需求完成实现',
        startDate: new Date().toISOString().substring(0, 10),
        endDate: new Date(Date.now() + 5 * 86400000).toISOString().substring(0, 10),
        estimatedHours: 16,
        priority: 'MEDIUM',
        assigneeId: null, assigneeName: null, parentTaskId: null, milestoneId: null,
      })
    }
  } catch (e: any) {
    ElMessage.error('生成草稿失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value.draft = false
  }
}

const addDraftRow = () => {
  draftList.value.push({
    title: '',
    description: '',
    startDate: new Date().toISOString().substring(0, 10),
    endDate: new Date(Date.now() + 5 * 86400000).toISOString().substring(0, 10),
    estimatedHours: 8,
    priority: 'MEDIUM',
    assigneeId: null, assigneeName: null, parentTaskId: null, milestoneId: null,
  })
}

const confirmConvert = async () => {
  if (!currentReq.value) return
  if (draftList.value.length === 0) {
    ElMessage.warning('至少需要 1 个任务')
    return
  }
  for (const d of draftList.value) {
    if (!d.title) {
      ElMessage.warning('请填写所有任务标题')
      return
    }
  }
  saving.value = true
  try {
    await request.post(`/requirement-tasks/convert/${currentReq.value.id}`, draftList.value)
    ElMessage.success(`已拆解为 ${draftList.value.length} 个任务`)
    showConvert.value = false
    await loadAll()
  } catch (e: any) {
    ElMessage.error('拆解失败：' + (e?.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

const showTasksFor = async (req: Requirement) => {
  try {
    const [p, t] = await Promise.all([
      request.get(`/requirement-tasks/progress/${req.id}`),
      request.get(`/requirement-tasks/by-requirement/${req.id}`),
    ])
    progress.value = p.data?.data
    taskList.value = t.data?.data || []
    currentReq.value = req
    showTasks.value = true
  } catch (e) {
    ElMessage.error('加载任务失败')
  }
}

const showTasksByRow = (row: any) => {
  showTasksFor(row)
}

const changeStatus = async (row: Task, newStatus: string) => {
  try {
    await request.put(`/requirement-tasks/${row.id}/status`, null, { params: { status: newStatus } })
    row.status = newStatus
    ElMessage.success('状态已更新')
    // 刷新进度
    if (currentReq.value) {
      const p = await request.get(`/requirement-tasks/progress/${currentReq.value.id}`)
      progress.value = p.data?.data
    }
    await loadAll()
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(async () => {
  await fetchProjects()
  await loadAll()
})
</script>

<style scoped>
.req-task-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title h2 {
  font-size: 20px;
  color: #303133;
}

.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  align-items: center;
}

.draft-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 12px 0;
  padding: 8px 12px;
  background: #fafbfc;
  border-radius: 4px;
}

.draft-tip {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}
</style>
