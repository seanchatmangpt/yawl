/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.memory;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.rl.CandidateSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenSage-inspired hierarchical process memory backed by a JUNG directed graph.
 *
 * <p>Each GRPO optimization round produces a {@link CandidateSet}. Calling
 * {@link #remember(CandidateSet)} upserts the high-reward candidates as {@link PatternNode}
 * vertices and draws a "follows" edge from the previous top candidate to the current top —
 * encoding sequential learning across rounds.
 *
 * <p>Calling {@link #biasHint(String, int)} returns the top-k patterns by average reward,
 * formatted as a prompt fragment for {@code OllamaCandidateSampler}. This implements
 * OpenSage's "long-term memory recall" — the sampler naturally avoids regenerating
 * patterns that were already rewarded, biasing toward novel discovery.
 *
 * <h2>Thread safety</h2>
 * <p>All mutating operations are synchronized on the graph instance. This is intentional:
 * the graph is written only during GRPO rounds (at most K=4 writes per session) — contention
 * is negligible. Reads via {@link #biasHint} are also synchronized for consistency.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>The graph is in-process and session-scoped. It is not persisted across JVM restarts.
 * For cross-session persistence, a future extension can serialize the node/edge map to H2
 * or export to the YAWL observatory fact files.</p>
 */
public class ProcessKnowledgeGraph {

    /** Reward threshold: only patterns with reward ≥ this are remembered. */
    private static final double REWARD_THRESHOLD = 0.5;

    private final DirectedSparseGraph<PatternNode, String> graph;
    private final Map<String, PatternNode> byFingerprint;   // fingerprint → current node
    private final AtomicInteger edgeCounter;
    private String lastTopFingerprint;   // tracks cross-round FOLLOWS edges

    /** Creates a fresh, empty knowledge graph. */
    public ProcessKnowledgeGraph() {
        this.graph = new DirectedSparseGraph<>();
        this.byFingerprint = new HashMap<>();
        this.edgeCounter = new AtomicInteger(0);
        this.lastTopFingerprint = null;
    }

    // ─── public API ───────────────────────────────────────────────────────────

    /**
     * Upserts the high-reward candidates from a GRPO evaluation round into the graph.
     *
     * <p>For each candidate whose reward exceeds {@value REWARD_THRESHOLD}, the pattern
     * node is upserted (created or reward-accumulated). The highest-reward candidate of
     * this round gains a "FOLLOWS" edge from the previous round's top candidate.</p>
     *
     * @param candidateSet the evaluated GRPO candidates; must not be null
     */
    public synchronized void remember(CandidateSet candidateSet) {
        if (candidateSet == null) return;

        String currentTopFingerprint = null;
        double currentTopReward = -1.0;

        for (int i = 0; i < candidateSet.candidates().size(); i++) {
            PowlModel model = candidateSet.candidates().get(i);
            double reward = candidateSet.rewards().get(i);
            if (reward >= REWARD_THRESHOLD) {
                String fp = fingerprint(model);
                upsert(fp, reward);
                if (reward > currentTopReward) {
                    currentTopReward = reward;
                    currentTopFingerprint = fp;
                }
            }
        }

        // Draw FOLLOWS edge from last round's top to this round's top
        if (lastTopFingerprint != null && currentTopFingerprint != null
                && !lastTopFingerprint.equals(currentTopFingerprint)) {
            PatternNode from = byFingerprint.get(lastTopFingerprint);
            PatternNode to = byFingerprint.get(currentTopFingerprint);
            if (from != null && to != null) {
                String edgeId = "follows_" + edgeCounter.getAndIncrement();
                if (!graph.containsEdge(edgeId)) {
                    graph.addEdge(edgeId, from, to);
                }
            }
        }

        if (currentTopFingerprint != null) {
            lastTopFingerprint = currentTopFingerprint;
        }
    }

    /**
     * Returns a prompt bias hint listing the top-k known successful process patterns.
     *
     * <p>The hint is appended to the LLM generation prompt so the sampler naturally
     * explores novel patterns rather than rediscovering already-known ones.</p>
     *
     * @param description  the current process description (unused for now; reserved for
     *                     future semantic similarity filtering)
     * @param k            maximum number of patterns to include
     * @return a non-null, possibly-empty multi-line string; empty if graph has no patterns
     */
    public synchronized String biasHint(String description, int k) {
        List<PatternNode> nodes = new ArrayList<>(byFingerprint.values());
        nodes.sort((a, b) -> Double.compare(b.averageReward(), a.averageReward()));

        int limit = Math.min(k, nodes.size());
        // When limit == 0 the loop does not execute and sb.toString() returns the empty builder
        // result — semantically correct: no bias hint when no patterns are known.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i == 0) {
                sb.append("Previously successful process patterns (avoid exact duplication):\n");
            }
            PatternNode n = nodes.get(i);
            sb.append(i + 1).append(". ").append(n.fingerprint())
              .append(" (avg reward: ").append(String.format("%.2f", n.averageReward())).append(")\n");
        }
        return sb.toString().stripTrailing();
    }

    /** Returns the number of unique process patterns in the graph. */
    public synchronized int size() {
        return byFingerprint.size();
    }

    /**
     * Computes the structural fingerprint of a POWL model.
     *
     * <p>The fingerprint is the sorted list of all activity labels in the model tree,
     * joined by " → ". It is order-independent and stable across re-generations of
     * structurally equivalent models with different IDs.</p>
     *
     * @param model the POWL model to fingerprint; must not be null
     * @return a non-blank fingerprint string
     */
    public static String fingerprint(PowlModel model) {
        List<String> labels = new ArrayList<>();
        collectLabels(model.root(), labels);
        Collections.sort(labels);
        return String.join(" → ", labels);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private void upsert(String fp, double reward) {
        PatternNode existing = byFingerprint.get(fp);
        if (existing == null) {
            PatternNode fresh = PatternNode.of(fp).withReward(reward);
            byFingerprint.put(fp, fresh);
            graph.addVertex(fresh);
        } else {
            PatternNode updated = existing.withReward(reward);
            byFingerprint.put(fp, updated);
            // Replace vertex in graph (JUNG doesn't support in-place update)
            graph.removeVertex(existing);
            graph.addVertex(updated);
        }
    }

    private static void collectLabels(PowlNode node, List<String> labels) {
        switch (node) {
            case PowlActivity a -> labels.add(a.label());
            case PowlOperatorNode op -> op.children().forEach(c -> collectLabels(c, labels));
        }
    }
}
