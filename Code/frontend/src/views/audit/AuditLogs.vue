<template>
  <div class="audit-logs-container">
    <!-- 顶部哈希链状态横幅 -->
    <div class="hash-chain-banner">
      <div class="chain-status-badge" :class="chainValid ? 'valid' : 'invalid'">
        <el-icon size="16">
          <CircleCheck v-if="chainValid" />
          <CircleClose v-else />
        </el-icon>
        哈希链校验 {{ chainValid ? '完整' : '异常' }}
      </div>
      <div class="chain-meta">
        <div class="meta-row">日志总数：<strong>{{ totalLogs }} 条</strong></div>
        <div class="meta-row">已校验：<strong>{{ verifyResult?.totalChecked ?? 0 }} 条</strong></div>
      </div>
      <div class="chain-meta">
        <div class="meta-row">最后有效 ID：<strong>{{ verifyResult?.lastValidId ?? '-' }}</strong></div>
        <div class="meta-row">最后校验：<strong>{{ lastVerifyTime }}</strong></div>
      </div>
      <el-button size="small" :loading="verifying" @click="verifyChain">重新校验</el-button>
      <el-button size="small" type="primary" plain @click="goExport">导出日志</el-button>
    </div>

    <!-- 防篡改警示横幅 -->
    <el-alert
      v-if="!chainValid"
      type="error"
      :closable="false"
      show-icon
      class="tamper-alert"
    >
      <template #title>
        检测到哈希链异常
        <span v-if="verifyResult?.firstFailureId">
          （首个断裂点 ID：<strong>{{ verifyResult.firstFailureId }}</strong>，
          类型：<strong>{{ failureTypeLabel }}</strong>）
        </span>
        ，可能存在篡改，请立即核查。
      </template>
      <template #default>
        <div class="tamper-tip">{{ verifyResult?.message || '请联系系统管理员确认链状态。' }}</div>
        <el-button
          v-if="verifyResult?.firstFailureId"
          size="small"
          type="danger"
          plain
          @click="verifyFromFailure"
        >
          从断裂点之后开始分段校验
        </el-button>
      </template>
    </el-alert>

    <!-- 工具栏 -->
    <div class="filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        size="default"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 360px;"
      />
      <el-select
        v-model="filterEntity"
        placeholder="实体类型"
        clearable
        style="width: 160px;"
        @change="loadLogs"
      >
        <el-option label="全部实体" value="" />
        <el-option
          v-for="m in MODULE_OPTIONS"
          :key="m.value"
          :label="m.label"
          :value="m.value"
        />
      </el-select>
      <el-select
        v-model="filterAction"
        placeholder="操作类型"
        clearable
        style="width: 160px;"
        @change="loadLogs"
      >
        <el-option label="全部操作" value="" />
        <el-option
          v-for="a in ACTION_OPTIONS"
          :key="a.value"
          :label="a.label"
          :value="a.value"
        />
      </el-select>
      <el-input
        v-model="filterKeyword"
        placeholder="搜索操作人/内容"
        style="width: 220px;"
        clearable
        @keyup.enter="loadLogs"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :loading="loading" @click="loadLogs">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <!-- 日志表格 -->
    <div class="log-table-wrap">
      <el-table
        :data="filteredLogs"
        border
        stripe
        v-loading="loading"
        empty-text="暂无审计日志"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <span style="font-family: monospace; font-size: 12px;">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人" width="120">
          <template #default="{ row }">
            <div class="user-cell">
              <div class="user-avatar">{{ (row.operatorName || '?')[0] }}</div>
              <span>{{ row.operatorName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <span class="action-tag" :class="mapActionType(row.eventType)">
              {{ displayActionType(row.eventType) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="entityType" label="模块" width="140" />
        <el-table-column label="目标" width="160">
          <template #default="{ row }">
            <span class="entity-link" :title="`点击查看 ${row.entityType} #${row.entityId}`">
              {{ row.entityType }}#{{ row.entityId ?? '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="operation" label="详情" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="detail-cell" :title="row.operation || ''">
              {{ row.operation || '-' }}
            </div>
            <div v-if="row.reason" class="reason-cell" :title="row.reason">
              原因：{{ row.reason }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" label="IP" width="130" />
        <el-table-column label="当前哈希" width="180">
          <template #default="{ row }">
            <span
              class="hash-cell"
              :class="row.hashValid ? 'hash-valid' : 'hash-invalid'"
              :title="row.currentHash || '无哈希'"
            >
              {{ truncateHash(row.currentHash) }}
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-footer">
        <span class="page-info">
          共 {{ logs.length }} 条记录
          <span v-if="filteredLogs.length !== logs.length" style="color:#909399">
            （已过滤显示 {{ filteredLogs.length }} 条）
          </span>
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="logs.length"
          :page-sizes="[10, 20, 50, 100]"
          layout="sizes, prev, pager, next"
          small
          background
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleClose, Search } from '@element-plus/icons-vue'
import {
  auditApi,
  type AuditLog,
  type HashChainVerifyResult,
  ACTION_TYPE_MAP,
  ACTION_OPTIONS,
  MODULE_OPTIONS,
  toBackendEntityType,
} from '@/api/audit'

const router = useRouter()

// ============== 状态 ==============
const loading = ref(false)
const verifying = ref(false)
const logs = ref<AuditLog[]>([])
const verifyResult = ref<HashChainVerifyResult | null>(null)
const lastVerifyTime = ref<string>('未校验')

const dateRange = ref<[string, string] | null>(null)
const filterEntity = ref<string>('')
const filterAction = ref<string>('')
const filterKeyword = ref<string>('')

const currentPage = ref(1)
const pageSize = ref(20)

// ============== 计算属性 ==============
const chainValid = computed(() => verifyResult.value?.valid ?? true)

const totalLogs = computed(() => verifyResult.value?.totalChecked ?? logs.value.length)

const failureTypeLabel = computed(() => {
  const t = verifyResult.value?.firstFailureType
  if (!t) return '-'
  if (t === 'PREV_HASH_MISMATCH') return '前向哈希不匹配'
  if (t === 'CURRENT_HASH_MISMATCH') return '当前哈希不匹配'
  return t
})

const filteredLogs = computed(() => {
  // 后端 list 不返回 total，使用 page/size 切分；这里在客户端做最后一道 keyword 过滤 + 哈希有效性映射
  const kw = filterKeyword.value.trim().toLowerCase()
  return logs.value.filter((log) => {
    if (kw) {
      const haystack = [
        log.operatorName,
        log.operation,
        log.reason,
        log.entityType,
        String(log.entityId ?? ''),
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
      if (!haystack.includes(kw)) return false
    }
    return true
  })
})

// ============== 数据加载 ==============
const buildQuery = () => {
  const params: Record<string, any> = {
    page: currentPage.value - 1,
    size: pageSize.value,
  }
  if (dateRange.value && dateRange.value.length === 2) {
    params.startTime = dateRange.value[0]
    params.endTime = dateRange.value[1]
  }
  if (filterEntity.value) params.entityType = toBackendEntityType(filterEntity.value)
  // 前端 5 类 → 后端 eventType 集合（5 类合并可能的 eventType）
  if (filterAction.value) {
    const eventTypeMap: Record<string, string[]> = {
      create: ['CREATE'],
      update: ['MODIFY', 'STATUS_CHANGE'],
      delete: ['DELETE'],
      approve: ['APPROVE'],
      sign: ['SIGN'],
    }
    // 后端 listAuditLogs 只支持单值 eventType 精确匹配，这里取第一个；若需要多值过滤应后端支持
    const candidates = eventTypeMap[filterAction.value] || []
    if (candidates.length === 1) {
      params.eventType = candidates[0]
    } else if (candidates.length > 1) {
      // 多次请求合并（简化：取第一个，按需扩展）
      params.eventType = candidates[0]
    }
  }
  return params
}

const loadLogs = async () => {
  loading.value = true
  try {
    const res = await auditApi.list(buildQuery())
    const data = res.data?.data || []
    logs.value = Array.isArray(data) ? data.map(enrichHashValid) : []
  } catch (e: any) {
    ElMessage.error('加载审计日志失败：' + (e?.response?.data?.message || e.message))
    logs.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 客户端不可重算哈希（缺少 SecurityUtils.calculateAuditHash 算法），
 * 这里暂时把所有记录视为"待服务端验证"。具体有效性以后端 verify/detailed 为准。
 * 视觉提示：服务端标记为 invalid 的链上的全部记录标红（保守）。
 */
const enrichHashValid = (log: AuditLog): AuditLog & { hashValid: boolean } => {
  const chainOk = chainValid.value
  return { ...log, hashValid: chainOk }
}

const verifyChain = async () => {
  verifying.value = true
  try {
    const res = await auditApi.verifyDetailed()
    verifyResult.value = res.data?.data || null
    lastVerifyTime.value = new Date().toLocaleString('zh-CN')
    if (chainValid.value) {
      ElMessage.success(`哈希链校验通过，共 ${verifyResult.value?.totalChecked ?? 0} 条`)
    } else {
      ElMessage.error('哈希链校验失败：' + (verifyResult.value?.message || '存在断裂点'))
    }
    // 校验后刷新日志列表，hashValid 重新计算
    await loadLogs()
  } catch (e: any) {
    ElMessage.error('校验失败：' + (e?.response?.data?.message || e.message))
  } finally {
    verifying.value = false
  }
}

const verifyFromFailure = async () => {
  const failureId = verifyResult.value?.firstFailureId
  if (!failureId) return
  verifying.value = true
  try {
    // 断裂点之后 = firstFailureId 自身
    const res = await auditApi.verifyFrom(failureId)
    verifyResult.value = res.data?.data || null
    lastVerifyTime.value = new Date().toLocaleString('zh-CN')
    ElMessage.success(
      `分段校验完成：${verifyResult.value?.valid ? '通过' : '失败'}（共 ${verifyResult.value?.totalChecked ?? 0} 条）`
    )
    await loadLogs()
  } catch (e: any) {
    ElMessage.error('分段校验失败：' + (e?.response?.data?.message || e.message))
  } finally {
    verifying.value = false
  }
}

// ============== 工具 ==============
const mapActionType = (eventType: string): string => {
  // 优先从映射表查；否则按规则归类
  if (eventType === 'CREATE') return 'create'
  if (eventType === 'MODIFY' || eventType === 'STATUS_CHANGE') return 'update'
  if (eventType === 'DELETE') return 'delete'
  if (eventType === 'APPROVE') return 'approve'
  if (eventType === 'SIGN') return 'sign'
  if (eventType === 'LOGIN') return 'update'
  return 'update'
}

const displayActionType = (eventType: string): string => {
  if (eventType === 'STATUS_CHANGE') return 'UPDATE'
  return (eventType || 'UPDATE').toUpperCase()
}

const truncateHash = (hash: string | null): string => {
  if (!hash) return '-'
  if (hash.length <= 16) return hash
  return `${hash.substring(0, 8)}…${hash.substring(hash.length - 8)}`
}

const formatTime = (t: string | null): string => {
  if (!t) return '-'
  // 后端返回 ISO 字符串（LocalDateTime toString），展示替换为空格更易读
  return t.replace('T', ' ').substring(0, 19)
}

const resetFilters = () => {
  dateRange.value = null
  filterEntity.value = ''
  filterAction.value = ''
  filterKeyword.value = ''
  currentPage.value = 1
  loadLogs()
}

const goExport = () => {
  router.push('/audit-logs/export')
}

onMounted(async () => {
  // 初次进入先校验链，再加载日志
  await verifyChain()
})
</script>

<style scoped>
.audit-logs-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

/* 哈希链状态横幅 */
.hash-chain-banner {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 18px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  flex-wrap: wrap;
}

.chain-status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
}

.chain-status-badge.valid {
  background: #f0f9eb;
  color: #67c23a;
}

.chain-status-badge.invalid {
  background: #fef0f0;
  color: #f56c6c;
}

.chain-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-row {
  font-size: 12px;
  color: #909399;
}

.meta-row strong {
  color: #303133;
  font-family: monospace;
}

/* 工具栏 */
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

/* 日志表格 */
.log-table-wrap {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  padding: 16px;
}

.pagination-footer {
  padding: 16px 0 4px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-info {
  font-size: 13px;
  color: #606266;
}

.tamper-alert {
  margin-bottom: 12px;
}

.tamper-tip {
  font-size: 12px;
  color: #606266;
  margin: 4px 0 6px;
}

/* 哈希单元 */
.hash-cell {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  max-width: 160px;
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
}

.hash-valid {
  color: #67c23a;
  background: #f0f9eb;
}

.hash-invalid {
  color: #f56c6c;
  background: #fef0f0;
}

/* 操作标签 */
.action-tag {
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  display: inline-block;
}

.action-tag.create {
  background: #f0f9eb;
  color: #67c23a;
}

.action-tag.update {
  background: #ecf5ff;
  color: #409eff;
}

.action-tag.delete {
  background: #fef0f0;
  color: #f56c6c;
}

.action-tag.approve {
  background: #fdf6ec;
  color: #e6a23c;
}

.action-tag.sign {
  background: #f4f4f5;
  color: #606266;
}

/* 用户单元格 */
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
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

.entity-link {
  color: #409eff;
  font-family: monospace;
  font-size: 12px;
  cursor: pointer;
}

.entity-link:hover {
  text-decoration: underline;
}

.detail-cell {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}

.reason-cell {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 360px;
}
</style>
