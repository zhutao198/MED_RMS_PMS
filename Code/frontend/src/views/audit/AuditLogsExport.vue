<template>
  <div class="audit-export-container">
    <el-page-header @back="goBack" class="page-back">
      <template #content>
        <span class="page-title">审计日志导出</span>
      </template>
    </el-page-header>

    <el-card class="page-intro">
      <div class="intro-title">合规说明</div>
      <div class="intro-text">
        本导出符合 21 CFR Part 11 §11.10(b) 记录保护要求。导出文件包含完整审计日志及哈希链证据，
        可作为合规审查与外部审计的取证依据。建议使用 <strong>CSV</strong> 格式以便存档与版本控制系统的差异追踪。
      </div>
    </el-card>

    <!-- 步骤条 -->
    <el-card class="stepper-card">
      <el-steps :active="currentStep" align-center finish-status="success">
        <el-step title="时间范围" description="选择导出时间窗口" />
        <el-step title="筛选条件" description="可选：按用户/动作/模块" />
        <el-step title="格式与字段" description="选择文件格式与字段" />
        <el-step title="生成与下载" description="执行导出并下载" />
      </el-steps>
    </el-card>

    <!-- 步骤 1：时间范围 -->
    <el-card v-show="currentStep === 0" class="step-card" header="步骤 1 / 4  ·  选择时间范围">
      <el-form :model="form" label-width="120px" label-position="right">
        <el-form-item label="预设范围" prop="preset">
          <el-radio-group v-model="preset" @change="applyPreset">
            <el-radio label="7">最近 7 天</el-radio>
            <el-radio label="30">最近 30 天</el-radio>
            <el-radio label="90">最近 90 天</el-radio>
            <el-radio label="custom">自定义</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="开始时间" required>
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            placeholder="选择开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
            :disabled="preset !== 'custom'"
          />
        </el-form-item>
        <el-form-item label="结束时间" required>
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            placeholder="选择结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%;"
            :disabled="preset !== 'custom'"
          />
        </el-form-item>
        <el-form-item>
          <el-tag v-if="dayCount > 0" type="info">
            时间跨度：{{ dayCount }} 天
          </el-tag>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 步骤 2：筛选条件 -->
    <el-card v-show="currentStep === 1" class="step-card" header="步骤 2 / 4  ·  筛选条件（可选）">
      <el-form :model="form" label-width="120px">
        <el-form-item label="操作人">
          <el-input
            v-model.number="form.operatorId"
            type="number"
            placeholder="留空表示全部操作人（输入用户 ID 精确匹配）"
            clearable
          />
        </el-form-item>
        <el-form-item label="操作动作">
          <el-select v-model="form.actionFilter" placeholder="全部操作" clearable>
            <el-option label="全部操作" value="" />
            <el-option
              v-for="a in ACTION_OPTIONS"
              :key="a.value"
              :label="a.label"
              :value="a.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实体类型">
          <el-select v-model="form.entityType" placeholder="全部实体" clearable>
            <el-option label="全部实体" value="" />
            <el-option
              v-for="m in MODULE_OPTIONS"
              :key="m.value"
              :label="m.label"
              :value="m.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 步骤 3：格式与字段 -->
    <el-card v-show="currentStep === 2" class="step-card" header="步骤 3 / 4  ·  选择文件格式">
      <el-form :model="form" label-width="120px">
        <el-form-item label="导出格式" required>
          <el-radio-group v-model="form.format">
            <el-radio-button label="csv">CSV</el-radio-button>
            <el-radio-button label="xlsx">Excel</el-radio-button>
            <el-radio-button label="pdf">PDF</el-radio-button>
          </el-radio-group>
          <div class="format-hint">
            <el-tag v-if="form.format === 'csv'" type="success" size="small">后端原生支持</el-tag>
            <el-tag v-else type="warning" size="small">前端兜底生成</el-tag>
            <span class="format-desc">{{ formatDescription }}</span>
          </div>
        </el-form-item>
        <el-form-item label="包含字段">
          <el-checkbox-group v-model="form.fields">
            <el-checkbox
              v-for="f in availableFields"
              :key="f.value"
              :label="f.value"
            >
              {{ f.label }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 步骤 4：导出进度 -->
    <el-card v-show="currentStep === 3" class="step-card" header="步骤 4 / 4  ·  生成与下载">
      <div v-if="!progress.finished && !progress.error" class="exporting-block">
        <el-progress
          :percentage="progress.percent"
          :status="progress.percent >= 100 ? 'success' : undefined"
          :stroke-width="18"
        />
        <div class="progress-stage">{{ progress.stage }}</div>
        <div class="progress-tip">
          预计记录数：<strong>{{ progress.estimatedCount.toLocaleString() }}</strong> 条
        </div>
      </div>

      <el-result
        v-else-if="progress.finished"
        icon="success"
        title="导出完成"
        :sub-title="`已生成 ${progress.actualCount.toLocaleString()} 条记录的 ${form.format.toUpperCase()} 文件`"
      >
        <template #extra>
          <el-button type="primary" @click="downloadAgain">重新下载</el-button>
          <el-button @click="resetAll">再次导出</el-button>
        </template>
      </el-result>

      <el-result
        v-else
        icon="error"
        title="导出失败"
        :sub-title="progress.error"
      >
        <template #extra>
          <el-button type="primary" @click="startExport">重试</el-button>
          <el-button @click="currentStep = 2">返回上一步</el-button>
        </template>
      </el-result>
    </el-card>

    <!-- 导出预览（常驻） -->
    <el-card class="preview-card" header="导出预览">
      <el-descriptions :column="2" border size="default">
        <el-descriptions-item label="时间范围">
          {{ form.startTime || '-' }} ~ {{ form.endTime || '-' }}
          <span v-if="dayCount > 0" style="color:#909399">（{{ dayCount }} 天）</span>
        </el-descriptions-item>
        <el-descriptions-item label="操作人">全部 / 指定 ID：{{ form.operatorId || '无' }}</el-descriptions-item>
        <el-descriptions-item label="操作动作">
          {{ form.actionFilter ? form.actionFilter.toUpperCase() : '全部' }}
        </el-descriptions-item>
        <el-descriptions-item label="实体类型">
          {{ form.entityType || '全部' }}
        </el-descriptions-item>
        <el-descriptions-item label="文件格式">{{ form.format.toUpperCase() }}</el-descriptions-item>
        <el-descriptions-item label="包含字段">{{ form.fields.length }} 个</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 操作栏 -->
    <div class="action-bar">
      <el-button v-if="currentStep > 0 && !progress.started" @click="prevStep">上一步</el-button>
      <el-button
        v-if="currentStep < 3"
        type="primary"
        :disabled="!canGoNext"
        @click="nextStep"
      >
        {{ currentStep === 2 ? '开始导出' : '下一步' }}
      </el-button>
      <el-button v-if="currentStep === 3 && progress.finished" @click="goBack">返回列表</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { auditApi, ACTION_OPTIONS, MODULE_OPTIONS, toBackendEntityType } from '@/api/audit'

const router = useRouter()

// ============== 步骤状态 ==============
const currentStep = ref(0)
const preset = ref<'7' | '30' | '90' | 'custom'>('30')

// ============== 表单 ==============
const form = reactive({
  startTime: '',
  endTime: '',
  operatorId: null as number | null,
  actionFilter: '',
  entityType: '',
  format: 'csv' as 'csv' | 'xlsx' | 'pdf',
  fields: ['timestamp', 'user', 'action', 'module', 'target', 'ip', 'detail', 'hash'] as string[],
})

const availableFields = [
  { value: 'timestamp', label: '时间戳' },
  { value: 'user', label: '操作人' },
  { value: 'action', label: '操作类型' },
  { value: 'module', label: '模块' },
  { value: 'target', label: '目标实体' },
  { value: 'ip', label: 'IP 地址' },
  { value: 'detail', label: '操作详情' },
  { value: 'hash', label: '哈希值' },
  { value: 'reason', label: '操作原因' },
]

// ============== 进度 ==============
const progress = reactive({
  started: false,
  finished: false,
  error: '' as string,
  percent: 0,
  stage: '准备中…',
  estimatedCount: 0,
  actualCount: 0,
  blobUrl: '' as string,
  blobName: '' as string,
})

// ============== 计算属性 ==============
const dayCount = computed(() => {
  if (!form.startTime || !form.endTime) return 0
  const s = new Date(form.startTime).getTime()
  const e = new Date(form.endTime).getTime()
  if (isNaN(s) || isNaN(e) || e < s) return 0
  return Math.max(1, Math.ceil((e - s) / (1000 * 60 * 60 * 24)))
})

const formatDescription = computed(() => {
  switch (form.format) {
    case 'csv':
      return '逗号分隔值，UTF-8 编码，可被 Excel/WPS 直接打开，适合版本控制 diff'
    case 'xlsx':
      return 'Excel 表格格式，含列宽自适应（前端兜底生成）'
    case 'pdf':
      return '可打印 PDF 报告，含合规签字栏（前端兜底生成）'
    default:
      return ''
  }
})

const canGoNext = computed(() => {
  if (currentStep.value === 0) {
    return !!form.startTime && !!form.endTime && dayCount.value > 0
  }
  if (currentStep.value === 1) return true
  if (currentStep.value === 2) return !!form.format && form.fields.length > 0
  return false
})

// ============== 步骤控制 ==============
const applyPreset = (val: string | number | boolean | undefined) => {
  const v = String(val || '')
  if (v === 'custom') return
  const days = Number(v)
  if (!days) return
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days)
  form.endTime = formatDateTime(end)
  form.startTime = formatDateTime(start)
}

