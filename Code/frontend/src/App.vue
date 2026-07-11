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
        <el-menu :default-active="activePath" :default-openeds="openedGroups" @select="onMenuSelect" class="sidebar-menu">
          <template v-for="item in visibleMenus" :key="item.label + (item.path || '')">
            <el-sub-menu v-if="item.children?.length" :index="item.label">
              <template #title><span>{{ item.label }}</span></template>
              <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path!" :class="{ 'menu-item-parent': child.isGroupEntry }">
                {{ child.label }}
              </el-menu-item>
            </el-sub-menu>
            <el-menu-item v-else :index="item.path!">
              {{ item.label }}
            </el-menu-item>
          </template>
        </el-menu>
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
import { getRoles, getRoleLabel } from '@/utils/auth'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const currentProjectName = ref('心电监护仪 v3.0')
const unreadCount = ref(0)

interface MenuChild {
  label: string
  path: string
  roles: string[]
  activeCheck?: (p: string) => boolean
  isGroupEntry?: boolean
}

interface MenuGroup {
  label: string
  roles: string[]
  children?: MenuChild[]
  path?: string
  activeCheck?: (p: string) => boolean
}

const ALL_MENUS: MenuGroup[] = [
  { label: '📊 仪表盘', path: '/dashboard', roles: ['*'] },

  { label: '📋 需求管理', roles: ['*'], children: [
    { label: '需求管理', path: '/requirements', roles: ['*'], isGroupEntry: true, activeCheck: p => p.startsWith('/requirements') && !p.includes('/kanban') && !p.includes('/quality') && !p.includes('/ai-assist') },
    { label: '🗂 需求看板', path: '/requirements/kanban', roles: ['*'] },
    { label: '🎯 质量评分', path: '/requirements/quality', roles: ['admin', 'qa_mgr'] },
    { label: '🤖 AI 辅助分析', path: '/requirements/ai-assist', roles: ['admin', 'pm', 're'] },
    { label: '🔨 需求拆解', path: '/decompose', roles: ['admin', 're'] },
    { label: '📥 需求池', path: '/requirement-pool', roles: ['admin', 're', 'pd'] },
    { label: '🔨 需求→任务', path: '/requirement-tasks', roles: ['admin', 're', 'pm'] },
    { label: '🧪 测试用例', path: '/testcases', roles: ['admin', 'qa_mgr', 're'] },
  ]},

  { label: '🔗 追溯管理', roles: ['admin', 're', 'qa_mgr'], children: [
    { label: '追溯管理', path: '/traceability', roles: ['admin', 're', 'qa_mgr'], isGroupEntry: true, activeCheck: p => p.startsWith('/traceability') },
    { label: '📥 追溯导入', path: '/traceability/import', roles: ['admin', 're', 'qa_mgr'] },
    { label: '🕸️ 追溯图谱', path: '/trace-graph', roles: ['admin', 're', 'qa_mgr'] },
  ]},

  { label: '📝 变更管理', roles: ['admin', 'pm', 'qa_mgr', 're'], children: [
    { label: '变更管理', path: '/changes', roles: ['admin', 'pm', 'qa_mgr', 're'], isGroupEntry: true, activeCheck: p => p.startsWith('/changes') && !p.includes('/approvals') },
    { label: '✅ 我的审批', path: '/changes/approvals', roles: ['admin', 'pm', 'qa_mgr', 'reviewer'] },
  ]},

  { label: '✅ 合规管理', roles: ['admin', 'compliance', 'qa_mgr'], children: [
    { label: '合规管理', path: '/compliance', roles: ['admin', 'compliance', 'qa_mgr'], isGroupEntry: true, activeCheck: p => p.startsWith('/compliance') },
    { label: '📋 IEC 62304 清单', path: '/compliance/iec62304', roles: ['admin', 'compliance', 'qa_mgr'] },
    { label: '📦 DHF 证据包', path: '/compliance/dhf', roles: ['admin', 'compliance', 'qa_mgr'] },
    { label: '📤 NMPA eRPS 导出', path: '/compliance/erps', roles: ['admin', 'compliance'] },
    { label: '📜 法规影响分析', path: '/compliance/regulation-impact', roles: ['admin', 'compliance'] },
    { label: '📋 合规模板', path: '/projects/templates', roles: ['admin', 'qa_mgr'] },
  ]},

  { label: '✍️ 电子签名', path: '/esignature', roles: ['*'], activeCheck: p => p.startsWith('/esignature') || p.startsWith('/signatures') || p.startsWith('/signature-') },

  { label: '⚠️ 风险管理', roles: ['admin', 'risk_mgr', 'pm'], children: [
    { label: '风险管理', path: '/risk', roles: ['admin', 'risk_mgr', 'pm'], isGroupEntry: true, activeCheck: p => p.startsWith('/risk') || p.startsWith('/risks') },
    { label: '🛠 FMEA 编辑器', path: '/risk/fmea', roles: ['admin', 'risk_mgr'] },
    { label: '🌡 风险矩阵', path: '/risks/matrix', roles: ['admin', 'risk_mgr'] },
    { label: '📈 风险监控', path: '/risks/monitoring', roles: ['admin', 'risk_mgr', 'pm'] },
  ]},

  { label: '📁 项目管理', roles: ['admin', 'pm', 'pd'], children: [
    { label: '项目管理', path: '/projects', roles: ['admin', 'pm', 'pd'], isGroupEntry: true, activeCheck: p => p.startsWith('/projects') && !p.includes('/templates') && !p.includes('/gantt') && !p.includes('/ipd') && !p.includes('/resources') && !p.includes('/worklog') },
    { label: '📅 甘特图', path: '/projects/gantt', roles: ['admin', 'pm'] },
    { label: '🚦 IPD 阶段门', path: '/projects/ipd', roles: ['admin', 'pm'] },
    { label: '🎯 里程碑', path: '/milestones', roles: ['admin', 'pm'] },
    { label: '👥 资源管理', path: '/projects/resources', roles: ['admin', 'pm'] },
    { label: '⏱ 工时统计', path: '/projects/worklog', roles: ['admin', 'pm'] },
    { label: '📋 任务看板', path: '/projects/task-board', roles: ['admin', 'pm', 'pd'] },
    { label: '🕐 活动流', path: '/projects/activities', roles: ['admin', 'pm', 'pd'] },
    { label: '🔍 审计追踪', path: '/projects/audit', roles: ['admin', 'qa_mgr', 'compliance'] },
  ]},

  { label: '📊 报表与审计', roles: ['*'], children: [
    { label: '报表中心', path: '/reports', roles: ['*'], isGroupEntry: true, activeCheck: p => p.startsWith('/reports') },
    { label: '📤 报告导出', path: '/reports/export', roles: ['*'] },
    { label: '🔐 审计日志', path: '/audit-logs', roles: ['admin', 'qa_mgr', 'compliance'], activeCheck: p => p.startsWith('/audit-logs') },
  ]},

  { label: '⚙️ 系统管理', roles: ['admin'], children: [
    { label: '系统管理', path: '/system/users', roles: ['admin'], isGroupEntry: true, activeCheck: p => p.startsWith('/system') },
    { label: '👤 个人中心', path: '/system/profile', roles: ['*'] },
    { label: '🏢 组织架构', path: '/system/organization', roles: ['admin'] },
    { label: '📋 操作日志', path: '/system/operation-logs', roles: ['admin'] },
    { label: '🔑 登录日志', path: '/system/login-logs', roles: ['admin'] },
    { label: '🔐 角色权限', path: '/system/roles/:id/edit', roles: ['admin'] },
    { label: '📥 数据迁移', path: '/system/migration', roles: ['admin'] },
  ]},

  { label: '🔔 通知', path: '/notifications', roles: ['*'] },
]

