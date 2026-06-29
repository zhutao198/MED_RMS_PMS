<template>
  <div class="esignature-container">
    <el-card>
      <template #header>
        <span>电子签名 - {{ currentTabLabel }}</span>
      </template>

      <!-- R97 新增：状态 Tab（默认 PENDING 对应 Dashboard 待签字计数） -->
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane
          v-for="t in STATUS_TABS"
          :key="t.value"
          :name="t.value"
          :label="t.label"
        />
      </el-tabs>

      <el-form :inline="true" class="filter-form">
        <el-form-item label="文档类型">
          <el-select v-model="filterType" placeholder="全部" clearable @change="fetchData">
            <el-option label="需求" value="REQUIREMENT" />
            <el-option label="变更" value="CHANGE" />
            <el-option label="评审" value="REVIEW" />
            <el-option label="基线" value="BASELINE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="fetchData">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="records" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="intentNo" label="意向编号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="documentType" label="文档类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.documentType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="documentId" label="文档ID" width="100" />
        <el-table-column prop="intentCode" label="意向代码" width="120">
          <template #default="{ row }">
            <el-tag type="warning">{{ row.intentCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="meaningCode" label="含义代码" width="120">
          <template #default="{ row }">
            <el-tag size="small">{{ row.meaningCode }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="STATUS_TAG_TYPE[row.status] || 'info'" size="small">
              {{ STATUS_LABEL_ZH[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expiresAt" label="过期时间" width="180">
          <template #default="{ row }">
            <span :style="{ color: isExpired(row.expiresAt) && row.status === 'PENDING' ? '#F56C6C' : '' }">
              {{ row.expiresAt || '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small"
              type="primary"
              @click="goSign(row)"
            >去签</el-button>
            <el-button v-else size="small" @click="viewDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchData"
        @size-change="fetchData"
        style="margin-top: 16px; justify-content: flex-end;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { esignatureApi, type SignatureIntent } from '@/api/esignature'

// R97：签名意图状态选项 + Tab 配置
const STATUS_TABS = [
  { value: 'PENDING', label: '待签字' },
  { value: 'CONSUMED', label: '已签' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'CANCELLED', label: '已取消' },
  { value: '', label: '全部' }
] as const

const STATUS_LABEL_ZH: Record<string, string> = {
  PENDING: '待签字',
  CONSUMED: '已签',
  EXPIRED: '已过期',
  CANCELLED: '已取消'
}
const STATUS_TAG_TYPE: Record<string, string> = {
  PENDING: 'warning',
  CONSUMED: 'success',
  EXPIRED: 'info',
  CANCELLED: ''
}

const route = useRoute()
const router = useRouter()

// 默认 status 从 URL query 读取（R97：Dashboard "待签字" 跳转时带 ?status=PENDING）
const initialStatus = (route.query.status as string) || 'PENDING'
const activeTab = ref(initialStatus)

const currentTabLabel = computed(() => {
  const t = STATUS_TABS.find(s => s.value === activeTab.value)
  return t?.label || '全部'
})

const filterType = ref('')
const records = ref<SignatureIntent[]>([])
const loading = ref(false)
const pagination = reactive({ page: 1, size: 20, total: 0 })

const fetchData = async () => {
  loading.value = true
  try {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}')
    const signerId = currentUser?.id || currentUser?.userId
    const res = await esignatureApi.listIntents({
      signerId: signerId || undefined,
      status: activeTab.value || undefined,
      page: pagination.page - 1, // 后端 0-based
      size: pagination.size
    } as any)
    const allRecords: SignatureIntent[] = (res.data as any)?.data?.records || []
    // 前端内存过滤 documentType（按大小写不敏感，参考 R94）
    let filtered = allRecords
    if (filterType.value) {
      const target = filterType.value.toUpperCase()
      filtered = allRecords.filter(r => r.documentType?.toUpperCase() === target)
    }
    records.value = filtered
    pagination.total = (res.data as any)?.data?.total ?? 0
  } catch (e: any) {
    // R97：列表查询失败不要 ElMessage.error 刷屏，console.warn 即可
    console.warn('[SignatureList] fetchData failed:', e?.message)
    records.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  pagination.page = 1
  // 同步 URL query，便于分享 / 刷新
  router.replace({ query: { ...route.query, status: activeTab.value || undefined } })
  fetchData()
}

const goSign = (row: SignatureIntent) => {
  router.push(`/signature-intent/${row.id}`)
}

const viewDetail = (row: SignatureIntent) => {
  router.push(`/signature-intent/${row.id}`)
}

const isExpired = (expiresAt?: string) => {
  if (!expiresAt) return false
  return new Date(expiresAt).getTime() < Date.now()
}

onMounted(fetchData)
</script>

<style scoped>
.esignature-container { padding: 16px; }
.filter-form { margin-bottom: 16px; }
</style>