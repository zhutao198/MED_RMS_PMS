<template>
  <el-dialog
    v-model="visible"
    title="批量导入需求"
    width="860px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item label="目标层级" required>
        <el-radio-group v-model="form.targetLevel">
          <el-radio-button value="URS">父级 (URS)</el-radio-button>
          <el-radio-button value="PRS">子级 (PRS)</el-radio-button>
          <el-radio-button value="SRS">孙级 (SRS)</el-radio-button>
        </el-radio-group>
        <!-- 选中子/孙级时，必须关联一个父/祖父需求，用于建立层级链 -->
        <span v-if="form.targetLevel !== 'URS'" class="level-hint">
          （需指定父级需求以建立层级追溯链）
        </span>
      </el-form-item>

      <el-form-item v-if="form.targetLevel !== 'URS'" label="父级需求" required>
        <el-select
          v-model="form.parentId"
          filterable
          remote
          :remote-method="searchParentRequirements"
          :loading="parentLoading"
          placeholder="按编号/标题搜索父级需求"
          style="width: 100%;"
        >
          <el-option
            v-for="p in parentOptions"
            :key="p.id"
            :label="`${p.requirementNo} - ${p.title}`"
            :value="p.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="项目" required>
        <el-select v-model="form.projectId" placeholder="所属项目" style="width: 100%;">
          <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="导入文件" required>
        <input ref="fileInputRef" type="file" accept=".csv" style="display:none" @change="handleFile" />
        <el-button @click="fileInputRef?.click()">选择 CSV 文件</el-button>
        <span v-if="form.fileName" class="file-name">已选：{{ form.fileName }}（{{ form.previewRows.length }} 行有效数据）</span>
        <el-button v-if="form.fileName" link type="primary" @click="loadTemplate">下载模板</el-button>
      </el-form-item>

      <el-form-item v-if="form.previewRows.length > 0" label="预览（前 5 行）">
        <el-table :data="form.previewRows" border size="small" max-height="240">
          <el-table-column prop="requirementType" label="层级" width="70" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="priority" label="优先级" width="80" />
          <el-table-column prop="riskLevel" label="风险等级" width="80" />
          <el-table-column prop="safetyClass" label="安全等级" width="80" />
        </el-table>
        <div class="preview-tip">共解析 {{ form.allRows.length }} 条有效数据，仅展示前 5 条作为预览</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="form.allRows.length === 0"
        @click="handleConfirm"
      >
        确认导入 {{ form.allRows.length }} 条
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * v1.52 P1-7 修复：需求批量导入独立 Dialog
 * - 目标层级（父/子/孙）+ 父级需求 select 联动
 * - CSV 解析后预览前 5 行
 * - 确认导入按钮批量提交
 */
import { ref, reactive, watch, defineExpose, defineEmits } from 'vue'
import { ElMessage } from 'element-plus'
import { requirementApi, type Requirement } from '../../../api/requirement'
import { projectApi } from '../../../api/project'
import type { Project } from '../../../api/project'

const visible = ref(false)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)
const projectList = ref<Project[]>([])
const parentOptions = ref<Requirement[]>([])
const parentLoading = ref(false)

const form = reactive({
  targetLevel: 'URS' as 'URS' | 'PRS' | 'SRS',
  parentId: null as number | null,
  projectId: null as number | null,
  fileName: '',
  previewRows: [] as Partial<Requirement>[],
  allRows: [] as Partial<Requirement>[],
})

const emit = defineEmits<{
  (e: 'imported'): void
}>()

/** 打开弹窗（外部调用） */
const open = (defaultProjectId?: number) => {
  visible.value = true
  form.targetLevel = 'URS'
  form.parentId = null
  form.fileName = ''
  form.previewRows = []
  form.allRows = []
  if (defaultProjectId) form.projectId = defaultProjectId
  loadProjects()
}

defineExpose({ open })

const loadProjects = async () => {
  try {
    const res = await projectApi.list()
    projectList.value = res.data?.data || []
  } catch (e) {
    console.error('加载项目列表失败', e)
  }
}

/** 远程搜索父级需求（按编号/标题） */
const searchParentRequirements = async (kw: string) => {
  // 层级降级匹配：选 PRS 时父级应为 URS；选 SRS 时父级应为 PRS
  const targetParentType = form.targetLevel === 'PRS' ? 'URS' : 'PRS'
  parentLoading.value = true
  try {
    const res = await requirementApi.list({
      type: targetParentType,
      keyword: kw || undefined,
      page: 0,
      size: 50,
    } as any)
    parentOptions.value = res.data?.data?.records || []
  } catch (e) {
    console.error('搜索父级需求失败', e)
  } finally {
    parentLoading.value = false
  }
}

