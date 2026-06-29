<template>
  <div class="fmea-container">
    <div class="page-title">
      <h2>🛠 FMEA 在线编辑器（FR-1.8）</h2>
      <div class="header-actions">
        <el-input v-model="rpnThreshold" placeholder="RPN ≥" clearable style="width: 140px;" @clear="loadList" @keyup.enter="loadList" />
        <el-button @click="loadList">筛选</el-button>
        <el-button @click="loadList">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px;">
      FMEA（失效模式与影响分析）：S（严重度）× O（发生度）× D（探测度）= RPN（风险优先数）。
      RPN ≥ 100 建议优先采取改进措施。
    </el-alert>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="requirementId" label="需求" width="80" />
      <el-table-column prop="hazardSource" label="失效模式 / 危险源" min-width="200" show-overflow-tooltip />
      <el-table-column prop="harm" label="影响" width="160" show-overflow-tooltip />
      <el-table-column label="S" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="scoreTag(row.severity)">{{ row.severity || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="O" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="scoreTag(row.occurrence)">{{ row.occurrence || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="D" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="scoreTag(row.detection)">{{ row.detection || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="RPN" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.rpn" :type="rpnTag(row.rpn)" effect="dark">{{ row.rpn }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="措施状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.actionStatus" :type="actionStatusTag(row.actionStatus)">{{ actionStatusLabel(row.actionStatus) }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="actionOwner" label="责任人" width="100" />
      <!-- P3-6 v1.53 修复：加「残余风险」列（可接受/不可接受），编辑列允许切换 -->
      <el-table-column label="残余风险" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.residualRiskAcceptable === false ? 'danger' : 'success'" size="small">
            {{ row.residualRiskAcceptable === false ? '不可接受' : '可接受' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="openEdit(row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- FMEA 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="`FMEA 编辑 - 风险 #${current?.id}`" width="720px">
      <el-form v-if="current" :model="form" label-width="100px">
        <el-form-item label="危险源">
          <div style="color:#606266">{{ current.hazardSource }}</div>
        </el-form-item>
        <el-form-item label="影响">
          <div style="color:#606266">{{ current.harm }}</div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="S 严重度" required>
              <el-input-number v-model="form.severity" :min="1" :max="10" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="O 发生度" required>
              <el-input-number v-model="form.occurrence" :min="1" :max="10" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="D 探测度" required>
              <el-input-number v-model="form.detection" :min="1" :max="10" :step="1" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="RPN（自动）">
          <el-tag :type="rpnTag(calcRpn)" effect="dark" size="large">{{ calcRpn }}</el-tag>
          <span style="margin-left:12px;color:#909399">阈值参考：≥100 高风险</span>
        </el-form-item>
        <el-form-item label="改进措施">
          <el-input v-model="form.actionPlan" type="textarea" :rows="3" placeholder="例如：增加冗余设计、补充自检逻辑..." />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="责任人">
              <el-input v-model="form.actionOwner" placeholder="例如：张工" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="完成期限">
              <el-date-picker v-model="form.actionDueDate" type="date" value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- P3-6 v1.53 修复：残余风险可接受性切换（采取改进措施后，仍可能存在残余风险） -->
        <el-form-item label="残余风险">
          <el-radio-group v-model="form.residualRiskAcceptable">
            <el-radio :value="true">可接受</el-radio>
            <el-radio :value="false">不可接受（需进一步控制）</el-radio>
          </el-radio-group>
          <div style="font-size:12px;color:#909399;margin-top:4px;">
            残余 RPN = 当前 RPN × 措施有效性系数（默认 0.3）；≥ 50 建议标记为不可接受
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const loading = ref(false)
const list = ref<any[]>([])
const rpnThreshold = ref<string>('')
const dialogVisible = ref(false)
const current = ref<any>(null)
const form = reactive<any>({ severity: 1, occurrence: 1, detection: 1, actionPlan: '', actionOwner: '', actionDueDate: null, residualRiskAcceptable: true })

const calcRpn = computed(() => (form.severity || 1) * (form.occurrence || 1) * (form.detection || 1))

const scoreTag = (s: number) => {
  if (!s) return 'info'
  if (s >= 8) return 'danger'
  if (s >= 5) return 'warning'
  return 'success'
}
const rpnTag = (r: number) => {
  if (r >= 200) return 'danger'
  if (r >= 100) return 'warning'
  if (r >= 50) return ''
  return 'success'
}
const actionStatusTag = (s: string) => ({ OPEN: 'danger', IN_PROGRESS: 'warning', COMPLETED: 'success' } as any)[s] || 'info'
const actionStatusLabel = (s: string) => ({ OPEN: '未开始', IN_PROGRESS: '进行中', COMPLETED: '已完成' } as any)[s] || s

const loadList = async () => {
  loading.value = true
  try {
    const params: any = {}
    const t = parseInt(rpnThreshold.value || '')
    if (!isNaN(t) && t > 0) params.rpnThreshold = t
    const res = await request.get('/risk/fmea', { params })
    list.value = res.data?.data || []
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const openEdit = (row: any) => {
  current.value = row
  form.severity = row.severity || 1
  form.occurrence = row.occurrence || 1
  form.detection = row.detection || 1
  form.actionPlan = row.actionPlan || ''
  form.actionOwner = row.actionOwner || ''
  form.actionDueDate = row.actionDueDate || null
  // P3-6 v1.53 修复：读取残余风险字段（默认 true 可接受）
  form.residualRiskAcceptable = row.residualRiskAcceptable !== false
  dialogVisible.value = true
}

const confirmSave = async () => {
  if (!current.value) return
  try {
    await request.post(`/risk/${current.value.id}/fmea`, form)
    ElMessage.success('FMEA 已保存')
    dialogVisible.value = false
    await loadList()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(() => loadList())
</script>

<style scoped>
.fmea-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
</style>
