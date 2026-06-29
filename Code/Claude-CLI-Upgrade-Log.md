# Claude Code CLI 升级日志

> 用途：记录每次 Claude Code CLI 升级操作（版本前后、备份位置、命令、验证、回滚方法）
> 起始日期：2026-06-10
> 当前 CLI 版本：**2.1.170**（升级自 2.1.118）
> 维护人：Claude（自动登记）

---

## 升级记录表

| 序号 | 日期 | 升级前版本 | 升级后版本 | 备份目录 | 状态 | 备注 |
|------|------|-----------|-----------|---------|------|------|
| U01 | 2026-06-10 | 2.1.118 | 2.1.170 | `Code/backup/claude-cli/20260610_2.1.118/` | ✅ 成功 | 代理冲突 `--proxy=false` 解决；EPERM 警告（临时目录占用，不影响） |
| P01 | 2026-06-10 | - | - | - | ✅ 成功 | settings.json 插件调整：启用 +2（jdtls-lsp/pyright-lsp），禁用 -3（zapier/optibot/goodmem） |

---

## U01 升级详情（2026-06-10）

### 升级前环境

| 项 | 值 |
|---|---|
| 已安装版本 | `2.1.118` |
| 全局 npm 根 | `C:\Users\zhuta\AppData\Roaming\npm\node_modules` |
| claude 可执行文件 | `C:\Users\zhuta\AppData\Roaming\npm\claude` + `claude.cmd` |
| 用户配置目录 | `C:\Users\zhuta\.claude\`（含 settings.json、CLAUDE.md、memory、history 等） |
| npm registry | `https://registry.npmmirror.com/` |
| npm 代理 | `http://127.0.0.1:7897`（**已失效，连不上**） |
| 直连测试 | `registry.npmmirror.com` 200 / `registry.npmjs.org` 200（可达） |

### 升级命令

```bash
# 1. 标准升级（代理未失效时）
npm install -g @anthropic-ai/claude-code@latest

# 2. 本次实际使用（代理失效，临时禁用）
npm install -g @anthropic-ai/claude-code@latest --proxy=false --https-proxy=false
```

### 升级输出（关键行）

```
changed 2 packages in 47s

# 警告（可忽略，不影响升级）：
npm warn cleanup Failed to remove some directories [
npm warn cleanup   'C:\Users\zhuta\AppData\Roaming\npm\node_modules\@anthropic-ai\.claude-code-gzZR2C4V',
npm warn cleanup   [Error: EPERM: operation not permitted, unlink 'claude.exe']
]
```

**EPERM 根因**：npm 升级时会先把新包解压到 `.claude-code-{hash}` 临时目录，再原子替换旧的 `node_modules/@anthropic-ai/claude-code/`。临时目录里的 `claude.exe` 被 Windows Defender / 杀软锁住，npm 报 EPERM。**实际升级已成功**（47s 后提示 `changed 2 packages`），临时目录残留是垃圾，手动清：

```bash
# 清理临时残留（升级后可选执行）
rm -rf "C:\Users\zhuta\AppData\Roaming\npm\node_modules\@anthropic-ai\.claude-code-"*
```

### 升级后验证

| 验证项 | 命令 | 结果 |
|--------|------|------|
| 版本号 | `claude --version` | `2.1.170 (Claude Code)` ✅ |
| npm 仓库版本 | `npm view @anthropic-ai/claude-code version` | `2.1.170` ✅ 一致 |
| 健康检查 | `claude doctor` | 交互式命令（在 Claude Code 进程内跑会 stdio 冲突），需退出 CLI 后单独跑 |
| MCP 工具 | 启动新 session 验证 | 待用户在 GUI 内确认 |

---

## 备份详情

### 备份目录

```
D:\zhutao\MED_RMS_PMS\Code\backup\claude-cli\20260610_2.1.118\
├── claude-code-package\           # 476M — @anthropic-ai/claude-code 包（含 win32-x64 二进制）
├── claude                         # 1.0K — npm shim（Linux 风格）
├── claude.cmd                     # 1.0K — npm shim（Windows 批处理）
└── dot-claude-config\             # 835M — 用户配置 ~/.claude/ 完整目录
    ├── CLAUDE.md                  # 全局指令
    ├── settings.json              # 全局设置
    ├── settings.local.json
    ├── MEMORY.md                  # 自动记忆索引
    ├── user.md, project.md
    ├── MCP.json                   # MCP 服务器配置
    ├── projects\                  # 项目级 memory
    ├── plans\                     # 计划文件
    ├── plugins\                   # 插件
    ├── history.jsonl              # 会话历史
    ├── cache\, file-history\, paste-cache\, session-env\, sessions\, shell-snapshots\, tasks\  # 缓存与运行时
    └── backups\                   # settings 备份
```

