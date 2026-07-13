#!/usr/bin/env python3
"""R187 批量替换脚本：把目标 .vue 文件中的内联 projectName/getProjectName/loadProjects 改为 useProject composable

策略：
1. 删除 `import { projectApi }` 行（多种写法）
2. 删除 `import type { Project }` 行
3. 替换模板字符串 `:label="p.projectName"` → `:label="getProjectLabel(p.id)"`
4. 替换模板字符串 `:label="\`${p.projectNo} ${p.projectName}\`"` → `:label="getProjectLabel(p.id)"`
5. 替换 `{{ getProjectName(id) }}` → `{{ getProjectLabel(id) }}`
6. 删除 `const projectList = ref<...>([])` 行
7. 删除 `const loadProjects = async () => { ... }` 整个函数
8. 删除 `const getProjectName = ...` 整个函数（顶层）
9. 在 `onMounted(() => {` 前一行添加 `const { projectList, getProjectLabel, ensureLoaded } = useProject()`
10. 在 import 区域添加 `import { useProject } from '@/composables/useProject'`
11. 替换 `loadProjects()` 调用 → `ensureLoaded()`
"""

import re
import sys
from pathlib import Path

# 28 个目标文件
TARGETS = [
    "Code/frontend/src/views/dashboard/Dashboard.vue",
    "Code/frontend/src/views/compliance/Baselines.vue",
    "Code/frontend/src/views/compliance/ComplianceList.vue",
    "Code/frontend/src/views/compliance/ComplianceReports.vue",
    "Code/frontend/src/views/compliance/DhfPackage.vue",
    "Code/frontend/src/views/compliance/ErpsExport.vue",
    "Code/frontend/src/views/compliance/Iec62304.vue",
    "Code/frontend/src/views/compliance/ProblemReportCreate.vue",
    "Code/frontend/src/views/compliance/RegulationImpact.vue",
    "Code/frontend/src/views/compliance/SafetyClassification.vue",
    "Code/frontend/src/views/project/GanttView.vue",
    "Code/frontend/src/views/project/IpdGate.vue",
    "Code/frontend/src/views/project/ProjectActivity.vue",
    "Code/frontend/src/views/project/ProjectAuditLog.vue",
    "Code/frontend/src/views/project/ResourceManagement.vue",
    "Code/frontend/src/views/project/TaskBoard.vue",
    "Code/frontend/src/views/project/TemplateManagement.vue",
    "Code/frontend/src/views/project/WorklogView.vue",
    "Code/frontend/src/views/project/milestone/MilestoneList.vue",
    "Code/frontend/src/views/report/ReportCenter.vue",
    "Code/frontend/src/views/report/ReportExport.vue",
    "Code/frontend/src/views/report/ReportsCustom.vue",
    "Code/frontend/src/views/requirement/ReqCreate.vue",
    "Code/frontend/src/views/requirement/TestCaseList.vue",
    "Code/frontend/src/views/requirement/components/RequirementImportDialog.vue",
    "Code/frontend/src/views/risk/RiskRegister.vue",
    "Code/frontend/src/views/risk/RiskReport.vue",
    "Code/frontend/src/views/risk/RisksMatrix.vue",
    "Code/frontend/src/views/traceability/TraceCoverage.vue",
    "Code/frontend/src/views/traceability/TraceGaps.vue",
]


