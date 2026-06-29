<!--
  Signatures.vue - 电子签名聚合页
  21 CFR Part 11 §11.10(e) 签名记录
  路由：/signatures
  对应原型：signatures-原型.html
  v1.51 P0 修复
-->
<template>
  <div class="signatures-container">
    <!-- 顶部统计 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="value">{{ totalCount }}</div>
        <div class="label">签名记录总数</div>
        <div class="sub">本月 +{{ thisMonthCount }}</div>
      </div>
      <div class="stat-card">
        <div class="value">{{ intentCount.approve }}</div>
        <div class="label">approve</div>
        <div class="sub">审批类签名</div>
      </div>
      <div class="stat-card">
        <div class="value">{{ intentCount.review }}</div>
        <div class="label">review</div>
        <div class="sub">审核类签名</div>
      </div>
      <div class="stat-card">
        <div class="value">{{ intentCount.confirm + intentCount.release }}</div>
        <div class="label">confirm / release</div>
        <div class="sub">确认/发布类</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="签名意图">
          <el-select v-model="filterIntent" placeholder="全部意图" clearable @change="onFilterChange">
            <el-option label="全部意图" value="" />
            <el-option v-for="it in SIGNATURE_INTENTS" :key="it" :label="`${it} (${INTENT_LABEL_ZH[it]})`" :value="it" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filterRole" placeholder="全部角色" clearable @change="onFilterChange">
            <el-option label="全部角色" value="" />
            <el-option v-for="r in SIGNATURE_MEANINGS" :key="r" :label="MEANING_LABEL_ZH[r]" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="filterKeyword"
            placeholder="搜索签名人/文档"
            style="width: 200px;"
            clearable
            @keyup.enter="onFilterChange"
            @clear="onFilterChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onFilterChange">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
          <el-button type="primary" plain @click="goVerifyHash">🔍 哈希校验</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 签名记录表 -->
    <el-card class="table-card" shadow="never" v-loading="loading">
      <el-table :data="filteredRecords" border stripe>
        <el-table-column prop="signedAt" label="签名时间" width="170">
          <template #default="{ row }">
            <span style="white-space: nowrap;">{{ formatDate(row.signedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="signerName" label="签名人" min-width="160">
          <template #default="{ row }">
            <div class="signer-cell">
              <div class="signer-avatar">{{ (row.signerName || '?').charAt(0) }}</div>
              <div>
                <div class="signer-name">{{ row.signerName }}</div>
                <div class="signer-ip">{{ row.ipAddress || '-' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="intent" label="意图" width="100">
          <template #default="{ row }">
            <span class="sig-intent-tag" :class="(row.intent || row.signatureType || 'approve').toLowerCase()">
              {{ row.intent || row.signatureType }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="signerRole" label="角色码" width="110">
          <template #default="{ row }">
            <span class="sig-meaning-tag">{{ row.signerRole || row.signatureType || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="签名对象" min-width="220">
          <template #default="{ row }">
            <span class="sig-doc-link" @click="viewDetail(row)">
              {{ row.documentNo || `${row.documentType}#${row.documentId}` }}
            </span>
            <div class="doc-type-sub">{{ documentTypeLabel(row.documentType) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="OTP 验证" width="100">
          <template #default="{ row }">
            <span class="verify-badge" :class="row.isValid === false ? 'fail' : 'ok'">
              {{ row.isValid === false ? '✗ 失败' : '✓ 通过' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="签名哈希" min-width="160">
          <template #default="{ row }">
            <span class="hash-display" :title="row.signatureValue || row.signatureHash || row.hashValue || '-'">
              {{ truncateHash(row.signatureValue || row.signatureHash || row.hashValue) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="primary" plain @click="verifyOne(row)">验签</el-button>
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

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="签名详情" width="640px">
      <div v-if="selectedRecord">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="签名记录 ID">{{ selectedRecord.id }}</el-descriptions-item>
          <el-descriptions-item label="OTP 验证">
            <span class="verify-badge" :class="selectedRecord.isValid === false ? 'fail' : 'ok'">
              {{ selectedRecord.isValid === false ? '失败' : '通过' }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="签名人">{{ selectedRecord.signerName }}</el-descriptions-item>
          <el-descriptions-item label="签名时间">{{ formatDate(selectedRecord.signedAt) }}</el-descriptions-item>
          <el-descriptions-item label="签名意图">
            <span class="sig-intent-tag" :class="(selectedRecord.intent || '').toLowerCase()">
              {{ selectedRecord.intent || selectedRecord.signatureType }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="角色码">{{ selectedRecord.signerRole || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签名对象" :span="2">
            <span class="sig-doc-link">
              {{ selectedRecord.documentNo || `${selectedRecord.documentType}#${selectedRecord.documentId}` }}
            </span>
            <span style="margin-left: 8px; color: #909399; font-size: 12px;">
              {{ documentTypeLabel(selectedRecord.documentType) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="IP 地址" :span="2">{{ selectedRecord.ipAddress || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备信息" :span="2">{{ selectedRecord.deviceInfo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签名原因" :span="2">{{ selectedRecord.reason || '（无）' }}</el-descriptions-item>
        </el-descriptions>

        <div class="hash-block-title">🔗 签名值（SHA-256）</div>
        <div class="hash-block">{{ selectedRecord.signatureValue || selectedRecord.signatureHash || selectedRecord.hashValue || '（未生成）' }}</div>

        <div class="hash-block-title">📋 实体哈希（被签文档指纹）</div>
        <div class="hash-block">{{ selectedRecord.entityHash || '（未生成）' }}</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" plain :loading="verifyLoading" @click="verifyOne(selectedRecord!)">哈希校验</el-button>
        <el-button @click="goHistoryDetail(selectedRecord?.id)">查看完整历史</el-button>
      </template>
    </el-dialog>

    <!-- 哈希校验弹窗 -->
    <el-dialog v-model="verifyDialogVisible" title="🔍 签名哈希校验" width="480px">
      <div class="verify-dialog-body">
        <p>输入需校验的签名哈希值，系统将验证其在哈希链中的完整性：</p>
        <el-input
          v-model="verifyHash"
          placeholder="粘贴签名哈希值"
          style="margin-top: 12px;"
          type="textarea"
          :rows="3"
        />
        <div v-if="verifyResult" class="verify-result" :class="verifyResult.valid ? 'ok' : 'fail'">
          <div class="verify-result-title">
            {{ verifyResult.valid ? '✓ 签名有效' : '✗ 签名无效' }}
          </div>
          <div class="verify-result-msg">{{ verifyResult.message }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="verifyDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="verifyLoading" @click="doVerifyHash">开始校验</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  esignatureApi,
  type SignatureRecord,
  type SignatureIntentType,
  SIGNATURE_INTENTS,
  SIGNATURE_MEANINGS,
  INTENT_LABEL_ZH,
  MEANING_LABEL_ZH
} from '@/api/esignature'

const router = useRouter()

// 数据
const allRecords = ref<SignatureRecord[]>([])
const totalCount = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)

// 过滤
const filterIntent = ref<SignatureIntentType | ''>('')
const filterRole = ref<string>('')
const filterKeyword = ref('')

const intentCount = computed(() => {
  const c: Record<string, number> = { approve: 0, confirm: 0, review: 0, release: 0 }
  for (const r of allRecords.value) {
    const i = (r.intent || r.signatureType || '').toLowerCase()
    if (i in c) c[i]++
  }
  return c
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

// 过滤后的数据（客户端二次过滤）
const filteredRecords = computed(() => {
  let list = allRecords.value
  if (filterIntent.value) {
    const it = filterIntent.value.toLowerCase()
    list = list.filter((r) => (r.intent || r.signatureType || '').toLowerCase() === it)
  }
  if (filterRole.value) {
    list = list.filter((r) => (r.signerRole || r.signatureType || '') === filterRole.value)
  }
  if (filterKeyword.value) {
    const k = filterKeyword.value.toLowerCase()
    list = list.filter(
      (r) =>
        (r.signerName || '').toLowerCase().includes(k) ||
        (r.documentNo || '').toLowerCase().includes(k) ||
        `${r.documentType}#${r.documentId}`.toLowerCase().includes(k)
    )
  }
  return list
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
    ElMessage.error('获取签名记录失败：' + (e?.message || '未知错误'))
    allRecords.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

const onFilterChange = () => {
  pageNum.value = 1
  fetchData()
}
const resetFilter = () => {
  filterIntent.value = ''
  filterRole.value = ''
  filterKeyword.value = ''
  onFilterChange()
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

// 详情
const detailVisible = ref(false)
const selectedRecord = ref<SignatureRecord | null>(null)
const viewDetail = (row: SignatureRecord) => {
  selectedRecord.value = row
  detailVisible.value = true
}
const goHistoryDetail = (id: number | undefined) => {
  if (!id) return
  detailVisible.value = false
  router.push({ name: 'SignatureHistoryDetail', params: { id: String(id) } })
}

// 验签
const verifyLoading = ref(false)
const verifyOne = async (row: SignatureRecord) => {
  verifyLoading.value = true
  try {
    const res: any = await esignatureApi.verifySignature(row.id)
    const valid = !!res?.data?.data
    ElMessage[valid ? 'success' : 'error'](
      valid ? `签名 #${row.id} 校验通过` : `签名 #${row.id} 校验失败（已被篡改或失效）`
    )
  } catch (e: any) {
    ElMessage.error('验签失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    verifyLoading.value = false
  }
}

// 哈希校验弹窗
const verifyDialogVisible = ref(false)
const verifyHash = ref('')
const verifyResult = ref<{ valid: boolean; message: string } | null>(null)
const goVerifyHash = () => {
  verifyHash.value = ''
  verifyResult.value = null
  verifyDialogVisible.value = true
}
const doVerifyHash = async () => {
  if (!verifyHash.value.trim()) {
    ElMessage.warning('请输入签名哈希值')
    return
  }
  verifyLoading.value = true
  try {
    // 通过列表查找匹配的签名记录并验签
    const match = allRecords.value.find(
      (r) =>
        (r.signatureValue || r.signatureHash || r.hashValue || '').toLowerCase() ===
        verifyHash.value.trim().toLowerCase()
    )
    if (!match) {
      verifyResult.value = { valid: false, message: '未找到匹配的签名记录' }
      return
    }
    const res: any = await esignatureApi.verifySignature(match.id)
    const valid = !!res?.data?.data
    verifyResult.value = {
      valid,
      message: valid ? '签名值与文档哈希匹配，签名完整有效' : '签名值与文档哈希不匹配，签名已被篡改或失效'
    }
  } catch (e: any) {
    verifyResult.value = { valid: false, message: e?.response?.data?.message || e?.message || '校验异常' }
  } finally {
    verifyLoading.value = false
  }
}

// 工具函数
const formatDate = (s: string | undefined): string => {
  if (!s) return '-'
  const d = new Date(s)
  if (isNaN(d.getTime())) return s
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
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
.signatures-container {
  padding: 16px;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 18px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.stat-card .value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}
.stat-card .label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.stat-card .sub {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}

.filter-card,
.table-card {
  margin-bottom: 16px;
}
.filter-form {
  margin-bottom: 0;
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

.signer-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.signer-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.signer-name {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}
.signer-ip {
  font-size: 11px;
  color: #909399;
}
.doc-type-sub {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}

.sig-intent-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.sig-intent-tag.approve {
  background: #ecf5ff;
  color: #409eff;
}
.sig-intent-tag.confirm {
  background: #f0f9eb;
  color: #67c23a;
}
.sig-intent-tag.review {
  background: #fdf6ec;
  color: #e6a23c;
}
.sig-intent-tag.release {
  background: #f4f4f5;
  color: #606266;
}
.sig-meaning-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  background: #f4f4f5;
  color: #606266;
}
.sig-doc-link {
  color: #409eff;
  cursor: pointer;
  font-weight: 500;
}
.sig-doc-link:hover {
  text-decoration: underline;
}
.hash-display {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
}
.verify-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.verify-badge.ok {
  background: #f0f9eb;
  color: #67c23a;
}
.verify-badge.fail {
  background: #fef0f0;
  color: #f56c6c;
}

.hash-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 6px;
}
.hash-block {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  font-family: monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
}

.verify-dialog-body p {
  font-size: 13px;
  color: #606266;
  margin: 0;
}
.verify-result {
  margin-top: 12px;
  padding: 12px;
  border-radius: 6px;
  font-size: 13px;
}
.verify-result.ok {
  background: #f0f9eb;
  color: #67c23a;
}
.verify-result.fail {
  background: #fef0f0;
  color: #f56c6c;
}
.verify-result-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.verify-result-msg {
  font-size: 12px;
  color: #606266;
}
</style>
