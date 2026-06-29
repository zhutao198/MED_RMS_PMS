<template>
  <div class="project-create-container">
    <el-page-header @back="$router.back()" content="新建项目" class="page-back" />

    <el-card class="form-card" v-loading="submitting">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" label-position="right">
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" placeholder="如：新一代影像 AI 平台" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="项目目标、范围、关键里程碑等" />
        </el-form-item>
        <el-form-item label="项目经理" prop="managerId">
          <el-input-number v-model="form.managerId" :min="1" />
          <span class="form-tip">从系统域用户管理获取 userId</span>
        </el-form-item>
        <el-form-item label="项目状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="计划中" value="PLANNING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">创建项目</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { projectApi } from '@/api/project'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = ref({
  projectName: '',
  description: '',
  managerId: 1,
  status: 'PLANNING',
  startDate: '',
  endDate: '',
})
const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
}

const submit = async () => {
  await formRef.value?.validate().catch(() => null)
  if (!form.value.projectName) return
  submitting.value = true
  try {
    // WHY: 提交字段严格对齐后端 Project DTO（projectNo 由后端自动生成，managerId 为必填）
    const res = await projectApi.create(form.value as any)
    ElMessage.success('项目创建成功')
    const id = res.data?.data?.id
    router.push(id ? `/projects/${id}` : '/projects')
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.project-create-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.form-card { max-width: 720px; }
.form-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
