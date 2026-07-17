import { ref, watch } from 'vue'
import { useProjectStore } from '@/stores/project'

export function useSyncProjectId(defaultValue?: number | null) {
  const store = useProjectStore()
  // -1 是 Dashboard "全部项目" 的占位值，非 Dashboard 页面应视为无选择
  const effective = store.currentProjectId === -1 ? null : store.currentProjectId
  const projectId = ref(effective ?? defaultValue ?? null)

  watch(() => store.currentProjectId, (id) => {
    const effective = id === -1 ? null : id
    if (effective !== projectId.value) projectId.value = effective
  })

  return projectId
}
