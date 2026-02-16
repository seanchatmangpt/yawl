/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.benchmarks;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom benchmark harness for measuring performance.
 * 
 * Provides warmup, measurement iterations, statistical analysis.
 * Simpler alternative to JMH for this specific use case.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class BenchmarkHarness {

    private final String benchmarkName;
    private final int warmupIterations;
    private final int measurementIterations;
    private final List<Long> latencies;

    public BenchmarkHarness(String benchmarkName) {
        this(benchmarkName, 10, 100);
    }

    public BenchmarkHarness(String benchmarkName, int warmupIterations, int measurementIterations) {
        if (benchmarkName == null || benchmarkName.isEmpty()) {
            throw new IllegalArgumentException("benchmarkName is required");
        }
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must be >= 0");
        }
        if (measurementIterations < 1) {
            throw new IllegalArgumentException("measurementIterations must be >= 1");
        }
        this.benchmarkName = benchmarkName;
        this.warmupIterations = warmupIterations;
        this.measurementIterations = measurementIterations;
        this.latencies = new ArrayList<>(measurementIterations);
    }

    /**
     * Run the benchmark with warmup and measurement phases.
     *
     * @param operation the operation to benchmark
     * @return benchmark result with statistics
     */
    public BenchmarkResult run(BenchmarkOperation operation) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }

        System.out.println("\n=== Benchmark: " + benchmarkName + " ===");
        System.out.println("Warmup iterations: " + warmupIterations);
        System.out.println("Measurement iterations: " + measurementIterations);

        operation.setup();

        try {
            System.out.print("Warmup: ");
            for (int i = 0; i < warmupIterations; i++) {
                operation.run();
                if (i % 10 == 9) {
                    System.out.print(".");
                }
            }
            System.out.println(" done");

            System.gc();
            Thread.sleep(100);

            System.out.print("Measurement: ");
            latencies.clear();
            for (int i = 0; i < measurementIterations; i++) {
                long start = System.nanoTime();
                operation.run();
                long end = System.nanoTime();
                latencies.add(end - start);
                if (i % 10 == 9) {
                    System.out.print(".");
                }
            }
            System.out.println(" done");

            return computeResult();

        } finally {
            operation.teardown();
        }
    }

    /**
     * Measure throughput (operations per second).
     *
     * @param operation the operation to benchmark
     * @param durationSeconds how long to run
     * @return operations per second
     */
    public double measureThroughput(BenchmarkOperation operation, int durationSeconds) throws Exception {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        if (durationSeconds < 1) {
            throw new IllegalArgumentException("durationSeconds must be >= 1");
        }

        System.out.println("\n=== Throughput: " + benchmarkName + " ===");
        System.out.println("Duration: " + durationSeconds + " seconds");

        operation.setup();

        try {
            System.out.print("Warmup: ");
            for (int i = 0; i < warmupIterations; i++) {
                operation.run();
            }
            System.out.println(" done");

            System.gc();
            Thread.sleep(100);

            long startTime = System.currentTimeMillis();
            long endTime = startTime + (durationSeconds * 1000L);
            long count = 0;

            System.out.print("Measuring: ");
            while (System.currentTimeMillis() < endTime) {
                operation.run();
                count++;
                if (count % 100 == 0) {
                    System.out.print(".");
                }
            }
            System.out.println(" done");

            long actualDuration = System.currentTimeMillis() - startTime;
            double throughput = (count * 1000.0) / actualDuration;

            System.out.printf("Operations: %d in %d ms%n", count, actualDuration);
            System.out.printf("Throughput: %.2f ops/sec%n", throughput);

            return throughput;

        } finally {
            operation.teardown();
        }
    }

    private BenchmarkResult computeResult() {
        if (latencies.isEmpty()) {
            throw new IllegalStateException("No measurements taken");
        }

        latencies.sort(Long::compareTo);

        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long sum = 0;
        for (long lat : latencies) {
            sum += lat;
        }
        double mean = (double) sum / latencies.size();

        double variance = 0;
        for (long lat : latencies) {
            double diff = lat - mean;
            variance += diff * diff;
        }
        double stddev = Math.sqrt(variance / latencies.size());

        long p50 = latencies.get((int) (latencies.size() * 0.50));
        long p95 = latencies.get((int) (latencies.size() * 0.95));
        long p99 = latencies.get((int) (latencies.size() * 0.99));

        BenchmarkResult result = new BenchmarkResult(
            benchmarkName,
            measurementIterations,
            mean,
            stddev,
            min,
            max,
            p50,
            p95,
            p99
        );

        result.print();

        return result;
    }

    /**
     * Interface for benchmark operations.
     */
    public interface BenchmarkOperation {
        /**
         * Setup before benchmark (not measured).
         */
        default void setup() throws Exception {}

        /**
         * The operation to benchmark (measured).
         */
        void run() throws Exception;

        /**
         * Teardown after benchmark (not measured).
         */
        default void teardown() throws Exception {}
    }

    /**
     * Benchmark result with statistics.
     */
    public static final class BenchmarkResult {
        private final String name;
        private final int iterations;
        private final double meanNs;
        private final double stddevNs;
        private final long minNs;
        private final long maxNs;
        private final long p50Ns;
        private final long p95Ns;
        private final long p99Ns;

        public BenchmarkResult(String name, int iterations, double meanNs, double stddevNs,
                              long minNs, long maxNs, long p50Ns, long p95Ns, long p99Ns) {
            this.name = name;
            this.iterations = iterations;
            this.meanNs = meanNs;
            this.stddevNs = stddevNs;
            this.minNs = minNs;
            this.maxNs = maxNs;
            this.p50Ns = p50Ns;
            this.p95Ns = p95Ns;
            this.p99Ns = p99Ns;
        }

        public String getName() { return name; }
        public int getIterations() { return iterations; }
        public double getMeanMs() { return meanNs / 1_000_000.0; }
        public double getStddevMs() { return stddevNs / 1_000_000.0; }
        public double getMinMs() { return minNs / 1_000_000.0; }
        public double getMaxMs() { return maxNs / 1_000_000.0; }
        public double getP50Ms() { return p50Ns / 1_000_000.0; }
        public double getP95Ms() { return p95Ns / 1_000_000.0; }
        public double getP99Ms() { return p99Ns / 1_000_000.0; }

        public void print() {
            System.out.println("\n--- Results ---");
            System.out.printf("Iterations:  %d%n", iterations);
            System.out.printf("Mean:        %.3f ms (± %.3f ms)%n", getMeanMs(), getStddevMs());
            System.out.printf("Min:         %.3f ms%n", getMinMs());
            System.out.printf("P50:         %.3f ms%n", getP50Ms());
            System.out.printf("P95:         %.3f ms%n", getP95Ms());
            System.out.printf("P99:         %.3f ms%n", getP99Ms());
            System.out.printf("Max:         %.3f ms%n", getMaxMs());
        }

        /**
         * Calculate overhead percentage compared to baseline.
         *
         * @param baseline the baseline result
         * @return overhead percentage (positive means slower, negative means faster)
         */
        public double overheadPercentage(BenchmarkResult baseline) {
            if (baseline == null) {
                throw new IllegalArgumentException("baseline is required");
            }
            return ((this.meanNs - baseline.meanNs) / baseline.meanNs) * 100.0;
        }
    }
}
