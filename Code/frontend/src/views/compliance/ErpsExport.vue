<template>
  <div class="erps-container">
    <div class="page-title">
      <h2>📤 NMPA eRPS 报告导出（FR-1.12）</h2>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      国家药品监督管理局医疗器械注册电子申报（eRPS）结构化报告。
      包含产品信息、软件描述、安全等级、风险管理、需求追溯、变更控制、问题报告、IEC 62304 等 8 大章节。
    </el-alert>

    <el-card>
      <el-form :inline="true" @submit.prevent>
        <el-form-item label="选择项目">
          <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 360px;" @change="loadPreview">
            <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadPreview" :disabled="!projectId">预览</el-button>
          <el-button type="success" :disabled="!projectId" @click="download">下载 JSON</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="data" style="margin-top: 16px;" v-loading="loading">
      <template #header>
        <div class="card-title">
          eRPS 包结构（checksum: <el-tag effect="plain">{{ data.checksum }}</el-tag>）
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="Schema">{{ data.schema }}</el-descriptions-item>
        <el-descriptions-item label="生成时间">{{ data.generatedAt }}</el-descriptions-item>
        <el-descriptions-item label="项目编号">{{ data.productInfo?.projectNo }}</el-descriptions-item>
        <el-descriptions-item label="项目名称">{{ data.productInfo?.projectName }}</el-descriptions-item>
        <el-descriptions-item label="软件名称">{{ data.softwareDescription?.softwareName }}</el-descriptions-item>
        <el-descriptions-item label="软件版本">{{ data.softwareDescription?.versionNo }}</el-descriptions-item>
        <el-descriptions-item label="软件安全等级">
          <el-tag :type="safetyTag(data.softwareDescription?.safetyLevel)" effect="dark">
            Level {{ data.softwareDescription?.safetyLevel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="需求数">{{ data.softwareDescription?.requirementCount }}</el-descriptions-item>
        <el-descriptions-item label="风险总数">{{ data.riskManagementSummary?.total }}</el-descriptions-item>
        <el-descriptions-item label="高 / 中 / 低">
          <el-tag type="danger" size="small">{{ data.riskManagementSummary?.high || 0 }}</el-tag>
          <el-tag type="warning" size="small" style="margin-left:4px">{{ data.riskManagementSummary?.medium || 0 }}</el-tag>
          <el-tag type="success" size="small" style="margin-left:4px">{{ data.riskManagementSummary?.low || 0 }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="变更请求数">{{ data.changeControl?.total }}</el-descriptions-item>
        <el-descriptions-item label="SOUP 数量">{{ data.softwareDescription?.soupList?.length || 0 }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <el-tabs>
        <el-tab-pane label="软件描述 (CH5.2)">
          <el-table :data="data.softwareDescription?.soupList || []" border size="small">
            <el-table-column prop="componentName" label="组件" />
            <el-table-column prop="version" label="版本" width="100" />
            <el-table-column prop="supplier" label="供应商" />
            <el-table-column label="风险等级" width="100">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="需求分布">
          <pre class="json-pre">{{ JSON.stringify(data.requirementTrace, null, 2) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="变更控制">
          <pre class="json-pre">{{ JSON.stringify(data.changeControl, null, 2) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="问题报告">
          <pre class="json-pre">{{ JSON.stringify(data.problemReports, null, 2) }}</pre>
        </el-tab-pane>
        <el-tab-pane label="IEC 62304">
          <pre class="json-pre">{{ JSON.stringify(data.iec62304Summary, null, 2) }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const projectList = ref<any[]>([])
const projectId = ref<number | null>(null)
const data = ref<any>(null)
const loading = ref(false)

const safetyTag = (l: string) => ({ A: 'success', B: 'warning', C: 'danger' } as any)[l] || 'info'
const riskTag = (l: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' } as any)[l] || 'info'

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
  } catch (e) {}
}

const loadPreview = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await request.get(`/compliance/erps/export/${projectId.value}`)
    data.value = res.data?.data
    ElMessage.success('eRPS 包已生成')
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const download = async () => {
  if (!projectId.value) return
  try {
    const res = await request.get(`/compliance/erps/download/${projectId.value}`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `eRPS-${projectId.value}-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('下载已开始')
  } catch (e: any) {
    ElMessage.error('下载失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(fetchProjects)
</script>

<style scoped>
.erps-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.card-title { font-size: 15px; font-weight: 600; }
.json-pre { background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 12px; max-height: 400px; overflow: auto; }
</style>
