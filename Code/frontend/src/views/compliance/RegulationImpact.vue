<template>
  <div class="reg-container">
    <div class="page-title">
      <h2>📜 法规更新影响分析（FR-2.2）</h2>
      <div class="header-actions">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 280px;" @change="runAnalysis">
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button @click="runAnalysis" :loading="loading" type="primary" :disabled="!projectId">运行分析</el-button>
        <el-button @click="exportReport" :disabled="!hasResult">导出报告</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      扫描项目所有来源=法规(REGULATION) 的需求，对比内置法规版本库（IEC 62304 / ISO 14971 / NMPA / EU MDR），
      标识出可能受新版法规影响的需求及影响范围。
    </el-alert>

    <div v-if="!hasResult && !loading" class="empty-tip">
      <el-empty description="请选择项目后运行分析" />
    </div>

    <div v-if="loading" class="loading-tip">
      <el-icon class="rotating"><Loading /></el-icon>
      <span>正在扫描项目法规需求并对比法规版本库...</span>
    </div>

    <template v-if="hasResult && !loading">
      <!-- 概览 -->
      <el-row :gutter="16">
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#409EFF">{{ result.totalRegReqs }}</div><div class="stat-label">法规相关需求</div></div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#F56C6C">{{ result.affectedReqs.length }}</div><div class="stat-label">受新法规影响</div></div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#E6A23C">{{ result.regulations.length }}</div><div class="stat-label">涉及法规</div></div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#67C23A">{{ result.compliantRate }}%</div><div class="stat-label">现行合规率</div></div></el-card>
        </el-col>
      </el-row>

      <!-- 法规版本对比表 -->
      <el-card style="margin-top: 16px">
        <template #header><div class="card-title">📚 内置法规版本库（{{ result.regulations.length }} 项）</div></template>
        <el-table :data="result.regulations" border>
          <el-table-column prop="code" label="法规编号" width="160" />
          <el-table-column prop="name" label="法规名称" min-width="200" />
          <el-table-column prop="currentVer" label="现行版本" width="120" />
          <el-table-column prop="newVer" label="新版" width="120" />
          <el-table-column prop="effectiveDate" label="新版生效日期" width="140" />
          <el-table-column label="影响等级" width="100">
            <template #default="{ row }">
              <el-tag :type="row.impactLevel === 'HIGH' ? 'danger' : (row.impactLevel === 'MEDIUM' ? 'warning' : 'info')" size="small">
                {{ { HIGH: '高', MEDIUM: '中', LOW: '低' }[row.impactLevel] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="showRegDetail(row)">查看差异</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 受影响需求 -->
      <el-card v-if="result.affectedReqs.length > 0" style="margin-top: 16px">
        <template #header>
          <div class="card-title">⚠️ 受新法规版本影响的需求（{{ result.affectedReqs.length }}）</div>
        </template>
        <el-table :data="result.affectedReqs" border stripe>
          <el-table-column prop="requirementNo" label="需求编号" width="140" />
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="sourceNo" label="法规条款" width="120" />
          <el-table-column label="影响等级" width="100">
            <template #default="{ row }">
              <el-tag :type="row.impactLevel === 'HIGH' ? 'danger' : 'warning'" size="small">
                {{ { HIGH: '高', MEDIUM: '中' }[row.impactLevel] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="changeSummary" label="需要变更的内容" min-width="200" show-overflow-tooltip />
          <el-table-column prop="action" label="建议动作" width="140">
            <template #default="{ row }">
              <el-tag effect="plain">{{ row.action }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 影响分析说明 -->
      <el-card style="margin-top: 16px">
        <template #header><div class="card-title">💡 影响分析摘要</div></template>
        <el-alert v-for="(item, i) in result.summary" :key="i" :type="item.type" :title="item.title" :description="item.desc" :closable="false" show-icon style="margin-bottom: 8px;" />
      </el-card>
    </template>

    <!-- 法规差异详情对话框 -->
    <el-dialog v-model="regDetailVisible" :title="`${currentReg?.code} 差异详情`" width="700px">
      <div v-if="currentReg">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="法规编号">{{ currentReg.code }}</el-descriptions-item>
          <el-descriptions-item label="法规名称">{{ currentReg.name }}</el-descriptions-item>
          <el-descriptions-item label="现行版本">{{ currentReg.currentVer }}</el-descriptions-item>
          <el-descriptions-item label="新版">{{ currentReg.newVer }}</el-descriptions-item>
          <el-descriptions-item label="新版生效日期">{{ currentReg.effectiveDate }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top:16px">主要差异点：</h4>
        <el-table :data="currentReg.changes" border size="small">
          <el-table-column label="章节" prop="section" width="100" />
          <el-table-column label="变更类型" prop="changeType" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.changeType === '新增' ? 'success' : (row.changeType === '删除' ? 'danger' : 'warning')">{{ row.changeType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="变更内容" prop="content" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

const projectList = ref<any[]>([])
const projectId = ref<number | null>(null)
const loading = ref(false)
const regDetailVisible = ref(false)
const currentReg = ref<any>(null)
const result = ref<any>({ regulations: [], affectedReqs: [], summary: [], totalRegReqs: 0, compliantRate: 0 })

// 内置法规版本库
const REGULATION_LIBRARY = [
  {
    code: 'IEC 62304',
    name: '医疗器械软件 - 软件生命周期过程',
    currentVer: '2006/Amd1:2015',
    newVer: '2024 (即将发布)',
    effectiveDate: '2026-12-31',
    impactLevel: 'HIGH',
    keywords: ['IEC 62304', '62304', '软件生命周期', 'SOUP', '风险控制', '软件安全'],
    changes: [
      { section: '5.1', changeType: '新增', content: '网络安全要求（与 IEC 81001-5-1 协调）' },
      { section: '5.7', changeType: '修改', content: 'SOUP 评估要求强化，需提供 SBOM' },
      { section: '5.8', changeType: '新增', content: '人工智能/机器学习组件的特殊要求' }
    ]
  },
  {
    code: 'ISO 14971',
    name: '医疗器械 - 风险管理对医疗器械的应用',
    currentVer: '2019',
    newVer: '2024',
    effectiveDate: '2026-06-01',
    impactLevel: 'MEDIUM',
    keywords: ['ISO 14971', '14971', '风险', '危害', '风险控制', '风险可接受性'],
    changes: [
      { section: '5.4', changeType: '修改', content: '明确"合理可预见误用"的范围' },
      { section: '7', changeType: '修改', content: '风险控制措施选择顺序调整' },
      { section: '10', changeType: '新增', content: '综合剩余风险可接受性评审要求' }
    ]
  },
  {
    code: 'NMPA eRPS',
    name: '医疗器械注册电子申报',
    currentVer: '2019',
    newVer: '2025 v2.0',
    effectiveDate: '2025-09-01',
    impactLevel: 'MEDIUM',
    keywords: ['NMPA', 'eRPS', '注册', '申报', '药监局'],
    changes: [
      { section: 'CH3.2.A', changeType: '新增', content: '网络安全文档要求（医疗器械唯一标识 UDI）' },
      { section: 'CH3.2.B', changeType: '修改', content: '软件描述资料结构变更' },
      { section: 'CH3.2.R', changeType: '新增', content: '可用性工程文档要求' }
    ]
  },
  {
    code: 'EU MDR',
    name: '欧盟医疗器械法规',
    currentVer: '2017/745',
    newVer: '2024 修订',
    effectiveDate: '2027-05-26',
    impactLevel: 'HIGH',
    keywords: ['MDR', 'EU 2017/745', '欧盟', 'CE', '公告机构'],
    changes: [
      { section: 'Annex I', changeType: '新增', content: '17.2 网络安全要求强化' },
      { section: 'Annex VIII', changeType: '修改', content: '分类规则 Rule 11（软件）细化' },
      { section: 'Annex IX', changeType: '新增', content: 'AI/ML 医疗器械评估要求' }
    ]
  }
]

const hasResult = computed(() => result.value.regulations.length > 0)

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
    if (projectList.value.length > 0 && !projectId.value) {
      projectId.value = projectList.value[0].id
    }
  } catch {}
}

const runAnalysis = async () => {
  if (!projectId.value) return
  loading.value = true
  try {
    // 获取项目的所有法规相关需求
    const res = await request.get('/requirements', { params: { projectId: projectId.value, page: 0, size: 500 } })
    const reqs = res.data?.data?.records || res.data?.data || []
    const regReqs = reqs.filter((r: any) => r.source === 'REGULATION' || /IEC|ISO|NMPA|MDR|GB|YY|法规|标准/i.test(r.sourceNo || ''))

    // 与法规库匹配
    const affectedReqs: any[] = []
    const matchedRegs: any[] = []

    for (const reg of REGULATION_LIBRARY) {
      const matched = regReqs.filter((r: any) => reg.keywords.some(kw => (r.sourceNo || '').includes(kw) || (r.title || '').includes(kw)))
      if (matched.length > 0 || reg.impactLevel === 'HIGH') {
        matchedRegs.push(reg)
        for (const m of matched) {
          // 决定影响等级
          const hasNewReq = reg.changes.some((c: any) => c.changeType === '新增')
          const impactLevel = hasNewReq ? 'HIGH' : 'MEDIUM'
          const newChanges = reg.changes.filter((c: any) => c.changeType === '新增' || c.changeType === '修改')
          affectedReqs.push({
            requirementNo: m.requirementNo,
            title: m.title,
            sourceNo: m.sourceNo,
            impactLevel,
            changeSummary: newChanges.slice(0, 2).map((c: any) => `${c.section}: ${c.content}`).join('；'),
            action: impactLevel === 'HIGH' ? '发起变更评审' : '更新需求描述'
          })
        }
      }
    }

    const compliantRate = regReqs.length > 0 ? Math.round((1 - affectedReqs.length / regReqs.length) * 100) : 100

    // 摘要
    const summary: any[] = []
    if (affectedReqs.length === 0) {
      summary.push({ type: 'success', title: '项目法规状态良好', desc: '当前所有法规相关需求与最新版本兼容，无需调整。' })
    } else {
      const highCount = affectedReqs.filter(r => r.impactLevel === 'HIGH').length
      if (highCount > 0) {
        summary.push({
          type: 'error',
          title: `存在 ${highCount} 项高影响需求`,
          desc: '这些需求需要发起变更评审流程（CR），评估新法规条款对设计/测试的影响。'
        })
      }
      summary.push({
        type: 'warning',
        title: `共 ${affectedReqs.length} 项需求受新法规版本影响`,
        desc: '建议在下一基线前完成差异评估和文档更新，确保注册申报时符合新版要求。'
      })
      summary.push({
        type: 'info',
        title: '建议动作',
        desc: '1) 召开跨部门法规评审会议；2) 评估是否需要补充设计文档；3) 必要时更新风险管理和可追溯性记录。'
      })
    }

    result.value = {
      regulations: matchedRegs.length > 0 ? matchedRegs : REGULATION_LIBRARY.slice(0, 3),
      affectedReqs,
      summary,
      totalRegReqs: regReqs.length,
      compliantRate
    }
  } catch (e: any) {
    ElMessage.error('分析失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const showRegDetail = (reg: any) => {
  currentReg.value = reg
  regDetailVisible.value = true
}

const exportReport = () => {
  const lines: string[] = []
  lines.push(`# 法规更新影响分析报告`)
  lines.push(`项目ID: ${projectId.value}`)
  lines.push(`生成时间: ${new Date().toISOString()}`)
  lines.push('')
  lines.push(`## 概览`)
  lines.push(`法规相关需求: ${result.value.totalRegReqs}`)
  lines.push(`受影响需求: ${result.value.affectedReqs.length}`)
  lines.push(`合规率: ${result.value.compliantRate}%`)
  lines.push('')
  lines.push(`## 受影响需求清单`)
  for (const r of result.value.affectedReqs) {
    lines.push(`- [${r.impactLevel}] ${r.requirementNo} ${r.title}（${r.sourceNo}）- ${r.action}`)
    lines.push(`  ${r.changeSummary}`)
  }
  const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `regulation-impact-${projectId.value}-${Date.now()}.md`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('报告已导出')
}

onMounted(async () => {
  await fetchProjects()
  if (projectId.value) await runAnalysis()
})
</script>

<style scoped>
.reg-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
.card-title { font-size: 15px; font-weight: 600; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-num { font-size: 32px; font-weight: 700; }
.stat-label { font-size: 13px; color: #606266; margin-top: 4px; }
.empty-tip, .loading-tip { padding: 60px 0; text-align: center; color: #606266; }
.loading-tip { display: flex; gap: 12px; justify-content: center; align-items: center; }
.rotating { animation: rotate 1s linear infinite; font-size: 24px; }
@keyframes rotate { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>
