<template>
  <div class="risk-register-container">
    <div class="page-header">
      <div class="page-title">风险管理</div>
      <div class="header-actions">
        <el-button @click="handleRefresh">刷新</el-button>
        <el-button type="primary" @click="openCreateDialog">新建风险</el-button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-value">{{ stats.total }}</div>
        <div class="stat-label">风险总数</div>
      </div>
      <!-- P2-9 v1.53 修复：严重度分布按 C/H/M/L/N 4 档（CRITICAL/MAJOR/MINOR/NEGLIGIBLE） -->
      <div class="stat-card critical">
        <div class="stat-value">{{ stats.critical }}</div>
        <div class="stat-label">灾难性 C</div>
      </div>
      <div class="stat-card major">
        <div class="stat-value">{{ stats.major }}</div>
        <div class="stat-label">严重 M</div>
      </div>
      <div class="stat-card minor">
        <div class="stat-value">{{ stats.minor }}</div>
        <div class="stat-label">轻度 m</div>
      </div>
      <div class="stat-card negligible">
        <div class="stat-value">{{ stats.negligible }}</div>
        <div class="stat-label">可忽略 N</div>
      </div>
      <div class="stat-card open">
        <div class="stat-value">{{ stats.open }}</div>
        <div class="stat-label">待处理</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索风险编号/标题" style="width: 220px" clearable @keyup.enter="fetchRisks">
        <template #prefix><span>🔍</span></template>
      </el-input>
      <el-select v-model="filter.status" placeholder="状态" style="width: 130px;" clearable @change="fetchRisks">
        <el-option label="开放" value="OPEN" />
        <el-option label="处理中" value="IN_PROGRESS" />
        <el-option label="已关闭" value="CLOSED" />
        <el-option label="已接受" value="ACCEPTED" />
      </el-select>
      <el-select v-model="filter.category" placeholder="类别" style="width: 130px;" clearable @change="fetchRisks">
        <el-option label="产品风险" value="PRODUCT" />
        <el-option label="过程风险" value="PROCESS" />
        <el-option label="供应商风险" value="SUPPLIER" />
        <el-option label="法规风险" value="REGULATORY" />
      </el-select>
      <el-select v-model="filter.projectId" placeholder="项目" style="width: 160px;" clearable @change="fetchRisks">
        <el-option
          v-for="p in projects"
          :key="p.id"
          :label="p.projectName"
          :value="p.id" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
      <el-button size="small" type="primary" plain @click="handleExport" :disabled="!risks.length">导出 CSV</el-button>
    </div>

    <div class="risk-table">
      <el-table :data="filteredRisks" border size="small" v-loading="loading">
        <el-table-column prop="riskNo" label="风险编号" width="140" />
        <el-table-column prop="riskTitle" label="风险标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ getCategoryLabel(row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="严重度" width="100" align="center">
          <!-- v1.53 P1-18 修复：保留后端枚举 + 数字徽标（CRITICAL=4, MAJOR=3, MINOR=2, NEGLIGIBLE=1） -->
          <template #default="{ row }">
            <el-tag :type="getSeverityType(row.severity)" size="small">
              {{ getSeverityLabel(row.severity) }} {{ getSeverityNum(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="probability" label="概率" width="80" align="center">
          <!-- v1.53 P1-18 修复：概率值 1-3 数字徽标 -->
          <template #default="{ row }">
            <el-tag :type="getProbabilityType(row.probability)" size="small">
              {{ row.probability || '-' }} {{ getProbabilityNum(row.probability) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="等级" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <!-- P3-5：RPN列（severity×probability×detectability），>30红色 tag高危提示；无 rpn时按当前三字段计算 -->
 <el-table-column prop="rpn" label="RPN" width="80" align="center">
 <template #default="{ row }">
 <el-tag v-if="(row.rpn ?? computeRpn(row))" :type="rpnTagType(row.rpn ?? computeRpn(row))" size="small" effect="dark">{{ row.rpn ?? computeRpn(row) }}</el-tag>
 <span v-else style="color:#c0c4cc">-</span>
 </template>
 </el-table-column>
        <el-table-column prop="detectability" label="可探测性" width="100" align="center">
          <template #default="{ row }">
            <span style="font-size:12px">{{ row.detectability || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="责任人" width="90">
          <template #default="{ row }">
            <span v-if="row.ownerName">{{ row.ownerName }}</span>
            <span v-else style="color:#c0c4cc">未指定</span>
          </template>
        </el-table-column>
        <el-table-column prop="dueDate" label="截止日期" width="120">
          <template #default="{ row }">
            <span v-if="row.dueDate">{{ formatDate(row.dueDate) }}</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'OPEN'" size="small" text type="warning" @click="handleProcess(row)">处理</el-button>
            <el-button v-if="row.status === 'IN_PROGRESS' || row.status === 'OPEN'" size="small" text type="success" @click="handleAccept(row)">接受</el-button>
            <el-button v-if="row.status !== 'CLOSED' && row.status !== 'ACCEPTED'" size="small" text type="info" @click="handleClose(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreate" title="新建风险" width="600px">
      <el-form :model="newRisk" label-width="100px">
        <el-form-item label="风险标题" required>
          <el-input v-model="newRisk.riskTitle" placeholder="请输入风险标题" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="newRisk.category" style="width: 100%;">
            <el-option label="产品风险" value="PRODUCT" />
            <el-option label="过程风险" value="PROCESS" />
            <el-option label="供应商风险" value="SUPPLIER" />
            <el-option label="法规风险" value="REGULATORY" />
          </el-select>
        </el-form-item>
        <el-form-item label="严重度">
          <el-select v-model="newRisk.severity" style="width: 100%;">
            <el-option label="灾难性" value="CRITICAL" />
            <el-option label="严重" value="MAJOR" />
            <el-option label="轻度" value="MINOR" />
            <el-option label="可忽略" value="NEGLIGIBLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="可能性">
          <el-select v-model="newRisk.probability" style="width: 100%;">
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <el-form-item label="可探测度">
          <el-select v-model="newRisk.detectability" style="width: 100%;">
            <el-option label="易" value="EASY" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="难" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="响应策略">
          <el-select v-model="newRisk.responseStrategy" style="width: 100%;">
            <el-option label="规避 AVOID" value="AVOID" />
            <el-option label="缓解 MITIGATE" value="MITIGATE" />
            <el-option label="转移 TRANSFER" value="TRANSFER" />
            <el-option label="接受 ACCEPT" value="ACCEPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-input v-model="newRisk.ownerName" placeholder="如：张工" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="newRisk.dueDate" type="date" style="width: 100%;" value-format="YYYY-MM-DD" placeholder="不填表示无截止" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newRisk.description" type="textarea" :rows="3" placeholder="请描述风险" />
        </el-form-item>
        <el-form-item label="控制措施">
          <el-input v-model="newRisk.controlMeasure" type="textarea" :rows="2" placeholder="请输入控制措施" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetail" title="风险详情" width="650px">
      <div v-if="selectedRisk" class="risk-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="风险编号">{{ selectedRisk.riskNo }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="getLevelType(selectedRisk.riskLevel)">{{ selectedRisk.riskLevel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="类别">{{ getCategoryLabel(selectedRisk.category) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ getStatusLabel(selectedRisk.status) }}</el-descriptions-item>
          <el-descriptions-item label="责任人">{{ selectedRisk.ownerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="截止日期">{{ selectedRisk.dueDate || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-section">
          <div class="section-title">风险描述</div>
          <p>{{ selectedRisk.description || '无' }}</p>
        </div>
        <div class="detail-section">
          <div class="section-title">根因分析</div>
          <p>{{ selectedRisk.rootCause || '待分析' }}</p>
        </div>
        <div class="detail-section">
          <div class="section-title">控制措施</div>
          <p>{{ selectedRisk.controlMeasure || '待制定' }}</p>
        </div>
        <div class="detail-section">
          <div class="section-title">响应策略</div>
          <p>{{ selectedRisk.responseStrategy || '待确定' }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { riskRegisterApi, type RiskRegister } from '@/api/risk'
import { projectApi } from '@/api/project'

const filter = ref({ status: '', category: '', projectId: '' as number | ''  })
const searchKeyword = ref('')
const showCreate = ref(false)
const showDetail = ref(false)
const creating = ref(false)
const loading = ref(false)
const selectedRisk = ref<RiskRegister | null>(null)
const risks = ref<RiskRegister[]>([])
const projects = ref<any[]>([])

const stats = ref({ total: 0, critical: 0, major: 0, minor: 0, negligible: 0, open: 0 })

const newRisk = ref({
  riskTitle: '',
  category: 'PRODUCT',
  severity: 'MAJOR',
  probability: 'MEDIUM',
  detectability: 'MEDIUM',
  description: '',
  controlMeasure: '',
  responseStrategy: 'MITIGATE',
  ownerName: '',
  dueDate: ''
})

const filteredRisks = computed(() => {
  const kw = searchKeyword.value.toLowerCase()
  return risks.value.filter(risk => {
    if (kw && !(risk.riskNo || '').toLowerCase().includes(kw) && !(risk.riskTitle || '').toLowerCase().includes(kw)) return false
    if (filter.value.status && risk.status !== filter.value.status) return false
    if (filter.value.category && risk.category !== filter.value.category) return false
    return true
  })
})

const formatDate = (v?: string | null) => {
  if (!v) return ''
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return ''
}

const computeStats = (list: RiskRegister[]) => {
  // P2-9 v1.53 修复：按 severity 字段 4 档统计 CRITICAL/MAJOR/MINOR/NEGLIGIBLE
  // 兼容：若 severity 缺失则降级到 riskLevel (HIGH→CRITICAL, MEDIUM→MAJOR, LOW→MINOR)
  const sevOf = (r: any): string => {
    if (r.severity) return r.severity
    return r.riskLevel === 'HIGH' ? 'CRITICAL' : r.riskLevel === 'MEDIUM' ? 'MAJOR' : r.riskLevel === 'LOW' ? 'MINOR' : ''
  }
  stats.value = {
    total: list.length,
    critical: list.filter(r => sevOf(r) === 'CRITICAL').length,
    major: list.filter(r => sevOf(r) === 'MAJOR').length,
    minor: list.filter(r => sevOf(r) === 'MINOR').length,
    negligible: list.filter(r => sevOf(r) === 'NEGLIGIBLE').length,
    open: list.filter(r => r.status === 'OPEN').length
  }
}

const fetchRisks = async () => {
  loading.value = true
  try {
    const params: any = {}
    if (filter.value.status) params.status = filter.value.status
    if (filter.value.category) params.category = filter.value.category
    if (filter.value.projectId) params.projectId = filter.value.projectId
    const res = await riskRegisterApi.list(params)
    risks.value = (res.data?.data || []) as RiskRegister[]
    computeStats(risks.value)
  } catch (e: any) {
    ElMessage.error('加载风险清单失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const getCategoryLabel = (cat: string) => ({ PRODUCT: '产品', PROCESS: '过程', SUPPLIER: '供应商', REGULATORY: '法规' }[cat] || cat)
const getSeverityLabel = (sev: string) => ({ CRITICAL: '灾难性', MAJOR: '严重', MINOR: '轻度', NEGLIGIBLE: '可忽略' }[sev] || sev || '-')
// v1.53 P1-18 修复：严重度数字映射 1-4 + 颜色
const SEVERITY_NUMS: Record<string, number> = { CRITICAL: 4, MAJOR: 3, MINOR: 2, NEGLIGIBLE: 1 }
const SEVERITY_TYPES: Record<string, string> = { CRITICAL: 'danger', MAJOR: 'danger', MINOR: 'warning', NEGLIGIBLE: 'info' }
const getSeverityNum = (s: string) => SEVERITY_NUMS[s] !== undefined ? `(${SEVERITY_NUMS[s]})` : ''
const getSeverityType = (s: string) => SEVERITY_TYPES[s] || 'info'
const PROBABILITY_NUMS: Record<string, number> = { HIGH: 3, MEDIUM: 2, LOW: 1 }
const PROBABILITY_TYPES: Record<string, string> = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
const getProbabilityNum = (p: string) => PROBABILITY_NUMS[p] !== undefined ? `(${PROBABILITY_NUMS[p]})` : ''
const getProbabilityType = (p: string) => PROBABILITY_TYPES[p] || 'info'
const getLevelType = (level: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }[level] || 'info')

// P3-5：RPN阈值——>30为高风险（红色 tag）。RPN = severity×probability×detectability
const rpnTagType = (rpn: number) => (rpn >30 ? 'danger' : (rpn >=16 ? 'warning' : 'success'))

 // P3-5：按三字段计算 RPN（severity×probability×detectability）。注意数字映射需与表格上方的 SEVERITY_NUMS / PROBABILITY_NUMS保持一致。
 const computeRpn = (row) => {
 const s = SEVERITY_NUMS[row.severity] ??0
 const p = PROBABILITY_NUMS[row.probability] ??0
 const d = ({ EASY:1, MEDIUM:2, HARD:3 })[row.detectability] ??1
 return s * p * d
 }
const getStatusType = (status: string) => ({ OPEN: 'danger', IN_PROGRESS: 'warning', CLOSED: 'success', ACCEPTED: 'info' }[status] || 'info')
const getStatusLabel = (status: string) => ({ OPEN: '开放', IN_PROGRESS: '处理中', CLOSED: '已关闭', ACCEPTED: '已接受' }[status] || status)

const resetFilters = () => {
  searchKeyword.value = ''
  filter.value.status = ''
  filter.value.category = ''
  filter.value.projectId = ''
  fetchRisks()
}

const handleRefresh = () => fetchRisks()

const viewDetail = async (row: RiskRegister) => {
  try {
    const res = await riskRegisterApi.get(row.id!)
    selectedRisk.value = res.data?.data || row
  } catch {
    selectedRisk.value = row
  }
  showDetail.value = true
}

const openCreateDialog = () => {
  newRisk.value = {
    riskTitle: '', category: 'PRODUCT', severity: 'MAJOR', probability: 'MEDIUM', detectability: 'MEDIUM',
    description: '', controlMeasure: '', responseStrategy: 'MITIGATE', ownerName: '', dueDate: ''
  }
  showCreate.value = true
}

const submitCreate = async () => {
  if (!newRisk.value.riskTitle) {
    ElMessage.warning('请输入风险标题')
    return
  }
  creating.value = true
  try {
    const payload: any = { ...newRisk.value }
    if (!payload.dueDate) delete payload.dueDate
    const res = await riskRegisterApi.create(payload)
    ElMessage.success(`风险创建成功：${res.data?.data?.riskNo || ''}`)
    showCreate.value = false
    await fetchRisks()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e.message))
  } finally {
    creating.value = false
  }
}

const handleProcess = async (row: RiskRegister) => {
  try {
    await ElMessageBox.confirm(`将风险 ${row.riskNo}「${row.riskTitle}」状态切换为「处理中」，确定吗？`, '处理风险', {
      type: 'info', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await riskRegisterApi.update(row.id!, { status: 'IN_PROGRESS' })
    ElMessage.success('风险已进入处理中状态')
    await fetchRisks()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败：' + (e?.response?.data?.message || e.message))
  }
}

const handleAccept = async (row: RiskRegister) => {
  try {
    await ElMessageBox.confirm(`将风险 ${row.riskNo} 标记为「已接受」（适用：风险可接受不需控制），确定吗？`, '接受风险', {
      type: 'info', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await riskRegisterApi.accept(row.id!)
    ElMessage.success('风险已接受')
    await fetchRisks()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败：' + (e?.response?.data?.message || e.message))
  }
}

const handleClose = async (row: RiskRegister) => {
  let closureNote = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入关闭原因（必填，存入 closureNote）', '关闭风险', {
      inputType: 'textarea', confirmButtonText: '关闭', cancelButtonText: '取消',
      inputValidator: (val: string) => (val && val.trim() ? true : '关闭原因不能为空')
    })
    closureNote = value
  } catch {
    return
  }
  try {
    await riskRegisterApi.close(row.id!, closureNote)
    ElMessage.success(`风险 ${row.riskNo} 已关闭`)
    await fetchRisks()
  } catch (e: any) {
    ElMessage.error('关闭失败：' + (e?.response?.data?.message || e.message))
  }
}

const handleExport = () => {
  if (!filteredRisks.value.length) {
    ElMessage.warning('暂无可导出的风险记录')
    return
  }
  const headers = ['风险编号', '标题', '类别', '严重度', '可能性', '可探测度', '风险等级', '状态', '响应策略', '责任人', '截止日期', '控制措施', '根因', '描述']
  const lines = [headers.join(',')]
  filteredRisks.value.forEach((r: any) => {
    lines.push([
      r.riskNo || '',
      `"${(r.riskTitle || '').replace(/"/g, '""')}"`,
      r.category || '',
      r.severity || '',
      r.probability || '',
      r.detectability || '',
      r.riskLevel || '',
      r.status || '',
      r.responseStrategy || '',
      `"${(r.ownerName || '').replace(/"/g, '""')}"`,
      formatDate(r.dueDate),
      `"${(r.controlMeasure || '').replace(/"/g, '""')}"`,
      `"${(r.rootCause || '').replace(/"/g, '""')}"`,
      `"${(r.description || '').replace(/"/g, '""')}"`
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `风险清单-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${filteredRisks.value.length} 条风险记录`)
}

const fetchProjects = async () => {
  try {
    const res = await projectApi.list()
    projects.value = (res.data?.data || []) as any[]
  } catch (e) {
    console.warn('加载项目列表失败', e)
  }
}

onMounted(() => { fetchProjects(); fetchRisks() })
</script>

<style scoped>
.risk-register-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; }
.stats-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { background: #fff; border-radius: 8px; padding: 20px; text-align: center; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); }
.stat-card.total { border-left: 4px solid #409eff; }
/* P2-9 v1.53 修复：4 档严重度色条（红/橙红/橙/蓝灰） */
.stat-card.critical { border-left: 4px solid #c00000; }
.stat-card.major { border-left: 4px solid #f56c6c; }
.stat-card.minor { border-left: 4px solid #e6a23c; }
.stat-card.negligible { border-left: 4px solid #909399; }
.stat-card.open { border-left: 4px solid #606266; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.filter-bar { background: #fff; border-radius: 8px; padding: 14px 16px; margin-bottom: 16px; display: flex; gap: 12px; align-items: center; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); }
.risk-table { background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); }
.risk-detail { padding: 10px 0; }
.detail-section { margin-top: 16px; }
.section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; border-left: 3px solid #409eff; padding-left: 8px; }
.detail-section p { font-size: 13px; color: #606266; line-height: 1.6; }
</style>
