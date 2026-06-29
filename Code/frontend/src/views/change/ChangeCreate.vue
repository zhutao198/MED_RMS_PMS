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
        <el-form-item label="需求">
          <el-select
            v-model="form.requirementId"
            placeholder="请选择要变更的需求"
            filterable
            style="width: 100%"
            :disabled="!!preSelectedRequirementId"
          >
            <el-option
              v-for="req in requirements"
              :key="req.id"
              :label="`${req.requirementNo} - ${req.title}`"
              :value="req.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="变更标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入变更标题" />
        </el-form-item>

        <el-form-item label="变更类型" prop="changeType">
          <el-select v-model="form.changeType" style="width: 100%">
            <el-option label="纠正性变更" value="CORRECTIVE" />
            <el-option label="适应性变更" value="ADAPTIVE" />
            <el-option label="完善性变更" value="PERFECTIVE" />
            <el-option label="紧急变更" value="EMERGENCY" />
          </el-select>
        </el-form-item>

        <el-form-item label="紧急程度" prop="urgency">
          <!-- v1.53 P1-9 修复：与 ChangeList/ChangeRequest 统一 4 档 -->
          <el-select v-model="form.urgency" style="width: 100%">
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="紧急" value="CRITICAL" />
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
          <el-button type="primary" @click="handleSubmit" :loading="submitting">创建并提交</el-button>
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
        </template>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { requirementApi } from '@/api/requirement'
import { changeApi } from '@/api/change'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const requirements = ref<any[]>([])

const form = ref({
  requirementId: 0,
  title: '',
  changeType: 'CORRECTIVE',
  reason: '',
  urgency: 'MEDIUM',
  requestedBy: 1,
})

const rules: FormRules = {
  requirementId: [{ required: true, message: '请选择要变更的需求', trigger: 'change' }],
  title: [{ required: true, message: '请输入变更标题', trigger: 'blur' }],
  changeType: [{ required: true, message: '请选择变更类型', trigger: 'change' }],
  reason: [{ required: true, message: '请输入变更原因', trigger: 'blur' }],
}

const preSelectedRequirementId = route.query.requirementId
  ? Number(route.query.requirementId)
  : null

const loadRequirements = async () => {
  try {
    const res = await requirementApi.list({ page: 1, size: 1000 })
    // 只显示已基线化的需求（只有已基线化的需求才能发起变更）
    requirements.value = (res.data?.data?.records || []).filter(
      (req: any) => req.status === 'Baseline'
    )
    if (preSelectedRequirementId) {
      form.value.requirementId = preSelectedRequirementId
    }
  } catch (e) {
    console.error(e)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  if (!form.value.requirementId || !form.value.reason) {
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
    ElMessage.error(e?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadRequirements()
})
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
</style>
