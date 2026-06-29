<template>
  <div class="change-list-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>变更申请列表</span>
          <div>
            <el-radio-group v-model="viewMode" size="small" style="margin-right: 12px">
              <el-radio-button label="table">📋 表格</el-radio-button>
              <el-radio-button label="card">🗂 卡片</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="showCreateDialog = true">新建变更</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <!-- R95 清理：删除冗余的"已批准(数据)" + "审核中"（R91 兼容大小写时留的兜底项，现在后端已支持大小写不敏感 IN 查询）-->
          <el-select v-model="filterStatus" placeholder="全部" clearable @change="fetchData">
            <el-option key="__all__" label="📋 全部状态" value="" />
            <el-option label="草稿" value="DRAFT" />
            <el-option label="影响分析中" value="ANALYZING" />
            <el-option label="待审批" value="PENDING_APPROVAL" />
            <el-option label="已批准" value="APPROVED" />
            <el-option label="执行中" value="EXECUTING" />
            <el-option label="已关闭" value="CLOSED" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="需求编号">
          <el-input v-model="filterRequirement" placeholder="需求编号" clearable @keyup.enter="fetchData" />
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilter">重置</el-button>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <div v-if="viewMode === 'table'">
        <el-table :data="changes" border stripe v-loading="loading">
        <el-table-column prop="changeNo" label="变更编号" width="160" />
        <el-table-column prop="changeType" label="变更类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ CHANGE_TYPE_ZH[row.changeType] || row.changeType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="变更原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="影响范围" width="160">
          <template #default="{ row }">
            <span v-if="impactSummary[row.id]">
              <el-tag size="small" type="info" effect="plain">URS {{ impactSummary[row.id].urs || 0 }}</el-tag>
              <el-tag size="small" type="success" effect="plain" style="margin-left:4px">PRS {{ impactSummary[row.id].prs || 0 }}</el-tag>
              <el-tag size="small" type="warning" effect="plain" style="margin-left:4px">SRS {{ impactSummary[row.id].srs || 0 }}</el-tag>
            </span>
            <span v-else style="color:#c0c4cc">未评估</span>
          </template>
        </el-table-column>
        <el-table-column prop="urgency" label="紧急程度" width="110">
          <template #default="{ row }">
            <!-- v1.53 P1-9 修复：4 档颜色 蓝/黄/橙/红 -->
            <el-tag :type="getUrgencyType(row.urgency || row.priority)">{{ getUrgencyLabel(row.urgency || row.priority) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="requestedAt" label="申请时间" width="160" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="info" @click="handleSubmit(row)" v-if="row.status === 'DRAFT'">提交</el-button>
            <el-button size="small" type="warning" @click="handleAnalyze(row)" v-if="row.status === 'ANALYZING'">执行影响评估</el-button>
            <el-button size="small" type="primary" @click="showApproveDialog(row)" v-if="row.status === 'PENDING_APPROVAL'">审批</el-button>
            <el-button size="small" type="success" @click="showExecuteDialog(row)" v-if="row.status === 'APPROVED'">执行</el-button>
            <el-button size="small" type="warning" @click="showVerifyDialog(row)" v-if="row.status === 'EXECUTING'">验证</el-button>
            <el-button size="small" type="info" @click="showCloseDialog(row)" v-if="['APPROVED','EXECUTING','VERIFIED'].includes(row.status)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- P3-1 修复：卡片视图 -->
      <div v-else class="change-card-grid">
        <el-card v-for="row in changes" :key="row.id" class="change-card" shadow="hover">
          <template #header>
            <div class="change-card-header">
              <span class="cc-no">{{ row.changeNo }}</span>
              <el-tag :type="getUrgencyType(row.urgency || row.priority)" size="small">{{ getUrgencyLabel(row.urgency || row.priority) }}</el-tag>
            </div>
          </template>
          <div class="change-card-body">
            <div class="cc-row"><span class="lbl">类型：</span><el-tag size="small">{{ CHANGE_TYPE_ZH[row.changeType] || row.changeType }}</el-tag></div>
            <div class="cc-row"><span class="lbl">原因：</span><span class="val">{{ row.reason }}</span></div>
            <div class="cc-row"><span class="lbl">状态：</span><el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag></div>
            <div class="cc-row"><span class="lbl">申请时间：</span><span class="val">{{ row.requestedAt }}</span></div>
          </div>
          <template #footer>
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="primary" @click="showApproveDialog(row)" v-if="row.status === 'PENDING_APPROVAL'">审批</el-button>
          </template>
        </el-card>
        <el-empty v-if="changes.length === 0" :image-size="100" />
      </div>

      <el-pagination
        style="margin-top: 16px; justify-content: flex-end"
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </el-card>

    <!-- 创建变更对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建变更申请" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="需求ID">
          <el-input v-model.number="createForm.requirementId" type="number" />
        </el-form-item>
        <el-form-item label="变更标题">
          <el-input v-model="createForm.title" placeholder="请输入变更标题" />
        </el-form-item>
        <el-form-item label="变更类型">
          <el-select v-model="createForm.changeType">
            <el-option label="纠正性变更" value="CORRECTIVE" />
            <el-option label="适应性变更" value="ADAPTIVE" />
            <el-option label="完善性变更" value="PERFECTIVE" />
            <el-option label="紧急变更" value="EMERGENCY" />
          </el-select>
        </el-form-item>
        <el-form-item label="紧急程度">
          <!-- v1.53 P1-9 修复：统一 4 档枚举 LOW/MEDIUM/HIGH/CRITICAL -->
          <el-select v-model="createForm.urgency">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="createForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createChange" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>

    <!-- 审批对话框 -->
    <el-dialog v-model="showApproveDialogFlag" title="审批变更" width="400px">
      <el-form :model="approveForm" label-width="80px">
        <el-form-item label="决策">
          <el-radio-group v-model="approveForm.decision">
            <el-radio label="APPROVED">批准</el-radio>
            <el-radio label="REJECTED">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="意见">
          <el-input v-model="approveForm.comments" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showApproveDialogFlag = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">确定</el-button>
      </template>
    </el-dialog>

    <!-- 变更详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="变更详情" width="700px">
      <el-descriptions :column="2" border v-if="currentChange">
        <el-descriptions-item label="变更编号">{{ currentChange.changeNo }}</el-descriptions-item>
        <el-descriptions-item label="变更类型">{{ CHANGE_TYPE_ZH[currentChange.changeType] || currentChange.changeType }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">{{ URGENCY_ZH[currentChange.urgency] || currentChange.urgency }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ changeStatusLabels[currentChange.status] || currentChange.status }}</el-descriptions-item>
        <el-descriptions-item label="变更原因" :span="2">{{ currentChange.reason }}</el-descriptions-item>
        <el-descriptions-item label="申请时间">{{ currentChange.requestedAt }}</el-descriptions-item>
        <el-descriptions-item label="审批时间">{{ currentChange.approvedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见" :span="2">{{ currentChange.approvalComments || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { changeApi, type ChangeRequest, impactAssessmentApi } from '@/api/change'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CHANGE_TYPE_ZH, URGENCY_ZH, toZh } from '@/utils/zh-mapping'

const route = useRoute()
// R94 修复：原页面不读 URL query，从 Dashboard "待审批变更"跳过来时显示全部 103 条变更
// 改为：进入页面时从 ?status=PENDING_APPROVAL 读取并自动应用
const filterStatus = ref<string>((route.query.status as string) || '')
const filterRequirement = ref('')
const changes = ref<ChangeRequest[]>([])
const loading = ref(false)
const page = ref(1)
// P3-1 修复：表格/卡片视图切换
const viewMode = ref<'table' | 'card'>('table')
const pageSize = ref(10)
const total = ref(0)
const impactSummary = ref<Record<number, { urs: number; prs: number; srs: number; drs: number; testcases: number }>>({})

const showCreateDialog = ref(false)
const showApproveDialogFlag = ref(false)
const showDetailDialog = ref(false)
const submitting = ref(false)
const currentChange = ref<ChangeRequest | null>(null)

const createForm = ref({
  requirementId: 0,
  title: '',
  changeType: 'CORRECTIVE',
  reason: '',
  urgency: 'MEDIUM',
  requestedBy: 1,
})

const approveForm = ref({
  decision: 'APPROVED',
  comments: '',
})

const fetchData = async () => {
  loading.value = true
  try {
    const params: any = { size: 100 }
    if (filterStatus.value) params.status = filterStatus.value
    if (filterRequirement.value) params.requirementNo = filterRequirement.value
    const res = await changeApi.list(params)
    let list = res.data.data || []
    if (filterRequirement.value && list.length > 0) {
      const kw = filterRequirement.value.toLowerCase()
      list = list.filter((c: any) => {
        const no = (c.requirementNo || c.requirement_no || '').toLowerCase()
        return no.includes(kw) || !c.requirementNo
      })
    }
    changes.value = list
    total.value = list.length
    fetchImpactSummary(list)
  } catch {
    ElMessage.error('获取变更列表失败')
  } finally {
    loading.value = false
  }
}

const fetchImpactSummary = async (list: any[]) => {
  const next: Record<number, any> = {}
  for (const c of list) {
    if (!c.id) continue
    try {
      const res = await impactAssessmentApi.listByChange(c.id).catch(() => null)
      const arr = res?.data?.data || []
      const tally = { urs: 0, prs: 0, srs: 0, drs: 0, testcases: 0 }
      for (const a of arr) {
        if (a.affectedLevel === 'URS') tally.urs++
        else if (a.affectedLevel === 'PRS') tally.prs++
        else if (a.affectedLevel === 'SRS') tally.srs++
        else if (a.affectedLevel === 'DRS') tally.drs++
        else if (a.affectedLevel === 'TEST_CASE') tally.testcases++
      }
      next[c.id] = tally
    } catch {
      next[c.id] = { urs: 0, prs: 0, srs: 0, drs: 0, testcases: 0 }
    }
  }
  impactSummary.value = next
}

const resetFilter = () => {
  filterStatus.value = ''
  filterRequirement.value = ''
  fetchData()
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: 'info',
    ANALYZING: 'warning',
    PENDING_APPROVAL: 'warning',
    APPROVED: 'primary',
    EXECUTING: 'warning',
    VERIFIED: 'success',
    CLOSED: 'info',
  }
  return map[status] || 'info'
}

const changeStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ANALYZING: '影响分析中',
  PENDING_APPROVAL: '待审批',
  APPROVED: '已批准',
  EXECUTING: '执行中',
  VERIFIED: '已验证',
  CLOSED: '已关闭',
  SUBMITTED: '已提交',
  REJECTED: '已拒绝',
  InReview: '审核中',
  Approved: '已批准',
  CANCELLED: '已取消',
  PENDING_ANALYSIS: '待影响分析',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成'
}

const getStatusLabel = (status: string) => changeStatusLabels[status] || status

// v1.53 P1-9 修复：4 档统一枚举 + 4 色映射（蓝/黄/橙/红）
const URGENCY_LABELS: Record<string, string> = {
  LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急',
  NORMAL: '中', URGENT: '紧急', EMERGENCY: '紧急',
  MAJOR: '高', MINOR: '低', DOCUMENT: '中', REQUIREMENT_UPDATE: '高'
}
const URGENCY_TYPES: Record<string, string> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger',
  NORMAL: 'info', URGENT: 'danger'
}
const getUrgencyLabel = (v?: string) => URGENCY_LABELS[v || ''] || v || '-'
const getUrgencyType = (v?: string) => URGENCY_TYPES[v || ''] || 'info'

const viewDetail = (row: ChangeRequest) => {
  currentChange.value = row
  showDetailDialog.value = true
}

const showApproveDialog = (row: ChangeRequest) => {
  currentChange.value = row
  showApproveDialogFlag.value = true
}

const handleSubmit = async (row: ChangeRequest) => {
  try {
    await ElMessageBox.confirm('确认提交此变更申请？提交后将进入影响分析阶段。', '提交变更')
    await changeApi.submit(row.id!)
    ElMessage.success('变更已提交')
    fetchData()
  } catch {}
}

const handleAnalyze = async (row: ChangeRequest) => {
  try {
    await ElMessageBox.confirm('确认执行影响评估？将分析此变更对追溯链的影响。', '执行影响评估')
    await changeApi.assess(row.id!)
    ElMessage.success('影响评估完成')
    fetchData()
  } catch {}
}

const showExecuteDialog = async (row: ChangeRequest) => {
  try {
    await ElMessageBox.confirm('确认执行此变更?', '执行变更')
    await changeApi.execute(row.id, {})
    ElMessage.success('变更已执行')
    fetchData()
  } catch {}
}

const showVerifyDialog = async (row: ChangeRequest) => {
  try {
    await ElMessageBox.confirm('确认验证通过?', '验证变更')
    await changeApi.verify(row.id)
    ElMessage.success('变更已验证')
    fetchData()
  } catch {}
}

const showCloseDialog = async (row: ChangeRequest) => {
  try {
    await ElMessageBox.confirm('确认关闭此变更?', '关闭变更')
    await changeApi.close(row.id)
    ElMessage.success('变更已关闭')
    fetchData()
  } catch {}
}

const createChange = async () => {
  if (!createForm.value.requirementId || createForm.value.requirementId <= 0 || !createForm.value.reason || !createForm.value.title) {
    ElMessage.warning('请填写必填项')
    return
  }
  const submitData = {
    requirementId: createForm.value.requirementId,
    title: createForm.value.title,
    changeType: createForm.value.changeType,
    reason: createForm.value.reason,
    urgency: createForm.value.urgency,
    requestedBy: createForm.value.requestedBy,
  }
  submitting.value = true
  try {
    await changeApi.create(submitData)
    ElMessage.success('变更申请已创建')
    showCreateDialog.value = false
    fetchData()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

const submitApprove = async () => {
  if (!currentChange.value) return
  try {
    await changeApi.approve(currentChange.value.id, 1, approveForm.value.decision, approveForm.value.comments)
    ElMessage.success('审批已提交')
    showApproveDialogFlag.value = false
    fetchData()
  } catch {
    ElMessage.error('审批失败')
  }
}

onMounted(fetchData)
</script>

<style scoped>
.change-list-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.filter-form {
  margin-bottom: 16px;
}
/* P3-1 修复：卡片视图 */
.change-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  margin-top: 8px;
}
.change-card { border-radius: 8px; }
.change-card-header { display: flex; justify-content: space-between; align-items: center; }
.cc-no { font-weight: 600; color: #303133; }
.cc-row { display: flex; align-items: center; margin: 6px 0; font-size: 13px; }
.cc-row .lbl { color: #909399; min-width: 70px; }
.cc-row .val { color: #303133; }
</style>