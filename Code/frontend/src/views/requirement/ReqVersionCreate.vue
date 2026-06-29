<template>
  <div class="req-version-create-container">
    <!-- 顶部导航 -->
    <div class="page-header">
      <el-button @click="goBack">← 返回</el-button>
      <h2>新建需求版本</h2>
    </div>

    <!-- 加载中骨架 -->
    <el-skeleton v-if="loading" :rows="6" animated />

    <!-- 需求基本信息预览 -->
    <el-card v-if="requirement" class="info-card" shadow="never">
      <div class="info-row">
        <span class="info-label">需求编号</span>
        <span class="info-value">{{ requirement.requirementNo || '-' }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">需求标题</span>
        <span class="info-value">{{ requirement.title || '-' }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">当前层级</span>
        <el-tag :type="getTypeColor(requirement.requirementType)" effect="light">
          {{ requirement.requirementType || '-' }}
        </el-tag>
        <el-tag :type="getStatusColor(requirement.status)" effect="light" style="margin-left: 8px;">
          {{ requirement.status || '-' }}
        </el-tag>
      </div>
      <div class="info-row">
        <span class="info-label">当前最新版本</span>
        <span class="info-value">{{ latestVersionNo || '暂无版本' }}</span>
        <span class="info-hint" v-if="latestVersionNo">创建后版本号将自动 minor +1</span>
      </div>
    </el-card>

    <!-- 创建版本表单 -->
    <el-card v-if="requirement" class="form-card" shadow="never">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
        label-position="top"
      >
        <!-- 变更摘要 -->
        <el-form-item label="变更摘要" prop="summary">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="4"
            placeholder="请简要描述本次变更的内容与原因（必填）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <!-- CTI 关联标准（3 行默认勾选） -->
        <el-form-item label="关联标准 (CTI)">
          <div class="cti-block">
            <div class="cti-hint">医疗器械软件常用标准，可多选；勾选后随版本快照一并落库</div>
            <el-checkbox-group v-model="form.cti">
              <el-checkbox
                v-for="opt in ctiOptions"
                :key="opt.code"
                :value="opt.code"
                :label="opt.code"
                class="cti-checkbox"
              >
                <span class="cti-label">{{ opt.code }}</span>
                <span class="cti-desc">{{ opt.description }}</span>
              </el-checkbox>
            </el-checkbox-group>
            <el-button
              type="primary"
              link
              size="small"
              @click="resetCtiToDefault"
              style="margin-top: 8px;"
            >
              ↻ 恢复默认（3 行全选）
            </el-button>
          </div>
        </el-form-item>

        <!-- 版本号（可选手动指定） -->
        <el-form-item label="指定版本号">
          <el-input
            v-model="form.versionNo"
            placeholder="留空则按 minor+1 自动生成 (如 v1.1)"
            style="max-width: 320px;"
          />
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            创建版本快照
          </el-button>
          <el-button @click="goBack">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 空状态 -->
    <el-empty v-if="!loading && !requirement" description="未找到对应需求" />
  </div>
</template>

<script setup lang="ts">
/**
 * v1.52 新增：需求版本手动创建页
 * - 用途：手动触发一次版本快照（用于重要变更、基线化前、阶段性归档）
 * - 入参：summary（变更摘要，必填） + cti（关联标准代码数组，可选）
 * - 行为：调用后端 POST /api/requirements/{id}/versions，成功后跳回版本历史页
 */
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, FormInstance, FormRules } from 'element-plus'
import { requirementApi } from '../../api/requirement'
import type { Requirement } from '../../api/requirement'

const route = useRoute()
const router = useRouter()

// ========== 状态 ==========
const loading = ref(false)
const submitting = ref(false)
const requirement = ref<Requirement | null>(null)
const latestVersionNo = ref<string>('')

// 3 行默认 CTI 标准（医疗器械软件常用 3 项）
const DEFAULT_CTI = ['IEC 62304', 'ISO 14971', 'IEC 60601-1']
const ctiOptions = [
  { code: 'IEC 62304', description: '医疗器械软件生命周期' },
  { code: 'ISO 14971', description: '医疗器械风险管理' },
  { code: 'IEC 60601-1', description: '医用电气设备安全' }
]

const form = reactive({
  summary: '',
  cti: [...DEFAULT_CTI] as string[],
  versionNo: ''
})

const formRef = ref<FormInstance>()
const rules: FormRules = {
  summary: [
    { required: true, message: '请输入变更摘要', trigger: 'blur' },
    { min: 5, max: 500, message: '长度在 5 到 500 字符', trigger: 'blur' }
  ]
}

// ========== 数据加载 ==========
const loadRequirement = async () => {
  const id = Number(route.params.id)
  if (!id) {
    ElMessage.error('需求 ID 无效')
    return
  }
  loading.value = true
  try {
    // 1) 加载需求主表
    const res = await requirementApi.get(id)
    requirement.value = (res.data?.data as Requirement) || null

    // 2) 加载最新版本号（用于展示）
    try {
      const vRes = await fetch(`/api/requirements/${id}/versions`)
      const vData = await vRes.json()
      const list = (vData.data || []) as Array<{ versionNo: string }>
      if (list.length > 0) {
        latestVersionNo.value = list[0].versionNo
      }
    } catch (e) {
      console.warn('加载版本历史失败（忽略）', e)
    }
  } catch (e) {
    console.error('加载需求失败', e)
    ElMessage.error('加载需求失败')
  } finally {
    loading.value = false
  }
}

const resetCtiToDefault = () => {
  form.cti = [...DEFAULT_CTI]
}

// ========== 提交 ==========
const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  const id = Number(route.params.id)
  if (!id) {
    ElMessage.error('需求 ID 无效')
    return
  }

  submitting.value = true
  try {
    // 打包 changeSummary 为 JSON 字符串（后端会再解析、过滤、补字段）
    const changeSummaryObj = {
      summary: form.summary,
      cti: form.cti
    }
    const body: Record<string, unknown> = {
      changeSummary: JSON.stringify(changeSummaryObj)
    }
    if (form.versionNo && form.versionNo.trim()) {
      body.versionNo = form.versionNo.trim()
    }

    const res = await fetch(`/api/requirements/${id}/versions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    const json = await res.json()
    if (json.code === 200 && json.data) {
      ElMessage.success(`版本 ${json.data.versionNo} 创建成功`)
      // 跳回版本历史页
      router.push(`/requirements/${id}/versions`)
    } else {
      ElMessage.error(json.message || '创建版本失败')
    }
  } catch (e) {
    console.error('创建版本失败', e)
    ElMessage.error('创建版本失败')
  } finally {
    submitting.value = false
  }
}

// ========== 辅助 ==========
const goBack = () => {
  const id = route.params.id
  if (id) {
    router.push(`/requirements/${id}/versions`)
  } else {
    router.back()
  }
}

const getTypeColor = (type?: string) => {
  switch (type) {
    case 'URS': return 'success'
    case 'PRS': return 'warning'
    case 'SRS': return 'primary'
    case 'DRS': return 'info'
    default: return ''
  }
}

const getStatusColor = (status?: string) => {
  if (!status) return ''
  if (status === 'Baseline' || status === 'Verified') return 'success'
  if (status === 'Rejected' || status === 'ReviewRejected') return 'danger'
  if (status === 'Approved' || status === 'ReviewApproved') return 'primary'
  return 'info'
}

// ========== 生命周期 ==========
onMounted(() => {
  loadRequirement()
})
</script>

<style scoped>
.req-version-create-container {
  padding: 20px;
  max-width: 960px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.info-card,
.form-card {
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px dashed #ebeef5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 110px;
  color: #909399;
  font-size: 14px;
}

.info-value {
  color: #303133;
  font-size: 14px;
  font-weight: 500;
}

.info-hint {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}

.cti-block {
  width: 100%;
}

.cti-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.cti-checkbox {
  display: block;
  margin: 8px 0;
  height: auto;
  white-space: normal;
}

.cti-label {
  font-weight: 500;
  color: #303133;
  margin-right: 8px;
}

.cti-desc {
  color: #909399;
  font-size: 12px;
}
</style>
