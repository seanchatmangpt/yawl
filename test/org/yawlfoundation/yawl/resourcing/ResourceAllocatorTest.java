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

package org.yawlfoundation.yawl.resourcing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.engine.YWorkItemID;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link ResourceAllocator} implementations:
 * {@link RoundRobinAllocator}, {@link LeastLoadedAllocator}, {@link RoleBasedAllocator},
 * {@link Participant}, and {@link AllocationException}.
 *
 * <p>Uses real implementations and real {@link YWorkItem} instances — no mocks.
 *
 * @since YAWL 6.0
 */
@Tag("unit")
class ResourceAllocatorTest {

    // -------------------------------------------------------------------------
    // Participant
    // -------------------------------------------------------------------------

    @Test
    void participant_initialState() {
        Participant p = new Participant("Alice", "analyst", Set.of("java", "sql"));
        assertNotNull(p.getId());
        assertEquals("Alice", p.getName());
        assertEquals("analyst", p.getRole());
        assertEquals(Set.of("java", "sql"), p.getCapabilities());
        assertEquals(0, p.getCurrentLoad());
    }

    @Test
    void participant_loadIncrementDecrement() {
        Participant p = new Participant("Bob", "manager", Set.of());
        p.incrementLoad();
        p.incrementLoad();
        assertEquals(2, p.getCurrentLoad());
        p.decrementLoad();
        assertEquals(1, p.getCurrentLoad());
        p.decrementLoad();
        p.decrementLoad(); // floor at zero
        assertEquals(0, p.getCurrentLoad());
    }

