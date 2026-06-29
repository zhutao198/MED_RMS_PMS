<template>
  <div class="trace-coverage-container">
    <!-- v1.55 修复：面包屑 -->
    <el-breadcrumb separator="/" style="margin-bottom: 8px;">
      <el-breadcrumb-item>追溯管理</el-breadcrumb-item>
      <el-breadcrumb-item>覆盖率报告</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-header">
      <div class="page-title">📊 追溯覆盖率报告</div>
      <div class="header-actions">
        <el-select v-model="selectedProject" style="width: 220px; margin-right: 10px;" @change="loadData">
          <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-date-picker v-model="reportDate" type="date" placeholder="报告日期" style="width: 140px; margin-right: 10px;" />
        <el-button type="primary" :loading="loading" @click="handleExport">导出报告</el-button>
      </div>
    </div>

    <!-- v1.53 P1-16 修复：4 tab 切换（架构层/详细设计层/代码层/单测集成层/SOUP层） -->
    <el-tabs v-model="activeTab" type="border-card" @tab-change="onTabChange" style="margin-bottom: 16px">
      <el-tab-pane label="架构层" name="ARCHITECTURE" />
      <el-tab-pane label="详细设计层" name="DETAILED_DESIGN" />
      <el-tab-pane label="代码层" name="CODE" />
      <el-tab-pane label="单测/集成层" name="TEST" />
      <el-tab-pane label="SOUP层" name="SOUP" />
    </el-tabs>

    <div class="stats-grid" v-loading="loading">
      <div class="stat-card excellent">
        <div class="stat-value">{{ overall.linkedRate }}%</div>
        <div class="stat-label">总体追溯率</div>
      </div>
      <div class="stat-card good">
        <div class="stat-value">{{ levelStats.reduce((s, l) => s + l.linkedCount, 0) }}</div>
        <div class="stat-label">已追溯总数</div>
      </div>
      <div class="stat-card warning">
        <div class="stat-value">{{ overall.unlinkedRate }}%</div>
        <div class="stat-label">未追溯率</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ overall.total }}</div>
        <div class="stat-label">需求总数</div>
      </div>
    </div>

    <div class="content-grid">
      <div class="chart-card">
        <div class="chart-title">总体追溯状态分布</div>
        <div class="chart-container">
          <div class="donut-chart">
            <!-- P2-7 v1.53 修复：手写 SVG 圆形 + stroke-dasharray 绘制 3 段环形图（绿/橙/红）
                 圆周长 C = 2πr = 2*3.14159*15.9155 ≈ 100；stroke-dasharray 第一个值为可见段长度，第二个 100 为间隔。
                 stroke-dashoffset 控制起点：25 让首段从 12 点钟方向开始。
                 绿段：已追溯  橙段：疑似  红段：未追溯  三段顺次累加覆盖圆环 -->
            <svg viewBox="0 0 36 36" style="width: 100%; height: 100%;">
              <!-- 背景灰色圈 -->
              <circle cx="18" cy="18" r="15.9155" fill="none" stroke="#e4e7ed" stroke-width="3" />
              <!-- 第一段：已追溯（绿） -->
              <circle
                cx="18" cy="18" r="15.9155"
                fill="none" stroke="#67c23a" stroke-width="3"
                :stroke-dasharray="`${overall.linkedRate} ${100 - overall.linkedRate}`"
                stroke-dashoffset="25"
                transform="rotate(-90 18 18)"
              />
              <!-- 第二段：疑似（橙）紧接绿段 -->
              <circle
                cx="18" cy="18" r="15.9155"
                fill="none" stroke="#e6a23c" stroke-width="3"
                :stroke-dasharray="`${overall.suspectRate} ${100 - overall.suspectRate}`"
                :stroke-dashoffset="25 - overall.linkedRate"
                transform="rotate(-90 18 18)"
              />
              <!-- 第三段：未追溯（红） 100 - linked - suspect -->
              <circle
                cx="18" cy="18" r="15.9155"
                fill="none" stroke="#f56c6c" stroke-width="3"
                :stroke-dasharray="`${overall.unlinkedRate} ${100 - overall.unlinkedRate}`"
                :stroke-dashoffset="25 - overall.linkedRate - overall.suspectRate"
                transform="rotate(-90 18 18)"
              />
            </svg>
            <div class="donut-center">
              <div class="donut-percent">{{ overall.linkedRate }}%</div>
              <div class="donut-label">已追溯</div>
            </div>
          </div>
        </div>
        <div class="legend">
          <div class="legend-item"><span class="legend-dot green"></span> 已追溯 ({{ overall.linkedRate }}%)</div>
          <div class="legend-item"><span class="legend-dot orange"></span> 疑似 ({{ overall.suspectRate }}%)</div>
          <div class="legend-item"><span class="legend-dot red"></span> 未追溯 ({{ overall.unlinkedRate }}%)</div>
        </div>
      </div>

      <div class="chart-card">
        <div class="chart-title">各层级追溯率</div>
        <div class="bar-chart">
          <div class="bar-item" v-for="level in levelStats" :key="level.key">
            <div class="bar-label">{{ level.name }}</div>
            <div class="bar-track">
              <div class="bar-fill" :class="getBarClass(level.linked)" :style="{ width: level.linked + '%' }">
                {{ level.linked }}%
              </div>
            </div>
            <div class="bar-value">{{ level.total }} 条</div>
          </div>
          <div v-if="levelStats.length === 0" class="empty-bar">暂无数据</div>
        </div>
      </div>

      <div class="table-card">
        <div class="chart-title">各层级追溯详情</div>
        <el-table :data="levelStats" border size="small">
          <el-table-column prop="name" label="层级" width="100">
            <template #default="{ row }">
              <span class="level-badge" :class="row.key">{{ row.name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="total" label="需求数量" width="100" align="center" />
          <el-table-column prop="linkedCount" label="已追溯" width="100" align="center">
            <template #default="{ row }">
              <span class="count-green">{{ row.linkedCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="unlinkedCount" label="未追溯" width="100" align="center">
            <template #default="{ row }">
              <span class="count-red">{{ row.unlinkedCount }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="linked" label="追溯率" width="180">
            <template #default="{ row }">
              <div class="progress-cell">
                <el-progress :percentage="row.linked" :stroke-width="6" :show-text="false" :color="row.progressColor" style="width: 100px;" />
                <span class="progress-text">{{ row.linked }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="statusText" label="状态" width="100">
            <template #default="{ row }">
              <span class="status-dot" :class="row.statusClass"></span>
              {{ row.statusText }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template #default="{ row }">
              <el-button size="small" text type="primary" @click="viewGaps(row)">查看缺口</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="table-card iec-table">
        <div class="chart-title">IEC 62304 追溯性合规检查</div>
        <el-alert
          v-if="overall.linkedRate >= 95"
          title="满足 IEC 62304 追溯性要求（≥95%）"
          type="success"
          :closable="false"
          style="margin-bottom: 16px;"
        />
        <el-alert
          v-else
          :title="`未达到 IEC 62304 追溯性要求（当前 ${overall.linkedRate}% ≥ 需 95%）`"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px;"
        />
        <el-table :data="iecRows" border size="small">
          <el-table-column prop="system" label="软件系统/子系统" min-width="140" />
          <el-table-column prop="reqCount" label="需求数量" width="100" align="center" />
          <el-table-column prop="archDesign" label="架构设计" width="100" align="center">
            <template #default="{ row }">
              <span :class="(row.archDesign ?? 0) >= 95 ? 'count-green' : (row.archDesign ?? 0) >= 80 ? 'count-orange' : 'count-red'">
                {{ row.archDesign ?? 0 }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="detailedDesign" label="详细设计" width="100" align="center">
            <template #default="{ row }">
              <span :class="(row.detailedDesign ?? 0) >= 95 ? 'count-green' : (row.detailedDesign ?? 0) >= 80 ? 'count-orange' : 'count-red'">
                {{ row.detailedDesign ?? 0 }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="codeImpl" label="代码实现" width="100" align="center">
            <template #default="{ row }">
              <span :class="(row.codeImpl ?? 0) >= 95 ? 'count-green' : (row.codeImpl ?? 0) >= 80 ? 'count-orange' : 'count-red'">
                {{ row.codeImpl ?? 0 }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="unitTest" label="单元测试" width="100" align="center">
            <template #default="{ row }">
              <span :class="(row.unitTest ?? 0) >= 95 ? 'count-green' : (row.unitTest ?? 0) >= 80 ? 'count-orange' : 'count-red'">
                {{ row.unitTest ?? 0 }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="integration" label="集成测试" width="100" align="center">
            <template #default="{ row }">
              <span :class="(row.integration ?? 0) >= 95 ? 'count-green' : (row.integration ?? 0) >= 80 ? 'count-orange' : 'count-red'">
                {{ row.integration ?? 0 }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="soupMgmt" label="SOUP 管理" width="100" align="center">
            <template #default="{ row }">
              <span :class="(row.soupMgmt ?? 0) >= 95 ? 'count-green' : (row.soupMgmt ?? 0) >= 80 ? 'count-orange' : 'count-red'">
                {{ row.soupMgmt ?? 0 }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="overall" label="总体" width="100" align="center">
            <template #default="{ row }">
              <span :class="row.overall >= 95 ? 'count-green' : 'count-red'"><strong>{{ row.overall }}%</strong></span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { traceabilityApi } from '@/api/traceability'
import request from '@/api/request'

interface Project {
  id: number
  projectName: string
}

interface LevelStat {
  key: string
  name: string
  total: number
  linkedCount: number
  unlinkedCount: number
  linked: number
  statusText: string
  statusClass: string
  progressColor: string
  barClass: string
}

interface IecRow {
  system: string
  reqCount: number
  // v1.55 修复：IEC 表按生命周期列（与 5-tab 联动）
  archDesign?: number      // 架构设计覆盖率
  detailedDesign?: number  // 详细设计覆盖率
  codeImpl?: number        // 代码实现覆盖率
  unitTest?: number        // 单元测试覆盖率
  integration?: number     // 集成测试覆盖率
  soupMgmt?: number        // SOUP 管理覆盖率
  overall: number
}

const router = useRouter()
const loading = ref(false)
const projectList = ref<Project[]>([])
const selectedProject = ref<number | ''>(1)
const reportDate = ref<string>(new Date().toISOString().slice(0, 10))

// v1.53 P1-16 修复：tab 维度映射
const activeTab = ref<'ARCHITECTURE' | 'DETAILED_DESIGN' | 'CODE' | 'TEST' | 'SOUP'>('ARCHITECTURE')
const TAB_TO_LEVELS: Record<string, string[]> = {
  ARCHITECTURE: ['URS', 'PRS'],
  DETAILED_DESIGN: ['SRS', 'DRS'],
  CODE: ['CODE', 'MODULE'],
  TEST: ['TEST_CASE', 'INTEGRATION'],
  SOUP: ['SOUP']
}
const onTabChange = () => { loadData() }

const overall = ref({
  total: 0,
  traced: 0,
  untraced: 0,
  linkedRate: 0,
  suspectRate: 0,
  unlinkedRate: 0
})

const levelStats = ref<LevelStat[]>([])
const iecRows = ref<IecRow[]>([])
// v1.55 修复：保存首次加载时的全量 IEC 数据，tab 切换不影响
const storedIecRows = ref<IecRow[]>([])

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    projectList.value = res.data?.data || []
  } catch (e) {
    console.warn('加载项目列表失败', e)
  }
}

const getBarClass = (rate: number) => {
  if (rate >= 95) return 'linked'
  if (rate >= 80) return 'suspect'
  return 'unlinked'
}

const getStatusInfo = (rate: number) => {
  if (rate >= 95) return { text: '优秀', cls: 'excellent', color: '#67c23a' }
  if (rate >= 85) return { text: '良好', cls: 'good', color: '#67c23a' }
  if (rate >= 70) return { text: '需改进', cls: 'warning', color: '#e6a23c' }
  return { text: '需关注', cls: 'danger', color: '#f56c6c' }
}

const loadData = async () => {
  if (!selectedProject.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  loading.value = true
  try {
    const res = await traceabilityApi.getCoverageStats(selectedProject.value as number)
    const d: any = res.data?.data || {}
    const total = d.total || 0
    const traced = d.traced || 0
    const untraced = d.untraced || 0
    // P2-7 v1.53 修复：后端返回 suspect 字段（疑似追溯数），用于渲染橙色段
    const suspect = d.suspect || 0
    const safeUntraced = Math.max(total - traced - suspect, 0)
    overall.value = {
      total,
      traced,
      untraced: safeUntraced,
      linkedRate: total > 0 ? Math.round(traced * 100 / total) : 0,
      suspectRate: total > 0 ? Math.round(suspect * 100 / total) : 0,
      unlinkedRate: total > 0 ? Math.round(safeUntraced * 100 / total) : 0
    }
    const typeOrder = TAB_TO_LEVELS[activeTab.value] || ['URS', 'PRS', 'SRS', 'DRS']
    const rows: LevelStat[] = []
    typeOrder.forEach((t, i) => {
      const lv = d[t.toLowerCase()] || {}
      const lvTotal = lv.total || 0
      const lvTraced = lv.traced || 0
      const lvUntraced = lv.untraced || (lvTotal - lvTraced)
      const rate = lvTotal > 0 ? Math.round(lvTraced * 100 / lvTotal) : 0
      const status = getStatusInfo(rate)
      rows.push({
        key: t.toLowerCase(),
        name: t,
        total: lvTotal,
        linkedCount: lvTraced,
        unlinkedCount: lvUntraced,
        linked: rate,
        statusText: status.text,
        statusClass: status.cls,
        progressColor: status.color,
        barClass: getBarClass(rate)
      })
    })
    levelStats.value = rows

    const proj = projectList.value.find(p => p.id === selectedProject.value)
    // v1.55 修复：IEC 表列改为生命周期（架构设计/详细设计/代码实现/单元测试/集成测试/SOUP管理）
    // 数据来源：后端 coverage 接口若返回 lifecycle 字段则直接用，否则按 tab 维度估算
    const lifecycle = d.lifecycle || {}
    const archDesign = lifecycle.archDesign?.rate ?? rows[0]?.linked ?? 0
    const detailedDesign = lifecycle.detailedDesign?.rate ?? rows[1]?.linked ?? 0
    const codeImpl = lifecycle.codeImpl?.rate ?? rows[2]?.linked ?? 0
    const unitTest = lifecycle.unitTest?.rate ?? rows[3]?.linked ?? 0
    const integration = lifecycle.integration?.rate ?? 0
    const soupMgmt = lifecycle.soupMgmt?.rate ?? 0
    const newIecRow: IecRow = {
      system: proj?.projectName || '当前项目',
      reqCount: total,
      archDesign,
      detailedDesign,
      codeImpl,
      unitTest,
      integration,
      soupMgmt,
      overall: overall.value.linkedRate
    }
    // v1.55 修复：tab 切换时 IEC 表用首次全量数据，不被 tab 维度影响
    if (storedIecRows.value.length === 0 || storedIecRows.value[0]?.reqCount !== newIecRow.reqCount) {
      storedIecRows.value = [newIecRow]
    }
    iecRows.value = storedIecRows.value
  } catch (e: any) {
    ElMessage.error(`加载覆盖率数据失败：${e?.response?.data?.message || e?.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

const handleExport = () => {
  if (levelStats.value.length === 0) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const proj = projectList.value.find(p => p.id === selectedProject.value)
  const projectName = proj?.projectName || `项目${selectedProject.value}`
  const lines: string[] = []
  lines.push(`追溯覆盖率报告 - ${projectName} - ${reportDate.value}`)
  lines.push('')
  lines.push(`总需求数,${overall.value.total}`)
  lines.push(`已追溯,${overall.value.traced} (${overall.value.linkedRate}%)`)
  lines.push(`未追溯,${overall.value.untraced} (${overall.value.unlinkedRate}%)`)
  lines.push('')
  lines.push('层级,需求数量,已追溯,未追溯,追溯率,状态')
  levelStats.value.forEach(l => {
    lines.push(`${l.name},${l.total},${l.linkedCount},${l.unlinkedCount},${l.linked}%,${l.statusText}`)
  })
  lines.push('')
  lines.push('IEC 62304 合规检查')
  lines.push(`总体追溯率 ${overall.value.linkedRate}%,${overall.value.linkedRate >= 95 ? '✓ 满足 IEC 62304 追溯性要求（≥95%）' : '⚠ 未达到 IEC 62304 追溯性要求'}`)
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `覆盖率报告-${projectName}-${reportDate.value}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('覆盖率报告已导出')
}

const viewGaps = (row: LevelStat) => {
  router.push(`/traceability/gaps`)
}

onMounted(async () => {
  await fetchProjects()
  loadData()
})
</script>

<style scoped>
.trace-coverage-container {
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
  align-items: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.stat-card.excellent { border-left: 4px solid #67c23a; }
.stat-card.good { border-left: 4px solid #409eff; }
.stat-card.warning { border-left: 4px solid #e6a23c; }
.stat-card.danger { border-left: 4px solid #f56c6c; }

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
}

.stat-card.excellent .stat-value { color: #67c23a; }
.stat-card.good .stat-value { color: #409eff; }
.stat-card.warning .stat-value { color: #e6a23c; }
.stat-card.danger .stat-value { color: #f56c6c; }

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.chart-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  padding: 20px;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
}

.chart-container {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.donut-chart {
  position: relative;
  width: 160px;
  height: 160px;
}

.donut-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  text-align: center;
}

.donut-percent {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.donut-label {
  font-size: 12px;
  color: #909399;
}

.legend {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-top: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.legend-dot.green { background: #67c23a; }
.legend-dot.orange { background: #e6a23c; }
.legend-dot.red { background: #f56c6c; }

.bar-chart { padding: 10px 0; }

.bar-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.bar-label {
  width: 60px;
  font-size: 13px;
  color: #606266;
}

.bar-track {
  flex: 1;
  height: 24px;
  background: #f0f2f5;
  border-radius: 12px;
  overflow: hidden;
  margin: 0 12px;
}

.bar-fill {
  height: 100%;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  transition: width 0.5s;
}

.bar-fill.linked { background: linear-gradient(90deg, #67c23a, #529b2e); }
.bar-fill.suspect { background: linear-gradient(90deg, #e6a23c, #c77c24); }
.bar-fill.unlinked { background: linear-gradient(90deg, #f56c6c, #d44040); }

.bar-value {
  width: 60px;
  font-size: 13px;
  color: #909399;
  text-align: right;
}

.empty-bar {
  text-align: center;
  color: #909399;
  padding: 20px;
}

.table-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  padding: 20px;
  grid-column: 1 / -1;
}

.iec-table { margin-top: 0; }

.level-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.level-badge.urs { background: #ecf5ff; color: #409eff; }
.level-badge.prs { background: #f0f9eb; color: #67c23a; }
.level-badge.srs { background: #fdf6ec; color: #e6a23c; }
.level-badge.drs { background: #fef0f0; color: #f56c6c; }

.count-green { color: #67c23a; font-weight: 600; }
.count-orange { color: #e6a23c; font-weight: 600; }
.count-red { color: #f56c6c; font-weight: 600; }

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-text {
  font-weight: 600;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-right: 6px;
}

.status-dot.excellent { background: #67c23a; }
.status-dot.good { background: #409eff; }
.status-dot.warning { background: #e6a23c; }
.status-dot.danger { background: #f56c6c; }

.check-ok { color: #67c23a; }
.check-warn { color: #e6a23c; }
</style>
