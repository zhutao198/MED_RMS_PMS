<template>
  <div class="milestone-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span style="font-size:16px;font-weight:600">🎯 里程碑管理（FR-2.6）- {{ projectName }}</span>
            <el-tag v-if="overdueCount > 0" type="danger" size="small" style="margin-left:8px">{{ overdueCount }} 项延期</el-tag>
            <el-tag v-else-if="milestones.length > 0" type="success" size="small" style="margin-left:8px">全部按计划</el-tag>
          </div>
          <div style="display:flex;gap:8px">
            <el-select v-model="projectId" placeholder="选择项目" filterable style="width:280px" @change="fetchData">
              <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
            </el-select>
            <el-button type="primary" @click="showMilestoneDialog = true">新建里程碑</el-button>
          </div>
        </div>
      </template>

      <el-steps :active="currentStep" align-center finish-status="success">
        <el-step v-for="m in milestones" :key="m.id"
          :title="m.name"
          :description="m.gateType + ' - ' + (m.plannedDate || '')"
          :status="getStepStatus(m)"
        />
      </el-steps>

      <el-divider />

      <el-table :data="milestones" border stripe>
        <el-table-column prop="milestoneNo" label="里程碑编号" width="150" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="gateType" label="阶段门" width="100">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.gateType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plannedDate" label="计划日期" width="120" />
        <el-table-column prop="actualDate" label="实际日期" width="120" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkResult" label="检查结果" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.checkResult" :type="row.checkResult === 'PASS' ? 'success' : 'danger'">
              {{ row.checkResult }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="checkGate(row)">Gantt 检查</el-button>
            <el-button size="small" type="primary" @click="ipdCheck(row)" v-if="row.gateType">IPD 自动检查</el-button>
            <el-button size="small" type="success" @click="completeMilestone(row)" v-if="row.status !== 'COMPLETED'">完成</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showMilestoneDialog" title="新建里程碑" width="400px">
      <el-form :model="milestoneForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="milestoneForm.name" />
        </el-form-item>
        <el-form-item label="阶段门">
          <el-select v-model="milestoneForm.gateType">
            <el-option label="概念阶段门(DCP1)" value="DCP1" />
            <el-option label="计划阶段门(DCP2)" value="DCP2" />
            <el-option label="开发阶段门(DCP3)" value="DCP3" />
            <el-option label="验证阶段门(DCP4)" value="DCP4" />
            <el-option label="发布阶段门(DCP5)" value="DCP5" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="milestoneForm.plannedDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="milestoneForm.description" type="textarea" rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMilestoneDialog = false">取消</el-button>
        <el-button type="primary" @click="createMilestone">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="ipdResultVisible" :title="`IPD 阶段门自动检查 - DCP${ipdGateNo}`" width="700px">
      <div v-if="ipdResult">
        <el-alert :type="ipdResult.verdict === 'PASS' ? 'success' : 'error'" :closable="false" show-icon>
          <template #title>
            <b>{{ ipdResult.verdict === 'PASS' ? '✅ 通过' : '❌ 不通过' }}</b>
            <span style="margin-left:12px">通过项 {{ ipdResult.passedItems }}/{{ ipdResult.totalItems }}</span>
          </template>
        </el-alert>
        <el-table :data="ipdResult.items" border size="small" style="margin-top:12px">
          <el-table-column label="检查项" prop="name" width="160" />
          <el-table-column label="标准" prop="criterion" />
          <el-table-column label="实际" prop="actual" width="100" align="center" />
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.pass" type="success" size="small">✅</el-tag>
              <el-tag v-else type="danger" size="small">❌</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

interface Milestone {
  id: number
  milestoneNo: string
  name: string
  gateType: string
  plannedDate: string
  actualDate: string
  status: string
  checkResult: string
}

const projectList = ref<any[]>([])
// R105 D3 修复：移除硬编码默认值 1；fetchProjects() 异步加载后会自动选第一个项目
const projectId = ref<number | undefined>(undefined)
const projectName = ref('加载中...')
const milestones = ref<Milestone[]>([])
const currentStep = ref(0)
const showMilestoneDialog = ref(false)
const ipdResultVisible = ref(false)
const ipdResult = ref<any>(null)
const ipdGateNo = ref(0)

const milestoneForm = ref({
  name: '',
  gateType: 'DCP1',
  plannedDate: '',
  description: '',
  // R105 D3 修复：默认值改 undefined，等用户选项目后赋值
  projectId: undefined as number | undefined
})

const overdueCount = computed(() => {
  const today = new Date().toISOString().split('T')[0]
  return milestones.value.filter(m => m.plannedDate && m.plannedDate < today && m.status !== 'COMPLETED').length
})

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
    if (projectList.value.length > 0) {
      projectId.value = projectList.value[0].id
      projectName.value = projectList.value[0].projectName
    }
  } catch {}
}

