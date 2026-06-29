<template>
  <div class="soup-detail-container">
    <div class="page-header">
      <div class="page-title-group">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/soup' }">SOUP 管理</el-breadcrumb-item>
          <el-breadcrumb-item>组件详情</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">📦 {{ soup.componentName || 'SOUP 组件详情' }}</div>
        <div class="page-subtitle">第三方开源/商业组件信息，用于 IEC 62304 合规性追溯</div>
      </div>
      <div class="header-actions">
        <el-button @click="goReview">🛡 审查记录</el-button>
        <el-button type="primary" @click="handleEdit">✏️ 编辑</el-button>
      </div>
    </div>

    <!-- 基本信息卡片 -->
    <el-card v-loading="loading" class="info-card">
      <template #header>
        <div class="card-header-row">
          <span>📋 基本信息</span>
          <el-tag v-if="soup.softwareCategory" :class="getTypeTagClass(soup.softwareCategory)" size="small">
            {{ getTypeLabel(soup.softwareCategory) }}
          </el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="组件名称">{{ soup.componentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ soup.supplier || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ soup.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="组件代号">{{ soup.componentCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商国家">{{ soup.supplierCountry || '-' }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag v-if="soup.riskLevel" :type="getRiskType(soup.riskLevel)" size="small">{{ getRiskLabel(soup.riskLevel) }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="许可证类型">{{ soup.licenseType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="许可证到期">{{ formatDate(soup.licenseExpiry) || '永久' }}</el-descriptions-item>
        <el-descriptions-item label="使用状态">
          <el-tag :type="soup.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ soup.status === 'ACTIVE' ? '使用中' : (soup.status === 'DEPRECATED' ? '已停用' : (soup.status || '-')) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="安全更新">{{ formatDate(soup.lastSecurityUpdate) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维护人">{{ soup.maintainedBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(soup.createdAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="遵循标准" :span="3">{{ soup.complianceStandard || '-' }}</el-descriptions-item>
        <el-descriptions-item label="使用场景" :span="3">{{ soup.usageScenario || '-' }}</el-descriptions-item>
        <el-descriptions-item label="认证文档" :span="3">{{ soup.certificationDoc || '未提供' }}</el-descriptions-item>
        <el-descriptions-item label="安全披露" :span="3">{{ soup.securityDisclosure || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 已知异常 -->
    <el-card class="info-card">
      <template #header>
        <div class="card-header-row">
          <span>⚠️ 已知异常（FR-1.11 SOUP 异常检测）</span>
          <el-button size="small" @click="fetchAnomalies" :loading="loadingAnomaly">刷新</el-button>
        </div>
      </template>
      <el-table :data="anomalies" border size="small" v-loading="loadingAnomaly" empty-text="无已知异常">
        <el-table-column prop="type" label="异常类型" width="160" />
        <el-table-column prop="severity" label="严重度" width="100">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'HIGH' ? 'danger' : (row.severity === 'MEDIUM' ? 'warning' : 'info')" size="small">
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="异常描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="detectedAt" label="检测时间" width="160">
          <template #default="{ row }">
            {{ formatDate(row.detectedAt) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 操作按钮 -->
    <div class="footer-actions">
      <el-button @click="$router.back()">← 返回列表</el-button>
      <el-button type="warning" @click="handleRenew">📅 许可证续期</el-button>
      <el-button type="danger" @click="handleDelete">删除组件</el-button>
    </div>

    <!-- P1-31: SOUP 编辑弹框（详情页内同样支持扩展字段编辑） -->
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
  </div>
</template>

<script setup lang="ts">
/**
 * SOUP 组件详情页 (合规域独立页面 P0 修复)
 * 对应原型：soup-detail-原型.html
 * 对应路由：/compliance/soup/:id
 * 对应后端：SoupController.getById / getAnomalies
 */
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadingAnomaly = ref(false)
const soup = ref<any>({})
const anomalies = ref<any[]>([])

const formatDate = (v?: string | null) => {
  if (!v) return ''
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return ''
}

const getRiskType = (level: string) => {
  const k = (level || '').toLowerCase()
  if (k === 'critical' || k === 'high') return 'danger'
  if (k === 'medium') return 'warning'
  if (k === 'low') return 'info'
  return 'success'
}

// P1-29: 风险等级中文化（统一显示）
const RISK_LABELS: Record<string, string> = {
  critical: '严重', high: '高', medium: '中', low: '低',
  CRITICAL: '严重', HIGH: '高', MEDIUM: '中', LOW: '低'
}
const getRiskLabel = (lvl: string) => RISK_LABELS[lvl] || (lvl || '-')

// P1-31: 编辑弹框
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

const getTypeTagClass = (t: string) => {
  const k = t.toLowerCase()
  if (k === 'open-source' || k === 'opensource') return 'soup-type-open-source'
  if (k === 'commercial') return 'soup-type-commercial'
  if (k === 'proprietary') return 'soup-type-proprietary'
  return ''
}

const getTypeLabel = (t: string) => {
  const k = t.toLowerCase()
  if (k === 'open-source' || k === 'opensource') return '开源组件'
  if (k === 'commercial') return '商业组件'
  if (k === 'proprietary') return '专有组件'
  return t
}

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await request.get(`/requirement/soup-components/${id}`)
    soup.value = res.data?.data || {}
  } catch (e: any) {
    ElMessage.error('加载 SOUP 详情失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const fetchAnomalies = async () => {
  const id = route.params.id
  if (!id) return
  loadingAnomaly.value = true
  try {
    const res = await request.get(`/requirement/soup-components/${id}/anomalies`)
    anomalies.value = (res.data?.data || []) as any[]
  } catch {
    anomalies.value = []
  } finally {
    loadingAnomaly.value = false
  }
}

const goReview = () => {
  router.push(`/compliance/soup/${route.params.id}/review`)
}

const handleEdit = () => {
  // P1-31: 扩展为多字段编辑
  editForm.value = {
    id: Number(route.params.id) || 0,
    componentName: soup.value.componentName || '',
    version: soup.value.version || '',
    supplier: soup.value.supplier || '',
    licenseType: soup.value.licenseType || '',
    riskLevel: (soup.value.riskLevel || 'medium').toLowerCase(),
    description: soup.value.description || soup.value.usageScenario || '',
    licenseExpiry: soup.value.licenseExpiry || ''
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
    await request.put(`/requirement/soup-components/${route.params.id}`, payload)
    ElMessage.success('已保存')
    showEdit.value = false
    await fetchDetail()
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    saving.value = false
  }
}

const handleRenew = async () => {
  try {
    await ElMessageBox.confirm(`将把 ${soup.value.componentName} 的许可证续期 1 年，确定吗？`, '续期许可证', {
      type: 'info', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await request.post(`/requirement/soup-components/${route.params.id}/renew`)
    ElMessage.success('许可证已续期')
    await fetchDetail()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('续期失败：' + (e?.response?.data?.message || e?.message))
  }
}

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`将删除（逻辑删除）${soup.value.componentName}，确定吗？`, '删除 SOUP', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await request.delete(`/requirement/soup-components/${route.params.id}`)
    ElMessage.success('已删除')
    router.push('/compliance/soup')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message))
  }
}

onMounted(async () => {
  await fetchDetail()
  await fetchAnomalies()
})
</script>

<style scoped>
.soup-detail-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
.page-title-group { flex: 1; }
.page-title { font-size: 22px; font-weight: 700; color: #303133; margin-top: 8px; margin-bottom: 4px; }
.page-subtitle { font-size: 14px; color: #909399; }
.header-actions { display: flex; gap: 10px; }
.info-card { margin-bottom: 16px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.soup-type-open-source { background: #ecf5ff; color: #409eff; }
.soup-type-commercial { background: #fef0f0; color: #e6a23c; }
.soup-type-proprietary { background: #f4f4f5; color: #909399; }
.footer-actions { display: flex; justify-content: flex-end; gap: 12px; padding-top: 8px; }
</style>
