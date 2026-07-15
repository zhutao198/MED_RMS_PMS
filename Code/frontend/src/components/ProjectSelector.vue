<template>
  <el-select
    v-model="selectedId"
    :placeholder="placeholder"
    filterable
    clearable
    style="width: 240px"
    @change="onChange"
  >
    <el-option v-if="showAll" key="__all__" label="📋 全部项目" :value="-1" />
    <el-option
      v-for="p in projectList"
      :key="p.id"
      :label="getProjectLabel(p.id)"
      :value="p.id"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useProjectStore } from '@/stores/project'
import { useProject } from '@/composables/useProject'

const props = withDefaults(defineProps<{
  modelValue?: number | null
  placeholder?: string
  showAll?: boolean
  syncToStore?: boolean
}>(), {
  modelValue: undefined,
  placeholder: '请选择项目',
  showAll: false,
  syncToStore: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', val: number | null): void
}>()

const store = useProjectStore()
const { projectList, getProjectLabel, ensureLoaded } = useProject()

const selectedId = ref(props.modelValue !== undefined ? props.modelValue : null)

watch(() => props.modelValue, (val) => {
  if (val !== undefined) selectedId.value = val
})

function onChange(val: number | null) {
  selectedId.value = val
  emit('update:modelValue', val)
  if (props.syncToStore) store.setCurrentProjectId(val)
}

onMounted(() => ensureLoaded())
</script>
