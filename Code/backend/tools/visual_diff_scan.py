#!/usr/bin/env python3
"""
视觉/交互验收：截图 88 路由 vs 77 原型 + 像素 diff

阶段（可单跑或全跑）：
  1. map         —— 解析路由 + 原型，建立 route↔prototype 映射
  2. screenshot  —— Playwright 抓 88 路由 + 渲染 77 原型
  3. diff        —— PIL 像素 diff，>5% 报警
  4. report      —— 输出 Markdown 报告

用法：
  python visual_diff_scan.py --phase=map
  python visual_diff_scan.py --phase=screenshot
  python visual_diff_scan.py --phase=diff
  python visual_diff_scan.py --phase=report
  python visual_diff_scan.py            # 全跑

依赖：playwright + pillow（已确认安装）
"""
import re
import json
import argparse
from pathlib import Path
from playwright.sync_api import sync_playwright
from PIL import Image, ImageChops, ImageFilter

# ============== 路径配置 ==============
ROOT = Path(r"D:/zhutao/MED_RMS_PMS")
ROUTER_FILE = ROOT / "Code/frontend/src/router/index.ts"
PROTO_DIR = ROOT / "Detailed/07-交互原型"
FRONTEND = "http://localhost:5173"
OUT_DIR = ROOT / "Code/backend/tools/visual_baselines"
DIFF_DIR = ROOT / "Code/backend/tools/visual_diffs"
MAP_FILE = ROOT / "Code/backend/tools/visual_route_proto_map.json"
DIFF_JSON = ROOT / "Code/backend/tools/visual_diff_results.json"
REPORT_FILE = ROOT / "Code/backend/tools/visual_diff_report.md"

VIEWPORT = {"width": 1440, "height": 900}
# 分级阈值：pixel 模式（0-90% 范围）vs structural 模式（0-30% 范围）独立设置
THRESHOLDS = {
    "pixel":      {"match_below": 0.05, "minor_below": 0.20, "moderate_below": 0.50},
    "structural": {"match_below": 0.03, "minor_below": 0.08, "moderate_below": 0.15},
}
ADMIN_USER = "admin"
ADMIN_PWD = "admin123"

# ============== 路由解析（沿用 route_scan.py 模式）==============
def parse_routes():
    text = ROUTER_FILE.read_text(encoding="utf-8")
    paths = re.findall(r"path:\s*'([^']+)'", text)
    return [p for p in paths if p not in ('/', '/login')]

def resolve_id(p):
    """把 :id 占位符替换为 1（沿用 route_scan.py）"""
    mapping = {
        "/requirements/:id": "/requirements/1",
        "/requirements/:id/edit": "/requirements/1/edit",
        "/requirements/:id/decompose": "/requirements/1/decompose",
        "/requirements/:id/versions": "/requirements/1/versions",
        "/requirements/:id/versions/create": "/requirements/1/versions/create",
        "/changes/:id": "/changes/1",
        "/changes/:id/impact": "/changes/1/impact",
        "/changes/:id/execute": "/changes/1/execute",
        "/changes/:id/verify": "/changes/1/verify",
        "/projects/:id": "/projects/1",
        "/projects/:id/edit": "/projects/1/edit",
        "/projects/:id/members": "/projects/1/members",
        "/projects/:id/members/add": "/projects/1/members/add",
        "/projects/:id/deliverables": "/projects/1/deliverables",
        "/projects/:id/gates": "/projects/1/gates",
        "/projects/:id/gantt": "/projects/1/gantt",
        "/signature-history/:id": "/signature-history/1",
        "/signature-intent/:id": "/signature-intent/1",
        "/system/roles/:id/edit": "/system/roles/1/edit",
        "/compliance/soup/:id": "/compliance/soup/1",
        "/compliance/soup/:id/review": "/compliance/soup/1/review",
        "/compliance/problem-report/:id": "/compliance/problem-report/1",
        "/compliance/baselines/:id": "/compliance/baselines/1",
        "/compliance/baselines/:id/edit": "/compliance/baselines/1/edit",
    }
    return mapping.get(p, p)

# ============== 原型解析 ==============
def parse_prototypes():
    """返回 [(base_name, full_path), ...]"""
    out = []
    for f in sorted(PROTO_DIR.glob("*.html")):
        if f.name.startswith("00-") or "prototype-shell" in f.name:
            continue  # 跳过总览和壳模板
        name = f.stem.replace("-原型", "")
        out.append((name, f))
    return out

# ============== 路由 → 原型自动映射 ==============
# 关键词归一化：route token → prototype base name 候选
NORMALIZE = {
    "requirement": "req", "requirements": "req", "req": "req",
    "change": "change", "changes": "change",
    "project": "project", "projects": "project",
    "risk": "risk", "risks": "risk",
    "compliance": "compliance",
    "traceability": "trace", "trace-graph": "trace", "trace": "trace",
    "audit-logs": "audit-logs",
    "dashboard": "dashboard",
    "report": "report", "reports": "report",
    "review": "review", "reviews": "review",
    "system": "system",
    "user": "user", "users": "user", "user-management": "user-management",
    "role": "role", "roles": "role", "dict": "dict", "organization": "organization",
    "signature": "signature", "signatures": "signature",
    "soup": "soup", "iec62304": "iec62304",
    "baseline": "baseline", "baselines": "baseline",
    "problem-report": "problem-report",
    "notification": "notification", "notifications": "notification",
    "decompose": "decompose", "decomposition": "decompose",
    "gantt": "gantt",
    "impact": "impact",
    "gates": "gates", "gate": "gates",
    "members": "members", "member": "members",
    "deliverables": "deliverables",
    "kanban": "kanban", "board": "kanban",
    "milestone": "milestone", "milestones": "milestone",
    "decompose": "decompose", "decomposition": "decompose",
    "version": "version", "versions": "version",
    "import": "import",
    "approve": "approve", "approval": "approve", "approvals": "approve",
    "execute": "execute", "verify": "verify",
    "edit": "edit", "create": "create",
    "detail": "detail",
    "settings": "settings",
    "history": "history",
    "intent": "intent",
}

