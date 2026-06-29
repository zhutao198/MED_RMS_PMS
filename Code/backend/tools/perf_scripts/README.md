# Med-RMS 性能压测报告模板（W5）

> 本目录用于存放 W5 阶段的性能压测执行结果。

## 报告命名规范

`perf_reports/<YYYYMMDD>/<scenario>-<HHMMSS>.{log,json,html}`

## 已就绪脚本

| 脚本 | 目标 | 工具 | 阈值 |
|------|------|------|------|
| `perf_scripts/baseline-requirements-list.js` | 需求列表 5000 条 P95 ≤ 3s | k6 | p(95)<3000, errors<1% |
| `perf_scripts/baseline-traceability-matrix.js` | 追溯矩阵 P95 ≤ 5s | k6 | p(95)<5000, errors<1% |
| `perf_scripts/baseline-impact-assessment.js` | 变更影响评估 P95 ≤ 10s | k6 | p(95)<10000, errors<1% |

## 执行方式

```cmd
REM 一键执行 3 个 k6 性能基线
tools\perf_scripts\run-perf.bat

REM 单个场景
tools\perf_scripts\run-perf.bat list
tools\perf_scripts\run-perf.bat matrix
tools\perf_scripts\run-perf.bat impact
```

## 报告检查清单

- [ ] k6 JSON 输出完整
- [ ] P95 / P99 满足阈值
- [ ] 错误率 < 1%
- [ ] 吞吐量（req/s）满足业务要求
- [ ] 系统资源（CPU / 内存）在健康范围

## 真实环境执行

需满足：
1. 后端服务在 `localhost:8080` 运行（mvn spring-boot:run -pl med-rms-web）
2. PostgreSQL 在 `localhost:5432` 运行（med_rms_pms 数据库）
3. 测试数据：约 5000 需求 + 追溯关系

```bash
# 1. 启动后端
cd Code/backend
mvn spring-boot:run -pl med-rms-web -Dspring-boot.run.profiles=dev

# 2. 另开终端，跑压测
cd Code/backend
tools\perf_scripts\run-perf.bat
```
