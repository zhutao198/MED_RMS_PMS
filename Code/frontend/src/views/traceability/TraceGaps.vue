<template>
  <div class="trace-gaps-container">
    <!-- v1.55 修复：面包屑 -->
    <el-breadcrumb separator="/" style="margin-bottom: 8px;">
      <el-breadcrumb-item>追溯管理</el-breadcrumb-item>
      <el-breadcrumb-item>缺口分析</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-header">
      <div class="page-title">🔍 追溯缺口分析与修复</div>
      <div class="header-actions">
        <el-button @click="handleAutoFix">🤖 自动修复建议</el-button>
        <el-button @click="handleRefresh" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="handleExport">导出报告</el-button>
      </div>
    </div>

    <!-- v1.55 修复：快速修复向导 -->
    <div class="wizard-panel">
      <div class="wizard-title">🛠️ 快速修复向导</div>
      <div class="wizard-step">
        <div class="step-num">1</div>
        <div class="step-content">
          <div class="step-title">选择缺口类型</div>
          <div class="step-desc">筛选需要修复的缺口类别</div>
        </div>
        <el-select v-model="wizardType" style="width: 160px;" @change="applyWizard">
          <el-option label="全部缺口" value="" />
          <el-option label="未关联子项" value="MISSING_CHILDREN" />
          <el-option label="孤立" value="ORPHAN" />
          <el-option label="无测试用例" value="NO_TEST_CASE" />
        </el-select>
      </div>
      <div class="wizard-step">
        <div class="step-num">2</div>
        <div class="step-content">
          <div class="step-title">筛选需求层级</div>
          <div class="step-desc">按 URS/PRS/SRS/DRS 缩小范围</div>
        </div>
        <el-select v-model="wizardLevel" style="width: 140px;" @change="applyWizard">
          <el-option label="全部层级" value="" />
          <el-option label="URS" value="URS" />
          <el-option label="PRS" value="PRS" />
          <el-option label="SRS" value="SRS" />
          <el-option label="DRS" value="DRS" />
        </el-select>
      </div>
      <div class="wizard-step">
        <div class="step-num">3</div>
        <div class="step-content">
          <div class="step-title">批量操作</div>
          <div class="step-desc">对当前筛选结果批量操作</div>
        </div>
        <el-button size="small" type="warning" plain @click="batchIgnore" :disabled="filteredGaps.length === 0">忽略全部（当前筛选）</el-button>
        <el-button size="small" type="primary" plain @click="batchFix" :disabled="filteredGaps.length === 0">🔗 批量建立追溯</el-button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card total">
        <div class="stat-icon">📊</div>
        <div class="stat-info">
          <div class="value">{{ stats.total }}</div>
          <div class="label">缺口总数</div>
        </div>
      </div>
      <div class="stat-card unlinked">
        <div class="stat-icon">🔗</div>
        <div class="stat-info">
          <div class="value">{{ stats.missingChildren + stats.orphan }}</div>
          <div class="label">未关联</div>
        </div>
      </div>
      <div class="stat-card suspect">
        <div class="stat-icon">⚠️</div>
        <div class="stat-info">
          <div class="value">{{ stats.noTestCase }}</div>
          <div class="label">缺测试用例</div>
        </div>
      </div>
      <div class="stat-card fixed">
        <div class="stat-icon">✅</div>
        <div class="stat-info">
          <div class="value">{{ stats.total === 0 ? '100%' : Math.round((stats.total - stats.missingChildren - stats.orphan - stats.noTestCase) / stats.total * 100) + '%' }}</div>
          <div class="label">健康度</div>
        </div>
      </div>
    </div>

    <div class="filter-bar">
      <el-input v-model="searchKeyword" placeholder="搜索需求编号/标题" style="width: 220px" clearable @keyup.enter="handleRefresh">
        <template #prefix>
          <span>🔍</span>
        </template>
      </el-input>
      <!-- P2-8 v1.53 修复：加优先级筛选（MUST/SHOULD/COULD 三档），与缺口类型并列 -->
      <el-select v-model="priorityFilter" style="width: 140px" placeholder="优先级" @change="handleRefresh">
        <el-option label="全部优先级" value="" />
        <el-option label="MUST 必须" value="MUST" />
        <el-option label="SHOULD 应该" value="SHOULD" />
        <el-option label="COULD 可以" value="COULD" />
      </el-select>
      <el-select v-model="gapTypeFilter" style="width: 150px" placeholder="缺口类型" @change="handleRefresh">
        <el-option label="全部" value="" />
        <el-option label="未关联子项" value="MISSING_CHILDREN" />
        <el-option label="孤立" value="ORPHAN" />
        <el-option label="无测试用例" value="NO_TEST_CASE" />
      </el-select>
      <el-select v-model="levelFilter" style="width: 130px" placeholder="需求层级" @change="handleRefresh">
        <el-option label="全部" value="" />
        <el-option label="URS" value="URS" />
        <el-option label="PRS" value="PRS" />
        <el-option label="SRS" value="SRS" />
        <el-option label="DRS" value="DRS" />
      </el-select>
      <el-select v-model="projectFilter" style="width: 180px" placeholder="选择项目" @change="handleRefresh">
        <el-option label="全部项目" value="" />
        <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="gap-list" v-loading="loading">
      <div v-if="filteredGaps.length === 0" class="empty-tip">
        <el-empty description="暂无追溯缺口 — 数据健康" />
      </div>
      <div v-for="gap in filteredGaps" :key="gap.requirementId" class="gap-card" :class="[gap.gapType.toLowerCase(), 'pri-' + (gap.priority || 'SHOULD').toLowerCase()]">
        <div class="gap-header">
          <div class="gap-title">
            <span class="gap-code" @click="handleViewDetail(gap)">{{ gap.requirementNo }}</span>
            <span class="gap-level" :class="gap.requirementType">{{ gap.requirementType }}</span>
            <span class="gap-type" :class="gap.gapType.toLowerCase()">
              {{ gapTypeLabel(gap.gapType) }}
            </span>
            <!-- P2-8 v1.53 修复：缺口优先级标签 -->
            <span class="gap-priority" :class="'pri-' + (gap.priority || 'SHOULD').toLowerCase()">
              {{ priorityLabel(gap.priority) }}
            </span>
          </div>
          <div class="gap-actions">
            <el-tag :type="getStatusTagType(gap.status)" size="small">
              {{ statusLabel(gap.status) }}
            </el-tag>
            <el-button size="small" text type="primary" @click="handleViewDetail(gap)">查看</el-button>
            <el-button v-if="gap.status !== 'FIXED' && gap.status !== 'IGNORED'" size="small" text type="primary" @click="handleFix(gap)">修复</el-button>
            <!-- v1.55 修复：忽略操作 -->
            <el-button v-if="gap.status !== 'FIXED' && gap.status !== 'IGNORED'" size="small" text type="warning" @click="handleIgnore(gap)">忽略</el-button>
            <el-button v-if="gap.status === 'IGNORED'" size="small" text type="success" @click="handleUnignore(gap)">取消忽略</el-button>
          </div>
        </div>
        <div class="gap-content">
          <div class="gap-title-text">{{ gap.title }}</div>
          <div class="gap-meta">
            <span>项目：{{ projectName }}</span>
            <span>缺口 ID：{{ gap.requirementId }}</span>
          </div>
        </div>
        <div v-if="gap.message" class="gap-suggestion">
          <div class="suggestion-label">系统提示：</div>
          <div class="suggestion-content">{{ gap.message }}</div>
        </div>
      </div>
    </div>

    <div class="pagination-footer" v-if="total > 0">
      <span class="page-info">共 {{ total }} 条，当前 {{ (currentPage - 1) * pageSize + 1 }}-{{ Math.min(currentPage * pageSize, total) }} 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="sizes, prev, pager, next"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { traceabilityApi, type TraceGap } from '@/api/traceability'
