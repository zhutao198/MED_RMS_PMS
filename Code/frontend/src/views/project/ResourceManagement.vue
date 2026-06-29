<template>
  <div class="resource-container">
    <!-- R92 修复：后端已实现 /requirement-tasks/by-project/{id} 端点（R87 临时挂的"功能开发中"已撤除） -->
    <el-card>
      <template #header>
        <div class="card-header">
          <div>
            <span style="font-size:16px;font-weight:600">👥 资源管理（FR-2.8）- {{ projectName }}</span>
            <el-tag v-if="overloadMembers.length > 0" type="danger" size="small" style="margin-left:8px">
              {{ overloadMembers.length }} 人超载
            </el-tag>
            <el-tag v-else-if="members.length > 0" type="success" size="small" style="margin-left:8px">负载正常</el-tag>
          </div>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="projectId" placeholder="选择项目" filterable style="width:280px" @change="fetchAll">
              <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
            </el-select>
            <el-button type="primary" @click="openAdd">添加成员</el-button>
          </div>
        </div>
      </template>

      <!-- 资源冲突警告 -->
      <el-alert v-if="overloadMembers.length > 0" type="error" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          ⚠️ 资源冲突：以下成员工作量超出阈值（>15）
        </template>
        <div v-for="m in overloadMembers" :key="m.userId" class="conflict-line">
          <b>{{ m.realName }}</b> - 共 {{ m.totalLoad }} 项任务（需求 {{ m.requirementCount }} + 风险 {{ m.riskCount }} + 任务 {{ m.taskCount }}）
        </div>
      </el-alert>

      <el-alert v-if="crossProjectMembers.length > 0" type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        <template #title>
          📢 跨项目成员：{{ crossProjectMembers.length }} 人同时参与多个项目
        </template>
        <div v-for="m in crossProjectMembers" :key="m.userId" class="conflict-line">
          <b>{{ m.realName }}</b> - 参与 {{ m.projects.length }} 个项目：{{ m.projects.join('、') }}
        </div>
      </el-alert>

      <!-- 成员卡片 -->
      <el-row :gutter="16" style="margin-top: 8px">
        <el-col v-for="m in membersWithLoad" :key="m.id" :xs="24" :sm="12" :md="8" :lg="6" style="margin-bottom: 16px">
          <el-card shadow="hover" class="member-card" :class="{ 'is-overload': m.totalLoad > 15 }">
            <div class="member-header">
              <el-avatar :size="48" style="background:#409eff">{{ (m.realName || m.username || '?').charAt(0) }}</el-avatar>
              <div class="member-info">
                <div class="member-name">{{ m.realName || m.username }}</div>
                <el-tag size="small" :type="getRoleType(m.role)">{{ getRoleLabel(m.role) }}</el-tag>
              </div>
            </div>
            <el-divider style="margin: 12px 0" />
            <div class="load-row">
              <span class="load-label">📋 需求</span>
              <el-progress :percentage="Math.min(100, m.requirementCount * 20)" :stroke-width="10" />
              <span class="load-num">{{ m.requirementCount }}</span>
            </div>
            <div class="load-row">
              <span class="load-label">⚠️ 风险</span>
              <el-progress :percentage="Math.min(100, m.riskCount * 25)" :stroke-width="10" status="warning" />
              <span class="load-num">{{ m.riskCount }}</span>
            </div>
            <div class="load-row">
              <span class="load-label">🔨 任务</span>
              <el-progress :percentage="Math.min(100, m.taskCount * 20)" :stroke-width="10" status="success" />
              <span class="load-num">{{ m.taskCount }}</span>
            </div>
            <div class="total-load">
              <span>总负载：<b :class="{ 'overload-text': m.totalLoad > 15 }">{{ m.totalLoad }}</b></span>
            </div>
            <div class="member-actions">
              <el-button size="small" @click="openEdit(m)">编辑</el-button>
              <el-dropdown @command="(c: string) => switchRole(m, c)">
                <el-button size="small" type="primary">切换角色</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="r in ROLE_OPTIONS" :key="r.value" :command="r.value">{{ r.label }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button size="small" type="danger" @click="removeMember(m)">移除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-empty v-if="members.length === 0" description="该项目暂无成员" />
    </el-card>

    <!-- 添加/编辑成员对话框 -->
    <el-dialog v-model="showDialog" :title="dialogMode === 'add' ? '添加成员' : '编辑成员'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item v-if="dialogMode === 'add'" label="选择用户" required>
          <el-select v-model="form.userId" filterable placeholder="选择系统用户" style="width:100%" @change="onUserChange">
            <el-option v-for="u in allUsers" :key="u.id" :label="`${u.username} (${u.realName || '-'})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width:100%">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

interface ProjectMember {
  id: number
  projectId: number
  projectNo: string
  userId: number
  username: string
  realName: string
  role: string
  department: string
  joinedAt: string
  status: string
}

interface User {
  id: number
  username: string
  realName: string
  department: string
}

const projectList = ref<any[]>([])
const projectId = ref<number | null>(null)
const projectName = ref('')
const members = ref<ProjectMember[]>([])
const allMembersAcrossProjects = ref<ProjectMember[]>([])
const allUsers = ref<User[]>([])
const requirements = ref<any[]>([])
const risks = ref<any[]>([])
const tasks = ref<any[]>([])

const showDialog = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const form = ref<any>({ id: 0, userId: null, realName: '', role: 'MEMBER', department: '' })

const membersWithLoad = computed(() => {
  return members.value.map(m => {
    const requirementCount = requirements.value.filter((r: any) => r.createdBy === m.userId || r.updatedBy === m.userId).length
    const riskCount = risks.value.filter((r: any) => r.assessedBy === m.userId || r.ownerId === m.userId).length
    const taskCount = tasks.value.filter((t: any) => t.assigneeId === m.userId || t.ownerId === m.userId).length
    return { ...m, requirementCount, riskCount, taskCount, totalLoad: requirementCount + riskCount + taskCount }
  })
})

const overloadMembers = computed(() => membersWithLoad.value.filter(m => m.totalLoad > 15))

const crossProjectMembers = computed(() => {
  const map = new Map<number, any>()
  for (const m of allMembersAcrossProjects.value) {
    if (!map.has(m.userId)) {
      map.set(m.userId, { userId: m.userId, realName: m.realName, projects: [] as string[] })
    }
    const p = projectList.value.find(p => p.id === m.projectId)
    if (p && !map.get(m.userId).projects.includes(p.projectName)) {
      map.get(m.userId).projects.push(p.projectName)
    }
  }
  return Array.from(map.values()).filter((m: any) => m.projects.length > 1)
})

const getRoleLabel = (role: string) => ({
  MANAGER: '项目经理', LEADER: '组长', MEMBER: '成员',
  PROJECT_MANAGER: '项目经理', DEVELOPER: '开发工程师', TESTER: '测试工程师',
  REQUIREMENT_ENGINEER: '需求工程师', QUALITY: '质量工程师',
  ARCHITECT: '架构师', REVIEWER: '评审专家'
} as Record<string, string>)[role] || role
const getRoleType = (role: string) => ({
  MANAGER: 'danger', PROJECT_MANAGER: 'danger', LEADER: 'warning',
  DEVELOPER: 'primary', REQUIREMENT_ENGINEER: 'success', TESTER: 'info',
  QUALITY: 'warning', MEMBER: '', ARCHITECT: 'danger', REVIEWER: 'info'
} as Record<string, string>)[role] || ''

const ROLE_OPTIONS = [
  { label: '项目经理', value: 'MANAGER' },
  { label: '组长', value: 'LEADER' },
  { label: '开发工程师', value: 'DEVELOPER' },
  { label: '需求工程师', value: 'REQUIREMENT_ENGINEER' },
  { label: '测试工程师', value: 'TESTER' },
  { label: '质量工程师', value: 'QUALITY' },
  { label: '架构师', value: 'ARCHITECT' },
  { label: '评审专家', value: 'REVIEWER' },
  { label: '成员', value: 'MEMBER' }
]

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

const fetchAllUsers = async () => {
  try {
    const res = await request.get('/system/users')
    allUsers.value = res.data?.data || []
  } catch {}
}

const fetchAll = async () => {
  if (!projectId.value) return
  try {
    const p = projectList.value.find(p => p.id === projectId.value)
    if (p) projectName.value = p.projectName
    const [mRes, reqRes, riskRes, taskRes, allMRes] = await Promise.allSettled([
      request.get(`/project/member/list/${projectId.value}`),
      request.get('/requirements', { params: { projectId: projectId.value, page: 0, size: 500 } }),
      request.get(`/risk/list/${projectId.value}`),
      request.get(`/requirement-tasks/by-project/${projectId.value}`),
      Promise.all(projectList.value.map(pj => request.get(`/project/member/list/${pj.id}`).then(r => (r.data?.data || []).map((m: ProjectMember) => ({ ...m, projectId: pj.id }))).catch(() => []))).then(arr => arr.flat())
    ])
    members.value = mRes.status === 'fulfilled' ? (mRes.value.data?.data || []) : []
    const reqData = reqRes.status === 'fulfilled' ? (reqRes.value.data?.data || {}) : {}
    requirements.value = Array.isArray(reqData) ? reqData : (reqData.records || [])
    risks.value = riskRes.status === 'fulfilled' ? (riskRes.value.data?.data || []) : []
    tasks.value = taskRes.status === 'fulfilled' ? (taskRes.value.data?.data || []) : []
    allMembersAcrossProjects.value = allMRes.status === 'fulfilled' ? (allMRes.value || []) : []
  } catch {}
}

const onUserChange = (userId: number) => {
  const u = allUsers.value.find(u => u.id === userId)
  if (u) {
    form.value.realName = u.realName || u.username
    form.value.department = u.department || ''
  }
}

const openAdd = () => {
  dialogMode.value = 'add'
  form.value = { id: 0, userId: null, realName: '', role: 'MEMBER', department: '' }
  showDialog.value = true
}

const openEdit = (m: ProjectMember) => {
  dialogMode.value = 'edit'
  form.value = { id: m.id, userId: m.userId, realName: m.realName, role: m.role, department: m.department }
  showDialog.value = true
}

const confirmSave = async () => {
  if (dialogMode.value === 'add' && !form.value.userId) {
    ElMessage.warning('请选择用户')
    return
  }
  try {
    if (dialogMode.value === 'add') {
      await request.post('/project/member', {
        projectId: projectId.value,
        userId: form.value.userId,
        username: allUsers.value.find(u => u.id === form.value.userId)?.username,
        realName: form.value.realName,
        role: form.value.role,
        department: form.value.department
      })
      ElMessage.success('已添加')
    } else {
      await request.put(`/project/member/${form.value.id}`, form.value)
      ElMessage.success('已更新')
    }
    showDialog.value = false
    fetchAll()
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e?.response?.data?.message || e.message))
  }
}

