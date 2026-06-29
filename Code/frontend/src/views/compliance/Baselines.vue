<template>
  <div class="baselines-container">
    <div class="page-header">
      <div class="page-title">基线管理</div>
      <div class="header-actions">
        <el-button @click="handleRefresh">刷新</el-button>
        <el-button @click="goCompare">🔁 基线对比</el-button>
        <el-button @click="goExport">📤 统一导出</el-button>
        <el-button type="primary" @click="handleCreate">创建基线</el-button>
      </div>
    </div>

    <el-card class="lock-flow-card">
      <div class="lock-flow-title">📜 基线签名锁定流程（21 CFR Part 11 §11.50 / §11.70）</div>
      <div class="lock-flow">
        <div class="flow-step">
          <div class="step-circle step-1">1</div>
          <div class="step-label">创建基线</div>
          <div class="step-desc">草稿状态</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="step-circle step-2">2</div>
          <div class="step-label">QA 提交签名</div>
          <div class="step-desc">电子签名 1</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="step-circle step-3">3</div>
          <div class="step-label">主管复核签名</div>
          <div class="step-desc">电子签名 2</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="step-circle step-4">4</div>
          <div class="step-label">基线锁定</div>
          <div class="step-desc">已锁定状态</div>
        </div>
        <div class="flow-arrow">→</div>
        <div class="flow-step">
          <div class="step-circle step-5">5</div>
          <div class="step-label">归档 / 解锁</div>
          <div class="step-desc">已归档 / 已解锁</div>
        </div>
      </div>
    </el-card>

    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-icon">📋</div>
        <div class="stat-info">
          <div class="value">{{ stats.total }}</div>
          <div class="label">基线总数</div>
        </div>
      </div>
      <div class="stat-card locked">
        <div class="stat-icon">🔒</div>
        <div class="stat-info">
          <div class="value">{{ stats.locked }}</div>
          <div class="label">已锁定</div>
        </div>
      </div>
      <div class="stat-card unlocked">
        <div class="stat-icon">🔓</div>
        <div class="stat-info">
          <div class="value">{{ stats.unlocked }}</div>
          <div class="label">未锁定</div>
        </div>
      </div>
      <div class="stat-card draft">
        <div class="stat-icon">📝</div>
        <div class="stat-info">
          <div class="value">{{ stats.draft }}</div>
          <div class="label">草稿</div>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索基线编号/名称" style="width: 220px" clearable @keyup.enter="handleRefresh">
        <template #prefix>
          <span>🔍</span>
        </template>
      </el-input>
      <el-select v-model="statusFilter" style="width: 130px" placeholder="基线状态" @change="handleRefresh">
        <el-option label="全部" value="" />
        <el-option label="已锁定" value="LOCKED" />
        <el-option label="草稿" value="DRAFT" />
      </el-select>
      <el-select v-model="projectFilter" style="width: 200px" placeholder="选择项目" @change="handleRefresh">
        <el-option label="全部项目" :value="''" />
        <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="baselines-table">
      <el-table :data="filteredBaselines" border style="width: 100%" v-loading="loading">
        <el-table-column prop="baselineNo" label="基线编号" width="140" />
        <el-table-column prop="baselineName" label="基线名称" min-width="200" />
        <el-table-column prop="projectName" label="关联项目" width="150" />
        <el-table-column prop="requirementCount" label="需求数量" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lockedBy" label="锁定人" width="100" />
        <el-table-column prop="lockedAt" label="锁定时间" width="160" />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="280" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleView(row)">查看</el-button>
            <el-button size="small" text type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" text type="primary" @click="handleCompare(row)">对比</el-button>
            <el-button v-if="row.status === 'DRAFT'" size="small" text type="success" @click="handleLock(row)">
              锁定
            </el-button>
            <el-button v-if="row.status === 'LOCKED'" size="small" text type="warning" @click="handleUnlock(row)">
              解锁
            </el-button>
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

    <!-- 创建基线 - P1-30 升级为多字段大弹框 -->
    <el-dialog v-model="showCreate" title="创建基线" width="720px">
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="项目" required>
          <el-select v-model="createForm.projectId" placeholder="选择项目" style="width:100%;" @change="onProjectChangeForCreate">
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="基线名称" required>
          <el-input v-model="createForm.name" placeholder="例如：心电监护仪 v3.0 需求基线 v1" />
        </el-form-item>
        <el-form-item label="基线类型" required>
          <el-select v-model="createForm.baselineType" style="width:100%;">
            <el-option label="产品基线（Product Baseline）" value="PRODUCT" />
            <el-option label="设计基线（Design Baseline）" value="DESIGN" />
            <el-option label="测试基线（Test Baseline）" value="TEST" />
          </el-select>
        </el-form-item>
        <el-form-item label="锁定方式" required>
          <el-radio-group v-model="createForm.lockType">
            <el-radio value="SOFT">软锁（可解锁变更）</el-radio>
            <el-radio value="HARD">硬锁（需双签强制变更）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="纳入需求">
          <el-select v-model="createForm.requirementIds" multiple filterable collapse-tags collapse-tags-tooltip
                     placeholder="默认全选项目下所有需求" style="width:100%;" :loading="loadingReqs">
            <el-option v-for="r in projectReqs" :key="r.id" :label="`${r.requirementNo || r.code || ''} ${r.title || r.name || ''}`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="纳入变更">
          <el-select v-model="createForm.changeIds" multiple filterable collapse-tags collapse-tags-tooltip
                     placeholder="可选择已关闭的变更请求纳入基线" style="width:100%;" :loading="loadingChanges">
            <el-option v-for="c in projectChanges" :key="c.id" :label="`${c.changeNo || c.code || ''} ${c.title || ''}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="纳入风险">
          <el-select v-model="createForm.riskIds" multiple filterable collapse-tags collapse-tags-tooltip
                     placeholder="可选择已关闭的风险纳入基线" style="width:100%;" :loading="loadingRisks">
            <el-option v-for="r in projectRisks" :key="r.id" :label="`${r.riskNo || r.code || ''} ${r.title || r.description || ''}`" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon style="margin-top: 8px;">
          创建后系统将快照所选条目至基线；硬锁需双签（Part 11 §11.200）。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const router = useRouter()

