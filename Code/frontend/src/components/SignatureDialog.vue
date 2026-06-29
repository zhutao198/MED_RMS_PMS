<!--
  SignatureDialog - 通用电子签名弹窗
  依据：21 CFR Part 11 §11.50（签名意图明确声明） + §11.70（签名与记录关联）
  说明：弹窗本身只做凭证采集与签名提交，业务侧的"创建签名意图"由调用方控制；
        弹窗接受已创建好的 intentId，签名成功后通过 onSigned 回调通知父组件。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="580px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    class="es-dialog"
    @update:model-value="onModelUpdate"
    @closed="onDialogClosed"
  >
    <!-- 签名内容区 -->
    <div v-if="!signSuccess" class="sign-content">
      <div class="sign-doc-title">
        {{ docTitle || `${context.entityType} #${context.entityId}` }}
      </div>
      <div class="sign-doc-meta">
        签名时间：{{ signTime }} | 当前用户：{{ userStore.userInfo?.realName || '未登录' }}
      </div>
      <div class="sign-context-box" v-if="signContext">
        <div class="context-label">📋 签名对象内容摘要</div>
        {{ signContext }}
      </div>
    </div>

    <!-- 签名意图选择 + 凭证 -->
    <div v-if="!signSuccess" class="sign-settings">
      <div class="signer-info">
        <div class="signer-avatar">{{ signerInitial }}</div>
        <div class="signer-details">
          <div class="signer-name">{{ userStore.userInfo?.realName || '未登录' }}</div>
          <div class="signer-role">签名角色：{{ roleLabel }}</div>
        </div>
        <el-tag size="small" type="warning">需二次确认</el-tag>
      </div>

      <div class="sign-intent-section">
        <div class="section-label">
          <span class="required-star">*</span> 签名意图（sign_intent）
          <el-tooltip content="根据21 CFR Part 11 §11.50，签名必须明确声明其含义">
            <el-icon><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <el-radio-group v-model="signForm.intent" class="intent-options">
          <el-radio-button
            v-for="opt in intentOptions"
            :key="opt.value"
            :value="opt.value"
            class="intent-option"
          >
            <div class="intent-icon">{{ opt.icon }}</div>
            <div class="intent-label">{{ opt.label }}</div>
            <div class="intent-desc">{{ opt.desc }}</div>
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="credential-section">
        <div class="credential-field">
          <div class="credential-label">
            <span class="required-star">*</span> 签名密码
            <el-tooltip content="独立于登录密码的签名专用密码">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <el-input
            v-model="signForm.password"
            type="password"
            placeholder="请输入独立签名密码（非登录密码）"
            show-password
            autocomplete="new-password"
          />
          <div class="error-tip" v-if="passwordError">
            <el-icon><CircleCloseFilled /></el-icon> 签名密码错误，请重新输入
          </div>
        </div>

        <div class="credential-field">
          <div class="credential-label">
            <span class="required-star">*</span> OTP 验证码
            <el-tooltip content="绑定的 authenticator 应用生成的 6 位动态验证码">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <el-input
            v-model="signForm.otp"
            placeholder="请输入 6 位动态验证码"
            maxlength="6"
            pattern="\d*"
          />
          <div class="error-tip" v-if="otpError">
            <el-icon><CircleCloseFilled /></el-icon> OTP 验证码错误或已过期
          </div>
        </div>
      </div>

      <!-- 签名预览 -->
      <div class="sign-preview">
        <div class="preview-sig-text">{{ userStore.userInfo?.realName?.slice(0, 4) || 'Sign' }}</div>
        <div class="preview-info">
          <div class="preview-meaning">签名含义：{{ signForm.intent }} — {{ intentMeaning[signForm.intent] }}</div>
          <div class="preview-time">{{ signTime }}</div>
        </div>
      </div>
    </div>

    <!-- 合规声明 -->
    <div v-if="!signSuccess" class="compliance-notice">
      <div class="compliance-title">
        <el-icon><WarningFilled /></el-icon> 21 CFR Part 11 合规声明
      </div>
      <div class="compliance-text">
        <p>本电子签名受保护，不可否认。签署人：(1) 已审查所签署的文档内容；(2) 理解签名的法律效力；
          (3) 其签名意图与所选意图一致；(4) 确认使用安全的电子方式签署。</p>
        <p style="margin-top:8px;">签署后，签名记录将被存储并受哈希链保护，无法被篡改或删除。</p>
      </div>
    </div>

    <!-- 成功状态 -->
    <div v-if="signSuccess" class="success-overlay">
      <div class="success-icon">✓</div>
      <div class="success-title">电子签名提交成功</div>
      <div class="success-sub">签名已记录并加盖时间戳，符合 21 CFR Part 11 要求</div>
      <div class="success-hash" v-if="signatureHash">
        SHA-256: {{ signatureHash }}
      </div>
      <el-button type="primary" style="margin-top:20px;" @click="closeAfterSuccess">确定</el-button>
    </div>

    <template #footer v-if="!signSuccess">
      <div class="footer-wrap">
        <el-checkbox v-model="signForm.confirmRead" class="compliance-checkbox">
          我已阅读并理解合规声明
        </el-checkbox>
        <div class="footer-buttons">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" :disabled="!canSubmit || submitting" @click="submitSignature">
            {{ submitting ? '提交中…' : '提交电子签名' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * SignatureDialog 通用电子签名弹窗
 *
 * 接口约定（21 CFR Part 11 §11.50/§11.70）：
 *  - 调用方应先 esignatureApi.createIntent({documentType, documentId, meaningCode})
 *    拿到 intentId，再 open({intentId, ...}) 弹出本组件；
 *  - 签名成功后回调 onSigned({signatureId, record})，由父组件负责刷新与跳转。
 */
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { esignatureApi, type SignatureRecord } from '../api/esignature'
import { useUserStore } from '../stores/user'

// -------- 类型 --------
type SignatureIntentType = 'approve' | 'confirm' | 'review' | 'release'

interface OpenParams {
  entityType: string
  entityId: number
  intentId?: number
  meaningCode?: SignatureIntentType
  documentNo?: string
  docTitle?: string
  signContext?: string
  reason?: string
  onSigned?: (payload: { signatureId: number; record: SignatureRecord }) => void
}

// -------- Props / Emits --------
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'signed', payload: { signatureId: number; record: SignatureRecord }): void
}>()

