// k6 W19 长时压测（5 分钟 × 100 VU）
import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '60s', target: 100 },
    { duration: '120s', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
    http_req_failed: ['rate<0.01'],
  },
};

const BASE = 'http://localhost:8080';

export function setup() {
  const loginRes = http.post(`${BASE}/api/auth/login`, JSON.stringify({
    username: 'admin', password: 'admin123'
  }), { headers: { 'Content-Type': 'application/json' } });
  const token = loginRes.json('data.accessToken');
  if (!token) throw new Error(`Login failed: ${loginRes.status}`);
  return { token };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  http.get(`${BASE}/api/dashboard/view/requirements?projectId=1`, { headers });
  http.get(`${BASE}/api/requirements?size=20`, { headers });
  http.get(`${BASE}/api/projects?page=0&size=20`, { headers });
  http.get(`${BASE}/api/trace-graph/project/1`, { headers });
  http.get(`${BASE}/api/risk/register/list`, { headers });
  sleep(Math.random() * 0.5 + 0.3);
}
