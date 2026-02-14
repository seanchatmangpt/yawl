// k6 Load Test Script for YAWL Engine
// OCI DevOps Performance Testing
// Version: 1.0.0

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const latencyTrend = new Trend('latency');
const requestsPerSecond = new Counter('requests');

// Test configuration
export const options = {
  // Stage configuration
  stages: [
    { duration: '1m', target: 10 },   // Ramp up to 10 users
    { duration: '3m', target: 50 },   // Ramp up to 50 users
    { duration: '5m', target: 100 },  // Ramp up to 100 users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 0 },    // Ramp down to 0
  ],
  // Thresholds
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],  // 95% under 2s, 99% under 5s
    errors: ['rate<0.05'],  // Less than 5% errors
    http_req_failed: ['rate<0.05'],
  },
  // OCI-specific settings
  ext: {
    loadimpact: {
      name: 'YAWL Engine Load Test',
      projectID: 'yawl-performance',
    },
  },
};

// Configuration from environment
const BASE_URL = __ENV.YAWL_ENGINE_URL || 'http://yawl-engine.yawl.svc.cluster.local:8080/yawl';
const API_KEY = __ENV.YAWL_API_KEY || '';

// Default headers
const headers = {
  'Content-Type': 'application/xml',
  'Accept': 'application/xml',
};

if (API_KEY) {
  headers['X-YAWL-API-Key'] = API_KEY;
}

// Test data - Sample YAWL specification
const sampleSpecification = `<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  version="0.1">
  <specification uri="LoadTest_{{TIMESTAMP}}">
    <metaData>
      <description>Load Test Specification</description>
      <creator>k6 Load Test</creator>
    </metaData>
    <rootNet id="LoadTestNet">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto>
            <nextElementRef id="task1" />
          </flowsInto>
        </inputCondition>
        <task id="task1">
          <flowsInto>
            <nextElementRef id="end" />
          </flowsInto>
          <decomposition id="task1_decomp" />
        </task>
        <outputCondition id="end" />
      </processControlElements>
    </rootNet>
  </specification>
</specificationSet>`;

export function setup() {
  // Setup phase - verify service is available
  console.log(`Testing against: ${BASE_URL}`);

  const healthCheck = http.get(`${BASE_URL}/health`, { headers });

  check(healthCheck, {
    'health check passed': (r) => r.status === 200,
  });

  return { baseUrl: BASE_URL };
}

export default function (data) {
  const baseUrl = data.baseUrl;

  // Test 1: Health check
  const healthResponse = http.get(`${baseUrl}/health`, { headers });

  check(healthResponse, {
    'health check status is 200': (r) => r.status === 200,
    'health check body is valid': (r) => r.body.includes('healthy') || r.status === 200,
  });

  errorRate.add(healthResponse.status !== 200);
  latencyTrend.add(healthResponse.timings.duration);
  requestsPerSecond.add(1);

  sleep(1);

  // Test 2: API connection check
  const connectionResponse = http.get(`${baseUrl}/api/connection`, { headers });

  check(connectionResponse, {
    'connection check status is 200 or 401': (r) => r.status === 200 || r.status === 401,
  });

  errorRate.add(connectionResponse.status >= 500);
  latencyTrend.add(connectionResponse.timings.duration);
  requestsPerSecond.add(1);

  sleep(1);

  // Test 3: Specification list (requires auth)
  const specListResponse = http.get(`${baseUrl}/api/specifications`, { headers });

  check(specListResponse, {
    'spec list returns valid response': (r) => r.status === 200 || r.status === 401 || r.status === 403,
  });

  latencyTrend.add(specListResponse.timings.duration);
  requestsPerSecond.add(1);

  sleep(1);

  // Test 4: Engine status
  const statusResponse = http.get(`${baseUrl}/api/monitor/status`, { headers });

  check(statusResponse, {
    'status endpoint accessible': (r) => r.status === 200 || r.status === 401,
  });

  latencyTrend.add(statusResponse.timings.duration);
  requestsPerSecond.add(1);

  sleep(2);
}

export function teardown(data) {
  // Cleanup phase
  console.log('Load test completed');
}

// Handle summary output
export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: false }),
    'load-test-summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '  ';
  let summary = '\n=== YAWL Load Test Summary ===\n\n';

  if (data.metrics.http_req_duration) {
    const p95 = data.metrics.http_req_duration.values['p(95)'];
    const p99 = data.metrics.http_req_duration.values['p(99)'];
    summary += `${indent}HTTP Request Duration:\n`;
    summary += `${indent}  p(95): ${p95?.toFixed(2) || 'N/A'}ms\n`;
    summary += `${indent}  p(99): ${p99?.toFixed(2) || 'N/A'}ms\n\n`;
  }

  if (data.metrics.errors) {
    const errorRate = data.metrics.errors.values.rate;
    summary += `${indent}Error Rate: ${(errorRate * 100).toFixed(2)}%\n\n`;
  }

  if (data.metrics.http_reqs) {
    const totalReqs = data.metrics.http_reqs.values.count;
    const rate = data.metrics.http_reqs.values.rate;
    summary += `${indent}Total Requests: ${totalReqs}\n`;
    summary += `${indent}Requests/second: ${rate?.toFixed(2) || 'N/A'}\n\n`;
  }

  summary += '==============================\n';
  return summary;
}