const userStore = useUserStore()

// -------- 弹窗状态 --------
const dialogTitle = computed(() => `✍️ 电子签名（21 CFR Part 11）— ${context.value.entityType} #${context.value.entityId}`)
const context = ref<OpenParams>({
  entityType: 'requirement',
  entityId: 0,
})
const submitting = ref(false)
const signSuccess = ref(false)
const passwordError = ref(false)
const otpError = ref(false)
const signatureHash = ref('')
const signTime = ref('')

const intentOptions: { value: SignatureIntentType; label: string; desc: string; icon: string }[] = [
  { value: 'approve', label: 'approve', desc: '审批通过', icon: '✅' },
  { value: 'confirm', label: 'confirm', desc: '确认', icon: '🔄' },
  { value: 'review', label: 'review', desc: '审核', icon: '👁' },
  { value: 'release', label: 'release', desc: '发布', icon: '🔒' },
]

const intentMeaning: Record<SignatureIntentType, string> = {
  approve: '审批通过 - 确认文档/变更符合要求，同意推进流程',
  confirm: '确认 - 确认已评估影响，同意实施变更',
  review: '审核 - 确认已完成技术评审，审核意见已记录',
  release: '发布 - 确认文档/基线已就绪，同意正式发布',
}

const roleLabel = computed(() => intentMeaning[signForm.intent].split(' - ')[1] || '审批人')

const signForm = reactive({
  intent: 'approve' as SignatureIntentType,
  password: '',
  otp: '',
  confirmRead: false,
})

// 显式 const 别名：模板需 docTitle / signContext；保存签名前的 formData 供回填
const docTitle = computed(() => context.value.docTitle || '')
const signContext = computed(() => context.value.signContext || '')

const signerInitial = computed(() => (userStore.userInfo?.realName || '?').slice(0, 1))

const canSubmit = computed(() =>
  signForm.password.length >= 6 &&
  /^\d{6}$/.test(signForm.otp) &&
  signForm.confirmRead
)

// -------- 方法：open/close --------
/** 弹窗打开入口。调用方在父级通过 ref 拿到本组件实例后调用 open() */
const open = (params: OpenParams) => {
  context.value = { ...params }
  signForm.intent = (params.meaningCode || 'approve') as SignatureIntentType
  signForm.password = ''
  signForm.otp = ''
  signForm.confirmRead = false
  signSuccess.value = false
  passwordError.value = false
  otpError.value = false
  signatureHash.value = ''
  signTime.value = formatNow()
  emit('update:modelValue', true)
}

const closeAfterSuccess = () => {
  signSuccess.value = false
  emit('update:modelValue', false)
}

const handleCancel = () => {
  emit('update:modelValue', false)
}

const onModelUpdate = (v: boolean) => {
  emit('update:modelValue', v)
}

const onDialogClosed = () => {
  // 仅在外部关闭（点 X / Esc）后清空状态；内部 confirm 关闭由 closeAfterSuccess 控制
  signSuccess.value = false
  passwordError.value = false
  otpError.value = false
}

// 监听 modelValue：父级通过 v-model=false 主动关闭时，同步清空成功态
watch(
  () => props.modelValue,
  (v) => {
    if (!v) {
      signSuccess.value = false
    }
  }
)