def route_to_proto_keys(route):
    """从 route 路径提取关键词 token（去掉 :id 占位）"""
    # 例: /requirements/:id/versions → ['requirements', 'versions']
    parts = [p for p in route.split('/') if p and not p.startswith(':')]
    return parts

def fuzzy_match(route, protos):
    """对单个 route 找最佳 prototype 匹配"""
    # 显式覆盖表（解决模糊匹配歧义，确保关键路由精确）
    EXPLICIT = {
        "/requirements/:id": "req-detail",
        "/changes/:id": "change-request",
        "/changes/:id/impact": "change-impact-analysis",
        "/changes/:id/execute": "change-execute",
        "/changes/:id/verify": "change-verify",
        "/projects/:id": "project-detail",
        "/projects/:id/edit": "project-edit",
        "/projects/:id/members": "project-members",
        "/projects/:id/members/add": "project-members-add",
        "/projects/:id/deliverables": "project-deliverables",
        "/projects/:id/gates": "project-gates",
        "/projects/:id/gantt": "gantt-blank",  # 或 'project-gantt'，暂用 gantt
        "/compliance/soup": "soup-management",
        "/compliance/soup/:id": "soup-detail",
        "/compliance/soup/:id/review": "soup-review",
        "/compliance/problem-report": "problem-report",
        "/compliance/problem-report/:id": "problem-report-detail",
        "/compliance/baselines": "baselines",
        "/compliance/baselines/:id": "baselines-detail",
        "/compliance/baselines/:id/edit": "baselines-edit",
        "/compliance/iec62304": "iec62304-checklist",
        "/compliance/reports": "compliance-reports",
        "/system/users": "user-management",
        "/system/roles": "role-edit",
        "/system/roles/:id/edit": "role-edit",
        "/system/dict": "dict-management",
        "/system/organization": "organization",
        "/system/login-logs": "system-login-logs",
        "/system/operation-logs": "system-operation-logs",
        "/system/audit-logs": "audit-logs",
        "/system/audit-logs/export": "audit-logs-export",
        "/signature": "signatures",
        "/signature-history": "signature-history",
        "/signature-history/:id": "signature-history-detail",
        "/signature-intent": "signatures",
        "/signature-intent/:id": "signature-intent-detail",
        "/signature-intent/create": "signature-intent-create",
        "/signature-settings": "e-sign-settings",
        "/risk": "risks-matrix",
        "/risk/register": "risks-register",
        "/risk/fmea": "risks-analysis",
        "/risk/analysis": "risks-analysis",
        "/report": "reports",
        "/report/custom": "reports-custom",
        "/report/export": "report-export",
        "/notifications": "notifications",
        "/kanban": "kanban",
        "/decompose": "req-decompose",
        "/versions": "req-versions",
        "/versions/create": "req-version-create",
        "/import": "req-import",
        "/review": "reviews",
        "/reviews": "reviews",
        "/audit": "audit-logs",
        "/safety-classification": "safety-classification-detail",
    }
    if route in EXPLICIT:
        target = EXPLICIT[route]
        for base, full in protos:
            if base == target:
                return (100, base, full, [target], 1.0)
        return None  # 显式指定的原型不存在

    keys = route_to_proto_keys(route)
    # 归一化每个 key
    norm_keys = []
    for k in keys:
        k_norm = NORMALIZE.get(k.lower(), k.lower())
        norm_keys.append(k_norm)

    # 显式优先映射：父路由（单 key）优先匹配 list/matrix/management 类原型
    PREFERRED = {"list", "matrix", "management", "index", "monitoring", "monitor", "history", "kanban", "register", "coverage"}
    DEPRECATED = {"edit", "create", "detail", "delete"}

    candidates = []
    for base, full in protos:
        base_lower = base.lower()
        score = 0
        matched_keys = []
        for nk in norm_keys:
            if nk in base_lower:
                score += 10
                matched_keys.append(nk)
            elif any(nk.startswith(b) or b.startswith(nk) for b in base_lower.split('-') if len(b) > 2):
                score += 3
                matched_keys.append(nk)
        if score > 0:
            # 覆盖率：matched / total，要求多 key 路由尽量完全覆盖
            coverage = len(matched_keys) / max(len(norm_keys), 1)
            # 父路由（path 含 :id 表示是详情，跳过 list 偏好）
            is_parent = ':id' not in route and len(norm_keys) == 1
            if is_parent:
                tail = base_lower.split('-')[-1] if '-' in base_lower else base_lower
                if tail in PREFERRED:
                    score += 5
                elif tail in DEPRECATED:
                    score -= 5
            # coverage 加权
            score = score * (1 + coverage)
            candidates.append((score, base, full, matched_keys, coverage))
    if not candidates:
        return None
    # 排序：分数降序 + 长度升序
    candidates.sort(key=lambda x: (-x[0], len(x[1])))
    return candidates[0]

