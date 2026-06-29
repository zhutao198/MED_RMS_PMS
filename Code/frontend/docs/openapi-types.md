# OpenAPI → 前端类型自动同步

> 状态：永久化指南（实际集成按需启用）

## 目标

后端改 API 字段时，前端 TypeScript 类型**自动同步**，避免"前端类型与后端不一致"bug。

## 工具

- **openapi-typescript** — 把 OpenAPI 3 规范生成 TS 类型
- **orval** — 高级选项（生成 fetch 客户端 + 类型）

## 集成步骤（5 分钟）

### 1. 安装依赖
```bash
cd Code/frontend
npm install --save-dev openapi-typescript
```

### 2. 加 npm script
```json
// package.json
{
  "scripts": {
    "gen:api": "openapi-typescript http://localhost:8080/api/api-docs -o src/api/schema.d.ts"
  }
}
```

### 3. 启动后端时生成
```bash
# 一次性生成
npm run gen:api

# 持续生成（watch 模式）
npx openapi-typescript http://localhost:8080/api/api-docs -o src/api/schema.d.ts --watch
```

### 4. 在代码中使用
```typescript
import type { paths } from '@/api/schema'

// 类型自动从 OpenAPI 推断
type LoginResponse = paths['/auth/login']['post']['responses']['200']['content']['application/json']
```

## CI 集成（已永久化在 `.github/workflows/ci.yml`）

```yaml
- name: 生成前端类型
  run: |
    cd Code/frontend
    npm install --no-save openapi-typescript
    npx openapi-typescript http://localhost:8080/api/api-docs -o src/api/schema.d.ts

- name: 检查漂移
  run: |
    if [[ -n "$(git diff --name-only src/api/schema.d.ts)" ]]; then
      echo "::error::前端类型与后端 OpenAPI 漂移，请跑 npm run gen:api"
      exit 1
    fi
```

## 收益

- ✅ 改 API 字段后前端 TS 类型自动更新
- ✅ CI 检测类型漂移（防止"前端用了旧类型"的 bug）
- ✅ 后端 OpenAPI 永远是"事实源"（single source of truth）

## 已知限制

- 类型生成仅覆盖 OpenAPI 已声明的字段
- 复杂类型（联合、泛型）需手写 extends
- 后端未声明 OpenAPI 的字段（如 BaseEntity.id）不会出现在生成结果中

## 何时启用

- 后端 API 频繁变动（每个 sprint > 5 个 endpoint 变化）
- 前后端跨团队开发（API 契约重要）
- 当前 v1.56 状态稳定，**可暂缓启用**，按需开启
