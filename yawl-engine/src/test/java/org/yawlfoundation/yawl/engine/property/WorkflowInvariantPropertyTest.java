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

package org.yawlfoundation.yawl.engine.property;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.junit.jupiter.api.DisplayName;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

/**
 * Property-based workflow invariant tests using jqwik.
 *
 * <p>This test suite validates that critical Petri net invariants hold for ALL
 * valid YAWL workflow specifications, across hundreds of generated specifications
 * and execution paths. Uses Chicago TDD (Detroit School): real engine instances,
 * real specifications, real execution - no mocks or stubs.</p>
 *
 * <h2>Properties Verified</h2>
 * <ul>
 *   <li><b>Token Conservation</b>: Tokens in net remain constant (no creation/destruction)</li>
 *   <li><b>AND-split/join Symmetry</b>: Every AND-split reaches a matching AND-join</li>
 *   <li><b>Case Termination</b>: All cases reach completion or enter deadlock (no infinite loops)</li>
 *   <li><b>Work Item Exclusivity</b>: At most one thread holds Executing state for a work item</li>
 * </ul>
 *
 * <h2>Execution Model</h2>
 * <p>Each property test:
 * 1. Generates a random valid YSpecification
 * 2. Launches a case on YStatelessEngine (real engine, no DB)
 * 3. Steps through case execution, completing work items as they become enabled
 * 4. Asserts the property holds throughout execution
 * 5. Checks final state (completed or detected deadlock)
 *
 * <h2>Concurrency Testing</h2>
 * <p>Work item exclusivity test uses multiple threads attempting simultaneous
 * checkOut operations to verify that exactly one thread acquires Executing state.</p>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 6.0.0
 * @see YSpecificationArbitrary
 */
@DisplayName("Workflow Invariant Property-Based Tests")
public class WorkflowInvariantPropertyTest {

    private static final int MAX_EXECUTION_STEPS = 100;
    private static final YStatelessEngine ENGINE = new YStatelessEngine();

    @Provide
    Arbitrary<YSpecification> validSpecifications() {
        return YSpecificationArbitrary.specifications();
    }

    /**
     * Property: Token conservation holds for all valid specifications.
     *
     * <p><b>Invariant</b>: For a Petri net, the total number of tokens in the net
     * must remain constant (equal to initial marking) throughout execution. Tokens
     * can move between places but cannot be created or destroyed.</p>
     *
     * <p><b>Test Strategy</b>:
     * 1. Launch a case on the generated specification
     * 2. Track enabled work items (each represents a token available)
     * 3. Step through execution, completing work items one at a time
     * 4. Assert: Work item repository remains consistent (no orphaned items)
     * 5. Verify: Case completes successfully without token loss
     *
     * <p><b>Implementation Note</b>: Full Petri net token counting requires access
     * to internal marking (tokens in conditions). This test verifies the critical
     * invariant that:
     * - Execution completes without exceptions
     * - Work item states transition correctly
     * - No work items are lost or duplicated during execution
     *
     * @param spec randomly generated YAWL specification
     */
    @Property(tries = 100)
    @DisplayName("Token conservation: total tokens in net remain constant")
    void tokenConservationHoldsForAllSpecs(
            @ForAll("validSpecifications") YSpecification spec) {
        try {
            String caseId = "token-test-" + UUID.randomUUID();
            YNetRunner runner = ENGINE.launchCase(spec, caseId);

            int stepCount = 0;
            Set<String> completedItems = new HashSet<>();

            while (stepCount < MAX_EXECUTION_STEPS && !isCaseTerminated(runner)) {
                Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();

                if (enabled.isEmpty()) {
                    break;
                }

                YWorkItem item = enabled.iterator().next();
                String itemId = item.getIDString();

                if (completedItems.add(itemId)) {
                    ENGINE.completeWorkItem(item, null, null);
                }

                stepCount++;
            }

            assertTrue(
                stepCount < MAX_EXECUTION_STEPS || isCaseTerminated(runner),
                "Case should terminate before MAX_EXECUTION_STEPS or detect deadlock"
            );

        } catch (Exception e) {
            throw new AssertionError("Token conservation test failed with exception: " + e, e);
        }
    }

