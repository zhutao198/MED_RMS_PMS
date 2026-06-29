<template>
  <div class="projects-list-container">
    <div class="page-header">
      <div class="page-title">项目管理</div>
      <div class="header-actions">
        <el-button @click="handleRefresh">刷新</el-button>
        <el-button type="primary" @click="handleCreate">创建项目</el-button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-icon">📁</div>
        <div class="stat-info">
          <div class="value">{{ stats.total }}</div>
          <div class="label">项目总数</div>
        </div>
      </div>
      <div class="stat-card active">
        <div class="stat-icon">✅</div>
        <div class="stat-info">
          <div class="value">{{ stats.active }}</div>
          <div class="label">进行中</div>
        </div>
      </div>
      <div class="stat-card pending">
        <div class="stat-icon">⏳</div>
        <div class="stat-info">
          <div class="value">{{ stats.pending }}</div>
          <div class="label">计划中</div>
        </div>
      </div>
      <div class="stat-card completed">
        <div class="stat-icon">🎉</div>
        <div class="stat-info">
          <div class="value">{{ stats.completed }}</div>
          <div class="label">已完成</div>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索项目名称/编号" style="width: 220px" clearable
        @keyup.enter="fetchProjects" @clear="fetchProjects">
        <template #prefix>
          <span>🔍</span>
        </template>
      </el-input>
      <el-select v-model="statusFilter" style="width: 130px" placeholder="项目状态" clearable @change="fetchProjects">
        <el-option label="全部" value="" />
        <el-option label="计划中" value="PLANNING" />
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已终止" value="TERMINATED" />
      </el-select>
      <el-select v-model="healthFilter" style="width: 130px" placeholder="健康状态" clearable @change="fetchProjects">
        <el-option label="全部" value="" />
        <el-option label="正常" value="HEALTHY" />
        <el-option label="警告" value="WARNING" />
        <el-option label="极高风险" value="CRITICAL" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="projects-grid" v-loading="loading">
      <div v-for="project in filteredProjects" :key="project.id" class="project-card" @click="handleView(project)">
        <div class="project-header">
          <div class="project-info">
            <div class="project-no">{{ project.projectNo }}</div>
            <div class="project-name">{{ project.projectName }}</div>
          </div>
          <el-tag :type="getStatusType(project.status)" size="small">
            {{ getStatusLabel(project.status) }}
          </el-tag>
        </div>

        <div class="project-meta">
          <span class="meta-item">创建时间：{{ project.startDate || '-' }}</span>
          <span class="meta-item">项目经理：{{ project.managerName || '-' }}</span>
        </div>

        <div class="project-progress">
          <div class="progress-label">
            <span>整体进度</span>
            <span class="progress-value">
              <template v-if="project.progress === null || project.progress === undefined">无数据</template>
              <template v-else>{{ project.progress }}%</template>
            </span>
          </div>
          <el-progress
            v-if="project.progress !== null && project.progress !== undefined"
            :percentage="project.progress"
            :stroke-width="8"
            :status="project.progress >= 80 ? 'success' : (project.progress < 30 ? 'warning' : '')"
          />
          <el-progress v-else :percentage="0" :stroke-width="8" :show-text="false" status="warning" />
        </div>

        <div class="project-stats">
          <div class="stat-item">
            <div class="stat-value">{{ project.startDate || '-' }}</div>
            <div class="stat-label">开始日期</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ project.endDate || '-' }}</div>
            <div class="stat-label">结束日期</div>
          </div>
          <div class="stat-item">
            <div class="stat-value">{{ project.managerName || '-' }}</div>
            <div class="stat-label">负责人</div>
          </div>
        </div>

        <div class="project-actions">
          <el-button size="small" text type="primary" @click.stop="handleView(project)">详情</el-button>
          <el-button size="small" text type="primary" @click.stop="handleGantt(project)">甘特图</el-button>
          <el-button size="small" text type="primary" @click.stop="handleEdit(project)">编辑</el-button>
        </div>
      </div>
      <el-empty v-if="filteredProjects.length === 0 && !loading" description="暂无项目数据" />
    </div>

    <div class="pagination-footer">
      <span class="page-info">共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <el-dialog v-model="showCreateDialog" :title="editingId ? '编辑项目' : '新建项目'" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.projectName" placeholder="如：新一代影像 AI 平台" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="createForm.status" style="width:100%">
            <el-option label="计划中" value="PLANNING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="createForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="createForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { projectApi, type Project } from '@/api/project'
import request from '@/api/request'

const router = useRouter()

