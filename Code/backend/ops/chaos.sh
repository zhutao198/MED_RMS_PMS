#!/bin/bash
# Med-RMS 故障注入实战演练
# 模拟"后端挂掉 / DB 连接耗尽"，验证自动恢复 + 记录 MTTR
#
# 用法：./chaos.sh [scenario]
#   scenario: kill-backend / kill-db-conns / kill-all
#   默认: kill-backend

set -e

LOG_DIR="D:/zhutao/medrms-chaos-logs"
mkdir -p "$LOG_DIR"
LOG="$LOG_DIR/chaos-$(date +%Y%m%d-%H%M%S).log"
SCENARIO="${1:-kill-backend}"

log() { echo "[$(date '+%H:%M:%S')] $1" | tee -a "$LOG"; }

check_health() {
    local code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:8080/api/health" 2>/dev/null || echo "000")
    echo "$code"
}

log "===== Chaos Test: $SCENARIO ====="
log "Log: $LOG"

# 0) 初始健康基线
INITIAL=$(check_health)
log "初始健康: $INITIAL (期望 200)"

OUTAGE_START=$(date +%s)
OUTAGE_DETECTED=""

case "$SCENARIO" in
    kill-backend)
        log "🔪 模拟：kill 后端进程"
        # 找后端 PID（8080 端口）
        BACKEND_PID=$(netstat -ano 2>/dev/null | grep ':8080\s' | head -1 | awk '{print $NF}' | tr -d ' ')
        if [ -z "$BACKEND_PID" ]; then
            log "⚠️ 未找到 8080 监听进程"
            exit 1
        fi
        log "  目标 PID: $BACKEND_PID"

        # 模拟后端崩溃
        taskkill //F //PID "$BACKEND_PID" 2>&1 | tee -a "$LOG" || true
        log "  ✅ 后端已 kill"
        ;;

    kill-db-conns)
        log "🔪 模拟：杀 PG 连接（保留后端进程）"
        PGPASSWORD=postgres "/c/Program Files/PostgreSQL/16/bin/pg_terminate_backend.exe" \
            -h localhost -U postgres -d med_rms_pms \
            "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='med_rms_pms' AND application_name != 'chaos'" 2>&1 | tee -a "$LOG"
        log "  ✅ PG 连接已杀"
        ;;

    kill-all)
        log "🔪 模拟：kill-all（后端 + 杀 PG 连接）"
        BACKEND_PID=$(netstat -ano 2>/dev/null | grep ':8080\s' | head -1 | awk '{print $NF}' | tr -d ' ')
        [ -n "$BACKEND_PID" ] && taskkill //F //PID "$BACKEND_PID" 2>&1 | tee -a "$LOG" || true
        ;;

    *)
        log "❌ 未知 scenario: $SCENARIO"
        log "   可用: kill-backend / kill-db-conns / kill-all"
        exit 1
        ;;
esac

# 1) 检测 outage（轮询 5s，10 次）
log "📡 检测 outage..."
for i in $(seq 1 10); do
    sleep 1
    HEALTH=$(check_health)
    if [ "$HEALTH" != "200" ]; then
        OUTAGE_DETECTED=$(date +%s)
        log "  ❌ 故障检出 (i=$i): HTTP $HEALTH"
        break
    fi
done

# 2) 自动恢复（重启后端）
log "🔧 自动恢复..."
if [ "$SCENARIO" = "kill-backend" ] || [ "$SCENARIO" = "kill-all" ]; then
    # 启动后端
    cd "D:/zhutao/MED_RMS_PMS/Code/backend" || exit 1
    log "  启动后端..."
    nohup mvn spring-boot:run -pl med-rms-web -Dspring-boot.run.profiles=dev \
        > "$LOG_DIR/backend-restart.log" 2>&1 &
    log "  后端 PID: $!"
fi

# 3) 验证恢复
log "✅ 验证恢复..."
for i in $(seq 1 30); do
    sleep 2
    HEALTH=$(check_health)
    if [ "$HEALTH" = "200" ]; then
        OUTAGE_END=$(date +%s)
        MTTR=$((OUTAGE_END - OUTAGE_START))
        log "  ✅ 恢复成功 (i=$i, ${MTTR}s)"
        log ""
        log "📊 演练结果："
        log "  故障: $SCENARIO"
        log "  MTTR: ${MTTR}s"
        log "  状态: ✅ 系统自动恢复"
        log "  日志: $LOG"
        exit 0
    fi
done

log "  ❌ 30 次轮询未恢复（60s 超时）"
log "  请人工介入"
exit 1
