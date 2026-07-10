<template>
  <div class="verify-container">
    <h2 class="page-title">🔐 审计日志哈希链校验</h2>

    <!-- 搜索表单 -->
    <div class="search-card">
      <el-date-picker
        v-model="form.startDate"
        type="datetime"
        placeholder="开始日期"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 200px;"
      />
      <span class="separator">至</span>
      <el-date-picker
        v-model="form.endDate"
        type="datetime"
        placeholder="结束日期"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 200px;"
      />
      <el-select v-model="form.module" placeholder="选择模块" style="width: 160px;">
        <el-option
          v-for="m in MODULE_OPTIONS"
          :key="m.value"
          :label="m.label"
          :value="m.value"
        />
      </el-select>
      <el-button type="primary" :loading="verifying" @click="startVerify">
        开始校验
      </el-button>
    </div>

    <!-- 未部署占位 -->
    <el-card v-if="apiUnavailable" class="placeholder-card" shadow="never">
      <el-empty description="审计日志哈希链校验 API 待部署" />
    </el-card>

    <!-- 结果区 -->
    <template v-else-if="verifyDone">
      <!-- 汇总卡片 -->
      <div class="summary-card">
        <div class="summary-item">
          <span class="summary-label">校验范围</span>
          <span class="summary-value">{{ result.total }} 条记录</span>
        </div>
        <div class="summary-item pass">
          <span class="summary-label">通过</span>
          <span class="summary-value">{{ result.passed }} 条</span>
        </div>
        <div class="summary-item" :class="result.failed > 0 ? 'fail' : 'pass'">
          <span class="summary-label">失败</span>
          <span class="summary-value">{{ result.failed }} 条</span>
        </div>
      </div>

      <!-- 明细表格 -->
      <div class="table-wrap">
        <el-table
          :data="result.records"
          border
          stripe
          v-loading="verifying"
          empty-text="暂无校验记录"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column label="操作类型" width="120">
            <template #default="{ row }">
              <span class="action-tag" :class="actionClass(row.actionType)">
                {{ row.actionType || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="120" />
          <el-table-column label="操作时间" width="170">
            <template #default="{ row }">
              <span class="mono">{{ formatTime(row.operateTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="校验结果" width="130" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.verifyResult === 'valid'" type="success" effect="dark" size="small">valid</el-tag>
              <el-tag v-else-if="row.verifyResult === 'invalid'" type="warning" effect="dark" size="small">invalid</el-tag>
              <el-tag v-else-if="row.verifyResult === 'tampered'" type="danger" effect="dark" size="small">tampered</el-tag>
              <el-tag v-else type="info" size="small">{{ row.verifyResult || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        </el-table>
      </div>
    </template>

    <!-- 空状态 -->
    <el-card v-else-if="!apiUnavailable && !verifyDone" class="placeholder-card" shadow="never">
      <el-empty description="请选择条件后点击「开始校验」" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { requestFetch } from '@/api/request'

const MODULE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '需求管理', value: 'REQUIREMENT' },
  { label: '变更管理', value: 'CHANGE' },
  { label: '合规管理', value: 'COMPLIANCE' },
  { label: '电子签名', value: 'ESIGNATURE' },
  { label: '系统管理', value: 'SYSTEM' },
]

interface VerifyForm {
  startDate: string
  endDate: string
  module: string
}

interface VerifyRecord {
  id: number
  actionType: string
  operator: string
  operateTime: string
  verifyResult: string
  detail: string
}

interface VerifyData {
  total: number
  passed: number
  failed: number
  records: VerifyRecord[]
}

const form = reactive<VerifyForm>({
  startDate: '',
  endDate: '',
  module: '',
})

const verifying = ref(false)
const verifyDone = ref(false)
const apiUnavailable = ref(false)
const result = reactive<VerifyData>({
  total: 0,
  passed: 0,
  failed: 0,
  records: [],
})

const actionClass = (type: string): string => {
  const map: Record<string, string> = {
    CREATE: 'create',
    MODIFY: 'update',
    DELETE: 'delete',
    APPROVE: 'approve',
    SIGN: 'sign',
  }
  return map[type] || 'default'
}

const formatTime = (t: string | null): string => {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}

const startVerify = async () => {
  verifying.value = true
  verifyDone.value = false
  apiUnavailable.value = false
  try {
    const params = new URLSearchParams()
    if (form.startDate) params.set('startDate', form.startDate)
    if (form.endDate) params.set('endDate', form.endDate)

    const resp = await requestFetch(`/compliance/audit-logs/verify/detailed?${params.toString()}`)
    if (!resp) {
      apiUnavailable.value = true
      return
    }
    if (!resp.ok) {
      if (resp.status === 404) {
        apiUnavailable.value = true
        return
      }
      const errBody = await resp.json().catch(() => null)
      ElMessage.error(errBody?.message || `请求失败 (${resp.status})`)
      return
    }
    const json = await resp.json()
    if (json.code !== 200) {
      ElMessage.error(json.message || '校验失败')
      return
    }
    const data = json.data as any
    const totalChecked = data.totalChecked || 0
    const valid = data.valid === true
    result.total = totalChecked
    result.passed = valid ? totalChecked : (data.lastValidId ? (data.lastValidId as number) : 0)
    result.failed = totalChecked - result.passed
    result.records = valid ? [] : [{
      id: data.firstFailureId || 0,
      actionType: data.firstFailureType || 'UNKNOWN',
      operator: '-',
      operateTime: '-',
      verifyResult: valid ? 'valid' : 'invalid',
      detail: data.message || '哈希链断裂',
    }]
    verifyDone.value = true

    if (!valid) {
      ElMessage.warning(`校验发现断裂：${data.message || '哈希链不完整'}`)
    } else {
      ElMessage.success(`校验通过：${totalChecked} 条记录`)
    }
  } catch (e: any) {
    if (e?.message?.includes('404') || e?.status === 404) {
      apiUnavailable.value = true
    } else {
      ElMessage.error('校验失败：' + (e?.message || '未知错误'))
      apiUnavailable.value = true
    }
  } finally {
    verifying.value = false
  }
}
</script>

<style scoped>
.verify-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-title {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.search-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  flex-wrap: wrap;
}

.separator {
  color: #909399;
  font-size: 14px;
}

.summary-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  display: flex;
  gap: 32px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.summary-item.pass .summary-value {
  color: #67c23a;
}

.summary-item.fail .summary-value {
  color: #f56c6c;
}

.summary-label {
  font-size: 12px;
  color: #909399;
}

.summary-value {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  font-family: monospace;
}

.table-wrap {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  padding: 16px;
}

.placeholder-card {
  border-radius: 8px;
}

.mono {
  font-family: monospace;
  font-size: 12px;
}

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

.action-tag.default {
  background: #f4f4f5;
  color: #909399;
}
</style>
