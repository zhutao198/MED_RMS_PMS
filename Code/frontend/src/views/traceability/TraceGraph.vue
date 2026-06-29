<template>
  <div class="trace-graph-container">
    <div class="page-header">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/traceability' }">追溯管理</el-breadcrumb-item>
        <el-breadcrumb-item>追溯图谱</el-breadcrumb-item>
      </el-breadcrumb>
      <div class="page-title">🔗 追溯链路图谱</div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
            <el-select v-model="projectId" placeholder="选择项目" style="width: 240px;" filterable @change="loadData">
              <el-option v-for="p in projects" :key="p.id" :label="`${p.projectNo} ${p.projectName}`" :value="p.id" />
            </el-select>
            <el-input v-model="searchKw" placeholder="搜索需求编号/标题" style="width: 220px;" clearable @keyup.enter="handleSearch">
              <template #prefix><span>🔍</span></template>
            </el-input>
            <el-radio-group v-model="filterLevel" size="small" @change="applyFilter">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="URS">URS</el-radio-button>
              <el-radio-button label="PRS">PRS</el-radio-button>
              <el-radio-button label="SRS">SRS</el-radio-button>
              <el-radio-button label="DRS">DRS</el-radio-button>
            </el-radio-group>
          </div>
          <div>
            <el-button @click="resetView">重置视图</el-button>
          </div>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="16">
          <div class="graph-viewer" ref="graphContainer">
            <svg class="trace-svg" :viewBox="`0 0 ${svgWidth} ${svgHeight}`">
              <defs>
                <marker id="arrow-decompose" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#409eff"/>
                </marker>
                <marker id="arrow-refines" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#67c23a"/>
                </marker>
                <marker id="arrow-verifies" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#e6a23c"/>
                </marker>
                <marker id="arrow-default" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                  <path d="M 0 0 L 10 5 L 0 10 z" fill="#909399"/>
                </marker>
              </defs>

              <!-- v1.55 修复：边层（在节点下层避免遮挡点击） -->
              <g class="edges-layer">
                <line
                  v-for="(edge, idx) in edgePositions"
                  :key="`edge-${idx}`"
                  :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2"
                  :stroke="getEdgeColor(edge.type)"
                  stroke-width="1.5"
                  :marker-end="`url(#${getEdgeMarker(edge.type)})`"
                  opacity="0.75"
                />
              </g>

              <!-- 节点层 -->
              <g v-for="(layer, layerIdx) in graphLayers" :key="layerIdx" :transform="`translate(0, ${layerIdx * 140})`">
                <text :x="20" :y="20" class="layer-label">{{ layer.label }}</text>
                <g
                  v-for="(node, nodeIdx) in layer.nodes"
                  :key="node.id"
                  :transform="`translate(${nodeIdx * 180 + 40}, 30)`"
                  :class="['trace-node', { 'is-focused': focusedNodeId === node.id, 'is-hovered': hoveredNodeId === node.id }]"
                  @click="selectNode(node)"
                  @mouseenter="hoveredNodeId = node.id"
                  @mouseleave="hoveredNodeId = null"
                  style="cursor: pointer;"
                >
                  <title>{{ node.requirementNo }} - {{ node.title }}\n类型：{{ node.type }}\n状态：{{ node.status }}\n优先级：{{ node.priority }}\n点击查看详情</title>
                  <rect
                    width="160" height="60" rx="5"
                    :fill="getNodeColor(node)"
                    :stroke="focusedNodeId === node.id ? '#409eff' : (hoveredNodeId === node.id ? '#67c23a' : '#333')"
                    :stroke-width="focusedNodeId === node.id ? 2.5 : (hoveredNodeId === node.id ? 1.5 : 1)"
                  />
                  <text x="80" y="25" text-anchor="middle" class="node-no">{{ node.requirementNo }}</text>
                  <text x="80" y="45" text-anchor="middle" class="node-title">{{ truncate(node.title, 15) }}</text>
                </g>
              </g>
            </svg>
            <el-empty v-if="nodes.length === 0" description="暂无追溯数据" />
            <div v-else-if="filteredNodes.length === 0" style="padding: 40px; text-align: center; color: #909399;">
              当前筛选下无节点
            </div>
          </div>
          <div class="graph-legend">
            <div class="legend-item"><span class="legend-line" style="background: #409eff;"></span>拆解 DECOMPOSE</div>
            <div class="legend-item"><span class="legend-line" style="background: #67c23a;"></span>精化 REFINES</div>
            <div class="legend-item"><span class="legend-line" style="background: #e6a23c;"></span>验证 VERIFIES</div>
            <div class="legend-item"><span class="legend-line" style="background: #909399;"></span>其他</div>
            <div style="margin-left: auto; color: #909399; font-size: 12px;">提示：点击节点查看需求详情，hover 节点查看概览</div>
          </div>
        </el-col>

        <el-col :span="8">
          <el-card header="统计信息">
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="总需求数">{{ stats.totalNodes }}</el-descriptions-item>
              <el-descriptions-item label="追溯边数">{{ stats.totalEdges }}</el-descriptions-item>
              <el-descriptions-item label="追溯率">{{ stats.traceRate }}%</el-descriptions-item>
              <el-descriptions-item label="孤立需求">{{ stats.orphanCount }}</el-descriptions-item>
              <el-descriptions-item label="当前筛选">{{ filteredNodes.length }} / {{ nodes.length }}</el-descriptions-item>
            </el-descriptions>
            <!-- W22 修复：后端 W17 加了 MAX_NODES 截断 + truncated 标记，前端展示提示 -->
            <el-alert
              v-if="stats.truncated"
              type="warning"
              :closable="false"
              show-icon
              style="margin-top: 12px"
            >
              <template #title>
                节点已截断
              </template>
              当前显示前 {{ stats.maxNodes || 500 }} 个节点（实际总需求 {{ stats.totalNodes }} 个），
              如需查看全部请使用需求列表或缩小时间范围。
            </el-alert>
          </el-card>

          <el-card header="孤立需求" style="margin-top: 16px" v-if="orphans.length > 0">
            <el-tag
              v-for="o in orphans"
              :key="o.id"
              type="warning"
              style="margin: 4px; cursor: pointer;"
              @click="focusNode(o.id)"
            >{{ o.requirementNo }}</el-tag>
          </el-card>

          <el-card header="按类型统计" style="margin-top: 16px">
            <div v-for="(count, type) in stats.byType" :key="type" class="type-stat">
              <span>{{ type }}:</span>
              <el-tag>{{ count }}</el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 质量评分 -->
    <el-card style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>需求质量评分</span>
          <el-select v-model="selectedRequirement" placeholder="选择需求" filterable @change="loadQualityScore">
            <el-option v-for="n in nodes" :key="n.id" :label="`${n.requirementNo} - ${truncate(n.title, 12)}`" :value="n.id" />
          </el-select>
        </div>
      </template>
      <div v-if="qualityScore" class="quality-panel">
        <el-progress type="circle" :percentage="qualityScore.score" :color="getScoreColor(qualityScore.level)" width="120">
          <template #default>{{ qualityScore.score }}分</template>
        </el-progress>
        <el-tag :type="getScoreTagType(qualityScore.level)" size="large">{{ qualityScore.level }}</el-tag>
        <el-descriptions :column="2" border style="margin-top: 16px">
          <el-descriptions-item label="完整性">{{ qualityScore.breakdown.completeness }}/25</el-descriptions-item>
          <el-descriptions-item label="一致性">{{ qualityScore.breakdown.consistency }}/25</el-descriptions-item>
          <el-descriptions-item label="可测试性">{{ qualityScore.breakdown.testability }}/25</el-descriptions-item>
          <el-descriptions-item label="合规性">{{ qualityScore.breakdown.compliance }}/25</el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else description="请选择需求查看评分" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

