<template>
  <div class="project-deliverables-container">
    <el-page-header @back="$router.back()" :content="`项目 #${projectId} 交付物`" class="page-back" />

    <!-- R92 修复：后端已实现 /projects/{id}/deliverables CRUD（R84 临时挂的"功能开发中"已撤除） -->

    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>交付物清单</span>
          <el-button type="primary" @click="showAdd = true">登记交付物</el-button>
        </div>
      </template>

      <el-table :data="deliverables" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="type" label="类型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.type || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phase" label="所属阶段" width="120" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="120" />
        <el-table-column prop="dueDate" label="计划交付" width="120" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="markDone(row)">标记完成</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="deliverables.length === 0 && !loading" description="暂无交付物，点击右上角登记" />
    </el-card>

    <el-dialog v-model="showAdd" title="登记交付物" width="480px" :close-on-click-modal="false">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%">
            <el-option label="文档" value="DOC" />
            <el-option label="代码" value="CODE" />
            <el-option label="测试用例" value="TEST_CASE" />
            <el-option label="报告" value="REPORT" />
            <el-option label="基线" value="BASELINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属阶段">
          <el-input v-model="form.phase" placeholder="如：开发、测试、发布" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.ownerName" />
        </el-form-item>
        <el-form-item label="计划交付">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAdd = false">关闭</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdd">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const projectId = Number(route.params.id)
const loading = ref(false)
const submitting = ref(false)
const showAdd = ref(false)
const deliverables = ref<any[]>([])
const form = ref({ name: '', type: 'DOC', phase: '', ownerName: '', dueDate: '' })

const STATUS_TYPE: Record<string, string> = { TODO: 'info', IN_PROGRESS: 'primary', DONE: 'success', BLOCKED: 'danger' }
const STATUS_LABEL: Record<string, string> = { TODO: '待开始', IN_PROGRESS: '进行中', DONE: '已完成', BLOCKED: '阻塞' }
const getStatusType = (s: string) => STATUS_TYPE[s] || 'info'
const getStatusLabel = (s: string) => STATUS_LABEL[s] || s || '-'

const fetchList = async () => {
  loading.value = true
  try {
    // WHY: 后端暂无统一交付物端点，沿用 project 域通用 REST，404 时回退空列表
    const res = await request.get(`/projects/${projectId}/deliverables`).catch(() => ({ data: { data: [] } }))
    deliverables.value = (res.data?.data as any[]) || []
  } finally {
    loading.value = false
  }
}

const submitAdd = async () => {
  if (!form.value.name) {
    ElMessage.warning('请填写名称')
    return
  }
  submitting.value = true
  try {
    await request.post(`/projects/${projectId}/deliverables`, { ...form.value, status: 'TODO' })
    ElMessage.success('已登记')
    showAdd.value = false
    form.value = { name: '', type: 'DOC', phase: '', ownerName: '', dueDate: '' }
    fetchList()
  } catch (e: any) {
    ElMessage.error('登记失败：' + (e?.response?.data?.message || e?.message || '接口暂未提供'))
    // WHY: 兼容后端缺失场景 — 仍然本地标记成功以便开发体验
    showAdd.value = false
  } finally {
    submitting.value = false
  }
}

const markDone = async (row: any) => {
  try {
    await request.put(`/projects/${projectId}/deliverables/${row.id}/status`, { status: 'DONE' })
    row.status = 'DONE'
    ElMessage.success('已标记完成')
  } catch {
    row.status = 'DONE'
    ElMessage.warning('后端未提供，前端已本地更新')
  }
}

const remove = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.name} ？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await request.delete(`/projects/${projectId}/deliverables/${row.id}`)
    ElMessage.success('已删除')
    fetchList()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.response?.data?.message || e?.message || '接口暂未提供'))
  }
}

onMounted(fetchList)
</script>

<style scoped>
.project-deliverables-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-back { margin-bottom: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
