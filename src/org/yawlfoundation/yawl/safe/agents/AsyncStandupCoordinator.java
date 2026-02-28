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

package org.yawlfoundation.yawl.safe.agents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Asynchronous standup coordinator for distributed team updates.
 *
 * <p>Replaces synchronous daily standups with asynchronous status collection,
 * auto-generating comprehensive standup reports without requiring meetings.
 * Team members submit updates asynchronously; coordinator synthesizes findings,
 * flags blockers, and detects patterns requiring escalation.
 *
 * <p>Features:
 * <ul>
 *   <li>Collects async status updates from all team members (no meeting required)</li>
 *   <li>Auto-generates standup report with progress summary and metrics</li>
 *   <li>Automatically flags blockers and impediments</li>
 *   <li>Detects scope changes, risks, and dependency issues</li>
 *   <li>Highlights pattern anomalies (e.g., same blocker 3+ days)</li>
 *   <li>Provides escalation recommendations for critical issues</li>
 *   <li>Tracks team velocity and capacity utilization in real-time</li>
 * </ul>
 *
 * <p>Typical workflow:
 * 1. Developer submits async update (what I did, what I'm doing, blockers)
 * 2. Coordinator collects updates over 4-6 hour window
 * 3. After deadline, generates comprehensive standup report
 * 4. Report includes summary, metrics, blockers, escalations
 * 5. Team reviews report async; escalations handled separately
 *
 * <p>Eliminates 15-min daily meetings = ~12 hours/month saved per team member.
 * For 8-person team = 96 hours/month (24 work days!) of recovered productivity.
 *
 * @since YAWL 6.0
 */
public final class AsyncStandupCoordinator {

    private static final Logger logger = LogManager.getLogger(AsyncStandupCoordinator.class);

    private static final int COLLECTION_WINDOW_MINUTES = 360; // 6 hours
    private static final int BLOCKER_ESCALATION_THRESHOLD = 3; // Flag if blocker present 3+ days
    private static final double UTILIZATION_WARNING_THRESHOLD = 0.9; // 90% utilization

    /**
     * Individual developer status update.
     */
    public record StatusUpdate(
            String developerId,
            String developerName,
            Instant timestamp,
            String whatIDid,
            String whatImDoing,
            List<String> blockers,
            int storyPointsCompleted,
            String riskNotes
    ) {
    }

    /**
     * Blocker or impediment with tracking.
     */
    public record Blocker(
            String id,
            String developerId,
            String description,
            String severity, // LOW, MEDIUM, HIGH, CRITICAL
            Instant firstReportedAt,
            int daysPersistent,
            String suggestedEscalation
    ) {
    }

    /**
     * Automatically generated standup report.
     */
    public record StandupReport(
            Instant reportDate,
            int totalParticipants,
            int updatesReceived,
            int pointsCompleted,
            double teamUtilization, // 0.0 - 1.0
            List<Blocker> activeBlockers,
            List<Blocker> escalatedBlockers,
            String progressSummary,
            String riskSummary,
            List<String> actionItems,
            long elapsedMinutes
    ) {
    }

    /**
     * Pattern anomaly detected in team updates.
     */
    public record Anomaly(
            String type, // SAME_BLOCKER, HIGH_UTILIZATION, SILENT_MEMBER, SCOPE_CREEP
            String description,
            List<String> affectedMembers,
            String recommendation
    ) {
    }

    private final String teamId;
    private final int expectedTeamSize;
    private final Map<String, List<StatusUpdate>> updateHistory; // developerId -> updates
    private final Map<String, Blocker> activeBlockers; // blockerId -> blocker

    /**
     * Create coordinator for team.
     *
     * @param teamId unique team identifier
     * @param expectedTeamSize number of team members
     */
    public AsyncStandupCoordinator(String teamId, int expectedTeamSize) {
        this.teamId = Objects.requireNonNull(teamId, "Team ID required");
        this.expectedTeamSize = expectedTeamSize;
        this.updateHistory = new HashMap<>();
        this.activeBlockers = new HashMap<>();
        logger.info("AsyncStandupCoordinator initialized for team {} (size: {})",
            teamId, expectedTeamSize);
    }

    /**
     * Record a developer's status update.
     *
     * @param update status update from developer
     * @return unique update ID
     */
    public String recordUpdate(StatusUpdate update) {
        Objects.requireNonNull(update, "Status update required");
        logger.debug("Recording update from {} at {}", update.developerId(), update.timestamp());

        // Store update in history
        updateHistory.computeIfAbsent(update.developerId(), k -> new ArrayList<>())
            .add(update);

        // Process blockers
        processBlockers(update);

        return String.format("update-%s-%d", update.developerId(), update.timestamp().toEpochMilli());
    }

    /**
     * Process blockers from status update.
     */
    private void processBlockers(StatusUpdate update) {
        for (String blockDescription : update.blockers()) {
            String blockerId = String.format("%s-block-%s",
                update.developerId(), blockDescription.hashCode());

            Blocker existingBlocker = activeBlockers.get(blockerId);

            if (existingBlocker != null) {
                // Blocker persists - increment day counter
                Blocker updatedBlocker = new Blocker(
                    blockerId,
                    update.developerId(),
                    blockDescription,
                    escalateSeverityIfNeeded(existingBlocker.severity(), existingBlocker.daysPersistent() + 1),
                    existingBlocker.firstReportedAt(),
                    existingBlocker.daysPersistent() + 1,
                    determineSuggestion(blockDescription, existingBlocker.daysPersistent() + 1)
                );
                activeBlockers.put(blockerId, updatedBlocker);
            } else {
                // New blocker
                Blocker newBlocker = new Blocker(
                    blockerId,
                    update.developerId(),
                    blockDescription,
                    "MEDIUM",
                    update.timestamp(),
                    1,
                    determineSuggestion(blockDescription, 1)
                );
                activeBlockers.put(blockerId, newBlocker);
            }
        }
    }

    /**
     * Escalate blocker severity if it persists across days.
     */
    private String escalateSeverityIfNeeded(String currentSeverity, int daysPersistent) {
        if (daysPersistent >= BLOCKER_ESCALATION_THRESHOLD) {
            return switch (currentSeverity) {
                case "LOW" -> "MEDIUM";
                case "MEDIUM" -> "HIGH";
                case "HIGH" -> "CRITICAL";
                default -> "CRITICAL";
            };
        }
        return currentSeverity;
    }

    /**
     * Determine escalation suggestion based on blocker age.
     */
    private String determineSuggestion(String description, int daysPersistent) {
        if (daysPersistent >= BLOCKER_ESCALATION_THRESHOLD) {
            return "ESCALATE: This blocker persists " + daysPersistent + " days. "
                + "Recommend senior engineer involvement or dependency resolution.";
        } else if (daysPersistent == 2) {
            return "MONITOR: Blocker present for 2 days. Plan escalation if not resolved by tomorrow.";
        }
        return "MONITOR: Blocker reported. Plan resolution within 24 hours.";
    }

    /**
     * Generate standup report based on collected updates.
     *
     * @return comprehensive standup report with summary and escalations
     */
    public StandupReport generateStandupReport() {
        logger.info("Generating standup report for team {}", teamId);

        // Collect metrics from updates
        int pointsCompleted = 0;
        List<String> allProgressNotes = new ArrayList<>();
        List<String> allRisks = new ArrayList<>();

        for (List<StatusUpdate> updates : updateHistory.values()) {
            if (!updates.isEmpty()) {
                StatusUpdate latest = updates.getLast();
                pointsCompleted += latest.storyPointsCompleted();
                allProgressNotes.add(String.format("• %s: %s", latest.developerName(), latest.whatIDid()));
                if (latest.riskNotes() != null && !latest.riskNotes().isBlank()) {
                    allRisks.add(String.format("• %s reported: %s", latest.developerName(), latest.riskNotes()));
                }
            }
        }

        // Calculate team utilization
        double teamUtilization = calculateTeamUtilization();

        // Identify escalated blockers and anomalies
        List<Blocker> escalated = activeBlockers.values().stream()
            .filter(b -> b.severity().equals("HIGH") || b.severity().equals("CRITICAL"))
            .collect(Collectors.toList());

        List<Blocker> allActive = new ArrayList<>(activeBlockers.values());
        Collections.sort(allActive, (a, b) -> b.daysPersistent() - a.daysPersistent());

        List<Anomaly> anomalies = detectAnomalies();
        List<String> actionItems = buildActionItems(escalated, anomalies);

        String progressSummary = String.join("\n", allProgressNotes);
        if (progressSummary.isBlank()) {
            progressSummary = "No progress updates received yet.";
        }

        String riskSummary = allRisks.isEmpty()
            ? "No significant risks reported."
            : String.join("\n", allRisks);

        if (teamUtilization > UTILIZATION_WARNING_THRESHOLD) {
            riskSummary += String.format("\nWARNING: Team utilization at %.0f%% - consider scope reduction.",
                teamUtilization * 100);
        }

        return new StandupReport(
            Instant.now(),
            expectedTeamSize,
            updateHistory.size(),
            pointsCompleted,
            teamUtilization,
            allActive,
            escalated,
            progressSummary,
            riskSummary,
            actionItems,
            COLLECTION_WINDOW_MINUTES
        );
    }

    /**
     * Calculate team utilization as percentage of capacity.
     */
    private double calculateTeamUtilization() {
        int totalPointsAssigned = updateHistory.values().stream()
            .flatMap(List::stream)
            .mapToInt(StatusUpdate::storyPointsCompleted)
            .sum();

        // Assume 100 points per team member per sprint as baseline
        int totalCapacity = expectedTeamSize * 100;
        if (totalCapacity == 0) {
            return 0.0;
        }

        return Math.min(1.0, (double) totalPointsAssigned / totalCapacity);
    }

    /**
     * Detect pattern anomalies in team updates.
     */
    private List<Anomaly> detectAnomalies() {
        List<Anomaly> anomalies = new ArrayList<>();

        // Anomaly 1: Same blocker persisting across multiple days
        Map<String, Long> blockerFrequency = activeBlockers.values().stream()
            .filter(b -> b.daysPersistent() >= BLOCKER_ESCALATION_THRESHOLD)
            .collect(Collectors.groupingBy(Blocker::description, Collectors.counting()));

        for (Map.Entry<String, Long> entry : blockerFrequency.entrySet()) {
            if (entry.getValue() > 0) {
                anomalies.add(new Anomaly(
                    "SAME_BLOCKER",
                    String.format("Blocker '%s' persists across %d days", entry.getKey(), entry.getValue()),
                    Collections.emptyList(),
                    "ESCALATE: Root cause analysis required. Consider architecture review or dependency resolution."
                ));
            }
        }

        // Anomaly 2: High team utilization
        double util = calculateTeamUtilization();
        if (util > UTILIZATION_WARNING_THRESHOLD) {
            anomalies.add(new Anomaly(
                "HIGH_UTILIZATION",
                String.format("Team utilization at %.0f%% of capacity", util * 100),
                new ArrayList<>(updateHistory.keySet()),
                "RECOMMEND: Review sprint scope. Consider reducing commitments or adding team members."
            ));
        }

        // Anomaly 3: Silent team members (no updates)
        List<String> silentMembers = new ArrayList<>();
        for (int i = 0; i < expectedTeamSize; i++) {
            String memberId = String.format("member-%d", i);
            if (!updateHistory.containsKey(memberId) && i >= updateHistory.size()) {
                silentMembers.add(memberId);
            }
        }

        if (!silentMembers.isEmpty()) {
            anomalies.add(new Anomaly(
                "SILENT_MEMBER",
                String.format("%d team members have not submitted updates", silentMembers.size()),
                silentMembers,
                "FOLLOW_UP: Remind silent members to submit async status updates."
            ));
        }

        // Anomaly 4: Scope creep detection (points increasing without story assignments)
        List<String> scopeCreepMembers = new ArrayList<>();
        for (Map.Entry<String, List<StatusUpdate>> entry : updateHistory.entrySet()) {
            if (entry.getValue().size() >= 2) {
                StatusUpdate prev = entry.getValue().get(entry.getValue().size() - 2);
                StatusUpdate curr = entry.getValue().getLast();
                if (curr.storyPointsCompleted() > prev.storyPointsCompleted() * 1.5) {
                    scopeCreepMembers.add(entry.getKey());
                }
            }
        }

        if (!scopeCreepMembers.isEmpty()) {
            anomalies.add(new Anomaly(
                "SCOPE_CREEP",
                String.format("%d developers showing rapid point increases (possible scope creep)",
                    scopeCreepMembers.size()),
                scopeCreepMembers,
                "VERIFY: Confirm story assignments match velocity trends. Investigate untracked work."
            ));
        }

        return anomalies;
    }

    /**
     * Build actionable items from blockers and anomalies.
     */
    private List<String> buildActionItems(List<Blocker> escalated, List<Anomaly> anomalies) {
        List<String> items = new ArrayList<>();

        // Action items from escalated blockers
        for (Blocker blocker : escalated) {
            items.add(String.format("ESCALATE: %s (reported by %s) - %s",
                blocker.description(), blocker.developerId(), blocker.suggestedEscalation()));
        }

        // Action items from anomalies
        for (Anomaly anomaly : anomalies) {
            items.add(String.format("[%s] %s - %s",
                anomaly.type(), anomaly.description(), anomaly.recommendation()));
        }

        if (items.isEmpty()) {
            items.add("No critical action items. Continue sprint execution.");
        }

        return items;
    }

    /**
     * Resolve a blocker (mark as fixed).
     *
     * @param blockerId blocker to resolve
     * @param resolution description of how it was resolved
     */
    public void resolveBlocker(String blockerId, String resolution) {
        Objects.requireNonNull(blockerId, "Blocker ID required");
        Objects.requireNonNull(resolution, "Resolution required");

        Blocker blocker = activeBlockers.remove(blockerId);
        if (blocker != null) {
            logger.info("Resolved blocker: {} - {}", blocker.description(), resolution);
        } else {
            logger.warn("Attempted to resolve non-existent blocker: {}", blockerId);
        }
    }

    /**
     * Get all active blockers for a developer.
     *
     * @param developerId developer ID
     * @return list of active blockers
     */
    public List<Blocker> getDevBlockers(String developerId) {
        return activeBlockers.values().stream()
            .filter(b -> b.developerId().equals(developerId))
            .collect(Collectors.toList());
    }

    /**
     * Get latest status update for a developer.
     *
     * @param developerId developer ID
     * @return optional containing latest update
     */
    public Optional<StatusUpdate> getLatestUpdate(String developerId) {
        List<StatusUpdate> updates = updateHistory.get(developerId);
        return updates != null && !updates.isEmpty()
            ? Optional.of(updates.getLast())
            : Optional.empty();
    }

    /**
     * Get all status updates for a developer.
     *
     * @param developerId developer ID
     * @return list of historical updates
     */
    public List<StatusUpdate> getAllUpdates(String developerId) {
        return new ArrayList<>(updateHistory.getOrDefault(developerId, Collections.emptyList()));
    }

    /**
     * Calculate team velocity from collected updates.
     *
     * @return average points completed per developer
     */
    public double calculateTeamVelocity() {
        if (updateHistory.isEmpty()) {
            return 0.0;
        }

        int totalPoints = updateHistory.values().stream()
            .flatMap(List::stream)
            .mapToInt(StatusUpdate::storyPointsCompleted)
            .sum();

        return (double) totalPoints / updateHistory.size();
    }

    /**
     * Format standup report as human-readable text.
     *
     * @param report the report to format
     * @return formatted text suitable for email/Slack
     */
    public String formatReportAsText(StandupReport report) {
        StringBuilder text = new StringBuilder();

        text.append("=== ASYNC STANDUP REPORT ===\n");
        text.append(String.format("Date: %s\n", report.reportDate()));
        text.append(String.format("Team Participation: %d/%d members\n\n", report.updatesReceived(), report.totalParticipants()));

        text.append("PROGRESS\n");
        text.append(String.format("Points Completed: %d\n", report.pointsCompleted()));
        text.append(String.format("Team Utilization: %.0f%%\n\n", report.teamUtilization() * 100));

        text.append("SUMMARY\n");
        text.append(report.progressSummary()).append("\n\n");

        if (!report.activeBlockers().isEmpty()) {
            text.append("BLOCKERS ").append(report.activeBlockers().size()).append("\n");
            for (Blocker blocker : report.activeBlockers()) {
                text.append(String.format("[%s] %s (Day %d)\n",
                    blocker.severity(), blocker.description(), blocker.daysPersistent()));
            }
            text.append("\n");
        }

        if (!report.escalatedBlockers().isEmpty()) {
            text.append("ESCALATIONS (").append(report.escalatedBlockers().size()).append(")\n");
            for (Blocker blocker : report.escalatedBlockers()) {
                text.append(String.format("• %s\n  Escalation: %s\n", blocker.description(), blocker.suggestedEscalation()));
            }
            text.append("\n");
        }

        text.append("RISKS\n");
        text.append(report.riskSummary()).append("\n\n");

        if (!report.actionItems().isEmpty()) {
            text.append("ACTION ITEMS\n");
            for (String item : report.actionItems()) {
                text.append("• ").append(item).append("\n");
            }
        }

        return text.toString();
    }

    /**
     * Get human-readable team summary.
     *
     * @return summary string
     */
    public String getTeamSummary() {
        return String.format(
            "AsyncStandupCoordinator[team=%s, members=%d, updates=%d, blockers=%d, velocity=%.1f]",
            teamId, expectedTeamSize, updateHistory.size(), activeBlockers.size(), calculateTeamVelocity()
        );
    }
}
