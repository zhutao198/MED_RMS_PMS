<template>
  <div class="requirement-detail" v-loading="loading">
    <div class="page-header">
      <el-button @click="$router.back()">← 返回</el-button>
      <h2>需求详情</h2>
    </div>

    <!-- 顶部：需求基本信息卡（含 meta 行） -->
    <el-card v-if="requirement" class="req-header-card" shadow="never">
      <div class="req-header-top">
        <div>
          <div class="req-code-row">
            <span class="req-code">{{ requirement.requirementNo || '-' }}</span>
            <el-tag :type="getTypeColor(requirement.requirementType)" effect="light">
              {{ requirement.requirementType || '-' }}
            </el-tag>
            <el-tag :type="getStatusColor(requirement.status)" effect="light">
              {{ getStatusLabel(requirement.status) }}
            </el-tag>
            <el-tag :type="getPriorityColor(requirement.priority)" effect="plain" v-if="requirement.priority">
              {{ requirement.priority }}
            </el-tag>
          </div>
          <div class="req-title">{{ requirement.title || '-' }}</div>
        </div>
        <div class="action-buttons">
          <!-- 通用：电子签名（21 CFR Part 11 §11.50/§11.70）-->
          <!-- FR-0.17 操作序列强制检查 UI 提示：审批/批准前必须先完成评审（21 CFR Part 11 §11.10(f)） -->
          <el-popover
            placement="bottom"
            :width="280"
            trigger="hover"
            :disabled="!['Draft', 'Rejected', 'PendingDecompose'].includes(requirement.status)"
          >
            <template #reference>
              <el-button type="primary" @click="handleOpenSignature">📝 电子签名</el-button>
            </template>
            <div style="font-size:13px;line-height:1.6">
              <div style="font-weight:600;margin-bottom:4px">⚠ 前置条件未满足（21 CFR Part 11 §11.10(f)）</div>
              <div>当前状态 <b>{{ getStatusLabel(requirement.status) }}</b>，请先完成评审后再签批。</div>
              <div style="margin-top:6px;color:#909399;font-size:12px">点击"提交评审"按钮发起评审，评审通过后状态变为"已通过"，再签批。</div>
            </div>
          </el-popover>
          <!-- 已基线化需求：显示发起变更 -->
          <template v-if="requirement.status === 'Baseline'">
            <el-button type="warning" @click="handleCreateChange">发起变更</el-button>
            <el-button type="info" @click="handleVersions">查看版本历史</el-button>
          </template>
          <!-- 已批准需求：显示拆解和评审入口 -->
          <template v-else-if="requirement.status === 'Approved'">
            <el-button type="primary" @click="handleDecompose">拆解为下层需求</el-button>
            <el-button type="success" @click="handleReview">提交评审</el-button>
            <el-button type="info" @click="handleVersions">查看版本历史</el-button>
          </template>
          <!-- 草稿/待拆解/已拆解/已驳回 -->
          <template v-else-if="['Draft', 'PendingDecompose', 'Decomposed', 'Rejected'].includes(requirement.status)">
            <el-button type="primary" @click="handleEdit">编辑</el-button>
            <el-button type="success" @click="handleReview">提交评审</el-button>
            <el-button type="info" @click="handleVersions">查看版本历史</el-button>
          </template>
          <!-- 默认：仅编辑与版本历史 -->
          <template v-else>
            <el-button type="primary" @click="handleEdit">编辑</el-button>
            <el-button type="info" @click="handleVersions">查看版本历史</el-button>
          </template>
        </div>
      </div>
      <div class="req-meta-row">
        <span class="req-meta-item">📅 创建：{{ formatDate(requirement.createdAt) || '-' }}</span>
        <span class="req-meta-item">👤 创建人：{{ requirement.createdBy ? `用户${requirement.createdBy}` : '-' }}</span>
        <span class="req-meta-item">🏷 安全分类：{{ getSafetyClassLabel(requirement.safetyClass) }}</span>
        <span class="req-meta-item">🔗 追溯：{{ upstreamCount }} 条上游 / {{ downstreamCount }} 条下游</span>
        <span class="req-meta-item">📋 测试用例：{{ testCaseCount }} 个</span>
        <span class="req-meta-item">⚠ 风险等级：{{ requirement.riskLevel || '-' }}</span>
        <span class="req-meta-item">📁 所属项目：{{ getProjectName(requirement.projectId) }}</span>
      </div>
    </el-card>

    <!-- 顶部：生命周期时间轴 -->
    <el-card v-if="requirement" class="lifecycle-card" shadow="never">
      <div class="lifecycle-title">📊 需求生命周期</div>
      <el-steps :active="lifecycleActive" finish-status="success" align-center class="lifecycle-steps">
        <el-step v-for="node in lifecycleNodes" :key="node.key" :title="node.title" :description="node.description" />
      </el-steps>
    </el-card>

    <!-- 4 Tab 内容 -->
    <el-card v-if="requirement" class="tabs-card" shadow="never">
      <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- Tab 1: 基本信息 -->
        <el-tab-pane label="基本信息" name="basic">
          <el-skeleton :loading="loading" :rows="6" animated>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="需求编号">{{ requirement.requirementNo || '-' }}</el-descriptions-item>
              <el-descriptions-item label="需求层级">
                <el-tag :type="getTypeColor(requirement.requirementType)">{{ requirement.requirementType || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="需求标题" :span="2">{{ requirement.title || '-' }}</el-descriptions-item>
              <el-descriptions-item label="优先级">
                <el-tag :type="getPriorityColor(requirement.priority)">{{ requirement.priority || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="getStatusColor(requirement.status)">{{ getStatusLabel(requirement.status) }}</el-tag>
              </el-descriptions-item>
              <!-- P2-5：来源以彩色 tag区分显示 -->
 <el-descriptions-item label="需求来源">
 <el-tag v-if="requirement.source" :type="getSourceTagType(requirement.source)" size="small">{{ getSourceLabel(requirement.source) }}</el-tag>
 <span v-else>-</span>
 </el-descriptions-item>
              <el-descriptions-item label="软件安全分类">{{ getSafetyClassLabel(requirement.safetyClass) }}</el-descriptions-item>
              <el-descriptions-item label="需求分类">{{ getCategoryLabel(requirement.requirementCategory) }}</el-descriptions-item>
              <el-descriptions-item label="风险等级">{{ requirement.riskLevel || '-' }}</el-descriptions-item>
              <el-descriptions-item label="所属项目" :span="2">{{ getProjectName(requirement.projectId) }}</el-descriptions-item>
              <el-descriptions-item label="创建人">{{ requirement.createdBy ? `用户${requirement.createdBy}` : '-' }}</el-descriptions-item>
              <el-descriptions-item label="最后更新">{{ formatDate(requirement.updatedAt) || formatDate(requirement.createdAt) || '-' }}</el-descriptions-item>
              <el-descriptions-item label="需求描述" :span="2">{{ requirement.description || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-skeleton>
        </el-tab-pane>

        <!-- Tab 2: 追溯关系 -->
        <el-tab-pane label="追溯关系" name="trace">
          <el-skeleton :loading="traceLoading" :rows="4" animated>
            <div class="trace-section">
              <div class="trace-section-title">⬆️ 上游需求（父级）</div>
              <el-table :data="upstreamList" border stripe v-if="upstreamList.length > 0">
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag effect="plain">{{ row.sourceType || 'REQUIREMENT' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="sourceNo" label="上游编号" width="160" />
                <el-table-column label="关联类型" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getLinkTypeColor(row.linkType)" size="small">{{ getLinkTypeLabel(row.linkType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="traceContext" label="关联说明" min-width="180" />
                <el-table-column prop="createdAt" label="创建时间" width="170">
                  <template #default="{ row }">{{ formatDate(row.createdAt) || '-' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无上游需求" :image-size="80" />
            </div>

            <div class="trace-section">
              <div class="trace-section-title">⬇️ 下游需求（子级）</div>
              <el-table :data="downstreamList" border stripe v-if="downstreamList.length > 0">
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag effect="plain">{{ row.targetType || 'REQUIREMENT' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="targetNo" label="下游编号" width="160" />
                <el-table-column label="关联类型" width="120">
                  <template #default="{ row }">
                    <el-tag :type="getLinkTypeColor(row.linkType)" size="small">{{ getLinkTypeLabel(row.linkType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="traceContext" label="关联说明" min-width="180" />
                <el-table-column prop="createdAt" label="创建时间" width="170">
                  <template #default="{ row }">{{ formatDate(row.createdAt) || '-' }}</template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无下游需求" :image-size="80" />
            </div>
          </el-skeleton>
        </el-tab-pane>

        <!-- Tab 3: 签名记录 -->
        <el-tab-pane label="签名记录" name="signature">
          <el-skeleton :loading="signatureLoading" :rows="3" animated>
            <div class="trace-section-title">📝 电子签名历史</div>
            <div v-if="signatureList.length === 0" class="empty-block">
              <el-empty description="暂无签名记录" :image-size="80" />
            </div>
            <div v-else>
              <div v-for="sig in signatureList" :key="sig.id" class="signature-record">
                <div class="sig-header">
                  <div>
                    <div class="sig-person">{{ sig.signerName || `用户${sig.signerId}` }}</div>
                    <div class="sig-role">{{ sig.signerRole || sig.signatureType || '-' }}</div>
                  </div>
                  <el-tag type="primary" effect="plain" size="small">{{ sig.intent || sig.signType || sig.signatureMethod || 'approve' }}</el-tag>
                </div>
                <div class="sig-detail" v-if="sig.reason">签名含义：{{ sig.reason }}</div>
                <div class="sig-time">
                  {{ formatDateTime(sig.signedAt) || '-' }}
                  <span v-if="sig.signatureHash || sig.hashValue"> | SHA-256: {{ (sig.signatureHash || sig.hashValue || '').slice(0, 8) }}...{{ (sig.signatureHash || sig.hashValue || '').slice(-4) }}</span>
                </div>
              </div>
            </div>
          </el-skeleton>
        </el-tab-pane>

        <!-- Tab 4: 测试用例 -->
        <el-tab-pane label="测试用例" name="testcase">
          <el-skeleton :loading="testCaseLoading" :rows="4" animated>
            <div class="testcase-toolbar">
              <div class="trace-section-title">🧪 关联测试用例（{{ testCaseCount }} 个）</div>
            </div>
            <el-table :data="testCaseList" border stripe v-if="testCaseList.length > 0">
              <el-table-column prop="testCaseNo" label="用例编号" width="140" />
              <el-table-column prop="title" label="用例标题" min-width="200" />
              <el-table-column label="测试类型" width="110">
                <template #default="{ row }">{{ getTestTypeLabel(row.testType) }}</template>
              </el-table-column>
              <el-table-column label="安全分类" width="100">
                <template #default="{ row }">
                  <el-tag :type="getSafetyClassColor(row.safetyClass)" size="small">{{ row.safetyClass || '-' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="getTestStatusColor(row.status)" size="small">{{ getTestStatusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间" width="170">
                <template #default="{ row }">{{ formatDate(row.createdAt) || '-' }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-else description="该需求暂无关联测试用例" :image-size="80" />
          </el-skeleton>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 通用电子签名弹窗（21 CFR Part 11 §11.50/§11.70） -->
    <SignatureDialog
      ref="signatureDialogRef"
      v-model="signatureDialogVisible"
      @signed="onSignatureSigned"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { requirementApi } from '../../api/requirement'
import type { Requirement } from '../../api/requirement'
import { projectApi } from '../../api/project'
import type { Project } from '../../api/project'
import { traceabilityApi, type TraceLink } from '../../api/traceability'
import { esignatureApi, type SignatureRecord } from '../../api/esignature'
import { testCaseApi, type TestCase } from '../../api/testcase'
import SignatureDialog from '../../components/SignatureDialog.vue'

const route = useRoute()
const router = useRouter()

const requirement = ref<Requirement | null>(null)
const projectList = ref<Project[]>([])
const loading = ref(false)
const activeTab = ref('basic')

// 追溯数据
const upstreamList = ref<TraceLink[]>([])
const downstreamList = ref<TraceLink[]>([])
const traceLoading = ref(false)

// 签名数据
const signatureList = ref<SignatureRecord[]>([])
const signatureLoading = ref(false)

// 测试用例
const testCaseList = ref<TestCase[]>([])
const testCaseLoading = ref(false)

// 电子签名弹窗（21 CFR Part 11 §11.50/§11.70）
const signatureDialogRef = ref<InstanceType<typeof SignatureDialog> | null>(null)
const signatureDialogVisible = ref(false)

// P1-1 修复：追溯与测试用例计数优先取服务端专用端点，失败时降级为列表长度
const upstreamCount = ref(0)
const downstreamCount = ref(0)
const testCaseCount = ref(0)

/** 加载项目列表（用于显示项目名） */
const loadProjects = async () => {
  try {
    const res = await projectApi.list()
    projectList.value = res.data?.data || []
  } catch (e) {
    console.error('加载项目列表失败', e)
  }
}

/** 加载需求主数据 */
const loadRequirement = async () => {
  const id = Number(route.params.id)
  loading.value = true
  try {
    const res = await requirementApi.get(id)
    requirement.value = res.data?.data || null
  } catch (e) {
    console.error('加载需求详情失败', e)
    ElMessage.error('加载需求详情失败')
  } finally {
    loading.value = false
  }
}

/** 加载追溯关系（上游 + 下游） */
const loadTraceability = async () => {
  const id = Number(route.params.id)
  if (!id) return
  traceLoading.value = true
  try {
    const { upstream, downstream } = await traceabilityApi.listByRequirement(id)
    upstreamList.value = upstream || []
    downstreamList.value = downstream || []
    upstreamCount.value = upstreamList.value.length
    downstreamCount.value = downstreamList.value.length
    // P1-1 修复：尝试用专用计数端点覆盖本地聚合
    try {
      const res = await requirementApi.getTraceCount(id)
      const data = res.data?.data
      if (data) {
        upstreamCount.value = data.upstream ?? upstreamList.value.length
        downstreamCount.value = data.downstream ?? downstreamList.value.length
      }
    } catch {
      // 端点不存在时回退到列表长度
    }
  } catch (e) {
    console.error('加载追溯关系失败', e)
    // 失败时回退为空数组
    upstreamList.value = []
    downstreamList.value = []
  } finally {
    traceLoading.value = false
  }
}

/** 加载签名记录 */
const loadSignatures = async () => {
  const id = Number(route.params.id)
  if (!id) return
  signatureLoading.value = true
  try {
    const res = await esignatureApi.getByEntity('requirement', id)
    signatureList.value = res.data?.data || []
  } catch (e) {
    console.error('加载签名记录失败', e)
    signatureList.value = []
  } finally {
    signatureLoading.value = false
  }
}

/** 加载测试用例 */
const loadTestCases = async () => {
  const id = Number(route.params.id)
  if (!id) return
  testCaseLoading.value = true
  try {
    const res = await testCaseApi.listByRequirement(id)
    testCaseList.value = res.data?.data || []
    testCaseCount.value = testCaseList.value.length
    // P1-1 修复：尝试用专用计数端点覆盖本地聚合
    try {
      const r = await requirementApi.getTestCaseCount(id)
      const data = r.data?.data
      if (data && typeof data.count === 'number') testCaseCount.value = data.count
    } catch {
      // 端点不存在时回退到列表长度
    }
  } catch (e) {
    console.error('加载测试用例失败', e)
    testCaseList.value = []
  } finally {
    testCaseLoading.value = false
  }
}

const getProjectName = (projectId: number | undefined) => {
  if (!projectId) return '-'
  const project = projectList.value.find(p => p.id === projectId)
  return project ? project.projectName : `项目${projectId}`
}

const handleDecompose = () => {
  router.push(`/requirements/${route.params.id}/decompose`)
}

const handleReview = async () => {
  const id = Number(route.params.id)
  try {
    await requirementApi.submitReview(id, 1, '')
    ElMessage.success('评审提交成功')
    loadRequirement()
  } catch (e: any) {
    ElMessage.error('评审提交失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleVersions = () => {
  router.push(`/requirements/${route.params.id}/versions`)
}

const handleCreateChange = () => {
  if (!requirement.value?.id) return
  router.push({ path: '/changes/create', query: { requirementId: requirement.value.id.toString() } })
}

const handleEdit = () => {
  if (!requirement.value?.id) return
  router.push({ path: `/requirements/${requirement.value.id}/edit` })
}

/**
 * 打开电子签名弹窗（21 CFR Part 11 §11.50/§11.70）
 * 默认含义为 approve（审批通过），调用方可在 open() 入参覆盖。
 */
const handleOpenSignature = () => {
  if (!requirement.value?.id) {
    ElMessage.warning('需求未加载，无法签名')
    return
  }
  signatureDialogRef.value?.open({
    entityType: 'requirement',
    entityId: requirement.value.id,
    documentNo: requirement.value.requirementNo,
    docTitle: requirement.value.title || `需求 #${requirement.value.id}`,
    signContext: `需求 ${requirement.value.requirementNo || ''} ${requirement.value.title || ''} 的电子签名确认。`,
    reason: `需求签名 - ${requirement.value.requirementNo || requirement.value.id}`,
    meaningCode: 'approve',
  })
}

/** 签名成功回调：刷新签名记录 Tab + 提示 */
const onSignatureSigned = () => {
  loadSignatures()
}

const getTypeColor = (type?: string) => {
  return ({ URS: '', PRS: 'success', SRS: 'warning', DRS: 'danger' } as Record<string, string>)[type || ''] || ''
}

const getPriorityColor = (priority?: string) => {
  return ({ MUST: 'danger', SHOULD: 'warning', COULD: 'info', WONT: '' } as Record<string, string>)[priority || ''] || ''
}

/** 状态 → el-tag type 映射（用于头部状态徽章） */
const getStatusColor = (status?: string) => {
  return ({
    Draft: 'info',
    Submitted: 'warning',
    InReview: 'warning',
    ReviewApproved: 'primary',
    ReviewRejected: 'danger',
    Approved: 'primary',
    Rejected: 'danger',
    InProgress: 'warning',
    InTest: 'warning',
    Verified: 'success',
    Baseline: 'success',
    Decomposed: 'success',
    Suspect: 'warning',
    Withdrawn: 'info',
  } as Record<string, string>)[status || ''] || 'info'
}

const statusLabels: Record<string, string> = {
  Draft: '草稿',
  Submitted: '已提交',
  InReview: '评审中',
  ReviewApproved: '评审通过',
  ReviewRejected: '评审驳回',
  Approved: '已批准',
  Rejected: '已驳回',
  PendingDecompose: '待拆解',
  Decomposed: '已拆解',
  InProgress: '实施中',
  InTest: '测试中',
  Verified: '已验证',
  Baseline: '已基线',
  Suspect: 'Suspect',
  Withdrawn: '已撤回',
}

const getStatusLabel = (status?: string) => statusLabels[status || ''] || status || '-'

const getSafetyClassLabel = (sc?: string) => {
  if (!sc) return '-'
  const map: Record<string, string> = { A: 'Class A', B: 'Class B', C: 'Class C' }
  return map[sc] || sc
}

const getSafetyClassColor = (sc?: string) => {
  if (!sc) return 'info'
  return ({ A: 'success', B: 'warning', C: 'danger' } as Record<string, string>)[sc] || 'info'
}

// P2-5：来源 → el-tag type映射（不同来源以不同颜色区分，便于一眼识别）
const getSourceTagType = (s?: string): string => {
 const map: Record<string, string> = {
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
 }
 return map[s || ''] || ''
}

const getSourceLabel = (s?: string) => {
  if (!s) return '-'
  const map: Record<string, string> = {
    // P2-5：8档扩展源；旧值保留兼容
 USER_INTERVIEW: '用户访谈',
 HISTORICAL_PROJECT: '历史项目',
 CUSTOMER_COMPLAINT: '客户投诉',
 EXPERT_REVIEW: '专家评审',
 SYSTEM_LOG: '系统日志',
 OTHER: '其他',
 CUSTOMER: '客户需求',
    MARKET: '市场需求',
    REGULATION: '法规标准',
    INTERNAL: '内部需求',
    COMPETITOR: '竞品分析',
  }
  return map[s] || s
}

const getCategoryLabel = (c?: string) => {
  if (!c) return '-'
  const map: Record<string, string> = { SOFTWARE: '软件', HARDWARE: '硬件', BOTH: '软硬件' }
  return map[c] || c
}

const getLinkTypeLabel = (t?: string) => {
  if (!t) return '-'
  const map: Record<string, string> = {
    DECOMPOSE: '拆解',
    REFINES: '精化',
    DEPENDS: '依赖',
    CONFLICTS: '冲突',
    REUSES: '复用',
    VERIFIES: '验证',
  }
  return map[t] || t
}

const getLinkTypeColor = (t?: string) => {
  return ({
    DECOMPOSE: 'primary',
    REFINES: 'primary',
    DEPENDS: 'warning',
    CONFLICTS: 'danger',
    REUSES: 'success',
    VERIFIES: 'success',
  } as Record<string, string>)[t || ''] || 'info'
}

const getTestTypeLabel = (t?: string) => {
  if (!t) return '-'
  const map: Record<string, string> = {
    UNIT: '单元测试',
    INTEGRATION: '集成测试',
    SYSTEM: '系统测试',
    ACCEPTANCE: '验收测试',
  }
  return map[t] || t
}

const getTestStatusLabel = (s?: string) => {
  if (!s) return '-'
  const map: Record<string, string> = {
    DRAFT: '草稿',
    ACTIVE: '进行中',
    PASSED: '通过',
    FAILED: '失败',
    OBSOLETE: '已废弃',
  }
  return map[s] || s
}

const getTestStatusColor = (s?: string) => {
  return ({
    DRAFT: 'info',
    ACTIVE: 'primary',
    PASSED: 'success',
    FAILED: 'danger',
    OBSOLETE: 'warning',
  } as Record<string, string>)[s || ''] || 'info'
}

/** 日期格式化：YYYY-MM-DD */
const formatDate = (s?: string) => {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd}`
}

/** 日期时间格式化：YYYY-MM-DD HH:mm:ss */
const formatDateTime = (s?: string) => {
  if (!s) return ''
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const yyyy = d.getFullYear()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mi = String(d.getMinutes()).padStart(2, '0')
  const ss = String(d.getSeconds()).padStart(2, '0')
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`
}

/** 生命周期时间轴 7 节点 */
const lifecycleNodes = [
  { key: 'Draft', title: '草稿', description: '创建需求' },
  { key: 'Submitted', title: '已提交', description: '提交评审' },
  { key: 'InReview', title: '评审中', description: '评审流转' },
  { key: 'Approved', title: '已批准', description: '评审通过' },
  { key: 'PendingDecompose', title: '待分解', description: '等待拆解' },
  { key: 'Decomposed', title: '已分解', description: '拆解完成' },
  { key: 'Baseline', title: '已基线', description: '基线锁定' },
]

/** 根据 status 计算当前激活节点（0-based index） */
const lifecycleActive = computed(() => {
  const status = requirement.value?.status
  if (!status) return 0
  const idx = lifecycleNodes.findIndex(n => n.key === status)
  return idx >= 0 ? idx : 0
})

onMounted(() => {
  loadProjects()
  loadRequirement()
  // 四个 Tab 数据并行加载，避免切换 Tab 时的二次等待
  loadTraceability()
  loadSignatures()
  loadTestCases()
})
</script>

<style scoped>
.requirement-detail {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

/* 头部基本信息卡 */
.req-header-card {
  margin-bottom: 16px;
}
.req-header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
  gap: 16px;
}
.req-code-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.req-code {
  font-size: 16px;
  font-weight: 700;
  color: #409eff;
}
.req-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-top: 8px;
}
.req-meta-row {
  display: flex;
  gap: 24px;
  font-size: 13px;
  color: #606266;
  margin-top: 12px;
  flex-wrap: wrap;
}
.req-meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.action-buttons {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

/* 生命周期卡 */
.lifecycle-card {
  margin-bottom: 16px;
}
.lifecycle-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}
.lifecycle-steps {
  padding: 8px 0;
}

/* Tab 卡 */
.tabs-card {
  margin-bottom: 16px;
}
.detail-tabs {
  padding: 0 8px;
}
.tab-content-pad {
  padding: 16px 4px;
}

/* 追溯区块 */
.trace-section {
  margin-bottom: 24px;
}
.trace-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

/* 签名记录卡 */
.signature-record {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 14px;
  margin-bottom: 10px;
}
.sig-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.sig-person {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.sig-role {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
}
.sig-detail {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.sig-time {
  font-size: 12px;
  color: #c0c4cc;
}
.empty-block {
  padding: 16px 0;
}

/* 测试用例工具栏 */
.testcase-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
</style>