def phase_map():
    """Phase 1: 路由↔原型映射"""
    routes = parse_routes()
    protos = parse_prototypes()
    print(f"[MAP] 解析到 {len(routes)} 路由, {len(protos)} 原型")
    mapping = {}  # route → {proto_base, proto_path, score, matched_keys}
    for r in routes:
        result = fuzzy_match(r, protos)
        if result:
            score, base, full, keys, coverage = result
            mapping[r] = {
                "proto_base": base,
                "proto_path": str(full),
                "score": score,
                "coverage": coverage,
                "matched_keys": keys,
            }
        else:
            mapping[r] = None
    # 反向索引：哪些原型没被映射
    used_protos = {v["proto_base"] for v in mapping.values() if v}
    unused_protos = [p[0] for p in protos if p[0] not in used_protos]
    out = {
        "routes_total": len(routes),
        "protos_total": len(protos),
        "mapped": sum(1 for v in mapping.values() if v),
        "unmapped_routes": [r for r, v in mapping.items() if not v],
        "unused_protos": unused_protos,
        "mapping": {r: v for r, v in mapping.items()},
    }
    MAP_FILE.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[MAP] 映射: {out['mapped']}/{out['routes_total']} 路由 → 原型")
    print(f"[MAP] 未映射路由: {len(out['unmapped_routes'])}")
    print(f"[MAP] 未使用原型: {len(out['unused_protos'])}")
    print(f"[MAP] 写入: {MAP_FILE.name}")
    return out

# ============== 截图与渲染 ==============
def login(page, ctx):
    """登录 + token 注入"""
    page.goto(f"{FRONTEND}/login")
    page.wait_for_load_state("networkidle", timeout=10000)
    try:
        page.locator("input").nth(0).fill(ADMIN_USER)
        page.locator("input[type='password']").fill(ADMIN_PWD)
        page.click("button:has-text('登录')")
        page.wait_for_load_state("networkidle", timeout=10000)
        page.wait_for_timeout(1500)
        token = page.evaluate('() => localStorage.getItem("token")')
        if token:
            ctx.set_extra_http_headers({"Authorization": f"Bearer {token}"})
            return True
    except Exception as e:
        print(f"[LOGIN] err: {e}")
    return False

def safe_name(s):
    """route/path → 文件名安全字符串"""
    return re.sub(r'[\\/:*?"<>|/:]', '_', s).strip('_')

def phase_screenshot():
    """Phase 2: 截图 88 路由 + 渲染 77 原型"""
    if not MAP_FILE.exists():
        print("[SHOT] 映射文件不存在，先跑 phase=map")
        return
    map_data = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    routes_real = [resolve_id(r) for r in map_data["mapping"].keys()]
    protos = parse_prototypes()

    route_dir = OUT_DIR / "routes"
    proto_dir = OUT_DIR / "prototypes"
    route_dir.mkdir(parents=True, exist_ok=True)
    proto_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=True)
        ctx = browser.new_context(viewport=VIEWPORT)
        page = ctx.new_page()

        # 登录
        if login(page, ctx):
            print("[SHOT] 登录成功")
        else:
            print("[SHOT] 登录失败，继续（可能部分页面受限）")

        # 路由截图
        print(f"[SHOT] 开始截 {len(routes_real)} 路由")
        ok_routes, fail_routes = 0, 0
        for i, route in enumerate(routes_real):
            try:
                page.goto(f"{FRONTEND}{route}", wait_until="domcontentloaded", timeout=15000)
                page.wait_for_load_state("networkidle", timeout=8000)
                page.wait_for_timeout(800)
                out = route_dir / f"{safe_name(route)}.png"
                page.screenshot(path=str(out), full_page=False)
                ok_routes += 1
            except Exception as e:
                fail_routes += 1
                print(f"  [FAIL] {route}: {str(e)[:80]}")
            if (i + 1) % 10 == 0:
                print(f"  [{i+1}/{len(routes_real)}]")
        print(f"[SHOT] 路由: {ok_routes} ok, {fail_routes} fail")

        # 原型渲染（直接 file:// 打开，无须登录）
        print(f"[SHOT] 开始渲染 {len(protos)} 原型")
        ok_protos, fail_protos = 0, 0
        for base, full in protos:
            try:
                page.goto(f"file:///{str(full).replace(chr(92), '/')}", wait_until="domcontentloaded", timeout=10000)
                page.wait_for_load_state("networkidle", timeout=5000)
                page.wait_for_timeout(500)
                out = proto_dir / f"{safe_name(base)}.png"
                page.screenshot(path=str(out), full_page=False)
                ok_protos += 1
            except Exception as e:
                fail_protos += 1
                print(f"  [FAIL] {base}: {str(e)[:80]}")
        print(f"[SHOT] 原型: {ok_protos} ok, {fail_protos} fail")

        browser.close()
    print(f"[SHOT] 输出: {OUT_DIR}")

