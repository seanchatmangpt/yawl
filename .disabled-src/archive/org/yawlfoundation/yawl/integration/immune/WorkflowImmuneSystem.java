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

package org.yawlfoundation.yawl.integration.immune;

import org.jdom2.Document;
import org.yawlfoundation.yawl.engine.ObserverGateway;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.integration.verification.SoundnessVerifier;
import org.yawlfoundation.yawl.integration.verification.VerificationReport;
import org.yawlfoundation.yawl.integration.verification.VerificationFinding;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Workflow immune system for YAWL v6.0 that predicts and reports deadlock-prone
 * states after task executions.
 *
 * <p>This system:
 * <ul>
 *   <li>Implements ObserverGateway to receive task firing announcements</li>
 *   <li>Analyzes the workflow net structure using SoundnessVerifier</li>
 *   <li>Predicts deadlock patterns and emits DeadlockPrediction records</li>
 *   <li>Maintains a cumulative report of all predictions</li>
 *   <li>Allows configuration of lookahead depth and ignored patterns</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> The system uses CopyOnWriteArrayList for concurrent access
 * to the predictions list. Listeners may be called from any thread.
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * var config = ImmuneSystemConfig.defaults();
 * var immuneSystem = new WorkflowImmuneSystem(
 *     config,
 *     prediction -> System.out.println("Deadlock detected: " + prediction.findingType())
 * );
 *
 * // After a task fires, analyze the net:
 * var predictions = immuneSystem.predict(
 *     "case-123", "task-id",
 *     placeToTransitions, transitionToPlaces,
 *     "p_start", "p_end"
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkflowImmuneSystem implements ObserverGateway {
    private static final Logger LOGGER = Logger.getLogger(WorkflowImmuneSystem.class.getName());

    private final ImmuneSystemConfig config;
    private final Consumer<DeadlockPrediction> predictionListener;
    private final List<DeadlockPrediction> allPredictions = new CopyOnWriteArrayList<>();
    private final AtomicInteger casesScanned = new AtomicInteger(0);

    /**
     * Creates a WorkflowImmuneSystem with the given configuration and listener.
     *
     * @param config               configuration for the system behavior (non-null)
     * @param predictionListener   callback invoked when a deadlock is predicted (non-null)
     * @throws NullPointerException if config or predictionListener is null
     */
    public WorkflowImmuneSystem(ImmuneSystemConfig config, Consumer<DeadlockPrediction> predictionListener) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.predictionListener = Objects.requireNonNull(predictionListener, "prediction listener must not be null");
    }

    /**
     * Analyzes the net structure after a task firing and predicts deadlock patterns.
     *
     * <p>If SoundnessVerifier finds ERROR-severity findings that are not in
     * ignoredPatterns, a DeadlockPrediction is created and emitted to the listener.
     *
     * @param caseId                the case identifier (non-null, non-empty)
     * @param firedTaskId           the task that just fired (non-null, non-empty)
     * @param placeToTransitions    map of place IDs to their output transition IDs
     * @param transitionToPlaces    map of transition IDs to their output place IDs
     * @param startPlace            the start place ID
     * @param endPlace              the end place ID
     * @return list of DeadlockPrediction records emitted (empty if net is sound)
     * @throws NullPointerException if any required parameter is null
     * @throws IllegalArgumentException if caseId or firedTaskId is empty,
     *                                  or if start/end places are not in the maps
     */
    public List<DeadlockPrediction> predict(
        String caseId,
        String firedTaskId,
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        String startPlace,
        String endPlace
    ) {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(firedTaskId, "firedTaskId must not be null");
        Objects.requireNonNull(placeToTransitions, "placeToTransitions must not be null");
        Objects.requireNonNull(transitionToPlaces, "transitionToPlaces must not be null");
        Objects.requireNonNull(startPlace, "startPlace must not be null");
        Objects.requireNonNull(endPlace, "endPlace must not be null");

        if (caseId.isBlank()) {
            throw new IllegalArgumentException("caseId must not be empty");
        }
        if (firedTaskId.isBlank()) {
            throw new IllegalArgumentException("firedTaskId must not be empty");
        }

        List<DeadlockPrediction> emittedPredictions = new ArrayList<>();

        try {
            // Run soundness verification on the net
            var verifier = new SoundnessVerifier(placeToTransitions, transitionToPlaces, startPlace, endPlace);
            VerificationReport report = verifier.verify();

            // Collect ERROR-level findings that are not ignored
            for (VerificationFinding finding : report.findings()) {
                if (finding.severity() == VerificationFinding.Severity.ERROR &&
                    !config.ignoredPatterns().contains(finding.pattern().displayName())) {
                    
                    // Create a prediction with high confidence for ERROR findings
                    var prediction = new DeadlockPrediction(
                        caseId,
                        firedTaskId,
                        finding.pattern().displayName(),
                        extractAffectedElements(finding),
                        1.0,  // ERROR findings have high confidence
                        Instant.now()
                    );

                    allPredictions.add(prediction);
                    emittedPredictions.add(prediction);
                    predictionListener.accept(prediction);

                    LOGGER.info("Deadlock predicted for case %s: %s at task %s"
                        .formatted(caseId, finding.pattern().displayName(), firedTaskId));
                }
            }

            casesScanned.incrementAndGet();

        } catch (Exception e) {
            LOGGER.warning("Error during soundness verification for case %s: %s"
                .formatted(caseId, e.getMessage()));
            // Return empty list on verification failure
        }

        return emittedPredictions;
    }

    /**
     * Returns the cumulative report of all predictions emitted by this system.
     *
     * @return ImmuneReport with all predictions and statistics
     */
    public ImmuneReport getReport() {
        return new ImmuneReport(
            new ArrayList<>(allPredictions),
            casesScanned.get(),
            (int) allPredictions.size()
        );
    }

    /**
     * Returns the MCP gateway scheme identifier.
     *
     * @return the scheme "yawl-immune"
     */
    @Override
    public String getScheme() {
        return "yawl-immune";
    }

    /**
     * Called by the engine when a new workitem gets enabled.
     * Logs the event but does not perform analysis (net structure not available in announcement).
     *
     * @param announcement the work item descriptors
     */
    @Override
    public void announceFiredWorkItem(YAnnouncement announcement) {
        if (announcement != null) {
            LOGGER.fine("Workitem fired: " + announcement.getItem().getIDString());
        }
    }

    /**
     * Called by the engine when a previously posted workitem has been cancelled.
     * No action needed by the immune system.
     *
     * @param announcement the work item descriptors
     */
    @Override
    public void announceCancelledWorkItem(YAnnouncement announcement) {
        // No-op for immune system
    }

    /**
     * Called by the engine when a timer for a workitem expires.
     * No action needed by the immune system.
     *
     * @param announcement the work item descriptors
     */
    @Override
    public void announceTimerExpiry(YAnnouncement announcement) {
        // No-op for immune system
    }

    /**
     * Called by engine when a case is complete and a completion observer was
     * specified at case launch.
     * No action needed by the immune system.
     *
     * @param yawlService the yawl service nominated as the completion observer
     * @param caseID      the case that completed
     * @param caseData    the output data of the case
     */
    @Override
    public void announceCaseCompletion(YAWLServiceReference yawlService, YIdentifier caseID, Document caseData) {
        // No-op for immune system
    }

    /**
     * Called by engine to announce when a case has commenced.
     * No action needed by the immune system.
     *
     * @param services        the set of registered custom services
     * @param specID          the specification id of the started case
     * @param caseID          the case that has started
     * @param launchingService the service that started the case
     * @param delayed         true if this is a delayed case launch, false if immediate
     */
    @Override
    public void announceCaseStarted(Set<YAWLServiceReference> services, YSpecificationID specID, YIdentifier caseID,
                                    String launchingService, boolean delayed) {
        // No-op for immune system
    }

    /**
     * Called by engine to announce when a case is complete and a completion
     * observer was not specified at case launch.
     * No action needed by the immune system.
     *
     * @param services the set of services currently registered with the engine
     * @param caseID   the case that completed
     * @param caseData the output data of the case
     */
    @Override
    public void announceCaseCompletion(Set<YAWLServiceReference> services, YIdentifier caseID, Document caseData) {
        // No-op for immune system
    }

    /**
     * Called by the engine to announce when a case suspends.
     * No action needed by the immune system.
     *
     * @param services the set of services currently registered with the engine
     * @param caseID   the identifier of the suspended case
     */
    @Override
    public void announceCaseSuspended(Set<YAWLServiceReference> services, YIdentifier caseID) {
        // No-op for immune system
    }

    /**
     * Called by the engine to announce when a case starts to suspend.
     * No action needed by the immune system.
     *
     * @param services the set of services currently registered with the engine
     * @param caseID   the identifier of the suspending case
     */
    @Override
    public void announceCaseSuspending(Set<YAWLServiceReference> services, YIdentifier caseID) {
        // No-op for immune system
    }

    /**
     * Called by the engine to announce when a case resumes.
     * No action needed by the immune system.
     *
     * @param services the set of services currently registered with the engine
     * @param caseID   the identifier of the suspended case
     */
    @Override
    public void announceCaseResumption(Set<YAWLServiceReference> services, YIdentifier caseID) {
        // No-op for immune system
    }

    /**
     * Called by the engine to notify of a work item status change.
     * No action needed by the immune system.
     *
     * @param services  the set of services currently registered with the engine
     * @param workItem  that has changed
     * @param oldStatus previous status
     * @param newStatus new status
     */
    @Override
    public void announceWorkItemStatusChange(Set<YAWLServiceReference> services, YWorkItem workItem,
                                            YWorkItemStatus oldStatus, YWorkItemStatus newStatus) {
        // No-op for immune system
    }

    /**
     * Called by the engine to notify that it has completed initialisation.
     * No action needed by the immune system.
     *
     * @param services        the set of services currently registered with the engine
     * @param maxWaitSeconds  the maximum amount of time to wait before giving up
     */
    @Override
    public void announceEngineInitialised(Set<YAWLServiceReference> services, int maxWaitSeconds) {
        // No-op for immune system
    }

    /**
     * Called by the engine to announce a case has been cancelled.
     * No action needed by the immune system.
     *
     * @param services the set of services currently registered with the engine
     * @param id       the identifier of the cancelled case
     */
    @Override
    public void announceCaseCancellation(Set<YAWLServiceReference> services, YIdentifier id) {
        // No-op for immune system
    }

    /**
     * Called by the engine to announce that a case has deadlocked.
     * No action needed by the immune system (prediction happens before, not after).
     *
     * @param services the set of services currently registered with the engine
     * @param id       the identifier of the deadlocked case
     * @param tasks    the set of deadlocked tasks
     */
    @Override
    public void announceDeadlock(Set<YAWLServiceReference> services, YIdentifier id, Set<YTask> tasks) {
        // No-op for immune system (prediction happens proactively)
    }

    /**
     * Called when the Engine is shutdown.
     * Performs cleanup operations.
     */
    @Override
    public void shutdown() {
        LOGGER.info("WorkflowImmuneSystem shutting down. Total predictions: " + allPredictions.size());
    }

    /**
     * Extracts affected element IDs from a verification finding.
     *
     * @param finding the verification finding
     * @return set of affected task/place IDs
     */
    private Set<String> extractAffectedElements(VerificationFinding finding) {
        // The finding's taskId is the primary affected element
        return Set.of(finding.taskId());
    }
}
