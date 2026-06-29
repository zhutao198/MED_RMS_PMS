<template>
  <div class="req-create-container">
    <div class="page-header">
      <div class="page-title">创建需求</div>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <div class="wizard-card">
      <div class="wizard-steps">
        <div
          v-for="(step, index) in steps"
          :key="index"
          class="wizard-step"
          :class="{ active: currentStep === index + 1, completed: currentStep > index + 1 }"
          @click="currentStep > index + 1 && (currentStep = index + 1)"
        >
          <div class="step-icon">{{ index + 1 }}</div>
          <div class="step-label">{{ step }}</div>
        </div>
      </div>

      <div class="wizard-body">
        <!-- Step 1: 选择需求层级 -->
        <div v-if="currentStep === 1" class="step-content">
          <div class="form-section">
            <div class="section-title">选择需求层级</div>
            <div class="form-group">
              <label>所属项目 <span class="required">*</span></label>
              <el-select v-model="formData.projectId" placeholder="请选择所属项目" style="width: 100%">
                <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </div>
            <div class="level-cards">
              <div
                v-for="level in levels"
                :key="level.value"
                class="level-card"
                :class="{ selected: formData.level === level.value }"
                @click="formData.level = level.value"
              >
                <div class="level-icon">{{ level.icon }}</div>
                <div class="level-name">{{ level.label }}</div>
                <div class="level-desc">{{ level.description }}</div>
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="section-title">基本信息</div>
            <div class="form-row">
              <div class="form-group">
                <label>需求编号</label>
                <el-input v-model="formData.requirementNo" placeholder="系统自动生成" disabled />
              </div>
              <div class="form-group">
                <label>优先级 <span class="required">*</span></label>
                <el-select v-model="formData.priority" placeholder="请选择" style="width: 100%">
                  <el-option label="必须（MUST）" value="MUST" />
                  <el-option label="应该（SHOULD）" value="SHOULD" />
                  <el-option label="可以（COULD）" value="COULD" />
                  <el-option label="不做（WONT）" value="WONT" />
                </el-select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>需求来源 <span class="required">*</span></label>
                <el-select v-model="formData.source" placeholder="请选择需求来源" style="width: 100%">
                  <!-- P2-5：扩展为8档需求来源（用户访谈/法规标准/竞品分析/历史项目/客户投诉/专家评审/系统日志/其他） -->
 <el-option label="用户访谈 USER_INTERVIEW" value="USER_INTERVIEW" />
 <el-option label="法规标准 REGULATION" value="REGULATION" />
 <el-option label="竞品分析 COMPETITOR" value="COMPETITOR" />
 <el-option label="历史项目 HISTORICAL_PROJECT" value="HISTORICAL_PROJECT" />
 <el-option label="客户投诉 CUSTOMER_COMPLAINT" value="CUSTOMER_COMPLAINT" />
 <el-option label="专家评审 EXPERT_REVIEW" value="EXPERT_REVIEW" />
 <el-option label="系统日志 SYSTEM_LOG" value="SYSTEM_LOG" />
 <el-option label="其他 OTHER" value="OTHER" />
                </el-select>
              </div>
              <div class="form-group">
                <label>来源编号</label>
                <el-input v-model="formData.sourceNo" placeholder="原始需求编号/法规条款号" />
              </div>
            </div>
            <div class="form-group">
              <label>需求标题 <span class="required">*</span></label>
              <el-input
                v-model="formData.title"
                placeholder="请输入需求标题，控制在50字以内"
                maxlength="50"
                show-word-limit
              />
            </div>
          </div>
        </div>

        <!-- Step 2: 填写详细信息 -->
        <div v-if="currentStep === 2" class="step-content">
          <div class="form-section">
            <div class="section-title">需求描述</div>
            <div class="form-group">
              <label>详细描述 <span class="required">*</span></label>
              <el-input
                v-model="formData.description"
                type="textarea"
                :rows="5"
                placeholder="请详细描述需求内容，包括功能、性能、安全性等要求"
              />
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>适用产品</label>
                <el-select v-model="formData.productId" placeholder="请选择" style="width: 100%">
                  <el-option label="心电监护仪 v3.0" value="ecg-v3" />
                  <el-option label="脉搏血氧仪 v2.1" value="spo2-v2" />
                </el-select>
              </div>
              <div class="form-group">
                <label>期望版本</label>
                <el-select v-model="formData.targetVersion" placeholder="请选择" style="width: 100%">
                  <el-option label="v3.0.0" value="3.0.0" />
                  <el-option label="v3.1.0" value="3.1.0" />
                </el-select>
              </div>
              <div class="form-group">
                <label>截止日期</label>
                <el-date-picker v-model="formData.dueDate" type="date" placeholder="选择日期" style="width: 100%" />
              </div>
            </div>
            <div class="form-group">
              <label>备注</label>
              <el-input
                v-model="formData.remarks"
                type="textarea"
                :rows="2"
                placeholder="补充说明、相关背景或其他需要告知评审者的信息"
              />
            </div>
          </div>
        </div>

        <!-- Step 3: 关联追溯 -->
        <div v-if="currentStep === 3" class="step-content">
          <div class="form-section">
            <div class="section-title">法规关联</div>
            <div class="trace-section">
              <div class="trace-row">
                <span class="trace-label">适用法规</span>
                <span class="trace-value">IEC 60601-1:2020 医用电气设备安全通用要求</span>
                <el-button size="small" type="primary" @click="handleAddRegulation">添加</el-button>
              </div>
              <div class="trace-row">
                <span class="trace-label">SOUP 组件</span>
                <span class="trace-value warning">未关联 SOUP</span>
                <el-button size="small" type="primary" @click="handleAddSoup">添加</el-button>
              </div>
            </div>
          </div>

          <div class="form-section">
            <div class="section-title">上游需求（父级）</div>
            <el-table :data="upstreamReqs" border size="small" style="font-size: 13px">
              <el-table-column prop="requirementNo" label="需求编号" width="140" />
              <el-table-column prop="title" label="需求标题" />
              <el-table-column prop="level" label="层级" width="80" />
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button size="small" text type="danger" @click="handleRemoveUpstream($index)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" style="margin-top: 8px" @click="handleAddUpstream">关联上游需求</el-button>
          </div>
        </div>

        <!-- Step 4: 提交评审 -->
        <div v-if="currentStep === 4" class="step-content">
          <div class="form-section">
            <div class="section-title">提交确认</div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="需求编号">自动生成</el-descriptions-item>
              <el-descriptions-item label="需求层级">
                <el-tag size="small">{{ formData.level || '未选择' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="需求标题" :span="2">{{ formData.title || '—' }}</el-descriptions-item>
              <el-descriptions-item label="优先级">
                <el-tag size="small">{{ formData.priority }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="期望版本">{{ formData.targetVersion || '未指定' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <div class="form-section">
            <div class="section-title">签名确认（21 CFR Part 11）</div>
            <div class="signature-section">
              <div class="signer-info">
                <el-avatar size="large">{{ (userStore.userInfo?.realName || '?').slice(0, 1) }}</el-avatar>
                <div>
                  <div class="signer-name">{{ userStore.userInfo?.realName || '未登录' }}</div>
                  <div class="signer-time">{{ new Date().toISOString().slice(0, 10) }}</div>
                </div>
              </div>
              <div class="sign-actions">
                <el-button size="small" @click="handleSave">暂存</el-button>
                <el-button type="primary" size="small" @click="handleOpenSignature">📝 电子签名并提交</el-button>
                <el-button type="success" size="small" @click="handleSubmit">提交评审（无签名）</el-button>
              </div>
            </div>
          </div>
        </div>

        <div class="form-actions">
          <el-button v-if="currentStep > 1" @click="currentStep--">上一步</el-button>
          <el-button v-if="currentStep < 4" type="primary" @click="currentStep++">下一步</el-button>
        </div>
      </div>
    </div>

    <!-- 通用电子签名弹窗（21 CFR Part 11 §11.50/§11.70） -->
    <SignatureDialog
      ref="signatureDialogRef"
      v-model="signatureDialogVisible"
      @signed="onSignatureSigned"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { projectApi } from '../../api/project'
import type { Project } from '../../api/project'
import { requirementApi } from '../../api/requirement'
import request from '../../api/request'
import { useUserStore } from '../../stores/user'
import SignatureDialog from '../../components/SignatureDialog.vue'

const currentStep = ref(1)
const steps = ['选择层级', '填写信息', '关联追溯', '提交评审']
const projectList = ref<Project[]>([])
const submitting = ref(false)
const router = useRouter()
const userStore = useUserStore()

// 电子签名弹窗（21 CFR Part 11 §11.50/§11.70）
const signatureDialogRef = ref<InstanceType<typeof SignatureDialog> | null>(null)
const signatureDialogVisible = ref(false)

const levels = [
  { value: 'URS', label: 'URS', description: '用户需求规格说明', icon: '📝' },
  { value: 'PRS', label: 'PRS', description: '产品需求规格说明', icon: '⚙️' },
  { value: 'SRS', label: 'SRS', description: '软件需求规格说明', icon: '📐' },
  { value: 'DRS', label: 'DRS', description: '设计需求规格说明', icon: '🔧' }
]

const formData = reactive({
  projectId: null as number | null,
  level: 'URS',
  requirementNo: '',
  priority: '',
  title: '',
  description: '',
  productId: '',
  targetVersion: '',
  dueDate: '',
  source: '',
  sourceNo: '',
  remarks: '',
  upstreamReqs: [] as any[],
  soupComponentIds: [] as number[],
  regulations: [] as any[]
})

const upstreamReqs = ref([])

const loadProjects = async () => {
  try {
    const res = await projectApi.list()
    projectList.value = res.data?.data || []
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  loadProjects()
})

/**
 * 打开电子签名弹窗（Step 4）
 * 流程：先创建 Draft 需求 → 打开签名弹窗 → 签名成功后再调用 submitReview 推进。
 */
const handleOpenSignature = async () => {
  if (!formData.projectId) {
    ElMessage.warning('请选择所属项目')
    return
  }
  if (!formData.title) {
    ElMessage.warning('请输入需求标题')
    return
  }
  if (!formData.priority || !formData.source) {
    ElMessage.warning('请选择优先级与需求来源')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    const created = await requirementApi.create({
      requirementType: formData.level,
      projectId: formData.projectId!,
      title: formData.title,
      description: formData.description || '',
      priority: formData.priority,
      source: formData.source,
      sourceNo: formData.sourceNo,
      status: 'Draft',
    } as any)
    const newId = created?.data?.data?.id
    if (!newId) {
      ElMessage.error('需求创建失败，未返回 id')
      return
    }
    // 打开签名弹窗 — 签名成功后再走 submitReview
    signatureDialogRef.value?.open({
      entityType: 'requirement',
      entityId: newId,
      documentNo: created?.data?.data?.requirementNo,
      docTitle: formData.title,
      signContext: `新建需求 ${formData.title}（${formData.level}）的电子签名确认。`,
      reason: '新建需求签名',
      meaningCode: 'approve',
      onSigned: async () => {
        // 签名成功 → 提交评审（FR-0.17：单 reviewer 模式提交即通过）
        if (userStore.userInfo?.id) {
          try {
            await requirementApi.submitReview(newId, userStore.userInfo.id, '创建并电子签名后提交评审')
          } catch (e) {
            console.warn('提交评审失败（已签名）:', e)
          }
        }
        ElMessage.success('签名与提交评审完成')
        router.push(`/requirements/${newId}`)
      },
    })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

const onSignatureSigned = () => {
  // 父级默认无操作（业务回调在 open() 的 onSigned 中处理）
}

const handleSubmit = async () => {
  if (!formData.projectId) {
    ElMessage.warning('请选择所属项目')
    return
  }
  if (!formData.level) {
    ElMessage.warning('请选择需求层级')
    return
  }
  if (!formData.title) {
    ElMessage.warning('请输入需求标题')
    return
  }
  if (!formData.priority) {
    ElMessage.warning('请选择优先级')
    return
  }
  if (!formData.source) {
    ElMessage.warning('请选择需求来源')
    return
  }
  if (formData.source === 'REGULATION' && !formData.sourceNo) {
    ElMessage.warning('法规来源必填来源编号（条款号）')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    // 1. 创建需求（仅 Draft 状态）
    const created = await requirementApi.create({
      requirementType: formData.level,
      projectId: formData.projectId!,
      title: formData.title,
      description: formData.description || '',
      priority: formData.priority,
      source: formData.source,
      sourceNo: formData.sourceNo,
      status: 'Draft'
    } as any)
    const newId = created?.data?.data?.id
    // 2. 提交评审（FR-0.17：单 reviewer 模式提交即通过）
    if (newId && userStore.userInfo?.id) {
      try {
        await requirementApi.submitReview(newId, userStore.userInfo.id, '创建后提交评审')
      } catch (e) {
        console.warn('提交评审失败（需求已创建为 Draft）:', e)
      }
    }
    ElMessage.success('需求创建并提交评审成功')
    router.push(newId ? `/requirements/${newId}` : '/requirements')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

const handleSave = async () => {
  if (!formData.projectId || !formData.title) {
    ElMessage.warning('请至少填写项目与标题再暂存')
    return
  }
  if (submitting.value) return
  submitting.value = true
  try {
    await requirementApi.create({
      requirementType: formData.level || 'URS',
      projectId: formData.projectId!,
      title: formData.title,
      description: formData.description || '',
      priority: formData.priority || 'MUST',
      source: formData.source || 'INTERNAL',
      sourceNo: formData.sourceNo,
      status: 'Draft'
    } as any)
    ElMessage.success('已暂存为草稿')
    router.push('/requirements')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '暂存失败')
  } finally {
    submitting.value = false
  }
}

const handleAddRegulation = async () => {
  // v1.43 P1 修复：从后端法规库选择真实法规条目
  try {
    const res: any = await request.get('/compliance/regulations')
    const list = (res?.data?.data || []) as any[]
    if (!Array.isArray(list) || list.length === 0) {
      ElMessage.warning('暂无可关联的法规')
      return
    }
    const { value } = await ElMessageBox.prompt(
      `共 ${list.length} 项法规（前 10 条）：\n${list.slice(0, 10).map((r: any) => `  ${r.id}  ${r.regulationCode || r.code || '-'}  ${r.regulationName || r.name || '-'}`).join('\n')}${list.length > 10 ? '\n  …' : ''}\n\n请输入要关联的法规 ID：`,
      '关联法规',
      { inputPattern: /^\d+$/, inputErrorMessage: '请输入有效的法规 ID', confirmButtonText: '关联', cancelButtonText: '取消' }
    )
    const selected = list.find((r: any) => String(r.id) === String(value))
    if (!selected) {
      ElMessage.warning('未找到该 ID 的法规')
      return
    }
    formData.regulations = [...(formData.regulations || []), selected]
    ElMessage.success(`已关联法规：${selected.regulationName || selected.regulationCode || selected.id}`)
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('加载法规失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleAddSoup = async () => {
  // v1.43 P1 修复：从后端拉真实 SOUP 列表，弹出选择对话框
  try {
    const res: any = await request.get('/requirement/soup-components', { params: { size: 100 } })
    const list = (res?.data?.data?.records || res?.data?.data || []) as any[]
    if (!Array.isArray(list) || list.length === 0) {
      ElMessage.warning('暂无 SOUP 组件可关联')
      return
    }
    // 用 ElMessageBox.prompt 简化处理：让用户输入 SOUP ID
    const { value } = await ElMessageBox.prompt(
      `共 ${list.length} 个 SOUP 组件（ID / 名称）：\n${list.slice(0, 10).map((s: any) => `  ${s.id}  ${s.componentName || s.name || '-'}`).join('\n')}${list.length > 10 ? '\n  …' : ''}\n\n请输入要关联的 SOUP ID：`,
      '关联 SOUP 组件',
      { inputPattern: /^\d+$/, inputErrorMessage: '请输入有效的 SOUP ID', confirmButtonText: '关联', cancelButtonText: '取消' }
    )
    const selected = list.find((s: any) => String(s.id) === String(value))
    if (!selected) {
      ElMessage.warning('未找到该 ID 的 SOUP 组件')
      return
    }
    // 将选中的 SOUP 暂存到本地（提交时一并发送）
    formData.soupComponentIds = [...(formData.soupComponentIds || []), selected.id]
    ElMessage.success(`已关联 SOUP：${selected.componentName || selected.name || selected.id}`)
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('加载 SOUP 失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleAddUpstream = async () => {
  try {
    const res: any = await requirementApi.list({ page: 1, size: 20 })
    const list = res?.data?.data?.records || []
    const candidates = list
      .filter((r: any) => r.id && !upstreamReqs.value.find((u: any) => u.id === r.id))
      .slice(0, 10)
    if (candidates.length === 0) {
      ElMessage.warning('暂无可关联的需求')
      return
    }
    const first = candidates[0]
    upstreamReqs.value.push({
      id: first.id,
      requirementNo: first.requirementNo,
      title: first.title,
      level: first.requirementType
    })
    ElMessage.success(`已关联上游：${first.requirementNo} ${first.title}`)
  } catch (e: any) {
    ElMessage.error(`查询上游需求失败：${e?.message || '未知错误'}`)
  }
}

const handleRemoveUpstream = (idx: number) => {
  const removed = upstreamReqs.value.splice(idx, 1)[0]
  if (removed) ElMessage.success(`已移除：${removed.requirementNo}`)
}
</script>

<style scoped>
.req-create-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.wizard-card {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.wizard-steps {
  display: flex;
  background: #f5f7fa;
  padding: 0 40px;
}

.wizard-step {
  flex: 1;
  padding: 16px 0;
  text-align: center;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.wizard-step.active {
  color: #409eff;
  font-weight: 600;
}

.wizard-step.completed {
  color: #67c23a;
}

.step-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e4e7ed;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.wizard-step.active .step-icon {
  background: #409eff;
}

.wizard-step.completed .step-icon {
  background: #67c23a;
}

.wizard-body {
  padding: 32px 40px;
}

.step-content {
  min-height: 400px;
}

.form-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 2px solid #409eff;
}

.level-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.level-card {
  border: 2px solid #e4e7ed;
  border-radius: 10px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.level-card:hover {
  border-color: #409eff;
  transform: translateY(-2px);
}

.level-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.level-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.level-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.level-desc {
  font-size: 12px;
  color: #909399;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
  margin-bottom: 8px;
  display: block;
}

.required {
  color: #f56c6c;
  margin-left: 2px;
}

.trace-section {
  background: #f9f9f9;
  border-radius: 8px;
  padding: 16px;
}

.trace-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding: 10px;
  background: #fff;
  border-radius: 6px;
}

.trace-label {
  font-size: 13px;
  color: #606266;
  min-width: 80px;
}

.trace-value {
  flex: 1;
  font-size: 13px;
  color: #303133;
  background: #f5f7fa;
  padding: 6px 10px;
  border-radius: 4px;
}

.trace-value.warning {
  background: #fff3e0;
  color: #e65100;
}

.signature-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #f9f9f9;
  border-radius: 8px;
}

.signer-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.signer-name {
  font-weight: 600;
  color: #303133;
}

.signer-time {
  font-size: 12px;
  color: #909399;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid #ebeef5;
}
</style>