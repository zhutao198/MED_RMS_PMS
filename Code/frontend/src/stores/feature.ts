import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/api/request'

/**
 * R221 v1.77: Feature Flag 全局 store（前端）
 *
 * 后端 application.yml → 启动时拉取 /api/feature/flags → store
 * 各组件用 v-if="featureStore.signature" 屏蔽相关功能
 */
export const useFeatureStore = defineStore('feature', () => {
  // 签名功能开关（默认 true；后端可配置禁用）
  const signature = ref<boolean>(true)
  // DHF 证据包
  const dhf = ref<boolean>(true)
  // eRPS 报告
  const erps = ref<boolean>(true)
  // IEC 62304 合规
  const iec = ref<boolean>(true)
  // SOUP 组件
  const soup = ref<boolean>(true)
  // 加载状态
  const loaded = ref<boolean>(false)

  /**
   * 启动时从后端拉取 flags
   * 后端实现：GET /api/feature/flags（未认证也能调用，返回当前生效的合规开关）
   */
  const loadFlags = async () => {
    if (loaded.value) return
    try {
      const res: any = await request.get('/feature/flags')
      const data = res?.data?.data || res?.data || {}
      if (data.signature !== undefined) signature.value = data.signature
      if (data.dhf !== undefined) dhf.value = data.dhf
      if (data.erps !== undefined) erps.value = data.erps
      if (data.iec !== undefined) iec.value = data.iec
      if (data.soup !== undefined) soup.value = data.soup
      loaded.value = true
      console.info('R221 Feature Flags 加载完成:', data)
    } catch (e) {
      // 静默失败：默认值启用（最坏情况 = 后端禁用但前端显示可用 → 后端 503 兜底）
      console.warn('R221 加载 Feature Flags 失败:', e)
    }
  }

  return { signature, dhf, erps, iec, soup, loaded, loadFlags }
})