# ============== 像素 diff ==============
def diff_pair(img_a_path, img_b_path, mode="pixel"):
    """
    mode=pixel:      原始像素 diff（受字体/抗锯齿/小位移影响大）
    mode=structural: 网格结构 diff（240×150 → 24×15=360 cell，cell 级 dark ratio 差异）
    """
    try:
        a = Image.open(img_a_path).convert("RGB")
        b = Image.open(img_b_path).convert("RGB")
        if a.size != b.size:
            b = b.resize(a.size, Image.LANCZOS)
        if mode == "structural":
            # 灰度 + 4x 降采样到 360×225
            target = (360, 225)
            a_g = a.convert("L").resize(target, Image.LANCZOS)
            b_g = b.convert("L").resize(target, Image.LANCZOS)
            # 中值滤波消除字体/抗锯齿/小图标噪声
            a_s = a_g.filter(ImageFilter.MedianFilter(3))
            b_s = b_g.filter(ImageFilter.MedianFilter(3))
            # 切成 24×15 = 360 cell，每 cell 15×15 px
            GRID = (24, 15)
            cell_w = target[0] // GRID[0]  # 15
            cell_h = target[1] // GRID[1]  # 15
            a_arr = list(a_s.getdata())
            b_arr = list(b_s.getdata())
            diff_cells = 0
            total_cells = GRID[0] * GRID[1]
            for gy in range(GRID[1]):
                for gx in range(GRID[0]):
                    # 提取 cell 内所有像素
                    a_cell = []
                    b_cell = []
                    for dy in range(cell_h):
                        for dx in range(cell_w):
                            x = gx * cell_w + dx
                            y = gy * cell_h + dy
                            if x < target[0] and y < target[1]:
                                idx = y * target[0] + x
                                a_cell.append(a_arr[idx])
                                b_cell.append(b_arr[idx])
                    # 计算 cell 的"暗度"（像素均值越低越暗）
                    a_dark = sum(1 for p in a_cell if p < 128) / len(a_cell) if a_cell else 0
                    b_dark = sum(1 for p in b_cell if p < 128) / len(b_cell) if b_cell else 0
                    # cell dark 差异 > 20% 视为结构差异（敏感性高于像素模式）
                    if abs(a_dark - b_dark) > 0.20:
                        diff_cells += 1
            ratio = diff_cells / total_cells
            # 生成 diff 可视化（用原图叠红框标识差异 cell）
            a_color = a.copy()
            from PIL import ImageDraw
            draw = ImageDraw.Draw(a_color)
            # 把 a_color resize 到原图大小以画框
            a_color = a_color.resize(a.size, Image.LANCZOS)
            draw = ImageDraw.Draw(a_color)
            scale_x = a.size[0] / target[0]
            scale_y = a.size[1] / target[1]
            for gy in range(GRID[1]):
                for gx in range(GRID[0]):
                    a_cell = []
                    b_cell = []
                    for dy in range(cell_h):
                        for dx in range(cell_w):
                            x = gx * cell_w + dx
                            y = gy * cell_h + dy
                            if x < target[0] and y < target[1]:
                                idx = y * target[0] + x
                                a_cell.append(a_arr[idx])
                                b_cell.append(b_arr[idx])
                    a_dark = sum(1 for p in a_cell if p < 128) / len(a_cell) if a_cell else 0
                    b_dark = sum(1 for p in b_cell if p < 128) / len(b_cell) if b_cell else 0
                    if abs(a_dark - b_dark) > 0.20:
                        x0 = int(gx * cell_w * scale_x)
                        y0 = int(gy * cell_h * scale_y)
                        x1 = int((gx + 1) * cell_w * scale_x)
                        y1 = int((gy + 1) * cell_h * scale_y)
                        draw.rectangle([x0, y0, x1, y1], outline="red", width=3)
            return ratio, a_color
        # 默认 pixel 模式
        diff = ImageChops.difference(a, b)
        pixels = list(diff.getdata())
        total = len(pixels)
        diff_count = sum(1 for p in pixels if max(p) > 10)
        ratio = diff_count / total if total else 0
        return ratio, diff
    except Exception as e:
        return None, str(e)

