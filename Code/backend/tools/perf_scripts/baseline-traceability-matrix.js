// k6 性能基线 2：追溯矩阵生成
// 目标：P95 ≤ 5s（PRD US-3 架构师 L2→L3 拆解场景）
// 用法：tools\bin\run.cmd k6 run perf_scripts\baseline-traceability-matrix.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const matrixLatency = new Trend('matrix_latency', true);
const matrixError = new Rate('matrix_errors');

export const options = {
    stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        // PRD 性能验收：追溯矩阵生成 P95 ≤ 5s
        'http_req_duration{name:matrix}': ['p(95)<5000'],
        'http_req_duration{name:coverage}': ['p(95)<3000'],
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

    // 矩阵
    const mStart = Date.now();
    const matrixRes = http.get(`${BASE_URL}/traceability/matrix?projectId=1`, {
        headers,
        tags: { name: 'matrix' },
    });
    matrixLatency.add(Date.now() - mStart);
    matrixError.add(matrixRes.status !== 200);

    check(matrixRes, {
        '矩阵 200': (r) => r.status === 200,
    });

    // 覆盖率
    const cStart = Date.now();
    const covRes = http.get(`${BASE_URL}/traceability/coverage?projectId=1`, {
        headers,
        tags: { name: 'coverage' },
    });
    matrixLatency.add(Date.now() - cStart);

    check(covRes, {
        '覆盖率 200': (r) => r.status === 200,
        '有 overall 字段': (r) => {
            try {
                return JSON.parse(r.body).data && JSON.parse(r.body).data.overall !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    sleep(1);
}
