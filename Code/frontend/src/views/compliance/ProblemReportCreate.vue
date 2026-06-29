<template>
  <div class="pr-create-container">
    <div class="page-header">
      <div class="page-title-group">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/problem-report' }">问题报告</el-breadcrumb-item>
          <el-breadcrumb-item>新建</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">📝 新建问题报告</div>
        <div class="page-subtitle">CAPA（纠正/预防措施）入口 · 关联至 ISO 13485:2016 条款 10</div>
      </div>
    </div>

    <!-- 步骤条 -->
    <div class="step-bar">
      <div class="step-item active">
        <div class="step-circle">1</div>
        <div class="step-label">填写信息</div>
      </div>
      <div class="step-item">
        <div class="step-circle">2</div>
        <div class="step-label">关联需求</div>
      </div>
      <div class="step-item">
        <div class="step-circle">3</div>
        <div class="step-label">提交</div>
      </div>
    </div>

    <!-- 问题报告基本信息 -->
    <el-card class="info-card">
      <template #header>
        <span>🔍 问题报告信息</span>
      </template>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="问题编号">
              <el-input v-model="form.reportCode" disabled placeholder="保存后自动生成" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="问题类型" required>
              <el-select v-model="form.problemType" style="width:100%;">
                <el-option label="不合格项（NC）" value="nonconformance" />
                <el-option label="观察项（Obs）" value="observation" />
                <el-option label="不良事件" value="incident" />
                <el-option label="客户投诉" value="customer" />
                <el-option label="审计发现" value="audit" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="严重程度" required>
              <el-select v-model="form.severity" style="width:100%;">
                <el-option label="严重（Critical）" value="CRITICAL" />
                <el-option label="主要（Major）" value="MAJOR" />
                <el-option label="次要（Minor）" value="MINOR" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="问题来源" required>
              <el-select v-model="form.sourceType" style="width:100%;">
                <el-option label="内部发现" value="internal" />
                <el-option label="外部反馈" value="external" />
                <el-option label="审计发现" value="audit" />
                <el-option label="监管机构" value="regulatory" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="问题标题" required>
          <el-input v-model="form.title" placeholder="简要描述问题，如：评审专家发现 URS 版本管理流程缺失签名控制" />
        </el-form-item>
        <el-form-item label="问题描述" required>
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="详细描述问题现象、发现时间、发现人、涉及范围等信息..." />
        </el-form-item>
        <el-form-item label="根本原因">
          <el-input v-model="form.rootCause" type="textarea" :rows="3" placeholder="初步分析问题的根本原因（可在后续分析中补充）..." />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="发现日期" required>
              <el-date-picker v-model="form.discoveryDate" type="date" value-format="YYYY-MM-DD" style="width:100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望关闭">
              <el-date-picker v-model="form.expectedCloseDate" type="date" value-format="YYYY-MM-DD" style="width:100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="关联项目">
          <el-select v-model="form.projectId" placeholder="选择项目（可选）" style="width:100%;" filterable clearable>
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- CAPA 提示 -->
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      <template #title>系统将根据您填写的问题信息自动推荐 CAPA 模板</template>
      <div>您可以在提交后进入问题详情页面补充完整的根本原因分析（RCA）和 CAPA 计划。</div>
    </el-alert>

    <!-- 关联需求 -->
    <el-card class="info-card">
      <template #header>
        <div class="card-header-row">
          <span>🔗 关联需求（可选）</span>
          <el-button size="small" @click="addRequirement">+ 添加关联</el-button>
        </div>
      </template>
      <div v-if="relatedReqs.length === 0" class="empty-tip">暂未关联需求</div>
      <div v-else>
        <div v-for="(req, idx) in relatedReqs" :key="idx" class="related-req">
          <div class="related-req-info">
            <div class="related-req-id">{{ req.requirementNo }}</div>
            <div class="related-req-name">{{ req.title }}</div>
          </div>
          <el-tag :class="'badge ' + (req.level || '').toLowerCase()" size="small">{{ req.level }}</el-tag>
          <el-button size="small" text type="danger" @click="relatedReqs.splice(idx, 1)">移除</el-button>
        </div>
      </div>
    </el-card>

    <div class="footer-actions">
      <el-button @click="$router.back()">← 取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">提交问题报告</el-button>
    </div>

    <!-- 选择需求 -->
    <el-dialog v-model="showReqPicker" title="选择关联需求" width="640px">
      <el-input v-model="reqSearch" placeholder="搜索需求编号/标题" style="margin-bottom: 12px;" clearable />
      <el-table :data="filteredReqs" border height="400" @row-click="pickReq">
        <el-table-column prop="requirementNo" label="编号" width="160" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="requirementType" label="层级" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 新建问题报告页 (合规域独立页面 P0 修复)
 * 对应原型：problem-report-create-原型.html
 * 对应路由：/compliance/problem-report/create
 * 对应后端：ComplianceController.createProblemReport
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()
const saving = ref(false)
const projectList = ref<any[]>([])
const reqList = ref<any[]>([])
const reqSearch = ref('')
const showReqPicker = ref(false)
const relatedReqs = ref<any[]>([])