**备份命令**：

```bash
BACKUP_DIR="D:/zhutao/MED_RMS_PMS/Code/backup/claude-cli/20260610_2.1.118"
mkdir -p "$BACKUP_DIR"

# 1. 包
cp -r "C:/Users/zhuta/AppData/Roaming/npm/node_modules/@anthropic-ai/claude-code" \
      "$BACKUP_DIR/claude-code-package"

# 2. shim
cp "C:/Users/zhuta/AppData/Roaming/npm/claude"     "$BACKUP_DIR/claude"
cp "C:/Users/zhuta/AppData/Roaming/npm/claude.cmd" "$BACKUP_DIR/claude.cmd"

# 3. 用户配置
cp -r "C:/Users/zhuta/.claude" "$BACKUP_DIR/dot-claude-config"
```

**总大小**：约 **1.3 GB**（包 476M + 配置 835M）

### 是否纳入 .gitignore

- `Code/backup/` 目录建议加入根 `.gitignore`（`Code/backup/`）
- 备份盘外另存一份更稳妥（U 盘/网盘）

---

## 回滚方法

### 方法 1：npm 官方回滚（推荐，最快）

```bash
# 回滚到升级前版本
npm install -g @anthropic-ai/claude-code@2.1.118 --proxy=false --https-proxy=false

# 验证
claude --version    # 应输出 2.1.118
```

### 方法 2：从备份还原（npm 装包失败/包损坏时）

```bash
BACKUP_DIR="D:/zhutao/MED_RMS_PMS/Code/backup/claude-cli/20260610_2.1.118"

# 1. 卸载当前版本（可选，强制清理）
npm uninstall -g @anthropic-ai/claude-code

# 2. 恢复包
cp -r "$BACKUP_DIR/claude-code-package" \
      "C:/Users/zhuta/AppData/Roaming/npm/node_modules/@anthropic-ai/claude-code"

# 3. 恢复 shim
cp "$BACKUP_DIR/claude"     "C:/Users/zhuta/AppData/Roaming/npm/claude"
cp "$BACKUP_DIR/claude.cmd" "C:/Users/zhuta/AppData/Roaming/npm/claude.cmd"

# 4. 恢复用户配置（谨慎，会覆盖现有 settings/memory）
#    如只想回滚 CLI 不动配置，跳过此步
cp -r "$BACKUP_DIR/dot-claude-config/"* "C:/Users/zhuta/.claude/"

# 5. 验证
claude --version
```

### 方法 3：完全清场（最彻底）

```bash
# 1. 卸载
npm uninstall -g @anthropic-ai/claude-code

# 2. 清理残留
rm -rf "C:/Users/zhuta/AppData/Roaming/npm/node_modules/@anthropic-ai/claude-code"
rm -rf "C:/Users/zhuta/AppData/Roaming/npm/node_modules/@anthropic-ai/.claude-code-"*
rm "C:/Users/zhuta/AppData/Roaming/npm/claude"
rm "C:/Users/zhuta/AppData/Roaming/npm/claude.cmd"

# 3. 装回旧版
npm install -g @anthropic-ai/claude-code@2.1.118 --proxy=false --https-proxy=false

# 4. 验证
claude --version
```

### 回滚后验证清单

- [ ] `claude --version` 输出 `2.1.118`
- [ ] 启动 Claude Code，登录账号正常
- [ ] `~/.claude/settings.json` 加载正确（无报错）
- [ ] `~/.claude/CLAUDE.md` 全局指令生效
- [ ] MCP 工具列表与升级前一致
- [ ] 现有项目 memory（`~/.claude/projects/`）未丢失

---

## 升级常见问题

