<template>
  <div class="baseline-detail-container">
    <div class="page-header">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/baselines' }">基线管理</el-breadcrumb-item>
          <el-breadcrumb-item>详情</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">
          🔒 {{ baseline.baselineNo || `基线 #${baseline.id || ''}` }}
          <el-tag v-if="baseline.status" :type="getStatusType(baseline.status)" size="small" style="margin-left: 8px;">
            {{ getStatusLabel(baseline.status) }}
          </el-tag>
        </div>
      </div>
      <div class="header-actions">
        <el-button @click="goCompare">🔁 对比</el-button>
        <el-button v-if="baseline.status === 'DRAFT'" type="success" @click="$emit('lock', baseline)">🔒 锁定</el-button>
        <el-button v-if="baseline.status === 'LOCKED'" type="warning" @click="$emit('unlock', baseline)">🔓 解锁</el-button>
        <el-button type="primary" @click="goEdit">✏️ 编辑</el-button>
      </div>
    </div>

    <el-card v-loading="loading" class="info-card">
      <template #header><span>📋 基线信息</span></template>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="info-item">
            <div class="label">基线编号</div>
            <div class="value">{{ baseline.baselineNo || '-' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">基线名称</div>
            <div class="value">{{ baseline.baselineName || baseline.name || '-' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">关联项目</div>
            <div class="value">{{ baseline.projectName || `项目 ${baseline.projectId || '-'}` }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">状态</div>
            <div class="value">
              <el-tag :type="getStatusType(baseline.status)" size="small">{{ getStatusLabel(baseline.status) }}</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">需求数量</div>
            <div class="value">{{ requirementCount }} 条</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">创建人</div>
            <div class="value">{{ baseline.createdBy || '-' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">创建时间</div>
            <div class="value">{{ formatDate(baseline.createdAt) || '-' }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-item">
            <div class="label">锁定人/时间</div>
            <div class="value">{{ baseline.lockedBy || '-' }} / {{ formatDate(baseline.lockedAt) || '-' }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-header-row">
          <span>📜 基线包含的需求列表（快照）</span>
          <el-input v-model="searchKw" placeholder="搜索需求编号/标题" size="small" style="width: 220px;" clearable />
        </div>
      </template>
      <el-table :data="filteredReqs" border stripe style="width: 100%;" empty-text="无需求快照数据">
        <el-table-column prop="requirementNo" label="需求编号" width="160">
          <template #default="{ row }">
            <span style="font-weight: 600; color: #409eff;">{{ row.requirementNo || row.code || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requirementType" label="层级" width="100">
          <template #default="{ row }">
            <span v-if="row.requirementType" class="level-tag" :class="(row.requirementType || '').toUpperCase()">
              {{ row.requirementType }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="需求标题" min-width="280" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column prop="createdBy" label="创建人" width="100" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </el-card>

    <div class="footer-actions">
      <el-button @click="$router.back()">← 返回基线列表</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 基线详情页 (合规域独立页面 P0 修复)
 * 对应原型：baselines-detail-原型.html
 * 对应路由：/compliance/baselines/:id
 * 对应后端：BaselineController.getByProject + 基线快照 JSON 解析
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const searchKw = ref('')
const baseline = ref<any>({})
const snapshotReqs = ref<any[]>([])

const formatDate = (v?: string | null) => {
  if (!v) return ''
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return ''
}

const getStatusType = (s: string) => s === 'LOCKED' ? 'success' : (s === 'ARCHIVED' ? 'info' : 'warning')
const getStatusLabel = (s: string) => ({ LOCKED: '已锁定', DRAFT: '草稿', ARCHIVED: '已归档' }[s] || s || '-')

const requirementCount = computed(() => snapshotReqs.value.length)

const filteredReqs = computed(() => {
  const kw = searchKw.value.toLowerCase()
  if (!kw) return snapshotReqs.value
  return snapshotReqs.value.filter(r =>
    (r.requirementNo || r.code || '').toLowerCase().includes(kw) ||
    (r.title || '').toLowerCase().includes(kw)
  )
})

const fetchBaseline = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    // 由于无单条 get 接口，先获取所有基线查找
    const projectsRes = await request.get('/projects', { params: { page: 0, size: 200 } })
    const pd = projectsRes.data?.data
    const projects: any[] = Array.isArray(pd) ? pd : (pd?.records || [])
    for (const p of projects) {
      try {
        const res = await request.get(`/baselines/project/${p.id}`)
        const list = (res.data?.data || []) as any[]
        const hit = list.find(b => String(b.id) === String(id))
        if (hit) {
          baseline.value = { ...hit, projectName: p.projectName }
          // 解析 snapshotData
          try {
            const parsed = JSON.parse(hit.snapshotData || '[]')
            snapshotReqs.value = Array.isArray(parsed) ? parsed : []
          } catch {
            snapshotReqs.value = []
          }
          break
        }
      } catch {}
    }
  } catch (e: any) {
    ElMessage.error('加载基线失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const goEdit = () => {
  router.push(`/compliance/baselines/${route.params.id}/edit`)
}

const goCompare = () => {
  router.push('/compliance/baselines/compare')
}

onMounted(fetchBaseline)
</script>

<style scoped>
.baseline-detail-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; }
.info-card { margin-bottom: 16px; }
.info-item { background: #f5f7fa; padding: 12px; border-radius: 6px; margin-bottom: 8px; }
.info-item .label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.info-item .value { font-size: 14px; font-weight: 600; color: #303133; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.level-tag { padding: 2px 8px; border-radius: 3px; font-size: 11px; font-weight: 600; }
.level-tag.URS { background: #ecf5ff; color: #409eff; }
.level-tag.PRS { background: #f0f9eb; color: #67c23a; }
.level-tag.SRS { background: #fdf6ec; color: #e6a23c; }
.level-tag.DRS { background: #fef0f0; color: #f56c6c; }
.footer-actions { display: flex; justify-content: flex-end; gap: 12px; padding-top: 8px; }
</style>
