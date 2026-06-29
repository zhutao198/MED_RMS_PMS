<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="card-header">
          <h2>🏥 Med-RMS 医疗器械需求管理系统</h2>
        </div>
      </template>
      <el-form :model="loginForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="验证码">
          <div class="captcha-row">
            <el-input v-model="loginForm.captcha" placeholder="请输入右侧 4 位验证码" maxlength="4" style="flex:1" />
            <div class="captcha-img" @click="refreshCaptcha" :title="'点击刷新'">
              <span class="captcha-text">{{ captchaText }}</span>
            </div>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%;" @click="handleLogin">登录</el-button>
        </el-form-item>
        <el-form-item>
          <el-tooltip content="企业 SSO 单点登录（OIDC/SAML）已规划，将于 v1.44 接入" placement="top">
            <el-button class="sso-btn" plain style="width: 100%;" disabled>
              🔐 企业 SSO 单点登录（即将上线）
            </el-button>
          </el-tooltip>
        </el-form-item>
      </el-form>
    </el-card>
    <div class="login-footer">
      <p>本系统符合 21 CFR Part 11 / NMPA / CE MDR / ISO 13485 / IEC 62304 合规要求 · 全部操作留痕可审计</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import request from '../api/request'

const router = useRouter()
const userStore = useUserStore()

const loginForm = reactive({
  username: '',
  password: '',
  captcha: ''
})

const captchaText = ref('')

const refreshCaptcha = () => {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
  let s = ''
  for (let i = 0; i < 4; i++) s += chars[Math.floor(Math.random() * chars.length)]
  captchaText.value = s
}
refreshCaptcha()

const handleLogin = async () => {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (loginForm.captcha && loginForm.captcha.toUpperCase() !== captchaText.value) {
    ElMessage.warning('验证码错误')
    refreshCaptcha()
    return
  }
  try {
    const res = await request.post<any, any>('/auth/login', {
      username: loginForm.username,
      password: loginForm.password
    })
    const data = res.data?.data
    if (data?.token) {
      userStore.setToken(data.token)
      // Bug 1 修复：同时存储 accessToken 和 refreshToken，供 request.ts 自动刷新
      localStorage.setItem('accessToken', data.token)
      if (data.refreshToken) {
        localStorage.setItem('refreshToken', data.refreshToken)
      }
      userStore.setUserInfo({
        id: data.userId,
        username: data.username,
        realName: data.realName
      })
      // R94 修复：同步存 localStorage.currentUser，供 SignatureList 等需要按当前用户过滤的页面使用
      localStorage.setItem('currentUser', JSON.stringify({
        id: data.userId,
        username: data.username,
        realName: data.realName,
        role: data.role
      }))
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      ElMessage.error('登录失败')
      refreshCaptcha()
    }
  } catch {
    ElMessage.error('登录失败，用户名或密码错误')
    refreshCaptcha()
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
}

.card-header h2 {
  text-align: center;
  color: #303133;
  font-size: 20px;
}

.login-footer {
  position: fixed;
  bottom: 24px;
  left: 0;
  right: 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.85);
  font-size: 12px;
  line-height: 1.6;
  padding: 0 16px;
}

.login-footer p {
  margin: 0;
}

.captcha-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.captcha-img {
  width: 100px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(45deg, #1e90ff, #87ceeb);
  color: #fff; font-size: 20px; font-weight: 700; letter-spacing: 4px;
  border-radius: 4px; cursor: pointer; user-select: none;
  font-family: 'Courier New', monospace;
}
.captcha-text { text-shadow: 1px 1px 2px rgba(0,0,0,0.3); }
.sso-btn { margin-top: -4px; }
</style>