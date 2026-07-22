<template>
  <el-dialog
    v-model="visible"
    title="批量导入需求（R208 v1.65 · FR-1.13 Excel 导入）"
    width="860px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="目标层级" required>
        <el-radio-group v-model="form.targetLevel">
          <el-radio-button value="URS">URS</el-radio-button>
          <el-radio-button value="PRS">PRS</el-radio-button>
          <el-radio-button value="SRS">SRS</el-radio-button>
          <el-radio-button value="DRS">DRS</el-radio-button>
        </el-radio-group>
        <span class="level-hint">R208 支持四层批量导入（≤500 行/次）</span>
      </el-form-item>

      <el-form-item label="项目" required>
        <ProjectSelector v-model="form.projectId" :sync-to-store="false" @change="onProjectChange" />
      </el-form-item>

      <el-form-item label="模板下载">
        <el-button link type="primary" @click="loadTemplate">
          📥 下载 {{ form.targetLevel }} 模板 (.xlsx)
        </el-button>
        <span class="template-tip">含字段说明 + 示例数据 + 枚举下拉</span>
      </el-form-item>

      <el-form-item label="导入文件" required>
        <input ref="fileInputRef" type="file" accept=".xlsx" style="display:none" @change="handleFile" />
        <el-button @click="fileInputRef?.click()">选择 Excel 文件 (.xlsx)</el-button>
        <span v-if="form.fileName" class="file-name">已选：{{ form.fileName }}（{{ form.file.size }} KB）</span>
      </el-form-item>

      <!-- R208: 部分成功错误展示 -->
      <el-form-item v-if="importResult" label="导入结果">
        <el-alert :type="importResult.failed.length === 0 ? 'success' : 'warning'" :closable="false">
          <template #title>
            <strong>共 {{ importResult.total }} 条</strong>，
            成功 <span style="color:#67c23a">{{ importResult.success }}</span> 条，
            失败 <span style="color:#f56c6c">{{ importResult.failed.length }}</span> 条
          </template>
          <div v-if="importResult.failed.length > 0" class="failed-list">
            <el-table :data="importResult.failed" border size="small" max-height="200" style="margin-top:8px">
              <el-table-column prop="row" label="行号" width="70" />
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column label="错误" min-width="280">
                <template #default="{ row }">
                  <el-tag v-for="e in row.errors" :key="e" type="danger" size="small" style="margin: 2px;">{{ e }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-alert>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!form.file"
        @click="handleConfirm"
      >
        🚀 确认导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * R208 v1.65: 四层需求 Excel 批量导入（FR-1.13）
 *
 * 改造要点（v1.52 CSV → v1.65 Excel）：
 *  - 接受 .xlsx 文件（不再是 .csv）
 *  - 走后端 /requirements/excel/import/{type}（不再前端 CSV 解析）
 *  - 显示后端返回的 failed 详情（行号 + 错误列表）
 *  - 模板下载走后端 /requirements/excel/template/{type}（带枚举下拉）
 *  - 支持四层（URS/PRS/SRS/DRS），原版只支持 URS/PRS/SRS
 *  - 不再要求选 parentId：使用上游追溯编号列（upstreamNos）批量建立关系
 */
import { useProject } from '@/composables/useProject'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { ref, reactive, watch, defineExpose, defineEmits } from 'vue'
import { ElMessage } from 'element-plus'
import { requirementApi } from '../../../api/requirement'

interface ImportResult {
  total: number
  success: number
  failed: Array<{ row: number; title: string; errors: string[] }>
  createdIds: number[]
  packageType: string
}

const visible = ref(false)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const form = reactive({
  targetLevel: 'URS' as 'URS' | 'PRS' | 'SRS' | 'DRS',
  projectId: null as number | null,
  fileName: '',
  file: null as File | null,
})

const importResult = ref<ImportResult | null>(null)

const emit = defineEmits<{
  (e: 'imported'): void
}>()

/** 打开弹窗（外部调用） */
const open = (defaultProjectId?: number) => {
  visible.value = true
  form.targetLevel = 'URS'
  form.fileName = ''
  form.file = null
  importResult.value = null
  if (defaultProjectId) form.projectId = defaultProjectId
  ensureLoaded()
}

defineExpose({ open })

const handleFile = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('请选择 .xlsx 格式的 Excel 文件')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 10 MB')
    return
  }
  form.fileName = file.name
  form.file = file
  importResult.value = null
}

const handleConfirm = async () => {
  if (!form.projectId) {
    ElMessage.warning('请选择所属项目')
    return
  }
  if (!form.file) {
    ElMessage.warning('请先选择 Excel 文件')
    return
  }
  submitting.value = true
  importResult.value = null
  try {
    const res = await requirementApi.importExcel(form.targetLevel, form.projectId, form.file)
    const result = (res.data as any)?.data as ImportResult
    importResult.value = result
    if (result.failed.length === 0) {
      ElMessage.success(`导入完成：${result.success}/${result.total} 条全部成功`)
      emit('imported')
    } else {
      ElMessage.warning(`部分成功：${result.success} 成功 / ${result.failed.length} 失败`)
    }
  } catch (err: any) {
    ElMessage.error('导入失败：' + (err?.response?.data?.message || err?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  visible.value = false
}

/** 下载模板（走后端端点，含枚举下拉） */
const loadTemplate = async () => {
  try {
    const res = await requirementApi.downloadExcelTemplate(form.targetLevel)
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `需求模板_${form.targetLevel}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('模板已下载')
  } catch (e: any) {
    ElMessage.error('模板下载失败：' + (e?.message || '未知错误'))
  }
}

// 切换目标层级时清空已选文件（避免层级与文件内容不匹配）
watch(() => form.targetLevel, () => {
  form.fileName = ''
  form.file = null
  importResult.value = null
})

const { ensureLoaded } = useProject()
</script>

<style scoped>
.level-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
.template-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
.file-name {
  margin-left: 12px;
  font-size: 13px;
  color: #67c23a;
}
.failed-list {
  margin-top: 4px;
}
</style>
