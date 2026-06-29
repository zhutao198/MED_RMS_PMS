// k6 性能基线 3：变更影响评估
// 目标：P95 ≤ 10s（PRD 性能验收 US-2 项目经理-变更影响）
// 用法：tools\bin\run.cmd k6 run perf_scripts\baseline-impact-assessment.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const impactLatency = new Trend('impact_latency', true);
const impactError = new Rate('impact_errors');

export const options = {
    stages: [
        { duration: '20s', target: 3 },
        { duration: '1m', target: 10 },
        { duration: '1m', target: 30 },
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        // PRD 性能验收：影响评估 P95 ≤ 10s
        'http_req_duration{name:impact}': ['p(95)<10000'],
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

export function setup() {
    const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ username: 'admin', password: 'admin123' }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    if (loginRes.status !== 200) {
        throw new Error(`登录失败: ${loginRes.status}`);
    }
    return { token: JSON.parse(loginRes.body).data.token };
}

export default function (data) {
    const headers = {
        'Authorization': `Bearer ${data.token}`,
        'Content-Type': 'application/json',
    };

    // 拉取变更列表（取第一条）
    const listRes = http.get(`${BASE_URL}/changes/list`, { headers });
    if (listRes.status !== 200) {
        impactError.add(true);
        return;
    }

    let changeId;
    try {
        const changes = JSON.parse(listRes.body).data;
        if (Array.isArray(changes) && changes.length > 0) {
            changeId = changes[0].id;
        } else {
            sleep(1);
            return;
        }
    } catch (e) {
        impactError.add(true);
        return;
    }

    // 影响评估（GET 影响列表）
    const start = Date.now();
    const res = http.get(`${BASE_URL}/changes/${changeId}/impacts`, {
        headers,
        tags: { name: 'impact' },
    });
    impactLatency.add(Date.now() - start);
    impactError.add(res.status !== 200);

    check(res, {
        '影响评估 200': (r) => r.status === 200,
    });

    sleep(2);
}
