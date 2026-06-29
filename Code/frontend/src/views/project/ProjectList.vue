<template>
  <div class="project-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>项目管理</span>
          <el-button type="primary" @click="showCreateDialog = true">新建项目</el-button>
        </div>
      </template>

      <el-table :data="projects" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="projectNo" label="项目编号" width="140" />
        <el-table-column prop="projectName" label="项目名称" min-width="200" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="120" />
        <el-table-column prop="endDate" label="结束日期" width="120" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="primary" @click="editProject(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog v-model="showCreateDialog" :title="editingProject ? '编辑项目' : '新建项目'" width="500px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目名称">
          <el-input v-model="form.projectName" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="计划中" value="PLANNING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="项目详情" width="600px">
      <el-descriptions :column="2" border v-if="currentProject">
        <el-descriptions-item label="项目编号">{{ currentProject.projectNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentProject.status)">{{ currentProject.status }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="项目名称" :span="2">{{ currentProject.projectName }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentProject.description }}</el-descriptions-item>
        <el-descriptions-item label="开始日期">{{ currentProject.startDate }}</el-descriptions-item>
        <el-descriptions-item label="结束日期">{{ currentProject.endDate }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { projectApi, type Project } from '@/api/project'
import { ElMessage } from 'element-plus'

const projects = ref<Project[]>([])
const loading = ref(false)
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const submitting = ref(false)
const editingProject = ref<Project | null>(null)
const currentProject = ref<Project | null>(null)

const form = ref({
  projectName: '',
  description: '',
  status: 'PLANNING',
  startDate: '',
  endDate: '',
  managerId: 1,
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await projectApi.list()
    projects.value = res.data.data || []
  } catch {
    ElMessage.error('获取项目列表失败')
  } finally {
    loading.value = false
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { PLANNING: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', TERMINATED: 'danger' }
  return map[status] || 'info'
}

const projectStatusLabels: Record<string, string> = {
  PLANNING: '计划中',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
  TERMINATED: '已终止',
}

const getStatusLabel = (status: string) => projectStatusLabels[status] || status

const viewDetail = (row: Project) => {
  currentProject.value = row
  showDetailDialog.value = true
}

const editProject = (row: Project) => {
  editingProject.value = row
  form.value = { ...row }
  showCreateDialog.value = true
}

const submitForm = async () => {
  if (!form.value.projectName) {
    ElMessage.warning('请填写项目名称')
    return
  }
  submitting.value = true
  try {
    if (editingProject.value) {
      await projectApi.update(editingProject.value.id, form.value)
    } else {
      await projectApi.create(form.value as any)
    }
    ElMessage.success('操作成功')
    showCreateDialog.value = false
    fetchData()
  } catch {
    ElMessage.error('操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.project-list-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>