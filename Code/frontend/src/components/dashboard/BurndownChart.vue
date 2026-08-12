<template>
  <el-card class="burndown-card" shadow="never">
    <template #header>
      <div class="burndown-header">
        <span>燃尽图</span>
        <el-button text @click="loadData" :icon="'Refresh'" :loading="loading" />
      </div>
    </template>
    <div
      v-loading="loading"
      class="burndown-chart-wrapper"
    >
      <div v-if="emptyData" class="burndown-empty">
        <el-empty :image-size="80" :description="emptyReasonText" />
      </div>
      <div v-else-if="api404" class="burndown-placeholder">
        <el-empty :image-size="80" description="燃尽图数据将在项目启动后生成" />
      </div>
      <div v-else ref="chartRef" class="burndown-chart"></div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { requestFetch } from '@/api/request'
import * as echarts from 'echarts'

const props = defineProps<{ projectId: number }>()

const chartRef = ref<HTMLElement | null>(null)
const loading = ref(false)
const emptyData = ref(false)
const api404 = ref(false)
// D-15 修复：用后端返回的 reason 字段区分"无数据"原因（PROJECT_NOT_FOUND / NO_START_DATE / NO_ESTIMATED_HOURS）
const emptyReason = ref<string>('')

let chartInstance: echarts.ECharts | null = null

interface BurndownData {
  dates: string[]
  ideal: number[]
  actual: number[]
  reason?: string
  totalEffort?: number
  doneEffort?: number
}

const loadData = async () => {
  loading.value = true
  emptyData.value = false
  api404.value = false
  emptyReason.value = ''
  try {
    const resp = await requestFetch(`/gantt/burndown/${props.projectId}`)
    if (!resp) return
    if (resp.status === 404) {
      api404.value = true
      return
    }
    const json = await resp.json()
    const body = json as { code: number; message: string; data?: BurndownData }
    // D-17 修复：Result.success 用 code=200（数字），前端原代码用 '0000' 字符串比较永远不等，
    // 即使成功响应也会触发 API_ERROR
    if (body.code !== 200 || !body.data) {
      emptyData.value = true
      emptyReason.value = 'API_ERROR'
      return
    }
    const { dates, ideal, actual, reason } = body.data
    if (!dates?.length || !ideal?.length || !actual?.length) {
      emptyData.value = true
      emptyReason.value = reason || 'UNKNOWN'
      return
    }
    renderChart(dates, ideal, actual)
  } catch {
    emptyData.value = true
    emptyReason.value = 'NETWORK_ERROR'
  } finally {
    loading.value = false
  }
}

const emptyReasonText = computed(() => {
  switch (emptyReason.value) {
    case 'NO_START_DATE': return '项目未设置开始日期'
    case 'NO_ESTIMATED_HOURS': return '项目任务尚未填写预估工时'
    case 'PROJECT_NOT_FOUND': return '项目不存在'
    case 'API_ERROR': return 'API 调用错误（请确认项目 ID 有效）'
    case 'NETWORK_ERROR': return '网络错误'
    default: return '项目无燃尽数据（请确认设置了开始/结束日期，并填写任务预估工时）'
  }
})

const renderChart = (dates: string[], ideal: number[], actual: number[]) => {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  chartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const date = params[0]?.axisValue || ''
        let html = `<div style="font-weight:600;margin-bottom:4px">${date}</div>`
        params.forEach((p: any) => {
          html += `<div style="display:flex;align-items:center;gap:6px">
            <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color}"></span>
            ${p.seriesName}: <strong>${p.value}</strong>
          </div>`
        })
        return html
      }
    },
    legend: {
      top: 0,
      data: ['理想线', '实际线']
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: { rotate: 30 }
    },
    yAxis: {
      type: 'value',
      name: '剩余工作量'
    },
    series: [
      {
        name: '理想线',
        type: 'line',
        data: ideal,
        smooth: true,
        lineStyle: { color: '#909399', type: 'dashed' },
        itemStyle: { color: '#909399' }
      },
      {
        name: '实际线',
        type: 'line',
        data: actual,
        smooth: true,
        lineStyle: { color: '#409eff', width: 2 },
        itemStyle: { color: '#409eff' },
        areaStyle: { color: 'rgba(64,158,255,0.1)' }
      }
    ]
  })
}

const resizeChart = () => {
  chartInstance?.resize()
}

watch(() => props.projectId, () => {
  chartInstance?.dispose()
  chartInstance = null
  loadData()
})

onMounted(() => {
  loadData()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  chartInstance?.dispose()
  chartInstance = null
})

defineExpose({ loadData })
</script>

<style scoped>
.burndown-card {
  height: 100%;
}
.burndown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  font-size: 14px;
}
.burndown-chart-wrapper {
  height: 300px;
  position: relative;
}
.burndown-chart {
  width: 100%;
  height: 100%;
}
.burndown-empty,
.burndown-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
