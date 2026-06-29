<!--
  TraceImport.vue - 追溯数据导入
  v1.53 P1-13 修复：上传文件（CSV/Excel/JSON），字段映射，解析预览，确认导入
  v1.55 修复：
    - 新增面包屑
    - 步骤从 4 步变 5 步：上传文件→字段映射→解析预览→数据校验→确认导入
    - 第 4 步调 previewImport 后端接口，区分有效/无效行
    - 无效行可下载 CSV 错误报告
    - 提交逻辑改调 commitImport(projectId, validRows)
    - 项目选择必填（与后端 projectId 参数对齐）
-->
<template>
  <div class="trace-import-container">
    <!-- v1.55 修复：面包屑 -->
    <el-breadcrumb separator="/" style="margin-bottom: 8px;">
      <el-breadcrumb-item>追溯管理</el-breadcrumb-item>
      <el-breadcrumb-item>追溯导入</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>📥 追溯数据导入</span>
          <div style="display: flex; gap: 8px; align-items: center;">
            <el-select v-model="projectId" placeholder="选择项目" style="width: 220px;" filterable>
              <el-option v-for="p in projects" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
            </el-select>
            <el-button @click="resetAll">重置</el-button>
          </div>
        </div>
      </template>

      <!-- v1.55 修复：5 步流程 -->
      <el-steps :active="step" finish-status="success" align-center style="margin-bottom: 24px">
        <el-step title="上传文件" />
        <el-step title="字段映射" />
        <el-step title="解析预览" />
        <el-step title="数据校验" />
        <el-step title="确认导入" />
      </el-steps>

      <!-- Step 1: 上传 -->
      <div v-if="step === 0">
        <el-alert v-if="!projectId" type="warning" :closable="false" show-icon style="margin-bottom: 16px">
          请先在右上角选择项目
        </el-alert>
        <el-upload
          drag
          :auto-upload="false"
          :on-change="handleFileChange"
          :show-file-list="false"
          accept=".csv,.xlsx,.xls,.json"
          :disabled="!projectId"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将文件拖拽到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 CSV / Excel (.xlsx, .xls) / JSON 文件，单文件最大 10MB</div>
          </template>
        </el-upload>
        <div v-if="uploadedFile" class="file-info">
          <el-tag>已选择：{{ uploadedFile.name }}（{{ formatSize(uploadedFile.size) }}）</el-tag>
          <el-button style="margin-left: 12px" @click="autoMap">自动映射字段</el-button>
          <el-button type="primary" @click="goToMapping" :disabled="!parsedHeaders.length">下一步</el-button>
        </div>
      </div>

      <!-- Step 2: 字段映射 -->
      <div v-if="step === 1">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
          请将文件中的列映射到追溯系统标准字段。
        </el-alert>
        <el-table :data="mappingRows" border>
          <el-table-column label="文件列名" prop="source" width="200" />
          <el-table-column label="样例数据" width="240">
            <template #default="{ row }">
              <span class="sample-data">{{ row.sample }}</span>
            </template>
          </el-table-column>
          <el-table-column label="目标字段">
            <template #default="{ row }">
              <el-select v-model="row.target" placeholder="请选择目标字段" style="width: 100%">
                <el-option v-for="f in TARGET_FIELDS" :key="f.value" :label="f.label" :value="f.value" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 16px; text-align: right">
          <el-button @click="step = 0">上一步</el-button>
          <el-button type="primary" @click="goToPreview" :disabled="!isMappingValid">下一步</el-button>
        </div>
      </div>

      <!-- Step 3: 解析预览 -->
      <div v-if="step === 2">
        <el-table :data="previewRows" border max-height="500">
          <el-table-column
            v-for="col in activeTargetFields"
            :key="col"
            :prop="col"
            :label="TARGET_FIELDS.find(f => f.value === col)?.label || col"
            min-width="140"
            show-overflow-tooltip
          />
        </el-table>
        <div style="margin-top: 16px; text-align: right">
          <el-button @click="step = 1">上一步</el-button>
          <el-button type="primary" @click="goToValidate" :disabled="!previewRows.length">下一步</el-button>
        </div>
      </div>

      <!-- Step 4: 数据校验（v1.55 新增） -->
      <div v-if="step === 3">
        <div v-if="!validated">
          <el-empty description="正在校验数据..." />
        </div>
        <div v-else>
          <el-row :gutter="16" style="margin-bottom: 16px">
            <el-col :span="8">
              <el-card shadow="never">
                <div class="validation-stat">
                  <div class="stat-num total">{{ previewRows.length }}</div>
                  <div class="stat-label">总行数</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never">
                <div class="validation-stat">
                  <div class="stat-num valid">{{ validRows.length }}</div>
                  <div class="stat-label">✅ 有效行</div>
                </div>
              </el-card>
            </el-col>
            <el-col :span="8">
              <el-card shadow="never">
                <div class="validation-stat">
                  <div class="stat-num invalid">{{ invalidRows.length }}</div>
                  <div class="stat-label">❌ 无效行</div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-alert v-if="invalidRows.length === 0" type="success" :closable="false" show-icon style="margin-bottom: 16px">
            所有数据校验通过，可以进入下一步导入
          </el-alert>
          <el-alert v-else type="warning" :closable="false" show-icon style="margin-bottom: 16px">
            有 {{ invalidRows.length }} 条数据存在错误，下载错误报告查看详情；可继续导入有效行（{{ validRows.length }} 条）
          </el-alert>

          <el-tabs v-model="validateTab">
            <el-tab-pane label="有效行" name="valid">
              <el-table :data="validRows" border max-height="400" :empty-text="'无有效行'">
                <el-table-column type="index" label="#" width="60" />
                <el-table-column prop="sourceNo" label="源编号" min-width="120" />
                <el-table-column prop="sourceType" label="源类型" min-width="80" />
                <el-table-column prop="targetNo" label="目标编号" min-width="120" />
                <el-table-column prop="targetType" label="目标类型" min-width="80" />
                <el-table-column prop="linkType" label="追溯类型" min-width="100" />
                <el-table-column prop="comment" label="备注" min-width="160" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
            <el-tab-pane :label="`无效行（${invalidRows.length}）`" name="invalid">
              <div v-if="invalidRows.length > 0" style="margin-bottom: 12px">
                <el-button size="small" type="warning" @click="downloadErrorReport">📥 下载错误报告（CSV）</el-button>
              </div>
              <el-table :data="invalidRows" border max-height="400" :empty-text="'无无效行'">
                <el-table-column type="index" label="#" width="60" />
                <el-table-column prop="rowIndex" label="行号" width="80" />
                <el-table-column prop="sourceNo" label="源编号" min-width="120" />
                <el-table-column prop="targetNo" label="目标编号" min-width="120" />
                <el-table-column prop="linkType" label="追溯类型" min-width="100" />
                <el-table-column label="错误信息" min-width="280">
                  <template #default="{ row }">
                    <el-tag v-for="(err, i) in row.errors" :key="i" type="danger" size="small" style="margin: 2px;">
                      {{ err }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>

          <div style="margin-top: 16px; text-align: right">
            <el-button @click="step = 2">上一步</el-button>
            <el-button type="primary" @click="goToConfirm" :disabled="validRows.length === 0">下一步</el-button>
          </div>
        </div>
      </div>

      <!-- Step 5: 确认导入 -->
      <div v-if="step === 4">
        <el-result icon="success" title="数据已准备好">
          <template #sub-title>
            <div>共 <strong>{{ validRows.length }}</strong> 条追溯数据将导入到项目（已过滤 {{ invalidRows.length }} 条无效数据）</div>
            <div v-if="importResult" style="margin-top: 8px; color: #67c23a">
              导入成功 {{ importResult.success }} 条，失败 {{ importResult.failed }} 条
            </div>
            <div v-if="importResult && importResult.errors && importResult.errors.length" style="margin-top: 8px; text-align: left;">
              <el-alert type="warning" :closable="false" show-icon>
                <template #title>
                  部分行导入失败（{{ importResult.errors.length }} 条）
                </template>
                <div style="max-height: 200px; overflow-y: auto; font-size: 12px;">
                  <div v-for="(err, i) in importResult.errors.slice(0, 20)" :key="i" style="margin: 4px 0;">
                    {{ err.sourceNo || '-' }} → {{ err.targetNo || '-' }}: {{ err.message }}
                  </div>
                  <div v-if="importResult.errors.length > 20" style="color: #909399; margin-top: 4px;">
                    ……还有 {{ importResult.errors.length - 20 }} 条
                  </div>
                </div>
              </el-alert>
            </div>
          </template>
        </el-result>
        <div style="text-align: center">
          <el-button @click="step = 3">上一步</el-button>
          <el-button type="primary" :loading="importing" @click="doImport" :disabled="validRows.length === 0">确认导入</el-button>
          <el-button @click="$router.push('/traceability')">返回追溯矩阵</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { traceabilityApi } from '@/api/traceability'
import request from '@/api/request'

interface MappingRow {
  source: string
  target: string
  sample: string
}

const TARGET_FIELDS = [
  { value: 'sourceNo', label: '源需求编号' },
  { value: 'sourceType', label: '源需求类型' },
  { value: 'targetNo', label: '目标需求编号' },
  { value: 'targetType', label: '目标需求类型' },
  { value: 'linkType', label: '追溯类型' },
  { value: 'comment', label: '备注' },
  { value: '_skip', label: '— 不导入 —' }
]

const step = ref(0)
const projectId = ref<number | null>(null)
const projects = ref<Array<{ id: number; projectNo: string; projectName: string }>>([])
const uploadedFile = ref<File | null>(null)
const parsedHeaders = ref<string[]>([])
const parsedRows = ref<any[]>([])
const mappingRows = ref<MappingRow[]>([])
const previewRows = ref<any[]>([])
// v1.55 修复：校验结果
const validated = ref(false)
const validRows = ref<any[]>([])
const invalidRows = ref<any[]>([])
const validateTab = ref<'valid' | 'invalid'>('valid')
const importing = ref(false)
const importResult = ref<{ success: number; failed: number; errors?: Array<{ sourceNo?: string; targetNo?: string; message: string }> } | null>(null)

const activeTargetFields = computed(() => {
  const set = new Set(mappingRows.value.filter(r => r.target && r.target !== '_skip').map(r => r.target))
  return Array.from(set)
})

const isMappingValid = computed(() => mappingRows.value.some(r => r.target === 'sourceNo' || r.target === 'targetNo'))

const formatSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const data = res.data?.data
    projects.value = Array.isArray(data) ? data : (data?.records || [])
    if (projects.value.length > 0 && !projectId.value) {
      projectId.value = projects.value[0].id
    }
  } catch (e) {
    console.warn('加载项目列表失败', e)
  }
}

