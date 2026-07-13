import { defineStore } from 'pinia'
import { ref } from 'vue'
import { projectApi, type Project } from '@/api/project'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([])
  const loaded = ref(false)
  const loading = ref(false)

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

  return { projects, loaded, loading, fetchProjects, ensureLoaded, getProjectLabel, getProjectName }
})
