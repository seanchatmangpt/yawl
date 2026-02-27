/**
 * YAWL Stateful vs Stateless Engine Scaling Test
 * Compares performance between stateful and stateless engine configurations
 * Version: 6.0
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const statefulLaunchTime = new Trend('stateful_launch_time');
const statelessLaunchTime = new Trend('stateless_launch_time');
const engineSwitchCount = new Counter('engine_switches');
const activeCases = new Gauge('active_cases');
const memoryPressure = new Gauge('memory_pressure_mb');

// Test configuration
export const options = {
    stages: [
        { duration: '2m', target: 500 },   // Warm up
        { duration: '3m', target: 1500 },  // Ramp to 1.5K users
        { duration: '5m', target: 3000 },  // Ramp to 3K users
        { duration: '10m', target: 3000 }, // Hold at 3K
        { duration: '3m', target: 1500 },  // Step down
        { duration: '2m', target: 0 },     // Cool down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1000', 'p(99)<3000'],
        'http_req_failed': ['rate<0.05'],
        'stateful_launch_time': ['p(95)<800'],
        'stateless_launch_time': ['p(95)<600'],
        'errors': ['rate<0.08'],
    },
};

// Base URL from environment or default
const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

// Engine test data
const ENGINE_TYPES = ['stateful', 'stateless'];
const WORKFLOW_SPECS = [
    'MakeRecordings',
    'MakeTrip', 
    'OrderFulfillment',
    'ResourceAllocation',
    'DocumentApproval'
];

// State tracking
let engineTypeIndex = 0;
let totalCases = 0;
let lastSwitch = Date.now();

export function setup() {
    console.log('==========================================');
    console.log('YAWL Stateful vs Stateless Engine Scaling Test');
    console.log('==========================================');
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Max VUs: 3000`);
    console.log(`Duration: 25 minutes`);
    console.log('Test Pattern: Alternate between stateful and stateless');
    console.log('');

    // Initialize service
    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Service unavailable: ${health.status}`);
    }

    // Test both engine types
    testEngineType('stateful');
    testEngineType('stateless');

    return { 
        startTime: Date.now(), 
        switchInterval: 300000, // 5 minutes per engine type
        lastSwitch: Date.now()
    };
}

export default function(data) {
    const now = Date.now();
    
    // Switch engine type every 5 minutes
    if (now - data.lastSwitch > data.switchInterval) {
        engineTypeIndex = (engineTypeIndex + 1) % ENGINE_TYPES.length;
        data.lastSwitch = now;
        engineSwitchCount.add(1);
        console.log(`Switched to ${ENGINE_TYPES[engineTypeIndex]} engine`);
    }

    const currentEngine = ENGINE_TYPES[engineTypeIndex];
    const headers = {
        'Content-Type': 'application/xml',
        'Accept': 'application/xml',
        'X-Engine-Type': currentEngine,
    };

    // Test operations with engine-specific patterns
    const operations = [
        () => testEngineHealth(currentEngine, headers),
        () => testWorkflowLaunch(currentEngine, headers),
        () => testWorkItemRetrieval(currentEngine, headers),
        () => testCaseLifecycle(currentEngine, headers),
        () => testEngineMetrics(currentEngine, headers),
    ];

    // Execute random operation
    const operation = operations[Math.floor(Math.random() * operations.length)];
    const start = Date.now();

    try {
        operation();
        const duration = Date.now() - start;
        
        // Track launch time by engine type
        if (currentEngine === 'stateful') {
            statefulLaunchTime.add(duration);
        } else {
            statelessLaunchTime.add(duration);
        }

        totalCases += 1;
        activeCases.add(totalCases);
    } catch (error) {
        console.error(`Operation failed for ${currentEngine} engine: ${error.message}`);
    }

    // Variable sleep based on engine type
    const sleepTime = currentEngine === 'stateful' ? Math.random() * 3 : Math.random() * 1.5;
    sleep(sleepTime);
}

function testEngineHealth(engineType, headers) {
    const response = http.get(`${BASE_URL}/yawl/ib`, {
        headers,
        timeout: '5s',
    });

    const success = check(response, {
        'engine health ok': (r) => r.status === 200,
        'correct engine response': (r) => r.body.includes(engineType),
    });

    return success;
}

function testWorkflowLaunch(engineType, headers) {
    const specId = WORKFLOW_SPECS[Math.floor(Math.random() * WORKFLOW_SPECS.length)];
    const caseId = `${engineType}_${__VU}_${Date.now()}`;

    const launchStart = Date.now();
    const payload = `<launchCase>
        <specificationID>${specId}</specificationID>
        <caseID>${caseId}</caseID>
        <caseParams>
            <param name="engineType" value="${engineType}"/>
        </caseParams>
    </launchCase>`;

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers,
        timeout: '15s',
    });

    const duration = Date.now() - launchStart;

    const success = check(response, {
        'workflow launched': (r) => r.status === 200 || r.status === 201,
        'correct engine type': (r) => r.body.includes(engineType),
        'response time acceptable': (r) => duration < 5000,
    });

    if (!success) {
        throw new Error(`Workflow launch failed for ${engineType} engine`);
    }

    return duration;
}

function testWorkItemRetrieval(engineType, headers) {
    const response = http.post(
        `${BASE_URL}/yawl/ib`,
        `<getAllWorkItems>
            <filter>
                <engineType>${engineType}</engineType>
            </filter>
        </getAllWorkItems>`,
        { headers, timeout: '5s' }
    );

    check(response, {
        'work items retrieved': (r) => r.status === 200,
        'engine type filtered': (r) => r.body.includes(engineType),
    });
}

function testCaseLifecycle(engineType, headers) {
    group(`Case lifecycle - ${engineType}`, function() {
        // Launch new case
        const specId = WORKFLOW_SPECS[Math.floor(Math.random() * WORKFLOW_SPECS.length)];
        const caseId = `${engineType}_lifecycle_${__VU}_${Date.now()}`;

        const launchResponse = http.post(`${BASE_URL}/yawl/ib`, 
            `<launchCase><specificationID>${specId}</specificationID><caseID>${caseId}</caseID></launchCase>`,
            { headers, timeout: '10s' }
        );

        check(launchResponse, {
            'case launched': (r) => r.status === 200 || r.status === 201,
        });

        sleep(0.5);

        // Get running cases
        const casesResponse = http.post(`${BASE_URL}/yawl/ib`,
            `<getRunningCases><specificationID>${specId}</specificationID></getRunningCases>`,
            { headers, timeout: '5s' }
        );

        check(casesResponse, {
            'running cases retrieved': (r) => r.status === 200,
        });
    });
}

function testEngineMetrics(engineType, headers) {
    // Simulate memory pressure tracking
    const memoryUsage = Math.random() * 512 + 256; // 256-768 MB
    memoryPressure.add(memoryUsage);

    const response = http.get(`${BASE_URL}/actuator/metrics`, {
        headers: { 'Accept': 'application/json' },
        timeout: '5s',
    });

    check(response, {
        'metrics endpoint available': (r) => r.status === 200,
        'engine type in metrics': (r) => r.body.includes(engineType),
    });
}

export function teardown(data) {
    const durationSec = (Date.now() - data.startTime) / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('==========================================');
    console.log('Stateful vs Stateless Engine Test Complete');
    console.log('==========================================');
    console.log(`Total Duration: ${durationMin} minutes`);
    console.log(`Engine Switches: ${engineSwitchCount.values.count}`);
    console.log(`Total Cases: ${totalCases}`);
    console.log('');
    
    console.log('Engine Performance Comparison:');
    console.log(`  Stateful Avg Launch Time: ${statefulLaunchTime.values.avg.toFixed(2)}ms`);
    console.log(`  Stateless Avg Launch Time: ${statelessLaunchTime.values.avg.toFixed(2)}ms`);
    console.log(`  Stateless is ${((1 - statelessLaunchTime.values.avg/statefulLaunchTime.values.avg)*100).toFixed(1)}% faster`);
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test_type: 'stateful_vs_stateless_scaling',
        max_vus: 3000,
        duration_seconds: data.state.testRunDurationMs / 1000,
        engine_switches: engineSwitchCount.values.count,
        total_cases: totalCases,
        metrics: {
            stateful_launch_time_avg: statefulLaunchTime.values.avg || 0,
            stateless_launch_time_avg: statelessLaunchTime.values.avg || 0,
            stateful_launch_time_p95: statefulLaunchTime.values['p(95)'] || 0,
            stateless_launch_time_p95: statelessLaunchTime.values['p(95)'] || 0,
            total_requests: data.metrics.http_reqs?.values.count || 0,
            failed_requests: data.metrics.http_req_failed?.values.rate || 0,
            error_rate: data.metrics.errors?.values.rate || 0,
            avg_response_time: data.metrics.http_req_duration?.values.avg || 0,
            p95_response_time: data.metrics.http_req_duration?.values['p(95)'] || 0,
        },
        engine_comparison: {
            performance_improvement: statefulLaunchTime.values.avg > 0 ? 
                ((statefulLaunchTime.values.avg - statelessLaunchTime.values.avg) / statefulLaunchTime.values.avg * 100).toFixed(1) : 0,
            consistency_ratio: (statelessLaunchTime.values.stddev || 1) / (statefulLaunchTime.values.stddev || 1),
        },
        thresholds_passed: Object.keys(data.metrics)
            .filter(k => data.metrics[k].thresholds)
            .reduce((acc, k) => {
                acc[k] = Object.values(data.metrics[k].thresholds).every(t => t.ok);
                return acc;
            }, {}),
    };

    console.log('');
    console.log('Detailed Performance Comparison:');
    console.log(`  Stateful - Avg: ${summary.metrics.stateful_launch_time_avg.toFixed(2)}ms, P95: ${summary.metrics.stateful_launch_time_p95.toFixed(2)}ms`);
    console.log(`  Stateless - Avg: ${summary.metrics.stateless_launch_time_avg.toFixed(2)}ms, P95: ${summary.metrics.stateless_launch_time_p95.toFixed(2)}ms`);
    console.log(`  Performance Improvement: ${summary.engine_comparison.performance_improvement}%`);
    console.log(`  Consistency Ratio: ${summary.engine_comparison.consistency_ratio.toFixed(2)}x`);
    console.log(`  Thresholds Passed: ${Object.values(summary.thresholds_passed).filter(v => v).length}/${Object.keys(summary.thresholds_passed).length}`);

    return {
        'stdout': textSummary(data, summary, { indent: ' ', enableColors: true }),
        'k6-stateful-stateless-summary.json': JSON.stringify(summary, null, 2),
    };
}

function textSummary(data, summary, options) {
    return `
YAWL Stateful vs Stateless Engine Scaling Results
=================================================

Duration: ${(data.state.testRunDurationMs / 1000 / 60).toFixed(1)} minutes
Max VUs: 3000
Engine Switches: ${summary.engine_switches}
Total Cases: ${summary.total_cases}

Engine Performance Comparison:
  Stateful Engine:
    Avg Launch Time: ${summary.metrics.stateful_launch_time_avg.toFixed(2)}ms
    P95 Launch Time: ${summary.metrics.stateful_launch_time_p95.toFixed(2)}ms
  
  Stateless Engine:
    Avg Launch Time: ${summary.metrics.stateless_launch_time_avg.toFixed(2)}ms
    P95 Launch Time: ${summary.metrics.stateless_launch_time_p95.toFixed(2)}ms
  
  Improvement Metrics:
    Performance Gain: ${summary.engine_comparison.performance_improvement}%
    Consistency Ratio: ${summary.engine_comparison.consistency_ratio.toFixed(2)}x

HTTP Metrics:
  Total Requests: ${summary.metrics.total_requests}
  Failed Rate: ${(summary.metrics.failed_requests * 100).toFixed(2)}%
  Error Rate: ${(summary.metrics.error_rate * 100).toFixed(2)}%
  Avg Response Time: ${summary.metrics.avg_response_time.toFixed(2)}ms
  P95 Response Time: ${summary.metrics.p95_response_time.toFixed(2)}ms

Test Status: ${Object.values(summary.thresholds_passed).every(v => v) ? 'PASSED ✓' : 'FAILED ✗'}
`;
}
