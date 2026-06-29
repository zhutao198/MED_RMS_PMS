<template>
  <div class="ipd-container">
    <div class="page-title">
      <h2>🚦 IPD 阶段门管理（FR-2.5）</h2>
      <div class="header-actions">
        <el-select v-model="filterProject" placeholder="选择项目" filterable style="width: 320px;" @change="loadGates">
          <el-option v-for="p in projectList" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
        </el-select>
        <el-button @click="loadGates">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px;">
      IPD 集成产品开发：5 个阶段门（DCP1-DCP5）覆盖产品立项→上市全过程。
      每道门限有自动检查规则，通过后可继续下一阶段。
    </el-alert>

    <div class="gate-flow">
      <div v-for="g in gateFlow" :key="g.no" class="gate-step" :class="getStepClass(g.no)">
        <div class="gate-no">DCP{{ g.no }}</div>
        <div class="gate-name">{{ g.name }}</div>
        <div class="gate-type">{{ g.type }}</div>
        <div class="gate-status">
          <el-tag v-if="getGateByNo(g.no)" :type="statusTag(getGateByNo(g.no).status)" size="small">
            {{ statusLabel(getGateByNo(g.no).status) }}
          </el-tag>
          <el-tag v-else type="info" size="small">未创建</el-tag>
        </div>
        <el-button v-if="getGateByNo(g.no)" size="small" type="primary" @click="openAutoCheck(g.no)">自动检查</el-button>
        <el-button v-else size="small" @click="openCreate(g)">创建门</el-button>
      </div>
    </div>

    <el-card style="margin-top: 20px">
      <template #header><div class="card-title">📋 阶段门列表</div></template>
      <el-table :data="gates" v-loading="loading" border stripe>
        <el-table-column prop="gateNo" label="DCP" width="80">
          <template #default="{ row }">DCP{{ row.gateNo }}</template>
        </el-table-column>
        <el-table-column prop="gateName" label="门名称" />
        <el-table-column prop="gateType" label="阶段类型" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plannedDate" label="计划日期" width="120" />
        <el-table-column prop="actualDate" label="实际日期" width="120" />
        <el-table-column prop="reviewer" label="评审人" width="100" />
        <el-table-column label="签名进度" width="160">
          <template #default="{ row }">
            <div class="sig-progress-cell">
              <el-progress
                :percentage="signPercent(row)"
                :status="signStatus(row)"
                :stroke-width="14"
                :format="() => `${signedCount(row)}/${requiredCount(row.gateNo)}`"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="comment" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openAutoCheck(row.gateNo)">自动检查</el-button>
            <el-button size="small" type="warning" @click="openSignatureDialog(row)">签名</el-button>
            <el-button size="small" type="success" :disabled="row.status === 'PASSED'" @click="openPass(row, 'APPROVED')">通过</el-button>
            <el-button size="small" type="danger" :disabled="row.status === 'PASSED'" @click="openFail(row)">不通过</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 自动检查结果对话框 -->
    <el-dialog v-model="checkVisible" :title="`DCP${checkGateNo} 阶段门自动检查`" width="720px">
      <div v-loading="checkLoading" v-if="checkResult">
        <el-alert :type="checkResult.verdict === 'PASS' ? 'success' : 'error'" :closable="false" show-icon style="margin-bottom: 16px;">
          <template #title>
            <b>总判定：{{ checkResult.verdict === 'PASS' ? '通过 ✅' : '不通过 ❌' }}</b>
            <span style="margin-left: 12px">通过项 {{ checkResult.passedItems }}/{{ checkResult.totalItems }}</span>
          </template>
        </el-alert>
        <el-table :data="checkResult.items" border>
          <el-table-column label="检查项" prop="name" width="160" />
          <el-table-column label="判定标准" prop="criterion" />
          <el-table-column label="实际值" prop="actual" width="120" align="center" />
          <el-table-column label="结果" width="100" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.pass" type="success" size="small">✅ 通过</el-tag>
              <el-tag v-else type="danger" size="small">❌ 未通过</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div v-for="(item, i) in (checkResult.items || []).filter(it => !it.pass)" :key="i" class="hint-line">
          ⚠️ <b>{{ item.name }}</b>：{{ item.failHint }}
        </div>
      </div>
    </el-dialog>

    <!-- 创建门对话框 -->
    <el-dialog v-model="createVisible" :title="`创建 DCP${createForm.gateNo} 阶段门`" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="门序号">
          <el-input-number v-model="createForm.gateNo" :min="1" :max="5" style="width:100%" />
        </el-form-item>
        <el-form-item label="门名称">
          <el-input v-model="createForm.gateName" />
        </el-form-item>
        <el-form-item label="阶段类型">
          <!-- R88 修复：原硬编码 DEFINE/DEVELOPMENT/RELEASE/MARKET 是设计阶段值，后端实际用 DCP1-DCP5/PLANNING。
               选错值会导致阶段门创建后类型不匹配后端逻辑。改为后端实际支持的 6 个枚举值。 -->
          <el-select v-model="createForm.gateType" style="width:100%">
            <el-option label="PLANNING - 规划" value="PLANNING" />
            <el-option label="DCP1 - 立项门" value="DCP1" />
            <el-option label="DCP2 - 需求冻结" value="DCP2" />
            <el-option label="DCP3 - 开发完成" value="DCP3" />
            <el-option label="DCP4 - 发布门" value="DCP4" />
            <el-option label="DCP5 - 上市门" value="DCP5" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划日期">
          <el-date-picker v-model="createForm.plannedDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="评审人">
          <el-input v-model="createForm.reviewer" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 通过/不通过评论对话框 -->
    <el-dialog v-model="decisionVisible" :title="`DCP${decisionForm.gateNo} ${decisionForm.pass ? '通过' : '不通过'}`" width="500px">
      <el-form label-width="80px">
        <el-form-item label="决策">
          <el-radio-group v-model="decisionForm.decision">
            <el-radio label="APPROVED">批准</el-radio>
            <el-radio label="REJECTED">拒绝</el-radio>
            <el-radio label="DEFERRED">延期</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评论">
          <el-input v-model="decisionForm.comment" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button :type="decisionForm.pass ? 'success' : 'danger'" @click="confirmDecision">确认</el-button>
      </template>
    </el-dialog>

    <!-- v1.46 P1-后端-3：阶段门多签详情 -->
    <el-dialog v-model="sigDialogVisible" :title="sigDialogTitle" width="780px" @open="loadSignaturesForGate">
      <div v-loading="sigLoading" v-if="sigGate">
        <el-alert :type="signedCount(sigGate) >= requiredCount(sigGate.gateNo) ? 'success' : 'warning'" :closable="false" show-icon style="margin-bottom: 16px;">
          <template #title>
            <b>签名进度：{{ signedCount(sigGate) }} / {{ requiredCount(sigGate.gateNo) }}</b>
            <span style="margin-left: 12px; color: #909399; font-weight: normal;">
              必签角色：{{ requiredRolesLabel(sigGate.gateNo) }}
            </span>
          </template>
        </el-alert>

        <div class="sig-section-title">✅ 已签名（{{ signedCount(sigGate) }}）</div>
        <el-table :data="signedList(sigGate)" border size="small" style="margin-bottom: 16px">
          <el-table-column prop="signerRole" label="角色" width="100">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ roleLabel(row.signerRole) || row.signerRole || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="signerName" label="签名人" width="120" />
          <el-table-column prop="signatureType" label="签名类型" width="100" />
          <el-table-column prop="signatureMethod" label="签名方法" width="100" />
          <el-table-column prop="signedAt" label="签名时间" width="170" />
          <el-table-column prop="signatureHash" label="签名哈希" show-overflow-tooltip>
            <template #default="{ row }">
              <code style="font-size: 11px">{{ (row.signatureHash || row.signatureValue || '').slice(0, 24) }}…</code>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="missingRoles(sigGate).length > 0">
          <div class="sig-section-title">⚠️ 缺少签名（{{ missingRoles(sigGate).length }}）</div>
          <el-table :data="missingRoles(sigGate)" border size="small">
            <el-table-column label="待签角色" width="120">
              <template #default="{ row }">
                <el-tag size="small" type="warning">{{ roleLabel(row) || row }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="说明">
              <template #default="{ row }">
                等待具有「{{ roleLabel(row) || row }}」角色的用户完成签名
              </template>
            </el-table-column>
          </el-table>
        </div>
        <el-empty v-else description="已收齐全部必签签名 🎉" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="sigDialogVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="missingRoles(sigGate).length === 0" @click="requestSignature">
          📩 申请签名（创建 Intent）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { esignatureApi } from '@/api/esignature'

const projectList = ref<any[]>([])
const filterProject = ref<number | null>(null)
const gates = ref<any[]>([])
const loading = ref(false)

const checkVisible = ref(false)
const checkLoading = ref(false)
const checkGateNo = ref(0)
const checkResult = ref<any>(null)

const createVisible = ref(false)
const createForm = reactive<any>({ gateNo: 1, gateName: '', gateType: 'PLANNING', plannedDate: null, reviewer: '' })

const decisionVisible = ref(false)
const decisionForm = reactive<any>({ gateId: 0, gateNo: 0, pass: true, decision: 'APPROVED', comment: '' })

// v1.46 P1-后端-3：阶段门多签状态
const sigDialogVisible = ref(false)
const sigLoading = ref(false)
const sigGate = ref<any>(null)
const sigMap = ref<Record<number, any[]>>({})

// 各阶段门必签角色（与 21 CFR Part 11 / ISO 13485 设计控制要求对齐）
const REQUIRED_SIGNERS: Record<number, string[]> = {
  1: ['PM', 'QA'],
  2: ['PM', 'QA'],
  3: ['PM', 'QA'],
  4: ['PM', 'QA', 'COMPLIANCE'],
  5: ['PM', 'QA', 'COMPLIANCE']
}
const ROLE_LABELS: Record<string, string> = {
  PM: '项目经理',
  QA: '质量',
  COMPLIANCE: '合规'
}

const gateFlow = [
  // R88 修复：原 type 用 DEFINE/DEVELOPMENT/RELEASE/MARKET 是设计阶段值，后端实际用 DCP1-DCP5
  { no: 1, name: '立项门 (DCP1)', type: 'DCP1' },
  { no: 2, name: '需求冻结 (DCP2)', type: 'DCP2' },
  { no: 3, name: '开发完成 (DCP3)', type: 'DCP3' },
  { no: 4, name: '发布门 (DCP4)', type: 'DCP4' },
  { no: 5, name: '上市门 (DCP5)', type: 'DCP5' }
]

const statusLabel = (s: string) => ({ PENDING: '待审', PASSED: '已通过', FAILED: '未通过', SKIPPED: '已跳过' } as any)[s] || s
const statusTag = (s: string) => ({ PENDING: '', PASSED: 'success', FAILED: 'danger', SKIPPED: 'info' } as any)[s] || 'info'
const getGateByNo = (no: number) => gates.value.find(g => g.gateNo === no)
const getStepClass = (no: number) => {
  const g = getGateByNo(no)
  if (!g) return 'pending'
  if (g.status === 'PASSED') return 'passed'
  if (g.status === 'FAILED') return 'failed'
  return 'pending'
}

// 签名辅助：同一角色多次签名只计一次（取最早一条）
const uniqueByRole = (sigs: any[]) => {
  const m = new Map<string, any>()
  for (const s of sigs) {
    const k = s.signerRole || 'UNKNOWN'
    if (!m.has(k)) m.set(k, s)
  }
  return Array.from(m.values())
}
const requiredCount = (gateNo: number) => (REQUIRED_SIGNERS[gateNo] || []).length
const requiredRolesLabel = (gateNo: number) => (REQUIRED_SIGNERS[gateNo] || []).map(r => ROLE_LABELS[r] || r).join(' + ')
const roleLabel = (code: string) => ROLE_LABELS[code] || code
const signedList = (gate: any) => uniqueByRole(sigMap.value[gate.id] || [])
const signedCount = (gate: any) => signedList(gate).length
const signPercent = (gate: any) => {
  const req = requiredCount(gate.gateNo)
  return req === 0 ? 0 : Math.round((signedCount(gate) / req) * 100)
}
const signStatus = (gate: any): 'success' | 'warning' | 'exception' => {
  const req = requiredCount(gate.gateNo)
  const got = signedCount(gate)
  if (got >= req) return 'success'
  if (got > 0) return 'warning'
  return 'exception'
}
const missingRoles = (gate: any) => {
  const required = REQUIRED_SIGNERS[gate.gateNo] || []
  const signedRoles = new Set(signedList(gate).map(s => s.signerRole))
  return required.filter(r => !signedRoles.has(r))
}
const sigDialogTitle = computed(() => sigGate.value ? `DCP${sigGate.value.gateNo} ${sigGate.value.gateName} - 签名详情` : '签名详情')

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const d = res.data?.data
    projectList.value = Array.isArray(d) ? d : (d?.records || [])
    if (projectList.value.length > 0 && !filterProject.value) filterProject.value = projectList.value[0].id
  } catch (e) {}
}

