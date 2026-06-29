<template>
  <div class="compliance-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>合规检查记录</span>
          <el-button type="primary" @click="showCheckDialog = true">新增检查</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="项目">
          <el-select v-model="projectId" placeholder="全部" clearable @change="fetchData">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable @change="fetchData">
            <el-option label="待检查" value="PENDING" />
            <el-option label="通过" value="PASS" />
            <el-option label="不通过" value="FAIL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilter">重置</el-button>
          <el-button type="primary" @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="requirementId" label="需求ID" width="100" />
        <el-table-column prop="regulationType" label="法规类型" width="140">
          <template #default="{ row }">
            <el-tag>{{ row.regulationType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkItem" label="检查项" min-width="200" show-overflow-tooltip />
        <el-table-column prop="checkResult" label="检查结果" width="120">
          <template #default="{ row }">
            <el-tag :type="row.checkResult === 'PASS' ? 'success' : 'danger'">{{ row.checkResult }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkedAt" label="检查时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增检查对话框 -->
    <el-dialog v-model="showCheckDialog" title="合规检查" width="500px">
      <el-form :model="checkForm" label-width="100px">
        <el-form-item label="需求ID">
          <el-input v-model.number="checkForm.requirementId" type="number" />
        </el-form-item>
        <el-form-item label="法规类型">
          <el-select v-model="checkForm.regulationType">
            <el-option label="NMPA" value="nmpa" />
            <el-option label="FDA" value="fda" />
            <el-option label="CE" value="ce" />
            <el-option label="IEC" value="iec" />
            <el-option label="ISO" value="iso" />
          </el-select>
        </el-form-item>
        <el-form-item label="检查项">
          <el-input v-model="checkForm.checkItem" type="textarea" rows="2" />
        </el-form-item>
        <el-form-item label="检查结果">
          <el-radio-group v-model="checkForm.checkResult">
            <el-radio label="PASS">通过</el-radio>
            <el-radio label="FAIL">不通过</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCheckDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCheck" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="合规检查详情" width="600px">
      <el-descriptions :column="2" border v-if="currentRecord">
        <el-descriptions-item label="需求ID">{{ currentRecord.requirementId }}</el-descriptions-item>
        <el-descriptions-item label="法规类型">{{ currentRecord.regulationType }}</el-descriptions-item>
        <el-descriptions-item label="检查项" :span="2">{{ currentRecord.checkItem }}</el-descriptions-item>
        <el-descriptions-item label="检查结果">{{ currentRecord.checkResult }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentRecord.status }}</el-descriptions-item>
        <el-descriptions-item label="检查时间">{{ currentRecord.checkedAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { complianceApi, type ComplianceRecord } from '@/api/compliance'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

interface Project {
  id: number
  projectName: string
}

const projectId = ref(1)
const projects = ref<Project[]>([])
const filterStatus = ref('')
const records = ref<ComplianceRecord[]>([])
const loading = ref(false)
const showCheckDialog = ref(false)
const showDetailDialog = ref(false)
const submitting = ref(false)
const currentRecord = ref<ComplianceRecord | null>(null)

const checkForm = ref({
  requirementId: 0,
  regulationType: 'iec',
  checkItem: '',
  checkResult: 'PASS',
})

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects')
    projects.value = res.data.data || []
  } catch {
    // ignore
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await complianceApi.getRecords({ projectId: projectId.value, status: filterStatus.value })
    records.value = res.data.data || []
  } catch {
    ElMessage.error('获取合规记录失败')
  } finally {
    loading.value = false
  }
}

const resetFilter = () => {
  filterStatus.value = ''
  fetchData()
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { PENDING: 'info', PASS: 'success', FAIL: 'danger' }
  return map[status] || 'info'
}

const complianceStatusLabels: Record<string, string> = {
  PENDING: '待检查',
  PASS: '通过',
  FAIL: '不通过',
}

const getStatusLabel = (status: string) => complianceStatusLabels[status] || status

const viewDetail = (row: ComplianceRecord) => {
  currentRecord.value = row
  showDetailDialog.value = true
}

const submitCheck = async () => {
  if (!checkForm.value.requirementId || !checkForm.value.checkItem) {
    ElMessage.warning('请填写必填项')
    return
  }
  submitting.value = true
  try {
    await complianceApi.check(checkForm.value)
    ElMessage.success('检查已记录')
    showCheckDialog.value = false
    fetchData()
  } catch {
    ElMessage.error('提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await fetchProjects()
  fetchData()
})
</script>

<style scoped>
.compliance-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-form { margin-bottom: 16px; }
</style>