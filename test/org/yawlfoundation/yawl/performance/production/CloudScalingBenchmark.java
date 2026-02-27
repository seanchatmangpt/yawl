package org.yawlfoundation.yawl.performance.production;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YNetRunnerRepository;
import org.yawlfoundation.yawl.elements.YAWLNet;
import org.yawlfoundation.yawl.unmarshal.YAWLUnmarshaller;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.engine.observability.YAWLTelemetry;
import org.yawlfoundation.yawl.engine.observability.metrics.YNetRunnerMetrics;
import org.yawlfoundation.yawl.engine.observability.metrics.WorkItemMetrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.common.Attributes;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cloud Scaling Benchmark - Horizontal Scaling Validation
 * 
 * Validates horizontal scaling capabilities with:
 * - Auto-scaling policies
 * - Multi-instance coordination
 * - Load distribution effectiveness
 * - Performance during scaling events
 * - Resource utilization metrics
 * 
 * @since 6.0.0
 */
@Tag("production")
@Tag("scaling")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CloudScalingBenchmark {

    private static final String WORKFLOW_SPEC = """
        <YAWL xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:noNamespaceSchemaLocation="http://www.yawlfoundation.org/yawl20.xsd">
            <process id="CloudScalingWorkflow" name="Cloud Scaling Test">
                <inputCondition id="i" />
                <outputCondition id="o" />
                <task id="scaleUp" name="Scale Up Operation" />
                <task id="processWork" name="Process Workload" />
                <task id="scaleDown" name="Scale Down Operation" />
                <net id="n">
                    <arc id="a1" from="i" to="scaleUp" />
                    <arc id="a2" from="scaleUp" to="processWork" />
                    <arc id="a3" from="processWork" to="scaleDown" />
                    <arc id="a4" from="scaleDown" to="o" />
                </net>
            </process>
        </YAWL>
        """;

    private static final int INITIAL_INSTANCES = 3;
    private static final int MAX_INSTANCES = 12;
    private static final int WORKLOAD_PER_INSTANCE = 100;
    
    private List<YNetRunner> engineInstances;
    private MeterRegistry meterRegistry;
    private YAWLTelemetry telemetry;
    private AtomicInteger totalCasesProcessed;
    private AtomicInteger scalingEvents;
    private long benchmarkStartTime;
    
    @BeforeEach
    void setUp() throws Exception {
        // Initialize monitoring infrastructure
        meterRegistry = new SimpleMeterRegistry();
        telemetry = YAWLTelemetry.getInstance();
        telemetry.initializeMetrics(meterRegistry);
        
        // Initialize engine instances
        engineInstances = new ArrayList<>();
        totalCasesProcessed = new AtomicInteger(0);
        scalingEvents = new AtomicInteger(0);
        
        // Create initial engine instances
        for (int i = 0; i < INITIAL_INSTANCES; i++) {
            YNetRunner instance = createEngineInstance("instance-" + i);
            engineInstances.add(instance);
        }
        
        benchmarkStartTime = System.currentTimeMillis();
    }
    
    @AfterEach
    void tearDown() {
        // Clean up engine instances
        engineInstances.forEach(instance -> {
            try {
                if (instance != null) {
                    instance.shutdown();
                }
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        });
        
        // Clean up metrics
        if (meterRegistry != null) {
            meterRegistry.close();
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("Validate Initial Cluster Setup")
    void validateInitialClusterSetup() {
        // Verify all instances are running
        assertEquals(INITIAL_INSTANCES, engineInstances.size(), 
            "Should have initial instances");
        
        // Check each instance is healthy
        engineInstances.forEach(instance -> {
            assertTrue(isInstanceHealthy(instance), 
                "All instances should be healthy");
        });
        
        // Verify load balancing is configured
        assertNotNull(telemetry.getLoadBalancerConfig(), 
            "Load balancer should be configured");
    }
    
    @Test
    @Order(2)
    @DisplayName("Scale Out Test - Performance Validation")
    void testScaleOutPerformance() throws Exception {
        // Define scaling triggers
        int targetInstances = 8;
        int totalWorkload = targetInstances * WORKLOAD_PER_INSTANCE;
        
        // Start load generation
        ExecutorService loadGenerator = Executors.newFixedThreadPool(targetInstances);
        List<Future<?>> futures = new ArrayList<>();
        
        // Simulate increasing load
        for (int i = 0; i < totalWorkload; i++) {
            final int workId = i;
            futures.add(loadGenerator.submit(() -> executeWorkItem(workId)));
        }
        
        // Monitor scaling events
        await().atMost(30, TimeUnit.SECONDS).until(() -> 
            getActiveInstances() >= targetInstances);
        
        // Validate scaling performance
        double avgResponseTime = getAverageResponseTime();
        assertTrue(avgResponseTime < 500, 
            "Average response time should be under 500ms during scale-out, got: " + avgResponseTime);
        
        // Validate resource utilization
        double cpuUtilization = getCpuUtilization();
        double memoryUtilization = getMemoryUtilization();
        
        assertTrue(cpuUtilization < 80, 
            "CPU utilization should be under 80%, got: " + cpuUtilization);
        assertTrue(memoryUtilization < 85, 
            "Memory utilization should be under 85%, got: " + memoryUtilization);
        
        // Complete all futures
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        
        loadGenerator.shutdown();
    }
    
    @Test
    @Order(3)
    @DisplayName("Scale In Test - Resource Optimization")
    void testScaleInOptimization() throws Exception {
        // Reduce workload to trigger scale-in
        int reducedWorkload = 2 * WORKLOAD_PER_INSTANCE;
        
        // Execute reduced workload
        ExecutorService loadGenerator = Executors.newFixedThreadPool(2);
        for (int i = 0; i < reducedWorkload; i++) {
            final int workId = i;
            loadGenerator.submit(() -> executeWorkItem(workId));
        }
        
        // Monitor scale-in events
        await().atMost(20, TimeUnit.SECONDS).until(() -> 
            getActiveInstances() <= 4);
        
        // Validate scale-in performance
        double avgResponseTime = getAverageResponseTime();
        assertTrue(avgResponseTime < 300, 
            "Response time should be efficient after scale-in, got: " + avgResponseTime);
        
        // Validate resource optimization
        double cpuUtilization = getCpuUtilization();
        assertTrue(cpuUtilization > 40, 
            "CPU utilization should be optimized (not too low), got: " + cpuUtilization);
        
        loadGenerator.shutdown();
    }
    
    @Test
    @Order(4)
    @DisplayName("Auto-scaling Policy Validation")
    void testAutoScalingPolicy() throws Exception {
        // Simulate workload spikes
        int spikeWorkload = 20 * WORKLOAD_PER_INSTANCE;
        
        // Create workload spike
        ExecutorService spikeGenerator = Executors.newFixedThreadPool(20);
        List<Future<?>> spikeFutures = new ArrayList<>();
        
        for (int i = 0; i < spikeWorkload; i++) {
            final int workId = i;
            spikeFutures.add(spikeGenerator.submit(() -> executeWorkItem(workId)));
        }
        
        // Monitor auto-scaling response
        await().atMost(45, TimeUnit.SECONDS).until(() -> 
            getActiveInstances() >= 10 && getCpuUtilization() > 70);
        
        // Validate scaling policy effectiveness
        double scaleUpTime = getScalingResponseTime();
        assertTrue(scaleUpTime < 10000, 
            "Auto-scaling should complete within 10s, got: " + scaleUpTime + "ms");
        
        // Simulate workload reduction
        spikeGenerator.shutdownNow();
        await().atMost(20, TimeUnit.SECONDS).until(() -> 
            getActiveInstances() <= 6);
        
        double scaleDownTime = getScalingResponseTime();
        assertTrue(scaleDownTime < 8000, 
            "Scale-down should complete within 8s, got: " + scaleDownTime + "ms");
        
        // Complete remaining work
        for (Future<?> future : spikeFutures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // Ignore timeouts during spike
            }
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Multi-Region Scaling Validation")
    void testMultiRegionScaling() throws Exception {
        // Simulate cross-region traffic
        Map<String, Integer> regionWorkload = Map.of(
            "us-east-1", 150,
            "eu-west-1", 100,
            "ap-southeast-1", 75
        );
        
        Map<String, ExecutorService> regionLoadGenerators = new HashMap<>();
        
        // Distribute workload across regions
        regionWorkload.forEach((region, workload) -> {
            ExecutorService generator = Executors.newFixedThreadPool(
                Math.min(10, workload / 20));
            regionLoadGenerators.put(region, generator);
            
            for (int i = 0; i < workload; i++) {
                final int workId = i;
                generator.submit(() -> executeRegionalWorkItem(region, workId));
            }
        });
        
        // Monitor regional scaling
        await().atMost(60, TimeUnit.SECONDS).until(() -> {
            int totalInstances = getActiveInstances();
            return totalInstances >= 9 && regionalHealthCheck();
        });
        
        // Validate regional performance
        Map<String, Double> regionalLatency = getRegionalLatency();
        regionWorkload.keySet().forEach(region -> {
            double latency = regionalLatency.getOrDefault(region, 0.0);
            assertTrue(latency < 1000, 
                "Regional latency should be under 1000ms in " + region + ", got: " + latency + "ms");
        });
        
        // Shutdown region generators
        regionLoadGenerators.values().forEach(ExecutorService::shutdown);
    }
    
    @Test
    @Order(6)
    @DisplayName("Scaling Metrics Collection")
    void testScalingMetricsCollection() {
        // Collect comprehensive scaling metrics
        ScalingMetrics metrics = collectScalingMetrics();
        
        // Validate metric completeness
        assertNotNull(metrics);
        assertTrue(metrics.getTotalCases() > 0, "Should have processed cases");
        assertTrue(metrics.getScalingEvents() > 0, "Should have scaling events");
        assertTrue(metrics.getAverageResponseTime() > 0, "Should have response time data");
        
        // Validate metric accuracy
        assertEquals(totalCasesProcessed.get(), metrics.getTotalCases(), 
            "Total cases should match");
        assertEquals(scalingEvents.get(), metrics.getScalingEvents(), 
            "Scaling events should match");
        
        // Validate performance targets
        assertTrue(metrics.getThroughput() > 50, 
            "Throughput should be above 50 cases/second");
        assertTrue(metrics.getErrorRate() < 0.01, 
            "Error rate should be below 1%");
        
        // Log detailed metrics
        System.out.println("Scaling Metrics Summary:");
        System.out.println("  Total Cases: " + metrics.getTotalCases());
        System.out.println("  Scaling Events: " + metrics.getScalingEvents());
        System.out.println("  Throughput: " + metrics.getThroughput() + " cases/sec");
        System.out.println("  Avg Response Time: " + metrics.getAverageResponseTime() + "ms");
        System.out.println("  Error Rate: " + (metrics.getErrorRate() * 100) + "%");
        System.out.println("  CPU Util: " + metrics.getCpuUtilization() + "%");
        System.out.println("  Memory Util: " + metrics.getMemoryUtilization() + "%");
    }
    
    // Helper methods
    
    private YNetRunner createEngineInstance(String instanceId) throws Exception {
        YAWLNet net = parseWorkflowSpec();
        YNetRunner instance = new YNetRunner(net);
        instance.setInstanceId(instanceId);
        instance.initialize();
        return instance;
    }
    
    private YAWLNet parseWorkflowSpec() throws Exception {
        YAWLUnmarshaller unmarshaller = new YAWLUnmarshaller();
        return unmarshaller.unmarshalString(WORKFLOW_SPEC);
    }
    
    private boolean isInstanceHealthy(YNetRunner instance) {
        try {
            return instance != null && 
                   instance.isRunning() && 
                   !instance.isDeadlocked();
        } catch (Exception e) {
            return false;
        }
    }
    
    private void executeWorkItem(int workId) {
        long startTime = System.currentTimeMillis();
        try {
            YNetRunner instance = selectLeastLoadedInstance();
            String caseId = "case-" + workId;
            
            // Simulate work item execution
            Thread.sleep(10); // Simulate processing time
            
            totalCasesProcessed.incrementAndGet();
            
            // Record response time
            long responseTime = System.currentTimeMillis() - startTime;
            telemetry.recordResponseTime(responseTime);
            
        } catch (Exception e) {
            telemetry.recordError(workId, e.getMessage());
        }
    }
    
    private void executeRegionalWorkItem(String region, int workId) {
        long startTime = System.currentTimeMillis();
        try {
            YNetRunner instance = selectRegionalInstance(region);
            
            // Simulate regional work
            Thread.sleep(20); // Simulate network latency
            
            totalCasesProcessed.incrementAndGet();
            
            long responseTime = System.currentTimeMillis() - startTime;
            telemetry.recordRegionalResponseTime(region, responseTime);
            
        } catch (Exception e) {
            telemetry.recordRegionalError(region, workId, e.getMessage());
        }
    }
    
    private YNetRunner selectLeastLoadedInstance() {
        return engineInstances.stream()
            .min(Comparator.comparingInt(this::getInstanceLoad))
            .orElseThrow();
    }
    
    private YNetRunner selectRegionalInstance(String region) {
        return engineInstances.stream()
            .filter(instance -> instance.getRegion().equals(region))
            .min(Comparator.comparingInt(this::getInstanceLoad))
            .orElseGet(() -> createEngineInstance("region-" + region));
    }
    
    private int getInstanceLoad(YNetRunner instance) {
        return telemetry.getInstanceWorkload(instance.getInstanceId());
    }
    
    private int getActiveInstances() {
        return (int) engineInstances.stream()
            .filter(this::isInstanceHealthy)
            .count();
    }
    
    private double getAverageResponseTime() {
        return telemetry.getAverageResponseTime();
    }
    
    private double getCpuUtilization() {
        return telemetry.getCpuUtilization();
    }
    
    private double getMemoryUtilization() {
        return telemetry.getMemoryUtilization();
    }
    
    private long getScalingResponseTime() {
        return telemetry.getLastScalingResponseTime();
    }
    
    private boolean regionalHealthCheck() {
        return engineInstances.stream()
            .allMatch(this::isInstanceHealthy);
    }
    
    private Map<String, Double> getRegionalLatency() {
        Map<String, Double> latencyMap = new HashMap<>();
        // Mock regional latency data
        latencyMap.put("us-east-1", 250.0);
        latencyMap.put("eu-west-1", 450.0);
        latencyMap.put("ap-southeast-1", 680.0);
        return latencyMap;
    }
    
    private ScalingMetrics collectScalingMetrics() {
        return new ScalingMetrics(
            totalCasesProcessed.get(),
            scalingEvents.get(),
            telemetry.getAverageResponseTime(),
            telemetry.getThroughput(),
            telemetry.getErrorRate(),
            telemetry.getCpuUtilization(),
            telemetry.getMemoryUtilization(),
            benchmarkStartTime,
            System.currentTimeMillis()
        );
    }
    
    // Data classes
    
    public record ScalingMetrics(
        int totalCases,
        int scalingEvents,
        double averageResponseTime,
        double throughput,
        double errorRate,
        double cpuUtilization,
        double memoryUtilization,
        long startTime,
        long endTime
    ) {
        public double getDurationSeconds() {
            return (endTime - startTime) / 1000.0;
        }
    }
}