interface GraphNode {
  id: number
  requirementNo: string
  title: string
  type: string
  status: string
  priority: string
}

interface GraphEdge {
  source: number
  target: number
  type: string
}

interface EdgePosition {
  x1: number
  y1: number
  x2: number
  y2: number
  type: string
}

const router = useRouter()
const projectId = ref<number | null>(1)
const projects = ref<Array<{ id: number; projectNo: string; projectName: string }>>([])
const nodes = ref<GraphNode[]>([])
const edges = ref<GraphEdge[]>([])
const orphans = ref<GraphNode[]>([])
const stats = ref<any>({ totalNodes: 0, totalEdges: 0, traceRate: 0, orphanCount: 0, byType: {} })
const selectedRequirement = ref<number | null>(null)
const qualityScore = ref<any>(null)
// v1.55 修复：搜索 + 过滤 + 交互状态
const searchKw = ref('')
const filterLevel = ref<string>('')
const focusedNodeId = ref<number | null>(null)
const hoveredNodeId = ref<number | null>(null)
const svgWidth = 800
const LAYER_HEIGHT = 140
const NODE_WIDTH = 160
const NODE_HEIGHT = 60
const NODE_GAP = 180

interface Layer {
  label: string
  nodes: GraphNode[]
}

const filteredNodes = computed<GraphNode[]>(() => {
  let list = nodes.value
  if (filterLevel.value) {
    list = list.filter(n => n.type === filterLevel.value)
  }
  if (searchKw.value) {
    const kw = searchKw.value.toLowerCase()
    list = list.filter(n =>
      n.requirementNo.toLowerCase().includes(kw) ||
      (n.title || '').toLowerCase().includes(kw)
    )
  }
  return list
})

