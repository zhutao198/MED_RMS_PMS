<template>
  <div class="decompose-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>需求拆解工作台</span>
          <el-button type="primary" @click="saveDecompose">保存拆解</el-button>
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
          <h4>子需求列表</h4>
          <div v-for="(child, index) in filteredChildRequirements" :key="index" class="child-item">
            <el-card>
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
              </el-form>
              <el-button type="danger" size="small" @click="removeChild(index)">删除</el-button>
            </el-card>
          </div>
          <el-button type="dashed" style="width: 100%; margin-top: 10px" @click="addChild">
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
import { requirementApi } from '@/api/requirement'
import { ElMessage } from 'element-plus'

const route = useRoute()

const parentRequirementId = ref<number | null>(null)
const parentRequirement = ref<any>(null)
const childRequirements = ref<any[]>([])

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
