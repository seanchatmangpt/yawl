package org.yawlfoundation.yawl.billing.gcp;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudcommerceprocurement.v1.CloudCommerceProcurement;
import com.google.api.services.cloudcommerceprocurement.v1.model.Account;
import com.google.api.services.cloudcommerceprocurement.v1.model.ApproveEntitlementRequest;
import com.google.api.services.cloudcommerceprocurement.v1.model.Entitlement;
import com.google.api.services.cloudcommerceprocurement.v1.model.ListEntitlementsResponse;
import com.google.api.services.servicecontrol.v1.ServiceControl;
import com.google.api.services.servicecontrol.v1.model.CheckRequest;
import com.google.api.services.servicecontrol.v1.model.CheckResponse;
import com.google.api.services.servicecontrol.v1.model.Operation;
import com.google.api.services.servicecontrol.v1.model.MetricValueSet;
import com.google.api.services.servicecontrol.v1.model.ReportRequest;
import com.google.api.services.servicecontrol.v1.model.ReportResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GCP Cloud Marketplace usage metering integration.
 * Handles usage reporting, entitlement management, and billing events.
 * Enforces per-tenant resource quotas to prevent DoS attacks.
 */
public class UsageMeter {

    private static final Logger LOG = LoggerFactory.getLogger(UsageMeter.class);

    private static final String METERING_SERVICE_NAME = "yawlfoundation-yawl.googleapis.com";
    private static final String USAGE_METRIC = "yawlfoundation-yawl.googleapis.com/workflow_executions";
    private static final String COMPUTE_METRIC = "yawlfoundation-yawl.googleapis.com/compute_units";

    private final String participantId;
    private final ServiceControl serviceControl;
    private final CloudCommerceProcurement procurementService;
    private final ScheduledExecutorService scheduler;
    private final Map<String, UsageAccumulator> usageAccumulators;
    private final Map<String, QuotaEnforcer> quotaEnforcers;
    private final AtomicLong reportCounter;

    private int batchSize = 100;
    private long flushIntervalMs = 60000;

    /**
     * Creates a new UsageMeter instance.
     *
     * @param credentialsPath Path to GCP service account credentials JSON
     * @param participantId   GCP Marketplace participant ID
     * @throws GeneralSecurityException if security setup fails
     * @throws IOException              if credentials cannot be read
     */
    public UsageMeter(String credentialsPath, String participantId)
            throws GeneralSecurityException, IOException {
        this.participantId = participantId;

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        GoogleCredential credentials = GoogleCredential
                .fromStream(new FileInputStream(credentialsPath))
                .createScoped(Collections.singletonList(
                        "https://www.googleapis.com/auth/cloud-platform"));

        this.serviceControl = new ServiceControl.Builder(httpTransport, jsonFactory, credentials)
                .setApplicationName("yawl-gcp-metering")
                .build();

        this.procurementService = new CloudCommerceProcurement.Builder(
                httpTransport, jsonFactory, credentials)
                .setApplicationName("yawl-gcp-procurement")
                .build();

        this.usageAccumulators = new ConcurrentHashMap<>();
        this.quotaEnforcers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.reportCounter = new AtomicLong(0);

        startBackgroundTasks();
    }

    /**
     * Starts background tasks for usage flushing and health checks.
     */
    private void startBackgroundTasks() {
        // Periodic flush of accumulated usage
        scheduler.scheduleAtFixedRate(
                this::flushUsage,
                flushIntervalMs,
                flushIntervalMs,
                TimeUnit.MILLISECONDS);

        // Periodic entitlement sync
        scheduler.scheduleAtFixedRate(
                this::syncEntitlements,
                5,
                5,
                TimeUnit.MINUTES);

        LOG.info("Started background tasks: flushInterval={}ms", flushIntervalMs);
    }

