<template>
  <div class="decompose-list">
    <div class="page-header">
      <h2>🔨 需求拆解</h2>
      <el-button type="primary" @click="$router.push('/requirements')">+ 新建需求</el-button>
    </div>

    <el-card>
      <div class="filter-row">
        <el-select v-model="filters.projectId" placeholder="所属项目" clearable style="width: 160px;" @change="loadRequirements">
          <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-select v-model="filters.type" placeholder="需求层级" clearable style="width: 120px;" @change="loadRequirements">
          <el-option label="URS" value="URS" />
          <el-option label="PRS" value="PRS" />
          <el-option label="SRS" value="SRS" />
          <el-option label="DRS" value="DRS" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索需求标题" style="width: 200px;" clearable @keyup.enter="loadRequirements" />
        <el-button @click="loadRequirements">搜索</el-button>
      </div>

      <el-table :data="requirements" style="width: 100%; margin-top: 20px;" v-loading="loading">
        <el-table-column prop="requirementNo" label="编号" width="150" />
        <el-table-column prop="requirementType" label="层级" width="80">
          <template #default="{ row }">
            <el-tag :type="getTypeColor(row.requirementType)">{{ row.requirementType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="getPriorityColor(row.priority)">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectId" label="项目" width="120">
          <template #default="{ row }">
            {{ getProjectName(row.projectId) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goDecompose(row)">拆解</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadRequirements"
        @current-change="loadRequirements"
        style="margin-top: 20px; justify-content: flex-end;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { requirementApi } from '@/api/requirement'
import { projectApi } from '@/api/project'
import type { Requirement } from '@/api/requirement'
import type { Project } from '@/api/project'

const router = useRouter()

const loading = ref(false)
const requirements = ref<Requirement[]>([])
const projectList = ref<Project[]>([])
const filters = reactive({
  projectId: null as number | null,
  type: '',
  keyword: ''
})
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const loadRequirements = async () => {
  loading.value = true
  try {
    const res = await requirementApi.list({
      projectId: filters.projectId || undefined,
      page: pagination.page - 1,
      size: pagination.size,
      type: filters.type || undefined,
      status: 'Approved'
    })
    requirements.value = res.data?.data?.records || []
    pagination.total = res.data?.data?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

const loadProjects = async () => {
  try {
    const res = await projectApi.list()
    projectList.value = res.data?.data || []
  } catch (e) {
    console.error(e)
  }
}

const goDecompose = (req: Requirement) => {
  router.push(`/requirements/${req.id}/decompose`)
}

const getTypeColor = (type: string) => {
  return { URS: 'primary', PRS: 'success', SRS: 'warning', DRS: 'danger' }[type] || 'info'
}

const getPriorityColor = (priority: string) => {
  return { MUST: 'danger', SHOULD: 'warning', COULD: 'info', WONT: 'info' }[priority] || 'info'
}

const getProjectName = (projectId: number | undefined) => {
  if (!projectId) return '-'
  const project = projectList.value.find(p => p.id === projectId)
  return project ? project.projectName : `项目${projectId}`
}

onMounted(() => {
  loadProjects()
  loadRequirements()
})
</script>

<style scoped>
.decompose-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
}

.filter-row {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>