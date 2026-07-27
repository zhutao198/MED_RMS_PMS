<template>
  <div class="product-list">
    <h2>产品管理</h2>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input v-model="search.keyword" placeholder="产品编码/名称" clearable style="width: 200px" />
      <el-select v-model="search.productLine" placeholder="产品线" clearable style="width: 160px; margin-left: 8px">
        <el-option v-for="d in dictItems.product_line" :key="d.dictCode" :label="d.dictName" :value="d.dictCode" />
      </el-select>
      <el-select v-model="search.status" placeholder="状态" clearable style="width: 140px; margin-left: 8px">
        <el-option label="在产" value="ACTIVE" />
        <el-option label="开发中" value="DEVELOPMENT" />
        <el-option label="停产" value="DISCONTINUED" />
      </el-select>
      <el-button type="primary" @click="onSearch" style="margin-left: 8px">查询</el-button>
      <el-button @click="onReset">重置</el-button>
      <el-button type="success" @click="onExport" style="margin-left: 8px">导出 Excel</el-button>
      <el-button type="primary" @click="openCreateDialog" style="float: right">新增产品</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="page.data" border style="margin-top: 12px">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="productCode" label="编码" width="120" />
      <el-table-column prop="productName" label="名称" />
      <el-table-column label="产品线" width="120">
        <template #default="{ row }">{{ dictMap.product_line[row.productLine] || row.productLine }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="search.page"
      v-model:page-size="search.size"
      :page-sizes="[20, 50, 100]"
      :total="page.total"
      layout="total, sizes, prev, pager, next, jumper"
      @current-change="loadList"
      @size-change="loadList"
      style="margin-top: 12px; justify-content: flex-end"
    />

    <!-- 新增/编辑 弹窗（含双签选择器） -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="560px" @closed="resetForm">
      <el-form :model="form" label-width="100px" :rules="rules" ref="formRef">
        <el-form-item label="产品编码" prop="productCode">
          <el-input v-model="form.productCode" :disabled="dialog.mode === 'edit'" placeholder="如 8333" />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input v-model="form.productName" placeholder="如 8333 多参数监护仪" />
        </el-form-item>
        <el-form-item label="产品线">
          <el-select v-model="form.productLine" placeholder="请选择产品线" clearable style="width: 100%">
            <el-option v-for="d in dictItems.product_line" :key="d.dictCode" :label="d.dictName" :value="d.dictCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="在产" value="ACTIVE" />
            <el-option label="开发中" value="DEVELOPMENT" />
            <el-option label="停产" value="DISCONTINUED" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="第二签名人" prop="secondSignerId">
          <el-select v-model="form.secondSignerId" placeholder="21 CFR Part 11 §11.200 双签约束" filterable style="width: 100%">
            <el-option v-for="u in adminUsers" :key="u.id" :label="`${u.username} (${u.realName || ''})`" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit" :loading="dialog.loading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * R199 v1.62: 产品管理列表页
 * - CRUD + Excel 导出
 * - 所有写操作强制双签（21 CFR Part 11 §11.200）
 * - 字典从 DictItem API 加载（参考 MEMORY.md 字段结构）
 */
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi, Product, ProductCreateRequest, ProductUpdateRequest } from '@/api/product'
import { systemApi } from '@/api/system'  // 用户 + 字典（替代不存在的 dictApi/userApi）

const search = reactive({ keyword: '', productLine: '', status: '', page: 1, size: 20 })
const page = reactive({ data: [] as Product[], total: 0 })

const dictItems = reactive<{ product_line: any[] }>({ product_line: [] })
const dictMap = computed(() => ({
  product_line: Object.fromEntries(dictItems.product_line.map((d: any) => [d.dictCode, d.dictName]))
}))

const adminUsers = ref<any[]>([])

const dialog = reactive({ visible: false, mode: 'create' as 'create' | 'edit', title: '', loading: false })
const form = reactive<any>({
  id: null, productCode: '', productName: '', productLine: '', status: 'ACTIVE',
  description: '', secondSignerId: null
})
const formRef = ref()
const rules = {
  productCode: [{ required: true, message: '产品编码不能为空', trigger: 'blur' }],
  productName: [{ required: true, message: '产品名称不能为空', trigger: 'blur' }],
  secondSignerId: [{ required: true, message: '双签约束需选择第二签名人', trigger: 'change' }]
}

