<template>
  <div class="problem-report-container">
    <div class="page-header">
      <div class="page-title">问题报告管理</div>
      <div class="header-actions">
        <el-button @click="goReports">📊 合规报告</el-button>
        <el-button type="primary" @click="goCreate">+ 新建问题报告</el-button>
      </div>
    </div>

    <div class="status-flow">
      <div
        v-for="(status, index) in statusFlow"
        :key="status"
        class="status-node"
        :class="{
          active: currentStatus === status,
          completed: passedStatuses.includes(status)
        }"
      >
        {{ status }}
      </div>
      <template v-for="(status, index) in statusFlow" :key="index + 'arrow'">
        <span v-if="index < statusFlow.length - 1" class="status-arrow">→</span>
      </template>
    </div>

    <div class="filter-bar">
      <el-input v-model="filter.keyword" placeholder="搜索编号/标题" style="width: 200px;" clearable @keyup.enter="fetchReports">
        <template #prefix><span>🔍</span></template>
      </el-input>
      <el-select v-model="filter.severity" placeholder="严重度" style="width: 130px;" clearable @change="fetchReports">
        <el-option label="灾难性" value="CRITICAL" />
        <el-option label="严重" value="MAJOR" />
        <el-option label="轻度" value="MINOR" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" style="width: 140px;" clearable @change="fetchReports">
        <el-option label="待处理" value="Open" />
        <el-option label="分析中" value="Analyzing" />
        <el-option label="纠正中" value="Correcting" />
        <el-option label="验证中" value="Verifying" />
        <el-option label="已关闭" value="Closed" />
      </el-select>
      <el-select v-model="filter.source" placeholder="来源" style="width: 140px;" clearable @change="handleSourceChange">
        <el-option label="内部" value="internal" />
        <el-option label="外部" value="external" />
        <el-option label="法规" value="regulatory" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="pr-table">
      <el-table :data="filteredReports" stripe @row-click="viewDetail" style="cursor:pointer;" v-loading="loading">
        <el-table-column label="编号" width="150">
          <template #default="{ row }">
            {{ row.reportCode || row.prCode }}
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="getSeverityType(row.severity)" size="small">{{ getSeverityLabel(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="100" />
        <el-table-column label="报告人" width="90">
          <template #default="{ row }">
            {{ row.reporterName || row.ownerName }}
          </template>
        </el-table-column>
        <el-table-column label="发现日期" width="120">
          <template #default="{ row }">
            {{ row.discoveryDate || row.reportedAt }}
          </template>
        </el-table-column>
        <!-- P1-32: 列表行内加「查看/编辑」按钮，明确跳独立路由 -->
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="router.push(`/compliance/problem-report/${row.id}`)">查看</el-button>
            <el-button size="small" text type="warning" @click.stop="router.push(`/compliance/problem-report/${row.id}`)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- P1-32: 列表页移除弹框创建/查看/纠正，统一走独立路由 /create /:id -->
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()
const statusFlow = ['Open', 'Analyzing', 'Correcting', 'Verifying', 'Closed']
// R94 修复：原模板 line 16-19 用了 currentStatus 和 passedStatuses 但未定义 → undefined.includes 抛错 → 页面卡死
const currentStatus = ref('')  // 当前问题报告状态（用于高亮）
const passedStatuses = ref<string[]>([])  // 已流转过的状态
// P1-32: 列表页仅展示，弹框相关状态全部移除
const loading = ref(false)
const filter = ref({ keyword: '', severity: '', status: '', source: '' })

interface ProblemReportItem {
  id: number
  reportCode?: string
  prCode?: string
  title: string
  severity: string
  status: string
  sourceType?: string
  source?: string
  reporterName?: string
  ownerName?: string
  discoveryDate?: string
  reportedAt?: string
  description: string
  affectedItems?: string
  relatedReq?: string
  resolution?: string
  resolvedAt?: string
  corrections?: any[]
}

const reports = ref<ProblemReportItem[]>([])

const fetchReports = async () => {
  loading.value = true
  try {
    const params: any = { page: 0, size: 50 }
    if (filter.value.severity) params.severity = filter.value.severity
    if (filter.value.status) params.status = filter.value.status
    const res = await request.get('/compliance/problem-reports', { params })
    const data = res.data?.data
    if (Array.isArray(data)) {
      reports.value = data
    } else if (data?.records) {
      reports.value = data.records
    } else {
      reports.value = []
    }
    for (const r of reports.value) {
      if (!r.corrections) r.corrections = []
    }
  } catch (e) {
    ElMessage.error('获取问题报告失败')
  } finally {
    loading.value = false
  }
}

const filteredReports = computed(() => {
  return reports.value.filter(pr => {
    if (filter.value.keyword) {
      const kw = filter.value.keyword.toLowerCase()
      const code = (pr.reportCode || pr.prCode || '').toLowerCase()
      if (!code.includes(kw) && !pr.title.toLowerCase().includes(kw)) return false
    }
    if (filter.value.source && (pr.sourceType || pr.source) !== filter.value.source) return false
    return true
  })
})

const getSeverityType = (severity: string) => {
  const map: Record<string, string> = { CRITICAL: 'danger', MAJOR: 'warning', MINOR: 'info',
    critical: 'danger', major: 'warning', minor: 'info' }
  return map[severity] || 'info'
}

// P1-29: 严重度中文化
const SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: '灾难性', MAJOR: '严重', MINOR: '轻度',
  critical: '灾难性', major: '严重', minor: '轻度',
  HIGH: '高', MEDIUM: '中', LOW: '低'
}
const getSeverityLabel = (s: string) => SEVERITY_LABELS[s] || s || '-'

const problemStatusLabels: Record<string, string> = {
  Open: '开放',
  Analyzing: '分析中',
  Correcting: '纠正中',
  Verifying: '验证中',
  Resolved: '已解决',
  Closed: '已关闭',
  Pending: '待处理',
}

const getStatusLabel = (status: string) => problemStatusLabels[status] || status

const viewDetail = (row: any) => {
  // P1-32: 跳转到独立详情路由
  if (row?.id) router.push(`/compliance/problem-report/${row.id}`)
}

const goCreate = () => {
  // P1-32: 跳转到独立创建路由
  router.push('/compliance/problem-report/create')
}

const goReports = () => {
  router.push('/compliance/reports')
}

const viewReq = async (val?: string) => {
  if (!val) {
    ElMessage.warning('未关联需求')
    return
  }
  const trimmed = String(val).trim()
  if (/^\d+$/.test(trimmed)) {
    router.push(`/requirements/${trimmed}`)
    return
  }
  try {
    const res = await request.get('/requirements', { params: { page: 0, size: 50, keyword: trimmed } })
    const data = res.data?.data
    const list: any[] = Array.isArray(data) ? data : (data?.records || [])
    const hit = list.find((r: any) => r.requirementNo === trimmed || r.code === trimmed)
    if (hit) {
      router.push(`/requirements/${hit.id}`)
    } else if (list.length) {
      router.push(`/requirements/${list[0].id}`)
      ElMessage.info(`未找到编号 ${trimmed} 的精确匹配，已跳转到首个相似结果`)
    } else {
      ElMessage.warning(`未找到需求 ${trimmed}，请到需求列表查询`)
    }
  } catch {
    ElMessage.info('查看需求: ' + trimmed)
  }
}

const handleSourceChange = () => fetchReports()

const resetFilters = () => {
  filter.value = { keyword: '', severity: '', status: '', source: '' }
  fetchReports()
}

onMounted(() => {
  fetchReports()
})
</script>

<style scoped>
.problem-report-container {
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

.status-flow {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 24px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  overflow-x: auto;
}

.status-node {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  white-space: nowrap;
  border: 2px solid #e4e7ed;
  color: #909399;
}

.status-node.active {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
  font-weight: 600;
}

.status-node.completed {
  border-color: #67c23a;
  background: #f0f9eb;
  color: #67c23a;
}

.status-arrow {
  color: #c0c4cc;
  font-size: 18px;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.pr-table {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.pr-detail {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  border-left: 3px solid #409eff;
  padding-left: 10px;
  margin-bottom: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.info-item .label {
  color: #909399;
  margin-bottom: 4px;
  font-size: 12px;
}

.info-item .value {
  color: #303133;
  font-weight: 500;
  font-size: 13px;
}

.info-item .value.link {
  color: #409eff;
  cursor: pointer;
}

.info-item .value.link:hover {
  text-decoration: underline;
}

.description-text {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.correction-timeline {
  margin-top: 16px;
}

.correction-item {
  display: flex;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.type-badge {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  height: fit-content;
}

.type-badge.corrective {
  background: #fef0f0;
  color: #f56c6c;
}

.type-badge.preventive {
  background: #f0f9eb;
  color: #67c23a;
}

.correction-desc {
  font-size: 14px;
  color: #303133;
}

.correction-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>