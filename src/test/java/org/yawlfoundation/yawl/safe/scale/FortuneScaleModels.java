/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.safe.scale;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Domain models for Fortune 5 SAFe simulation at enterprise scale.
 *
 * Records (Java 25 feature) for immutable data structures:
 * - Business unit, value stream, ART, team hierarchy
 * - Stories, dependencies, themes
 * - PI planning results
 * - Portfolio allocation
 * - Disruption response
 * - M&A integration
 */

// ==================== ENTERPRISE STRUCTURE ====================

record BusinessUnit(
    String id,
    String name,
    int totalCapacityPersonDays,
    List<ValueStream> valueStreams
) {}

record ValueStream(
    String id,
    String name,
    List<ART> arts,
    String strategy
) {}

record ART(
    String id,
    String name,
    List<Team> teams,
    ValueStream valueStream,
    int totalCapacityPersonDays,
    Set<String> skills
) {}

record Team(
    String id,
    String name,
    String scrumMasterId,
    String productOwnerId,
    List<String> developerIds,
    int capacityPersonDays,
    Set<String> skills
) {}

// ==================== STORY & DEPENDENCY ====================

record UserStory(
    String id,
    String title,
    String description,
    List<String> acceptanceCriteria,
    int storyPoints,
    int priority,
    String status,
    List<String> dependsOn,
    String assigneeId
) {}

record Dependency(
    String id,
    String consumerArtId,
    String providerArtId,
    String storyId,
    String status,  // SUBMITTED, NEGOTIATING, CONFIRMED, RESOLVED
    Instant submittedAt,
    Instant confirmedAt
) {}

// ==================== PORTFOLIO ====================

record Theme(
    String name,
    String description,
    long estimatedCapacityNeeded,
    int businessValueScore
) {}

// ==================== TEST RESULTS ====================

record PIResult(
    String artId,
    boolean successful,
    List<UserStory> assignedStories,
    List<String> dependencies,
    List<String> teamCommitments,
    List<String> unresolvedIssues,
    Instant startTime,
    Instant endTime
) {
    public static PIResult failed(String artId, Exception cause) {
        return new PIResult(
            artId, false,
            List.of(), List.of(), List.of(),
            List.of(cause.getMessage()),
            Instant.now(), Instant.now()
        );
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<UserStory> getAssignedStories() {
        return assignedStories;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<String> getUnresolvedIssues() {
        return unresolvedIssues;
    }

    public long getDurationSeconds() {
        return Duration.between(startTime, endTime).toSeconds();
    }
}

record StoryFlowResult(
    String storyId,
    boolean completed,
    List<String> stateTransitions,
    Instant startTime,
    Instant endTime
) {
    public static StoryFlowResult failed(String storyId, Exception cause) {
        return new StoryFlowResult(
            storyId, false,
            List.of(cause.getMessage()),
            Instant.now(), Instant.now()
        );
    }

    public boolean isCompleted() {
        return completed;
    }

    public List<String> getStateTransitions() {
        return stateTransitions;
    }

    public long getTotalDurationSeconds() {
        return Duration.between(startTime, endTime).toSeconds();
    }
}

record DependencyResolutionResult(
    String dependencyId,
    boolean resolved,
    boolean hasCircularDependency,
    Instant resolvedAt
) {
    public static DependencyResolutionResult circular(String depId) {
        return new DependencyResolutionResult(depId, false, true, Instant.now());
    }

    public static DependencyResolutionResult failed(String depId, Exception cause) {
        return new DependencyResolutionResult(depId, false, false, Instant.now());
    }

    public boolean isResolved() {
        return resolved;
    }
}

record PortfolioAllocationRequest(
    List<Theme> themes,
    int artCount
) {
    public long getTotalAvailableCapacity() {
        return artCount * 100L;  // 100 person-days per ART baseline
    }
}

record ThemeAllocation(
    String themeName,
    long allocatedCapacity,
    long unallocatedCapacity,
    Instant allocatedAt
) {}

record PortfolioAllocationResult(
    boolean feasible,
    Map<String, ThemeAllocation> themeAllocations,
    long totalAvailableCapacity,
    long totalAllocatedCapacity,
    Instant allocatedAt
) {
    public static PortfolioAllocationResult failed(Exception cause) {
        return new PortfolioAllocationResult(
            false, new HashMap<>(),
            0, 0, Instant.now()
        );
    }

    public boolean isFeasible() {
        return feasible;
    }

    public Map<String, ThemeAllocation> getThemeAllocations() {
        return themeAllocations;
    }
}

record DisruptionAlert(
    String description,
    String severity,  // CRITICAL, HIGH, MEDIUM, LOW
    DisruptionType type,
    Instant alertedAt
) {}

enum DisruptionType {
    MARKET_THREAT, TECHNICAL_DEBT, SECURITY_ISSUE, CAPACITY_CONSTRAINT, RESOURCE_LOSS
}

record DisruptionResponseResult(
    boolean successful,
    String executiveDecision,
    Instant decisionMadeAt,
    int artsReplanned,
    long originalBusinessValue,
    long newBusinessValue,
    Instant completedAt
) {
    public static DisruptionResponseResult failed(Exception cause) {
        return new DisruptionResponseResult(
            false, "FAILED",
            Instant.now(), 0, 0, 0, Instant.now()
        );
    }

    public boolean isSuccessful() {
        return successful;
    }

    public int getARTsReplanned() {
        return artsReplanned;
    }
}

record MAIntegrationResult(
    boolean successful,
    List<ART> newARTs,
    int backlogStoriesIntegrated,
    Instant integratedAt
) {
    public static MAIntegrationResult failed(Exception cause) {
        return new MAIntegrationResult(
            false, List.of(), 0, Instant.now()
        );
    }

    public boolean isSuccessful() {
        return successful;
    }

    public List<ART> getNewARTs() {
        return newARTs;
    }

    public int getNewARTsCreated() {
        return newARTs.size();
    }
}

// ==================== TEST CONTEXTS ====================

class ARTContext {
    private final ART art;
    private final List<UserStory> stories;

    public ARTContext(ART art, List<UserStory> stories) {
        this.art = art;
        this.stories = stories;
    }

    public ART getART() {
        return art;
    }

    public List<UserStory> getStories() {
        return stories;
    }
}

class ChaosInjector {
    private final double failureRate;
    private final Random random;

    public ChaosInjector(double failureRate) {
        this.failureRate = failureRate;
        this.random = new Random();
    }

    public void maybeInjectFailure() throws Exception {
        if (random.nextDouble() < failureRate) {
            Thread.sleep(random.nextInt(1000));  // Simulate timeout
            throw new TimeoutException("Simulated agent timeout");
        }
    }
}

class TimeoutException extends Exception {
    public TimeoutException(String message) {
        super(message);
    }
}