const handleFile = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const rows = parseCsv(text)
    if (rows.length < 2) {
      ElMessage.warning('文件无有效数据行')
      return
    }
    const header = rows[0].map(h => h.trim())
    const colIdx = (name: string) => header.findIndex(h => h === name)
    const typeIdx = colIdx('层级'), titleIdx = colIdx('标题'),
      prioIdx = colIdx('优先级'), descIdx = colIdx('描述'),
      riskIdx = colIdx('风险等级'), safetyIdx = colIdx('安全等级')
    if (titleIdx < 0) {
      ElMessage.error('文件缺少必填列：标题')
      return
    }
    const parsed: Partial<Requirement>[] = []
    for (let i = 1; i < rows.length; i++) {
      const row = rows[i]
      if (!row || row.every(c => !c.trim())) continue
      parsed.push({
        requirementType: (row[typeIdx] || '').trim() || form.targetLevel,
        title: (row[titleIdx] || '').trim(),
        description: descIdx >= 0 ? (row[descIdx] || '').trim() : '',
        priority: prioIdx >= 0 ? (row[prioIdx] || '').trim() : 'SHOULD',
        riskLevel: riskIdx >= 0 ? (row[riskIdx] || '').trim() : '',
        safetyClass: safetyIdx >= 0 ? (row[safetyIdx] || '').trim() : '',
      })
    }
    if (parsed.length === 0) {
      ElMessage.warning('无有效数据可导入')
      return
    }
    form.fileName = file.name
    form.allRows = parsed
    form.previewRows = parsed.slice(0, 5)
  } catch (err: any) {
    ElMessage.error('文件解析失败：' + (err?.message || '未知错误'))
  } finally {
    target.value = ''
  }
}

const handleConfirm = async () => {
  if (!form.projectId) {
    ElMessage.warning('请选择所属项目')
    return
  }
  if (form.targetLevel !== 'URS' && !form.parentId) {
    ElMessage.warning('请先选择父级需求')
    return
  }
  if (form.allRows.length === 0) {
    ElMessage.warning('无数据可导入')
    return
  }
  submitting.value = true
  try {
    const toCreate: Requirement[] = form.allRows.map(r => ({
      projectId: form.projectId!,
      requirementType: r.requirementType || form.targetLevel,
      title: r.title || '',
      description: r.description || '',
      priority: r.priority || 'SHOULD',
      riskLevel: r.riskLevel || '',
      safetyClass: r.safetyClass || '',
    } as Requirement))
    const res = await requirementApi.createBatch(toCreate)
    const list = (res.data as any)?.data || []
    ElMessage.success(`导入完成：提交 ${toCreate.length} 条，成功 ${list.length} 条`)
    emit('imported')
    handleClose()
  } catch (err: any) {
    ElMessage.error('导入失败：' + (err?.response?.data?.message || err?.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  visible.value = false
}

const loadTemplate = () => {
  // 提供 CSV 模板下载，降低用户填写错误
  const header = ['层级', '标题', '描述', '优先级', '风险等级', '安全等级']
  const sample = [
    [form.targetLevel, '示例标题：用户登录功能', '用户通过用户名密码登录系统', 'MUST', 'Medium', 'B'],
  ]
  const lines = [header.join(','), ...sample.map(r => r.join(','))]
  const csv = '﻿' + lines.join('\r\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `requirement-import-template-${form.targetLevel}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

// 切换目标层级时清空父级选择，避免残留不匹配的父级
watch(() => form.targetLevel, () => {
  form.parentId = null
  parentOptions.value = []
})

// CSV 解析（支持引号包裹与换行转义）
const parseCsv = (text: string): string[][] => {
  const rows: string[][] = []
  let row: string[] = []
  let cur = ''
  let inQuote = false
  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (inQuote) {
      if (ch === '"' && text[i + 1] === '"') { cur += '"'; i++ }
      else if (ch === '"') { inQuote = false }
      else { cur += ch }
    } else {
      if (ch === '"') inQuote = true
      else if (ch === ',') { row.push(cur); cur = '' }
      else if (ch === '\n') { row.push(cur); rows.push(row); row = []; cur = '' }
      else if (ch === '\r') { /* skip */ }
      else { cur += ch }
    }
  }
  if (cur !== '' || row.length > 0) { row.push(cur); rows.push(row) }
  return rows
}
</script>

<style scoped>
.level-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
.file-name {
  margin-left: 12px;
  font-size: 13px;
  color: #67c23a;
}
.preview-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
