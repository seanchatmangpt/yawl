/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.yawlfoundation.yawl.graalpy.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Performance benchmark utility for measuring execution time and memory usage
 */
public class PerformanceBenchmark {

    /**
     * Measures execution time of a supplier
     * @param supplier The supplier to benchmark
     * @param <T> Return type of supplier
     * @return Execution time in milliseconds
     */
    public <T> long measureExecutionTime(Supplier<T> supplier) {
        Instant start = Instant.now();
        supplier.get();
        Instant end = Instant.now();
        return Duration.between(start, end).toMillis();
    }

    /**
     * Measures memory usage of a supplier
     * @param supplier The supplier to benchmark
     * @return Memory usage in KB
     */
    public long measureMemoryUsage(Supplier<Object> supplier) {
        Runtime runtime = Runtime.getRuntime();

        // Run garbage collection to get more accurate measurements
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        supplier.get();

        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        return (memoryAfter - memoryBefore) / 1024; // Convert to KB
    }
}