const form = ref({
  reportCode: 'PR-' + new Date().getFullYear() + '-自动生成',
  problemType: 'nonconformance',
  severity: 'MAJOR',
  sourceType: 'internal',
  title: '',
  description: '',
  rootCause: '',
  discoveryDate: new Date().toISOString().slice(0, 10),
  expectedCloseDate: '',
  projectId: null as number | null
})

const filteredReqs = computed(() => {
  const kw = reqSearch.value.toLowerCase()
  if (!kw) return reqList.value.slice(0, 100)
  return reqList.value.filter(r =>
    (r.requirementNo || '').toLowerCase().includes(kw) ||
    (r.title || '').toLowerCase().includes(kw)
  ).slice(0, 100)
})

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
  } catch {}
}

const fetchRequirements = async () => {
  try {
    const res = await request.get('/requirements', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    reqList.value = Array.isArray(d) ? d : (d?.records || [])
  } catch {}
}

const addRequirement = () => {
  showReqPicker.value = true
  if (reqList.value.length === 0) fetchRequirements()
}

const pickReq = (row: any) => {
  if (relatedReqs.value.find(r => r.id === row.id)) {
    ElMessage.warning('已添加过该需求')
    return
  }
  relatedReqs.value.push({
    id: row.id,
    requirementNo: row.requirementNo,
    title: row.title,
    level: row.requirementType || 'URS'
  })
  showReqPicker.value = false
}

const submit = async () => {
  if (!form.value.title || !form.value.description) {
    ElMessage.warning('请填写问题标题和描述')
    return
  }
  saving.value = true
  try {
    const payload: any = {
      title: form.value.title,
      severity: form.value.severity,
      sourceType: form.value.sourceType,
      problemType: form.value.problemType,
      description: form.value.description,
      rootCause: form.value.rootCause,
      discoveryDate: form.value.discoveryDate,
      expectedCloseDate: form.value.expectedCloseDate,
      projectId: form.value.projectId,
      affectedItems: relatedReqs.value.map(r => r.requirementNo).join(','),
      status: 'Open'
    }
    const res = await request.post('/compliance/problem-reports', payload)
    const newId = res.data?.data?.id
    ElMessage.success('问题报告创建成功')
    if (newId) {
      router.push(`/compliance/problem-report/${newId}`)
    } else {
      router.push('/compliance/problem-report')
    }
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

onMounted(fetchProjects)
</script>

<style scoped>
.pr-create-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; }
.page-title-group { }
.page-title { font-size: 22px; font-weight: 700; color: #303133; margin-top: 8px; margin-bottom: 4px; }
.page-subtitle { font-size: 14px; color: #909399; }
.step-bar { display: flex; gap: 8px; margin-bottom: 24px; position: relative; background: #fff; padding: 16px; border-radius: 8px; }
.step-item { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; position: relative; }
.step-circle { width: 28px; height: 28px; border-radius: 50%; background: #e4e7ed; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; color: #fff; }
.step-item.active .step-circle { background: #409eff; }
.step-label { font-size: 12px; color: #909399; }
.step-item.active .step-label { color: #409eff; font-weight: 600; }
.info-card { margin-bottom: 16px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.empty-tip { text-align: center; color: #909399; padding: 24px; }
.related-req { border: 1px solid #e4e7ed; border-radius: 6px; padding: 12px; margin-bottom: 8px; display: flex; align-items: center; gap: 12px; }
.related-req-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.related-req-id { font-size: 13px; font-weight: 600; color: #409eff; }
.related-req-name { font-size: 12px; color: #606266; }
.badge { display: inline-flex; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 600; }
.badge.urs { background: #dbeafe; color: #1e40af; }
.badge.prs { background: #dcfce7; color: #166534; }
.badge.srs { background: #fef3c7; color: #92400e; }
.badge.drs { background: #fce7f3; color: #9d174d; }
.footer-actions { display: flex; justify-content: flex-end; gap: 12px; padding: 16px 0; }
</style>
