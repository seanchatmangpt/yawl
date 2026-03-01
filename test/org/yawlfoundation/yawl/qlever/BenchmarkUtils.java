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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.qlever;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for benchmark operations and performance measurement.
 */
public final class BenchmarkUtils {
    
    private BenchmarkUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Gets the total number of GC collections across all GCs.
     */
    public static long getGcCount() {
        return getGarbageCollectorMXBeans().stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * Gets the total time spent in GC in milliseconds.
     */
    public static long getGcTime() {
        return getGarbageCollectorMXBeans().stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
    
    /**
     * Estimates GC pause time based on GC cycles (approximation).
     */
    public static long estimateGcPauseTime(long gcBefore, long gcAfter) {
        // Rough estimate: ~10ms per GC cycle
        return (gcAfter - gcBefore) * 10;
    }
    
    /**
     * Calculates memory usage in bytes.
     */
    public static long getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    /**
     * Calculates mean of a list of values.
     */
    public static double calculateMean(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return values.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }
    
    /**
     * Calculates median of a list of values.
     */
    public static double calculateMedian(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream()
            .sorted()
            .collect(Collectors.toList());
        
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        } else {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        }
    }
    
    /**
     * Calculates standard deviation of a list of values.
     */
    public static double calculateStandardDeviation(List<Long> values, double mean) {
        if (values.isEmpty()) {
            return 0;
        }
        double variance = values.stream()
            .mapToDouble(value -> Math.pow(value - mean, 2))
            .average()
            .orElse(0);
        return Math.sqrt(variance);
    }
    
    /**
     * Calculates 95th percentile of a list of values.
     */
    public static double calculateP95(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = values.stream()
            .sorted()
            .collect(Collectors.toList());
        
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(index);
    }
    
    /**
     * Calculates operations per second from duration in nanoseconds.
     */
    public static double calculateOpsPerSecond(long totalNanos, int operations) {
        if (totalNanos == 0 || operations == 0) {
            return 0;
        }
        return (operations * 1_000_000_000.0) / totalNanos;
    }
    
    private static List<GarbageCollectorMXBean> getGarbageCollectorMXBeans() {
        return ManagementFactory.getGarbageCollectorMXBeans();
    }
}
