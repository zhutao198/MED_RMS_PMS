<template>
  <div class="regulations-container">
    <div class="page-header">
      <div class="page-title">📜 法规条款映射</div>
      <div class="header-actions">
        <el-button @click="handleExport">📤 导出映射表</el-button>
        <el-button type="primary" @click="showCreate = true">+ 添加映射规则</el-button>
        <el-button @click="$router.push('/compliance/regulation-impact')">🔍 影响分析</el-button>
      </div>
    </div>

    <div class="filter-bar">
      <el-select v-model="regulationFilter" style="width: 140px" placeholder="法规" @change="handleFilter" clearable>
        <el-option label="全部法规" value="" />
        <el-option label="NMPA" value="nmpa" />
        <el-option label="FDA" value="fda" />
        <el-option label="CE" value="ce" />
        <el-option label="IEC" value="iec" />
        <el-option label="ISO" value="iso" />
      </el-select>
      <el-select v-model="levelFilter" style="width: 130px" placeholder="需求层级" @change="handleFilter" clearable>
        <el-option label="全部层级" value="" />
        <el-option label="URS" value="URS" />
        <el-option label="PRS" value="PRS" />
        <el-option label="SRS" value="SRS" />
        <el-option label="DRS" value="DRS" />
      </el-select>
      <el-input v-model="searchKeyword" placeholder="搜索条款/需求" style="width: 220px" clearable @keyup.enter="fetchMappings">
        <template #prefix><span>🔍</span></template>
      </el-input>
      <el-button size="small" @click="resetFilters">重置</el-button>
    </div>

    <div class="mapping-card">
      <el-table :data="filteredMappings" border style="width: 100%" v-loading="loading">
        <el-table-column prop="regType" label="法规" width="110">
          <template #default="{ row }">
            <span class="reg-badge" :class="(row.regType || '').toLowerCase()">{{ (row.regType || '').toUpperCase() }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="clause" label="条款编号" width="160">
          <template #default="{ row }">
            <span style="font-weight: 600;">{{ row.clause }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="条款描述" min-width="240" show-overflow-tooltip />
        <el-table-column label="关联需求" min-width="200">
          <template #default="{ row }">
            <span v-for="req in (row.linkedReqs || [])" :key="req" class="req-link">{{ req }}</span>
            <span v-if="!row.linkedReqs || row.linkedReqs.length === 0" style="color:#c0c4cc">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="count" label="映射数量" width="100" align="center">
          <template #default="{ row }">
            <span style="font-weight: 600;">{{ row.count || (row.linkedReqs || []).length }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="handleView(row)">详情</el-button>
            <el-button size="small" text type="primary" @click="handleEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 创建/编辑映射规则 -->
    <el-dialog v-model="showCreate" :title="editing ? '编辑映射规则' : '添加映射规则'" width="540px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="法规类型" required>
          <el-select v-model="form.regType" style="width:100%;">
            <el-option label="NMPA" value="nmpa" />
            <el-option label="FDA" value="fda" />
            <el-option label="CE" value="ce" />
            <el-option label="IEC" value="iec" />
            <el-option label="ISO" value="iso" />
          </el-select>
        </el-form-item>
        <el-form-item label="条款编号" required>
          <el-input v-model="form.clause" placeholder="如 YY 0505 / 21 CFR Part 820" />
        </el-form-item>
        <el-form-item label="条款描述" required>
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="关联需求">
          <el-input v-model="form.linkedReqsText" type="textarea" :rows="2" placeholder="多个需求编号用英文逗号分隔，如 URS-001,PRS-005" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 法规条款映射页 (合规域独立页面 P0 修复)
 * 对应原型：compliance-regulations-原型.html
 * 对应路由：/compliance/regulations
 * 对应后端：ComplianceController.getRegulations / createRegulation
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

interface RegulationMapping {
  id: number
  regType: string
  clause: string
  description: string
  linkedReqs?: string[]
  count?: number
}

const regulationFilter = ref('')
const levelFilter = ref('')
const searchKeyword = ref('')
const loading = ref(false)
const saving = ref(false)
const showCreate = ref(false)
const editing = ref(false)
const form = ref({
  id: 0,
  regType: 'nmpa',
  clause: '',
  description: '',
  linkedReqsText: ''
})
const mappings = ref<RegulationMapping[]>([])

// 内置法规库兜底（当后端无数据时展示）
// P1-29: 法规类型统一小写
const FALLBACK_MAPPINGS: RegulationMapping[] = [
  { id: 1, regType: 'nmpa', clause: 'YY 0505', description: '医用电气设备电磁兼容要求', linkedReqs: ['URS-ECG3-0010', 'PRS-ECG3-0020'], count: 2 },
  { id: 2, regType: 'fda', clause: '21 CFR Part 820', description: '质量体系法规', linkedReqs: ['URS-ECG3-0001'], count: 1 },
  { id: 3, regType: 'ce', clause: 'EN 60601-1', description: '医用电气设备通用安全要求', linkedReqs: ['URS-ECG3-0002', 'PRS-ECG3-0015', 'SRS-ECG3-0042'], count: 3 },
  { id: 4, regType: 'iec', clause: 'IEC 62304', description: '医疗器械软件生命周期过程', linkedReqs: ['URS-ECG3-0003', 'PRS-ECG3-0016'], count: 2 },
  { id: 5, regType: 'iso', clause: 'ISO 14971', description: '医疗器械风险管理对医疗器械的应用', linkedReqs: ['URS-ECG3-0004'], count: 1 }
]

const filteredMappings = computed(() => {
  const kw = searchKeyword.value.toLowerCase()
  return mappings.value.filter(m => {
    if (regulationFilter.value && (m.regType || '').toUpperCase() !== regulationFilter.value.toUpperCase()) return false
    if (kw) {
      const inClause = (m.clause || '').toLowerCase().includes(kw)
      const inDesc = (m.description || '').toLowerCase().includes(kw)
      const inReqs = (m.linkedReqs || []).some(r => r.toLowerCase().includes(kw))
      if (!inClause && !inDesc && !inReqs) return false
    }
    return true
  })
})

const fetchMappings = async () => {
  loading.value = true
  try {
    const res = await request.get('/compliance/regulations')
    const data = res.data?.data
    if (Array.isArray(data) && data.length > 0) {
      mappings.value = data.map((d: any) => ({
        id: d.id,
        regType: d.regulationType || d.regType,
        clause: d.clause || d.clauseNo,
        description: d.description || d.clauseDesc,
        linkedReqs: d.linkedReqs || [],
        count: d.count || 0
      }))
    } else {
      mappings.value = FALLBACK_MAPPINGS
    }
  } catch {
    mappings.value = FALLBACK_MAPPINGS
  } finally {
    loading.value = false
  }
}

const handleFilter = () => { /* computed 自动响应 */ }
const resetFilters = () => {
  regulationFilter.value = ''
  levelFilter.value = ''
  searchKeyword.value = ''
}

const handleView = (row: RegulationMapping) => {
  ElMessageBox.alert(
    `法规：${row.regType}\n条款：${row.clause}\n描述：${row.description}\n关联需求：${(row.linkedReqs || []).join(', ') || '无'}`,
    '映射详情',
    { confirmButtonText: '关闭' }
  )
}

const handleEdit = (row: RegulationMapping) => {
  editing.value = true
  form.value = {
    id: row.id,
    regType: row.regType,
    clause: row.clause,
    description: row.description,
    linkedReqsText: (row.linkedReqs || []).join(',')
  }
  showCreate.value = true
}

const submitForm = async () => {
  if (!form.value.clause || !form.value.description) {
    ElMessage.warning('请填写条款编号和描述')
    return
  }
  saving.value = true
  try {
    const linkedReqs = form.value.linkedReqsText
      .split(',').map(s => s.trim()).filter(Boolean)
    await request.post('/compliance/regulations', {
      id: editing.value ? form.value.id : undefined,
      regulationType: form.value.regType,
      clause: form.value.clause,
      description: form.value.description,
      linkedReqs
    })
    ElMessage.success(editing.value ? '已更新映射规则' : '已添加映射规则')
    showCreate.value = false
    editing.value = false
    form.value = { id: 0, regType: 'nmpa', clause: '', description: '', linkedReqsText: '' }
    await fetchMappings()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

const handleExport = () => {
  if (!mappings.value.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const headers = ['法规', '条款编号', '条款描述', '关联需求', '映射数量']
  const lines = [headers.join(',')]
  mappings.value.forEach(m => {
    lines.push([
      m.regType,
      `"${(m.clause || '').replace(/"/g, '""')}"`,
      `"${(m.description || '').replace(/"/g, '""')}"`,
      `"${(m.linkedReqs || []).join(';')}"`,
      m.count || (m.linkedReqs || []).length
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `法规映射-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${mappings.value.length} 条映射`)
}

onMounted(fetchMappings)
</script>

<style scoped>
.regulations-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; }
.header-actions { display: flex; gap: 10px; }
.filter-bar { background: #fff; border-radius: 8px; padding: 14px 16px; margin-bottom: 16px; display: flex; gap: 12px; align-items: center; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.mapping-card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.reg-badge { display: inline-flex; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.reg-badge.nmpa { background: #fef0f0; color: #f56c6c; }
.reg-badge.fda { background: #ecf5ff; color: #409eff; }
.reg-badge.ce { background: #f0f9eb; color: #67c23a; }
.reg-badge.iec { background: #fdf6ec; color: #e6a23c; }
.reg-badge.iso { background: #f4f4f5; color: #909399; }
.req-link { color: #409eff; cursor: pointer; font-size: 12px; margin-right: 8px; }
.req-link:hover { text-decoration: underline; }
</style>
