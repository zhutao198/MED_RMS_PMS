<!--
  RisksMatrix.vue - 风险矩阵热力图
  v1.53 P1-17 修复：severity × probability 热力图
-->
<template>
  <div class="risks-matrix-container">
    <div class="page-header">
      <div class="page-title">风险矩阵热力图</div>
      <div class="header-actions">
        <el-select v-model="selectedProject" style="width: 220px; margin-right: 10px;" @change="loadData">
          <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-button @click="loadData" :loading="loading">刷新</el-button>
      </div>
    </div>

    <el-card v-loading="loading">
      <template #header>
        <span>严重度 × 可能性 热力图</span>
      </template>
      <table class="heatmap-table">
        <thead>
          <tr>
            <th class="corner-cell">严重度 \ 概率</th>
            <th v-for="prob in PROBABILITIES" :key="prob" class="prob-header">{{ prob }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="sev in SEVERITIES" :key="sev">
            <th class="sev-header">{{ sev }}</th>
            <td
              v-for="prob in PROBABILITIES"
              :key="prob"
              :class="['cell', getCellClass(sev, prob)]"
              @click="onCellClick(sev, prob)"
            >
              <div class="cell-content">
                <div class="cell-count">{{ getCellCount(sev, prob) }}</div>
                <div class="cell-level">{{ getLevelLabel(sev, prob) }}</div>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="legend">
        <span class="legend-item"><span class="dot low"></span>低风险</span>
        <span class="legend-item"><span class="dot medium"></span>中风险</span>
        <span class="legend-item"><span class="dot high"></span>高风险</span>
        <span class="legend-item"><span class="dot critical"></span>严重</span>
      </div>
    </el-card>

    <el-card style="margin-top: 16px" v-loading="loading">
      <template #header>
        <span>单元格明细</span>
      </template>
      <el-table :data="cellDetails" border size="small" empty-text="点击上方热力图单元格查看详情">
        <el-table-column prop="riskNo" label="风险编号" width="140" />
        <el-table-column prop="riskTitle" label="风险标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="severity" label="严重度" width="100" />
        <el-table-column prop="probability" label="概率" width="100" />
        <el-table-column prop="riskLevel" label="等级" width="100">
          <template #default="{ row }">
            <el-tag :type="getRiskLevelType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="ownerName" label="责任人" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

interface Project { id: number; projectName: string }
interface RiskItem { id: number; riskNo: string; riskTitle: string; severity: string; probability: string; riskLevel: string; status: string; ownerName?: string }

const SEVERITIES = ['NEGLIGIBLE', 'MINOR', 'MAJOR', 'CRITICAL']
const PROBABILITIES = ['LOW', 'MEDIUM', 'HIGH']

// 概率 × 严重度 → 等级矩阵（NEGLIGIBLE=1, MINOR=2, MAJOR=3, CRITICAL=4；LOW=1, MEDIUM=2, HIGH=3）
const sevNum = (s: string) => ({ NEGLIGIBLE: 1, MINOR: 2, MAJOR: 3, CRITICAL: 4 }[s] || 1)
const probNum = (p: string) => ({ LOW: 1, MEDIUM: 2, HIGH: 3 }[p] || 1)
const product = (s: string, p: string) => sevNum(s) * probNum(p)
const getLevelLabel = (s: string, p: string) => {
  const v = product(s, p)
  if (v >= 9) return '严重'
  if (v >= 6) return '高'
  if (v >= 3) return '中'
  return '低'
}
const getCellClass = (s: string, p: string) => {
  const v = product(s, p)
  if (v >= 9) return 'cell-critical'
  if (v >= 6) return 'cell-high'
  if (v >= 3) return 'cell-medium'
  return 'cell-low'
}
const getRiskLevelType = (lvl: string) => ({ HIGH: 'danger', MEDIUM: 'warning', LOW: 'success' }[lvl] || 'info')

const router = useRouter()
const loading = ref(false)
const projectList = ref<Project[]>([])
const selectedProject = ref<number | ''>(1)
const risks = ref<RiskItem[]>([])
const cellDetails = ref<RiskItem[]>([])

const fetchProjects = async () => {
  try { projectList.value = (await request.get('/projects')).data?.data || [] } catch { /* ignore */ }
}

const getCellCount = (s: string, p: string) => risks.value.filter(r => r.severity === s && r.probability === p).length

const onCellClick = (s: string, p: string) => {
  cellDetails.value = risks.value.filter(r => r.severity === s && r.probability === p)
}

const loadData = async () => {
  if (!selectedProject.value) return
  loading.value = true
  try {
    // R109 G3 修复：按 selectedProject 过滤风险数据
    const res = await request.get('/risk/register/list', {
      params: { projectId: selectedProject.value }
    })
    risks.value = (res.data?.data || []) as RiskItem[]
  } catch (e: any) {
    ElMessage.error('加载风险数据失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

onMounted(async () => { await fetchProjects(); loadData() })
</script>

<style scoped>
.risks-matrix-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; }
.header-actions { display: flex; align-items: center; }
.heatmap-table { width: 100%; border-collapse: collapse; }
.heatmap-table th, .heatmap-table td { border: 1px solid #e4e7ed; padding: 12px; text-align: center; }
.corner-cell { background: #f5f7fa; font-size: 12px; color: #909399; }
.prob-header, .sev-header { background: #f5f7fa; font-size: 13px; color: #303133; font-weight: 600; }
.cell { cursor: pointer; transition: opacity 0.2s; }
.cell:hover { opacity: 0.8; }
.cell-content { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.cell-count { font-size: 20px; font-weight: 700; }
.cell-level { font-size: 11px; }
.cell-low { background: #f0f9eb; color: #67c23a; }
.cell-medium { background: #fdf6ec; color: #e6a23c; }
.cell-high { background: #fef0f0; color: #f56c6c; }
.cell-critical { background: #f56c6c; color: #fff; }
.legend { display: flex; justify-content: center; gap: 24px; margin-top: 16px; }
.legend-item { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.dot { width: 14px; height: 14px; border-radius: 3px; }
.dot.low { background: #f0f9eb; border: 1px solid #67c23a; }
.dot.medium { background: #fdf6ec; border: 1px solid #e6a23c; }
.dot.high { background: #fef0f0; border: 1px solid #f56c6c; }
.dot.critical { background: #f56c6c; }
</style>
