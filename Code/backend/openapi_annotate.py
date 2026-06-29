"""v1.29 OpenAPI x-required-permission 自动标注脚本（YAML 解析版）
解析 PermissionMatrix.java 的 225 条规则，向 OpenAPI yaml 的每个 operation
注入 x-required-permission 扩展（OpenAPI 3.x 自定义扩展以 x- 开头）。

匹配策略：
  1. 精确匹配：PermissionMatrix 路径（Ant 风格 {id}）↔ OpenAPI 路径（{id}）1:1 对齐
  2. 前缀匹配：路径 startsWith 即可
  3. 变量名归一化：{entityType}→{type}, 其余 ID 字段→{id}
  4. 路径别名：覆盖 v1.27 RBAC 归一化前的 OpenAPI 旧路径
"""
import re
import sys
import shutil
from pathlib import Path

try:
    import yaml
except ImportError:
    print('PyYAML not installed. pip install pyyaml')
    sys.exit(1)

MATRIX = Path('D:/zhutao/MED_RMS_PMS/Code/backend/med-rms-admin/src/main/java/com/zhutao/medrms/admin/security/PermissionMatrix.java')
OPENAPI = Path('D:/zhutao/MED_RMS_PMS/Detailed/03-OpenAPI/med-rms-openapi.yaml')

METHOD_MAP = {'GET':'get','POST':'post','PUT':'put','DELETE':'delete','PATCH':'patch','HEAD':'head','OPTIONS':'options'}

