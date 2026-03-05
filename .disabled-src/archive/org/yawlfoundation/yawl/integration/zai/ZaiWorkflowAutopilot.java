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

package org.yawlfoundation.yawl.integration.zai;

import java.util.*;
import java.util.concurrent.ExecutionException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Document;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.ObserverGateway;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;

/**
 * AI-powered real-time case monitoring co-pilot for YAWL workflows.
 *
 * <p>ZaiWorkflowAutopilot implements the ObserverGateway interface to intercept all
 * YAWL engine lifecycle events and feed them into Z.AI for intelligent analysis.
 * When a work item fires, a deadlock occurs, a case completes, or a status changes,
 * this gateway routes events through AI reasoning to generate actionable insights
 * and recommendations.</p>
 *
 * <h2>Event Processing</h2>
 * <ul>
 *   <li><b>Work Item Events</b> - Routing recommendations and task analysis</li>
 *   <li><b>Deadlock Detection</b> - Analysis and resolution suggestions</li>
 *   <li><b>Case Completion</b> - Summary generation and metrics</li>
 *   <li><b>Status Changes</b> - Intervention suggestions for problematic states</li>
 *   <li><b>Lifecycle Events</b> - Informational logging for case/engine state</li>
 * </ul>
 *
 * <h2>Asynchronous Processing</h2>
 * <p>All AI calls are executed asynchronously on virtual threads to avoid blocking
 * the YAWL engine. Event handlers are fire-and-forget; failures are logged but not
 * propagated.</p>
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 * @since 5.2
 */
public class ZaiWorkflowAutopilot implements ObserverGateway {

    private static final Logger logger = LogManager.getLogger(ZaiWorkflowAutopilot.class);

    private final ZaiService zaiService;
    private final ZaiDecisionReasoner decisionReasoner;

    /**
     * Create a new workflow autopilot with the specified AI services.
     *
     * @param zaiService the Z.AI chat service (must not be null)
     * @param decisionReasoner the decision reasoning engine (must not be null)
     * @throws IllegalArgumentException if either parameter is null
     */
    public ZaiWorkflowAutopilot(ZaiService zaiService,
                                 ZaiDecisionReasoner decisionReasoner) {
        if (zaiService == null) {
            throw new IllegalArgumentException("ZaiService cannot be null");
        }
        if (decisionReasoner == null) {
            throw new IllegalArgumentException("ZaiDecisionReasoner cannot be null");
        }
        this.zaiService = zaiService;
        this.decisionReasoner = decisionReasoner;
        logger.info("ZaiWorkflowAutopilot initialized");
    }

    @Override
    public String getScheme() {
        return "zai-autopilot";
    }

    /**
     * Called when a work item is fired (enabled for execution).
     * Routes async AI analysis to generate routing recommendations.
     */
    @Override
    public void announceFiredWorkItem(YAnnouncement announcement) {
        if (announcement == null) {
            return;
        }

        YWorkItem item = announcement.getItem();
        if (item == null) {
            return;
        }

        Thread.ofVirtual()
                .name("autopilot-fired-" + item.getIDString())
                .start(() -> processFiredWorkItem(item));
    }

    /**
     * Called when a work item is cancelled.
     * Logs cancellation at debug level.
     */
    @Override
    public void announceCancelledWorkItem(YAnnouncement announcement) {
        if (announcement == null) {
            return;
        }

        YWorkItem item = announcement.getItem();
        if (item != null) {
            logger.debug("Work item cancelled: {} in case {}",
                    item.getIDString(), item.getCaseID());
        }
    }

    /**
     * Called when a work item timer expires.
     * Logs expiry at debug level.
     */
    @Override
    public void announceTimerExpiry(YAnnouncement announcement) {
        if (announcement == null) {
            return;
        }

        YWorkItem item = announcement.getItem();
        if (item != null) {
            logger.debug("Work item timer expired: {} in case {}",
                    item.getIDString(), item.getCaseID());
        }
    }

    /**
     * Called when a case is completed with a nominated completion observer.
     * Routes async AI analysis to generate completion summary.
     */
    @Override
    public void announceCaseCompletion(YAWLServiceReference yawlService,
                                        YIdentifier caseID, Document caseData) {
        if (caseID == null) {
            return;
        }

        Thread.ofVirtual()
                .name("autopilot-complete-" + caseID)
                .start(() -> processCaseCompletion(caseID, caseData));
    }

