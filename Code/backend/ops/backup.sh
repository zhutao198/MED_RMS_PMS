#!/bin/bash
# Med-RMS PostgreSQL 自动备份脚本
# 用法：
#   ./backup.sh              # 完整备份到 BACKUP_DIR
#   BACKUP_DIR=/path/to/dir ./backup.sh
#
# crontab 安装（每天 2:00 备份，保留 7 天）：
#   0 2 * * * /path/to/backup.sh >> /var/log/medrms-backup.log 2>&1

set -e

# 配置
DB_NAME="med_rms_pms"
DB_USER="postgres"
DB_HOST="localhost"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
DAY_OF_WEEK=$(date +%u)

BACKUP_DIR="${BACKUP_DIR:-D:/zhutao/medrms-backups}"
mkdir -p "$BACKUP_DIR"

BACKUP_FILE="$BACKUP_DIR/medrms-$TIMESTAMP.dump"
LOG_FILE="$BACKUP_DIR/backup.log"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log "===== 备份开始 ====="
log "DB: $DB_NAME @ $DB_HOST"
log "目标: $BACKUP_FILE"

# 1) pg_dump 完整备份
# W21 修复：用 PG bin 目录的 pg_dump（pgAdmin 4 自带版缺 lz4 库）
PGPASSWORD=postgres "/c/Program Files/PostgreSQL/16/bin/pg_dump.exe" \
    -h "$DB_HOST" -U "$DB_USER" -d "$DB_NAME" \
    --format=custom --compress=9 \
    --file="$BACKUP_FILE" 2>> "$LOG_FILE"

if [ ! -f "$BACKUP_FILE" ]; then
    log "❌ 备份失败：未生成文件"
    exit 1
fi

BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
log "✅ 备份成功: $BACKUP_FILE ($BACKUP_SIZE)"

# 2) 保留策略：只保留最近 7 天的备份
DELETED=$(find "$BACKUP_DIR" -name "medrms-*.dump" -mtime +7 -type f -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
    log "🗑️ 清理过期备份: $DELETED 个"
fi

# 3) 每周日 0 5 * * 0 备份到远程（可选，注释掉）
# rsync -avz "$BACKUP_DIR" backup@backup-server:/remote/medrms/

log "===== 备份完成 ====="
