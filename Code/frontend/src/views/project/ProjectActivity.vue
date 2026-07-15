<template>
  <div class="activity-timeline">
    <el-card>
      <template #header>
        <div class="card-header">
          <span style="font-size:16px;font-weight:600">🕐 活动流时间线</span>
          <div style="display:flex;gap:8px;align-items:center">
            <ProjectSelector v-model="projectId" @change="loadActivities" />
            <el-button @click="fetchActivities">刷新</el-button>
          </div>
        </div>
      </template>

      <div class="timeline">
        <el-timeline>
          <el-timeline-item v-for="a in activities" :key="a.id"
            :timestamp="a.createdAt" placement="top"
            :color="getColor(a.eventType)">
            <div class="tl-item">
              <div class="tl-header">
                <el-tag :type="getTagType(a.eventType)" size="small">{{ eventLabel(a.eventType) }}</el-tag>
                <span class="tl-user">{{ a.operatorName || a.operatorId }}</span>
                <el-button v-if="a.detail" size="small" text type="primary" @click.stop="toggleExpand(a.id)">
                  {{ expandedIds.has(a.id) ? '收起' : '详情' }}
                </el-button>
              </div>
              <div class="tl-desc">{{ a.summary || a.description }}</div>
              <div v-if="a.detail && expandedIds.has(a.id)" class="tl-detail-box">
                <pre class="tl-detail-json">{{ formatDetail(a.detail) }}</pre>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="activities.length === 0" description="暂无活动记录" />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useProject } from '@/composables/useProject'
import { ref, onMounted } from 'vue'
import ProjectSelector from '@/components/ProjectSelector.vue'
import request from '@/api/request'

const projectId = ref<number | null>(null)
const activities = ref<any[]>([])
const expandedIds = ref<Set<number>>(new Set())

const toggleExpand = (id: number) => {
  const s = new Set(expandedIds.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  expandedIds.value = s
}

const formatDetail = (detail: string) => {
  if (!detail) return ''
  try {
    const obj = JSON.parse(detail)
    return JSON.stringify(obj, null, 2)
  } catch {
    return detail
  }
}

const eventLabel = (evt: string) => {
  const map: Record<string, string> = {
    TASK_CREATED: '创建任务', TASK_UPDATED: '更新任务', TASK_STATUS_CHANGED: '任务状态变更',
    MILESTONE_CREATED: '创建里程碑', MILESTONE_COMPLETED: '里程碑完成',
    PROJECT_CREATED: '创建项目', PROJECT_UPDATED: '更新项目',
    MEMBER_ADDED: '添加成员', MEMBER_REMOVED: '移除成员',
    WORKLOG_SUBMITTED: '填报工时', GATE_CHECKED: '门控检查',
  }
  return map[evt] || evt
}

const getColor = (evt: string) => {
  if (evt?.includes('CREATED') || evt?.includes('ADDED')) return '#67C23A'
  if (evt?.includes('COMPLETED')) return '#409EFF'
  if (evt?.includes('UPDATED') || evt?.includes('CHANGED')) return '#E6A23C'
  return '#909399'
}

const getTagType = (evt: string) => {
  if (evt?.includes('CREATED') || evt?.includes('ADDED')) return 'success' as const
  if (evt?.includes('COMPLETED')) return 'primary' as const
  if (evt?.includes('UPDATED') || evt?.includes('CHANGED')) return 'warning' as const
  return 'info' as const
}

const fetchActivities = async () => {
  if (!projectId.value) return
  try {
    const res = await request.get(`/project-activity/${projectId.value}`)
    activities.value = res.data?.data || []
  } catch {
    activities.value = []
  }
}

const { ensureLoaded } = useProject()

onMounted(async () => {
  await ensureLoaded()
  await fetchActivities()
})
</script>

<style scoped>
.activity-timeline { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.timeline { max-height: 600px; overflow-y: auto; }
.tl-item { font-size: 13px; }
.tl-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.tl-user { font-size: 12px; color: #909399; }
.tl-desc { color: #303133; line-height: 1.5; }
.tl-detail-box { margin-top: 6px; background: #f5f7fa; padding: 8px 12px; border-radius: 4px; max-height: 200px; overflow: auto; }
.tl-detail-json { margin: 0; font-size: 12px; color: #606266; white-space: pre-wrap; word-break: break-all; }
</style>
