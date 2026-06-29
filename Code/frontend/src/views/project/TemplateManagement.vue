<template>
  <div class="tpl-container">
    <div class="page-title">
      <h2>行业合规模板（FR-1.9）</h2>
      <div>
        <el-button @click="loadTemplates">刷新</el-button>
        <el-button type="primary" @click="showCreate = true">+ 自定义模板</el-button>
      </div>
    </div>

    <div class="banner">
      <el-alert title="内置 4 个行业合规模板 + 支持自定义" type="info" show-icon :closable="false">
        <template #default>
          点击模板卡片"应用到项目"按钮，可将该模板的配置（DCP门限/评审流程/法规关联/证据包）一键应用到指定项目。
        </template>
      </el-alert>
    </div>

    <el-row :gutter="16" class="tpl-grid">
      <el-col v-for="tpl in templates" :key="tpl.id" :xs="24" :sm="12" :md="8" :lg="6">
        <el-card class="tpl-card" :class="{ preset: tpl.type === 'PRESET' }" shadow="hover">
          <template #header>
            <div class="card-header">
              <div>
                <div class="tpl-name">{{ tpl.name }}</div>
                <el-tag :type="tpl.type === 'PRESET' ? 'success' : 'warning'" size="small">
                  {{ tpl.type === 'PRESET' ? '系统预设' : '自定义' }}
                </el-tag>
              </div>
              <div class="tpl-code">{{ tpl.code }}</div>
            </div>
          </template>
          <div class="tpl-desc">{{ tpl.description }}</div>
          <div class="tpl-config" v-if="tpl.configJson">
            <div class="config-title">配置：</div>
            <div v-for="(items, key) in parseConfig(tpl.configJson)" :key="key" class="config-row">
              <span class="config-key">{{ configLabel(key) }}：</span>
              <span class="config-val">{{ formatList(items) }}</span>
            </div>
          </div>
          <template #footer>
            <div class="card-footer">
              <el-button size="small" type="primary" plain @click="openApply(tpl)">应用到项目</el-button>
              <el-button v-if="tpl.type === 'CUSTOM'" size="small" @click="openEdit(tpl)">编辑</el-button>
              <el-button v-if="tpl.type === 'CUSTOM'" size="small" type="danger" plain @click="remove(tpl)">删除</el-button>
            </div>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 应用模板对话框 -->
    <el-dialog v-model="showApply" title="应用合规模板到项目" width="500px">
      <el-form label-width="100px">
        <el-form-item label="模板">
          <el-input :value="applyForm.templateName" disabled />
        </el-form-item>
        <el-form-item label="选择项目" required>
          <el-select v-model="applyForm.projectId" placeholder="选择项目" style="width:100%;">
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon>
          模板的 DCP 门限 / 评审流程 / 法规关联 / 证据包配置将自动应用到所选项目。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="showApply = false">取消</el-button>
        <el-button type="primary" :loading="applying" @click="doApply">应用</el-button>
      </template>
    </el-dialog>

    <!-- 新建/编辑自定义模板 -->
    <el-dialog v-model="showCreate" :title="editingId ? '编辑自定义模板' : '新建自定义模板'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="模板编号" required>
          <el-input v-model="form.code" :disabled="!!editingId" placeholder="CUSTOM_xxx" />
        </el-form-item>
        <el-form-item label="模板名称" required>
          <el-input v-model="form.name" placeholder="如：院内自研软件模板" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="配置 JSON">
          <el-input v-model="form.configJson" type="textarea" :rows="6" placeholder='{"defaultUrsFields":{"regulatoryTarget":"内部"},"reviewProcess":["peer-review","final-review"]}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

interface Template {
  id: number
  code: string
  name: string
  type: string
  description: string
  configJson: string
  isActive: boolean
}

interface Project {
  id: number
  projectName: string
}

const templates = ref<Template[]>([])
const projectList = ref<Project[]>([])
const showApply = ref(false)
const showCreate = ref(false)
const applying = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const applyForm = ref({ templateId: 0, templateName: '', projectId: null as number | null })
const form = ref({ code: '', name: '', description: '', configJson: '{}' })

