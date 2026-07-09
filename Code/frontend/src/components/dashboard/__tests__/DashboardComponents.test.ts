import { describe, it, expect, vi, beforeEach } from 'vitest'
import { shallowMount } from '@vue/test-utils'

// ---- mocks ----
vi.mock('@/api/request', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { data: [] } }) },
  requestFetch: vi.fn()
}))

vi.mock('echarts', () => {
  const mockChart = {
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn()
  }
  return { init: vi.fn(() => mockChart), default: { init: vi.fn(() => mockChart) } }
})

// ---- imports after mocks ----
import MilestoneProgress from '@/components/dashboard/MilestoneProgress.vue'
import BurndownChart from '@/components/dashboard/BurndownChart.vue'
import SoupStatusCard from '@/components/dashboard/SoupStatusCard.vue'
import request from '@/api/request'
import { requestFetch } from '@/api/request'

// ---- MilestoneProgress ----
describe('MilestoneProgress.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders with projectId prop', () => {
    const wrapper = shallowMount(MilestoneProgress, {
      props: { projectId: 1 }
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.milestone-progress').exists()).toBe(true)
  })

  it('passes projectId prop correctly', () => {
    const wrapper = shallowMount(MilestoneProgress, {
      props: { projectId: 42 }
    })
    expect(wrapper.props('projectId')).toBe(42)
  })

  it('calls API on mount with correct projectId', async () => {
    const getSpy = vi.mocked(request.get)
    shallowMount(MilestoneProgress, {
      props: { projectId: 7 }
    })
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(getSpy).toHaveBeenCalledWith('/projects/7/milestones')
  })

  it('shows empty state when no milestones', async () => {
    vi.mocked(request.get).mockResolvedValue({ data: { data: [] } })
    const wrapper = shallowMount(MilestoneProgress, {
      props: { projectId: 1 }
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    expect(wrapper.find('.milestone-progress').exists()).toBe(true)
  })

  it('renders summary text with milestone count', async () => {
    vi.mocked(request.get).mockResolvedValue({
      data: { data: [{ name: 'M1', status: 'COMPLETED', progress: 100 }] }
    })
    const wrapper = shallowMount(MilestoneProgress, {
      props: { projectId: 1 }
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    const summary = wrapper.find('.summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('1/1')
    expect(summary.text()).toContain('已完成')
  })
})

async function flush(wrapper: any) {
  await new Promise(resolve => setTimeout(resolve, 10))
  await wrapper.vm?.$nextTick?.()
}

// ======== BurndownChart ========
const burndownGlobalStubs = {
  stubs: ['el-card', 'el-button', 'el-empty']
}

describe('BurndownChart.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders with projectId prop', () => {
    const wrapper = shallowMount(BurndownChart, {
      props: { projectId: 1 },
      global: burndownGlobalStubs
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('passes projectId prop correctly', () => {
    const wrapper = shallowMount(BurndownChart, {
      props: { projectId: 99 },
      global: burndownGlobalStubs
    })
    expect(wrapper.vm.$props.projectId).toBe(99)
  })

  it('calls requestFetch on mount with correct projectId', async () => {
    const fetchSpy = vi.mocked(requestFetch).mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: '0000', data: { dates: ['2024-01-01'], ideal: [10], actual: [10] } }),
      status: 200
    } as any)
    shallowMount(BurndownChart, {
      props: { projectId: 5 },
      global: burndownGlobalStubs
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    expect(fetchSpy).toHaveBeenCalledWith('/projects/5/burndown')
  })

  it('shows empty data state', async () => {
    vi.mocked(requestFetch).mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: '0000', data: null }),
      status: 200
    } as any)
    const wrapper = shallowMount(BurndownChart, {
      props: { projectId: 1 },
      global: burndownGlobalStubs
    })
    await flush(wrapper)
    expect(wrapper.vm.emptyData).toBe(true)
    expect(wrapper.vm.api404).toBe(false)
  })

  it('shows 404 placeholder state', async () => {
    vi.mocked(requestFetch).mockResolvedValue({ status: 404 } as any)
    const wrapper = shallowMount(BurndownChart, {
      props: { projectId: 1 },
      global: burndownGlobalStubs
    })
    await flush(wrapper)
    expect(wrapper.vm.api404).toBe(true)
    expect(wrapper.vm.emptyData).toBe(false)
  })

  it('exposes loadData method', () => {
    const wrapper = shallowMount(BurndownChart, {
      props: { projectId: 1 },
      global: burndownGlobalStubs
    })
    expect(typeof wrapper.vm.loadData).toBe('function')
  })
})

// ======== SoupStatusCard ========
const soupGlobalStubs = {
  stubs: ['el-icon']
}

describe('SoupStatusCard.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders with projectId prop', () => {
    const wrapper = shallowMount(SoupStatusCard, {
      props: { projectId: 1 },
      global: soupGlobalStubs
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('.soup-status-card').exists()).toBe(true)
  })

  it('passes projectId prop correctly', () => {
    const wrapper = shallowMount(SoupStatusCard, {
      props: { projectId: 10 },
      global: soupGlobalStubs
    })
    expect(wrapper.vm.$props.projectId).toBe(10)
  })

  it('calls requestFetch on mount with correct query param', async () => {
    const fetchSpy = vi.mocked(requestFetch).mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: '0000', data: { total: 10, assessed: 5, pending: 4, anomalies: 1 } })
    } as any)
    shallowMount(SoupStatusCard, {
      props: { projectId: 3 },
      global: soupGlobalStubs
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    expect(fetchSpy).toHaveBeenCalledWith('/compliance/soup/stats?projectId=3')
  })

  it('displays loaded stats values', async () => {
    vi.mocked(requestFetch).mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ code: '0000', data: { total: 10, assessed: 5, pending: 4, anomalies: 1 } })
    } as any)
    const wrapper = shallowMount(SoupStatusCard, {
      props: { projectId: 1 },
      global: soupGlobalStubs
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    const values = wrapper.findAll('.stat-value')
    expect(values).toHaveLength(4)
    expect(values[0].text()).toBe('5')
    expect(values[1].text()).toBe('4')
    expect(values[2].text()).toBe('1')
    expect(values[3].text()).toBe('10')
  })

  it('renders skeleton while loading', async () => {
    vi.mocked(requestFetch).mockImplementation(() => new Promise(() => {}))
    const wrapper = shallowMount(SoupStatusCard, {
      props: { projectId: 1 },
      global: soupGlobalStubs
    })
    await flush(wrapper)
    expect(wrapper.find('.skeleton').exists()).toBe(true)
    expect(wrapper.findAll('.skeleton-line')).toHaveLength(8)
  })

  it('shows error state on fetch failure', async () => {
    vi.mocked(requestFetch).mockRejectedValue(new Error('network error'))
    const wrapper = shallowMount(SoupStatusCard, {
      props: { projectId: 1 },
      global: soupGlobalStubs
    })
    await new Promise(resolve => setTimeout(resolve, 50))
    expect(wrapper.find('.card-error').exists()).toBe(true)
    expect(wrapper.find('.card-error').text()).toContain('数据加载失败')
  })
})