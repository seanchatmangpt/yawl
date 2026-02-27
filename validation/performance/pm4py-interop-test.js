/**
 * YAWL PM4py Interoperability Test - Process Mining Integration
 * Tests PM4py process mining integration performance
 * Version: 6.0
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const modelImportTime = new Trend('pm4py_import_time');
const conformanceCheckingTime = new Trend('pm4py_conformance_time');
const processDiscoveryTime = new Trend('pm4py_discovery_time');
const eventLogProcessingTime = new Trend('pm4py_log_processing_time');
const petriNetConversionTime = new Trend('pm4py_conversion_time');
const miningAccuracy = new Trend('pm4py_mining_accuracy');

// Test configuration
export const options = {
    stages: [
        { duration: '2m', target: 1000 },    // Warm up
        { duration: '3m', target: 2500 },   // Ramp to 2.5K
        { duration: '6m', target: 5000 },   // Ramp to 5K
        { duration: '12m', target: 5000 },  // Hold at 5K
        { duration: '3m', target: 2500 },   // Step down
        { duration: '2m', target: 0 },      // Cool down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1500', 'p(99)<3000'],
        'http_req_failed': ['rate<0.015'],
        'pm4py_import_time': ['p(95)<1000'],
        'pm4py_conformance_time': ['p(95)<2000'],
        'pm4py_discovery_time': ['p(95)<3000'],
        'pm4py_log_processing_time': ['p(95)<1500'],
        'errors': ['rate<0.02'],
    },
};

// Base URL from environment or default
const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

// Process mining configurations
const PROCESS_SIZES = ['small', 'medium', 'large', 'xlarge'];
const DISCOVERY_ALGORITHMS = ['alpha', 'inductive', 'heuristic', 'genetic'];
const CONFORMANCE_TECHNIQUES = ['alignments', 'token_replay', 'footprints'];
const EVENT_LOG_FORMATS = ['xes', 'csv', 'json'];

// Test state
importedModels = new Map();
processedEventLogs = 0;
totalMiningOperations = 0;

export function setup() {
    console.log('==========================================');
    console.log('YAWL PM4py Interoperability Test - Process Mining');
    console.log('==========================================');
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Max VUs: 5000`);
    console.log(`Duration: 28 minutes`);
    console.log('Focus: Process mining model import, conformance, and discovery');
    console.log('');

    // Initialize service
    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Service unavailable: ${health.status}`);
    }

    // Pre-import some process models
    preImportProcessModels();

    return { 
        startTime: Date.now(),
        modelRegistry: new Map(),
        logProcessor: new Map()
    };
}

export default function(data) {
    const miningOperations = [
        () => testModelImport(),
        () => testConformanceChecking(),
        () => testProcessDiscovery(),
        () => testEventLogProcessing(),
        () => testPetriNetConversion(),
        () => testProcessEnhancement(),
        () => testProcessMonitoring(),
    ];

    // Execute random process mining operation
    const operation = miningOperations[Math.floor(Math.random() * miningOperations.length)];
    const start = Date.now();

    try {
        const result = operation();
        const duration = Date.now() - start;
        
        if (result) {
            // Track specific metrics
            if (result.importTime) {
                modelImportTime.add(result.importTime);
            }
            if (result.conformanceTime) {
                conformanceCheckingTime.add(result.conformanceTime);
            }
            if (result.discoveryTime) {
                processDiscoveryTime.add(result.discoveryTime);
            }
            if (result.logProcessingTime) {
                eventLogProcessingTime.add(result.logProcessingTime);
            }
            if (result.conversionTime) {
                petriNetConversionTime.add(result.conversionTime);
            }
            if (result.accuracy) {
                miningAccuracy.add(result.accuracy);
            }
        }
    } catch (error) {
        console.error(`Process mining operation failed: ${error.message}`);
    }

    // Variable sleep for realistic mining workload
    sleep(Math.random() * 2 + 0.5);
}

function preImportProcessModels() {
    console.log('Pre-importing process models...');
    
    const models = [
        'OrderProcessing',
        'LoanApplication',
        'InsuranceClaim',
        'SoftwareDelivery'
    ];

    for (const model of models.slice(0, 2)) {
        try {
            http.post(`${BASE_URL}/pm4py/import`,
                `<processModel>
                    <name>${model}</name>
                    <format>bpmn</format>
                    <source>preloaded</source>
                </processModel>`,
                { 
                    headers: { 'Content-Type': 'application/xml' },
                    timeout: '10s'
                }
            );
        } catch (e) {
            console.log(`Warning: Could not pre-import ${model}: ${e.message}`);
        }
    }
}

function testModelImport() {
    const processName = `Process_${__VU}_${Date.now()}`;
    const processSize = PROCESS_SIZES[Math.floor(Math.random() * PROCESS_SIZES.length)];
    const format = EVENT_LOG_FORMATS[Math.floor(Math.random() * EVENT_LOG_FORMATS.length)];

    const importStart = Date.now();
    const response = http.post(`${BASE_URL}/pm4py/import`,
        `<processModel>
            <name>${processName}</name>
            <format>${format}</format>
            <size>${processSize}</size>
            <source>generated</source>
            <description>Test process model for import performance</description>
        </processModel>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '8s',
        }
    );

    const importTime = Date.now() - importStart;

    const success = check(response, {
        'model imported': (r) => r.status === 200 || r.status === 201,
        'model ID returned': (r) => r.body.includes('modelId'),
        'import time acceptable': (r) => importTime < 1500,
    });

    if (success) {
        const modelId = extractModelId(response.body);
        importedModels.set(modelId, { name: processName, size: processSize });
    }

    return { importTime, success };
}

function testConformanceChecking() {
    const modelIds = Array.from(importedModels.keys());
    const modelId = modelIds.length > 0 ? 
        modelIds[Math.floor(Math.random() * modelIds.length)] : 
        'model_123';
    
    const technique = CONFORMANCE_TECHNIQUES[Math.floor(Math.random() * CONFORMANCE_TECHNIQUES.length)];
    const logSize = Math.floor(Math.random() * 10000) + 1000; // 1K-10K events

    const conformanceStart = Date.now();
    const response = http.post(`${BASE_URL}/pm4py/conformance`,
        `<conformanceCheck>
            <modelId>${modelId}</modelId>
            <technique>${technique}</technique>
            <logSize>${logSize}</logSize>
            <enableTraceAlignment>true</enableTraceAlignment>
            <fitnessThreshold>0.9</fitnessThreshold>
        </conformanceCheck>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '10s',
        }
    );

    const conformanceTime = Date.now() - conformanceStart;

    check(response, {
        'conformance check completed': (r) => r.status === 200,
        'conformance metrics returned': (r) => r.body.includes('fitness') || r.body.includes('precision'),
        'conformance time acceptable': (r) => conformanceTime < 2500,
    });

    return { conformanceTime };
}

function testProcessDiscovery() {
    const algorithm = DISCOVERY_ALGORITHMS[Math.floor(Math.random() * DISCOVERY_ALGORITHMS.length)];
    const logSize = Math.floor(Math.random() * 50000) + 5000; // 5K-50K events
    const variants = Math.floor(Math.random() * 100) + 10; // 10-110 variants

    const discoveryStart = Date.now();
    const response = http.post(`${BASE_URL}/pm4py/discover`,
        `<processDiscovery>
            <algorithm>${algorithm}</algorithm>
            <logSize>${logSize}</logSize>
            <variantsCount>${variants}</variantsCount>
            <noiseThreshold>0.1</noiseThreshold>
            <enableReducing>true</enableReducing>
        </processDiscovery>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '12s',
        }
    );

    const discoveryTime = Date.now() - discoveryStart;

    check(response, {
        'process discovered': (r) => r.status === 200 || r.status === 201,
        'discovery model generated': (r) => r.body.includes('petriNet') || r.body.includes('model'),
        'discovery time acceptable': (r) => discoveryTime < 3500,
    });

    return { discoveryTime };
}

function testEventLogProcessing() {
    const logFormat = EVENT_LOG_FORMATS[Math.floor(Math.random() * EVENT_LOG_FORMATS.length)];
    const logSize = Math.floor(Math.random() * 100000) + 10000; // 10K-100K events
    const batchSize = Math.floor(Math.random() * 1000) + 100; // 100-1000 batch

    const processingStart = Date.now();
    const response = http.post(`${BASE_URL}/pm4py/ProcessLog`,
        `<eventLogProcessing>
            <format>${logFormat}</format>
            <logSize>${logSize}</logSize>
            <batchSize>${batchSize}</batchSize>
            <enableFiltering>true</enableFiltering>
            <enableAggregation>true</enableAggregation>
        </eventLogProcessing>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '8s',
        }
    );

    const logProcessingTime = Date.now() - processingStart;

    check(response, {
        'event log processed': (r) => r.status === 200,
        'processed events returned': (r) => r.body.includes('processedEvents'),
        'processing time acceptable': (r) => logProcessingTime < 2000,
    });

    processedEventLogs += logSize;

    return { logProcessingTime };
}

function testPetriNetConversion() {
    const sourceFormat = ['bpmn', 'bpmn20', 'pnml'][Math.floor(Math.random() * 3)];
    const targetFormat = 'petriNet';

    const conversionStart = Date.now();
    const response = http.post(`${BASE_URL}/pm4py/convert`,
        `<modelConversion>
            <sourceFormat>${sourceFormat}</sourceFormat>
            <targetFormat>${targetFormat}</targetFormat>
            <preserveSemantics>true</preserveSemantics>
            <optimizeStructure>true</optimizeStructure>
        </modelConversion>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '6s',
        }
    );

    const conversionTime = Date.now() - conversionStart;

    check(response, {
        'model converted': (r) => r.status === 200 || r.status === 201,
        'converted model generated': (r) => r.body.includes('petriNet'),
        'conversion time acceptable': (r) => conversionTime < 1200,
    });

    return { conversionTime };
}

function testProcessEnhancement() {
    group('Process Enhancement', function() {
        // Test process model enhancement
        const enhanceResponse = http.post(`${BASE_URL}/pm4py/enhance`,
            `<processEnhancement>
                <modelId>model_123</modelId>
                <enhancementType>performance</enhancementType>
                <includeMetrics>true</includeMetrics>
                <optimizationLevel>high</optimizationLevel>
            </processEnhancement>`,
            { 
                headers: { 'Content-Type': 'application/xml' },
                timeout: '10s',
            }
        );

        check(enhanceResponse, {
            'model enhanced': (r) => r.status === 200,
            'enhancement metrics provided': (r) => r.body.includes('enhancementScore'),
        });

        // Test process pattern mining
        const patternResponse = http.post(`${URL}/pm4py/patterns`,
            `<patternMining>
                <modelId>model_123</modelId>
                <patternTypes>all</patternTypes>
                <minSupport>0.1</minSupport>
            </patternMining>`,
            { 
                headers: { 'Content-Type': 'application/xml' },
                timeout: '8s',
            }
        );

        check(patternResponse, {
            'patterns mined': (r) => r.status === 200,
            'pattern results returned': (r) => r.body.includes('patterns'),
        });

        // Test process similarity analysis
        const similarityResponse = http.post(`${URL}/pm4py/similarity`,
            `<similarityAnalysis>
                <modelId1>model_123</modelId1>
                <modelId2>model_456</modelId2>
                <similarityType>structural</similarityType>
                <threshold>0.8</threshold>
            </similarityAnalysis>`,
            { 
                headers: { 'Content-Type': 'application/xml' },
                timeout: '6s',
            }
        );

        check(similarityResponse, {
            'similarity computed': (r) => r.status === 200,
            'similarity score provided': (r) => r.body.includes('similarityScore'),
        });
    });

    return { success: true };
}

function testProcessMonitoring() {
    group('Process Monitoring', function() {
        // Get process performance metrics
        const metricsResponse = http.get(`${BASE_URL}/pm4py/metrics`, {
            headers: { 'Accept': 'application/json' },
            timeout: '5s',
        });

        check(metricsResponse, {
            'metrics available': (r) => r.status === 200,
            'valid metrics data': (r) => {
                const data = tryParseJson(r.body);
                return data && (data.cycleTime !== undefined || data.throughput !== undefined);
            },
        });

        // Test deviation detection
        const deviationResponse = http.post(`${URL}/pm4py/deviations`,
            `<deviationDetection>
                <modelId>model_123</modelId>
                <detectionMethod>statistical</detectionMethod>
                <confidenceLevel>0.95</confidenceLevel>
            </deviationDetection>`,
            { 
                headers: { 'Content-Type': 'application/xml' },
                timeout: '8s',
            }
        );

        check(deviationResponse, {
            'deviations detected': (r) => r.status === 200,
            'deviation report generated': (r) => r.body.includes('deviations'),
        });

        // Get process dashboard data
        const dashboardResponse = http.get(`${URL}/pm4py/dashboard`, {
            headers: { 'Accept': 'application/json' },
            timeout: '5s',
        });

        check(dashboardResponse, {
            'dashboard data available': (r) => r.status === 200,
            'dashboard metrics present': (r) => r.body.includes('kpi'),
        });
    });

    return { success: true };
}

export function teardown(data) {
    const durationSec = (Date.now() - data.startTime) / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('==========================================');
    console.log('PM4py Interoperability Test Complete');
    console.log('==========================================');
    console.log(`Total Duration: ${durationMin} minutes`);
    console.log(`Imported Models: ${importedModels.size}`);
    console.log(`Processed Events: ${processedEventLogs}`);
    console.log(`Mining Operations: ${totalMiningOperations}`);
    console.log('');
    
    console.log('Process Mining Performance Summary:');
    console.log(`  Avg Import Time: ${modelImportTime.values.avg.toFixed(2)}ms`);
    console.log(`  P95 Import Time: ${modelImportTime.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Avg Conformance Time: ${conformanceCheckingTime.values.avg.toFixed(2)}ms`);
    console.log(`  P95 Conformance Time: ${conformanceCheckingTime.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Avg Discovery Time: ${processDiscoveryTime.values.avg.toFixed(2)}ms`);
    console.log(`  P95 Discovery Time: ${processDiscoveryTime.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Avg Log Processing Time: ${eventLogProcessingTime.values.avg.toFixed(2)}ms`);
    console.log(`  Mining Accuracy: ${miningAccuracy.values.avg ? (miningAccuracy.values.avg * 100).toFixed(2) + '%' : 'N/A'}`);
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test_type: 'pm4py_interop_test',
        max_vus: 5000,
        duration_seconds: data.state.testRunDurationMs / 1000,
        imported_models: importedModels.size,
        processed_events: processedEventLogs,
        mining_operations: totalMiningOperations,
        metrics: {
            model_import_time_avg: modelImportTime.values.avg || 0,
            model_import_time_p95: modelImportTime.values['p(95)'] || 0,
            conformance_time_avg: conformanceCheckingTime.values.avg || 0,
            conformance_time_p95: conformanceCheckingTime.values['p(95)'] || 0,
            discovery_time_avg: processDiscoveryTime.values.avg || 0,
            discovery_time_p95: processDiscoveryTime.values['p(95)'] || 0,
            log_processing_time_avg: eventLogProcessingTime.values.avg || 0,
            log_processing_time_p95: eventLogProcessingTime.values['p(95)'] || 0,
            conversion_time_avg: petriNetConversionTime.values.avg || 0,
            conversion_time_p95: petriNetConversionTime.values['p(95)'] || 0,
            mining_accuracy_avg: miningAccuracy.values.avg || 0,
            total_requests: data.metrics.http_reqs?.values.count || 0,
            failed_requests: data.metrics.http_req_failed?.values.rate || 0,
            error_rate: data.metrics.errors?.values.rate || 0,
            avg_response_time: data.metrics.http_req_duration?.values.avg || 0,
            p95_response_time: data.metrics.http_req_duration?.values['p(95)'] || 0,
        },
        process_mining_analysis: {
            import_efficiency: calculateEfficiency(modelImportTime.values.avg, 1000),
            conformance_quality: calculateQuality(conformanceCheckingTime.values.avg, 2000),
            discovery_accuracy: calculateAccuracy(discoveryTime.values.avg, processDiscoveryTime.values.p95),
            throughput_score: calculateThroughput(processedEventLogs, data.state.testRunDurationMs / 1000),
            model_diversity: calculateModelDiversity(importedModels),
        },
        thresholds_passed: Object.keys(data.metrics)
            .filter(k => data.metrics[k].thresholds)
            .reduce((acc, k) => {
                acc[k] = Object.values(data.metrics[k].thresholds).every(t => t.ok);
                return acc;
            }, {}),
    };

    console.log('');
    console.log('Detailed PM4py Performance Analysis:');
    console.log(`  Import Efficiency: ${summary.process_mining_analysis.import_efficiency}%`);
    console.log(`  Conformance Quality: ${summary.process_mining_analysis.conformance_quality}/10`);
    console.log(`  Discovery Accuracy: ${summary.process_mining_analysis.discovery_accuracy}%`);
    console.log(`  Throughput Score: ${summary.process_mining_analysis.throughput_score} events/s`);
    console.log(`  Model Diversity: ${summary.process_mining_analysis.model_diversity} unique models`);
    console.log(`  Thresholds Passed: ${Object.values(summary.thresholds_passed).filter(v => v).length}/${Object.keys(summary.thresholds_passed).length}`);

    return {
        'stdout': textSummary(data, summary, { indent: ' ', enableColors: true }),
        'k6-pm4py-summary.json': JSON.stringify(summary, null, 2),
    };
}

function textSummary(data, summary, options) {
    return `
YAWL PM4py Interoperability Test Results
========================================

Duration: ${(data.state.testRunDurationMs / 1000 / 60).toFixed(1)} minutes
Max VUs: 5000
Imported Models: ${summary.imported_models}
Processed Events: ${summary.processed_events}
Mining Operations: ${summary.mining_operations}

Process Mining Performance Metrics:
  Model Import:
    Avg Time: ${summary.metrics.model_import_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.model_import_time_p95.toFixed(2)}ms
  
  Conformance Checking:
    Avg Time: ${summary.metrics.conformance_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.conformance_time_p95.toFixed(2)}ms
  
  Process Discovery:
    Avg Time: ${summary.metrics.discovery_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.discovery_time_p95.toFixed(2)}ms
  
  Event Log Processing:
    Avg Time: ${summary.metrics.log_processing_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.log_processing_time_p95.toFixed(2)}ms
  
  Petri Net Conversion:
    Avg Time: ${summary.metrics.conversion_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.conversion_time_p95.toFixed(2)}ms

Process Mining Analysis:
  Import Efficiency: ${summary.process_mining_analysis.import_efficiency}%
  Conformance Quality: ${summary.process_mining_analysis.conformance_quality}/10
  Discovery Accuracy: ${summary.process_mining_analysis.discovery_accuracy}%
  Throughput Score: ${summary.process_mining_analysis.throughput_score} events/s
  Model Diversity: ${summary.process_mining_analysis.model_diversity} unique models

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
function extractModelId(body) {
    const match = body.match(/modelId[>:]\s*([^<>\s]+)/);
    return match ? match[1] : `model_${Date.now()}`;
}

function tryParseJson(body) {
    try {
        return JSON.parse(body);
    } catch {
        return null;
    }
}

function calculateEfficiency(actualTime, targetTime) {
    if (!actualTime) return 0;
    return Math.max(0, Math.min(100, ((targetTime - actualTime) / targetTime) * 100));
}

function calculateQuality(actualTime, targetTime) {
    if (!actualTime) return 0;
    // Better quality = lower time, so inverse calculation
    return Math.min(10, Math.max(1, (targetTime / actualTime) * 5));
}

function calculateAccuracy(avgTime, p95Time) {
    if (!avgTime || !p95Time) return 0;
    // Consistency indicates accuracy
    const consistency = 1 - (p95Time - avgTime) / p95Time;
    return Math.max(0, Math.min(100, consistency * 100));
}

function calculateThroughput(events, seconds) {
    if (seconds === 0) return 0;
    return events / seconds;
}

function calculateModelDiversity(models) {
    let diversity = 0;
    const sizes = Array.from(models.values()).map(m => m.size);
    if (sizes.length > 0) {
        const uniqueSizes = new Set(sizes).size;
        diversity = (uniqueSizes / sizes.length) * models.size;
    }
    return diversity;
}
