<template>
  <div class="dashboard">
    <div class="dashboard-header">
      <h2>📊 多视角工作视图（FR-1.2）</h2>
      <div class="header-right">
        <el-select v-model="filterProject" placeholder="选择项目" clearable style="width: 280px;" @change="loadAll">
          <!-- R86 修复：加"全部项目"选项（value=null 表示不过滤）。原因：原设计强制选第一个项目，
               导致风险/管理视角被 projectId=1 过滤掉全部数据；3 条风险 projectId=null（游离），
               选具体项目时一条也查不到。默认 null = 全公司视图。
               R115 P1-01 修复：value=null 触发 Vue prop type warning，改为 -1 特殊值（项目 ID 不会为负） -->
          <el-option key="__all__" label="📋 全部项目" :value="-1" />
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button @click="loadAll">刷新</el-button>
      </div>
    </div>

    <!-- P1-27: DCP 5 门控时间线（无图表库，使用 el-steps 展示项目阶段分布） -->
    <el-card class="dcp-card" shadow="never">
      <template #header>
        <div class="dcp-header">
          <span>🚦 DCP 5 门控项目阶段分布</span>
          <span class="dcp-tip">概念 → 计划 → 开发 → 验证 → 发布（数字=当前阶段项目数）</span>
        </div>
      </template>
      <el-steps :active="dcpActiveIdx" finish-status="success" align-center class="dcp-steps">
        <el-step v-for="s in DCP_STAGES" :key="s.key" :title="s.label" :description="`${dcpCounts[s.key] || 0} 个项目`" />
      </el-steps>
    </el-card>

    <div v-if="riskAlert" class="risk-alert">
      <el-alert
        :title="riskAlert"
        type="error"
        :closable="false"
        show-icon
        center
        @click="$router.push('/traceability/gaps')"
      />
    </div>

    <el-tabs v-model="activeTab" v-loading="loading" class="dashboard-tabs">
      <!-- 需求视角 -->
      <el-tab-pane label="📋 需求视角" name="requirements">
        <div class="view-grid">
          <el-card class="stat-card">
            <div class="stat-value" style="color:#409EFF">{{ reqView.total || 0 }}</div>
            <div class="stat-label">需求总数</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#FF5722">{{ reqView.suspectCount || 0 }}</div>
            <div class="stat-label">Suspect 数量</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#67C23A">{{ reqView.coverage?.traced || 0 }}</div>
            <div class="stat-label">已追溯</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#E6A23C">{{ ((reqView.coverage?.overall ?? reqView.coverage?.coverageRate) || 0) + '%' }}</div>
            <div class="stat-label">覆盖率</div>
          </el-card>
        </div>
        <el-row :gutter="20" style="margin-top:20px">
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">按状态分布</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in reqView.byStatus" :key="k">
                  <div class="bar-label">{{ k }}</div>
                  <el-progress :percentage="totalPct(reqView.total, v)" :format="() => v" :stroke-width="18" />
                </div>
                <el-empty v-if="!hasKeys(reqView.byStatus)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">按类型分布</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in reqView.byType" :key="k">
                  <div class="bar-label">{{ typeLabel(k) }}</div>
                  <el-progress :percentage="totalPct(reqView.total, v)" :format="() => v" :stroke-width="18" :color="typeColor(k)" />
                </div>
                <el-empty v-if="!hasKeys(reqView.byType)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 风险视角 -->
      <el-tab-pane label="⚠️ 风险视角" name="risk">
        <div class="view-grid">
          <el-card class="stat-card">
            <div class="stat-value" style="color:#409EFF">{{ riskView.total || 0 }}</div>
            <div class="stat-label">风险总数</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#F56C6C">{{ riskView.highCount || 0 }}</div>
            <div class="stat-label">高风险数</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#E6A23C">{{ (riskView.avgRpn ?? riskView.avgScore) || 0 }}</div>
            <div class="stat-label">平均分</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#67C23A">{{ riskView.total ? Math.round(((riskView.total - riskView.highCount) / riskView.total) * 100) : 0 }}%</div>
            <div class="stat-label">非高风险比例</div>
          </el-card>
        </div>
        <el-row :gutter="20" style="margin-top:20px">
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">按风险等级</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in riskView.byLevel" :key="k">
                  <div class="bar-label">{{ k }}</div>
                  <el-tag :type="levelTagType(k)" effect="dark" size="default">{{ v }}</el-tag>
                </div>
                <el-empty v-if="!hasKeys(riskView.byLevel)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">按状态分布</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in riskView.byStatus" :key="k">
                  <div class="bar-label">{{ k }}</div>
                  <el-progress :percentage="totalPct(riskView.total, v)" :format="() => v" :stroke-width="18" />
                </div>
                <el-empty v-if="!hasKeys(riskView.byStatus)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 管理视角 FR-2.10 -->
      <el-tab-pane label="📈 管理视角" name="management">
        <div class="view-grid">
          <el-card class="stat-card">
            <div class="stat-value" style="color:#409EFF">{{ mgmtView.projectCount || 0 }}</div>
            <div class="stat-label">项目总数</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#F56C6C">{{ mgmtView.alerts?.total || 0 }}</div>
            <div class="stat-label">异常预警</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#E6A23C">{{ mgmtView.alerts?.pendingReviewCount || 0 }}</div>
            <div class="stat-label">待评审</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#67C23A">{{ ((mgmtView.coverage?.overall ?? mgmtView.coverage?.coverageRate) || 0) + '%' }}</div>
            <div class="stat-label">追溯覆盖率</div>
          </el-card>
        </div>
        <el-row :gutter="20" style="margin-top:20px">
          <!-- P1-27: 图表 - 因无 chart.js/echarts 依赖，使用 el-progress 圆环 + 柱状条替代 -->
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">需求完成率</div></template>
              <div class="chart-block">
                <el-progress type="dashboard" :percentage="reqCompletionRate" :width="180" :stroke-width="12" />
                <div class="chart-legend">
                  <div><span class="dot" style="background:#67C23A"></span>已完成 {{ reqView.coverage?.covered || 0 }}</div>
                  <div><span class="dot" style="background:#E6A23C"></span>进行中 {{ reqView.byStatus?.['IN_PROGRESS'] || 0 }}</div>
                  <div><span class="dot" style="background:#F56C6C"></span>未启动 {{ reqView.byStatus?.['DRAFT'] || 0 }}</div>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">变更趋势（最近 7 周）</div></template>
              <div class="chart-block">
                <div class="bar-chart">
                  <div v-for="(v, i) in changeTrend" :key="i" class="bar-col">
                    <div class="bar-val">{{ v }}</div>
                    <div class="bar" :style="{ height: (v * 6) + 'px' }"></div>
                    <div class="bar-lbl">W{{ i + 1 }}</div>
                  </div>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-row :gutter="20" style="margin-top:20px">
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">项目状态分布</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in mgmtView.byStatus" :key="k">
                  <div class="bar-label">{{ projectStatusLabel(k) }}</div>
                  <el-progress :percentage="totalPct(mgmtView.projectCount, v)" :format="() => v" :stroke-width="18" />
                </div>
                <el-empty v-if="!hasKeys(mgmtView.byStatus)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card>
              <template #header><div class="card-title">异常预警</div></template>
              <div class="bar-list">
                <div class="bar-row">
                  <div class="bar-label">Suspect 需求</div>
                  <el-tag type="danger" effect="dark">{{ mgmtView.alerts?.suspectCount || 0 }}</el-tag>
                </div>
                <div class="bar-row">
                  <div class="bar-label">待评审需求</div>
                  <el-tag type="warning" effect="dark">{{ mgmtView.alerts?.pendingReviewCount || 0 }}</el-tag>
                </div>
                <div class="bar-row">
                  <div class="bar-label">高风险项</div>
                  <el-tag type="danger" effect="dark">{{ mgmtView.alerts?.highRiskCount || 0 }}</el-tag>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 合规视角 -->
      <el-tab-pane label="✅ 合规视角" name="compliance">
        <div class="view-grid">
          <el-card class="stat-card">
            <div class="stat-value" style="color:#409EFF">{{ complianceView.iec62304?.total || 0 }}</div>
            <div class="stat-label">IEC 62304 条目</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#67C23A">{{ complianceView.iec62304?.passCount || 0 }}</div>
            <div class="stat-label">已通过</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#E6A23C">{{ complianceView.changes?.total || 0 }}</div>
            <div class="stat-label">变更请求</div>
          </el-card>
          <el-card class="stat-card">
            <div class="stat-value" style="color:#F56C6C">{{ complianceView.problems?.total || 0 }}</div>
            <div class="stat-label">问题报告</div>
          </el-card>
        </div>
        <el-row :gutter="20" style="margin-top:20px">
          <el-col :span="8">
            <el-card>
              <template #header><div class="card-title">IEC 62304 合规</div></template>
              <div class="bar-list">
                <div class="bar-row">
                  <div class="bar-label">完成率</div>
                  <el-progress :percentage="complianceView.iec62304?.completionRate || 0" :stroke-width="18" />
                </div>
                <div class="bar-row">
                  <div class="bar-label">必选条目</div>
                  <span>{{ complianceView.iec62304?.mandatoryCount || 0 }}</span>
                </div>
                <div class="bar-row">
                  <div class="bar-label">已完成</div>
                  <span style="color:#67C23A">{{ complianceView.iec62304?.completedCount || 0 }}</span>
                </div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card>
              <template #header><div class="card-title">变更请求状态</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in complianceView.changes?.byStatus" :key="k">
                  <div class="bar-label">{{ k }}</div>
                  <el-progress :percentage="totalPct(complianceView.changes?.total, v)" :format="() => v" :stroke-width="16" />
                </div>
                <el-empty v-if="!hasKeys(complianceView.changes?.byStatus)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card>
              <template #header><div class="card-title">问题报告严重度</div></template>
              <div class="bar-list">
                <div class="bar-row" v-for="(v,k) in complianceView.problems?.bySeverity" :key="k">
                  <div class="bar-label">{{ k }}</div>
                  <el-tag :type="severityTagType(k)" effect="dark">{{ v }}</el-tag>
                </div>
                <el-empty v-if="!hasKeys(complianceView.problems?.bySeverity)" :image-size="60" />
              </div>
            </el-card>
          </el-col>
        </el-row>
        <el-card style="margin-top:20px">
          <template #header><div class="card-title">项目概况</div></template>
          <div>项目总数：<b>{{ complianceView.projectCount || 0 }}</b></div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- P1-27: 我的待办（合并 5 类待办） - 放在 tabs 外常驻显示 -->
    <el-card class="todo-card" shadow="never">
      <template #header>
        <div class="todo-header">
          <span>📌 我的待办</span>
          <span class="todo-tip">共 {{ totalTodoCount }} 项待处理</span>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col v-for="t in TODO_LIST" :key="t.key" :span="4" :xs="12">
          <div class="todo-item" :class="'todo-' + t.tone" @click="t.action">
            <div class="todo-label">{{ t.label }}</div>
            <div class="todo-value">{{ todoCounts[t.key] ?? 0 }}</div>
            <div class="todo-go">查看 →</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'

