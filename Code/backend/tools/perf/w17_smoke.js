// k6 W17 关键 API 性能压测
// 验证 v1.56 后修改的代码无性能退化
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '5s', target: 10 },   // 预热
    { duration: '10s', target: 30 },  // 30 VU
    { duration: '10s', target: 50 },  // 50 VU
    { duration: '5s', target: 0 },     // 冷却
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = 'http://localhost:8080';

export function setup() {
  const loginRes = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    username: 'admin', password: 'admin123'
  }), { headers: { 'Content-Type': 'application/json' } });
  const token = loginRes.json('data.accessToken');
  if (!token) {
    throw new Error(`Login failed: ${loginRes.status} ${loginRes.body}`);
  }
  return { token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  // 1) 登录态主页
  const r1 = http.get(`${BASE}/api/dashboard/view/requirements?projectId=1`, { headers });
  check(r1, { 'dashboard 200': (r) => r.status === 200 });

  // 2) 需求列表（638 条真实数据）
  const r2 = http.get(`${BASE}/api/requirements?size=20`, { headers });
  check(r2, { 'requirements 200': (r) => r.status === 200 });

  // 3) 项目列表
  const r3 = http.get(`${BASE}/api/projects?page=0&size=20`, { headers });
  check(r3, { 'projects 200': (r) => r.status === 200 });

  // 4) 追溯图（W17 优化后应 < 30K 响应）
  const r4 = http.get(`${BASE}/api/trace-graph/project/1`, { headers });
  check(r4, { 'trace-graph 200': (r) => r.status === 200 });

  // 5) 风险列表
  const r5 = http.get(`${BASE}/api/risk/register/list`, { headers });
  check(r5, { 'risk 200': (r) => r.status === 200 });

  sleep(0.5);
}
