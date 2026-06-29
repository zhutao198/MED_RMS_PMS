<template>
  <div class="dhf-container">
    <div class="page-title">
      <h2>DHF 证据包中心（FR-1.4）</h2>
      <div class="header-actions">
        <el-select v-model="filterProject" placeholder="选择项目" style="width: 280px;" @change="loadManifest">
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button type="primary" @click="loadManifest">刷新</el-button>
        <el-button type="success" :loading="downloading" @click="downloadPackage">📥 下载完整包</el-button>
      </div>
    </div>

    <el-alert v-if="verdict" :type="verdict.status === 'PASS' ? 'success' : (verdict.status === 'WARN' ? 'warning' : 'error')" :closable="false" show-icon style="margin-bottom: 16px;">
      <template #title>
        <strong>合规判定：{{ verdict.status }}</strong> — {{ verdict.reason }}
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col :xs="24" :md="8">
        <el-card shadow="hover" header="包清单">
          <template v-if="manifest">
            <div v-for="s in manifest.sections" :key="s.key" class="manifest-item">
              <span class="mi-title">{{ s.title }}</span>
              <el-tag size="small" :type="s.scope === 'PROJECT' ? 'primary' : 'info'">
                {{ s.scope === 'PROJECT' ? '项目' : '全局' }}{{ s.limit ? ` · ${s.limit}` : '' }}
              </el-tag>
            </div>
          </template>
          <el-empty v-else description="请选择项目" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :xs="24" :md="16">
        <el-card shadow="hover" header="证据包详情">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="项目信息" name="project">
              <el-descriptions v-if="pkg && pkg.project" :column="2" border size="small">
                <el-descriptions-item label="项目编号">{{ pkg.project.projectNo }}</el-descriptions-item>
                <el-descriptions-item label="项目名称">{{ pkg.project.projectName }}</el-descriptions-item>
                <el-descriptions-item label="状态">{{ pkg.project.status }}</el-descriptions-item>
                <el-descriptions-item label="合规模板">{{ pkg.project.templateCode || '-' }}</el-descriptions-item>
                <el-descriptions-item label="描述" :span="2">{{ pkg.project.description }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="覆盖率" name="coverage">
              <div v-if="pkg && pkg.coverageStats">
                <el-statistic title="总体覆盖率" :value="pkg.coverageStats.overall || 0" suffix="%" />
                <el-divider />
                <div v-for="t in ['URS','PRS','SRS','DRS']" :key="t" class="cov-line">
                  <span>{{ t }}：</span>
                  <el-progress :percentage="pkg.coverageStats[t]?.coverageRate || 0" :stroke-width="14" />
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="IEC 62304 状态" name="iec">
              <el-descriptions v-if="pkg && pkg.iec62304Stats" :column="2" border size="small">
                <el-descriptions-item label="完全合规">{{ pkg.iec62304Stats.compliant }}</el-descriptions-item>
                <el-descriptions-item label="部分合规">{{ pkg.iec62304Stats.partial }}</el-descriptions-item>
                <el-descriptions-item label="不合规">{{ pkg.iec62304Stats.nonCompliant }}</el-descriptions-item>
                <el-descriptions-item label="不适用">{{ pkg.iec62304Stats.notApplicable }}</el-descriptions-item>
                <el-descriptions-item label="待评估">{{ pkg.iec62304Stats.pending }}</el-descriptions-item>
                <el-descriptions-item label="合规率">{{ pkg.iec62304Stats.complianceRate }}%</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane :label="`变更历史（${(pkg?.changeHistory || []).length}）`" name="changes">
              <el-table :data="pkg?.changeHistory || []" border size="small" max-height="380">
                <el-table-column prop="changeNo" label="变更单号" width="160" />
                <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
                <el-table-column prop="changeType" label="类型" width="100" />
                <el-table-column prop="status" label="状态" width="120" />
                <el-table-column prop="urgency" label="紧急度" width="80" />
                <el-table-column prop="requesterName" label="申请人" width="100" />
                <el-table-column prop="createdAt" label="创建时间" width="170" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane :label="`审计日志（${(pkg?.auditLogs || []).length}）`" name="audits">
              <el-table :data="pkg?.auditLogs || []" border size="small" max-height="380">
                <el-table-column prop="eventType" label="事件类型" width="120" />
                <el-table-column prop="entityType" label="实体类型" width="120" />
                <el-table-column prop="entityId" label="实体 ID" width="80" />
                <el-table-column prop="operatorName" label="操作人" width="100" />
                <el-table-column prop="createdAt" label="时间" width="170" />
                <el-table-column prop="operation" label="操作" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>

            <el-tab-pane :label="`电子签名（${(pkg?.signatureLogs || []).length}）`" name="signs">
              <el-table :data="pkg?.signatureLogs || []" border size="small" max-height="380">
                <el-table-column prop="signerName" label="签署人" width="100" />
                <el-table-column prop="signatureType" label="类型" width="100" />
                <el-table-column prop="documentType" label="文档类型" width="120" />
                <el-table-column prop="documentId" label="文档 ID" width="80" />
                <el-table-column prop="signedAt" label="签署时间" width="170" />
                <el-table-column prop="signatureHash" label="哈希" min-width="200" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const projectList = ref<{ id: number; projectNo: string; projectName: string }[]>([])
const filterProject = ref<number | null>(null)
const manifest = ref<any>(null)
const pkg = ref<any>(null)
const activeTab = ref('project')
const downloading = ref(false)
const verdict = ref<any>(null)

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const data = res.data?.data
    projectList.value = Array.isArray(data) ? data : (data?.records || [])
    if (projectList.value.length > 0 && filterProject.value == null) {
      filterProject.value = projectList.value[0].id
      await loadManifest()
    }
  } catch (e) {
    ElMessage.error('加载项目失败')
  }
}

const loadManifest = async () => {
  if (!filterProject.value) return
  try {
    const [m, g] = await Promise.all([
      request.get(`/compliance/dhf/manifest/${filterProject.value}`),
      request.post(`/compliance/dhf/generate/${filterProject.value}`),
    ])
    manifest.value = m.data?.data
    pkg.value = g.data?.data
    verdict.value = pkg.value?.verdict
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e.message))
  }
}

const downloadPackage = async () => {
  if (!filterProject.value) return
  downloading.value = true
  try {
    const res = await request.get(`/compliance/dhf/download/${filterProject.value}`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `DHF-${filterProject.value}-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('DHF 证据包已下载')
  } catch (e: any) {
    ElMessage.error('下载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    downloading.value = false
  }
}

watch(filterProject, loadManifest)
onMounted(fetchProjects)
</script>

<style scoped>
.dhf-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}
.page-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
.manifest-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.mi-title { font-size: 14px; color: #303133; }
.cov-line { display: flex; align-items: center; gap: 12px; margin: 12px 0; }
.cov-line span { width: 50px; font-weight: 600; }
</style>