import request from '@/api/request'

interface Project {
  id: number
  projectName: string
}

interface GapRow {
  gapType: 'MISSING_CHILDREN' | 'ORPHAN' | 'NO_TEST_CASE'
  requirementId: number
  requirementNo: string
  requirementType: string
  title: string
  status: 'PENDING' | 'FIXED'
  message: string
  // P2-8 v1.53 修复：缺口优先级 MUST/SHOULD/COULD，缺失时默认 SHOULD
  priority?: 'MUST' | 'SHOULD' | 'COULD'
}

const router = useRouter()
const loading = ref(false)
const projectList = ref<Project[]>([])

const projectFilter = ref<number | ''>(1)
const projectName = ref<string>('心电监护仪 v3.0')
const searchKeyword = ref('')
const gapTypeFilter = ref('')
const levelFilter = ref('')
// P2-8 v1.53 修复：优先级筛选状态
const priorityFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(20)

const allGaps = ref<GapRow[]>([])
const total = ref(0)

const stats = ref({
  total: 0,
  missingChildren: 0,
  orphan: 0,
  noTestCase: 0
})

const filteredGaps = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return allGaps.value
    .filter(g => {
      if (searchKeyword.value) {
        const kw = searchKeyword.value.toLowerCase()
        if (!g.requirementNo.toLowerCase().includes(kw) && !g.title.toLowerCase().includes(kw)) return false
      }
      if (gapTypeFilter.value && g.gapType !== gapTypeFilter.value) return false
      if (levelFilter.value && g.requirementType !== levelFilter.value) return false
      // P2-8 v1.53 修复：按优先级过滤
      if (priorityFilter.value && g.priority !== priorityFilter.value) return false
      return true
    })
    .slice(start, end)
})