    @Test
    void participant_nullName_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> new Participant(null, "role", Set.of()));
    }

    @Test
    void participant_nullRole_throws() {
        assertThrows(NullPointerException.class,
            () -> new Participant("Alice", null, Set.of()));
    }

    @Test
    void participant_nullCapabilities_throws() {
        assertThrows(NullPointerException.class,
            () -> new Participant("Alice", "role", null));
    }

    @Test
    void participant_equalityById() {
        Participant p1 = new Participant("Alice", "analyst", Set.of());
        Participant p2 = new Participant("Alice", "analyst", Set.of());
        // Different UUIDs → not equal
        assertNotEquals(p1, p2);
        assertEquals(p1, p1);
    }

    // -------------------------------------------------------------------------
    // RoundRobinAllocator
    // -------------------------------------------------------------------------

    @Test
    void roundRobin_cyclesThroughPool() throws Exception {
        RoundRobinAllocator rr = new RoundRobinAllocator();
        List<Participant> pool = List.of(
            new Participant("A", "role", Set.of()),
            new Participant("B", "role", Set.of()),
            new Participant("C", "role", Set.of())
        );
        YWorkItem wi = workItemWithTask("task1");

        Participant p1 = rr.allocate(wi, pool);
        Participant p2 = rr.allocate(wi, pool);
        Participant p3 = rr.allocate(wi, pool);
        Participant p4 = rr.allocate(wi, pool); // wraps

        assertEquals("A", p1.getName());
        assertEquals("B", p2.getName());
        assertEquals("C", p3.getName());
        assertEquals("A", p4.getName());
    }

    @Test
    void roundRobin_incrementsLoad() throws Exception {
        RoundRobinAllocator rr = new RoundRobinAllocator();
        Participant p = new Participant("X", "role", Set.of());
        List<Participant> pool = List.of(p);
        YWorkItem wi = workItemWithTask("t");

        rr.allocate(wi, pool);
        rr.allocate(wi, pool);
        assertEquals(2, p.getCurrentLoad());
    }

    @Test
    void roundRobin_emptyPool_throws() throws Exception {
        RoundRobinAllocator rr = new RoundRobinAllocator();
        YWorkItem wi = workItemWithTask("t");
        assertThrows(AllocationException.class, () -> rr.allocate(wi, List.of()));
    }

    @Test
    void roundRobin_nullPool_throws() throws Exception {
        RoundRobinAllocator rr = new RoundRobinAllocator();
        YWorkItem wi = workItemWithTask("t");
        assertThrows(NullPointerException.class, () -> rr.allocate(wi, null));
    }

    @Test
    void roundRobin_strategyName() {
        assertEquals("round-robin", new RoundRobinAllocator().strategyName());
    }

    // -------------------------------------------------------------------------
    // LeastLoadedAllocator
    // -------------------------------------------------------------------------

    @Test
    void leastLoaded_picksMinimumLoad() throws Exception {
        LeastLoadedAllocator ll = new LeastLoadedAllocator();
        Participant heavy = new Participant("Heavy", "role", Set.of());
        Participant light = new Participant("Light", "role", Set.of());
        heavy.incrementLoad(); heavy.incrementLoad(); heavy.incrementLoad();
        light.incrementLoad();

        List<Participant> pool = List.of(heavy, light);
        YWorkItem wi = workItemWithTask("t");

        Participant chosen = ll.allocate(wi, pool);
        assertEquals("Light", chosen.getName());
    }

    @Test
    void leastLoaded_allEqualLoad_picksFirst() throws Exception {
        LeastLoadedAllocator ll = new LeastLoadedAllocator();
        Participant a = new Participant("A", "role", Set.of());
        Participant b = new Participant("B", "role", Set.of());
        List<Participant> pool = List.of(a, b);
        YWorkItem wi = workItemWithTask("t");

        Participant chosen = ll.allocate(wi, pool);
        assertEquals("A", chosen.getName()); // first min
    }

    @Test
    void leastLoaded_incrementsChosenLoad() throws Exception {
        LeastLoadedAllocator ll = new LeastLoadedAllocator();
        Participant p = new Participant("P", "role", Set.of());
        assertEquals(0, p.getCurrentLoad());
        ll.allocate(workItemWithTask("t"), List.of(p));
        assertEquals(1, p.getCurrentLoad());
    }

    @Test
    void leastLoaded_emptyPool_throws() throws Exception {
        LeastLoadedAllocator ll = new LeastLoadedAllocator();
        assertThrows(AllocationException.class,
            () -> ll.allocate(workItemWithTask("t"), List.of()));
    }

    @Test
    void leastLoaded_strategyName() {
        assertEquals("least-loaded", new LeastLoadedAllocator().strategyName());
    }

    // -------------------------------------------------------------------------
    // RoleBasedAllocator
    // -------------------------------------------------------------------------

    @Test
    void roleBased_filtersToMatchingRole() throws Exception {
        RoleBasedAllocator rba = new RoleBasedAllocator(
            new RoundRobinAllocator(), "approver");

        Participant analyst  = new Participant("Ana",  "analyst",  Set.of());
        Participant approver = new Participant("Bob",  "approver", Set.of());
        Participant approver2= new Participant("Cara", "approver", Set.of());
        List<Participant> pool = List.of(analyst, approver, approver2);

        YWorkItem wi = workItemWithTask("any-task");
        Participant p1 = rba.allocate(wi, pool);
        Participant p2 = rba.allocate(wi, pool);
        // Round-robin over {Bob, Cara}
        assertEquals("Bob",  p1.getName());
        assertEquals("Cara", p2.getName());
    }

    @Test
    void roleBased_derivesRoleFromTaskId() throws Exception {
        // No explicit role → task ID "manager" is used as role
        RoleBasedAllocator rba = new RoleBasedAllocator(new LeastLoadedAllocator());

        Participant mgr = new Participant("Mike", "manager", Set.of());
        Participant dev = new Participant("Dave", "developer", Set.of());
        List<Participant> pool = List.of(dev, mgr);

        YWorkItem wi = workItemWithTask("manager");
        Participant chosen = rba.allocate(wi, pool);
        assertEquals("Mike", chosen.getName());
    }

    @Test
    void roleBased_noMatchingRole_throws() throws Exception {
        RoleBasedAllocator rba = new RoleBasedAllocator(
            new RoundRobinAllocator(), "cto");

        Participant p = new Participant("Alice", "developer", Set.of());
        YWorkItem wi = workItemWithTask("t");
        assertThrows(AllocationException.class, () -> rba.allocate(wi, List.of(p)));
    }

    @Test
    void roleBased_emptyPool_throws() throws Exception {
        RoleBasedAllocator rba = new RoleBasedAllocator(
            new RoundRobinAllocator(), "any");
        assertThrows(AllocationException.class,
            () -> rba.allocate(workItemWithTask("t"), List.of()));
    }

    @Test
    void roleBased_strategyNameIncludesDelegate() {
        RoleBasedAllocator rba = new RoleBasedAllocator(new LeastLoadedAllocator());
        assertEquals("role-based(least-loaded)", rba.strategyName());
    }

    @Test
    void roleBased_nullDelegate_throws() {
        assertThrows(NullPointerException.class, () -> new RoleBasedAllocator(null));
    }

    // -------------------------------------------------------------------------
    // AllocationException
    // -------------------------------------------------------------------------

    @Test
    void allocationException_messageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        AllocationException ex = new AllocationException("oops", cause);
        assertEquals("oops", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void allocationException_isCheckedException() {
        assertTrue(Exception.class.isAssignableFrom(AllocationException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(AllocationException.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Constructs a minimal {@link YWorkItem} with the given task ID via reflection.
     * Mirrors the pattern used in {@code CapabilityMatcherTest}.
     */
    private static YWorkItem workItemWithTask(String taskId) throws Exception {
        YWorkItem item = new YWorkItem();
        YIdentifier caseId = new YIdentifier("case-test");
        YWorkItemID wid = new YWorkItemID(caseId, taskId);
        Field f = YWorkItem.class.getDeclaredField("_workItemID");
        f.setAccessible(true);
        f.set(item, wid);
        return item;
    }
}
