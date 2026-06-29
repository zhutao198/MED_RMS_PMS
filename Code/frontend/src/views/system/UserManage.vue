<template>
  <div class="user-manage-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="部门">
          <el-input v-model="filterDept" placeholder="请输入部门" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filterRole" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="fetchUsers">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="users" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '—' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="电话" width="130">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="120">
          <template #default="{ row }">{{ row.department || '—' }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="150">
          <template #default="{ row }">
            <el-tag>{{ getRoleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <!-- v1.53 P1-24：三态 status — active/locked/pending -->
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="220">
          <template #default="{ row }">
            <!-- v1.53 P1-24：多角色展示 -->
            <el-tag v-for="r in getUserRoles(row)" :key="r" size="small" style="margin-right: 4px;">{{ getRoleLabel(r) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editUser(row)">编辑</el-button>
            <!-- v1.53 P1-24：解锁/激活按钮（按当前状态动态展示） -->
            <el-button v-if="row.status === 'LOCKED'" size="small" type="success" @click="unlockUser(row)">解锁</el-button>
            <el-button v-else-if="row.status === 'PENDING'" size="small" type="primary" @click="activateUser(row)">激活</el-button>
            <el-button v-else size="small" type="warning" @click="lockUser(row)">锁定</el-button>
            <el-button size="small" type="info" @click="confirmResetPassword(row)">重置密码</el-button>
            <el-button size="small" type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 用户对话框 -->
    <el-dialog
      v-model="showUserDialog"
      :title="editingUser ? '编辑用户' : '新增用户'"
      width="500px"
      @closed="resetUserForm"
    >
      <el-form :model="userForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" :disabled="!!editingUser" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="userForm.realName" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" type="email" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="userForm.department" />
        </el-form-item>
        <el-form-item label="角色">
          <!-- v1.53 P1-24：多角色 — el-select multiple -->
          <el-select v-model="userForm.roles" multiple placeholder="请选择角色（可多选）" style="width: 100%">
            <el-option v-for="r in ROLE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingUser" label="状态">
          <!-- v1.53 P1-24：三态：active/locked/pending -->
          <el-radio-group v-model="userForm.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="LOCKED">锁定</el-radio>
            <el-radio label="PENDING">待激活</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else>
          <el-text size="small" type="info">默认密码为 123456</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitUser">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { systemApi, type User } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'

const ROLE_OPTIONS = [
  { value: 'ADMIN', label: '系统管理员' },
  { value: 'PM', label: '项目经理' },
  { value: 'RE', label: '需求工程师' },
  { value: 'QA_MGR', label: 'QA经理' },
  { value: 'REVIEWER', label: '评审专家' },
  { value: 'RISK_MGR', label: '风险管理' },
  { value: 'COMPLIANCE', label: '合规人员' },
  { value: 'VIEWER', label: '只读用户' },
]

const ROLE_LABELS: Record<string, string> = Object.fromEntries(ROLE_OPTIONS.map(r => [r.value, r.label]))

// v1.53 P1-24：状态三态
const userStatusLabels: Record<string, string> = {
  ACTIVE: '启用',
  INACTIVE: '禁用',
  LOCKED: '已锁定',
  PENDING: '待激活',
}
const userStatusTagType: Record<string, string> = {
  ACTIVE: 'success',
  INACTIVE: 'info',
  LOCKED: 'danger',
  PENDING: 'warning',
}
const getStatusTagType = (s: string) => userStatusTagType[s] || 'info'

const filterDept = ref('')
const filterRole = ref('')
const users = ref<User[]>([])
const loading = ref(false)
const showUserDialog = ref(false)
const submitting = ref(false)
const editingUser = ref<User | null>(null)

// v1.53 P1-24：roles 多选数组
const userForm = ref({
  username: '',
  realName: '',
  email: '',
  phone: '',
  department: '',
  role: 'RE',
  roles: ['RE'] as string[],
  status: 'ACTIVE',
})

// v1.53 P1-24：取用户角色数组（兼容老格式：role 字符串）
const getUserRoles = (row: any): string[] => {
  if (Array.isArray(row.roles) && row.roles.length > 0) return row.roles
  if (row.role) return [row.role]
  return []
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await systemApi.getUsers({
      department: filterDept.value || undefined,
      role: filterRole.value || undefined,
    })
    users.value = res.data.data || []
  } catch {
    ElMessage.error('获取用户列表失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editingUser.value = null
  resetUserForm()
  showUserDialog.value = true
}

const editUser = (row: User) => {
  editingUser.value = row
  userForm.value = {
    ...row,
    // v1.53 P1-24：编辑时把单 role 转多 role
    roles: getUserRoles(row),
  } as any
  showUserDialog.value = true
}

const resetUserForm = () => {
  userForm.value = {
    username: '',
    realName: '',
    email: '',
    phone: '',
    department: '',
    role: 'RE',
    roles: ['RE'],
    status: 'ACTIVE',
  }
}

const submitUser = async () => {
  if (!userForm.value.username || !userForm.value.realName) {
    ElMessage.warning('请填写必填项')
    return
  }
  submitting.value = true
  try {
    // v1.53 P1-24：提交时把 roles 数组与 role 单值同步
    const payload = { ...userForm.value, role: userForm.value.roles[0] || 'RE' }
    if (editingUser.value) {
      await systemApi.updateUser(editingUser.value.id, payload as any)
    } else {
      await systemApi.createUser(payload as any)
    }
    ElMessage.success('操作成功')
    showUserDialog.value = false
    fetchUsers()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

// v1.53 P1-24：解锁 / 激活 / 锁定
const changeStatus = async (row: User, newStatus: 'ACTIVE' | 'LOCKED' | 'PENDING') => {
  try {
    await systemApi.updateUser(row.id, { status: newStatus } as any)
    ElMessage.success(`已${newStatus === 'ACTIVE' ? '激活' : newStatus === 'LOCKED' ? '锁定' : '置为待激活'}`)
    fetchUsers()
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}
const unlockUser = (row: User) => changeStatus(row, 'ACTIVE')
const activateUser = (row: User) => changeStatus(row, 'ACTIVE')
const lockUser = (row: User) => changeStatus(row, 'LOCKED')

const confirmResetPassword = (row: User) => {
  ElMessageBox.confirm(
    `确认将用户 "${row.username}" 的密码重置为 123456？`,
    '重置密码',
    { type: 'warning' }
  ).then(async () => {
    try {
      await systemApi.resetPassword(row.id)
      ElMessage.success('密码已重置为 123456')
    } catch {
      ElMessage.error('重置失败')
    }
  }).catch(() => {})
}

const confirmDelete = (row: User) => {
  ElMessageBox.confirm(
    `确认删除用户 "${row.username}"？该操作不可撤销。`,
    '删除用户',
    { type: 'warning' }
  ).then(async () => {
    try {
      await systemApi.deleteUser(row.id)
      ElMessage.success('已删除')
      fetchUsers()
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const getRoleLabel = (role: string) => ROLE_LABELS[role] || role || '—'
const getStatusLabel = (status: string) => userStatusLabels[status] || status || '—'

onMounted(fetchUsers)
</script>

<style scoped>
.user-manage-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-form { margin-bottom: 16px; }
</style>
