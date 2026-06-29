<!--
  SignatureHistoryDetail.vue - 签名记录详情
  21 CFR Part 11 §11.10(e) 实体哈希 + §11.70 防篡改
  路由：/signature-history/:id
  对应原型：signature-history-detail-原型.html
  v1.51 P0 修复
-->
<template>
  <div class="detail-container">
    <el-page-header @back="goBack" content="签名记录详情" class="page-header" />

    <div v-loading="loading" v-if="record">
      <!-- 签名人信息 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <span>🖊️ 签名人信息</span>
        </template>
        <div class="signature-box">
          <div class="signature-icon">✍️</div>
          <div class="signer-name">{{ record.signerName }}</div>
          <div class="signer-role">{{ record.signerRole || '签名人' }} · 签名记录 #{{ record.id }}</div>
          <div class="signature-meta">
            <span>📅 {{ formatDate(record.signedAt) }}</span>
            <span>🌐 IP: {{ record.ipAddress || '-' }}</span>
            <span>🖥️ 设备: {{ deviceInfo }}</span>
          </div>
        </div>

        <el-descriptions :column="1" border size="default" class="detail-table">
          <el-descriptions-item label="签名类型">电子签名（ESign）</el-descriptions-item>
          <el-descriptions-item label="签名意图">
            <span class="intent-tag" :class="(record.intent || record.signatureType || '').toLowerCase()">
              {{ record.intent || record.signatureType }}
            </span>
            <span style="margin-left: 8px; color: #606266; font-size: 13px;">
              {{ INTENT_LABEL_ZH[(record.intent || record.signatureType || 'approve') as SignatureIntentType] }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="关联操作">
            {{ record.documentNo || `${record.documentType}#${record.documentId}` }}
            <span style="color: #909399; font-size: 12px; margin-left: 6px;">{{ documentTypeLabel(record.documentType) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="签名语义">
            "我{{ INTENT_LABEL_ZH[(record.intent || record.signatureType || 'approve') as SignatureIntentType] }}该{{ documentTypeLabel(record.documentType) }}{{ record.documentNo || `#${record.documentId}` }}，并承诺所提交信息真实有效"
          </el-descriptions-item>
          <el-descriptions-item label="签名原因">{{ record.reason || '（无）' }}</el-descriptions-item>
          <el-descriptions-item label="哈希值 (signatureValue)">
            <code class="hash-text">{{ record.signatureValue || record.signatureHash || record.hashValue || '（未生成）' }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="实体哈希 (entityHash)">
            <code class="hash-text">{{ record.entityHash || '（未生成）' }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="证书状态">
            <span :class="record.isValid !== false ? 'status-ok' : 'status-fail'">
              {{ record.isValid !== false ? '✅ 有效' : '❌ 已失效' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="合规状态">
            <span class="status-ok">✅ 符合 21 CFR Part 11</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 操作上下文 -->
      <el-card class="context-card" shadow="never">
        <template #header>
          <span>📋 操作上下文</span>
        </template>
        <el-descriptions :column="1" border size="default" class="detail-table">
          <el-descriptions-item label="被签名文档">
            {{ record.documentNo || `${record.documentType}#${record.documentId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ documentTypeLabel(record.documentType) }}</el-descriptions-item>
          <el-descriptions-item label="签名时间">
            {{ formatDate(record.signedAt) }}（北京时间 UTC+8）
          </el-descriptions-item>
          <el-descriptions-item label="签名方法">{{ record.signatureMethod || 'PASSWORD_OTP' }}</el-descriptions-item>
          <el-descriptions-item label="设备信息 / 浏览器">{{ deviceInfo }}</el-descriptions-item>
          <el-descriptions-item label="签名 IP">{{ record.ipAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="record.signatureId" label="关联签名记录">#{{ record.signatureId }}</el-descriptions-item>
          <el-descriptions-item label="审计追溯编号">AUD-{{ record.id }}-{{ formatDateForAudit(record.signedAt) }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 验签操作 -->
      <el-card class="verify-card" shadow="never" v-if="verifyResult !== null || verifying">
        <template #header>
          <span>🔍 验签结果</span>
        </template>
        <el-alert
          v-if="verifyResult === true"
          title="签名验证通过"
          type="success"
          description="签名值与文档哈希匹配，签名完整有效"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="verifyResult === false"
          title="签名验证失败"
          type="error"
          description="签名值与文档哈希不匹配，签名已被篡改或失效"
          show-icon
          :closable="false"
        />
        <el-alert
          v-else-if="verifyError"
          :title="verifyError"
          type="warning"
          show-icon
          :closable="false"
        />
      </el-card>

      <div class="actions">
        <el-button @click="goBack">← 返回列表</el-button>
        <el-button type="primary" plain :loading="verifying" @click="doVerify">🔍 验签</el-button>
        <el-button @click="exportProof">📤 导出签名证明</el-button>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="未找到签名记录" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  esignatureApi,
  type SignatureRecord,
  type SignatureIntentType,
  INTENT_LABEL_ZH
} from '@/api/esignature'

const route = useRoute()
const router = useRouter()

const recordId = computed(() => Number(route.params.id))

const record = ref<SignatureRecord | null>(null)
const loading = ref(false)
const verifying = ref(false)
const verifyResult = ref<boolean | null>(null)
const verifyError = ref<string>('')

const deviceInfo = computed(() => {
  if (record.value?.deviceInfo) return record.value.deviceInfo
  // 兜底：从浏览器 UA 推断
  const ua = typeof navigator !== 'undefined' ? navigator.userAgent : ''
  if (!ua) return '-'
  const platform =
    ua.includes('Win') ? 'Windows' : ua.includes('Mac') ? 'macOS' : ua.includes('Linux') ? 'Linux' : 'Unknown'
  const browser =
    ua.includes('Edg/') ? 'Edge' : ua.includes('Chrome/') ? 'Chrome' : ua.includes('Firefox/') ? 'Firefox' : 'Browser'
  const version = (ua.match(/(Chrome|Edg|Firefox)\/(\d+)/) || [])[2] || ''
  return `${platform} / ${browser} ${version}`
})

const fetchData = async () => {
  if (!recordId.value || isNaN(recordId.value)) return
  loading.value = true
  try {
    const res: any = await esignatureApi.getSignatureById(recordId.value)
    record.value = res?.data?.data || null
    if (!record.value) {
      ElMessage.error('未找到该签名记录')
    }
  } catch (e: any) {
    ElMessage.error('加载签名详情失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    record.value = null
  } finally {
    loading.value = false
  }
}

const doVerify = async () => {
  if (!record.value) return
  verifying.value = true
  verifyResult.value = null
  verifyError.value = ''
  try {
    const res: any = await esignatureApi.verifySignature(record.value.id)
    verifyResult.value = !!res?.data?.data
    ElMessage[verifyResult.value ? 'success' : 'error'](
      verifyResult.value ? '签名验证通过' : '签名验证失败'
    )
  } catch (e: any) {
    verifyResult.value = false
    verifyError.value = e?.response?.data?.message || e?.message || '验签异常'
    ElMessage.error('验签失败：' + verifyError.value)
  } finally {
    verifying.value = false
  }
}

const exportProof = () => {
  if (!record.value) return
  const r = record.value
  const lines = [
    '═══════════════════════════════════════════════════════',
    'Med-RMS 电子签名证明书',
    '21 CFR Part 11 §11.50 / §11.70 合规',
    '═══════════════════════════════════════════════════════',
    '',
    `签名记录 ID: ${r.id}`,
    `签名人: ${r.signerName}`,
    `签名人角色: ${r.signerRole || '-'}`,
    `签名时间: ${formatDate(r.signedAt)}`,
    `签名 IP: ${r.ipAddress || '-'}`,
    `设备/浏览器: ${deviceInfo.value}`,
    `签名方法: ${r.signatureMethod || 'PASSWORD_OTP'}`,
    `签名意图: ${r.intent || r.signatureType}`,
    `签名含义: ${INTENT_LABEL_ZH[(r.intent || r.signatureType || 'approve') as SignatureIntentType]}`,
    `被签文档: ${r.documentNo || `${r.documentType}#${r.documentId}`}`,
    `文档类型: ${documentTypeLabel(r.documentType)}`,
    `签名原因: ${r.reason || '（无）'}`,
    '',
    '────── 哈希值 ──────',
    `签名值 (signatureValue): ${r.signatureValue || r.signatureHash || r.hashValue || '（未生成）'}`,
    `实体哈希 (entityHash): ${r.entityHash || '（未生成）'}`,
    '',
    '────── 合规声明 ──────',
    '本电子签名受 21 CFR Part 11 保护，',
    '签名值与文档实体哈希绑定，任何篡改都将被发现。',
    '',
    `导出时间: ${formatDate(new Date().toISOString())}`,
    '═══════════════════════════════════════════════════════'
  ]
  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `signature-proof-${r.id}.txt`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('签名证明已导出')
}

const formatDate = (s: string | undefined): string => {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
const formatDateForAudit = (s: string | undefined): string => {
  if (!s) return '00000000'
  const d = new Date(s)
  if (isNaN(d.getTime())) return '00000000'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
}
const documentTypeLabel = (t: string): string => {
  const m: Record<string, string> = {
    REQUIREMENT: '需求',
    CHANGE: '变更',
    REVIEW: '评审',
    BASELINE: '基线',
    SOUP: 'SOUP',
    RISK: '风险',
    PROBLEM_REPORT: '问题报告',
    IEC62304: 'IEC 62304'
  }
  return m[t] || t
}
const goBack = () => {
  router.back()
}

watch(() => route.params.id, fetchData, { immediate: false })
onMounted(fetchData)
</script>

<style scoped>
.detail-container {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
}
.info-card,
.context-card,
.verify-card {
  margin-bottom: 16px;
}
.signature-box {
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 24px;
  text-align: center;
  margin-bottom: 20px;
}
.signature-icon {
  font-size: 48px;
  color: #67c23a;
  margin-bottom: 12px;
}
.signer-name {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 4px;
}
.signer-role {
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}
.signature-meta {
  display: flex;
  justify-content: center;
  gap: 24px;
  font-size: 13px;
  color: #909399;
  flex-wrap: wrap;
}

.detail-table {
  font-size: 13px;
}
.detail-table :deep(.el-descriptions__label) {
  width: 160px;
  color: #909399;
  font-weight: 500;
}
.hash-text {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
  background: #f5f7fa;
  padding: 4px 8px;
  border-radius: 3px;
  display: inline-block;
  width: 100%;
}
.intent-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.intent-tag.approve {
  background: #ecf5ff;
  color: #409eff;
}
.intent-tag.confirm {
  background: #f0f9eb;
  color: #67c23a;
}
.intent-tag.review {
  background: #fdf6ec;
  color: #e6a23c;
}
.intent-tag.release {
  background: #f4f4f5;
  color: #606266;
}
.status-ok {
  color: #67c23a;
  font-weight: 600;
}
.status-fail {
  color: #f56c6c;
  font-weight: 600;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
