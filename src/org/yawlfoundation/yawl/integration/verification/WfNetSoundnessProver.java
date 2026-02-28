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

package org.yawlfoundation.yawl.integration.verification;

import java.time.Instant;
import java.util.*;

/**
 * Proves soundness of a Workflow Net using van der Aalst's theorem (1997).
 *
 * <p><strong>Van der Aalst's Soundness Theorem:</strong>
 * <em>A WF-net N is sound iff the short-circuited net N* (with arc from sink back to
 * source) is both live and bounded.</em>
 *
 * <p>Implementation:
 * <ol>
 *   <li>Validate N has WF-net structure (exactly one source, one sink, all nodes reachable)</li>
 *   <li>Construct N*: add τ transition from sink → source</li>
 *   <li>Check if N* is live (all transitions can fire from some reachable marking)</li>
 *   <li>Check if N* is bounded (finite token count in all places)</li>
 *   <li>Return proof: N is sound iff N* is both live AND bounded</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * var prover = new WfNetSoundnessProver();
 * var proof = prover.prove(placeToTransitions, transitionToPlaces,
 *     "source", "sink");
 * System.out.println("Sound: " + proof.isSound());
 * System.out.println(proof.theorem());
 * proof.evidence().forEach(System.out::println);
 * </pre>
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
public final class WfNetSoundnessProver {

    /**
     * Proof of soundness or unsoundness of a WF-net.
     *
     * @param isSound true iff the WF-net is sound
     * @param structureResult structural validation result
     * @param shortCircuitIsLive true iff N* is live
     * @param shortCircuitIsBounded true iff N* is bounded
     * @param theorem the formal theorem applied
     * @param evidence collected evidence from checks
     * @param provedAt timestamp when proof was generated
     */
    public record SoundnessProof(
        boolean isSound,
        WfNetStructureValidator.WfNetStructureResult structureResult,
        boolean shortCircuitIsLive,
        boolean shortCircuitIsBounded,
        String theorem,
        List<String> evidence,
        Instant provedAt
    ) {}

    private final WfNetStructureValidator structureValidator = new WfNetStructureValidator();
    private final LivenessChecker livenessChecker = new LivenessChecker();
    private final BoundednessChecker boundednessChecker = new BoundednessChecker();

    /**
     * Proves soundness of a WF-net according to van der Aalst (1997).
     *
     * @param placeToTransitions map of place ID to output transition IDs
     * @param transitionToPlaces map of transition ID to output place IDs
     * @param sourcePlaceId ID of the source place (initial marking)
     * @param sinkPlaceId ID of the sink place (final marking)
     * @return SoundnessProof with soundness status and evidence
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if source or sink is empty
     */
    public SoundnessProof prove(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        String sourcePlaceId,
        String sinkPlaceId
    ) {
        Objects.requireNonNull(placeToTransitions, "placeToTransitions must not be null");
        Objects.requireNonNull(transitionToPlaces, "transitionToPlaces must not be null");
        Objects.requireNonNull(sourcePlaceId, "sourcePlaceId must not be null");
        Objects.requireNonNull(sinkPlaceId, "sinkPlaceId must not be null");

        if (sourcePlaceId.isBlank()) {
            throw new IllegalArgumentException("sourcePlaceId must not be empty");
        }
        if (sinkPlaceId.isBlank()) {
            throw new IllegalArgumentException("sinkPlaceId must not be empty");
        }

        Instant startTime = Instant.now();
        List<String> evidence = new ArrayList<>();

        evidence.add("Proving WF-net soundness via van der Aalst (1997) theorem");
        evidence.add("Source place: " + sourcePlaceId);
        evidence.add("Sink place: " + sinkPlaceId);

        // Step 1: Validate WF-net structure
        evidence.add("\n[Step 1] Validating WF-net structure...");
        var structureResult = structureValidator.validate(placeToTransitions, transitionToPlaces);

        if (!structureResult.isWfNet()) {
            evidence.add("Structure validation FAILED:");
            structureResult.violations().forEach(v -> evidence.add("  - " + v));
            return new SoundnessProof(
                false,
                structureResult,
                false,
                false,
                "VAN_DER_AALST_1997: Structure check failed",
                Collections.unmodifiableList(evidence),
                Instant.now()
            );
        }

        evidence.add("Structure validation OK");
        evidence.add("  - Source place: " + sourcePlaceId);
        evidence.add("  - Sink place: " + sinkPlaceId);
        evidence.add("  - All nodes on source→sink path");

        // Step 2: Build short-circuited net N*
        evidence.add("\n[Step 2] Building short-circuited net N* (add τ: sink → source)...");
        String tauTransitionId = "_tau_short_circuit_";
        var nStar = buildShortCircuitNet(
            placeToTransitions,
            transitionToPlaces,
            sourcePlaceId,
            sinkPlaceId,
            tauTransitionId
        );
        evidence.add("N* built with τ transition: " + tauTransitionId);

        // Step 3: Check liveness of N*
        evidence.add("\n[Step 3] Checking liveness of N*...");
        var livenessResult = livenessChecker.check(
            nStar.placeToTransitions(),
            nStar.transitionToPlaces(),
            sourcePlaceId,
            10000  // Reasonable default for WF-nets
        );

        boolean isLive = livenessResult.isLive();
        evidence.add("Liveness check: " + (isLive ? "PASS" : "FAIL"));
        if (!isLive) {
            evidence.add("  Dead transitions: " + livenessResult.deadTransitions());
        } else {
            evidence.add("  All transitions can fire from reachable markings");
        }
        evidence.add("  Markings explored: " + livenessResult.reachableMarkingsChecked());

        // Step 4: Check boundedness of N*
        evidence.add("\n[Step 4] Checking boundedness of N*...");
        var boundednessResult = boundednessChecker.check(
            nStar.placeToTransitions(),
            nStar.transitionToPlaces(),
            sourcePlaceId,
            100,    // Allow up to 100 tokens per place before declaring unbounded
            10000   // Reasonable default for WF-nets
        );

        boolean isBounded = boundednessResult.isBounded();
        evidence.add("Boundedness check: " + (isBounded ? "PASS" : "FAIL"));
        if (!isBounded) {
            evidence.add("  Unbounded places: " + boundednessResult.unboundedPlaces());
        } else {
            evidence.add("  Maximum tokens per place: " + boundednessResult.boundK());
            evidence.add("  Safe: " + boundednessResult.isSafe());
        }

        // Step 5: Apply van der Aalst's theorem
        evidence.add("\n[Step 5] Applying van der Aalst's theorem...");
        boolean isSound = isLive && isBounded;
        String theorem = "VAN_DER_AALST_1997: N is sound iff (N* is live) AND (N* is bounded)";

        if (isSound) {
            evidence.add("SOUNDNESS PROOF COMPLETE: N is SOUND");
        } else {
            evidence.add("SOUNDNESS PROOF COMPLETE: N is UNSOUND");
            if (!isLive) {
                evidence.add("  - N* is NOT live (dead transitions exist)");
            }
            if (!isBounded) {
                evidence.add("  - N* is NOT bounded (unbounded places exist)");
            }
        }

        return new SoundnessProof(
            isSound,
            structureResult,
            isLive,
            isBounded,
            theorem,
            Collections.unmodifiableList(evidence),
            Instant.now()
        );
    }

    /**
     * Builds the short-circuited net N* by adding an arc from sink to source.
     *
     * @param placeToTransitions original place → transitions map
     * @param transitionToPlaces original transition → places map
     * @param sourcePlaceId source place ID
     * @param sinkPlaceId sink place ID
     * @param tauId ID for the short-circuit transition
     * @return ShortCircuitNet with updated maps
     */
    private ShortCircuitNet buildShortCircuitNet(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        String sourcePlaceId,
        String sinkPlaceId,
        String tauId
    ) {
        // Copy original maps
        Map<String, Set<String>> newPlaceToTransitions = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : placeToTransitions.entrySet()) {
            newPlaceToTransitions.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        Map<String, Set<String>> newTransitionToPlaces = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : transitionToPlaces.entrySet()) {
            newTransitionToPlaces.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        // Add τ transition: sinkPlaceId → tauId → sourcePlaceId
        // This means: sinkPlaceId outputs to tauId, and tauId outputs to sourcePlaceId
        newPlaceToTransitions.computeIfAbsent(sinkPlaceId, _ -> new HashSet<>()).add(tauId);
        newTransitionToPlaces.put(tauId, new HashSet<>(Set.of(sourcePlaceId)));

        return new ShortCircuitNet(newPlaceToTransitions, newTransitionToPlaces);
    }

    /**
     * Records the short-circuited net structure.
     */
    private record ShortCircuitNet(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces
    ) {}
}
