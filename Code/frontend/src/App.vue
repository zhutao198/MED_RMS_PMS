<template>
  <div id="app">
    <div class="app-header">
      <h1>🏥 Med-RMS 医疗器械需求管理系统</h1>
      <div class="header-right">
        <!-- v1.43 BUG #62 修复：铃铛 badge 接真实未读数（之前硬编码 :value="3" :hidden="true" 永远不显示） -->
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
          <el-icon size="20" @click="$router.push('/notifications')" style="cursor:pointer">🔔</el-icon>
        </el-badge>
        <el-tag type="success">{{ currentProjectName }}</el-tag>
        <span>{{ userStore.userInfo?.realName || '未登录' }}<template v-if="userStore.userInfo">（{{ roleLabel }}）</template></span>
      </div>
    </div>

    <div class="main-layout">
      <div class="sidebar">
        <div class="menu-group">导航菜单</div>
        <div class="menu-item" :class="{ active: $route.path === '/dashboard' }" @click="$router.push('/dashboard')">
          📊 仪表盘
        </div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/requirements') }" @click="$router.push('/requirements')">
          📋 需求管理
        </div>
        <div class="menu-item" :class="{ active: $route.path === '/requirements/kanban' }" @click="$router.push('/requirements/kanban')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🗂 需求看板</div>
        <div class="menu-item" :class="{ active: $route.path === '/requirements/quality' }" @click="$router.push('/requirements/quality')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🎯 质量评分</div>
        <div class="menu-item" :class="{ active: $route.path === '/requirements/ai-assist' }" @click="$router.push('/requirements/ai-assist')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🤖 AI 辅助分析</div>
        <div class="menu-item" :class="{ active: $route.path === '/decompose' }" @click="$router.push('/decompose')">
          🔨 需求拆解
        </div>
        <div class="menu-item" :class="{ active: $route.path === '/testcases' }" @click="$router.push('/testcases')">
          🧪 测试用例
        </div>
        <div class="menu-item" @click="$router.push('/traceability')">🔗 追溯管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/traceability/import' }" @click="$router.push('/traceability/import')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📥 追溯导入</div>
        <div class="menu-item" @click="$router.push('/trace-graph')">🕸️ 追溯图谱</div>
        <div class="menu-item" @click="$router.push('/changes')">📝 变更管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/changes/approvals' }" @click="$router.push('/changes/approvals')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;✅ 我的审批</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/compliance') }" @click="$router.push('/compliance')">✅ 合规管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/compliance/iec62304' }" @click="$router.push('/compliance/iec62304')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📋 IEC 62304 清单</div>
        <div class="menu-item" :class="{ active: $route.path === '/compliance/dhf' }" @click="$router.push('/compliance/dhf')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📦 DHF 证据包</div>
        <div class="menu-item" :class="{ active: $route.path === '/compliance/erps' }" @click="$router.push('/compliance/erps')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📤 NMPA eRPS 导出</div>
        <div class="menu-item" :class="{ active: $route.path === '/compliance/regulation-impact' }" @click="$router.push('/compliance/regulation-impact')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📜 法规影响分析</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/esignature') }" @click="$router.push('/esignature')">✍️ 电子签名</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/risk') }" @click="$router.push('/risk')">⚠️ 风险管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/risk/fmea' }" @click="$router.push('/risk/fmea')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🛠 FMEA 编辑器</div>
        <div class="menu-item" :class="{ active: $route.path === '/risks/matrix' }" @click="$router.push('/risks/matrix')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🌡 风险矩阵</div>
        <div class="menu-item" :class="{ active: $route.path === '/risks/monitoring' }" @click="$router.push('/risks/monitoring')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📈 风险监控</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/projects') }" @click="$router.push('/projects')">📁 项目管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/projects/templates' }" @click="$router.push('/projects/templates')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📋 合规模板</div>
        <div class="menu-item" :class="{ active: $route.path === '/projects/gantt' }" @click="$router.push('/projects/gantt')">📅 甘特图</div>
        <div class="menu-item" :class="{ active: $route.path === '/projects/ipd' }" @click="$router.push('/projects/ipd')">🚦 IPD 阶段门</div>
        <div class="menu-item" :class="{ active: $route.path === '/milestones' }" @click="$router.push('/milestones')">🎯 里程碑</div>
        <div class="menu-item" :class="{ active: $route.path === '/projects/resources' }" @click="$router.push('/projects/resources')">👥 资源管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/projects/worklog' }" @click="$router.push('/projects/worklog')">⏱ 工时统计</div>
        <div class="menu-item" :class="{ active: $route.path === '/requirement-pool' }" @click="$router.push('/requirement-pool')">📥 需求池</div>
        <div class="menu-item" :class="{ active: $route.path === '/requirement-tasks' }" @click="$router.push('/requirement-tasks')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🔨 需求→任务</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/reports') }" @click="$router.push('/reports')">📊 报表中心</div>
        <div class="menu-item" :class="{ active: $route.path === '/reports/export' }" @click="$router.push('/reports/export')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📤 报告导出</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/audit-logs') }" @click="$router.push('/audit-logs')">🔐 审计日志</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/system') }" @click="$router.push('/system/users')">⚙️ 系统管理</div>
        <div class="menu-item" :class="{ active: $route.path === '/system/migration' }" @click="$router.push('/system/migration')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📥 数据迁移</div>
        <div class="menu-item" :class="{ active: $route.path === '/system/login-logs' }" @click="$router.push('/system/login-logs')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🔑 登录日志</div>
        <div class="menu-item" :class="{ active: $route.path === '/system/operation-logs' }" @click="$router.push('/system/operation-logs')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;📋 操作日志</div>
        <div class="menu-item" :class="{ active: $route.path === '/system/profile' }" @click="$router.push('/system/profile')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;👤 个人中心</div>
        <div class="menu-item" :class="{ active: $route.path.startsWith('/system/roles') }" @click="$router.push('/system')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🔐 角色权限</div>
        <div class="menu-item" :class="{ active: $route.path === '/system/organization' }" @click="$router.push('/system/organization')" style="padding-left: 32px; font-size: 13px;">&nbsp;&nbsp;🏢 组织架构</div>
        <div class="menu-item" :class="{ active: $route.path === '/notifications' }" @click="$router.push('/notifications')">🔔 通知</div>
      </div>

      <div class="content-area">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { notificationApi } from '@/api/notification'
