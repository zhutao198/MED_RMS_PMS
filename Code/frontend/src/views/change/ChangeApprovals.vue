<!--
  ChangeApprovals.vue - 我的审批工作台
  v1.53 P1-8 修复：展示 status=PENDING_APPROVAL + currentApprover=currentUser 的变更
  操作：同意/拒绝/转交
-->
<template>
  <div class="change-approvals-container">
    <div class="page-header">
      <div class="page-title">我的审批工作台</div>
      <div class="header-actions">
        <el-button @click="fetchData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <el-card class="stat-card-row">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-statistic title="待我审批" :value="stats.pending" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="今日已审" :value="stats.approvedToday" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="本周已审" :value="stats.approvedThisWeek" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="平均处理时长" :value="stats.avgHours" suffix="h" />
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>待我审批列表</span>
          <el-radio-group v-model="filterType" size="small" @change="fetchData">
            <el-radio-button label="PENDING">待我审批</el-radio-button>
            <el-radio-button label="ALL">全部历史</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table :data="approvals" border stripe v-loading="loading" empty-text="暂无待审批变更">
        <el-table-column prop="changeNo" label="变更单号" width="160" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="changeType" label="类型" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.changeType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="urgency" label="紧急程度" width="100">
          <template #default="{ row }">
            <el-tag :type="getUrgencyType(row.urgency)" size="small">{{ getUrgencyLabel(row.urgency) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requesterName" label="申请人" width="100" />
        <el-table-column prop="requestedAt" label="申请时间" width="160" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="handleApprove(row)">同意</el-button>
            <el-button size="small" type="danger" @click="handleReject(row)">拒绝</el-button>
            <el-button size="small" plain @click="openDelegate(row)">转交</el-button>
            <el-button size="small" text @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 拒绝对话框 -->
    <el-dialog v-model="showReject" title="拒绝变更" width="460px">
      <el-form :model="rejectForm" label-width="80px">
        <el-form-item label="拒绝原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="请说明拒绝原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReject = false">取消</el-button>
        <el-button type="danger" @click="submitReject" :loading="submitting">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 转交对话框 -->
    <el-dialog v-model="showDelegate" title="转交审批" width="460px">
      <el-form :model="delegateForm" label-width="100px">
        <el-form-item label="转交给" required>
          <el-input v-model.number="delegateForm.toUserId" type="number" placeholder="用户ID" />
        </el-form-item>
        <el-form-item label="转交原因">
          <el-input v-model="delegateForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDelegate = false">取消</el-button>
        <el-button type="primary" @click="submitDelegate" :loading="submitting">确认转交</el-button>
      </template>
    </el-dialog>

    <!-- 批准电子签名弹窗 -->
    <ESignPopup ref="eSignRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import ESignPopup from '@/views/esignature/ESignPopup.vue'

interface ChangeItem {
  id: number
  changeNo: string
  title: string
  changeType: string
  urgency: string
  requesterName: string
  requestedAt: string
  status: string
  currentApprover?: number | null
}

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const filterType = ref<'PENDING' | 'ALL'>('PENDING')
const approvals = ref<ChangeItem[]>([])
const stats = ref({ pending: 0, approvedToday: 0, approvedThisWeek: 0, avgHours: 0 })
const currentUserId = ref<number>(1)

const URGENCY_LABELS: Record<string, string> = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急' }
const URGENCY_TYPES: Record<string, string> = { LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger' }
const getUrgencyLabel = (v?: string) => URGENCY_LABELS[v || ''] || v || '-'
const getUrgencyType = (v?: string) => URGENCY_TYPES[v || ''] || 'info'

const STATUS_TYPES: Record<string, string> = {
  DRAFT: 'info', SUBMITTED: 'warning', ANALYZING: 'warning',
  PENDING_APPROVAL: 'warning', APPROVED: 'success', REJECTED: 'danger',
  EXECUTING: 'warning', VERIFIED: 'success', CLOSED: 'info'
}
const getStatusType = (s: string) => STATUS_TYPES[s] || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    // v1.53 P1-8：拉取 PENDING_APPROVAL 列表，前端按 currentApprover 过滤
    const res = await request.get('/changes/list', { params: { status: 'PENDING_APPROVAL', size: 200 } })
    const all = (res.data?.data || []) as ChangeItem[]
    approvals.value = filterType.value === 'PENDING'
      ? all.filter(c => !c.currentApprover || c.currentApprover === currentUserId.value)
      : all
    stats.value.pending = approvals.value.length
  } catch (e: any) {
    ElMessage.error('加载审批列表失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const showReject = ref(false)
const rejectForm = ref({ reason: '' })
const currentChange = ref<ChangeItem | null>(null)

const handleApprove = (row: ChangeItem) => {
  currentChange.value = row
  // v1.53 P1-12：批准时走电子签名
  eSignRef.value?.open({
    scenario: '变更审批电子签名',
    context: `变更单：${row.changeNo}\n标题：${row.title}\n决策：批准`,
    documentType: 'CHANGE_REQUEST',
    documentId: row.id,
    intentCode: 'approve',
    meaningCode: 'approve',
    onSuccess: async () => {
      try {
        await request.post(`/changes/${row.id}/approve`, null, { params: { approverId: currentUserId.value, decision: 'APPROVED' } })
        ElMessage.success(`已批准：${row.changeNo}`)
        fetchData()
      } catch (e: any) {
        ElMessage.error('批准失败：' + (e?.response?.data?.message || e?.message))
      }
    }
  })
}

const handleReject = (row: ChangeItem) => {
  currentChange.value = row
  rejectForm.value = { reason: '' }
  showReject.value = true
}

const submitReject = async () => {
  if (!currentChange.value || !rejectForm.value.reason) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  submitting.value = true
  try {
    await request.post(`/changes/${currentChange.value.id}/reject`, null, { params: { reason: rejectForm.value.reason } })
    ElMessage.success('已拒绝')
    showReject.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error('拒绝失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    submitting.value = false
  }
}

const showDelegate = ref(false)
const delegateForm = ref({ toUserId: 1, reason: '' })
const openDelegate = (row: ChangeItem) => {
  currentChange.value = row
  showDelegate.value = true
}

const submitDelegate = async () => {
  if (!currentChange.value) return
  submitting.value = true
  try {
    await request.post(`/changes/${currentChange.value.id}/delegate`, null, {
      params: { fromUserId: currentUserId.value, toUserId: delegateForm.value.toUserId, reason: delegateForm.value.reason }
    })
    ElMessage.success('已转交')
    showDelegate.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error('转交失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    submitting.value = false
  }
}

const eSignRef = ref<InstanceType<typeof ESignPopup> | null>(null)
const viewDetail = (row: ChangeItem) => router.push(`/changes/${row.id}`)

onMounted(() => {
  const u = JSON.parse(localStorage.getItem('userInfo') || '{}')
  if (u?.id) currentUserId.value = Number(u.id)
  fetchData()
})
</script>

<style scoped>
.change-approvals-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; }
.header-actions { display: flex; gap: 10px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
