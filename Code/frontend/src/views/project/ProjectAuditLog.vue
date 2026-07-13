<template>
  <div class="audit-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span style="font-size:16px;font-weight:600">🔍 项目级审计追踪</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="projectId" placeholder="选择项目" filterable style="width:220px" @change="fetchLogs">
              <el-option v-for="p in projectList" :key="p.id" :label="getProjectLabel(p.id)" :value="p.id" />
            </el-select>
            <el-button @click="fetchLogs">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="logs" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="operatorName" label="操作人" width="120" />
        <el-table-column prop="action" label="操作" width="120" />
        <el-table-column prop="entityType" label="实体类型" width="100" />
        <el-table-column prop="entityId" label="实体ID" width="80" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="操作时间" width="170" />
      </el-table>
      <el-empty v-if="!loading && logs.length === 0" description="暂无审计日志" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useProject } from '@/composables/useProject'
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const projectId = ref<number | null>(null)
const logs = ref<any[]>([])
const loading = ref(false)

const fetchProjects = async () => {
  try {
    const d = res.data?.data
    if (projectList.value.length > 0 && !projectId.value) projectId.value = projectList.value[0].id
  } catch {}
}

const fetchLogs = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    const res = await request.get('/compliance/audit-logs', {
      params: { entityType: 'PROJECT', entityId: projectId.value, page: 0, size: 200 }
    })
    logs.value = res.data?.data || []
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

const { projectList, getProjectLabel, ensureLoaded } = useProject()

onMounted(async () => {
  await fetchProjects()
  await fetchLogs()
})
</script>

<style scoped>
.audit-view { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
