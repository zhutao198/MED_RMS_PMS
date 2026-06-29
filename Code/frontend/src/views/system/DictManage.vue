<template>
  <div class="dict-manage-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>字典管理</span>
          <el-button type="primary" :disabled="!filterType" @click="showDictDialog = true">
            新增字典项
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="字典类型">
          <el-select
            v-model="filterType"
            placeholder="请选择类型"
            style="width: 240px"
            @change="fetchDicts"
          >
            <el-option
              v-for="t in dictTypes"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-table :data="dictItems" border stripe v-loading="loading" empty-text="请先选择字典类型">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dictType" label="类型" width="180" />
        <el-table-column prop="dictCode" label="编码" width="160" />
        <el-table-column prop="dictName" label="名称" min-width="200" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editDict(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 字典项对话框 -->
    <el-dialog
      v-model="showDictDialog"
      :title="editingDict ? '编辑字典项' : '新增字典项'"
      width="500px"
      @closed="resetDictForm"
    >
      <el-form :model="dictForm" label-width="100px">
        <el-form-item label="字典类型">
          <el-input v-model="dictForm.dictType" :disabled="!!editingDict" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="dictForm.dictCode" :disabled="!!editingDict" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="dictForm.dictName" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dictForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDictDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitDict">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { systemApi, type DictItem } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'

const filterType = ref<string>('')
const dictTypes = ref<string[]>([])
const dictItems = ref<DictItem[]>([])
const loading = ref(false)
const showDictDialog = ref(false)
const submitting = ref(false)
const editingDict = ref<DictItem | null>(null)

const dictForm = ref<{ dictType: string; dictCode: string; dictName: string; sortOrder: number }>({
  dictType: '',
  dictCode: '',
  dictName: '',
  sortOrder: 1,
})

const fetchDictTypes = async () => {
  try {
    const res = await systemApi.getAllDicts()
    const all = res.data.data || []
    const set = new Set<string>()
    for (const d of all) set.add(d.dictType)
    dictTypes.value = Array.from(set).sort()
  } catch {
    ElMessage.error('获取字典类型失败')
  }
}

const fetchDicts = async () => {
  if (!filterType.value) {
    dictItems.value = []
    return
  }
  loading.value = true
  try {
    const res = await systemApi.getDicts(filterType.value)
    dictItems.value = res.data.data || []
  } catch {
    ElMessage.error('获取字典失败')
  } finally {
    loading.value = false
  }
}

const editDict = (row: DictItem) => {
  editingDict.value = row
  dictForm.value = {
    dictType: row.dictType,
    dictCode: row.dictCode,
    dictName: row.dictName,
    sortOrder: row.sortOrder ?? 1,
  }
  showDictDialog.value = true
}

const resetDictForm = () => {
  editingDict.value = null
  dictForm.value = {
    dictType: filterType.value,
    dictCode: '',
    dictName: '',
    sortOrder: 1,
  }
}

const submitDict = async () => {
  if (!dictForm.value.dictType || !dictForm.value.dictCode || !dictForm.value.dictName) {
    ElMessage.warning('请填写完整字段')
    return
  }
  submitting.value = true
  try {
    if (editingDict.value) {
      await systemApi.updateDict(editingDict.value.id, dictForm.value)
    } else {
      await systemApi.createDict(dictForm.value as any)
    }
    ElMessage.success('保存成功')
    showDictDialog.value = false
    await fetchDictTypes()
    await fetchDicts()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const confirmDelete = (row: DictItem) => {
  ElMessageBox.confirm(
    `确认删除字典项 "${row.dictCode} - ${row.dictName}"？`,
    '提示',
    { type: 'warning' }
  ).then(async () => {
    try {
      await systemApi.deleteDict(row.id)
      ElMessage.success('已删除')
      fetchDicts()
    } catch {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

onMounted(async () => {
  await fetchDictTypes()
  if (dictTypes.value.length > 0) {
    filterType.value = dictTypes.value[0]
    fetchDicts()
  }
})
</script>

<style scoped>
.dict-manage-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-form { margin-bottom: 16px; }
</style>