    /**
     * Called when a case starts.
     * Logs case startup at debug level.
     */
    @Override
    public void announceCaseStarted(Set<YAWLServiceReference> services,
                                     YSpecificationID specID, YIdentifier caseID,
                                     String launchingService, boolean delayed) {
        if (caseID != null) {
            String delayedStatus = delayed ? " (delayed)" : "";
            logger.debug("Case started: {} from spec {}{}",
                    caseID, specID, delayedStatus);
        }
    }

    /**
     * Called when a case is completed with no nominated observer.
     * Routes async AI analysis to generate completion summary.
     */
    @Override
    public void announceCaseCompletion(Set<YAWLServiceReference> services,
                                        YIdentifier caseID, Document caseData) {
        if (caseID == null) {
            return;
        }

        Thread.ofVirtual()
                .name("autopilot-complete-" + caseID)
                .start(() -> processCaseCompletion(caseID, caseData));
    }

    /**
     * Called when a case becomes fully suspended.
     * Routes async AI analysis to suggest intervention.
     */
    @Override
    public void announceCaseSuspended(Set<YAWLServiceReference> services,
                                       YIdentifier caseID) {
        if (caseID == null) {
            return;
        }

        Thread.ofVirtual()
                .name("autopilot-suspended-" + caseID)
                .start(() -> processCaseSuspended(caseID));
    }

    /**
     * Called when a case enters the suspending state.
     * Logs at debug level.
     */
    @Override
    public void announceCaseSuspending(Set<YAWLServiceReference> services,
                                        YIdentifier caseID) {
        if (caseID != null) {
            logger.debug("Case suspending: {}", caseID);
        }
    }

    /**
     * Called when a case resumes from suspension.
     * Logs at debug level.
     */
    @Override
    public void announceCaseResumption(Set<YAWLServiceReference> services,
                                        YIdentifier caseID) {
        if (caseID != null) {
            logger.debug("Case resumed: {}", caseID);
        }
    }

    /**
     * Called when a work item status changes.
     * Routes async AI analysis if status indicates a problem.
     */
    @Override
    public void announceWorkItemStatusChange(Set<YAWLServiceReference> services,
                                              YWorkItem workItem,
                                              YWorkItemStatus oldStatus,
                                              YWorkItemStatus newStatus) {
        if (workItem == null || oldStatus == null || newStatus == null) {
            return;
        }

        logger.debug("Work item {} status change: {} -> {}",
                workItem.getIDString(), oldStatus, newStatus);

        // If transitioning to a problematic state, request AI intervention
        if (isProblemStatus(newStatus)) {
            Thread.ofVirtual()
                    .name("autopilot-status-" + workItem.getIDString())
                    .start(() -> processProblematicStatus(workItem, newStatus));
        }
    }

    /**
     * Called when the engine completes initialization.
     * Logs at info level.
     */
    @Override
    public void announceEngineInitialised(Set<YAWLServiceReference> services,
                                           int maxWaitSeconds) {
        logger.info("YAWL engine initialized (max wait: {} seconds)", maxWaitSeconds);
    }

    /**
     * Called when a case is cancelled.
     * Logs at debug level.
     */
    @Override
    public void announceCaseCancellation(Set<YAWLServiceReference> services,
                                          YIdentifier id) {
        if (id != null) {
            logger.debug("Case cancelled: {}", id);
        }
    }

    /**
     * Called when a case deadlocks.
     * Routes async AI analysis to suggest deadlock resolution strategies.
     */
    @Override
    public void announceDeadlock(Set<YAWLServiceReference> services, YIdentifier id,
                                  Set<YTask> tasks) {
        if (id == null || tasks == null || tasks.isEmpty()) {
            return;
        }

        Thread.ofVirtual()
                .name("autopilot-deadlock-" + id)
                .start(() -> processDeadlock(id, tasks));
    }

    /**
     * Called when the engine is being shut down.
     * Releases resources and logs shutdown.
     */
    @Override
    public void shutdown() {
        logger.info("ZaiWorkflowAutopilot shutting down");
    }

    // =========================================================================
    // Private event processing methods (async)
    // =========================================================================

