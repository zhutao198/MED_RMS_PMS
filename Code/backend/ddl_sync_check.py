"""v1.27 DDL 同步核查"""
import re
from pathlib import Path

BACKEND = Path('D:/zhutao/MED_RMS_PMS/Code/backend')
DDL_DIR = BACKEND / 'ddl'

ddl_tables = {}
for sql in DDL_DIR.glob('*.sql'):
    text = sql.read_text(encoding='utf-8', errors='ignore')
    for m in re.finditer(r'CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)\.(\w+)\s*\((.*?)\);', text, re.IGNORECASE | re.DOTALL):
        schema, table, body = m.group(1), m.group(2), m.group(3)
        cols = set(re.findall(r'\b(\w+)\s+(?:VARCHAR|INTEGER|BIGINT|TEXT|TIMESTAMP|BOOLEAN|DATE|NUMERIC|DECIMAL|DOUBLE|SERIAL|CHAR)\b', body, re.IGNORECASE))
        ddl_tables[table] = {'schema': schema, 'columns': cols, 'ddl_file': sql.name}

entity_tables = {}
for f in BACKEND.glob('med-rms-*/src/main/java/**/domain/entity/*.java'):
    text = f.read_text(encoding='utf-8', errors='ignore')
    m_tn = re.search(r'@TableName\(\s*"([^"]+)"\s*\)', text)
    if not m_tn:
        continue
    tn = m_tn.group(1).split('.')[-1]
    fields = set(re.findall(r'private\s+\S+\s+(\w+)\s*[=;]', text))
    fields = {f for f in fields if not f.startswith('this')}
    entity_tables[tn] = {'fields': fields, 'java_file': str(f.relative_to(BACKEND))}

finds = []
v127_entities = [
    't_baseline', 't_requirement', 't_test_case', 't_requirement_pool',
    't_risk_register', 't_risk_assessment', 't_permission', 't_role_permission',
    't_user_role', 't_user', 't_audit_log', 't_change_request',
    't_compliance_template', 't_electronic_signature', 't_notification',
    't_ipd_gate', 't_worklog', 't_project_member', 't_milestone', 't_task',
    't_review', 't_requirement_version', 't_signature_settings',
    't_soup_component', 't_soup_risk_link', 't_dhf_evidence', 't_report',
    't_problem_report', 't_iec62304_checklist', 't_gantt_resource',
    't_project', 't_role', 't_dict_item', 't_system_config',
]
print('=== v1.27 DDL 同步核查 ===')
ok = 0
for t in v127_entities:
    has_entity = t in entity_tables
    has_ddl = t in ddl_tables
    if has_entity and has_ddl:
        print(f'  [OK]   {t:30s}  entity:{len(entity_tables[t]["fields"]):2d} fields  ddl:{len(ddl_tables[t]["columns"]):2d} cols')
        ok += 1
    elif has_entity and not has_ddl:
        finds.append(f'[MISS] {t}: 实体有但 DDL 无')
    elif not has_entity and has_ddl:
        print(f'  [INFO] {t:30s}  DDL 有但未找到实体（可能用 Map）')
    else:
        finds.append(f'[SKIP] {t}: 实体和 DDL 都未找到')

print(f'\n汇总: {ok} OK, {len(finds)} 问题')
print(f'实体总数: {len(entity_tables)}')
print(f'DDL 表数: {len(ddl_tables)}')
for f in finds[:10]:
    print(' ', f)
