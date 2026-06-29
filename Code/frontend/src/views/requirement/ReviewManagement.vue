<template>
  <div class="review-management-container">
    <div class="page-header">
      <div class="page-title">评审管理</div>
      <div class="header-actions">
        <el-button @click="handleRefresh">刷新</el-button>
      </div>
    </div>

    <div class="review-kpi">
      <div class="kpi-card warning">
        <div class="kpi-label">待评审</div>
        <div class="kpi-value">{{ kpi.pending }}</div>
        <div class="kpi-sub">需求评审队列</div>
      </div>
      <div class="kpi-card danger">
        <div class="kpi-label">超期未评</div>
        <div class="kpi-value">{{ kpi.overdue }}</div>
        <div class="kpi-sub">超过 3 天未处理</div>
      </div>
      <div class="kpi-card success">
        <div class="kpi-label">本月通过率</div>
        <div class="kpi-value">{{ kpi.passRate }}%</div>
        <div class="kpi-sub">通过 {{ kpi.approved }} / 提交 {{ kpi.submitted }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">平均评审周期</div>
        <div class="kpi-value">{{ kpi.avgCycle }}<span class="kpi-unit">天</span></div>
        <div class="kpi-sub">目标 3 天以内</div>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="filterLevel" size="small" style="width: 120px;" placeholder="需求层级" clearable>
        <el-option label="全部层级" value="" />
        <el-option label="URS" value="URS" />
        <el-option label="PRS" value="PRS" />
        <el-option label="SRS" value="SRS" />
        <el-option label="DRS" value="DRS" />
      </el-select>
      <el-select v-model="filterStatus" size="small" style="width: 130px;" placeholder="评审状态" clearable>
        <el-option label="全部状态" value="" />
        <el-option label="待评审" value="pending" />
        <el-option label="评审中" value="in-review" />
        <el-option label="已通过" value="approved" />
        <el-option label="已驳回" value="rejected" />
      </el-select>
      <el-input v-model="filterKeyword" size="small" placeholder="搜索需求编号/标题" style="width: 180px;" clearable />
    </div>

    <div class="review-list">
      <div
        v-for="r in filteredReviews"
        :key="r.id"
        class="review-card"
        :class="r.status"
        @click="openReview(r)"
      >
        <div class="review-level-icon" :class="r.level">{{ r.level }}</div>
        <div class="review-main">
          <div class="review-code">{{ r.code }}</div>
          <div class="review-title">{{ r.title }}</div>
          <div class="review-meta">
            <span class="review-stat">📅 提交：{{ r.submittedAt }}</span>
            <span class="review-stat">👤 提交人：{{ r.submitter }}</span>
            <span class="review-stat">📋 评审人：{{ r.reviewer || '待分配' }}</span>
          </div>
        </div>
        <div class="review-right">
          <el-tag size="small" :type="statusType(r.status)">{{ statusText[r.status] }}</el-tag>
          <div class="overdue-tag" :class="{ danger: r.isOverdue }">{{ r.daysText }}</div>
          <div class="review-actions" @click.stop>
            <el-button v-if="r.status === 'pending'" size="small" type="primary" @click="handleAssign(r)">接单</el-button>
            <el-button v-if="r.status === 'in-review'" size="small" type="primary" @click="openReview(r)">评审</el-button>
            <el-button v-if="r.status === 'approved'" size="small" plain @click="openReview(r)">查看</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showReviewDialog" :title="selectedReview ? '评审详情 — ' + selectedReview.code : '评审详情'" width="680px">
      <div v-if="selectedReview">
        <el-descriptions :column="2" border size="small" style="margin-bottom: 16px;">
          <el-descriptions-item label="需求编号">{{ selectedReview.code }}</el-descriptions-item>
          <el-descriptions-item label="需求层级">
            <el-tag size="small" :type="getLevelTagType(selectedReview.level)">{{ selectedReview.level }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="需求标题" :span="2">{{ selectedReview.title }}</el-descriptions-item>
          <el-descriptions-item label="优先级"><el-tag size="small" type="danger">{{ selectedReview.priority }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="提交日期">{{ selectedReview.submittedAt }}</el-descriptions-item>
        </el-descriptions>

        <div class="opinion-section">
          <div class="section-title">📋 评审意见（{{ selectedReview.opinions?.length || 0 }}条）</div>
          <div class="review-opinion">
            <div class="opinion-item" v-for="(op, idx) in selectedReview.opinions" :key="idx">
              <div class="opinion-header">
                <div>
                  <div class="opinion-author">{{ op.author }}</div>
                  <div class="opinion-role">{{ op.role }}</div>
                </div>
                <div class="opinion-time">{{ op.time }}</div>
              </div>
              <div class="opinion-text">{{ op.content }}</div>
            </div>
          </div>
        </div>

        <div class="review-form" v-if="selectedReview.status === 'in-review'">
          <div class="form-title">✍ 填写评审意见</div>
          <el-input v-model="reviewComment" type="textarea" :rows="3" placeholder="请输入评审意见（必填）" style="margin-bottom: 12px;" />
          <div class="form-actions">
            <el-button @click="submitOpinion('rejected')" type="danger" plain>驳回</el-button>
            <el-button type="primary" @click="submitOpinion('approved')">通过并签名</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showReviewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { requirementApi } from '../../api/requirement'
import request from '../../api/request'
import { useUserStore } from '../../stores/user'

const filterLevel = ref('')
const filterStatus = ref('')
const filterKeyword = ref('')
const showReviewDialog = ref(false)
const selectedReview = ref<any>(null)
const reviewComment = ref('')
const loading = ref(false)
const userStore = useUserStore()

const STATUS_MAP: Record<string, string> = {
  Draft: 'pending', Submitted: 'in-review', Approved: 'approved', Rejected: 'rejected', Baseline: 'approved'
}
const PRIORITY_TAG: Record<string, string> = { MUST: 'danger', SHOULD: 'warning', COULD: 'info', WONT: '' }

const statusText: Record<string, string> = { pending: '待评审', 'in-review': '评审中', approved: '已通过', rejected: '已驳回' }
const statusType = (s: string) => ({ pending: 'warning', 'in-review': '', approved: 'success', rejected: 'danger' }[s] || 'info')

const kpi = ref({
  pending: 0,
  overdue: 0,
  passRate: 0,
  approved: 0,
  submitted: 0,
  avgCycle: 0
})

const computeKpi = (list: any[]) => {
  const reviewable = list.filter((r: any) => ['Submitted', 'Approved', 'Rejected', 'Baseline'].includes(r.status))
  const submitted = reviewable.length
  const approved = reviewable.filter((r: any) => ['Approved', 'Baseline'].includes(r.status)).length
  const pending = list.filter((r: any) => r.status === 'Submitted').length
  const now = Date.now()
  const overdue = list.filter((r: any) => {
    if (r.status !== 'Submitted') return false
    const t = r.createdAt ? Date.parse(r.createdAt) : NaN
    return !isNaN(t) && (now - t) > 3 * 24 * 3600 * 1000
  }).length
  const cycles = list
    .filter((r: any) => ['Approved', 'Rejected', 'Baseline'].includes(r.status) && r.createdAt && r.updatedAt)
    .map((r: any) => Math.max(0, (Date.parse(r.updatedAt) - Date.parse(r.createdAt)) / (24 * 3600 * 1000)))
  const avgCycle = cycles.length ? Number((cycles.reduce((a, b) => a + b, 0) / cycles.length).toFixed(1)) : 0
  kpi.value = {
    pending,
    overdue,
    passRate: submitted > 0 ? Math.round((approved / submitted) * 100) : 0,
    approved,
    submitted,
    avgCycle
  }
}

const reviews = ref<any[]>([])

const loadReviews = async () => {
  loading.value = true
  try {
    // R94 修复：原代码调 requirementApi.list({page:1, size:100}) 拉全部需求，前端 JS 过滤
    // 但后端按 id 排序返回前 100 条，Submitted 状态（共 22 条）可能全部落在 100 条之后
    // 导致页面显示 0 条（与 Dashboard "待评审 22" 不一致）
    // 修复：按 4 个 status 分别查询再合并
    const REVIEW_STATUSES = ['Submitted', 'Approved', 'Rejected', 'Baseline']
    const responses = await Promise.all(
      REVIEW_STATUSES.map(status => requirementApi.list({ status, page: 1, size: 200 }).catch(() => null))
    )
    const list = responses
      .filter(r => r != null)
      .flatMap(r => ((r!.data as any)?.data?.records || []))
    reviews.value = list
      .map((r: any) => ({
        id: r.id,
        code: r.requirementNo,
        title: r.title,
        level: r.requirementType,
        status: STATUS_MAP[r.status] || 'pending',
        submitter: String(r.createdBy || '未知'),
        reviewer: r.status === 'Submitted' ? '待分配' : (userStore.userInfo?.realName || '已分配'),
        submittedAt: (r.createdAt || '').replace('T', ' ').slice(0, 16),
        priority: r.priority,
        isOverdue: r.status === 'Submitted' && r.createdAt && (Date.now() - Date.parse(r.createdAt)) > 3 * 24 * 3600 * 1000,
        daysText: r.status === 'Submitted' ? '待评审' : (r.status === 'Approved' || r.status === 'Baseline' ? '通过' : '已驳回'),
        opinions: []
      }))
    computeKpi(list)
  } catch (e) {
    console.error('load reviews failed:', e)
    ElMessage.error('加载评审列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadReviews)

const filteredReviews = computed(() => {
  let list = reviews.value
  if (filterLevel.value) list = list.filter(r => r.level === filterLevel.value)
  if (filterStatus.value) list = list.filter(r => r.status === filterStatus.value)
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase()
    list = list.filter(r => r.code.toLowerCase().includes(kw) || r.title.toLowerCase().includes(kw))
  }
  return list
})

const getLevelTagType = (level: string) => ({ URS: '', PRS: 'success', SRS: 'warning', DRS: 'danger' }[level] || '')

const handleRefresh = async () => {
  await loadReviews()
  ElMessage.success('刷新成功')
}

const openReview = async (r: any) => {
  selectedReview.value = r
  showReviewDialog.value = true
  try {
    const res = await request.get(`/requirements/${r.id}/reviews`)
    const list = (res.data as any)?.data || []
    selectedReview.value = {
      ...r,
      opinions: list.map((op: any) => ({
        author: op.reviewerName || (op.reviewerId ? `用户 #${op.reviewerId}` : '匿名'),
        role: op.role || '评审人',
        time: (op.reviewedAt || op.createdAt || '').replace('T', ' ').slice(0, 16),
        content: op.comments || op.decision || '（无意见）'
      }))
    }
  } catch (e: any) {
    console.error('加载评审意见失败', e)
    ElMessage.error('加载评审意见失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleAssign = async (r: any) => {
  try {
    const reviewerId = userStore.userInfo?.id
    if (!reviewerId) {
      ElMessage.warning('未获取到当前用户信息，无法接单')
      return
    }
    await requirementApi.submitReview(r.id, reviewerId, '评审接单')
    ElMessage.success(`已接单：${r.code}`)
    await loadReviews()
  } catch (e: any) {
    console.error('handleAssign failed:', e)
    ElMessage.error(`接单失败：${e?.response?.data?.message || e?.message || '未知错误'}`)
  }
}

const submitOpinion = async (result: string) => {
  if (!selectedReview.value) {
    ElMessage.warning('请先选择评审项')
    return
  }
  if (!reviewComment.value.trim()) {
    ElMessage.warning('请填写评审意见')
    return
  }
  const approverId = userStore.userInfo?.id
  if (!approverId) {
    ElMessage.warning('未获取到当前用户信息，无法提交评审')
    return
  }
  const decision = result === 'approved' ? 'APPROVED' : 'REJECTED'
  try {
    await requirementApi.approve(
      selectedReview.value.id,
      decision,
      approverId,
      reviewComment.value.trim()
    )
    ElMessage.success(result === 'approved' ? `评审通过：${selectedReview.value.code}` : `已驳回：${selectedReview.value.code}`)
    showReviewDialog.value = false
    reviewComment.value = ''
    await loadReviews()
  } catch (e: any) {
    console.error('submitOpinion failed:', e)
    ElMessage.error(`评审失败：${e?.response?.data?.message || e?.message || '未知错误'}`)
  }
}
</script>

<style scoped>
.review-management-container {
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

.review-kpi {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.kpi-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.kpi-card .kpi-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.kpi-card .kpi-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.kpi-card .kpi-unit {
  font-size: 14px;
}

.kpi-card .kpi-sub {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.kpi-card.warning .kpi-value { color: #e6a23c; }
.kpi-card.danger .kpi-value { color: #f56c6c; }
.kpi-card.success .kpi-value { color: #67c23a; }

.filter-bar {
  background: #fff;
  border-radius: 8px;
  padding: 14px 16px;
  margin-bottom: 12px;
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: flex-start;
  gap: 16px;
  cursor: pointer;
  transition: all 0.2s;
  border-left: 4px solid transparent;
}

.review-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.review-card.pending { border-left-color: #e6a23c; }
.review-card.in-review { border-left-color: #409eff; }
.review-card.approved { border-left-color: #67c23a; }
.review-card.rejected { border-left-color: #f56c6c; }

.review-level-icon {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.review-level-icon.URS { background: #ecf5ff; color: #409eff; }
.review-level-icon.PRS { background: #f0f9eb; color: #67c23a; }
.review-level-icon.SRS { background: #fdf6ec; color: #e6a23c; }
.review-level-icon.DRS { background: #fef0f0; color: #f56c6c; }

.review-main { flex: 1; }

.review-code {
  font-size: 14px;
  font-weight: 700;
  color: #409eff;
}

.review-title {
  font-size: 15px;
  color: #303133;
  margin-top: 4px;
  font-weight: 500;
}

.review-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
  flex-wrap: wrap;
}

.review-stat {
  display: flex;
  align-items: center;
  gap: 4px;
}

.review-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.overdue-tag {
  font-size: 12px;
  color: #c0c4cc;
}

.overdue-tag.danger {
  color: #f56c6c;
  font-weight: 600;
}

.review-actions {
  display: flex;
  gap: 8px;
}

.opinion-section {
  margin-bottom: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.review-opinion {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.opinion-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
}

.opinion-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.opinion-author {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.opinion-role {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.opinion-time {
  font-size: 11px;
  color: #c0c4cc;
}

.opinion-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.review-form {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px;
}

.form-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>