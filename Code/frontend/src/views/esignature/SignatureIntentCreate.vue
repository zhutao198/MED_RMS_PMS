<!--
  SignatureIntentCreate.vue - 发起签名意图
  21 CFR Part 11 §11.200 访问控制时效性
  路由：/signature-intent/create
  对应原型：signature-intent-create-原型.html
  v1.51 P0 修复
-->
<template>
  <div class="intent-create-container">
    <el-page-header @back="goBack" content="发起签名意图" class="page-header" />

    <el-card shadow="never" class="form-card">
      <template #header>
        <span>📋 发起签名意图</span>
      </template>

      <!-- 待签名实体 -->
      <div class="entity-card">
        <div class="entity-header">
          <div class="entity-icon"><el-icon><Document /></el-icon></div>
          <div>
            <div class="entity-title">{{ entityTitle }}</div>
            <div class="entity-subtitle">{{ entitySubtitle }}</div>
          </div>
        </div>
        <div class="entity-details">
          <div class="entity-detail">
            <div class="entity-detail-value">{{ currentUserName }}</div>
            <div class="entity-detail-label">发起人</div>
          </div>
          <div class="entity-detail">
            <div class="entity-detail-value">{{ formData.signerName || '（待定）' }}</div>
            <div class="entity-detail-label">当前签名人</div>
          </div>
          <div class="entity-detail">
            <div class="entity-detail-value">{{ todayStr }}</div>
            <div class="entity-detail-label">创建日期</div>
          </div>
          <div class="entity-detail">
            <div class="entity-detail-value">{{ formData.intentType === 'multi' ? '多人' : '单人' }}</div>
            <div class="entity-detail-label">签名类型</div>
          </div>
        </div>
      </div>

      <!-- 签名意图选择 -->
      <el-form :model="formData" :rules="formRules" ref="formRef" label-position="top">
        <el-form-item label="文档类型" prop="documentType" required>
          <el-select v-model="formData.documentType" placeholder="请选择文档类型" style="width: 100%;">
            <el-option v-for="t in DOCUMENT_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="文档 ID" prop="documentId" required>
          <el-input v-model.number="formData.documentId" placeholder="请输入被签名文档的 ID" type="number" />
        </el-form-item>

        <el-form-item label="签名意图" prop="intentCode" required>
          <el-radio-group v-model="formData.intentCode" class="intent-radio-group">
            <el-radio-button
              v-for="it in SIGNATURE_INTENTS"
              :key="it"
              :value="it"
              class="intent-radio-option"
            >
              <div class="intent-icon">{{ intentIcon[it] }}</div>
              <div class="intent-label">{{ it }}</div>
              <div class="intent-desc">{{ INTENT_LABEL_ZH[it] }}</div>
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="签名含义" prop="meaningCode" required>
          <el-select v-model="formData.meaningCode" placeholder="请选择签名含义" style="width: 100%;">
            <el-option v-for="m in SIGNATURE_MEANINGS" :key="m" :label="`${m} - ${MEANING_LABEL_ZH[m]}`" :value="m" />
          </el-select>
        </el-form-item>

        <el-form-item label="签名人" prop="signerName" required>
          <el-input v-model="formData.signerName" placeholder="请输入签名人姓名" />
        </el-form-item>

        <el-form-item label="签名人角色" prop="signerRole">
          <el-input v-model="formData.signerRole" placeholder="例如：质量负责人（QA Lead）" />
        </el-form-item>

        <el-form-item label="签名意图说明" prop="reason" required>
          <el-input
            v-model="formData.reason"
            type="textarea"
            :rows="3"
            placeholder="说明本次签名的意图，如：确认变更请求CR-2025-0047经影响分析和审批流程后获批，请批准执行。"
          />
        </el-form-item>

        <el-form-item label="预计完成时间" prop="expectedDuration">
          <el-select v-model="formData.expectedDuration" style="width: 200px;">
            <el-option label="1 小时内" value="1h" />
            <el-option label="4 小时内" value="4h" />
            <el-option label="24 小时内" value="24h" />
            <el-option label="3 个工作日内" value="3d" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 提示 -->
      <el-alert
        class="notice"
        type="warning"
        :closable="false"
        show-icon
        title="签名意图提示"
        description="签名意图发起后，系统将发送邮件/站内通知给签名人。签名人需在预计时间内完成签名，否则系统将发送催签通知。签名记录将永久保存在审计日志中，不可篡改。"
      />

      <div class="actions">
        <el-button @click="goBack">← 取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">
          <el-icon><Position /></el-icon> 发起签名请求
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Document, Position } from '@element-plus/icons-vue'
import {
  esignatureApi,
  type SignatureIntentType,
  type SignatureMeaningType,
  SIGNATURE_INTENTS,
  SIGNATURE_MEANINGS,
  INTENT_LABEL_ZH,
  MEANING_LABEL_ZH
} from '@/api/esignature'

const route = useRoute()
const router = useRouter()

const DOCUMENT_TYPES = [
  { value: 'REQUIREMENT', label: '需求 (REQUIREMENT)' },
  { value: 'CHANGE', label: '变更 (CHANGE)' },
  { value: 'REVIEW', label: '评审 (REVIEW)' },
  { value: 'BASELINE', label: '基线 (BASELINE)' },
  { value: 'SOUP', label: 'SOUP' },
  { value: 'RISK', label: '风险 (RISK)' },
  { value: 'PROBLEM_REPORT', label: '问题报告 (PROBLEM_REPORT)' },
  { value: 'IEC62304', label: 'IEC 62304' }
]

