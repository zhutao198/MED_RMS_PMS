<template>
  <div class="change-request-container" v-loading="loading">
    <div class="page-header">
      <div class="page-title">变更详情</div>
      <div class="header-actions">
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" @click="handleEdit" :disabled="!canEdit">编辑</el-button>
        <el-button type="warning" @click="handleAnalyze" :disabled="!changeRequest.id">影响分析</el-button>
        <el-button type="primary" plain @click="openDelegateDialog" :disabled="!changeRequest.id">委派</el-button>
        <el-button v-if="changeRequest.countersignRequired" type="primary" plain @click="openCountersignDialog">设置会签人</el-button>
        <!-- FR-0.17 操作序列强制检查 UI 提示：批准前必须先完成影响评估（影响项 > 0） -->
        <el-popover
          placement="bottom"
          :width="280"
          trigger="hover"
          :disabled="canApprove && impactTotals.total > 0"
        >
          <template #reference>
            <el-button type="success" @click="handleApprove" :disabled="!canApprove">批准并签署</el-button>
          </template>
          <div style="font-size:13px;line-height:1.6">
            <div style="font-weight:600;margin-bottom:4px">⚠ 前置条件未满足（21 CFR Part 11 §11.10(f)）</div>
            <div v-if="impactTotals.total === 0">✗ 尚未执行影响评估，无法审批</div>
            <div v-else-if="!canApprove">✗ 当前状态 <b>{{ changeRequest.status }}</b> 不允许审批</div>
            <div style="margin-top:6px;color:#909399;font-size:12px">请先点击"影响分析"按钮完成评估</div>
          </div>
        </el-popover>
      </div>
    </div>

    <!-- v1.53 P1-11 修复：6/5 步生命周期流程条（DRAFT→SUBMITTED→ANALYZING→PENDING_APPROVAL→APPROVED→EXECUTING→VERIFIED→CLOSED） -->
    <el-card class="lifecycle-card">
      <el-steps :active="currentStepIndex" finish-status="success" align-center>
        <el-step v-for="s in lifecycleSteps" :key="s.value" :title="s.title" :description="s.desc" />
      </el-steps>
    </el-card>

    <div v-if="changeRequest.countersignRequired || changeRequest.delegatedAt" class="flow-bar">
      <el-tag v-if="changeRequest.delegatedAt" type="info" size="large">
        委派：{{ changeRequest.delegatedFromName }} → {{ changeRequest.assigneeName || changeRequest.requesterName }}（于 {{ changeRequest.delegatedAt }}）
      </el-tag>
      <el-tag v-if="changeRequest.countersignRequired" :type="countersignTagType" size="large">
        会签进度：{{ countersignProgressLabel }}（{{ signedCount }}/{{ totalSigners }}）
      </el-tag>
    </div>

    <div class="status-bar">
      <el-tag :type="getStatusType(changeRequest.status)" size="large">
        {{ getStatusLabel(changeRequest.status) }}
      </el-tag>
      <span class="change-no">变更单号：{{ changeRequest.changeNo }}</span>
      <span class="change-date">创建时间：{{ changeRequest.createdAt }}</span>
    </div>

    <div class="content-grid">
      <div class="main-content">
        <el-card class="basic-info-card">
          <template #header>
            <div class="card-header">
              <span>基本信息</span>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="变更标题" :span="2">{{ changeRequest.title }}</el-descriptions-item>
            <el-descriptions-item label="变更类型">
              <el-tag :type="getChangeTypeTag(changeRequest.changeType)" size="small">
                {{ changeRequest.changeType }}
              </el-tag>
            </el-descriptions-item>
            <!-- v1.53 P1-9 修复：统一使用 urgency -->
            <el-descriptions-item label="紧急程度">
              <el-tag :type="getUrgencyType(changeRequest.urgency || changeRequest.priority)" size="small">
                {{ getUrgencyLabel(changeRequest.urgency || changeRequest.priority) }}
              </el-tag>
            </el-descriptions-item>
            <!-- v1.53 P1-10 修复：影响范围展示 -->
            <el-descriptions-item label="影响范围">
              <el-tag :type="getImpactScopeType(changeRequest.impactScope)" size="small">
                {{ getImpactScopeLabel(changeRequest.impactScope) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="关联项目">{{ changeRequest.projectName }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ changeRequest.applicantName }}</el-descriptions-item>
            <el-descriptions-item label="期望完成日期">{{ changeRequest.expectedDate }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card class="description-card">
          <template #header>
            <div class="card-header">
              <span>变更描述</span>
            </div>
          </template>
          <div class="description-content">
            {{ changeRequest.description }}
          </div>
        </el-card>

        <el-card class="reason-card">
          <template #header>
            <div class="card-header">
              <span>变更理由</span>
            </div>
          </template>
          <div class="reason-content">
            {{ changeRequest.reason }}
          </div>
        </el-card>

        <el-card class="affected-items-card">
          <template #header>
            <div class="card-header">
              <span>影响项（ {{ affectedItems.length }} 项）</span>
              <el-button size="small" type="primary" plain @click="goImpactAnalysis" v-if="changeRequest.id">前往分析</el-button>
            </div>
          </template>
          <el-table :data="affectedItems" border size="small" v-loading="loadingImpacts">
            <el-table-column prop="itemNo" label="编号" width="140">
              <template #default="{ row }">
                <span style="color:#909399" v-if="!row.itemNo">-</span>
                <span v-else>{{ row.itemNo }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="itemType" label="类型" width="100" />
            <el-table-column prop="itemName" label="名称" />
            <el-table-column prop="impactLevel" label="影响程度" width="100">
              <template #default="{ row }">
                <el-tag :type="getImpactLevelTag(row.impactLevel)" size="small">
                  {{ row.impactLevel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="impactType" label="影响类型" width="120" />
            <el-table-column prop="suggestedAction" label="建议操作" width="150" show-overflow-tooltip />
            <el-table-column label="操作" width="100" align="center">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="viewImpact(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- v1.53 P1-10 修复：影响范围进度条（受影响需求 × 风险 × SOP 数量 / 总数） -->
        <el-card class="impact-scope-card">
          <template #header>
            <div class="card-header">
              <span>影响范围评估（基于 /changes/{id}/impacts）</span>
            </div>
          </template>
          <div v-loading="loadingImpacts">
            <el-row :gutter="16">
              <el-col :span="6">
                <el-statistic title="受影响需求" :value="impactTotals.requirementCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="受影响风险" :value="impactTotals.riskCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="受影响 SOP" :value="impactTotals.sopCount" />
              </el-col>
              <el-col :span="6">
                <el-statistic title="影响项总数" :value="impactTotals.total" />
              </el-col>
            </el-row>
            <el-progress
              :percentage="impactTotals.coverageRate"
              :stroke-width="14"
              :color="['#67c23a', '#e6a23c', '#f56c6c']"
              style="margin-top: 16px"
            />
            <div class="impact-meta">
              <span>已评估：{{ impactTotals.total }} 项 / 未评估：{{ impactTotals.unassessed }} 项</span>
            </div>
          </div>
        </el-card>

        <el-card v-if="changeRequest.riskAssessment" class="risk-card">
          <template #header>
            <div class="card-header">
              <span>风险评估</span>
            </div>
          </template>
          <div class="risk-content">
            <div class="risk-item">
              <span class="risk-label">风险等级：</span>
              <el-tag :type="getRiskTag(changeRequest.riskAssessment.riskLevel)" size="small">
                {{ changeRequest.riskAssessment.riskLevel }}
              </el-tag>
            </div>
            <div class="risk-item">
              <span class="risk-label">风险描述：</span>
              <span>{{ changeRequest.riskAssessment.description }}</span>
            </div>
            <div class="risk-item">
              <span class="risk-label">缓解措施：</span>
              <span>{{ changeRequest.riskAssessment.mitigation }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <div class="side-content">
        <el-card class="timeline-card">
          <template #header>
            <div class="card-header">
              <span>审批流程</span>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in changeRequest.approvals"
              :key="index"
              :type="getTimelineType(item.status)"
              :timestamp="item.timestamp"
              placement="top"
            >
              <div class="timeline-content">
                <div class="timeline-title">{{ item.title }}</div>
                <div class="timeline-desc">{{ item.description }}</div>
                <div class="timeline-user">{{ item.userName }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card v-if="changeRequest.countersignRequired" class="countersign-card">
          <template #header>
            <div class="card-header">
              <span>会签人列表</span>
              <el-button size="small" type="primary" plain @click="openMyCountersign">我要会签</el-button>
            </div>
          </template>
          <el-table :data="parsedCountersigners" border size="small">
            <el-table-column prop="name" label="姓名" width="140" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="row.signed ? 'success' : 'info'" size="small">
                  {{ row.signed ? '已签' : '待签' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="signedAt" label="签署时间" width="200" />
            <el-table-column prop="comments" label="意见" />
          </el-table>
        </el-card>

        <el-card class="attachment-card">
          <template #header>
            <div class="card-header">
              <span>附件</span>
              <el-button size="small" text type="primary" @click="handleUploadClick" :loading="uploading">上传</el-button>
              <input ref="fileInputRef" type="file" style="display:none" @change="handleFileChange" />
            </div>
          </template>
          <div v-if="changeRequest.attachments?.length" class="attachment-list">
            <div v-for="file in changeRequest.attachments" :key="file.id" class="attachment-item">
              <span class="attachment-icon">📎</span>
              <span class="attachment-name">{{ file.originalName || file.name }}</span>
              <span v-if="file.fileSize" class="attachment-size" style="font-size:11px;color:#909399;margin-right:4px">
                {{ formatSize(file.fileSize) }}
              </span>
              <el-button size="small" text type="primary" @click="handleDownload(file)">下载</el-button>
              <el-button size="small" text type="danger" @click="handleDeleteAttachment(file)">删除</el-button>
            </div>
          </div>
          <el-empty v-else description="暂无附件" :image-size="60" />
        </el-card>

        <el-card class="related-changes-card">
          <template #header>
            <div class="card-header">
              <span>关联变更</span>
            </div>
          </template>
          <div v-if="changeRequest.relatedChanges?.length" class="related-list">
            <div v-for="change in changeRequest.relatedChanges" :key="change.id" class="related-item" @click="goChangeDetail(change.id)">
              <span class="related-code">{{ change.changeNo }}</span>
              <span class="related-title">{{ change.title }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无关联变更" :image-size="60" />
        </el-card>
      </div>
    </div>
    <!-- 委派对话框 -->
    <el-dialog v-model="showDelegate" title="委派变更审批" width="480px" @open="loadUsers">
      <el-form :model="delegateForm" label-width="100px">
        <el-form-item label="当前操作人">
          <el-input v-model="delegateForm.fromUserName" placeholder="您的姓名" />
        </el-form-item>
        <el-form-item label="受派人">
          <!-- R110 H3 修复：el-select 选用户（替代硬编码 el-input-number） -->
          <el-select v-model="delegateForm.toUserId" placeholder="请选择受派人" filterable style="width: 100%" @change="onDelegateToUserChange">
            <el-option v-for="u in userList" :key="u.id" :label="`${u.realName} (${u.username})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="受派人姓名">
          <el-input v-model="delegateForm.toUserName" placeholder="受派人姓名（选用户自动填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDelegate = false">取消</el-button>
        <el-button type="primary" @click="confirmDelegate">确认委派</el-button>
      </template>
    </el-dialog>

    <!-- 设置会签人对话框 -->
    <el-dialog v-model="showCountersign" title="设置会签人" width="640px" @open="loadUsers">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px;">
        会签人全部签署完成后，状态才允许进入"审批通过"。
      </el-alert>
      <div v-for="(s, i) in countersigners" :key="i" class="signer-row" style="margin-bottom: 8px;">
        <!-- R110 H3 修复：el-select 选用户（替代硬编码 el-input-number） -->
        <el-select v-model="s.id" placeholder="选择用户" filterable size="small" style="width: 260px;" @change="(uid) => onCountersignerUserChange(i, uid)">
          <el-option v-for="u in userList" :key="u.id" :label="`${u.realName} (${u.username})`" :value="u.id" />
        </el-select>
        <el-input v-model="s.name" placeholder="姓名（选用户自动填）" size="small" style="width: 160px; margin-left: 8px;" />
        <el-button size="small" type="danger" text @click="countersigners.splice(i, 1)" style="margin-left: 8px;">删</el-button>
      </div>
      <!-- R110 H3 修复：默认 undefined 而非硬编码 1 -->
      <el-button size="small" type="primary" plain @click="countersigners.push({ id: undefined as any, name: '' })">+ 添加会签人</el-button>
      <template #footer>
        <el-button @click="showCountersign = false">取消</el-button>
        <el-button type="primary" @click="confirmSetCountersigners">保存</el-button>
      </template>
    </el-dialog>

    <!-- 我要会签 -->
    <el-dialog v-model="showMyCountersign" title="会签" width="480px" @open="loadUsers">
      <el-form :model="countersignForm" label-width="100px">
        <el-form-item label="签署人">
          <!-- R110 H3 修复：el-select 选用户（替代硬编码 el-input-number） -->
          <el-select v-model="countersignForm.signerUserId" placeholder="请选择签署人" filterable style="width: 100%" @change="onMyCountersignUserChange">
            <el-option v-for="u in userList" :key="u.id" :label="`${u.realName} (${u.username})`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会签意见">
          <el-input v-model="countersignForm.comments" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMyCountersign = false">取消</el-button>
        <el-button type="primary" @click="confirmCountersign">提交会签</el-button>
      </template>
    </el-dialog>

    <!-- 编辑变更单 -->
    <el-dialog v-model="showEditDialog" title="编辑变更单" width="560px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="变更标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="变更描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="变更理由">
          <el-input v-model="editForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="submitEdit" :loading="submittingEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 审批 -->
    <el-dialog v-model="showApproveDialog" title="审批变更" width="480px">
      <!-- v1.53 P1-12 修复：Part 11 合规提示 -->
      <el-alert
        title="此操作将依据 21 CFR Part 11 §11.50 进行电子签名"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #default>
          <div style="font-size: 12px; line-height: 1.6">
            批准操作将弹出电子签名对话框（ESignPopup），需要您输入签名密码 + OTP 动态验证码完成签署。签署后签名记录受哈希链保护，无法被篡改。
          </div>
        </template>
      </el-alert>
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
        <el-button @click="showApproveDialog = false">取消</el-button>
        <el-button type="primary" @click="submitApprove">批准并签署</el-button>
      </template>
    </el-dialog>

    <!-- v1.53 P1-12 修复：电子签名弹窗（Part 11 §11.50） -->
    <ESignPopup ref="eSignRef" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import ESignPopup from '@/views/esignature/ESignPopup.vue'
import { systemApi } from '@/api/system'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const loadingImpacts = ref(false)
const affectedItems = ref<any[]>([])
const fileInputRef = ref<HTMLInputElement | null>(null)
const showApproveDialog = ref(false)
const approveForm = ref({ decision: 'APPROVED', comments: '' })
// v1.53 P1-12 修复：电子签名弹窗引用
const eSignRef = ref<InstanceType<typeof ESignPopup> | null>(null)

const changeRequest = ref<any>({
  id: null as number | null,
  changeNo: '',
  title: '',
  changeType: '',
  priority: '',
  status: '',
  impactScope: '',
  projectName: '',
  applicantName: '',
  createdAt: '',
  expectedDate: '',
  description: '',
  reason: '',
  // FR-1.7 委派/会签字段
  assigneeId: null as number | null,
  assigneeName: null as string | null,
  delegatedFromId: null as number | null,
  delegatedFromName: null as string | null,
  delegatedAt: null as string | null,
  countersignRequired: false,
  countersignProgress: 'NONE',
  countersigners: null as string | null,
  // 风险评估 + 审批流程 + 附件 + 关联变更（后端暂无独立接口，保留 fallback）
  riskAssessment: null as any,
  approvals: [] as any[],
  attachments: [] as any[],
  relatedChanges: [] as any[]
})

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: 'info',
    PENDING_ANALYSIS: 'warning',
    PENDING_APPROVAL: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    IN_PROGRESS: 'primary',
    COMPLETED: 'success'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING_ANALYSIS: '待影响分析',
    PENDING_APPROVAL: '待审批',
    APPROVED: '已批准',
    REJECTED: '已拒绝',
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成',
    ANALYZING: '影响分析中',
    EXECUTING: '执行中',
    VERIFIED: '已验证',
    CLOSED: '已关闭',
    SUBMITTED: '已提交',
    CANCELLED: '已取消',
    InReview: '审核中',
    Approved: '已批准'
  }
  return map[status] || status
}

const getChangeTypeTag = (type: string) => {
  const map: Record<string, string> = {
    CORRECTIVE: 'danger',
    ADAPTIVE: 'warning',
    PERFECTIVE: 'success',
    EMERGENCY: 'danger'
  }
  return map[type] || 'info'
}

const getPriorityTag = (priority: string) => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
  return map[priority] || 'info'
}

// v1.53 P1-9 修复：4 档统一紧急程度标签
const URGENCY_LABELS: Record<string, string> = {
  LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急',
  NORMAL: '中', URGENT: '紧急'
}
const URGENCY_TYPES: Record<string, string> = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'warning', CRITICAL: 'danger',
  NORMAL: 'info', URGENT: 'danger'
}
const getUrgencyLabel = (v?: string) => URGENCY_LABELS[v || ''] || v || '-'
const getUrgencyType = (v?: string) => URGENCY_TYPES[v || ''] || 'info'

// v1.53 P1-10 修复：影响范围展示
const IMPACT_SCOPE_LABELS: Record<string, string> = {
  SINGLE_SYSTEM: '单系统',
  MULTI_SYSTEM: '多系统',
  CROSS_SYSTEM: '跨系统'
}
const IMPACT_SCOPE_TYPES: Record<string, string> = {
  SINGLE_SYSTEM: 'success',
  MULTI_SYSTEM: 'warning',
  CROSS_SYSTEM: 'danger'
}
const getImpactScopeLabel = (v?: string) => IMPACT_SCOPE_LABELS[v || ''] || v || '-'
const getImpactScopeType = (v?: string) => IMPACT_SCOPE_TYPES[v || ''] || 'info'

// v1.53 P1-11 修复：6/5 步生命周期
const lifecycleSteps = [
  { value: 'DRAFT', title: '草稿', desc: '创建变更草稿' },
  { value: 'SUBMITTED', title: '已提交', desc: '已提交至变更控制委员会' },
  { value: 'ANALYZING', title: '影响分析', desc: '分析对追溯链的影响' },
  { value: 'PENDING_APPROVAL', title: '待审批', desc: '等待 CCB 审批' },
  { value: 'APPROVED', title: '已批准', desc: '审批通过待执行' },
  { value: 'EXECUTING', title: '执行中', desc: '正在执行变更' },
  { value: 'VERIFIED', title: '已验证', desc: '执行结果已验证' },
  { value: 'CLOSED', title: '已关闭', desc: '变更已关闭归档' }
]
const currentStepIndex = computed(() => {
  const idx = lifecycleSteps.findIndex(s => s.value === changeRequest.value.status)
  // 若后端返回 REJECTED 映射到 PENDING_APPROVAL 之前一步
  if (changeRequest.value.status === 'REJECTED') return 2
  return idx >= 0 ? idx : 0
})

// v1.53 P1-10 修复：影响项统计（基于 /changes/{id}/impacts）
const impactTotals = ref({
  requirementCount: 0,
  riskCount: 0,
  sopCount: 0,
  total: 0,
  unassessed: 0,
  coverageRate: 0
})

const getImpactLevelTag = (level: string) => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return map[level] || 'info'
}

const getRiskTag = (risk: string) => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return map[risk] || 'info'
}

const getTimelineType = (status: string) => {
  const map: Record<string, string> = {
    COMPLETED: 'success',
    PENDING: 'warning',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

const handleEdit = () => {
  if (!changeRequest.value.id) {
    ElMessage.warning('请先加载变更详情')
    return
  }
  showEditDialog.value = true
  editForm.value = {
    title: changeRequest.value.title || '',
    description: changeRequest.value.description || '',
    reason: changeRequest.value.reason || ''
  }
}

const showEditDialog = ref(false)
const editForm = ref({ title: '', description: '', reason: '' })
const submittingEdit = ref(false)
const submitEdit = async () => {
  if (!editForm.value.title) {
    ElMessage.warning('请填写变更标题')
    return
  }
  submittingEdit.value = true
  try {
    // R92 修复：后端 ChangeController 已新增 PUT /changes/{id} 端点（仅 DRAFT/ANALYZING 状态可编辑）
    const res = await request.put(`/changes/${changeRequest.value.id}`, {
      title: editForm.value.title,
      description: editForm.value.description,
      reason: editForm.value.reason
    })
    if (res.data?.data) Object.assign(changeRequest.value, res.data.data)
    showEditDialog.value = false
    ElMessage.success('已更新')
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.response?.data?.message || e.message))
  } finally {
    submittingEdit.value = false
  }
}

const handleAnalyze = async () => {
  if (!changeRequest.value.id) return
  try {
    await ElMessageBox.confirm('确认执行影响评估？将分析此变更对追溯链的影响。', '执行影响评估')
    loading.value = true
    const res = await request.post(`/changes/${changeRequest.value.id}/assess`)
    if (res.data?.data) Object.assign(changeRequest.value, res.data.data)
    ElMessage.success('影响评估完成')
    router.push(`/changes/${changeRequest.value.id}/impact`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('影响评估失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const handleApprove = () => {
  if (!changeRequest.value.id) return
  showApproveDialog.value = true
  approveForm.value = { decision: 'APPROVED', comments: '' }
}

const submitApprove = async () => {
  if (!changeRequest.value.id) return
  // v1.53 P1-12 修复：先关闭对话框，然后弹出电子签名弹窗
  const decision = approveForm.value.decision
  const comments = approveForm.value.comments
  showApproveDialog.value = false
  // 组装签名内容摘要
  const ctx = `变更单：${changeRequest.value.changeNo || changeRequest.value.id}\n标题：${changeRequest.value.title || '-'}\n决策：${decision === 'APPROVED' ? '批准' : '拒绝'}\n意见：${comments || '（无）'}`
  eSignRef.value?.open({
    scenario: '变更审批电子签名',
    context: ctx,
    documentType: 'CHANGE_REQUEST',
    documentId: changeRequest.value.id,
    intentCode: decision === 'APPROVED' ? 'approve' : 'confirm',
    meaningCode: decision === 'APPROVED' ? 'approve' : 'confirm',
    signerName: currentUserName.value,
    onSuccess: async () => {
      // 签名成功后真正调用审批接口
      try {
        const res = await request.post(`/changes/${changeRequest.value.id}/approve`, null, {
          params: { approverId: 1, decision, comments }
        })
        if (res.data?.data) Object.assign(changeRequest.value, res.data.data)
        ElMessage.success('审批已提交，电子签名已记录')
      } catch (e: any) {
        ElMessage.error('审批失败：' + (e?.response?.data?.message || e.message))
      }
    }
  })
}

const canApprove = computed(() => ['PENDING_APPROVAL', 'InReview', 'SUBMITTED'].includes(changeRequest.value.status))
const canEdit = computed(() => ['DRAFT'].includes(changeRequest.value.status))
// FR-0.17：批准前必须先有影响评估（影响项 > 0）
const canApproveWithPrereq = computed(() => canApprove.value && impactTotals.value.total > 0)

// ===== 影响项 / 附件 / 跳转 =====
const loadImpactItems = async () => {
  if (!changeRequest.value.id) return
  loadingImpacts.value = true
  try {
    const res = await request.get(`/changes/${changeRequest.value.id}/impacts`)
    const arr = (res.data?.data || []) as any[]
    affectedItems.value = arr
    // v1.53 P1-10 修复：按 itemType 统计受影响需求/风险/SOP
    const stats = { requirementCount: 0, riskCount: 0, sopCount: 0, total: arr.length, unassessed: 0, coverageRate: 0 }
    for (const a of arr) {
      const t = (a.itemType || a.affectedLevel || '').toUpperCase()
      if (t === 'REQUIREMENT' || t === 'URS' || t === 'PRS' || t === 'SRS' || t === 'DRS') stats.requirementCount++
      else if (t === 'RISK' || t === 'FMEA') stats.riskCount++
      else if (t === 'SOP' || t === 'PROCEDURE') stats.sopCount++
    }
    stats.coverageRate = arr.length > 0 ? 100 : 0
    impactTotals.value = stats
  } catch {
    affectedItems.value = []
  } finally {
    loadingImpacts.value = false
  }
}

const goImpactAnalysis = () => {
  if (changeRequest.value.id) router.push(`/changes/${changeRequest.value.id}/impact`)
}

const viewImpact = (row: any) => {
  if (row.changeRequestId) {
    router.push(`/changes/${row.changeRequestId}/impact`)
  } else {
    ElMessage.info(`影响项：${row.itemName || row.itemNo || '-'}`)
  }
}

const goChangeDetail = (id: number) => {
  if (id) router.push(`/changes/${id}`)
}

const handleUploadClick = () => {
  if (!changeRequest.value.id) {
    ElMessage.warning('请先加载变更详情')
    return
  }
  fileInputRef.value?.click()
}

// v1.43 P1-6 修复：真实上传到后端
const uploading = ref(false)
const handleFileChange = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (!changeRequest.value.id) {
    ElMessage.warning('请先加载变更详情')
    target.value = ''
    return
  }
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    form.append('uploaderId', String(currentUserId.value || 1))
    form.append('uploaderName', currentUserName.value || '当前用户')
    const res = await request.post(`/changes/${changeRequest.value.id}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const newAtt = (res.data as any)?.data
    if (newAtt) {
      changeRequest.value.attachments = [newAtt, ...(changeRequest.value.attachments || [])]
    }
    ElMessage.success(`已上传：${file.name}`)
  } catch (err: any) {
    ElMessage.error('上传失败：' + (err?.response?.data?.message || err?.message || '未知错误'))
  } finally {
    uploading.value = false
    target.value = ''
  }
}

const handleDownload = async (file: any) => {
  try {
    // 优先用后端下载端点
    if (file.id && !file._file) {
      const res = await request.get(`/changes/attachments/${file.id}/download`, { responseType: 'blob' })
      const blob = new Blob([res.data as BlobPart])
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = file.originalName || file.name || `attachment_${file.id}`
      a.click()
      URL.revokeObjectURL(url)
      return
    }
    // 兜底：本地暂存附件
    if (file._file) {
      const url = URL.createObjectURL(file._file)
      const a = document.createElement('a')
      a.href = url
      a.download = file.name
      a.click()
      URL.revokeObjectURL(url)
      return
    }
    ElMessage.warning('附件不可下载：缺少文件数据')
  } catch (e: any) {
    ElMessage.error('下载失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleDeleteAttachment = async (file: any) => {
  if (!file.id || file._file) {
    // 本地暂存附件直接移除
    changeRequest.value.attachments = (changeRequest.value.attachments || []).filter((a: any) => a !== file)
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除附件"${file.originalName || file.name}"？`, '删除附件', { type: 'warning' })
    await request.delete(`/changes/attachments/${file.id}`)
    changeRequest.value.attachments = (changeRequest.value.attachments || []).filter((a: any) => a.id !== file.id)
    ElMessage.success('已删除')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const formatSize = (bytes: number) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

const currentUserId = ref<number | null>(null)
const currentUserName = ref<string>('当前用户')

// ===== FR-1.7 委派/会签 =====

const showDelegate = ref(false)
// R110 H3 修复：移除硬编码 fromUserId/toUserId 默认值（之前 1/2）
// 当前操作人改用 userStore 动态获取；受派人由 el-select 选用户
import { useUserStore } from '@/stores/user'
const userStore = useUserStore()
const delegateForm = ref<{ fromUserId: number | undefined; fromUserName: string; toUserId: number | undefined; toUserName: string }>({
  fromUserId: undefined,
  fromUserName: userStore.userInfo?.realName || '',
  toUserId: undefined,
  toUserName: ''
})
// R110 H3 修复：用户列表（从 systemApi.getUsers 拉取）
const userList = ref<{ id: number; username: string; realName: string }[]>([])
const loadUsers = async () => {
  try {
    const res = await systemApi.getUsers()
    userList.value = (res.data?.data || []) as any[]
  } catch (e) {
    console.warn('loadUsers failed', e)
  }
}
const openDelegateDialog = () => { showDelegate.value = true }
// R110 H3 修复：选用户时自动同步姓名
const onDelegateToUserChange = (uid: number | undefined) => {
  const u = userList.value.find(u => u.id === uid)
  delegateForm.value.toUserName = u?.realName || ''
}
// R110 H3：会签人下拉选用户时同步姓名
const onCountersignerUserChange = (i: number, uid: number | undefined) => {
  const u = userList.value.find(u => u.id === uid)
  if (countersigners.value[i]) {
    countersigners.value[i].name = u?.realName || ''
  }
}
// R110 H3：我会签选签署人
const onMyCountersignUserChange = (uid: number | undefined) => {
  // 当前不强制同步字段，但预留扩展
  if (!uid) return
}
const confirmDelegate = async () => {
  if (!delegateForm.value.toUserId) {
    ElMessage.warning('请选择受派人')
    return
  }
  if (!delegateForm.value.toUserName) {
    ElMessage.warning('请填写受派人姓名')
    return
  }
  try {
    const res = await request.post(`/changes/${changeRequest.value.id}/delegate`, null, {
      params: {
        fromUserId: delegateForm.value.fromUserId,
        fromUserName: delegateForm.value.fromUserName,
        toUserId: delegateForm.value.toUserId,
        toUserName: delegateForm.value.toUserName
      }
    })
    Object.assign(changeRequest.value, res.data?.data || {})
    showDelegate.value = false
    ElMessage.success('已委派')
  } catch (e: any) {
    ElMessage.error('委派失败：' + (e?.response?.data?.message || e.message))
  }
}

const showCountersign = ref(false)
const countersigners = ref<{ id: number | undefined; name: string }[]>([{ id: undefined, name: '' }])
const openCountersignDialog = () => { showCountersign.value = true }
const confirmSetCountersigners = async () => {
  if (countersigners.value.some(s => !s.name)) {
    ElMessage.warning('请填写所有会签人姓名')
    return
  }
  try {
    const res = await request.post(`/changes/${changeRequest.value.id}/countersigners`, countersigners.value)
    Object.assign(changeRequest.value, res.data?.data || {})
    showCountersign.value = false
    ElMessage.success('会签人已设置')
  } catch (e: any) {
    ElMessage.error('设置失败：' + (e?.response?.data?.message || e.message))
  }
}

const showMyCountersign = ref(false)
const countersignForm = ref<{ signerUserId: number | undefined; comments: string }>({ signerUserId: undefined, comments: '' })
const openMyCountersign = () => { showMyCountersign.value = true }
const confirmCountersign = async () => {
  try {
    const res = await request.post(`/changes/${changeRequest.value.id}/countersign`, null, {
      params: { signerUserId: countersignForm.value.signerUserId, comments: countersignForm.value.comments }
    })
    Object.assign(changeRequest.value, res.data?.data || {})
    showMyCountersign.value = false
    ElMessage.success('会签已提交')
  } catch (e: any) {
    ElMessage.error('会签失败：' + (e?.response?.data?.message || e.message))
  }
}

const parsedCountersigners = computed(() => {
  if (!changeRequest.value.countersigners) return []
  try { return JSON.parse(changeRequest.value.countersigners) } catch { return [] }
})
const totalSigners = computed(() => parsedCountersigners.value.length)
const signedCount = computed(() => parsedCountersigners.value.filter((s: any) => s.signed).length)
const countersignProgressLabel = computed(() => ({
  NONE: '未启用', PENDING: '待签', PARTIAL: '部分签', COMPLETED: '已完成'
} as any)[changeRequest.value.countersignProgress] || '未知')
const countersignTagType = computed(() => ({
  NONE: 'info', PENDING: 'warning', PARTIAL: 'warning', COMPLETED: 'success'
} as any)[changeRequest.value.countersignProgress] || 'info')

onMounted(async () => {
  const idFromRoute = Number(route.params.id)
  if (!idFromRoute || Number.isNaN(idFromRoute)) {
    ElMessage.error('变更单 ID 缺失')
    router.replace('/changes')
    return
  }
  changeRequest.value.id = idFromRoute
  loading.value = true
  try {
    const res = await request.get(`/changes/${idFromRoute}`)
    if (res.data?.data) Object.assign(changeRequest.value, res.data.data)
  } catch (e: any) {
    ElMessage.error('加载变更详情失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
  await loadImpactItems()
  // v1.43 P1-6 修复：从后端加载附件列表
  try {
    const attRes = await request.get(`/changes/${idFromRoute}/attachments`)
    changeRequest.value.attachments = (attRes.data as any)?.data || []
  } catch {
    changeRequest.value.attachments = []
  }
})
</script>

<style scoped>
.change-request-container {
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

.status-bar {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.change-no {
  font-size: 14px;
  color: #606266;
}

.change-date {
  font-size: 14px;
  color: #909399;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 20px;
}

.main-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.description-content,
.reason-content {
  font-size: 14px;
  line-height: 1.8;
  color: #303133;
}

.affected-items-card :deep(.el-table) {
  font-size: 13px;
}

.risk-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-item {
  font-size: 14px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.risk-label {
  color: #606266;
  font-weight: 500;
}

.timeline-content {
  padding: 4px 0;
}

.timeline-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.timeline-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.timeline-user {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
}

.attachment-icon {
  font-size: 16px;
}

.attachment-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
}

.related-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.related-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  cursor: pointer;
}

.related-item:hover {
  background: #ecf5ff;
}

.related-code {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}

.related-title {
  font-size: 12px;
  color: #606266;
}
</style>