# v1.27 RBAC 归一化前的 OpenAPI 路径别名 → 新路径
PATH_ALIASES = {
    '/users': '/system/users',
    '/users/me': '/admin/users/me',
    '/users/me/password': '/system/users',
    '/users/{id}': '/system/users/{id}',
    '/users/{id}/status': '/system/users/{id}',
    '/roles': '/system/roles',
    '/roles/{id}': '/system/roles/{id}',
    '/roles/{id}/permissions': '/system/roles/{id}',
    '/permissions': None,
    '/organizations': None,
    '/organizations/sync': None,
    '/dicts': '/system/dicts',
    '/dicts/{code}/entries': '/system/dicts',
    '/configs': '/system/configs',
    '/configs/{key}': '/system/configs/{id}',
    '/traces': '/traceability',
    '/traces/{id}': '/traceability',
    '/traces/matrix/{projectId}':   '/traceability/matrix',
    '/traces/coverage/{projectId}': '/traceability/coverage',
    '/traces/gaps/{projectId}':     '/traceability/gaps',
    '/traces/matrix/{projectId}/export': '/traceability/matrix',
    '/soup': '/requirement/soup-components',
    '/soup/{id}': '/requirement/soup-components/{id}',
    '/soup/{id}/review': '/requirement/soup-components/{id}/renew',
    '/esign': '/esignature',
    '/esign/intent': None,
    '/esign/sign': '/esignature/sign',
    '/esign/records': '/esignature/signatures',
    '/esign/records/{entityType}/{entityId}': '/esignature/entity/{type}/{id}',
    '/esign/verify': None,
    '/esign/password': None,
    '/esign/otp/send': None,
    '/risks': '/risk',
    '/risks/{id}': '/risk/register/list',
    '/risks/{id}/status': '/risk/register/{id}',
    '/risks/{id}/analysis': '/risk/{id}/fmea',
    '/risks/{id}/controls': '/risk/{id}/control',
    '/risks/matrix/{projectId}': '/risk/matrix/list/{id}',
    '/projects/{id}/members': '/project/member/list/{id}',
    '/projects/{id}/members/{userId}': '/project/member/{id}',
    '/projects/{id}/gates': '/project/ipd-gate/list/{id}',
    '/projects/{id}/gates/{gateId}/review': '/project/ipd-gate/{id}/pass',
    '/dashboard': '/dashboard',
    '/dashboard/layout': '/dashboard',
    '/statistics/{projectId}/requirements': '/requirements',
    '/statistics/{projectId}/changes':      '/changes',
    '/statistics/{projectId}/risks':        '/risk',
    '/statistics/{projectId}/compliance':   '/compliance/audit-logs',
    '/statistics/{projectId}/trends':       None,
    '/reports/export':           '/reports/generate',
    '/notifications':            '/notifications/unread',
    '/notifications/{id}/read':  '/notifications/{id}/read',
    '/notifications/unread-count': '/notifications/unread/count',
    '/baselines/{id}/lock':      '/baselines/{id}',
    '/baselines/{id}/unlock':    '/baselines/{id}',
    '/baselines/{id}/compare/{targetId}': '/baselines/compare',
    '/regulations':              '/compliance/regulations',
    '/problem-reports':          '/compliance/problem-reports',
    '/problem-reports/{id}':     '/compliance/problem-reports',
    '/problem-reports/{id}/status':    '/compliance/problem-reports/{id}/status',
    '/problem-reports/{id}/corrections': None,
    '/iec62304/checklist/{projectId}':          '/compliance/iec62304/checklist/{id}',
    '/iec62304/checklist/{projectId}/assess':   '/compliance/iec62304/checklist/{id}/assess',
    '/requirements/{id}/status':   '/requirements',
    '/requirements/{id}/submit':   None,
    '/requirements/import':        '/requirements',
    # OpenAPI 中的实际路径（无 /compliance 前缀）→ Matrix 路径
    '/audit-logs':                                   '/compliance/audit-logs',
    '/audit-logs/{entityType}/{entityId}':           '/compliance/audit-logs/entity/{type}/{id}',
    '/audit-logs/verify':                            '/compliance/audit-logs/verify',
    '/safety-classifications':                       '/compliance/safety-classifications',
    '/permissions':                                  None,  # 公开查询
    '/organizations':                                None,  # 公开查询
    '/organizations/sync':                           None,  # 仅 admin
    '/baselines/{id}/lock':                          '/baselines/{id}',
    '/baselines/{id}/unlock':                        '/baselines/{id}',
    '/baselines/{id}/compare/{targetId}':            '/baselines/compare',
    '/problem-reports/{id}/corrections':             '/compliance/problem-reports/{id}',
    '/dashboard/layout':                             '/dashboard',
    '/statistics/{projectId}/trends':                None,  # 未实现
    # 自身账号管理
    '/users/me':                                     '/system/users/me',
    '/users/me/password':                            '/system/users/me/password',
    # PATCH 状态变更（matrix 中无对应，按业务推断或保留未匹配）
    '/changes/{id}/status':                          '/changes/{id}/status',
    '/baselines/{id}/lock':                          '/baselines/{id}/lock',
    '/baselines/{id}/unlock':                        '/baselines/{id}/unlock',
    # eSign 未实现
    '/esign/intent':                                 None,
    '/esign/verify':                                 None,
    '/esign/password':                               None,
    '/esign/otp/send':                               None,
}

def parse_matrix():
    text = MATRIX.read_text(encoding='utf-8', errors='ignore')
    exact = []
    prefix = []
    for m, p, c in re.findall(r'addExact\(HttpMethod\.(\w+),\s*"([^"]+)",\s*"([^"]+)"\)', text):
        exact.append((METHOD_MAP.get(m, m.lower()), p, c))
    for m, p, c in re.findall(r'addPrefix\(HttpMethod\.(\w+),\s*"([^"]+)",\s*"([^"]+)"\)', text):
        prefix.append((METHOD_MAP.get(m, m.lower()), p, c))
    exact.sort(key=lambda r: len(r[1]), reverse=True)
    return exact, prefix

