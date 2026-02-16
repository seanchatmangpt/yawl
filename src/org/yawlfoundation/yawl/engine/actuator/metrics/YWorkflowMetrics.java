/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.actuator.metrics;

import io.micrometer.core.instrument.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YNetRunnerRepository;
import org.yawlfoundation.yawl.engine.YSpecificationTable;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metrics for YAWL workflow execution.
 *
 * Provides metrics for:
 * - Case lifecycle (launched, completed, cancelled)
 * - Work item processing (enabled, started, completed, failed)
 * - Execution times and throughput
 * - Active workflow instances
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component
public class YWorkflowMetrics {


    private static final Logger logger = LogManager.getLogger(YWorkflowMetrics.class);
    private static final Logger _logger = LogManager.getLogger(YWorkflowMetrics.class);

    private final MeterRegistry registry;
    private final YEngine engine;
    private final YWorkItemRepository workItemRepository;
    private final YNetRunnerRepository netRunnerRepository;
    private final YSpecificationTable specificationTable;

    private final AtomicInteger casesLaunched;
    private final AtomicInteger casesCompleted;
    private final AtomicInteger casesCancelled;
    private final AtomicInteger casesFailed;

    private final AtomicInteger workItemsEnabled;
    private final AtomicInteger workItemsStarted;
    private final AtomicInteger workItemsCompleted;
    private final AtomicInteger workItemsFailed;

    private final AtomicLong totalExecutionTime;
    private final AtomicInteger executionCount;

    private Counter caseLaunchCounter;
    private Counter caseCompletionCounter;
    private Counter caseCancellationCounter;
    private Counter caseFailureCounter;

    private Counter workItemEnabledCounter;
    private Counter workItemStartedCounter;
    private Counter workItemCompletedCounter;
    private Counter workItemFailedCounter;

    private Timer caseExecutionTimer;
    private Timer workItemExecutionTimer;

    private Gauge activeCasesGauge;
    private Gauge activeWorkItemsGauge;
    private Gauge loadedSpecificationsGauge;

    public YWorkflowMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.engine = YEngine.getInstance();
        this.workItemRepository = YWorkItemRepository.getInstance();
        this.netRunnerRepository = engine.getNetRunnerRepository();
        this.specificationTable = YSpecificationTable.getInstance();

        this.casesLaunched = new AtomicInteger(0);
        this.casesCompleted = new AtomicInteger(0);
        this.casesCancelled = new AtomicInteger(0);
        this.casesFailed = new AtomicInteger(0);

        this.workItemsEnabled = new AtomicInteger(0);
        this.workItemsStarted = new AtomicInteger(0);
        this.workItemsCompleted = new AtomicInteger(0);
        this.workItemsFailed = new AtomicInteger(0);

        this.totalExecutionTime = new AtomicLong(0);
        this.executionCount = new AtomicInteger(0);
    }

    @PostConstruct
    public void initializeMetrics() {
        _logger.info("Initializing YAWL workflow metrics");

        caseLaunchCounter = Counter.builder("yawl.cases.launched")
            .description("Total number of workflow cases launched")
            .tag("engine", "yawl")
            .register(registry);

        caseCompletionCounter = Counter.builder("yawl.cases.completed")
            .description("Total number of workflow cases completed")
            .tag("engine", "yawl")
            .register(registry);

        caseCancellationCounter = Counter.builder("yawl.cases.cancelled")
            .description("Total number of workflow cases cancelled")
            .tag("engine", "yawl")
            .register(registry);

        caseFailureCounter = Counter.builder("yawl.cases.failed")
            .description("Total number of workflow cases failed")
            .tag("engine", "yawl")
            .register(registry);

        workItemEnabledCounter = Counter.builder("yawl.workitems.enabled")
            .description("Total number of work items enabled")
            .tag("engine", "yawl")
            .register(registry);

        workItemStartedCounter = Counter.builder("yawl.workitems.started")
            .description("Total number of work items started")
            .tag("engine", "yawl")
            .register(registry);

        workItemCompletedCounter = Counter.builder("yawl.workitems.completed")
            .description("Total number of work items completed")
            .tag("engine", "yawl")
            .register(registry);

        workItemFailedCounter = Counter.builder("yawl.workitems.failed")
            .description("Total number of work items failed")
            .tag("engine", "yawl")
            .register(registry);

        caseExecutionTimer = Timer.builder("yawl.case.execution.time")
            .description("Time taken to execute workflow cases")
            .tag("engine", "yawl")
            .register(registry);

        workItemExecutionTimer = Timer.builder("yawl.workitem.execution.time")
            .description("Time taken to execute work items")
            .tag("engine", "yawl")
            .register(registry);

        activeCasesGauge = Gauge.builder("yawl.cases.active", this::getActiveCaseCount)
            .description("Number of currently active workflow cases")
            .tag("engine", "yawl")
            .register(registry);

        activeWorkItemsGauge = Gauge.builder("yawl.workitems.active", this::getActiveWorkItemCount)
            .description("Number of currently active work items")
            .tag("engine", "yawl")
            .register(registry);

        loadedSpecificationsGauge = Gauge.builder("yawl.specifications.loaded", this::getLoadedSpecificationCount)
            .description("Number of loaded workflow specifications")
            .tag("engine", "yawl")
            .register(registry);

        _logger.info("YAWL workflow metrics initialized successfully");
    }

    public void recordCaseLaunched() {
        casesLaunched.incrementAndGet();
        caseLaunchCounter.increment();
    }

    public void recordCaseCompleted(long executionTimeMs) {
        casesCompleted.incrementAndGet();
        caseCompletionCounter.increment();
        caseExecutionTimer.record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        totalExecutionTime.addAndGet(executionTimeMs);
        executionCount.incrementAndGet();
    }

    public void recordCaseCancelled() {
        casesCancelled.incrementAndGet();
        caseCancellationCounter.increment();
    }

    public void recordCaseFailed() {
        casesFailed.incrementAndGet();
        caseFailureCounter.increment();
    }

    public void recordWorkItemEnabled() {
        workItemsEnabled.incrementAndGet();
        workItemEnabledCounter.increment();
    }

    public void recordWorkItemStarted() {
        workItemsStarted.incrementAndGet();
        workItemStartedCounter.increment();
    }

    public void recordWorkItemCompleted(long executionTimeMs) {
        workItemsCompleted.incrementAndGet();
        workItemCompletedCounter.increment();
        workItemExecutionTimer.record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordWorkItemFailed() {
        workItemsFailed.incrementAndGet();
        workItemFailedCounter.increment();
    }

    private int getActiveCaseCount() {
        try {
            return engine.getRunningCaseCount();
        } catch (Exception e) {
            _logger.warn("Failed to get active case count for metrics", e);
            return 0;
        }
    }

    private int getActiveWorkItemCount() {
        try {
            return workItemRepository.getWorkItemCount();
        } catch (Exception e) {
            _logger.warn("Failed to get active work item count for metrics", e);
            return 0;
        }
    }

    private int getLoadedSpecificationCount() {
        try {
            return engine.getLoadedSpecificationCount();
        } catch (Exception e) {
            _logger.warn("Failed to get loaded specification count for metrics", e);
            return 0;
        }
    }

    public double getAverageExecutionTime() {
        int count = executionCount.get();
        if (count == 0) {
            return 0.0;
        }
        return (double) totalExecutionTime.get() / count;
    }

    public int getTotalCasesLaunched() {
        return casesLaunched.get();
    }

    public int getTotalCasesCompleted() {
        return casesCompleted.get();
    }

    public int getTotalWorkItemsCompleted() {
        return workItemsCompleted.get();
    }
}