def process_file(path: Path) -> tuple[bool, str]:
    """处理单个文件，返回 (changed, message)"""
    if not path.exists():
        return False, f"NOT_FOUND: {path}"

    original = path.read_text(encoding="utf-8")
    text = original
    changes = []

    # 1. 删除 `import { projectApi ... }` 整行（多种写法）
    new_text, n = re.subn(
        r"^import \{ projectApi[^\n]*\}\s+from\s+['\"][^'\"]+['\"]\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"removed {n} projectApi import")
        text = new_text

    # 2. 删除 `import type { Project }` 行
    new_text, n = re.subn(
        r"^import\s+type\s+\{\s*Project[^\n]*\}\s+from\s+['\"][^'\"]+['\"]\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"removed {n} Project type import")
        text = new_text

    # 3. 模板字符串 `:label="p.projectName"` → `:label="getProjectLabel(p.id)"`
    new_text, n = re.subn(
        r':label="p\.projectName"',
        ':label="getProjectLabel(p.id)"',
        text,
    )
    if n:
        changes.append(f"label p.projectName → getProjectLabel: {n}")
        text = new_text

    # 4. 模板字符串 Dashboard 特殊格式 `:label="\`${p.projectNo} ${p.projectName}\`"`
    new_text, n = re.subn(
        r':label="`\$\{p\.projectNo\}\s+\$\{p\.projectName\}`"',
        ':label="getProjectLabel(p.id)"',
        text,
    )
    if n:
        changes.append(f"label template p.projectNo+name → getProjectLabel: {n}")
        text = new_text

    # 5. `{{ getProjectName(id) }}` → `{{ getProjectLabel(id) }}`
    new_text, n = re.subn(
        r"\{\{\s*getProjectName\(([^)]+)\)\s*\}\}",
        r"{{ getProjectLabel(\1) }}",
        text,
    )
    if n:
        changes.append(f"{{getProjectName}} → {{getProjectLabel}}: {n}")
        text = new_text

    # 6. 删除 `const projectList = ref<...>([])` 行
    new_text, n = re.subn(
        r"^const\s+projectList\s*=\s*ref<[^>]*>\(\[\]\)\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"removed projectList ref: {n}")
        text = new_text

    # 7. 删除内联 `getProjectName` 函数（包括 multi-line）
    new_text, n = re.subn(
        r"^const\s+getProjectName\s*=\s*\([^)]*\)\s*=>\s*\{[\s\S]*?\n\}\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"removed getProjectName function: {n}")
        text = new_text

    # 8. 删除 `loadProjects` 函数（包括 multi-line）
    new_text, n = re.subn(
        r"^const\s+loadProjects\s*=\s*async\s*\(\)\s*=>\s*\{[\s\S]*?\n\}\s*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"removed loadProjects function: {n}")
        text = new_text

    # 9. `loadProjects()` 调用 → `ensureLoaded()`
    new_text, n = re.subn(r"\bloadProjects\(\)", "ensureLoaded()", text)
    if n:
        changes.append(f"loadProjects() → ensureLoaded(): {n}")
        text = new_text

    # 10. 如果还没有 useProject import，添加
    if "useProject } from '@/composables/useProject'" not in text and "useProject } from '../../composables/useProject'" not in text and "useProject } from \"@/composables/useProject\"" not in text and "useProject } from \"../../composables/useProject\"" not in text:
        # 根据 import 风格选择路径
        # 默认用 @/composables/useProject（@ 是 src 别名，vite.config.ts 已配）
        # 注意：原代码可能用相对路径，我们用 @ 别名更简洁
        new_text = re.sub(
            r"(<script setup lang=\"ts\">\n)",
            r"\1import { useProject } from '@/composables/useProject'\n",
            text,
            count=1,
        )
        if new_text != text:
            changes.append("added useProject import")
            text = new_text

    # 11. 在 onMounted 前添加 `const { projectList, getProjectLabel, ensureLoaded } = useProject()`
    # 找 onMounted(() => {  之前的位置
    if "useProject()" not in text:
        # 找 onMounted 行
        m = re.search(r"^onMounted\(", text, flags=re.MULTILINE)
        if m:
            # 在 onMounted 前一行插入
            pos = m.start()
            # 找 pos 前最近一个空行（即 onMounted 所在函数顶部）
            # 简单做法：在 onMounted 上方加 const { ... } = useProject()
            # 但保留空行
            text = text[:pos] + "const { projectList, getProjectLabel, ensureLoaded } = useProject()\n\n" + text[pos:]
            changes.append("added useProject() destructuring")
        else:
            # 如果没找到 onMounted，在 script setup 结束前加
            m = re.search(r"^</script>", text, flags=re.MULTILINE)
            if m:
                pos = m.start()
                text = text[:pos] + "\nconst { projectList, getProjectLabel, ensureLoaded } = useProject()\n" + text[pos:]
                changes.append("added useProject() before </script>")

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True, f"OK {path.name}: " + "; ".join(changes)
    return False, f"NO_CHANGE: {path.name}"


def main():
    root = Path("D:/zhutao/MED_RMS_PMS")
    ok = 0
    skip = 0
    not_found = 0
    for rel in TARGETS:
        path = root / rel
        changed, msg = process_file(path)
        print(msg)
        if changed:
            ok += 1
        elif "NOT_FOUND" in msg:
            not_found += 1
        else:
            skip += 1
    print(f"\n=== Summary: changed={ok} skip={skip} not_found={not_found} ===")


if __name__ == "__main__":
    main()