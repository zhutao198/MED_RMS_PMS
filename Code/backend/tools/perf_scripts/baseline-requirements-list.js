// k6 性能基线 1：需求列表 5000 条
// 目标：P95 ≤ 3s（PRD 性能验收 US-7）
// 用法：tools\bin\run.cmd k6 run perf_scripts\baseline-requirements-list.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 自定义指标
const listLatency = new Trend('req_list_latency', true);
const listError = new Rate('req_list_errors');

export const options = {
    stages: [
        { duration: '30s', target: 10 },   // 预热
        { duration: '1m', target: 50 },    // 50 并发稳态
        { duration: '1m', target: 100 },   // 100 并发峰值
        { duration: '30s', target: 0 },    // 退压
    ],
    thresholds: {
        // PRD 性能验收：列表加载 P95 ≤ 3s
        'http_req_duration{name:list}': ['p(95)<3000'],
        'http_req_failed{name:list}': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

export function setup() {
    // 登录获取 token
    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ username: 'admin', password: 'admin123' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status !== 200) {
        throw new Error(`登录失败: ${loginRes.status} - ${loginRes.body}`);
    }
    const token = JSON.parse(loginRes.body).data.token;
    return { token };
}

export default function (data) {
    const headers = {
        'Authorization': `Bearer ${data.token}`,
        'Content-Type': 'application/json',
    };

    // 需求列表
    const start = Date.now();
    const res = http.get(`${BASE_URL}/requirements?page=1&size=20`, {
        headers,
        tags: { name: 'list' },
    });
    listLatency.add(Date.now() - start);
    listError.add(res.status !== 200);

    check(res, {
        '需求列表 200': (r) => r.status === 200,
        '有 records 字段': (r) => {
            try {
                return JSON.parse(r.body).data && JSON.parse(r.body).data.records;
            } catch (e) {
                return false;
            }
        },
    });

    sleep(0.5);
}