const intentIcon: Record<SignatureIntentType, string> = {
  approve: '✅',
  confirm: '🔄',
  review: '👁',
  release: '🔒'
}

const formRef = ref<FormInstance>()
const submitting = ref(false)

const formData = reactive({
  documentType: (route.query.documentType as string) || 'REQUIREMENT',
  documentId: Number(route.query.documentId || 0),
  intentCode: 'approve' as SignatureIntentType,
  meaningCode: 'approve' as SignatureMeaningType,
  signerName: '',
  signerRole: '',
  reason: '',
  expectedDuration: '1h',
  intentType: 'single'
})

const formRules: FormRules = {
  documentType: [{ required: true, message: '请选择文档类型', trigger: 'change' }],
  documentId: [{ required: true, type: 'number', message: '请输入文档 ID', trigger: 'blur' }],
  intentCode: [{ required: true, message: '请选择签名意图', trigger: 'change' }],
  meaningCode: [{ required: true, message: '请选择签名含义', trigger: 'change' }],
  signerName: [{ required: true, message: '请输入签名人姓名', trigger: 'blur' }],
  reason: [{ required: true, message: '请输入签名意图说明', trigger: 'blur' }]
}

const currentUserName = computed(() => {
  try {
    const u = JSON.parse(localStorage.getItem('userInfo') || '{}')
    return u.realName || u.username || '当前用户'
  } catch {
    return '当前用户'
  }
})

const todayStr = computed(() => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})

const entityTitle = computed(() => {
  if (formData.documentType === 'REQUIREMENT') return '需求审批'
  if (formData.documentType === 'CHANGE') return '变更请求审批'
  if (formData.documentType === 'BASELINE') return '基线锁定'
  if (formData.documentType === 'REVIEW') return '评审通过'
  if (formData.documentType === 'PROBLEM_REPORT') return '问题报告关闭'
  if (formData.documentType === 'SOUP') return 'SOUP 更新确认'
  return `${formData.documentType} 文档签名`
})
const entitySubtitle = computed(() => {
  if (!formData.documentId) return `${formData.documentType} · 文档 ID 待定`
  return `${formData.documentType} #${formData.documentId}`
})

const submitCreate = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.error('请填写必填项')
    return
  }

  if (!formData.documentId || formData.documentId <= 0) {
    ElMessage.error('请输入有效的文档 ID')
    return
  }

  submitting.value = true
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const userId = Number(userInfo.id || 0)
    if (!userId) {
      ElMessage.error('无法获取当前用户信息，请重新登录')
      submitting.value = false
      return
    }
    const res: any = await esignatureApi.createIntent({
      requesterId: userId,
      signerId: userId, // v1.47 兼容别名
      documentType: formData.documentType,
      documentId: formData.documentId,
      intentCode: formData.intentCode,
      meaningCode: formData.meaningCode
    })
    const intent = res?.data?.data
    if (intent?.id) {
      ElMessage.success(`签名意图已创建，ID = ${intent.id}`)
      router.push({ name: 'SignatureIntentDetail', params: { id: String(intent.id) } })
    } else {
      ElMessage.warning('签名意图已提交，但未返回 ID')
      router.push({ name: 'Signatures' })
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '未知错误'
    ElMessage.error('创建签名意图失败：' + msg)
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  // 预填 URL 参数
  if (route.query.documentType) formData.documentType = String(route.query.documentType)
  if (route.query.documentId) formData.documentId = Number(route.query.documentId)
  if (route.query.intentCode) formData.intentCode = String(route.query.intentCode) as SignatureIntentType
  if (route.query.meaningCode) formData.meaningCode = String(route.query.meaningCode) as SignatureMeaningType
})
</script>

<style scoped>
.intent-create-container {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
}
.form-card {
  max-width: 880px;
  margin: 0 auto;
}

.entity-card {
  border: 2px solid #e4e7ed;
  border-radius: 4px;
  padding: 20px;
  margin-bottom: 20px;
  position: relative;
}
.entity-card::before {
  content: '待签名的文档';
  position: absolute;
  top: -10px;
  left: 16px;
  background: #fff;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  color: #909399;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.entity-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.entity-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #e3f2fd;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  font-size: 20px;
}
.entity-title {
  font-size: 15px;
  font-weight: 600;
}
.entity-subtitle {
  font-size: 12px;
  color: #909399;
}
.entity-details {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #e4e7ed;
}
.entity-detail {
  text-align: center;
}
.entity-detail-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.entity-detail-label {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.intent-radio-group {
  display: grid !important;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  width: 100%;
}
.intent-radio-option {
  margin: 0;
}
.intent-radio-option :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 16px 8px;
  border: 2px solid #e4e7ed !important;
  border-radius: 8px !important;
  text-align: center;
  white-space: normal;
  display: block;
}
.intent-radio-option.is-active :deep(.el-radio-button__inner) {
  border-color: #409eff !important;
  background: #e3f2fd !important;
  box-shadow: none !important;
  color: #303133;
}
.intent-icon {
  font-size: 24px;
  margin-bottom: 6px;
}
.intent-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.intent-desc {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

.notice {
  margin: 20px 0;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}
</style>
