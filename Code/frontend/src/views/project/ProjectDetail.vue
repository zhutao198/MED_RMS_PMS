<template>
  <div class="project-detail-container">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <div class="project-title">
            <h2>{{ project.projectName }}</h2>
            <el-tag :type="getStatusType(project.status)" size="small">{{ getStatusLabel(project.status) }}</el-tag>
          </div>
          <div class="header-actions">
            <el-button @click="$router.push('/projects')">返回列表</el-button>
            <el-button @click="exportExcel">导出 Excel</el-button>
            <el-button @click="showImportDialog = true">导入任务</el-button>
            <el-button @click="showSaveTemplateDialog = true">保存为模板</el-button>
            <el-button type="primary" v-permission="'proj:update'" @click="editProjectBasic">编辑项目</el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="概览" name="overview">
          <div class="stat-cards">
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num">{{ stats.totalRequirements }}</div>
              <div class="stat-label">需求总数</div>
            </el-card>
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num text-success">{{ stats.completedRequirements }}</div>
              <div class="stat-label">已完成</div>
            </el-card>
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num text-warning">{{ stats.inProgressRequirements }}</div>
              <div class="stat-label">进行中</div>
            </el-card>
            <el-card class="stat-card" shadow="hover">
              <div class="stat-num text-primary">{{ stats.overallProgress }}%</div>
              <div class="stat-label">总体进度</div>
            </el-card>
          </div>
          <!-- R175 FR-2.16：健康度评分卡 -->
          <el-card v-if="healthScore" class="health-card" shadow="hover" style="cursor:pointer;" @click="showHealthDetail = true">
            <template #header>
              <div class="card-title">
                <span>📊 项目健康度评分</span>
                <el-tag :type="getHealthLevel(healthScore.score).type" size="small">{{ getHealthLevel(healthScore.score).label }}</el-tag>
              </div>
            </template>
            <div class="health-body">
              <div class="health-score-ring">
                <el-progress type="circle" :percentage="healthScore.score" :status="healthScore.score >= 80 ? 'success' : healthScore.score >= 60 ? 'warning' : 'exception'" :width="100" />
              </div>
              <div class="health-details">
                <div v-for="(v, k) in healthScore.details" :key="k" class="health-row">
                  <span class="health-key">{{ ({ progress: '进度', risk: '风险', quality: '质量', compliance: '合规' } as Record<string, string>)[k] || k }}</span>
                  <el-tag :type="typeof v === 'number' && v >= 80 ? 'success' : typeof v === 'number' && v >= 60 ? 'warning' : 'danger'" size="small">{{ v }}</el-tag>
                </div>
              </div>
            </div>
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="项目编号">{{ project.projectNo }}</el-descriptions-item>
            <el-descriptions-item label="项目经理">{{ project.managerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ getStatusLabel(project.status) }}</el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ project.startDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ project.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="结束日期">{{ project.endDate || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="DCP门控" name="gates">
          <div class="gates-section">
            <div class="section-header">
              <span>门控列表</span>
              <!-- R270：新增 DCP 门控按钮（修复 ProjectDetail DCP 门控 tab 无入口） -->
              <el-button type="primary" size="small" v-permission="'proj:create'" @click="showAddGate = true">新增门控</el-button>
            </div>
            <el-table :data="gates" border stripe>
              <el-table-column prop="gateNo" label="门控编号" width="100" />
              <el-table-column prop="gateName" label="门控名称" min-width="150" />
              <el-table-column prop="gateType" label="门控类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="getGateType(row.gateType)" size="small">{{ row.gateType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="plannedDate" label="计划日期" width="120" />
              <el-table-column prop="actualDate" label="实际日期" width="120" />
              <!-- v1.53 P1-21：签署人 + 签署状态（与电子签名系统打通） -->
              <el-table-column label="签署人" width="120">
                <template #default="{ row }">
                  <span v-if="row.signerName">{{ row.signerName }}</span>
                  <span v-else class="text-muted">未指定</span>
                </template>
              </el-table-column>
              <el-table-column label="签署状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="getSignStatusType(row.signStatus)" size="small">
                    {{ getSignStatusLabel(row.signStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="viewGateDetail(row)">详情</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- R270：新增门控对话框 -->
          <el-dialog v-model="showAddGate" title="新增门控" width="480px">
            <el-form :model="gateForm" label-width="100px">
              <el-form-item label="编号" required>
                <el-input-number v-model="gateForm.gateNo" :min="1" :max="20" style="width:100%" />
              </el-form-item>
              <el-form-item label="名称" required>
                <el-input v-model="gateForm.gateName" />
              </el-form-item>
              <el-form-item label="类型">
                <el-select v-model="gateForm.gateType" style="width:100%">
                  <el-option label="DCP1 概念" value="DCP1" />
                  <el-option label="DCP2 计划" value="DCP2" />
                  <el-option label="DCP3 开发" value="DCP3" />
                  <el-option label="DCP4 验证" value="DCP4" />
                  <el-option label="DCP5 发布" value="DCP5" />
                </el-select>
              </el-form-item>
              <el-form-item label="计划日期">
                <el-date-picker v-model="gateForm.plannedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
              </el-form-item>
              <el-form-item label="签署人">
                <el-select v-model="gateForm.gateReviewerId" placeholder="请选择签署人（按组织架构）" filterable style="width:100%">
                  <el-option
                    v-for="u in userList"
                    :key="u.id"
                    :label="`${u.realName || u.username} (${u.username})`"
                    :value="u.id"
                  >
                    <span style="float:left">{{ u.realName || u.username }}</span>
                    <span style="float:right;color:#909399;font-size:12px;margin-left:8px">
                      {{ u.department || '-' }} · {{ u.role || '-' }}
                    </span>
                  </el-option>
                </el-select>
                <span class="form-tip">关联签署人（按 R255 决策，签名走线下流程）</span>
              </el-form-item>
            </el-form>
            <template #footer>
              <el-button @click="showAddGate = false">取消</el-button>
              <el-button type="primary" :loading="gateSubmitting" @click="submitAddGate">创建</el-button>
            </template>
          </el-dialog>
        </el-tab-pane>

        <el-tab-pane label="成员管理" name="members">
          <div class="members-section">
            <div class="section-header">
              <span>项目成员</span>
              <el-button type="primary" size="small" v-permission="'proj:member'" @click="showAddMember = true">添加成员</el-button>
            </div>
            <el-table :data="members" border stripe>
              <el-table-column prop="realName" label="姓名" width="120" />
              <el-table-column prop="role" label="角色" width="120" />
              <el-table-column prop="department" label="部门" width="150" />
              <el-table-column prop="joinedAt" label="加入日期" width="120" />
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150">
                <template #default="{ row }">
                  <el-button size="small" type="danger" v-permission="'proj:member'" @click="removeMember(row)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="里程碑" name="milestones">
          <div class="milestones-section">
            <div class="section-header">
              <span>里程碑</span>
              <el-button type="primary" size="small" v-permission="'proj:create'" @click="showAddMilestone = true">添加里程碑</el-button>
            </div>
            <el-table :data="milestones" border stripe>
              <el-table-column prop="name" label="名称" min-width="150" />
              <el-table-column prop="gateType" label="门控类型" width="100">
                <template #default="{ row }">
                  <el-tag :type="getGateType(row.gateType)" size="small">{{ row.gateType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="plannedDate" label="计划日期" width="120" />
              <el-table-column prop="actualDate" label="实际日期" width="120" />
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="甘特图" name="gantt">
          <div class="gantt-placeholder">
            <el-button type="primary" @click="$router.push(`/projects/${projectId}/gantt`)">查看甘特图</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="需求任务追溯" name="requirement-tasks">
          <div class="rt-filter-row">
            <el-select v-model="rtFilter.type" placeholder="需求类型" clearable style="width: 120px;" @change="rtFilterKey++">
              <el-option label="URS" value="URS" />
              <el-option label="PRS" value="PRS" />
              <el-option label="SRS" value="SRS" />
              <el-option label="DRS" value="DRS" />
            </el-select>
            <el-input v-model="rtFilter.keyword" placeholder="搜索需求编号/标题" style="width: 220px;" clearable @keyup.enter="rtFilterKey++" />
            <el-button @click="rtFilterKey++">搜索</el-button>
            <el-button @click="loadRequirementTasks" :disabled="rtLoading">刷新</el-button>
          </div>

          <div class="rt-stat-row">
            <el-card shadow="hover" class="rt-stat-card">
              <div class="rt-stat-num">{{ rtFiltered.length }}</div>
              <div class="rt-stat-label">需求数</div>
            </el-card>
            <el-card shadow="hover" class="rt-stat-card">
              <div class="rt-stat-num text-warning">{{ rtTotalTasks }}</div>
              <div class="rt-stat-label">总任务数</div>
            </el-card>
            <el-card shadow="hover" class="rt-stat-card">
              <div class="rt-stat-num text-primary">{{ rtAvgProgress }}%</div>
              <div class="rt-stat-label">平均完成率</div>
            </el-card>
          </div>

          <el-table :data="rtFiltered" v-loading="rtLoading" row-key="requirementId" border style="margin-top: 16px;">
            <el-table-column type="expand" width="40">
              <template #default="{ row }">
                <el-table :data="row.tasks" size="small" border>
                  <el-table-column prop="taskNo" label="任务编号" width="130" />
                  <el-table-column prop="title" label="标题" min-width="180" />
                  <el-table-column prop="status" label="状态" width="130">
                    <template #default="{ row: t }">
                      <el-select :model-value="t.status" size="small" @change="(v: string) => updateTaskStatus(t, v)">
                        <el-option label="待办" value="TODO" />
                        <el-option label="进行中" value="IN_PROGRESS" />
                        <el-option label="已完成" value="DONE" />
                        <el-option label="已阻塞" value="BLOCKED" />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column prop="assigneeName" label="负责人" width="90" />
                  <el-table-column prop="estimatedHours" label="工时(h)" width="80" />
                  <el-table-column prop="startDate" label="开始" width="100" />
                  <el-table-column prop="endDate" label="截止" width="100" />
                </el-table>
              </template>
            </el-table-column>
            <el-table-column prop="requirementNo" label="需求编号" width="140" />
            <el-table-column prop="title" label="需求标题" min-width="180" />
            <el-table-column prop="requirementType" label="类型" width="80">
              <template #default="{ row }">
                <el-tag :type="rtTypeColor(row.requirementType)" size="small">{{ row.requirementType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="任务进度" width="200">
              <template #default="{ row }">
                <div class="rt-progress-cell">
                  <el-progress :percentage="row.progress" :status="row.progress >= 100 ? 'success' : row.blocked > 0 ? 'exception' : undefined" :stroke-width="14" />
                  <span class="rt-progress-text">{{ row.done }}/{{ row.totalTasks }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="负责人" width="120">
              <template #default="{ row }">{{ row.assigneeNames?.join(', ') || '-' }}</template>
            </el-table-column>
            <el-table-column prop="totalEstimatedHours" label="总工时(h)" width="80" />
          </el-table>
          <el-empty v-if="!rtLoading && rtFiltered.length === 0" description="暂无需求转化任务" />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 添加成员对话框（R279：基于组织架构下拉选择） -->
    <el-dialog v-model="showAddMember" title="添加成员" width="480px">
      <el-form :model="memberForm" label-width="80px">
        <el-form-item label="成员">
          <el-select
            v-model="memberForm.userId"
            placeholder="请选择组织架构用户"
            filterable
            style="width:100%"
            @change="onMemberSelect"
          >
            <el-option
              v-for="u in userList"
              :key="u.id"
              :label="`${u.realName || u.username} (${u.username})`"
              :value="u.id"
            >
              <span style="float:left">{{ u.realName || u.username }}</span>
              <span style="float:right;color:#909399;font-size:12px;margin-left:8px">
                {{ u.department || '-' }} · {{ u.role || '-' }}
              </span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="memberForm.realName" disabled />
        </el-form-item>
        <el-form-item label="角色">
          <el-input v-model="memberForm.role" disabled />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="memberForm.department" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMember = false">取消</el-button>
        <el-button type="primary" @click="addMember">确定</el-button>
      </template>
    </el-dialog>

    <!-- 添加里程碑对话框 -->
    <el-dialog v-model="showAddMilestone" title="添加里程碑" width="400px">
      <el-form :model="milestoneForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="milestoneForm.name" />
        </el-form-item>
        <el-form-item label="门控类型">
          <el-select v-model="milestoneForm.gateType">
            <el-option label="DCP1" value="DCP1" />
            <el-option label="DCP2" value="DCP2" />
            <el-option label="DCP3" value="DCP3" />
            <el-option label="DCP4" value="DCP4" />
            <el-option label="DCP5" value="DCP5" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="milestoneForm.plannedDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMilestone = false">取消</el-button>
        <el-button type="primary" @click="addMilestone">确定</el-button>
      </template>
    </el-dialog>

    <!-- 保存为模板对话框 -->
    <el-dialog v-model="showSaveTemplateDialog" title="保存为项目模板" width="400px">
      <el-form :model="templateForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.templateName" placeholder="输入模板名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSaveTemplateDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveAsTemplate">确定保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入任务对话框 -->
    <el-dialog v-model="showImportDialog" title="导入任务" width="500px">
      <el-upload drag action="" :before-upload="handleImportUpload" accept=".xlsx,.xls,.json">
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 .xlsx / .xls / .json 格式，第一行必须是表头</div>
        </template>
      </el-upload>
      <div v-if="importErrors.length > 0" style="margin-top: 12px;">
        <el-alert title="导入错误" type="error" :description="importErrors.join('; ')" show-icon />
      </div>
    </el-dialog>

    <!-- 健康度详情弹窗 -->
    <el-dialog v-model="showHealthDetail" title="健康度评分详情" width="500px">
      <div v-if="healthScore">
        <el-table :data="healthDimensionRows" border stripe>
          <el-table-column prop="label" label="维度" width="120" />
          <el-table-column prop="score" label="评分" width="100">
            <template #default="{ row }">
              <el-tag :type="row.score >= 80 ? 'success' : row.score >= 60 ? 'warning' : 'danger'" size="small">{{ row.score }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="desc" label="说明" />
        </el-table>
        <div style="margin-top:16px; text-align:center;">
          <div style="font-size:36px; font-weight:700;">{{ healthScore.score }}</div>
          <div>总分（{{ healthScore.level === 'GREEN' ? '健康' : healthScore.level === 'YELLOW' ? '需关注' : '危险' }}）</div>
        </div>
      </div>
    </el-dialog>

    <!-- 编辑项目对话框 -->
    <el-dialog v-model="showEditProject" title="编辑项目" width="500px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="项目名称" required>
          <el-input v-model="editForm.projectName" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width:100%">
            <el-option label="计划中" value="PLANNING" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已终止" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始日期">
          <el-date-picker v-model="editForm.startDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="editForm.endDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditProject = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveProjectEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { projectApi, ipdGateApi, projectMemberApi, type Project, type IpdGate, type ProjectMember } from '@/api/project'
import request from '@/api/request'
import { requirementApi } from '@/api/requirement'
import { systemApi, type User } from '@/api/system'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useProjectStore } from '@/stores/project'
import * as XLSX from 'xlsx'

const route = useRoute()
const router = useRouter()
const projectId = ref(Number(route.params.id))
// R105 D1 修复：从 userStore 取当前操作者 userId（之前硬编码 1 会让新成员都关联到 admin）
const userStore = useUserStore()

const loading = ref(false)
const activeTab = ref('info')
const project = ref<Project>({} as Project)
const stats = ref({ totalRequirements: 0, completedRequirements: 0, inProgressRequirements: 0, overallProgress: 0 })
const gates = ref<IpdGate[]>([])

// R270：新增 DCP 门控（详情页 DCP 门控 tab）
const showAddGate = ref(false)
const gateSubmitting = ref(false)
// R278：签署人下拉列表（从组织架构 systemApi.getUsers 拉取）
const userList = ref<User[]>([])
const gateForm = ref<any>({
  gateNo: 1,
  gateName: '',
  gateType: 'DCP1',
  plannedDate: '',
  gateReviewerId: null,
})
const submitAddGate = async () => {
  if (!gateForm.value.gateName) { ElMessage.warning('请填写门控名称'); return }
  gateSubmitting.value = true
  try {
    await ipdGateApi.create({ projectId: projectId.value, ...gateForm.value, status: 'PENDING' } as any)
    ElMessage.success('门控已创建')
    showAddGate.value = false
    gateForm.value = { gateNo: 1, gateName: '', gateType: 'DCP1', plannedDate: '', gateReviewerId: null }
    fetchGates()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e?.message))
  } finally {
    gateSubmitting.value = false
  }
}

// R278：拉取组织架构用户列表（签署人下拉选项）
const loadUserList = async () => {
  try {
    const res = await systemApi.getUsers({ status: 'ACTIVE' })
    userList.value = res.data?.data || []
  } catch (e) {
    console.error('loadUserList failed', e)
    userList.value = []
  }
}
const members = ref<ProjectMember[]>([])
const milestones = ref<any[]>([])

const showAddMember = ref(false)
const showAddMilestone = ref(false)
const showEditProject = ref(false)
const showSaveTemplateDialog = ref(false)
const showImportDialog = ref(false)
const showHealthDetail = ref(false)
const savingTemplate = ref(false)
const templateForm = ref({ templateName: '' })
const importErrors = ref<string[]>([])

const editForm = ref<{ projectName: string; description: string; startDate: string; endDate: string; status: string }>({
  projectName: '', description: '', startDate: '', endDate: '', status: 'PLANNING'
})
const editSaving = ref(false)

const memberForm = ref<any>({
  userId: null,  // R279：选中的被添加成员 userId
  realName: '',
  role: '',
  department: '',
})

const milestoneForm = ref({
  name: '',
  gateType: 'DCP1',
  plannedDate: '',
})

const fetchProject = async () => {
  loading.value = true
  try {
    const res = await projectApi.get(projectId.value)
    project.value = res.data.data || {}
  } catch {
    ElMessage.error('获取项目详情失败')
  } finally {
    loading.value = false
  }
}

const fetchGates = async () => {
  try {
    const res = await ipdGateApi.listByProject(projectId.value)
    // WHY: 后端字段名兼容 — gateReviewerId/userId 统一映射为 signerId
    gates.value = (res.data.data || []).map((g: any) => ({
      ...g,
      signerId: g.gateReviewerId ?? g.userId ?? g.signerId ?? null,
      signerName: g.signerName ?? g.gateReviewerName ?? g.reviewer ?? null,
      signStatus: g.signStatus ?? (g.status === 'COMPLETED' ? 'SIGNED' : g.status === 'REJECTED' ? 'REJECTED' : 'UNSIGNED'),
    }))
  } catch {
    // ignore
  }
}

// v1.53 P1-21：签署状态映射
const SIGN_STATUS_MAP: Record<string, { type: string; label: string }> = {
  UNSIGNED: { type: 'info', label: '未签署' },
  SIGNED:   { type: 'success', label: '已签署' },
  REJECTED: { type: 'danger', label: '已拒签' },
}
const getSignStatusType = (s?: string) => (s && SIGN_STATUS_MAP[s]?.type) || 'info'
const getSignStatusLabel = (s?: string) => (s && SIGN_STATUS_MAP[s]?.label) || '未签署'

const fetchMembers = async () => {
  try {
    const res = await projectMemberApi.listByProject(projectId.value)
    members.value = res.data.data || []
  } catch {
    // ignore
  }
}

const fetchMilestones = async () => {
  try {
    const res = await request.get(`/gantt/milestones/project/${projectId.value}`)
    milestones.value = res.data?.data || []
  } catch {
    milestones.value = []
  }
}

const fetchProjectStats = async () => {
  try {
    // R275：合并为单次 `/requirements` 调用（之前 size=1 + size=1000 两次 → 现在 size=1000 一次）
    // total 从 data.total 取，records 计算 completed/inProgress
    const res = await request.get(`/requirements`, {
      params: { projectId: projectId.value, page: 0, size: 1000 }
    })
    const data = res.data?.data || {}
    const total = data.total || 0
    const all = data.records || []
    const completed = all.filter((r: any) => ['Verified', 'Baseline', 'Closed'].includes(r.status)).length
    const inProgress = all.filter((r: any) => ['Submitted', 'InReview', 'Approved', 'Implemented', 'InProgress', 'InTest'].includes(r.status)).length
    stats.value = {
      totalRequirements: total,
      completedRequirements: completed,
      inProgressRequirements: inProgress,
      overallProgress: total > 0 ? Math.round((completed / total) * 100) : 0
    }
  } catch {
    stats.value = { totalRequirements: 0, completedRequirements: 0, inProgressRequirements: 0, overallProgress: 0 }
  }
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    PLANNING: 'info', IN_PROGRESS: 'primary', COMPLETED: 'success', TERMINATED: 'danger'
  }
  return map[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PLANNING: '计划中', IN_PROGRESS: '进行中', COMPLETED: '已完成', TERMINATED: '已终止'
  }
  return map[status] || status
}

const getGateType = (gateType: string) => {
  const map: Record<string, string> = {
    DCP1: 'primary', DCP2: 'success', DCP3: 'warning', DCP4: 'danger', DCP5: 'info'
  }
  return map[gateType] || 'info'
}

const viewGateDetail = async (gate: IpdGate) => {
  if (!gate.id) {
    ElMessage.warning('门控尚未保存，无详情')
    return
  }
  try {
    const res = await request.get(`/gantt/gate/${gate.id}/check`)
    const data = res.data?.data
    if (data) {
      const verdict = data.verdict || data.checkResult || 'UNKNOWN'
      const passed = data.passedItems ?? data.passedCount ?? 0
      const total = data.totalItems ?? data.totalCount ?? 0
      ElMessageBox.alert(`门控：${gate.gateName}\n判定：${verdict}\n通过项 ${passed}/${total}`, '门控自动检查', { type: verdict === 'PASS' ? 'success' : 'warning' })
    } else {
      ElMessage.info(`门控：${gate.gateName}\n状态：${gate.status}`)
    }
  } catch (e: any) {
    ElMessage.info(`门控：${gate.gateName}\n状态：${gate.status}\n计划：${gate.plannedDate || '-'}\n实际：${gate.actualDate || '-'}`)
  }
}

// R279：选择成员后自动填充 realName/role/department
const onMemberSelect = (userId: number) => {
  const u = userList.value.find(x => x.id === userId)
  if (u) {
    memberForm.value.realName = u.realName || u.username
    memberForm.value.role = u.role || ''
    memberForm.value.department = u.department || ''
  }
}

const addMember = async () => {
  if (!memberForm.value.userId) {
    ElMessage.warning('请选择成员')
    return
  }
  try {
    await projectMemberApi.add({
      projectId: projectId.value,
      // R279 修复：userId 应该是选中的被添加成员，不是当前操作人 userStore.userInfo.id
      userId: memberForm.value.userId,
      userId: userStore.userInfo?.id,
      realName: memberForm.value.realName,
      role: memberForm.value.role,
      department: memberForm.value.department,
      status: 'ACTIVE',
    } as any)
    ElMessage.success('添加成功')
    showAddMember.value = false
    memberForm.value = { realName: '', role: 'REQUIREMENT_ENGINEER', department: '' }
    fetchMembers()
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e?.response?.data?.message || e.message))
  }
}

const removeMember = async (member: ProjectMember) => {
  if (!member.id) {
    ElMessage.warning('该成员缺少主键，无法移除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认移除成员 ${member.realName}？`, '移除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await projectMemberApi.remove(member.id)
    ElMessage.success('移除成功')
    fetchMembers()
  } catch (e: any) {
    ElMessage.error('移除失败：' + (e?.response?.data?.message || e.message))
  }
}

const addMilestone = async () => {
  if (!milestoneForm.value.name || !milestoneForm.value.plannedDate) {
    ElMessage.warning('请填写名称与计划日期')
    return
  }
  try {
    await request.post('/gantt/milestones', {
      projectId: projectId.value,
      name: milestoneForm.value.name,
      gateType: milestoneForm.value.gateType,
      plannedDate: milestoneForm.value.plannedDate,
      status: 'PLANNED'
    })
    ElMessage.success('里程碑已添加')
    showAddMilestone.value = false
    milestoneForm.value = { name: '', gateType: 'DCP1', plannedDate: '' }
    fetchMilestones()
  } catch (e: any) {
    ElMessage.error('添加失败：' + (e?.response?.data?.message || e.message))
  }
}

const exportExcel = async () => {
  try {
    const res = await request.get(`/projects/${projectId.value}/export/excel`, { responseType: 'blob' })
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${project.value.projectName}_${new Date().toISOString().split('T')[0]}.xlsx`
    a.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('项目计划已导出为 Excel')
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.response?.data?.message || e.message))
  }
}

const saveAsTemplate = async () => {
  if (!templateForm.value.templateName) {
    ElMessage.warning('请输入模板名称')
    return
  }
  savingTemplate.value = true
  try {
    await request.post(`/projects/${projectId.value}/save-as-template`, null, {
      params: {
        templateName: templateForm.value.templateName,
        operatorId: userStore.userInfo?.id,
        operatorName: userStore.userInfo?.username || userStore.userInfo?.realName
      }
    })
    ElMessage.success('已保存为模板')
    showSaveTemplateDialog.value = false
    templateForm.value = { templateName: '' }
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally {
    savingTemplate.value = false
  }
}

const handleImportUpload = async (file: File) => {
  importErrors.value = []
  try {
    let tasks: any[]
    if (file.name.endsWith('.json')) {
      const text = await file.text()
      const data = JSON.parse(text)
      tasks = data.tasks || data
    } else if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
      const buf = await file.arrayBuffer()
      const workbook = XLSX.read(buf, { type: 'array' })
      const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
      tasks = XLSX.utils.sheet_to_json(firstSheet)
    } else {
      ElMessage.error('不支持的文件格式，请使用 .xlsx / .xls / .json')
      return false
    }
    if (!tasks || tasks.length === 0) {
      ElMessage.warning('文件中没有数据')
      return false
    }
    await request.post(`/projects/${projectId.value}/import-tasks`, tasks, {
      params: {
        operatorId: userStore.userInfo?.id,
        operatorName: userStore.userInfo?.username || userStore.userInfo?.realName
      }
    })
    ElMessage.success(`成功导入 ${tasks.length} 条任务`)
    showImportDialog.value = false
  } catch (e: any) {
    importErrors.value = [e?.response?.data?.message || e.message || '导入失败']
  }
  return false
}

// R175：健康度评分
const healthScore = ref<{ score: number; level: string; details: Record<string, any> } | null>(null)
const loadHealthScore = async () => {
  try {
    const res = await request.get(`/projects/${projectId.value}/health-score`)
    const data = res.data?.data
    if (data) {
      healthScore.value = {
        score: data.totalScore,
        level: data.level,
        details: data.dimensions || {}
      }
      if (data.totalScore < 60) {
        ElMessage.warning('项目健康度评分低于 60，请关注！')
      }
    }
  } catch {}
}

const healthDimensionRows = computed(() => {
  if (!healthScore.value?.details) return []
  const labels: Record<string, string> = { progress: '进度', risk: '风险', quality: '质量', compliance: '合规' }
  return Object.entries(healthScore.value.details).map(([k, v]) => ({
    label: labels[k] || k,
    score: v,
    desc: ''
  }))
})

const getHealthLevel = (s: number) => {
  if (s >= 80) return { type: 'success', label: '健康' }
  if (s >= 60) return { type: 'warning', label: '一般' }
  return { type: 'danger', label: '需关注' }
}

const editProjectBasic = () => {
  editForm.value = {
    projectName: project.value.projectName || '',
    description: project.value.description || '',
    startDate: project.value.startDate || '',
    endDate: project.value.endDate || '',
    status: project.value.status || 'PLANNING'
  }
  showEditProject.value = true
}

const saveProjectEdit = async () => {
  if (!editForm.value.projectName) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  editSaving.value = true
  try {
    await projectApi.update(projectId.value, editForm.value as any)
    ElMessage.success('已保存')
    showEditProject.value = false
    await fetchProject()
  } catch (e: any) {
    ElMessage.error('保存失败：' + (e?.response?.data?.message || e.message))
  } finally {
    editSaving.value = false
  }
}

// --- 需求任务追溯 ---
const rtLoading = ref(false)
const rtData = ref<{ requirement: any; tasks: any[] }[]>([])
const rtFilter = reactive({ type: '', keyword: '' })
const rtFilterKey = ref(0)

const rtFiltered = computed(() => {
  const kw = rtFilter.keyword?.toLowerCase() || ''
  return rtData.value
    .filter(i => !rtFilter.type || i.requirement.requirementType === rtFilter.type)
    .filter(i => !kw || (i.requirement.requirementNo + i.requirement.title).toLowerCase().includes(kw))
    .map(i => {
      const tasks = i.tasks
      const done = tasks.filter((t: any) => t.status === 'DONE').length
      const blocked = tasks.filter((t: any) => t.status === 'BLOCKED').length
      return {
        requirementId: i.requirement.id,
        requirementNo: i.requirement.requirementNo,
        title: i.requirement.title,
        requirementType: i.requirement.requirementType,
        tasks,
        totalTasks: tasks.length,
        done,
        inProgress: tasks.filter((t: any) => t.status === 'IN_PROGRESS').length,
        blocked,
        todo: tasks.filter((t: any) => t.status === 'TODO').length,
        progress: tasks.length > 0 ? Math.round(done / tasks.length * 100) : 0,
        assigneeNames: [...new Set(tasks.map((t: any) => t.assigneeName).filter(Boolean))],
        totalEstimatedHours: tasks.reduce((s: number, t: any) => s + (t.estimatedHours || 0), 0),
        totalActualHours: tasks.reduce((s: number, t: any) => s + (t.actualHours || 0), 0),
      }
    })
})

const rtTotalTasks = computed(() => rtFiltered.value.reduce((s: number, i: any) => s + i.totalTasks, 0))
const rtAvgProgress = computed(() => {
  const items = rtFiltered.value
  return items.length > 0 ? Math.round(items.reduce((s: number, i: any) => s + i.progress, 0) / items.length) : 0
})

const rtTypeColor = (type: string) => ({ URS: 'primary', PRS: 'success', SRS: 'warning', DRS: 'danger' } as Record<string, string>)[type] || 'info'

const loadRequirementTasks = async () => {
  if (!projectId.value) return
  rtLoading.value = true
  try {
    const res = await request.get(`/requirement-tasks/by-project/${projectId.value}`)
    const tasks: any[] = res.data?.data || []
    const grouped = new Map<number, any[]>()
    for (const t of tasks) {
      if (!t.requirementId) continue
      if (!grouped.has(t.requirementId)) grouped.set(t.requirementId, [])
      grouped.get(t.requirementId)!.push(t)
    }
    // R275：用批量需求接口替代 N+1（之前 5 个/chunk 并行 → 现在 1 次）
    const reqRes = await requirementApi.list({ projectId: projectId.value, size: 200 })
    const allReqs: any[] = Array.isArray(reqRes.data?.data)
      ? reqRes.data.data
      : (reqRes.data?.data?.records || [])
    const reqMap = new Map<number, any>()
    for (const r of allReqs) reqMap.set(r.id, r)
    const entries: { requirement: any; tasks: any[] }[] = []
    for (const [rid, ts] of grouped) {
      const req = reqMap.get(rid)
      if (req) entries.push({ requirement: req, tasks: ts })
    }
    rtData.value = entries
  } catch {
    rtData.value = []
  } finally {
    rtLoading.value = false
  }
}

const updateTaskStatus = async (task: any, newStatus: string) => {
  try {
    await request.put(`/requirement-tasks/${task.id}/status`, null, { params: { status: newStatus } })
    task.status = newStatus
    ElMessage.success('任务状态已更新')
    await loadRequirementTasks()
  } catch (e: any) {
    ElMessage.error('更新失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(() => {
  useProjectStore().setCurrentProjectId(projectId.value)
  fetchProject()
  fetchGates()
  fetchMembers()
  fetchMilestones()
  fetchProjectStats()
  loadHealthScore()
  loadRequirementTasks()
  loadUserList()  // R278：拉取签署人下拉选项
})
</script>

<style scoped>
.project-detail-container { padding: 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.project-title { display: flex; align-items: center; gap: 12px; }
.project-title h2 { margin: 0; }
.header-actions { display: flex; gap: 8px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.gantt-placeholder { padding: 40px; text-align: center; }
.health-card { margin-top: 16px; }
.health-body { display: flex; gap: 24px; align-items: center; }
.health-score-ring { flex-shrink: 0; }
.health-details { flex: 1; display: flex; flex-wrap: wrap; gap: 8px; }
.health-row { display: flex; align-items: center; gap: 8px; }
.health-key { font-size: 13px; color: #606266; }

.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 12px 0;
}
.stat-card { text-align: center; padding: 18px 0; }
.stat-num { font-size: 30px; font-weight: 700; line-height: 1.2; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.text-success { color: #67c23a; }
.text-warning { color: #e6a23c; }
.text-primary { color: #409eff; }
.text-muted { color: #909399; font-size: 12px; }
.rt-filter-row { display: flex; gap: 10px; align-items: center; margin-bottom: 16px; }
.rt-stat-row { display: flex; gap: 16px; margin-bottom: 8px; }
.rt-stat-card { flex: 1; text-align: center; padding: 12px 0; }
.rt-stat-num { font-size: 28px; font-weight: 700; line-height: 1.2; }
.rt-stat-label { font-size: 13px; color: #909399; margin-top: 2px; }
.rt-progress-cell { display: flex; align-items: center; gap: 8px; }
.rt-progress-text { font-size: 12px; color: #909399; white-space: nowrap; }
</style>