const configLabels: Record<string, string> = {
  defaultUrsFields: 'URS 预填',
  reviewProcess: '评审流程',
  dcpGates: 'DCP 门限',
  evidencePackage: '证据包',
  regulationRefs: '法规引用',
}

const configLabel = (key: string) => configLabels[key] || key

const parseConfig = (json: string) => {
  try {
    return JSON.parse(json)
  } catch {
    return {}
  }
}

const formatList = (v: any) => {
  if (Array.isArray(v)) return v.join(' / ')
  if (typeof v === 'object') return Object.entries(v).map(([k, vv]) => `${k}=${vv}`).join(', ')
  return String(v)
}

const loadTemplates = async () => {
  try {
    const res = await request.get('/projects/templates')
    templates.value = res.data?.data || []
  } catch (e) {
    ElMessage.error('获取模板列表失败')
  }
}

const loadProjects = async () => {
  try {
    const res = await request.get('/projects')
    const data = res.data?.data
    if (Array.isArray(data)) projectList.value = data
    else if (data?.records) projectList.value = data.records
    else projectList.value = []
  } catch (e) {
    ElMessage.error('获取项目列表失败')
  }
}

const openApply = (tpl: Template) => {
  applyForm.value = { templateId: tpl.id, templateName: `${tpl.code} - ${tpl.name}`, projectId: null }
  showApply.value = true
}

const doApply = async () => {
  if (!applyForm.value.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  applying.value = true
  try {
    await request.post(`/projects/${applyForm.value.projectId}/apply-template`, null, {
      params: { templateId: applyForm.value.templateId }
    })
    ElMessage.success('模板已应用到项目')
    showApply.value = false
  } catch (e: any) {
    ElMessage.error('应用失败：' + (e?.response?.data?.message || e.message))
  } finally {
    applying.value = false
  }
}

const openEdit = (tpl: Template) => {
  editingId.value = tpl.id
  form.value = {
    code: tpl.code,
    name: tpl.name,
    description: tpl.description || '',
    configJson: tpl.configJson || '{}',
  }
  showCreate.value = true
}

const remove = async (tpl: Template) => {
  try {
    await ElMessageBox.confirm(`确定删除自定义模板 ${tpl.code} 吗？`, '确认删除', { type: 'warning' })
  } catch {
    return
  }
  try {
    await request.delete(`/projects/templates/${tpl.id}`)
    ElMessage.success('已删除')
    await loadTemplates()
  } catch (e: any) {
    ElMessage.error('删除失败：' + (e?.response?.data?.message || e.message))
  }
}

const saveTemplate = async () => {
  if (!form.value.code || !form.value.name) {
    ElMessage.warning('请填写模板编号和名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await request.put(`/projects/templates/${editingId.value}`, form.value)
      ElMessage.success('模板已更新')
    } else {
      await request.post('/projects/templates', form.value)
      ElMessage.success('模板已创建')
    }
    showCreate.value = false
    editingId.value = null
    form.value = { code: '', name: '', description: '', configJson: '{}' }
    await loadTemplates()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadTemplates()
  loadProjects()
})
</script>

<style scoped>
.tpl-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.page-title h2 {
  font-size: 20px;
  color: #303133;
}

.banner {
  margin-bottom: 16px;
}

.tpl-grid {
  margin-top: 8px;
}

.tpl-card {
  margin-bottom: 16px;
  border-top: 3px solid #409eff;
}

.tpl-card.preset {
  border-top-color: #67c23a;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.tpl-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.tpl-code {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
}

.tpl-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  margin-bottom: 8px;
}

.tpl-config {
  background: #fafbfc;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
  max-height: 160px;
  overflow-y: auto;
}

.config-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: #303133;
}

.config-row {
  margin: 2px 0;
  line-height: 1.5;
}

.config-key {
  color: #909399;
}

.config-val {
  color: #303133;
  word-break: break-all;
}

.card-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
