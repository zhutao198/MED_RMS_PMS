<template>
  <div class="change-create-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>发起变更申请</span>
          <el-button @click="$router.back()">取消</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <!-- R257.1 + R257.3：先选项目（接入 R192 全局项目选择同步） -->
        <el-form-item label="所属项目" prop="projectId">
          <ProjectSelector
            v-model="form.projectId"
            placeholder="请选择项目"
            :sync-to-store="true"
            style="width: 100%"
          />
        </el-form-item>

        <!-- 二级联动：仅在项目选择后显示该项目的已基线需求 -->
        <el-form-item label="变更需求" prop="requirementId">
          <el-select
            v-model="form.requirementId"
            :placeholder="form.projectId ? '请选择要变更的需求（仅显示已基线需求）' : '请先选择项目'"
            filterable
            style="width: 100%"
            :disabled="!form.projectId || !!preSelectedRequirementId"
            :loading="loadingRequirements"
          >
            <el-option
              v-for="req in requirements"
              :key="req.id"
              :label="`${req.requirementNo} - ${req.title}`"
              :value="req.id"
            />
            <template #empty>
              <el-empty
                v-if="form.projectId"
                :image-size="60"
                description="该项目暂无已基线需求"
              />
              <el-empty
                v-else
                :image-size="60"
                description="请先选择项目"
              />
            </template>
          </el-select>
          <div class="field-hint" v-if="form.projectId && requirements.length === 0 && !loadingRequirements">
            <el-icon><InfoFilled /></el-icon>
            提示：未基线化的需求无需走变更流程，可直接编辑修改。
          </div>
        </el-form-item>

        <el-form-item label="变更标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入变更标题" />
        </el-form-item>

        <el-form-item label="变更类型" prop="changeType">
          <!-- R261：变更类型枚举对齐后端白名单 [MINOR, NORMAL, EMERGENCY, DOCUMENT, MAJOR] -->
          <el-select v-model="form.changeType" style="width: 100%">
            <el-option label="小型变更" value="MINOR" />
            <el-option label="普通变更" value="NORMAL" />
            <el-option label="紧急变更" value="EMERGENCY" />
            <el-option label="文档变更" value="DOCUMENT" />
            <el-option label="重大变更" value="MAJOR" />
          </el-select>
        </el-form-item>

        <el-form-item label="紧急程度" prop="urgency">
          <el-select v-model="form.urgency" style="width: 100%">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="CRITICAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级" prop="priority">
          <el-select v-model="form.priority" style="width: 100%">
            <el-option label="CRITICAL" value="CRITICAL" />
            <el-option label="MAJOR" value="MAJOR" />
            <el-option label="MINOR" value="MINOR" />
            <el-option label="TRIVIAL" value="TRIVIAL" />
          </el-select>
        </el-form-item>

        <el-form-item label="影响范围">
          <el-select v-model="form.impactScope" style="width: 100%">
            <el-option label="单系统" value="SINGLE" />
            <el-option label="多系统" value="MULTIPLE" />
            <el-option label="跨模块" value="CROSS_MODULE" />
          </el-select>
        </el-form-item>

        <el-form-item label="变更原因" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="4"
            placeholder="请详细描述变更原因和背景"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting" v-permission="'chg:create'">创建并提交</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        title="变更流程说明"
        type="info"
        :closable="false"
        style="margin-top: 20px"
      >
        <template #default>
          <ol style="margin: 8px 0; padding-left: 20px">
            <li>创建变更申请（草稿状态）</li>
            <li>提交后进入影响分析阶段</li>
            <li>完成影响评估后进入待审批状态</li>
            <li>审批通过后执行变更</li>
            <li>变更执行后需要验证</li>
          </ol>
          <div style="margin-top: 8px; color: #909399; font-size: 12px">
            <strong>边界约定</strong>：仅对已基线化的需求发起变更；未基线化的需求可直接编辑修改（R257 强化约束）。
          </div>
        </template>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { requirementApi } from '@/api/requirement'
import { changeApi } from '@/api/change'
import { useProjectStore } from '@/stores/project'
import ProjectSelector from '@/components/ProjectSelector.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const loadingRequirements = ref(false)
const requirements = ref<any[]>([])

const form = ref({
  // R257.1：项目作为一级选择（与 R192 全局项目选择同步）
  projectId: null as number | null,
  requirementId: 0,
  title: '',
  changeType: 'NORMAL',
  reason: '',
  urgency: 'MEDIUM',
  priority: 'MAJOR',
  impactScope: 'SINGLE',
  requestedBy: 1,
})

const rules: FormRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  requirementId: [{ required: true, message: '请选择要变更的需求', trigger: 'change' }],
  title: [{ required: true, message: '请输入变更标题', trigger: 'blur' }],
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  reason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }],
}

// 从路由 query 预选需求（从项目详情页跳转时）
const preSelectedRequirementId = route.query.requirementId
  ? Number(route.query.requirementId)
  : null

// R257.3：进入页面时优先采用全局 store 当前项目（与 R192 一致）
onMounted(async () => {
  const currentProjectId = projectStore.currentProjectId
  if (currentProjectId && currentProjectId !== -1) {
    form.value.projectId = currentProjectId
    await loadRequirements()
  }
  if (preSelectedRequirementId) {
    form.value.requirementId = preSelectedRequirementId
  }
})

// 监听项目变化，重新加载需求列表
watch(
  () => form.value.projectId,
  async (newId, oldId) => {
    if (newId === oldId) return
    // 项目切换时清空需求选择
    form.value.requirementId = 0
    if (newId) {
      await loadRequirements()
    } else {
      requirements.value = []
    }
  }
)

// R257.1：加载指定项目的已基线需求（按 R255/R257 边界约束，仅 Baseline 状态）
const loadRequirements = async () => {
  if (!form.value.projectId) return
  loadingRequirements.value = true
  try {
    const res = await requirementApi.list({
      projectId: form.value.projectId,
      status: 'Baseline',
      page: 1,
      size: 1000,
    })
    requirements.value = res.data?.data?.records || []
  } catch (e: any) {
    console.error(e)
    ElMessage.error('加载需求列表失败')
    requirements.value = []
  } finally {
    loadingRequirements.value = false
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (!form.value.projectId || !form.value.requirementId || !form.value.reason) {
    ElMessage.warning('请填写必填项')
    return
  }

  submitting.value = true
  try {
    const res = await changeApi.create({
      requirementId: form.value.requirementId,
      changeType: form.value.changeType,
      reason: form.value.reason,
      urgency: form.value.urgency,
      priority: form.value.priority,
      impactScope: form.value.impactScope,
      requestedBy: form.value.requestedBy,
      title: form.value.title || `需求变更-${form.value.requirementId}`,
    })
    const changeId = res.data?.data?.id
    if (changeId) {
      // 自动提交进入影响分析阶段
      await changeApi.submit(changeId)
      ElMessage.success('变更申请已创建并提交，请进行影响评估')
      router.push('/changes')
    }
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.change-create-container {
  padding: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>