// -------- 提交签名 --------
const submitSignature = async () => {
  if (!canSubmit.value || submitting.value) return
  passwordError.value = false
  otpError.value = false

  // 1. 若调用方未传 intentId，先在弹窗内兜底创建一次意图
  let intentId = context.value.intentId
  if (!intentId) {
    try {
      const res = await esignatureApi.createIntent({
        documentType: context.value.entityType,
        documentId: context.value.entityId,
        meaningCode: signForm.intent,
      })
      intentId = res?.data?.data?.id
    } catch (e: any) {
      ElMessage.error('创建签名意图失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
      return
    }
  }
  if (!intentId) {
    ElMessage.error('签名意图创建失败（未返回 id）')
    return
  }

  // 2. 调用后端 sign 端点（ElectronicSignatureController#sign，SignRequest）
  // 完整三段式：intentId + signaturePassword + otpCode（v1.47 §11.70）
  submitting.value = true
  try {
    const res = await esignatureApi.sign({
      documentType: context.value.entityType,
      documentId: context.value.entityId,
      signType: signForm.intent,
      intentId,
      meaningCode: signForm.intent,
      documentNo: context.value.documentNo,
      reason: context.value.reason,
      signatureMethod: 'PASSWORD_OTP',
      signaturePassword: signForm.password,
      otpCode: signForm.otp,
    })
    const record = res?.data?.data as SignatureRecord
    if (!record) throw new Error('签名返回为空')

    signatureHash.value = (record.signatureValue || record.signatureHash || '').slice(0, 16) + '...'
    signSuccess.value = true
    ElMessage.success('电子签名提交成功')

    emit('signed', { signatureId: record.id, record })
    context.value.onSigned?.({ signatureId: record.id, record })
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '未知错误'
    if (/password|密码/i.test(msg)) passwordError.value = true
    else if (/otp|验证码/i.test(msg)) otpError.value = true
    ElMessage.error('签名失败：' + msg)
  } finally {
    submitting.value = false
  }
}

// -------- 工具 --------
const formatNow = () => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

defineExpose({ open })
</script>

<style scoped>
/* 弹窗样式覆盖 */
:deep(.es-dialog .el-dialog__header) {
  background: #1a1a2e;
  margin: 0;
  padding: 16px 20px;
}
:deep(.es-dialog .el-dialog__title) {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}
:deep(.es-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #fff;
}
:deep(.es-dialog .el-dialog__body) {
  padding: 0;
}
:deep(.es-dialog .el-dialog__footer) {
  padding: 14px 20px;
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
}

/* 签名内容区 */
.sign-content {
  background: linear-gradient(135deg, #f0f7ff 0%, #fff 100%);
  padding: 20px;
  border-bottom: 1px solid #e4e7ed;
}
.sign-doc-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.sign-doc-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
}
.sign-context-box {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 14px;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}
.context-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

/* 设置区 */
.sign-settings {
  padding: 20px;
}
.signer-info {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.signer-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
}
.signer-details {
  flex: 1;
}
.signer-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.signer-role {
  font-size: 12px;
  color: #606266;
  margin-top: 2px;
}

.sign-intent-section {
  margin-bottom: 20px;
}
.section-label {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.required-star {
  color: #f56c6c;
}
.intent-options {
  display: flex;
  gap: 8px;
  width: 100%;
}
.intent-options :deep(.el-radio-button) {
  flex: 1;
}
.intent-options :deep(.el-radio-button__inner) {
  width: 100%;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.intent-icon {
  font-size: 20px;
}
.intent-label {
  font-size: 13px;
  font-weight: 600;
}
.intent-desc {
  font-size: 11px;
  color: #909399;
}

.credential-section {
  margin-bottom: 20px;
}
.credential-field {
  margin-bottom: 14px;
}
.credential-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.error-tip {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 签名预览 */
.sign-preview {
  background: linear-gradient(to right, #1a1a2e, #2d3561);
  border-radius: 6px;
  padding: 14px 16px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 12px;
}
.preview-sig-text {
  font-size: 18px;
  font-family: 'Brush Script MT', cursive, serif;
  color: #fff;
  opacity: 0.9;
}
.preview-info {
  flex: 1;
}
.preview-meaning {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}
.preview-time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
}

/* 合规声明 */
.compliance-notice {
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 6px;
  padding: 14px;
  margin: 0 20px 20px;
}
.compliance-title {
  font-size: 13px;
  font-weight: 600;
  color: #f56c6c;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.compliance-text {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
}
.compliance-text p {
  margin: 4px 0;
}

/* Footer */
.footer-wrap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}
.compliance-checkbox {
  font-size: 12px;
  color: #909399;
}
.footer-buttons {
  display: flex;
  gap: 10px;
}

/* 成功覆盖层 */
.success-overlay {
  position: relative;
  background: rgba(255, 255, 255, 0.97);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}
.success-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #67c23a;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: #fff;
  margin-bottom: 16px;
}
.success-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.success-sub {
  font-size: 13px;
  color: #909399;
  text-align: center;
  max-width: 280px;
}
.success-hash {
  margin-top: 16px;
  padding: 8px 16px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  font-family: monospace;
  word-break: break-all;
  text-align: center;
}
</style>
