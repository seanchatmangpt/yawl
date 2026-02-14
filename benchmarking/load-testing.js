import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const responseTime = new Trend('response_time');
const successCount = new Counter('success_count');
const activeUsers = new Gauge('active_users');

// Load testing configuration
export const options = {
  // Define test stages: ramp-up, sustained load, ramp-down
  stages: [
    { duration: '30s', target: 10 },   // Ramp-up to 10 users
    { duration: '1m30s', target: 50 }, // Ramp-up to 50 users
    { duration: '5m', target: 50 },    // Sustained 50 users
    { duration: '2m', target: 100 },   // Ramp-up to 100 users
    { duration: '5m', target: 100 },   // Sustained 100 users
    { duration: '30s', target: 0 },    // Ramp-down to 0 users
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed': ['rate<0.1'],
    'errors': ['rate<0.05'],
  },
  ext: {
    loadimpact: {
      projectID: 3456789,
      name: 'YAWL Performance Test',
    },
  },
};

// Environment configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_PATH = '/api/v1';

// Test data
const testUsers = [
  { username: 'testuser1', password: 'password123' },
  { username: 'testuser2', password: 'password123' },
  { username: 'testuser3', password: 'password123' },
];

export function setup() {
  console.log('Setting up performance test environment...');
  // Authentication setup if needed
  return { timestamp: new Date().toISOString() };
}

export default function (data) {
  activeUsers.add(1);

  group('API Endpoint Tests', () => {
    testHealthCheck();
    testGetEndpoint();
    testPostEndpoint();
    testPaginationEndpoint();
    testComplexQueryEndpoint();
  });

  group('Authentication Flow', () => {
    testAuthenticationFlow();
  });

  group('Error Handling', () => {
    testErrorHandling();
  });

  sleep(1);
}

export function teardown(data) {
  console.log(`Completed performance test at ${data.timestamp}`);
}

// Health check endpoint
function testHealthCheck() {
  const res = http.get(`${BASE_URL}/health`);
  check(res, {
    'Health check status is 200': (r) => r.status === 200,
    'Health check response time < 100ms': (r) => r.timings.duration < 100,
  }) || errorRate.add(1);
  responseTime.add(res.timings.duration, { endpoint: 'health' });
}

// GET endpoint test
function testGetEndpoint() {
  const res = http.get(`${BASE_URL}${API_PATH}/resources?limit=100`);
  check(res, {
    'GET status is 200': (r) => r.status === 200,
    'GET response contains data': (r) => r.body.length > 0,
    'GET response time < 500ms': (r) => r.timings.duration < 500,
  }) || errorRate.add(1);

  if (res.status === 200) {
    successCount.add(1);
  }
  responseTime.add(res.timings.duration, { endpoint: 'GET /resources' });
}

// POST endpoint test
function testPostEndpoint() {
  const payload = {
    name: `Test Resource ${Math.random()}`,
    description: 'Performance testing resource',
    type: 'test',
    metadata: {
      createdBy: 'load-test',
      timestamp: new Date().toISOString(),
    },
  };

  const res = http.post(
    `${BASE_URL}${API_PATH}/resources`,
    JSON.stringify(payload),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );

  check(res, {
    'POST status is 201': (r) => r.status === 201 || r.status === 200,
    'POST response contains ID': (r) => r.body.includes('id') || r.body.includes('_id'),
    'POST response time < 750ms': (r) => r.timings.duration < 750,
  }) || errorRate.add(1);

  if (res.status === 201 || res.status === 200) {
    successCount.add(1);
  }
  responseTime.add(res.timings.duration, { endpoint: 'POST /resources' });
}

// Pagination test
function testPaginationEndpoint() {
  const page = Math.floor(Math.random() * 10) + 1;
  const res = http.get(`${BASE_URL}${API_PATH}/resources?page=${page}&limit=50`);

  check(res, {
    'Pagination status is 200': (r) => r.status === 200,
    'Pagination response contains page info': (r) =>
      r.body.includes('page') || r.body.includes('total'),
  }) || errorRate.add(1);
  responseTime.add(res.timings.duration, { endpoint: 'pagination' });
}

// Complex query test
function testComplexQueryEndpoint() {
  const params = {
    filter: 'active:true',
    sort: '-created_at',
    limit: 50,
  };

  const queryString = Object.keys(params)
    .map((key) => `${key}=${params[key]}`)
    .join('&');

  const res = http.get(`${BASE_URL}${API_PATH}/resources?${queryString}`);

  check(res, {
    'Complex query status is 200': (r) => r.status === 200,
    'Complex query response time < 1000ms': (r) => r.timings.duration < 1000,
  }) || errorRate.add(1);
  responseTime.add(res.timings.duration, { endpoint: 'complex_query' });
}

// Authentication flow test
function testAuthenticationFlow() {
  const user = testUsers[Math.floor(Math.random() * testUsers.length)];

  const loginRes = http.post(
    `${BASE_URL}${API_PATH}/auth/login`,
    JSON.stringify(user),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );

  check(loginRes, {
    'Login status is 200': (r) => r.status === 200,
    'Login response contains token': (r) => r.body.includes('token'),
  }) || errorRate.add(1);

  if (loginRes.status === 200) {
    const token = JSON.parse(loginRes.body).token || '';

    // Authenticated request
    const authRes = http.get(
      `${BASE_URL}${API_PATH}/resources/protected`,
      {
        headers: { Authorization: `Bearer ${token}` },
      }
    );

    check(authRes, {
      'Protected endpoint status is 200': (r) => r.status === 200,
      'Protected endpoint response time < 600ms': (r) => r.timings.duration < 600,
    }) || errorRate.add(1);
    responseTime.add(authRes.timings.duration, { endpoint: 'protected' });
  }
}

// Error handling tests
function testErrorHandling() {
  // Test 404 error
  const notFoundRes = http.get(`${BASE_URL}${API_PATH}/resources/nonexistent-id-${Math.random()}`);
  check(notFoundRes, {
    '404 status is 404': (r) => r.status === 404,
  });

  // Test invalid input
  const invalidRes = http.post(
    `${BASE_URL}${API_PATH}/resources`,
    JSON.stringify({ invalid: 'data' }),
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );
  check(invalidRes, {
    'Invalid input returns error status': (r) => r.status >= 400,
  });

  // Test concurrent requests
  const batch = http.batch([
    ['GET', `${BASE_URL}${API_PATH}/resources`],
    ['GET', `${BASE_URL}${API_PATH}/resources`],
    ['GET', `${BASE_URL}${API_PATH}/resources`],
  ]);

  check(batch[0], {
    'Batch request status is 200': (r) => r.status === 200,
  });
}