    /**
     * Process a fired work item: extract context and request routing recommendation.
     */
    private void processFiredWorkItem(YWorkItem item) {
        try {
            String taskName = item.getTaskID();
            String caseId = item.getCaseID().toString();
            String workItemId = item.getIDString();

            String prompt = buildFiredWorkItemPrompt(taskName, caseId, workItemId);
            String response = zaiService.chat(prompt);

            logger.info("Fired work item analysis for {}: {}",
                    workItemId, response);
        } catch (Exception e) {
            logger.warn("Failed to analyze fired work item: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a deadlock: build task description and request resolution suggestions.
     */
    private void processDeadlock(YIdentifier caseId, Set<YTask> deadlockedTasks) {
        try {
            String taskList = deadlockedTasks.stream()
                    .map(YTask::getID)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(unknown)");

            String prompt = String.format("""
                    YAWL Workflow Deadlock Analysis

                    Case ID: %s
                    Deadlocked Tasks: %s

                    Analyze this deadlock situation and suggest resolution strategies:
                    1. What caused this deadlock?
                    2. What are recommended resolution options?
                    3. Should the case be cancelled, resumed with manual override, or force-completed?

                    Provide actionable recommendations for the workflow administrator.
                    """, caseId, taskList);

            String analysis = zaiService.chat(prompt);

            logger.warn("DEADLOCK DETECTED - Case {}: {}",
                    caseId, analysis);
        } catch (Exception e) {
            logger.error("Failed to analyze deadlock in case {}: {}",
                    caseId, e.getMessage(), e);
        }
    }

    /**
     * Process case completion: generate summary with key metrics.
     */
    private void processCaseCompletion(YIdentifier caseId, Document caseData) {
        try {
            String dataString = caseData != null
                    ? new org.jdom2.output.XMLOutputter().outputString(caseData)
                    : "(no data)";

            String prompt = String.format("""
                    YAWL Case Completion Summary

                    Case ID: %s
                    Output Data: %s

                    Generate a concise completion summary including:
                    1. Case status and outcome
                    2. Key metrics if available
                    3. Any data quality observations
                    4. Recommendations for process improvement
                    """, caseId, dataString);

            String summary = zaiService.chat(prompt);

            logger.info("Case {} completed. Summary: {}",
                    caseId, summary);
        } catch (Exception e) {
            logger.warn("Failed to generate completion summary for case {}: {}",
                    caseId, e.getMessage(), e);
        }
    }

    /**
     * Process a suspended case: request intervention recommendations.
     */
    private void processCaseSuspended(YIdentifier caseId) {
        try {
            String prompt = String.format("""
                    YAWL Case Suspension Analysis

                    Case ID: %s
                    Status: Case has been suspended

                    Provide recommendations:
                    1. What actions should be taken to resume this case?
                    2. Are there blocking conditions that need resolution?
                    3. Should the case be reassigned, escalated, or cancelled?
                    """, caseId);

            String recommendation = zaiService.chat(prompt);

            logger.warn("Case {} suspended. Recommendation: {}",
                    caseId, recommendation);
        } catch (Exception e) {
            logger.warn("Failed to analyze suspended case {}: {}",
                    caseId, e.getMessage(), e);
        }
    }

    /**
     * Process a work item in a problematic status: request intervention.
     */
    private void processProblematicStatus(YWorkItem item, YWorkItemStatus status) {
        try {
            String prompt = String.format("""
                    YAWL Work Item Problem Analysis

                    Case ID: %s
                    Work Item: %s
                    Task: %s
                    Status: %s

                    Analyze this status and suggest intervention:
                    1. Why might this work item be in this state?
                    2. What are recommended actions?
                    3. Should it be reassigned, escalated, or force-completed?
                    """,
                    item.getCaseID(),
                    item.getIDString(),
                    item.getTaskID(),
                    status);

            String intervention = zaiService.chat(prompt);

            logger.warn("Work item {} status issue. Intervention: {}",
                    item.getIDString(), intervention);
        } catch (Exception e) {
            logger.warn("Failed to analyze problematic status for work item {}: {}",
                    item.getIDString(), e.getMessage(), e);
        }
    }

    // =========================================================================
    // Private helper methods
    // =========================================================================

    /**
     * Determine if a status indicates a potential problem.
     */
    private boolean isProblemStatus(YWorkItemStatus status) {
        return status == YWorkItemStatus.statusDeadlocked ||
               status == YWorkItemStatus.statusFailed ||
               status == YWorkItemStatus.statusDeleted ||
               status == YWorkItemStatus.statusWithdrawn;
    }

    /**
     * Build a prompt for fired work item analysis.
     */
    private String buildFiredWorkItemPrompt(String taskName, String caseId,
                                             String workItemId) {
        return String.format("""
                YAWL Work Item Firing Analysis

                Case ID: %s
                Work Item: %s
                Task Name: %s

                This task has just been enabled and is ready for execution.
                Provide analysis:
                1. What is the purpose of this task in the workflow?
                2. What routing or decision recommendations apply?
                3. Are there any data quality or validation concerns?
                """, caseId, workItemId, taskName);
    }

}