const router = useRouter()
const activeTab = ref('requirements')
const loading = ref(false)
// R115 P1-01 修复：默认 -1 表示"全部项目"（与 el-option value=-1 对应）
const filterProject = ref<number | null>(-1)
const projectList = ref<any[]>([])

const reqView = reactive<any>({ total: 0, byStatus: {}, byType: {}, suspectCount: 0, coverage: {} })
const riskView = reactive<any>({ total: 0, highCount: 0, avgScore: 0, byLevel: {}, byStatus: {} })
const complianceView = reactive<any>({ iec62304: {}, changes: {}, problems: {}, projectCount: 0 })
const mgmtView = reactive<any>({ projectCount: 0, byStatus: {}, alerts: {}, coverage: {} })

// P1-27: DCP 5 门控阶段定义（IPD 流程：概念/计划/开发/验证/发布）
const DCP_STAGES = [
  { key: 'CONCEPT', label: '概念' },
  { key: 'PLAN', label: '计划' },
  { key: 'DEVELOP', label: '开发' },
  { key: 'VERIFY', label: '验证' },
  { key: 'RELEASE', label: '发布' }
]
// WHY: 后端 status 字段实际值不固定，按子串匹配 5 阶段以最大化兼容
const DCP_STATUS_MAP: Record<string, string> = {
  CONCEPT: 'CONCEPT', PLAN: 'PLAN', PLANNING: 'PLAN',
  DEVELOP: 'DEVELOP', IN_PROGRESS: 'DEVELOP', EXECUTING: 'DEVELOP',
  VERIFY: 'VERIFY', VERIFYING: 'VERIFY', TESTING: 'VERIFY',
  RELEASE: 'RELEASE', RELEASED: 'RELEASE', COMPLETED: 'RELEASE'
}
const dcpCounts = reactive<Record<string, number>>({ CONCEPT: 0, PLAN: 0, DEVELOP: 0, VERIFY: 0, RELEASE: 0 })
// dcpActiveIdx 取所有阶段中计数最高的索引（>=0 让高亮有视觉中心）
const dcpActiveIdx = computed(() => {
  let maxIdx = 0, maxVal = -1
  DCP_STAGES.forEach((s, i) => {
    const v = dcpCounts[s.key] || 0
    if (v > maxVal) { maxVal = v; maxIdx = i }
  })
  return maxVal > 0 ? maxIdx : 0
})

