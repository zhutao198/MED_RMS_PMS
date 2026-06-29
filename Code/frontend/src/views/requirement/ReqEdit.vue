<template>
  <div class="req-edit-container">
    <div class="page-header">
      <div class="page-title">编辑需求</div>
      <div class="header-actions">
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </div>
    </div>

    <div class="edit-form">
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-tag v-if="formData.requirementNo" size="small">{{ formData.requirementNo }}</el-tag>
          </div>
        </template>
        <!-- P2-6：按 requirementType动态展示对应层级特有属性 -->
 <el-form :model="formData" label-width="120px" class="form-grid">
          <el-form-item label="需求层级">
            <el-select v-model="formData.requirementType" style="width: 100%">
              <el-option label="URS - 用户需求规格" value="URS" />
              <el-option label="PRS - 产品需求规格" value="PRS" />
              <el-option label="SRS - 软件需求规格" value="SRS" />
              <el-option label="DRS - 设计需求规格" value="DRS" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="formData.priority" style="width: 100%">
              <el-option label="必须（MUST）" value="MUST" />
              <el-option label="应该（SHOULD）" value="SHOULD" />
              <el-option label="可以（COULD）" value="COULD" />
              <el-option label="不做（WONT）" value="WONT" />
            </el-select>
          </el-form-item>
          <el-form-item label="需求标题" class="span-2">
            <el-input v-model="formData.title" maxlength="50" show-word-limit placeholder="请输入需求标题" />
          </el-form-item>
          <el-form-item label="需求描述" class="span-2">
            <el-input v-model="formData.description" type="textarea" :rows="5" placeholder="请详细描述需求内容" />
          </el-form-item>
          <el-form-item label="需求来源">
            <el-input v-model="formData.source" placeholder="如 USER/REGULATORY/RISK_CONTROL" />
          </el-form-item>
          <el-form-item label="来源编号">
            <el-input v-model="formData.sourceNo" placeholder="来源关联编号" />
          </el-form-item>
          <el-form-item label="需求分类">
            <el-input v-model="formData.requirementCategory" placeholder="如 FUNCTIONAL/PERFORMANCE/SAFETY" />
          </el-form-item>
          <el-form-item label="风险等级">
            <el-select v-model="formData.riskLevel" style="width: 100%">
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item label="安全分类">
            <el-select v-model="formData.safetyClass" style="width: 100%">
              <el-option label="A 类" value="A" />
              <el-option label="B 类" value="B" />
              <el-option label="C 类" value="C" />
            </el-select>
          </el-form-item>
        </el-form>
      </el-card>

 <!-- P2-6：4层级特有属性动态区域（按 requirementType切换 URS/PRS/SRS/DRS字段） -->
 <el-card class="form-card">
 <template #header>
 <div class="card-header">
 <span>{{ dynamicFieldsTitle }}</span>
 <el-tag size="small" effect="plain">{{ formData.requirementType }}</el-tag>
 </div>
 </template>
 <el-form :model="formData" label-width="120px" class="form-grid">
 <!-- URS：场景/用例/角色/期望结果 -->
 <template v-if="formData.requirementType === 'URS'">
 <el-form-item label="使用场景" class="span-2">
 <el-input v-model="formData.dynamicFields.scenario" placeholder="如：临床医生日常查房时…" />
 </el-form-item>
 <el-form-item label="用例描述">
 <el-input v-model="formData.dynamicFields.useCase" placeholder="如：用户在30秒内完成…" />
 </el-form-item>
 <el-form-item label="用户角色">
 <el-input v-model="formData.dynamicFields.userRole" placeholder="如：临床医生/护士/患者" />
 </el-form-item>
 <el-form-item label="期望结果" class="span-2">
 <el-input v-model="formData.dynamicFields.expectedOutcome" type="textarea" :rows="2" placeholder="如：系统正确显示患者数据…" />
 </el-form-item>
 </template>
 <!-- PRS：设计约束/实现方案/影响组件 -->
 <template v-else-if="formData.requirementType === 'PRS'">
 <el-form-item label="设计约束" class="span-2">
 <el-input v-model="formData.dynamicFields.designConstraint" type="textarea" :rows="2" placeholder="硬件/接口/法规等约束…" />
 </el-form-item>
 <el-form-item label="实现方案" class="span-2">
 <el-input v-model="formData.dynamicFields.implementationApproach" type="textarea" :rows="2" placeholder="总体技术路径…" />
 </el-form-item>
 <el-form-item label="影响组件" class="span-2">
 <el-input v-model="formData.dynamicFields.affectedComponents" placeholder="如：电源管理模块/通信模块" />
 </el-form-item>
 </template>
 <!-- SRS：接口规格/性能目标/数据结构 -->
 <template v-else-if="formData.requirementType === 'SRS'">
 <el-form-item label="接口规格" class="span-2">
 <el-input v-model="formData.dynamicFields.interfaceSpec" type="textarea" :rows="2" placeholder="函数签名/参数/返回值…" />
 </el-form-item>
 <el-form-item label="性能目标" class="span-2">
 <el-input v-model="formData.dynamicFields.performanceTarget" placeholder="如：响应<200ms /吞吐量≥1000 qps" />
 </el-form-item>
 <el-form-item label="数据结构" class="span-2">
 <el-input v-model="formData.dynamicFields.dataStructure" type="textarea" :rows="2" placeholder="如：JSON Schema /字段定义…" />
 </el-form-item>
 </template>
 <!-- DRS：算法/版本控制/部署环境 -->
 <template v-else-if="formData.requirementType === 'DRS'">
 <el-form-item label="算法" class="span-2">
 <el-input v-model="formData.dynamicFields.algorithm" type="textarea" :rows="3" placeholder="核心算法描述/伪代码…" />
 </el-form-item>
 <el-form-item label="版本控制">
 <el-input v-model="formData.dynamicFields.versionControlCode" placeholder="如：Git SHA / 分支标签" />
 </el-form-item>
 <el-form-item label="部署环境">
 <el-input v-model="formData.dynamicFields.deploymentEnv" placeholder="如：Linux5.10 / Docker20.x" />
 </el-form-item>
 </template>
 <template v-else>
 <el-alert type="info" :closable="false" show-icon>
 <template #title>请先选择需求层级（URS/PRS/SRS/DRS）以展示对应字段</template>
 </el-alert>
 </template>
 </el-form>
 </el-card>

      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>说明</span>
          </div>
        </template>
        <el-alert type="info" :closable="false" show-icon>
          <template #title>追溯关系（上游/下游）、法规条款、SOUP 组件关联请在详情页或拆解工作台操作</template>
        </el-alert>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { requirementApi } from '@/api/requirement'