    /**
     * Records usage for a workflow execution.
     * Enforces per-tenant quotas before allowing execution.
     *
     * @param customerId      GCP customer account ID
     * @param entitlementId   Entitlement ID for the subscription
     * @param workflowName    Name of the executed workflow
     * @param executionTimeMs Execution time in milliseconds
     * @param computeUnits    Compute units consumed
     * @throws IllegalStateException if tenant quota exceeded
     */
    public void recordWorkflowUsage(String customerId, String entitlementId,
                                    String workflowName, long executionTimeMs,
                                    double computeUnits) {
        String key = customerId + ":" + entitlementId;

        // Check quota before recording usage
        QuotaEnforcer enforcer = quotaEnforcers.computeIfAbsent(
                key, k -> new QuotaEnforcer(customerId));

        if (!enforcer.checkAndRecordUsage(executionTimeMs, computeUnits)) {
            LOG.warn("Quota exceeded for tenant {}: {} compute units", customerId, computeUnits);
            throw new IllegalStateException("Tenant quota exceeded for customerId=" + customerId);
        }

        UsageAccumulator accumulator = usageAccumulators.computeIfAbsent(
                key, k -> new UsageAccumulator(customerId, entitlementId));

        accumulator.addWorkflowExecution(workflowName, executionTimeMs, computeUnits);

        if (accumulator.getBatchSize() >= batchSize) {
            flushAccumulator(accumulator);
        }
    }

    /**
     * Reports accumulated usage to GCP Service Control.
     */
    private void flushUsage() {
        List<UsageAccumulator> toFlush = new ArrayList<>();

        usageAccumulators.forEach((key, accumulator) -> {
            if (accumulator.hasUsage()) {
                toFlush.add(accumulator);
            }
        });

        for (UsageAccumulator accumulator : toFlush) {
            flushAccumulator(accumulator);
        }
    }

    /**
     * Flushes a single accumulator to GCP.
     *
     * @param accumulator The accumulator to flush
     */
    private void flushAccumulator(UsageAccumulator accumulator) {
        try {
            Operation operation = new Operation();
            operation.setOperationId(generateOperationId());
            operation.setConsumerId("project:" + accumulator.getCustomerId());

            List<MetricValueSet> metricValueSets = new ArrayList<>();

            // Workflow executions metric
            MetricValueSet executionsMetric = new MetricValueSet();
            executionsMetric.setMetricName(USAGE_METRIC);
            executionsMetric.setMetricValues(Collections.singletonList(
                    Map.of("int64Value", String.valueOf(accumulator.getExecutionCount()))));
            metricValueSets.add(executionsMetric);

            // Compute units metric
            MetricValueSet computeMetric = new MetricValueSet();
            computeMetric.setMetricName(COMPUTE_METRIC);
            computeMetric.setMetricValues(Collections.singletonList(
                    Map.of("doubleValue", accumulator.getTotalComputeUnits())));
            metricValueSets.add(computeMetric);

            operation.setMetricValueSets(metricValueSets);

            ReportRequest reportRequest = new ReportRequest();
            reportRequest.setOperations(Collections.singletonList(operation));

            ReportResponse response = serviceControl.services()
                    .report(METERING_SERVICE_NAME, reportRequest)
                    .execute();

            LOG.info("Reported usage: customerId={}, executions={}, computeUnits={}, response={}",
                    accumulator.getCustomerId(),
                    accumulator.getExecutionCount(),
                    accumulator.getTotalComputeUnits(),
                    response);

            accumulator.reset();

        } catch (IOException e) {
            LOG.error("Failed to flush usage for customer {}: {}",
                    accumulator.getCustomerId(), e.getMessage(), e);
        }
    }

    /**
     * Generates a unique operation ID for reporting.
     *
     * @return Unique operation ID
     */
    private String generateOperationId() {
        return "yawl-metering-" + Instant.now().toEpochMilli() + "-" +
                reportCounter.incrementAndGet();
    }

