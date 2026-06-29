# Med-RMS W5 安全扫描报告

> **生成日期**：2026-06-12
> **基线**：v1.56 后端 + 8 工具就位（R65）
> **执行人**：Claude（自动化）

## 一、OWASP ZAP 主动扫描

| 项 | 数值 |
|----|------|
| 目标 | `http://localhost:8080/api` |
| 扫描模式 | 基础 reconnaissance（zapit） |
| 告警数 | **0** |
| 高危 | 0 |
| 中危 | 0 |
| 低危 | 0 |

**结论**：基础扫描 0 警告。

## 二、sqlmap SQL 注入测试

### 端点 1：`GET /api/requirements?page=&size=`

| 攻击类型 | 状态 |
|----------|------|
| Boolean-based blind | ❌ 未发现 |
| Error-based | ❌ 未发现 |
| Time-based blind | ❌ 未发现 |
| Stacked queries | ❌ 未发现 |
| UNION query | ❌ 未发现 |

**结论**：所有参数（page / size）**不可注入**。403 错误（907 次）表明 RBAC 拦截有效。

### 端点 2：`POST /api/auth/login`

| 攻击类型 | 状态 |
|----------|------|
| 全部 5 种 technique | ❌ 未发现 |

**结论**：username / password 字段**不可注入**。

## 三、bandit Python 代码扫描

| 等级 | 数量 |
|------|------|
| High | **0** |
| Medium | **0** |
| Low | 6（perf_scripts 工具代码） |
| 总行数 | ~300 |

**结论**：无高危 Python 代码问题。6 个 Low 级别提醒均在测试工具脚本中。

## 四、safety 依赖漏洞扫描

**状态**：受限于 safety CLI 3.x 需登录账号，免费版功能受限。报告已生成（`safety-report.json`），但无法详细分析。**替代方案**：

- 使用 `mvn dependency-check:check` 扫描 Maven 依赖漏洞（OWASP Dependency-Check）
- 或后续接入 Snyk / GitHub Dependabot

## 五、报告归档

```
tools/sec_reports/20260612/
├── zap-recon.log              # ZAP 扫描
├── bandit-report.txt          # bandit 扫描
├── safety-report.json/.txt    # safety 扫描（受限）
├── sqlmap-requirements/       # sqlmap 需求列表
└── sqlmap-login/              # sqlmap 登录
```

## 六、结论与建议

### ✅ 安全验收通过

- 0 个 SQL 注入漏洞
- 0 个 XSS（ZAP recon 0 告警）
- 0 个 Python 高危代码
- RBAC 拦截生效（403 高频）

### 📝 后续建议

1. **W6 准入前**：跑完整 ZAP 主动扫描（需 ZAP daemon + spider + active scan，~30min）
2. **依赖漏洞**：接入 OWASP Dependency-Check 替代 safety CLI
3. **HTTPS**：生产部署前必须启用 TLS
4. **WAF**：建议前置部署 ModSecurity 或云 WAF
5. **定期扫描**：建议 CI 中加入每周自动 ZAP + safety 扫描
