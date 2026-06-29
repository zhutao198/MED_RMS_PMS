<template>
  <div class="requirement-list">
    <div class="page-header">
      <h2>📋 需求列表</h2>
      <div class="header-actions">
        <el-button @click="handleImport">📥 导入</el-button>
        <el-button @click="handleExport">📤 导出</el-button>
        <el-button type="primary" @click="$router.push('/requirements/create')">+ 新建需求</el-button>
      </div>
    </div>

    <!-- P1-3 修复：全量统计卡（调用 /requirements/stats，本地聚合降级） -->
    <div class="stat-cards">
      <el-card class="stat-card stat-card-total" shadow="hover">
        <div class="stat-num">{{ globalStats.total }}</div>
        <div class="stat-label">需求总数</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ globalStats.draft }}</div>
        <div class="stat-label">草稿</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ globalStats.approved }}</div>
        <div class="stat-label">已批准</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ globalStats.implemented }}</div>
        <div class="stat-label">已实现</div>
      </el-card>
      <el-card class="stat-card" shadow="hover">
        <div class="stat-num">{{ globalStats.closed }}</div>
        <div class="stat-label">已关闭</div>
      </el-card>
    </div>

    <el-card>
      <div class="filter-row">
        <!-- R91 修复：每个筛选器加"📋 全部"选项（value='' / null 表示不过滤） -->
        <el-select v-model="filters.projectId" placeholder="所属项目" clearable style="width: 160px;" @change="loadRequirements">
          <el-option key="__all__" label="📋 全部项目" value="" />
          <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-select v-model="filters.type" placeholder="需求层级" clearable style="width: 120px;" @change="loadRequirements">
          <el-option key="__all__" label="📋 全部层级" value="" />
          <el-option label="URS" value="URS" />
          <el-option label="PRS" value="PRS" />
          <el-option label="SRS" value="SRS" />
          <el-option label="DRS" value="DRS" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 150px;" @change="loadRequirements">
          <el-option key="__all__" label="📋 全部状态" value="" />
          <el-option label="草稿" value="Draft" />
          <el-option label="待拆解" value="PendingDecompose" />
          <el-option label="已拆解" value="Decomposed" />
          <el-option label="已提交" value="Submitted" />
          <el-option label="评审中" value="InReview" />
          <el-option label="已批准" value="Approved" />
          <el-option label="已驳回" value="Rejected" />
          <el-option label="已实现" value="Implemented" />
          <el-option label="已验证" value="Verified" />
          <el-option label="基线" value="Baseline" />
          <el-option label="已变更" value="Changed" />
          <el-option label="已关闭" value="Closed" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="优先级" clearable style="width: 120px;" @change="loadRequirements">
          <el-option key="__all__" label="📋 全部优先级" value="" />
          <el-option label="必须有 (MUST)" value="MUST" />
          <el-option label="应该有 (SHOULD)" value="SHOULD" />
          <el-option label="可以有 (COULD)" value="COULD" />
          <el-option label="不要 (WONT)" value="WONT" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索需求标题" style="width: 200px;" clearable @keyup.enter="loadRequirements" />
        <el-button @click="loadRequirements">搜索</el-button>
        <el-button @click="resetFilter">重置</el-button>
      </div>

      <!-- P1-2 修复：层级筛选按钮组（父级/子级/孙级/全部） -->
      <div class="level-tabs">
        <span class="level-tabs-label">层级视角：</span>
        <el-radio-group v-model="filters.levelView" size="default" @change="loadRequirements">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="parent">父级</el-radio-button>
          <el-radio-button value="child">子级</el-radio-button>
          <el-radio-button value="grand">孙级</el-radio-button>
        </el-radio-group>
        <span class="level-tabs-hint">
          （基于 parentId 关系判定：父级=无父、子级=有父、孙级=父也有父）
        </span>
      </div>

      <!-- P2-1修复：行点击触发详情 drawer 不跳页 -->
 <el-table
 :data="filteredRequirements"
 style="width:100%; margin-top:20px;"
 v-loading="loading"
 row-class-name="req-row-clickable"
 @row-click="openDetailDrawer"
 >
        <el-table-column prop="requirementNo" label="编号" width="150" />
        <el-table-column prop="requirementType" label="层级" width="80">
          <template #default="{ row }">
            <el-tag :type="getTypeColor(row.requirementType)">{{ row.requirementType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="getPriorityColor(row.priority)">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <span :class="'status-' + (row.status || '').toLowerCase()">{{ getStatusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.source" size="small" :type="getSourceColor(row.source)">{{ getSourceLabel(row.source) }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- P1-2 修复：追溯状态列（后端 traceStatus 字段，无则本地基于 upstream/downstream 数推断） -->
        <el-table-column label="追溯状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="getTraceStatusColor(row)">
              {{ getTraceStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectId" label="项目" width="120">
          <template #default="{ row }">
            {{ getProjectName(row.projectId) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row.id)">查看</el-button>
            <el-button link type="primary" @click="decompose(row)" v-if="row.status === 'Approved'">拆解</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadRequirements"
        @current-change="loadRequirements"
        style="margin-top: 20px; justify-content: flex-end;"
      />
    </el-card>

    <!-- P1-7 修复：导入弹窗独立组件 -->
    <RequirementImportDialog ref="importDialogRef" @imported="loadRequirements" />
  
 <!-- P2-1：点击行打开右侧抽屉展示摘要（不跳页） -->
 <el-drawer
 v-model="detailDrawerVisible"
 title="需求摘要"
 size="560px"
 direction="rtl"
 destroy-on-close
 >
 <div v-if="drawerReq" class="req-drawer-body">
 <div class="req-drawer-row"><span class="lbl">编号：</span><span class="val mono">{{ drawerReq.requirementNo }}</span></div>
 <div class="req-drawer-row"><span class="lbl">标题：</span><span class="val">{{ drawerReq.title }}</span></div>
 <div class="req-drawer-row">
 <span class="lbl">层级：</span>
 <el-tag size="small" :type="getTypeColor(drawerReq.requirementType)">{{ drawerReq.requirementType }}</el-tag>
 <span class="lbl" style="margin-left:16px">优先级：</span>
 <el-tag size="small" :type="getPriorityColor(drawerReq.priority)">{{ drawerReq.priority }}</el-tag>
 <span class="lbl" style="margin-left:16px">状态：</span>
 <span class="val">{{ getStatusLabel(drawerReq.status) }}</span>
 </div>
 <div class="req-drawer-row"><span class="lbl">创建时间：</span><span class="val">{{ drawerReq.createdAt }}</span></div>
 <div class="req-drawer-row"><span class="lbl">更新时间：</span><span class="val">{{ drawerReq.updatedAt || drawerReq.createdAt }}</span></div>
 <div class="req-drawer-row">
 <span class="lbl">关键描述：</span>
 <div class="val desc">{{ truncate(drawerReq.description || drawerReq.title,200) }}</div>
 </div>
 <div class="req-drawer-actions">
 <el-button type="primary" @click="goDetailFromDrawer(drawerReq)">查看完整详情</el-button>
 <el-button @click="detailDrawerVisible = false">关闭</el-button>
 </div>
 </div>
 </el-drawer>
</div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { requirementApi, type Requirement } from '../../api/requirement'
import { projectApi } from '../../api/project'
import type { Project } from '../../api/project'
import RequirementImportDialog from './components/RequirementImportDialog.vue'

const router = useRouter()
const importDialogRef = ref<InstanceType<typeof RequirementImportDialog> | null>(null)

const loading = ref(false)
const requirements = ref<Requirement[]>([])
const projectList = ref<Project[]>([])
const filters = reactive({
  projectId: null as number | null,
  type: '',
  status: '',
  priority: '',
  keyword: '',
  levelView: '' as '' | 'parent' | 'child' | 'grand',
})
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

/**
 * P1-3 全量统计
 * - 优先调用 /requirements/stats 端点
 * - 后端未实现时降级为本地聚合：取 size=1000 的全量需求按 status 分桶
 */
const globalStats = reactive({
  total: 0,
  draft: 0,
  approved: 0,
  implemented: 0,
  closed: 0,
})

const loadRequirements = async () => {
  loading.value = true
  try {
    const res = await requirementApi.list({
      projectId: filters.projectId || undefined,
      page: pagination.page - 1,
      size: pagination.size,
      type: filters.type || undefined,
      status: filters.status || undefined,
      priority: filters.priority || undefined,
      keyword: filters.keyword?.trim() || undefined
    })
    requirements.value = res.data?.data?.records || []
    pagination.total = res.data?.data?.total || 0
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
  // 拉取统计与当前列表并行
  loadGlobalStats()
}

/**
 * 拉取全量统计
 * 1) 优先尝试 GET /statistics/requirements（StatisticsController，约定返回 {total, draft, approved, implemented, closed}）
 * 2) 业务异常（HTTP 200 但 code !== '0000'）→ 视为失败，走降级
 * 3) 网络/4xx/5xx 异常 → 走降级：本地 size=1000 拉取全量按 status 分桶
 */
const loadGlobalStats = async () => {
  const axios = (await import('axios')).default
  const token = localStorage.getItem('token')
  let remoteOk = false

  // 第一步：尝试远程统计
  try {
    const res = await axios.get('/api/statistics/requirements', {
      params: { projectId: filters.projectId || undefined },
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      timeout: 8000,
    })
    // 业务异常包在 HTTP 200 里：必须校验 code，否则 SY0101 等会被静默吞掉 → 全 0
    const code = String(res.data?.code ?? '')
    if (code === '0000' || code === '200') {
      const data = res.data?.data || {}
      // 后端真实结构：data.byStatus.{Draft,Approved,Baseline,...}
      // 而非前端原假设的扁平字段 draft/approved/implemented/closed
      const byStatus = data.byStatus || {}
      const draft = byStatus.Draft ?? 0
      const approved = byStatus.Approved ?? 0
      // 后端无 Implemented/Verified/Closed key；用 InProgress 近似"已实现/在途"，Baseline/Closed 算"已关闭"
      const implemented = (byStatus.Implemented ?? 0) + (byStatus.Verified ?? 0) + (byStatus.InProgress ?? 0)
      const closed = (byStatus.Closed ?? 0) + (byStatus.Baseline ?? 0)
      Object.assign(globalStats, { total: data.total ?? 0, draft, approved, implemented, closed })
      remoteOk = true
    } else {
      console.warn('[loadGlobalStats] 业务异常，走降级:', res.data?.message || code)
    }
  } catch (e) {
    console.warn('[loadGlobalStats] 网络异常，走降级:', e?.message || e)
  }

  // 第二步：远程失败时本地聚合降级
  if (!remoteOk) {
    try {
      const res = await requirementApi.list({
        projectId: filters.projectId || undefined,
        page: 0,
        size: 1000,
      } as any)
      const all: Requirement[] = res.data?.data?.records || []
      const tally = { total: all.length, draft: 0, approved: 0, implemented: 0, closed: 0 }
      for (const r of all) {
        if (r.status === 'Draft') tally.draft++
        else if (r.status === 'Approved') tally.approved++
        else if (r.status === 'Implemented' || r.status === 'Verified') tally.implemented++
        else if (r.status === 'Closed' || r.status === 'Baseline') tally.closed++
      }
      Object.assign(globalStats, tally)
    } catch (e2) {
      console.error('[loadGlobalStats] 降级也失败，统计卡保持 0', e2)
    }
  }
}

/**
 * P1-2 修复：层级视角筛选
 * - 父级：无 parentId
 * - 子级：有 parentId 且父级无 parentId
 * - 孙级：父级也有 parentId
 * 注：依赖 parentId 字段，缺失则跳过该视角
 */
const filteredRequirements = computed(() => {
  if (!filters.levelView) return requirements.value
  return requirements.value.filter(r => {
    const pid = (r as any).parentId
    if (filters.levelView === 'parent') return !pid
    if (filters.levelView === 'child') return !!pid
    if (filters.levelView === 'grand') return !!pid && hasParentParent(r, requirements.value)
    return true
  })
})

/** 判断 r 的父级是否还有父级（即 r 是否为孙级） */
const hasParentParent = (r: any, list: Requirement[]) => {
  const parent = list.find(x => x.id === r.parentId)
  return !!(parent && (parent as any).parentId)
}

const handleImport = () => {
  if (!filters.projectId) {
    ElMessage.warning('请先选择所属项目，再导入需求')
    return
  }
  importDialogRef.value?.open(filters.projectId)
}

const handleExport = async () => {
  // v1.43 P1-2 修复：导出当前过滤条件下全部需求为 CSV（Excel 可直接打开）
  try {
    ElMessage.info('正在导出，请稍候...')
    const res = await requirementApi.list({
      projectId: filters.projectId || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
      priority: filters.priority || undefined,
      keyword: filters.keyword?.trim() || undefined,
      page: 0,
      size: 1000
    } as any)
    const list: Requirement[] = (res.data as any)?.data?.records || []
    if (list.length === 0) {
      ElMessage.warning('当前过滤条件下无数据可导出')
      return
    }
    const header = ['编号', '层级', '标题', '优先级', '状态', '来源', '风险等级', '安全等级', '创建时间']
    const lines = [header.join(',')]
    for (const r of list) {
      lines.push([
        r.requirementNo || '',
        r.requirementType || '',
        escapeCsv(r.title || ''),
        r.priority || '',
        r.status || '',
        (r as any).source || '',
        (r as any).riskLevel || '',
        (r as any).safetyClass || '',
        r.createdAt || ''
      ].join(','))
    }
    const csv = '﻿' + lines.join('\r\n') // BOM 让 Excel 识别 UTF-8
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ts = new Date().toISOString().slice(0, 10)
    a.download = `requirements_${filters.projectId || 'all'}_${ts}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${list.length} 条需求`)
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

// CSV 工具：处理引号包裹与换行转义
const escapeCsv = (s: string) => {
  if (s == null) return ''
  const str = String(s)
  if (/[",\r\n]/.test(str)) {
    return '"' + str.replace(/"/g, '""') + '"'
  }
  return str
}

const resetFilter = () => {
  filters.projectId = null
  filters.type = ''
  filters.status = ''
  filters.priority = ''
  filters.keyword = ''
  filters.levelView = ''
  loadRequirements()
}

const loadProjects = async () => {
  try {
    const res = await projectApi.list()
    projectList.value = res.data?.data || []
  } catch (e) {
    console.error(e)
  }
}

const viewDetail = (id: number | undefined) => {
  if (id) router.push(`/requirements/${id}`)
}

const decompose = (req: Requirement) => {
  router.push(`/requirements/${req.id}/decompose`)
}

const getTypeColor = (type: string) => {
  return { URS: 'primary', PRS: 'success', SRS: 'warning', DRS: 'danger' }[type] || 'info'
}

const getPriorityColor = (priority: string) => {
  return { MUST: 'danger', SHOULD: 'warning', COULD: 'info', WONT: 'info' }[priority] || 'info'
}

const statusLabels: Record<string, string> = {
  Draft: '草稿',
  PendingDecompose: '待拆解',
  Decomposed: '已拆解',
  Submitted: '已提交',
  InReview: '评审中',
  Approved: '已批准',
  Rejected: '已拒绝',
  Implemented: '已实现',
  Verified: '已验证',
  Baseline: '基线',
  Closed: '已关闭',
  ReviewApproved: '评审通过',
  ReviewRejected: '评审拒绝',
  REVIEW_APPROVED: '评审通过',
  REVIEW_REJECTED: '评审拒绝',
}

const getStatusLabel = (status: string) => statusLabels[status] || status

// P2-5：扩展为8档需求来源标签 + tag type映射
const sourceLabels: Record<string, string> = {
 USER_INTERVIEW: '用户访谈',
 REGULATION: '法规',
 COMPETITOR: '竞品',
 HISTORICAL_PROJECT: '历史',
 CUSTOMER_COMPLAINT: '投诉',
 EXPERT_REVIEW: '专家',
 SYSTEM_LOG: '日志',
 OTHER: '其他',
 //兼容历史5档枚举
 CUSTOMER: '客户',
 MARKET: '市场',
 INTERNAL: '内部',
}

const getSourceLabel = (source: string) => sourceLabels[source] || source
// P2-5:8档来源 tag颜色；保留旧值兼容
const getSourceColor = (source: string) => {
 return {
 USER_INTERVIEW: 'primary',
 REGULATION: 'danger',
 COMPETITOR: 'success',
 HISTORICAL_PROJECT: 'warning',
 CUSTOMER_COMPLAINT: 'danger',
 EXPERT_REVIEW: 'info',
 SYSTEM_LOG: 'info',
 OTHER: '',
 CUSTOMER: 'primary',
 MARKET: 'warning',
 INTERNAL: 'info',
 }[source] || 'info'
}

const getProjectName = (projectId: number | undefined) => {
  if (!projectId) return '-'
  const project = projectList.value.find(p => p.id === projectId)
  return project ? project.projectName : `项目${projectId}`
}

/**
 * P1-2 修复：追溯状态
 * - 优先取后端返回的 traceStatus 字段
 * - 缺失时本地基于 hasChildren / hasParent 推断
 *   已关联=有上游或有下游
 *   未关联=无上游且无下游
 *   部分关联=仅单边
 */
const getTraceStatusLabel = (row: any) => {
  if (row.traceStatus) return row.traceStatus
  const hasUp = !!row.parentId
  const hasDown = !!row.hasChildren || row.childCount > 0
  if (!hasUp && !hasDown) return '未关联'
  if (hasUp && hasDown) return '已关联'
  return '部分关联'
}

const getTraceStatusColor = (row: any) => {
  const label = getTraceStatusLabel(row)
  return ({ '已关联': 'success', '未关联': 'danger', '部分关联': 'warning' } as Record<string, string>)[label] || 'info'
}


/**
 * P2-1：行点击 drawer（不跳页）
 * - WHY: 用户体验 —列表查看不打断当前筛选上下文，点行直接右侧预览详情摘要
 * -摘要：标题/编号/层级/优先级/状态/创建时间/最近修改/关键描述前200 字
 * - 提供「查看完整详情」按钮跳详情页
 */
const detailDrawerVisible = ref(false)
const drawerReq = ref<Requirement | null>(null)
const openDetailDrawer = (row: Requirement) => {
 drawerReq.value = row
 detailDrawerVisible.value = true
}
const goDetailFromDrawer = (row: Requirement) => {
 if (row?.id) {
 detailDrawerVisible.value = false
 router.push()
 }
}
const truncate = (s: string, n: number) => {
 if (!s) return ''
 return s.length > n ? s.substring(0, n) + '...' : s
}

onMounted(() => {
  loadProjects()
  loadRequirements()
})
</script>

<style scoped>
.requirement-list {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
}

.filter-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* P1-2 层级筛选按钮组 */
.level-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
}
.level-tabs-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.level-tabs-hint {
  font-size: 12px;
  color: #909399;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  padding: 16px 0;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
  line-height: 1.2;
}

.stat-card-total .stat-num {
  color: #67c23a;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.status-draft { color: #909399; }
.status-pendingdecompose { color: #e6a23c; }
.status-decomposed { color: #409eff; }
.status-submitted { color: #a0cfff; }
.status-approved { color: #67c23a; }
.status-rejected { color: #f56c6c; }
.status-baseline { color: #67c23a; font-weight: 600; }

/* P2-1：行可点击 + drawer样式 */
:deep(.req-row-clickable) { cursor: pointer; }
:deep(.req-row-clickable:hover > td) { background-color: #f5f7fa !important; }
.req-drawer-body { padding:4px4px; }
.req-drawer-row { margin-bottom:14px; font-size:14px; line-height:1.6; }
.req-drawer-row .lbl { color: #909399; margin-right:6px; }
.req-drawer-row .val { color: #303133; }
.req-drawer-row .val.mono { font-family: monospace; }
.req-drawer-row .val.desc {
 background: #f5f7fa;
 border-radius:6px;
 padding:12px;
 margin-top:6px;
 white-space: pre-wrap;
 word-break: break-word;
}
.req-drawer-actions { margin-top:24px; text-align: right; }
</style>
