#!/usr/bin/env python3
"""R187 第二轮修复：删除 projectList.value / projects.value 写入（computed 只读）"""

import re
from pathlib import Path

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
    "Code/frontend/src/views/requirement/AIRequirementAssist.vue",
    "Code/frontend/src/views/requirement/KanbanBoard.vue",
    "Code/frontend/src/views/requirement/QualityScore.vue",
    "Code/frontend/src/views/requirement/RequirementTaskConvert.vue",
    "Code/frontend/src/views/requirement/TestCaseList.vue",
    "Code/frontend/src/views/risk/RiskRegister.vue",
    "Code/frontend/src/views/risk/RisksMatrix.vue",
    "Code/frontend/src/views/traceability/TraceCoverage.vue",
    "Code/frontend/src/views/traceability/TraceGaps.vue",
]


def process_file(path: Path) -> tuple[bool, str]:
    if not path.exists():
        return False, f"NOT_FOUND"

    original = path.read_text(encoding="utf-8")
    text = original
    changes = []

    # 1. 删除 `projectList.value = ...` 整行（包括 Array.isArray 模式、简单赋值等）
    # 模式：行首是空白 + projectList.value = ...
    new_text, n = re.subn(
        r"^[ \t]*projectList\.value\s*=[^\n]*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"deleted {n} projectList.value = lines")
        text = new_text

    # 2. 删除 `projects.value = ...` 整行（注意排除 useProjectStore 内部的 fetchProjects 逻辑）
    new_text, n = re.subn(
        r"^[ \t]*projects\.value\s*=[^\n]*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"deleted {n} projects.value = lines")
        text = new_text

    # 3. 替换 `await fetchProjects()` → `await ensureLoaded()`（仅在组件上下文，不在 store）
    # 但是这个风险大，先不处理（store.fetchProjects 不存在此模式）

    # 4. 删除内联的 `const res = await request.get('/projects', ...)` 整行（孤立的）
    new_text, n = re.subn(
        r"^[ \t]*const\s+\w+\s*=\s*await\s+request\.get\(['\"]/projects['\"][^\n]*\n",
        "",
        text,
        flags=re.MULTILINE,
    )
    if n:
        changes.append(f"deleted {n} inline request.get('/projects') lines")
        text = new_text

    # 5. 把 fetchProjects 函数体简化（保留函数但改为调用 ensureLoaded）
    # 模式：const fetchProjects = async () => {\n  try {\n    [空]  } catch {}\n}
    # → const fetchProjects = async () => { await ensureLoaded() }
    # 但 Dashboard 有 computeDcpCounts 副作用，不能直接简化
    # 安全做法：跳过此步，留人工处理

    if text != original:
        path.write_text(text, encoding="utf-8")
        return True, f"OK {path.name}: " + "; ".join(changes)
    return False, f"NO_CHANGE: {path.name}"


def main():
    root = Path("D:/zhutao/MED_RMS_PMS")
    ok = 0
    skip = 0
    for rel in TARGETS:
        path = root / rel
        changed, msg = process_file(path)
        print(msg)
        if changed:
            ok += 1
        else:
            skip += 1
    print(f"\n=== Summary: changed={ok} skip={skip} ===")


if __name__ == "__main__":
    main()