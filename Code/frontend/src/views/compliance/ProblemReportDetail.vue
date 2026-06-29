<template>
  <div class="pr-detail-container">
    <div class="page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/problem-report' }">问题报告</el-breadcrumb-item>
          <el-breadcrumb-item>详情</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">
          问题报告详情
          <el-tag v-if="pr.status" :type="statusTagType(pr.status)" size="small" style="margin-left: 8px;">
            {{ statusLabel(pr.status) }}
          </el-tag>
        </div>
      </div>
      <div class="header-actions">
        <el-button @click="handleExport">导出 PDF</el-button>
        <el-button type="primary" @click="showCorrection = true">+ 添加纠正措施</el-button>
      </div>
    </div>

    <el-card v-loading="loading" class="info-card">
      <template #header><span>基本信息</span></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="报告编号">
          <span style="font-weight: 700; color: #409eff;">{{ pr.reportCode || pr.prCode || `PR-${pr.id}` }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="问题类型">{{ pr.problemType || pr.sourceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="严重程度">
          <el-tag v-if="pr.severity" :type="getSeverityType(pr.severity)" size="small">{{ pr.severity }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源">{{ pr.sourceType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发现日期">{{ pr.discoveryDate || pr.reportedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusLabel(pr.status) }}</el-descriptions-item>
        <el-descriptions-item label="报告人">{{ pr.reporterName || pr.ownerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联项目">{{ pr.projectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关联需求" :span="3">
          <span v-if="pr.affectedItems">{{ pr.affectedItems }}</span>
          <span v-else style="color:#c0c4cc">-</span>
        </el-descriptions-item>
      </el-descriptions>
      <div style="padding: 16px 20px;">
        <div class="info-label">问题描述</div>
        <div class="description-box">{{ pr.description || '—' }}</div>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-bottom: 16px;">
      <el-col :span="12">
        <el-card>
          <template #header><span>🧐 根本原因分析（5-Why）</span></template>
          <div v-if="pr.rootCause" class="rca-box">{{ pr.rootCause }}</div>
          <div v-else class="rca-box" style="color: #909399;">待分析</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header-row">
              <span>📋 CAPA 措施</span>
              <el-button size="small" type="primary" @click="showCorrection = true">+ 添加</el-button>
            </div>
          </template>
          <div v-if="corrections.length === 0" style="color:#909399; text-align:center; padding: 12px;">暂无 CAPA 措施</div>
          <div v-else>
            <div v-for="c in corrections" :key="c.id" class="capa-item">
              <div class="capa-icon">📝</div>
              <div class="capa-content">
                <div class="capa-title">{{ c.actionType === 'corrective' ? '纠正措施' : '预防措施' }}: {{ c.action || c.description }}</div>
                <div class="capa-meta">负责人: {{ c.ownerName || c.assignee || '未指定' }} · 截止: {{ c.dueDate || '-' }}</div>
                <div v-if="c.status === 'COMPLETED' || c.status === 'VERIFIED'" class="capa-status done">✓ {{ c.status === 'VERIFIED' ? '已验证' : '已完成' }}</div>
                <div v-else class="capa-status pending">进行中</div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 处理流程时间线 -->
    <el-card>
      <template #header><span>🕒 处理流程</span></template>
      <el-timeline>
        <el-timeline-item
          v-for="(item, idx) in timeline"
          :key="idx"
          :type="item.status === 'done' ? 'success' : (item.status === 'current' ? 'primary' : 'info')"
          :timestamp="item.timestamp"
        >
          <strong>{{ item.title }}</strong>
          <div style="color:#909399; font-size:12px;">{{ item.detail }}</div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 状态推进 -->
    <el-card style="margin-top: 16px;">
      <template #header>
        <span>⚡ 状态推进</span>
        <!-- FR-0.17 操作序列强制检查 UI 提示：问题报告关闭前必须完成"验证中"阶段 -->
        <el-alert
          v-if="pr && pr.status !== 'Closed' && pr.status !== 'Verifying'"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top:8px;font-size:12px"
        >
          <template #title>
            关闭前需先经过"验证中"阶段（21 CFR Part 11 §11.10(f)）
          </template>
        </el-alert>
      </template>
      <el-button-group>
        <el-button v-for="s in statusFlow" :key="s" :type="pr.status === s ? 'primary' : 'default'" :disabled="pr.status === s || (s === 'Closed' && pr.status !== 'Verifying')" @click="updateStatus(s)">
          {{ statusLabel(s) }}
        </el-button>
      </el-button-group>
    </el-card>

    <!-- 添加纠正措施 -->
    <el-dialog v-model="showCorrection" title="添加纠正措施" width="500px">
      <el-form :model="correctionForm" label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="correctionForm.actionType" style="width:100%;">
            <el-option label="纠正措施" value="corrective" />
            <el-option label="预防措施" value="preventive" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="correctionForm.action" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="correctionForm.ownerName" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="correctionForm.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCorrection = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCorrection">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 问题报告详情页 (合规域独立页面 P0 修复)
 * 对应原型：problem-report-detail-原型.html
 * 对应路由：/compliance/problem-report/:id
 * 对应后端：ComplianceController.getProblemReports + PrCorrectionController
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const showCorrection = ref(false)
const pr = ref<any>({})
const corrections = ref<any[]>([])

const statusFlow = ['Open', 'Analyzing', 'Correcting', 'Verifying', 'Closed']
const statusLabelMap: Record<string, string> = {
  Open: '开放', Analyzing: '分析中', Correcting: '纠正中', Verifying: '验证中', Closed: '已关闭', Resolved: '已解决'
}
const statusTagTypeMap: Record<string, string> = {
  Open: 'warning', Analyzing: 'primary', Correcting: 'warning', Verifying: 'primary', Closed: 'success', Resolved: 'success'
}

const statusLabel = (s?: string) => s ? (statusLabelMap[s] || s) : '-'
const statusTagType = (s?: string) => s ? (statusTagTypeMap[s] || 'info') : 'info'

const getSeverityType = (s: string) => s === 'CRITICAL' ? 'danger' : (s === 'MAJOR' ? 'warning' : 'info')

const correctionForm = ref({
  actionType: 'corrective',
  action: '',
  ownerName: '',
  dueDate: ''
})

const timeline = computed(() => {
  const items: { title: string; detail: string; status: string; timestamp: string }[] = []
  if (pr.value.id) {
    items.push({
      title: '问题报告提交',
      detail: `${pr.value.reporterName || '-'} · ${pr.value.discoveryDate || pr.value.createdAt || '-'}`,
      status: 'done',
      timestamp: pr.value.discoveryDate || pr.value.createdAt || '-'
    })
    items.push({
      title: '质量经理确认',
      detail: '已确认严重程度并开通 CAPA 流程',
      status: pr.value.status && pr.value.status !== 'Open' ? 'done' : 'current',
      timestamp: '-'
    })
    items.push({
      title: '根本原因分析',
      detail: pr.value.rootCause ? 'RCA 已完成' : '待分析',
      status: pr.value.rootCause ? 'done' : 'pending',
      timestamp: '-'
    })
    items.push({
      title: '纠正预防措施执行',
      detail: corrections.value.length > 0 ? `${corrections.value.length} 项措施` : '暂无',
      status: corrections.value.length > 0 ? 'current' : 'pending',
      timestamp: '-'
    })
    items.push({
      title: '效果验证与关闭',
      detail: pr.value.status === 'Closed' ? '已完成' : '待完成',
      status: pr.value.status === 'Closed' ? 'done' : 'pending',
      timestamp: '-'
    })
  }
  return items
})

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await request.get('/compliance/problem-reports', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    const list: any[] = Array.isArray(d) ? d : (d?.records || [])
    pr.value = list.find((p: any) => String(p.id) === String(id)) || { id }
  } catch (e: any) {
    pr.value = { id }
  } finally {
    loading.value = false
  }
}

const fetchCorrections = async () => {
  try {
    const res = await request.get(`/compliance/pr-correction/by-report/${route.params.id}`, { params: { page: 0, size: 50 } })
    const d = res.data?.data
    corrections.value = d?.records || d || []
  } catch {
    corrections.value = []
  }
}

const submitCorrection = async () => {
  if (!correctionForm.value.action) {
    ElMessage.warning('请填写措施描述')
    return
  }
  saving.value = true
  try {
    await request.post('/compliance/pr-correction', {
      problemReportId: Number(route.params.id),
      action: correctionForm.value.action,
      ownerId: 0,
      dueDate: correctionForm.value.dueDate || null
    })
    ElMessage.success('纠正措施已添加')
    showCorrection.value = false
    correctionForm.value = { actionType: 'corrective', action: '', ownerName: '', dueDate: '' }
    await fetchCorrections()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

const updateStatus = async (status: string) => {
  try {
    await request.put(`/compliance/problem-reports/${route.params.id}/status`, null, { params: { status } })
    pr.value.status = status
    ElMessage.success(`状态已更新为 ${statusLabel(status)}`)
  } catch (e: any) {
    ElMessage.error('状态更新失败：' + (e?.response?.data?.message || e?.message))
  }
}

const handleExport = () => {
  ElMessage.info('PDF 导出（演示版）— 实际集成请使用后端 export 接口')
  const lines: string[] = []
  lines.push(`# 问题报告 - ${pr.value.reportCode || pr.value.id}`)
  lines.push(`\n## 基本信息`)
  lines.push(`- 标题: ${pr.value.title || '-'}`)
  lines.push(`- 严重度: ${pr.value.severity || '-'}`)
  lines.push(`- 状态: ${statusLabel(pr.value.status)}`)
  lines.push(`- 来源: ${pr.value.sourceType || '-'}`)
  lines.push(`\n## 问题描述`)
  lines.push(pr.value.description || '-')
  lines.push(`\n## 根本原因`)
  lines.push(pr.value.rootCause || '-')
  lines.push(`\n## CAPA 措施`)
  corrections.value.forEach((c, i) => {
    lines.push(`${i + 1}. [${c.actionType || ''}] ${c.action || c.description}（${c.ownerName || '未指定'}，${c.dueDate || '-'}）`)
  })
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `problem-report-${pr.value.id}.md`
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(async () => {
  await fetchDetail()
  await fetchCorrections()
})
</script>

<style scoped>
.pr-detail-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; }
.info-card { margin-bottom: 16px; }
.info-label { font-size: 12px; color: #909399; margin-bottom: 8px; }
.description-box { font-size: 13px; line-height: 1.6; padding: 12px 16px; background: #f8fafc; border-radius: 6px; }
.rca-box { font-size: 13px; line-height: 1.6; padding: 12px 16px; background: #f8fafc; border-radius: 6px; min-height: 60px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.capa-item { display: flex; gap: 10px; padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 6px; margin-bottom: 8px; }
.capa-icon { font-size: 14px; }
.capa-content { flex: 1; }
.capa-title { font-weight: 600; font-size: 13px; }
.capa-meta { font-size: 12px; color: #909399; margin-top: 2px; }
.capa-status { font-size: 11px; margin-top: 4px; }
.capa-status.done { color: #67c23a; }
.capa-status.pending { color: #409eff; }
</style>