def normalize_path(p):
    """OpenAPI 路径变量名标准化：{entityType}→{type}, 其余 ID 字段→{id}"""
    p = re.sub(r'\{entityType\}', '{type}', p)
    p = re.sub(r'\{gateType\}',  '{type}', p)
    p = re.sub(r'\{changeType\}','{type}', p)
    for name in ('entityId','userId','signerId','gateId','changeId','reportId','baselineId','requirementId',
                 'projectId','reviewerId','approverId','operatorId','parentId','childId',
                 'targetId','signerUserId','fromUserId','toUserId','configId','dictId','keyId'):
        p = re.sub(r'\{' + name + r'\}', '{id}', p)
    return p

def find_perm(exact_rules, prefix_rules, method, path, _depth=0):
    if _depth > 2:
        return None
    norm_path = normalize_path(path)
    for m, p, c in exact_rules:
        if m == method and (p == path or p == norm_path):
            return c
    for m, p, c in prefix_rules:
        if m == method and (path.startswith(p) or norm_path.startswith(p)):
            return c
    alias = PATH_ALIASES.get(path)
    if alias is not None and alias != path:
        return find_perm(exact_rules, prefix_rules, method, alias, _depth + 1)
    if method == 'patch':
        return find_perm(exact_rules, prefix_rules, 'put', path, _depth + 1)
    return None

def annotate_yaml(data, exact_rules, prefix_rules):
    """递归走 OpenAPI 数据结构，给每个 operation 注入 x-required-permission"""
    paths = data.get('paths', {})
    annotated = 0
    skipped = 0
    no_match = []
    perm_log = []
    for path, path_item in paths.items():
        if not isinstance(path_item, dict):
            continue
        for method, operation in path_item.items():
            if method not in ('get', 'post', 'put', 'delete', 'patch', 'head', 'options'):
                continue
            if not isinstance(operation, dict):
                continue
            if 'x-required-permission' in operation:
                skipped += 1
                continue
            perm = find_perm(exact_rules, prefix_rules, method, path)
            if perm is None:
                no_match.append((method, path))
                continue
            operation['x-required-permission'] = perm
            annotated += 1
            perm_log.append((method, path, perm))
    return annotated, skipped, no_match, perm_log

def main():
    exact, prefix = parse_matrix()
    print(f'=== OpenAPI 权限标注（YAML 解析版）===')
    print(f'PermissionMatrix: {len(exact)} exact + {len(prefix)} prefix = {len(exact)+len(prefix)}')

    with open(OPENAPI, 'r', encoding='utf-8') as f:
        data = yaml.safe_load(f)

    annotated, skipped, no_match, log = annotate_yaml(data, exact, prefix)

    print(f'\n结果：')
    print(f'  新增标注: {annotated}')
    print(f'  已存在跳过: {skipped}')
    print(f'  无匹配规则: {len(no_match)}')

    if no_match:
        print(f'\n无匹配的 operation（可能是公开端点或 RBAC 未覆盖）:')
        for m, p in no_match:
            print(f'  {m:6s} {p}')

    print(f'\n前 20 条标注:')
    for m, p, c in log[:20]:
        print(f'  {m:6s} {p:55s} -> {c}')

    # 备份
    backup = OPENAPI.with_suffix('.yaml.bak')
    if not backup.exists():
        shutil.copy(OPENAPI, backup)
    print(f'\n备份: {backup}')

    # 写回（保留原格式风格）
    with open(OPENAPI, 'w', encoding='utf-8') as f:
        f.write('# Med-RMS OpenAPI Specification v1.1\n')
        f.write('# 日期：2026-05-22\n')
        f.write('# 基线：概要设计 v1.2 + 系统架构 v1.1\n')
        f.write('# v1.29：自动注入 x-required-permission 标注（来自 PermissionMatrix 225 条规则）\n\n')
        yaml.dump(data, f, allow_unicode=True, sort_keys=False, default_flow_style=False, width=120)

    # 校验
    after_text = OPENAPI.read_text(encoding='utf-8', errors='ignore')
    after_count = after_text.count('x-required-permission:')
    print(f'\n标注后总 x-required-permission 字段数: {after_count}')

if __name__ == '__main__':
    main()