interface Project {
  id: number
  projectName: string
}

interface Baseline {
  id: number
  baselineNo: string
  baselineName: string
  projectId: number
  projectName?: string
  requirementCount?: number
  status: string
  lockedBy: string | null
  lockedAt: string | null
  createdAt: string | null
  snapshotData?: string
}

const searchKeyword = ref('')
const statusFilter = ref('')
const projectFilter = ref<number | ''>('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)
// P1-30: 基线创建表单（多字段）
const createForm = ref({
  projectId: null as number | null,
  name: '',
  baselineType: 'PRODUCT',
  lockType: 'SOFT',
  requirementIds: [] as number[],
  changeIds: [] as number[],
  riskIds: [] as number[]
})
// P1-30: 弹框内项目下的可选条目（需求/变更/风险）
const projectReqs = ref<any[]>([])
const projectChanges = ref<any[]>([])
const projectRisks = ref<any[]>([])
const loadingReqs = ref(false)
const loadingChanges = ref(false)
const loadingRisks = ref(false)

const projectList = ref<Project[]>([])
const baselines = ref<Baseline[]>([])
const stats = ref({ total: 0, locked: 0, unlocked: 0, draft: 0 })

const filteredBaselines = computed(() => {
  const kw = searchKeyword.value.toLowerCase()
  return baselines.value.filter(b => {
    if (kw && !(b.baselineNo || '').toLowerCase().includes(kw) && !(b.baselineName || '').toLowerCase().includes(kw)) {
      return false
    }
    if (statusFilter.value && b.status !== statusFilter.value) return false
    if (projectFilter.value && b.projectId !== projectFilter.value) return false
    return true
  })
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = { LOCKED: 'success', DRAFT: 'info' }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    LOCKED: '已锁定',
    Locked: '已锁定',
    DRAFT: '草稿',
    Draft: '草稿',
    RELEASED: '已发布',
    Released: '已发布',
    ARCHIVED: '已归档',
    Archived: '已归档',
    UNLOCKED: '已解锁',
    Unlocked: '已解锁'
  }
  return map[status] || status
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const data = res.data?.data
    projectList.value = Array.isArray(data) ? data : (data?.records || [])
  } catch {}
}

const computeRequirementCount = (snapshot: string | undefined) => {
  if (!snapshot) return 0
  try {
    const arr = JSON.parse(snapshot)
    return Array.isArray(arr) ? arr.length : 0
  } catch { return 0 }
}

