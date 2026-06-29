<template>
  <div class="iec62304-container">
    <div class="page-title">
      <h2>IEC 62304 合规检查清单</h2>
      <div>
        <el-button @click="exportChecklist">导出检查报告</el-button>
        <el-button type="primary" :loading="checking" @click="runFullCheck">一键合规检查</el-button>
      </div>
    </div>

    <div class="project-bar">
      <span class="bar-label">当前项目：</span>
      <el-select v-model="projectId" placeholder="选择项目" style="width: 260px;" @change="onProjectChange">
        <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
      </el-select>
      <el-button v-if="!loaded" type="primary" plain :loading="initializing" @click="initTemplate" style="margin-left: 12px;">初始化清单模板</el-button>
      <span v-else class="loaded-tip">已加载 {{ rawItems.length }} 条条款</span>
    </div>

    <div v-if="loaded" class="progress-card">
      <div class="progress-ring">
        <svg width="100" height="100" viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="42" fill="none" stroke="#e4e7ed" stroke-width="8" />
          <circle cx="50" cy="50" r="42" fill="none"
                  :stroke="stats.complianceRate >= 80 ? '#67c23a' : '#e6a23c'"
                  stroke-width="8"
                  :stroke-dasharray="arc + ' ' + (264 - arc)"
                  stroke-linecap="round" />
        </svg>
        <div class="text">{{ stats.complianceRate }}%</div>
      </div>
      <div class="progress-stats">
        <div class="stat-item" style="background:#f0f9eb;">
          <div class="num" style="color:#67c23a;">{{ stats.compliant }}</div>
          <div class="lbl">合规</div>
        </div>
        <div class="stat-item" style="background:#fdf6ec;">
          <div class="num" style="color:#e6a23c;">{{ stats.partial }}</div>
          <div class="lbl">部分合规</div>
        </div>
        <div class="stat-item" style="background:#fef0f0;">
          <div class="num" style="color:#f56c6c;">{{ stats.nonCompliant }}</div>
          <div class="lbl">不合规</div>
        </div>
        <div class="stat-item" style="background:#f4f4f5;">
          <div class="num" style="color:#909399;">{{ stats.notApplicable }}</div>
          <div class="lbl">不适用</div>
        </div>
      </div>
    </div>

    <div v-if="loaded" class="checklist-section" v-for="section in sections" :key="section.id">
      <div class="section-header">
        <h3>{{ section.title }}</h3>
        <el-tag :type="section.progress === 100 ? 'success' : 'warning'" size="small">
          {{ section.progress }}% 完成
        </el-tag>
      </div>
      <div class="clause-item" v-for="clause in section.clauses" :key="clause.id">
        <div class="clause-header">
          <div>
            <span class="clause-no">{{ clause.clauseNo }}</span>
            <el-tag size="small" :type="statusTagType(clause.complianceStatus)" style="margin-left: 8px;">
              {{ statusLabel(clause.complianceStatus) }}
            </el-tag>
          </div>
          <el-button size="small" text type="primary" @click="openAssess(clause)">评估</el-button>
        </div>
        <div class="clause-title">{{ clause.clauseTitle }}</div>
        <div class="clause-meta">
          <span v-if="clause.evidence">证据：{{ clause.evidence }}</span>
          <span v-if="clause.gaps" style="color: #f56c6c;">差距：{{ clause.gaps }}</span>
          <span v-if="clause.assessorName">评估人：{{ clause.assessorName }} | {{ formatDate(clause.assessedAt) }}</span>
        </div>
      </div>
    </div>

    <el-empty v-if="loaded && sections.length === 0" description="该项目下尚无条款" />

    <el-dialog v-model="showAssess" title="合规评估" width="560px" class="assess-dialog">
      <el-form :model="assessForm" label-width="100px" v-if="assessForm">
        <el-form-item label="条款号">
          <el-input :value="assessForm.clauseNo" disabled />
        </el-form-item>
        <el-form-item label="条款标题">
          <el-input :value="assessForm.clauseTitle" disabled />
        </el-form-item>
        <el-form-item label="合规状态" required>
          <el-radio-group v-model="assessForm.complianceStatus">
            <el-radio value="COMPLIANT">合规</el-radio>
            <el-radio value="PARTIAL">部分合规</el-radio>
            <el-radio value="NON_COMPLIANT">不合规</el-radio>
            <el-radio value="NOT_APPLICABLE">不适用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="合规证据">
          <el-input v-model="assessForm.evidence" type="textarea" :rows="3" placeholder="说明满足条款的证据来源" />
        </el-form-item>
        <el-form-item label="差距描述" v-if="needsGaps(assessForm.complianceStatus)">
          <el-input v-model="assessForm.gaps" type="textarea" :rows="3" placeholder="描述不合规的具体差距" />
        </el-form-item>
        <el-form-item label="评估人">
          <el-input v-model="assessForm.assessorName" placeholder="如：陈工" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAssess = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssess">保存评估</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

