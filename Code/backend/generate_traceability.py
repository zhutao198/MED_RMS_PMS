#!/usr/bin/env python3
"""Generate traceability SQL for 8117 project"""
import json

with open("D:/zhutao/MED_RMS_PMS/Code/backend/requirement_ids.json") as f:
    data = json.load(f)

lines = ["-- 8117项目完整追溯链", ""]

# URS -> PRS -> SRS -> DRS
# PRS -> SRS -> DRS
# SRS -> DRS

chains = [
    # ECG chain: URS-001 -> PRS-001,002,003 -> SRS-001,002,003 -> DRS-001,002,003
    ("urs", "URS-8117-001", "prs", "PRS-8117-001"),
    ("urs", "URS-8117-001", "prs", "PRS-8117-002"),
    ("urs", "URS-8117-001", "prs", "PRS-8117-003"),
    ("prs", "PRS-8117-001", "srs", "SRS-8117-001"),
    ("prs", "PRS-8117-001", "srs", "SRS-8117-002"),
    ("prs", "PRS-8117-003", "srs", "SRS-8117-003"),
    ("srs", "SRS-8117-001", "drs", "DRS-8117-001"),
    ("srs", "SRS-8117-001", "drs", "DRS-8117-002"),
    ("srs", "SRS-8117-002", "drs", "DRS-8117-003"),

    # AI chain: URS-002 -> PRS-004,005 -> SRS-004,005 -> DRS-004,005
    ("urs", "URS-8117-002", "prs", "PRS-8117-004"),
    ("urs", "URS-8117-002", "prs", "PRS-8117-005"),
    ("prs", "PRS-8117-004", "srs", "SRS-8117-004"),
    ("prs", "PRS-8117-004", "srs", "SRS-8117-005"),
    ("srs", "SRS-8117-004", "drs", "DRS-8117-004"),
    ("srs", "SRS-8117-004", "drs", "DRS-8117-005"),

    # Network chain: URS-003 -> PRS-006,007,008 -> SRS-006,007,008 -> DRS-006,007,008
    ("urs", "URS-8117-003", "prs", "PRS-8117-006"),
    ("urs", "URS-8117-003", "prs", "PRS-8117-007"),
    ("urs", "URS-8117-003", "prs", "PRS-8117-008"),
    ("prs", "PRS-8117-006", "srs", "SRS-8117-006"),
    ("prs", "PRS-8117-007", "srs", "SRS-8117-007"),
    ("prs", "PRS-8117-008", "srs", "SRS-8117-008"),
    ("srs", "SRS-8117-006", "drs", "DRS-8117-006"),
    ("srs", "SRS-8117-007", "drs", "DRS-8117-007"),
    ("srs", "SRS-8117-008", "drs", "DRS-8117-008"),

    # Critical Alert chain: URS-004 -> PRS-009,010 -> SRS-009,010 -> DRS-009,010
    ("urs", "URS-8117-004", "prs", "PRS-8117-009"),
    ("urs", "URS-8117-004", "prs", "PRS-8117-010"),
    ("prs", "PRS-8117-009", "srs", "SRS-8117-009"),
    ("prs", "PRS-8117-010", "srs", "SRS-8117-010"),
    ("srs", "SRS-8117-009", "drs", "DRS-8117-010"),
    ("srs", "SRS-8117-010", "drs", "DRS-8117-009"),

    # Platform chain: URS-005 -> PRS-011,012 -> SRS-011,012,013 -> DRS-011,012
    ("urs", "URS-8117-005", "prs", "PRS-8117-011"),
    ("urs", "URS-8117-005", "prs", "PRS-8117-012"),
    ("prs", "PRS-8117-011", "srs", "SRS-8117-011"),
    ("prs", "PRS-8117-011", "srs", "SRS-8117-012"),
    ("prs", "PRS-8117-012", "srs", "SRS-8117-013"),
    ("srs", "SRS-8117-011", "drs", "DRS-8117-011"),
    ("srs", "SRS-8117-012", "drs", "DRS-8117-012"),

    # Hardware chain: URS-006 -> PRS-013,014 -> SRS-014,015 -> DRS-013,014
    ("urs", "URS-8117-006", "prs", "PRS-8117-013"),
    ("urs", "URS-8117-006", "prs", "PRS-8117-014"),
    ("prs", "PRS-8117-013", "srs", "SRS-8117-014"),
    ("prs", "PRS-8117-014", "srs", "SRS-8117-015"),
    ("srs", "SRS-8117-014", "drs", "DRS-8117-013"),
    ("srs", "SRS-8117-015", "drs", "DRS-8117-014"),

    # Battery chain: URS-007 -> PRS-015,016 -> SRS-016,017 -> DRS-015,016
    ("urs", "URS-8117-007", "prs", "PRS-8117-015"),
    ("urs", "URS-8117-007", "prs", "PRS-8117-016"),
    ("prs", "PRS-8117-015", "srs", "SRS-8117-016"),
    ("prs", "PRS-8117-016", "srs", "SRS-8117-017"),
    ("srs", "SRS-8117-016", "drs", "DRS-8117-015"),
    ("srs", "SRS-8117-017", "drs", "DRS-8117-016"),

    # Lead detection: URS-008 -> PRS-017 -> SRS-018 -> DRS-017
    ("urs", "URS-8117-008", "prs", "PRS-8117-017"),
    ("prs", "PRS-8117-017", "srs", "SRS-8117-018"),
    ("srs", "SRS-8117-018", "drs", "DRS-8117-017"),

    # Screen rotation: URS-013 -> PRS-018
    ("urs", "URS-8117-013", "prs", "PRS-8117-018"),

    # NFC: URS-015 -> PRS-019
    ("urs", "URS-8117-015", "prs", "PRS-8117-019"),

    # Camera: URS-016 -> PRS-020
    ("urs", "URS-8117-016", "prs", "PRS-8117-020"),

    # GPS: URS-017 -> PRS-021
    ("urs", "URS-8117-017", "prs", "PRS-8117-021"),

    # Pick up wake: URS-018 -> PRS-022
    ("urs", "URS-8117-018", "prs", "PRS-8117-022"),

    # Fast boot: URS-019 -> PRS-023
    ("urs", "URS-8117-019", "prs", "PRS-8117-023"),

    # Emergency: URS-020 -> PRS-024
    ("urs", "URS-8117-020", "prs", "PRS-8117-024"),

    # Offline: URS-022 -> PRS-025
    ("urs", "URS-8117-022", "prs", "PRS-8117-025"),

    # Security log: URS-023 -> PRS-026
    ("urs", "URS-8117-023", "prs", "PRS-8117-026"),

    # Compliance: URS-024 -> PRS-027,028
    ("urs", "URS-8117-024", "prs", "PRS-8117-027"),
    ("urs", "URS-8117-024", "prs", "PRS-8117-028"),
]

inserts = []
def get_id(level, no):
    entry = data[level][no]
    if isinstance(entry, int):
        return entry
    return entry["id"]

for level_from, no_from, level_to, no_to in chains:
    id_from = get_id(level_from, no_from)
    id_to = get_id(level_to, no_to)
    inserts.append(f"INSERT INTO req_schema.t_requirement_ancestor (ancestor_id, descendant_id, depth) VALUES ({id_from}, {id_to}, 1);")

sql = "-- 8117项目完整追溯链\n\n" + "\n".join(inserts) + "\n"
with open("D:/zhutao/MED_RMS_PMS/Code/backend/ddl/traceability_8117_final.sql", "w") as f:
    f.write(sql)

print(f"Generated {len(inserts)} traceability records")
print("Saved to ddl/traceability_8117_final.sql")