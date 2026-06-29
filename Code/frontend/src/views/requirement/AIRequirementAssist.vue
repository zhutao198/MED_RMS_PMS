<template>
  <div class="ai-container">
    <div class="page-title">
      <h2>🤖 AI 辅助需求分析（FR-2.1）</h2>
      <div class="header-actions">
        <el-select v-model="projectId" placeholder="选择项目" filterable style="width: 280px;" @change="fetchData">
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button @click="analyzeAll" :loading="analyzing" type="primary" :disabled="!requirements.length">
          🚀 一键分析所有需求（{{ requirements.length }}）
        </el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      基于规则引擎的智能分析：检查标题质量、Shall/Must 关键词、描述完整性、安全分类、优先级建议、相似需求检测。
      <br />分析结果仅供参考，正式评审前需工程师确认。
    </el-alert>

    <!-- 统计概览 -->
    <el-row :gutter="16" v-if="analyzedResults.length > 0">
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#67C23A">{{ stats.good }}</div><div class="stat-label">优秀（≥90 分）</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#E6A23C">{{ stats.normal }}</div><div class="stat-label">合格（60-89）</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#F56C6C">{{ stats.poor }}</div><div class="stat-label">需改进（<60）</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><div class="stat-card"><div class="stat-num" style="color:#909399">{{ stats.unscored }}</div><div class="stat-label">未分析</div></div></el-card>
      </el-col>
    </el-row>

    <!-- 单需求分析器 -->
    <el-card style="margin-top: 16px">
      <template #header><div class="card-title">📝 单需求实时分析</div></template>
      <el-form :model="draft" label-width="100px">
        <el-form-item label="标题">
          <el-input v-model="draft.title" placeholder="输入需求标题..." maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="draft.description" type="textarea" :rows="4" placeholder="详细描述需求..." />
        </el-form-item>
        <el-form-item label="分类">
          <el-radio-group v-model="draft.requirementCategory">
            <el-radio label="SOFTWARE">纯软件</el-radio>
            <el-radio label="HARDWARE">纯硬件</el-radio>
            <el-radio label="BOTH">软硬结合</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="安全分类">
          <el-radio-group v-model="draft.safetyClass">
            <el-radio label="A">A 级（无伤害）</el-radio>
            <el-radio label="B">B 级（非严重伤害）</el-radio>
            <el-radio label="C">C 级（可能严重伤害/死亡）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-divider />
      <div v-if="draftAnalysis" class="draft-result">
        <div class="score-display">
          <el-progress type="circle" :percentage="draftAnalysis.score" :color="getScoreColor(draftAnalysis.score)" :width="100" />
          <div style="margin-left:24px">
            <h3>评分：{{ draftAnalysis.score }} / 100 - <el-tag :type="getScoreType(draftAnalysis.score)">{{ getScoreLabel(draftAnalysis.score) }}</el-tag></h3>
            <div>建议优先级：<el-tag :type="getPriorityType(draftAnalysis.suggestedPriority)">{{ draftAnalysis.suggestedPriority }}</el-tag></div>
            <div>建议安全分类：<el-tag :type="draftAnalysis.suggestedSafetyClass === 'C' ? 'danger' : (draftAnalysis.suggestedSafetyClass === 'B' ? 'warning' : '')">{{ draftAnalysis.suggestedSafetyClass }} 级</el-tag></div>
          </div>
        </div>
        <el-divider />
        <h4>📋 检查项：</h4>
        <el-table :data="draftAnalysis.items" border size="small">
          <el-table-column label="检查项" prop="name" width="160" />
          <el-table-column label="结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.pass" type="success" size="small">✅ 通过</el-tag>
              <el-tag v-else type="danger" size="small">❌ 未通过</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" prop="message" />
        </el-table>
        <div v-if="draftAnalysis.warnings.length > 0" class="warning-box">
          <h4>⚠️ 改进建议：</h4>
          <ul>
            <li v-for="(w, i) in draftAnalysis.warnings" :key="i">{{ w }}</li>
          </ul>
        </div>
      </div>
      <el-empty v-else description="填写标题和描述后实时分析" />
    </el-card>

    <!-- 批量分析结果 -->
    <el-card v-if="analyzedResults.length > 0" style="margin-top: 16px">
      <template #header>
        <div class="card-header-inline">
          <span>📊 批量分析结果</span>
          <div>
            <el-button size="small" @click="filterMode = 'all'" :type="filterMode === 'all' ? 'primary' : ''">全部 ({{ analyzedResults.length }})</el-button>
            <el-button size="small" @click="filterMode = 'poor'" :type="filterMode === 'poor' ? 'primary' : ''">需改进 ({{ stats.poor }})</el-button>
            <el-button size="small" @click="filterMode = 'duplicate'" :type="filterMode === 'duplicate' ? 'primary' : ''">疑似重复 ({{ duplicateResults.length }})</el-button>
          </div>
        </div>
      </template>
      <el-table :data="filteredResults" border stripe>
        <el-table-column prop="requirementNo" label="需求编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="评分" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getScoreType(row.score)" effect="dark">{{ row.score }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="建议优先级" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="getPriorityType(row.suggestedPriority)">{{ row.suggestedPriority }}</el-tag>
            <span v-if="row.suggestedPriority !== row.originalPriority" class="diff-mark">*</span>
          </template>
        </el-table-column>
        <el-table-column label="建议安全分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="row.suggestedSafetyClass === 'C' ? 'danger' : ''">{{ row.suggestedSafetyClass }}</el-tag>
            <span v-if="row.suggestedSafetyClass !== row.safetyClass" class="diff-mark">*</span>
          </template>
        </el-table-column>
        <el-table-column label="问题" width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-for="(w, i) in row.warnings.slice(0, 2)" :key="i" class="warning-pill">{{ w }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" :title="`AI 分析详情 - ${currentResult?.requirementNo || ''}`" width="700px">
      <div v-if="currentResult">
        <h3>{{ currentResult.title }}</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="当前评分">{{ currentResult.score }} / 100</el-descriptions-item>
          <el-descriptions-item label="当前优先级">{{ currentResult.originalPriority || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="建议优先级">
            <el-tag :type="getPriorityType(currentResult.suggestedPriority)">{{ currentResult.suggestedPriority }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前安全分类">{{ currentResult.safetyClass || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="建议安全分类">{{ currentResult.suggestedSafetyClass }}</el-descriptions-item>
          <el-descriptions-item label="重复检测" v-if="currentResult.duplicates?.length > 0">
            疑似重复 {{ currentResult.duplicates.length }} 条
          </el-descriptions-item>
        </el-descriptions>
        <h4>检查项</h4>
        <el-table :data="currentResult.items" border size="small">
          <el-table-column label="检查项" prop="name" width="160" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.pass" type="success" size="small">✅</el-tag>
              <el-tag v-else type="danger" size="small">❌</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" prop="message" />
        </el-table>
        <h4>改进建议</h4>
        <ul>
          <li v-for="(w, i) in currentResult.warnings" :key="i">{{ w }}</li>
        </ul>
        <div v-if="currentResult.duplicates?.length > 0">
          <h4>疑似相似需求</h4>
          <el-tag v-for="d in currentResult.duplicates" :key="d.requirementNo" type="warning" effect="plain" style="margin:2px">
            {{ d.requirementNo }} - {{ d.title }} ({{ Math.round(d.similarity * 100) }}% 相似)
          </el-tag>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

const projectList = ref<any[]>([])
const projectId = ref<number | null>(null)
const requirements = ref<any[]>([])
const analyzing = ref(false)
const analyzedResults = ref<any[]>([])
const filterMode = ref<'all' | 'poor' | 'duplicate'>('all')
const detailVisible = ref(false)
const currentResult = ref<any>(null)

const draft = ref({ title: '', description: '', requirementCategory: 'SOFTWARE', safetyClass: 'A' })
const draftAnalysis = ref<any>(null)

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

const fetchData = async () => {
  if (!projectId.value) return
  analyzedResults.value = []
  try {
    const res = await request.get('/requirements', { params: { projectId: projectId.value, page: 0, size: 500 } })
    const d = res.data?.data
    requirements.value = Array.isArray(d) ? d : (d?.records || [])
  } catch {}
}

// 核心规则引擎
const analyzeRequirement = (req: any) => {
  const items: any[] = []
  const warnings: string[] = []
  let score = 100
  const title = (req.title || '').trim()
  const desc = (req.description || '').trim()

  // 1. 标题质量
  if (title.length === 0) {
    items.push({ name: '标题非空', pass: false, message: '标题不能为空' })
    score -= 30
    warnings.push('标题不能为空')
  } else if (title.length < 8) {
    items.push({ name: '标题长度', pass: false, message: `标题过短（${title.length} 字符），建议至少 8 字符` })
    score -= 15
    warnings.push('标题过短，建议扩展为完整需求陈述')
  } else if (title.length > 150) {
    items.push({ name: '标题长度', pass: false, message: '标题过长，建议拆分子需求' })
    score -= 5
  } else {
    items.push({ name: '标题长度', pass: true, message: `长度 ${title.length} 字符` })
  }

  // 2. Shall/Must 关键词
  const hasKeyword = /\b(shall|must|应该|必须|应当)\b/i.test(title + ' ' + desc)
  if (hasKeyword) {
    items.push({ name: 'Shall/Must 关键词', pass: true, message: '包含强约束关键词' })
  } else if (title.length > 0) {
    items.push({ name: 'Shall/Must 关键词', pass: false, message: '建议使用 shall/must 表达强约束' })
    score -= 10
    warnings.push('使用 shall/must 表达强约束')
  }

  // 3. 描述完整性
  if (desc.length === 0) {
    items.push({ name: '描述完整性', pass: false, message: '描述为空' })
    score -= 25
    warnings.push('补充详细描述')
  } else if (desc.length < 30) {
    items.push({ name: '描述完整性', pass: false, message: `描述过短（${desc.length} 字符）` })
    score -= 10
    warnings.push('扩展描述以说明背景、输入、输出、约束')
  } else {
    items.push({ name: '描述完整性', pass: true, message: `描述 ${desc.length} 字符` })
  }

  // 4. 验收标准（描述中应包含"验证"、"确认"、"测试"等）
  const hasVerify = /验证|确认|测试|verify|validate|test|通过|接受/i.test(desc)
  if (desc.length > 0) {
    if (hasVerify) {
      items.push({ name: '验收标准', pass: true, message: '包含验收相关描述' })
    } else {
      items.push({ name: '验收标准', pass: false, message: '建议补充验收标准' })
      score -= 8
      warnings.push('补充验收标准（验证方法、可接受准则）')
    }
  }

  // 5. 安全分类
  if (req.safetyClass) {
    items.push({ name: '安全分类', pass: true, message: `已设为 ${req.safetyClass} 级` })
  } else {
    items.push({ name: '安全分类', pass: false, message: '未设置安全分类' })
    score -= 10
    warnings.push('设置 IEC 62304 软件安全分类（A/B/C）')
  }

  // 6. 优先级
  if (req.priority) {
    items.push({ name: '优先级', pass: true, message: `已设为 ${req.priority}` })
  } else {
    items.push({ name: '优先级', pass: false, message: '未设置优先级' })
    score -= 5
  }

  // 7. 风险关键词（关键词触发）
  const highRiskWords = /死亡|致命|危及生命|误诊|过量|生命支持|生命体征|fatal|life.support|life.threatening|death/i
  const medRiskWords = /错误|偏差|超时|精度|报警|告警|alarm|error|warning/i
  let suggestedSafetyClass = req.safetyClass || 'A'
  if (highRiskWords.test(title + ' ' + desc)) {
    suggestedSafetyClass = 'C'
    if (req.safetyClass !== 'C') {
      items.push({ name: '安全分类建议', pass: false, message: '检测到高风险关键词，建议设为 C 级' })
      warnings.push('关键词提示"致命/生命支持"，建议安全分类 C')
      score -= 5
    }
  } else if (medRiskWords.test(title + ' ' + desc) && req.safetyClass !== 'B' && req.safetyClass !== 'C') {
    suggestedSafetyClass = 'B'
    if (req.safetyClass !== 'B') {
      items.push({ name: '安全分类建议', pass: false, message: '检测到中风险关键词，建议设为 B 级' })
      warnings.push('关键词提示"报警/错误"，建议安全分类 B')
    }
  }

  // 8. 优先级建议
  let suggestedPriority = req.priority || 'SHOULD'
  if (suggestedSafetyClass === 'C' && suggestedPriority !== 'MUST') {
    suggestedPriority = 'MUST'
    if (req.priority !== 'MUST') warnings.push('安全分类 C 级建议优先级 MUST')
  } else if (suggestedSafetyClass === 'B' && !['MUST', 'SHOULD'].includes(suggestedPriority)) {
    suggestedPriority = 'SHOULD'
  }

  // 9. 数值/单位（医疗设备常涉及）
  const hasMetric = /\d+\s*(%|ms|s|min|mm|cm|kg|g|mg|°C|mmHg|kPa)/i.test(title + ' ' + desc)
  if (req.requirementCategory !== 'HARDWARE' && !hasMetric && desc.length > 50) {
    items.push({ name: '量化指标', pass: false, message: '建议补充量化指标（精度、阈值、响应时间）' })
    score -= 5
    warnings.push('补充量化指标')
  } else {
    items.push({ name: '量化指标', pass: true, message: hasMetric ? '包含量化指标' : '可不强制' })
  }

  score = Math.max(0, Math.min(100, score))
  return { ...req, items, warnings, score, suggestedPriority, suggestedSafetyClass }
}

// 相似度：基于标题的 Jaccard 词集合
const jaccardSimilarity = (a: string, b: string) => {
  if (!a || !b) return 0
  const setA = new Set(a.toLowerCase().split(/\W+/).filter(w => w.length > 1))
  const setB = new Set(b.toLowerCase().split(/\W+/).filter(w => w.length > 1))
  if (setA.size === 0 || setB.size === 0) return 0
  let inter = 0
  for (const w of setA) if (setB.has(w)) inter++
  return inter / (setA.size + setB.size - inter)
}

const detectDuplicates = (reqs: any[]) => {
  const threshold = 0.6
  return reqs.map(r => {
    const dups = reqs.filter(other => other.id !== r.id && jaccardSimilarity(r.title, other.title) >= threshold)
      .map(d => ({ ...d, similarity: jaccardSimilarity(r.title, d.title) }))
      .sort((a, b) => b.similarity - a.similarity)
      .slice(0, 3)
    return { ...r, duplicates: dups }
  })
}

const analyzeAll = async () => {
  analyzing.value = true
  try {
    await new Promise(r => setTimeout(r, 300)) // 模拟处理延迟
    const analyzed = requirements.value.map(r => analyzeRequirement(r))
    const withDups = detectDuplicates(analyzed)
    analyzedResults.value = withDups
    ElMessage.success(`分析完成，共 ${analyzedResults.value.length} 条`)
  } finally {
    analyzing.value = false
  }
}

const stats = computed(() => {
  const result = { good: 0, normal: 0, poor: 0, unscored: requirements.value.length - analyzedResults.value.length }
  for (const r of analyzedResults.value) {
    if (r.score >= 90) result.good++
    else if (r.score >= 60) result.normal++
    else result.poor++
  }
  return result
})

const duplicateResults = computed(() => analyzedResults.value.filter(r => r.duplicates && r.duplicates.length > 0))

const filteredResults = computed(() => {
  if (filterMode.value === 'all') return analyzedResults.value
  if (filterMode.value === 'poor') return analyzedResults.value.filter(r => r.score < 60)
  return duplicateResults.value
})

const viewDetail = (row: any) => {
  currentResult.value = row
  detailVisible.value = true
}

const getScoreColor = (s: number) => s >= 90 ? '#67C23A' : s >= 60 ? '#E6A23C' : '#F56C6C'
const getScoreType = (s: number) => s >= 90 ? 'success' : s >= 60 ? 'warning' : 'danger'
const getScoreLabel = (s: number) => s >= 90 ? '优秀' : s >= 60 ? '合格' : '需改进'
const getPriorityType = (p: string) => ({ MUST: 'danger', SHOULD: 'warning', COULD: '', WONT: 'info' } as any)[p] || ''

// 实时分析草稿
watch([() => draft.value.title, () => draft.value.description, () => draft.value.requirementCategory, () => draft.value.safetyClass], () => {
  if (draft.value.title || draft.value.description) {
    draftAnalysis.value = analyzeRequirement(draft.value)
  } else {
    draftAnalysis.value = null
  }
}, { deep: true })

onMounted(async () => {
  await fetchProjects()
  if (projectId.value) await fetchData()
})
</script>

<style scoped>
.ai-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
.card-title { font-size: 15px; font-weight: 600; }
.card-header-inline { display: flex; justify-content: space-between; align-items: center; }
.stat-card { text-align: center; padding: 8px 0; }
.stat-num { font-size: 32px; font-weight: 700; }
.stat-label { font-size: 13px; color: #606266; margin-top: 4px; }
.score-display { display: flex; align-items: center; padding: 16px; background: #f9fafc; border-radius: 8px; }
.warning-box { margin-top: 12px; padding: 12px; background: #fdf6ec; border-left: 4px solid #E6A23C; border-radius: 4px; }
.warning-box ul { margin-top: 6px; padding-left: 20px; }
.warning-pill { display: inline-block; margin-right: 4px; padding: 1px 6px; background: #fef0f0; color: #F56C6C; border-radius: 3px; font-size: 11px; }
.diff-mark { color: #E6A23C; font-weight: bold; margin-left: 4px; }
</style>
