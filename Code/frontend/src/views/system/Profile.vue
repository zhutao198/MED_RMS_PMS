<template>
  <div class="profile-container">
    <div class="page-header">
      <div class="page-title">个人中心</div>
    </div>

    <!-- R92 修复：后端已实现 /system/profile + /system/users/{id}/change-password（R87 临时挂的"功能开发中"已撤除） -->

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card class="user-card">
          <div class="avatar-wrap">
            <el-avatar :size="80">{{ user.realName?.slice(0, 1) || user.username?.slice(0, 1) || '?' }}</el-avatar>
          </div>
          <h3 class="user-name">{{ user.realName || user.username || '-' }}</h3>
          <div class="user-meta">
            <div>用户名：{{ user.username || '-' }}</div>
            <div>部门：{{ user.department || '-' }}</div>
            <div>邮箱：{{ user.email || '-' }}</div>
            <div>手机：{{ user.phone || '-' }}</div>
            <div>角色：<el-tag size="small">{{ user.role || '-' }}</el-tag></div>
          </div>
          <el-button type="warning" plain style="width:100%; margin-top: 16px;" @click="showPwdDialog = true">修改密码</el-button>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card>
          <template #header><span>资料编辑</span></template>
          <el-form :model="form" label-width="100px" v-loading="loading">
            <el-form-item label="姓名">
              <el-input v-model="form.realName" maxlength="50" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="form.email" type="email" />
            </el-form-item>
            <el-form-item label="手机">
              <el-input v-model="form.phone" />
            </el-form-item>
            <el-form-item label="部门">
              <el-input v-model="form.department" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showPwdDialog" title="修改密码" width="420px">
      <el-form :model="pwdForm" label-width="100px" :rules="pwdRules" ref="pwdFormRef">
        <el-form-item label="旧密码" prop="oldPwd">
          <el-input v-model="pwdForm.oldPwd" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPwd">
          <el-input v-model="pwdForm.newPwd" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPwd">
          <el-input v-model="pwdForm.confirmPwd" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPwdDialog = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="changePwd">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import request from '@/api/request'

const user = ref<any>({})
const form = ref({ realName: '', email: '', phone: '', department: '' })
const loading = ref(false)
const saving = ref(false)

const showPwdDialog = ref(false)
const pwdSaving = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPwd: '', newPwd: '', confirmPwd: '' })
const pwdRules: FormRules = {
  oldPwd: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPwd: [{ required: true, min: 6, message: '新密码至少 6 位', trigger: 'blur' }],
  confirmPwd: [{
    validator: (_r, v, cb) => v === pwdForm.newPwd ? cb() : cb(new Error('两次输入不一致')),
    trigger: 'blur',
  }],
}

const loadProfile = async () => {
  loading.value = true
  try {
    // WHY: 后端无 /system/profile 标准端点时，优先用本地 user 缓存（登录返回）
    const res = await request.get('/system/profile').catch(() => null)
    if (res?.data?.data) {
      user.value = res.data.data
    } else {
      const cached = localStorage.getItem('currentUser')
      if (cached) user.value = JSON.parse(cached)
    }
    form.value = {
      realName: user.value.realName || '',
      email: user.value.email || '',
      phone: user.value.phone || '',
      department: user.value.department || '',
    }
  } finally {
    loading.value = false
  }
}

const save = async () => {
  saving.value = true
  try {
    await request.put(`/system/users/${user.value.id}`, form.value)
    ElMessage.success('已保存')
    Object.assign(user.value, form.value)
    localStorage.setItem('currentUser', JSON.stringify(user.value))
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '后端接口暂未提供'))
  } finally {
    saving.value = false
  }
}

const changePwd = async () => {
  await pwdFormRef.value?.validate().catch(() => null)
  pwdSaving.value = true
  try {
    await request.post(`/system/users/${user.value.id}/change-password`, {
      oldPassword: pwdForm.oldPwd, newPassword: pwdForm.newPwd,
    })
    ElMessage.success('密码已修改')
    showPwdDialog.value = false
    pwdForm.oldPwd = pwdForm.newPwd = pwdForm.confirmPwd = ''
  } catch (e: any) {
    ElMessage.error('修改失败：' + (e?.response?.data?.message || e?.message || '后端接口暂未提供'))
  } finally {
    pwdSaving.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 16px; }
.page-title { font-size: 20px; font-weight: 600; }
.user-card { text-align: center; }
.avatar-wrap { display: flex; justify-content: center; margin-bottom: 12px; }
.user-name { margin: 8px 0 16px; }
.user-meta { text-align: left; font-size: 13px; color: #606266; line-height: 28px; }
</style>