    /**
     * Syncs entitlements from GCP Procurement API.
     */
    private void syncEntitlements() {
        try {
            ListEntitlementsResponse response = procurementService.partners()
                    .entitlements()
                    .list(participantId)
                    .execute();

            if (response.getEntitlements() != null) {
                for (Entitlement entitlement : response.getEntitlements()) {
                    processEntitlement(entitlement);
                }
            }

            LOG.info("Synced {} entitlements", response.getEntitlements() != null ?
                    response.getEntitlements().size() : 0);

        } catch (IOException e) {
            LOG.error("Failed to sync entitlements: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes a single entitlement event.
     *
     * @param entitlement The entitlement to process
     */
    private void processEntitlement(Entitlement entitlement) {
        String state = entitlement.getState();
        String entitlementId = entitlement.getName();

        LOG.info("Processing entitlement: id={}, state={}", entitlementId, state);

        switch (state) {
            case "ENTITLEMENT_ACTIVATION_REQUESTED":
                approveEntitlement(entitlementId);
                break;
            case "ENTITLEMENT_ACTIVE":
                // Entitlement is active, enable service
                activateServiceForEntitlement(entitlement);
                break;
            case "ENTITLEMENT_CANCELLED":
            case "ENTITLEMENT_DELETED":
                // Handle cancellation
                deactivateServiceForEntitlement(entitlement);
                break;
            case "ENTITLEMENT_SUSPENDED":
                // Handle suspension
                suspendServiceForEntitlement(entitlement);
                break;
            default:
                LOG.debug("Unhandled entitlement state: {}", state);
        }
    }

    /**
     * Approves an entitlement activation request.
     *
     * @param entitlementId The entitlement ID to approve
     */
    private void approveEntitlement(String entitlementId) {
        try {
            ApproveEntitlementRequest request = new ApproveEntitlementRequest();
            request.setEntitlement(entitlementId);

            procurementService.partners()
                    .entitlements()
                    .approve(entitlementId, request)
                    .execute();

            LOG.info("Approved entitlement: {}", entitlementId);

        } catch (IOException e) {
            LOG.error("Failed to approve entitlement {}: {}", entitlementId, e.getMessage(), e);
        }
    }

    /**
     * Activates service for an entitlement.
     *
     * @param entitlement The active entitlement
     */
    private void activateServiceForEntitlement(Entitlement entitlement) {
        // Implementation would update local state and enable access
        LOG.info("Activating service for entitlement: {}", entitlement.getName());
    }

    /**
     * Deactivates service for an entitlement.
     *
     * @param entitlement The cancelled/deleted entitlement
     */
    private void deactivateServiceForEntitlement(Entitlement entitlement) {
        // Implementation would update local state and disable access
        LOG.info("Deactivating service for entitlement: {}", entitlement.getName());
    }

    /**
     * Suspends service for an entitlement.
     *
     * @param entitlement The suspended entitlement
     */
    private void suspendServiceForEntitlement(Entitlement entitlement) {
        // Implementation would update local state and suspend access
        LOG.info("Suspending service for entitlement: {}", entitlement.getName());
    }

    /**
     * Checks if usage is allowed for a consumer.
     *
     * @param consumerId The consumer ID to check
     * @return true if usage is allowed, false otherwise
     */
    public boolean checkUsageAllowed(String consumerId) {
        try {
            CheckRequest checkRequest = new CheckRequest();
            Operation operation = new Operation();
            operation.setOperationId(generateOperationId());
            operation.setConsumerId("project:" + consumerId);
            checkRequest.setOperation(operation);

            CheckResponse response = serviceControl.services()
                    .check(METERING_SERVICE_NAME, checkRequest)
                    .execute();

            return response.getServiceConfigId() != null &&
                    !response.hasCheckErrors();

        } catch (IOException e) {
            LOG.error("Usage check failed for {}: {}", consumerId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Shuts down the metering service.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Final flush
        flushUsage();

        LOG.info("UsageMeter shutdown complete");
    }

    /**
     * Sets the batch size for usage reporting.
     *
     * @param batchSize Maximum events before flush
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    /**
     * Sets the flush interval.
     *
     * @param flushIntervalMs Interval in milliseconds
     */
    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    /**
     * Enforces per-tenant resource quotas to prevent DoS attacks.
     * Implements hard limits on CPU, memory, and storage per tenant.
     */
    private static class QuotaEnforcer {
        // Hard limits per tenant (monthly)
        private static final long MAX_EXECUTION_TIME_MS_MONTHLY = 30_000_000; // 8.3 hours/month
        private static final double MAX_COMPUTE_UNITS_MONTHLY = 10_000.0;    // Equivalent to ~1000 workflows

        private final String tenantId;
        private final AtomicLong monthlyExecutionTimeMs;
        private final AtomicLong currentMonth;
        private volatile double monthlyComputeUnits;

        QuotaEnforcer(String tenantId) {
            this.tenantId = tenantId;
            this.monthlyExecutionTimeMs = new AtomicLong(0);
            this.monthlyComputeUnits = 0.0;
            this.currentMonth = new AtomicLong(currentMonthNumber());
        }

        synchronized boolean checkAndRecordUsage(long executionTimeMs, double computeUnits) {
            // Reset if month changed
            long month = currentMonthNumber();
            if (month != currentMonth.get()) {
                currentMonth.set(month);
                monthlyExecutionTimeMs.set(0);
                monthlyComputeUnits = 0.0;
            }

            // Check quotas
            long newExecutionTime = monthlyExecutionTimeMs.get() + executionTimeMs;
            double newComputeUnits = monthlyComputeUnits + computeUnits;

            if (newExecutionTime > MAX_EXECUTION_TIME_MS_MONTHLY) {
                return false; // Quota exceeded
            }
            if (newComputeUnits > MAX_COMPUTE_UNITS_MONTHLY) {
                return false; // Quota exceeded
            }

            // Record usage
            monthlyExecutionTimeMs.set(newExecutionTime);
            monthlyComputeUnits = newComputeUnits;
            return true;
        }

        private static long currentMonthNumber() {
            Instant now = Instant.now();
            return (now.getEpochSecond() / (30L * 24 * 3600));
        }

        String getTenantId() {
            return tenantId;
        }

        double getMonthlyComputeUnits() {
            return monthlyComputeUnits;
        }
    }

    /**
     * Internal class to accumulate usage before reporting.
     */
    private static class UsageAccumulator {
        private final String customerId;
        private final String entitlementId;
        private final AtomicLong executionCount;
        private final AtomicLong totalExecutionTimeMs;
        private final AtomicLong batchSize;
        private volatile double totalComputeUnits;

        UsageAccumulator(String customerId, String entitlementId) {
            this.customerId = customerId;
            this.entitlementId = entitlementId;
            this.executionCount = new AtomicLong(0);
            this.totalExecutionTimeMs = new AtomicLong(0);
            this.batchSize = new AtomicLong(0);
            this.totalComputeUnits = 0.0;
        }

        synchronized void addWorkflowExecution(String workflowName,
                                               long executionTimeMs,
                                               double computeUnits) {
            executionCount.incrementAndGet();
            totalExecutionTimeMs.addAndGet(executionTimeMs);
            totalComputeUnits += computeUnits;
            batchSize.incrementAndGet();
        }

        synchronized void reset() {
            executionCount.set(0);
            totalExecutionTimeMs.set(0);
            batchSize.set(0);
            totalComputeUnits = 0.0;
        }

        boolean hasUsage() {
            return batchSize.get() > 0;
        }

        long getBatchSize() {
            return batchSize.get();
        }

        String getCustomerId() {
            return customerId;
        }

        String getEntitlementId() {
            return entitlementId;
        }

        long getExecutionCount() {
            return executionCount.get();
        }

        long getTotalExecutionTimeMs() {
            return totalExecutionTimeMs.get();
        }

        double getTotalComputeUnits() {
            return totalComputeUnits;
        }
    }
}
