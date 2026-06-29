<template>
  <div class="safety-container">
    <div class="page-header">
      <div class="page-title">🏷️ 医疗器械安全分类</div>
      <div class="header-actions">
        <el-button @click="handleExport">📤 导出分类表</el-button>
        <el-button type="primary" @click="showCreate = true">+ 添加分类规则</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="projectFilter" placeholder="选择项目" style="width: 220px" filterable @change="fetchList">
        <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-select v-model="classFilter" style="width: 140px" placeholder="分类" @change="handleFilter" clearable>
        <el-option label="全部" value="" />
        <el-option label="Class A" value="A" />
        <el-option label="Class B" value="B" />
        <el-option label="Class C" value="C" />
        <el-option label="Class D" value="D" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="stats-row">
      <div class="stat-card classA">
        <div class="stat-icon">🟢</div>
        <div class="stat-info">
          <div class="value">{{ stats.A }}</div>
          <div class="label">Class A - 低风险</div>
        </div>
      </div>
      <div class="stat-card classB">
        <div class="stat-icon">🔵</div>
        <div class="stat-info">
          <div class="value">{{ stats.B }}</div>
          <div class="label">Class B - 中低风险</div>
        </div>
      </div>
      <div class="stat-card classC">
        <div class="stat-icon">🟠</div>
        <div class="stat-info">
          <div class="value">{{ stats.C }}</div>
          <div class="label">Class C - 中高风险</div>
        </div>
      </div>
      <div class="stat-card classD">
        <div class="stat-icon">🔴</div>
        <div class="stat-info">
          <div class="value">{{ stats.D }}</div>
          <div class="label">Class D - 高风险</div>
        </div>
      </div>
    </div>

    <div class="table-card">
      <el-table :data="filteredList" border style="width: 100%" v-loading="loading">
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <span class="class-badge" :class="'class' + row.safetyClass">Class {{ row.safetyClass }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="standard" label="分类标准" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getClassStandard(row.safetyClass) }}
          </template>
        </el-table-column>
        <el-table-column prop="examples" label="典型应用" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getClassExamples(row.safetyClass) }}
          </template>
        </el-table-column>
        <el-table-column label="需求数量" width="100" align="center">
          <template #default="{ row }">
            <span style="font-weight: 600;">{{ row.reqCount || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IEC 62304 要求" width="160">
          <template #default="{ row }">
            {{ getIecReq(row.safetyClass) }}
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="复核人" width="100" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'LOCKED' ? 'success' : 'info'" size="small">
              {{ row.status === 'LOCKED' ? '已锁定' : '待复核' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleView(row)">详情</el-button>
            <el-button v-if="row.status !== 'LOCKED'" size="small" text type="success" @click="handleReview(row)">复核锁定</el-button>
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
      />
    </div>

    <!-- 添加分类 -->
    <el-dialog v-model="showCreate" title="添加安全分类" width="540px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目" required>
          <el-select v-model="form.projectId" placeholder="选择项目" style="width:100%;">
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全分类" required>
          <el-select v-model="form.safetyClass" style="width:100%;">
            <el-option label="Class A - 低风险" value="A" />
            <el-option label="Class B - 中低风险" value="B" />
            <el-option label="Class C - 中高风险" value="C" />
            <el-option label="Class D - 高风险" value="D" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类依据" required>
          <el-input v-model="form.rationale" type="textarea" :rows="3" placeholder="说明该项目的安全分类依据" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remarks" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 医疗器械软件安全分类页 (合规域独立页面 P0 修复)
 * 对应原型：compliance-safety-原型.html
 * 对应路由：/compliance/safety
 * 对应后端：SafetyClassificationController
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

interface Project { id: number; projectName: string }
interface SafetyItem {
  id: number
  projectId: number
  projectName?: string
  safetyClass: string
  rationale?: string
  remarks?: string
  reviewerId?: number
  reviewerName?: string
  status?: string
  reqCount?: number
  createdAt?: string
}

const projectList = ref<Project[]>([])
const projectFilter = ref<number | null>(null)
const classFilter = ref('')
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)

const form = ref({ projectId: null as number | null, safetyClass: 'B', rationale: '', remarks: '' })

const list = ref<SafetyItem[]>([])

const stats = ref({ A: 0, B: 0, C: 0, D: 0 })

const filteredList = computed(() => {
  return list.value.filter(s => {
    if (classFilter.value && s.safetyClass !== classFilter.value) return false
    return true
  })
})

const CLASS_STANDARD: Record<string, string> = {
  A: '低风险 - 不可能产生伤害',
  B: '中低风险 - 产生非严重伤害',
  C: '中高风险 - 产生严重伤害',
  D: '高风险 - 产生致命伤害'
}
const CLASS_EXAMPLES: Record<string, string> = {
  A: '口腔器械、绷带、非电动家具',
  B: '体温计、轮椅、注射器',
  C: '心电监护仪、输液泵、呼吸机',
  D: '植入式心脏起搏器、人工心肺机'
}
const CLASS_IEC: Record<string, string> = {
  A: 'A 级（IEC 62304）',
  B: 'B 级（IEC 62304）',
  C: 'C 级（IEC 62304）',
  D: 'C 级（IEC 62304）'
}

const getClassStandard = (cls: string) => CLASS_STANDARD[cls] || '-'
const getClassExamples = (cls: string) => CLASS_EXAMPLES[cls] || '-'
const getIecReq = (cls: string) => CLASS_IEC[cls] || '-'

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
  } catch {}
}

const fetchList = async () => {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, size: pageSize.value }
    if (projectFilter.value) params.projectId = projectFilter.value
    const res = await request.get('/compliance/safety-classification', { params })
    const d = res.data?.data
    const records: any[] = d?.records || d || []
    list.value = records.map((r: any) => ({
      ...r,
      projectName: projectList.value.find(p => p.id === r.projectId)?.projectName || `项目 ${r.projectId}`
    }))
    total.value = d?.total || records.length
    computeStats()
  } catch (e: any) {
    list.value = []
    total.value = 0
    computeStats()
  } finally {
    loading.value = false
  }
}