const switchRole = async (m: ProjectMember, role: string) => {
  try {
    await request.post(`/project/member/${m.id}/switch-role?role=${role}`)
    ElMessage.success('角色已切换')
    fetchAll()
  } catch (e: any) {
    ElMessage.error('切换失败：' + (e?.response?.data?.message || e.message))
  }
}

const removeMember = async (m: ProjectMember) => {
  try {
    await ElMessageBox.confirm(`确认移除成员 ${m.realName}?`, '移除成员', { type: 'warning' })
    await request.delete(`/project/member/${m.id}`)
    ElMessage.success('已移除')
    fetchAll()
  } catch {}
}

onMounted(async () => {
  await fetchProjects()
  await fetchAllUsers()
  await fetchAll()
})
</script>

<style scoped>
.resource-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.member-card { border-radius: 8px; }
.member-card.is-overload { border: 2px solid #F56C6C; }
.member-header { display: flex; align-items: center; gap: 12px; }
.member-info { flex: 1; }
.member-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
.load-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.load-label { font-size: 12px; color: #606266; min-width: 50px; }
.load-num { font-size: 12px; color: #303133; min-width: 24px; text-align: right; }
.total-load { margin-top: 8px; padding-top: 8px; border-top: 1px dashed #ebeef5; font-size: 13px; }
.overload-text { color: #F56C6C; font-size: 16px; }
.member-actions { display: flex; gap: 4px; margin-top: 12px; flex-wrap: wrap; }
.conflict-line { font-size: 13px; margin-top: 4px; }
</style>
