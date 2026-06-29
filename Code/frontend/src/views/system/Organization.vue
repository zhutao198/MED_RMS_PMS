<template>
  <div class="organization-container">
    <div class="page-header">
      <div class="page-title">组织架构</div>
      <div class="page-tip">R99 支持任意层级树形 + 部门 CRUD + 拖拽排序</div>
    </div>
    <el-card v-loading="loading">
      <el-row :gutter="16">
        <el-col :span="10">
          <div class="tree-toolbar">
            <el-input v-model="filter" placeholder="搜索部门" clearable size="small" style="width: 180px;" />
            <el-button size="small" type="primary" @click="openCreateDialog(0)">
              <el-icon><Plus /></el-icon>新增顶级部门
            </el-button>
          </div>
          <el-tree
            ref="treeRef"
            class="dept-tree"
            :data="treeData"
            :props="defaultProps"
            node-key="id"
            default-expand-all
            :filter-node-method="filterNode"
            :allow-drop="allowDrop"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <span class="org-node">
                <span class="org-name">{{ node.label }}</span>
                <span class="org-meta">({{ data.userCount || 0 }}人)</span>
                <span class="org-actions">
                  <el-button size="small" link type="primary" @click.stop="openCreateDialog(data.id)">
                    新增子部门
                  </el-button>
                  <el-button size="small" link type="warning" @click.stop="openEditDialog(data)">
                    编辑
                  </el-button>
                  <el-button size="small" link type="danger" @click.stop="confirmDelete(data)">
                    删除
                  </el-button>
                </span>
              </span>
            </template>
          </el-tree>
          <el-empty v-if="treeData.length === 0 && !loading" description="暂无部门数据，请先创建" />
        </el-col>
        <el-col :span="14">
          <div class="detail-wrap">
            <h3 class="detail-title">{{ selectedNode?.label || '请选择左侧节点' }}</h3>
            <div v-if="selectedNode" class="detail-meta">
              <div>用户数：{{ selectedNode.userCount || 0 }}</div>
              <div>部门编码：{{ (selectedNode as any).code || '-' }}</div>
            </div>
            <el-table v-if="selectedMembers.length > 0" :data="selectedMembers" border size="small" style="margin-top: 12px;">
              <el-table-column prop="username" label="用户名" width="120" />
              <el-table-column prop="realName" label="姓名" width="120" />
              <el-table-column prop="role" label="角色" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                    {{ row.status === 'ACTIVE' ? '在岗' : '离岗' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 部门编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="resetDialog">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门编码" prop="code">
          <el-input v-model="form.code" placeholder="RND / QA / RA 等" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="父部门" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="{ label: 'label', value: 'id', children: 'children' }"
            placeholder="顶级部门（不选）"
            check-strictly
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { systemApi, type OrgNode } from '@/api/system'

const loading = ref(false)
const treeData = ref<OrgNode[]>([])
const filter = ref('')
const treeRef = ref()
const selectedNode = ref<OrgNode | null>(null)
const selectedMembers = ref<any[]>([])
const defaultProps = { children: 'children', label: 'label' }

watch(filter, (v) => { treeRef.value?.filter(v) })
const filterNode = (v: string, data: any) => {
  if (!v) return true
  return (data.label || '').toLowerCase().includes(v.toLowerCase())
}

const fetchTree = async () => {
  loading.value = true
  try {
    const res = await systemApi.getDepartmentTree()
    treeData.value = res.data.data || []
  } catch (e: any) {
    ElMessage.error('加载部门树失败：' + (e?.response?.data?.message || e?.message || ''))
    treeData.value = []
  } finally {
    loading.value = false
  }
}

const handleNodeClick = async (node: OrgNode) => {
  selectedNode.value = node
  try {
    // 复用 users 列表接口
    const res = await systemApi.getUsers({ department: node.label })
    selectedMembers.value = res.data.data || []
  } catch {
    selectedMembers.value = []
  }
}

const allowDrop = () => false // R99 暂不允许拖拽改 parent（防循环引用）

// ==================== 弹窗 ====================
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogTitle = computed(() => dialogMode.value === 'create' ? '新增部门' : '编辑部门')
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  parentId: 0 as number,
  name: '',
  code: '',
  sortOrder: 0
})
const rules = {
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入部门编码', trigger: 'blur' }]
}

const parentTreeOptions = computed(() => {
  // 顶级虚拟节点可作为 parentId=0
  return [{ id: 0, label: '顶级（Med-RMS 整体组织）', children: treeData.value }]
})

const resetDialog = () => {
  form.id = undefined
  form.parentId = 0
  form.name = ''
  form.code = ''
  form.sortOrder = 0
  formRef.value?.clearValidate()
}

const openCreateDialog = (parentId: number) => {
  resetDialog()
  dialogMode.value = 'create'
  form.parentId = parentId
  dialogVisible.value = true
}

const openEditDialog = (node: any) => {
  resetDialog()
  dialogMode.value = 'edit'
  form.id = node.id
  form.parentId = node.id === 0 ? 0 : (node.parentId || 0)
  form.name = node.label || ''
  form.code = node.code || ''
  form.sortOrder = node.sortOrder || 0
  dialogVisible.value = true
}

const submitDialog = async () => {
  await formRef.value?.validate().catch(() => {})
  try {
    if (dialogMode.value === 'create') {
      await systemApi.createDepartment({ ...form })
      ElMessage.success('创建成功')
    } else {
      await systemApi.updateDepartment(form.id!, { ...form })
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    await fetchTree()
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e?.response?.data?.message || e?.message || ''))
  }
}

const confirmDelete = async (node: any) => {
  try {
    await ElMessageBox.confirm(
      `确认删除部门"${node.label}"？该操作不可撤销（若有子部门或用户会拒绝）。`,
      '删除确认',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await systemApi.deleteDepartment(node.id)
    ElMessage.success('删除成功')
    await fetchTree()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message || ''))
  }
}

onMounted(fetchTree)
</script>

<style scoped>
.organization-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 16px; display: flex; align-items: center; gap: 12px; }
.page-title { font-size: 20px; font-weight: 600; }
.page-tip { font-size: 12px; color: #909399; }
.tree-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.dept-tree { background: #fafbfc; padding: 8px; border-radius: 4px; max-height: 600px; overflow-y: auto; }
.org-node { display: flex; align-items: center; gap: 8px; width: 100%; }
.org-name { font-size: 14px; font-weight: 500; }
.org-meta { font-size: 12px; color: #909399; }
.org-actions { margin-left: auto; display: none; gap: 4px; }
.org-node:hover .org-actions { display: flex; }
.detail-wrap { padding: 8px; }
.detail-title { margin: 0 0 8px; font-size: 16px; }
.detail-meta { font-size: 13px; color: #606266; line-height: 24px; }
</style>