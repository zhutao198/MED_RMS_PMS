"""
JaCoCo 覆盖率汇总脚本（W9-D1）
聚合所有模块的 jacoco.csv，输出 Service 层覆盖率 + 盲区清单
"""
import csv
import os
import re
from collections import defaultdict
from pathlib import Path

BACKEND = Path(r"D:\zhutao\MED_RMS_PMS\Code\backend")
MODULES = [
    "med-rms-common", "med-rms-admin", "med-rms-compliance", "med-rms-change",
    "med-rms-esignature", "med-rms-notification", "med-rms-project",
    "med-rms-requirement", "med-rms-risk", "med-rms-traceability", "med-rms-web"
]

# Service 类识别（按包名 / 类名后缀）
SERVICE_RE = re.compile(r"\.service\.[A-Z][A-Za-z0-9]+Service\b")
CONTROLLER_RE = re.compile(r"\.controller\.[A-Z][A-Za-z0-9]+Controller\b")
MAPPER_RE = re.compile(r"\.mapper\.[A-Z][A-Za-z0-9]+Mapper\b")
ENTITY_RE = re.compile(r"\.domain\.entity\.[A-Z][A-Za-z0-9]+$")

def parse_module(module: str):
    csv_path = BACKEND / module / "target" / "site" / "jacoco" / "jacoco.csv"
    if not csv_path.exists():
        return None
    services = []
    controllers = []
    mappers = []
    entities = []
    with csv_path.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            cls = row.get("CLASS", "")
            pkg = row.get("PACKAGE", "")
            if not cls:
                continue
            covered = int(row["LINE_COVERED"])
            missed = int(row["LINE_MISSED"])
            total = covered + missed
            if total == 0:
                continue
            rate = covered * 100 / total
            # 用 PACKAGE 判断类型（更可靠）
            entry = (cls, covered, missed, total, rate)
            if pkg.endswith(".service") and cls.endswith("Service"):
                services.append(entry)
            elif pkg.endswith(".controller") and cls.endswith("Controller"):
                controllers.append(entry)
            elif pkg.endswith(".mapper") and cls.endswith("Mapper"):
                mappers.append(entry)
            elif ".entity" in pkg:
                entities.append(entry)
    return {
        "module": module,
        "services": sorted(services, key=lambda x: x[4]),
        "controllers": sorted(controllers, key=lambda x: x[4]),
        "mappers": mappers,
        "entities": entities,
    }

def fmt_class(cls: str) -> str:
    """简化类名显示"""
    return cls.split(".")[-1]

def main():
    print("=" * 80)
    print("Med-RMS JaCoCo 覆盖率分析报告（W9-D1）")
    print("=" * 80)

    all_services = []
    all_controllers = []
    total_covered = 0
    total_missed = 0
    module_count = 0

    for m in MODULES:
        data = parse_module(m)
        if not data:
            continue
        module_count += 1
        for entry in data["services"]:
            all_services.append((m, *entry))
        for entry in data["controllers"]:
            all_controllers.append((m, *entry))
        for entry in data["services"] + data["controllers"]:
            total_covered += entry[2]
            total_missed += entry[3]

    total = total_covered + total_missed
    overall = total_covered * 100 / total if total else 0

    # 按 Service 行覆盖升序（最差在前）
    all_services.sort(key=lambda x: x[5])

    # ===== 总体指标 =====
    print(f"\n## 1. 总体指标（Service + Controller）")
    print(f"  - 模块数：{module_count}")
    print(f"  - 覆盖行：{total_covered} / {total}")
    print(f"  - 总体行覆盖：{overall:.1f}%")
    print(f"  - Service 类数：{len(all_services)}")
    print(f"  - Controller 类数：{len(all_controllers)}")

    # ===== Service 盲区 Top 10 =====
    print(f"\n## 2. Service 行覆盖盲区 Top 10（最低优先）")
    print(f"  {'模块':<22} {'类名':<40} {'覆盖':>5} {'未覆':>5} {'率':>6}")
    print(f"  {'-'*22} {'-'*40} {'-'*5} {'-'*5} {'-'*6}")
    for m, cls, cov, miss, tot, rate in all_services[:10]:
        print(f"  {m:<22} {fmt_class(cls):<40} {cov:>5} {miss:>5} {rate:>5.1f}%")

    # ===== 优秀 Top 5 =====
    print(f"\n## 3. Service 行覆盖优秀 Top 5（≥ 80%）")
    for m, cls, cov, miss, tot, rate in reversed(all_services[-5:]):
        print(f"  {m:<22} {fmt_class(cls):<40} {cov:>5} {miss:>5} {rate:>5.1f}%")

    # ===== 按模块聚合 =====
    print(f"\n## 4. 按模块聚合行覆盖")
    module_stats = defaultdict(lambda: [0, 0])
    for m, cls, cov, miss, tot, rate in all_services:
        module_stats[m][0] += cov
        module_stats[m][1] += miss
    for m in sorted(module_stats.keys()):
        cov, miss = module_stats[m]
        tot = cov + miss
        rate = cov * 100 / tot if tot else 0
        print(f"  {m:<22}  {cov:>5} / {tot:<5}  {rate:>5.1f}%")

    # ===== Service 总体指标 =====
    print(f"\n## 5. 业务 Service 覆盖率统计（合并各模块）")
    s_covered = sum(e[2] for e in all_services)
    s_missed = sum(e[3] for e in all_services)
    s_total = s_covered + s_missed
    s_rate = s_covered * 100 / s_total if s_total else 0
    print(f"  Service 覆盖：{s_covered} / {s_total} = {s_rate:.1f}%")

    if s_rate >= 80:
        print(f"  [OK] 达到 >=80% 目标")
    else:
        print(f"  [WARN] 距离 80% 目标还差 {80 - s_rate:.1f}%")

    # ===== Controller 指标 =====
    c_covered = sum(e[2] for e in all_controllers)
    c_missed = sum(e[3] for e in all_controllers)
    c_total = c_covered + c_missed
    c_rate = c_covered * 100 / c_total if c_total else 0
    print(f"\n  Controller 覆盖：{c_covered} / {c_total} = {c_rate:.1f}%")
    if c_rate >= 85:
        print(f"  [OK] 达到 >=85% 目标")
    else:
        print(f"  [WARN] 距离 85% 目标还差 {85 - c_rate:.1f}%")

    print()
    print("=" * 80)

if __name__ == "__main__":
    main()