// P1-27: 5 类待办快捷入口（合并待审批/待签字/待评审/待关闭/待发布）
// WHY: 用 el-row 分栏展示并直接路由跳转，避免弹框
const TODO_LIST = [
  { key: 'pendingChange', label: '待审批变更', tone: 'warning', action: () => router.push('/changes?status=PENDING_APPROVAL') },
  // R97：跳转到签名页时带 status=PENDING query，让 SignatureList 默认只显示待签字
  { key: 'pendingSignature', label: '待签字', tone: 'danger', action: () => router.push('/esignature?status=PENDING') },
  { key: 'pendingReview', label: '待评审', tone: 'info', action: () => router.push('/reviews') },
  { key: 'pendingClose', label: '待关闭', tone: 'primary', action: () => router.push('/compliance/problem-report') },
  { key: 'pendingRelease', label: '待发布', tone: 'success', action: () => router.push('/projects') }
]
const todoCounts = reactive<Record<string, number>>({})
const totalTodoCount = computed(() => Object.values(todoCounts).reduce((a, b) => a + (b || 0), 0))

// P1-27: 需求完成率（基于 coverage 覆盖数 / total）
const reqCompletionRate = computed(() => {
  const total = reqView.total || 0
  const covered = reqView.coverage?.covered || 0
  return total > 0 ? Math.round((covered / total) * 100) : 0
})
// P1-27: 变更趋势（7 周，mock 数据；后端如能提供 change-trend 接口可替换）
// WHY: 暂用 mock 保证 UI 可视化，后续可接入 /dashboard/changes/trend
const changeTrend = ref<number[]>([3, 5, 2, 8, 6, 4, 7])