const statusLabel = (s: string) => ({ ACTIVE: '在产', DEVELOPMENT: '开发中', DISCONTINUED: '停产' } as any)[s] || s
const statusTagType = (s: string) => ({ ACTIVE: 'success', DEVELOPMENT: 'warning', DISCONTINUED: 'info' } as any)[s] || ''

const loadList = async () => {
  const res: any = await productApi.list({
    keyword: search.keyword || undefined,
    productLine: search.productLine || undefined,
    status: search.status || undefined,
    page: search.page - 1,
    size: search.size
  })
  // R231.2 CONTRACT-009：前端固定 Result<PageResult<Product>> 类型断言，不再用魔法兜底
  const apiBody = res.data
  if (apiBody?.code !== 200) throw new Error(apiBody?.message || '加载失败')
  const pageResult = apiBody.data  // 后端返回 Result<PageResult<Product>>，data 是 PageResult
  page.data = Array.isArray(pageResult?.data) ? pageResult.data : []
  page.total = pageResult?.total ?? 0
}

const onSearch = () => { search.page = 1; loadList() }
const onReset = () => { search.keyword = ''; search.productLine = ''; search.status = ''; onSearch() }

const onExport = async () => {
  try {
    const res: any = await productApi.export({
      keyword: search.keyword || undefined,
      productLine: search.productLine || undefined
    })
    const blob = new Blob([res.data || res], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '产品清单.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e.message || ''))
  }
}

const openCreateDialog = () => {
  dialog.mode = 'create'
  dialog.title = '新增产品'
  dialog.visible = true
}

const openEditDialog = (row: Product) => {
  dialog.mode = 'edit'
  dialog.title = '编辑产品'
  Object.assign(form, row)
  dialog.visible = true
}

const resetForm = () => {
  Object.assign(form, {
    id: null, productCode: '', productName: '', productLine: '', status: 'ACTIVE',
    description: '', secondSignerId: null
  })
  formRef.value?.clearValidate()
}

const onSubmit = async () => {
  await formRef.value.validate()
  dialog.loading = true
  try {
    if (dialog.mode === 'create') {
      const req: ProductCreateRequest = {
        productCode: form.productCode,
        productName: form.productName,
        productLine: form.productLine,
        status: form.status,
        description: form.description
      }
      await productApi.create(req, form.secondSignerId)
      ElMessage.success('创建成功')
    } else {
      const req: ProductUpdateRequest = {
        productName: form.productName,
        productLine: form.productLine,
        status: form.status,
        description: form.description
      }
      await productApi.update(form.id, req, form.secondSignerId)
      ElMessage.success('编辑成功')
    }
    dialog.visible = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e.message || '操作失败')
  } finally {
    dialog.loading = false
  }
}

const onDelete = async (row: Product) => {
  try {
    await ElMessageBox.confirm(`确认删除产品「${row.productCode} ${row.productName}」？此操作需双签。`, '警告', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    // 双签：弹窗让用户输入第二签名人 ID（简化：复用 dialog 流程）
    const { value: secondSignerId } = await ElMessageBox.prompt(
      '请输入第二签名人用户 ID（21 CFR Part 11 §11.200）', '双签确认', {
        confirmButtonText: '确定', cancelButtonText: '取消', inputPattern: /^\d+$/, inputErrorMessage: '必须是数字'
      }
    )
    await productApi.delete(row.id!, Number(secondSignerId))
    ElMessage.success('删除成功')
    loadList()
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.response?.data?.message || e.message || '删除失败')
    }
  }
}

const loadDicts = async () => {
  try {
    const res: any = await systemApi.getDicts('product_line')
    // Axios 拦截器返回完整 response：res = {data: {code:200, data:[...]}}
    const arr = res.data?.data || res.data || []
    dictItems.product_line = Array.isArray(arr) ? arr : []
  } catch (e) {
    console.warn('加载产品线字典失败', e)
  }
}

const loadAdminUsers = async () => {
  try {
    const res: any = await systemApi.getUsers({ role: 'ADMIN,PD' })
    // Axios 拦截器返回完整 response：res = {data: {code:200, data:[...]}}
    const arr = res.data?.data || res.data || []
    adminUsers.value = Array.isArray(arr) ? arr : []
  } catch (e) {
    console.warn('加载用户列表失败', e)
  }
}

onMounted(() => {
  loadDicts()
  loadAdminUsers()
  loadList()
})
</script>

<style scoped>
.product-list { padding: 16px; }
.search-bar { display: flex; align-items: center; flex-wrap: wrap; }
</style>