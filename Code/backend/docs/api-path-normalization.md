# API 路径规范化方案 — R119 中期方案

> **创建日期**: 2026-06-30
> **创建节点**: R119
> **状态**: 文档化（不建议立即执行，避免破坏前端）

## 背景

R114 测试发现后端 API 路径存在不一致问题：
- SOUP 组件端点使用 `/requirement/soup-components` 而非符合模块前缀的 `/soup`
- 基线列表端点路径为 `/baselines/project/{id}`（实际正确）
- 部分路径前缀不一致（`/admin/users/{id}` vs `/system/users/{id}`）

## 当前路径清单（与规范偏差）

| # | 当前路径 | 规范路径 | 差异原因 | 前端调用方 |
|---|---------|---------|---------|-----------|
| 1 | `/requirement/soup-components` | `/soup` | 模块前缀冗余 | `views/compliance/Soup*.vue` 等 5 处 |
| 2 | `/admin/users/{id}` vs `/system/users/{id}` | 统一 `/system/users/{id}` | 历史遗留 | 混合调用 |
| 3 | `/compliance/baselines`（不存在） | `/baselines/project/{id}` | 测试误报（已撤销） | `views/compliance/Baselines.vue` 实际正确 |

## 规范化建议

### 方案 A：保持现状 + 文档化（推荐）

**理由**：
- R114 测试已确认前端所有调用路径正确
- 路径变更涉及前端 + 后端同步改造，工作量大
- 历史路径在生产环境已被大量前端代码依赖

**行动**：
- 在 `PermissionMatrix.java` 和 `Swagger` 中添加路径说明
- 新增端点统一规范（避免继续累积）

### 方案 B：完整重构（不推荐）

**理由**：
- 风险高：所有前端 API 调用 + 后端 Controller + 测试脚本 + CI/CD 都要改
- 收益低：仅美观性提升，无功能改进
- 中文字段兼容性：`/requirement/soup-components` 已存在大量旧代码

**行动**（如果必须执行）：
1. 创建兼容层：新路径 Controller 调用旧路径 Service
2. 前端分批迁移到新路径
3. 保留旧路径 1-2 个版本作为 deprecation
4. CI 测试覆盖新旧路径

## 决定

**采用方案 A**：保持现状 + 文档化 + 新增端点规范化。

## 行动项

- [ ] 在 `PermissionMatrix.java` 添加路径说明注释
- [ ] 更新 `测试报告/00-汇总/README.md` 标记 P2-04 为「文档化接受」
- [ ] 新增端点审查：所有 R118+ 新增端点路径必须符合规范
- [ ] Swagger 文档补充（OpenAPI v3 已支持）

## 后续

如果未来要重构，建议：
1. 独立 R 节点（如 R120+）
2. 使用 API Gateway 统一路径
3. 前端 axios interceptor 适配路径迁移