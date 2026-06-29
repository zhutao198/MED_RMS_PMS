"""v1.27 OpenAPI 权限标注核查
对比 PermissionMatrix 的 URL→perm 规则 vs OpenAPI yaml 中的 path，
检查 OpenAPI 文档中是否标注了 x-required-permission
"""
import re
from pathlib import Path

MATRIX_FILE = Path('D:/zhutao/MED_RMS_PMS/Code/backend/med-rms-admin/src/main/java/com/zhutao/medrms/admin/security/PermissionMatrix.java')
OPENAPI = Path('D:/zhutao/MED_RMS_PMS/Detailed/03-OpenAPI/med-rms-openapi.yaml')

text = MATRIX_FILE.read_text(encoding='utf-8', errors='ignore')

# 实际格式: addExact(HttpMethod.POST, "/path/{id}/x", "perm:code")
exact_rules = re.findall(r'addExact\(HttpMethod\.(\w+),\s*"([^"]+)",\s*"([^"]+)"\)', text)
prefix_rules = re.findall(r'addPrefix\(HttpMethod\.(\w+),\s*"([^"]+)",\s*"([^"]+)"\)', text)

yaml_text = OPENAPI.read_text(encoding='utf-8', errors='ignore')
openapi_paths = re.findall(r'^\s{2}(/[\w/{}]+):', yaml_text, re.MULTILINE)
annotated = yaml_text.count('x-required-permission')

print('=== OpenAPI 权限标注核查 ===')
print(f'PermissionMatrix 规则: {len(exact_rules)} exact + {len(prefix_rules)} prefix = {len(exact_rules)+len(prefix_rules)}')
print(f'OpenAPI paths: {len(openapi_paths)}')
print(f'OpenAPI 中 x-required-permission 标注数: {annotated}')

# 列出前 5 条 PermissionMatrix 规则示例
print('\nPermissionMatrix 规则示例 (前 5):')
for m, p, c in (exact_rules + prefix_rules)[:5]:
    print(f'  {m:7s} {p:50s} -> {c}')

# 检查 OpenAPI 文档中未标注的 path
print('\n未标注 x-required-permission 的 path (前 10):')
paths_with_perm = set()
for m in re.finditer(r'^\s{2}(/[\w/{}]+):\s*\n((?:\s{4,}.+\n)+)', yaml_text, re.MULTILINE):
    path = m.group(1)
    block = m.group(2)
    if 'x-required-permission' in block:
        paths_with_perm.add(path)
unannotated = [p for p in openapi_paths if p not in paths_with_perm]
for p in unannotated[:10]:
    print(f'  {p}')

print(f'\n结论:')
if annotated == 0:
    print('  OpenAPI 文档中没有任何 x-required-permission 标注')
    print('  建议: 给每个 path 加 x-required-permission 字段，引用 PermissionMatrix')
else:
    cov = len(paths_with_perm) * 100 / max(len(openapi_paths), 1)
    print(f'  标注覆盖率: {cov:.1f}% ({len(paths_with_perm)} / {len(openapi_paths)})')
    if len(unannotated) > 0:
        print(f'  仍有 {len(unannotated)} 个 path 未标注')
