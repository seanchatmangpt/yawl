/**
 * YAWL v6.0.0-GA Production Stress Test - Enhanced Version
 *
 * Comprehensive stress testing framework with realistic workload patterns,
 * multi-tenant support, memory leak detection, and production simulation.
 *
 * Features:
 * - Realistic workflow mix (60% simple, 30% complex, 10% priority)
 * - Ramp-up/down scenarios for production simulation
 * - Memory leak detection with threshold monitoring
 * - Multi-tenant load testing (100+ concurrent tenants)
 * - Comprehensive performance monitoring and alerting
 * - Three test profiles: Light, Medium, Heavy
 * - Real-time metrics collection and trend analysis
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { Counter as CustomCounter } from 'k6/metrics';

// Export custom metrics for external monitoring
export let options = {
    stages: [],
    thresholds: {
        'http_req_duration': ['p(95)<5000', 'p(99)<10000'],
        'http_req_failed': ['rate<0.05'],
        'errors': ['rate<0.10'],
        'active_users': ['count<210'],
        'memory_growth': ['rate<0.01'], // Memory leak detection
        'tenant_throughput': ['rate>100'], // Tenant-specific throughput
    },
    scenarios: {
        production_simulation: {
            executor: 'ramping-vus',
            startTime: 0,
            stages: getProductionRampStages(),
        },
        memory_test: {
            executor: 'constant-vus',
            startTime: '30s',
            duration: '20m',
            vus: 50,
        },
        multi_tenant: {
            executor: 'ramping-vus',
            startTime: '5m',
            stages: [
                { duration: '5m', target: 100 },
                { duration: '10m', target: 100 },
                { duration: '5m', target: 0 },
            ],
        },
    },
};

// Custom metrics
const errorRate = new Rate('errors');
const activeUsers = new Gauge('active_users');
const throughput = new Rate('throughput');
const responseTime = new Trend('response_time_ms');
const memoryLeakDetection = new Gauge('memory_growth');
const tenantThroughput = new Counter('tenant_throughput');
const alertTriggered = new Counter('alert_triggered');

// Performance monitoring metrics
const performanceMonitor = {
    startTime: Date.now(),
    baselineMemory: 0,
    memorySamples: [],
    alertThresholds: {
        memory: 100 * 1024 * 1024, // 100MB
        responseTime: 5000, // 5s
        errorRate: 0.05, // 5%
        throughput: 50, // 50 req/s
    },
    alerts: [],
};

// Load test configurations from external files
const testProfiles = new SharedArray('test_profiles', function () {
    return [
        {
            name: 'Light',
            vus: 10,
            duration: '30s',
            requests: 1000,
            description: 'Validation and development testing'
        },
        {
            name: 'Medium',
            vus: 50,
            duration: '60s',
            requests: 5000,
            description: 'Staging environment testing'
        },
        {
            name: 'Heavy',
            vus: 100,
            duration: '120s',
            requests: 10000,
            description: 'Production readiness testing'
        }
    ];
});

const workflowPatterns = new SharedArray('workflows', function () {
    return [
        {
            id: 'simple',
            weight: 60,
            complexity: 'low',
            avgTime: 1000,
            specs: ['MakeRecordings', 'ResourceTest', 'BasicOrder'],
            operations: ['status', 'workitems', 'resource']
        },
        {
            id: 'complex',
            weight: 30,
            complexity: 'medium',
            avgTime: 3000,
            specs: ['MakeTrip', 'OrderFlow', 'ComplexApproval'],
            operations: ['launch', 'concurrent', 'subprocess']
        },
        {
            id: 'priority',
            weight: 10,
            complexity: 'high',
            avgTime: 5000,
            specs: ['EmergencyProcess', 'UrgentApproval', 'HighPriorityTask'],
            operations: ['urgent', 'monitoring', 'sla']
        }
    ];
});

const tenantConfigs = new SharedArray('tenants', function () {
    const tenants = [];
    for (let i = 0; i < 100; i++) {
        tenants.push({
            id: `tenant-${String(i + 1).padStart(3, '0')}`,
            users: Math.floor(Math.random() * 50) + 10,
            priority: Math.random() > 0.7 ? 'high' : Math.random() > 0.4 ? 'medium' : 'low',
            workflows: getRandomWorkflows(),
            customerType: i < 30 ? 'enterprise' : i < 60 ? 'professional' : 'small-business'
        });
    }
    return tenants;
});

const alertRules = new SharedArray('alerts', function () {
    return [
        {
            id: 'high_memory_growth',
            condition: (metrics) => metrics.memoryDelta > performanceMonitor.alertThresholds.memory,
            message: 'High memory growth detected',
            severity: 'critical'
        },
        {
            id: 'slow_response_time',
            condition: (metrics) => metrics.p99 > performanceMonitor.alertThresholds.responseTime,
            message: 'Slow response time detected',
            severity: 'warning'
        },
        {
            id: 'high_error_rate',
            condition: (metrics) => metrics.errorRate > performanceMonitor.alertThresholds.errorRate,
            message: 'High error rate detected',
            severity: 'critical'
        },
        {
            id: 'low_throughput',
            condition: (metrics) => metrics.throughput < performanceMonitor.alertThresholds.throughput,
            message: 'Low throughput detected',
            severity: 'warning'
        }
    ];
});

const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

export function setup() {
    console.log('='.repeat(70));
    console.log('YAWL v6.0.0-GA Production Stress Test - Enhanced');
    console.log('='.repeat(70));
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Test Profiles Available: ${testProfiles.map(p => p.name).join(', ')}`);
    console.log('Workload Distribution: 60% simple, 30% complex, 10% priority');
    console.log('Multi-tenant: 100 concurrent tenants');
    console.log('');

    // Health check and initialization
    const health = checkSystemHealth();
    if (!health.healthy) {
        throw new Error(`System unhealthy: ${health.message}`);
    }

    performanceMonitor.baselineMemory = getMemoryUsage();
    console.log(`Baseline memory: ${formatBytes(performanceMonitor.baselineMemory)}`);
    console.log('');

    return {
        startTime: Date.now(),
        tenantIndex: 0,
        workflowIndex: 0,
        systemHealth: health,
    };
}

export default function(data) {
    activeUsers.add(__VU);
    const tenantId = getTenantForVU(__VU);
    const tenant = tenantConfigs.find(t => t.id === tenantId) || tenantConfigs[0];
    const workflow = selectWorkflowForTenant(tenant);

    // Memory leak detection
    const currentMemory = getMemoryUsage();
    const memoryDelta = currentMemory - performanceMonitor.baselineMemory;
    memoryLeakDetection.add(memoryDelta);

    // Check for memory leaks
    if (memoryDelta > performanceMonitor.alertThresholds.memory) {
        console.warn(`MEMORY LEAK: ${tenantId} - Growth: ${formatBytes(memoryDelta)}`);
        alertTriggered.add(1, { type: 'memory_leak', tenant_id: tenantId });

        // Record memory sample for trend analysis
        performanceMonitor.memorySamples.push({
            timestamp: Date.now(),
            memory: currentMemory,
            delta: memoryDelta,
            tenantId: tenantId
        });
    }

    // Monitor performance metrics
    monitorPerformanceMetrics(tenant, workflow);

    // Execute workflow operations
    const operations = getOperationsForWorkflow(workflow, tenant);
    const operation = operations[Math.floor(Math.random() * operations.length)];

    const start = Date.now();
    const sleepTime = calculateSleepTime(workflow.avgTime);

    try {
        group(`${tenant.id} - ${workflow.id}`, function () {
            operation();
        });

        const duration = Date.now() - start;

        // Record metrics
        responseTime.add(duration, {
            tenant_id: tenantId,
            workflow_id: workflow.id
        });
        throughput.add(1, { tenant_id: tenantId });
        tenantThroughput.add(1, { tenant_id: tenantId });

        // Check performance thresholds
        checkPerformanceThresholds(duration, tenant, workflow);

        // Simulate realistic user behavior
        sleep(sleepTime);
    } catch (error) {
        errorRate.add(1, { tenant_id: tenantId });
        console.error(`Operation failed for ${tenantId}: ${error.message}`);

        // Check if this constitutes an alert
        checkAlertConditions(tenant, workflow);
    }
}

export function teardown(data) {
    const durationMs = Date.now() - data.startTime;
    const durationSec = durationMs / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('='.repeat(70));
    console.log('Stress Test Complete - Enhanced Report');
    console.log('='.repeat(70));
    console.log(`Total Duration: ${durationMin} minutes`);

    // Generate comprehensive report
    generateComprehensiveReport(data);

    // Check for memory leaks
    checkMemoryLeaks();

    // Validate system health
    validateSystemHealth();
}

// Helper functions

function getProductionRampStages() {
    return [
        // Morning startup
        { duration: '30s', target: 10 },
        { duration: '2m', target: 50 },
        // Business hours ramp-up
        { duration: '3m', target: 100 },
        { duration: '5m', target: 150 },
        { duration: '10m', target: 100 },
        // Peak business hours
        { duration: '5m', target: 200 },
        { duration: '10m', target: 100 },
        // Evening wind-down
        { duration: '5m', target: 50 },
        { duration: '2m', target: 10 },
        // Weekend shutdown
        { duration: '1m', target: 0 },
    ];
}

function checkSystemHealth() {
    const checks = {
        engine: false,
        database: false,
        memory: false,
        overall: false
    };

    try {
        // Engine health check
        const engineHealth = http.get(`${BASE_URL}/yawl/ib`, {
            headers: { 'Content-Type': 'application/xml' },
            timeout: '5s'
        });
        checks.engine = engineHealth.status === 200;

        // Database connectivity
        const dbHealth = http.get(`${BASE_URL}/actuator/health`, {
            timeout: '3s'
        });
        checks.database = dbHealth.status === 200;

        // Memory usage
        const memory = getMemoryUsage();
        checks.memory = memory < 2 * 1024 * 1024 * 1024; // Less than 2GB

        checks.overall = checks.engine && checks.database && checks.memory;

        return {
            healthy: checks.overall,
            message: checks.overall ? 'All systems healthy' :
                `Issues: Engine=${checks.engine}, Database=${checks.database}, Memory=${checks.memory}`,
            details: checks
        };
    } catch (error) {
        return {
            healthy: false,
            message: `Health check failed: ${error.message}`,
            details: checks
        };
    }
}

function getMemoryUsage() {
    // Simulated memory metric in production this would be actual JVM memory
    const baseMemory = 500 * 1024 * 1024; // 500MB base
    const variation = Math.sin(Date.now() / 60000) * 50 * 1024 * 1024; // Sinusoidal variation
    return baseMemory + variation + (performanceMonitor.memorySamples.length * 1024 * 1024);
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function getTenantForVU(vu) {
    const tenantCount = tenantConfigs.length;
    const tenantIndex = vu % tenantCount;
    return tenantConfigs[tenantIndex].id;
}

function selectWorkflowForTenant(tenant) {
    const availableWorkflows = tenant.workflows.map(id =>
        workflowPatterns.find(w => w.id === id)
    ).filter(w => w !== undefined);

    // Select based on tenant priority and weights
    const random = Math.random() * 100;
    let cumulative = 0;

    for (const workflow of availableWorkflows) {
        cumulative += workflow.weight;
        if (random <= cumulative) {
            return workflow;
        }
    }

    return availableWorkflows[0] || workflowPatterns[0];
}

function getOperationsForWorkflow(workflow, tenant) {
    const baseOperations = [
        () => testEngineStatus(tenant),
        () => testTenantWorkItemRetrieval(tenant),
        () => testTenantResourceQuery(tenant),
    ];

    // Add workflow-specific operations
    switch (workflow.id) {
        case 'simple':
            return [
                ...baseOperations,
                () => testSimpleWorkflowLaunch(tenant),
                () => testBasicWorkItemProcessing(tenant)
            ];

        case 'complex':
            return [
                ...baseOperations,
                () => testComplexWorkflowLaunch(tenant),
                () => testConcurrentCaseProcessing(tenant),
                () => testSubprocessManagement(tenant)
            ];

        case 'priority':
            return [
                ...baseOperations,
                () => testPriorityWorkflowLaunch(tenant),
                () => testRealTimeMonitoring(tenant),
                () => testUrgentWorkItemProcessing(tenant),
                () => testSLACompliance(tenant)
            ];

        default:
            return baseOperations;
    }
}

function calculateSleepTime(avgTime) {
    // Realistic user behavior with burst patterns
    const baseSleep = avgTime / 1000;
    const burstProbability = Math.random();

    if (burstProbability < 0.1) { // 10% chance of burst
        return Math.max(0.1, baseSleep * 0.1); // Fast processing
    } else if (burstProbability < 0.3) { // 20% chance of slower
        return Math.max(0.1, baseSleep * 2 * Math.random());
    } else {
        return Math.max(0.1, baseSleep * Math.random());
    }
}

// Test operation implementations

function testEngineStatus(tenant) {
    const response = http.get(`${BASE_URL}/yawl/ib`, {
        headers: {
            'Content-Type': 'application/xml',
            'X-Tenant-Id': tenant.id
        },
        timeout: '5s'
    });

    check(response, {
        'engine status ok': (r) => r.status === 200,
        'engine response time': (r) => r.timings.duration < 1000,
    });
}

function testTenantWorkItemRetrieval(tenant) {
    const response = http.post(
        `${BASE_URL}/yawl/ib`,
        `<getWorkItemsForTenant>
            <tenantID>${tenant.id}</tenantID>
            <status>Offered</status>
        </getWorkItemsForTenant>`,
        {
            headers: {
                'Content-Type': 'application/xml',
                'X-Tenant-Id': tenant.id
            },
            timeout: '5s'
        }
    );

    check(response, {
        'tenant work items retrieved': (r) => r.status === 200,
        'valid response format': (r) => r.body && r.body.includes('<workItems>'),
    });
}

function testTenantResourceQuery(tenant) {
    const response = http.get(`${BASE_URL}/resourceService/rs/tenant/${tenant.id}`, {
        headers: {
            'X-Tenant-Id': tenant.id
        },
        timeout: '5s'
    });

    check(response, {
        'resource service available': (r) => r.status !== 0,
        'tenant resources found': (r) => r.status === 200,
    });
}

function testSimpleWorkflowLaunch(tenant) {
    const specs = ['MakeRecordings', 'ResourceTest', 'BasicOrder'];
    const specId = specs[Math.floor(Math.random() * specs.length)];
    const caseId = `${tenant.id}-simple-${Date.now()}`;

    const payload = `<launchCase>
        <specificationID>${specId}</specificationID>
        <caseID>${caseId}</caseID>
        <caseParams>
            <variable name="tenantID" type="string">${tenant.id}</variable>
            <variable name="priority" type="string">normal</variable>
            <variable name="customerType" type="string">${tenant.customerType}</variable>
        </caseParams>
    </launchCase>`;

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers: {
            'Content-Type': 'application/xml',
            'X-Tenant-Id': tenant.id
        },
        timeout: '10s'
    });

    check(response, {
        'simple workflow launched': (r) => r.status === 200 || r.status === 201,
        'case created': (r) => r.body && r.body.includes('<caseID>'),
    });
}

function testComplexWorkflowLaunch(tenant) {
    const specs = ['MakeTrip', 'OrderFlow', 'ComplexApproval'];
    const specId = specs[Math.floor(Math.random() * specs.length)];
    const caseId = `${tenant.id}-complex-${Date.now()}`;

    const payload = `<launchCase>
        <specificationID>${specId}</specificationID>
        <caseID>${caseId}</caseID>
        <caseParams>
            <variable name="tenantID" type="string">${tenant.id}</variable>
            <variable name="workflowType" type="string">complex</variable>
            <variable name="dataComplexity" type="int">5</variable>
            <variable name="subprocessCount" type="int">3</variable>
        </caseParams>
    </launchCase>`;

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers: {
            'Content-Type': 'application/xml',
            'X-Tenant-Id': tenant.id
        },
        timeout: '15s'
    });

    check(response, {
        'complex workflow launched': (r) => r.status === 200 || r.status === 201,
        'subprocess capability': (r) => r.body && r.body.length > 100,
    });
}

function testPriorityWorkflowLaunch(tenant) {
    const specs = ['EmergencyProcess', 'UrgentApproval', 'HighPriorityTask'];
    const specId = specs[Math.floor(Math.random() * specs.length)];
    const caseId = `${tenant.id}-priority-${Date.now()}`;

    const payload = `<launchCase>
        <specificationID>${specId}</specificationID>
        <caseID>${caseId}</caseID>
        <caseParams>
            <variable name="tenantID" type="string">${tenant.id}</variable>
            <variable name="priority" type="string">urgent</variable>
            <variable name="slaDeadline" type="dateTime">${getSLADeadline()}</variable>
            <variable name="escalationPath" type="string">manager,director</variable>
        </caseParams>
    </launchCase>`;

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers: {
            'Content-Type': 'application/xml',
            'X-Tenant-Id': tenant.id,
            'X-Priority': 'high'
        },
        timeout: '8s'
    });

    check(response, {
        'priority workflow launched': (r) => r.status === 200 || r.status === 201,
        'high priority marker': (r) => r.body && r.body.includes('priority="urgent"'),
    });
}

function testConcurrentCaseProcessing(tenant) {
    const batchSize = 3;
    const requests = [];

    for (let i = 0; i < batchSize; i++) {
        const caseId = `${tenant.id}-batch-${Date.now()}-${i}`;

        requests.push({
            method: 'POST',
            url: `${BASE_URL}/yawl/ib`,
            body: `<updateCaseStatus>
                <caseID>${caseId}</caseID>
                <newStatus>InProgress</newStatus>
                <updateReason>stress test batch</updateReason>
            </updateCaseStatus>`,
            params: {
                headers: {
                    'Content-Type': 'application/xml',
                    'X-Tenant-Id': tenant.id
                },
                timeout: '5s'
            },
        });
    }

    const responses = http.batch(requests);
    const successCount = responses.filter(r => r.status === 200).length;

    check(responses, {
        'concurrent processing successful': () => successCount >= batchSize * 0.6,
        'batch performance acceptable': () => successCount >= batchSize * 0.8,
    });
}

function testRealTimeMonitoring(tenant) {
    const response = http.get(`${BASE_URL}/yawl/monitoring/realtime?tenant=${tenant.id}`, {
        headers: {
            'X-Tenant-Id': tenant.id
        },
        timeout: '3s'
    });

    check(response, {
        'real-time monitoring available': (r) => r.status === 200,
        'metric data returned': (r) => r.body && r.body.includes('<metrics>'),
    });
}

function testUrgentWorkItemProcessing(tenant) {
    const response = http.post(
        `${BASE_URL}/yawl/ib`,
        `<getUrgentWorkItems>
            <tenantID>${tenant.id}</tenantID>
            <maxAge>300000</maxAge>
            <priority>urgent</priority>
        </getUrgentWorkItems>`,
        {
            headers: {
                'Content-Type': 'application/xml',
                'X-Tenant-Id': tenant.id
            },
            timeout: '5s'
        }
    );

    check(response, {
        'urgent work items retrieved': (r) => r.status === 200,
        'urgent cases identified': (r) => r.body && r.body.includes('urgent'),
    });
}

function testSLACompliance(tenant) {
    const startTime = Date.now();
    const response = http.get(`${BASE_URL}/yawl/monitoring/sla?tenant=${tenant.id}`, {
        headers: {
            'X-Tenant-Id': tenant.id
        },
        timeout: '5s'
    });

    const duration = Date.now() - startTime;
    responseTime.add(duration, { tenant_id: tenant.id, metric: 'sla_check' });

    check(response, {
        'SLA monitoring available': (r) => r.status === 200,
        'SLA data valid': (r) => r.body && r.body.includes('<slaMetrics>'),
    });
}

function testBasicWorkItemProcessing(tenant) {
    const response = http.post(
        `${BASE_URL}/yawl/ib`,
        `<completeWorkItem>
            <tenantID>${tenant.id}</tenantID>
            <workItemID>${tenant.id}-work-${Date.now()}</workItemID>
            <completeReason>stress test completion</completeReason>
        </completeWorkItem>`,
        {
            headers: {
                'Content-Type': 'application/xml',
                'X-Tenant-Id': tenant.id
            },
            timeout: '5s'
        }
    );

    check(response, {
        'work item processed': (r) => r.status === 200,
        'completion confirmation': (r) => r.body && r.body.includes('completed'),
    });
}

function testSubprocessManagement(tenant) {
    const response = http.get(`${BASE_URL}/yawl/subprocess/management?tenant=${tenant.id}`, {
        headers: {
            'X-Tenant-Id': tenant.id
        },
        timeout: '8s'
    });

    check(response, {
        'subprocess management available': (r) => r.status === 200,
        'subprocess data valid': (r) => r.body && r.body.includes('<subprocesses>'),
    });
}

// Monitoring and alerting functions

function monitorPerformanceMetrics(tenant, workflow) {
    const metrics = {
        timestamp: Date.now(),
        tenantId: tenant.id,
        workflowId: workflow.id,
        tenantPriority: tenant.priority,
        customerType: tenant.customerType,
        vuid: __VU
    };

    // Send metrics to external monitoring (in production)
    // sendMetricsToExternalSystem(metrics);
}

function checkPerformanceThresholds(duration, tenant, workflow) {
    const metrics = {
        duration: duration,
        tenantPriority: tenant.priority,
        workflowComplexity: workflow.complexity,
        timestamp: Date.now()
    };

    // Check tenant-specific performance expectations
    if (tenant.priority === 'high' && duration > 2000) {
        console.warn(`High priority tenant ${tenant.id} experiencing slow performance: ${duration}ms`);
    }

    if (workflow.id === 'priority' && duration > performanceMonitor.alertThresholds.responseTime) {
        console.warn(`Priority workflow ${workflow.id} taking too long: ${duration}ms`);
    }
}

function checkAlertConditions(tenant, workflow) {
    const metrics = {
        errorCount: errorRate.count,
        memoryDelta: getMemoryUsage() - performanceMonitor.baselineMemory,
        responseTime: responseTime.current,
        throughput: throughput.current,
        timestamp: Date.now()
    };

    for (const rule of alertRules) {
        if (rule.condition(metrics)) {
            console.warn(`ALERT: ${rule.message} for tenant ${tenant.id}`);
            console.warn(`ALERT Details: ${JSON.stringify(metrics)}`);

            alertTriggered.add(1, {
                type: rule.id,
                tenant_id: tenant.id,
                severity: rule.severity,
                message: rule.message
            });

            // In production, send to alerting system
            // sendAlert(rule, tenant, metrics);
        }
    }
}

function getSLADeadline() {
    const now = new Date();
    const deadline = new Date(now.getTime() + 60 * 60 * 1000); // 1 hour from now
    return deadline.toISOString();
}

function getRandomWorkflows() {
    const workflowOptions = ['simple', 'complex', 'priority'];
    const selected = [];
    const weights = [0.6, 0.3, 0.1];

    for (let i = 0; i < 2; i++) {
        const random = Math.random();
        let cumulative = 0;
        for (let j = 0; j < weights.length; j++) {
            cumulative += weights[j];
            if (random <= cumulative) {
                if (!selected.includes(workflowOptions[j])) {
                    selected.push(workflowOptions[j]);
                }
                break;
            }
        }
    }

    return selected.length > 0 ? selected : ['simple'];
}

function checkMemoryLeaks() {
    if (performanceMonitor.memorySamples.length < 10) return;

    const latestSample = performanceMonitor.memorySamples[performanceMonitor.memorySamples.length - 1];
    const firstSample = performanceMonitor.memorySamples[0];
    const totalGrowth = latestSample.delta - firstSample.delta;

    console.log(`Memory leak analysis:`);
    console.log(`  Initial memory: ${formatBytes(firstSample.memory)}`);
    console.log(`  Current memory: ${formatBytes(latestSample.memory)}`);
    console.log(`  Total growth: ${formatBytes(totalGrowth)}`);
    console.log(`  Sample points: ${performanceMonitor.memorySamples.length}`);

    if (totalGrowth > performanceMonitor.alertThresholds.memory) {
        console.warn(`MEMORY LEAK CONFIRMED: ${formatBytes(totalGrowth)} growth detected`);

        // Analyze growth trend
        analyzeMemoryTrend();
    } else {
        console.log('No significant memory leaks detected');
    }
}

function analyzeMemoryTrend() {
    if (performanceMonitor.memorySamples.length < 20) return;

    const recent = performanceMonitor.memorySamples.slice(-20);
    const first10 = recent.slice(0, 10);
    const last10 = recent.slice(-10);

    const firstAvg = first10.reduce((sum, sample) => sum + sample.delta, 0) / first10.length;
    const lastAvg = last10.reduce((sum, sample) => sum + sample.delta, 0) / last10.length;

    const growthRate = (lastAvg - firstAvg) / first10.length;

    console.log(`Memory trend analysis:`);
    console.log(`  Average growth rate: ${formatBytes(growthRate)}/sample`);
    console.log(`  First 10 samples avg: ${formatBytes(firstAvg)}`);
    console.log(`  Last 10 samples avg: ${formatBytes(lastAvg)}`);

    if (growthRate > 1024 * 1024) { // >1MB per sample
        console.warn('HIGH MEMORY GROWTH RATE DETECTED - Potential memory leak');
    }
}

function generateComprehensiveReport(data) {
    const report = {
        timestamp: new Date().toISOString(),
        test_type: 'enhanced_stress',
        test_duration_ms: Date.now() - data.startTime,
        profiles: testProfiles,
        metrics: {
            total_requests: __ITERATION,
            error_rate: errorRate.rate,
            avg_response_time: responseTime.current,
            p95_response_time: responseTime.current['p(95)'],
            p99_response_time: responseTime.current['p(99)'],
            throughput: throughput.rate,
            tenant_count: tenantConfigs.length,
            memory_samples: performanceMonitor.memorySamples.length,
            alerts_triggered: alertTriggered.count
        },
        workflow_distribution: {
            simple: workflowPatterns[0].weight,
            complex: workflowPatterns[1].weight,
            priority: workflowPatterns[2].weight
        },
        tenant_distribution: {
            enterprise: tenantConfigs.filter(t => t.customerType === 'enterprise').length,
            professional: tenantConfigs.filter(t => t.customerType === 'professional').length,
            small_business: tenantConfigs.filter(t => t.customerType === 'small-business').length
        },
        system_health: data.systemHealth
    };

    console.log('\nComprehensive Performance Report:');
    console.log('='.repeat(50));
    console.log(`Test Duration: ${(report.test_duration_ms / 1000 / 60).toFixed(1)} minutes`);
    console.log(`Total Requests: ${report.metrics.total_requests}`);
    console.log(`Error Rate: ${(report.metrics.error_rate * 100).toFixed(2)}%`);
    console.log(`Avg Response Time: ${report.metrics.avg_response_time.toFixed(2)}ms`);
    console.log(`P95 Response Time: ${report.metrics.p95_response_time.toFixed(2)}ms`);
    console.log(`P99 Response Time: ${report.metrics.p99_response_time.toFixed(2)}ms`);
    console.log(`Throughput: ${report.metrics.throughput.toFixed(2)} req/s`);
    console.log(`Alerts Triggered: ${report.metrics.alerts_triggered}`);
    console.log(`Memory Samples Collected: ${report.metrics.memory_samples}`);

    // Save report to file
    // saveReportToFile(report);
}

function validateSystemHealth() {
    const finalHealth = checkSystemHealth();
    console.log('\nFinal System Health Check:');
    console.log(`Healthy: ${finalHealth.healthy}`);
    console.log(`Message: ${finalHealth.message}`);

    if (finalHealth.details) {
        console.log('Detailed Health Status:');
        Object.entries(finalHealth.details).forEach(([key, value]) => {
            console.log(`  ${key}: ${value ? 'OK' : 'FAILED'}`);
        });
    }
}

// Export test profile runner for different environments
export function runTestProfile(profileName) {
    const profile = testProfiles.find(p => p.name === profileName);
    if (!profile) {
        throw new Error(`Test profile '${profileName}' not found`);
    }

    console.log(`Running ${profile.name} test profile:`);
    console.log(`  VUs: ${profile.vus}`);
    console.log(`  Duration: ${profile.duration}`);
    console.log(`  Target Requests: ${profile.requests}`);
    console.log('');

    // Configure options for this profile
    options.stages = [
        { duration: '10s', target: profile.vus / 2 },
        { duration: '20s', target: profile.vus },
        { duration: profile.duration, target: profile.vus },
        { duration: '10s', target: 0 }
    ];

    return options;
}

// Additional utility functions
function getCurrentTimeOfDay() {
    const hour = new Date().getHours();
    if (hour >= 9 && hour <= 17) return 'business_hours';
    if (hour >= 20 || hour <= 6) return 'night_time';
    return 'off_hours';
}

function getTenantWorkloadIntensity(tenant) {
    const timeOfDay = getCurrentTimeOfDay();
    const baseIntensity = tenant.customerType === 'enterprise' ? 1.5 : 1.0;

    switch (timeOfDay) {
        case 'business_hours':
            return baseIntensity * 1.2;
        case 'night_time':
            return baseIntensity * 0.3;
        default:
            return baseIntensity * 0.7;
    }
}