const fetchBaselines = async () => {
  loading.value = true
  try {
    const projectIds = projectFilter.value
      ? [projectFilter.value as number]
      : projectList.value.map(p => p.id)
    const results: Baseline[] = []
    for (const pid of projectIds) {
      try {
        const res = await request.get(`/baselines/project/${pid}`)
        const list = (res.data?.data || []) as Baseline[]
        for (const b of list) {
          const proj = projectList.value.find(p => p.id === b.projectId)
          b.projectName = proj?.projectName || `项目 ${b.projectId}`
          b.requirementCount = computeRequirementCount(b.snapshotData)
          results.push(b)
        }
      } catch {}
    }
    baselines.value = results
    total.value = results.length
    stats.value = {
      total: results.length,
      locked: results.filter(b => b.status === 'LOCKED').length,
      unlocked: results.filter(b => b.status !== 'LOCKED').length,
      draft: results.filter(b => b.status === 'DRAFT').length
    }
  } catch (e: any) {
    ElMessage.error('加载基线失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => {
  fetchBaselines()
}

const handleCreate = () => {
  if (!projectList.value.length) {
    ElMessage.warning('请先加载项目')
    return
  }
  showCreate.value = true
  // P1-30: 完整重置表单
  createForm.value = {
    projectId: projectList.value[0].id,
    name: '',
    baselineType: 'PRODUCT',
    lockType: 'SOFT',
    requirementIds: [],
    changeIds: [],
    riskIds: []
  }
  // 预加载可选条目
  onProjectChangeForCreate()
}

// P1-30: 项目切换时拉取该项目的需求/变更/风险（弹框内）
const onProjectChangeForCreate = async () => {
  const pid = createForm.value.projectId
  if (!pid) return
  loadingReqs.value = true
  loadingChanges.value = true
  loadingRisks.value = true
  // 需求
  try {
    const res = await request.get('/requirements', { params: { projectId: pid, size: 500 } })
    const d = res.data?.data
    projectReqs.value = Array.isArray(d) ? d : (d?.records || [])
    // 默认全选
    createForm.value.requirementIds = projectReqs.value.map((r: any) => r.id)
  } catch { projectReqs.value = [] } finally { loadingReqs.value = false }
  // 变更（仅 CLOSED/VERIFIED 状态）
  try {
    const res = await request.get('/changes', { params: { projectId: pid, size: 500 } })
    const d = res.data?.data
    const arr: any[] = Array.isArray(d) ? d : (d?.records || [])
    projectChanges.value = arr.filter((c: any) => ['CLOSED', 'VERIFIED', 'COMPLETED'].includes((c.status || '').toUpperCase()))
  } catch { projectChanges.value = [] } finally { loadingChanges.value = false }
  // 风险
  try {
    const res = await request.get('/risks', { params: { projectId: pid, size: 500 } })
    const d = res.data?.data
    projectRisks.value = Array.isArray(d) ? d : (d?.records || [])
  } catch { projectRisks.value = [] } finally { loadingRisks.value = false }
}

const submitCreate = async () => {
  if (!createForm.value.projectId || !createForm.value.name) {
    ElMessage.warning('请选择项目并填写基线名称')
    return
  }
  if (!createForm.value.requirementIds.length) {
    ElMessage.warning('请至少选择 1 条纳入需求')
    return
  }
  creating.value = true
  try {
    // P1-30: 提交完整基线创建参数
    const res = await request.post('/baselines', {
      projectId: createForm.value.projectId,
      name: createForm.value.name,
      baselineType: createForm.value.baselineType,
      lockType: createForm.value.lockType,
      requirementIds: createForm.value.requirementIds,
      changeIds: createForm.value.changeIds,
      riskIds: createForm.value.riskIds
    })
    const created = res.data?.data
    ElMessage.success(`基线创建成功：${created?.baselineNo || ''}（${createForm.value.requirementIds.length} 需求 / ${createForm.value.changeIds.length} 变更 / ${createForm.value.riskIds.length} 风险）`)
    showCreate.value = false
    await fetchBaselines()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e.message))
  } finally {
    creating.value = false
  }
}

const goCompare = () => {
  router.push('/compliance/baselines/compare')
}

// P2-2 修复：跨域导出跳转
const goExport = () => {
  router.push({ path: '/reports/export', query: { reportType: 'BASELINE' } })
}

const handleView = (row: any) => {
  // v1.52 P0 修复：跳转到独立基线详情页
  router.push(`/compliance/baselines/${row.id}`)
}

const handleEdit = (row: any) => {
  // v1.52 P0 修复：跳转到独立基线编辑页
  router.push(`/compliance/baselines/${row.id}/edit`)
}

