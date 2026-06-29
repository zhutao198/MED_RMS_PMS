<template>
  <div class="project-members-add-container">
    <el-page-header @back="$router.back()" :content="`为项目 #${projectId} 添加成员`" class="page-back" />

    <el-card class="form-card" v-loading="submitting">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="用户ID" prop="userId">
          <el-input-number v-model="form.userId" :min="1" style="width: 100%" />
          <span class="form-tip">R105 D2 修复：请填写要添加的 user.id（不留默认值避免硬编码到 admin）</span>
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="如：张三" maxlength="50" />
        </el-form-item>
        <el-form-item label="项目角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="项目经理" value="PROJECT_MANAGER" />
            <el-option label="需求工程师" value="REQUIREMENT_ENGINEER" />
            <el-option label="开发工程师" value="DEVELOPER" />
            <el-option label="测试工程师" value="TESTER" />
            <el-option label="QA" value="QA" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" prop="department">
          <el-input v-model="form.department" placeholder="如：研发部" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="submit">添加</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { projectMemberApi } from '@/api/project'

const route = useRoute()
const router = useRouter()
const projectId = Number(route.params.id)
const formRef = ref<FormInstance>()
const submitting = ref(false)
// R105 D2 修复：移除硬编码默认值 1；userId 由用户必填输入（避免任何新成员都被关联到 admin）
const form = ref({ userId: undefined as number | undefined, realName: '', role: 'REQUIREMENT_ENGINEER', department: '' })
const rules: FormRules = {
  userId: [{ required: true, message: '请填写 userId', trigger: 'blur' }],
  realName: [{ required: true, message: '请填写姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const submit = async () => {
  await formRef.value?.validate().catch(() => null)
  if (!form.value.realName) return
  submitting.value = true
  try {
    // WHY: 后端 ProjectMember 需要 projectId + userId + role 同时存在
    await projectMemberApi.add({
      projectId,
      userId: form.value.userId,
      realName: form.value.realName,
      role: form.value.role,
      department: form.value.department,
      status: 'ACTIVE',
    } as any)
    ElMessage.success('成员已添加')
    router.push(`/projects/${projectId}/members`)
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.project-members-add-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.form-card { max-width: 600px; }
.form-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
