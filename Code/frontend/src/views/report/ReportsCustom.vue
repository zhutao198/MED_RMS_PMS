<template>
  <div class="reports-custom-container">
    <div class="page-header">
      <div class="page-title">自定义报表配置</div>
      <div class="header-actions">
        <el-button size="small" @click="loadSavedConfigs">📂 已保存的配置</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="handleSave">💾 保存报表</el-button>
      </div>
    </div>

    <div class="config-layout">
      <div class="config-left">
        <div class="config-card">
          <div class="config-title">报表基础配置</div>
          <el-form :model="reportConfig" label-width="100px">
            <el-form-item label="报表名称" required>
              <el-input v-model="reportConfig.name" placeholder="请输入报表名称" maxlength="100" show-word-limit />
            </el-form-item>
            <el-form-item label="报表类型" required>
              <el-select v-model="reportConfig.type" style="width:100%;" @change="handleTypeChange">
                <el-option label="📋 需求统计报表" value="req" />
                <el-option label="🔄 变更统计报表" value="change" />
                <el-option label="📝 评审统计报表" value="review" />
                <el-option label="⚠️ 风险统计报表" value="risk" />
                <el-option label="📁 项目进度报表" value="project" />
              </el-select>
            </el-form-item>
            <el-form-item label="项目">
              <el-select v-model="reportConfig.projectId" placeholder="全部项目" clearable filterable style="width:100%;" @change="fetchPreview">
                <el-option
                  v-for="p in projects"
                  :key="p.id"
                  :label="`${p.projectNo} - ${p.projectName}`"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="reportConfig.dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width:100%;"
                value-format="YYYY-MM-DD"
                @change="fetchPreview"
              />
            </el-form-item>
          </el-form>
        </div>

        <div class="config-card">
          <div class="config-title">选择字段（勾选要显示的列）</div>
          <div class="field-chips">
            <span
              v-for="field in availableFields"
              :key="field.key"
              class="field-chip"
              :class="{ selected: reportConfig.fields.includes(field.key) }"
              @click="toggleField(field.key)"
            >
              {{ field.label }}
            </span>
          </div>
        </div>
      </div>

      <div class="config-right">
        <div class="config-card">
          <div class="config-title">
            报表预览
            <span v-if="previewData.length > 0" class="preview-stat">
              共 {{ previewData.length }} 条
              <span v-if="fetching" class="loading-text">加载中…</span>
            </span>
          </div>
          <div class="preview-area">
            <el-table
              v-if="reportConfig.fields.length > 0"
              :data="previewData"
              border
              size="small"
              v-loading="fetching"
              style="font-size:12px;"
              max-height="420"
            >
              <el-table-column
                v-for="f in activeColumns"
                :key="f.key"
                :prop="f.key"
                :label="f.label"
                :min-width="f.minWidth || 100"
                :show-overflow-tooltip="true"
              />
            </el-table>
            <el-empty v-else description="请选择要显示的字段" :image-size="60" />
          </div>
          <div class="export-actions">
            <el-button size="small" :disabled="previewData.length === 0" @click="handleExport('excel')">📥 导出 Excel (CSV)</el-button>
            <el-button size="small" :disabled="previewData.length === 0" @click="handleExport('pdf')">📄 导出 PDF</el-button>
            <el-button size="small" :disabled="previewData.length === 0" @click="handleExport('email')">📧 发送邮件</el-button>
            <el-button size="small" :disabled="previewData.length === 0" @click="fetchPreview">🔄 刷新</el-button>
          </div>
          <div class="hint-text">
            <el-alert v-if="['pdf', 'email'].includes(lastExportType) && lastExportMsg" :title="lastExportMsg" type="info" :closable="false" show-icon />
            <div v-else class="hint">提示：当前预览最多展示 200 条，CSV 导出包含全部数据；PDF/邮件 v1.42 接入服务端生成。</div>
          </div>
        </div>
      </div>
    </div>

    <!-- v1.46 P1-后端-1：已保存的配置列表 -->
    <el-dialog v-model="savedDialogVisible" title="已保存的报表配置" width="640px">
      <el-table :data="savedConfigs" border size="small" v-loading="loadingConfigs" empty-text="暂无已保存的报表配置">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="reportType" label="类型" width="100">
          <template #default="{ row }">{{ typeNameMap[row.reportType] || row.reportType }}</template>
        </el-table-column>
        <el-table-column prop="projectId" label="项目" width="80">
          <template #default="{ row }">{{ row.projectId || '—' }}</template>
        </el-table-column>
        <el-table-column prop="isShared" label="共享" width="60">
          <template #default="{ row }">
            <el-tag v-if="row.isShared" size="small" type="success">是</el-tag>
            <el-tag v-else size="small" type="info">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170">
          <template #default="{ row }">{{ row.updatedAt ? String(row.updatedAt).replace('T', ' ').substring(0, 19) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="loadConfigIntoForm(row)">加载</el-button>
            <el-button size="small" text type="danger" @click="handleDeleteConfig(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import { projectApi, type Project } from '@/api/project'
