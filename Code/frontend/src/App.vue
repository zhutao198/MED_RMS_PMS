<template>
  <div id="app">
    <div class="app-header">
      <h1>🏥 Med-RMS 医疗器械需求管理系统</h1>
      <div class="header-right">
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
        <div v-for="item in visibleMenus" :key="item.path" class="menu-item" :class="{ active: isActive(item) }" :style="item.style || {}" @click="navigate(item)">
          {{ item.label }}
        </div>
      </div>

      <div class="content-area">
        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { notificationApi } from '@/api/notification'
import { requestFetch } from '@/api/request'
import { hasRole, getRoles, getRoleLabel } from '@/utils/auth'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const currentProjectName = ref('心电监护仪 v3.0')
const unreadCount = ref(0)

interface MenuItem {
  label: string
  path: string
  roles: string[]
  parent?: boolean
  style?: string
  activeCheck?: (path: string) => boolean
}

const ALL_MENUS: MenuItem[] = [
  { label: '📊 仪表盘', path: '/dashboard', roles: ['*'] },
  { label: '📋 需求管理', path: '/requirements', roles: ['*'], activeCheck: p => p.startsWith('/requirements') && !p.includes('/kanban') && !p.includes('/quality') && !p.includes('/ai-assist') },
  { label: '🗂 需求看板', path: '/requirements/kanban', roles: ['*'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🎯 质量评分', path: '/requirements/quality', roles: ['admin', 'qa_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🤖 AI 辅助分析', path: '/requirements/ai-assist', roles: ['admin', 'pm', 're'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🔨 需求拆解', path: '/decompose', roles: ['admin', 're'] },
  { label: '🧪 测试用例', path: '/testcases', roles: ['admin', 'qa_mgr', 're'] },
  { label: '🔗 追溯管理', path: '/traceability', roles: ['admin', 're', 'qa_mgr'], activeCheck: p => p.startsWith('/traceability') },
  { label: '📥 追溯导入', path: '/traceability/import', roles: ['admin', 're', 'qa_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🕸️ 追溯图谱', path: '/trace-graph', roles: ['admin', 're', 'qa_mgr'] },
  { label: '📝 变更管理', path: '/changes', roles: ['admin', 'pm', 'qa_mgr', 're'], activeCheck: p => p.startsWith('/changes') && !p.includes('/approvals') },
  { label: '✅ 我的审批', path: '/changes/approvals', roles: ['admin', 'pm', 'qa_mgr', 'reviewer'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '✅ 合规管理', path: '/compliance', roles: ['admin', 'compliance', 'qa_mgr'], activeCheck: p => p.startsWith('/compliance') },
  { label: '📋 IEC 62304 清单', path: '/compliance/iec62304', roles: ['admin', 'compliance', 'qa_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📦 DHF 证据包', path: '/compliance/dhf', roles: ['admin', 'compliance', 'qa_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📤 NMPA eRPS 导出', path: '/compliance/erps', roles: ['admin', 'compliance'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📜 法规影响分析', path: '/compliance/regulation-impact', roles: ['admin', 'compliance'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '✍️ 电子签名', path: '/esignature', roles: ['*'], activeCheck: p => p.startsWith('/esignature') || p.startsWith('/signatures') || p.startsWith('/signature-') },
  { label: '⚠️ 风险管理', path: '/risk', roles: ['admin', 'risk_mgr', 'pm'], activeCheck: p => p.startsWith('/risk') || p.startsWith('/risks') },
  { label: '🛠 FMEA 编辑器', path: '/risk/fmea', roles: ['admin', 'risk_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🌡 风险矩阵', path: '/risks/matrix', roles: ['admin', 'risk_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📈 风险监控', path: '/risks/monitoring', roles: ['admin', 'risk_mgr', 'pm'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📁 项目管理', path: '/projects', roles: ['admin', 'pm', 'pd'], activeCheck: p => p.startsWith('/projects') && !p.includes('/templates') && !p.includes('/gantt') && !p.includes('/ipd') && !p.includes('/resources') && !p.includes('/worklog') },
  { label: '📋 合规模板', path: '/projects/templates', roles: ['admin', 'qa_mgr'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📅 甘特图', path: '/projects/gantt', roles: ['admin', 'pm'], parent: false, style: '' },
  { label: '🚦 IPD 阶段门', path: '/projects/ipd', roles: ['admin', 'pm'], parent: false, style: '' },
  { label: '🎯 里程碑', path: '/milestones', roles: ['admin', 'pm'] },
  { label: '👥 资源管理', path: '/projects/resources', roles: ['admin', 'pm'], parent: false, style: '' },
  { label: '⏱ 工时统计', path: '/projects/worklog', roles: ['admin', 'pm'], parent: false, style: '' },
  { label: '📥 需求池', path: '/requirement-pool', roles: ['admin', 're', 'pd'] },
  { label: '🔨 需求→任务', path: '/requirement-tasks', roles: ['admin', 're', 'pm'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📊 报表中心', path: '/reports', roles: ['*'], activeCheck: p => p.startsWith('/reports') },
  { label: '📤 报告导出', path: '/reports/export', roles: ['*'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🔐 审计日志', path: '/audit-logs', roles: ['admin', 'qa_mgr', 'compliance'] },
  { label: '⚙️ 系统管理', path: '/system/users', roles: ['admin'], activeCheck: p => p.startsWith('/system') },
  { label: '📥 数据迁移', path: '/system/migration', roles: ['admin'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🔑 登录日志', path: '/system/login-logs', roles: ['admin'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '📋 操作日志', path: '/system/operation-logs', roles: ['admin'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '👤 个人中心', path: '/system/profile', roles: ['*'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🔐 角色权限', path: '/system/roles/:id/edit', roles: ['admin'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🏢 组织架构', path: '/system/organization', roles: ['admin'], parent: true, style: 'padding-left: 32px; font-size: 13px;' },
  { label: '🔔 通知', path: '/notifications', roles: ['*'] }
]

const visibleMenus = computed(() => {
  const userRoles = getRoles()
  return ALL_MENUS.filter(m => {
    if (m.roles.includes('*')) return true
    return userRoles.some(r => m.roles.includes(r))
  })
})

function isActive(item: MenuItem): boolean {
  if (item.activeCheck) return item.activeCheck(route.path)
  return route.path === item.path
}

function navigate(item: MenuItem) {
  let target = item.path
  if (target === '/system/roles/:id/edit') target = '/system'
  router.push(target)
}

const roleLabel = computed(() => {
  const r = userStore.userInfo?.role
  return r ? getRoleLabel(r) : '用户'
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