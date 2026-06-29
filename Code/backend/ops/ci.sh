#!/bin/bash
# Med-RMS 完整 CI 流水线
# 串联：单测 → 集成 → 备份 → k6 冒烟 → 报告
#
# 用法：./ci.sh
# 退出码：0=全过 / 1=任意阶段失败

set -e

ROOT="D:/zhutao/MED_RMS_PMS/Code/backend"
LOG_DIR="$ROOT/ci-logs"
mkdir -p "$LOG_DIR"

step() { echo ""; echo "===== [$1] ====="; }
run() {
    local name=$1; shift
    local log="$LOG_DIR/$name.log"
    echo "▶ $name"
    if "$@" > "$log" 2>&1; then
        echo "  ✅ PASS: $name ($(wc -l < $log) lines log)"
        return 0
    else
        echo "  ❌ FAIL: $name (tail 20 below)"
        tail -20 "$log"
        return 1
    fi
}

START=$(date +%s)

step "Stage 1: 单元测试"
cd "$ROOT" || exit 1
run "unit-test" mvn test -Djacoco.skip=true
TESTS=$(grep -h 'Tests run' target/surefire-reports/*.txt 2>/dev/null | awk -F'[:,]' '{tests+=$2; failures+=$3; errors+=$4} END {printf "%d/%d/%d", tests, failures, errors}')
echo "  📊 单元测试: $TESTS"

step "Stage 2: 集成测试"
run "integration-test" mvn -pl med-rms-web test -Djacoco.skip=true

step "Stage 3: DB 备份"
run "db-backup" "$ROOT/ops/backup.sh"
BACKUP_SIZE=$(du -h "$LOG_DIR/db-backup.log" 2>/dev/null | tail -1 | awk '{print $1}')
echo "  💾 备份完成: $BACKUP_SIZE"

step "Stage 4: k6 性能冒烟（短 30s × 30VU）"
K6="$ROOT/../backend/tools/bin/k6/k6-v0.50.0-windows-amd64/k6.exe"
[ -f "$K6" ] || K6="D:/zhutao/MED_RMS_PMS/Code/backend/tools/bin/k6/k6-v0.50.0-windows-amd64/k6.exe"
run "k6-smoke" "$K6" run --duration 30s --vus 30 "$ROOT/tools/perf/w17_smoke.js"

step "Stage 5: 总结"
END=$(date +%s)
ELAPSED=$((END - START))
echo ""
echo "🎉 全部 CI 阶段通过"
echo "  ⏱️  耗时: ${ELAPSED}s"
echo "  📁  日志: $LOG_DIR"

# 写 CI 摘要
SUMMARY="$LOG_DIR/ci-summary.txt"
{
    echo "Med-RMS CI Run @ $(date '+%Y-%m-%d %H:%M:%S')"
    echo "Elapsed: ${ELAPSED}s"
    echo "Unit tests: $TESTS"
    echo "Status: ✅ ALL PASS"
} > "$SUMMARY"
cat "$SUMMARY"
