<template>
  <div class="soup-management-container">
    <div class="page-header">
      <div class="page-title">SOUP 管理</div>
      <div class="header-actions">
        <el-button @click="handleImport">导入</el-button>
        <el-button @click="handleExport">导出</el-button>
        <el-button type="warning" @click="openAnomalyDialog">🔍 异常检测（FR-1.11）</el-button>
        <el-button @click="goRegulations">📜 法规映射</el-button>
        <el-button type="primary" @click="handleCreate">登记 SOUP</el-button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-icon">📦</div>
        <div class="stat-info">
          <div class="value">{{ stats.total }}</div>
          <div class="label">SOUP 总数</div>
        </div>
      </div>
      <div class="stat-card active">
        <div class="stat-icon">✅</div>
        <div class="stat-info">
          <div class="value">{{ stats.active }}</div>
          <div class="label">使用中</div>
        </div>
      </div>
      <div class="stat-card warning">
        <div class="stat-icon">⚠️</div>
        <div class="stat-info">
          <div class="value">{{ stats.withIssues }}</div>
          <div class="label">已知异常</div>
        </div>
      </div>
      <div class="stat-card linked">
        <div class="stat-icon">🔗</div>
        <div class="stat-info">
          <div class="value">{{ stats.linkedRate }}%</div>
          <div class="label">需求关联率</div>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索组件名称/供应商" style="width: 220px" clearable @keyup.enter="fetchSoups">
        <template #prefix>
          <span>🔍</span>
        </template>
      </el-input>
      <el-select v-model="categoryFilter" style="width: 150px" placeholder="组件分类" @change="handleFilterChange">
        <el-option label="全部分类" value="" />
        <el-option label="LIBRARY" value="LIBRARY" />
        <el-option label="FRAMEWORK" value="FRAMEWORK" />
        <el-option label="TOOL" value="TOOL" />
        <el-option label="OS" value="OS" />
      </el-select>
      <el-select v-model="riskFilter" style="width: 130px" placeholder="风险等级" @change="fetchSoups">
        <el-option label="全部等级" value="" />
        <el-option label="critical - 严重" value="critical" />
        <el-option label="high - 高" value="high" />
        <el-option label="medium - 中" value="medium" />
        <el-option label="low - 低" value="low" />
      </el-select>
      <el-select v-model="statusFilter" style="width: 130px" placeholder="使用状态" @change="fetchSoups">
        <el-option label="全部状态" value="" />
        <el-option label="使用中" value="ACTIVE" />
        <el-option label="已停用" value="DEPRECATED" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="soup-table">
      <el-table :data="filteredSoupList" border style="width: 100%" v-loading="loading">
        <el-table-column prop="componentName" label="组件名称" min-width="180">
          <template #default="{ row }">
            <div class="soup-name-cell">
              <span class="soup-name">{{ row.componentName }}</span>
              <span class="soup-version">v{{ row.version }} · {{ row.componentCode || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="softwareCategory" label="分类" width="100">
          <template #default="{ row }">
            <el-tag :class="'soup-tag-' + (row.softwareCategory || '').toLowerCase()" size="small">
              {{ row.softwareCategory || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" width="140" show-overflow-tooltip />
        <el-table-column prop="complianceStandard" label="标准" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.complianceStandard">{{ row.complianceStandard }}</span>
            <span v-else style="color:#c0c4cc">未指定</span>
          </template>
        </el-table-column>
        <el-table-column prop="riskLevel" label="风险等级" width="90" align="center">
          <template #default="{ row }">
            <span :class="'risk-badge risk-' + (row.riskLevel || '').toLowerCase()">
              {{ getRiskLabel(row.riskLevel) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="linkedRequirementCount" label="关联需求" width="90" align="center">
          <template #default="{ row }">
            <span class="linked-count">
              <span v-if="row.linkedRequirementCount > 0" class="linked-icon">🔗</span>
              <span v-else class="unlinked-icon">⚠️</span>
              {{ row.linkedRequirementCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="knownAnomalies" label="已知异常" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.knownAnomalies > 0" class="anomaly-count">
              {{ row.knownAnomalies }}
            </span>
            <span v-else class="no-anomaly">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="licenseExpiry" label="许可证到期" width="120">
          <template #default="{ row }">
            <span v-if="row.licenseExpiry">{{ formatDate(row.licenseExpiry) }}</span>
            <span v-else style="color:#c0c4cc">永久</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '使用中' : (row.status === 'DEPRECATED' ? '已停用' : (row.status || '-')) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleView(row)">详情</el-button>
            <el-button size="small" text type="warning" @click="handleRenew(row)">续期</el-button>
            <el-button size="small" text type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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

    <!-- 登记 SOUP -->
    <el-dialog v-model="showCreate" title="登记 SOUP 组件" width="540px">
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="组件名称" required>
          <el-input v-model="createForm.componentName" placeholder="如 OpenSSL" />
        </el-form-item>
        <el-form-item label="组件代号" required>
          <el-input v-model="createForm.componentCode" placeholder="如 OPENSSL" />
        </el-form-item>
        <el-form-item label="版本" required>
          <el-input v-model="createForm.version" placeholder="如 3.0.5" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="createForm.supplier" placeholder="如 OpenSSL Project" />
        </el-form-item>
        <el-form-item label="供应商国家">
          <el-input v-model="createForm.supplierCountry" placeholder="如 US" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="createForm.softwareCategory" style="width:100%;">
            <el-option label="LIBRARY" value="LIBRARY" />
            <el-option label="FRAMEWORK" value="FRAMEWORK" />
            <el-option label="TOOL" value="TOOL" />
            <el-option label="OS" value="OS" />
          </el-select>
        </el-form-item>
        <el-form-item label="风险等级" required>
          <el-select v-model="createForm.riskLevel" style="width:100%;">
            <el-option label="critical - 严重" value="critical" />
            <el-option label="high - 高" value="high" />
            <el-option label="medium - 中" value="medium" />
            <el-option label="low - 低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="许可证类型">
          <el-input v-model="createForm.licenseType" placeholder="如 Apache-2.0" />
        </el-form-item>
        <el-form-item label="许可证到期">
          <el-date-picker v-model="createForm.licenseExpiry" type="date" style="width:100%;" value-format="YYYY-MM-DD" placeholder="不填表示永久" />
        </el-form-item>
        <el-form-item label="维护人">
          <el-input v-model="createForm.maintainedBy" placeholder="如 dev-team" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- P1-31: SOUP 编辑大弹框（扩展字段） -->
    <el-dialog v-model="showEdit" title="编辑 SOUP 组件" width="640px">
      <el-form :model="editForm" label-width="120px">
        <el-form-item label="组件名称" required>
          <el-input v-model="editForm.componentName" />
        </el-form-item>
        <el-form-item label="版本" required>
          <el-input v-model="editForm.version" placeholder="如 3.0.5" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="editForm.supplier" />
        </el-form-item>
        <el-form-item label="许可证类型">
          <el-input v-model="editForm.licenseType" placeholder="如 Apache-2.0" />
        </el-form-item>
        <el-form-item label="风险等级" required>
          <el-select v-model="editForm.riskLevel" style="width:100%;">
            <el-option label="critical - 严重" value="critical" />
            <el-option label="high - 高" value="high" />
            <el-option label="medium - 中" value="medium" />
            <el-option label="low - 低" value="low" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="组件用途/使用说明" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="editForm.licenseExpiry" type="date" style="width:100%;" value-format="YYYY-MM-DD" placeholder="不填表示永久" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEdit = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- SOUP 详情 -->
    <el-dialog v-model="showDetail" :title="`SOUP 详情 - ${detail.componentName || ''}`" width="640px">
      <el-descriptions :column="2" border size="small" v-if="detail.id">
        <el-descriptions-item label="组件名称">{{ detail.componentName }}</el-descriptions-item>
        <el-descriptions-item label="组件代号">{{ detail.componentCode }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ detail.version }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ detail.supplier }}</el-descriptions-item>
        <el-descriptions-item label="供应商国家">{{ detail.supplierCountry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ detail.softwareCategory || '-' }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="getRiskTagType(detail.riskLevel)" size="small">
            {{ getRiskLabel(detail.riskLevel) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ detail.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="许可证类型">{{ detail.licenseType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="许可证到期">{{ formatDate(detail.licenseExpiry) || '永久' }}</el-descriptions-item>
        <el-descriptions-item label="安全更新">{{ formatDate(detail.lastSecurityUpdate) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维护人">{{ detail.maintainedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="遵循标准" :span="2">{{ detail.complianceStandard || '-' }}</el-descriptions-item>
        <el-descriptions-item label="使用场景" :span="2">{{ detail.usageScenario || '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证文档" :span="2">{{ detail.certificationDoc || '未提供' }}</el-descriptions-item>
        <el-descriptions-item label="安全披露" :span="2">{{ detail.securityDisclosure || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(detail.createdAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatDate(detail.updatedAt) || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showDetail = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 异常检测与风险关联（FR-1.11） -->
    <el-dialog v-model="showAnomaly" title="SOUP 异常检测（FR-1.11）" width="820px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px;">
        检测许可证过期、安全更新滞后、缺少认证等异常。点击"关联风险"可将异常自动创建为风险评估记录。
      </el-alert>
      <el-table :data="anomalyList" border size="small" v-loading="loadingAnomaly">
        <el-table-column prop="componentName" label="SOUP 组件" width="160" />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="异常数" width="80">
          <template #default="{ row }">
            <el-tag :type="row.anomalies.length > 0 ? 'danger' : 'success'">{{ row.anomalies.length }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" type="warning" @click="openLinkRisk(row)">关联风险</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-dialog v-model="showLinkRisk" title="选择目标需求并关联风险" width="480px" append-to-body>
        <el-form :model="linkForm" label-width="100px">
          <el-form-item label="目标需求">
            <el-select v-model="linkForm.requirementId" placeholder="选择项目下的需求" style="width: 100%;">
              <el-option v-for="r in requirementList" :key="r.id" :label="`${r.requirementNo} ${r.title}`" :value="r.id" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showLinkRisk = false">取消</el-button>
          <el-button type="primary" @click="confirmLinkRisk">确认关联</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const router = useRouter()

const searchKeyword = ref('')
const categoryFilter = ref('')
const riskFilter = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

const stats = ref({ total: 0, active: 0, withIssues: 0, linkedRate: 0 })

interface Soup {
  id: number
  componentName: string
  componentCode?: string
  version?: string
  supplier?: string
  supplierCountry?: string
  softwareCategory?: string
  complianceStandard?: string
  usageScenario?: string
  riskLevel?: string
  status?: string
  licenseType?: string
  licenseExpiry?: string
  lastSecurityUpdate?: string
  maintainedBy?: string
  certificationDoc?: string
  createdAt?: string
  updatedAt?: string
  linkedRequirementCount?: number
  knownAnomalies?: number
}

const soupList = ref<Soup[]>([])
const anomalyByComponentId = ref<Record<number, number>>({})
const linkCountByComponentId = ref<Record<number, number>>({})

const filteredSoupList = computed(() => {
  const kw = searchKeyword.value.toLowerCase()
  return soupList.value.filter(item => {
    if (kw && !(item.componentName || '').toLowerCase().includes(kw) && !(item.supplier || '').toLowerCase().includes(kw)) {
      return false
    }
    if (categoryFilter.value && item.softwareCategory !== categoryFilter.value) return false
    // P1-29: 大小写不敏感比较
    if (riskFilter.value && (item.riskLevel || '').toLowerCase() !== riskFilter.value.toLowerCase()) return false
    if (statusFilter.value && item.status !== statusFilter.value) return false
    return true
  })
})

const formatDate = (v?: string | null) => {
  if (!v) return ''
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return ''
}

const fetchSoups = async () => {
  loading.value = true
  try {
    const params: any = {}
    if (statusFilter.value) params.status = statusFilter.value
    if (riskFilter.value) params.riskLevel = riskFilter.value.toUpperCase()
    const res = await request.get('/requirement/soup-components', { params })
    soupList.value = (res.data?.data || []) as Soup[]
    total.value = soupList.value.length
    await Promise.all([fetchAnomalies(), fetchLinkedCounts()])
    computeStats()
  } catch (e: any) {
    ElMessage.error('加载 SOUP 失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const fetchAnomalies = async () => {
  try {
    const res = await request.get('/requirement/soup-components/anomalies/all')
    const list = (res.data?.data || []) as any[]
    const map: Record<number, number> = {}
    for (const it of list) {
      map[it.componentId] = (it.anomalies || []).length
    }
    anomalyByComponentId.value = map
    for (const s of soupList.value) s.knownAnomalies = map[s.id] || 0
  } catch {}
}

const fetchLinkedCounts = async () => {
  try {
    const res = await request.get('/requirements', { params: { size: 1000 } })
    const data = res.data?.data
    const list: any[] = Array.isArray(data) ? data : (data?.records || [])
    const map: Record<number, number> = {}
    for (const r of list) {
      if (r.soupComponentId) {
        map[r.soupComponentId] = (map[r.soupComponentId] || 0) + 1
      }
    }
    linkCountByComponentId.value = map
    for (const s of soupList.value) s.linkedRequirementCount = map[s.id] || 0
  } catch {}
}

const computeStats = () => {
  const all = soupList.value
  const total = all.length
  const active = all.filter(s => s.status === 'ACTIVE').length
  const withIssues = all.filter(s => (s.knownAnomalies || 0) > 0).length
  const linked = all.filter(s => (s.linkedRequirementCount || 0) > 0).length
  stats.value = {
    total,
    active,
    withIssues,
    linkedRate: total > 0 ? Math.round((linked / total) * 100) : 0
  }
}

// P1-29: 风险等级大小写归一（后端可能返回 critical/high/medium/low 大小写混合）
// WHY: 内部值保持小写以匹配后端契约；显示用统一标签
const RISK_LABELS: Record<string, string> = {
  critical: '严重', high: '高', medium: '中', low: '低',
  CRITICAL: '严重', HIGH: '高', MEDIUM: '中', LOW: '低'
}
const getRiskTagType = (lvl: string) => {
  const k = (lvl || '').toLowerCase()
  if (k === 'critical') return 'danger'
  if (k === 'high') return 'danger'
  if (k === 'medium') return 'warning'
  if (k === 'low') return 'info'
  return 'info'
}
const getRiskLabel = (lvl: string) => RISK_LABELS[lvl] || (lvl || '-')

const handleFilterChange = () => fetchSoups()

const handleImport = () => {
  showCreate.value = true
}

const handleExport = () => {
  if (!soupList.value.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const headers = ['组件名称', '代号', '版本', '供应商', '分类', '风险等级', '状态', '关联需求', '已知异常', '许可证到期']
  const lines = [headers.join(',')]
  soupList.value.forEach((r: any) => {
    lines.push([
      `"${(r.componentName || '').replace(/"/g, '""')}"`,
      r.componentCode || '',
      r.version || '',
      `"${(r.supplier || '').replace(/"/g, '""')}"`,
      r.softwareCategory || '',
      r.riskLevel || '',
      r.status || '',
      r.linkedRequirementCount || 0,
      r.knownAnomalies || 0,
      formatDate(r.licenseExpiry) || '永久'
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `SOUP组件清单-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${soupList.value.length} 条 SOUP 记录`)
}

// ===== 登记 SOUP =====
const showCreate = ref(false)
const creating = ref(false)
const createForm = ref({
  componentName: '', componentCode: '', version: '',
  supplier: '', supplierCountry: '', softwareCategory: 'LIBRARY',
  riskLevel: 'medium', licenseType: '', licenseExpiry: '', maintainedBy: ''
})

const handleCreate = () => {
  showCreate.value = true
  createForm.value = {
    componentName: '', componentCode: '', version: '',
    supplier: '', supplierCountry: '', softwareCategory: 'LIBRARY',
    riskLevel: 'medium', licenseType: '', licenseExpiry: '', maintainedBy: ''
  }
}

const submitCreate = async () => {
  if (!createForm.value.componentName || !createForm.value.componentCode || !createForm.value.version) {
    ElMessage.warning('请填写组件名称、代号、版本')
    return
  }
  creating.value = true
  try {
    const payload: any = { ...createForm.value }
    if (!payload.licenseExpiry) delete payload.licenseExpiry
    const res = await request.post('/requirement/soup-components', payload)
    ElMessage.success(`SOUP 组件已登记：${res.data?.data?.componentName || ''}`)
    showCreate.value = false
    await fetchSoups()
  } catch (e: any) {
    ElMessage.error('登记失败：' + (e?.response?.data?.message || e.message))
  } finally {
    creating.value = false
  }
}

// ===== 详情 / 编辑 / 续期 / 删除 =====
const showDetail = ref(false)
const detail = ref<any>({})
const handleView = async (row: Soup) => {
  // v1.52 P0 修复：跳转到独立 SOUP 详情页
  if (row?.id) {
    router.push(`/compliance/soup/${row.id}`)
    return
  }
  try {
    const res = await request.get(`/requirement/soup-components/${row.id}`)
    detail.value = res.data?.data || row
    showDetail.value = true
  } catch (e: any) {
    detail.value = row
    showDetail.value = true
  }
}

const goRegulations = () => {
  router.push('/compliance/regulations')
}

// P1-31: SOUP 编辑弹框（扩展字段）
const showEdit = ref(false)
const saving = ref(false)
const editForm = ref({
  id: 0,
  componentName: '',
  version: '',
  supplier: '',
  licenseType: '',
  riskLevel: 'medium',
  description: '',
  licenseExpiry: ''
})
const handleEdit = (row: Soup) => {
  // WHY: 之前仅能改 version；现按要求扩展到 7 字段
  editForm.value = {
    id: row.id,
    componentName: row.componentName || '',
    version: row.version || '',
    supplier: row.supplier || '',
    licenseType: row.licenseType || '',
    riskLevel: (row.riskLevel || 'medium').toLowerCase(),
    description: row.description || (row as any).usageScenario || '',
    licenseExpiry: row.licenseExpiry || ''
  }
  showEdit.value = true
}
const submitEdit = async () => {
  if (!editForm.value.componentName || !editForm.value.version) {
    ElMessage.warning('请填写组件名称和版本')
    return
  }
  saving.value = true
  try {
    const payload: any = { ...editForm.value }
    if (!payload.licenseExpiry) delete payload.licenseExpiry
    delete payload.id
    await request.put(`/requirement/soup-components/${editForm.value.id}`, payload)
    ElMessage.success(`已更新 ${editForm.value.componentName}`)
    showEdit.value = false
    await fetchSoups()
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

const handleRenew = async (row: Soup) => {
  try {
    await ElMessageBox.confirm(`将把 ${row.componentName} 的许可证续期 1 年，确定吗？`, '续期许可证', {
      type: 'info', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await request.post(`/requirement/soup-components/${row.id}/renew`)
    ElMessage.success(`${row.componentName} 许可证已续期至 +1 年`)
    await fetchSoups()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('续期失败：' + (e?.response?.data?.message || e.message))
  }
}

const handleDelete = async (row: Soup) => {
  try {
    await ElMessageBox.confirm(`将删除（逻辑删除）${row.componentName}，确定吗？`, '删除 SOUP', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await request.delete(`/requirement/soup-components/${row.id}`)
    ElMessage.success(`${row.componentName} 已删除`)
    await fetchSoups()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.response?.data?.message || e.message))
  }
}

// ===== FR-1.11 SOUP 异常检测 + 风险关联 =====
const showAnomaly = ref(false)
const showLinkRisk = ref(false)
const loadingAnomaly = ref(false)
const anomalyList = ref<any[]>([])
const requirementList = ref<any[]>([])
const linkForm = ref({ componentId: null as number | null, requirementId: null as number | null })
const currentProjectId = ref<number | null>(null)

const openAnomalyDialog = async () => {
  showAnomaly.value = true
  loadingAnomaly.value = true
  try {
    const res = await request.get('/requirement/soup-components/anomalies/all', { params: { projectId: currentProjectId.value } })
    anomalyList.value = res.data?.data || []
  } catch (e: any) {
    ElMessage.error('检测失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loadingAnomaly.value = false
  }
}

const openLinkRisk = async (row: any) => {
  linkForm.value.componentId = row.componentId
  if (row.projectId) currentProjectId.value = row.projectId
  try {
    const res = await request.get('/requirements', { params: { projectId: row.projectId, size: 200 } })
    const data = res.data?.data
    requirementList.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {}
  showLinkRisk.value = true
}

const confirmLinkRisk = async () => {
  if (!linkForm.value.requirementId) {
    ElMessage.warning('请选择目标需求')
    return
  }
  try {
    const res = await request.post(`/requirement/soup-components/${linkForm.value.componentId}/anomalies/link-risk`, null, {
      params: { requirementId: linkForm.value.requirementId, assessedBy: 1 }
    })
    const count = (res.data?.data || []).length
    ElMessage.success(`已创建 ${count} 条风险评估`)
    showLinkRisk.value = false
    openAnomalyDialog()
  } catch (e: any) {
    ElMessage.error('关联失败：' + (e?.response?.data?.message || e.message))
  }
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
}

const handleCurrentChange = (val: number) => {
  currentPage.value = val
}

const resetFilters = () => {
  searchKeyword.value = ''
  categoryFilter.value = ''
  riskFilter.value = ''
  statusFilter.value = ''
  fetchSoups()
}

onMounted(fetchSoups)
</script>

<style scoped>
.soup-management-container {
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

.stat-card.warning .stat-icon {
  background: linear-gradient(135deg, #f093fb, #f5576c);
  color: #fff;
}

.stat-card.linked .stat-icon {
  background: linear-gradient(135deg, #4facfe, #00f2fe);
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

.soup-table {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.soup-name-cell {
  display: flex;
  flex-direction: column;
}

.soup-name {
  font-weight: 600;
  color: #303133;
}

.soup-version {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.soup-tag-open-source {
  background: #ecf5ff;
  color: #409eff;
}

.soup-tag-commercial {
  background: #fdf6ec;
  color: #e6a23c;
}

.soup-tag-proprietary {
  background: #fef0f0;
  color: #f56c6c;
}

.risk-badge {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.risk-badge.risk-critical {
  background: #fef0f0;
  color: #f56c6c;
  font-weight: 700;
}
.risk-badge.risk-high {
  background: #fef0f0;
  color: #f56c6c;
}

.risk-badge.risk-medium {
  background: #fdf6ec;
  color: #e6a23c;
}

.risk-badge.risk-low {
  background: #e1f3d8;
  color: #529b2e;
}

.linked-count {
  display: flex;
  align-items: center;
  gap: 6px;
}

.linked-icon {
  color: #67c23a;
}

.unlinked-icon {
  color: #f56c6c;
}

.anomaly-count {
  color: #f56c6c;
  font-weight: 600;
}

.no-anomaly {
  color: #67c23a;
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