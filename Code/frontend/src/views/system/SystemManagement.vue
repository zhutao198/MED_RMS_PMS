<template>
  <div class="system-container">
    <div class="page-header">
      <div class="page-title">系统管理</div>
    </div>

    <el-tabs v-model="activeTab" class="system-tabs">
      <el-tab-pane label="用户管理" name="users">
        <div class="tab-content">
          <div class="content-header">
            <el-input v-model="userSearch" placeholder="搜索用户名/姓名" style="width: 220px" clearable @input="filterUsers">
              <template #prefix><span>🔍</span></template>
            </el-input>
            <el-button type="primary" @click="openAddUser">添加用户</el-button>
          </div>

          <el-table :data="pagedUsers" border style="width: 100%" v-loading="userLoading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="realName" label="姓名" width="100" />
            <el-table-column prop="email" label="邮箱" width="180">
              <template #default="{ row }">{{ row.email || '-' }}</template>
            </el-table-column>
            <el-table-column prop="phone" label="手机" width="130">
              <template #default="{ row }">{{ row.phone || '-' }}</template>
            </el-table-column>
            <el-table-column prop="department" label="部门" width="100">
              <template #default="{ row }">{{ row.department || '-' }}</template>
            </el-table-column>
            <el-table-column prop="role" label="角色" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getRoleLabel(row.role) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastLoginAt" label="最后登录" width="160">
              <template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="handleEditUser(row)">编辑</el-button>
                <el-button size="small" text type="warning" @click="handleResetPwd(row)">重置密码</el-button>
                <el-button size="small" text type="danger" :disabled="row.id === 1" @click="handleDeleteUser(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="userPage"
            v-model:page-size="userPageSize"
            :total="filteredUsers.length"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            style="margin-top: 16px; justify-content: flex-end;"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="角色权限" name="roles">
        <div class="tab-content">
          <div class="content-header">
            <el-button type="primary" @click="openAddRole">添加角色</el-button>
          </div>

          <el-table :data="roles" border style="width: 100%" v-loading="roleLoading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="roleName" label="角色名称" width="150" />
            <el-table-column prop="roleCode" label="角色编码" width="150" />
            <el-table-column prop="description" label="描述" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                  {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" align="center">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="handleEditRole(row)">编辑</el-button>
                <el-button size="small" text type="warning" @click="openPermissionDialog(row)">权限配置</el-button>
                <el-button size="small" text type="danger" @click="handleDeleteRole(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="组织架构" name="organizations">
        <div class="tab-content">
          <div class="org-tree">
            <el-tree :data="orgTreeData" :props="defaultProps" default-expand-all>
              <template #default="{ node, data }">
                <span class="org-node">
                  <span class="org-name">{{ node.label }}</span>
                  <span class="org-count">({{ data.userCount || 0 }}人)</span>
                </span>
              </template>
            </el-tree>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="数据字典" name="dicts">
        <div class="tab-content">
          <div class="content-header">
            <el-select v-model="selectedDictType" placeholder="请选择字典类型" style="width: 200px" @change="fetchDicts">
              <el-option v-for="t in dictTypes" :key="t" :label="t" :value="t" />
            </el-select>
            <el-button type="primary" @click="openAddDict">添加字典</el-button>
          </div>

          <el-table :data="dicts" border style="width: 100%" v-loading="dictLoading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="dictType" label="字典类型" width="150" />
            <el-table-column prop="dictCode" label="编码" width="120" />
            <el-table-column prop="dictName" label="名称" min-width="200" />
            <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
            <el-table-column label="操作" width="160" align="center">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="handleEditDict(row)">编辑</el-button>
                <el-button size="small" text type="danger" @click="handleDeleteDict(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="系统配置" name="configs">
        <div class="tab-content">
          <div class="config-list">
            <div v-for="config in configs" :key="config.id" class="config-item">
              <div class="config-info">
                <div class="config-name">{{ config.configName }}</div>
                <div class="config-key">配置键：{{ config.configKey }}</div>
                <div class="config-value">配置值：{{ config.configValue }}</div>
                <div v-if="config.description" class="config-desc">说明：{{ config.description }}</div>
              </div>
              <div class="config-actions">
                <el-button size="small" text type="primary" @click="openEditConfig(config)">编辑</el-button>
              </div>
            </div>
            <el-empty v-if="configs.length === 0 && !configLoading" description="暂无系统配置" />
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 用户编辑对话框 -->
    <el-dialog v-model="userDialogVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="500px">
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
        <el-form-item label="手机">
          <el-input v-model="userForm.phone" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="userForm.department" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role">
            <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="userForm.status">
            <el-radio label="ACTIVE">启用</el-radio>
            <el-radio label="INACTIVE">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSubmitting" @click="submitUser">提交</el-button>
      </template>
    </el-dialog>

    <!-- 角色编辑对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="editingRole ? '编辑角色' : '新增角色'" width="500px">
      <el-form :model="roleForm" label-width="80px">
        <el-form-item label="角色名称">
          <el-input v-model="roleForm.roleName" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="roleForm.roleCode" :disabled="!!editingRole" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="roleForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSubmitting" @click="submitRole">提交</el-button>
      </template>
    </el-dialog>

    <!-- 字典编辑对话框 -->
    <el-dialog v-model="dictDialogVisible" :title="editingDict ? '编辑字典' : '新增字典'" width="500px">
      <el-form :model="dictForm" label-width="80px">
        <el-form-item label="字典类型">
          <el-input v-model="dictForm.dictType" :disabled="!!editingDict" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="dictForm.dictCode" :disabled="!!editingDict" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="dictForm.dictName" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dictForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="dictSubmitting" @click="submitDict">提交</el-button>
      </template>
    </el-dialog>

    <!-- v1.46 P1-后端-2：角色权限矩阵对话框 -->
    <el-dialog v-model="permDialogVisible" :title="`权限配置 - ${permRoleName}`" width="780px" top="6vh">
      <div v-loading="permLoading" class="perm-dialog-body">
        <div class="perm-toolbar">
          <el-input v-model="permFilter" placeholder="搜索权限码或名称" clearable size="small" style="width: 240px;" />
          <div class="perm-stat">已选 {{ selectedPermCodes.length }} / {{ allPermissions.length }}</div>
          <div class="perm-actions">
            <el-button size="small" @click="selectAllPerms">全选</el-button>
            <el-button size="small" @click="clearAllPerms">清空</el-button>
          </div>
        </div>
        <div v-for="(group, modKey) in groupedPermissions" :key="modKey" class="perm-group">
          <div class="perm-group-header">
            <el-checkbox
              :model-value="isModuleAllSelected(group)"
              :indeterminate="isModuleIndeterminate(group)"
              @change="toggleModule(group, $event)"
            >
              {{ modKey }}（{{ group.length }} 项）
            </el-checkbox>
          </div>
          <div class="perm-group-body">
            <el-checkbox
              v-for="p in group"
              :key="p.permCode"
              :model-value="selectedPermCodes.includes(p.permCode)"
              @change="togglePerm(p.permCode, $event)"
            >
              <span class="perm-name">{{ p.permName }}</span>
              <span class="perm-code">{{ p.permCode }}</span>
              <el-tag size="small" :type="p.permType === 'MENU' ? 'primary' : p.permType === 'BUTTON' ? 'success' : 'info'">
                {{ p.permType }}
              </el-tag>
            </el-checkbox>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="submitRolePermissions">保存</el-button>
      </template>
    </el-dialog>

    <!-- 系统配置编辑对话框 -->
    <el-dialog v-model="configDialogVisible" :title="editingConfig ? '编辑系统配置' : '新增系统配置'" width="500px">
      <el-form :model="configForm" label-width="80px">
        <el-form-item label="配置名">
          <el-input v-model="configForm.configName" :disabled="!!editingConfig" />
        </el-form-item>
        <el-form-item label="配置键">
          <el-input v-model="configForm.configKey" :disabled="!!editingConfig" />
        </el-form-item>
        <el-form-item label="配置值">
          <el-input v-model="configForm.configValue" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="configForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="configSubmitting" @click="submitConfig">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi, type User, type Role, type DictItem, type SystemConfig, type OrgNode, type Permission } from '@/api/system'

const activeTab = ref('users')
const userSearch = ref('')
const userPage = ref(1)
const userPageSize = ref(10)

const users = ref<User[]>([])
const userLoading = ref(false)
const userSubmitting = ref(false)
const userDialogVisible = ref(false)
const editingUser = ref<User | null>(null)
const userForm = ref({
  username: '',
  realName: '',
  email: '',
  phone: '',
  department: '',
  role: 'RE',
  status: 'ACTIVE',
})

const roles = ref<Role[]>([])
const roleLoading = ref(false)
const roleSubmitting = ref(false)
const roleDialogVisible = ref(false)
const editingRole = ref<Role | null>(null)
const roleForm = ref({ roleName: '', roleCode: '', description: '' })

const orgTreeData = ref<OrgNode[]>([])
const defaultProps = { children: 'children', label: 'label' }

const dicts = ref<DictItem[]>([])
const dictTypes = ref<string[]>([])
const selectedDictType = ref('')
const dictLoading = ref(false)
const dictSubmitting = ref(false)
const dictDialogVisible = ref(false)
const editingDict = ref<DictItem | null>(null)
const dictForm = ref({ dictType: '', dictCode: '', dictName: '', sortOrder: 1 })

const configs = ref<SystemConfig[]>([])
const configLoading = ref(false)
const configSubmitting = ref(false)
const configDialogVisible = ref(false)
const editingConfig = ref<SystemConfig | null>(null)
const configForm = ref({ configName: '', configKey: '', configValue: '', description: '' })

const ROLE_LABELS: Record<string, string> = {
  ADMIN: '系统管理员',
  PM: '项目经理',
  RE: '需求工程师',
  QA_MGR: 'QA经理',
  REVIEWER: '评审专家',
  RISK_MGR: '风险管理',
  COMPLIANCE: '合规人员',
  VIEWER: '只读用户',
}
const roleOptions = [
  { value: 'ADMIN', label: '系统管理员' },
  { value: 'PM', label: '项目经理' },
  { value: 'RE', label: '需求工程师' },
  { value: 'QA_MGR', label: 'QA经理' },
  { value: 'REVIEWER', label: '评审专家' },
  { value: 'RISK_MGR', label: '风险管理' },
  { value: 'COMPLIANCE', label: '合规人员' },
  { value: 'VIEWER', label: '只读用户' },
]
const getRoleLabel = (role: string) => ROLE_LABELS[role] || role || '-'

const filteredUsers = computed(() => {
  if (!userSearch.value) return users.value
  const s = userSearch.value.toLowerCase()
  return users.value.filter(u =>
    (u.username || '').toLowerCase().includes(s) ||
    (u.realName || '').toLowerCase().includes(s)
  )
})
const pagedUsers = computed(() => {
  const start = (userPage.value - 1) * userPageSize.value
  return filteredUsers.value.slice(start, start + userPageSize.value)
})

const filterUsers = () => { userPage.value = 1 }

const formatDate = (d: string) => d ? String(d).replace('T', ' ').substring(0, 19) : '-'

// v1.42 BUG #53 修复：用户管理接入真实后端
const fetchUsers = async () => {
  userLoading.value = true
  try {
    const res = await systemApi.getUsers()
    users.value = res.data.data || []
  } catch {
    ElMessage.error('获取用户列表失败')
  } finally {
    userLoading.value = false
  }
}

const openAddUser = () => {
  editingUser.value = null
  userForm.value = { username: '', realName: '', email: '', phone: '', department: '', role: 'RE', status: 'ACTIVE' }
  userDialogVisible.value = true
}

const handleEditUser = (row: User) => {
  editingUser.value = row
  userForm.value = {
    username: row.username,
    realName: row.realName,
    email: row.email || '',
    phone: row.phone || '',
    department: row.department || '',
    role: row.role || 'RE',
    status: row.status || 'ACTIVE',
  }
  userDialogVisible.value = true
}

const submitUser = async () => {
  if (!userForm.value.username || !userForm.value.realName) {
    ElMessage.warning('请填写用户名和姓名')
    return
  }
  userSubmitting.value = true
  try {
    if (editingUser.value) {
      await systemApi.updateUser(editingUser.value.id, userForm.value)
      ElMessage.success('用户更新成功')
    } else {
      await systemApi.createUser(userForm.value as any)
      ElMessage.success('用户创建成功')
    }
    userDialogVisible.value = false
    fetchUsers()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '操作失败'
    ElMessage.error(msg)
  } finally {
    userSubmitting.value = false
  }
}

const handleResetPwd = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定重置用户 ${row.username} 的密码为 123456 ？`, '确认重置', { type: 'warning' })
    await systemApi.resetPassword(row.id)
    ElMessage.success('密码已重置为 123456')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('重置失败：' + (e?.response?.data?.message || e?.message))
  }
}

const handleDeleteUser = async (row: User) => {
  try {
    await ElMessageBox.confirm(`确定删除用户 ${row.username} ？该操作不可恢复`, '确认删除', { type: 'warning' })
    await systemApi.deleteUser(row.id)
    ElMessage.success('删除成功')
    fetchUsers()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message))
  }
}

// 角色管理
const fetchRoles = async () => {
  roleLoading.value = true
  try {
    const res = await systemApi.getRoles()
    roles.value = res.data.data || []
  } catch {
    ElMessage.error('获取角色列表失败')
  } finally {
    roleLoading.value = false
  }
}

const openAddRole = () => {
  editingRole.value = null
  roleForm.value = { roleName: '', roleCode: '', description: '' }
  roleDialogVisible.value = true
}

const handleEditRole = (row: Role) => {
  editingRole.value = row
  roleForm.value = { roleName: row.roleName, roleCode: row.roleCode, description: row.description || '' }
  roleDialogVisible.value = true
}

const submitRole = async () => {
  if (!roleForm.value.roleName || !roleForm.value.roleCode) {
    ElMessage.warning('请填写角色名称和编码')
    return
  }
  roleSubmitting.value = true
  try {
    if (editingRole.value) {
      await systemApi.updateRole(editingRole.value.id, roleForm.value)
    } else {
      await systemApi.createRole(roleForm.value as any)
    }
    ElMessage.success('操作成功')
    roleDialogVisible.value = false
    fetchRoles()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '操作失败'
    ElMessage.error(msg)
  } finally {
    roleSubmitting.value = false
  }
}

// v1.46 P1-后端-2：角色权限矩阵
const permDialogVisible = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const permRoleId = ref<number | null>(null)
const permRoleName = ref('')
const permFilter = ref('')
const allPermissions = ref<Permission[]>([])
const selectedPermCodes = ref<string[]>([])

const filteredPermissions = computed(() => {
  const kw = permFilter.value.trim().toLowerCase()
  if (!kw) return allPermissions.value
  return allPermissions.value.filter(p =>
    p.permCode.toLowerCase().includes(kw) || p.permName.toLowerCase().includes(kw),
  )
})

const groupedPermissions = computed(() => {
  // 按权限码冒号前缀分组（sys / req / chg / esign 等）
  const groups: Record<string, Permission[]> = {}
  for (const p of filteredPermissions.value) {
    const moduleKey = p.permCode.split(':')[0] || 'other'
    if (!groups[moduleKey]) groups[moduleKey] = []
    groups[moduleKey].push(p)
  }
  return groups
})

const isModuleAllSelected = (group: Permission[]) =>
  group.length > 0 && group.every(p => selectedPermCodes.value.includes(p.permCode))

const isModuleIndeterminate = (group: Permission[]) => {
  const chosen = group.filter(p => selectedPermCodes.value.includes(p.permCode)).length
  return chosen > 0 && chosen < group.length
}

const toggleModule = (group: Permission[], checked: boolean | string | number) => {
  const codes = group.map(p => p.permCode)
  if (checked) {
    const set = new Set([...selectedPermCodes.value, ...codes])
    selectedPermCodes.value = Array.from(set)
  } else {
    selectedPermCodes.value = selectedPermCodes.value.filter(c => !codes.includes(c))
  }
}

const togglePerm = (code: string, checked: boolean | string | number) => {
  if (checked) {
    if (!selectedPermCodes.value.includes(code)) selectedPermCodes.value.push(code)
  } else {
    selectedPermCodes.value = selectedPermCodes.value.filter(c => c !== code)
  }
}

const selectAllPerms = () => {
  selectedPermCodes.value = allPermissions.value.map(p => p.permCode)
}

const clearAllPerms = () => {
  selectedPermCodes.value = []
}

const openPermissionDialog = async (row: Role) => {
  permRoleId.value = row.id
  permRoleName.value = row.roleName
  permFilter.value = ''
  permDialogVisible.value = true
  permLoading.value = true
  try {
    const [permsRes, assignedRes] = await Promise.all([
      systemApi.getAllPermissions(),
      systemApi.getRolePermissions(row.id),
    ])
    allPermissions.value = permsRes.data.data || []
    selectedPermCodes.value = assignedRes.data.data || []
  } catch (e: any) {
    ElMessage.error('加载权限失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    permLoading.value = false
  }
}

const submitRolePermissions = async () => {
  if (permRoleId.value == null) return
  permSaving.value = true
  try {
    await systemApi.updateRolePermissions(permRoleId.value, selectedPermCodes.value)
    ElMessage.success(`${permRoleName.value} 权限已更新（${selectedPermCodes.value.length} 项）`)
    permDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    permSaving.value = false
  }
}

const handleDeleteRole = async (row: Role) => {
  try {
    await ElMessageBox.confirm(`确定删除角色 ${row.roleName} ？`, '确认删除', { type: 'warning' })
    await systemApi.deleteRole(row.id)
    ElMessage.success('删除成功')
    fetchRoles()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message))
  }
}

// 组织架构
const fetchOrgTree = async () => {
  try {
    const res = await systemApi.getOrgTree()
    orgTreeData.value = res.data.data || []
  } catch {
    orgTreeData.value = []
  }
}

// 字典管理
const fetchDictTypes = async () => {
  try {
    const res = await systemApi.getAllDicts()
    const all = res.data.data || []
    const types = Array.from(new Set(all.map((d: any) => d.dictType))).sort()
    dictTypes.value = types
    if (types.length > 0 && !selectedDictType.value) {
      selectedDictType.value = types[0]
      fetchDicts()
    }
  } catch (e: any) {
    ElMessage.error('获取字典类型失败：' + (e?.response?.data?.message || e?.message))
  }
}

const fetchDicts = async () => {
  if (!selectedDictType.value) return
  dictLoading.value = true
  try {
    const res = await systemApi.getDicts(selectedDictType.value)
    dicts.value = res.data.data || []
  } catch {
    ElMessage.error('获取字典失败')
  } finally {
    dictLoading.value = false
  }
}

const openAddDict = () => {
  editingDict.value = null
  dictForm.value = { dictType: selectedDictType.value, dictCode: '', dictName: '', sortOrder: 1 }
  dictDialogVisible.value = true
}

const handleEditDict = (row: DictItem) => {
  editingDict.value = row
  dictForm.value = {
    dictType: row.dictType,
    dictCode: row.dictCode,
    dictName: row.dictName,
    sortOrder: row.sortOrder || 1,
  }
  dictDialogVisible.value = true
}

const submitDict = async () => {
  if (!dictForm.value.dictType || !dictForm.value.dictCode || !dictForm.value.dictName) {
    ElMessage.warning('请填写完整字典信息')
    return
  }
  dictSubmitting.value = true
  try {
    if (editingDict.value) {
      await systemApi.updateDict(editingDict.value.id, dictForm.value)
    } else {
      await systemApi.createDict(dictForm.value as any)
    }
    ElMessage.success('操作成功')
    dictDialogVisible.value = false
    fetchDicts()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '操作失败'
    ElMessage.error(msg)
  } finally {
    dictSubmitting.value = false
  }
}

const handleDeleteDict = async (row: DictItem) => {
  try {
    await ElMessageBox.confirm(`确定删除字典项 ${row.dictName} ？`, '确认删除', { type: 'warning' })
    await systemApi.deleteDict(row.id)
    ElMessage.success('删除成功')
    fetchDicts()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message))
  }
}

// 系统配置
const fetchConfigs = async () => {
  configLoading.value = true
  try {
    const res = await systemApi.getConfigs()
    configs.value = res.data.data || []
  } catch {
    ElMessage.error('获取系统配置失败')
  } finally {
    configLoading.value = false
  }
}

const openEditConfig = (row: SystemConfig) => {
  editingConfig.value = row
  configForm.value = {
    configName: row.configName,
    configKey: row.configKey,
    configValue: row.configValue || '',
    description: row.description || '',
  }
  configDialogVisible.value = true
}

const submitConfig = async () => {
  if (!configForm.value.configName || !configForm.value.configKey) {
    ElMessage.warning('请填写配置名和键')
    return
  }
  configSubmitting.value = true
  try {
    await systemApi.updateConfig(editingConfig.value!.id, configForm.value)
    ElMessage.success('配置更新成功')
    configDialogVisible.value = false
    fetchConfigs()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '操作失败'
    ElMessage.error(msg)
  } finally {
    configSubmitting.value = false
  }
}

onMounted(() => {
  fetchUsers()
  fetchRoles()
  fetchOrgTree()
  fetchDictTypes()
  fetchConfigs()
})
</script>

<style scoped>
.system-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.system-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.tab-content {
  padding: 16px 0;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 12px;
}

.org-tree {
  padding: 16px;
}

.org-node {
  display: flex;
  align-items: center;
  gap: 8px;
}

.org-name {
  font-size: 14px;
}

.org-count {
  font-size: 12px;
  color: #909399;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.config-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.config-key,
.config-value,
.config-desc {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
}

/* v1.46 P1-后端-2：角色权限矩阵对话框 */
.perm-dialog-body {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 8px;
}
.perm-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
  position: sticky;
  top: 0;
  background: #fff;
  z-index: 1;
}
.perm-stat {
  font-size: 12px;
  color: #909399;
}
.perm-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.perm-group {
  margin-bottom: 16px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.perm-group-header {
  background: #f5f7fa;
  padding: 8px 12px;
  font-weight: 600;
  font-size: 13px;
}
.perm-group-body {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px 16px;
  padding: 10px 12px;
}
.perm-group-body :deep(.el-checkbox) {
  margin-right: 0;
}
.perm-name {
  margin-right: 8px;
  color: #303133;
}
.perm-code {
  font-family: monospace;
  color: #909399;
  font-size: 12px;
  margin-right: 8px;
}
</style>