const graphLayers = computed<Layer[]>(() => {
  const layerMap: Record<string, GraphNode[]> = { URS: [], PRS: [], SRS: [], DRS: [] }
  filteredNodes.value.forEach(n => {
    if (layerMap[n.type]) layerMap[n.type].push(n)
  })
  return Object.entries(layerMap).filter(([_, ns]) => ns.length > 0).map(([label, ns]) => ({ label, nodes: ns }))
})

const svgHeight = computed(() => {
  return Math.max(500, graphLayers.value.length * LAYER_HEIGHT + 60)
})

// v1.55 修复：边坐标计算（节点中心到底部 + 目标节点顶部）
const edgePositions = computed<EdgePosition[]>(() => {
  // 用全部节点构建位置索引（不被 filterLevel 影响，便于跨层级边）
  const layerMap: Record<string, GraphNode[]> = { URS: [], PRS: [], SRS: [], DRS: [] }
  nodes.value.forEach(n => { if (layerMap[n.type]) layerMap[n.type].push(n) })
  const posMap: Record<number, { x: number; y: number; layer: string }> = {}
  const layerOrder = ['URS', 'PRS', 'SRS', 'DRS']
  layerOrder.forEach((layer, layerIdx) => {
    layerMap[layer].forEach((n, nodeIdx) => {
      posMap[n.id] = {
        x: nodeIdx * NODE_GAP + 40 + NODE_WIDTH / 2,
        y: layerIdx * LAYER_HEIGHT + 30 + NODE_HEIGHT / 2,
        layer,
      }
    })
  })
  return edges.value
    .filter(e => posMap[e.source] && posMap[e.target])
    .map(e => {
      const src = posMap[e.source]
      const tgt = posMap[e.target]
      // 从源节点底部画到目标节点顶部
      return {
        x1: src.x,
        y1: src.y + NODE_HEIGHT / 2,
        x2: tgt.x,
        y2: tgt.y - NODE_HEIGHT / 2,
        type: e.type || '',
      }
    })
})

const fetchProjects = async () => {
  try {
    const res = await request.get('/projects', { params: { page: 0, size: 200 } })
    const data = res.data?.data
    projects.value = Array.isArray(data) ? data : (data?.records || [])
    if (projects.value.length > 0 && !projects.value.find(p => p.id === projectId.value)) {
      projectId.value = projects.value[0].id
    }
  } catch (e) {
    console.warn('加载项目列表失败', e)
  }
}

