/**
 * YAWL Load Test - k6 Performance Testing Script
 * Simulates 100 concurrent users accessing YAWL workflow engine
 * Version: 5.2
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const workflowLaunchTime = new Trend('workflow_launch_time');
const workflowCompletionTime = new Trend('workflow_completion_time');
const apiCallCounter = new Counter('api_calls');

// Test configuration
export const options = {
    stages: [
        { duration: '2m', target: 20 },   // Ramp up to 20 users
        { duration: '5m', target: 100 },  // Ramp up to 100 users
        { duration: '10m', target: 100 }, // Stay at 100 users
        { duration: '2m', target: 0 },    // Ramp down to 0 users
    ],
    thresholds: {
        'http_req_duration': ['p(95)<2000', 'p(99)<5000'],
        'http_req_failed': ['rate<0.05'],
        'errors': ['rate<0.1'],
        'workflow_launch_time': ['p(95)<3000'],
    },
};

// Base URL from environment or default
const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

// Test data
const WORKFLOW_SPECS = [
    'MakeRecordings',
    'MakeTrip',
    'OrderFulfillment',
    'ResourceAllocation',
];

/**
 * Setup function - runs once before test
 */
export function setup() {
    console.log('Starting YAWL Load Test');
    console.log(`Base URL: ${BASE_URL}`);
    console.log(`VUs: ${options.stages[2].target}`);

    // Verify service is up
    const healthCheck = http.get(`${BASE_URL}/actuator/health`);
    if (healthCheck.status !== 200) {
        throw new Error(`Service not available: ${healthCheck.status}`);
    }

    console.log('Service health check passed');
    return { startTime: Date.now() };
}

/**
 * Main test function - runs for each VU
 */
export default function(data) {
    const headers = {
        'Content-Type': 'application/xml',
        'Accept': 'application/xml',
    };

    // Test 1: Check engine status
    {
        const response = http.get(`${BASE_URL}/yawl/ib`, { headers });
        apiCallCounter.add(1);

        const success = check(response, {
            'engine status is 200': (r) => r.status === 200,
            'response time < 500ms': (r) => r.timings.duration < 500,
        });

        errorRate.add(!success);
    }

    sleep(1);

    // Test 2: List specifications
    {
        const response = http.post(
            `${BASE_URL}/yawl/ib`,
            '<getSpecificationList/>',
            { headers }
        );
        apiCallCounter.add(1);

        check(response, {
            'list specs is 200': (r) => r.status === 200,
            'response contains specs': (r) => r.body.includes('specification'),
        });
    }

    sleep(2);

    // Test 3: Launch workflow case
    {
        const specId = WORKFLOW_SPECS[Math.floor(Math.random() * WORKFLOW_SPECS.length)];
        const caseId = `test_case_${__VU}_${Date.now()}`;

        const launchStart = Date.now();
        const response = http.post(
            `${BASE_URL}/yawl/ib`,
            `<launchCase><specificationID>${specId}</specificationID><caseID>${caseId}</caseID></launchCase>`,
            { headers }
        );
        const launchDuration = Date.now() - launchStart;

        apiCallCounter.add(1);
        workflowLaunchTime.add(launchDuration);

        const success = check(response, {
            'workflow launched': (r) => r.status === 200 || r.status === 201,
            'case ID returned': (r) => r.body.includes('case'),
        });

        errorRate.add(!success);
    }

    sleep(3);

    // Test 4: Get work items
    {
        const response = http.post(
            `${BASE_URL}/yawl/ib`,
            '<getAllWorkItems/>',
            { headers }
        );
        apiCallCounter.add(1);

        check(response, {
            'work items retrieved': (r) => r.status === 200,
        });
    }

    sleep(2);

    // Test 5: Check resource service
    {
        const response = http.get(`${BASE_URL}/resourceService/rs`, { headers });
        apiCallCounter.add(1);

        check(response, {
            'resource service available': (r) => r.status === 200 || r.status === 401,
        });
    }

    sleep(1);

    // Test 6: MCP integration endpoint (if enabled)
    {
        const response = http.get(`${BASE_URL}/mcp/v1/capabilities`, {
            headers: { 'Content-Type': 'application/json' }
        });
        apiCallCounter.add(1);

        check(response, {
            'MCP endpoint available': (r) => r.status === 200 || r.status === 404,
        });
    }

    sleep(2);

    // Test 7: A2A agent registry
    {
        const response = http.get(`${BASE_URL}/a2a/agents`, {
            headers: { 'Content-Type': 'application/json' }
        });
        apiCallCounter.add(1);

        check(response, {
            'A2A registry available': (r) => r.status === 200 || r.status === 404,
        });
    }

    sleep(3);
}

/**
 * Teardown function - runs once after test
 */
export function teardown(data) {
    const duration = (Date.now() - data.startTime) / 1000;
    console.log(`Test completed in ${duration} seconds`);
    console.log('Check detailed results in the k6 output');
}

/**
 * Handle summary - custom summary output
 */
export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        'k6-load-test-summary.json': JSON.stringify(data),
    };
}

/**
 * Helper function for text summary
 */
function textSummary(data, options) {
    const indent = options.indent || '';
    const lines = [
        '',
        `${indent}YAWL Load Test Summary`,
        `${indent}${'='.repeat(50)}`,
        '',
        `${indent}Total Requests: ${data.metrics.http_reqs.values.count}`,
        `${indent}Failed Requests: ${data.metrics.http_req_failed.values.rate * 100}%`,
        `${indent}Avg Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`,
        `${indent}95th Percentile: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`,
        `${indent}99th Percentile: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`,
        '',
        `${indent}Custom Metrics:`,
        `${indent}  Error Rate: ${(data.metrics.errors.values.rate * 100).toFixed(2)}%`,
        `${indent}  Workflow Launch Time (avg): ${data.metrics.workflow_launch_time?.values.avg.toFixed(2) || 'N/A'}ms`,
        `${indent}  API Calls Total: ${data.metrics.api_calls.values.count}`,
        '',
    ];

    return lines.join('\n');
}
