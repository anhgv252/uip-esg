/**
 * Sprint 7 — SLA Gate Performance Tests (UPDATED for k6 v1.x)
 *
 * Architecture:
 *   - Backend (HMAC auth): http://localhost:8080
 *   - Kong API Gateway:    http://localhost:8000 (only /api/v1/analytics)
 *   - Frontend (SPA):      http://localhost:3000
 *
 * Usage:
 *   K6_QUICK=true k6 run infrastructure/k6/sla-gate.js
 *   k6 run infrastructure/k6/sla-gate.js
 *
 * Scenarios:
 *   - frontend_load:   SLA-004 — Frontend initial load <3s p95
 *   - backend_api:     SLA-005 — Backend API p99 <100ms
 *   - alerts_500vu:    SLA-007 — Alerts API 500 VU stable
 *   - buildings_200vu: SLA-008 — Buildings API 200 VU stable
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BACKEND_URL = __ENV.K6_BACKEND_URL || 'http://localhost:8080';
const FRONTEND_URL = __ENV.K6_FRONTEND_URL || 'http://localhost:3000';
const QUICK = __ENV.K6_QUICK === 'true';

const errorRate = new Rate('errors');
const apiLatency = new Trend('api_latency', true);

// Scale factors for quick mode
const vuScale = QUICK ? 0.1 : 1;
const durScale = QUICK ? 0.25 : 1;

export const options = {
  scenarios: {
    // SLA-004: Frontend initial load <3s p95
    frontend_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${Math.round(30 * durScale)}s`, target: Math.round(50 * vuScale) },
        { duration: `${Math.round(60 * durScale)}s`, target: Math.round(50 * vuScale) },
        { duration: `${Math.round(10 * durScale)}s`, target: 0 },
      ],
      tags: { scenario: 'frontend' },
      exec: 'frontendLoad',
    },

    // SLA-005: Backend API p99 <100ms (alerts endpoint)
    backend_api: {
      executor: 'constant-vus',
      vus: Math.round(100 * vuScale),
      duration: `${Math.round(60 * durScale)}s`,
      tags: { scenario: 'backend_api' },
      exec: 'backendApi',
    },

    // SLA-007: Alerts API 500 VU
    alerts_500vu: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${Math.round(30 * durScale)}s`, target: Math.round(500 * vuScale) },
        { duration: `${Math.round(120 * durScale)}s`, target: Math.round(500 * vuScale) },
        { duration: `${Math.round(30 * durScale)}s`, target: 0 },
      ],
      tags: { scenario: 'alerts_heavy' },
      exec: 'alertsHeavy',
    },

    // SLA-008: Buildings API 200 VU
    buildings_200vu: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${Math.round(20 * durScale)}s`, target: Math.round(200 * vuScale) },
        { duration: `${Math.round(60 * durScale)}s`, target: Math.round(200 * vuScale) },
        { duration: `${Math.round(10 * durScale)}s`, target: 0 },
      ],
      tags: { scenario: 'buildings' },
      exec: 'buildingsLoad',
    },
  },

  // k6 v1.x: thresholds at top level, with tag filters
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    'http_req_duration{scenario:backend_api}': ['p(99)<100'],
    'http_req_duration{scenario:alerts_heavy}': ['p(95)<2000'],
    'http_req_duration{scenario:buildings}': ['p(95)<3000'],
    errors: ['rate<0.01'],
  },

  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// --- Auth helper — HMAC login via backend ---
let cachedToken = '';
let tokenExpiry = 0;

function getAuthHeaders() {
  const now = Date.now();
  if (cachedToken && now < tokenExpiry - 60000) {
    return {
      headers: {
        Authorization: `Bearer ${cachedToken}`,
        'Content-Type': 'application/json',
        'X-Tenant-ID': 'default',
      },
    };
  }

  const loginRes = http.post(
    `${BACKEND_URL}/api/v1/auth/login`,
    JSON.stringify({ username: 'admin', password: 'admin_Dev#2026!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const body = loginRes.json();
  cachedToken = body.accessToken || body.token || body.access_token || '';
  tokenExpiry = now + ((body.expiresIn || 900) * 1000);

  return {
    headers: {
      Authorization: `Bearer ${cachedToken}`,
      'Content-Type': 'application/json',
      'X-Tenant-ID': 'default',
    },
  };
}

// --- Scenario: Frontend Load (SLA-004) ---
export function frontendLoad() {
  const res = http.get(`${FRONTEND_URL}/`);
  const ok = check(res, {
    'frontend: status 200': (r) => r.status === 200,
    'frontend: has HTML': (r) => r.body && r.body.length > 100,
  });
  errorRate.add(!ok);
  apiLatency.add(res.timings.duration);
  sleep(1);
}

// --- Scenario: Backend API (SLA-005) ---
export function backendApi() {
  const auth = getAuthHeaders();
  const res = http.get(`${BACKEND_URL}/api/v1/alerts?page=0&size=10`, auth);
  const ok = check(res, {
    'backend api: status 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  errorRate.add(!ok);
  apiLatency.add(res.timings.duration);
  sleep(0.5);
}

// --- Scenario: Alerts Heavy 500 VU (SLA-007) ---
export function alertsHeavy() {
  const auth = getAuthHeaders();
  const res = http.get(`${BACKEND_URL}/api/v1/alerts?page=0&size=20`, auth);
  const ok = check(res, {
    'alerts heavy: status 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  errorRate.add(!ok);
  apiLatency.add(res.timings.duration);
  sleep(2);
}

// --- Scenario: Buildings 200 VU (SLA-008) ---
export function buildingsLoad() {
  const auth = getAuthHeaders();
  const res = http.get(`${BACKEND_URL}/api/v1/buildings`, auth);
  const ok = check(res, {
    'buildings: status 2xx': (r) => r.status >= 200 && r.status < 300,
  });
  errorRate.add(!ok);
  apiLatency.add(res.timings.duration);
  sleep(2);
}
