#!/bin/bash
# Med-RMS 健康检查 + 故障探测脚本
# 持续监控 /actuator/health，任何非 200/UP 立即告警（写日志）
# 用法：
#   ./health_check.sh 2>&1 | tee -a /var/log/medrms-health.log

INTERVAL="${INTERVAL:-10}"  # 秒
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
LOG="/tmp/medrms-health.log"

echo "===== Med-RMS 健康检查启动 $(date) ====="
echo "  Target: $BACKEND_URL/api/health"
echo "  Interval: ${INTERVAL}s"
echo "  Log: $LOG"

check_one() {
    local code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$BACKEND_URL/api/health" 2>/dev/null)
    local now=$(date '+%Y-%m-%d %H:%M:%S')
    if [ "$code" = "200" ]; then
        echo "[$now] ✅ UP ($code)"
    else
        echo "[$now] ❌ DOWN (code=$code) **ALERT**"
    fi
}

trap "echo '收到 SIGINT，退出健康检查'; exit 0" INT TERM

while true; do
    check_one
    sleep "$INTERVAL"
done
