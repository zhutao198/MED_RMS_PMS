<template>
  <div class="project-detail-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <div class="project-title">
            <h2>{{ project.projectName }}</h2>
            <el-tag :type="getStatusType(project.status)" size="small">{{ getStatusLabel(project.status) }}</el-tag>
          </div>
          <div class="header-actions">
            <el-button @click="$router.push('/projects')">返回列表</el-button>
            <el-button type="primary" @click="editProjectBasic">编辑项目</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="概览" name="overview">
          <div class="stat-cards">
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num">{{ stats.totalRequirements }}</div>
              <div class="stat-label">需求总数</div>
            </el-card>
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num text-success">{{ stats.completedRequirements }}</div>
              <div class="stat-label">已完成</div>
            </el-card>
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num text-warning">{{ stats.inProgressRequirements }}</div>
              <div class="stat-label">进行中</div>
            </el-card>
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num text-primary">{{ stats.overallProgress }}%</div>
              <div class="stat-label">总体进度</div>
            </el-card>
          </div>
        </el-tab-pane>
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="项目编号">{{ project.projectNo }}</el-descriptions-item>
            <el-descriptions-item label="项目经理">{{ project.managerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ getStatusLabel(project.status) }}</el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ project.startDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ project.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结束日期">{{ project.endDate || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="DCP门控" name="gates">
          <div class="gates-section">
            <div class="section-header">
              <span>门控列表</span>
            </div>
            <el-table :data="gates" border stripe>
              <el-table-column prop="gateNo" label="门控编号" width="100" />
              <el-table-column prop="gateName" label="门控名称" min-width="150" />
              <el-table-column prop="gateType" label="门控类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="getGateType(row.gateType)" size="small">{{ row.gateType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="plannedDate" label="计划日期" width="120" />
              <el-table-column prop="actualDate" label="实际日期" width="120" />
              <!-- v1.53 P1-21：签署人 + 签署状态（与电子签名系统打通） -->
              <el-table-column label="签署人" width="120">
                <template #default="{ row }">
                  <span v-if="row.signerName">{{ row.signerName }}</span>
                  <span v-else class="text-muted">未指定</span>
                </template>
              </el-table-column>
              <el-table-column label="签署状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="getSignStatusType(row.signStatus)" size="small">
                    {{ getSignStatusLabel(row.signStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="viewGateDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="成员管理" name="members">
          <div class="members-section">
            <div class="section-header">
              <span>项目成员</span>
              <el-button type="primary" size="small" @click="showAddMember = true">添加成员</el-button>
            </div>
            <el-table :data="members" border stripe>
              <el-table-column prop="realName" label="姓名" width="120" />
              <el-table-column prop="role" label="角色" width="120" />
              <el-table-column prop="department" label="部门" width="150" />
              <el-table-column prop="joinedAt" label="加入日期" width="120" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" type="danger" @click="removeMember(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="里程碑" name="milestones">
          <div class="milestones-section">
            <div class="section-header">
              <span>里程碑</span>
              <el-button type="primary" size="small" @click="showAddMilestone = true">添加里程碑</el-button>
            </div>
            <el-table :data="milestones" border stripe>
              <el-table-column prop="name" label="名称" min-width="150" />
              <el-table-column prop="gateType" label="门控类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="getGateType(row.gateType)" size="small">{{ row.gateType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="plannedDate" label="计划日期" width="120" />
              <el-table-column prop="actualDate" label="实际日期" width="120" />
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="甘特图" name="gantt">
          <div class="gantt-placeholder">
            <el-button type="primary" @click="$router.push(`/projects/${projectId}/gantt`)">查看甘特图</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 添加成员对话框 -->
    <el-dialog v-model="showAddMember" title="添加成员" width="400px">
      <el-form :model="memberForm" label-width="80px">
        <el-form-item label="姓名">
          <el-input v-model="memberForm.realName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="memberForm.role">
            <el-option label="项目经理" value="PROJECT_MANAGER" />
            <el-option label="需求工程师" value="REQUIREMENT_ENGINEER" />
            <el-option label="开发工程师" value="DEVELOPER" />
            <el-option label="测试工程师" value="TESTER" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="memberForm.department" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMember = false">取消</el-button>
        <el-button type="primary" @click="addMember">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加里程碑对话框 -->
    <el-dialog v-model="showAddMilestone" title="添加里程碑" width="400px">
      <el-form :model="milestoneForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="milestoneForm.name" />
        </el-form-item>
        <el-form-item label="门控类型">
          <el-select v-model="milestoneForm.gateType">
            <el-option label="DCP1" value="DCP1" />
            <el-option label="DCP2" value="DCP2" />
            <el-option label="DCP3" value="DCP3" />
            <el-option label="DCP4" value="DCP4" />
            <el-option label="DCP5" value="DCP5" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="milestoneForm.plannedDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMilestone = false">取消</el-button>
        <el-button type="primary" @click="addMilestone">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑项目对话框 -->
    <el-dialog v-model="showEditProject" title="编辑项目" width="500px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="项目名称" required>
          <el-input v-model="editForm.projectName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width:100%">
            <el-option label="计划中" value="PLANNING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="editForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="editForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditProject = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveProjectEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectApi, ipdGateApi, projectMemberApi, type Project, type IpdGate, type ProjectMember } from '@/api/project'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const projectId = ref(Number(route.params.id))
// R105 D1 修复：从 userStore 取当前操作者 userId（之前硬编码 1 会让新成员都关联到 admin）
const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('info')
const project = ref<Project>({} as Project)
const stats = ref({ totalRequirements: 0, completedRequirements: 0, inProgressRequirements: 0, overallProgress: 0 })
const gates = ref<IpdGate[]>([])
const members = ref<ProjectMember[]>([])
const milestones = ref<any[]>([])

const showAddMember = ref(false)
const showAddMilestone = ref(false)
const showEditProject = ref(false)
const editForm = ref<{ projectName: string; description: string; startDate: string; endDate: string; status: string }>({
  projectName: '', description: '', startDate: '', endDate: '', status: 'PLANNING'
})
const editSaving = ref(false)

const memberForm = ref({
  realName: '',
  role: 'REQUIREMENT_ENGINEER',
  department: '',
})

const milestoneForm = ref({
  name: '',
  gateType: 'DCP1',
  plannedDate: '',
})

const fetchProject = async () => {
  loading.value = true
  try {
    const res = await projectApi.get(projectId.value)
    project.value = res.data.data || {}
  } catch {
    ElMessage.error('获取项目详情失败')
  } finally {
    loading.value = false
  }
}

const fetchGates = async () => {
  try {
    const res = await ipdGateApi.listByProject(projectId.value)
    // WHY: 后端字段名兼容 — gateReviewerId/userId 统一映射为 signerId
    gates.value = (res.data.data || []).map((g: any) => ({
      ...g,
      signerId: g.gateReviewerId ?? g.userId ?? g.signerId ?? null,
      signerName: g.signerName ?? g.gateReviewerName ?? g.reviewer ?? null,
      signStatus: g.signStatus ?? (g.status === 'COMPLETED' ? 'SIGNED' : g.status === 'REJECTED' ? 'REJECTED' : 'UNSIGNED'),
    }))
  } catch {
    // ignore
  }
}

// v1.53 P1-21：签署状态映射
const SIGN_STATUS_MAP: Record<string, { type: string; label: string }> = {
  UNSIGNED: { type: 'info', label: '未签署' },
  SIGNED:   { type: 'success', label: '已签署' },
  REJECTED: { type: 'danger', label: '已拒签' },
}
const getSignStatusType = (s?: string) => (s && SIGN_STATUS_MAP[s]?.type) || 'info'
const getSignStatusLabel = (s?: string) => (s && SIGN_STATUS_MAP[s]?.label) || '未签署'

const fetchMembers = async () => {
  try {
    const res = await projectMemberApi.listByProject(projectId.value)
    members.value = res.data.data || []
  } catch {
    // ignore
  }
}

const fetchMilestones = async () => {
  try {
    const res = await request.get(`/gantt/milestones/project/${projectId.value}`)
    milestones.value = res.data?.data || []
  } catch {
    milestones.value = []
  }
}

const fetchProjectStats = async () => {
  try {
    // R84 修复：原 /requirements/list 错误路径 → Spring 路由到 /requirements/{id} → SY0101 参数类型不匹配
    // 正确端点：/requirements（与 RequirementController.listRequirements 对齐）
    const res = await request.get(`/requirements`, {
      params: { projectId: projectId.value, page: 0, size: 1 }
    })
    const total = res.data?.data?.total || 0
    let completed = 0
    let inProgress = 0
    try {
      const closedRes = await request.get(`/requirements`, {
        params: { projectId: projectId.value, page: 0, size: 1000 }
      })
      const all = closedRes.data?.data?.records || []
      completed = all.filter((r: any) => ['Verified', 'Baseline', 'Closed'].includes(r.status)).length
      inProgress = all.filter((r: any) => ['Submitted', 'InReview', 'Approved', 'Implemented', 'InProgress', 'InTest'].includes(r.status)).length
    } catch {
      // ignore
    }
    stats.value = {
      totalRequirements: total,
      completedRequirements: completed,
      inProgressRequirements: inProgress,
      overallProgress: total > 0 ? Math.round((completed / total) * 100) : 0
    }
  } catch {
    stats.value = { totalRequirements: 0, completedRequirements: 0, inProgressRequirements: 0, overallProgress: 0 }
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PLANNING: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', TERMINATED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PLANNING: '计划中', IN_PROGRESS: '进行中', COMPLETED: '已完成', TERMINATED: '已终止'
  }
  return map[status] || status
}

const getGateType = (gateType: string) => {
  const map: Record<string, string> = {
    DCP1: 'primary', DCP2: 'success', DCP3: 'warning', DCP4: 'danger', DCP5: 'info'
  }
  return map[gateType] || 'info'
}

const viewGateDetail = async (gate: IpdGate) => {
  if (!gate.id) {
    ElMessage.warning('门控尚未保存，无详情')
    return
  }
  try {
    const res = await request.get(`/gantt/gate/${gate.id}/check`)
    const data = res.data?.data
    if (data) {
      const verdict = data.verdict || data.checkResult || 'UNKNOWN'
      const passed = data.passedItems ?? data.passedCount ?? 0
      const total = data.totalItems ?? data.totalCount ?? 0
      ElMessageBox.alert(`门控：${gate.gateName}\n判定：${verdict}\n通过项 ${passed}/${total}`, '门控自动检查', { type: verdict === 'PASS' ? 'success' : 'warning' })
    } else {
      ElMessage.info(`门控：${gate.gateName}\n状态：${gate.status}`)
    }
  } catch (e: any) {
    ElMessage.info(`门控：${gate.gateName}\n状态：${gate.status}\n计划：${gate.plannedDate || '-'}\n实际：${gate.actualDate || '-'}`)
  }
}

const addMember = async () => {
  if (!memberForm.value.realName) {
    ElMessage.warning('请输入成员姓名')
    return
  }
  try {
    await projectMemberApi.add({
      projectId: projectId.value,
      // R105 D1 修复：userId 从当前 userStore 取（之前硬编码 1 导致所有新成员记录与 admin 重复）
      userId: userStore.userInfo?.id,
      realName: memberForm.value.realName,
      role: memberForm.value.role,
      department: memberForm.value.department,
      status: 'ACTIVE',
    } as any)
    ElMessage.success('添加成功')
    showAddMember.value = false
    memberForm.value = { realName: '', role: 'REQUIREMENT_ENGINEER', department: '' }
    fetchMembers()
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e?.response?.data?.message || e.message))
  }
}

const removeMember = async (member: ProjectMember) => {
  if (!member.id) {
    ElMessage.warning('该成员缺少主键，无法移除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认移除成员 ${member.realName}？`, '移除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await projectMemberApi.remove(member.id)
    ElMessage.success('移除成功')
    fetchMembers()
  } catch (e: any) {
    ElMessage.error('移除失败：' + (e?.response?.data?.message || e.message))
  }
}

const addMilestone = async () => {
  if (!milestoneForm.value.name || !milestoneForm.value.plannedDate) {
    ElMessage.warning('请填写名称与计划日期')
    return
  }
  try {
    await request.post('/gantt/milestones', {
      projectId: projectId.value,
      name: milestoneForm.value.name,
      gateType: milestoneForm.value.gateType,
      plannedDate: milestoneForm.value.plannedDate,
      status: 'PLANNED'
    })
    ElMessage.success('里程碑已添加')
    showAddMilestone.value = false
    milestoneForm.value = { name: '', gateType: 'DCP1', plannedDate: '' }
    fetchMilestones()
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e?.response?.data?.message || e.message))
  }
}

const editProjectBasic = () => {
  editForm.value = {
    projectName: project.value.projectName || '',
    description: project.value.description || '',
    startDate: project.value.startDate || '',
    endDate: project.value.endDate || '',
    status: project.value.status || 'PLANNING'
  }
  showEditProject.value = true
}

const saveProjectEdit = async () => {
  if (!editForm.value.projectName) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  editSaving.value = true
  try {
    await projectApi.update(projectId.value, editForm.value as any)
    ElMessage.success('已保存')
    showEditProject.value = false
    await fetchProject()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally {
    editSaving.value = false
  }
}

onMounted(() => {
  fetchProject()
  fetchGates()
  fetchMembers()
  fetchMilestones()
  fetchProjectStats()
})
</script>

<style scoped>
.project-detail-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.project-title { display: flex; align-items: center; gap: 12px; }
.project-title h2 { margin: 0; }
.header-actions { display: flex; gap: 8px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.gantt-placeholder { padding: 40px; text-align: center; }

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 12px 0;
}
.stat-card { text-align: center; padding: 18px 0; }
.stat-num { font-size: 30px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.text-success { color: #67c23a; }
.text-warning { color: #e6a23c; }
.text-primary { color: #409eff; }
.text-muted { color: #909399; font-size: 12px; }
</style>