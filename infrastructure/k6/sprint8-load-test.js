/**
 * Sprint 8 — Full Load Test (S8-QA02)
 *
 * Scenarios:
 *   heavy_500vu   — 500 VU ramp-up + 30 min sustained (API mix)
 *   baseline_200vu — 200 VU sustained 30 min (stability gate)
 *   mobile_api     — mobile dashboard + alerts endpoints
 *   ch_analytics   — ClickHouse HA analytics queries
 *
 * SLA Thresholds (Sprint 8):
 *   SLA-002: Dashboard API p95 < 3s
 *   SLA-004: Kafka throughput > 1,667 msg/s  (measured separately via Kafka metrics)
 *   SLA-005: API error rate < 0.01% over 20 min
 *   SLA-006: ClickHouse query p95 < 1,000ms
 *   SLA-007: Cross-building query p95 < 2,000ms
 *   SLA-008: Mobile API p95 < 100ms
 *
 * Usage:
 *   # Full 30-min run (CI gate):
 *   k6 run infrastructure/k6/sprint8-load-test.js
 *
 *   # Quick smoke (5% load, 5 min):
 *   K6_QUICK=true k6 run infrastructure/k6/sprint8-load-test.js
 *
 *   # Custom backend:
 *   K6_BACKEND_URL=http://staging:8080 k6 run infrastructure/k6/sprint8-load-test.js
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ─── Config ──────────────────────────────────────────────────────────────────
const BACKEND_URL  = __ENV.K6_BACKEND_URL  || 'http://localhost:8080';
const FRONTEND_URL = __ENV.K6_FRONTEND_URL || 'http://localhost:3000';
const QUICK        = __ENV.K6_QUICK === 'true';

// Quick mode: 5% VU, 25% duration
const vuFactor  = QUICK ? 0.05 : 1;
const durFactor = QUICK ? 0.25 : 1;

const SUSTAINED_DURATION = QUICK ? '2m' : '30m';
const RAMP_UP_DURATION   = QUICK ? '30s' : '3m';
const RAMP_DOWN_DURATION = QUICK ? '15s' : '2m';

// ─── Custom metrics ──────────────────────────────────────────────────────────
const errorRate            = new Rate('errors');
const dashboardLatency     = new Trend('dashboard_api_latency', true);
const mobileApiLatency     = new Trend('mobile_api_latency', true);
const chAnalyticsLatency   = new Trend('ch_analytics_latency', true);
const crossBuildingLatency = new Trend('cross_building_latency', true);
const requestsTotal        = new Counter('requests_total');

// ─── Scenarios ───────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    // SLA-005 + SLA-002: 500 VU mixed API load sustained 30 min
    heavy_500vu: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP_DURATION,   target: Math.round(500 * vuFactor) },
        { duration: SUSTAINED_DURATION, target: Math.round(500 * vuFactor) },
        { duration: RAMP_DOWN_DURATION, target: 0 },
      ],
      tags: { scenario: 'heavy_500vu' },
      exec: 'mixedApiLoad',
    },

    // SLA-005: 200 VU stability baseline — separate from 500 VU peak
    baseline_200vu: {
      executor: 'constant-vus',
      vus: Math.round(200 * vuFactor),
      duration: `${QUICK ? '2m' : '30m'}`,
      startTime: QUICK ? '0s' : '5m',  // Start after 500 VU is warm
      tags: { scenario: 'baseline_200vu' },
      exec: 'baselineLoad',
    },

    // SLA-008: Mobile API p95 < 100ms — Sprint 8 new endpoints
    mobile_api: {
      executor: 'constant-vus',
      vus: Math.round(50 * vuFactor),
      duration: `${QUICK ? '1m' : '10m'}`,
      startTime: QUICK ? '0s' : '5m',
      tags: { scenario: 'mobile' },
      exec: 'mobileLoad',
    },

    // SLA-006 + SLA-007: ClickHouse HA analytics
    ch_analytics: {
      executor: 'constant-vus',
      vus: Math.round(20 * vuFactor),
      duration: `${QUICK ? '1m' : '15m'}`,
      startTime: QUICK ? '0s' : '5m',
      tags: { scenario: 'ch_analytics' },
      exec: 'chAnalyticsLoad',
    },
  },

  thresholds: {
    // SLA-002: Dashboard API p95 < 3s
    'dashboard_api_latency': ['p(95)<3000'],

    // SLA-005: Error rate < 0.01%
    'errors': ['rate<0.0001'],

    // SLA-006: ClickHouse query p95 < 1,000ms
    'ch_analytics_latency': ['p(95)<1000'],

    // SLA-007: Cross-building query p95 < 2s
    'cross_building_latency': ['p(95)<2000'],

    // SLA-008: Mobile API p95 < 100ms
    'mobile_api_latency': ['p(95)<100'],

    // Global HTTP checks
    'http_req_duration{scenario:heavy_500vu}':   ['p(95)<2000'],
    'http_req_duration{scenario:baseline_200vu}': ['p(95)<500'],
    'http_req_failed': ['rate<0.001'],
  },

  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ─── Auth ────────────────────────────────────────────────────────────────────
// VU-local cached token (each VU has isolated JS context in k6)
let _token = '';
let _tokenExpiry = 0;

function getToken() {
  const now = Date.now();
  if (_token && now < _tokenExpiry - 60_000) return _token;

  const res = http.post(
    `${BACKEND_URL}/api/v1/auth/login`,
    JSON.stringify({ username: 'admin', password: 'admin_Dev#2026!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'auth_login' } }
  );
  if (res.status === 200) {
    const body = res.json();
    _token = body.accessToken || body.token || body.access_token || '';
    _tokenExpiry = now + ((body.expiresIn || 900) * 1000);
  }
  return _token;
}

function authHeaders(extra = {}) {
  return {
    headers: {
      Authorization: `Bearer ${getToken()}`,
      'Content-Type': 'application/json',
      'X-Tenant-ID': 'default',
      ...extra,
    },
  };
}

// ─── Scenario: Mixed API load (500 VU) ───────────────────────────────────────
export function mixedApiLoad() {
  const opts = authHeaders();

  group('dashboard', () => {
    const r = http.get(`${BACKEND_URL}/api/v1/dashboard`, { ...opts, tags: { name: 'dashboard' } });
    check(r, { 'dashboard 2xx': (res) => res.status >= 200 && res.status < 300 });
    dashboardLatency.add(r.timings.duration);
    errorRate.add(r.status < 200 || r.status >= 300);
    requestsTotal.add(1);
  });

  group('alerts', () => {
    const r = http.get(`${BACKEND_URL}/api/v1/alerts?page=0&size=20`, { ...opts, tags: { name: 'alerts' } });
    check(r, { 'alerts 2xx': (res) => res.status >= 200 && res.status < 300 });
    errorRate.add(r.status < 200 || r.status >= 300);
    requestsTotal.add(1);
  });

  group('environment', () => {
    const r = http.get(`${BACKEND_URL}/api/v1/environment/sensors?page=0&size=10`, { ...opts, tags: { name: 'env_sensors' } });
    check(r, { 'env_sensors 2xx': (res) => res.status >= 200 && res.status < 300 });
    errorRate.add(r.status < 200 || r.status >= 300);
    requestsTotal.add(1);
  });

  sleep(1 + Math.random() * 2);
}

// ─── Scenario: Baseline load (200 VU) ────────────────────────────────────────
export function baselineLoad() {
  const opts = authHeaders();

  const endpoints = [
    `/api/v1/dashboard`,
    `/api/v1/alerts?page=0&size=10`,
    `/api/v1/esg/summary?period=quarterly&year=2026&quarter=1`,
    `/api/v1/environment/sensors?page=0&size=10`,
    `/api/v1/buildings`,
  ];

  const url = endpoints[Math.floor(Math.random() * endpoints.length)];
  const r = http.get(`${BACKEND_URL}${url}`, { ...opts, tags: { name: 'baseline' } });
  check(r, { 'baseline 2xx': (res) => res.status >= 200 && res.status < 300 });
  errorRate.add(r.status < 200 || r.status >= 300);
  requestsTotal.add(1);

  sleep(0.5 + Math.random());
}

// ─── Scenario: Mobile API (SLA-008, p95 < 100ms) ─────────────────────────────
export function mobileLoad() {
  const opts = authHeaders();

  group('mobile_dashboard', () => {
    const r = http.get(`${BACKEND_URL}/api/v1/dashboard`, { ...opts, tags: { name: 'mobile_dashboard' } });
    check(r, { 'mobile dashboard 2xx': (res) => res.status >= 200 && res.status < 300 });
    mobileApiLatency.add(r.timings.duration);
    errorRate.add(r.status < 200 || r.status >= 300);
    requestsTotal.add(1);
  });

  group('mobile_alerts', () => {
    const r = http.get(`${BACKEND_URL}/api/v1/alerts?page=0&size=20&sort=severity`, { ...opts, tags: { name: 'mobile_alerts' } });
    check(r, { 'mobile alerts 2xx': (res) => res.status >= 200 && res.status < 300 });
    mobileApiLatency.add(r.timings.duration);
    errorRate.add(r.status < 200 || r.status >= 300);
    requestsTotal.add(1);
  });

  group('building_safety', () => {
    const r = http.get(`${BACKEND_URL}/api/v1/buildings/B001/safety`, { ...opts, tags: { name: 'building_safety' } });
    // 200 or 404 (building may not exist in test data) — both count as non-error
    check(r, { 'building safety non-5xx': (res) => res.status < 500 });
    mobileApiLatency.add(r.timings.duration);
    errorRate.add(r.status >= 500);
    requestsTotal.add(1);
  });

  sleep(0.3 + Math.random() * 0.5);
}

// ─── Scenario: ClickHouse HA analytics (SLA-006 + SLA-007) ───────────────────
export function chAnalyticsLoad() {
  const opts = authHeaders();

  group('ch_analytics_single', () => {
    const r = http.get(
      `${BACKEND_URL}/api/v1/analytics/energy?buildingId=B001&period=7d`,
      { ...opts, tags: { name: 'ch_single' } }
    );
    check(r, { 'ch analytics non-5xx': (res) => res.status < 500 });
    chAnalyticsLatency.add(r.timings.duration);
    errorRate.add(r.status >= 500);
    requestsTotal.add(1);
  });

  group('ch_cross_building', () => {
    const r = http.get(
      `${BACKEND_URL}/api/v1/analytics/energy/summary?period=30d`,
      { ...opts, tags: { name: 'ch_cross_building' } }
    );
    check(r, { 'ch cross-building non-5xx': (res) => res.status < 500 });
    crossBuildingLatency.add(r.timings.duration);
    errorRate.add(r.status >= 500);
    requestsTotal.add(1);
  });

  sleep(2 + Math.random() * 3);
}

// ─── Summary output ──────────────────────────────────────────────────────────
export function handleSummary(data) {
  const report = {
    sprint: 'MVP3-8',
    story: 'S8-QA02',
    timestamp: new Date().toISOString(),
    sla: {
      'SLA-002_dashboard_p95_ms': data.metrics.dashboard_api_latency?.values['p(95)'] ?? null,
      'SLA-005_error_rate':       data.metrics.errors?.values.rate ?? null,
      'SLA-006_ch_p95_ms':        data.metrics.ch_analytics_latency?.values['p(95)'] ?? null,
      'SLA-007_cross_p95_ms':     data.metrics.cross_building_latency?.values['p(95)'] ?? null,
      'SLA-008_mobile_p95_ms':    data.metrics.mobile_api_latency?.values['p(95)'] ?? null,
    },
    thresholds_passed: Object.values(data.metrics)
      .every(m => !m.thresholds || Object.values(m.thresholds).every(t => t.ok)),
    total_requests: data.metrics.requests_total?.values.count ?? 0,
    full_data: data,
  };

  return {
    stdout: JSON.stringify({ sla: report.sla, thresholds_passed: report.thresholds_passed, total_requests: report.total_requests }, null, 2),
    'perf/sprint8-k6-results.json': JSON.stringify(report, null, 2),
  };
}