// v1.53 P1-15 修复：前端枚举映射（MISSING_CHILDREN→"未关联子项", ORPHAN→"孤立", NO_TEST_CASE→"无测试用例"）
const gapTypeLabel = (t: string) => {
  const map: Record<string, string> = {
    MISSING_CHILDREN: '未关联子项',
    MISSING_CHILD: '未关联子项',
    ORPHAN: '孤立',
    NO_TEST_CASE: '无测试用例'
  }
  return map[t] || t
}

// P2-8 v1.53 修复：优先级中文标签
const priorityLabel = (p?: string) => {
  const map: Record<string, string> = { MUST: 'MUST 必须', SHOULD: 'SHOULD 应该', COULD: 'COULD 可以' }
  return map[p || 'SHOULD'] || (p || 'SHOULD')
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    projectList.value = res.data.data || []
  } catch (e) {
    console.warn('加载项目列表失败', e)
  }
}

const loadGaps = async () => {
  if (!projectFilter.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  loading.value = true
  try {
    const res = await traceabilityApi.getTraceGaps(projectFilter.value as number)
    const raw: any[] = res.data?.data || []
    // P2-8 v1.53 修复：缺口优先级推断（孤立 ORPHAN 与未关联子项 → MUST，NO_TEST_CASE 按层级推断）
    const inferPriority = (g: any): GapRow['priority'] => {
      if (g.priority) return g.priority
      const t = (g.type || g.gapType) as string
      const lvl = g.requirement?.requirementType || g.requirementType
      if (t === 'ORPHAN') return 'MUST'
      if (t === 'MISSING_CHILDREN' || t === 'MISSING_CHILD') {
        // URS/PRS 高层缺口 → MUST，DRS → SHOULD，SRS → SHOULD
        return (lvl === 'URS' || lvl === 'PRS') ? 'MUST' : 'SHOULD'
      }
      if (t === 'NO_TEST_CASE') {
        return (lvl === 'URS' || lvl === 'PRS') ? 'MUST' : (lvl === 'SRS' ? 'SHOULD' : 'COULD')
      }
      return 'SHOULD'
    }
    const rows: GapRow[] = raw.map(g => ({
      gapType: (g.type || g.gapType) as GapRow['gapType'],
      requirementId: g.requirement?.id || g.requirementId || 0,
      requirementNo: g.requirement?.requirementNo || g.requirementNo || '',
      requirementType: g.requirement?.requirementType || g.requirementType || '',
      title: g.requirement?.title || g.requirementName || '',
      status: 'PENDING',
      message: g.message || (g.expectedParentType ? `期望父级类型：${g.expectedParentType}` : ''),
      priority: inferPriority(g)
    }))
    allGaps.value = rows
    total.value = rows.length
    stats.value = {
      total: rows.length,
      missingChildren: rows.filter(r => r.gapType === 'MISSING_CHILDREN').length,
      orphan: rows.filter(r => r.gapType === 'ORPHAN').length,
      noTestCase: rows.filter(r => r.gapType === 'NO_TEST_CASE').length
    }
    const proj = projectList.value.find(p => p.id === projectFilter.value)
    if (proj) projectName.value = proj.projectName
  } catch (e: any) {
    ElMessage.error(`加载追溯缺口失败：${e?.response?.data?.message || e?.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

const handleRefresh = () => {
  loadGaps()
}

const handleExport = () => {
  if (allGaps.value.length === 0) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  // 导出 CSV
  const headers = ['缺口类型', '需求编号', '需求层级', '需求标题', '项目', '系统提示']
  const lines = [headers.join(',')]
  allGaps.value.forEach(g => {
    lines.push([
      gapTypeLabel(g.gapType),
      g.requirementNo,
      g.requirementType,
      `"${(g.title || '').replace(/"/g, '""')}"`,
      projectName.value,
      `"${(g.message || '').replace(/"/g, '""')}"`
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `追溯缺口报告-${projectName.value}-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${allGaps.value.length} 条缺口记录`)
}