const fetchData = async () => {
  try {
    const res = await request.get(`/gantt/milestones/project/${projectId.value}`)
    milestones.value = res.data.data || []
    const p = projectList.value.find(p => p.id === projectId.value)
    if (p) projectName.value = p.projectName
    updateStep()
  } catch {
    // ignore
  }
}

const updateStep = () => {
  const completedCount = milestones.value.filter(m => m.status === 'COMPLETED').length
  currentStep.value = Math.min(completedCount, milestones.value.length - 1)
}

const getStepStatus = (m: Milestone) => {
  if (m.status === 'COMPLETED') return 'success'
  if (m.status === 'IN_PROGRESS') return 'process'
  if (m.status === 'DELAYED') return 'error'
  // 自动判定延期
  if (m.plannedDate && m.plannedDate < new Date().toISOString().split('T')[0] && m.status !== 'COMPLETED') return 'error'
  return 'wait'
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { PLANNED: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', DELAYED: 'danger' }
  return map[status] || 'info'
}

const milestoneStatusLabels: Record<string, string> = {
  PLANNED: '计划中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  DELAYED: '已延期',
}

const getStatusLabel = (status: string) => milestoneStatusLabels[status] || status

const createMilestone = async () => {
  if (!milestoneForm.value.name) {
    ElMessage.warning('请填写名称')
    return
  }
  if (!projectId.value) {
    ElMessage.warning('项目尚未加载完成，请稍后重试')
    return
  }
  try {
    milestoneForm.value.projectId = projectId.value
    await request.post('/gantt/milestones', milestoneForm.value)
    ElMessage.success('创建成功')
    showMilestoneDialog.value = false
    fetchData()
  } catch {
    ElMessage.error('创建失败')
  }
}

const checkGate = async (row: Milestone) => {
  try {
    const res = await request.get(`/gantt/gate/${row.id}/check`)
    const result = res.data.data || {}
    ElMessage.info(`检查结果: ${result.result} - ${result.message}`)
  } catch {
    ElMessage.error('检查失败')
  }
}

const ipdCheck = async (row: Milestone) => {
  const m = (row.gateType || '').match(/DCP(\d)/)
  if (!m) { ElMessage.warning('该里程碑无 DCP 门对应') }
  const gateNo = m ? parseInt(m[1]) : 0
  ipdGateNo.value = gateNo
  ipdResult.value = null
  ipdResultVisible.value = true
  try {
    // 复用 IPD 自动检查的统计
    const [reqRes, riskRes, compRes, evRes] = await Promise.allSettled([
      request.get('/requirements/quality', { params: { projectId: projectId.value } }),
      request.get(`/risk/report/${projectId.value}`),
      request.get(`/compliance/iec62304/checklist/${projectId.value}/stats`),
      request.get(`/compliance/evidence/${projectId.value}`)
    ])
    const reqs = reqRes.status === 'fulfilled' ? (reqRes.value.data?.data || []) : []
    const approvedReqs = (Array.isArray(reqs) ? reqs : []).filter((r: any) => ['Approved', 'Baseline', 'Verified'].includes(r.status)).length
    const riskStats = riskRes.status === 'fulfilled' ? (riskRes.value.data?.data || {}) : {}
    const compStats = compRes.status === 'fulfilled' ? (compRes.value.data?.data || {}) : {}
    const evList = evRes.status === 'fulfilled' ? (evRes.value.data?.data || []) : []

    const res = await request.post('/project/ipd-gate/auto-check', {
      projectId: projectId.value,
      gateNo,
      requirementCount: Array.isArray(reqs) ? reqs.length : 0,
      approvedRequirementCount: approvedReqs,
      riskCount: riskStats.totalRisks || 0,
      highRiskCount: riskStats.highRisks || 0,
      testCaseCount: 0,
      passedTestCaseCount: 0,
      iecCompliantCount: compStats.compliant || 0,
      totalIecItems: compStats.total || 0,
      dhfEvidenceCount: Array.isArray(evList) ? evList.length : 0
    })
    ipdResult.value = res.data?.data
  } catch (e: any) {
    ElMessage.error('IPD 检查失败：' + (e?.response?.data?.message || e.message))
  }
}

const completeMilestone = async (row: Milestone) => {
  try {
    await ElMessageBox.confirm('确认完成此里程碑?', '完成里程碑')
    row.status = 'COMPLETED'
    row.actualDate = new Date().toISOString().split('T')[0]
    await request.put(`/gantt/milestones/${row.id}`, row)
    ElMessage.success('里程碑已完成')
    fetchData()
  } catch {
    // cancel
  }
}

onMounted(async () => {
  await fetchProjects()
  await fetchData()
})
</script>

<style scoped>
.milestone-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
</style>