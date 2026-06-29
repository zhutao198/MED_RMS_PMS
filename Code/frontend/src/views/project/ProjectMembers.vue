<template>
  <div class="project-members-container">
    <el-page-header @back="$router.back()" :content="`项目 #${projectId} 成员管理`" class="page-back" />

    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>成员列表（共 {{ members.length }} 人）</span>
          <el-button type="primary" @click="$router.push(`/projects/${projectId}/members/add`)">添加成员</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索姓名/角色/部门" clearable style="width: 220px;" @input="filterRows" />
      </el-form>

      <el-table :data="filteredMembers" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="role" label="角色" width="160">
          <template #default="{ row }">
            <el-tag size="small">{{ getRoleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="140" />
        <el-table-column prop="joinedAt" label="加入日期" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '在岗' : '已离岗' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="switchRoleDialog(row)">切换角色</el-button>
            <el-button size="small" type="danger" text @click="removeMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="filteredMembers.length === 0 && !loading" description="暂无成员" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectMemberApi, type ProjectMember } from '@/api/project'

const route = useRoute()
const projectId = Number(route.params.id)
const loading = ref(false)
const members = ref<ProjectMember[]>([])
const keyword = ref('')

const ROLE_LABELS: Record<string, string> = {
  PROJECT_MANAGER: '项目经理',
  REQUIREMENT_ENGINEER: '需求工程师',
  DEVELOPER: '开发工程师',
  TESTER: '测试工程师',
  QA: 'QA',
}
const getRoleLabel = (r: string) => ROLE_LABELS[r] || r || '-'

const filteredMembers = computed(() => {
  if (!keyword.value) return members.value
  const k = keyword.value.toLowerCase()
  return members.value.filter(m =>
    (m.realName || '').toLowerCase().includes(k) ||
    (m.role || '').toLowerCase().includes(k) ||
    (m.department || '').toLowerCase().includes(k)
  )
})

const fetchMembers = async () => {
  loading.value = true
  try {
    const res = await projectMemberApi.listByProject(projectId)
    members.value = res.data.data || []
  } catch (e: any) {
    ElMessage.error('加载成员失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const filterRows = () => { /* computed 自动重算 */ }

const switchRoleDialog = async (member: ProjectMember) => {
  if (!member.id) return
  try {
    const { value: newRole } = await ElMessageBox.prompt('请输入新角色编码：PROJECT_MANAGER / REQUIREMENT_ENGINEER / DEVELOPER / TESTER / QA', '切换角色', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: member.role,
    })
    await projectMemberApi.switchRole(member.id, newRole)
    ElMessage.success('已切换')
    fetchMembers()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('切换失败：' + (e?.response?.data?.message || e?.message))
  }
}

const removeMember = async (member: ProjectMember) => {
  if (!member.id) return
  try {
    await ElMessageBox.confirm(`确认移除成员 ${member.realName} ？`, '移除确认', { type: 'warning' })
    await projectMemberApi.remove(member.id)
    ElMessage.success('已移除')
    fetchMembers()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('移除失败：' + (e?.response?.data?.message || e?.message))
  }
}

onMounted(fetchMembers)
</script>

<style scoped>
.project-members-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-bar { margin-bottom: 12px; }
</style>
