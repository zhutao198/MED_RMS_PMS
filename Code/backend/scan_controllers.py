#!/usr/bin/env python3
"""Scan all *Controller.java files (excluding target/) and extract HTTP endpoints."""
import os
import re
import sys
import io
from collections import defaultdict, OrderedDict

# Force UTF-8 stdout
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

ROOT = r"D:/zhutao/MED_RMS_PMS/Code/backend"

# Regex patterns
CLASS_MAPPING_RE = re.compile(
    r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)"|\{[^}]*\}[^)]*)\)',
    re.DOTALL
)
METHOD_MAPPING_RES = [
    ('GET',     re.compile(r'@GetMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?\s*\)', re.DOTALL)),
    ('POST',    re.compile(r'@PostMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?\s*\)', re.DOTALL)),
    ('PUT',     re.compile(r'@PutMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?\s*\)', re.DOTALL)),
    ('DELETE',  re.compile(r'@DeleteMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?\s*\)', re.DOTALL)),
    ('PATCH',   re.compile(r'@PatchMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?\s*\)', re.DOTALL)),
    ('REQUEST', re.compile(r'@RequestMapping\s*\(\s*(?:value\s*=\s*)?(?:"([^"]*)")?\s*(?:,\s*method\s*=\s*RequestMethod\.(\w+))?\s*\)', re.DOTALL)),
]
PARAM_RE = re.compile(
    r'@(RequestParam|PathVariable|RequestBody|RequestHeader)(?:\s*\(([^)]*)\))?',
    re.DOTALL
)
CLASS_RE = re.compile(r'class\s+(\w+Controller)\b')

def parse_params(text):
    """Parse the parameter block to find @RequestParam/@PathVariable/@RequestBody."""
    params = []
    # find the first '(' and the matching ')'
    depth = 0
    start = text.find('(')
    if start < 0:
        return params
    i = start
    while i < len(text):
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
            if depth == 0:
                body = text[start+1:i]
                break
        i += 1
    else:
        return params
    # find all @Xxx(params) within body
    for m in PARAM_RE.finditer(body):
        ann = m.group(1)
        args = m.group(2) or ''
        # extract name if present
        nm = re.search(r'(?:name|value)\s*=\s*"([^"]+)"', args)
        required = 'required\s*=\s*false' in args
        if ann == 'RequestBody':
            params.append('@RequestBody')
        elif nm:
            if ann == 'PathVariable':
                params.append(f'@PV {nm.group(1)}')
            elif ann == 'RequestParam':
                params.append(f'@QP {nm.group(1)}' + ('' if not required else ''))
            else:
                params.append(f'@{ann} {nm.group(1)}')
        else:
            short = {'RequestParam':'@QP','PathVariable':'@PV','RequestBody':'@Body','RequestHeader':'@Hdr'}[ann]
            params.append(short)
    return params

def extract_endpoints(file_path):
    """Return list of (method, path, params) for the controller."""
    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
    # find class name
    cls_m = CLASS_RE.search(content)
    if not cls_m:
        return None, [], []
    cls_name = cls_m.group(1)
    # base path
    base = ''
    # look for @RequestMapping on class (not the first one in the file)
    class_mappings = []
    for m in re.finditer(r'@RequestMapping\s*\(([^)]*(?:\([^)]*\)[^)]*)*)\)', content, re.DOTALL):
        # take first one - usually class level
        args = m.group(1)
        val = re.search(r'(?:value|path)\s*=\s*"([^"]+)"', args)
        if val:
            class_mappings.append(val.group(1))
        break
    if class_mappings:
        base = class_mappings[0]
    else:
        # Handle simple form: @RequestMapping("/path")
        simple = re.search(r'@RequestMapping\s*\(\s*"([^"]+)"\s*\)', content)
        if simple:
            base = simple.group(1)
    # find each method's annotations by iterating
    endpoints = []
    # Strategy: find all method-level mapping annotations and the next method signature
    for http, regex in METHOD_MAPPING_RES:
        for m in regex.finditer(content):
            sub = m.group(1) or ''
            # If this is the class-level @RequestMapping (first occurrence), skip if it has no method
            start_pos = m.start()
            # find the next method signature: a method name and paren with content
            tail = content[start_pos:m.end()]
            # look ahead for method name and parameters until first '{' or ';'
            after = content[m.end():m.end()+4000]
            # find method name (word followed by '(')
            mname_match = re.search(r'\b(public|private|protected)\s+[\w<>\[\],\s]+?\s+(\w+)\s*\(', after)
            params = []
            if mname_match:
                # extract method param block
                paren_start = after.find('(', mname_match.end()-1)
                depth = 0
                for j in range(paren_start, len(after)):
                    if after[j] == '(':
                        depth += 1
                    elif after[j] == ')':
                        depth -= 1
                        if depth == 0:
                            params = parse_params(after[paren_start:j+1])
                            break
            full_path = (base.rstrip('/') + '/' + sub.lstrip('/')).rstrip('/') or '/'
            endpoints.append((http, sub or '/', full_path, params))
    return cls_name, base, endpoints

def main():
    modules = OrderedDict()
    total_controllers = 0
    total_endpoints = 0
    # walk modules
    for entry in sorted(os.listdir(ROOT)):
        path = os.path.join(ROOT, entry)
        if not os.path.isdir(path):
            continue
        if not entry.startswith('med-rms-'):
            continue
        # find Controller.java files
        controllers = []
        for root, dirs, files in os.walk(path):
            dirs[:] = [d for d in dirs if d != 'target']
            for fn in files:
                if fn.endswith('Controller.java'):
                    controllers.append(os.path.join(root, fn))
        if not controllers:
            continue
        modules[entry] = []
        for cpath in sorted(controllers):
            cls_name, base, eps = extract_endpoints(cpath)
            if not cls_name:
                continue
            modules[entry].append((cpath, cls_name, base, eps))
            total_controllers += 1
            total_endpoints += len(eps)
    # output
    for mod, ctrls in modules.items():
        total_eps = sum(len(c[3]) for c in ctrls)
        print(f"## {mod}  ({len(ctrls)} ctrls, {total_eps} eps)")
        for cpath, cls_name, base, eps in ctrls:
            print(f"### {cls_name} ({base})")
            if not eps:
                print("  (no mappings)")
            # Print in 2 columns for compactness
            items = []
            for http, sub, full, params in eps:
                if http == 'REQUEST':
                    continue
                pstr = ' [' + ', '.join(params) + ']' if params else ''
                items.append(f"{http} {full}{pstr}")
            # Two columns
            half = (len(items) + 1) // 2
            col_w = max((len(s) for s in items), default=10) + 2
            for i in range(half):
                left = items[i] if i < len(items) else ''
                right = items[i + half] if (i + half) < len(items) else ''
                print(f"  {left:<{col_w}}  {right}")
    print(f"## 统计")
    print(f"- 模块数: {len(modules)}")
    print(f"- Controller 总数: {total_controllers}")
    print(f"- 端点总数: {total_endpoints}")

if __name__ == '__main__':
    main()
