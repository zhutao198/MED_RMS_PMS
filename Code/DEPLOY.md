# Med-RMS 部署文档

> 版本：v1.0 | 适用：Med-RMS v1.56 + 12 周冲刺交付

## 1. 系统要求

| 组件 | 最低 | 推荐 |
|------|------|------|
| CPU | 4 核 | 8 核 |
| 内存 | 8 GB | 16 GB |
| 磁盘 | 50 GB SSD | 200 GB SSD |
| OS | Linux x86_64（Ubuntu 22.04 / CentOS 8+）| Ubuntu 24.04 LTS |
| JDK | OpenJDK 17 | OpenJDK 21 LTS |
| PostgreSQL | 14 | 16 |
| Redis | 6 | 7 |
| Node.js | 18 | 20 LTS |

## 2. 部署架构

```
                    ┌─────────────┐
                    │   Nginx     │  :80/:443 (TLS)
                    │   反向代理  │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │                         │
        ┌─────▼─────┐            ┌───────▼──────┐
        │ Frontend  │            │   Backend    │
        │ (Vue 3    │            │ (Spring Boot │
        │  静态文件)│            │  12 模块)    │
        │  :5173    │            │    :8080     │
        └───────────┘            └───────┬──────┘
                                       │
                          ┌────────────┴────────────┐
                          │                         │
                   ┌──────▼──────┐          ┌───────▼──────┐
                   │ PostgreSQL  │          │    Redis     │
                   │  :5432      │          │    :6379     │
                   └─────────────┘          └──────────────┘
```

## 3. 数据库准备

### 3.1 安装 PostgreSQL 16
```bash
# Ubuntu
sudo apt install postgresql-16

# 启动
sudo systemctl enable postgresql
sudo systemctl start postgresql
```

### 3.2 创建数据库 + 用户
```bash
sudo -u postgres psql <<'EOF'
CREATE USER medrms WITH PASSWORD 'change-me-in-prod';
CREATE DATABASE med_rms_pms OWNER medrms;
GRANT ALL PRIVILEGES ON DATABASE med_rms_pms TO medrms;
EOF
```

### 3.3 执行 DDL（11 个 schema）
```bash
PGPASSWORD=change-me-in-prod psql -h localhost -U medrms -d med_rms_pms \
    -f Code/backend/med-rms-web/src/main/resources/02-DDL/med_rms_ddl.sql
```

### 3.4 导入示例数据（可选，沙箱就绪）
```bash
# 5 大演示剧本（10 分钟）
psql -h localhost -U medrms -d med_rms_pms -f tools/demo-sandbox/01-init.sql
```

## 4. Redis 准备

```bash
sudo apt install redis-server
sudo systemctl enable redis
sudo systemctl start redis
redis-cli ping  # 应返 PONG
```

## 5. 后端部署

### 5.1 构建
```bash
cd Code/backend
mvn clean package -DskipTests
# 产物：Code/backend/med-rms-web/target/med-rms-web-1.0.0-SNAPSHOT.jar
```

### 5.2 配置（生产 application-prod.yml）
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/med_rms_pms
    username: medrms
    password: ${DB_PASSWORD}
  redis:
    password: ${REDIS_PASSWORD}

server:
  port: 8080
  servlet:
    context-path: /api
```

### 5.3 systemd 服务
```ini
# /etc/systemd/system/medrms-backend.service
[Unit]
Description=Med-RMS Backend
After=postgresql.service redis-server.service

