<template>
  <div class="worklog-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span style="font-size:16px;font-weight:600">⏱ 工时统计（FR-2.9）- {{ currentProjectName }}</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="filterProjectId" placeholder="选择项目" filterable clearable style="width:220px" @change="fetchSummary">
              <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
            <el-button @click="fetchSummary" type="primary">查询</el-button>
            <el-button @click="showDialog = true" type="success">+ 填报工时</el-button>
          </div>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
        工时统计支持按项目/人员/需求三个维度汇总。点击"填报工时"录入实际工时，提交后立即生效并可重新汇总。
      </el-alert>

      <!-- 汇总概览 -->
      <el-row :gutter="16" v-if="summary">
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#409EFF">{{ summary.count }}</div><div class="stat-label">工时记录数</div></div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#67C23A">{{ summary.totalHours }}</div><div class="stat-label">总工时（小时）</div></div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#E6A23C">{{ Object.keys(summary.byWorker || {}).length }}</div><div class="stat-label">参与人员数</div></div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#909399">{{ Object.keys(summary.byTask || {}).length }}</div><div class="stat-label">关联任务数</div></div></el-card>
        </el-col>
      </el-row>

      <!-- 按人员汇总 -->
      <el-card style="margin-top:16px">
        <template #header><div class="card-title">👤 按人员汇总</div></template>
        <el-table :data="byWorkerTable" border stripe v-if="byWorkerTable.length > 0">
          <el-table-column prop="name" label="人员" />
          <el-table-column prop="hours" label="总工时（小时）" width="200" />
          <el-table-column label="占比" width="200">
            <template #default="{ row }">
              <el-progress :percentage="row.percent" :stroke-width="14" />
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无工时数据" />
      </el-card>

      <!-- 按任务汇总 -->
      <el-card style="margin-top:16px">
        <template #header><div class="card-title">📋 按任务汇总</div></template>
        <el-table :data="byTaskTable" border stripe v-if="byTaskTable.length > 0">
          <el-table-column prop="taskId" label="任务 ID" width="200" />
          <el-table-column prop="hours" label="总工时（小时）" />
        </el-table>
        <el-empty v-else description="暂无任务工时数据" />
      </el-card>
    </el-card>

    <!-- 填报工时对话框 -->
    <el-dialog v-model="showDialog" title="⏱ 填报工时" width="540px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <el-select v-model="form.projectId" placeholder="选择项目" filterable style="width:100%">
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="人员" prop="workerName">
          <el-input v-model="form.workerName" placeholder="如：张工" />
        </el-form-item>
        <el-form-item label="人员ID" prop="workerId">
          <el-input-number v-model="form.workerId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="工作日期" prop="workDate">
          <el-date-picker v-model="form.workDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="工时" prop="hours">
          <el-input-number v-model="form.hours" :min="0.5" :step="0.5" :max="24" style="width:100%" />
        </el-form-item>
        <el-form-item label="需求ID">
          <el-input-number v-model="form.requirementId" :min="1" style="width:100%" placeholder="可选：关联需求" />
        </el-form-item>
        <el-form-item label="任务ID">
          <el-input-number v-model="form.taskId" :min="1" style="width:100%" placeholder="可选：关联任务" />
        </el-form-item>
        <el-form-item label="工作内容">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="描述本次工作的内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface Project { id: number; projectName: string; }
interface Worklog {
  id?: number
  projectId?: number
  workerId?: number
  workerName?: string
  workDate?: string
  hours?: number
  requirementId?: number
  taskId?: number
  description?: string
}
interface Summary {
  count: number
  totalHours: number
  byWorker: Record<string, number>
  byTask: Record<string, number>
}

const projectList = ref<Project[]>([])
const filterProjectId = ref<number | null>(null)
const currentProjectName = ref('请选择项目')
const summary = ref<Summary | null>(null)
const showDialog = ref(false)
const submitting = ref(false)
const formRef = ref()

const form = ref<Worklog>({
  projectId: undefined,
  workerId: 1,
  workerName: '',
  workDate: new Date().toISOString().substring(0, 10),
  hours: 4,
  requirementId: undefined,
  taskId: undefined,
  description: ''
})

const rules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  workerName: [{ required: true, message: '请输入人员姓名', trigger: 'blur' }],
  workerId: [{ required: true, message: '请输入人员ID', trigger: 'blur' }],
  workDate: [{ required: true, message: '请选择工作日期', trigger: 'change' }],
  hours: [{ required: true, message: '请输入工时', trigger: 'blur' }]
}

const byWorkerTable = computed(() => {
  if (!summary.value) return []
  const total = Number(summary.value.totalHours) || 1
  return Object.entries(summary.value.byWorker || {}).map(([name, hours]) => ({
    name,
    hours,
    percent: Math.round((Number(hours) / total) * 100)
  }))
})

const byTaskTable = computed(() => {
  if (!summary.value) return []
  return Object.entries(summary.value.byTask || {}).map(([taskId, hours]) => ({
    taskId,
    hours
  }))
})

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
    if (projectList.value.length > 0) {
      filterProjectId.value = projectList.value[0].id
      currentProjectName.value = projectList.value[0].projectName
    }
  } catch (e) {
    console.error('Failed to load projects', e)
  }
}

const fetchSummary = async () => {
  try {
    const params: any = {}
    if (filterProjectId.value) params.projectId = filterProjectId.value
    const res = await request.get('/worklog/summary', { params })
    summary.value = res.data.data
    const p = projectList.value.find(p => p.id === filterProjectId.value)
    if (p) currentProjectName.value = p.projectName
  } catch (e) {
    console.error('Failed to load summary', e)
  }
}

const submit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    submitting.value = true
    try {
      await request.post('/worklog', form.value)
      ElMessage.success('工时已提交')
      showDialog.value = false
      await fetchSummary()
    } catch (e: any) {
      ElMessage.error('提交失败：' + (e?.response?.data?.message || e.message))
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  await fetchProjects()
  await fetchSummary()
})
</script>

<style scoped>
.worklog-container { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 14px; font-weight: 600; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-num { font-size: 28px; font-weight: 700; }
.stat-label { color: #909399; font-size: 13px; margin-top: 4px; }
</style>