| 现象 | 根因 | 解决 |
|------|------|------|
| `ECONNREFUSED 127.0.0.1:7897` | npm 配了死的代理 | `--proxy=false --https-proxy=false` |
| `EPERM: unlink 'claude.exe'` | 杀软/Defender 锁住临时目录 | 忽略（升级已成功），或加白名单 `C:\Users\zhuta\AppData\Roaming\npm\node_modules\@anthropic-ai\` |
| 升级后 `claude --version` 没变 | 新 PATH 缓存 | 重开终端或 `where claude` 确认路径 |
| `claude doctor` 卡住 | 交互式命令 | 退出当前 session 后单独跑 |
| npm 仓库 404 | registry 配错 | `npm config set registry https://registry.npmjs.org/` |

---

## 关联回滚节点

- `开发日志.md` 中 **R57** 节点（v1.57 Claude Code CLI 升级）
- 关联变更：U01（2.1.118 → 2.1.170）

---

## P01 插件调整（2026-06-10）

### 调整原因

升级到 2.1.170 后 settings.json 自动新增了 8 个插件，部分与项目栈（C/C#）相关（如 `csharp-lsp` `clangd-lsp`），部分无关（`zapier` `optibot` `goodmem`）。基于用户多语言栈（Java + C# + C/C++ + Python + TypeScript + Vue + PostgreSQL），重新筛选。

### 调整前 → 调整后

| 状态 | 插件 | 调整 |
|---|---|---|
| ➕ 启用 | `jdtls-lsp@claude-plugins-official` | 新增（Java LSP，Spring Boot 后端） |
| ➕ 启用 | `pyright-lsp@claude-plugins-official` | 新增（Python LSP，测试脚本） |
| ➖ 禁用 | `zapier@claude-plugins-official` | 禁用（无 Zapier 集成） |
| ➖ 禁用 | `optibot@claude-plugins-official` | 禁用（用途不明） |
| ➖ 禁用 | `goodmem@claude-plugins-official` | 禁用（与现有 MEMORY.md 体系功能重叠） |

### 候选但未启用（3 个，本地 marketplace 未同步）

| 插件 | 状态 | 原因 |
|---|---|---|
| `context7@claude-plugins-official` | ❌ 暂不启用 | 本地 `~/.claude/plugins/marketplaces/claude-plugins-official/plugins/` 目录里**没有 context7**（marketplace clone 滞后或不全） |
| `prisma@claude-plugins-official` | ❌ 暂不启用 | 本地未下载 |
| `microsoft-docs@claude-plugins-official` | ❌ 暂不启用 | 本地未下载 |

**根因排查**：
- `plugin-catalog-cache.json` 列出 222 个插件（来自 `https://github.com/anthropics/claude-plugins-official` 远端）
- 本地 `marketplaces/claude-plugins-official/plugins/` 只有 33 个（marketplace clone 不全）
- jdtls-lsp / pyright-lsp 恰好在本地 33 个里 → 启用成功
- context7 / prisma / microsoft-docs 不在本地 → 即使在 settings.json 写 `true`，下次 session 启动会因 cache 缺失而**加载失败**

**后续处理**（如需启用这 3 个）：
```bash
# 手动重 clone marketplace（下次 session 中执行）
# Claude Code 启动时会自动 fetch 缺失插件；或在 Claude Code 内执行：
/plugin install context7@claude-plugins-official
/plugin install prisma@claude-plugins-official
/plugin install microsoft-docs@claude-plugins-official
```

### 备份

- 调整前 `settings.json` 已备份至：`Code/backup/claude-cli/20260610_2.1.118/settings_pre_plugins.json`（2162 B）
- 当前 `settings.json` 大小：2158 B（微减 4 B，因 3 个 false 替换 3 个 true + 2 个新 true）

### 最终 enabledPlugins 状态

**启用 17 个**：
- 核心开发：`frontend-design` `code-review` `code-simplifier` `github` `pr-review-toolkit` `serena` `ralph-loop`
- LSP（多语言）：`typescript-lsp` `csharp-lsp` `clangd-lsp` `jdtls-lsp` `pyright-lsp`
- 浏览器/MCP：`chrome-devtools-mcp@chrome-devtools-plugins` `chrome-devtools-mcp@claude-plugins-official` `playwright`
- Claude Code 管理：`claude-md-management` `claude-code-setup`

**禁用 3 个**：`zapier` `optibot` `goodmem`

### 关联回滚节点
- `开发日志.md` 中 **R57** 节点（v1.57 Claude Code CLI 升级）
- 关联变更：U01（CLI 升级） + P01（插件调整）