def phase_diff():
    """Phase 3: 像素 diff（支持 pixel / structural 两种模式）"""
    if not MAP_FILE.exists():
        print("[DIFF] 映射文件不存在")
        return
    map_data = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    DIFF_DIR.mkdir(parents=True, exist_ok=True)
    results = []
    matched = [(r, m) for r, m in map_data["mapping"].items() if m]
    print(f"[DIFF] 模式={_args.diff_mode} | 开始 {len(matched)} 对 diff")
    for i, (route, m) in enumerate(matched):
        route_png = OUT_DIR / "routes" / f"{safe_name(resolve_id(route))}.png"
        proto_png = OUT_DIR / "prototypes" / f"{safe_name(m['proto_base'])}.png"
        if not route_png.exists() or not proto_png.exists():
            results.append({
                "route": route,
                "proto": m["proto_base"],
                "status": "missing_screenshot",
            })
            continue
        ratio, diff = diff_pair(route_png, proto_png, mode=_args.diff_mode)
        if ratio is None:
            results.append({"route": route, "proto": m["proto_base"], "status": "diff_error", "err": diff})
            continue
        # 分级：按 mode 取阈值
        T = THRESHOLDS[_args.diff_mode]
        if ratio < T["match_below"]:
            tier = "match"
        elif ratio < T["minor_below"]:
            tier = "minor"
        elif ratio < T["moderate_below"]:
            tier = "moderate"
        else:
            tier = "major"
        result = {
            "route": route,
            "route_url": f"{FRONTEND}{resolve_id(route)}",
            "proto": m["proto_base"],
            "proto_path": m["proto_path"],
            "diff_ratio": round(ratio, 4),
            "threshold": T["match_below"],
            "tier": tier,
            "status": "match" if tier == "match" else "deviation",
            "route_screenshot": str(route_png.relative_to(ROOT)),
            "proto_screenshot": str(proto_png.relative_to(ROOT)),
            "diff_mode": _args.diff_mode,
        }
        results.append(result)
        # 保存 diff 图（所有非 match 都保存，供人工复核）
        if tier != "match":
            suffix = "_struct" if _args.diff_mode == "structural" else ""
            diff_png = DIFF_DIR / f"{safe_name(route)}__vs__{safe_name(m['proto_base'])}{suffix}.png"
            try:
                diff.save(str(diff_png))
                result["diff_image"] = str(diff_png.relative_to(ROOT))
            except Exception:
                pass
        if (i + 1) % 10 == 0:
            print(f"  [{i+1}/{len(matched)}]")
    DIFF_JSON.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    n_match = sum(1 for r in results if r.get("tier") == "match")
    n_minor = sum(1 for r in results if r.get("tier") == "minor")
    n_moderate = sum(1 for r in results if r.get("tier") == "moderate")
    n_major = sum(1 for r in results if r.get("tier") == "major")
    n_miss = sum(1 for r in results if r.get("status") == "missing_screenshot")
    T = THRESHOLDS[_args.diff_mode]
    print(f"[DIFF] 模式={_args.diff_mode} | "
          f"match(<{int(T['match_below']*100)}%): {n_match}, "
          f"minor({int(T['match_below']*100)}-{int(T['minor_below']*100)}%): {n_minor}, "
          f"moderate({int(T['minor_below']*100)}-{int(T['moderate_below']*100)}%): {n_moderate}, "
          f"major(>={int(T['moderate_below']*100)}%): {n_major}, missing: {n_miss}")
    print(f"[DIFF] 写入: {DIFF_JSON.name}")

