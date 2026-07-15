<template>
  <div class="pool-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>需求收集池</span>
          <div>
            <el-button v-permission="'req:create'" type="primary" @click="openAddDialog">添加需求</el-button>
            <el-button @click="showImportDialog = true">导入</el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable @change="fetchData" style="width:130px">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已转换" value="CONVERTED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="filterSource" placeholder="全部" clearable @change="fetchData" style="width:130px">
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="市场" value="MARKET" />
            <el-option label="法规" value="REGULATION" />
            <el-option label="内部" value="INTERNAL" />
            <el-option label="竞品" value="COMPETITOR" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="用户反馈" value="FEEDBACK" />
            <el-option label="支持工单" value="SUPPORT" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="poolItems" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="source" label="来源" width="100">
          <template #default="{ row }">
            <el-tag>{{ getSourceName(row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceNo" label="来源编号" width="140" />
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="rawDescription" label="原始描述" min-width="250" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.priority" :type="getPriorityType(row.priority)">{{ row.priority }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="row.status !== 'PENDING'" @click="showConvertDialog(row)">
              转换为URS
            </el-button>
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'PENDING'" size="small" type="warning" @click="rejectItem(row)">
              拒绝
            </el-button>
            <el-button v-if="row.status === 'REJECTED'" size="small" type="danger" @click="deleteItem(row)">
              删除
            </el-button>
            <el-button v-if="row.convertedToId" size="small" type="success" link @click="gotoUrs(row.convertedToId)">
              查看URS
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 导入对话框 -->
    <el-dialog v-model="showImportDialog" title="导入需求" width="500px">
      <el-upload drag action="" :before-upload="handleImportUpload" accept=".xlsx,.xls,.json">
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 .xlsx / .xls / .json 格式，第一行必须是表头</div>
        </template>
      </el-upload>
      <div v-if="importResult" style="margin-top: 12px;">
        <el-alert v-if="importResult.success > 0" title="导入成功" type="success" :description="`成功导入 ${importResult.success} 条需求`" show-icon />
        <el-alert v-if="importResult.errors.length > 0" title="导入错误" type="error" :description="importResult.errors.join('; ')" show-icon />
      </div>
    </el-dialog>

    <!-- 拒绝理由对话框 -->
    <el-dialog v-model="showRejectDialog" title="拒绝需求" width="480px">
      <el-form label-width="100px">
        <el-form-item label="需求">
          <span>{{ rejectTarget?.title || rejectTarget?.rawDescription }}</span>
        </el-form-item>
        <el-form-item label="拒绝理由" required>
          <el-input v-model="rejectReason" type="textarea" rows="4" placeholder="请说明拒绝该需求的理由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="warning" :loading="rejecting" @click="confirmReject">确定拒绝</el-button>
      </template>
    </el-dialog>

    <!-- 添加需求对话框 -->
    <el-dialog v-model="showAddDialog" title="添加需求到收集池" width="600px">
      <el-form :model="addForm" label-width="120px">
        <el-form-item label="来源" required>
          <el-select v-model="addForm.source" style="width:100%">
            <el-option label="客户" value="CUSTOMER" />
            <el-option label="市场" value="MARKET" />
            <el-option label="法规" value="REGULATION" />
            <el-option label="内部" value="INTERNAL" />
            <el-option label="竞品" value="COMPETITOR" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="用户反馈" value="FEEDBACK" />
            <el-option label="支持工单" value="SUPPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源编号">
          <el-input v-model="addForm.sourceNo" placeholder="法规条款号/客户需求编号等" />
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="addForm.title" placeholder="需求标题" />
        </el-form-item>
        <el-form-item label="优先级" required>
          <el-select v-model="addForm.priority" style="width:100%">
            <el-option label="MUST 必须" value="MUST" />
            <el-option label="SHOULD 应该" value="SHOULD" />
            <el-option label="COULD 可以" value="COULD" />
            <el-option label="WONT 不会" value="WONT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务场景">
          <el-input v-model="addForm.businessScenario" type="textarea" rows="3" placeholder="描述该需求对应的业务场景和用户故事" />
        </el-form-item>
        <el-form-item label="竞争分析">
          <el-input v-model="addForm.competitiveAnalysis" type="textarea" rows="3" placeholder="竞品是否有类似功能？差异化优势是什么？" />
        </el-form-item>
        <el-form-item label="原始描述" required>
          <el-input v-model="addForm.rawDescription" type="textarea" rows="4" placeholder="请录入原始需求描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="addLoading" @click="submitAdd">添加</el-button>
      </template>
    </el-dialog>

    <!-- 转换对话框 -->
    <el-dialog v-model="showConvertDialogFlag" title="转换为URS" width="420px">
      <el-form :model="convertForm" label-width="100px">
        <el-form-item label="项目" required>
          <ProjectSelector v-model="convertForm.projectId" :sync-to-store="false" @change="onProjectChange" />
        </el-form-item>
        <el-form-item label="优先级" required>
          <el-select v-model="convertForm.priority">
            <el-option label="MUST 必须" value="MUST" />
            <el-option label="SHOULD 应该" value="SHOULD" />
            <el-option label="COULD 可以" value="COULD" />
            <el-option label="WONT 不会" value="WONT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showConvertDialogFlag = false">取消</el-button>
        <el-button v-permission="'req:create'" type="primary" :loading="convertLoading" @click="submitConvert">转换</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="需求详情" width="640px">
      <el-descriptions v-if="detailItem" :column="2" border>
        <el-descriptions-item label="ID">{{ detailItem.id }}</el-descriptions-item>
        <el-descriptions-item label="来源">
          <el-tag>{{ getSourceName(detailItem.source) }}</el-tag>
          <span class="raw-text">({{ detailItem.source }})</span>
        </el-descriptions-item>
        <el-descriptions-item label="来源编号" :span="2">{{ detailItem.sourceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detailItem.title }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag v-if="detailItem.priority" :type="getPriorityType(detailItem.priority)">{{ detailItem.priority }}</el-tag>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(detailItem.status)">{{ getStatusLabel(detailItem.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="关联项目" :span="2">{{ getProjectLabel(detailItem.projectId) }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailItem.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailItem.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="原始描述" :span="2">
          <pre class="desc-pre">{{ detailItem.rawDescription }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.parsedDescription" label="解析后描述" :span="2">
          <pre class="desc-pre">{{ detailItem.parsedDescription }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.businessScenario" label="业务场景" :span="2">
          <pre class="desc-pre">{{ detailItem.businessScenario }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.competitiveAnalysis" label="竞争分析" :span="2">
          <pre class="desc-pre">{{ detailItem.competitiveAnalysis }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.rejectionReason" label="拒绝理由" :span="2">
          <pre class="desc-pre">{{ detailItem.rejectionReason }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.convertedToId" label="转换后URS ID">
          <el-link type="primary" @click="gotoUrs(detailItem.convertedToId)">#{{ detailItem.convertedToId }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.conversionNotes" label="转换备注" :span="2">
          <pre class="desc-pre">{{ detailItem.conversionNotes }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useProject } from '@/composables/useProject'
import ProjectSelector from '@/components/ProjectSelector.vue'
const { getProjectLabel, ensureLoaded } = useProject()
import * as XLSX from 'xlsx'

interface PoolItem {
  id: string
  source: string
  sourceNo: string
  title: string
  rawDescription: string
  parsedDescription?: string
  priority?: string
  status: string
  projectId?: number
  createdBy?: number
  createdAt: string
  convertedToId?: number
  conversionNotes?: string
  businessScenario?: string
  competitiveAnalysis?: string
  rejectionReason?: string
}

const router = useRouter()

const filterStatus = ref('')
const filterSource = ref('')
const poolItems = ref<PoolItem[]>([])
const loading = ref(false)
const showAddDialog = ref(false)
const showConvertDialogFlag = ref(false)
const showDetailDialog = ref(false)
const currentItem = ref<PoolItem | null>(null)
const detailItem = ref<PoolItem | null>(null)
const addLoading = ref(false)
const convertLoading = ref(false)

const defaultAddForm = () => ({
  source: 'CUSTOMER',
  sourceNo: '',
  rawDescription: '',
  title: '',
  priority: '',
  businessScenario: '',
  competitiveAnalysis: '',
  projectId: undefined as number | undefined,
})

const addForm = ref(defaultAddForm())

const defaultConvertForm = () => ({
  priority: 'MUST',
  projectId: undefined as number | undefined,
})

const convertForm = ref(defaultConvertForm())

const showImportDialog = ref(false)
const importResult = ref<{ success: number; errors: string[] } | null>(null)

const showRejectDialog = ref(false)
const rejectTarget = ref<PoolItem | null>(null)
const rejectReason = ref('')
const rejecting = ref(false)

const handleImportUpload = async (file: File) => {
  importResult.value = null
  try {
    let items: any[]
    if (file.name.endsWith('.json')) {
      const text = await file.text()
      const data = JSON.parse(text)
      items = data.items || data
    } else if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
      const buf = await file.arrayBuffer()
      const workbook = XLSX.read(buf, { type: 'array' })
      const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
      const rows: any[][] = XLSX.utils.sheet_to_json(firstSheet, { header: 1 })
      const headerRowIndex = rows.findIndex(r => r.some(c => String(c).match(/编码|内容|描述|标题|来源|rawDescription/i)))
      if (headerRowIndex === -1) {
        ElMessage.error('未找到有效表头行，请确保第一行含"内容"/"描述"等列名')
        return false
      }
      const headerMap: Record<number, string> = {}
      const headers = rows[headerRowIndex]
      for (let i = 0; i < headers.length; i++) {
        const h = String(headers[i] ?? '').trim()
        if (h) headerMap[i] = h
      }
      items = []
      for (let r = headerRowIndex + 1; r < rows.length; r++) {
        const row = rows[r]
        if (!row || row.every((c: any) => c === undefined || c === null || String(c).trim() === '')) continue
        const obj: Record<string, any> = {}
        for (const [colIdx, colName] of Object.entries(headerMap)) {
          obj[colName] = row[Number(colIdx)]
        }
        items.push(obj)
      }
    } else {
      ElMessage.error('不支持的文件格式，请使用 .xlsx / .xls / .json')
      return false
    }
    if (!items || items.length === 0) {
      ElMessage.warning('文件中没有数据')
      return false
    }
    await request.post('/requirement-pool/import', items)
    ElMessage.success(`成功导入 ${items.length} 条需求`)
    showImportDialog.value = false
    fetchData()
  } catch (e: any) {
    importResult.value = {
      success: 0,
      errors: [e?.response?.data?.message || e.message || '导入失败'],
    }
  }
  return false
}

const fetchData = async () => {
  loading.value = true
  try {
    const params: Record<string, string> = {}
    if (filterStatus.value) params.status = filterStatus.value
    if (filterSource.value) params.source = filterSource.value
    const res = await request.get('/requirement-pool', { params })
    poolItems.value = res.data.data || []
  } catch {
    ElMessage.error('获取失败')
  } finally {
    loading.value = false
  }
}

const getSourceName = (source: string) => {
  const map: Record<string, string> = {
    CUSTOMER: '客户',
    MARKET: '市场',
    REGULATION: '法规',
    INTERNAL: '内部',
    COMPETITOR: '竞品',
    EMAIL: '邮件',
    FEEDBACK: '用户反馈',
    SUPPORT: '支持工单',
  }
  return map[source] || source
}

const getPriorityType = (priority: string) => {
  const map: Record<string, string> = { MUST: 'danger', SHOULD: 'warning', COULD: 'info', WONT: 'success' }
  return map[priority] || 'info'
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = { PENDING: 'info', PARSED: 'warning', CONVERTED: 'success', REJECTED: 'danger' }
  return map[status] || 'info'
}

const poolStatusLabels: Record<string, string> = {
  PENDING: '待处理',
  PARSED: '已解析',
  CONVERTED: '已转换',
  REJECTED: '已拒绝',
}

const getStatusLabel = (status: string) => poolStatusLabels[status] || status

const openAddDialog = () => {
  addForm.value = defaultAddForm()
  showAddDialog.value = true
}

const showConvertDialog = (row: PoolItem) => {
  currentItem.value = row
  convertForm.value = defaultConvertForm()
  showConvertDialogFlag.value = true
}

const viewDetail = (row: PoolItem) => {
  detailItem.value = row
  showDetailDialog.value = true
}

const gotoUrs = (id: number) => {
  showDetailDialog.value = false
  router.push(`/requirements/${id}`)
}

const submitAdd = async () => {
  if (!addForm.value.source) {
    ElMessage.warning('请选择来源')
    return
  }
  if (!addForm.value.title) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!addForm.value.priority) {
    ElMessage.warning('请选择优先级')
    return
  }
  if (!addForm.value.rawDescription) {
    ElMessage.warning('请填写原始描述')
    return
  }
  addLoading.value = true
  try {
    const res = await request.post('/requirement-pool', addForm.value)
    const newId = res.data?.data
    showAddDialog.value = false
    addForm.value = defaultAddForm()
    ElMessage.success(`添加成功 ID=${newId}`)
    fetchData()
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    addLoading.value = false
  }
}

const submitConvert = async () => {
  if (!currentItem.value) return
  if (!convertForm.value.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  if (!convertForm.value.priority) {
    ElMessage.warning('请选择优先级')
    return
  }
  convertLoading.value = true
  try {
    const res = await request.post(
      `/requirement-pool/${currentItem.value.id}/convert`,
      convertForm.value,
    )
    const ursId = res.data?.data
    showConvertDialogFlag.value = false
    ElMessageBox.confirm(
      `转换成功！已生成 URS 需求 ID=${ursId}，是否立即查看？`,
      '转换成功',
      { confirmButtonText: '查看 URS', cancelButtonText: '留在列表' },
    )
      .then(() => gotoUrs(ursId))
      .catch(() => fetchData())
  } catch (e: any) {
    ElMessage.error('转换失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    convertLoading.value = false
  }
}

const rejectItem = (row: PoolItem) => {
  rejectTarget.value = row
  rejectReason.value = ''
  showRejectDialog.value = true
}

const confirmReject = async () => {
  if (!rejectTarget.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写拒绝理由')
    return
  }
  rejecting.value = true
  try {
    await request.post(`/requirement-pool/${rejectTarget.value.id}/reject`, { reason: rejectReason.value })
    ElMessage.success('已拒绝')
    showRejectDialog.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error('拒绝失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    rejecting.value = false
  }
}

const deleteItem = async (row: PoolItem) => {
  try {
    await ElMessageBox.confirm(`确定永久删除需求「${row.title || row.rawDescription}」？此操作不可撤销。`, '确认删除', {
      confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error',
    })
    await request.delete(`/requirement-pool/${row.id}`)
    ElMessage.success('已删除')
    fetchData()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

onMounted(() => {
  fetchData()
  ensureLoaded()
})
</script>

<style scoped>
.pool-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-form { margin-bottom: 16px; }
.raw-text { color: #909399; margin-left: 6px; font-size: 12px; }
.desc-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
}
</style>