const loadGates = async () => {
  if (!filterProject.value) return
  loading.value = true
  try {
    const res = await request.get(`/project/ipd-gate/list/${filterProject.value}`)
    gates.value = res.data?.data || []
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

const openCreate = (g: any) => {
  createForm.gateNo = g.no
  createForm.gateName = g.name
  createForm.gateType = g.type
  createForm.plannedDate = null
  createForm.reviewer = ''
  createVisible.value = true
}

const confirmCreate = async () => {
  try {
    await request.post('/project/ipd-gate', { projectId: filterProject.value, ...createForm })
    ElMessage.success('创建成功')
    createVisible.value = false
    await loadGates()
  } catch (e: any) {
    ElMessage.error('创建失败：' + (e?.response?.data?.message || e.message))
  }
}

const openAutoCheck = async (gateNo: number) => {
  checkGateNo.value = gateNo
  checkVisible.value = true
  checkLoading.value = true
  checkResult.value = null
  try {
    // 从各端点取统计（简化版：使用需求质量评分、风险报告、合规统计等已有端点）
    const params: any = { projectId: filterProject.value, gateNo }
    // 通过 promise.all 并行获取
    const [reqRes, riskRes, compRes, evRes] = await Promise.allSettled([
      request.get('/requirements/quality', { params: { projectId: filterProject.value } }),
      request.get(`/risk/report/${filterProject.value}`),
      request.get(`/compliance/iec62304/checklist/${filterProject.value}/stats`),
      request.get(`/compliance/evidence/${filterProject.value}`)
    ])

    const reqs = reqRes.status === 'fulfilled' ? (reqRes.value.data?.data || []) : []
    const approvedReqs = (Array.isArray(reqs) ? reqs : []).filter((r: any) => ['Approved', 'Baseline', 'Verified'].includes(r.status)).length
    const riskStats = riskRes.status === 'fulfilled' ? (riskRes.value.data?.data || {}) : {}
    const compStats = compRes.status === 'fulfilled' ? (compRes.value.data?.data || {}) : {}
    const evList = evRes.status === 'fulfilled' ? (evRes.value.data?.data || []) : []

    params.requirementCount = Array.isArray(reqs) ? reqs.length : 0
    params.approvedRequirementCount = approvedReqs
    params.riskCount = riskStats.totalRisks || 0
    params.highRiskCount = riskStats.highRisks || 0
    params.testCaseCount = 0
    params.passedTestCaseCount = 0
    params.iecCompliantCount = compStats.compliant || 0
    params.totalIecItems = compStats.total || 0
    params.dhfEvidenceCount = Array.isArray(evList) ? evList.length : 0

    const res = await request.post('/project/ipd-gate/auto-check', params)
    checkResult.value = res.data?.data
  } catch (e: any) {
    ElMessage.error('检查失败：' + (e?.response?.data?.message || e.message))
  } finally {
    checkLoading.value = false
  }
}

const openPass = (row: any, decision: string) => {
  decisionForm.gateId = row.id
  decisionForm.gateNo = row.gateNo
  decisionForm.pass = true
  decisionForm.decision = decision
  decisionForm.comment = ''
  decisionVisible.value = true
}

const openFail = (row: any) => {
  decisionForm.gateId = row.id
  decisionForm.gateNo = row.gateNo
  decisionForm.pass = false
  decisionForm.decision = 'REJECTED'
  decisionForm.comment = ''
  decisionVisible.value = true
}

const confirmDecision = async () => {
  try {
    if (decisionForm.pass) {
      await request.post(`/project/ipd-gate/${decisionForm.gateId}/pass?decision=${decisionForm.decision}&comment=${encodeURIComponent(decisionForm.comment || '')}`)
    } else {
      await request.post(`/project/ipd-gate/${decisionForm.gateId}/fail?comment=${encodeURIComponent(decisionForm.comment || '不符合要求')}`)
    }
    ElMessage.success('已记录')
    decisionVisible.value = false
    await loadGates()
  } catch (e: any) {
    ElMessage.error('操作失败：' + (e?.response?.data?.message || e.message))
  }
}

// ==================== v1.46 P1-后端-3：阶段门多签 ====================

const openSignatureDialog = (gate: any) => {
  sigGate.value = gate
  sigDialogVisible.value = true
  // 若尚未加载则预取（dialog 自身 @open 也会再拉一次以确保最新）
  if (!sigMap.value[gate.id]) {
    loadSignaturesForGate()
  }
}

const loadSignaturesForGate = async () => {
  if (!sigGate.value) return
  const gateId = sigGate.value.id
  sigLoading.value = true
  try {
    const res = await esignatureApi.getByEntity('IPD_GATE', gateId)
    sigMap.value[gateId] = res.data?.data || []
  } catch (e: any) {
    sigMap.value[gateId] = []
    ElMessage.warning('签名记录加载失败：' + (e?.response?.data?.message || e.message))
  } finally {
    sigLoading.value = false
  }
}

const requestSignature = async () => {
  if (!sigGate.value) return
  try {
    // 1) 创建签名意图（v1.46 BUG #104 修复后强制要求先建 Intent 才能签）
    const userIdStr = localStorage.getItem('userId') || localStorage.getItem('uid') || '1'
    const requesterId = Number(userIdStr) || 1
    const intentRes = await request.post('/esignature/intents', {
      requesterId,
      documentType: 'IPD_GATE',
      documentId: sigGate.value.id,
      intentCode: 'GATE_APPROVAL',
      meaningCode: 'GATE_APPROVAL'
    })
    const intentId = intentRes.data?.data?.id
    if (!intentId) {
      ElMessage.warning('签名意图创建返回异常，请检查后端日志')
      return
    }
    ElMessage.success(`已创建签名意图 #${intentId}，请通知相关角色完成签名`)
    // 刷新签名列表（虽然还没有实际签名记录，但确保缓存最新）
    await loadSignaturesForGate()
  } catch (e: any) {
    ElMessage.error('申请签名失败：' + (e?.response?.data?.message || e.message))
  }
}

onMounted(async () => {
  await fetchProjects()
  await loadGates()
})
</script>

<style scoped>
.ipd-container { padding: 20px; background: #f0f2f5; min-height: 100vh; }
.page-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title h2 { font-size: 20px; }
.header-actions { display: flex; gap: 12px; }
.gate-flow { display: flex; gap: 12px; flex-wrap: wrap; }
.gate-step { flex: 1; min-width: 180px; background: #fff; border-radius: 8px; padding: 16px; text-align: center; border-top: 4px solid #909399; }
.gate-step.passed { border-top-color: #67C23A; background: #f0f9eb; }
.gate-step.failed { border-top-color: #F56C6C; background: #fef0f0; }
.gate-step.pending { border-top-color: #E6A23C; }
.gate-no { font-size: 20px; font-weight: 700; color: #303133; }
.gate-name { font-size: 14px; color: #606266; margin: 4px 0; }
.gate-type { font-size: 12px; color: #909399; }
.gate-status { margin: 8px 0; }
.card-title { font-size: 15px; font-weight: 600; }
.hint-line { color: #E6A23C; font-size: 13px; margin-top: 8px; padding: 4px 8px; background: #fdf6ec; border-radius: 4px; }
.sig-progress-cell { padding: 4px 0; }
.sig-section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; padding-left: 4px; border-left: 3px solid #409EFF; }
</style>
