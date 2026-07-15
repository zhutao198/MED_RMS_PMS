<template>
  <div class="risk-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>风险评估报告</span>
          <ProjectSelector v-model="projectId" @change="loadData" />
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <el-statistic title="总风险数" :value="report.totalRisks" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="高风险" :value="report.highRisks" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="中风险" :value="report.mediumRisks" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="低风险" :value="report.lowRisks" />
        </el-col>
      </el-row>

      <el-divider />

      <el-progress :text-inside="true" :stroke-width="20" :percentage="highPercent" color="#f56c6c" style="margin: 20px 0">
        高风险 {{ highPercent }}%
      </el-progress>
      <el-progress :text-inside="true" :stroke-width="20" :percentage="mediumPercent" color="#e6a23c" style="margin: 20px 0">
        中风险 {{ mediumPercent }}%
      </el-progress>
      <el-progress :text-inside="true" :stroke-width="20" :percentage="lowPercent" color="#67c23a" style="margin: 20px 0">
        低风险 {{ lowPercent }}%
      </el-progress>

      <el-alert
        v-if="report.uncontrolledRisks > 0"
        title="存在未控制风险项"
        type="warning"
        :description="'有 ' + report.uncontrolledRisks + ' 项风险尚未制定控制措施'"
        style="margin-top: 16px"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useProject } from '@/composables/useProject'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { useSyncProjectId } from '@/composables/useSyncProjectId'
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { riskApi, type RiskReport } from '@/api/risk'
import request from '@/api/request'

const projectId = useSyncProjectId(1)
const report = ref<RiskReport>({
  totalRisks: 0,
  highRisks: 0,
  mediumRisks: 0,
  lowRisks: 0,
  uncontrolledRisks: 0,
})

const highPercent = computed(() => report.value.totalRisks ? Math.round(report.value.highRisks / report.value.totalRisks * 100) : 0)
const mediumPercent = computed(() => report.value.totalRisks ? Math.round(report.value.mediumRisks / report.value.totalRisks * 100) : 0)
const lowPercent = computed(() => report.value.totalRisks ? Math.round(report.value.lowRisks / report.value.totalRisks * 100) : 0)

const fetchReport = async () => {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    const res = await riskApi.getReport(projectId.value)
    report.value = res.data.data || report.value
  } catch (e: any) {
    ElMessage.error('加载风险报告失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const { ensureLoaded } = useProject()

onMounted(async () => {
  await ensureLoaded()
  await fetchReport()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>

<style scoped>
.risk-container { padding: 16px; }
</style>