<template>
  <div class="testcase-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>测试用例管理</span>
          <div class="header-actions">
            <!-- P3-7 v1.53 修复：加导出按钮（导出选中为 CSV） -->
            <el-button @click="exportSelected" :disabled="selectedIds.length === 0">导出选中 ({{ selectedIds.length }})</el-button>
            <!-- P3-7 v1.53 修复：批量操作下拉（启用/禁用/删除） -->
            <el-dropdown @command="handleBatchCommand" :disabled="selectedIds.length === 0">
              <el-button :disabled="selectedIds.length === 0">
                批量操作 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="ENABLE">启用</el-dropdown-item>
                  <el-dropdown-item command="DISABLE">禁用</el-dropdown-item>
                  <el-dropdown-item command="DELETE" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button type="primary" @click="showCreateDialog = true">新建用例</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="filterProjectId" placeholder="所属项目" clearable filterable style="width: 160px;" @change="onProjectChange">
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="p.projectName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="需求">
          <el-select v-model="filterRequirementId" placeholder="关联需求" clearable filterable style="width: 240px;" @change="fetchData">
            <el-option
              v-for="req in requirements"
              :key="req.id"
              :label="`${req.requirementNo} - ${req.title}`"
              :value="req.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable style="width: 120px;" @change="fetchData">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="进行中" value="ACTIVE" />
            <el-option label="通过" value="PASSED" />
            <el-option label="失败" value="FAILED" />
            <el-option label="已废弃" value="OBSOLETE" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table
        :data="testCases"
        border
        stripe
        v-loading="loading"
        @selection-change="onSelectionChange"
        row-key="id"
      >
        <!-- P3-7 v1.53 修复：多选列 -->
        <el-table-column type="selection" width="50" :reserve-selection="true" />
        <el-table-column prop="testCaseNo" label="用例编号" width="120" />
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="requirementNo" label="关联需求" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.requirementNo">{{ row.requirementNo }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="testType" label="测试类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ getTestTypeLabel(row.testType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="safetyClass" label="安全分类" width="80">
          <template #default="{ row }">
            <el-tag :type="getSafetyClassType(row.safetyClass)">{{ row.safetyClass || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <!-- P3-7 v1.53 修复：执行历史列（hover 显示最近 3 次执行结果） -->
        <el-table-column label="执行历史" width="110" align="center">
          <template #default="{ row }">
            <el-popover
              placement="left"
              :width="320"
              trigger="hover"
              :show-after="200"
            >
              <template #reference>
                <el-button text size="small" type="primary">
                  <el-icon><Clock /></el-icon> 历史
                </el-button>
              </template>
              <div v-if="getExecutionHistory(row).length === 0" style="color:#909399;text-align:center;padding:12px 0;">
                暂无执行记录
              </div>
              <div v-else>
                <div style="font-weight:600;margin-bottom:8px;font-size:13px;">最近 {{ getExecutionHistory(row).length }} 次执行</div>
                <div
                  v-for="(h, idx) in getExecutionHistory(row)"
                  :key="idx"
                  class="exec-history-row"
                >
                  <el-tag :type="getStatusType(h.result)" size="small" effect="plain">
                    {{ getStatusLabel(h.result) }}
                  </el-tag>
                  <span style="margin-left:8px;font-size:12px;color:#606266;">{{ h.executedAt }}</span>
                  <span v-if="h.executor" style="margin-left:8px;font-size:12px;color:#909399;">@{{ h.executor }}</span>
                </div>
              </div>
            </el-popover>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" type="success" @click="updateStatus(row, 'PASSED')" v-if="row.status !== 'PASSED'">通过</el-button>
            <el-button size="small" type="danger" @click="updateStatus(row, 'FAILED')" v-if="row.status !== 'FAILED'">失败</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="coverage-info" v-if="filterRequirementId && coverage >= 0">
        <el-progress :percentage="coverage" :status="coverage >= 100 ? 'success' : 'warning'" />
        <span class="coverage-text">测试覆盖率: {{ coverage }}%</span>
      </div>
    </el-card>

    <!-- 创建对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建测试用例" width="600px">
      <el-form :model="testCaseForm" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="关联项目" prop="projectId">
          <el-select v-model="testCaseForm.projectId" placeholder="选择关联项目" filterable style="width: 100%;" @change="onCreateProjectChange">
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="p.projectName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联需求" prop="requirementId">
          <el-select v-model="testCaseForm.requirementId" placeholder="选择关联需求" filterable style="width: 100%;">
            <el-option
              v-for="req in requirements"
              :key="req.id"
              :label="`${req.requirementNo} - ${req.title}`"
              :value="req.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="testCaseForm.title" placeholder="请输入测试用例标题" />
        </el-form-item>
        <el-form-item label="测试类型">
          <el-select v-model="testCaseForm.testType" style="width: 100%">
            <el-option label="单元测试" value="UNIT" />
            <el-option label="集成测试" value="INTEGRATION" />
            <el-option label="系统测试" value="SYSTEM" />
            <el-option label="验收测试" value="ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="安全分类">
          <el-select v-model="testCaseForm.safetyClass" style="width: 100%">
            <el-option label="A类" value="A" />
            <el-option label="B类" value="B" />
            <el-option label="C类" value="C" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="testCaseForm.description" type="textarea" :rows="2" placeholder="请输入测试描述" />
        </el-form-item>
        <el-form-item label="前置条件">
          <el-input v-model="testCaseForm.preCondition" type="textarea" :rows="2" placeholder="请输入前置条件" />
        </el-form-item>
        <el-form-item label="测试步骤">
          <el-input v-model="testCaseForm.testSteps" type="textarea" :rows="3" placeholder="请输入测试步骤" />
        </el-form-item>
        <el-form-item label="预期结果">
          <el-input v-model="testCaseForm.expectedResult" type="textarea" :rows="2" placeholder="请输入预期结果" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createTestCase" :loading="submitting">创建</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="测试用例详情" width="600px">
      <el-descriptions :column="2" border v-if="currentTestCase">
        <el-descriptions-item label="用例编号">{{ currentTestCase.testCaseNo }}</el-descriptions-item>
        <el-descriptions-item label="关联需求">
          <el-tag v-if="currentTestCase.requirementNo">{{ currentTestCase.requirementNo }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ currentTestCase.title }}</el-descriptions-item>
        <el-descriptions-item label="测试类型">{{ getTestTypeLabel(currentTestCase.testType) }}</el-descriptions-item>
        <el-descriptions-item label="安全分类">{{ currentTestCase.safetyClass || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentTestCase.status)">{{ getStatusLabel(currentTestCase.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentTestCase.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="前置条件" :span="2">{{ currentTestCase.preCondition || '-' }}</el-descriptions-item>
        <el-descriptions-item label="测试步骤" :span="2">{{ currentTestCase.testSteps || '-' }}</el-descriptions-item>
        <el-descriptions-item label="预期结果" :span="2">{{ currentTestCase.expectedResult || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'
import { requirementApi } from '@/api/requirement'
import { projectApi } from '@/api/project'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, Clock } from '@element-plus/icons-vue'

interface TestCase {
  id: number
  testCaseNo: string
  title: string
  description?: string
  testType: string
  safetyClass?: string
  status: string
  preCondition?: string
  testSteps?: string
  expectedResult?: string
  requirementId?: number
  requirementNo?: string
  projectId?: number
}

interface Requirement {
  id: number
  requirementNo: string
  title: string
  status: string
  projectId?: number
}

interface Project {
  id: number
  projectName: string
}

const filterProjectId = ref<number | null>(null)
const filterRequirementId = ref<number | null>(null)
const filterStatus = ref('')
const testCases = ref<TestCase[]>([])
const requirements = ref<Requirement[]>([])
const projects = ref<Project[]>([])
const loading = ref(false)
const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const submitting = ref(false)
const coverage = ref<number>(-1)
const currentTestCase = ref<TestCase | null>(null)
// P3-7 v1.53 修复：批量选中 ID 列表
const selectedIds = ref<number[]>([])
// P3-7 v1.53 修复：执行历史缓存（按 testCaseId 索引），懒加载
const executionHistoryMap = ref<Record<number, Array<{ result: string; executedAt: string; executor?: string }>>>({})

const formRef = ref<FormInstance>()

const testCaseForm = ref({
  requirementId: 0,
  projectId: 0,
  title: '',
  testType: 'UNIT',
  safetyClass: 'C',
  description: '',
  preCondition: '',
  testSteps: '',
  expectedResult: '',
})

const rules: FormRules = {
  projectId: [{ required: true, message: '请选择关联项目', trigger: 'change' }],
  requirementId: [{ required: true, message: '请选择关联需求', trigger: 'change' }],
  title: [{ required: true, message: '请输入测试用例标题', trigger: 'blur' }]
}

const fetchProjects = async () => {
  try {
    const res = await projectApi.list()
    projects.value = res.data?.data || []
  } catch (e) {
    console.error(e)
  }
}

const fetchRequirements = async (projectId?: number) => {
  try {
    const params: any = { page: 1, size: 1000 }
    if (projectId) params.projectId = projectId
    const res = await requirementApi.list(params)
    requirements.value = res.data?.data?.records || []
  } catch (e) {
    console.error(e)
  }
}

const onProjectChange = async (projectId: number | null) => {
  filterRequirementId.value = null
  testCases.value = []
  if (projectId) {
    await fetchRequirements(projectId)
  } else {
    requirements.value = []
  }
  fetchData(projectId)
}

const onCreateProjectChange = async (projectId: number) => {
  testCaseForm.value.requirementId = 0
  if (projectId) {
    await fetchRequirements(projectId)
  }
}

const fetchData = async (projectId?: number | null) => {
  loading.value = true
  coverage.value = -1
  try {
    let url = '/testcases'
    if (filterRequirementId.value) {
      url = `/testcases/requirement/${filterRequirementId.value}`
    }
    const res = await request.get(url)
    let data = res.data.data || []
    // 按项目筛选：过滤出该项目下需求关联的测试用例
    if (projectId || filterProjectId.value) {
      const targetProjectId = projectId || filterProjectId.value
      const reqIds = requirements.value.map((r: Requirement) => r.id)
      data = data.filter((tc: TestCase) => reqIds.includes(tc.requirementId))
    }
    if (filterStatus.value) {
      data = data.filter((tc: TestCase) => tc.status === filterStatus.value)
    }
    testCases.value = data
    if (filterRequirementId.value) {
      fetchCoverage(filterRequirementId.value)
    }
  } catch {
    ElMessage.error('获取测试用例列表失败')
  } finally {
    loading.value = false
  }
}

const fetchCoverage = async (requirementId: number) => {
  try {
    const res = await request.get(`/testcases/coverage/${requirementId}`)
    coverage.value = res.data.data || 0
  } catch {
    coverage.value = 0
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: 'info',
    ACTIVE: 'primary',
    PASSED: 'success',
    FAILED: 'danger',
    OBSOLETE: 'warning',
    PENDING: 'info',
    BLOCKED: 'warning',
    NA: 'info'
  }
  return map[status] || 'info'
}

const testCaseStatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '执行中',
  PASSED: '已通过',
  FAILED: '未通过',
  OBSOLETE: '已废弃',
  PENDING: '待执行',
  BLOCKED: '已阻塞',
  NA: '不适用'
}

const getStatusLabel = (status: string) => testCaseStatusLabels[status] || status

const testTypeLabels: Record<string, string> = {
  UNIT: '单元测试',
  INTEGRATION: '集成测试',
  SYSTEM: '系统测试',
  ACCEPTANCE: '验收测试'
}

const getTestTypeLabel = (type: string) => testTypeLabels[type] || type

const getSafetyClassType = (safetyClass?: string) => {
  const map: Record<string, string> = { A: 'success', B: 'warning', C: 'danger' }
  return map[safetyClass || ''] || 'info'
}

const viewDetail = (row: TestCase) => {
  currentTestCase.value = row
  showDetailDialog.value = true
}

const createTestCase = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const selectedReq = requirements.value.find(r => r.id === testCaseForm.value.requirementId)
  submitting.value = true
  try {
    await request.post('/testcases', {
      ...testCaseForm.value,
      requirementNo: selectedReq?.requirementNo || '',
      projectId: filterProjectId.value || testCaseForm.value.projectId
    })
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    formRef.value.resetFields()
    fetchData()
  } catch {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

const updateStatus = async (row: TestCase, status: string) => {
  try {
    await request.put(`/testcases/${row.id}/status?status=${status}`)
    ElMessage.success('状态已更新')
    fetchData()
  } catch {
    ElMessage.error('更新失败')
  }
}

// P3-7 v1.53 修复：表格多选变化
const onSelectionChange = (rows: TestCase[]) => {
  selectedIds.value = rows.map(r => r.id)
}

// P3-7 v1.53 修复：导出选中为 CSV
const exportSelected = () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请先勾选用例')
    return
  }
  const selected = testCases.value.filter(tc => selectedIds.value.includes(tc.id))
  const headers = ['用例编号', '标题', '关联需求', '测试类型', '安全分类', '状态', '前置条件', '测试步骤', '预期结果', '描述']
  const lines = [headers.join(',')]
  selected.forEach(tc => {
    lines.push([
      tc.testCaseNo,
      `"${(tc.title || '').replace(/"/g, '""')}"`,
      tc.requirementNo || '',
      getTestTypeLabel(tc.testType),
      tc.safetyClass || '',
      getStatusLabel(tc.status),
      `"${(tc.preCondition || '').replace(/"/g, '""')}"`,
      `"${(tc.testSteps || '').replace(/"/g, '""')}"`,
      `"${(tc.expectedResult || '').replace(/"/g, '""')}"`,
      `"${(tc.description || '').replace(/"/g, '""')}"`
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `测试用例-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${selected.length} 条用例`)
}

// P3-7 v1.53 修复：批量操作命令处理
const handleBatchCommand = async (cmd: string) => {
  if (selectedIds.value.length === 0) return
  const labelMap: Record<string, string> = { ENABLE: '启用', DISABLE: '禁用', DELETE: '删除' }
  try {
    if (cmd === 'DELETE') {
      await ElMessageBox.confirm(`将删除选中的 ${selectedIds.value.length} 条用例，是否继续？`, '批量删除', {
        type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
      })
    } else {
      await ElMessageBox.confirm(`将${labelMap[cmd]}选中的 ${selectedIds.value.length} 条用例，是否继续？`, '批量操作', {
        type: 'info', confirmButtonText: '确定', cancelButtonText: '取消'
      })
    }
    // 调后端批量接口（优先用 /testcases/batch，单条降级为循环）
    try {
      await request.post('/testcases/batch', { ids: selectedIds.value, action: cmd })
    } catch {
      // 降级：循环单条
      for (const id of selectedIds.value) {
        if (cmd === 'DELETE') await request.delete(`/testcases/${id}`)
        else if (cmd === 'ENABLE') await request.put(`/testcases/${id}/status?status=ACTIVE`)
        else if (cmd === 'DISABLE') await request.put(`/testcases/${id}/status?status=OBSOLETE`)
      }
    }
    ElMessage.success(`已${labelMap[cmd]} ${selectedIds.value.length} 条用例`)
    selectedIds.value = []
    fetchData()
  } catch (e: any) {
    if (e === 'cancel') return
    ElMessage.error('批量操作失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

// P3-7 v1.53 修复：执行历史懒加载
const getExecutionHistory = (row: TestCase) => {
  if (executionHistoryMap.value[row.id]) return executionHistoryMap.value[row.id]
  // 首次访问时拉取一次
  request.get(`/testcases/${row.id}/executions`, { params: { limit: 3 } })
    .then(res => {
      const data = res.data?.data || []
      executionHistoryMap.value[row.id] = data
    })
    .catch(() => {
      // 后端无此接口时，返回空数组，避免阻塞 UI
      executionHistoryMap.value[row.id] = []
    })
  return executionHistoryMap.value[row.id] || []
}

onMounted(() => {
  fetchProjects()
  fetchData()
})
</script>

<style scoped>
.testcase-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 8px; }
.filter-form { margin-bottom: 16px; }
.coverage-info {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.coverage-text { font-size: 14px; color: #606266; }
/* P3-7 v1.53 修复：执行历史条目 */
.exec-history-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}
.exec-history-row:last-child { border-bottom: none; }
</style>