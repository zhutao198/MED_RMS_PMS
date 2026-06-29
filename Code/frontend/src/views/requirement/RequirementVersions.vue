<template>
  <div class="versions-container">
    <div class="page-header">
      <el-button @click="$router.back()">← 返回</el-button>
      <h2>版本历史</h2>
      <div class="header-spacer" />
      <el-button type="primary" @click="handleCreateVersion">+ 新建版本</el-button>
    </div>

    <el-card v-if="versions.length > 0">
      <!-- P1-6 修复：操作列添加 对比 / 导出 按钮 -->
      <el-table :data="versions" border stripe>
        <el-table-column prop="versionNo" label="版本号" width="100">
          <template #default="{ row }">
            <strong>v{{ row.versionNo }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="changedAt" label="变更时间" width="180">
          <template #default="{ row }">{{ formatDate(row.changedAt) }}</template>
        </el-table-column>
        <el-table-column prop="changeSummary" label="变更摘要" min-width="200" show-overflow-tooltip />
        <el-table-column prop="snapshot" label="快照预览" min-width="200">
          <template #default="{ row }">{{ truncateSnapshot(row.snapshot) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleCompare(row)">对比</el-button>
            <el-button link type="primary" @click="openExportMenu(row, $event)">导出</el-button>
            <el-dropdown-menu v-show="false">
              <el-dropdown-item @click="handleExport(row, 'excel')">导出 Excel</el-dropdown-item>
              <el-dropdown-item @click="handleExport(row, 'pdf')">导出 PDF</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-empty v-else description="暂无版本历史记录" />

    <!-- 导出格式选择弹窗 -->
    <el-dialog v-model="exportDialogVisible" title="导出版本快照" width="380px">
      <p>请选择导出格式：</p>
      <template #footer>
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleExport(activeVersion, 'excel')">Excel (.csv)</el-button>
        <el-button type="primary" @click="handleExport(activeVersion, 'pdf')">PDF (打印)</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { requirementApi } from '../../api/requirement'

const route = useRoute()
const router = useRouter()
const versions = ref<any[]>([])

// 导出相关
const exportDialogVisible = ref(false)
const activeVersion = ref<any>(null)

const loadVersions = async () => {
  const id = Number(route.params.id)
  if (!id) return
  try {
    const res = await fetch(`/api/requirements/${id}/versions`)
    const data = await res.json()
    versions.value = data.data || []
  } catch (e) {
    console.error('加载版本历史失败', e)
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const truncateSnapshot = (snapshot: string) => {
  if (!snapshot) return ''
  return snapshot.length > 100 ? snapshot.substring(0, 100) + '...' : snapshot
}

// v1.52 新增：跳转到新建版本页
const handleCreateVersion = () => {
  const id = Number(route.params.id)
  if (!id) return
  router.push(`/requirements/${id}/versions/create`)
}

/**
 * P1-6 修复：版本对比
 * - 跳到 baseline-compare 视图，把当前版本作为参数传入
 * - baseline-compare 支持按版本对比基线快照
 */
const handleCompare = (version: any) => {
  if (!version) return
  router.push({
    path: '/compliance/baselines/compare',
    query: {
      requirementId: String(route.params.id),
      versionId: String(version.id),
      versionNo: String(version.versionNo || ''),
    },
  })
}

/**
 * P1-6 修复：打开导出格式选择
 */
const openExportMenu = (version: any, _e: Event) => {
  activeVersion.value = version
  exportDialogVisible.value = true
}

/**
 * P1-6 修复：导出当前版本
 * - excel：构造 CSV blob 并触发下载（Excel 可直接打开 UTF-8 BOM）
 * - pdf：调用 window.print 浏览器原生打印为 PDF
 */
const handleExport = (version: any, format: 'excel' | 'pdf') => {
  if (!version) return
  exportDialogVisible.value = false
  if (format === 'excel') {
    exportAsCsv(version)
  } else {
    exportAsPdf(version)
  }
}

const exportAsCsv = (version: any) => {
  try {
    const header = ['版本号', '变更时间', '变更摘要', '快照']
    const lines = [
      header.join(','),
      [
        `v${version.versionNo || ''}`,
        formatDate(version.changedAt),
        escapeCsv(version.changeSummary || ''),
        escapeCsv(version.snapshot || ''),
      ].join(','),
    ]
    const csv = '﻿' + lines.join('\r\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `requirement-version-${version.versionNo || version.id}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`版本 v${version.versionNo} 已导出为 Excel`)
  } catch (e) {
    ElMessage.error('导出失败：' + (e as any)?.message)
  }
}

const exportAsPdf = (version: any) => {
  // P1-6：使用 window.print 走浏览器原生 PDF 导出，零依赖
  const w = window.open('', '_blank')
  if (!w) {
    ElMessage.warning('请允许浏览器弹窗以导出 PDF')
    return
  }
  const html = `
    <!doctype html><html><head><meta charset="utf-8"><title>版本 v${version.versionNo}</title>
    <style>body{font-family:Arial,sans-serif;padding:24px;color:#303133;}
    h1{font-size:18px;}table{width:100%;border-collapse:collapse;margin-top:12px;}
    td,th{border:1px solid #e4e7ed;padding:8px;font-size:13px;text-align:left;}
    th{background:#f5f7fa;}</style></head><body>
    <h1>需求版本快照 - v${version.versionNo}</h1>
    <table>
      <tr><th>版本号</th><td>v${version.versionNo || '-'}</td></tr>
      <tr><th>变更时间</th><td>${formatDate(version.changedAt)}</td></tr>
      <tr><th>变更摘要</th><td>${(version.changeSummary || '-').replace(/</g, '&lt;')}</td></tr>
      <tr><th>快照</th><td><pre>${(version.snapshot || '-').replace(/</g, '&lt;')}</pre></td></tr>
    </table>
    <script>window.onload=function(){window.print();}<\/script>
    </body></html>`
  w.document.write(html)
  w.document.close()
  ElMessage.success('请在打印对话框中选择"另存为 PDF"')
}

const escapeCsv = (s: string) => {
  if (s == null) return ''
  const str = String(s)
  if (/[",\r\n]/.test(str)) return '"' + str.replace(/"/g, '""') + '"'
  return str
}

onMounted(() => {
  loadVersions()
})
</script>

<style scoped>
.versions-container {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 20px;
  color: #303133;
}

.header-spacer {
  flex: 1;
}
</style>
