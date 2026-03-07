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

/**
 * Statistical Benchmark Framework for PhD-Quality Performance Testing.
 *
 * <h2>Overview</h2>
 * <p>This package provides rigorous statistical analysis for performance
 * benchmarks, meeting the standards required for ICSE/SE conference
 * publications.</p>
 *
 * <h2>Statistical Rigor</h2>
 * <ul>
 *   <li>99% confidence intervals (α = 0.01)</li>
 *   <li>Welch's t-test for comparisons (handles unequal variances)</li>
 *   <li>Cohen's d effect size calculation</li>
 *   <li>Normality testing (Shapiro-Wilk approximation)</li>
 *   <li>Outlier detection via percentile analysis</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create benchmark with PhD requirements
 * StatisticalBenchmark benchmark = new StatisticalBenchmark(100, 0.99);
 *
 * // Warmup JIT
 * benchmark.warmup(100, () -> engine.launchCase(spec, caseId));
 *
 * // Measure single operation
 * BenchmarkResult result = benchmark.measure(
 *     "Case Launch Latency",
 *     () -> engine.launchCase(spec, caseId)
 * );
 *
 * // Compare implementations
 * ComparisonResult comparison = benchmark.compare(
 *     "Stateless vs Stateful Engine",
 *     () -> statelessEngine.launchCase(spec, caseId),
 *     () -> statefulEngine.launchCase(spec, caseId)
 * );
 *
 * // Generate PhD-ready report
 * System.out.println(result.toPhdReport());
 * System.out.println(comparison.toPhdReport());
 * }</pre>
 *
 * <h2>PhD Publication Requirements</h2>
 * <table border="1">
 *   <tr><th>Metric</th><th>Requirement</th></tr>
 *   <tr><td>Sample Size</td><td>≥ 100 (Central Limit Theorem)</td></tr>
 *   <tr><td>Confidence Level</td><td>99% (α = 0.01)</td></tr>
 *   <tr><td>Significance</td><td>p-value < 0.01</td></tr>
 *   <tr><td>Effect Size</td><td>Cohen's d reported</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see StatisticalBenchmark
 */
package org.yawlfoundation.yawl.benchmark;
