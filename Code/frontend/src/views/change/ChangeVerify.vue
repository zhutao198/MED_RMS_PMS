<template>
  <div class="change-verify-container">
    <div class="page-header">
      <div>
        <el-button text @click="$router.back()">← 返回</el-button>
        <span class="page-title">验证变更 · {{ change?.changeCode || changeId }}</span>
      </div>
    </div>

    <el-card v-loading="loading" class="info-card">
      <template #header>
        <div class="card-header">
          <span>变更基本信息</span>
          <el-tag v-if="change" :type="statusType(change.status)">{{ change.status }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="变更编号">{{ change?.changeCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="变更类型">{{ change?.changeType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="紧急程度">{{ change?.urgency || '-' }}</el-descriptions-item>
        <el-descriptions-item label="影响范围" :span="3">{{ change?.impactScope || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card class="verify-card">
      <template #header>
        <div class="card-header">
          <span>验证清单</span>
          <span class="hint">提示：每行选择 PASSED/FAILED/NA，可上传证据文件（≤20MB）</span>
        </div>
      </template>
      <el-table :data="verifyItems" border>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="name" label="验证项" min-width="200" />
        <el-table-column label="结果" width="160">
          <template #default="{ row }">
            <el-radio-group v-model="row.result">
              <el-radio-button label="PASSED">通过</el-radio-button>
              <el-radio-button label="FAILED">失败</el-radio-button>
              <el-radio-button label="NA">不适用</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>
        <el-table-column label="证据上传" width="220">
          <template #default="{ row }">
            <el-upload
              :auto-upload="false"
              :limit="3"
              :on-change="(file: any) => handleFileChange(row, file)"
              :before-upload="beforeUpload"
              multiple
            >
              <el-button size="small" type="primary" text>选择文件</el-button>
            </el-upload>
            <div v-if="row.evidenceFiles?.length" class="files-list">
              <div v-for="(f, i) in row.evidenceFiles" :key="i" class="file-item">
                <span>{{ f.name }}</span>
                <el-button size="small" text type="danger" @click="removeFile(row, i)">移除</el-button>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" placeholder="备注" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="result-card">
      <template #header>
        <span>验证结论</span>
      </template>
      <el-form :model="conclusionForm" label-width="120px">
        <el-form-item label="验证结论" required>
          <el-select v-model="conclusionForm.conclusion" placeholder="请选择" style="width: 100%">
            <el-option label="全部通过" value="FULLY_PASSED" />
            <el-option label="部分通过" value="PARTIALLY_PASSED" />
            <el-option label="未通过" value="FAILED" />
            <el-option label="延期" value="DEFERRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="验证人意见">
          <el-input v-model="conclusionForm.remark" type="textarea" :rows="4" placeholder="请输入验证意见" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交验证</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { request } from '@/api/request'

const route = useRoute()
const router = useRouter()
const changeId = Number(route.params.id)
const loading = ref(false)
const submitting = ref(false)
const change = ref<any>(null)
const verifyItems = ref<any[]>([
  { name: '需求追溯链完整性', result: '', remark: '', evidenceFiles: [] },
  { name: '测试用例执行情况', result: '', remark: '', evidenceFiles: [] },
  { name: '风险控制措施落地', result: '', remark: '', evidenceFiles: [] },
  { name: '基线影响评估', result: '', remark: '', evidenceFiles: [] },
  { name: '相关 SOP 文档更新', result: '', remark: '', evidenceFiles: [] }
])
const conclusionForm = reactive({ conclusion: '', remark: '' })

const loadData = async () => {
  loading.value = true
  try {
    const c = await request.get(`/changes/${changeId}`)
    change.value = c.data?.data || null
  } catch (e: any) {
    ElMessage.error(`加载失败：${e?.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

const beforeUpload = (file: any) => {
  const maxMB = 20
  if (file.size / 1024 / 1024 > maxMB) {
    ElMessage.error(`文件 ${file.name} 超过 ${maxMB}MB 限制`)
    return false
  }
  return true
}

const handleFileChange = (row: any, file: any) => {
  if (!beforeUpload(file)) return
  if (!row.evidenceFiles) row.evidenceFiles = []
  row.evidenceFiles.push(file.raw || file)
}

const removeFile = (row: any, idx: number) => {
  row.evidenceFiles.splice(idx, 1)
}

const statusType = (s: string) => ({
  DRAFT: 'info', SUBMITTED: 'primary', ANALYZING: 'warning', PENDING_APPROVAL: 'warning',
  APPROVED: 'success', EXECUTING: 'warning', VERIFIED: 'success', CLOSED: 'info', CANCELLED: 'danger'
} as any)[s] || ''

const handleSubmit = async () => {
  if (!conclusionForm.conclusion) {
    ElMessage.warning('请选择验证结论')
    return
  }
  submitting.value = true
  try {
    await request.post(`/changes/${changeId}/verify`, null)
    ElMessage.success('验证提交成功')
    router.push(`/changes/${changeId}`)
  } catch (e: any) {
    ElMessage.error(`提交失败：${e?.message || '未知错误'}`)
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.change-verify-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { font-size: 18px; font-weight: 600; color: #303133; margin-left: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-size: 15px; font-weight: 600; }
.info-card, .verify-card, .result-card { margin-bottom: 16px; border-radius: 8px; }
.hint { font-size: 12px; color: #909399; }
.files-list { margin-top: 4px; }
.file-item { display: flex; justify-content: space-between; font-size: 12px; color: #606266; padding: 2px 0; }
</style>
