<template>
  <div class="milestone-progress">
    <div class="summary" v-if="milestones.length > 0">{{ summaryText }}</div>
    <div ref="chartRef" class="chart-container" v-loading="loading"></div>
    <el-empty v-if="!loading && milestones.length === 0" description="暂无比里程碑数据" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import request from '@/api/request'
import * as echarts from 'echarts'

const props = defineProps<{ projectId: number }>()

const chartRef = ref<HTMLElement | null>(null)
const milestones = ref<any[]>([])
const loading = ref(false)
let chartInstance: echarts.ECharts | null = null

// D-14 修复：DB 中里程碑状态为 PLANNED/IN_PROGRESS/COMPLETED/DELAYED，不是 PENDING
const completedCount = computed(() => milestones.value.filter(m => m.status === 'COMPLETED').length)
const summaryText = computed(() => `${completedCount.value}/${milestones.value.length} 里程碑已完成`)

const getProgress = (m: any): number => {
  if (m.status === 'COMPLETED') return 100
  if (m.status === 'PLANNED') return 0  // 修复：原代码用 'PENDING' 但 DB 存 'PLANNED'
  if (m.status === 'DELAYED') return m.progress ?? 0
  return m.progress ?? 0  // IN_PROGRESS 等
}

const getColor = (progress: number): string => {
  if (progress >= 80) return '#67C23A'
  if (progress >= 40) return '#E6A23C'
  return '#F56C6C'
}

const renderChart = () => {
  if (!chartRef.value || milestones.value.length === 0) return

  if (!chartInstance) chartInstance = echarts.init(chartRef.value)

  const list = milestones.value.map(m => ({
    name: m.name,
    progress: getProgress(m)
  }))

  chartInstance.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: list.map(i => i.name), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'bar',
      data: list.map(i => ({ value: i.progress, itemStyle: { color: getColor(i.progress) } })),
      barWidth: 30,
      label: { show: true, position: 'top', formatter: '{c}%' }
    }]
  })
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await request.get(`/gantt/milestones/project/${props.projectId}`)
    milestones.value = Array.isArray(res.data?.data) ? res.data.data : []
  } catch {
    milestones.value = []
  } finally {
    loading.value = false
    nextTick(renderChart)
  }
}

defineExpose({ loadData })

onMounted(loadData)

onUnmounted(() => {
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.milestone-progress { padding: 16px; }
.summary { font-size: 14px; color: #606266; margin-bottom: 12px; }
.chart-container { width: 100%; height: 300px; }
</style>
