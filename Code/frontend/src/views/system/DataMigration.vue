<template>
  <div class="dm-container">
    <div class="page-title">
      <h2>📥 数据迁移工具（FR-1.13）</h2>
      <div class="header-actions">
        <el-button @click="loadJobs">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      支持从 JSON / CSV 批量导入需求。CSV 表头：<b>requirementNo,title,requirementType,priority,projectId,status</b>。
      重复的 requirementNo 会被自动跳过（幂等导入）。
    </el-alert>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header><div class="card-title">📋 粘贴 JSON 导入</div></template>
          <el-input v-model="jsonText" type="textarea" :rows="10" placeholder='[{"requirementNo":"URS-001","title":"示例需求","requirementType":"URS","priority":"MUST","projectId":1,"status":"Draft"}]' />
          <div style="margin-top:12px; display:flex; gap:8px;">
            <el-button type="primary" :loading="loading" @click="submitJson">导入 JSON</el-button>
            <el-button @click="loadSample">填入示例</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><div class="card-title">📁 上传文件导入</div></template>
          <el-upload
            :http-request="uploadReq"
            :show-file-list="false"
            accept=".json,.csv"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">将 JSON / CSV 文件拖入或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 .json / .csv 文件</div>
            </template>
          </el-upload>
          <el-button style="margin-top:12px" @click="downloadSampleCsv">下载 CSV 模板</el-button>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:20px">
      <template #header><div class="card-title">📜 迁移任务历史</div></template>
      <el-table :data="jobs" v-loading="loading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="jobName" label="任务名" />
        <el-table-column prop="jobType" label="类型" width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="dark" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="总数" width="80" align="center" />
        <el-table-column prop="successCount" label="成功" width="80" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.successCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.failureCount > 0" type="danger" size="small">{{ row.failureCount }}</el-tag>
            <span v-else>0</span>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="160" />
        <el-table-column prop="finishedAt" label="结束时间" width="160" />
        <el-table-column label="错误日志" width="200">
          <template #default="{ row }">
            <el-button v-if="row.errorLog" type="warning" size="small" link @click="showErrorLog(row)">查看</el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="errorVisible" title="错误日志" width="640px">
      <pre class="log-pre">{{ currentError }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import request from '@/api/request'

const jsonText = ref('')
const jobs = ref<any[]>([])
const loading = ref(false)
const errorVisible = ref(false)
const currentError = ref('')

const statusTag = (s: string) => ({ SUCCESS: 'success', FAILED: 'danger', PARTIAL: 'warning', RUNNING: '' } as any)[s] || 'info'

const loadSample = () => {
  jsonText.value = JSON.stringify([
    { requirementNo: 'URS-MIG-001', title: '示例用户需求 1', requirementType: 'URS', priority: 'MUST', projectId: 1, status: 'Draft', description: '从迁移工具导入的示例' },
    { requirementNo: 'SRS-MIG-002', title: '示例软件需求 2', requirementType: 'SRS', priority: 'SHOULD', projectId: 1, status: 'Draft' }
  ], null, 2)
}

const submitJson = async () => {
  if (!jsonText.value.trim()) {
    ElMessage.warning('请输入 JSON 内容')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/admin/migration/import/requirements/json', {
      sourceName: 'manual-json-' + Date.now(),
      content: jsonText.value,
      operatorId: 1
    })
    const job = res.data?.data
    ElMessage.success(`导入完成：成功 ${job?.successCount}，失败 ${job?.failureCount}`)
    await loadJobs()
  } catch (e: any) {
    ElMessage.error('导入失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const uploadReq = async (opts: any) => {
  const file: File = opts.file
  const form = new FormData()
  form.append('file', file)
  form.append('sourceName', file.name)
  form.append('operatorId', '1')
  loading.value = true
  try {
    const isCsv = file.name.toLowerCase().endsWith('.csv')
    const url = isCsv ? '/admin/migration/import/requirements/upload-csv' : '/admin/migration/import/requirements/upload-json'
    const res = await request.post(url, form, { headers: { 'Content-Type': 'multipart/form-data' } })
    const job = res.data?.data
    ElMessage.success(`上传完成：成功 ${job?.successCount}，失败 ${job?.failureCount}`)
    await loadJobs()
  } catch (e: any) {
    ElMessage.error('上传失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const downloadSampleCsv = () => {
  const csv = 'requirementNo,title,requirementType,priority,projectId,status\n' +
              'URS-MIG-100,示例用户需求 A,URS,MUST,1,Draft\n' +
              'SRS-MIG-101,示例软件需求 B,SRS,SHOULD,1,Draft\n'
  const blob = new Blob([csv], { type: 'text/csv' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'requirement-template.csv'
  a.click()
  URL.revokeObjectURL(url)
}

const loadJobs = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/migration/jobs')
    jobs.value = res.data?.data || []
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const showErrorLog = (row: any) => {
  currentError.value = row.errorLog || ''
  errorVisible.value = true
}

onMounted(loadJobs)
</script>

<style scoped>
.dm-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.card-title { font-size: 15px; font-weight: 600; }
.log-pre { background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 12px; max-height: 400px; overflow: auto; white-space: pre-wrap; word-break: break-all; }
</style>