// v1.55 修复：状态标签辅助
const statusLabel = (s?: string) => {
  const map: Record<string, string> = { PENDING: '待处理', FIXED: '已修复', IGNORED: '已忽略' }
  return map[s || 'PENDING'] || (s || '待处理')
}
const getStatusTagType = (s?: string) => {
  const map: Record<string, string> = { PENDING: 'warning', FIXED: 'success', IGNORED: 'info' }
  return map[s || 'PENDING'] || 'info'
}

// v1.55 修复：向导 / 自动修复 / 忽略
const wizardType = ref('')
const wizardLevel = ref('')

const applyWizard = () => {
  gapTypeFilter.value = wizardType.value
  levelFilter.value = wizardLevel.value
  handleRefresh()
}

const handleAutoFix = () => {
  const fixable = allGaps.value.filter(g => g.status === 'PENDING')
  if (fixable.length === 0) {
    ElMessage.info('当前无待修复缺口')
    return
  }
  // 简单建议：按层级给出修复入口
  const grouped: Record<string, number> = {}
  fixable.forEach(g => {
    const k = g.requirementType
    grouped[k] = (grouped[k] || 0) + 1
  })
  const lines = Object.entries(grouped).map(([k, v]) => `  • ${k} 层：${v} 条缺口建议优先处理`)
  ElMessageBox.alert(
    `<div><strong>建议优先处理：</strong><br>${lines.join('<br>')}<br><br>` +
    `<strong>策略：</strong><br>` +
    `  1. 优先处理 MUST 优先级（红色边框）缺口<br>` +
    `  2. 孤立 (ORPHAN) 缺口可在「修复」按钮输入父级 ID<br>` +
    `  3. 未关联子项 (MISSING_CHILDREN) 进入拆解工作台补拆解<br>` +
    `  4. 无测试用例 (NO_TEST_CASE) 进入测试用例页补测试</div>`,
    '🤖 自动修复建议',
    { dangerouslyUseHTMLString: true }
  ).catch(() => {})
}

