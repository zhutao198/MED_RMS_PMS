<template>
  <div class="report-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>报表中心</span>
          <div class="header-actions">
            <el-button @click="goExport">📤统一导出</el-button>
            <el-button type="primary" @click="showGenerateDialog = true">生成报表</el-button>
          </div>
        </div>
      </template>

      <!-- v1.53 P1-26：4 个 tab — 标准报表 / 经营分析 / 审计报告 / 自定义报告 -->
      <el-tabs v-model="activeTab" class="report-tabs">
        <el-tab-pane label="标准报表" name="standard">
          <el-form :inline="true" class="filter-form">
            <el-form-item label="项目">
              <el-select v-model="filterProjectId" placeholder="全部项目" clearable @change="fetchData" style="width: 180px">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="报表类型">
              <el-select v-model="filterType" placeholder="全部" clearable @change="fetchData" style="width: 150px">
                <el-option label="需求追溯报告" value="TRACEABILITY" />
                <el-option label="变更统计报告" value="CHANGE" />
                <el-option label="合规检查报告" value="COMPLIANCE" />
                <el-option label="风险评估报告" value="RISK" />
              </el-select>
            </el-form-item>
          </el-form>

          <el-table :data="reports" border stripe v-loading="loading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="reportType" label="报表类型" width="140">
              <template #default="{ row }">
                <el-tag v-if="row.reportType">{{ getReportTypeName(row.reportType) }}</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="200" />
            <el-table-column prop="projectName" label="项目" width="140">
              <template #default="{ row }">
                <span v-if="row.projectId">{{ getProjectName(row.projectId) }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="generatedAt" label="生成时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.generatedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="downloadReport(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="reports.length === 0 && !loading" description="暂无报表数据" />
        </el-tab-pane>

        <el-tab-pane label="经营分析" name="business">
          <div class="business-grid">
            <el-row :gutter="16">
              <el-col :span="6" v-for="m in businessMetrics" :key="m.key">
                <el-card shadow="hover" class="metric-card">
                  <div class="metric-label">{{ m.label }}</div>
                  <div class="metric-value">{{ m.value }}</div>
                  <div class="metric-trend" :class="m.trend > 0 ? 'up' : 'down'">
                    {{ m.trend > 0 ? '+' : '' }}{{ m.trend }}% 同比
                  </div>
                </el-card>
              </el-col>
            </el-row>

            <el-card class="chart-card">
              <template #header>
                <div class="card-header">
                  <span>近 6 月项目状态分布</span>
                  <el-radio-group v-model="bizChartType" size="small">
                    <el-radio-button label="bar" />
                    <el-radio-button label="line" />
                  </el-radio-group>
                </div>
              </template>
              <!-- v1.53 P1-26：经营分析用 canvas 自绘（不引入 chart.js 依赖，保持轻量） -->
              <canvas ref="bizCanvas" width="800" height="260" class="biz-canvas"></canvas>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="审计报告" name="audit">
          <el-form :inline="true" class="filter-form">
            <el-select v-model="auditEntity" placeholder="实体类型" clearable style="width: 160px;" @change="loadAudit">
              <el-option label="需求" value="REQUIREMENT" />
              <el-option label="变更" value="CHANGE_REQUEST" />
              <el-option label="基线" value="BASELINE" />
              <el-option label="签名" value="SIGNATURE_RECORD" />
            </el-select>
            <el-select v-model="auditEvent" placeholder="操作类型" clearable style="width: 140px;" @change="loadAudit">
              <el-option label="创建" value="CREATE" />
              <el-option label="修改" value="MODIFY" />
              <el-option label="删除" value="DELETE" />
              <el-option label="状态变更" value="STATUS_CHANGE" />
              <el-option label="签名" value="SIGN" />
            </el-select>
          </el-form>
          <el-table :data="auditLogs" border stripe v-loading="auditLoading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="operatorName" label="操作人" width="120" />
            <el-table-column prop="eventType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.eventType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="entityType" label="实体" width="140" />
            <el-table-column prop="entityId" label="实体ID" width="100" />
            <el-table-column prop="operation" label="操作" min-width="200" show-overflow-tooltip />
            <el-table-column prop="ipAddress" label="IP" width="140" />
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="auditLogs.length === 0 && !auditLoading" description="暂无审计数据" />
        </el-tab-pane>

        <el-tab-pane label="自定义报告" name="custom">
          <div class="custom-entry">
            <el-empty description="点击下方按钮进入自定义报告配置器" />
            <el-button type="primary" @click="$router.push('/reports/custom')">前往自定义报告</el-button>
          </div>
        </el-tab-pane>

        <!-- R62 7.8.3 统一入口：5 类独立页报表的快捷入口卡片（DHF / eRPS / IEC / SOUP / Worklog） -->
        <el-tab-pane label="其他报表" name="more">
          <div class="more-entries">
            <p style="color:#909399;font-size:13px;margin-bottom:16px">
              以下报表由独立模块提供，点击卡片跳转：
            </p>
            <el-row :gutter="16">
              <el-col :span="8" v-for="entry in moreEntries" :key="entry.path">
                <el-card shadow="hover" class="entry-card" @click="$router.push(entry.path)" style="cursor:pointer">
                  <div class="entry-icon">{{ entry.icon }}</div>
                  <div class="entry-title">{{ entry.title }}</div>
                  <div class="entry-desc">{{ entry.desc }}</div>
                </el-card>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 生成报表对话框 -->
    <el-dialog v-model="showGenerateDialog" title="生成报表" width="400px">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="报表类型">
          <el-select v-model="generateForm.reportType">
            <el-option label="需求追溯报告" value="TRACEABILITY" />
            <el-option label="变更统计报告" value="CHANGE" />
            <el-option label="合规检查报告" value="COMPLIANCE" />
            <el-option label="风险评估报告" value="RISK" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="generateForm.projectId">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" @click="submitGenerate" :loading="submitting">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

interface Report {
  id: number
  reportType: string
  title: string
  projectId: number
  projectName?: string
  generatedAt: string
  generatedBy?: number
}

interface Project {
  id: number
  projectNo: string
  projectName: string
  status: string
}


// P2-2：跳转到统一导出页 /reports/export，预填当前过滤的报表类型
const router = useRouter()
const goExport = () => {
 router.push({ path: '/reports/export', query: { reportType: filterType.value || 'TRACEABILITY' } })
}
const activeTab = ref('standard')
const filterProjectId = ref<number | ''>('')
const filterType = ref('')
const reports = ref<Report[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const showGenerateDialog = ref(false)
const submitting = ref(false)

const generateForm = ref({
  reportType: 'TRACEABILITY',
  projectId: 1,
})

const reportTypeMap: Record<string, string> = {
  TRACEABILITY: '需求追溯报告',
  CHANGE: '变更统计报告',
  COMPLIANCE: '合规检查报告',
  RISK: '风险评估报告',
}

const getReportTypeName = (type: string) => reportTypeMap[type] || type

const getProjectName = (id: number) => {
  const p = projects.value.find(p => p.id === id)
  return p?.projectName || `项目${id}`
}

const formatDate = (date: string) => {
  if (!date) return '-'
  return date.replace('T', ' ').substring(0, 19)
}

// R62 7.8.3 统一入口：5 类其他报表的快捷入口
const moreEntries = [
  { icon: '📋', title: 'DHF 证据包',   desc: '一键生成包含追溯矩阵/评审/签名/审计/变更/法规/基线/SOUP/问题/IEC 的 PDF 合规证据包', path: '/compliance/dhf' },
  { icon: '🏛️', title: 'NMPA eRPS',    desc: '按 NMPA eRPS 模板生成可追溯性分析报告，支持中英文双语', path: '/compliance/erps' },
  { icon: '✅', title: 'IEC 62304 检查', desc: '按 Clause 5-9 条款生成合规检查报告，列出覆盖状态和证据', path: '/compliance/iec62304' },
  { icon: '🧩', title: 'SOUP 组件清单', desc: '导出含安全影响评估结果的 SOUP 组件清单 (Excel)', path: '/compliance/soup' },
  { icon: '⏱️', title: '工时统计',     desc: '按项目/人员/需求维度汇总的任务工时报表 (Excel)', path: '/projects/worklog' },
]

const fetchData = async () => {
  loading.value = true
  try {
    const res = await request.get('/reports', {
      params: {
        projectId: filterProjectId.value || undefined,
        reportType: filterType.value || undefined
      }
    })
    reports.value = res.data.data || []
  } catch {
    ElMessage.error('获取报表列表失败')
  } finally {
    loading.value = false
  }
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    projects.value = res.data.data || []
    if (projects.value.length > 0) {
      generateForm.value.projectId = projects.value[0].id
    }
  } catch {
    // ignore
  }
}

const submitGenerate = async () => {
  submitting.value = true
  try {
    await request.post('/reports/generate', generateForm.value)
    ElMessage.success('报表生成成功')
    showGenerateDialog.value = false
    fetchData()
  } catch {
    ElMessage.error('生成失败')
  } finally {
    submitting.value = false
  }
}

const downloadReport = async (row: Report) => {
  try {
    const res = await request.get(`/reports/${row.id}/download`, { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/plain;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = `MedRMS报表_${row.id}_${row.reportType || 'unknown'}.txt`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '下载失败'
    ElMessage.error(msg)
  }
}

// v1.53 P1-26：经营分析 — 指标 + canvas 简易图表
const bizChartType = ref<'bar' | 'line'>('bar')
const businessMetrics = ref([
  { key: 'active', label: '进行中项目', value: 12, trend: 8 },
  { key: 'completed', label: '本月完成', value: 4, trend: -2 },
  { key: 'risk', label: '高风险项', value: 3, trend: 1 },
  { key: 'rate', label: '需求达成率', value: '86%', trend: 5 },
])
const bizCanvas = ref<HTMLCanvasElement | null>(null)

const drawBizChart = () => {
  const canvas = bizCanvas.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const w = canvas.width, h = canvas.height
  ctx.clearRect(0, 0, w, h)
  // 6 个月数据（PLANNING / IN_PROGRESS / COMPLETED）
  const data = [
    { m: '1月', plan: 5, prog: 8, done: 2 },
    { m: '2月', plan: 7, prog: 9, done: 3 },
    { m: '3月', plan: 6, prog: 11, done: 4 },
    { m: '4月', plan: 4, prog: 10, done: 5 },
    { m: '5月', plan: 3, prog: 12, done: 4 },
    { m: '6月', plan: 2, prog: 10, done: 6 },
  ]
  const colors = { plan: '#909399', prog: '#409eff', done: '#67c23a' }
  const maxVal = 15
  const padL = 40, padB = 30, padT = 20, padR = 20
  const chartW = w - padL - padR
  const chartH = h - padT - padB
  // 坐标
  ctx.strokeStyle = '#ebeef5'
  ctx.beginPath()
  for (let i = 0; i <= 5; i++) {
    const y = padT + (chartH * i / 5)
    ctx.moveTo(padL, y); ctx.lineTo(w - padR, y)
  }
  ctx.stroke()
  if (bizChartType.value === 'bar') {
    const groupW = chartW / data.length
    const barW = groupW / 4
    data.forEach((d, i) => {
      const x0 = padL + groupW * i + groupW / 2
      ;(['plan', 'prog', 'done'] as const).forEach((k, j) => {
        const val = (d as any)[k]
        const barH = (val / maxVal) * chartH
        ctx.fillStyle = colors[k]
        ctx.fillRect(x0 - 1.5 * barW + j * barW, h - padB - barH, barW - 2, barH)
      })
      ctx.fillStyle = '#606266'; ctx.font = '12px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(d.m, x0, h - padB + 16)
    })
  } else {
    // 折线
    ;(['plan', 'prog', 'done'] as const).forEach(k => {
      ctx.strokeStyle = colors[k]; ctx.lineWidth = 2; ctx.beginPath()
      data.forEach((d, i) => {
        const x = padL + (chartW * i) / (data.length - 1)
        const y = h - padB - ((d as any)[k] / maxVal) * chartH
        i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
      })
      ctx.stroke()
    })
    data.forEach((d, i) => {
      const x = padL + (chartW * i) / (data.length - 1)
      ctx.fillStyle = '#606266'; ctx.textAlign = 'center'
      ctx.fillText(d.m, x, h - padB + 16)
    })
  }
}

watch(activeTab, (v) => {
  if (v === 'business') nextTick(drawBizChart)
})
watch(bizChartType, () => nextTick(drawBizChart))

// v1.53 P1-26：审计报告 tab
const auditLogs = ref<any[]>([])
const auditLoading = ref(false)
const auditEntity = ref('')
const auditEvent = ref('')
const loadAudit = async () => {
  auditLoading.value = true
  try {
    // WHY: 复用 /compliance/audit-logs，符合 21 CFR Part 11 §11.10(e) 审计要求
    const res = await request.get('/compliance/audit-logs', {
      params: { entityType: auditEntity.value || undefined, eventType: auditEvent.value || undefined, size: 200 }
    })
    auditLogs.value = res.data?.data || []
  } catch (e: any) {
    ElMessage.error('加载审计日志失败：' + (e?.response?.data?.message || e?.message))
    auditLogs.value = []
  } finally {
    auditLoading.value = false
  }
}

onMounted(() => {
  fetchProjects()
  fetchData()
  loadAudit()
})
</script>

<style scoped>
.report-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; }
.filter-form { margin-bottom: 16px; }
.business-grid { display: flex; flex-direction: column; gap: 16px; }
.metric-card { text-align: center; padding: 12px 0; }
.metric-label { font-size: 13px; color: #909399; }
.metric-value { font-size: 28px; font-weight: 700; color: #303133; margin: 6px 0; }
.metric-trend { font-size: 12px; }
.metric-trend.up { color: #67c23a; }
.metric-trend.down { color: #f56c6c; }
.chart-card { margin-top: 8px; }
.biz-canvas { width: 100%; max-width: 100%; }
.custom-entry { padding: 40px 0; text-align: center; }
.more-entries { padding: 8px 0; }
.entry-card { margin-bottom: 16px; transition: transform 0.2s, box-shadow 0.2s; }
.entry-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.12); }
.entry-icon { font-size: 32px; text-align: center; margin-bottom: 8px; }
.entry-title { font-size: 16px; font-weight: 600; text-align: center; color: #303133; margin-bottom: 6px; }
.entry-desc { font-size: 12px; color: #909399; line-height: 1.5; }
</style>