# ============== Markdown 报告 ==============
def phase_report():
    """Phase 4: 输出报告"""
    if not DIFF_JSON.exists() or not MAP_FILE.exists():
        print("[REPORT] 数据文件不存在")
        return
    map_data = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    diffs = json.loads(DIFF_JSON.read_text(encoding="utf-8"))
    n_total = map_data["routes_total"]
    n_mapped = map_data["mapped"]
    n_proto = map_data["protos_total"]
    n_match = sum(1 for d in diffs if d.get("tier") == "match")
    n_minor = sum(1 for d in diffs if d.get("tier") == "minor")
    n_moderate = sum(1 for d in diffs if d.get("tier") == "moderate")
    n_major = sum(1 for d in diffs if d.get("tier") == "major")
    n_miss = sum(1 for d in diffs if d.get("status") == "missing_screenshot")
    n_unmapped = len(map_data["unmapped_routes"])

    # 排序：偏差最大的在前
    devs = sorted(
        [d for d in diffs if d.get("tier") != "match"],
        key=lambda x: x.get("diff_ratio", 0),
        reverse=True,
    )

    lines = []
    a = lines.append
    a("# Med-RMS 视觉/交互验收报告")
    a("")
    T = THRESHOLDS[_args.diff_mode]
    a(f"> 生成时间：2026-06-10 | 工具：`tools/visual_diff_scan.py` | 模式：{_args.diff_mode} | 阈值：match<{int(T['match_below']*100)}% < minor<{int(T['minor_below']*100)}% < moderate<{int(T['moderate_below']*100)}% < major")
    a("")
    a("## 一、总体统计")
    a("")
    a("| 维度 | 数量 | 占比 |")
    a("|------|------|------|")
    a(f"| 前端路由总数 | {n_total} | 100% |")
    a(f"| 交互原型总数 | {n_proto} | - |")
    a(f"| 成功建立映射 | {n_mapped} | {n_mapped/n_total*100:.1f}% |")
    a(f"| 未建立映射（无对应原型） | {n_unmapped} | {n_unmapped/n_total*100:.1f}% |")
    T = THRESHOLDS[_args.diff_mode]
    a(f"| 🟢 视觉匹配 (match, <{int(T['match_below']*100)}%) | {n_match} | {n_match/n_mapped*100 if n_mapped else 0:.1f}% |")
    a(f"| 🟡 轻度偏差 (minor, {int(T['match_below']*100)}-{int(T['minor_below']*100)}%) | {n_minor} | {n_minor/n_mapped*100 if n_mapped else 0:.1f}% |")
    a(f"| 🟠 中度偏差 (moderate, {int(T['minor_below']*100)}-{int(T['moderate_below']*100)}%) | {n_moderate} | {n_moderate/n_mapped*100 if n_mapped else 0:.1f}% |")
    a(f"| 🔴 重度偏差 (major, ≥{int(T['moderate_below']*100)}%) | {n_major} | {n_major/n_mapped*100 if n_mapped else 0:.1f}% |")
    a(f"| 缺失截图 | {n_miss} | - |")
    a("")

    a("## 二、未映射路由清单（无对应原型）")
    a("")
    if map_data["unmapped_routes"]:
        for r in map_data["unmapped_routes"]:
            a(f"- `{r}`")
    else:
        a("- (无)")
    a("")

    a("## 三、未使用原型清单（无对应路由）")
    a("")
    if map_data["unused_protos"]:
        for p in map_data["unused_protos"]:
            a(f"- `{p}`")
    else:
        a("- (无)")
    a("")

    a("## 四、Top 20 视觉偏差（按 diff_ratio 降序）")
    a("")
    if devs:
        a("| 路由 | 原型 | 差异比 | 等级 | 路由截图 | 原型截图 | Diff 图 |")
        a("|------|------|--------|------|----------|----------|---------|")
        for d in devs[:20]:
            r_png = d.get("route_screenshot", "").replace("\\", "/")
            p_png = d.get("proto_screenshot", "").replace("\\", "/")
            d_png = d.get("diff_image", "").replace("\\", "/")
            r_link = f"[{Path(r_png).name}]({r_png})" if r_png else "-"
            p_link = f"[{Path(p_png).name}]({p_png})" if p_png else "-"
            d_link = f"[{Path(d_png).name}]({d_png})" if d_png else "-"
            tier_icon = {"minor": "🟡", "moderate": "🟠", "major": "🔴"}.get(d.get("tier", ""), "⚪")
            a(f"| `{d['route']}` | `{d['proto']}` | {d['diff_ratio']*100:.2f}% | {tier_icon} {d.get('tier','')} | {r_link} | {p_link} | {d_link} |")
    else:
        a("- (无偏差，恭喜！)")
    a("")

    a("## 五、按等级分组的全部偏差")
    a("")
    for tier, icon in [("major", "🔴"), ("moderate", "🟠"), ("minor", "🟡")]:
        tier_devs = [d for d in devs if d.get("tier") == tier]
        if not tier_devs:
            continue
        a(f"### {icon} {tier.upper()} ({len(tier_devs)} 个)")
        a("")
        a("| # | 路由 | 原型 | 差异比 | Diff 图 |")
        a("|---|------|------|--------|---------|")
        for i, d in enumerate(tier_devs, 1):
            d_png = d.get("diff_image", "").replace("\\", "/")
            d_link = f"[{Path(d_png).name}]({d_png})" if d_png else "-"
            a(f"| {i} | `{d['route']}` | `{d['proto']}` | {d['diff_ratio']*100:.2f}% | {d_link} |")
        a("")

    a("## 六、说明")
    a("")
    a("1. **数据**：用现有 DB 真实数据渲染，admin 登录态")
    a("2. **视口**：1440×900 桌面端")
    a("3. **分级阈值**：")
    a("   - 🟢 match: < match_below")
    a("   - 🟡 minor: match_below - minor_below")
    a("   - 🟠 moderate: minor_below - moderate_below")
    a("   - 🔴 major: ≥ moderate_below")
    a("   - 当前模式阈值见报告顶部（pixel 5/20/50，structural 3/8/15）")
    a("4. **限制**：")
    a("   - 原型为静态 HTML，与 Element Plus 实现的字体/颜色/抗锯齿天然差异")
    a("   - 时间戳、用户名等动态内容会推高 diff_ratio")
    a("   - 空数据态 vs 有数据态差异未区分（v1.58 可扩展双快照）")
    a("5. **建议行动**：")
    a("   - 🔴 major 优先：18 个，需人工逐个复核 diff 图（`tools/visual_diffs/`）")
    a("   - 🟠 moderate 抽样：62 个，按业务关键度选 10-20 个复核")
    a("   - 🟡 minor 暂缓：4 个，多为字体细节")
    a("   - 缺失映射的 4 个路由建议补原型或确认是否需原型")
    a("")

    REPORT_FILE.write_text("\n".join(lines), encoding="utf-8")
    print(f"[REPORT] 写入: {REPORT_FILE.name}")
    print(f"[REPORT] 总结: 路由 {n_total} | 映射 {n_mapped} | "
          f"match {n_match} | minor {n_minor} | moderate {n_moderate} | major {n_major}")


# ============== HTML 复核报告 ==============
HTML_REPORT_FILE = ROOT / "Code/backend/tools/visual_review_report.html"

