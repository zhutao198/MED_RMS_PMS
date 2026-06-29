<template>
  <div class="report-export-container">
    <el-page-header @back="$router.back()" content="报告导出" class="page-back" />

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card>
          <template #header><span>导出配置</span></template>
          <el-form :model="form" label-width="100px" v-loading="submitting">
            <el-form-item label="报告类型" required>
              <el-select v-model="form.reportType" style="width: 100%">
                <el-option label="需求报告" value="REQUIREMENT" />
                <el-option label="变更报告" value="CHANGE" />
                <el-option label="合规报告" value="COMPLIANCE" />
                <el-option label="风险报告" value="RISK" />
                <el-option label="审计报告" value="AUDIT" />
              </el-select>
            </el-form-item>
            <el-form-item label="项目">
              <el-select v-model="form.projectId" clearable style="width: 100%">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="时间范围" required>
              <el-date-picker
                v-model="dateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item label="导出格式" required>
              <el-radio-group v-model="form.format">
                <el-radio label="PDF">PDF</el-radio>
                <el-radio label="EXCEL">Excel</el-radio>
                <el-radio label="CSV">CSV</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="doExport">开始导出</el-button>
              <el-button @click="reset">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>历史导出</span>
              <el-button size="small" @click="loadHistory">刷新</el-button>
            </div>
          </template>
          <el-table :data="history" border size="small" v-loading="loading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="reportType" label="类型" width="100" />
            <el-table-column prop="format" label="格式" width="80" />
            <el-table-column prop="projectName" label="项目" min-width="120">
              <template #default="{ row }">{{ row.projectName || '全部' }}</template>
            </el-table-column>
            <el-table-column prop="generatedAt" label="导出时间" width="180">
              <template #default="{ row }">{{ formatDate(row.generatedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" type="primary" text @click="download(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="history.length === 0 && !loading" description="暂无导出记录" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { reportApi } from '@/api/report'

const form = ref({ reportType: 'REQUIREMENT', projectId: null as number | null, format: 'PDF' })
const dateRange = ref<[string, string] | null>(null)
const submitting = ref(false)
const loading = ref(false)
const projects = ref<any[]>([])
const history = ref<any[]>([])

const formatDate = (d?: string) => d ? d.replace('T', ' ').substring(0, 19) : '-'

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    projects.value = res.data.data || []
  } catch {
    projects.value = []
  }
}

const loadHistory = async () => {
  loading.value = true
  try {
    const res = await reportApi.list()
    history.value = (res.data.data || []).map((r: any) => ({ ...r, format: r.format || 'PDF' }))
  } catch {
    history.value = []
  } finally {
    loading.value = false
  }
}

const reset = () => {
  form.value = { reportType: 'REQUIREMENT', projectId: null, format: 'PDF' }
  dateRange.value = null
}

const doExport = async () => {
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择时间范围')
    return
  }
  submitting.value = true
  try {
    // WHY: 不同格式后端走不同 endpoint，统一调 /reports/export，参数区分
    const params = {
      reportType: form.value.reportType,
      projectId: form.value.projectId || undefined,
      startDate: dateRange.value[0],
      endDate: dateRange.value[1],
      format: form.value.format,
    }
    const res = await request.get('/reports/export', {
      params, responseType: 'blob',
    })
    const blob = new Blob([res.data], {
      type: form.value.format === 'PDF' ? 'application/pdf' :
            form.value.format === 'EXCEL' ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' :
            'text/csv'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `MedRMS_${form.value.reportType}_${dateRange.value[0]}_${dateRange.value[1]}.${form.value.format.toLowerCase()}`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出完成')
    loadHistory()
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.response?.data?.message || e?.message || '请检查后端导出接口'))
  } finally {
    submitting.value = false
  }
}

const download = async (row: any) => {
  try {
    const res = await reportApi.download(row.id)
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = `MedRMS_report_${row.id}.bin`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (e: any) {
    ElMessage.error('下载失败：' + (e?.response?.data?.message || e?.message))
  }
}

onMounted(() => { fetchProjects(); loadHistory() })
</script>

<style scoped>
.report-export-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