import { requestFetch } from '@/api/request'

const userStore = useUserStore()
const route = useRoute()
const currentProjectName = ref('心电监护仪 v3.0')
const unreadCount = ref(0)

const ROLE_LABELS: Record<string, string> = {
  admin: '系统管理员',
  pm: '项目经理',
  re: '需求工程师',
  reviewer: '评审员',
  risk_mgr: '风险管理员',
  qa_mgr: 'QA 主管',
  compliance: '合规专员',
  viewer: '只读用户'
}

const roleLabel = computed(() => {
  const u = userStore.userInfo?.username
  return ROLE_LABELS[u || ''] || '用户'
})

// v1.43 拉取未读数
const loadUnreadCount = async () => {
  const userId = userStore.userInfo?.id
  if (!userId) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await notificationApi.getUnreadCount(userId)
    unreadCount.value = res.data.data?.count || 0
  } catch {
    unreadCount.value = 0
  }
}

const handleNotificationUpdated = () => loadUnreadCount()

async function loadCurrentProject() {
  const projectId = localStorage.getItem('currentProjectId') || route.query.projectId
  if (!projectId) return
  try {
    // P1 统一：走 requestFetch，自动处理 401/403 refresh
    const resp = await requestFetch(`/projects/${projectId}`)
    if (resp && resp.ok) {
      const json = await resp.json()
      const p = json.data || json
      if (p?.name) currentProjectName.value = p.name
    }
  } catch (e) {
    // 静默失败，保留默认
  }
}

onMounted(() => {
  loadCurrentProject()
  loadUnreadCount()
  window.addEventListener('notification-updated', handleNotificationUpdated)
})

onUnmounted(() => {
  window.removeEventListener('notification-updated', handleNotificationUpdated)
})

watch(() => route.query.projectId, loadCurrentProject)
watch(() => userStore.userInfo?.id, loadUnreadCount)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  background: #f0f2f5;
}

#app {
  min-height: 100vh;
}

.app-header {
  height: 60px;
  background: #1a1a2e;
  color: #fff;
  display: flex;
  align-items: center;
  padding: 0 24px;
  justify-content: space-between;
}

.app-header h1 {
  font-size: 18px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.main-layout {
  display: flex;
  height: calc(100vh - 60px);
}

.sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  overflow-y: auto;
  flex-shrink: 0;
}

.menu-item {
  padding: 12px 20px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
  border-left: 3px solid transparent;
}

.menu-item:hover {
  background: #f5f7fa;
  color: #409eff;
}

.menu-item.active {
  background: #ecf5ff;
  color: #409eff;
  border-left-color: #409eff;
  font-weight: 600;
}

.menu-group {
  padding: 8px 20px 4px;
  font-size: 12px;
  color: #909399;
  font-weight: 600;
  text-transform: uppercase;
}

.content-area {
  flex: 1;
  overflow-y: auto;
}
</style>