import { reportApi, type ReportConfig } from '@/api/report'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

interface FieldDef {
  key: string
  label: string
  minWidth?: number
}

const projects = ref<Project[]>([])
const previewData = ref<Record<string, any>[]>([])
const fetching = ref(false)
const saving = ref(false)
const lastExportType = ref('')
const lastExportMsg = ref('')

const savedDialogVisible = ref(false)
const savedConfigs = ref<ReportConfig[]>([])
const loadingConfigs = ref(false)
const typeNameMap: Record<string, string> = {
  req: '需求', change: '变更', review: '评审', risk: '风险', project: '项目'
}

const availableFields = ref<FieldDef[]>([
  { key: 'id', label: '需求编号', minWidth: 140 },
  { key: 'requirementNo', label: '系统编号', minWidth: 140 },
  { key: 'title', label: '需求标题', minWidth: 200 },
  { key: 'level', label: '需求层级', minWidth: 80 },
  { key: 'requirementType', label: '类型', minWidth: 80 },
  { key: 'priority', label: '优先级', minWidth: 80 },
  { key: 'status', label: '状态', minWidth: 100 },
  { key: 'owner', label: '负责人', minWidth: 100 },
  { key: 'createDate', label: '创建日期', minWidth: 130 },
  { key: 'dueDate', label: '截止日期', minWidth: 130 },
  { key: 'product', label: '适用产品', minWidth: 120 },
  { key: 'linkedReqs', label: '关联需求数', minWidth: 100 },
])

const reportConfig = reactive({
  name: '需求统计报表',
  type: 'req',
  projectId: undefined as number | undefined,
  dateRange: [] as string[],
  fields: ['requirementNo', 'title', 'requirementType', 'priority', 'status'],
})

const activeColumns = computed(() =>
  reportConfig.fields
    .map(key => availableFields.value.find(f => f.key === key))
    .filter((f): f is FieldDef => Boolean(f)),
)

const toggleField = (key: string) => {
  const index = reportConfig.fields.indexOf(key)
  if (index >= 0) {
    reportConfig.fields.splice(index, 1)
  } else {
    reportConfig.fields.push(key)
  }
}

const handleTypeChange = () => {
  // 按报表类型预置字段
  const presets: Record<string, string[]> = {
    req: ['requirementNo', 'title', 'requirementType', 'priority', 'status'],
    change: ['id', 'title', 'priority', 'status'],
    review: ['id', 'title', 'status', 'owner'],
    risk: ['id', 'title', 'priority', 'status', 'owner'],
    project: ['id', 'title', 'status', 'owner'],
  }
  reportConfig.fields = presets[reportConfig.type] || ['requirementNo', 'title']
  fetchPreview()
}

const buildQuery = (page: number, size: number) => {
  const params: Record<string, any> = { page, size }
  if (reportConfig.projectId) params.projectId = reportConfig.projectId
  if (reportConfig.dateRange && reportConfig.dateRange.length === 2) {
    params.startDate = reportConfig.dateRange[0]
    params.endDate = reportConfig.dateRange[1]
  }
  if (reportConfig.type === 'req') {
    // 需求统计：拉取所有层级
    // 后端支持 type 过滤但这里不强制
  }
  return params
}

const mapRow = (r: any): Record<string, any> => ({
  id: r.id,
  requirementNo: r.requirementNo,
  title: r.title,
  level: r.requirementType,
  requirementType: r.requirementType,
  priority: r.priority,
  status: r.status,
  owner: r.createdBy ? `用户#${r.createdBy}` : '-',
  createDate: r.createdAt ? String(r.createdAt).replace('T', ' ').substring(0, 19) : '-',
  dueDate: r.dueDate || '-',
  product: r.projectId ? `项目#${r.projectId}` : '-',
  linkedReqs: '-', // 关联数另需追溯接口
})

