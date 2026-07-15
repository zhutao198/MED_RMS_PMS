import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { projectApi, type Project } from '@/api/project'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  // R192: 全局当前选中项目（跨页面同步）
  const currentProjectId = ref<number | null>(
    Number(localStorage.getItem('currentProjectId')) || null
  )
  const currentProjectName = computed(() => {
    if (!currentProjectId.value) return '未选择项目'
    const p = projects.value.find(x => x.id === currentProjectId.value)
    return p ? p.projectName : `项目 ${currentProjectId.value}`
  })

  function setCurrentProjectId(id: number | null) {
    currentProjectId.value = id
    if (id) localStorage.setItem('currentProjectId', String(id))
    else localStorage.removeItem('currentProjectId')
  }

  async function fetchProjects() {
    if (loaded.value) return
    loading.value = true
    try {
      const res = await projectApi.list()
      projects.value = res.data?.data || []
      loaded.value = true
    } catch {
      // 静默失败，允许重试
    } finally {
      loading.value = false
    }
  }

  async function ensureLoaded() {
    if (!loaded.value) await fetchProjects()
  }

  function getProjectLabel(projectId: number | undefined | null): string {
    if (!projectId) return '-'
    const p = projects.value.find(x => x.id === projectId)
    return p ? `${p.projectNo} - ${p.projectName}` : `项目 ${projectId}`
  }

  function getProjectName(projectId: number | undefined | null): string {
    if (!projectId) return '-'
    const p = projects.value.find(x => x.id === projectId)
    return p ? p.projectName : `项目 ${projectId}`
  }

  return {
    projects, loaded, loading,
    currentProjectId, currentProjectName, setCurrentProjectId,
    fetchProjects, ensureLoaded, getProjectLabel, getProjectName,
  }
})
