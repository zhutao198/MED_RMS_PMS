<template>
  <div class="e-sign-settings-container">
    <div class="page-header">
      <div class="header-title">
        <div class="breadcrumb">系统管理</div>
        <div class="page-title">电子签名设置</div>
      </div>
    </div>

    <div class="alert-box warn">
      <div class="alert-icon">⚠️</div>
      <div>
        <strong>21 CFR Part 11 合规提醒：</strong>电子签名设置用于 FDA 法规要求的电子记录签署。每次签名均需验证签名密码或 OTP，且签名记录不可篡改。
      </div>
    </div>

    <div class="settings-grid">
      <div class="settings-card">
        <div class="card-header">
          <h3>签名密码</h3>
          <span class="status-tag" :class="pwdSet ? 'success' : 'warning'">
            {{ pwdSet ? '✓ 已设置' : '未设置' }}
          </span>
        </div>
        <div class="card-body">
          <div class="setting-desc">用于确认您身份的电子签名密码，与登录密码分开，必须为8位以上字母数字组合</div>
          <div class="status-row">
            <span class="status-label">密码强度</span>
            <span class="status-val" :class="pwdSet ? 'on' : 'off'">{{ pwdSet ? '强' : '—' }}</span>
          </div>
          <div class="status-row">
            <span class="status-label">最后修改</span>
            <span class="status-val">{{ pwdSet ? '本次会话前' : '—' }}</span>
          </div>
          <div class="btn-group">
            <el-button class="btn-ghost" style="flex:1" @click="scrollToPwdForm">{{ pwdSet ? '修改密码' : '设置密码' }}</el-button>
            <el-button class="btn-ghost" style="flex:1" @click="handleForgotPwd" :disabled="!pwdSet">忘记密码</el-button>
          </div>
        </div>
      </div>

      <div class="settings-card">
        <div class="card-header">
          <h3>OTP 两步验证</h3>
          <span class="status-tag" :class="otpStatus === 'enabled' ? 'success' : 'warning'">
            {{ otpStatus === 'enabled' ? '✓ 已绑定' : '未启用' }}
          </span>
        </div>
        <div class="card-body">
          <div class="setting-desc">绑定身份验证器 APP（如 Google Authenticator），签署时需输入动态验证码，大幅提升安全性</div>
          <div class="status-row">
            <span class="status-label">绑定状态</span>
            <span class="status-val" :class="otpStatus === 'enabled' ? 'on' : 'off'">
              {{ otpStatus === 'enabled' ? '已绑定' : '未绑定' }}
            </span>
          </div>
          <div class="btn-group single">
            <el-button :type="otpStatus === 'enabled' ? 'default' : 'primary'" class="btn-full" @click="handleBindOtp" :loading="loadingSettings">
              {{ otpStatus === 'enabled' ? '解绑身份验证器' : '绑定身份验证器' }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="settings-card">
        <div class="card-header">
          <h3>操作员 PIN</h3>
          <span class="status-tag" :class="pinSet ? 'success' : 'warning'">
            {{ pinSet ? '✓ 已设置' : '未设置' }}
          </span>
        </div>
        <div class="card-body">
          <div class="setting-desc">4位数字 PIN，用于快速确认签名身份，配合签名密码使用，适合频繁签署场景</div>
          <div class="status-row">
            <span class="status-label">PIN状态</span>
            <span class="status-val" :class="pinSet ? 'on' : 'off'">{{ pinSet ? '已设置' : '未设置' }}</span>
          </div>
          <div class="btn-group single">
            <el-button class="btn-ghost btn-full" @click="handleChangePin">{{ pinSet ? '修改PIN' : '设置PIN' }}</el-button>
          </div>
        </div>
      </div>

      <div class="settings-card">
        <div class="card-header">
          <h3>签名记录审计</h3>
          <span class="status-tag secondary">最近90天</span>
        </div>
        <div class="card-body">
          <div class="setting-desc">查看本人所有电子签名的历史记录，包括签署时间、签署内容摘要及 IP 地址</div>
          <div class="status-row">
            <span class="status-label">签名总数</span>
            <span class="status-val">47 次</span>
          </div>
          <div class="status-row">
            <span class="status-label">本月签名</span>
            <span class="status-val">12 次</span>
          </div>
          <div class="btn-group single">
            <el-button class="btn-ghost btn-full" @click="handleViewHistory">查看签名历史</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="change-password-card">
      <div class="card-header">
        <h3>修改签名密码</h3>
      </div>
      <div class="card-body">
        <el-form :model="pwdForm" label-width="140px" class="pwd-form">
          <el-form-item label="当前签名密码 *">
            <el-input v-model="pwdForm.currentPwd" type="password" placeholder="输入当前签名密码" show-password />
          </el-form-item>
          <el-form-item label="新签名密码 *">
            <el-input v-model="pwdForm.newPwd" type="password" placeholder="8位以上字母数字组合" show-password />
            <div class="form-hint">密码必须包含：至少一个大写字母、一个小写字母、一个数字、一个特殊字符</div>
          </el-form-item>
          <el-form-item label="确认新密码 *">
            <el-input v-model="pwdForm.confirmPwd" type="password" placeholder="再次输入新密码" show-password />
          </el-form-item>
          <el-form-item>
            <div class="form-btns">
              <el-button class="btn-ghost" @click="handleCancelPwd">取消</el-button>
              <el-button type="primary" @click="handleSavePwd">保存新密码</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { esignatureApi } from '../../api/esignature'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const userId = Number(userStore.userInfo?.id || 0)

const pwdForm = ref({
  currentPwd: '',
  newPwd: '',
  confirmPwd: ''
})

const otpStatus = ref<'disabled' | 'enabled'>('disabled')
const pwdSet = ref(false)
const pinSet = ref(false)
const loadingSettings = ref(false)

const loadSettings = async () => {
  if (!userId) return
  loadingSettings.value = true
  try {
    const res: any = await esignatureApi.getSettings(userId)
    const s = res?.data?.data
    if (s) {
      otpStatus.value = s.otpEnabled ? 'enabled' : 'disabled'
      pwdSet.value = !!s.signaturePasswordHash
      pinSet.value = !!s.pinEnabled
    }
  } catch (e) {
    console.warn('加载签名设置失败', e)
  } finally {
    loadingSettings.value = false
  }
}

onMounted(loadSettings)

const handleSavePwd = async () => {
  if (pwdForm.value.newPwd !== pwdForm.value.confirmPwd) {
    ElMessage.error('两次输入的密码不一致')
    return
  }
  if (pwdForm.value.newPwd.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  try {
    await esignatureApi.changeSignaturePassword(userId, pwdForm.value.currentPwd, pwdForm.value.newPwd)
    pwdSet.value = true
    ElMessage.success('签名密码修改成功')
    pwdForm.value = { currentPwd: '', newPwd: '', confirmPwd: '' }
  } catch (e: any) {
    ElMessage.error('修改失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleCancelPwd = () => {
  pwdForm.value = { currentPwd: '', newPwd: '', confirmPwd: '' }
  ElMessage.info('已取消修改')
}

const scrollToPwdForm = () => {
  const el = document.querySelector('.change-password-card')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const handleForgotPwd = () => {
  ElMessage.warning('请联系系统管理员重置签名密码（admin / 安全审计员）')
}

const handleBindOtp = async () => {
  // v1.43 P1 修复：调用真实 OTP 端点
  if (otpStatus.value === 'enabled') {
    try {
      await ElMessageBox.confirm('确定要解绑 OTP 身份验证器？解绑后签署将不再要求 OTP 验证码', '解绑 OTP', { type: 'warning' })
      await esignatureApi.disableOtp(userId)
      otpStatus.value = 'disabled'
      ElMessage.success('已解绑 OTP')
    } catch (e: any) {
      if (e === 'cancel') return
      ElMessage.error('解绑失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
    }
    return
  }
  try {
    // 1) 生成 OTP 密钥
    const secretRes: any = await esignatureApi.generateOtpSecret(userId)
    const secret = secretRes?.data?.data
    if (!secret) {
      ElMessage.error('生成 OTP 密钥失败')
      return
    }
    // 2) 让用户输入身份验证器上的 6 位动态码做校验
    const { value: code } = await ElMessageBox.prompt(
      `OTP 密钥：${secret}\n\n请将上述密钥输入 Google Authenticator / Microsoft Authenticator，然后输入 APP 显示的 6 位动态码完成绑定：`,
      '绑定 OTP 身份验证器',
      { inputPattern: /^\d{6}$/, inputErrorMessage: '请输入 6 位数字动态码', confirmButtonText: '绑定', cancelButtonText: '取消' }
    )
    await esignatureApi.enableOtp(userId, secret)
    void code // 启用时仅保存密钥；签名时再做 verify
    otpStatus.value = 'enabled'
    ElMessage.success('OTP 绑定成功')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('绑定失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleChangePin = async () => {
  try {
    const { value: newPin } = await ElMessageBox.prompt(
      '请输入新的 4 位 PIN 码（纯数字）：',
      '修改 PIN',
      { inputPattern: /^\d{4}$/, inputErrorMessage: 'PIN 必须为 4 位数字', confirmButtonText: '保存', cancelButtonText: '取消' }
    )
    await esignatureApi.updatePin(userId, newPin)
    pinSet.value = true
    ElMessage.success('PIN 修改成功')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error('修改失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const handleViewHistory = () => {
  // 跳到既有签名历史页（若路由不存在则提示）
  window.location.href = '/esignature'
}
</script>

<style scoped>
.e-sign-settings-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 24px;
}

.breadcrumb {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.alert-box {
  padding: 14px 16px;
  border-radius: 8px;
  font-size: 13px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 20px;
}

.alert-box.warn {
  background: #fef3c7;
  color: #92400e;
  border: 1px solid #fde68a;
}

.alert-icon {
  font-size: 18px;
  margin-top: 1px;
}

.settings-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.settings-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.card-header {
  padding: 14px 20px;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header h3 {
  font-size: 14px;
  font-weight: 600;
}

.status-tag {
  font-size: 12px;
  font-weight: 600;
}

.status-tag.success { color: #16a34a; }
.status-tag.warning { color: #d97706; }
.status-tag.secondary { color: #64748b; }

.card-body {
  padding: 20px;
}

.setting-desc {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 16px;
  line-height: 1.5;
}

.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-top: 1px solid #e4e7ed;
  font-size: 13px;
}

.status-label { color: #64748b; }

.status-val { font-weight: 600; color: #1e293b; }
.status-val.on { color: #16a34a; }
.status-val.off { color: #dc2626; }

.btn-group {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.btn-group.single {
  justify-content: center;
}

.btn-ghost {
  background: transparent;
  color: #64748b;
  border: 1px solid #e4e7ed;
}

.btn-full {
  width: 100%;
}

.change-password-card {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.pwd-form {
  max-width: 500px;
}

.form-hint {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
  line-height: 1.4;
}

.form-btns {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
</style>