<template>
  <div class="baseline-compare-container">
    <div class="page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/baselines' }">基线管理</el-breadcrumb-item>
          <el-breadcrumb-item>对比</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">🔁 基线对比</div>
      </div>
      <div class="header-actions">
        <el-button @click="exportDiff">📄 导出对比报告</el-button>
        <el-button type="primary" @click="$router.push('/compliance/baselines')">+ 生成为新基线</el-button>
      </div>
    </div>

    <!-- 基线选择 -->
    <el-card class="select-card">
      <el-row :gutter="16">
        <el-col :span="10">
          <el-form-item label="基线 A（较旧版本）">
            <el-select v-model="baselineAId" placeholder="选择基线 A" filterable style="width: 100%;" @change="onSelectChange">
              <el-option v-for="b in allBaselines" :key="'A-' + b.id" :label="`${b.baselineNo} ${b.baselineName}（${formatDate(b.createdAt)}）`" :value="b.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="4" style="text-align: center; line-height: 80px;">
          <span style="font-size: 20px; color: #909399;">⟷</span>
        </el-col>
        <el-col :span="10">
          <el-form-item label="基线 B（较新版本）">
            <el-select v-model="baselineBId" placeholder="选择基线 B" filterable style="width: 100%;" @change="onSelectChange">
              <el-option v-for="b in allBaselines" :key="'B-' + b.id" :label="`${b.baselineNo} ${b.baselineName}（${formatDate(b.createdAt)}）`" :value="b.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-card>

    <!-- 统计汇总 -->
    <el-card v-if="hasResult" class="stats-card">
      <div class="stats-bar">
        <div class="stat-item">新增：<strong class="green">{{ diffSummary.added }}</strong> 条</div>
        <div class="stat-item">删除：<strong class="red">{{ diffSummary.removed }}</strong> 条</div>
        <div class="stat-item">修改：<strong class="orange">{{ diffSummary.modified }}</strong> 条</div>
        <div class="stat-item">不变：<strong>{{ diffSummary.unchanged }}</strong> 条</div>
        <div class="stat-item">合计：<strong>{{ diffSummary.total }}</strong> 条需求</div>
      </div>
      <el-table :data="diffList" border style="width: 100%;">
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <span class="diff-tag" :class="row.diffType">{{ getDiffLabel(row.diffType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requirementNo" label="编号" width="140">
          <template #default="{ row }">
            <span class="req-code">{{ row.requirementNo || row.code }}</span>
          </template>
        </el-table-column>
        <el-table-column label="需求标题" min-width="280">
          <template #default="{ row }">
            <div class="req-title">{{ row.title }}<span v-if="row.requirementType" class="req-level" :class="(row.requirementType || '').toLowerCase()">{{ row.requirementType }}</span></div>
            <div class="diff-content">{{ row.changeNote || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="变更说明" min-width="200">
          <template #default="{ row }">
            <span :style="{ color: getDiffColor(row.diffType), fontSize: '12px' }">{{ row.diffNote }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-empty v-else-if="baselineAId && baselineBId && !loading" description="两个基线无差异" />
    <el-empty v-else description="请选择两条基线进行对比" />
  </div>
</template>

<script setup lang="ts">
/**
 * 基线对比页 (合规域独立页面 P0 修复)
 * 对应原型：baseline-compare-原型.html
 * 对应路由：/compliance/baselines/compare
 * 对应后端：BaselineController.compare
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface BaselineItem {
  id: number
  baselineNo: string
  baselineName: string
  projectId: number
  projectName?: string
  snapshotData?: string
  createdAt?: string
}

const allBaselines = ref<BaselineItem[]>([])
const baselineAId = ref<number | null>(null)
const baselineBId = ref<number | null>(null)
const diffList = ref<any[]>([])
const diffSummary = ref({ added: 0, removed: 0, modified: 0, unchanged: 0, total: 0 })
const loading = ref(false)

const hasResult = computed(() => diffList.value.length > 0)

const formatDate = (v?: string | null) => {
  if (!v) return '-'
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return '-'
}

const getDiffLabel = (t: string) => ({ added: '新增', removed: '删除', modified: '修改', unchanged: '不变' }[t] || t)
const getDiffColor = (t: string) => ({ added: '#67c23a', removed: '#f56c6c', modified: '#e6a23c', unchanged: '#909399' }[t] || '#909399')

const fetchBaselines = async () => {
  try {
    const projectsRes = await request.get('/projects', { params: { page: 0, size: 200 } })
    const pd = projectsRes.data?.data
    const projects: any[] = Array.isArray(pd) ? pd : (pd?.records || [])
    const all: BaselineItem[] = []
    for (const p of projects) {
      try {
        const res = await request.get(`/baselines/project/${p.id}`)
        const list = (res.data?.data || []) as any[]
        for (const b of list) all.push({ ...b, projectName: p.projectName })
      } catch {}
    }
    allBaselines.value = all.sort((a, b) => (a.createdAt || '').localeCompare(b.createdAt || ''))
    if (allBaselines.value.length >= 2) {
      baselineAId.value = allBaselines.value[0].id
      baselineBId.value = allBaselines.value[allBaselines.value.length - 1].id
      onSelectChange()
    } else if (allBaselines.value.length === 1) {
      baselineAId.value = allBaselines.value[0].id
    }
  } catch (e: any) {
    ElMessage.error('加载基线列表失败：' + (e?.response?.data?.message || e?.message))
  }
}

const parseSnapshot = (b?: BaselineItem) => {
  if (!b || !b.snapshotData) return []
  try {
    const arr = JSON.parse(b.snapshotData)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

const onSelectChange = async () => {
  if (!baselineAId.value || !baselineBId.value) {
    diffList.value = []
    return
  }
  loading.value = true
  try {
    // 优先使用后端 compare 接口
    const res = await request.get('/baselines/compare', {
      params: { baselineId1: baselineAId.value, baselineId2: baselineBId.value }
    })
    const d = res.data?.data
    if (d && d.summary) {
      // 后端结构
      diffSummary.value = {
        added: d.summary.addedCount || 0,
        removed: d.summary.removedCount || 0,
        modified: d.summary.modifiedCount || 0,
        unchanged: d.summary.unchangedCount || 0,
        total: d.summary.totalCount || 0
      }
      diffList.value = d.diffList || d.changes || []
    } else {
      // 本地解析快照做 diff
      localCompare()
    }
  } catch {
    localCompare()
  } finally {
    loading.value = false
  }
}

const localCompare = () => {
  const A = allBaselines.value.find(b => b.id === baselineAId.value)
  const B = allBaselines.value.find(b => b.id === baselineBId.value)
  if (!A || !B) return
  const aReqs = parseSnapshot(A)
  const bReqs = parseSnapshot(B)
  const aMap = new Map(aReqs.map((r: any) => [r.id || r.requirementNo, r]))
  const bMap = new Map(bReqs.map((r: any) => [r.id || r.requirementNo, r]))

  const diff: any[] = []
  let added = 0, removed = 0, modified = 0, unchanged = 0

  for (const [k, bR] of bMap) {
    if (!aMap.has(k)) {
      added++
      diff.push({ ...bR, diffType: 'added', diffNote: '在基线 B 中新增' })
    } else {
      const aR = aMap.get(k)
      if (JSON.stringify(aR) !== JSON.stringify(bR)) {
        modified++
        diff.push({ ...bR, diffType: 'modified', diffNote: '两基线内容不一致' })
      } else {
        unchanged++
        diff.push({ ...bR, diffType: 'unchanged', diffNote: '两个基线版本内容一致' })
      }
    }
  }
  for (const [k, aR] of aMap) {
    if (!bMap.has(k)) {
      removed++
      diff.push({ ...aR, diffType: 'removed', diffNote: '在基线 A 中存在，基线 B 已移除' })
    }
  }
  diffList.value = diff
  diffSummary.value = { added, removed, modified, unchanged, total: diff.length }
}

const exportDiff = () => {
  if (!diffList.value.length) {
    ElMessage.warning('暂无对比数据')
    return
  }
  const lines: string[] = []
  lines.push(`# 基线对比报告`)
  lines.push(`基线 A: ${allBaselines.value.find(b => b.id === baselineAId.value)?.baselineNo}`)
  lines.push(`基线 B: ${allBaselines.value.find(b => b.id === baselineBId.value)?.baselineNo}`)
  lines.push(`\n## 统计\n新增 ${diffSummary.value.added}\n删除 ${diffSummary.value.removed}\n修改 ${diffSummary.value.modified}\n不变 ${diffSummary.value.unchanged}`)
  lines.push(`\n## 明细`)
  diffList.value.forEach(d => {
    lines.push(`- [${getDiffLabel(d.diffType)}] ${d.requirementNo || d.code} ${d.title}（${d.diffNote}）`)
  })
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `baseline-compare-${Date.now()}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出对比报告')
}

onMounted(fetchBaselines)
</script>

<style scoped>
.baseline-compare-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; }
.select-card { margin-bottom: 16px; }
.stats-card { margin-bottom: 16px; }
.stats-bar { display: flex; gap: 20px; padding: 12px 20px; background: #f8fafc; border-bottom: 1px solid #ebeef5; }
.stat-item { font-size: 13px; }
.stat-item strong { font-size: 15px; font-weight: 700; }
.stat-item strong.green { color: #67c23a; }
.stat-item strong.red { color: #f56c6c; }
.stat-item strong.orange { color: #e6a23c; }
.diff-tag { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; }
.diff-tag.added { background: #dcfce7; color: #166534; }
.diff-tag.removed { background: #fee2e2; color: #991b1b; }
.diff-tag.modified { background: #fef3c7; color: #92400e; }
.diff-tag.unchanged { background: #f1f5f9; color: #475569; }
.req-code { font-weight: 700; color: #409eff; font-size: 12px; }
.req-title { font-weight: 600; margin-bottom: 4px; }
.req-level { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; font-weight: 500; margin-left: 6px; }
.req-level.urs { background: #dbeafe; color: #1e40af; }
.req-level.prs { background: #dcfce7; color: #166534; }
.req-level.srs { background: #fef3c7; color: #92400e; }
.req-level.drs { background: #fce7f3; color: #9d174d; }
.diff-content { font-size: 12px; color: #64748b; line-height: 1.5; }
</style>