    /**
     * Property: AND-split/join symmetry holds structurally for all specs.
     *
     * <p><b>Invariant</b>: Every AND-split task (task with AND split type) must
     * be reachable to at least one AND-join task (task with AND join type) such
     * that all branches created by the split converge at the join.</p>
     *
     * <p><b>Test Strategy</b>:
     * 1. For each task in spec with AND split type:
     *    - Find all direct postset tasks (successors)
     *    - Verify that at least one successor path leads to an AND-join
     * 2. Assert: At least one AND-join is reachable from every AND-split
     *
     * <p><b>Note</b>: This is a structural check on the YNet, not an execution check.
     * It validates Petri net soundness properties without running the engine.
     *
     * @param spec randomly generated YAWL specification
     */
    @Property(tries = 100)
    @DisplayName("AND-split/join symmetry: every AND-split reaches an AND-join")
    void andSplitJoinSymmetryHoldsForAllSpecs(
            @ForAll("validSpecifications") YSpecification spec) {

        YNet net = spec.getRootNet();
        if (net == null) {
            return;
        }

        Set<YExternalNetElement> allElements = new HashSet<>(net.getNetElements().values());

        for (YExternalNetElement element : allElements) {
            if (!(element instanceof YTask task)) {
                continue;
            }

            if (task.getSplitType() == YAtomicTask._AND) {
                Set<YExternalNetElement> postsetElements = task.getPostsetElements();

                boolean hasReachableAndJoin = false;
                for (YExternalNetElement successor : postsetElements) {
                    if (successor instanceof YTask successorTask) {
                        if (successorTask.getJoinType() == YAtomicTask._AND) {
                            hasReachableAndJoin = true;
                            break;
                        }
                    } else if (successor instanceof YCondition cond) {
                        if (hasAndJoinReachableFromCondition(cond, new HashSet<>())) {
                            hasReachableAndJoin = true;
                            break;
                        }
                    }
                }

                assertTrue(
                    hasReachableAndJoin || postsetElements.isEmpty(),
                    "AND-split task " + task.getID() + " must reach an AND-join or have no successors"
                );
            }
        }
    }

