<template>
  <div class="role-edit-container">
    <el-page-header @back="$router.back()" :content="`编辑角色 #${roleId}`" class="page-back" />

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card v-loading="loading">
          <template #header><span>基础信息</span></template>
          <el-form :model="form" label-width="100px">
            <el-form-item label="角色名称">
              <el-input v-model="form.roleName" />
            </el-form-item>
            <el-form-item label="角色编码">
              <el-input v-model="form.roleCode" disabled />
              <span class="form-tip">编码不可修改</span>
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.description" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="saveBasic">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card v-loading="permLoading">
          <template #header>
            <div class="card-header">
              <span>权限矩阵（行=模块，列=操作）</span>
              <div class="header-actions">
                <el-button size="small" @click="selectAll">全选</el-button>
                <el-button size="small" @click="clearAll">清空</el-button>
                <el-button size="small" type="primary" :loading="permSaving" @click="savePerms">保存权限</el-button>
              </div>
            </div>
          </template>

          <el-input v-model="permFilter" placeholder="搜索权限码/名称" clearable size="small" style="width: 240px; margin-bottom: 12px;" />

          <!-- v1.53 P1-23：独立页权限矩阵，行=模块，列=查/增/改/删/批 -->
          <el-table :data="pagedGroups" border stripe>
            <el-table-column label="模块" min-width="160" prop="module" />
            <el-table-column label="查 (R)" align="center" width="80">
              <template #default="{ row }">
                <el-checkbox :model-value="hasOp(row, 'R')" @change="toggleOp(row, 'R', $event)" />
              </template>
            </el-table-column>
            <el-table-column label="增 (C)" align="center" width="80">
              <template #default="{ row }">
                <el-checkbox :model-value="hasOp(row, 'C')" @change="toggleOp(row, 'C', $event)" />
              </template>
            </el-table-column>
            <el-table-column label="改 (U)" align="center" width="80">
              <template #default="{ row }">
                <el-checkbox :model-value="hasOp(row, 'U')" @change="toggleOp(row, 'U', $event)" />
              </template>
            </el-table-column>
            <el-table-column label="删 (D)" align="center" width="80">
              <template #default="{ row }">
                <el-checkbox :model-value="hasOp(row, 'D')" @change="toggleOp(row, 'D', $event)" />
              </template>
            </el-table-column>
            <el-table-column label="批 (A)" align="center" width="80">
              <template #default="{ row }">
                <el-checkbox :model-value="hasOp(row, 'A')" @change="toggleOp(row, 'A', $event)" />
              </template>
            </el-table-column>
            <el-table-column label="权限码" min-width="220">
              <template #default="{ row }">
                <span v-for="(p, i) in row.matchedPerms" :key="p.permCode" class="perm-chip">
                  <el-tag size="small" type="info">{{ p.permCode }}</el-tag>
                  <span v-if="i < row.matchedPerms.length - 1"> </span>
                </span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="pagedGroups.length === 0 && !permLoading" description="无可分配权限" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { systemApi, type Permission } from '@/api/system'

const route = useRoute()
const roleId = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const permLoading = ref(false)
const permSaving = ref(false)
const form = ref<any>({ roleName: '', roleCode: '', description: '' })
const allPermissions = ref<Permission[]>([])
const selectedPermCodes = ref<string[]>([])
const permFilter = ref('')

// v1.53 P1-23：操作符 → 权限码后缀映射
// 约定：模块查询权限名以 :list/:view 结尾（查），:create（增），:update（改），:delete（删），:approve（批）
const OP_SUFFIX: Record<string, string[]> = {
  R: ['list', 'view', 'query', 'export', 'get'],
  C: ['create', 'add', 'import'],
  U: ['update', 'edit', 'modify', 'status', 'switch'],
  D: ['delete', 'remove'],
  A: ['approve', 'audit', 'sign', 'pass', 'reject'],
}

interface ModuleRow {
  module: string
  matchedPerms: Permission[]
  ops: Record<string, boolean>
}

const filteredPermissions = computed(() => {
  const kw = permFilter.value.trim().toLowerCase()
  if (!kw) return allPermissions.value
  return allPermissions.value.filter(p =>
    p.permCode.toLowerCase().includes(kw) || p.permName.toLowerCase().includes(kw))
})

const groupedRows = computed<ModuleRow[]>(() => {
  const groups: Record<string, Permission[]> = {}
  for (const p of filteredPermissions.value) {
    const modKey = p.permCode.split(':')[0] || 'other'
    if (!groups[modKey]) groups[modKey] = []
    groups[modKey].push(p)
  }
  return Object.keys(groups).sort().map(mod => {
    const perms = groups[mod]
    const ops: Record<string, boolean> = { R: false, C: false, U: false, D: false, A: false }
    for (const op of Object.keys(OP_SUFFIX)) {
      // WHY: 命中规则 = 模块下任一权限码后缀匹配该操作
      ops[op] = perms.some(p => {
        const code = p.permCode.toLowerCase()
        return OP_SUFFIX[op].some(suf => code.endsWith(`:${suf}`))
      })
    }
    return { module: mod, matchedPerms: perms, ops }
  })
})

const pagedGroups = computed(() => groupedRows.value)

const hasOp = (row: ModuleRow, op: string) => !!row.ops[op]
const toggleOp = (row: ModuleRow, op: string, checked: boolean | string | number) => {
  const targets = row.matchedPerms.filter(p => {
    const code = p.permCode.toLowerCase()
    return OP_SUFFIX[op].some(suf => code.endsWith(`:${suf}`))
  })
  const set = new Set(selectedPermCodes.value)
  if (checked) {
    targets.forEach(p => set.add(p.permCode))
  } else {
    targets.forEach(p => set.delete(p.permCode))
  }
  selectedPermCodes.value = Array.from(set)
}

const selectAll = () => { selectedPermCodes.value = allPermissions.value.map(p => p.permCode) }
const clearAll = () => { selectedPermCodes.value = [] }

const loadRole = async () => {
  loading.value = true
  try {
    const list = await systemApi.getRoles()
    const role = (list.data.data || []).find((r: any) => r.id === roleId)
    if (role) form.value = { roleName: role.roleName, roleCode: role.roleCode, description: role.description || '' }
    else ElMessage.warning('角色不存在')
  } catch (e: any) {
    ElMessage.error('加载角色失败')
  } finally {
    loading.value = false
  }
}

const loadPerms = async () => {
  permLoading.value = true
  try {
    const [permsRes, assignedRes] = await Promise.all([
      systemApi.getAllPermissions(),
      systemApi.getRolePermissions(roleId),
    ])
    allPermissions.value = permsRes.data.data || []
    selectedPermCodes.value = assignedRes.data.data || []
  } catch (e: any) {
    ElMessage.error('加载权限失败')
  } finally {
    permLoading.value = false
  }
}

const saveBasic = async () => {
  saving.value = true
  try {
    await systemApi.updateRole(roleId, { roleName: form.value.roleName, description: form.value.description })
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    saving.value = false
  }
}

const savePerms = async () => {
  permSaving.value = true
  try {
    await systemApi.updateRolePermissions(roleId, selectedPermCodes.value)
    ElMessage.success(`已保存 ${selectedPermCodes.value.length} 项权限`)
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    permSaving.value = false
  }
}

onMounted(() => { loadRole(); loadPerms() })
</script>

<style scoped>
.role-edit-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; }
.form-tip { margin-left: 8px; font-size: 12px; color: #909399; }
.perm-chip { margin-right: 4px; }
</style>
