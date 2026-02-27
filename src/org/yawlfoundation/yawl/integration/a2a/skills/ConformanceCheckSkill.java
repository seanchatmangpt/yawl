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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.ggen.polyglot.PowlJsonMarshaller;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintMatrix;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintScorer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A2A skill that checks behavioral conformance between POWL workflow models.
 * No LLM required — pure Jaccard-based footprint comparison.
 *
 * <h2>Two operation modes</h2>
 * <ol>
 *   <li><b>Extract</b>: supply {@code powlModelJson} to extract a footprint matrix.</li>
 *   <li><b>Compare</b>: supply {@code referenceModelJson} + {@code candidateModelJson}
 *       to get a conformance score in [0.0, 1.0].</li>
 * </ol>
 *
 * <h2>Conformance interpretation</h2>
 * <ul>
 *   <li>&ge; 0.8 → HIGH conformance</li>
 *   <li>0.5 – 0.8 → MEDIUM conformance</li>
 *   <li>&lt; 0.5 → LOW conformance</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public class ConformanceCheckSkill implements A2ASkill {

    private static final Logger _log = LoggerFactory.getLogger(ConformanceCheckSkill.class);
    private final FootprintExtractor extractor;

    public ConformanceCheckSkill() {
        this.extractor = new FootprintExtractor();
    }

    @Override
    public String getId() {
        return "conformance_check";
    }

    @Override
    public String getName() {
        return "Behavioral Conformance Check";
    }

    @Override
    public String getDescription() {
        return "Checks behavioral conformance between POWL workflow models using footprint "
            + "analysis and Jaccard similarity. Supports footprint extraction (single model) "
            + "and conformance comparison (reference vs candidate). No LLM required.";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("workflow:analyze");
    }

    @Override
    public List<String> getTags() {
        return List.of("conformance", "footprint", "no-llm", "behavioral", "jaccard");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String powlModelJson = request.getParameter("powlModelJson");
        String refJson = request.getParameter("referenceModelJson");
        String candJson = request.getParameter("candidateModelJson");

        // Determine mode: extract or compare
        if (powlModelJson != null && !powlModelJson.isBlank()) {
            return executeExtract(powlModelJson);
        }
        if (refJson != null && !refJson.isBlank() && candJson != null && !candJson.isBlank()) {
            return executeCompare(refJson, candJson);
        }

        return SkillResult.error(
            "Required parameter missing. Provide either: "
            + "(1) 'powlModelJson' for footprint extraction, or "
            + "(2) 'referenceModelJson' and 'candidateModelJson' for conformance comparison.");
    }

    // =========================================================================
    // Extract mode
    // =========================================================================

    private SkillResult executeExtract(String powlJson) {
        long start = System.currentTimeMillis();

        PowlModel model;
        try {
            model = PowlJsonMarshaller.fromJson(powlJson, "skill-extract-model");
        } catch (PowlParseException e) {
            return SkillResult.error("Failed to parse powlModelJson: " + e.getMessage());
        }

        FootprintMatrix matrix = extractor.extract(model);
        long elapsed = System.currentTimeMillis() - start;

        _log.info("ConformanceCheckSkill: extract mode, ds={}, conc={}, excl={}, elapsed={}ms",
            matrix.directSuccession().size(), matrix.concurrency().size(),
            matrix.exclusive().size(), elapsed);

        Map<String, Object> data = new HashMap<>();
        data.put("mode", "extract");
        data.put("directSuccession", matrix.directSuccession().stream()
            .map(p -> p.get(0) + " → " + p.get(1))
            .sorted()
            .toList());
        data.put("concurrency", matrix.concurrency().stream()
            .map(p -> p.get(0) + " ‖ " + p.get(1))
            .sorted()
            .toList());
        data.put("exclusive", matrix.exclusive().stream()
            .map(p -> p.get(0) + " ⊕ " + p.get(1))
            .sorted()
            .toList());
        data.put("directSuccessionCount", matrix.directSuccession().size());
        data.put("concurrencyCount", matrix.concurrency().size());
        data.put("exclusiveCount", matrix.exclusive().size());
        data.put("activityCount", countActivities(matrix));
        data.put("elapsed_ms", elapsed);
        return SkillResult.success(data, elapsed);
    }

    // =========================================================================
    // Compare mode
    // =========================================================================

    private SkillResult executeCompare(String refJson, String candJson) {
        long start = System.currentTimeMillis();

        PowlModel refModel;
        try {
            refModel = PowlJsonMarshaller.fromJson(refJson, "skill-ref-model");
        } catch (PowlParseException e) {
            return SkillResult.error("Failed to parse referenceModelJson: " + e.getMessage());
        }

        PowlModel candModel;
        try {
            candModel = PowlJsonMarshaller.fromJson(candJson, "skill-cand-model");
        } catch (PowlParseException e) {
            return SkillResult.error("Failed to parse candidateModelJson: " + e.getMessage());
        }

        FootprintMatrix refMatrix = extractor.extract(refModel);
        FootprintScorer scorer = new FootprintScorer(refMatrix);
        double score = scorer.score(candModel, "");

        FootprintMatrix candMatrix = extractor.extract(candModel);
        double dsSim = jaccardSimilarity(candMatrix.directSuccession(), refMatrix.directSuccession());
        double concSim = jaccardSimilarity(candMatrix.concurrency(), refMatrix.concurrency());
        double exclSim = jaccardSimilarity(candMatrix.exclusive(), refMatrix.exclusive());

        long elapsed = System.currentTimeMillis() - start;

        _log.info("ConformanceCheckSkill: compare mode, score={:.4f}, elapsed={}ms", score, elapsed);

        Map<String, Object> data = new HashMap<>();
        data.put("mode", "compare");
        data.put("score", score);
        data.put("directSuccessionJaccard", dsSim);
        data.put("concurrencyJaccard", concSim);
        data.put("exclusiveJaccard", exclSim);
        data.put("interpretation", interpretScore(score));
        data.put("elapsed_ms", elapsed);
        return SkillResult.success(data, elapsed);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String interpretScore(double score) {
        if (score >= 0.8) return "HIGH";
        if (score >= 0.5) return "MEDIUM";
        return "LOW";
    }

    private int countActivities(FootprintMatrix matrix) {
        Set<String> activities = new HashSet<>();
        matrix.directSuccession().forEach(p -> { activities.add(p.get(0)); activities.add(p.get(1)); });
        matrix.concurrency().forEach(p -> { activities.add(p.get(0)); activities.add(p.get(1)); });
        matrix.exclusive().forEach(p -> { activities.add(p.get(0)); activities.add(p.get(1)); });
        return activities.size();
    }

    private double jaccardSimilarity(Set<List<String>> a, Set<List<String>> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<List<String>> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<List<String>> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
