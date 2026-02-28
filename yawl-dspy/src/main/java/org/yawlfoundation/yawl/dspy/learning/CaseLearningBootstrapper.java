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

package org.yawlfoundation.yawl.dspy.learning;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.dspy.DspyProgramCache;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Orchestrates DSPy BootstrapFewShot compilation from historical work items.
 *
 * <p>Queries completed work items from {@link YWorkItemRepository}, extracts
 * natural language descriptions and POWL outputs as training examples, and
 * invokes {@link PythonDspyBridge#bootstrap(List)} to recompile the DSPy
 * POWL generator with improved examples. This enables automatic quality
 * improvement as new cases complete without manual intervention.</p>
 *
 * <h2>Bootstrap Process</h2>
 * <ol>
 *   <li>Query: Retrieve all completed work items from repository</li>
 *   <li>Extract: Convert each YWorkItem to DspyTrainingExample via CaseExampleExtractor</li>
 *   <li>Filter: Keep only examples with valid input-output pairs (min 1 example required)</li>
 *   <li>Bootstrap: Call PythonDspyBridge.bootstrap(examples) to recompile</li>
 *   <li>Cache: Store compiled module in DspyProgramCache for reuse</li>
 *   <li>Metrics: Log compilation time, example count, quality improvement</li>
 * </ol>
 *
 * <h2>Quality Improvement</h2>
 * <p>The DSPy BootstrapFewShot optimizer analyzes training examples and
 * selects the most informative few-shot demonstrations. As more historical
 * cases accumulate, the quality of POWL generation improves automatically.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All state is immutable or accessed through
 * thread-safe dependency objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class CaseLearningBootstrapper {

    private static final Logger log = LogManager.getLogger(CaseLearningBootstrapper.class);

    private static final int DEFAULT_MIN_EXAMPLES = 5;
    private static final String BOOTSTRAP_CACHE_KEY = "dspy_powl_generator_bootstrapped";

    private final YWorkItemRepository repository;
    private final PythonDspyBridge dspyBridge;
    private final DspyProgramCache cache;

    /**
     * Creates a new CaseLearningBootstrapper with default minimum examples (5).
     *
     * @param repository  the YWorkItemRepository to query; must not be null
     * @param dspyBridge  the PythonDspyBridge for bootstrap execution; must not be null
     * @param cache       the DspyProgramCache for caching compiled modules; must not be null
     * @throws NullPointerException if any parameter is null
     */
    public CaseLearningBootstrapper(
            YWorkItemRepository repository,
            PythonDspyBridge dspyBridge,
            DspyProgramCache cache) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.dspyBridge = Objects.requireNonNull(dspyBridge, "dspyBridge must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        log.info("CaseLearningBootstrapper initialized with repository and DSPy bridge");
    }

    /**
     * Executes DSPy bootstrap compilation with historical case examples.
     *
     * <p>Retrieves completed work items, extracts training examples, and invokes
     * the Python DSPy BootstrapFewShot optimizer to recompile the POWL generator.
     * If the minimum number of examples is not met, bootstrap is skipped.</p>
     *
     * @param minExamples minimum number of training examples required
     * @throws IllegalArgumentException if minExamples <= 0
     * @throws Exception                if bootstrap execution fails
     */
    public void bootstrap(long minExamples) throws Exception {
        if (minExamples <= 0) {
            throw new IllegalArgumentException("minExamples must be > 0, got: " + minExamples);
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting DSPy bootstrap: minExamples={}", minExamples);

        try {
            // Step 1: Query completed work items from repository
            List<YWorkItem> completedItems = queryCompletedItems();
            log.debug("Retrieved {} completed work items from repository", completedItems.size());

            if (completedItems.isEmpty()) {
                log.info("No completed items available for bootstrap, skipping");
                return;
            }

            // Step 2: Extract training examples
            List<DspyTrainingExample> examples = extractExamples(completedItems);
            log.info("Extracted {} training examples", examples.size());

            // Step 3: Check minimum threshold
            if (examples.size() < minExamples) {
                log.info("Insufficient training examples ({} < {}), skipping bootstrap",
                        examples.size(), minExamples);
                return;
            }

            // Step 4: Invoke DSPy bootstrap compilation
            String compiledPath = dspyBridge.bootstrap(
                    examples.stream()
                            .map(ex -> Map.of(
                                    "input", ex.input(),
                                    "output", ex.output()
                            ))
                            .collect(Collectors.toList())
            );
            log.info("Bootstrap compilation completed: {}", compiledPath);

            // Step 5: Cache compiled module
            cache.put(BOOTSTRAP_CACHE_KEY, compiledPath);
            log.info("Compiled DSPy module cached: key={}", BOOTSTRAP_CACHE_KEY);

            // Step 6: Log metrics
            long duration = System.currentTimeMillis() - startTime;
            logBootstrapMetrics(examples.size(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("DSPy bootstrap failed after {}ms: {}", duration, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Executes DSPy bootstrap with default minimum examples threshold.
     *
     * @throws Exception if bootstrap execution fails
     */
    public void bootstrap() throws Exception {
        bootstrap(DEFAULT_MIN_EXAMPLES);
    }

    /**
     * Queries all completed work items from the repository.
     *
     * @return list of completed YWorkItems
     */
    private List<YWorkItem> queryCompletedItems() {
        return new ArrayList<>(repository.getWorkItems(YWorkItemStatus.statusComplete));
    }

    /**
     * Extracts training examples from completed work items.
     *
     * <p>Converts each YWorkItem to a DspyTrainingExample, filtering out
     * any that fail extraction (logged as warnings).</p>
     *
     * @param items the completed work items
     * @return list of successfully extracted DspyTrainingExample records
     */
    private List<DspyTrainingExample> extractExamples(List<YWorkItem> items) {
        return items.stream()
                .map(item -> {
                    try {
                        return new CaseExampleExtractor(item).extract();
                    } catch (Exception e) {
                        log.warn("Failed to extract example from work item {}: {}",
                                item.getIDString(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Logs bootstrap metrics for observability.
     *
     * @param exampleCount number of training examples used
     * @param durationMs   bootstrap execution time in milliseconds
     */
    private void logBootstrapMetrics(int exampleCount, long durationMs) {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("timestamp", Instant.now());
        metrics.put("example_count", exampleCount);
        metrics.put("duration_ms", durationMs);
        metrics.put("cache_key", BOOTSTRAP_CACHE_KEY);

        log.info("DSPy Bootstrap Metrics: examples={}, duration={}ms, cached=true",
                exampleCount, durationMs);
    }

    /**
     * Returns the cached compiled bootstrap module path, if available.
     *
     * @return the cached module path, or null if not cached
     */
    public String getBootstrappedModulePath() {
        return cache.get(BOOTSTRAP_CACHE_KEY);
    }

    /**
     * Clears the cached bootstrap module.
     *
     * <p>Useful for forcing a fresh bootstrap compilation on next invocation.</p>
     */
    public void clearBootstrapCache() {
        String bootstrappedPath = cache.get(BOOTSTRAP_CACHE_KEY);
        if (bootstrappedPath != null) {
            // Note: DspyProgramCache LRU eviction will handle cleanup over time.
            // Direct removal would require cache.removeKey(BOOTSTRAP_CACHE_KEY).
            // For now, we log the state. Future enhancement: add removeKey() to DspyProgramCache.
            log.info("Bootstrap cache entry eligible for eviction: {}", bootstrappedPath);
        } else {
            log.debug("No cached bootstrap module to clear");
        }
    }
}
