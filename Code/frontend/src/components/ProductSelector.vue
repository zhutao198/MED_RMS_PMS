<template>
  <el-select
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :placeholder="placeholder"
    filterable
    clearable
    :loading="loading"
    :disabled="disabled"
    :style="{ width: '100%' }"
    @change="onChange"
  >
    <el-option
      v-for="p in products"
      :key="p.id"
      :label="`${p.productCode} ${p.productName}`"
      :value="p.id"
    >
      <span style="float: left">{{ p.productCode }}</span>
      <span style="float: right; color: #909399; font-size: 12px">{{ p.productLine }}</span>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
/**
 * R199 v1.62: 产品选择器（通用组件）
 * 参考 ProjectSelector.vue（filterable + clearable 模式，R192）
 * 数据源：GET /products/all（后端 5min TimedCache 缓存）
 */
import { ref, onMounted } from 'vue'
import { productApi, Product } from '@/api/product'

const props = withDefaults(defineProps<{
  modelValue: number | null
  placeholder?: string
  disabled?: boolean
}>(), {
  placeholder: '请选择产品',
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  change: [value: number | null]
}>()

const products = ref<Product[]>([])
const loading = ref(false)

const loadProducts = async () => {
  loading.value = true
  try {
    const res = await productApi.all()
    products.value = (res as any).data || res || []
  } catch (e) {
    console.error('加载产品列表失败', e)
  } finally {
    loading.value = false
  }
}

const onChange = (val: number | null) => {
  emit('change', val)
}

onMounted(loadProducts)
</script>