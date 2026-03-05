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

package org.yawlfoundation.yawl.integration.mcp.safe;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Thread-safe in-memory model registry for SAFe Responsible AI governance.
 *
 * Manages versioned AI models with promotion workflow:
 * - register: add new candidate version
 * - promote: move version to PROMOTED with ResponsibleAiReceipt proof
 * - rollback: revert to prior candidate version
 * - getEntry: current entry (highest promoted, else highest candidate)
 * - getHistory: all versions ordered by registeredAt descending
 *
 * Thread safety: Uses {@link ConcurrentHashMap} keyed by modelId.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ModelRegistry {
    private final Map<String, List<ModelRegistryEntry>> registry = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Register a new model version.
     *
     * @param entry non-null model registry entry
     * @throws IllegalArgumentException if modelId+version already registered
     */
    public void register(ModelRegistryEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }

        String modelId = entry.modelId();
        String version = entry.version();

        registry.compute(modelId, (key, entries) -> {
            List<ModelRegistryEntry> list = entries != null ? new ArrayList<>(entries) : new ArrayList<>();

            // Check for duplicate
            for (ModelRegistryEntry existing : list) {
                if (existing.version().equals(version)) {
                    throw new IllegalArgumentException(
                        "Model version already registered: " + modelId + ":" + version);
                }
            }

            list.add(entry);
            return list;
        });
    }

    /**
     * Promote a model version if responsible AI receipt proof is provided.
     *
     * @param modelId non-null model identifier
     * @param version non-null semantic version
     * @param responsibleAiReceiptJson non-null, non-blank JSON proof of responsible AI review
     * @return promoted entry with status PROMOTED
     * @throws NoSuchElementException if modelId:version not found
     * @throws IllegalStateException if responsibleAiReceiptJson is null or blank
     */
    public ModelRegistryEntry promote(String modelId, String version, String responsibleAiReceiptJson) {
        if (responsibleAiReceiptJson == null || responsibleAiReceiptJson.isBlank()) {
            throw new IllegalStateException(
                "Model promotion requires ResponsibleAiReceipt proof. " +
                "Provide non-blank responsibleAiReceiptJson containing evaluation evidence.");
        }

        List<ModelRegistryEntry> entries = registry.get(modelId);
        if (entries == null || entries.isEmpty()) {
            throw new NoSuchElementException("Model not found: " + modelId);
        }

        ModelRegistryEntry toPromote = null;
        for (ModelRegistryEntry e : entries) {
            if (e.version().equals(version)) {
                toPromote = e;
                break;
            }
        }

        if (toPromote == null) {
            throw new NoSuchElementException("Model version not found: " + modelId + ":" + version);
        }

        // Add receipt as evidence
        List<String> evidence = new ArrayList<>(toPromote.responsibleAiEvidence());
        evidence.add(responsibleAiReceiptJson);

        // Create promoted entry
        ModelRegistryEntry promoted = new ModelRegistryEntry(
            modelId,
            version,
            toPromote.datasetLineage(),
            toPromote.modelCard(),
            toPromote.evalSuiteRef(),
            toPromote.versionHash(),
            evidence,
            ModelRegistryEntry.PromotionStatus.PROMOTED,
            toPromote.registeredAt()
        );

        // Replace in registry
        int idx = entries.indexOf(toPromote);
        List<ModelRegistryEntry> updated = new ArrayList<>(entries);
        updated.set(idx, promoted);
        registry.put(modelId, updated);

        return promoted;
    }

    /**
     * Rollback the latest promoted version, reverting the prior version to CANDIDATE.
     *
     * @param modelId non-null model identifier
     * @return prior version reverted to CANDIDATE status
     * @throws NoSuchElementException if no promoted version or no prior version exists
     */
    public ModelRegistryEntry rollback(String modelId) {
        List<ModelRegistryEntry> entries = registry.get(modelId);
        if (entries == null || entries.isEmpty()) {
            throw new NoSuchElementException("Model not found: " + modelId);
        }

        // Find the LATEST promoted version (search from the end so multi-promote scenarios
        // roll back v2 before v1, not v1 which has no predecessor).
        ModelRegistryEntry promoted = null;
        int promotedIdx = -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).status() == ModelRegistryEntry.PromotionStatus.PROMOTED) {
                promoted = entries.get(i);
                promotedIdx = i;
                break;
            }
        }

        if (promoted == null) {
            throw new NoSuchElementException("No promoted version found for model: " + modelId);
        }

        // Find the immediate predecessor (any status — it may have been promoted before
        // being superseded, so we cannot require it to be CANDIDATE at this point).
        if (promotedIdx == 0) {
            throw new NoSuchElementException(
                "No prior version found for rollback: " + modelId);
        }
        ModelRegistryEntry prior = entries.get(promotedIdx - 1);

        List<ModelRegistryEntry> updated = new ArrayList<>(entries);

        // Mark the promoted version as rolled back
        updated.set(promotedIdx, new ModelRegistryEntry(
            promoted.modelId(),
            promoted.version(),
            promoted.datasetLineage(),
            promoted.modelCard(),
            promoted.evalSuiteRef(),
            promoted.versionHash(),
            promoted.responsibleAiEvidence(),
            ModelRegistryEntry.PromotionStatus.ROLLED_BACK,
            promoted.registeredAt()
        ));

        // Revert the prior version to CANDIDATE status
        ModelRegistryEntry candidate = new ModelRegistryEntry(
            prior.modelId(),
            prior.version(),
            prior.datasetLineage(),
            prior.modelCard(),
            prior.evalSuiteRef(),
            prior.versionHash(),
            prior.responsibleAiEvidence(),
            ModelRegistryEntry.PromotionStatus.CANDIDATE,
            prior.registeredAt()
        );
        updated.set(promotedIdx - 1, candidate);

        registry.put(modelId, updated);
        return candidate;
    }

    /**
     * Get current entry for a model (highest promoted, else highest candidate).
     *
     * @param modelId non-null model identifier
     * @return optional containing the current entry
     */
    public Optional<ModelRegistryEntry> getEntry(String modelId) {
        List<ModelRegistryEntry> entries = registry.get(modelId);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        // Search for PROMOTED (highest priority)
        for (ModelRegistryEntry e : entries) {
            if (e.status() == ModelRegistryEntry.PromotionStatus.PROMOTED) {
                return Optional.of(e);
            }
        }

        // Fall back to highest CANDIDATE
        for (int i = entries.size() - 1; i >= 0; i--) {
            ModelRegistryEntry e = entries.get(i);
            if (e.status() == ModelRegistryEntry.PromotionStatus.CANDIDATE) {
                return Optional.of(e);
            }
        }

        return Optional.empty();
    }

    /**
     * Get all versions of a model ordered by registeredAt descending.
     *
     * @param modelId non-null model identifier
     * @return list of all versions (newest first), empty if modelId not found
     */
    public List<ModelRegistryEntry> getHistory(String modelId) {
        List<ModelRegistryEntry> entries = registry.get(modelId);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<ModelRegistryEntry> sorted = new ArrayList<>(entries);
        sorted.sort((a, b) -> b.registeredAt().compareTo(a.registeredAt()));
        return List.copyOf(sorted);
    }

    /**
     * Serialize all entries to JSON string.
     *
     * @return JSON representation of the entire registry
     */
    public String toJson() {
        try {
            Map<String, Object> output = new LinkedHashMap<>();
            for (String modelId : registry.keySet()) {
                List<ModelRegistryEntry> entries = registry.get(modelId);
                if (entries != null) {
                    List<Map<String, Object>> entryMaps = new ArrayList<>();
                    for (ModelRegistryEntry entry : entries) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("modelId", entry.modelId());
                        map.put("version", entry.version());
                        map.put("datasetLineage", entry.datasetLineage());
                        map.put("modelCard", entry.modelCard());
                        map.put("evalSuiteRef", entry.evalSuiteRef());
                        map.put("versionHash", entry.versionHash());
                        map.put("responsibleAiEvidence", entry.responsibleAiEvidence());
                        map.put("status", entry.status().name());
                        map.put("registeredAt", entry.registeredAt().toString());
                        entryMaps.add(map);
                    }
                    output.put(modelId, entryMaps);
                }
            }
            return mapper.writeValueAsString(output);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize model registry to JSON", e);
        }
    }
}