[Service]
Type=simple
User=medrms
WorkingDirectory=/opt/medrms
Environment=DB_PASSWORD=change-me
Environment=REDIS_PASSWORD=change-me
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar /opt/medrms/med-rms-web-1.0.0-SNAPSHOT.jar \
    --spring.profiles.active=prod
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable medrms-backend
sudo systemctl start medrms-backend
sudo systemctl status medrms-backend
```

## 6. 前端部署

### 6.1 构建
```bash
cd Code/frontend
npm install
npm run build
# 产物：Code/frontend/dist/
```

### 6.2 Nginx 配置
```nginx
# /etc/nginx/sites-available/medrms
server {
    listen 80;
    server_name medrms.example.com;

    # 前端静态文件
    location / {
        root /var/www/medrms;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反代
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 100M;  # 附件上传
    }

    # OpenAPI 文档
    location /api/api-docs {
        proxy_pass http://localhost:8080;
    }
    location /api/swagger-ui/ {
        proxy_pass http://localhost:8080;
    }
}
```

```bash
sudo cp -r Code/frontend/dist/* /var/www/medrms/
sudo ln -s /etc/nginx/sites-available/medrms /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

## 7. 健康验证

```bash
# 后端健康
curl http://localhost:8080/actuator/health
# {"status":"UP",...}

# 前端访问
curl -I http://localhost/
# HTTP/1.1 200 OK

# 登录测试
# 浏览器打开 http://medrms.example.com/login
# admin / admin123 登录
```

## 8. 永久化运维

### 8.1 DB 自动备份（已永久化）
```bash
# 复制 ops/backup.sh 到生产
sudo cp Code/backend/ops/backup.sh /opt/medrms/ops/
sudo chmod +x /opt/medrms/ops/backup.sh

# crontab：每天 2:00 备份
echo "0 2 * * * medrms /opt/medrms/ops/backup.sh >> /var/log/medrms-backup.log 2>&1" \
    | sudo crontab -u medrms -
```

### 8.2 健康检查（已永久化）
```bash
# 复制 ops/health_check.sh 到生产
sudo cp Code/backend/ops/health_check.sh /opt/medrms/ops/

# supervisor 或 nohup 后台跑
nohup /opt/medrms/ops/health_check.sh >> /var/log/medrms-health.log 2>&1 &
```

### 8.3 CI 流水线（已永久化）
- 入 GitHub Actions: `.github/workflows/ci.yml`（见 ops/ci.sh 等价配置）
- 每次 push 跑：单测 → 集成 → DB 备份 → k6 冒烟

## 9. 监控（推荐）

| 工具 | 端点 | 用途 |
|------|------|------|
| Spring Boot Actuator | `/actuator/health` | 健康 |
| Spring Boot Actuator | `/actuator/metrics` | JVM/HTTP 指标 |
| Prometheus | scrape `/actuator/prometheus` | 时序数据库（需加 `micrometer-registry-prometheus`）|
| Grafana | dashboard | 可视化 |
| Sentry | 前端 + 后端 | 错误聚合 |

## 10. 升级与回滚

```bash
# 升级
sudo systemctl stop medrms-backend
cd /opt/medrms
mv med-rms-web-1.0.0-SNAPSHOT.jar med-rms-web-1.0.0-SNAPSHOT.jar.bak
cp /tmp/new-version.jar med-rms-web-1.0.0-SNAPSHOT.jar
sudo systemctl start medrms-backend

# 回滚（如升级失败）
sudo systemctl stop medrms-backend
mv med-rms-web-1.0.0-SNAPSHOT.jar.bak med-rms-web-1.0.0-SNAPSHOT.jar
sudo systemctl start medrms-backend
```

## 11. 故障排查

| 症状 | 排查 | 解决 |
|------|------|------|
| 401 Unauthorized | Token 过期 / 没传 | request.ts 自动 refresh；失败跳登录 |
| 403 业务码 SY0202 | 权限不足 | 检查 `t_user_role` |
| 启动报"无法连接 PG" | PG 未启 / 防火墙 | `systemctl start postgresql` |
| 启动报"无法连接 Redis" | Redis 未启 | `systemctl start redis` |
| 前端空白 | dist 路径错 | 检查 Nginx root |
| 慢查询 | pg_stat_statements | W19 优化经验：祖先表索引 |

## 12. 联系

- 技术支持：med-rms-team@example.com
- 紧急联系：+86-XXX-XXXX-XXXX
- Slack 频道：#med-rms-prod