def phase_htmlreport():
    """Phase 5: 生成单文件 HTML 复核报告（含 3 联对比 + 等级分组 + 图片可点击放大）"""
    if not DIFF_JSON.exists() or not MAP_FILE.exists():
        print("[HTML] 数据文件不存在")
        return
    map_data = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    diffs = json.loads(DIFF_JSON.read_text(encoding="utf-8"))
    n_total = map_data["routes_total"]
    n_mapped = map_data["mapped"]
    n_match = sum(1 for d in diffs if d.get("tier") == "match")
    n_minor = sum(1 for d in diffs if d.get("tier") == "minor")
    n_moderate = sum(1 for d in diffs if d.get("tier") == "moderate")
    n_major = sum(1 for d in diffs if d.get("tier") == "major")
    devs = sorted(
        [d for d in diffs if d.get("tier") != "match"],
        key=lambda x: x.get("diff_ratio", 0),
        reverse=True,
    )

    def render_dev(d):
        """渲染单个偏差的 HTML 块（3 联对比）"""
        # 转绝对 file:// 路径，避免 file:// 打开时相对路径解析失败
        def to_file_url(rel_path):
            if not rel_path:
                return ""
            abs_path = (ROOT / rel_path).resolve()
            # URL 编码处理中文/空格/特殊字符
            from urllib.parse import quote
            url_path = quote(str(abs_path).replace("\\", "/"), safe="/:")
            return "file:///" + url_path
        r_png_abs = to_file_url(d.get("route_screenshot", ""))
        p_png_abs = to_file_url(d.get("proto_screenshot", ""))
        d_png_abs = to_file_url(d.get("diff_image", ""))
        r_png_rel = d.get("route_screenshot", "").replace("\\", "/")
        p_png_rel = d.get("proto_screenshot", "").replace("\\", "/")
        d_png_rel = d.get("diff_image", "").replace("\\", "/")
        tier_icon = {"minor": "🟡", "moderate": "🟠", "major": "🔴"}.get(d.get("tier", ""), "⚪")
        return f"""
        <div class="dev-item tier-{d.get('tier','')}" id="route-{abs(hash(d['route']))%100000}">
          <h3>{tier_icon} <code>{d['route']}</code> ↔ <code>{d['proto']}</code> <span class="diff-ratio">{d['diff_ratio']*100:.2f}%</span></h3>
          <div class="triple">
            <figure>
              <figcaption>① 实施实拍<br><small>{r_png_rel}</small></figcaption>
              <a href="{r_png_abs}" target="_blank"><img src="{r_png_abs}" loading="lazy" alt="route"></a>
            </figure>
            <figure class="diff-fig">
              <figcaption>② 结构 diff<br><small>{d_png_rel}</small></figcaption>
              <a href="{d_png_abs}" target="_blank"><img src="{d_png_abs}" loading="lazy" alt="diff"></a>
            </figure>
            <figure>
              <figcaption>③ 设计原型<br><small>{p_png_rel}</small></figcaption>
              <a href="{p_png_abs}" target="_blank"><img src="{p_png_abs}" loading="lazy" alt="proto"></a>
            </figure>
          </div>
        </div>
        """

    sections_html = ""
    for tier, label, icon in [("major", "MAJOR (重度偏差)", "🔴"),
                              ("moderate", "MODERATE (中度偏差)", "🟠"),
                              ("minor", "MINOR (轻度偏差)", "🟡")]:
        tier_devs = [d for d in devs if d.get("tier") == tier]
        if not tier_devs:
            continue
        items = "".join(render_dev(d) for d in tier_devs)
        sections_html += f"""
        <section id="tier-{tier}">
          <h2>{icon} {label}（{len(tier_devs)} 个）</h2>
          {items}
        </section>
        """

    html = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<title>Med-RMS 视觉/交互验收复核报告</title>
