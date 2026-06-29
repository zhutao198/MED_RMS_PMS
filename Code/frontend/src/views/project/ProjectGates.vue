<template>
  <div class="project-gates-container">
    <el-page-header @back="$router.back()" :content="`项目 #${projectId} DCP 门控`" class="page-back" />

    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>门控清单（独立视图）</span>
          <el-button type="primary" @click="showAdd = true">新增门控</el-button>
        </div>
      </template>

      <el-table :data="gates" border stripe>
        <el-table-column prop="gateNo" label="编号" width="80" />
        <el-table-column prop="gateName" label="名称" min-width="160" />
        <el-table-column prop="gateType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.gateType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plannedDate" label="计划" width="120" />
        <el-table-column prop="actualDate" label="实际" width="120" />
        <!-- v1.53 P1-21：签署人 + 签署状态列（与电子签名系统打通） -->
        <el-table-column label="签署人" width="140">
          <template #default="{ row }">
            <span v-if="row.signerName">{{ row.signerName }}</span>
            <span v-else class="text-muted">未指定</span>
          </template>
        </el-table-column>
        <el-table-column label="签署状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getSignStatusType(row.signStatus)" size="small">
              {{ getSignStatusLabel(row.signStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewGate(row)">详情</el-button>
            <el-button size="small" text type="success" @click="passGate(row)" :disabled="row.status === 'COMPLETED'">通过</el-button>
            <el-button size="small" text type="danger" @click="failGate(row)" :disabled="row.status === 'COMPLETED'">拒签</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="gates.length === 0 && !loading" description="暂无门控" />
    </el-card>

    <el-dialog v-model="showAdd" title="新增门控" width="480px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="编号" required>
          <el-input-number v-model="form.gateNo" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.gateName" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.gateType" style="width:100%">
            <el-option label="DCP1 概念" value="DCP1" />
            <el-option label="DCP2 计划" value="DCP2" />
            <el-option label="DCP3 开发" value="DCP3" />
            <el-option label="DCP4 验证" value="DCP4" />
            <el-option label="DCP5 发布" value="DCP5" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="form.plannedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="签署人ID">
          <el-input-number v-model="form.gateReviewerId" :min="1" style="width:100%" />
          <span class="form-tip">关联到电子签名系统</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ipdGateApi, type IpdGate } from '@/api/project'

const route = useRoute()
const projectId = Number(route.params.id)
const loading = ref(false)
const submitting = ref(false)
const showAdd = ref(false)
const gates = ref<any[]>([])
const form = ref<any>({ gateNo: 1, gateName: '', gateType: 'DCP1', plannedDate: '', gateReviewerId: null })

// v1.53 P1-21：签署状态映射
const SIGN_STATUS: Record<string, { type: string; label: string }> = {
  UNSIGNED: { type: 'info', label: '未签署' },
  SIGNED:   { type: 'success', label: '已签署' },
  REJECTED: { type: 'danger', label: '已拒签' },
}
const getSignStatusType = (s?: string) => (s && SIGN_STATUS[s]?.type) || 'info'
const getSignStatusLabel = (s?: string) => (s && SIGN_STATUS[s]?.label) || '未签署'

const fetchGates = async () => {
  loading.value = true
  try {
    const res = await ipdGateApi.listByProject(projectId)
    gates.value = (res.data.data || []).map((g: any) => ({
      ...g,
      // WHY: 后端字段命名兼容 — gateReviewerId/userId 映射为 signerId，signerName 来自 user 关联
      signerId: g.gateReviewerId ?? g.userId ?? g.signerId ?? null,
      signerName: g.signerName ?? g.gateReviewerName ?? g.reviewer ?? '-',
      signStatus: g.signStatus ?? (g.status === 'COMPLETED' ? 'SIGNED' : g.status === 'REJECTED' ? 'REJECTED' : 'UNSIGNED'),
    }))
  } catch (e: any) {
    ElMessage.error('加载门控失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

const submitAdd = async () => {
  if (!form.value.gateName) { ElMessage.warning('请填写名称'); return }
  submitting.value = true
  try {
    await ipdGateApi.create({ projectId, ...form.value, status: 'PENDING' } as any)
    ElMessage.success('已创建')
    showAdd.value = false
    form.value = { gateNo: 1, gateName: '', gateType: 'DCP1', plannedDate: '', gateReviewerId: null }
    fetchGates()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    submitting.value = false
  }
}

const viewGate = async (row: any) => {
  try {
    const res = await fetch(`/gantt/gate/${row.id}/check`).catch(() => null)
    const data = res ? await (res as Response).json().catch(() => null) : null
    if (data?.data) {
      ElMessageBox.alert(`门控：${row.gateName}\n签署状态：${getSignStatusLabel(row.signStatus)}\n计划：${row.plannedDate || '-'}`, '门控详情', { type: 'info' })
    } else {
      ElMessage.info(`门控：${row.gateName}（${row.gateType}）— ${getSignStatusLabel(row.signStatus)}`)
    }
  } catch {
    ElMessage.info(`门控：${row.gateName}（${row.gateType}）— ${getSignStatusLabel(row.signStatus)}`)
  }
}

const passGate = async (row: any) => {
  if (!row.id) return
  try {
    await ipdGateApi.passGate(row.id, 'PASS', '门控通过')
    ElMessage.success('已通过')
    fetchGates()
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e?.response?.data?.message || e?.message))
  }
}

const failGate = async (row: any) => {
  if (!row.id) return
  try {
    const { value: comment } = await ElMessageBox.prompt('请输入拒签原因：', '拒签', { confirmButtonText: '拒签', cancelButtonText: '取消' })
    await ipdGateApi.failGate(row.id, comment || '未通过')
    ElMessage.success('已拒签')
    fetchGates()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('操作失败：' + (e?.response?.data?.message || e?.message))
  }
}

onMounted(fetchGates)
</script>

<style scoped>
.project-gates-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.text-muted { color: #909399; font-size: 12px; }
.form-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
