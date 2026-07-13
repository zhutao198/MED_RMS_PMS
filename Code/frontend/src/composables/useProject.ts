import { computed } from 'vue'
import { useProjectStore } from '@/stores/project'

export function useProject() {
  const store = useProjectStore()

  return {
    projectList: computed(() => store.projects),
    projectsLoaded: computed(() => store.loaded),
    projectsLoading: computed(() => store.loading),
    fetchProjects: store.fetchProjects,
    ensureLoaded: store.ensureLoaded,
    getProjectLabel: store.getProjectLabel,
    getProjectName: store.getProjectName,
  }
}