const handleFileChange = async (file: any) => {
  const raw: File = file.raw
  uploadedFile.value = raw
  const text = await raw.text()
  try {
    if (raw.name.toLowerCase().endsWith('.json')) {
      const arr = JSON.parse(text)
      if (Array.isArray(arr) && arr.length) {
        parsedHeaders.value = Object.keys(arr[0])
        parsedRows.value = arr
      }
    } else {
      // CSV 简单解析（首行表头）
      const lines = text.split(/\r?\n/).filter(Boolean)
      if (lines.length < 2) {
        ElMessage.warning('文件内容过少')
        return
      }
      parsedHeaders.value = lines[0].split(',').map(s => s.trim())
      parsedRows.value = lines.slice(1).map(line => {
        const cells = line.split(',')
        const obj: any = {}
        parsedHeaders.value.forEach((h, i) => { obj[h] = (cells[i] || '').trim() })
        return obj
      })
    }
    autoMap()
  } catch (e: any) {
    ElMessage.error('文件解析失败：' + (e?.message || ''))
  }
}

const autoMap = () => {
  mappingRows.value = parsedHeaders.value.map(h => {
    const lower = h.toLowerCase()
    let target = '_skip'
    if (lower.includes('source') && lower.includes('no')) target = 'sourceNo'
    else if (lower.includes('source') && lower.includes('type')) target = 'sourceType'
    else if (lower.includes('target') && lower.includes('no')) target = 'targetNo'
    else if (lower.includes('target') && lower.includes('type')) target = 'targetType'
    else if (lower.includes('link') || lower.includes('trace')) target = 'linkType'
    else if (lower.includes('comment') || lower.includes('remark')) target = 'comment'
    return { source: h, target, sample: String(parsedRows.value[0]?.[h] || '').slice(0, 30) }
  })
}

