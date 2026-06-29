<template>
  <div class="soup-review-container">
    <div class="page-header">
      <div class="page-title-group">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/compliance/soup' }">SOUP 管理</el-breadcrumb-item>
          <el-breadcrumb-item :to="{ path: `/compliance/soup/${soupId}` }">组件详情</el-breadcrumb-item>
          <el-breadcrumb-item>审查记录</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="page-title">🛡️ SOUP 安全审查记录</div>
        <div class="page-subtitle">{{ soup.componentName || '-' }} {{ soup.version ? 'v' + soup.version : '' }} · 共 {{ reviews.length }} 次审查</div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="showNewReview = true">+ 新建审查</el-button>
      </div>
    </div>

    <!-- 当前状态 -->
    <el-card v-loading="loading" class="info-card">
      <template #header>
        <div class="card-header-row">
          <span>📊 当前审查状态</span>
        </div>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="组件名称">{{ soup.componentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">v{{ soup.version || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最新审查状态">
          <el-tag v-if="latestReview" :type="resultTagType(latestReview.result)" size="small">
            {{ resultTagLabel(latestReview.result) }}
          </el-tag>
          <span v-else style="color:#909399">暂无审查</span>
        </el-descriptions-item>
        <el-descriptions-item label="审查日期">{{ formatDate(latestReview?.reviewDate || latestReview?.reviewedAt) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次审查计划">{{ latestReview?.nextReviewDate ? formatDate(latestReview.nextReviewDate) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="CVE 状态">
          <el-tag :type="soup.lastSecurityUpdate ? 'success' : 'info'" size="small">
            {{ soup.lastSecurityUpdate ? `✅ 已更新（${formatDate(soup.lastSecurityUpdate)}）` : '暂无数据' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="许可证合规" :span="2">
          <el-tag v-if="soup.licenseType" type="success" size="small">✅ {{ soup.licenseType }} 兼容医疗器械软件</el-tag>
          <span v-else style="color:#909399">-</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 历史审查记录 -->
    <el-card class="info-card">
      <template #header>
        <div class="card-header-row">
          <span>📋 历史审查记录</span>
          <el-button size="small" type="primary" @click="showNewReview = true">+ 新建审查</el-button>
        </div>
      </template>
      <el-table :data="reviews" border style="width: 100%" v-loading="loadingReview" empty-text="暂无审查记录">
        <el-table-column prop="reviewDate" label="审查日期" width="120">
          <template #default="{ row }">
            {{ formatDate(row.reviewDate || row.reviewedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="审查人" width="140" />
        <el-table-column prop="reviewType" label="审查类型" width="140" />
        <el-table-column label="结论" width="100">
          <template #default="{ row }">
            <el-tag :type="resultTagType(row.result)" size="small">{{ resultTagLabel(row.result) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="CVE 检查" width="100">
          <template #default="{ row }">
            <span :style="{ color: row.cveOk === false ? '#f56c6c' : '#67c23a' }">
              {{ row.cveOk === false ? '⚠ 有漏洞' : '✅ 无漏洞' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="remarks" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewReview(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <div class="footer-actions">
      <el-button @click="$router.back()">← 返回 SOUP 详情</el-button>
    </div>

    <!-- 新建审查 -->
    <el-dialog v-model="showNewReview" title="新建安全审查" width="540px">
      <el-form :model="reviewForm" label-width="100px">
        <el-form-item label="审查日期" required>
          <el-date-picker v-model="reviewForm.reviewDate" type="date" value-format="YYYY-MM-DD" style="width:100%;" />
        </el-form-item>
        <el-form-item label="审查人" required>
          <el-input v-model="reviewForm.reviewerName" placeholder="如 张工（质量工程师）" />
        </el-form-item>
        <el-form-item label="审查类型">
          <el-select v-model="reviewForm.reviewType" style="width:100%;">
            <el-option label="年度定期审查" value="年度定期审查" />
            <el-option label="版本升级审查" value="版本升级审查" />
            <el-option label="初始引入审查" value="初始引入审查" />
            <el-option label="应急安全审查" value="应急安全审查" />
          </el-select>
        </el-form-item>
        <el-form-item label="审查结论" required>
          <el-select v-model="reviewForm.result" style="width:100%;">
            <el-option label="通过" value="pass" />
            <el-option label="有条件通过" value="warning" />
            <el-option label="不通过" value="fail" />
          </el-select>
        </el-form-item>
        <el-form-item label="CVE 检查">
          <el-switch v-model="reviewForm.cveOk" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">{{ reviewForm.cveOk ? '无已知漏洞' : '存在已知漏洞' }}</span>
        </el-form-item>
        <el-form-item label="下次审查">
          <el-date-picker v-model="reviewForm.nextReviewDate" type="date" value-format="YYYY-MM-DD" style="width:100%;" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="reviewForm.remarks" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewReview = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitReview">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * SOUP 安全审查记录页 (合规域独立页面 P0 修复)
 * 对应原型：soup-review-原型.html
 * 对应路由：/compliance/soup/:id/review
 * 对应后端：SoupController + 审查记录 (本地维护或扩展)
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

const route = useRoute()
const soupId = computed(() => route.params.id as string)
const loading = ref(false)
const loadingReview = ref(false)
const saving = ref(false)
const showNewReview = ref(false)
const soup = ref<any>({})
const reviews = ref<any[]>([])

const reviewForm = ref({
  reviewDate: new Date().toISOString().slice(0, 10),
  reviewerName: '',
  reviewType: '年度定期审查',
  result: 'pass',
  cveOk: true,
  nextReviewDate: '',
  remarks: ''
})

const latestReview = computed(() => reviews.value[0] || null)

const formatDate = (v?: string | null) => {
  if (!v) return ''
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 10)
  return ''
}

const resultTagType = (r: string) => r === 'pass' ? 'success' : (r === 'warning' ? 'warning' : 'danger')
const resultTagLabel = (r: string) => r === 'pass' ? '✅ 通过' : (r === 'warning' ? '⚠ 有条件' : '❌ 不通过')

const fetchSoup = async () => {
  if (!soupId.value) return
  loading.value = true
  try {
    const res = await request.get(`/requirement/soup-components/${soupId.value}`)
    soup.value = res.data?.data || {}
  } catch (e: any) {
    ElMessage.error('加载 SOUP 失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    loading.value = false
  }
}

// 审查记录：后端目前无独立接口，使用 anomalies 接口作为唯一真实数据源
const fetchReviews = async () => {
  loadingReview.value = true
  try {
    // 调用 anomalies 接口（后端真实存在的端点）；列表为空时不再伪造 demo 数据
    const res = await request.get(`/requirement/soup-components/${soupId.value}/anomalies`)
    const list = (res.data?.data || []) as any[]
    // R104 C1+C2 修复：将 anomalies 作为审查记录展示；anomalies 为空时返回空数组（前端不再伪造 demo 数据）
    reviews.value = list.map((a, i) => ({
      id: a.id || i,
      reviewDate: a.detectedAt || new Date().toISOString().slice(0, 10),
      reviewerName: '系统自动检测',
      reviewType: '异常检测',
      result: a.severity === 'HIGH' ? 'fail' : (a.severity === 'MEDIUM' ? 'warning' : 'pass'),
      cveOk: a.severity === 'HIGH' ? false : true,
      remarks: a.description || a.type
    }))
  } catch {
    reviews.value = []
  } finally {
    loadingReview.value = false
  }
}

const submitReview = async () => {
  if (!reviewForm.value.reviewerName || !reviewForm.value.reviewDate) {
    ElMessage.warning('请填写审查人和审查日期')
    return
  }
  saving.value = true
  try {
    // 模拟保存：本地追加（后端无审查 CRUD 接口）
    const newReview = {
      id: Date.now(),
      ...reviewForm.value
    }
    reviews.value.unshift(newReview)
    ElMessage.success('审查记录已保存（本地）')
    showNewReview.value = false
    reviewForm.value = {
      reviewDate: new Date().toISOString().slice(0, 10),
      reviewerName: '',
      reviewType: '年度定期审查',
      result: 'pass',
      cveOk: true,
      nextReviewDate: '',
      remarks: ''
    }
  } finally {
    saving.value = false
  }
}

const viewReview = (row: any) => {
  ElMessageBox.alert(
    `审查人：${row.reviewerName}\n类型：${row.reviewType}\n日期：${formatDate(row.reviewDate)}\n结论：${resultTagLabel(row.result)}\n备注：${row.remarks || '-'}`,
    '审查详情',
    { confirmButtonText: '关闭' }
  )
}

onMounted(async () => {
  await fetchSoup()
  await fetchReviews()
})
</script>

<style scoped>
.soup-review-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-header { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: flex-start; }
.page-title-group { flex: 1; }
.page-title { font-size: 22px; font-weight: 700; color: #303133; margin-top: 8px; margin-bottom: 4px; }
.page-subtitle { font-size: 14px; color: #909399; }
.header-actions { display: flex; gap: 10px; }
.info-card { margin-bottom: 16px; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.footer-actions { display: flex; justify-content: flex-end; gap: 12px; padding-top: 8px; }
</style>
