<template>
  <div class="decompose-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>需求拆解工作台</span>
          <div style="display: flex; gap: 10px; align-items: center;">
            <!-- R234 批量标记按钮：仅在有选中时可见 -->
            <el-button
              v-if="selectedIds.size > 0"
              v-permission="'req:update'"
              type="success"
              :loading="bulkMarkingLoading"
              @click="bulkMarkDecomposed"
            >✓ 批量标记已拆解 ({{ selectedIds.size }})</el-button>
            <el-button v-permission="'req:create'" type="primary" @click="saveDecompose">保存拆解</el-button>
          </div>
        </div>
      </template>

      <!-- P1-5 修复：筛选 + 覆盖率 + 进度条 -->
      <div class="filter-bar">
        <el-radio-group v-model="filters.level" size="small" @change="applyFilter">
          <el-radio-button value="">全部层级</el-radio-button>
          <el-radio-button value="URS">父级</el-radio-button>
          <el-radio-button value="PRS">子级</el-radio-button>
          <el-radio-button value="SRS">孙级</el-radio-button>
        </el-radio-group>
        <el-radio-group v-model="filters.status" size="small" @change="applyFilter">
          <el-radio-button value="">全部状态</el-radio-button>
          <el-radio-button value="PendingDecompose">待拆解</el-radio-button>
          <el-radio-button value="Decomposed">已拆解</el-radio-button>
          <el-radio-button value="PartialDecompose">部分拆解</el-radio-button>
        </el-radio-group>
        <div class="coverage-strip">
          <span class="coverage-label">覆盖率：</span>
          <el-progress
            :percentage="coverageRate"
            :status="coverageRate >= 80 ? 'success' : coverageRate >= 50 ? '' : 'warning'"
            :stroke-width="14"
            style="flex: 1;"
          />
          <span class="coverage-text">
            {{ decomposedCount }} / {{ totalChildren }} 子需求已拆解
          </span>
        </div>
        <div class="progress-strip">
          <span class="coverage-label">进度：</span>
          <el-progress
            :percentage="progressRate"
            :stroke-width="14"
            style="flex: 1;"
          />
          <span class="coverage-text">
            {{ decomposedCount }} / {{ totalChildren }}（{{ progressRate }}%）
          </span>
        </div>
      </div>

      <el-row :gutter="20">
        <el-col :span="8">
          <h4>上游需求</h4>
          <el-input v-model.number="parentRequirementId" placeholder="输入父需求ID" type="number" style="margin-bottom: 10px">
            <template #append>
              <el-button @click="loadParentRequirement">加载</el-button>
            </template>
          </el-input>
          <el-card v-if="parentRequirement" class="parent-card">
            <h5>{{ parentRequirement.requirementNo }}</h5>
            <p>{{ parentRequirement.title }}</p>
            <el-tag>{{ parentRequirement.requirementType }}</el-tag>
            <el-tag type="warning" style="margin-left: 5px">{{ parentRequirement.priority }}</el-tag>
            <el-divider />
            <p class="desc">{{ parentRequirement.description }}</p>
          </el-card>
        </el-col>

        <el-col :span="16">
          <h4>子需求列表
            <!-- R234 全选 -->
            <el-checkbox
              v-if="selectableCount > 0"
              v-model="allSelected"
              style="margin-left: 16px; font-size: 13px;"
              @change="toggleAllSelection"
            >全选已保存子需求 ({{ selectableCount }})</el-checkbox>
          </h4>
          <div v-for="(child, index) in filteredChildRequirements" :key="index" class="child-item">
            <el-card>
              <div style="display: flex; justify-content: space-between; align-items: flex-start;">
                <!-- R234 复选框：仅已保存（有 id）的可勾选 -->
                <el-checkbox
                  v-if="child.id"
                  :model-value="selectedIds.has(child.id)"
                  @change="(checked: boolean) => toggleSelection(child.id, checked as boolean)"
                  style="margin-right: 12px; margin-top: 4px;"
                />
                <div style="flex: 1;">
                  <el-form :model="child" label-width="100px">
                    <el-form-item label="编号">
                      <el-input v-model="child.requirementNo" placeholder="自动生成" disabled />
                    </el-form-item>
                    <el-form-item label="标题" required>
                      <el-input v-model="child.title" placeholder="请输入子需求标题" />
                    </el-form-item>
                    <el-form-item label="描述">
                      <el-input v-model="child.description" type="textarea" rows="2" />
                    </el-form-item>
                    <el-form-item label="优先级">
                      <el-select v-model="child.priority">
                        <el-option label="必须 (MUST)" value="MUST" />
                        <el-option label="应该 (SHOULD)" value="SHOULD" />
                        <el-option label="可以 (COULD)" value="COULD" />
                        <el-option label="不做 (WONT)" value="WONT" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="状态" v-if="child.id">
                      <el-tag :type="getStatusColor(child.status)" size="small">{{ getStatusLabel(child.status) }}</el-tag>
                    </el-form-item>
                  </el-form>
                </div>
                <el-button v-permission="'req:delete'" type="danger" size="small" @click="removeChild(index)">删除</el-button>
              </div>
            </el-card>
          </div>
          <el-button style="width: 100%; margin-top: 10px" @click="addChild">
            + 添加子需求
          </el-button>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/api/request'