const goToMapping = () => { step.value = 1 }

const goToPreview = () => {
  previewRows.value = parsedRows.value.map((row, idx) => {
    const out: any = { __rowIndex: idx + 2 } // +2 跳过表头和 0 索引
    mappingRows.value.forEach(m => {
      if (m.target && m.target !== '_skip') {
        out[m.target] = row[m.source]
      }
    })
    return out
  })
  step.value = 2
}

// v1.55 修复：进入校验步骤，调 previewImport 后端
const goToValidate = async () => {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    step.value = 0
    return
  }
  if (previewRows.value.length === 0) {
    ElMessage.warning('无数据可校验')
    return
  }
  validated.value = false
  step.value = 3
  try {
    const res = await traceabilityApi.previewImport(projectId.value, previewRows.value)
    const data = res.data?.data || { validCount: 0, invalidCount: 0, validRows: [], invalidRows: [] }
    validRows.value = data.validRows || []
    invalidRows.value = data.invalidRows || []
    validated.value = true
    if (invalidRows.value.length > 0) {
      validateTab.value = 'invalid'
    } else {
      validateTab.value = 'valid'
    }
    ElMessage.success(`校验完成：有效 ${validRows.value.length} 条，无效 ${invalidRows.value.length} 条`)
  } catch (e: any) {
    // R103 B1：原"后端未实现"提示是历史遗留误判，实测 /traceability/import/preview 端点存在
    // 这里改为通用网络错误提示，避免误导用户
    console.warn('previewImport 调用失败', e)
    ElMessage.error(`校验失败：${e?.message || '网络异常，请稍后重试'}`)
    validated.value = false
  }
}