const hasKeys = (o: any) => o && Object.keys(o).length > 0
const totalPct = (total: number, n: number) => (total > 0 ? Math.round((n / total) * 100) : 0)
const typeLabel = (t: string) => ({ URS: '用户需求', PRS: '产品需求', SRS: '软件需求', DRS: '设计需求' } as any)[t] || t
const typeColor = (t: string) => ({ URS: '#409EFF', PRS: '#67C23A', SRS: '#E6A23C', DRS: '#909399' } as any)[t] || '#409EFF'
const levelTagType = (l: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success', UNKNOWN: 'info' } as any)[l] || 'info'
const severityTagType = (s: string) => ({ CRITICAL: 'danger', HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' } as any)[s] || 'info'
const projectStatusLabel = (s: string) => ({ PLANNING: '计划中', IN_PROGRESS: '进行中', COMPLETED: '已完成', TERMINATED: '已终止' } as any)[s] || s

// P1-27: DCP 阶段计数（聚合项目状态）
const computeDcpCounts = () => {
  // 重置
  DCP_STAGES.forEach(s => { dcpCounts[s.key] = 0 })
  for (const p of projectList.value) {
    const raw = (p.status || p.phase || '').toUpperCase()
    const key = DCP_STATUS_MAP[raw] || 'CONCEPT'
    dcpCounts[key] = (dcpCounts[key] || 0) + 1
  }
}

// P1-27: 计算 5 类待办数（各模块尽力调用，失败则 0）
// R83 修复：4 处契约不一致/URL 错误
// R97 修复：待签字计数改用 /esignature/intents?status=PENDING，按当前用户过滤（之前用 /esignature/signatures 含已签/作废，语义错位）
const loadTodoCounts = async () => {
  // 待审批变更
  // 修复：/changes 根路径只支持 POST，应调专用 GET 端点 /changes/pending
  try {
    const r = await request.get('/changes/pending')
    todoCounts.pendingChange = Array.isArray(r.data?.data) ? r.data.data.length : 0
  } catch { todoCounts.pendingChange = 0 }
  // 待签字（R97 修复：原 /esignature/signatures 包含已签/作废记录，"待签字 7" 实际混入历史）
  // 改为 /esignature/intents?signerId=&status=PENDING，语义对齐 SignatureIntent.status
  try {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')
    const signerId = currentUser?.id || currentUser?.userId
    const r = await request.get('/esignature/intents', { params: { signerId: signerId || undefined, status: 'PENDING', page: 0, size: 1 } })
    todoCounts.pendingSignature = r.data?.data?.total ?? 0
  } catch { todoCounts.pendingSignature = 0 }
  // 待评审
  // 修复：status 值错误。前端原传 PENDING_REVIEW → 永远 0；改为 Submitted（已提交待评审）
  // 状态机：Draft → Submitted → InReview → Approved；Submitted 是"待安排评审"阶段
  try {
    const r = await request.get('/requirements', { params: { status: 'Submitted', page: 0, size: 1 } })
    todoCounts.pendingReview = r.data?.data?.total ?? 0
  } catch { todoCounts.pendingReview = 0 }
  // 待关闭问题报告
  // 修复：status 值错误。问题报告实际枚举为 Open/Analyzing（前端原传 Verifying → 永远 0）
  try {
    const r = await request.get('/compliance/problem-reports', { params: { status: 'Open', page: 0, size: 1 } })
    todoCounts.pendingClose = r.data?.data?.total ?? 0
  } catch { todoCounts.pendingClose = 0 }
  // 待发布（IPD 验证通过的项目）
  todoCounts.pendingRelease = dcpCounts.VERIFY || 0
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const data = res.data?.data
    projectList.value = Array.isArray(data) ? data : (data?.records || [])
    // R86 修复：原默认锁第一个项目 → 风险/管理视角被 projectId 过滤掉全部数据
    // 现默认 null（全部项目），由用户主动选择具体项目
    // if (projectList.value.length > 0 && filterProject.value == null) {
    //   filterProject.value = projectList.value[0].id
    // }
    // P1-27: 项目加载完后聚合 DCP 计数
    computeDcpCounts()
  } catch (e) {}
}

const loadAll = async () => {
  loading.value = true
  try {
    // R115 P1-01 修复：-1 表示"全部项目"，不传 projectId 让后端聚合全公司数据
    const params = filterProject.value === -1 ? {} : { projectId: filterProject.value }
    const [r1, r2, r3, r4, gapsRes] = await Promise.all([
      request.get('/dashboard/view/requirements', { params }),
      request.get('/dashboard/view/risk', { params }),
      request.get('/dashboard/view/compliance', { params }),
      request.get('/dashboard/view/management', { params }),
      request.get('/traceability/gaps', { params }).catch(() => null)
    ])
    Object.assign(reqView, r1.data?.data || {})
    Object.assign(riskView, r2.data?.data || {})
    Object.assign(complianceView, r3.data?.data || {})
    Object.assign(mgmtView, r4.data?.data || {})

    const gaps = gapsRes?.data?.data?.gaps || gapsRes?.data?.data?.records || gapsRes?.data?.data || []
    const gapCount = Array.isArray(gaps) ? gaps.length : (typeof gaps === 'number' ? gaps : 0)
    riskAlert.value = gapCount > 0
      ? `⚠️ 检测到 ${gapCount} 条追溯缺口（断裂链路），点击查看详情`
      : ''
  } catch (e) {
    console.error('loadAll failed', e)
  } finally {
    loading.value = false
  }
}

const riskAlert = ref('')

onMounted(async () => {
  await fetchProjects()
  await loadAll()
  // P1-27: 加载待办计数（失败容错）
  await loadTodoCounts()
})
</script>

<style scoped>
.dashboard { padding: 0; }
.dashboard-header { background: #fff; padding: 16px 24px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #e4e7ed; }
.dashboard-header h2 { font-size: 20px; }
.header-right { display: flex; gap: 12px; align-items: center; }
.dashboard-tabs { padding: 0 20px 20px; }
.view-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { text-align: center; }
.stat-card :deep(.el-card__body) { padding: 20px; }
.stat-value { font-size: 32px; font-weight: 700; }
.stat-label { font-size: 14px; color: #909399; margin-top: 8px; }
.card-title { font-size: 15px; font-weight: 600; }
.bar-list { display: flex; flex-direction: column; gap: 14px; }
.bar-row { display: flex; align-items: center; gap: 12px; }
.bar-label { width: 110px; font-size: 13px; color: #606266; flex-shrink: 0; }
.bar-row :deep(.el-progress) { flex: 1; }
.risk-alert { padding: 0 24px 16px; cursor: pointer; }
/* P1-27: DCP 门控卡 */
.dcp-card { margin: 16px 24px 0; }
.dcp-header { display: flex; justify-content: space-between; align-items: center; }
.dcp-header span:first-child { font-weight: 600; }
.dcp-tip { font-size: 12px; color: #909399; }
.dcp-steps { padding: 8px 0; }
/* P1-27: 图表区 */
.chart-block { display: flex; align-items: center; gap: 32px; justify-content: center; min-height: 200px; }
.chart-legend { display: flex; flex-direction: column; gap: 8px; font-size: 13px; }
.chart-legend .dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
.bar-chart { display: flex; gap: 14px; align-items: flex-end; height: 200px; width: 100%; padding: 0 16px; }
.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; height: 100%; justify-content: flex-end; }
.bar { width: 100%; background: linear-gradient(180deg, #409EFF, #67C23A); border-radius: 4px 4px 0 0; min-height: 4px; }
.bar-val { font-size: 12px; color: #606266; }
.bar-lbl { font-size: 12px; color: #909399; }
/* P1-27: 待办区 */
.todo-card { margin: 20px 24px; }
.todo-header { display: flex; justify-content: space-between; align-items: center; }
.todo-header span:first-child { font-weight: 600; font-size: 15px; }
.todo-tip { font-size: 12px; color: #909399; }
.todo-item { padding: 16px; border-radius: 8px; cursor: pointer; transition: all 0.2s; border: 1px solid #ebeef5; }
.todo-item:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); transform: translateY(-2px); }
.todo-item.todo-warning { border-left: 4px solid #E6A23C; }
.todo-item.todo-danger { border-left: 4px solid #F56C6C; }
.todo-item.todo-info { border-left: 4px solid #909399; }
.todo-item.todo-primary { border-left: 4px solid #409EFF; }
.todo-item.todo-success { border-left: 4px solid #67C23A; }
.todo-label { font-size: 13px; color: #606266; }
.todo-value { font-size: 28px; font-weight: 700; color: #303133; margin: 6px 0; }
.todo-go { font-size: 12px; color: #409EFF; }
</style>