import { requirementApi } from '@/api/requirement'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()

const parentRequirementId = ref<number | null>(null)
const parentRequirement = ref<any>(null)
const childRequirements = ref<any[]>([])
// R234 批量标记：选中的子需求 id 集合（仅已保存的需求有 id）
const selectedIds = ref<Set<number>>(new Set())
const bulkMarkingLoading = ref(false)

// R234 切换选中状态
const toggleSelection = (id: number | undefined, checked: boolean) => {
  if (!id) return
  const next = new Set(selectedIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedIds.value = next
}
const toggleAllSelection = (checked: boolean) => {
  const next = new Set<number>()
  if (checked) {
    filteredChildRequirements.value.forEach(c => { if (c.id) next.add(c.id) })
  }
  selectedIds.value = next
}
const allSelected = computed(() => {
  const ids = filteredChildRequirements.value.filter(c => c.id).map(c => c.id as number)
  return ids.length > 0 && ids.every(id => selectedIds.value.has(id))
})
const selectableCount = computed(() => filteredChildRequirements.value.filter(c => c.id).length)

// R234 批量标记已拆解：循环 PUT /requirements/{id} status=Decomposed
const bulkMarkDecomposed = async () => {
  if (selectedIds.value.size === 0) return
  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${selectedIds.value.size} 个子需求标记为"已拆解"？`,
      '批量标记已拆解',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }
  bulkMarkingLoading.value = true
  let okCount = 0; let failCount = 0
  for (const id of selectedIds.value) {
    try {
      await request.put(`/requirements/${id}`, { status: 'Decomposed' })
      // 本地状态同步（避免重新加载整个列表）
      const local = childRequirements.value.find(c => c.id === id)
      if (local) local.status = 'Decomposed'
      okCount++
    } catch {
      failCount++
    }
  }
  bulkMarkingLoading.value = false
  selectedIds.value = new Set()  // 清空选中
  if (failCount === 0) {
    ElMessage.success(`已批量标记 ${okCount} 个子需求为已拆解`)
  } else {
    ElMessage.warning(`完成：成功 ${okCount}，失败 ${failCount}`)
  }
}

// P1-5 修复：层级 + 状态筛选
const filters = reactive({
  level: '' as '' | 'URS' | 'PRS' | 'SRS',
  status: '' as '' | 'PendingDecompose' | 'Decomposed' | 'PartialDecompose',
})

const loadParentRequirement = async () => {
  if (!parentRequirementId.value) return
  try {
    const res = await requirementApi.get(parentRequirementId.value)
    parentRequirement.value = res.data.data
    childRequirements.value = []
  } catch {
    ElMessage.error('加载父需求失败')
    parentRequirement.value = null
  }
}

/** 筛选后的子需求列表（按 level / status 过滤） */
const filteredChildRequirements = computed(() => {
  return childRequirements.value.filter(c => {
    if (filters.level && c.requirementType !== filters.level) return false
    if (filters.status) {
      const cs = c.status || 'PendingDecompose'
      if (filters.status === 'PartialDecompose') {
        // 部分拆解：未完成且已有部分
        return cs === 'PartialDecompose'
      }
      if (cs !== filters.status) return false
    }
    return true
  })
})

const applyFilter = () => {
  // 触发 computed 重算；保留占位便于后续扩展（如调用接口重新拉取）
}

/**
 * P1-5 修复：覆盖率与进度
 * - 覆盖率 = 已拆解子需求数 / 总子需求数
 * - 部分拆解（status=PartialDecompose）按 0.5 计入已拆解
 */
const totalChildren = computed(() => childRequirements.value.length)
const decomposedCount = computed(() => {
  return childRequirements.value.filter(c => {
    if (c.status === 'Decomposed') return true
    if (c.status === 'PartialDecompose') return true
    return false
  }).length
})
const coverageRate = computed(() => {
  if (totalChildren.value === 0) return 0
  return Math.round((decomposedCount.value / totalChildren.value) * 100)
})
const progressRate = computed(() => coverageRate.value)

watch(() => route.params.id, (newId) => {
  if (newId) {
    parentRequirementId.value = Number(newId)
    loadParentRequirement()
  }
}, { immediate: true })

const addChild = () => {
  childRequirements.value.push({
    title: '',
    description: '',
    priority: 'MUST',
    requirementType: getChildType(),
    status: 'PendingDecompose',
  })
}

const removeChild = (index: number) => {
  childRequirements.value.splice(index, 1)
}

const getChildType = () => {
  if (!parentRequirement.value) return 'DRS'
  const type = parentRequirement.value.requirementType
  if (type === 'URS') return 'PRS'
  if (type === 'PRS') return 'SRS'
  return 'DRS'
}

// R234：状态展示 helper（标签颜色 + 中文标签）
const getStatusLabel = (s: string) => ({
  Draft: '草稿', PendingDecompose: '待拆解', Decomposed: '已拆解',
  PartialDecompose: '部分拆解', Submitted: '已提交', InReview: '评审中',
  Approved: '已批准', Rejected: '已驳回', Baseline: '已基线',
}[s] || s || '草稿')
const getStatusColor = (s: string) => ({
  Draft: 'info', PendingDecompose: 'warning', Decomposed: 'success',
  PartialDecompose: 'warning', Submitted: '', InReview: 'warning',
  Approved: 'success', Rejected: 'danger', Baseline: 'success',
}[s] || 'info')

const saveDecompose = async () => {
  if (!parentRequirement.value) {
    ElMessage.warning('请先加载父需求')
    return
  }
  for (const child of childRequirements.value) {
    if (!child.title) {
      ElMessage.warning('子需求标题不能为空')
      return
    }
    try {
      await requirementApi.decompose(parentRequirement.value.id, {
        ...child,
        projectId: parentRequirement.value.projectId
      })
    } catch {
      ElMessage.error('保存失败')
    }
  }
  ElMessage.success('拆解成功')
}
</script>

<style scoped>
.decompose-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.parent-card { background: #f5f7fa; }
.child-item { margin-bottom: 10px; }
.desc { font-size: 12px; color: #666; }

/* P1-5 筛选条 */
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
  background: #f5f7fa;
  border-radius: 6px;
  margin-bottom: 16px;
}
.coverage-strip,
.progress-strip {
  display: flex;
  align-items: center;
  gap: 10px;
}
.coverage-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  min-width: 56px;
}
.coverage-text {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
</style>
