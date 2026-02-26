/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OpenSage-inspired horizontal-ensemble discovery board for the K parallel samplers
 * inside {@link OllamaCandidateSampler}.
 *
 * <p>When a virtual-thread sampler successfully parses a {@link PowlModel}, it calls
 * {@link #publish(PowlModel)}. Any subsequent sampler that has not yet received its
 * LLM response can call {@link #topK(int)} to retrieve the leading patterns and append
 * them to its generation prompt — steering diversity by signalling "these patterns are
 * already found; explore something different."
 *
 * <p>The board is scoped to a single {@code OllamaCandidateSampler.sample()} call:
 * a fresh {@code DiscoveryBoard} is created at the start of each round and discarded
 * after all K candidates have been collected. This keeps the ensemble effect local to
 * one GRPO round with no cross-round contamination (that is the job of
 * {@link ProcessKnowledgeGraph}).
 *
 * <h2>Thread safety</h2>
 * <p>{@code ConcurrentHashMap} guards duplicate fingerprints. {@code CopyOnWriteArrayList}
 * provides safe concurrent iteration of arrival order. Both operations ({@link #publish}
 * and {@link #topK}) are wait-free from the callers' perspective.</p>
 */
public final class DiscoveryBoard {

    /** Fingerprint → first PowlModel to claim that fingerprint. */
    private final ConcurrentHashMap<String, PowlModel> fingerprints = new ConcurrentHashMap<>();

    /** Arrival-ordered list of unique models for deterministic topK ordering. */
    private final CopyOnWriteArrayList<PowlModel> arrived = new CopyOnWriteArrayList<>();

    /**
     * Publishes a newly-parsed candidate to the board.
     *
     * <p>If another candidate with the same structural fingerprint was already published,
     * this call is a no-op (first writer wins — {@code putIfAbsent} semantics).</p>
     *
     * @param model the parsed candidate; must not be null
     */
    public void publish(PowlModel model) {
        String fp = ProcessKnowledgeGraph.fingerprint(model);
        if (fingerprints.putIfAbsent(fp, model) == null) {
            // Only the first thread to claim this fingerprint adds to the ordered list
            arrived.add(model);
        }
    }

    /**
     * Returns the first {@code k} unique models published so far, in arrival order.
     *
     * <p>Called by late-arriving sampler threads to augment their generation prompt with
     * patterns already discovered by peer threads in the same round.</p>
     *
     * @param k maximum number of models to return (actual count may be less if fewer arrived)
     * @return unmodifiable list of at most {@code k} models; never null
     */
    public List<PowlModel> topK(int k) {
        List<PowlModel> snapshot = new ArrayList<>(arrived);
        return List.copyOf(snapshot.subList(0, Math.min(k, snapshot.size())));
    }

    /** Returns {@code true} if no models have been published yet. */
    public boolean isEmpty() {
        return arrived.isEmpty();
    }

    /** Returns the number of unique models published so far. */
    public int size() {
        return arrived.size();
    }
}
