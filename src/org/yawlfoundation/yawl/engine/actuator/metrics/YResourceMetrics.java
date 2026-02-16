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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Micrometer metrics for YAWL resource utilization.
 *
 * Provides metrics for:
 * - JVM memory usage (heap, non-heap)
 * - Thread counts and states
 * - CPU usage
 * - GC statistics
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Component
public class YResourceMetrics {

    private static final Logger _logger = LogManager.getLogger(YResourceMetrics.class);

    private final MeterRegistry registry;
    private final MemoryMXBean memoryMXBean;
    private final ThreadMXBean threadMXBean;
    private final Runtime runtime;

    private Gauge heapMemoryUsedGauge;
    private Gauge heapMemoryMaxGauge;
    private Gauge nonHeapMemoryUsedGauge;
    private Gauge threadCountGauge;
    private Gauge daemonThreadCountGauge;
    private Gauge peakThreadCountGauge;
    private Gauge availableProcessorsGauge;

    public YResourceMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
        this.runtime = Runtime.getRuntime();
    }

    @PostConstruct
    public void initializeMetrics() {
        _logger.info("Initializing YAWL resource utilization metrics");

        heapMemoryUsedGauge = Gauge.builder("yawl.jvm.memory.heap.used",
                this, YResourceMetrics::getHeapMemoryUsed)
            .description("Used heap memory in bytes")
            .baseUnit("bytes")
            .register(registry);

        heapMemoryMaxGauge = Gauge.builder("yawl.jvm.memory.heap.max",
                this, YResourceMetrics::getHeapMemoryMax)
            .description("Maximum heap memory in bytes")
            .baseUnit("bytes")
            .register(registry);

        nonHeapMemoryUsedGauge = Gauge.builder("yawl.jvm.memory.nonheap.used",
                this, YResourceMetrics::getNonHeapMemoryUsed)
            .description("Used non-heap memory in bytes")
            .baseUnit("bytes")
            .register(registry);

        threadCountGauge = Gauge.builder("yawl.jvm.threads.live",
                this, YResourceMetrics::getThreadCount)
            .description("Current number of live threads")
            .register(registry);

        daemonThreadCountGauge = Gauge.builder("yawl.jvm.threads.daemon",
                this, YResourceMetrics::getDaemonThreadCount)
            .description("Current number of daemon threads")
            .register(registry);

        peakThreadCountGauge = Gauge.builder("yawl.jvm.threads.peak",
                this, YResourceMetrics::getPeakThreadCount)
            .description("Peak number of threads")
            .register(registry);

        availableProcessorsGauge = Gauge.builder("yawl.system.cpu.count",
                this, YResourceMetrics::getAvailableProcessors)
            .description("Number of available processors")
            .register(registry);

        Gauge.builder("yawl.jvm.memory.heap.usage",
                this, YResourceMetrics::getHeapMemoryUsagePercent)
            .description("Heap memory usage percentage")
            .baseUnit("percent")
            .register(registry);

        _logger.info("YAWL resource utilization metrics initialized successfully");
    }

    private double getHeapMemoryUsed() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    private double getHeapMemoryMax() {
        return memoryMXBean.getHeapMemoryUsage().getMax();
    }

    private double getNonHeapMemoryUsed() {
        return memoryMXBean.getNonHeapMemoryUsage().getUsed();
    }

    private double getThreadCount() {
        return threadMXBean.getThreadCount();
    }

    private double getDaemonThreadCount() {
        return threadMXBean.getDaemonThreadCount();
    }

    private double getPeakThreadCount() {
        return threadMXBean.getPeakThreadCount();
    }

    private double getAvailableProcessors() {
        return runtime.availableProcessors();
    }

    private double getHeapMemoryUsagePercent() {
        long used = memoryMXBean.getHeapMemoryUsage().getUsed();
        long max = memoryMXBean.getHeapMemoryUsage().getMax();
        if (max <= 0) {
            return 0.0;
        }
        return (double) used / max * 100.0;
    }

    public long getHeapMemoryUsedBytes() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    public long getHeapMemoryMaxBytes() {
        return memoryMXBean.getHeapMemoryUsage().getMax();
    }

    public int getCurrentThreadCount() {
        return threadMXBean.getThreadCount();
    }

    public double getMemoryUsagePercent() {
        return getHeapMemoryUsagePercent();
    }
}