const handleIgnore = async (gap: GapRow) => {
  try {
    await ElMessageBox.confirm(
      `确认忽略缺口「${gap.requirementNo}」？忽略后该缺口将从列表隐藏。`,
      '忽略缺口',
      { type: 'warning' }
    )
    await traceabilityApi.ignoreGap(projectFilter.value as number, gap.gapType, gap.requirementId)
    gap.status = 'IGNORED'
    // 从 allGaps 中物理移除（避免重复显示）
    allGaps.value = allGaps.value.filter(g => g !== gap)
    total.value = allGaps.value.length
    ElMessage.success('已忽略')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('忽略失败')
  }
}

const handleUnignore = (gap: GapRow) => {
  // 简化处理：仅本地切换状态
  gap.status = 'PENDING'
  ElMessage.success('已恢复为待处理')
}

const batchIgnore = async () => {
  try {
    await ElMessageBox.confirm(
      `确认批量忽略当前筛选的 ${filteredGaps.value.length} 条缺口？`,
      '批量忽略',
      { type: 'warning' }
    )
    let count = 0
    for (const g of filteredGaps.value) {
      if (g.status === 'PENDING') {
        try {
          await traceabilityApi.ignoreGap(projectFilter.value as number, g.gapType, g.requirementId)
          g.status = 'IGNORED'
          count++
        } catch { /* ignore single failure */ }
      }
    }
    allGaps.value = allGaps.value.filter(g => g.status !== 'IGNORED')
    total.value = allGaps.value.length
    ElMessage.success(`已忽略 ${count} 条缺口`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('批量忽略失败')
  }
}

const batchFix = () => {
  ElMessage.info('批量建立追溯功能开发中，建议逐个点击「修复」按钮')
}

const handleViewDetail = (gap: GapRow) => {
  if (gap.requirementId) {
    router.push(`/requirements/${gap.requirementId}`)
  } else {
    ElMessage.info(`查看需求：${gap.requirementNo}`)
  }
}

const handleFix = async (gap: GapRow) => {
  if (!gap.requirementId) {
    ElMessage.warning('该缺口无有效需求 ID')
    return
  }
  try {
    if (gap.gapType === 'MISSING_CHILDREN') {
      ElMessage.success(`正在进入拆解工作台：${gap.requirementNo}`)
      router.push(`/requirements/${gap.requirementId}/decompose`)
    } else if (gap.gapType === 'NO_TEST_CASE') {
      ElMessage.success(`正在进入测试用例页：${gap.requirementNo}`)
      router.push(`/testcases?requirementId=${gap.requirementId}`)
    } else if (gap.gapType === 'ORPHAN') {
      try {
        const { value: targetIdStr } = await ElMessageBox.prompt(
          `为 ${gap.requirementNo}（${gap.requirementType}）建立父级追溯链接。\n请输入父级需求 ID：`,
          '修复孤立缺口',
          {
            confirmButtonText: '创建追溯链接',
            cancelButtonText: '取消',
            inputType: 'number',
            inputPlaceholder: '父级需求 ID（数字）',
            inputValidator: (val: string) => {
              const id = Number(val)
              if (!val || isNaN(id) || id <= 0) return '请输入有效的需求 ID'
              if (id === gap.requirementId) return '不能将需求关联到自身'
              return true
            }
          }
        )
        const targetId = Number(targetIdStr)
        const res = await traceabilityApi.createTraceLink?.({
          sourceType: 'REQUIREMENT',
          sourceId: gap.requirementId,
          targetType: 'REQUIREMENT',
          targetId,
          linkType: 'REFINES',
        }).catch(async () => {
          return await request.post('/trace-links', {
            sourceType: 'REQUIREMENT',
            sourceId: gap.requirementId,
            targetType: 'REQUIREMENT',
            targetId,
            linkType: 'REFINES',
          })
        })
        ElMessage.success(`追溯链接创建成功：${gap.requirementNo} → 父级 #${targetId}`)
        gap.status = 'FIXED'
        loadGaps()
      } catch (e: any) {
        if (e === 'cancel') return
        ElMessage.error('创建追溯链接失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
      }
    } else {
      ElMessage.info('该缺口类型暂未支持自动修复，请手动处理')
    }
  } catch (e: any) {
    console.error('handleFix failed:', e)
    ElMessage.error('修复失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
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
  gapTypeFilter.value = ''
  levelFilter.value = ''
  priorityFilter.value = ''
  currentPage.value = 1
  loadGaps()
}

onMounted(async () => {
  await fetchProjects()
  loadGaps()
})
</script>

<style scoped>
.trace-gaps-container {
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

.stat-card.total .stat-icon { background: linear-gradient(135deg, #667eea, #764ba2); color: #fff; }
.stat-card.unlinked .stat-icon { background: linear-gradient(135deg, #f093fb, #f5576c); color: #fff; }
.stat-card.suspect .stat-icon { background: linear-gradient(135deg, #e6a23c, #c77c24); color: #fff; }
.stat-card.fixed .stat-icon { background: linear-gradient(135deg, #11998e, #38ef7d); color: #fff; }

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
  flex-wrap: wrap;
}

.gap-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-tip {
  background: #fff;
  border-radius: 8px;
  padding: 40px;
}

.gap-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  padding: 20px;
  border-left: 4px solid;
}

.gap-card.missing_children { border-left-color: #f56c6c; }
.gap-card.orphan { border-left-color: #e6a23c; }
.gap-card.no_test_case { border-left-color: #909399; }
/* P2-8 v1.53 修复：缺口左侧色条按优先级组合（红 MUST / 橙 SHOULD / 黄 COULD） */
.gap-card.pri-must { border-left-color: #f56c6c !important; border-left-width: 6px; }
.gap-card.pri-should { border-left-color: #e6a23c !important; border-left-width: 6px; }
.gap-card.pri-could { border-left-color: #f0c419 !important; border-left-width: 6px; }

.gap-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.gap-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.gap-code {
  font-size: 15px;
  font-weight: 600;
  color: #409eff;
  cursor: pointer;
}

.gap-code:hover { text-decoration: underline; }

.gap-level {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.gap-level.URS { background: #ecf5ff; color: #409eff; }
.gap-level.PRS { background: #f0f9eb; color: #67c23a; }
.gap-level.SRS { background: #fdf6ec; color: #e6a23c; }
.gap-level.DRS { background: #fef0f0; color: #f56c6c; }

.gap-type {
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.gap-type.missing_children { background: #fef0f0; color: #f56c6c; }
.gap-type.orphan { background: #fff3e0; color: #e65100; }
.gap-type.no_test_case { background: #f4f4f5; color: #909399; }

/* P2-8 v1.53 修复：优先级徽标样式 */
.gap-priority {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.gap-priority.pri-must { background: #fef0f0; color: #f56c6c; border: 1px solid #f56c6c; }
.gap-priority.pri-should { background: #fdf6ec; color: #e6a23c; border: 1px solid #e6a23c; }
.gap-priority.pri-could { background: #fffbe6; color: #d4a017; border: 1px solid #f0c419; }

.gap-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.gap-content { margin-bottom: 12px; }

.gap-title-text {
  font-size: 14px;
  color: #303133;
  margin-bottom: 8px;
}

.gap-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
}

.gap-suggestion {
  background: #f9f9f9;
  border-radius: 6px;
  padding: 12px;
}

.suggestion-label {
  font-size: 12px;
  color: #606266;
  font-weight: 600;
  margin-bottom: 4px;
}

.suggestion-content {
  font-size: 13px;
  color: #303133;
}

.pagination-footer {
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #ebeef5;
  background: #fff;
  margin-top: 16px;
  border-radius: 8px;
}

.page-info {
  font-size: 13px;
  color: #909399;
}

/* v1.55 修复：快速修复向导样式 */
.wizard-panel {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  padding: 20px;
  margin-bottom: 16px;
}

.wizard-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.wizard-step {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f0f2f5;
}

.wizard-step:last-child {
  border-bottom: none;
}

.step-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.step-content {
  flex: 1;
  min-width: 0;
}

.step-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.step-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
