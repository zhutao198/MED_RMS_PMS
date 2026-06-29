<template>
  <div class="qs-container">
    <div class="page-title">
      <h2>🎯 需求质量智能评分（FR-2.4）</h2>
      <div class="header-actions">
        <el-select v-model="filterProject" placeholder="选择项目" clearable style="width: 260px;" @change="loadAll">
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button @click="loadAll">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px;">
      4 维度评分（总分 100）：完整性(25) + 一致性(25) + 可测试性(25) + 合规性(25)。
      等级 A≥90 / B≥80 / C≥60 / D<60，&lt;60 分视为不合格并自动标记 Suspect。
    </el-alert>

    <el-row :gutter="16" v-if="list.length > 0" style="margin-bottom:16px">
      <el-col :span="6"><el-card><div class="stat-value" style="color:#409EFF">{{ avgScore }}</div><div class="stat-label">平均分</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-value" style="color:#67C23A">{{ gradeACount }}</div><div class="stat-label">A 级（优秀）</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-value" style="color:#E6A23C">{{ gradeBCount }}</div><div class="stat-label">B / C 级</div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-value" style="color:#F56C6C">{{ unqualifiedCount }}</div><div class="stat-label">不合格（&lt;60）</div></el-card></el-col>
    </el-row>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="requirementNo" label="需求编号" width="150" />
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column label="总分" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="scoreTag(row.totalScore)" effect="dark">{{ row.totalScore }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="等级" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="gradeTag(row.grade)">{{ row.grade }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完整性" width="80" align="center">
        <template #default="{ row }">{{ row.dimensions?.completeness }}/25</template>
      </el-table-column>
      <el-table-column label="一致性" width="80" align="center">
        <template #default="{ row }">{{ row.dimensions?.consistency }}/25</template>
      </el-table-column>
      <el-table-column label="可测试性" width="80" align="center">
        <template #default="{ row }">{{ row.dimensions?.testability }}/25</template>
      </el-table-column>
      <el-table-column label="合规性" width="80" align="center">
        <template #default="{ row }">{{ row.dimensions?.compliance }}/25</template>
      </el-table-column>
      <el-table-column label="合格" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.qualified" type="success" size="small">合格</el-tag>
          <el-tag v-else type="danger" size="small">不合格</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="detailVisible" :title="`评分详情 - ${current?.requirementNo}`" width="640px">
      <div v-if="current" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="标题">{{ current.title }}</el-descriptions-item>
          <el-descriptions-item label="总分">
            <el-tag :type="scoreTag(current.totalScore)" effect="dark">{{ current.totalScore }}</el-tag>
            <el-tag :type="gradeTag(current.grade)" style="margin-left:8px">{{ current.grade }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <div v-for="(reasons, dim) in current.issues" :key="dim" style="margin-bottom:12px">
          <div class="dim-label">{{ dimLabel(dim) }}：<el-tag :type="dimTag(current.dimensions?.[dim])" size="small">{{ current.dimensions?.[dim] }}/25</el-tag></div>
          <ul v-if="reasons && reasons.length > 0" class="issue-list">
            <li v-for="r in reasons" :key="r">⚠️ {{ r }}</li>
          </ul>
          <div v-else class="ok-text">✅ 无问题</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'

const loading = ref(false)
const detailLoading = ref(false)
const list = ref<any[]>([])
const filterProject = ref<number | null>(null)
const projectList = ref<any[]>([])
const detailVisible = ref(false)
const current = ref<any>(null)

const scoreTag = (s: number) => s >= 90 ? 'success' : s >= 60 ? 'warning' : 'danger'
const gradeTag = (g: string) => ({ A: 'success', B: '', C: 'warning', D: 'danger' } as any)[g] || 'info'
const dimTag = (s: number) => s >= 20 ? 'success' : s >= 12 ? 'warning' : 'danger'
const dimLabel = (k: string) => ({ completeness: '完整性', consistency: '一致性', testability: '可测试性', compliance: '合规性' } as any)[k] || k

const avgScore = computed(() => list.value.length > 0 ? Math.round(list.value.reduce((s, r) => s + (r.totalScore || 0), 0) / list.value.length) : 0)
const gradeACount = computed(() => list.value.filter(r => r.grade === 'A').length)
const gradeBCount = computed(() => list.value.filter(r => r.grade === 'B' || r.grade === 'C').length)
const unqualifiedCount = computed(() => list.value.filter(r => !r.qualified).length)

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
  } catch (e) {}
}

const loadAll = async () => {
  loading.value = true
  try {
    const res = await request.get('/requirements/quality', { params: { projectId: filterProject.value } })
    list.value = res.data?.data || []
  } catch (e) {
    console.error('loadAll failed', e)
  } finally {
    loading.value = false
  }
}

const showDetail = async (row: any) => {
  current.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await request.get(`/requirements/quality/${row.requirementId || row.id}`)
    current.value = res.data?.data
  } catch (e) {} finally { detailLoading.value = false }
}

onMounted(async () => {
  await fetchProjects()
  await loadAll()
})
</script>

<style scoped>
.qs-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
.stat-value { font-size: 28px; font-weight: 700; text-align: center; padding: 14px; }
.stat-label { text-align: center; color: #909399; font-size: 13px; }
.dim-label { font-weight: 600; margin-bottom: 4px; }
.issue-list { color: #F56C6C; font-size: 13px; padding-left: 20px; }
.ok-text { color: #67C23A; font-size: 13px; }
</style>
