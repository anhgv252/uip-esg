/**
 * S4-05 API Load Test — k6 script
 * Tests API p95 < 200ms with 50 concurrent users.
 *
 * Usage:
 *   k6 run api_load_test.js
 *   k6 run --vus 50 --duration 60s api_load_test.js
 *
 * Environment variables:
 *   BASE_URL  — Backend URL (default: http://localhost:8080)
 *   ADMIN_USER — Admin username (default: admin)
 *   ADMIN_PASS — Admin password (default: admin_Dev#2026!)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const ADMIN_USER = __ENV.ADMIN_USER || "admin";
const ADMIN_PASS = __ENV.ADMIN_PASS || "admin_Dev#2026!";

// Custom metrics
const errorRate = new Rate("errors");
const apiLatency = new Trend("api_latency", true);

export const options = {
  stages: [
    { duration: "10s", target: 10 },   // ramp-up to 10
    { duration: "10s", target: 30 },   // ramp-up to 30
    { duration: "10s", target: 50 },   // ramp-up to 50
    { duration: "60s", target: 50 },   // sustain 50 for 60s
    { duration: "10s", target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ["p(95)<200"],
    errors: ["rate<0.01"],
  },
  // Tag all requests for per-endpoint analysis
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "max"],
};

export function setup() {
  // Login once to get JWT token
  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }),
    { headers: { "Content-Type": "application/json" } }
  );

  check(loginRes, {
    "login successful": (r) => r.status === 200,
  });

  if (loginRes.status !== 200) {
    console.error(`Login failed: ${loginRes.status} ${loginRes.body}`);
    return { token: null };
  }

  const body = loginRes.json();
  return { token: body.accessToken };
}

export default function (data) {
  if (!data.token) {
    errorRate.add(1);
    return;
  }

  const headers = {
    Authorization: `Bearer ${data.token}`,
    "Content-Type": "application/json",
  };

  // Rotate through 5 API endpoints
  const scenario = __ITER % 5;

  switch (scenario) {
    case 0:
      testHealth();
      break;
    case 1:
      testSensors(headers);
      break;
    case 2:
      testAqiCurrent(headers);
      break;
    case 3:
      testAlerts(headers);
      break;
    case 4:
      testEsgSummary(headers);
      break;
  }

  sleep(0.3);
}

function testSensors(headers) {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/v1/environment/sensors`, { headers });
  apiLatency.add(Date.now() - start);

  check(res, { "sensors 200": (r) => r.status === 200 });
  errorRate.add(res.status >= 500 ? 1 : 0);
}

function testAqiCurrent(headers) {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/v1/environment/aqi/current`, { headers });
  apiLatency.add(Date.now() - start);

  check(res, { "aqi/current 200": (r) => r.status === 200 });
  errorRate.add(res.status >= 500 ? 1 : 0);
}

function testEsgSummary(headers) {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/v1/esg/summary?period=2026-Q1`, { headers });
  apiLatency.add(Date.now() - start);

  // 404 is expected when no ESG data exists — not counted as error
  errorRate.add(res.status >= 500);
}

function testAlerts(headers) {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/v1/alerts?limit=20`, { headers });
  apiLatency.add(Date.now() - start);

  check(res, { "alerts 200": (r) => r.status === 200 });
  errorRate.add(res.status >= 500 ? 1 : 0);
}

function testHealth() {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/v1/health`);
  apiLatency.add(Date.now() - start);

  check(res, { "health 200": (r) => r.status === 200 });
  errorRate.add(res.status >= 500 ? 1 : 0);
}

export function handleSummary(data) {
  const result = {
    test: "API Load Test (k6)",
    timestamp: new Date().toISOString(),
    scenarios: {
      vus: data.metrics.vus?.values?.max || 0,
      duration: data.state?.testRunDurationMs
        ? `${(data.state.testRunDurationMs / 1000).toFixed(1)}s`
        : "unknown",
    },
    thresholds: {
      http_req_duration_p95:
        data.metrics.http_req_duration?.values?.["p(95)"]?.toFixed(2) || "N/A",
      errors_rate:
        data.metrics.errors?.values?.rate?.toFixed(4) || "N/A",
      http_req_failed_rate:
        data.metrics.http_req_failed?.values?.rate?.toFixed(4) || "N/A",
    },
    requests: {
      total: data.metrics.http_reqs?.values?.count || 0,
      rps: data.metrics.http_reqs?.values?.rate?.toFixed(1) || "N/A",
    },
    status:
      data.metrics.http_req_duration?.values?.["p(95)"] < 200 &&
      (data.metrics.errors?.values?.rate || 0) < 0.01
        ? "PASS"
        : "FAIL",
  };

  console.log("\n=== k6 API Load Test Results ===");
  console.log(`VUs: ${result.scenarios.vus}`);
  console.log(`Total requests: ${result.requests.total}`);
  console.log(`RPS: ${result.requests.rps}`);
  console.log(`p95 latency: ${result.thresholds.http_req_duration_p95}ms`);
  console.log(`Error rate: ${result.thresholds.errors_rate}`);
  console.log(`HTTP fail rate: ${result.thresholds.http_req_failed_rate}`);
  console.log(`Status: ${result.status}`);

  return {
    "../../docs/reports/performance/api-load-results.json": JSON.stringify(result, null, 2),
    stdout: "",
  };
}
