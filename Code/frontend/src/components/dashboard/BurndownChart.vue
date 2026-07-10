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
        <el-empty :image-size="80" description="暂无数据" />
      </div>
      <div v-else-if="api404" class="burndown-placeholder">
        <el-empty :image-size="80" description="燃尽图数据将在项目启动后生成" />
      </div>
      <div v-else ref="chartRef" class="burndown-chart"></div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { requestFetch } from '@/api/request'
import * as echarts from 'echarts'

const props = defineProps<{ projectId: number }>()

const chartRef = ref<HTMLElement | null>(null)
const loading = ref(false)
const emptyData = ref(false)
const api404 = ref(false)

let chartInstance: echarts.ECharts | null = null

interface BurndownData {
  dates: string[]
  ideal: number[]
  actual: number[]
}

const loadData = async () => {
  loading.value = true
  emptyData.value = false
  api404.value = false
  try {
    const resp = await requestFetch(`/gantt/burndown/${props.projectId}`)
    if (!resp) return
    if (resp.status === 404) {
      api404.value = true
      return
    }
    const json = await resp.json()
    const body = json as { code: string; data?: BurndownData }
    if (body.code !== '0000' || !body.data) {
      emptyData.value = true
      return
    }
    const { dates, ideal, actual } = body.data
    if (!dates?.length || !ideal?.length || !actual?.length) {
      emptyData.value = true
      return
    }
    renderChart(dates, ideal, actual)
  } catch {
    emptyData.value = true
  } finally {
    loading.value = false
  }
}

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