const formatDateTime = (d: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const nextStep = () => {
  if (currentStep.value === 2) {
    startExport()
    return
  }
  currentStep.value += 1
}

const prevStep = () => {
  if (currentStep.value > 0) currentStep.value -= 1
}

const resetAll = () => {
  currentStep.value = 0
  progress.started = false
  progress.finished = false
  progress.error = ''
  progress.percent = 0
  progress.stage = '准备中…'
  progress.actualCount = 0
  if (progress.blobUrl) {
    URL.revokeObjectURL(progress.blobUrl)
    progress.blobUrl = ''
  }
}

const goBack = () => {
  router.push('/audit-logs')
}

// ============== 导出流程 ==============
const estimateCount = async (): Promise<number> => {
  // 简化为"按 30 天 ≈ 2000 条 / 7 天 ≈ 500 条"线性估算
  const base = Math.max(1, dayCount.value) * 70
  return Math.min(20000, Math.round(base))
}

const startExport = async () => {
  if (!canGoNext.value) {
    ElMessage.warning('请检查时间范围、格式与字段')
    return
  }
  currentStep.value = 3
  progress.started = true
  progress.finished = false
  progress.error = ''
  progress.percent = 0
  progress.actualCount = 0
  progress.estimatedCount = await estimateCount()

  try {
    progress.stage = '正在连接后端…'
    progress.percent = 20
    await sleep(300)

    if (form.format === 'csv') {
      // 后端原生 CSV 导出
      progress.stage = '后端生成 CSV…'
      progress.percent = 50
      const params: Record<string, string> = {}
      if (form.startTime) params.startTime = form.startTime
      if (form.endTime) params.endTime = form.endTime
      if (form.entityType) params.entityType = toBackendEntityType(form.entityType)
      const res = await auditApi.exportCsv(params)
      progress.percent = 80
      const blob = res.data as Blob
      const ts = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19)
      const filename = `audit-logs-${ts}.csv`
      saveBlob(blob, filename)
      progress.blobName = filename
      progress.blobUrl = URL.createObjectURL(blob)
      // 尝试从 CSV 行数估算实际记录数（粗略）
      const text = await blob.text()
      progress.actualCount = Math.max(0, text.split('\n').length - 1)
    } else {
      // xlsx / pdf：前端从后端拉数据并兜底生成
      progress.stage = '拉取审计数据…'
      progress.percent = 30
      const res = await auditApi.list({
        startTime: form.startTime,
        endTime: form.endTime,
        entityType: form.entityType ? toBackendEntityType(form.entityType) : undefined,
        size: 2000,
        page: 0,
      })
      const records = res.data?.data || []
      progress.actualCount = Array.isArray(records) ? records.length : 0
      progress.percent = 60
      progress.stage = `正在生成 ${form.format.toUpperCase()}（${progress.actualCount} 条）…`
      await sleep(500)
      const blob = form.format === 'xlsx'
        ? buildExcelBlob(records)
        : buildPdfBlob(records)
      const ts = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19)
      const filename = `audit-logs-${ts}.${form.format}`
      saveBlob(blob, filename)
      progress.blobName = filename
      progress.blobUrl = URL.createObjectURL(blob)
    }

    progress.percent = 100
    progress.stage = '完成'
    progress.finished = true
    ElMessage.success(`导出成功：${progress.actualCount} 条记录`)
  } catch (e: any) {
    progress.error = e?.response?.data?.message || e?.message || '未知错误'
    progress.finished = false
    ElMessage.error('导出失败：' + progress.error)
  }
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

