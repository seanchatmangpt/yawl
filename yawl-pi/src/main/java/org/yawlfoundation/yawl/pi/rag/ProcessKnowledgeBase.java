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

package org.yawlfoundation.yawl.pi.rag;

import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Knowledge base for storing and retrieving process mining facts.
 *
 * <p>Ingests facts from process mining reports and makes them available
 * for natural language queries via keyword-based retrieval. Thread-safe
 * using read-write locks for concurrent access.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ProcessKnowledgeBase {

    private final ZaiService zaiService;
    private final List<KnowledgeEntry> entries;
    private final ReadWriteLock entriesLock;

    /**
     * Create a new process knowledge base.
     *
     * @param zaiService Z.AI service for future embedding operations
     */
    public ProcessKnowledgeBase(ZaiService zaiService) {
        this.zaiService = zaiService;
        this.entries = new ArrayList<>();
        this.entriesLock = new ReentrantReadWriteLock();
    }

    /**
     * Ingest facts from a process mining report into the knowledge base.
     *
     * <p>Extracts performance metrics, conformance data, and variant information,
     * creating human-readable facts that are stored as knowledge entries.</p>
     *
     * @param specId specification identifier
     * @param report process mining analysis report
     * @throws PIException if ingestion fails
     */
    public void ingest(YSpecificationID specId, ProcessMiningFacade.ProcessMiningReport report)
            throws PIException {
        if (specId == null) {
            throw new PIException("Specification ID cannot be null", "rag");
        }
        if (report == null) {
            throw new PIException("Process mining report cannot be null", "rag");
        }

        String specIdStr = specId.getIdentifier() + "_v" + specId.getVersion();
        entriesLock.writeLock().lock();
        try {
            // Extract performance metrics
            if (report.performance != null) {
                // Average flow time
                String avgFlowTimeText = String.format(
                    "Specification %s average flow time is %d ms",
                    specIdStr, report.performance.avgFlowTimeMs
                );
                addEntry("perf_avg_flow_" + specIdStr, specIdStr, avgFlowTimeText,
                         "performance", report);

                // Throughput
                String throughputText = String.format(
                    "Specification %s throughput is %.2f cases per hour",
                    specIdStr, report.performance.throughputPerHour
                );
                addEntry("perf_throughput_" + specIdStr, specIdStr, throughputText,
                         "performance", report);

                // Trace count
                String traceCountText = String.format(
                    "Specification %s has %d recorded cases",
                    specIdStr, report.traceCount
                );
                addEntry("perf_trace_count_" + specIdStr, specIdStr, traceCountText,
                         "performance", report);
            }

            // Extract variant information
            if (report.variantFrequencies != null && !report.variantFrequencies.isEmpty()) {
                // Variant count
                String variantCountText = String.format(
                    "Specification %s has %d distinct process variants",
                    specIdStr, report.variantCount
                );
                addEntry("var_count_" + specIdStr, specIdStr, variantCountText,
                         "variant", report);

                // Top variant
                var topVariant = report.variantFrequencies.entrySet().stream()
                    .max(Map.Entry.comparingByValue());
                if (topVariant.isPresent()) {
                    String topVariantText = String.format(
                        "Most frequent variant in %s: %s (%d cases)",
                        specIdStr, topVariant.get().getKey(), topVariant.get().getValue()
                    );
                    addEntry("var_top_" + specIdStr, specIdStr, topVariantText,
                             "variant", report);
                }
            }

            // Extract conformance information
            if (report.conformance != null) {
                double fitness = report.conformance.computeFitness();
                String conformanceText = String.format(
                    "Specification %s conformance fitness score is %.3f",
                    specIdStr, fitness
                );
                addEntry("conf_fitness_" + specIdStr, specIdStr, conformanceText,
                         "conformance", report);
            }
        } finally {
            entriesLock.writeLock().unlock();
        }
    }

    /**
     * Retrieve relevant facts for a query using keyword-based matching.
     *
     * @param query search query
     * @param topK maximum number of results to return
     * @return list of relevant knowledge entries
     * @throws PIException if retrieval fails
     */
    public List<KnowledgeEntry> retrieve(String query, int topK) throws PIException {
        if (query == null || query.isEmpty()) {
            throw new PIException("Query cannot be null or empty", "rag");
        }

        entriesLock.readLock().lock();
        try {
            ProcessContextRetriever retriever = new ProcessContextRetriever();
            return retriever.retrieveRelevant(new ArrayList<>(entries), query, topK);
        } finally {
            entriesLock.readLock().unlock();
        }
    }

    /**
     * Remove all entries for a specific specification from the knowledge base.
     *
     * @param specId specification identifier to evict
     * @throws PIException if eviction fails
     */
    public void evict(YSpecificationID specId) throws PIException {
        if (specId == null) {
            throw new PIException("Specification ID cannot be null", "rag");
        }

        String specIdStr = specId.getIdentifier() + "_v" + specId.getVersion();
        entriesLock.writeLock().lock();
        try {
            entries.removeIf(e -> e.specificationId().equals(specIdStr));
        } finally {
            entriesLock.writeLock().unlock();
        }
    }

    /**
     * Get the current number of entries in the knowledge base.
     *
     * @return entry count
     */
    public int size() {
        entriesLock.readLock().lock();
        try {
            return entries.size();
        } finally {
            entriesLock.readLock().unlock();
        }
    }

    /**
     * Add a knowledge entry to the base.
     *
     * @param entryId unique entry identifier
     * @param specId specification identifier
     * @param factText human-readable fact
     * @param factType categorization
     * @param report source report for metadata
     */
    private void addEntry(String entryId, String specId, String factText, String factType,
                          ProcessMiningFacade.ProcessMiningReport report) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("traceCount", report.traceCount);
        metadata.put("variantCount", report.variantCount);
        metadata.put("analysisTime", report.analysisTime);

        KnowledgeEntry entry = new KnowledgeEntry(
            entryId,
            specId,
            factText,
            factType,
            null,  // embedding: not computed during ingest
            Instant.now(),
            metadata
        );

        entries.add(entry);
    }
}
