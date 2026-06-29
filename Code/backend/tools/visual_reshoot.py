#!/usr/bin/env python3
"""补抓超时路由（更宽限的 timeout）"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright
sys.path.insert(0, str(Path(__file__).parent))
from visual_diff_scan import (
    FRONTEND, OUT_DIR, VIEWPORT, ADMIN_USER, ADMIN_PWD,
    login, safe_name, resolve_id
)

routes_retry = ['/changes', '/testcases', '/requirement-tasks']

with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True)
    ctx = browser.new_context(viewport=VIEWPORT)
    page = ctx.new_page()
    if login(page, ctx):
        print("[RESHOOT] 登录成功")
    for r in routes_retry:
        try:
            print(f"[RESHOOT] 抓 {r} (timeout=30s)")
            page.goto(f"{FRONTEND}{r}", wait_until="domcontentloaded", timeout=30000)
            page.wait_for_load_state("networkidle", timeout=20000)
            page.wait_for_timeout(2000)
            out = OUT_DIR / "routes" / f"{safe_name(resolve_id(r))}.png"
            page.screenshot(path=str(out), full_page=False)
            print(f"  → {out}")
        except Exception as e:
            print(f"  [FAIL] {r}: {str(e)[:120]}")
    browser.close()
