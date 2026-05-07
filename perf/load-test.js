import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const sensorLatency = new Trend('sensor_latency', true);
const esgLatency = new Trend('esg_summary_latency', true);
const alertLatency = new Trend('alert_latency', true);
const healthLatency = new Trend('health_latency', true);

export const options = {
  stages: [
    { duration: '30s', target: 100 },
    { duration: '60s', target: 100 },
    { duration: '30s', target: 500 },
    { duration: '60s', target: 500 },
    { duration: '30s', target: 1000 },
    { duration: '60s', target: 1000 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.1'],
    sensor_latency: ['p(95)<200'],
    esg_summary_latency: ['p(95)<5000'],
    alert_latency: ['p(95)<200'],
    health_latency: ['p(95)<100'],
  },
};

const BASE = __ENV.API_BASE || 'http://localhost:8080';

export function setup() {
  const res = http.post(`${BASE}/api/v1/auth/login`, JSON.stringify({
    username: 'admin',
    password: 'admin_Dev#2026!',
  }), { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login ok': (r) => r.status === 200 });
  return { token: res.json('accessToken') };
}

export default function (data) {
  const auth = { headers: { Authorization: `Bearer ${data.token}` } };

  const health = http.get(`${BASE}/actuator/health`);
  healthLatency.add(health.timings.duration);
  check(health, { 'health 200': (r) => r.status === 200 });

  const sensors = http.get(`${BASE}/api/v1/environment/sensors`, auth);
  sensorLatency.add(sensors.timings.duration);
  check(sensors, { 'sensors 200': (r) => r.status === 200 });

  const esg = http.get(`${BASE}/api/v1/esg/summary?period=quarterly&year=2026&quarter=1`, auth);
  esgLatency.add(esg.timings.duration);
  check(esg, { 'esg 200': (r) => r.status === 200 });

  const alerts = http.get(`${BASE}/api/v1/alerts?page=0&size=20`, auth);
  alertLatency.add(alerts.timings.duration);
  check(alerts, { 'alerts 200': (r) => r.status === 200 });

  sleep(1);
}

export function handleSummary(data) {
  return {
    stdout: JSON.stringify(data, null, 2),
    'perf/results.json': JSON.stringify(data, null, 2),
  };
}
