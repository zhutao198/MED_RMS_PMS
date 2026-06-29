# Med-RMS 监控接入指南

> 状态：永久化指南（实际接入按需启用）

## 推荐监控栈

| 层 | 工具 | 端点 |
|----|------|------|
| 健康 | Spring Boot Actuator | `/actuator/health`（已启用 W22）|
| 指标 | Prometheus + Micrometer | `/actuator/prometheus`（需加依赖）|
| 面板 | Grafana | dashboard JSON |
| 错误聚合 | Sentry | 前端 + 后端 |
| 日志聚合 | Loki / ELK | /var/log/medrms-backend.log |
| 告警 | Alertmanager | 邮件 / 钉钉 / 飞书 / Slack |

## 1. Prometheus 接入

### 加依赖
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### application.yml
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  metrics:
    tags:
      application: med-rms
```

启动后访问 `GET /actuator/prometheus` 返回 Prometheus 格式指标。

## 2. 关键指标

| 指标 | 含义 | 告警阈值 |
|------|------|----------|
| `jvm_memory_used_bytes` | JVM 内存 | > 80% heap |
| `jvm_threads_live` | 线程数 | > 500 |
| `http_server_requests_seconds_count` | 请求总数 | - |
| `http_server_requests_seconds{uri="/api/auth/login",status="500"}` | 5xx 错误 | > 1% |
| `hikaricp_connections_active` | DB 连接池活跃 | > 80% pool |
| `hikaricp_connections_pending` | 等待连接 | > 0（>5s 持续）|
| `redis_commands_total` | Redis 命令 | - |
| `process_cpu_usage` | CPU 使用 | > 80% |

## 3. Grafana Dashboard

导入下面这个 dashboard JSON 到 Grafana：

```json
{
  "title": "Med-RMS Overview",
  "panels": [
    {
      "title": "QPS (last 5m)",
      "type": "graph",
      "targets": [{
        "expr": "sum(rate(http_server_requests_seconds_count[5m]))"
      }]
    },
    {
      "title": "P99 Latency (s)",
      "type": "graph",
      "targets": [{
        "expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))"
      }]
    },
    {
      "title": "Error Rate (5xx / total)",
      "type": "graph",
      "targets": [{
        "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))"
      }]
    },
    {
      "title": "JVM Heap",
      "type": "graph",
      "targets": [{
        "expr": "jvm_memory_used_bytes{area=\"heap\"} / jvm_memory_max_bytes{area=\"heap\"}"
      }]
    },
    {
      "title": "DB Connection Pool",
      "type": "graph",
      "targets": [{
        "expr": "hikaricp_connections_active"
      }]
    },
    {
      "title": "Health",
      "type": "stat",
      "targets": [{
        "expr": "up{job=\"med-rms-backend\"}"
      }]
    }
  ]
}
```

## 4. Sentry 接入

### 后端
```xml
<dependency>
    <groupId>io.sentry</groupId>
    <artifactId>sentry-spring-boot-starter</artifactId>
    <version>7.0.0</version>
</dependency>
```

```yaml
sentry:
  dsn: ${SENTRY_DSN}
  environment: production
  traces-sample-rate: 0.1
```

### 前端
```bash
npm install @sentry/vue
```

```typescript
import * as Sentry from '@sentry/vue'
Sentry.init({
  app,
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.MODE,
  tracesSampleRate: 0.1
})
```

## 5. 告警规则

```yaml
# alertmanager/rules/med-rms.yml
groups:
  - name: med-rms
    rules:
      - alert: BackendDown
        expr: up{job="med-rms-backend"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Med-RMS 后端宕机"

      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count[5m]))
          > 0.01
        for: 5m
        labels:
          severity: warning

      - alert: HighLatency
        expr: |
          histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
          > 1
        for: 10m
        labels:
          severity: warning

      - alert: DBPoolExhausted
        expr: hikaricp_connections_pending > 0
        for: 30s
        labels:
          severity: critical
```

## 6. 何时启用

| 场景 | 推荐 |
|------|------|
| 生产部署（公网） | 必装 Prometheus + Sentry |
| 生产部署（内网） | 装 Prometheus 可选 |
| 沙箱/演示 | 跳过 |
| CI | 跳过（CI 自己有监控）|

## 当前状态

- ✅ Spring Boot Actuator 健康端点（已配置 W22）
- ⚠️ Prometheus 接入指南（待实际集成）
- ⚠️ Sentry 接入指南（待实际集成）
- ⚠️ Grafana dashboard JSON（已提供模板）
