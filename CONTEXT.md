# Med-RMS 项目上下文指南（新会话快速恢复）

> **用途**: 新会话开场引用此文件，5 分钟内恢复到完整上下文
> **更新**: 每次 R 节点完成时更新此文件
> **最后更新**: 2026-07-02（R148）

---

## 🚀 30 秒恢复（最小启动集）

```bash
cd "D:/zhutao/MED_RMS_PMS"
git log --oneline -5                                    # 最新 commit
git tag -l "R1*" | tail -10                            # 最新 tag
netstat -ano | grep ":808.*LISTENING" | head -3        # 后端实例
curl -s -o /dev/null -w "HTTP %{http_code}\n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
```

## 📊 当前状态（2026-07-02 R148 后）

| 维度 | 值 |
|------|-----|
| **主分支** | `2633086` (R148-doc) |
| **HEAD commit** | R148-doc: 补充R148开发日志 |
| **最新 R 节点** | R148（OTP bug 修复） |
| **后端端口** | 8080（PID 48460，R143+R146+R148 修复） |
| **8081 验证** | PID 16148（可用） |
| **GitHub tag 数** | 40+ R tag 全部推送 |
| **数据库** | UTF-8 + 21 CFR Part 11 哈希链 |
| **RBAC** | 9 角色 × 63 权限 × 242 关联 |
| **测试** | 4 e2e 脚本 + 263 行 RBAC 矩阵 |
| **CI/CD** | R117 e2e ✅ + R129 cd-deploy ✅ |

## 📁 关键文件速查

| 文件 | 内容 | 行数 |
|------|------|------|
| `开发日志.md` | 45 个 R 节点完整记录 | 17000+ |
| `测试报告/00-汇总/README.md` | 全模块测试报告 + P0/P1 缺陷 | v2.0 |
| `Detailed/04-权限设计/RBAC矩阵.md` | 9 角色 × 63 权限完整矩阵 | 263 |
| `架构-实现偏差与文档同步/架构-实现偏差清单.md` | 设计 vs 实现偏差 | - |
| `架构-实现偏差与文档同步/DDL变更日志.md` | 44 个 DDL 文件登记 | - |
| `SESSION_SUMMARY.md` | 本次会话关键决策和教训 | - |
| `.claude/projects/.../memory/MEMORY.md` | 持久化记忆（项目级） | - |
| `tools/restart_8080.ps1` | 8080 重启脚本（UAC 触发） | 156 |
| `tools/test_runner/` | 8 个 e2e 测试脚本 | - |
| `.github/workflows/e2e-tests.yml` | R117 CI workflow | 126 |
| `.github/workflows/cd-deploy.yml` | R129 CD workflow | 154 |

## 🏷️ R 节点全景（45 个 commit）

```
R110 (历史) → R111 → ... → R143 (性能+bug) → R144 (清理) → R145 (mock)
                                                              ↓
                                              R146 (端点补强) → R147 (P2 性能)
                                                                 ↓
                                                              R148 (OTP)
                                                              [HEAD] = 2633086
```

**关键节点**：
- **R111-R117**: 偏差清单 + 状态机 18 态 + 前后端测试 + P0/P1 修复
- **R118-R120**: tree 性能 11.8 倍 + viewer 权限 + 4 项 P2 修复
- **R122**: GitHub push + 23 tag
- **R123**: 状态机 e2e 9/9 = 100%
- **R125**: PD 角色 0→21 修复
- **R126**: 端点补强（trace-count/test-case-count）
- **R127**: 8080 部署脚本
- **R133-R142**: CI/CD 修复（9 迭代）
- **R143**: 性能 14.2 倍 + 里程碑修复
- **R148**: OTP bug 修复

## 🎯 用户偏好（CLAUDE.md 已记录）

- 全程简体中文
- Rxx 节点规范（每次修改前建节点框架 + commit + tag）
- 报告不立即修复，记录后统一评估
- 模块化测试（test_runner/ 工具集）
- 决策前主动询问确认

## 🛠️ 服务信息

| 端口 | 进程 | 状态 |
|------|------|------|
| 8080 | PID 动态 | 后端主实例（用户环境，需 admin 权限重启）|
| 8081 | 16148 | 后端验证实例 |
| 5173 | - | 前端 Vite（用户浏览器访问） |
| 5432 | postgres | PostgreSQL 16 |
| 6379 | redis | Redis 7 |

## 🐛 已知未解决

| # | 问题 | 状态 |
|---|------|------|
| 后端 8080 重启需 admin | 用户操作 | R127 脚本 |
| 字符编码 SQL_ASCII | 已验证 DB 实际是 UTF-8 | R121 |
| API 路径不一致 | 文档化（保持现状）| R119 |
| 8080 实例有 4 个 java 进程 | 资源竞争 | 性能正常 |
| GitHub Actions 默认 secrets 缺失 | CD 仅 build 不部署 | 需配置 SSH |

## 🔧 用户实际操作模式

- 用其他工具（IDEA/VSCode/Postman）直接修改文件后让我检查
- 浏览器手动验证页面功能
- 关注实际运行问题（不只看代码）
- 期望完整工作流：诊断 → 修复 → 验证 → commit → push

## 📞 紧急恢复命令

```bash
# 8080 跑新代码
cd D:\zhutao\MED_RMS_PMS
powershell -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','D:\zhutao\MED_RMS_PMS\tools\restart_8080.ps1'"

# 手动 build + 启动
cd D:\zhutao\MED_RMS_PMS\Code\backend
mvn -B -DskipTests -Dspring-boot.repackage.skip=true install
cd med-rms-web
mvn -B -q -DskipTests spring-boot:run -Dspring-boot.run.arguments="--server.port=8080"

# 看 log
tail -f C:/temp/medrms-8080-r133-*.log | tail -100
```

## 📚 关联文档

- [开发日志.md](开发日志.md) - 45 个 R 节点详细记录
- [SESSION_SUMMARY.md](SESSION_SUMMARY.md) - 本次会话关键决策和教训
- [.claude/projects/.../memory/MEMORY.md](.claude/projects/.../memory/MEMORY.md) - 项目级持久化记忆
- [测试报告/00-汇总/README.md](测试报告/00-汇总/README.md) - 全模块测试报告
- [Detailed/04-权限设计/RBAC矩阵.md](Detailed/04-权限设计/RBAC矩阵.md) - 完整 RBAC 矩阵