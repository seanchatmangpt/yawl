/**
 * YAWL Chaos Network Test - Network Failure Simulation
 * Tests system resilience under various network failure scenarios
 * Version: 6.0
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Chaos engineering imports
import { checkers } from 'k6/chaos';

// Custom metrics
const networkLatency = new Trend('network_latency_ms');
const packetLossRate = new Rate('packet_loss');
const connectionFailureRate = new Rate('connection_failures');
const recoveryTime = new Trend('recovery_time_ms');
const systemThroughput = new Gauge('system_throughput');
const errorPropagation = new Trend('error_propagation_time');

// Test configuration
export const options = {
    stages: [
        { duration: '1m', target: 500 },    // Normal baseline
        { duration: '2m', target: 1000 },   // Normal ramp
        { duration: '5m', target: 2000 },   // Chaos testing
        { duration: '3m', target: 1000 },   // Recovery phase
        { duration: '1m', target: 500 },    // Stability check
        { duration: '2m', target: 0 },      // Cool down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<3000', 'p(99)<5000'],
        'http_req_failed': ['rate<0.10'],    // Allow 10% failures during chaos
        'network_latency_ms': ['p(95)<2000'],
        'recovery_time_ms': ['p(95)<3000'],
        'errors': ['rate<0.15'],            // Allow 15% errors during chaos
    },
    scenarios: {
        chaosInjection: {
            executor: 'rps',
            rps: 1000,
            duration: '5m',
            gracefulStop: '30s',
            startTime: '3m', // Start chaos at 3 minutes
        },
        baseline: {
            executor: 'rps',
            rps: 500,
            duration: '1m',
        },
    },
};

// Base URL from environment or default
const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

// Chaos configuration
const CHAOS_SCENARIOS = [
    'network_partition',
    'latency_spike',
    'packet_loss',
    'connection_timeout',
    'bandwidth_throttling',
    'dns_failure'
];

// Test state
let chaosStartTime = 0;
let lastChaosOperation = 0;
let successfulRecoveries = 0;
let systemDownTime = 0;
let baselineMetrics = {};

export function setup() {
    console.log('==========================================');
    console.log('YAWL Chaos Network Test - System Resilience');
    console.log('==========================================');
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Max VUs: 2000`);
    console.log(`Duration: 14 minutes`);
    console.log('Focus: Network failure resilience and recovery');
    console.log('');

    // Initialize service
    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '15s' });
    if (health.status !== 200) {
        throw new Error(`Service unavailable: ${health.status}`);
    }

    // Collect baseline metrics
    baselineMetrics = collectBaselineMetrics();

    return { 
        startTime: Date.now(),
        baselineMetrics: baselineMetrics,
        chaosActive: false,
        scenarioHistory: []
    };
}

export default function(data) {
    const currentTime = Date.now();
    
    // Start chaos testing after 3 minutes
    if (currentTime - data.startTime > 180000 && !data.chaosActive) {
        data.chaosActive = true;
        chaosStartTime = currentTime;
        console.log('🔥 CHAOS TESTING ACTIVATED 🔥');
    }

    const operations = [
        () => testSystemHealth(data),
        () => testWorkflowOperations(data),
        () => testResourceAvailability(data),
        () => testConcurrentOperations(data),
    ];

    // Execute random operation
    const operation = operations[Math.floor(Math.random() * operations.length)];
    const start = Date.now();

    try {
        // Apply chaos conditions if active
        if (data.chaosActive) {
            applyChaosCondition(data);
        }

        const result = operation();
        const duration = Date.now() - start;
        
        // Track system performance
        systemThroughput.add(calculateThroughput(result));
        
        if (data.chaosActive) {
            networkLatency.add(duration);
        }

    } catch (error) {
        // Track error propagation during chaos
        if (data.chaosActive) {
            errorPropagation.add(Date.now() - lastChaosOperation);
            packetLossRate.add(1);
        }
        console.error(`Operation failed: ${error.message}`);
    }

    // Sleep with jitter for realistic load
    const sleepTime = data.chaosActive ? Math.random() * 5 + 1 : Math.random() * 2 + 0.5;
    sleep(sleepTime);
}

function collectBaselineMetrics() {
    console.log('Collecting baseline metrics...');
    
    const baseline = {
        avgResponseTime: 0,
        successRate: 0,
        throughput: 0,
        errorRate: 0
    };

    // Run 10 baseline requests to establish metrics
    let totalLatency = 0;
    let successCount = 0;

    for (let i = 0; i < 10; i++) {
        const start = Date.now();
        const response = http.get(`${BASE_URL}/yawl/ib`, { timeout: '5s' });
        const latency = Date.now() - start;
        
        totalLatency += latency;
        if (response.status === 200) successCount++;
        sleep(0.5);
    }

    baseline.avgResponseTime = totalLatency / 10;
    baseline.successRate = successCount / 10;
    baseline.throughput = successCount / 5; // 5 seconds
    baseline.errorRate = 1 - baseline.successRate;

    console.log(`Baseline metrics collected - Avg RT: ${baseline.avgResponseTime.toFixed(2)}ms, Success Rate: ${(baseline.successRate * 100).toFixed(1)}%`);
    
    return baseline;
}

function applyChaosCondition(data) {
    const scenario = CHAOS_SCENARIOS[Math.floor(Math.random() * CHAOS_SCENARIOS.length)];
    const timestamp = Date.now();
    
    data.scenarioHistory.push({
        time: timestamp,
        scenario: scenario
    });

    lastChaosOperation = timestamp;

    // Simulate various network failures
    switch (scenario) {
        case 'network_partition':
            // Simulate network partition by blocking requests
            if (Math.random() < 0.3) { // 30% chance of failure
                throw new Error('Network partition detected - connection refused');
            }
            break;

        case 'latency_spike':
            // Simulate high latency
            sleep(Math.random() * 3); // 0-3 seconds additional latency
            break;

        case 'packet_loss':
            // Simulate packet loss
            if (Math.random() < 0.2) { // 20% chance of lost request
                throw new Error('Packet loss detected - request timeout');
            }
            break;

        case 'connection_timeout':
            // Simulate connection timeouts
            if (Math.random() < 0.15) { // 15% chance of timeout
                throw new Error('Connection timeout - unreachable host');
            }
            break;

        case 'bandwidth_throttling':
            // Simulate slow response times
            sleep(Math.random() * 2); // 0-2 seconds delay
            break;

        case 'dns_failure':
            // Simulate DNS resolution failures
            if (Math.random() < 0.1) { // 10% chance of DNS failure
                throw new Error('DNS resolution failed - hostname not found');
            }
            break;
    }
}

function testSystemHealth(data) {
    const response = http.get(`${BASE_URL}/actuator/health`, {
        timeout: data.chaosActive ? '10s' : '5s',
    });

    const success = check(response, {
        'system health check ok': (r) => r.status === 200,
    });

    if (data.chaosActive) {
        // Track recovery when chaos stops
        if (!success && Date.now() - chaosStartTime > 300000) { // After 5 minutes of chaos
            successfulRecoveries++;
            recoveryTime.add(Date.now() - chaosStartTime);
        }
    }

    return { success, responseTime: response.timings.duration };
}

function testWorkflowOperations(data) {
    const operations = [
        'launchCase',
        'getWorkItems', 
        'completeWorkItem',
        'getRunningCases'
    ];

    const operation = operations[Math.floor(Math.random() * operations.length)];
    const specId = ['MakeRecordings', 'MakeTrip', 'OrderFulfillment'][Math.floor(Math.random() * 3)];
    const caseId = `chaos_${__VU}_${Date.now()}`;

    let payload = '';
    switch (operation) {
        case 'launchCase':
            payload = `<launchCase><specificationID>${specId}</specificationID><caseID>${caseId}</caseID></launchCase>`;
            break;
        case 'getWorkItems':
            payload = '<getAllWorkItems/>';
            break;
        case 'getRunningCases':
            payload = `<getRunningCases><specificationID>${specId}</specificationID></getRunningCases>`;
            break;
        default:
            payload = `<completeWorkItem><caseID>${caseId}</caseID></completeWorkItem>`;
    }

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers: { 'Content-Type': 'application/xml' },
        timeout: data.chaosActive ? '15s' : '10s',
    });

    check(response, {
        [`${operation} successful`]: (r) => r.status === 200 || r.status === 201,
    });

    return { operation, success: response.status === 200 };
}

function testResourceAvailability(data) {
    const resourceResponse = http.get(`${BASE_URL}/resourceService/rs`, {
        timeout: data.chaosActive ? '8s' : '5s',
    });

    check(resourceResponse, {
        'resource service available': (r) => r.status === 200 || r.status === 401,
    });

    // Test resource allocation under chaos
    if (data.chaosActive && Math.random() < 0.3) {
        const allocationResponse = http.post(`${BASE_URL}/resourceService/rs`,
            `<allocateResources><requestedUnits>3</requestedUnits><timeout>10</timeout></allocateResources>`,
            { timeout: '8s' }
        );

        check(allocationResponse, {
            'resource allocation works': (r) => r.status === 200,
        });
    }

    return { success: resourceResponse.status === 200 };
}

function testConcurrentOperations(data) {
    const concurrentCount = data.chaosActive ? 5 : 3;
    const requests = [];

    for (let i = 0; i < concurrentCount; i++) {
        requests.push({
            method: 'POST',
            url: `${BASE_URL}/yawl/ib`,
            body: '<getSpecificationList/>',
            params: { timeout: data.chaosActive ? '8s' : '5s' },
        });
    }

    const responses = http.batch(requests);
    const successCount = responses.filter(r => r.status === 200).length;

    // Track connection failures
    if (data.chaosActive) {
        const failureCount = concurrentCount - successCount;
        if (failureCount > 0) {
            connectionFailureRate.add(failureCount / concurrentCount);
        }
    }

    check(responses, {
        ['concurrent operations success']: () => successCount >= concurrentCount * 0.6,
    });

    return { 
        success: successCount >= concurrentCount * 0.6,
        throughput: successCount / (concurrentCount * 0.1) 
    };
}

export function teardown(data) {
    const durationSec = (Date.now() - data.startTime) / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('==========================================');
    console.log('Chaos Network Test Complete');
    console.log('==========================================');
    console.log(`Total Duration: ${durationMin} minutes`);
    console.log(`Chaos Active Period: ${data.chaosActive ? 'Yes (5 minutes)' : 'No'}`);
    console.log(`Successful Recoveries: ${successfulRecoveries}`);
    console.log(`System Downtime: ${systemDownTime}ms`);
    console.log('');
    
    console.log('Chaos Resilience Summary:');
    console.log(`  Baseline Avg Response: ${data.baselineMetrics.avgResponseTime.toFixed(2)}ms`);
    console.log(`  Network Latency (avg): ${networkLatency.values.avg.toFixed(2)}ms`);
    console.log(`  Recovery Time (avg): ${recoveryTime.values.avg ? recoveryTime.values.avg.toFixed(2) + 'ms' : 'N/A'}`);
    console.log(`  Packet Loss Rate: ${(packetLossRate.values.rate * 100).toFixed(1)}%`);
    console.log(`  Connection Failure Rate: ${(connectionFailureRate.values.rate * 100).toFixed(1)}%`);
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test_type: 'chaos_network_test',
        max_vus: 2000,
        duration_seconds: data.state.testRunDurationMs / 1000,
        chaos_active: data.chaosActive,
        successful_recoveries: successfulRecoveries,
        system_downtime: systemDownTime,
        chaos_scenarios_applied: data.scenarioHistory.length,
        baseline_metrics: data.baselineMetrics,
        metrics: {
            network_latency_avg: networkLatency.values.avg || 0,
            network_latency_p95: networkLatency.values['p(95)'] || 0,
            network_latency_p99: networkLatency.values['p(99)'] || 0,
            packet_loss_rate: packetLossRate.values.rate || 0,
            connection_failure_rate: connectionFailureRate.values.rate || 0,
            recovery_time_avg: recoveryTime.values.avg || 0,
            recovery_time_p95: recoveryTime.values['p(95)'] || 0,
            error_propagation_avg: errorPropagation.values.avg || 0,
            total_requests: data.metrics.http_reqs?.values.count || 0,
            failed_requests: data.metrics.http_req_failed?.values.rate || 0,
            error_rate: data.metrics.errors?.values.rate || 0,
            avg_response_time: data.metrics.http_req_duration?.values.avg || 0,
            p95_response_time: data.metrics.http_req_duration?.values['p(95)'] || 0,
        },
        chaos_analysis: {
            resilience_score: calculateResilienceScore(data),
            recovery_rate: calculateRecoveryRate(successfulRecoveries, data.scenarioHistory.length),
            system_stability: calculateStability(data),
            chaos_effectiveness: calculateChaosEffectiveness(data),
            scenario_diversity: calculateScenarioDiversity(data.scenarioHistory),
        },
        scenario_breakdown: groupScenariosByType(data.scenarioHistory),
        thresholds_passed: Object.keys(data.metrics)
            .filter(k => data.metrics[k].thresholds)
            .reduce((acc, k) => {
                acc[k] = Object.values(data.metrics[k].thresholds).every(t => t.ok);
                return acc;
            }, {}),
    };

    console.log('');
    console.log('Detailed Chaos Analysis:');
    console.log(`  Resilience Score: ${summary.chaos_analysis.resilience_score}/10`);
    console.log(`  Recovery Rate: ${summary.chaos_analysis.recovery_rate}%`);
    console.log(`  System Stability: ${summary.chaos_analysis.system_stability}%`);
    console.log(`  Chaos Effectiveness: ${summary.chaos_analysis.chaos_effectiveness}%`);
    console.log(`  Scenario Diversity: ${summary.chaos_analysis.scenario_diversity} unique scenarios`);
    console.log(`  Thresholds Passed: ${Object.values(summary.thresholds_passed).filter(v => v).length}/${Object.keys(summary.thresholds_passed).length}`);

    return {
        'stdout': textSummary(data, summary, { indent: ' ', enableColors: true }),
        'k6-chaos-network-summary.json': JSON.stringify(summary, null, 2),
    };
}

function textSummary(data, summary, options) {
    return `
YAWL Chaos Network Test Results
===============================

Duration: ${(data.state.testRunDurationMs / 1000 / 60).toFixed(1)} minutes
Max VUs: 2000
Chaos Testing: ${summary.chaos_active ? 'Active (5 min)' : 'Inactive'}
Successful Recoveries: ${summary.successful_recoveries}
System Downtime: ${summary.system_downtime}ms
Scenarios Applied: ${summary.chaos_scenarios_applied}

Chaos Performance Metrics:
  Network Latency:
    Avg: ${summary.metrics.network_latency_avg.toFixed(2)}ms
    P95: ${summary.metrics.network_latency_p95.toFixed(2)}ms
    P99: ${summary.metrics.network_latency_p99.toFixed(2)}ms
  
  Packet Loss Rate: ${(summary.metrics.packet_loss_rate * 100).toFixed(1)}%
  Connection Failure Rate: ${(summary.metrics.connection_failure_rate * 100).toFixed(1)}%
  Recovery Time: ${summary.metrics.recovery_time_avg ? summary.metrics.recovery_time_avg.toFixed(2) + 'ms' : 'N/A'}
  Error Propagation: ${summary.metrics.error_propagation_avg ? summary.metrics.error_propagation_avg.toFixed(2) + 'ms' : 'N/A'}

Chaos Resilience Analysis:
  Resilience Score: ${summary.chaos_analysis.resilience_score}/10
  Recovery Rate: ${summary.chaos_analysis.recovery_rate}%
  System Stability: ${summary.chaos_analysis.system_stability}%
  Chaos Effectiveness: ${summary.chaos_analysis.chaos_effectiveness}%
  Scenario Diversity: ${summary.chaos_analysis.scenario_diversity} unique

HTTP Metrics:
  Total Requests: ${summary.metrics.total_requests}
  Failed Rate: ${(summary.metrics.failed_requests * 100).toFixed(2)}%
  Error Rate: ${(summary.metrics.error_rate * 100).toFixed(2)}%
  Avg Response Time: ${summary.metrics.avg_response_time.toFixed(2)}ms
  P95 Response Time: ${summary.metrics.p95_response_time.toFixed(2)}ms

Test Status: ${Object.values(summary.thresholds_passed).every(v => v) ? 'PASSED ✓' : 'FAILED ✗'}
`;
}

// Helper functions
function calculateThroughput(result) {
    return result.throughput || 1;
}

function calculateResilienceScore(data) {
    if (!data.chaosActive) return 10; // Perfect score if no chaos
    
    const latencyScore = Math.max(0, 10 - (networkLatency.values.avg / 1000));
    const recoveryScore = Math.min(10, recoveryTime.values.avg / 100);
    const errorScore = Math.max(0, 10 - (errorPropagation.values.avg / 100));
    
    return (latencyScore + recoveryScore + errorScore) / 3;
}

function calculateRecoveryRate(recoveries, scenarios) {
    if (scenarios === 0) return 100;
    return (recoveries / scenarios) * 100;
}

function calculateStability(data) {
    if (!data.chaosActive) return 100;
    
    const successRate = 1 - connectionFailureRate.values.rate;
    return successRate * 100;
}

function calculateChaosEffectiveness(data) {
    if (!data.chaosActive) return 0;
    
    const disruptionLevel = packetLossRate.values.rate + connectionFailureRate.values.rate;
    return Math.min(100, disruptionLevel * 200); // Scale to percentage
}

function calculateScenarioDiversity(scenarios) {
    const uniqueScenarios = new Set(scenarios.map(s => s.scenario));
    return uniqueScenarios.size;
}

function groupScenariosByType(scenarios) {
    const grouped = {};
    scenarios.forEach(s => {
        grouped[s.scenario] = (grouped[s.scenario] || 0) + 1;
    });
    return grouped;
}