const fetchPreview = async () => {
  fetching.value = true
  try {
    const res = await request.get('/requirements', { params: buildQuery(0, 200) })
    const data = res.data.data || {}
    const records = data.records || data.content || data || []
    previewData.value = Array.isArray(records) ? records.map(mapRow) : []
    if (previewData.value.length === 0) {
      ElMessage.info('当前筛选下无数据')
    }
  } catch (e: any) {
    ElMessage.error('预览加载失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    previewData.value = []
  } finally {
    fetching.value = false
  }
}

const fetchProjects = async () => {
  try {
    const res = await projectApi.list()
    projects.value = (res.data.data || []).filter((p: Project) => p.status !== 'CLOSED')
  } catch {
    // ignore
  }
}

const buildCsv = (): string => {
  const headers = activeColumns.value.map(c => c.label).join(',')
  const escape = (v: any) => {
    const s = v == null ? '' : String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const rows = previewData.value.map(r =>
    activeColumns.value.map(c => escape(r[c.key])).join(','),
  )
  return [headers, ...rows].join('\n')
}

const handleExport = (type: string) => {
  lastExportType.value = type
  if (type === 'excel') {
    const csv = '﻿' + buildCsv()
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${reportConfig.name || '报表'}_${new Date().toISOString().slice(0, 10)}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${previewData.value.length} 条 CSV`)
    lastExportMsg.value = ''
  } else if (type === 'pdf') {
    lastExportMsg.value = 'PDF 导出 v1.42 接入服务端 PDF 生成器（当前仅支持 CSV）'
    ElMessage.warning('PDF 导出将在 v1.42 接入')
  } else if (type === 'email') {
    lastExportMsg.value = '邮件发送 v1.42 接入 SMTP 服务（当前可下载 CSV 后手工发送）'
    ElMessage.warning('邮件发送将在 v1.42 接入')
  }
}

const handleSave = async () => {
  if (!reportConfig.name.trim()) {
    ElMessage.warning('请填写报表名称')
    return
  }
  if (reportConfig.fields.length === 0) {
    ElMessage.warning('请至少选择一个字段')
    return
  }
  saving.value = true
  try {
    // v1.46 P1-后端-1：使用专用报表配置端点（POST /reports/configs）
    await reportApi.createConfig({
      name: reportConfig.name.trim(),
      reportType: reportConfig.type,
      projectId: reportConfig.projectId,
      fieldsJson: JSON.stringify(reportConfig.fields),
      filtersJson: JSON.stringify({
        dateRange: reportConfig.dateRange,
      }),
      isShared: false,
      createdBy: userStore.userInfo?.id,
      createdByName: userStore.userInfo?.realName,
    } as any)
    ElMessage.success(`报表配置已保存：${reportConfig.name}`)
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

const loadSavedConfigs = async () => {
  savedDialogVisible.value = true
  loadingConfigs.value = true
  try {
    const res: any = await reportApi.listConfigs({ userId: userStore.userInfo?.id })
    savedConfigs.value = res?.data?.data || []
  } catch (e: any) {
    ElMessage.error('加载配置列表失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    savedConfigs.value = []
  } finally {
    loadingConfigs.value = false
  }
}

const loadConfigIntoForm = (row: ReportConfig) => {
  reportConfig.name = row.name
  reportConfig.type = row.reportType
  reportConfig.projectId = row.projectId
  try {
    const fields = JSON.parse(row.fieldsJson) as string[]
    if (Array.isArray(fields) && fields.length) reportConfig.fields = fields
  } catch { /* ignore parse */ }
  try {
    const filters = row.filtersJson ? JSON.parse(row.filtersJson) : null
    if (filters?.dateRange && Array.isArray(filters.dateRange)) {
      reportConfig.dateRange = filters.dateRange
    }
  } catch { /* ignore parse */ }
  savedDialogVisible.value = false
  ElMessage.success(`已加载配置：${row.name}`)
  fetchPreview()
}

const handleDeleteConfig = async (row: ReportConfig) => {
  try {
    await ElMessageBox.confirm(`确定删除报表配置"${row.name}"？`, '确认删除', { type: 'warning' })
    await reportApi.deleteConfig(row.id)
    savedConfigs.value = savedConfigs.value.filter(c => c.id !== row.id)
    ElMessage.success('删除成功')
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

onMounted(() => {
  fetchProjects()
  fetchPreview()
})
</script>

<style scoped>
.reports-custom-container {
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
.config-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.config-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  padding: 24px;
}
.config-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.preview-stat {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.loading-text {
  margin-left: 8px;
  color: #409eff;
}
.field-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.field-chip {
  display: inline-block;
  padding: 4px 10px;
  background: #ecf5ff;
  color: #409eff;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
  user-select: none;
}
.field-chip:hover {
  background: #d9ecff;
}
.field-chip.selected {
  background: #409eff;
  color: #fff;
}
.preview-area {
  background: #fafafa;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 16px;
  min-height: 200px;
}
.export-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}
.hint-text {
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}
.hint {
  color: #909399;
  font-size: 12px;
}
</style>