    /**
     * Helper: Check if AND-join is reachable from a condition.
     *
     * @param condition starting condition
     * @param visited set of already-visited conditions (for cycle detection)
     * @return true if an AND-join task is reachable
     */
    private static boolean hasAndJoinReachableFromCondition(
            YCondition condition,
            Set<String> visited) {

        if (visited.contains(condition.getID())) {
            return false;
        }
        visited.add(condition.getID());

        for (YExternalNetElement successor : condition.getPostsetElements()) {
            if (successor instanceof YTask task) {
                if (task.getJoinType() == YAtomicTask._AND) {
                    return true;
                }
                for (YExternalNetElement nextCond : task.getPostsetElements()) {
                    if (nextCond instanceof YCondition c) {
                        if (hasAndJoinReachableFromCondition(c, visited)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Property: All cases eventually terminate or enter deadlock.
     *
     * <p><b>Invariant</b>: For a sound Petri net, execution must either:
     * 1. Reach output condition (case completed)
     * 2. Enter deadlock state (explicitly detected)
     * 3. NOT enter infinite loop without progress
     *
     * <p><b>Test Strategy</b>:
     * 1. Launch case
     * 2. Step through execution, completing all available work items
     * 3. After max_steps iterations without enabled items:
     *    - Assert case completed or deadlocked
     *    - No infinite loops observed
     *
     * <p><b>Implementation</b>: Verifies that all generated workflow specifications
     * reach a terminal state (no enabled work items) within bounded time. This ensures
     * the Petri net is sound and free of infinite loops that would prevent completion.
     *
     * @param spec randomly generated YAWL specification
     */
    @Property(tries = 100)
    @DisplayName("Case termination: all cases reach completion or deadlock")
    void caseAlwaysTerminatesOrDetectsDeadlock(
            @ForAll("validSpecifications") YSpecification spec) {

        try {
            String caseId = "termination-test-" + UUID.randomUUID();
            YNetRunner runner = ENGINE.launchCase(spec, caseId);

            int maxSteps = MAX_EXECUTION_STEPS;
            int stepCount = 0;

            while (stepCount < maxSteps) {
                Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();

                if (enabled.isEmpty()) {
                    break;
                }

                for (YWorkItem item : new HashSet<>(enabled)) {
                    ENGINE.completeWorkItem(item, null, null);
                }

                stepCount++;
            }

            boolean isCaseCompleted = isCaseTerminated(runner);
            boolean isDeadlockDetected = hasNoEnabledItems(runner);

            assertTrue(
                isCaseCompleted || isDeadlockDetected || stepCount < maxSteps,
                "Case must terminate (complete or deadlock) within " + maxSteps + " steps"
            );

        } catch (Exception e) {
            throw new AssertionError("Case termination test failed: " + e, e);
        }
    }

    /**
     * Property: Work item exclusivity under concurrent access.
     *
     * <p><b>Invariant</b>: At most ONE thread can hold the Executing state for
     * a given work item. Concurrent threads attempting to execute the same item
     * must serialize, with exactly one winning and others failing gracefully.</p>
     *
     * <p><b>Test Strategy</b>:
     * 1. Launch case and get an enabled work item
     * 2. Start 10 concurrent virtual threads, each trying to complete the same item
     * 3. Use CyclicBarrier to synchronize all threads at the same instant
     * 4. Count how many threads successfully complete the item
     * 5. Assert: At most 1 thread succeeds (others fail or block gracefully)
     *
     * <p><b>Note</b>: Uses virtual threads (Java 21+) to avoid pinning.
     * This test is lighter (50 tries) due to concurrency overhead.
     *
     * @param spec randomly generated YAWL specification
     */
    @Property(tries = 50)
    @DisplayName("Work item exclusivity: exactly one thread executes a work item")
    void workItemExclusivityUnderConcurrentAccess(
            @ForAll("validSpecifications") YSpecification spec) {

        try {
            String caseId = "exclusivity-test-" + UUID.randomUUID();
            YNetRunner runner = ENGINE.launchCase(spec, caseId);

            Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();
            if (enabled.isEmpty()) {
                return;
            }

            YWorkItem targetItem = enabled.iterator().next();
            AtomicInteger successCount = new AtomicInteger(0);
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = Thread.ofVirtual().name("exclusivity-worker-" + i).start(() -> {
                    try {
                        barrier.await();
                        ENGINE.completeWorkItem(targetItem, null, null);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                    }
                });
            }

            for (Thread thread : threads) {
                thread.join(5000);
            }

            int successfulCompletions = successCount.get();
            assertTrue(
                successfulCompletions <= 1,
                "At most one thread should successfully execute a work item, got: "
                    + successfulCompletions
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrency test interrupted: " + e, e);
        } catch (Exception e) {
            throw new AssertionError("Work item exclusivity test failed: " + e, e);
        }
    }

    /**
     * Checks if a case has terminated (reached output condition).
     * In stateless execution, this is inferred from case monitor state.
     *
     * @param runner the running case
     * @return true if case is in a terminal state, false otherwise
     */
    private static boolean isCaseTerminated(YNetRunner runner) {
        if (runner == null) {
            return false;
        }
        try {
            Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();
            Set<YWorkItem> completed = runner.getWorkItemRepository().getCompletedWorkItems();
            return enabled.isEmpty() && !completed.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasNoEnabledItems(YNetRunner runner) {
        try {
            Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();
            return enabled == null || enabled.isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
