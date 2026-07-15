import { ref, watch } from 'vue'
import { useProjectStore } from '@/stores/project'

export function useSyncProjectId(defaultValue?: number | null) {
  const store = useProjectStore()
  const projectId = ref(store.currentProjectId ?? defaultValue ?? null)

  watch(() => store.currentProjectId, (id) => {
    if (id !== projectId.value) projectId.value = id
  })

  return projectId
}