interface Clause {
  id: number
  projectId: number
  clauseNo: string
  clauseTitle: string
  sectionTitle: string
  sectionOrder: number
  clauseOrder: number
  complianceStatus: string
  evidence: string | null
  gaps: string | null
  assessorId: number | null
  assessorName: string | null
  assessedAt: string | null
}

interface Project {
  id: number
  projectName: string
}

const projectList = ref<Project[]>([])
const projectId = ref<number | null>(null)
const rawItems = ref<Clause[]>([])
const loaded = ref(false)
const initializing = ref(false)
const checking = ref(false)
const saving = ref(false)
const showAssess = ref(false)
const assessForm = ref<Clause | null>(null)

const stats = ref({ total: 0, compliant: 0, partial: 0, nonCompliant: 0, notApplicable: 0, pending: 0, complianceRate: 0 })

const sections = computed(() => {
  const map = new Map<string, { id: number; title: string; order: number; clauses: Clause[] }>()
  for (const item of rawItems.value) {
    const key = item.sectionTitle || '未分类'
    if (!map.has(key)) {
      map.set(key, { id: item.sectionOrder, title: key, order: item.sectionOrder, clauses: [] })
    }
    map.get(key)!.clauses.push(item)
  }
  const arr = Array.from(map.values()).sort((a, b) => a.order - b.order)
  for (const s of arr) {
    const total = s.clauses.length
    const evaluated = s.clauses.filter(c => c.complianceStatus && c.complianceStatus !== 'PENDING').length
    s.progress = total === 0 ? 0 : Math.round(evaluated * 100 / total)
  }
  return arr
})

const arc = computed(() => Math.round(stats.value.complianceRate / 100 * 264))

const statusTagType = (s: string) => {
  return ({ COMPLIANT: 'success', PARTIAL: 'warning', NON_COMPLIANT: 'danger', NOT_APPLICABLE: 'info', PENDING: 'info' } as any)[s] || 'info'
}

const statusLabel = (s: string) => {
  return ({ COMPLIANT: '合规', PARTIAL: '部分合规', NON_COMPLIANT: '不合规', NOT_APPLICABLE: '不适用', PENDING: '待评估' } as any)[s] || s
}

const needsGaps = (s: string) => s === 'PARTIAL' || s === 'NON_COMPLIANT'

const formatDate = (s: string | null) => s ? s.substring(0, 10) : ''

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    const data = res.data?.data
    if (Array.isArray(data)) {
      projectList.value = data
    } else if (data?.records) {
      projectList.value = data.records
    } else {
      projectList.value = []
    }
    if (projectList.value.length > 0 && !projectId.value) {
      projectId.value = projectList.value[0].id
      await loadChecklist()
    }
  } catch (e) {
    ElMessage.error('获取项目列表失败')
  }
}

const loadChecklist = async () => {
  if (!projectId.value) return
  loaded.value = false
  try {
    const res = await request.get(`/compliance/iec62304/checklist/${projectId.value}`)
    const data = res.data?.data
    rawItems.value = Array.isArray(data) ? data : []
    loaded.value = true
    if (rawItems.value.length > 0) {
      await loadStats()
    } else {
      stats.value = { total: 0, compliant: 0, partial: 0, nonCompliant: 0, notApplicable: 0, pending: 0, complianceRate: 0 }
    }
  } catch (e: any) {
    ElMessage.error('获取清单失败：' + (e?.response?.data?.message || e.message))
    loaded.value = true
  }
}

const loadStats = async () => {
  if (!projectId.value) return
  try {
    const res = await request.get(`/compliance/iec62304/checklist/${projectId.value}/stats`)
    stats.value = res.data?.data || stats.value
  } catch (e) {
    console.warn('stats failed', e)
  }
}

const initTemplate = async () => {
  if (!projectId.value) return
  initializing.value = true
  try {
    const res = await request.post(`/compliance/iec62304/checklist/${projectId.value}/init`)
    const count = res.data?.data ?? 0
    ElMessage.success(count > 0 ? `已初始化 ${count} 条条款` : '清单已存在，跳过初始化')
    await loadChecklist()
  } catch (e: any) {
    ElMessage.error('初始化失败：' + (e?.response?.data?.message || e.message))
  } finally {
    initializing.value = false
  }
}

const onProjectChange = async () => {
  await loadChecklist()
}

const openAssess = (clause: Clause) => {
  assessForm.value = { ...clause }
  showAssess.value = true
}

