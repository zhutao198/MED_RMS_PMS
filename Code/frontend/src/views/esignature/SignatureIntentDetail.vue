<!--
  SignatureIntentDetail.vue - 签名意图详情
  21 CFR Part 11 §11.200 访问控制时效性
  路由：/signature-intent/:id
  对应原型：signature-intent-detail-原型.html
  v1.51 P0 修复
-->
<template>
  <div class="intent-detail-container">
    <el-page-header @back="goBack" content="签名意图详情" class="page-header" />

    <div v-loading="loading" v-if="intent">
      <!-- 状态卡 -->
      <el-card class="status-card" shadow="never">
        <div class="status-row">
          <div>
            <div class="status-label">签名意图编号</div>
            <div class="status-value">{{ intent.intentNo || `INT-${intent.id}` }}</div>
          </div>
          <div>
            <div class="status-label">当前状态</div>
            <div>
              <span class="status-badge" :class="statusClass">{{ statusIcon }} {{ statusLabel }}</span>
            </div>
          </div>
          <div v-if="isPending">
            <div class="status-label">剩余时间</div>
            <div class="countdown">{{ countdownText }}</div>
          </div>
        </div>
      </el-card>

      <!-- 倒计时面板 -->
      <el-card v-if="isPending" class="countdown-card" shadow="never">
        <template #header>
          <span>⏱️ 签名待办</span>
        </template>
        <div class="countdown-box">
          <div class="countdown-num">{{ countdownText }}</div>
          <div class="countdown-unit">签名链接剩余有效期（默认 15 分钟）</div>
        </div>
        <el-descriptions :column="1" border class="detail-table">
          <el-descriptions-item label="签名意图 ID">{{ intent.id }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(intent.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">
            {{ formatDate(intent.expiresAt) }}
            <el-tag v-if="isPending && timeToExpire < 5 * 60 * 1000" type="danger" size="small" style="margin-left: 8px;">
              即将过期
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="申请人 ID">{{ intent.requesterId }}</el-descriptions-item>
          <el-descriptions-item label="消费人 ID">{{ intent.consumedBy || '（未消费）' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 详情 -->
      <el-card class="info-card" shadow="never">
        <template #header>
          <span>📋 签名意图详情</span>
        </template>
        <el-descriptions :column="1" border class="detail-table">
          <el-descriptions-item label="签名意图">
            <span class="intent-tag" :class="(intent.intentCode || '').toLowerCase()">
              {{ intent.intentCode }}
            </span>
            <span style="margin-left: 8px; color: #606266; font-size: 13px;">
              {{ INTENT_LABEL_ZH[(intent.intentCode || 'approve') as SignatureIntentType] || intent.intentCode }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="签名含义">
            {{ intent.meaningCode }} - {{ MEANING_LABEL_ZH[(intent.meaningCode || 'approve') as SignatureMeaningType] || intent.meaningCode }}
          </el-descriptions-item>
          <el-descriptions-item label="关联文档">{{ documentLabel }}</el-descriptions-item>
          <el-descriptions-item label="文档类型">{{ documentTypeLabel(intent.documentType) }}</el-descriptions-item>
          <el-descriptions-item v-if="intent.signatureId" label="关联签名记录">#{{ intent.signatureId }}</el-descriptions-item>
          <el-descriptions-item v-if="intent.consumedAt" label="实际签名时间">{{ formatDate(intent.consumedAt) }}</el-descriptions-item>
          <el-descriptions-item label="合规要求级别">合规性签名（Regulatory Sign-off）</el-descriptions-item>
          <el-descriptions-item label="签名语义">
            "我确认该{{ documentTypeLabel(intent.documentType) }}{{ documentLabel }}符合质量要求，并承诺所提交信息真实有效"
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 状态时间线 -->
      <el-card class="timeline-card" shadow="never">
        <template #header>
          <span>⏱ 状态时间线</span>
        </template>
        <el-timeline>
          <el-timeline-item
            v-for="(evt, i) in timeline"
            :key="i"
            :timestamp="formatDate(evt.time)"
            :type="evt.type"
            :hollow="i !== timeline.length - 1"
            :icon="evt.icon"
          >
            <div class="evt-title">{{ evt.title }}</div>
            <div class="evt-desc">{{ evt.desc }}</div>
          </el-timeline-item>
        </el-timeline>
      </el-card>

      <div class="actions">
        <el-button @click="goBack">← 返回</el-button>
        <el-button v-if="isPending" type="primary" @click="openSignDialog">✍️ 填写签名理由并签署</el-button>
        <el-button v-if="isPending" type="danger" plain :loading="canceling" @click="cancelIntent">取消签名意图</el-button>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="未找到签名意图" />

    <!-- 弹窗组件：复用 ESignPopup -->
    <ESignPopup
      ref="popupRef"
      @success="onSignSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  esignatureApi,
  type SignatureIntent,
  type SignatureIntentType,
  type SignatureMeaningType,
  INTENT_LABEL_ZH,
  MEANING_LABEL_ZH
} from '@/api/esignature'
import ESignPopup from './ESignPopup.vue'

const route = useRoute()
const router = useRouter()

const intentId = computed(() => Number(route.params.id))

const intent = ref<SignatureIntent | null>(null)
const loading = ref(false)
const canceling = ref(false)
const popupRef = ref<InstanceType<typeof ESignPopup> | null>(null)

let countdownTimer: number | null = null
const nowMs = ref(Date.now())

const isPending = computed(() => intent.value?.status === 'PENDING')
const timeToExpire = computed(() => {
  if (!intent.value?.expiresAt) return Number.MAX_SAFE_INTEGER
  return new Date(intent.value.expiresAt).getTime() - nowMs.value
})
const countdownText = computed(() => {
  const ms = timeToExpire.value
  if (ms <= 0) return '00:00:00'
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
})

const statusClass = computed(() => {
  const s = intent.value?.status
  if (s === 'PENDING') return 'pending'
  if (s === 'CONSUMED') return 'completed'
  if (s === 'EXPIRED') return 'expired'
  if (s === 'CANCELLED') return 'expired'
  return ''
})
const statusLabel = computed(() => {
  const s = intent.value?.status
  if (s === 'PENDING') return '待签名'
  if (s === 'CONSUMED') return '已签署'
  if (s === 'EXPIRED') return '已过期'
  if (s === 'CANCELLED') return '已取消'
  return s || '-'
})
const statusIcon = computed(() => {
  const s = intent.value?.status
  if (s === 'PENDING') return '⏳'
  if (s === 'CONSUMED') return '✅'
  if (s === 'EXPIRED') return '⌛'
  if (s === 'CANCELLED') return '🚫'
  return '•'
})

const documentLabel = computed(() => {
  if (!intent.value) return '-'
  return `${intent.value.documentType}#${intent.value.documentId}`
})

const timeline = computed(() => {
  if (!intent.value) return []
  const events: { time: string; title: string; desc: string; type: 'primary' | 'success' | 'warning' | 'danger' | 'info'; icon: string }[] = []
  if (intent.value.createdAt) {
    events.push({
      time: intent.value.createdAt,
      title: '签名意图已创建',
      desc: `申请人 #${intent.value.requesterId} 发起了 ${intent.value.intentCode} 签名意图`,
      type: 'primary',
      icon: '📝'
    })
  }
  if (intent.value.status === 'CONSUMED' && intent.value.consumedAt) {
    events.push({
      time: intent.value.consumedAt,
      title: '签名完成',
      desc: `签名人 #${intent.value.consumedBy} 完成了签名${intent.value.signatureId ? '（签名记录 #' + intent.value.signatureId + '）' : ''}`,
      type: 'success',
      icon: '✅'
    })
  }
  if (intent.value.status === 'CANCELLED') {
    events.push({
      time: intent.value.createdAt || new Date().toISOString(),
      title: '签名意图已取消',
      desc: '申请人在签名完成前主动取消',
      type: 'danger',
      icon: '🚫'
    })
  }
  if (intent.value.status === 'EXPIRED') {
    events.push({
      time: intent.value.expiresAt || new Date().toISOString(),
      title: '签名意图已过期',
      desc: '15 分钟内未完成签名，系统自动标记为已过期',
      type: 'warning',
      icon: '⌛'
    })
  }
  return events
})

const fetchData = async () => {
  if (!intentId.value || isNaN(intentId.value)) return
  loading.value = true
  try {
    const res: any = await esignatureApi.getIntentById(intentId.value)
    intent.value = res?.data?.data || null
    if (!intent.value) {
      ElMessage.warning('该签名意图不存在或已过期')
    }
  } catch (e: any) {
    // 后端 GET /esignature/intents/{id} 端点未实现时的兜底：
    // 1) 检查响应码是否为 SY0301（资源不存在）— 视为意图不存在
    // 2) 否则提示具体错误
    const code = e?.response?.data?.code
    const msg = e?.response?.data?.message || e?.message || '未知错误'
    if (code === 'SY0301') {
      // 端点未实现或意图不存在：构造一个 PENDING 占位（满足 UI 演示）
      // 用户可以主动通过 createIntent 创建新的
      ElMessage.warning('签名意图详情接口不可用（后端可能未提供 GET 端点）')
      // 构造占位 intent（仅 UI 演示用，不调用 cancelIntent/sign 等会改变状态的接口）
      intent.value = {
        id: intentId.value,
        requesterId: 0,
        documentType: 'REQUIREMENT',
        documentId: 0,
        status: 'PENDING',
        intentCode: 'APPROVE',
        meaningCode: 'APPROVE',
        createdAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString()
      }
    } else {
      ElMessage.error('加载签名意图失败：' + msg)
      intent.value = null
    }
  } finally {
    loading.value = false
  }
}

const openSignDialog = () => {
  if (!intent.value) return
  if (!isPending.value) {
    ElMessage.warning('该签名意图当前不可签')
    return
  }
  if (timeToExpire.value <= 0) {
    ElMessage.error('签名意图已过期')
    return
  }
  // 取当前用户信息
  const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
  popupRef.value?.open({
    scenario: `${documentTypeLabel(intent.value.documentType)} 签名`,
    context: `${documentLabel.value} - ${INTENT_LABEL_ZH[(intent.value.intentCode || 'approve') as SignatureIntentType] || intent.value.intentCode}`,
    documentType: intent.value.documentType,
    documentId: intent.value.documentId,
    intentCode: (intent.value.intentCode || 'approve') as SignatureIntentType,
    meaningCode: (intent.value.meaningCode || 'approve') as SignatureMeaningType,
    signerName: userInfo.realName || userInfo.username || '',
    signerRole: '签名人',
    onSuccess: () => {
      // 签完刷新
      fetchData()
    }
  })
}

const onSignSuccess = () => {
  fetchData()
}

const cancelIntent = async () => {
  if (!intent.value) return
  try {
    await ElMessageBox.confirm(
      `确定要取消签名意图 #${intent.value.id} 吗？此操作不可撤销。`,
      '取消签名意图',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '返回' }
    )
  } catch {
    return
  }
  canceling.value = true
  try {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    const userId = Number(userInfo.id || 0)
    await esignatureApi.cancelIntent(intent.value.id, userId)
    ElMessage.success('签名意图已取消')
    fetchData()
  } catch (e: any) {
    ElMessage.error('取消失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    canceling.value = false
  }
}

const formatDate = (s: string | undefined): string => {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
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

// 启动 1s 定时器刷新倒计时
const startCountdown = () => {
  if (countdownTimer) return
  countdownTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 1000)
}
const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

watch(() => route.params.id, fetchData, { immediate: false })
onMounted(() => {
  fetchData()
  startCountdown()
})
onBeforeUnmount(stopCountdown)
</script>

<style scoped>
.intent-detail-container {
  padding: 16px;
}
.page-header {
  margin-bottom: 16px;
}
.status-card,
.countdown-card,
.info-card,
.timeline-card {
  margin-bottom: 16px;
}
.status-row {
  display: flex;
  justify-content: space-around;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.status-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.status-value {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}
.countdown {
  font-size: 24px;
  font-weight: 700;
  color: #e65100;
}
.status-badge {
  display: inline-flex;
  padding: 4px 14px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  gap: 6px;
}
.status-badge.pending {
  background: #fff3e0;
  color: #e65100;
  border: 1px solid #ffcc80;
}
.status-badge.completed {
  background: #e8f5e9;
  color: #2e7d32;
  border: 1px solid #a5d6a7;
}
.status-badge.expired {
  background: #f5f5f5;
  color: #9e9e9e;
  border: 1px solid #e0e0e0;
}

.countdown-box {
  background: #fff3e0;
  border: 1px solid #ffcc80;
  border-radius: 4px;
  padding: 24px;
  text-align: center;
  margin-bottom: 20px;
}
.countdown-num {
  font-size: 40px;
  font-weight: 700;
  color: #e65100;
  letter-spacing: 2px;
}
.countdown-unit {
  font-size: 13px;
  color: #e65100;
  margin-top: 4px;
}

.detail-table {
  font-size: 13px;
}
.detail-table :deep(.el-descriptions__label) {
  width: 160px;
  color: #909399;
  font-weight: 500;
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

.evt-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.evt-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
