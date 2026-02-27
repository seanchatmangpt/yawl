/**
 * YAWL Polyglot Workload Test
 * Mixed Java/Python workflow performance testing
 * Version: 6.0
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const javaResponseTime = new Trend('java_response_time');
const pythonResponseTime = new Trend('python_response_time');
const workflowTypeCount = new Counter('workflow_type_switches');
const languageMix = new Gauge('language_distribution');
const polyglotComplexity = new Trend('polyglot_complexity');

// Test configuration
export const options = {
    stages: [
        { duration: '2m', target: 1000 },   // Warm up
        { duration: '4m', target: 2500 },  // Ramp to 2.5K users
        { duration: '8m', target: 5000 },  // Ramp to 5K users
        { duration: '15m', target: 5000 }, // Hold at 5K
        { duration: '4m', target: 2500 },  // Step down
        { duration: '2m', target: 0 },      // Cool down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1500', 'p(99)<4000'],
        'http_req_failed': ['rate<0.03'],
        'java_response_time': ['p(95)<1000'],
        'python_response_time': ['p(95)<1200'],
        'errors': ['rate<0.05'],
    },
};

// Base URL from environment or default
const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

// Polyglot workflow specifications
const JAVA_WORKFLOWS = [
    'MakeRecordings',
    'OrderFulfillment', 
    'ResourceAllocation',
    'ComplianceCheck',
    'ContractReview'
];

const PYTHON_WORKFLOWS = [
    'MLModelTraining',
    'DataProcessing',
    'AIBotIntegration',
    'AnalyticsWorkflow',
    'MLInference'
];

// Language-specific operations
const JAVA_OPERATIONS = [
    'status_check',
    'launch_workflow', 
    'get_work_items',
    'resource_allocation',
    'compliance_validation'
];

const PYTHON_OPERATIONS = [
    'model_training',
    'data_processing',
    'ml_inference',
    'analytics_query',
    'bot_command'
];

// Test state
let workflowTypeIndex = 0;
let javaOperationCount = 0;
let pythonOperationCount = 0;
let lastSwitch = Date.now();

export function setup() {
    console.log('==========================================');
    console.log('YAWL Polyglot Workload Test - Java/Python Mix');
    console.log('==========================================');
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Max VUs: 5000`);
    console.log(`Duration: 35 minutes`);
    console.log('Workload: Mixed Java business + Python AI workflows');
    console.log('');

    // Initialize service
    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Service unavailable: ${health.status}`);
    }

    return { 
        startTime: Date.now(),
        switchInterval: 180000, // 3 minutes per workflow type
        lastSwitch: Date.now()
    };
}

export default function(data) {
    const now = Date.now();
    
    // Switch between Java and Python workflows
    if (now - data.lastSwitch > data.switchInterval) {
        workflowTypeIndex = (workflowTypeIndex + 1) % 2;
        data.lastSwitch = now;
        workflowTypeCount.add(1);
        
        const currentType = workflowTypeIndex === 0 ? 'Java' : 'Python';
        console.log(`Switched to ${currentType} workflow mode`);
    }

    const isJavaMode = workflowTypeIndex === 0;
    const currentWorkflowType = isJavaMode ? 'Java' : 'Python';
    const workflows = isJavaMode ? JAVA_WORKFLOWS : PYTHON_WORKFLOWS;
    const operations = isJavaMode ? JAVA_OPERATIONS : PYTHON_OPERATIONS;
    
    const headers = {
        'Content-Type': 'application/xml',
        'Accept': 'application/xml',
        'X-Language-Environment': currentWorkflowType.toLowerCase(),
    };

    // Test operations with language-specific patterns
    const testOperations = [
        () => testLanguageSpecificOperation(currentWorkflowType, operations, headers),
        () => testWorkflowLaunch(currentWorkflowType, workflows, headers),
        () => testLanguageOptimizations(currentWorkflowType, headers),
        () => testPolyglotInteroperability(currentWorkflowType, headers),
        () => testResourceManagement(currentWorkflowType, headers),
    ];

    // Execute random operation
    const operation = testOperations[Math.floor(Math.random() * testOperations.length)];
    const start = Date.now();

    try {
        const complexity = operation();
        const duration = Date.now() - start;
        
        // Track response time by language
        if (isJavaMode) {
            javaResponseTime.add(duration);
            javaOperationCount += 1;
        } else {
            pythonResponseTime.add(duration);
            pythonOperationCount += 1;
        }

        // Track complexity
        if (complexity) {
            polyglotComplexity.add(complexity);
        }

        // Update language distribution
        const ratio = javaOperationCount / (javaOperationCount + pythonOperationCount) || 0;
        languageMix.add(ratio);
    } catch (error) {
        console.error(`Operation failed for ${currentWorkflowType} workflow: ${error.message}`);
    }

    // Variable sleep based on language
    const sleepTime = isJavaMode ? Math.random() * 2 : Math.random() * 3;
    sleep(sleepTime);
}

function testLanguageSpecificOperation(languageType, operations, headers) {
    const operation = operations[Math.floor(Math.random() * operations.length)];
    const response = http.get(`${BASE_URL}/yawl/ib`, {
        headers,
        timeout: '3s',
    });

    const success = check(response, {
        [`${operation} ok`]: (r) => r.status === 200,
        [`${languageType} environment`]: (r) => r.body.includes(languageType.toLowerCase()),
    });

    // Return complexity score (1-10)
    return operation === 'model_training' ? 9 : operation === 'compliance_validation' ? 7 : 5;
}

function testWorkflowLaunch(languageType, workflows, headers) {
    const specId = workflows[Math.floor(Math.random() * workflows.length)];
    const caseId = `${languageType.toLowerCase()}_${__VU}_${Date.now()}`;

    const launchStart = Date.now();
    const payload = `<launchCase>
        <specificationID>${specId}</specificationID>
        <caseID>${caseId}</caseID>
        <caseParams>
            <param name="language" value="${languageType}"/>
            <param name="timestamp" value="${Date.now()}"/>
        </caseParams>
    </launchCase>`;

    const response = http.post(`${BASE_URL}/yawl/ib`, payload, {
        headers,
        timeout: '20s',
    });

    const duration = Date.now() - launchStart;

    const success = check(response, {
        'workflow launched': (r) => r.status === 200 || r.status === 201,
        'correct language environment': (r) => r.body.includes(languageType.toLowerCase()),
    });

    if (!success) {
        throw new Error(`Workflow launch failed for ${languageType}`);
    }

    return duration;
}

function testLanguageOptimizations(languageType, headers) {
    group(`Language Optimizations - ${languageType}`, function() {
        // Java-specific optimizations
        if (languageType === 'Java') {
            // Test JIT compilation effects
            const jitResponse = http.post(`${BASE_URL}/yawl/ib`,
                '<getOptimizationStats><type>JIT</type></getOptimizationStats>',
                { headers, timeout: '5s' }
            );
            
            check(jitResponse, {
                'JIT stats available': (r) => r.status === 200,
            });

            // Test garbage collection metrics
            const gcResponse = http.post(`${BASE_URL}/yawl/ib`,
                '<getGCMetrics/><language>java</language>',
                { headers, timeout: '5s' }
            );

            check(gcResponse, {
                'GC metrics available': (r) => r.status === 200,
            });
        }

        // Python-specific optimizations
        if (languageType === 'Python') {
            // Test Python interpreter optimization
            const pyResponse = http.post(`${BASE_URL}/yawl/ib`,
                '<getOptimizationStats><type>PYTHON</type></getOptimizationStats>',
                { headers, timeout: '5s' }
            );

            check(pyResponse, {
                'Python optimizations available': (r) => r.status === 200,
            });

            // Test numpy/scipy integration
            const numpyResponse = http.post(`${BASE_URL}/yawl/ib`,
                '<getMLCapabilities><library>numpy</library></getMLCapabilities>',
                { headers, timeout: '5s' }
            );

            check(numpyResponse, {
                'numpy integration available': (r) => r.status === 200,
            });
        }
    });

    return 8; // High complexity
}

function testPolyglotInteroperability(languageType, headers) {
    group('Cross-Language Interoperability', function() {
        // Test Java calling Python services
        const javaToPython = http.post(`${BASE_URL}/yawl/ib`,
            '<interoperabilityTest><from>java</from><to>python</to><operation>ml_inference</operation></interoperabilityTest>',
            { headers, timeout: '10s' }
        );

        check(javaToPython, {
            'java-to-python interoperable': (r) => r.status === 200,
        });

        // Test Python calling Java services
        const pythonToJava = http.post(`${BASE_URL}/yawl/ib`,
            '<interoperabilityTest><from>python</from><to>java</to><operation>compliance_check</operation></interoperabilityTest>',
            { headers, timeout: '10s' }
        );

        check(pythonToJava, {
            'python-to-java interoperable': (r) => r.status === 200,
        });

        // Test shared resource access
        const sharedResource = http.post(`${BASE_URL}/yawl/ib`,
            '<getSharedResources><language>*</language></getSharedResources>',
            { headers, timeout: '5s' }
        );

        check(sharedResource, {
            'shared resources accessible': (r) => r.status === 200,
        });
    });

    return 10; // Highest complexity
}

function testResourceManagement(languageType, headers) {
    // Test language-specific resource allocation
    const resourceResponse = http.post(`${BASE_URL}/resourceService/rs`,
        `<allocateResources>
            <language>${languageType}</language>
            <requestedUnits>5</requestedUnits>
            <timeout>30</timeout>
        </allocateResources>`,
        { headers, timeout: '10s' }
    );

    check(resourceResponse, {
        'resources allocated': (r) => r.status === 200,
    });

    // Monitor resource utilization
    const monitorResponse = http.get(`${BASE_URL}/resourceService/rs/monitor`, {
        headers: { 'Accept': 'application/json' },
        timeout: '5s',
    });

    check(monitorResponse, {
        'resource monitoring available': (r) => r.status === 200,
    });

    return 6; // Medium complexity
}

export function teardown(data) {
    const durationSec = (Date.now() - data.startTime) / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('==========================================');
    console.log('Polyglot Workload Test Complete');
    console.log('==========================================');
    console.log(`Total Duration: ${durationMin} minutes`);
    console.log(`Workflow Type Switches: ${workflowTypeCount.values.count}`);
    console.log('');
    
    console.log('Language Performance Summary:');
    console.log(`  Java Operations: ${javaOperationCount}`);
    console.log(`  Python Operations: ${pythonOperationCount}`);
    console.log(`  Java Avg Response Time: ${javaResponseTime.values.avg.toFixed(2)}ms`);
    console.log(`  Python Avg Response Time: ${pythonResponseTime.values.avg.toFixed(2)}ms`);
    console.log(`  Language Distribution: ${((javaOperationCount/(javaOperationCount + pythonOperationCount)) * 100).toFixed(1)}% Java`);
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test_type: 'polyglot_workload_test',
        max_vus: 5000,
        duration_seconds: data.state.testRunDurationMs / 1000,
        workflow_type_switches: workflowTypeCount.values.count,
        java_operations: javaOperationCount,
        python_operations: pythonOperationCount,
        metrics: {
            java_response_time_avg: javaResponseTime.values.avg || 0,
            java_response_time_p95: javaResponseTime.values['p(95)'] || 0,
            python_response_time_avg: pythonResponseTime.values.avg || 0,
            python_response_time_p95: pythonResponseTime.values['p(95)'] || 0,
            polyglot_complexity_avg: polyglotComplexity.values.avg || 0,
            total_requests: data.metrics.http_reqs?.values.count || 0,
            failed_requests: data.metrics.http_req_failed?.values.rate || 0,
            error_rate: data.metrics.errors?.values.rate || 0,
            avg_response_time: data.metrics.http_req_duration?.values.avg || 0,
            p95_response_time: data.metrics.http_req_duration?.values['p(95)'] || 0,
        },
        language_comparison: {
            java_ratio: javaOperationCount / (javaOperationCount + pythonOperationCount),
            python_ratio: pythonOperationCount / (javaOperationCount + pythonOperationCount),
            performance_gap: (pythonResponseTime.values.avg || 0) - (javaResponseTime.values.avg || 0),
            complexity_score: polyglotComplexity.values.avg || 0,
        },
        thresholds_passed: Object.keys(data.metrics)
            .filter(k => data.metrics[k].thresholds)
            .reduce((acc, k) => {
                acc[k] = Object.values(data.metrics[k].thresholds).every(t => t.ok);
                return acc;
            }, {}),
    };

    console.log('');
    console.log('Detailed Polyglot Analysis:');
    console.log(`  Java Operations: ${summary.java_operations} (${(summary.language_comparison.java_ratio * 100).toFixed(1)}%)`);
    console.log(`  Python Operations: ${summary.python_operations} (${(summary.language_comparison.python_ratio * 100).toFixed(1)}%)`);
    console.log(`  Java - Avg: ${summary.metrics.java_response_time_avg.toFixed(2)}ms, P95: ${summary.metrics.java_response_time_p95.toFixed(2)}ms`);
    console.log(`  Python - Avg: ${summary.metrics.python_response_time_avg.toFixed(2)}ms, P95: ${summary.metrics.python_response_time_p95.toFixed(2)}ms`);
    console.log(`  Performance Gap: ${summary.language_comparison.performance_gap.toFixed(2)}ms`);
    console.log(`  Average Complexity Score: ${summary.language_comparison.complexity_score.toFixed(2)}/10`);
    console.log(`  Thresholds Passed: ${Object.values(summary.thresholds_passed).filter(v => v).length}/${Object.keys(summary.thresholds_passed).length}`);

    return {
        'stdout': textSummary(data, summary, { indent: ' ', enableColors: true }),
        'k6-polyglot-summary.json': JSON.stringify(summary, null, 2),
    };
}

function textSummary(data, summary, options) {
    return `
YAWL Polyglot Workload Test Results
======================================

Duration: ${(data.state.testRunDurationMs / 1000 / 60).toFixed(1)} minutes
Max VUs: 5000
Workflow Type Switches: ${summary.workflow_type_switches}

Language Distribution:
  Java Operations: ${summary.java_operations} (${(summary.language_comparison.java_ratio * 100).toFixed(1)}%)
  Python Operations: ${summary.python_operations} (${(summary.language_comparison.python_ratio * 100).toFixed(1)}%)

Performance Comparison:
  Java Engine:
    Avg Response Time: ${summary.metrics.java_response_time_avg.toFixed(2)}ms
    P95 Response Time: ${summary.metrics.java_response_time_p95.toFixed(2)}ms
  
  Python Engine:
    Avg Response Time: ${summary.metrics.python_response_time_avg.toFixed(2)}ms
    P95 Response Time: ${summary.metrics.python_response_time_p95.toFixed(2)}ms
  
  Performance Gap: ${summary.language_comparison.performance_gap.toFixed(2)}ms
  Average Complexity: ${summary.language_comparison.complexity_score.toFixed(2)}/10

HTTP Metrics:
  Total Requests: ${summary.metrics.total_requests}
  Failed Rate: ${(summary.metrics.failed_requests * 100).toFixed(2)}%
  Error Rate: ${(summary.metrics.error_rate * 100).toFixed(2)}%
  Avg Response Time: ${summary.metrics.avg_response_time.toFixed(2)}ms
  P95 Response Time: ${summary.metrics.p95_response_time.toFixed(2)}ms

Test Status: ${Object.values(summary.thresholds_passed).every(v => v) ? 'PASSED ✓' : 'FAILED ✗'}
`;
}
