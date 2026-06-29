<!--
  SignatureHistory.vue - 签名历史（按 4 种 meaning 分类）
  21 CFR Part 11 §11.10(e) 签名记录可视化
  路由：/signature-history
  对应原型：signature-history-原型.html
  v1.51 P0 修复
-->
<template>
  <div class="history-container">
    <!-- 概览卡 -->
    <div class="sig-overview">
      <div class="overview-card green">
        <div class="icon">✍️</div>
        <div class="value">{{ thisMonthCount }}</div>
        <div class="label">本月签名数</div>
      </div>
      <div class="overview-card blue">
        <div class="icon">✓</div>
        <div class="value">{{ validCount }}</div>
        <div class="label">有效签名</div>
      </div>
      <div class="overview-card orange">
        <div class="icon">⏳</div>
        <div class="value">{{ pendingCount }}</div>
        <div class="label">待验证</div>
      </div>
      <div class="overview-card red">
        <div class="icon">⚠️</div>
        <div class="value">{{ invalidCount }}</div>
        <div class="label">异常/篡改</div>
      </div>
    </div>

    <!-- 含义分类筛选 -->
    <el-card class="filter-card" shadow="never">
      <el-radio-group v-model="activeMeaning" class="meaning-tabs" @change="onMeaningChange">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="m in MEANING_GROUPS" :key="m.code" :value="m.code">
          <span class="meaning-tab-label">
            <span class="meaning-dot" :class="m.code" />
            {{ m.label }} ({{ m.count }})
          </span>
        </el-radio-button>
      </el-radio-group>
    </el-card>

    <!-- 历史表 -->
    <el-card class="table-card" shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header-row">
          <span>📋 签名历史记录</span>
          <div>
            <el-button size="small" @click="exportHistory">📤 导出记录</el-button>
            <el-button size="small" type="primary" @click="goAll">查看全部</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredRecords" border stripe>
        <el-table-column label="签名者" min-width="180">
          <template #default="{ row }">
            <div class="sig-person">
              <div class="sig-avatar" :style="{ background: avatarColor(row.signerId) }">
                {{ (row.signerName || '?').charAt(0) }}
              </div>
              <div>
                <div class="sig-name">{{ row.signerName }}</div>
                <div class="sig-role">{{ row.signerRole || '-' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="intent" label="签名含义" width="130">
          <template #default="{ row }">
            <span class="meaning-badge" :class="(row.intent || row.signatureType || '').toLowerCase()">
              {{ MEANING_LABEL_ZH[(row.intent || row.signatureType || 'approve') as SignatureMeaningType] || row.intent }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="被签名实体" min-width="220">
          <template #default="{ row }">
            <div class="entity-link" @click="goDetail(row)">
              {{ row.documentNo || `${row.documentType}#${row.documentId}` }}
            </div>
            <div class="entity-type-sub">{{ documentTypeLabel(row.documentType) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="签名时间" min-width="180">
          <template #default="{ row }">
            <div>{{ formatDate(row.signedAt) }}</div>
            <div class="ip-sub">{{ row.ipAddress || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="签名值" min-width="180">
          <template #default="{ row }">
            <span class="hash-value" :title="row.signatureValue || row.signatureHash || row.hashValue || '-'">
              {{ truncateHash(row.signatureValue || row.signatureHash || row.hashValue) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="验证状态" width="120">
          <template #default="{ row }">
            <span class="verify-status" :class="verifyStatus(row)">
              {{ verifyStatusText(row) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="goDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <span class="total-text">共 {{ totalCount }} 条记录</span>
        <el-pagination
          background
          layout="prev, pager, next, sizes"
          :total="totalCount"
          :page-size="pageSize"
          :current-page="pageNum"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  esignatureApi,
  type SignatureRecord,
  type SignatureMeaningType,
  SIGNATURE_MEANINGS,
  MEANING_LABEL_ZH
} from '@/api/esignature'

const router = useRouter()

// 数据
const allRecords = ref<SignatureRecord[]>([])
const totalCount = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)

const activeMeaning = ref<string>('')

// 5 种 meaning 分类（含 reject 单独一项）
const MEANING_GROUPS = computed(() => {
  const result: { code: string; label: string; count: number }[] = []
  for (const m of SIGNATURE_MEANINGS) {
    result.push({ code: m, label: MEANING_LABEL_ZH[m], count: 0 })
  }
  for (const r of allRecords.value) {
    const code = (r.intent || r.signatureType || '').toLowerCase()
    const found = result.find((x) => x.code === code)
    if (found) found.count++
  }
  return result
})

const thisMonthCount = computed(() => {
  const now = new Date()
  const m = now.getMonth()
  const y = now.getFullYear()
  return allRecords.value.filter((r) => {
    const d = new Date(r.signedAt)
    return d.getMonth() === m && d.getFullYear() === y
  }).length
})

const validCount = computed(() => allRecords.value.filter((r) => r.isValid !== false).length)
const invalidCount = computed(() => allRecords.value.filter((r) => r.isValid === false).length)
const pendingCount = ref(0) // 待后端字段补齐，暂用 0

const filteredRecords = computed(() => {
  if (!activeMeaning.value) return allRecords.value
  const code = activeMeaning.value.toLowerCase()
  return allRecords.value.filter(
    (r) => (r.intent || r.signatureType || '').toLowerCase() === code
  )
})

const fetchData = async () => {
  loading.value = true
  try {
    const res: any = await esignatureApi.listSignatures({
      page: pageNum.value - 1,
      size: pageSize.value
    })
    const data = res?.data?.data
    allRecords.value = data?.records || []
    totalCount.value = data?.total || 0
  } catch (e: any) {
    ElMessage.error('获取签名历史失败：' + (e?.message || '未知错误'))
    allRecords.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

const onMeaningChange = () => {
  // 客户端过滤，无需重新拉取
}
const onPageChange = (p: number) => {
  pageNum.value = p
  fetchData()
}
const onSizeChange = (s: number) => {
  pageSize.value = s
  pageNum.value = 1
  fetchData()
}
const goAll = () => {
  router.push({ name: 'Signatures' })
}
const goDetail = (row: SignatureRecord) => {
  router.push({ name: 'SignatureHistoryDetail', params: { id: String(row.id) } })
}
const exportHistory = () => {
  if (filteredRecords.value.length === 0) {
    ElMessage.warning('当前无可导出的签名记录')
    return
  }
  const headers = ['签名ID', '签名人', '角色', '含义', '文档编号', '签名时间', 'IP地址', '签名值', '验证状态']
  const rows = filteredRecords.value.map((r) => [
    r.id,
    r.signerName,
    r.signerRole || '-',
    r.intent || r.signatureType || '-',
    r.documentNo || `${r.documentType}#${r.documentId}`,
    formatDate(r.signedAt),
    r.ipAddress || '-',
    r.signatureValue || r.signatureHash || r.hashValue || '-',
    r.isValid === false ? '无效' : '有效'
  ])
  const csv = '﻿' + [headers, ...rows].map((row) => row.map(csvCell).join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `signature-history-${formatDateForFile()}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${filteredRecords.value.length} 条签名历史`)
}
const csvCell = (v: unknown): string => {
  const s = String(v ?? '')
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
const formatDateForFile = (): string => {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}-${pad(d.getHours())}${pad(d.getMinutes())}`
}

const verifyStatus = (r: SignatureRecord): string => {
  if (r.isValid === false) return 'invalid'
  // 简化：根据 isValid !== false + 默认 valid；若要识别 tampered 需要后端单独字段
  return 'valid'
}
const verifyStatusText = (r: SignatureRecord): string => {
  const s = verifyStatus(r)
  if (s === 'valid') return '✓ 有效'
  if (s === 'invalid') return '✗ 无效'
  return '⚠ 篡改'
}

const avatarColor = (id: number): string => {
  const palette = ['#409eff', '#1a1a2e', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
  return palette[Math.abs(id) % palette.length]
}
const formatDate = (s: string | undefined): string => {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
const truncateHash = (h: string | undefined): string => {
  if (!h) return '-'
  return h.length > 14 ? `${h.substring(0, 10)}...` : h
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

onMounted(fetchData)
</script>

<style scoped>
.history-container {
  padding: 16px;
}

.sig-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.overview-card {
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.overview-card .icon {
  font-size: 32px;
  margin-bottom: 8px;
}
.overview-card .value {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
}
.overview-card .label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.overview-card.green {
  background: linear-gradient(135deg, #f0f9eb, #e8f5e0);
}
.overview-card.blue {
  background: linear-gradient(135deg, #ecf5ff, #d9edff);
}
.overview-card.orange {
  background: linear-gradient(135deg, #fdf6ec, #fef5e6);
}
.overview-card.red {
  background: linear-gradient(135deg, #fef0f0, #fde2e2);
}

.filter-card,
.table-card {
  margin-bottom: 16px;
}
.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.meaning-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.meaning-tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.meaning-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.meaning-dot.approve {
  background: #67c23a;
}
.meaning-dot.reject {
  background: #f56c6c;
}
.meaning-dot.review {
  background: #409eff;
}
.meaning-dot.confirm {
  background: #e6a23c;
}
.meaning-dot.release {
  background: #1a1a2e;
}

.sig-person {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sig-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  flex-shrink: 0;
}
.sig-name {
  font-weight: 600;
  color: #303133;
  font-size: 13px;
}
.sig-role {
  font-size: 11px;
  color: #909399;
}
.meaning-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.meaning-badge.approve {
  background: #f0f9eb;
  color: #67c23a;
}
.meaning-badge.reject {
  background: #fef0f0;
  color: #f56c6c;
}
.meaning-badge.review {
  background: #ecf5ff;
  color: #409eff;
}
.meaning-badge.confirm {
  background: #fdf6ec;
  color: #e6a23c;
}
.meaning-badge.release {
  background: #1a1a2e;
  color: #fff;
}
.entity-link {
  color: #409eff;
  font-weight: 600;
  cursor: pointer;
}
.entity-link:hover {
  text-decoration: underline;
}
.entity-type-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.ip-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.hash-value {
  font-family: monospace;
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
}
.verify-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.verify-status.valid {
  background: #f0f9eb;
  color: #67c23a;
}
.verify-status.invalid {
  background: #fef0f0;
  color: #f56c6c;
}
.verify-status.tampered {
  background: #f56c6c;
  color: #fff;
}

.pagination-row {
  padding: 12px 0 0;
  border-top: 1px solid #ebeef5;
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.total-text {
  font-size: 13px;
  color: #909399;
}
</style>