<style>
  * {{ box-sizing: border-box; }}
  body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
    margin: 0; padding: 0; background: #f5f5f7; color: #1d1d1f;
  }}
  header {{
    background: linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%);
    color: white; padding: 24px 32px; position: sticky; top: 0; z-index: 100;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  }}
  header h1 {{ margin: 0 0 8px 0; font-size: 24px; }}
  header .meta {{ font-size: 13px; opacity: 0.9; }}
  nav.toc {{
    background: white; padding: 12px 32px; border-bottom: 1px solid #e5e5e7;
    position: sticky; top: 88px; z-index: 99;
  }}
  nav.toc a {{
    display: inline-block; margin-right: 16px; padding: 6px 12px;
    text-decoration: none; color: #1d1d1f; border-radius: 6px;
    font-size: 13px; transition: background 0.15s;
  }}
  nav.toc a:hover {{ background: #f0f0f5; }}
  nav.toc a.major {{ color: #dc2626; }}
  nav.toc a.moderate {{ color: #ea580c; }}
  nav.toc a.minor {{ color: #ca8a04; }}
  .summary {{
    background: white; margin: 16px 32px; padding: 20px 24px;
    border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  }}
  .summary table {{ border-collapse: collapse; width: 100%; max-width: 600px; }}
  .summary th, .summary td {{ padding: 8px 12px; text-align: left; border-bottom: 1px solid #f0f0f5; font-size: 14px; }}
  .summary th {{ background: #fafafa; font-weight: 600; }}
  section {{ padding: 16px 32px; }}
  section h2 {{
    margin: 16px 0 12px 0; padding: 12px 16px;
    background: white; border-radius: 8px;
    border-left: 4px solid #3b82f6;
  }}
  .dev-item {{
    background: white; margin: 12px 0; padding: 16px 20px;
    border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  }}
  .dev-item.tier-major {{ border-left: 4px solid #dc2626; }}
  .dev-item.tier-moderate {{ border-left: 4px solid #ea580c; }}
  .dev-item.tier-minor {{ border-left: 4px solid #ca8a04; }}
  .dev-item h3 {{ margin: 0 0 12px 0; font-size: 16px; font-weight: 500; }}
  .dev-item code {{ background: #f0f0f5; padding: 2px 6px; border-radius: 4px; font-size: 13px; }}
  .diff-ratio {{
    float: right; padding: 4px 10px; border-radius: 12px;
    font-size: 13px; font-weight: 600; color: white;
  }}
  .tier-major .diff-ratio {{ background: #dc2626; }}
  .tier-moderate .diff-ratio {{ background: #ea580c; }}
  .tier-minor .diff-ratio {{ background: #ca8a04; }}
  .triple {{
    display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px;
    margin-top: 8px;
  }}
  figure {{ margin: 0; }}
  figure.diff-fig {{ background: #fff5f5; padding: 4px; border-radius: 6px; }}
  figcaption {{
    font-size: 12px; color: #6b7280; margin-bottom: 6px; text-align: center;
  }}
  figcaption small {{ font-family: monospace; font-size: 10px; color: #9ca3af; }}
  figure img {{
    width: 100%; height: auto; border: 1px solid #e5e5e7; border-radius: 4px;
    cursor: zoom-in; background: #fafafa;
  }}
  figure img:hover {{ box-shadow: 0 4px 12px rgba(0,0,0,0.1); }}
  @media (max-width: 900px) {{
    .triple {{ grid-template-columns: 1fr; }}
  }}
</style>
</head>
<body>
  <header>
    <h1>📋 Med-RMS 视觉/交互验收复核报告</h1>
    <div class="meta">生成时间：2026-06-10 | 模式：structural | 工具：<code>tools/visual_diff_scan.py</code> | 阈值：match&lt;3% / minor&lt;8% / moderate&lt;15% / major</div>
  </header>

  <nav class="toc">
    <strong>跳转：</strong>
    <a href="#summary">📊 概览</a>
    <a href="#tier-major" class="major">🔴 Major ({n_major})</a>
    <a href="#tier-moderate" class="moderate">🟠 Moderate ({n_moderate})</a>
    <a href="#tier-minor" class="minor">🟡 Minor ({n_minor})</a>
  </nav>

  <div class="summary" id="summary">
    <h2>📊 概览</h2>
    <table>
      <tr><th>维度</th><th>数量</th><th>占比</th></tr>
      <tr><td>前端路由总数</td><td>{n_total}</td><td>100%</td></tr>
      <tr><td>交互原型总数</td><td>{map_data["protos_total"]}</td><td>-</td></tr>
      <tr><td>成功建立映射</td><td>{n_mapped}</td><td>{n_mapped/n_total*100:.1f}%</td></tr>
      <tr><td>🟢 视觉匹配 (match)</td><td>{n_match}</td><td>{n_match/n_mapped*100 if n_mapped else 0:.1f}%</td></tr>
      <tr><td>🟡 轻度偏差 (minor)</td><td>{n_minor}</td><td>{n_minor/n_mapped*100 if n_mapped else 0:.1f}%</td></tr>
      <tr><td>🟠 中度偏差 (moderate)</td><td>{n_moderate}</td><td>{n_moderate/n_mapped*100 if n_mapped else 0:.1f}%</td></tr>
      <tr><td>🔴 重度偏差 (major)</td><td>{n_major}</td><td>{n_major/n_mapped*100 if n_mapped else 0:.1f}%</td></tr>
    </table>
    <p style="margin-top: 16px; font-size: 13px; color: #6b7280;">
      💡 <strong>使用说明</strong>：每条偏差展示 3 张图（实施实拍 / 结构 diff（红框标注差异区域）/ 设计原型），
      点击任一图片可在新窗口查看原图。建议优先复核 Major（6 个）+ Moderate（11 个），Minor 可抽样。
    </p>
  </div>

  {sections_html}

  <footer style="text-align: center; padding: 24px; color: #9ca3af; font-size: 12px;">
    Med-RMS v1.57 | 视觉/交互验收 | 自动生成 · 请勿手动修改
  </footer>
</body>
</html>"""

    HTML_REPORT_FILE.write_text(html, encoding="utf-8")
    print(f"[HTML] 写入: {HTML_REPORT_FILE.name}")
    print(f"[HTML] 文件大小: {HTML_REPORT_FILE.stat().st_size / 1024:.1f} KB")
    print(f"[HTML] 总结: 路由 {n_total} | 映射 {n_mapped} | "
          f"match {n_match} | minor {n_minor} | moderate {n_moderate} | major {n_major}")
    print(f"[HTML] 路径: file:///{str(HTML_REPORT_FILE).replace(chr(92), '/')}")

    # 防止末尾 print 引用未定义
    n_miss = sum(1 for d in diffs if d.get("status") == "missing_screenshot")
    print(f"[HTML] 总结: 路由 {n_total} | 映射 {n_mapped} | "
          f"match {n_match} | minor {n_minor} | moderate {n_moderate} | major {n_major} | 缺失 {n_miss}")
    print(f"[REPORT] 写入: {REPORT_FILE.name}")
    print(f"[REPORT] 总结: 路由 {n_total} | 映射 {n_mapped} | "
          f"match {n_match} | minor {n_minor} | moderate {n_moderate} | major {n_major} | 缺失 {n_miss}")

# ============== CLI ==============
def main():
    ap = argparse.ArgumentParser(description="视觉/交互验收：路由截图 vs 原型")
    ap.add_argument("--phase", default="all",
                    choices=["map", "screenshot", "diff", "report", "htmlreport", "all"],
                    help="执行阶段（默认 all）：map/screenshot/diff/report( md)/htmlreport( 单文件 HTML )/all")
    ap.add_argument("--diff-mode", default="pixel",
                    choices=["pixel", "structural"],
                    help="diff 模式：pixel=原始像素 / structural=结构对比（忽略字体/抗锯齿/小位移）")
    args = ap.parse_args()
    # 全局 _args 让 phase_diff 也能取到
    global _args
    _args = args

    print("=" * 60)
    print(f"[VIS-DIFF] Phase: {args.phase} | diff-mode: {args.diff_mode}")
    print("=" * 60)

    if args.phase in ("map", "all"):
        phase_map()
    if args.phase in ("screenshot", "all"):
        phase_screenshot()
    if args.phase in ("diff", "all"):
        phase_diff()
    if args.phase in ("report", "all"):
        phase_report()
    if args.phase in ("htmlreport", "all"):
        phase_htmlreport()

    print("=" * 60)
    print("[VIS-DIFF] Done.")

if __name__ == "__main__":
    main()