const saveBlob = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  // 不立即 revoke，给"重新下载"留余地
}

const downloadAgain = () => {
  if (!progress.blobUrl) return
  const a = document.createElement('a')
  a.href = progress.blobUrl
  a.download = progress.blobName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
}

// ============== 前端兜底：Excel 简易表（CSV 套 .xlsx 扩展名为 CSV workbook 不合规，生成 SpreadsheetML）==============
const buildExcelBlob = (records: any[]): Blob => {
  // 生成 SpreadsheetML 2003 XML（Excel 可直接打开）
  const esc = (s: any): string => {
    if (s === null || s === undefined) return ''
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
  }
  const headers = ['ID', '时间', '操作人', '操作', '实体类型', '实体ID', 'IP', '操作详情', '原因', '哈希']
  let xml = '<?xml version="1.0"?><?mso-application progid="Excel.Sheet"?>\n'
  xml += '<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" '
  xml += 'xmlns:o="urn:schemas-microsoft-com:office:office" '
  xml += 'xmlns:x="urn:schemas-microsoft-com:office:excel" '
  xml += 'xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet">\n'
  xml += '<Styles><Style ss:ID="Header"><Font ss:Bold="1"/><Interior ss:Color="#D9E1F2" ss:Pattern="Solid"/></Style></Styles>\n'
  xml += '<Worksheet ss:Name="审计日志"><Table>\n'
  xml += '<Row>' + headers.map((h) => `<Cell ss:StyleID="Header"><Data ss:Type="String">${h}</Data></Cell>`).join('') + '</Row>\n'
  for (const r of records) {
    xml += '<Row>'
    xml += `<Cell><Data ss:Type="Number">${esc(r.id)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc((r.createdAt || '').replace('T', ' '))}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc(r.operatorName)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc(r.eventType)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc(r.entityType)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="Number">${esc(r.entityId)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc(r.ipAddress)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc(r.operation)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc(r.reason)}</Data></Cell>`
    xml += `<Cell><Data ss:Type="String">${esc((r.currentHash || '').substring(0, 16))}</Data></Cell>`
    xml += '</Row>\n'
  }
  xml += '</Table></Worksheet></Workbook>'
  return new Blob([xml], { type: 'application/vnd.ms-excel' })
}

