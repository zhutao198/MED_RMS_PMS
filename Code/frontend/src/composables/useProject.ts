import { computed } from 'vue'
import { useProjectStore } from '@/stores/project'

export function useProject() {
  const store = useProjectStore()

  return {
    projectList: computed(() => store.projects),
    projectsLoaded: computed(() => store.loaded),
    projectsLoading: computed(() => store.loading),
    currentProjectId: computed(() => store.currentProjectId),
    currentProjectName: computed(() => store.currentProjectName),
    setCurrentProjectId: store.setCurrentProjectId,
    fetchProjects: store.fetchProjects,
    ensureLoaded: store.ensureLoaded,
    getProjectLabel: store.getProjectLabel,
    getProjectName: store.getProjectName,
  }
}