function filterChildren(children: MenuChild[], userRoles: string[]): MenuChild[] {
  return children.filter(c => {
    if (c.roles.includes('*')) return true
    return userRoles.some(r => c.roles.map(x => x.toLowerCase()).includes(r))
  })
}

const visibleMenus = computed(() => {
  const userRoles = getRoles().map(r => r.toLowerCase())
  return ALL_MENUS
    .map(g => {
      if (g.children) {
        const filtered = filterChildren(g.children, userRoles)
        if (filtered.length === 0) return null
        return { ...g, children: filtered }
      }
      if (g.roles.includes('*')) return g
      if (userRoles.some(r => g.roles.map(x => x.toLowerCase()).includes(r))) return g
      return null
    })
    .filter(Boolean) as MenuGroup[]
})

function bestMatch(items: Array<{ path?: string; activeCheck?: (p: string) => boolean }>, currentPath: string): string | null {
  for (const item of items) {
    if (item.path && item.path === currentPath) return item.path
  }
  for (const item of items) {
    if (item.activeCheck && item.activeCheck(currentPath)) return item.path || null
  }
  return null
}

const flatMenuItems = computed(() => {
  const items: Array<{ path?: string; activeCheck?: (p: string) => boolean }> = []
  for (const g of visibleMenus.value) {
    if (g.children) {
      for (const c of g.children) items.push(c)
    } else {
      items.push(g)
    }
  }
  return items
})

const activePath = computed(() => {
  return bestMatch(flatMenuItems.value, route.path) || route.path
})

const openedGroups = computed(() => {
  const opened: string[] = []
  for (const g of visibleMenus.value) {
    if (!g.children?.length) continue
    for (const c of g.children) {
      if (c.path === activePath.value || (c.activeCheck && c.activeCheck(route.path))) {
        opened.push(g.label)
        break
      }
    }
  }
  return opened
})

function onMenuSelect(index: string) {
  if (index === '/system/roles/:id/edit') {
    router.push('/system')
    return
  }
  router.push(index)
}

const roleLabel = computed(() => {
  const r = userStore.userInfo?.role
  return r ? getRoleLabel(r) : '用户'
})

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

.sidebar-menu {
  border-right: none !important;
}

.sidebar-menu .el-menu-item {
  height: 40px;
  line-height: 40px;
  font-size: 13px;
}

.sidebar-menu .el-sub-menu__title {
  height: 42px;
  line-height: 42px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.sidebar-menu .el-menu-item.menu-item-parent {
  font-weight: 500;
  color: #409eff;
}

.content-area {
  flex: 1;
  overflow-y: auto;
}
</style>