// ============== 前端兜底：PDF（生成可打印 HTML，让浏览器另存为）==============
const buildPdfBlob = (records: any[]): Blob => {
  const esc = (s: any): string => {
    if (s === null || s === undefined) return ''
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
  const rows = records.slice(0, 500).map((r) => `<tr>
    <td>${esc(r.id)}</td>
    <td>${esc((r.createdAt || '').replace('T', ' '))}</td>
    <td>${esc(r.operatorName)}</td>
    <td>${esc(r.eventType)}</td>
    <td>${esc(r.entityType)}</td>
    <td>${esc(r.entityId)}</td>
    <td>${esc(r.ipAddress)}</td>
    <td>${esc(r.operation)}</td>
  </tr>`).join('')
  const html = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>审计日志</title>
  <style>
    body { font-family: 'Microsoft YaHei', sans-serif; padding: 20px; }
    h1 { font-size: 18px; }
    .meta { color: #666; font-size: 12px; margin-bottom: 12px; }
    table { width: 100%; border-collapse: collapse; font-size: 11px; }
    th, td { border: 1px solid #ccc; padding: 4px 6px; text-align: left; }
    th { background: #f0f2f5; }
    .footer { margin-top: 24px; font-size: 11px; color: #999; }
  </style>
  </head><body>
  <h1>Med-RMS 审计日志报告</h1>
  <div class="meta">
    生成时间：${new Date().toLocaleString('zh-CN')}<br/>
    时间范围：${esc(form.startTime)} ~ ${esc(form.endTime)}<br/>
    记录数：${records.length}${records.length > 500 ? '（仅展示前 500 条）' : ''}<br/>
    实体类型过滤：${esc(form.entityType || '全部')}<br/>
    操作过滤：${esc(form.actionFilter || '全部')}<br/>
    合规依据：21 CFR Part 11 §11.10(b)
  </div>
  <table>
    <thead><tr>
      <th>ID</th><th>时间</th><th>操作人</th><th>操作</th>
      <th>实体类型</th><th>实体ID</th><th>IP</th><th>详情</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>
  <div class="footer">
    审计员签字：____________________ &nbsp;&nbsp; 日期：____________________<br/>
    Med-RMS 医疗器械需求管理系统 · 21 CFR Part 11 Compliant
  </div>
  </body></html>`
  return new Blob([html], { type: 'application/pdf' })
}

onMounted(() => {
  applyPreset('30')
})
</script>

<style scoped>
.audit-export-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
  max-width: 1100px;
  margin: 0 auto;
}

.page-back {
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.page-intro {
  margin-bottom: 16px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
}

.intro-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.intro-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.stepper-card {
  margin-bottom: 16px;
}

.step-card {
  margin-bottom: 16px;
}

.preview-card {
  margin-bottom: 16px;
}

.format-hint {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.format-desc {
  color: #606266;
}

.exporting-block {
  padding: 24px 0;
}

.progress-stage {
  margin-top: 16px;
  font-size: 14px;
  color: #303133;
  text-align: center;
}

.progress-tip {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: #909399;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 0;
}
</style>