const saveAssess = async () => {
  if (!assessForm.value) return
  if (!assessForm.value.complianceStatus) {
    ElMessage.warning('请选择合规状态')
    return
  }
  saving.value = true
  try {
    const params: any = {
      status: assessForm.value.complianceStatus,
      evidence: assessForm.value.evidence || '',
      gaps: assessForm.value.gaps || '',
      assessorName: assessForm.value.assessorName || ''
    }
    await request.post(`/compliance/iec62304/checklist/${assessForm.value.id}/assess`, null, { params })
    ElMessage.success('评估已保存')
    showAssess.value = false
    await loadChecklist()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

const runFullCheck = async () => {
  if (!projectId.value) return
  try {
    await ElMessageBox.confirm('一键检查将把所有 PENDING 条款标记为"部分合规"并写入"系统自动"评估人，是否继续？', '一键合规检查', { type: 'warning' })
  } catch {
    return
  }
  checking.value = true
  try {
    const res = await request.post(`/compliance/iec62304/checklist/${projectId.value}/run-full-check`)
    const data = res.data?.data
    ElMessage.success(`扫描完成：更新 ${data?.updatedCount ?? 0} 条 / 共 ${data?.totalCount ?? 0} 条`)
    await loadChecklist()
  } catch (e: any) {
    ElMessage.error('扫描失败：' + (e?.response?.data?.message || e.message))
  } finally {
    checking.value = false
  }
}

const exportChecklist = () => {
  if (!rawItems.value.length) {
    ElMessage.warning('当前项目尚无条款记录，请先初始化清单或选择其他项目')
    return
  }
  const headers = ['条款号', '所属章节', '条款标题', '合规状态', '证据', '差距', '评估人', '评估时间']
  const statusMap: Record<string, string> = {
    COMPLIANT: '合规', PARTIAL: '部分合规', NON_COMPLIANT: '不合规', NOT_APPLICABLE: '不适用', PENDING: '待评估'
  }
  const lines = [headers.join(',')]
  rawItems.value.forEach((it: Clause) => {
    lines.push([
      it.clauseNo || '',
      `"${(it.sectionTitle || '').replace(/"/g, '""')}"`,
      `"${(it.clauseTitle || '').replace(/"/g, '""')}"`,
      statusMap[it.complianceStatus] || it.complianceStatus || '-',
      `"${(it.evidence || '').replace(/"/g, '""')}"`,
      `"${(it.gaps || '').replace(/"/g, '""')}"`,
      it.assessorName || '',
      formatDate(it.assessedAt)
    ].join(','))
  })
  const summary = [
    '',
    `# 合规概览`,
    `# 项目: ${(projectList.value.find(p => p.id === projectId.value) || {}).projectName || projectId.value || ''}`,
    `# 合规: ${stats.value.compliant} | 部分合规: ${stats.value.partial} | 不合规: ${stats.value.nonCompliant} | 不适用: ${stats.value.notApplicable} | 待评估: ${stats.value.pending}`,
    `# 合规率: ${stats.value.complianceRate}%`,
    `# 导出时间: ${new Date().toISOString()}`
  ].join('\n')
  const csv = '﻿' + summary + '\n' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `IEC62304合规清单-${projectId.value || 'unselected'}-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${rawItems.value.length} 条条款，合规率 ${stats.value.complianceRate}%`)
}

onMounted(() => {
  fetchProjects()
})
</script>

<style scoped>
.iec62304-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title h2 {
  font-size: 20px;
  color: #303133;
}

.project-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
}

.bar-label {
  font-size: 13px;
  color: #606266;
}

.loaded-tip {
  font-size: 12px;
  color: #909399;
  margin-left: 12px;
}

.progress-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.progress-ring {
  width: 100px;
  height: 100px;
  position: relative;
}

.progress-ring svg {
  transform: rotate(-90deg);
}

.progress-ring .text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 20px;
  font-weight: 700;
  color: #303133;
}

.progress-stats {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-item {
  text-align: center;
  padding: 8px;
  border-radius: 6px;
}

.stat-item .num {
  font-size: 24px;
  font-weight: 700;
}

.stat-item .lbl {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.checklist-section {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h3 {
  font-size: 16px;
  color: #303133;
}

.clause-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  margin-bottom: 10px;
  transition: all 0.2s;
}

.clause-item:hover {
  border-color: #409eff;
  box-shadow: 0 1px 4px rgba(64, 158, 255, 0.1);
}

.clause-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.clause-no {
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
  font-family: monospace;
}

.clause-title {
  font-size: 14px;
  color: #303133;
  margin: 4px 0 0;
}

.clause-meta {
  display: flex;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  flex-wrap: wrap;
}
</style>
