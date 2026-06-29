#!/usr/bin/env python3
"""E2E 验证 FR-2.7 任务依赖持久化：实测 GanttView 的依赖保存/读取流程"""
import sys
from pathlib import Path
from playwright.sync_api import sync_playwright
import requests

ROOT = Path(r"D:/zhutao/MED_RMS_PMS")
FRONTEND = "http://localhost:5173"
BACKEND = "http://localhost:8080"
sys.path.insert(0, str(ROOT / "Code/backend/tools"))

def login_get_token():
    res = requests.post(f"{BACKEND}/api/auth/login",
                        json={"username": "admin", "password": "admin123"})
    return res.json()["data"]["token"]

def main():
    token = login_get_token()
    headers = {"Authorization": f"Bearer {token}"}

    # 找一个有任务的项目（项目 1 心电监护仪 v3.0）
    res = requests.get(f"{BACKEND}/api/gantt/tasks/project/1", headers=headers)
    print(f"[GET /gantt/tasks/project/1] status={res.status_code}")
    if res.status_code != 200:
        print(f"[FAIL] 拉取任务失败: {res.text[:200]}")
        return
    data = res.json().get("data", [])
    if isinstance(data, dict):
        tasks = data.get("tasks", [])
    else:
        tasks = data if isinstance(data, list) else []
    print(f"[OK] 项目 1 任务数: {len(tasks)}")
    if len(tasks) < 2:
        print("[SKIP] 任务数 < 2，跳过依赖测试")
        return

    # 加载项目依赖图（注意：graph 键是字符串，值是"后继任务"列表，即 graph[taskId] = [successorIds]）
    res = requests.get(f"{BACKEND}/api/gantt/dependencies/project/1", headers=headers)
    graph = res.json().get("data", {}) or {}
    graph_count = sum(1 for v in graph.values() if v)
    print(f"[GRAPH] 项目依赖图: {graph_count} 个任务有后继")

    # 找一对无循环依赖的任务对：BFS 从 task_a 出发，看 task_b 是否可达
    # 若可达（A → ... → B），则设置 A 为 B 的前置会形成环
    # 不可达，则 A 是 B 的新前置，安全
    task_a = tasks[0]
    task_b = None
    for t in tasks[1:30]:
        if t['id'] == task_a['id']:
            continue
        visited = {task_a['id']}
        stack = [task_a['id']]
        reaches_t = False
        while stack:
            cur = stack.pop()
            for succ in graph.get(str(cur), []) or []:
                if succ == t['id']:
                    reaches_t = True
                    break
                if succ not in visited:
                    visited.add(succ)
                    stack.append(succ)
            if reaches_t:
                break
        if not reaches_t:
            task_b = t
            break
    if not task_b:
        print("[SKIP] 找不到无循环依赖任务对")
        return
    if not task_b:
        print("[SKIP] 找不到无循环依赖任务对（任务图已较密）")
        return
    print(f"[INFO] 任务 A: id={task_a['id']}, taskNo={task_a.get('taskNo')}")
    print(f"[INFO] 任务 B: id={task_b['id']}, taskNo={task_b.get('taskNo')}")

    # 设置 B 依赖 A
    res = requests.put(f"{BACKEND}/api/gantt/tasks/{task_b['id']}/predecessors",
                       json=[task_a['id']], headers=headers)
    print(f"[SET] PUT 前置: status={res.status_code}, body={res.json()}")
    if res.status_code != 200:
        return

    # 读取 B 的前置任务
    res = requests.get(f"{BACKEND}/api/gantt/tasks/{task_b['id']}/predecessors", headers=headers)
    deps = res.json().get("data", [])
    print(f"[GET] 读取前置: {deps}")
    if task_a['id'] in deps:
        print(f"[✅ PASS] FR-2.7 任务依赖持久化 OK：任务 B ({task_b['id']}) 依赖任务 A ({task_a['id']}) 已保存到后端")
    else:
        print(f"[❌ FAIL] 任务 B 前置列表不含任务 A")

    # 加载项目依赖图
    res = requests.get(f"{BACKEND}/api/gantt/dependencies/project/1", headers=headers)
    graph = res.json().get("data", {}) or {}
    graph_count = sum(1 for v in graph.values() if v)
    print(f"[GRAPH] 项目依赖图: {graph_count} 个任务有后继")
    if task_b['id'] in (graph.get(str(task_a['id'])) or []):
        print(f"[PASS] 依赖图正确反映 A -> B 关系（task_a={task_a['id']} -> task_b={task_b['id']}）")

    # 验证持久化（重新拉）
    res = requests.get(f"{BACKEND}/api/gantt/tasks/{task_b['id']}/predecessors", headers=headers)
    deps = res.json().get("data", [])
    if task_a['id'] in deps:
        print(f"[✅ PASS] 持久化验证：再次拉取前置仍包含 A")

    # 清理：清空 B 的前置
    res = requests.put(f"{BACKEND}/api/gantt/tasks/{task_b['id']}/predecessors",
                       json=[], headers=headers)
    print(f"[CLEAN] 清空前置: {res.status_code}")

if __name__ == "__main__":
    main()
