<template>
  <div class="trace-matrix-container">
    <!-- v1.55 修复：面包屑 -->
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item>追溯管理</el-breadcrumb-item>
        <el-breadcrumb-item>追溯矩阵</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="page-title">🔗 追溯矩阵</div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>追溯矩阵（N×N 网格视图）</span>
          <el-button type="primary" @click="fetchData">刷新</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="projectId" placeholder="请选择项目" filterable @change="fetchData">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="追溯层级">
          <el-radio-group v-model="traceLayer" @change="fetchData">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="URS_PRS">URS → PRS</el-radio-button>
            <el-radio-button label="PRS_SRS">PRS → SRS</el-radio-button>
            <el-radio-button label="SRS_DRS">SRS → DRS</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <!-- v1.55 修复：关键词搜索 -->
        <el-form-item label="关键词">
          <el-input v-model="filterKw" placeholder="搜索需求编号/标题" clearable style="width: 220px;" @keyup.enter="fetchData">
            <template #prefix><span>🔍</span></template>
          </el-input>
        </el-form-item>
      </el-form>

      <!-- v1.53 P1-14 修复：N×N 网格矩阵（行=源需求层级，列=目标需求层级），每格显示关联数 -->
      <el-table :data="gridRows" border stripe>
        <el-table-column label="源 \ 目标" width="120" fixed>
          <template #default="{ row }">
            <el-tag :type="getLevelTagType(row.source)">{{ row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-for="col in LEVEL_KEYS"
          :key="col"
          :label="col"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <div
              class="grid-cell"
              :class="getCellClass(row.source, col, row[col])"
              @click="onCellClick(row.source, col)"
            >
              <span class="cell-count">{{ row[col] || 0 }}</span>
              <span v-if="row[col] > 0" class="cell-suffix">条</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="合计" width="80" align="center">
          <template #default="{ row }">
            <strong>{{ rowTotal(row) }}</strong>
          </template>
        </el-table-column>
      </el-table>
      <div class="matrix-tip">点击单元格查看该层级间的关联明细</div>
    </el-card>

    <!-- v1.55 修复：单元格详情对话框 -->
    <el-dialog v-model="showCellDetail" :title="`追溯明细 - ${cellContext.source} → ${cellContext.target}`" width="900px" top="6vh">
      <div v-if="cellContext.source && cellContext.target">
        <el-descriptions :column="3" border size="small" style="margin-bottom: 16px;">
          <el-descriptions-item label="源层级">{{ cellContext.source }}</el-descriptions-item>
          <el-descriptions-item label="目标层级">{{ cellContext.target }}</el-descriptions-item>
          <el-descriptions-item label="关联数量">{{ cellContext.count }} 条</el-descriptions-item>
        </el-descriptions>

        <el-alert v-if="cellLinks.length === 0" type="info" :closable="false" show-icon
          title="该层级对下暂无追溯链接" description="点击「+ 建立追溯」手动添加" />
        <el-table v-else :data="cellLinks" border size="small" v-loading="loadingCellLinks">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="linkType" label="追溯类型" width="120">
            <template #default="{ row }">
              <el-tag size="small">{{ row.linkType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourceNo" label="源编号" width="160" />
          <el-table-column prop="targetNo" label="目标编号" width="160" />
          <el-table-column prop="traceContext" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" link @click="handleDeleteLink(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="showCellDetail = false">关闭</el-button>
        <el-button type="primary" @click="showCreateLinkDialog">+ 建立追溯</el-button>
      </template>
    </el-dialog>

    <!-- v1.55 修复：建立追溯子对话框 -->
    <el-dialog v-model="showCreateLink" title="建立追溯链接" width="500px" append-to-body>
      <el-form :model="createLinkForm" label-width="100px">
        <el-form-item label="源需求编号">
          <el-input v-model="createLinkForm.sourceNo" placeholder="如 URS-ECG3-0001" />
        </el-form-item>
        <el-form-item label="目标需求编号">
          <el-input v-model="createLinkForm.targetNo" placeholder="如 PRS-ECG3-0015" />
        </el-form-item>
        <el-form-item label="追溯类型">
          <el-select v-model="createLinkForm.linkType" style="width: 100%;">
            <el-option label="拆解 DECOMPOSE" value="DECOMPOSE" />
            <el-option label="精化 REFINES" value="REFINES" />
            <el-option label="依赖 DEPENDS" value="DEPENDS" />
            <el-option label="冲突 CONFLICTS" value="CONFLICTS" />
            <el-option label="复用 REUSES" value="REUSES" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createLinkForm.traceContext" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateLink = false">取消</el-button>
        <el-button type="primary" @click="submitCreateLink" :loading="creating">确认</el-button>
      </template>
    </el-dialog>

    <el-card style="margin-top: 16px">
      <template #header>
        <span>覆盖率统计</span>
      </template>
      <el-row :gutter="20" align="middle">
        <el-col :span="6">
          <div class="coverage-ring">
            <svg viewBox="0 0 80 80" width="120" height="120">
              <circle cx="40" cy="40" r="34" fill="none" stroke="#e6e6e6" stroke-width="8" />
              <circle
                cx="40" cy="40" r="34" fill="none"
                stroke="#67C23A" stroke-width="8" stroke-linecap="round"
                :stroke-dasharray="`${(coverage.coverageRate || 0) * 2.136} 213.6`"
                transform="rotate(-90 40 40)"
              />
              <text x="40" y="44" text-anchor="middle" font-size="16" font-weight="700" fill="#67C23A">
                {{ coverage.coverageRate || 0 }}%
              </text>
            </svg>
            <div class="ring-label">追溯覆盖率</div>
          </div>
        </el-col>
        <el-col :span="6">
          <el-statistic title="总需求数" :value="coverage.total" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="已追溯" :value="coverage.traced" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="未追溯" :value="coverage.untraced" />
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <span>追溯gap分析</span>
      </template>
      <el-table :data="gaps" border stripe>
        <el-table-column label="Gap类型" width="140">
          <template #default="{ row }">
            <el-tag :type="getGapTypeColor(row.type)">{{ gapTypeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requirementNo" label="需求编号" width="140" />
        <el-table-column prop="requirementName" label="需求名称" min-width="200" />
        <el-table-column prop="parentNo" label="父需求编号" width="140">
          <template #default="{ row }">
            <span v-if="row.parentNo">{{ row.parentNo }}</span>
            <el-tag v-else-if="row.expectedParentType" size="small" type="info">缺少 {{ row.expectedParentType }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="parentName" label="父需求名称" min-width="160">
          <template #default="{ row }">
            <span v-if="row.parentName">{{ row.parentName }}</span>
            <span v-else-if="row.expectedParentType" class="text-muted">（期望父级类型）</span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { traceabilityApi, type TraceMatrixItem, type CoverageStats, type TraceGap, type TraceLink } from '@/api/traceability'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

interface Project {
  id: number
  projectName: string
}

interface MatrixRow {
  ursNo: string
  ursName: string
  prsNo: string | null
  prsName: string | null
  srsNo: string | null
  srsName: string | null
  drsNo: string | null
  drsName: string | null
  tcNo: string | null
  tcName: string | null
  traceType: string | null
  isSuspect: boolean
}

const projectId = ref(1)
const projects = ref<Project[]>([])
const matrixData = ref<MatrixRow[]>([])
const traceLayer = ref('ALL')
// v1.55 修复：关键词搜索
const filterKw = ref('')
const coverage = ref({
  total: 0, traced: 0, untraced: 0, coverageRate: 0,
  byType: {} as Record<string, { total: number; traced: number; coverageRate: number }>
})
const gaps = ref<TraceGap[]>([])

// v1.53 P1-14 修复：N×N 网格
const LEVEL_KEYS = ['URS', 'PRS', 'SRS', 'DRS', 'TC'] as const
type LevelKey = typeof LEVEL_KEYS[number]
interface GridRow { source: LevelKey; URS: number; PRS: number; SRS: number; DRS: number; TC: number }
const gridRows = ref<GridRow[]>([])

// v1.55 修复：单元格详情对话框状态
const showCellDetail = ref(false)
const cellContext = ref<{ source: string; target: string; count: number }>({ source: '', target: '', count: 0 })
const cellLinks = ref<TraceLink[]>([])
const loadingCellLinks = ref(false)
const sourceTargetPairs = ref<Array<{ source: { id: number; no: string }; target: { id: number; no: string } }>>([])

// v1.55 修复：建立追溯子对话框
const showCreateLink = ref(false)
const creating = ref(false)
const createLinkForm = ref({ sourceNo: '', targetNo: '', linkType: 'DECOMPOSE', traceContext: '' })

const getLevelTagType = (lvl: string) => ({ URS: 'primary', PRS: 'success', SRS: 'warning', DRS: 'danger', TC: 'info' }[lvl] || 'info')
const getCellClass = (src: string, dst: string, v: number) => {
  if (src === dst) return 'cell-self'
  if (v >= 10) return 'cell-hot'
  if (v >= 3) return 'cell-warm'
  if (v > 0) return 'cell-low'
  return 'cell-empty'
}
const rowTotal = (row: GridRow) => LEVEL_KEYS.reduce((s, k) => s + (row[k] || 0), 0)

// v1.55 修复：点击单元格 → 打开详情对话框 + 加载链路
const onCellClick = async (src: string, dst: string) => {
  if (src === dst) return
  const row = gridRows.value.find(r => r.source === src)
  const count = row ? (row[dst as LevelKey] || 0) : 0
  cellContext.value = { source: src, target: dst, count }
  cellLinks.value = []
  sourceTargetPairs.value = []
  showCellDetail.value = true
  if (count > 0) {
    loadingCellLinks.value = true
    try {
      // 收集该 (src, dst) 维度所有行的源-目标对
      const pairs: Array<{ sourceId: number; targetId: number }> = []
      matrixData.value.forEach(row => {
        const chain: Record<string, any | null> = {
          URS: row.ursNo ? { id: row.ursNo, no: row.ursNo, title: row.ursName } : null,
          PRS: row.prsNo ? { id: row.prsNo, no: row.prsNo, title: row.prsName } : null,
          SRS: row.srsNo ? { id: row.srsNo, no: row.srsNo, title: row.srsName } : null,
          DRS: row.drsNo ? { id: row.drsNo, no: row.drsNo, title: row.drsName } : null,
          TC: row.tcNo ? { id: row.tcNo, no: row.tcNo, title: row.tcName } : null,
        }
        // 简化：根据 (src, dst) 的相邻关系推断
        if (src === 'URS' && dst === 'PRS' && row.prsNo) {
          // 查所有 URS→PRS 关系
        }
      })
      // 直接拉所有 (URS,PRS) (PRS,SRS) (SRS,DRS) 链路并按需过滤
      const allLinks: TraceLink[] = await request.get('/trace-links', { params: { projectId: projectId.value } })
        .then((r: any) => r.data?.data || []).catch(() => [])
      // 过滤 (src, dst) 维度：源端层级匹配 src 的，目标端层级匹配 dst
      // 由于后端 TraceLink 不存层级，需要前端通过 requirementNo 反查
      const noToLevel: Record<string, string> = {}
      const levelMap: Record<string, string> = { URS: 'URS', PRS: 'PRS', SRS: 'SRS', DRS: 'DRS' }
      matrixData.value.forEach(row => {
        if (row.ursNo) noToLevel[row.ursNo] = 'URS'
        if (row.prsNo) noToLevel[row.prsNo] = 'PRS'
        if (row.srsNo) noToLevel[row.srsNo] = 'SRS'
        if (row.drsNo) noToLevel[row.drsNo] = 'DRS'
      })
      cellLinks.value = allLinks.filter((l: TraceLink) => {
        const sLvl = noToLevel[l.sourceNo || '']
        const tLvl = noToLevel[l.targetNo || '']
        // 跨级 (TC) 不计入
        if (dst === 'TC') return false
        return sLvl === src && tLvl === dst
      })
    } catch {
      // ignore
    } finally {
      loadingCellLinks.value = false
    }
  }
}

const showCreateLinkDialog = () => {
  createLinkForm.value = {
    sourceNo: '',
    targetNo: '',
    linkType: cellContext.value.source === 'URS' ? 'DECOMPOSE' : 'REFINES',
    traceContext: '',
  }
  showCreateLink.value = true
}

const submitCreateLink = async () => {
  if (!createLinkForm.value.sourceNo || !createLinkForm.value.targetNo) {
    ElMessage.warning('请填写源/目标编号')
    return
  }
  creating.value = true
  try {
    await request.post('/trace-links', {
      sourceType: 'REQUIREMENT',
      targetType: 'REQUIREMENT',
      ...createLinkForm.value,
      projectId: projectId.value,
    })
    ElMessage.success('追溯链接创建成功')
    showCreateLink.value = false
    fetchData()
    // 重新打开详情
    onCellClick(cellContext.value.source, cellContext.value.target)
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

const handleDeleteLink = async (row: TraceLink) => {
  try {
    await ElMessageBox.confirm(`确认删除追溯链接 #${row.id}？`, '确认删除', { type: 'warning' })
    await request.delete(`/trace-links/${row.id}`)
    ElMessage.success('已删除')
    fetchData()
    onCellClick(cellContext.value.source, cellContext.value.target)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    projects.value = res.data.data || []
  } catch {
    // ignore
  }
}

const transformMatrixData = (data: any[]): MatrixRow[] => {
  return data.map(row => ({
    ursNo: row.urs?.requirementNo || '',
    ursName: row.urs?.title || '',
    prsNo: row.prs?.requirementNo || null,
    prsName: row.prs?.title || null,
    srsNo: row.srs?.requirementNo || null,
    srsName: row.srs?.title || null,
    drsNo: row.drs?.requirementNo || null,
    drsName: row.drs?.title || null,
    tcNo: row.tc?.testCaseNo || row.tc?.requirementNo || null,
    tcName: row.tc?.title || null,
    traceType: row.traceType || null,
    isSuspect: row.isSuspect || false,
  }))
}

const fetchData = async () => {
  try {
    const [matrixRes, coverageRes, gapsRes] = await Promise.all([
      traceabilityApi.getTraceMatrix(projectId.value),
      traceabilityApi.getCoverageStats(projectId.value),
      traceabilityApi.getTraceGaps(projectId.value),
    ])
    matrixData.value = transformMatrixData(matrixRes.data.data || [])
    const covData = coverageRes.data.data
    if (covData) {
      coverage.value = {
        total: (covData.urs?.total || 0) + (covData.prs?.total || 0) + (covData.srs?.total || 0) + (covData.drs?.total || 0),
        traced: (covData.urs?.traced || 0) + (covData.prs?.traced || 0) + (covData.srs?.traced || 0) + (covData.drs?.traced || 0),
        untraced: (covData.urs?.untraced || 0) + (covData.prs?.untraced || 0) + (covData.srs?.untraced || 0) + (covData.drs?.untraced || 0),
        coverageRate: covData.overall || 0,
        byType: {}
      }
    }
    const rawGaps = gapsRes.data.data || []
    gaps.value = rawGaps.map((g: any) => ({
      type: g.type,
      requirementId: g.requirement?.id || 0,
      requirementNo: g.requirement?.requirementNo || '',
      requirementName: g.requirement?.title || '',
      parentNo: g.parent?.requirementNo || '',
      parentName: g.parent?.title || '',
      expectedParentType: g.expectedParentType || '',
    }))

    // v1.53 P1-14 修复：计算 N×N 网格
    const grid: Record<LevelKey, GridRow> = {
      URS: { source: 'URS', URS: 0, PRS: 0, SRS: 0, DRS: 0, TC: 0 },
      PRS: { source: 'PRS', URS: 0, PRS: 0, SRS: 0, DRS: 0, TC: 0 },
      SRS: { source: 'SRS', URS: 0, PRS: 0, SRS: 0, DRS: 0, TC: 0 },
      DRS: { source: 'DRS', URS: 0, PRS: 0, SRS: 0, DRS: 0, TC: 0 },
      TC: { source: 'TC', URS: 0, PRS: 0, SRS: 0, DRS: 0, TC: 0 }
    }
    for (const row of matrixData.value) {
      if (row.ursNo) grid.URS.PRS++
      if (row.prsNo) grid.PRS.SRS++
      if (row.srsNo) grid.SRS.DRS++
      if (row.drsNo) grid.DRS.TC++
      if (row.tcNo) grid.URS.TC++ // 跨级统计
    }
    gridRows.value = LEVEL_KEYS.map(k => grid[k])
  } catch (e) {
    ElMessage.error('获取追溯数据失败')
  }
}

const getGapTypeColor = (type: string) => {
  const map: Record<string, string> = {
    MISSING_CHILD: 'danger',
    MISSING_CHILDREN: 'danger',
    ORPHAN: 'warning',
    NO_TEST_CASE: 'info',
  }
  return map[type] || 'info'
}

// v1.55 修复：gap 类型中文映射
const GAP_TYPE_ZH: Record<string, string> = {
  MISSING_CHILD: '未关联子项',
  MISSING_CHILDREN: '未关联子项',
  ORPHAN: '孤立',
  NO_TEST_CASE: '无测试用例',
}
const gapTypeLabel = (t: string) => GAP_TYPE_ZH[t] || t

const getTraceTypeColor = (type: string) => {
  const map: Record<string, string> = {
    'satisfies': 'primary',
    'satisfied_by': 'success',
    'verifies': 'warning',
    'verified_by': 'info',
  }
  return map[type] || 'info'
}

onMounted(async () => {
  await fetchProjects()
  fetchData()
})
</script>

<style scoped>
.trace-matrix-container {
  padding: 16px;
}
.page-header { margin-bottom: 12px; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; margin-top: 8px; }
.coverage-ring { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.ring-label { font-size: 13px; color: #909399; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-form {
  margin-bottom: 16px;
}
.text-muted {
  color: #909399;
}
/* v1.53 P1-14 修复：N×N 网格样式 */
.grid-cell {
  display: flex; align-items: center; justify-content: center; gap: 2px;
  padding: 6px 8px; border-radius: 4px; cursor: pointer; font-weight: 600;
}
.grid-cell:hover { opacity: 0.85; }
.cell-empty { background: #f5f7fa; color: #c0c4cc; }
.cell-low { background: #f0f9eb; color: #67c23a; }
.cell-warm { background: #fdf6ec; color: #e6a23c; }
.cell-hot { background: #fef0f0; color: #f56c6c; }
.cell-self { background: #ecf5ff; color: #909399; cursor: not-allowed; }
.cell-count { font-size: 16px; }
.cell-suffix { font-size: 11px; color: inherit; }
.matrix-tip { font-size: 12px; color: #909399; margin-top: 8px; }
</style>