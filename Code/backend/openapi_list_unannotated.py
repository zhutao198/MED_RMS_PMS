"""列出未标注 x-required-permission 的所有 operation"""
import re
import io
import sys
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

OPENAPI = Path('D:/zhutao/MED_RMS_PMS/Detailed/03-OpenAPI/med-rms-openapi.yaml')
text = OPENAPI.read_text(encoding='utf-8', errors='ignore')

# Find all path blocks
path_re = re.compile(r'^  (/[\w/{}\-]+):\s*$', re.MULTILINE)
method_re = re.compile(r'^    (get|post|put|delete|patch):\s*$', re.MULTILINE)

paths = list(path_re.finditer(text))
ops = []
for i, pm in enumerate(paths):
    block_start = pm.end()
    block_end = paths[i+1].start() if i+1 < len(paths) else len(text)
    block = text[block_start:block_end]
    # Find methods and check if x-required-permission exists in each method's block
    methods = list(method_re.finditer(block))
    for j, mm in enumerate(methods):
        method = mm.group(1)
        method_start = mm.start()
        method_end = methods[j+1].start() if j+1 < len(methods) else len(block)
        method_block = block[method_start:method_end]
        if 'x-required-permission' not in method_block:
            ops.append((method, pm.group(1)))

print('Total operations without x-required-permission:', len(ops))
for m, p in ops:
    print(f'  {m:6s} {p}')
