<template>
  <div class="baseline-edit-container">
    <div class="page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/baselines' }">基线管理</el-breadcrumb-item>
          <el-breadcrumb-item>编辑</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">📌 编辑基线</div>
        <div class="page-subtitle">修改基线基本信息。注意：基线内容（需求快照）在锁定状态下不可直接修改。</div>
      </div>
    </div>

    <el-alert v-if="baseline.status === 'LOCKED'" type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
      <template #title>此基线状态为「已锁定」</template>
      <div>锁定时间：{{ formatDate(baseline.lockedAt) || '-' }} 锁定人：{{ baseline.lockedBy || '-' }}。锁定状态下只能修改基本信息，如需修改基线内容请先 <strong>申请解锁</strong>。</div>
    </el-alert>

    <el-card v-loading="loading" class="meta-card">
      <template #header><span>📋 基线元数据</span></template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="基线 ID">{{ baseline.id }}</el-descriptions-item>
        <el-descriptions-item label="基线编号">{{ baseline.baselineNo }}</el-descriptions-item>
        <el-descriptions-item label="关联项目">{{ baseline.projectName || `项目 ${baseline.projectId}` }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="getStatusType(baseline.status)" size="small">{{ getStatusLabel(baseline.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ baseline.createdBy || '-' }} （{{ formatDate(baseline.createdAt) }}）</el-descriptions-item>
        <el-descriptions-item label="需求数量">{{ requirementCount }} 条</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header-row">
          <span>📝 基线基本信息</span>
          <el-button v-if="baseline.status === 'LOCKED'" size="small" type="warning" @click="handleUnlockRequest">🔓 申请解锁</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="100px" style="max-width: 800px;">
        <el-form-item label="基线名称" required>
          <el-input v-model="form.baselineName" :disabled="baseline.status === 'LOCKED'" placeholder="如 v1.0-GMP-里程碑1" />
        </el-form-item>
        <el-form-item label="基线类型" required>
          <el-select v-model="form.baselineType" :disabled="baseline.status === 'LOCKED'" style="width: 100%;">
            <el-option label="里程碑基线" value="milestone" />
            <el-option label="发布基线" value="release" />
            <el-option label="审计基线" value="audit" />
            <el-option label="自定义基线" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目">
          <el-select v-model="form.projectId" disabled style="width: 100%;">
            <el-option :label="baseline.projectName || `项目 ${baseline.projectId}`" :value="baseline.projectId" />
          </el-select>
        </el-form-item>
        <el-form-item label="锁定方式">
          <el-input value="双人电子签名锁定（21 CFR Part 11）" disabled />
        </el-form-item>
        <el-form-item label="基线描述">
          <el-input v-model="form.description" :disabled="baseline.status === 'LOCKED'" type="textarea" :rows="3" placeholder="描述此基线的用途、创建背景或特殊说明..." />
        </el-form-item>
        <el-form-item v-if="baseline.status === 'LOCKED'" label="解锁原因">
          <el-input v-model="unlockReason" placeholder="申请解锁时必须填写原因" />
          <div style="font-size: 12px; color: #e6a23c; margin-top: 4px;">⚠️ 解锁需管理员审批，审批通过后方可编辑基线内容</div>
        </el-form-item>
        <el-form-item>
          <el-button @click="$router.back()">← 返回</el-button>
          <el-button type="primary" :loading="saving" :disabled="baseline.status === 'LOCKED' && !unlockReason" @click="submit">保存修改</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 编辑基线页 (合规域独立页面 P0 修复)
 * 对应原型：baselines-edit-原型.html
 * 对应路由：/compliance/baselines/:id/edit
 * 对应后端：BaselineController（无独立 update 接口；此处只读编辑+申请解锁工作流）
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const loading = ref(false)
const saving = ref(false)
const baseline = ref<any>({})
const requirementCount = ref(0)
const unlockReason = ref('')

const form = ref({
  baselineName: '',
  baselineType: 'milestone',
  projectId: null as number | null,
  description: ''
})

const formatDate = (v?: string | null) => {
  if (!v) return ''
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return ''
}

const getStatusType = (s: string) => s === 'LOCKED' ? 'success' : (s === 'ARCHIVED' ? 'info' : 'warning')
const getStatusLabel = (s: string) => ({ LOCKED: '已锁定', DRAFT: '草稿', ARCHIVED: '已归档' }[s] || s || '-')

const fetchBaseline = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const projectsRes = await request.get('/projects', { params: { page: 0, size: 200 } })
    const pd = projectsRes.data?.data
    const projects: any[] = Array.isArray(pd) ? pd : (pd?.records || [])
    for (const p of projects) {
      try {
        const res = await request.get(`/baselines/project/${p.id}`)
        const list = (res.data?.data || []) as any[]
        const hit = list.find(b => String(b.id) === String(id))
        if (hit) {
          baseline.value = { ...hit, projectName: p.projectName }
          try {
            const arr = JSON.parse(hit.snapshotData || '[]')
            requirementCount.value = Array.isArray(arr) ? arr.length : 0
          } catch {
            requirementCount.value = 0
          }
          form.value.baselineName = hit.baselineName || hit.name || ''
          form.value.projectId = hit.projectId
          form.value.description = hit.description || ''
          break
        }
      } catch {}
    }
  } catch (e: any) {
    ElMessage.error('加载基线失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!form.value.baselineName) {
    ElMessage.warning('请填写基线名称')
    return
  }
  saving.value = true
  try {
    // 后端无独立 update 接口；演示：弹出确认提示
    await ElMessageBox.alert(
      '基线字段（包含锁定状态、需求快照）受 21 CFR Part 11 合规要求保护，' +
      '后端未开放 update 接口。基线创建后字段不可直接修改，如需变更请创建新版基线（"创建基线"按钮）。',
      '保存提示',
      { type: 'info', confirmButtonText: '我知道了' }
    )
  } catch {
  } finally {
    saving.value = false
  }
}

const handleUnlockRequest = () => {
  if (!unlockReason.value) {
    ElMessage.warning('请填写解锁原因')
    return
  }
  ElMessageBox.confirm(
    `确定申请解锁基线 ${baseline.value.baselineNo}？\n原因：${unlockReason.value}\n解锁需管理员审批。`,
    '申请解锁',
    { type: 'warning', confirmButtonText: '提交申请', cancelButtonText: '取消' }
  ).then(() => {
    ElMessage.success('解锁申请已提交（演示）')
  }).catch(() => {})
}

onMounted(fetchBaseline)
</script>

<style scoped>
.baseline-edit-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; }
.page-title { font-size: 22px; font-weight: 700; color: #303133; margin-top: 8px; margin-bottom: 4px; }
.page-subtitle { font-size: 14px; color: #909399; }
.meta-card { margin-bottom: 16px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
</style>
