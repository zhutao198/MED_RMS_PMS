<template>
  <div class="pool-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>需求收集池</span>
          <el-button type="primary" @click="openAddDialog">添加需求</el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable @change="fetchData">
            <el-option label="待处理" value="PENDING" />
            <el-option label="已解析" value="PARSED" />
            <el-option label="已转换" value="CONVERTED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="filterSource" placeholder="全部" clearable @change="fetchData">
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
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="row.status !== 'PENDING'" @click="showConvertDialog(row)">
              转换为URS
            </el-button>
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button v-if="row.convertedToId" size="small" type="success" link @click="gotoUrs(row.convertedToId)">
              查看URS
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加需求对话框 -->
    <el-dialog v-model="showAddDialog" title="添加需求到收集池" width="500px">
      <el-form :model="addForm" label-width="100px">
        <el-form-item label="来源" required>
          <el-select v-model="addForm.source">
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
        <el-form-item label="原始描述" required>
          <el-input v-model="addForm.rawDescription" type="textarea" rows="4" placeholder="请输入原始需求描述" />
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
          <el-select v-model="convertForm.projectId" placeholder="选择项目" filterable>
            <el-option
              v-for="p in projectList"
              :key="p.id"
              :label="`${p.projectNo} - ${p.projectName}`"
              :value="p.id"
            />
          </el-select>
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
        <el-button type="primary" :loading="convertLoading" @click="submitConvert">转换</el-button>
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
        <el-descriptions-item label="关联项目" :span="2">{{ detailItem.projectId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detailItem.createdBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ detailItem.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="原始描述" :span="2">
          <pre class="desc-pre">{{ detailItem.rawDescription }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detailItem.parsedDescription" label="解析后描述" :span="2">
          <pre class="desc-pre">{{ detailItem.parsedDescription }}</pre>
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
import { projectApi, type Project } from '@/api/project'

interface PoolItem {
  id: number
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
const projectList = ref<Project[]>([])
const addLoading = ref(false)
const convertLoading = ref(false)

const defaultAddForm = () => ({
  source: 'CUSTOMER',
  sourceNo: '',
  rawDescription: '',
  title: '',
  priority: '',
  projectId: undefined as number | undefined,
})

const addForm = ref(defaultAddForm())

const defaultConvertForm = () => ({
  priority: 'MUST',
  projectId: undefined as number | undefined,
})

const convertForm = ref(defaultConvertForm())

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

const fetchProjects = async () => {
  try {
    const res = await projectApi.list()
    projectList.value = (res.data.data || []).filter((p: Project) => p.status !== 'CLOSED')
  } catch {
    // 项目列表拉取失败不阻塞主流程
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

onMounted(() => {
  fetchData()
  fetchProjects()
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
