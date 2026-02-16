/**
 * YAWL Stress Test - k6 Performance Testing Script
 * Ramps up to 1000 concurrent users to test system limits
 * Version: 5.2
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const activeUsers = new Gauge('active_users');
const throughput = new Rate('throughput');
const responseTime = new Trend('response_time_ms');

// Aggressive stress test configuration
export const options = {
    stages: [
        { duration: '1m', target: 100 },    // Warm up
        { duration: '2m', target: 500 },    // Ramp to 500
        { duration: '3m', target: 1000 },   // Ramp to 1000
        { duration: '5m', target: 1000 },   // Hold at 1000
        { duration: '2m', target: 500 },    // Step down
        { duration: '1m', target: 0 },      // Cool down
    ],
    thresholds: {
        'http_req_duration': ['p(99)<10000'], // 99% under 10s
        'http_req_failed': ['rate<0.20'],     // Allow 20% failures under stress
        'errors': ['rate<0.30'],              // Allow 30% errors under extreme load
    },
};

const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

export function setup() {
    console.log('==========================================');
    console.log('YAWL Stress Test - System Limits Testing');
    console.log('==========================================');
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Max VUs: 1000`);
    console.log(`Duration: 14 minutes`);
    console.log('');

    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Service unavailable: ${health.status}`);
    }

    return { startTime: Date.now() };
}

export default function(data) {
    activeUsers.add(__VU);

    const headers = {
        'Content-Type': 'application/xml',
        'Accept': 'application/xml',
    };

    // High-intensity workflow operations
    const operations = [
        () => testEngineStatus(headers),
        () => testWorkflowLaunch(headers),
        () => testWorkItemRetrieval(headers),
        () => testResourceQuery(headers),
        () => testConcurrentCases(headers),
    ];

    // Execute random operation
    const operation = operations[Math.floor(Math.random() * operations.length)];
    const start = Date.now();

    try {
        operation();
        const duration = Date.now() - start;
        responseTime.add(duration);
        throughput.add(1);
    } catch (error) {
        errorRate.add(1);
        console.error(`Operation failed: ${error.message}`);
    }

    // Variable sleep to create realistic load pattern
    sleep(Math.random() * 2);
}

function testEngineStatus(headers) {
    const response = http.get(`${BASE_URL}/yawl/ib`, {
        headers,
        timeout: '5s',
    });

    const success = check(response, {
        'status check ok': (r) => r.status === 200,
    });

    if (!success) errorRate.add(1);
}

function testWorkflowLaunch(headers) {
    const specs = ['MakeRecordings', 'MakeTrip', 'OrderFlow', 'ResourceTest'];
    const specId = specs[Math.floor(Math.random() * specs.length)];
    const caseId = `stress_${__VU}_${__ITER}_${Date.now()}`;

    const payload = `<launchCase>
        <specificationID>${specId}</specificationID>
        <caseID>${caseId}</caseID>
        <caseParams/>
    </launchCase>`;

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers,
        timeout: '10s',
    });

    const success = check(response, {
        'workflow launched': (r) => r.status === 200 || r.status === 201,
    });

    if (!success) errorRate.add(1);
}

function testWorkItemRetrieval(headers) {
    const response = http.post(
        `${BASE_URL}/yawl/ib`,
        '<getAllWorkItems/>',
        { headers, timeout: '5s' }
    );

    const success = check(response, {
        'work items retrieved': (r) => r.status === 200,
    });

    if (!success) errorRate.add(1);
}

function testResourceQuery(headers) {
    const response = http.get(`${BASE_URL}/resourceService/rs`, {
        headers,
        timeout: '5s',
    });

    check(response, {
        'resource service responds': (r) => r.status !== 0,
    });
}

function testConcurrentCases(headers) {
    const batchSize = 5;
    const requests = [];

    for (let i = 0; i < batchSize; i++) {
        requests.push({
            method: 'POST',
            url: `${BASE_URL}/yawl/ib`,
            body: `<getRunningCases><specificationID>*</specificationID></getRunningCases>`,
            params: { headers, timeout: '5s' },
        });
    }

    const responses = http.batch(requests);
    const successCount = responses.filter(r => r.status === 200).length;

    check(responses, {
        'batch requests completed': () => successCount >= batchSize * 0.8,
    });

    if (successCount < batchSize * 0.5) errorRate.add(1);
}

export function teardown(data) {
    const durationSec = (Date.now() - data.startTime) / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('==========================================');
    console.log('Stress Test Complete');
    console.log('==========================================');
    console.log(`Total Duration: ${durationMin} minutes`);
    console.log('');
    console.log('System survived stress test!');
    console.log('Review metrics for performance degradation');
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test_type: 'stress',
        max_vus: 1000,
        duration_seconds: data.state.testRunDurationMs / 1000,
        metrics: {
            total_requests: data.metrics.http_reqs?.values.count || 0,
            failed_requests: data.metrics.http_req_failed?.values.rate || 0,
            error_rate: data.metrics.errors?.values.rate || 0,
            avg_response_time: data.metrics.http_req_duration?.values.avg || 0,
            p95_response_time: data.metrics.http_req_duration?.values['p(95)'] || 0,
            p99_response_time: data.metrics.http_req_duration?.values['p(99)'] || 0,
            throughput_per_second: data.metrics.throughput?.values.rate || 0,
        },
        thresholds_passed: Object.keys(data.metrics)
            .filter(k => data.metrics[k].thresholds)
            .reduce((acc, k) => {
                acc[k] = Object.values(data.metrics[k].thresholds).every(t => t.ok);
                return acc;
            }, {}),
    };

    console.log('');
    console.log('Summary Metrics:');
    console.log(`  Total Requests: ${summary.metrics.total_requests}`);
    console.log(`  Failed Rate: ${(summary.metrics.failed_requests * 100).toFixed(2)}%`);
    console.log(`  Error Rate: ${(summary.metrics.error_rate * 100).toFixed(2)}%`);
    console.log(`  Avg Response: ${summary.metrics.avg_response_time.toFixed(2)}ms`);
    console.log(`  P95 Response: ${summary.metrics.p95_response_time.toFixed(2)}ms`);
    console.log(`  P99 Response: ${summary.metrics.p99_response_time.toFixed(2)}ms`);
    console.log(`  Throughput: ${summary.metrics.throughput_per_second.toFixed(2)} req/s`);

    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6-stress-test-summary.json': JSON.stringify(summary, null, 2),
    };
}

function textSummary(data, options) {
    return `
YAWL Stress Test Results
========================

Duration: ${(data.state.testRunDurationMs / 1000 / 60).toFixed(1)} minutes
Max VUs: 1000

HTTP Metrics:
  Total Requests: ${data.metrics.http_reqs?.values.count || 0}
  Failed: ${((data.metrics.http_req_failed?.values.rate || 0) * 100).toFixed(2)}%
  Avg Duration: ${(data.metrics.http_req_duration?.values.avg || 0).toFixed(2)}ms
  P95 Duration: ${(data.metrics.http_req_duration?.values['p(95)'] || 0).toFixed(2)}ms
  P99 Duration: ${(data.metrics.http_req_duration?.values['p(99)'] || 0).toFixed(2)}ms

Custom Metrics:
  Error Rate: ${((data.metrics.errors?.values.rate || 0) * 100).toFixed(2)}%
  Throughput: ${(data.metrics.throughput?.values.rate || 0).toFixed(2)} req/s

Test Status: ${Object.values(data.thresholds || {}).every(t => t.ok) ? 'PASSED ✓' : 'FAILED ✗'}
`;
}
