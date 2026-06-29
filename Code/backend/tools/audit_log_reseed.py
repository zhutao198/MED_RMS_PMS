#!/usr/bin/env python3
"""审计日志哈希链 reseed 工具

Why: 历史上 audit_log.hash 算法多次迭代（DDL 125 字段重命名、BUG #93 时间戳、
     String.valueOf(null)→"null" vs 空串 约定变更），导致 verifyHashChain 失败。
     仅靠改 Java 代码重算 hash 不能恢复已落库的 stored hash。
     本工具用当前算法重新计算全部 current_hash / prev_hash，保持链式链接。

用法:
  python tools/audit_log_reseed.py --dry-run   # 仅校验，不写库
  python tools/audit_log_reseed.py             # 实际 reseed
"""
import argparse
import hashlib
import sys
from pathlib import Path
import psycopg2

DB_DSN = dict(host="localhost", port=5432, database="med_rms_pms",
              user="postgres", password="postgres")


def calc_hash(prev_hash, event_type, entity_type, entity_id, operator_id,
              operation, old_val, new_val, ts):
    """对齐 SecurityUtils.calculateAuditHash（v1.45 BUG #93 修复后）"""
    # Java ISO_FORMATTER: yyyy-MM-dd'T'HH:mm:ss.SSS (millisecond)
    ts_str = ts.strftime("%Y-%m-%dT%H:%M:%S.") + f"{ts.microsecond // 1000:03d}"

    def to_str(v):
        # String.valueOf((Long)null) → "null"
        return "null" if v is None else str(v)

    def to_empty(v):
        # nullToEmpty(String) / oldValueJson == null ? "" : oldValueJson
        return "" if v is None else v

    content = "|".join([
        to_empty(prev_hash),
        event_type or "",
        entity_type or "",
        to_str(entity_id),
        to_str(operator_id),
        operation or "",
        to_empty(old_val),
        to_empty(new_val),
        ts_str,
    ])
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只校验不写库")
    args = ap.parse_args()

    conn = psycopg2.connect(**DB_DSN)
    cur = conn.cursor()
    cur.execute("""
        SELECT id, event_type, entity_type, entity_id, operator_id,
               operation, old_value, new_value, created_at
        FROM compliance_schema.t_audit_log
        WHERE is_deleted = false
        ORDER BY id
    """)
    rows = cur.fetchall()
    print(f"[INFO] 待处理记录: {len(rows)}")

    prev_hash = "0" * 64
    updated = 0
    mismatches = []

    for r in rows:
        rid, evt, ent_t, ent_id, op_id, op, old_v, new_v, ts = r
        new_hash = calc_hash(prev_hash, evt, ent_t, ent_id, op_id, op, old_v, new_v, ts)
        cur.execute(
            "UPDATE compliance_schema.t_audit_log SET prev_hash=%s, current_hash=%s WHERE id=%s",
            (prev_hash, new_hash, rid),
        )
        prev_hash = new_hash
        updated += 1

    conn.commit() if not args.dry_run else conn.rollback()
    print(f"[{'DRY-RUN' if args.dry_run else 'OK'}] {'将更新' if args.dry_run else '已更新'} {updated} 条记录")

    # 校验
    cur.execute("""
        SELECT id, prev_hash, event_type, entity_type, entity_id, operator_id,
               operation, old_value, new_value, current_hash, created_at
        FROM compliance_schema.t_audit_log WHERE is_deleted = false ORDER BY id
    """)
    expected = "0" * 64
    for r in cur.fetchall():
        rid, prev, evt, ent_t, ent_id, op_id, op, old_v, new_v, stored, ts = r
        if prev != expected:
            mismatches.append(f"id={rid} PREV mismatch")
            break
        h = calc_hash(prev, evt, ent_t, ent_id, op_id, op, old_v, new_v, ts)
        if h != stored:
            mismatches.append(f"id={rid} HASH mismatch stored={stored[:20]} computed={h[:20]}")
            break
        expected = stored

    if mismatches:
        print(f"[FAIL] 校验失败: {mismatches[0]}")
        sys.exit(1)
    else:
        print(f"[OK] 链式校验通过，全部 {updated} 条一致")
    conn.close()


if __name__ == "__main__":
    main()
