<template>
  <div class="project-edit-container">
    <el-page-header @back="$router.back()" content="编辑项目" class="page-back" />

    <el-card class="form-card" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="项目编号">
          <el-input v-model="form.projectNo" disabled />
        </el-form-item>
        <el-form-item label="项目名称" prop="projectName">
          <el-input v-model="form.projectName" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="项目经理" prop="managerId">
          <el-input-number v-model="form.managerId" :min="1" />
          <span class="form-tip">当前显示：{{ form.managerName || '-' }}</span>
        </el-form-item>
        <el-form-item label="项目状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="计划中" value="PLANNING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">保存修改</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { projectApi } from '@/api/project'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)
const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const form = ref<any>({
  projectNo: '', projectName: '', description: '',
  managerId: 1, managerName: '',
  status: 'PLANNING', startDate: '', endDate: '',
})
const rules: FormRules = {
  projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
}

const load = async () => {
  loading.value = true
  try {
    const res = await projectApi.get(projectId)
    Object.assign(form.value, res.data.data || {})
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  await formRef.value?.validate().catch(() => null)
  if (!form.value.projectName) return
  submitting.value = true
  try {
    // WHY: 编辑时 projectNo 不允许修改，提交时不携带以避免后端校验失败
    const { projectNo, managerName, ...payload } = form.value
    await projectApi.update(projectId, payload as any)
    ElMessage.success('保存成功')
    router.push(`/projects/${projectId}`)
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.project-edit-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.form-card { max-width: 720px; }
.form-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
