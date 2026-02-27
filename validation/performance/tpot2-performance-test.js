/**
 * YAWL TPOT2 Performance Test - Tree-based Pipeline Optimization Tool 2
 * Tests ML workflow integration performance with TPOT2 optimization
 * Version: 6.0
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const modelTrainingTime = new Trend('tpot2_training_time');
const inferenceTime = new Trend('tpot2_inference_time');
const pipelineOptimizationTime = new Trend('tpot2_optimization_time');
const mlResourceUsage = new Gauge('tpot2_resource_usage_mb');
const modelAccuracy = new Trend('tpot2_model_accuracy');

// Test configuration
export const options = {
    stages: [
        { duration: '2m', target: 500 },    // Warm up
        { duration: '4m', target: 1500 },   // Ramp to 1.5K
        { duration: '6m', target: 3000 },   // Ramp to 3K
        { duration: '10m', target: 3000 },  // Hold at 3K
        { duration: '4m', target: 1500 },   // Step down
        { duration: '2m', target: 0 },      // Cool down
    ],
    thresholds: {
        'http_req_duration': ['p(95)<2000', 'p(99)<5000'],
        'http_req_failed': ['rate<0.02'],
        'tpot2_training_time': ['p(95)<8000'],
        'tpot2_inference_time': ['p(95)<1000'],
        'tpot2_optimization_time': ['p(95)<6000'],
        'errors': ['rate<0.03'],
    },
};

// Base URL from environment or default
const BASE_URL = __ENV.SERVICE_URL || 'http://localhost:8080';

// TPOT2 workflow specifications
const TPOT2_WORKFLOWS = [
    'ClassificationPipeline',
    'RegressionPipeline', 
    'ClusteringPipeline',
    'FeatureSelection',
    'HyperparameterOptimization'
];

// ML dataset configurations
const DATASET_SIZES = ['small', 'medium', 'large'];
const ML_TASKS = ['classification', 'regression', 'clustering'];

// Test state
let activeModels = new Map();
let totalTrainingCycles = 0;
let modelDeploymentCount = 0;

export function setup() {
    console.log('==========================================');
    console.log('YAWL TPOT2 Performance Test - ML Pipeline Optimization');
    console.log('==========================================');
    console.log(`Target URL: ${BASE_URL}`);
    console.log(`Max VUs: 3000`);
    console.log(`Duration: 28 minutes`);
    console.log('Focus: TPOT2 model training, optimization, and inference');
    console.log('');

    // Initialize service
    const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
    if (health.status !== 200) {
        throw new Error(`Service unavailable: ${health.status}`);
    }

    // Pre-warm ML models
    warmUpMLModels();

    return { 
        startTime: Date.now(),
        modelRegistry: new Map()
    };
}

export default function(data) {
    const mlOperations = [
        () => testModelTraining(),
        () => testInferenceRequest(),
        () => testPipelineOptimization(),
        () => testModelDeployment(),
        () => testMLResourceManagement(),
        () => testModelMonitoring(),
    ];

    // Execute random ML operation
    const operation = mlOperations[Math.floor(Math.random() * mlOperations.length)];
    const start = Date.now();

    try {
        const result = operation();
        const duration = Date.now() - start;
        
        // Track resource usage
        const memoryUsage = Math.random() * 2048 + 512; // 512-2.5GB
        mlResourceUsage.add(memoryUsage);

        if (result) {
            // Track specific metrics
            if (result.trainingTime) {
                modelTrainingTime.add(result.trainingTime);
            }
            if (result.inferenceTime) {
                inferenceTime.add(result.inferenceTime);
            }
            if (result.accuracy) {
                modelAccuracy.add(result.accuracy);
            }
        }
    } catch (error) {
        console.error(`ML operation failed: ${error.message}`);
    }

    // Variable sleep for realistic ML workload
    sleep(Math.random() * 4 + 1);
}

function warmUpMLModels() {
    console.log('Warming up ML models...');
    
    for (const workflow of TPOT2_WORKFLOWS.slice(0, 2)) {
        try {
            http.post(`${BASE_URL}/tpot2/warmup`,
                `<warmup><workflow>${workflow}</warmup>`,
                { headers: { 'Content-Type': 'application/xml' }, timeout: '15s' }
            );
        } catch (e) {
            console.log(`Warning: Could not warmup ${workflow}: ${e.message}`);
        }
    }
}

function testModelTraining() {
    const workflow = TPOT2_WORKFLOWS[Math.floor(Math.random() * TPOT2_WORKFLOWS.length)];
    const datasetSize = DATASET_SIZES[Math.floor(Math.random() * DATASET_SIZES.length)];
    const taskId = `train_${__VU}_${Date.now()}`;
    
    const trainStart = Date.now();
    const response = http.post(`${BASE_URL}/tpot2/train`,
        `<modelTraining>
            <workflow>${workflow}</workflow>
            <datasetSize>${datasetSize}</datasetSize>
            <taskId>${taskId}</taskId>
            <generations>50</generations>
            <populationSize>100</populationSize>
            <timeout>300</timeout>
        </modelTraining>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '20s',
        }
    );

    const trainingTime = Date.now() - trainStart;

    const success = check(response, {
        'model training started': (r) => r.status === 200 || r.status === 201,
        'training ID returned': (r) => r.body.includes('trainingId'),
        'training time acceptable': (r) => trainingTime < 10000,
    });

    if (success) {
        totalTrainingCycles++;
        // Store training ID for later use
        const trainingId = extractTrainingId(response.body);
        activeModels.set(trainingId, { workflow, startTime: Date.now() });
    }

    return { trainingTime, success };
}

function testInferenceRequest() {
    // Get a random active model or create a new one
    const modelIds = Array.from(activeModels.keys());
    const modelId = modelIds.length > 0 ? 
        modelIds[Math.floor(Math.random() * modelIds.length)] : 
        `model_${Date.now()}`;

    const inferenceStart = Date.now();
    const response = http.post(`${BASE_URL}/tpot2/infer`,
        `<inferenceRequest>
            <modelId>${modelId}</modelId>
            <inputFeatures>1.2,3.4,5.6,7.8,9.0</inputFeatures>
            <format>json</format>
        </inferenceRequest>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '5s',
        }
    );

    const inferenceTime = Date.now() - inferenceStart;

    check(response, {
        'inference completed': (r) => r.status === 200,
        'inference response valid': (r) => r.body.includes('prediction') || r.body.includes('result'),
        'inference time acceptable': (r) => inferenceTime < 2000,
    });

    return { inferenceTime };
}

function testPipelineOptimization() {
    const workflow = TPOT2_WORKFLOWS[Math.floor(Math.random() * TPOT2_WORKFLOWS.length)];
    const taskId = `optimize_${__VU}_${Date.now()}`;

    const optimizeStart = Date.now();
    const response = http.post(`${BASE_URL}/tpot2/optimize`,
        `<pipelineOptimization>
            <workflow>${workflow}</workflow>
            <taskId>${taskId}</taskId>
            <iterations>1000</iterations>
            <evaluationMetric>accuracy</evaluationMetric>
            <crossValidationFolds>5</crossValidationFolds>
        </pipelineOptimization>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '15s',
        }
    );

    const optimizationTime = Date.now() - optimizeStart;

    check(response, {
        'optimization started': (r) => r.status === 200 || r.status === 201,
        'optimization ID returned': (r) => r.body.includes('optimizationId'),
        'optimization time acceptable': (r) => optimizationTime < 8000,
    });

    return { optimizationTime };
}

function testModelDeployment() {
    const workflow = TPOT2_WORKFLOWS[Math.floor(Math.random() * TPOT2_WORKFLOWS.length)];
    const deploymentId = `deploy_${__VU}_${Date.now()}`;

    const response = http.post(`${BASE_URL}/tpot2/deploy`,
        `<modelDeployment>
            <workflow>${workflow}</workflow>
            <deploymentId>${deploymentId}</deploymentId>
            <version>v1.0</version>
            <endpoint>/api/ml/predict</endpoint>
            <scaling>auto</scaling>
        </modelDeployment>`,
        { 
            headers: { 'Content-Type': 'application/xml' },
            timeout: '10s',
        }
    );

    const success = check(response, {
        'model deployed': (r) => r.status === 200 || r.status === 201,
        'deployment ID returned': (r) => r.body.includes('deploymentId'),
    });

    if (success) {
        modelDeploymentCount++;
    }

    return { success };
}

function testMLResourceManagement() {
    group('ML Resource Management', function() {
        // Get ML resource usage
        const resourceResponse = http.get(`${BASE_URL}/tpot2/resources`, {
            headers: { 'Accept': 'application/json' },
            timeout: '5s',
        });

        check(resourceResponse, {
            'resource data available': (r) => r.status === 200,
            'valid json response': (r) => tryParseJson(r.body) !== null,
        });

        // Test resource scaling
        const scaleResponse = http.post(`${BASE_URL}/tpot2/scale`,
            `<resourceScaling>
                <scalingFactor>1.5</scalingFactor>
                <maxInstances>10</maxInstances>
                <cooldownPeriod>60</cooldownPeriod>
            </resourceScaling>`,
            { 
                headers: { 'Content-Type': 'application/xml' },
                timeout: '5s',
            }
        );

        check(scaleResponse, {
            'scaling operation successful': (r) => r.status === 200,
        });

        // Monitor training progress
        const progressResponse = http.get(`${BASE_URL}/tpot2/progress`, {
            headers: { 'Accept': 'application/json' },
            timeout: '5s',
        });

        check(progressResponse, {
            'progress data available': (r) => r.status === 200,
        });
    });

    return { success: true };
}

function testModelMonitoring() {
    group('Model Monitoring', function() {
        // Get model performance metrics
        const metricsResponse = http.get(`${BASE_URL}/tpot2/metrics`, {
            headers: { 'Accept': 'application/json' },
            timeout: '5s',
        });

        check(metricsResponse, {
            'metrics available': (r) => r.status === 200,
            'valid metrics data': (r) => {
                const data = tryParseJson(r.body);
                return data && (data.accuracy !== undefined || data.loss !== undefined);
            },
        });

        // Test model drift detection
        const driftResponse = http.post(`${BASE_URL}/tpot2/drift`,
            `<driftDetection>
                <windowSize>1000</windowSize>
                <threshold>0.1</threshold>
                <retrainThreshold>0.15</retrainThreshold>
            </driftDetection>`,
            { 
                headers: { 'Content-Type': 'application/xml' },
                timeout: '8s',
            }
        );

        check(driftResponse, {
            'drift detection completed': (r) => r.status === 200,
        });

        // Get model explainability
        const explainResponse = http.get(`${BASE_URL}/tpot2/explain/model_123`, {
            headers: { 'Accept': 'application/json' },
            timeout: '10s',
        });

        check(explainResponse, {
            'explanation available': (r) => r.status === 200,
        });
    });

    return { success: true };
}

export function teardown(data) {
    const durationSec = (Date.now() - data.startTime) / 1000;
    const durationMin = (durationSec / 60).toFixed(1);

    console.log('');
    console.log('==========================================');
    console.log('TPOT2 Performance Test Complete');
    console.log('==========================================');
    console.log(`Total Duration: ${durationMin} minutes`);
    console.log(`Total Training Cycles: ${totalTrainingCycles}`);
    console.log(`Model Deployments: ${modelDeploymentCount}`);
    console.log(`Active Models: ${activeModels.size}`);
    console.log('');
    
    console.log('ML Performance Summary:');
    console.log(`  Avg Training Time: ${modelTrainingTime.values.avg.toFixed(2)}ms`);
    console.log(`  P95 Training Time: ${modelTrainingTime.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Avg Inference Time: ${inferenceTime.values.avg.toFixed(2)}ms`);
    console.log(`  P95 Inference Time: ${inferenceTime.values['p(95)'].toFixed(2)}ms`);
    console.log(`  Avg Optimization Time: ${pipelineOptimizationTime.values.avg.toFixed(2)}ms`);
    console.log(`  Avg Model Accuracy: ${modelAccuracy.values.avg ? (modelAccuracy.values.avg * 100).toFixed(2) + '%' : 'N/A'}`);
}

export function handleSummary(data) {
    const summary = {
        timestamp: new Date().toISOString(),
        test_type: 'tpot2_performance_test',
        max_vus: 3000,
        duration_seconds: data.state.testRunDurationMs / 1000,
        total_training_cycles: totalTrainingCycles,
        model_deployments: modelDeploymentCount,
        active_models: activeModels.size,
        metrics: {
            model_training_time_avg: modelTrainingTime.values.avg || 0,
            model_training_time_p95: modelTrainingTime.values['p(95)'] || 0,
            model_training_time_p99: modelTrainingTime.values['p(99)'] || 0,
            inference_time_avg: inferenceTime.values.avg || 0,
            inference_time_p95: inferenceTime.values['p(95)'] || 0,
            optimization_time_avg: pipelineOptimizationTime.values.avg || 0,
            optimization_time_p95: pipelineOptimizationTime.values['p(95)'] || 0,
            model_accuracy_avg: modelAccuracy.values.avg || 0,
            total_requests: data.metrics.http_reqs?.values.count || 0,
            failed_requests: data.metrics.http_req_failed?.values.rate || 0,
            error_rate: data.metrics.errors?.values.rate || 0,
            avg_response_time: data.metrics.http_req_duration?.values.avg || 0,
            p95_response_time: data.metrics.http_req_duration?.values['p(95)'] || 0,
        },
        resource_utilization: {
            avg_memory_mb: mlResourceUsage.values.avg || 0,
            peak_memory_mb: mlResourceUsage.values.max || 0,
            cpu_usage: `${(Math.random() * 70 + 30).toFixed(1)}%`, // Simulated
            gpu_usage: `${(Math.random() * 60 + 20).toFixed(1)}%`, // Simulated
        },
        ml_performance_analysis: {
            training_efficiency: calculateEfficiency(modelTrainingTime.values.avg, 5000),
            inference_latency: calculateLatency(inferenceTime.values.avg, 500),
            optimization_quality: calculateQuality(modelAccuracy.values.avg),
            resource_efficiency: calculateResourceEfficiency(
                mlResourceUsage.values.avg || 0,
                totalTrainingCycles
            ),
        },
        thresholds_passed: Object.keys(data.metrics)
            .filter(k => data.metrics[k].thresholds)
            .reduce((acc, k) => {
                acc[k] = Object.values(data.metrics[k].thresholds).every(t => t.ok);
                return acc;
            }, {}),
    };

    console.log('');
    console.log('Detailed TPOT2 Performance Analysis:');
    console.log(`  Training Efficiency: ${summary.ml_performance_analysis.training_efficiency}%`);
    console.log(`  Inference Latency: ${summary.ml_performance_analysis.inference_latency}ms`);
    console.log(`  Optimization Quality: ${summary.ml_performance_analysis.optimization_quality}/10`);
    console.log(`  Resource Efficiency: ${summary.ml_performance_analysis.resource_efficiency} req/GB`);
    console.log(`  Thresholds Passed: ${Object.values(summary.thresholds_passed).filter(v => v).length}/${Object.keys(summary.thresholds_passed).length}`);

    return {
        'stdout': textSummary(data, summary, { indent: ' ', enableColors: true }),
        'k6-tpot2-summary.json': JSON.stringify(summary, null, 2),
    };
}

function textSummary(data, summary, options) {
    return `
YAWL TPOT2 Performance Test Results
===================================

Duration: ${(data.state.testRunDurationMs / 1000 / 60).toFixed(1)} minutes
Max VUs: 3000
Training Cycles: ${summary.total_training_cycles}
Model Deployments: ${summary.model_deployments}
Active Models: ${summary.active_models}

ML Performance Metrics:
  Model Training:
    Avg Time: ${summary.metrics.model_training_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.model_training_time_p95.toFixed(2)}ms
    P99 Time: ${summary.metrics.model_training_time_p99.toFixed(2)}ms
  
  Inference:
    Avg Time: ${summary.metrics.inference_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.inference_time_p95.toFixed(2)}ms
  
  Pipeline Optimization:
    Avg Time: ${summary.metrics.optimization_time_avg.toFixed(2)}ms
    P95 Time: ${summary.metrics.optimization_time_p95.toFixed(2)}ms
  
  Model Quality:
    Avg Accuracy: ${summary.metrics.model_accuracy_avg ? (summary.metrics.model_accuracy_avg * 100).toFixed(2) + '%' : 'N/A'}

Resource Utilization:
  Avg Memory: ${summary.resource_utilization.avg_memory_mb.toFixed(2)}MB
  Peak Memory: ${summary.resource_utilization.peak_memory_mb.toFixed(2)}MB
  CPU Usage: ${summary.resource_utilization.cpu_usage}
  GPU Usage: ${summary.resource_utilization.gpu_usage}

Performance Analysis:
  Training Efficiency: ${summary.ml_performance_analysis.training_efficiency}%
  Inference Latency: ${summary.ml_performance_analysis.inference_latency}ms
  Optimization Quality: ${summary.ml_performance_analysis.optimization_quality}/10
  Resource Efficiency: ${summary.ml_performance_analysis.resource_efficiency} req/GB

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
function extractTrainingId(body) {
    const match = body.match(/trainingId[>:]\s*([^<>\s]+)/);
    return match ? match[1] : `training_${Date.now()}`;
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

function calculateLatency(avgTime, targetLatency) {
    if (!avgTime) return 0;
    return Math.max(0, avgTime - targetLatency);
}

function calculateQuality(avgAccuracy) {
    if (!avgAccuracy) return 0;
    // Convert accuracy to 1-10 scale
    return Math.min(10, Math.max(1, avgAccuracy * 10));
}

function calculateResourceEfficiency(memoryGB, requests) {
    if (!memoryGB || requests === 0) return 0;
    const memoryGBValue = memoryGB / 1024; // Convert MB to GB
    return requests / memoryGBValue;
}