const handleCompare = async (row: any) => {
  // v1.52 P0 修复：跳转到独立基线对比页
  router.push('/compliance/baselines/compare')
}

const handleLock = async (row: any) => {
  try {
    const { value: formVals } = await ElMessageBox.prompt(
      '锁定需 2 个不同签署人 + 2 个不同电子签名 ID（Part 11 §11.200 双签控制）。\n请按格式输入：user1Id,signatureId1,user2Id,signatureId2',
      '锁定基线 ' + row.baselineNo,
      {
        confirmButtonText: '锁定',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '示例：1,100,2,200（user1Id=1, sig1=100, user2Id=2, sig2=200）',
        inputValidator: (val: string) => {
          if (!val) return '请输入签名参数'
          const parts = val.split(',').map(s => s.trim())
          if (parts.length !== 4) return '格式错误：需 4 个值（user1Id,sig1,user2Id,sig2）'
          if (parts[0] === parts[2]) return 'user1Id 与 user2Id 必须不同'
          if (parts[1] === parts[3]) return 'sig1 与 sig2 必须不同'
          return true
        }
      }
    )
    const [user1Id, sig1, user2Id, sig2] = formVals.split(',').map((s: string) => Number(s.trim()))
    await request.post(`/baselines/${row.id}/lock`, null, {
      params: { user1Id, signatureId1: sig1, user2Id, signatureId2: sig2 }
    })
    ElMessage.success(`基线 ${row.baselineNo} 已双签锁定`)
    await fetchBaselines()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error('锁定失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleUnlock = async (row: any) => {
  try {
    const { value: formVals } = await ElMessageBox.prompt(
      '解锁需 2 个不同签署人 + 2 个不同电子签名 ID（Part 11 §11.200 双签控制）。\n请按格式输入：user1Id,signatureId1,user2Id,signatureId2,reason（reason 可选）',
      '解锁基线 ' + row.baselineNo,
      {
        confirmButtonText: '解锁',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '示例：1,100,2,200,偏差修复需重新发布',
        inputValidator: (val: string) => {
          if (!val) return '请输入签名参数'
          const parts = val.split(',').map(s => s.trim())
          if (parts.length < 4) return '格式错误：至少 4 个值'
          if (parts[0] === parts[2]) return 'user1Id 与 user2Id 必须不同'
          if (parts[1] === parts[3]) return 'sig1 与 sig2 必须不同'
          return true
        }
      }
    )
    const parts = formVals.split(',').map((s: string) => s.trim())
    const [user1Id, sig1, user2Id, sig2] = parts
    const reason = parts.slice(4).join(',') || '基线解锁'
    await request.post(`/baselines/${row.id}/unlock`, null, {
      params: { user1Id, signatureId1: Number(sig1), user2Id, signatureId2: Number(sig2), reason }
    })
    ElMessage.success(`基线 ${row.baselineNo} 已双签解锁`)
    await fetchBaselines()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error('解锁失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleSizeChange = (val: number) => {
  pageSize.value = val
  currentPage.value = 1
}

const handleCurrentChange = () => {
  // computed 过滤会自动响应
}

const resetFilters = () => {
  searchKeyword.value = ''
  statusFilter.value = ''
  projectFilter.value = ''
  fetchBaselines()
}

onMounted(async () => {
  await fetchProjects()
  await fetchBaselines()
})
</script>

<style scoped>
.baselines-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.lock-flow-card { margin-bottom: 20px; }
.lock-flow-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 16px; }
.lock-flow { display: flex; align-items: center; justify-content: space-between; padding: 4px 12px; }
.flow-step { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; }
.step-circle { width: 38px; height: 38px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; font-size: 16px; }
.step-1 { background: #909399; }
.step-2 { background: #409EFF; }
.step-3 { background: #E6A23C; }
.step-4 { background: #67C23A; }
.step-5 { background: #909399; }
.step-label { font-size: 13px; font-weight: 600; }
.step-desc { font-size: 11px; color: #909399; }
.flow-arrow { font-size: 20px; color: #c0c4cc; padding: 0 4px; }

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

.stat-card.locked .stat-icon {
  background: linear-gradient(135deg, #11998e, #38ef7d);
  color: #fff;
}

.stat-card.unlocked .stat-icon {
  background: linear-gradient(135deg, #f093fb, #f5576c);
  color: #fff;
}

.stat-card.draft .stat-icon {
  background: linear-gradient(135deg, #e6a23c, #f5a623);
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

.baselines-table {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
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