const searchKeyword = ref('')
const statusFilter = ref('')
const healthFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const showCreateDialog = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const createForm = ref({ projectName: '', description: '', status: 'PLANNING', startDate: '', endDate: '' })

const stats = ref({
  total: 0,
  active: 0,
  pending: 0,
  completed: 0
})

const allProjects = ref<Project[]>([])

const fetchProjects = async () => {
  loading.value = true
  try {
    const res = await projectApi.list({ status: statusFilter.value || undefined })
    const list = res.data.data || []
    allProjects.value = list
    total.value = list.length
    // 并行拉取每个项目的进度
    const progressResults = await Promise.allSettled(
      list.map((p: any) => request.get(`/projects/${p.id}/progress`).catch(() => null))
    )
    list.forEach((p: any, i: number) => {
      const r = progressResults[i]
      if (r && r.status === 'fulfilled' && r.value?.data?.data) {
        const data = r.value.data.data
        p.progress = (data.progress !== undefined && data.progress !== null) ? data.progress
                   : (data.completionRate !== undefined && data.completionRate !== null) ? data.completionRate
                   : null
      } else {
        p.progress = null
      }
    })
    updateStats()
  } catch (e: any) {
    ElMessage.error('获取项目列表失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const updateStats = () => {
  const list = allProjects.value
  stats.value = {
    total: list.length,
    active: list.filter(p => p.status === 'IN_PROGRESS').length,
    pending: list.filter(p => p.status === 'PLANNING').length,
    completed: list.filter(p => p.status === 'COMPLETED').length
  }
}

const filteredProjects = computed(() => {
  return allProjects.value.filter(project => {
    if (searchKeyword.value && !project.projectNo.includes(searchKeyword.value) && !project.projectName.includes(searchKeyword.value)) {
      return false
    }
    if (statusFilter.value && project.status !== statusFilter.value) {
      return false
    }
    return true
  })
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PLANNING: 'info',
    IN_PROGRESS: 'primary',
    COMPLETED: 'success',
    TERMINATED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PLANNING: '计划中',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    TERMINATED: '已终止'
  }
  return map[status] || status
}

const handleRefresh = () => {
  fetchProjects()
  ElMessage.success('刷新成功')
}

const handleCreate = () => {
  showCreateDialog.value = true
  createForm.value = { projectName: '', description: '', status: 'PLANNING', startDate: '', endDate: '' }
}

const handleView = (project: Project) => {
  router.push(`/projects/${project.id}`)
}

const handleGantt = (project: Project) => {
  router.push(`/projects/${project.id}/gantt`)
}

const handleEdit = (project: Project) => {
  showCreateDialog.value = true
  editingId.value = project.id
  createForm.value = {
    projectName: project.projectName,
    description: project.description || '',
    status: project.status,
    startDate: project.startDate || '',
    endDate: project.endDate || ''
  }
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
  fetchProjects()
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
  fetchProjects()
}

const submitCreate = async () => {
  if (!createForm.value.projectName) {
    ElMessage.warning('请输入项目名称')
    return
  }
  submitting.value = true
  try {
    if (editingId.value) {
      await projectApi.update(editingId.value, createForm.value as any)
      ElMessage.success('已更新')
    } else {
      await projectApi.create(createForm.value as any)
      ElMessage.success('已创建')
    }
    showCreateDialog.value = false
    editingId.value = null
    await fetchProjects()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

const resetFilters = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  healthFilter.value = ''
  fetchProjects()
}

onMounted(fetchProjects)
</script>

<style scoped>
.projects-list-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-card.total .stat-icon {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
}

.stat-card.active .stat-icon {
  background: linear-gradient(135deg, #11998e, #38ef7d);
  color: #fff;
}

.stat-card.pending .stat-icon {
  background: linear-gradient(135deg, #e6a23c, #f5a623);
  color: #fff;
}

.stat-card.completed .stat-icon {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
}

.stat-info .value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
}

.stat-info .label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.filter-bar {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.project-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.2s;
}

.project-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.project-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.project-info {
  flex: 1;
}

.project-no {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.project-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.project-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #606266;
  margin-bottom: 16px;
}

.project-progress {
  margin-bottom: 16px;
}

.progress-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
}

.progress-value {
  font-weight: 600;
  color: #409eff;
}

.project-stats {
  display: flex;
  gap: 20px;
  padding: 12px 0;
  border-top: 1px solid #ebeef5;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 12px;
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.project-actions {
  display: flex;
  gap: 8px;
}

.pagination-footer {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
}

.page-info {
  font-size: 13px;
  color: #909399;
}
</style>