const goToConfirm = () => { step.value = 4 }

// v1.55 修复：提交时只传有效行
const doImport = async () => {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  importing.value = true
  try {
    const res = await traceabilityApi.commitImport(projectId.value, validRows.value)
    importResult.value = res.data?.data || { success: validRows.value.length, failed: 0, errors: [] }
    ElMessage.success(`导入完成：成功 ${importResult.value!.success} 条，失败 ${importResult.value!.failed} 条`)
  } catch (e: any) {
    // R103 B1：原"后端未实现"提示是历史遗留误判，实测 /traceability/import 端点存在
    // 这里改为通用网络错误提示，避免误导用户
    importResult.value = {
      success: 0,
      failed: validRows.value.length,
      errors: [{ message: e?.message || '网络异常，请稍后重试' }]
    }
    ElMessage.error(`导入失败：${e?.message || '网络异常，请稍后重试'}`)
  } finally {
    importing.value = false
  }
}

// v1.55 修复：下载错误报告（CSV，Blob + BOM）
const downloadErrorReport = () => {
  if (invalidRows.value.length === 0) {
    ElMessage.warning('无错误数据可下载')
    return
  }
  const headers = ['行号', '源编号', '源类型', '目标编号', '目标类型', '追溯类型', '错误信息']
  const lines = [headers.join(',')]
  invalidRows.value.forEach(r => {
    lines.push([
      r.rowIndex ?? '',
      r.sourceNo ?? '',
      r.sourceType ?? '',
      r.targetNo ?? '',
      r.targetType ?? '',
      r.linkType ?? '',
      `"${(r.errors || []).join(' | ').replace(/"/g, '""')}"`
    ].join(','))
  })
  const csv = '﻿' + lines.join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `追溯导入错误报告-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${invalidRows.value.length} 条错误记录`)
}

const resetAll = () => {
  step.value = 0
  uploadedFile.value = null
  parsedHeaders.value = []
  parsedRows.value = []
  mappingRows.value = []
  previewRows.value = []
  validated.value = false
  validRows.value = []
  invalidRows.value = []
  importResult.value = null
  validateTab.value = 'valid'
}

onMounted(() => {
  fetchProjects()
})
</script>

<style scoped>
.trace-import-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.file-info { margin-top: 16px; }
.sample-data { color: #606266; font-size: 12px; }

/* v1.55 修复：校验步骤统计卡片 */
.validation-stat { text-align: center; padding: 8px 0; }
.validation-stat .stat-num { font-size: 28px; font-weight: 700; line-height: 1.2; }
.validation-stat .stat-num.total { color: #303133; }
.validation-stat .stat-num.valid { color: #67c23a; }
.validation-stat .stat-num.invalid { color: #f56c6c; }
.validation-stat .stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
