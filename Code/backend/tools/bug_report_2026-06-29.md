# Med-RMS 深度 Bug 扫描报告 v3.1

**扫描时间**: 2026-06-29 16:00  
**扫描范围**: 全量后端 API (40+ 端点)  
**扫描方法**: 实际 HTTP 调用 + 验证响应体业务码 + 检查数据完整性 + 对比 Smoke Test 结果

---

## 一句话结论

> **Smoke Test 是"假阳性"陷阱：HTTP 200 ≠ 后端正常。**  
> 真正的 P0 bug 只有 **1 个**：审计日志哈希链未实现（21 CFR Part 11 合规红线）。

---

## P0 — 必须立即修复（法规合规红线）

### B-01: 审计日志哈希链未实现 🔴 21 CFR Part 11

| 维度 | 说明 |
|------|------|
| 影响范围 | 全部 1481 条审计日志 |
| 问题描述 | 所有 `currentHash` 和 `prevHash` 字段为空字符串（len=0） |
| 法规影响 | **21 CFR Part 11 11.10(e)** 不可篡改审计追踪。哈希链缺失 = 日志可被修改而不被检测 |
| 修复方向 | AuditLogService 写入时计算 SHA256(内容+前一条哈希)，追加 prevHash |

```sql
-- 诊断 SQL
SELECT id, prev_hash, current_hash, LENGTH(current_hash) as hash_len
FROM compliance_schema.t_audit_log ORDER BY id DESC LIMIT 10;
-- 预期: hash_len=64  实际: hash_len=0
```

---

## P1 — 重要，建议本周修复

### B-02: 数据库字符编码问题（中文乱码）

| 影响 | 根因 | 修复 |
|------|------|------|
| 通知/问题报告中文乱码 | PostgreSQL 为 SQL_ASCII，应用层按 UTF8 解析 | 迁移到 UTF8 编码 |

### B-03: 追溯矩阵 API 缺少 coverage 汇总字段

| 问题 | 修复 |
|------|------|
| `/traceability/matrix` 只返回行数据，无顶层 coverage 字段 | 在 MatrixResponse 追加 coverage 汇总（`/traceability/coverage` 已正常） |

### B-04: 报表生成 reportType 值不匹配

| 问题 | 修复 |
|------|------|
| 前端传 `REQUIREMENT_TRACEABILITY` 但系统只支持 `req` | 扩展枚举或前端对齐 |

---

## P2 — 建议近期优化

| Bug | 说明 |
|-----|------|
| B-05: 变更列表缺少 `total` 分页字段 | 返回结构应改为 `{records:[], total:103}` |
| B-06: SOUP/基线 API 路由不规范 | `/requirement/soup-components` 应规范化为 `/soup` |

---

## 已验证正常的核心功能

| 模块 | 端点数 | 状态 |
|------|--------|------|
| 需求管理 | ~20 | ✅ 753 条数据，所有字段完整 |
| 追溯管理 | 5 | ✅ matrix + gaps + coverage 全通 |
| 变更管理 | 12 | ✅ 103 条变更请求 |
| 合规管理 | 10+ | ✅ DHF + SOUP(55条) + 问题报告(49条) + 审计日志 |
| 项目管理 | 9 | ✅ 项目 + 成员 + 里程碑 + IPD门控 |
| 报表仪表盘 | 8 | ✅ 75 报表 + Dashboard |
| 通知 | 6 | ✅ 32 条未读 |

---

## Smoke Test 根本问题

| 问题 | 修复 |
|------|------|
| `assert r.status_code == 200` 未检查响应体 code | 改为 `assert r.json()['code'] == 200` |
| 未验证哈希字段 | 检查 `LENGTH(hash) == 64` |
| 未检查中文编码 | 验证返回中文非乱码 |