const route = useRoute()
const router = useRouter()
const requirementId = Number(route.params.id)

const loading = ref(false)
const saving = ref(false)

const formData = reactive({
  id: 0,
  requirementNo: '',
  requirementType: 'URS',
  projectId: null as number | null,
  title: '',
  description: '',
  priority: 'MUST',
  status: 'Draft',
  riskLevel: 'MEDIUM',
  safetyClass: 'B',
  requirementCategory: '',
  source: '',
  sourceNo: '',
  baselineId: null as number | null,
  // P2-6 修复：4 层级特有属性合并入 formData
  dynamicFields: {
    scenario: '',
    useCase: '',
    userRole: '',
    expectedOutcome: '',
    designConstraint: '',
    implementationApproach: '',
    affectedComponents: '',
    interfaceSpec: '',
    performanceTarget: '',
    dataStructure: '',
    algorithm: '',
    versionControlCode: '',
    deploymentEnv: ''
  } as Record<string, string>
})

const loadRequirement = async () => {
  loading.value = true
  try {
    const res = await requirementApi.get(requirementId)
    const r = res.data?.data
    if (r) {
      formData.id = r.id
      formData.requirementNo = r.requirementNo || ''
      formData.requirementType = r.requirementType || 'URS'
      formData.projectId = r.projectId ?? null
      formData.title = r.title || ''
      formData.description = r.description || ''
      formData.priority = r.priority || 'MUST'
      formData.status = r.status || 'Draft'
      formData.riskLevel = r.riskLevel || 'MEDIUM'
      formData.safetyClass = r.safetyClass || 'B'
      formData.requirementCategory = r.requirementCategory || ''
      formData.source = r.source || ''
      formData.sourceNo = r.sourceNo || ''
      formData.baselineId = r.baselineId ?? null
 // P2-6：反序列化后端 dynamicFields（兼容 JSON字符串/对象两种形态）
 try {
 const df = (r as any).dynamicFields
 if (df && typeof df === 'string') Object.assign(formData.dynamicFields, JSON.parse(df))
 else if (df && typeof df === 'object') Object.assign(formData.dynamicFields, df)
 } catch (e) { /* 容错：后端格式异常不阻塞主流程 */ }
}
  } catch (e: any) {
    ElMessage.error(`加载失败：${e?.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

// P2-6：根据 requirementType计算动态区域标题
const dynamicFieldsTitle = computed(() => {
 const map: Record<string, string> = {
 URS: 'URS 用户层级特有属性',
 PRS: 'PRS 产品层级特有属性',
 SRS: 'SRS 软件层级特有属性',
 DRS: 'DRS 设计层级特有属性',
 }
 return map[formData.requirementType] || '层级特有属性'
})

const handleSave = async () => {
  if (!formData.title) {
    ElMessage.warning('请输入需求标题')
    return
  }
  saving.value = true
  try {
    // P2-6：将 dynamicFields合并到顶层（后端若已支持 dynamicFields JSON列则可直接接收）
 const payload = { ...formData, dynamicFields: JSON.stringify(formData.dynamicFields) }
 await requirementApi.update(formData.id, payload as any)
    ElMessage.success('保存成功')
    router.push(`/requirements/${formData.id}`)
  } catch (e: any) {
    ElMessage.error(`保存失败：${e?.message || '未知错误'}`)
  } finally {
    saving.value = false
  }
}

onMounted(loadRequirement)
</script>

<style scoped>
.req-edit-container {
  padding: 20px;
  background: #f0f2f5;
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.span-2 {
  grid-column: span 2;
}

.trace-section {
  background: #f9f9f9;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.trace-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.trace-label {
  font-size: 13px;
  color: #606266;
  min-width: 80px;
}

.trace-value {
  flex: 1;
  font-size: 13px;
  color: #303133;
  background: #fff;
  padding: 6px 10px;
  border-radius: 4px;
}

.relation-section {
  margin-bottom: 16px;
}

.relation-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
</style>