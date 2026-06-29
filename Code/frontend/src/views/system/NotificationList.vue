<template>
  <div class="notification-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的通知</span>
          <div class="header-actions">
            <el-button size="small" @click="loadNotifications" :loading="loading">刷新</el-button>
            <el-button
              size="small"
              type="primary"
              :disabled="unreadCount === 0"
              @click="confirmMarkAllRead"
            >
              全部标为已读 ({{ unreadCount }})
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="notifications.length === 0"
              @click="confirmClearAll"
            >
              清空全部
            </el-button>
          </div>
        </div>
      </template>

      <!-- P1-28: 分类 tab + 全部已读按钮 -->
      <el-tabs v-model="activeTab" @tab-change="onTabChange" class="cat-tabs">
        <el-tab-pane v-for="t in CATEGORY_TABS" :key="t.key" :label="`${t.label} (${tabCount(t.key)})`" :name="t.key" />
      </el-tabs>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable style="width: 120px" @change="loadNotifications">
            <el-option label="全部" value="" />
            <el-option label="未读" value="UNREAD" />
            <el-option label="已读" value="READ" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filterType" placeholder="全部" clearable style="width: 160px" @change="loadNotifications">
            <el-option label="全部" value="" />
            <el-option v-for="t in NOTIFICATION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="统计">
          <el-tag>共 {{ filteredNotifications.length }} 条</el-tag>
          <el-tag type="danger" style="margin-left: 8px">未读 {{ unreadCount }}</el-tag>
        </el-form-item>
      </el-form>

      <el-table
        :data="pagedNotifications"
        border
        stripe
        v-loading="loading"
        empty-text="暂无通知"
        style="width: 100%"
        @row-click="handleRowClick"
      >
        <!-- P1-28: 未读红点（数字 0 时不显示） -->
        <el-table-column label="" width="14">
          <template #default="{ row }">
            <span v-if="row.status === 'UNREAD'" class="unread-dot" title="未读"></span>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="getTypeColor(row.type)">{{ getTypeName(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180">
          <template #default="{ row }">
            <span :class="{ 'unread-title': row.status === 'UNREAD' }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            <el-link
              v-if="row.sourceId"
              :href="getSourceLink(row)"
              target="_blank"
              type="primary"
              :underline="false"
            >
              {{ getSourceName(row) }}
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'UNREAD'" type="danger" size="small">未读</el-tag>
            <el-tag v-else type="info" size="small">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'UNREAD'"
              size="small"
              type="primary"
              @click.stop="handleMarkRead(row)"
            >
              标为已读
            </el-button>
            <el-button v-else size="small" disabled>已读</el-button>
            <el-button size="small" type="danger" @click.stop="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="filteredNotifications.length"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { notificationApi, type Notification } from '@/api/notification'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()

// v1.43 字典类型：5 类真实通知（与 DB t_notification.type 一致）
const NOTIFICATION_TYPES = [
  { value: 'REVIEW_APPROVED', label: '评审通过' },
  { value: 'REVIEW_REJECTED', label: '评审不通过' },
  { value: 'TRACE_BROKEN', label: '追溯断裂' },
  { value: 'RISK_ALERT', label: '风险告警' },
  { value: 'CHANGE_APPROVED', label: '变更批准' },
  { value: 'SYSTEM', label: '系统通知' },
]

// P1-28: 5 大分类 tab（按业务域聚合通知类型）
// WHY: type 字段为细粒度（6 种），业务域为粗粒度（5 类），前端按 type 关键字做归并
const CATEGORY_TABS = [
  { key: 'all', label: '全部' },
  { key: 'requirement', label: '需求' },
  { key: 'change', label: '变更' },
  { key: 'risk', label: '风险' },
  { key: 'compliance', label: '合规' },
  { key: 'signature', label: '签名' }
]
const CATEGORY_KEYWORDS: Record<string, string[]> = {
  requirement: ['REQUIREMENT', 'REVIEW', 'requirement', 'review'],
  change: ['CHANGE', 'change', 'CHANGE_APPROVED'],
  risk: ['RISK', 'risk', 'TRACE_BROKEN'],
  compliance: ['COMPLIANCE', 'compliance', 'IEC', 'GMP'],
  signature: ['SIGNATURE', 'ESIGN', 'signature']
}

const TYPE_COLORS: Record<string, string> = {
  REVIEW_APPROVED: 'success',
  REVIEW_REJECTED: 'danger',
  TRACE_BROKEN: 'warning',
  RISK_ALERT: 'warning',
  CHANGE_APPROVED: 'success',
  SYSTEM: 'info',
}

const TYPE_LINKS: Record<string, string> = {
  REVIEW: '/requirements',
  REQUIREMENT: '/requirements',
  requirement: '/requirements',
  CHANGE: '/changes',
  change: '/changes',
  RISK: '/risk',
  risk: '/risk',
  gate: '/projects/ipd',
  GATE: '/projects/ipd',
}

const currentUserId = computed(() => userStore.userInfo?.id || 1)

const notifications = ref<Notification[]>([])
const loading = ref(false)
const filterStatus = ref<string>('')
const filterType = ref<string>('')
const currentPage = ref(1)
const pageSize = ref(10)
// P1-28: 当前分类 tab
const activeTab = ref('all')

const unreadCount = computed(() => notifications.value.filter(n => n.status === 'UNREAD').length)

// P1-28: 分类 tab 过滤后的列表
const filteredNotifications = computed(() => {
  if (activeTab.value === 'all') return notifications.value
  const kws = CATEGORY_KEYWORDS[activeTab.value] || []
  return notifications.value.filter(n => {
    const t = n.type || ''
    const st = n.sourceType || ''
    return kws.some(k => t.toUpperCase().includes(k.toUpperCase()) || st.toUpperCase().includes(k.toUpperCase()))
  })
})

// P1-28: 标签计数（用于 tab label 括号）
const tabCount = (key: string) => {
  if (key === 'all') return notifications.value.length
  const kws = CATEGORY_KEYWORDS[key] || []
  return notifications.value.filter(n => {
    const t = n.type || ''
    const st = n.sourceType || ''
    return kws.some(k => t.toUpperCase().includes(k.toUpperCase()) || st.toUpperCase().includes(k.toUpperCase()))
  }).length
}

const pagedNotifications = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredNotifications.value.slice(start, start + pageSize.value)
})

// P1-28: tab 切换时重置分页
const onTabChange = () => {
  currentPage.value = 1
}

// P1-28: 行点击即标记已读（仅未读时）
const handleRowClick = (row: Notification) => {
  if (row.status === 'UNREAD') handleMarkRead(row)
}

const loadNotifications = async () => {
  if (!currentUserId.value) {
    ElMessage.warning('未检测到登录用户')
    return
  }
  loading.value = true
  try {
    const res = await notificationApi.getAll(
      currentUserId.value,
      filterStatus.value || undefined,
      filterType.value || undefined
    )
    notifications.value = res.data.data || []
    currentPage.value = 1
  } catch (e: any) {
    const msg = e?.response?.data?.message || e?.message || '加载通知失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

const handleMarkRead = async (row: Notification) => {
  try {
    await notificationApi.markAsRead(row.id)
    ElMessage.success('已标记为已读')
    await loadNotifications()
    // 通知 App.vue 铃铛刷新
    window.dispatchEvent(new CustomEvent('notification-updated'))
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const confirmMarkAllRead = async () => {
  try {
    await ElMessageBox.confirm('确认将所有未读通知标记为已读？', '全部已读', { type: 'warning' })
    await notificationApi.markAllAsRead(currentUserId.value)
    ElMessage.success('已全部标记为已读')
    await loadNotifications()
    window.dispatchEvent(new CustomEvent('notification-updated'))
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const confirmDelete = async (row: Notification) => {
  try {
    await ElMessageBox.confirm(`确认删除通知「${row.title}」？`, '删除通知', { type: 'warning' })
    await notificationApi.deleteNotification(row.id)
    ElMessage.success('已删除')
    await loadNotifications()
    window.dispatchEvent(new CustomEvent('notification-updated'))
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const confirmClearAll = async () => {
  try {
    await ElMessageBox.confirm(
      `确认清空所有 ${notifications.value.length} 条通知？该操作不可撤销。`,
      '清空通知',
      { type: 'warning' }
    )
    await notificationApi.deleteAll(currentUserId.value)
    ElMessage.success('已清空')
    await loadNotifications()
    window.dispatchEvent(new CustomEvent('notification-updated'))
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

const getTypeColor = (type: string) => TYPE_COLORS[type] || 'info'
const getTypeName = (type: string) => NOTIFICATION_TYPES.find(t => t.value === type)?.label || type

const getSourceName = (row: Notification) => {
  const map: Record<string, string> = {
    REVIEW: '查看需求',
    REQUIREMENT: '查看需求',
    requirement: '查看需求',
    CHANGE: '查看变更',
    change: '查看变更',
    RISK: '查看风险',
    risk: '查看风险',
    gate: '查看门控',
    GATE: '查看门控',
  }
  return map[row.sourceType || ''] || (row.sourceType || '-')
}

const getSourceLink = (row: Notification) => {
  if (!row.sourceId || !row.sourceType) return '#'
  const base = TYPE_LINKS[row.sourceType] || '#'
  return `${base}/${row.sourceId}`
}

const formatDate = (d: string) => {
  if (!d) return '-'
  return String(d).replace('T', ' ').substring(0, 19)
}

onMounted(() => {
  loadNotifications()
  // 监听铃铛点击事件
  window.addEventListener('notification-updated', loadNotifications)
})
</script>

<style scoped>
.notification-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; }
.filter-form { margin-bottom: 16px; }
.unread-title { font-weight: 600; color: #303133; }
/* P1-28: 未读红点 */
.unread-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #F56C6C; box-shadow: 0 0 6px rgba(245, 108, 108, 0.6); }
.cat-tabs { margin-bottom: 4px; }
.cat-tabs :deep(.el-tabs__item) { font-weight: 500; }
</style>