const loadData = async () => {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    const res = await request.get(`/trace-graph/project/${projectId.value}`)
    const data = res.data.data || {}
    nodes.value = data.nodes || []
    edges.value = data.edges || []
    orphans.value = data.orphans || []
    stats.value = data.stats || { totalNodes: 0, totalEdges: 0, traceRate: 0, orphanCount: 0, byType: {} }
  } catch (e: any) {
    ElMessage.error('获取追溯图数据失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

const loadQualityScore = async () => {
  if (!selectedRequirement.value) return
  try {
    const res = await request.get(`/trace-graph/quality/${selectedRequirement.value}`)
    qualityScore.value = res.data.data
  } catch {
    ElMessage.error('获取质量评分失败')
  }
}

const getNodeColor = (node: GraphNode) => {
  const map: Record<string, string> = { URS: '#e6f7ff', PRS: '#fff7e6', SRS: '#fffbe6', DRS: '#f6ffed' }
  return map[node.type] || '#f5f5f5'
}

const getEdgeColor = (type: string) => {
  if (type === 'DECOMPOSE') return '#409eff'
  if (type === 'REFINES') return '#67c23a'
  if (type === 'VERIFIES') return '#e6a23c'
  return '#909399'
}

const getEdgeMarker = (type: string) => {
  if (type === 'DECOMPOSE') return 'arrow-decompose'
  if (type === 'REFINES') return 'arrow-refines'
  if (type === 'VERIFIES') return 'arrow-verifies'
  return 'arrow-default'
}

const truncate = (str: string, len: number) => (str && str.length > len) ? str.slice(0, len) + '...' : (str || '')

// v1.55 修复：节点点击 → 跳转需求详情
const selectNode = (node: GraphNode) => {
  if (!node?.id) return
  router.push(`/requirements/${node.id}`)
}

// v1.55 修复：搜索定位（高亮 2 秒后还原）
const focusNode = (id: number) => {
  focusedNodeId.value = id
  setTimeout(() => {
    if (focusedNodeId.value === id) focusedNodeId.value = null
  }, 2000)
}

const handleSearch = () => {
  if (!searchKw.value) return
  const hit = nodes.value.find(n =>
    n.requirementNo.toLowerCase().includes(searchKw.value.toLowerCase()) ||
    (n.title || '').toLowerCase().includes(searchKw.value.toLowerCase())
  )
  if (hit) {
    focusNode(hit.id)
  } else {
    ElMessage.warning('未找到匹配的需求')
  }
}

const applyFilter = () => {
  // 切换 filterLevel 时清空 focus
  focusedNodeId.value = null
}

const resetView = () => {
  searchKw.value = ''
  filterLevel.value = ''
  focusedNodeId.value = null
  hoveredNodeId.value = null
}

const getScoreColor = (level: string) => {
  const map: Record<string, string> = { EXCELLENT: '#67c23a', GOOD: '#409eff', FAIR: '#e6a23c', POOR: '#f56c6c' }
  return map[level] || '#909399'
}

const getScoreTagType = (level: string) => {
  const map: Record<string, string> = { EXCELLENT: 'success', GOOD: '', FAIR: 'warning', POOR: 'danger' }
  return map[level] || 'info'
}

onMounted(async () => {
  await fetchProjects()
  await loadData()
})
</script>

<style scoped>
.trace-graph-container { padding: 16px; }
.page-header { margin-bottom: 12px; }
.page-title { font-size: 20px; font-weight: 600; color: #303133; margin-top: 8px; }
.graph-viewer { min-height: 400px; border: 1px solid #e4e7ed; border-radius: 4px; overflow: auto; }
.trace-svg { width: 100%; min-height: 400px; }
.layer-label { font-weight: bold; fill: #666; }
.node-no { font-size: 12px; font-weight: bold; pointer-events: none; }
.node-title { font-size: 10px; pointer-events: none; }
.type-stat { display: flex; justify-content: space-between; align-items: center; margin: 8px 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.quality-panel { display: flex; flex-direction: column; align-items: center; gap: 16px; }
.graph-legend { display: flex; gap: 16px; align-items: center; padding: 8px 12px; font-size: 12px; color: #606266; margin-top: 8px; flex-wrap: wrap; }
.legend-item { display: flex; align-items: center; gap: 6px; }
.legend-line { display: inline-block; width: 24px; height: 3px; border-radius: 2px; }
.trace-node { transition: filter 0.2s; }
.trace-node.is-focused rect { filter: drop-shadow(0 0 4px #409eff); }
.trace-node.is-hovered rect { filter: drop-shadow(0 0 2px #67c23a); }
</style>