const computeStats = () => {
  stats.value = {
    A: list.value.filter(s => s.safetyClass === 'A').length,
    B: list.value.filter(s => s.safetyClass === 'B').length,
    C: list.value.filter(s => s.safetyClass === 'C').length,
    D: list.value.filter(s => s.safetyClass === 'D').length
  }
}

const handleFilter = () => { /* computed 自动响应 */ }
const resetFilters = () => {
  projectFilter.value = null
  classFilter.value = ''
  searchKeyword.value = ''
  fetchList()
}

const handleView = (row: SafetyItem) => {
  ElMessageBox.alert(
    `项目：${row.projectName}\n安全分类：Class ${row.safetyClass}\n分类依据：${row.rationale || '-'}\n备注：${row.remarks || '-'}\n复核人：${row.reviewerName || '未复核'}\n状态：${row.status || '-'}`,
    '安全分类详情',
    { confirmButtonText: '关闭' }
  )
}

const handleReview = async (row: SafetyItem) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入复核人 ID（必须与创建人不同）', `复核锁定 Class ${row.safetyClass}`, {
      inputType: 'number',
      inputPlaceholder: '如 2',
      confirmButtonText: '锁定',
      cancelButtonText: '取消'
    })
    const reviewerId = Number(value)
    if (!reviewerId) {
      ElMessage.warning('请输入有效的复核人 ID')
      return
    }
    await request.post(`/compliance/safety-classification/${row.id}/review`, null, { params: { reviewerId } })
    ElMessage.success('复核锁定成功')
    await fetchList()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('复核失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const submitCreate = async () => {
  if (!form.value.projectId || !form.value.rationale) {
    ElMessage.warning('请填写项目和分类依据')
    return
  }
  saving.value = true
  try {
    await request.post('/compliance/safety-classification', {
      projectId: form.value.projectId,
      safetyClass: form.value.safetyClass,
      rationale: form.value.rationale,
      remarks: form.value.remarks
    })
    ElMessage.success('安全分类已添加')
    showCreate.value = false
    form.value = { projectId: null, safetyClass: 'B', rationale: '', remarks: '' }
    await fetchList()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

const handleExport = () => {
  if (!list.value.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const headers = ['项目', '安全分类', '分类标准', '典型应用', '需求数量', '复核人', '状态']
  const lines = [headers.join(',')]
  list.value.forEach(r => {
    lines.push([
      `"${(r.projectName || '').replace(/"/g, '""')}"`,
      `Class ${r.safetyClass}`,
      `"${getClassStandard(r.safetyClass)}"`,
      `"${getClassExamples(r.safetyClass)}"`,
      r.reqCount || 0,
      r.reviewerName || '-',
      r.status || '-'
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `安全分类-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${list.value.length} 条`)
}

onMounted(async () => {
  await fetchProjects()
  await fetchList()
})
</script>

<style scoped>
.safety-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; }
.filter-bar { background: #fff; border-radius: 8px; padding: 14px 16px; margin-bottom: 16px; display: flex; gap: 12px; align-items: center; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.stat-card { background: #fff; border-radius: 8px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,.06); display: flex; align-items: center; gap: 16px; }
.stat-icon { width: 48px; height: 48px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.stat-card.classA .stat-icon { background: linear-gradient(135deg, #e1f3d8, #c8e6b8); }
.stat-card.classB .stat-icon { background: linear-gradient(135deg, #ecf5ff, #c6e2ff); }
.stat-card.classC .stat-icon { background: linear-gradient(135deg, #fdf6ec, #faecd8); }
.stat-card.classD .stat-icon { background: linear-gradient(135deg, #fef0f0, #fdd); }
.stat-info .value { font-size: 26px; font-weight: 700; color: #303133; }
.stat-info .label { font-size: 13px; color: #909399; margin-top: 4px; }
.table-card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.class-badge { padding: 4px 12px; border-radius: 4px; font-size: 12px; font-weight: 700; }
.class-badge.classA { background: #e1f3d8; color: #529b2e; }
.class-badge.classB { background: #ecf5ff; color: #409eff; }
.class-badge.classC { background: #fdf6ec; color: #e6a23c; }
.class-badge.classD { background: #fef0f0; color: #f56c6c; }
.pagination-footer { padding: 16px; display: flex; justify-content: space-between; align-items: center; margin-top: 16px; }
.page-info { font-size: 13px; color: #909399; }
</style>
