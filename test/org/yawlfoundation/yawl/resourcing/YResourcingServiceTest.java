/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or/modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.resourcing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for YAWL Resourcing Service.
 *
 * <p>This test suite validates the integration between the YAWL engine and
 * the resourcing module, including participant allocation, delegation,
 * escalation, and external system integration.</p>
 *
 * @since YAWL 6.0
 */
class YResourcingServiceTest {

    private YResourcingService resourcingService;
    private ParticipantRepository participantRepository;
    private ResourceManager resourceManager;

    @Mock
    private YEngine engine;

    @Mock
    private YWorkItem workItem;

    @Mock
    private YTask task;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        participantRepository = mock(ParticipantRepository.class);
        resourceManager = mock(ResourceManager.class);

        resourcingService = new YResourcingService(
            engine, resourceManager, participantRepository, false
        );

        // Mock work item
        when(workItem.getTask()).thenReturn(task);
        when(workItem.getTaskID()).thenReturn("task-123");
        when(workItem.setParticipant(any())).thenAnswer(invocation -> null);
    }

    @Test
    @DisplayName("Service initialization should succeed")
    void testServiceInitialization() {
        // Setup
        when(participantRepository.findAll()).thenReturn(Collections.emptyList());

        // Execute
        resourcingService.initialize();

        // Verify
        assertTrue(resourcingService.isInitialized());
        verify(participantRepository).findAll();
    }

    @Test
    @DisplayName("Manual participant allocation should succeed")
    void testManualParticipantAllocation() {
        // Setup
        Participant participant = createTestParticipant("user-123", "John Doe", "worker");
        when(participantRepository.findById("user-123")).thenReturn(participant);
        when(participant.getCurrentLoad()).thenReturn(0);

        // Execute
        boolean result = resourcingService.manuallyAllocateParticipant(workItem, "user-123");

        // Verify
        assertTrue(result);
        assertEquals(1, participant.getCurrentLoad());
        verify(workItem).setParticipant(participant);
    }

    @Test
    @DisplayName("Manual allocation should fail for invalid participant")
    void testManualAllocationFailsForInvalidParticipant() {
        // Setup
        when(participantRepository.findById("invalid-123")).thenReturn(null);

        // Execute & Verify
        assertThrows(AllocationException.class, () -> {
            resourcingService.manuallyAllocateParticipant(workItem, "invalid-123");
        });
    }

    @Test
    @DisplayName("Manual allocation should fail for overloaded participant")
    void testManualAllocationFailsForOverloadedParticipant() {
        // Setup
        Participant participant = createTestParticipant("user-123", "John Doe", "worker");
        participant.incrementLoad();
        participant.incrementLoad(); // At max capacity
        when(participantRepository.findById("user-123")).thenReturn(participant);
        when(participant.getCurrentLoad()).thenReturn(20); // Max load for worker

        // Execute & Verify
        assertThrows(AllocationException.class, () -> {
            resourcingService.manuallyAllocateParticipant(workItem, "user-123");
        });
    }

    @Test
    @DisplayName("Work item delegation should succeed")
    void testWorkItemDelegation() {
        // Setup
        Participant fromParticipant = createTestParticipant("from-123", "Alice", "worker");
        Participant toParticipant = createTestParticipant("to-123", "Bob", "supervisor");

        when(participantRepository.findById("to-123")).thenReturn(toParticipant);
        when(workItem.getParticipant()).thenReturn(fromParticipant);

        // Execute
        boolean result = resourcingService.delegateWorkItem(
            workItem, fromParticipant, "to-123", "Urgent request"
        );

        // Verify
        assertTrue(result);
        assertEquals(0, fromParticipant.getCurrentLoad());
        assertEquals(1, toParticipant.getCurrentLoad());
        verify(workItem).setParticipant(toParticipant);
    }

    @Test
    @DisplayName("Work item escalation should succeed")
    void testWorkItemEscalation() {
        // Setup
        Participant currentParticipant = createTestParticipant("user-123", "John", "worker");
        when(workItem.getParticipant()).thenReturn(currentParticipant);

        List<Participant> escalatedParticipants = Collections.singletonList(
            createTestParticipant("manager-123", "Sarah", "manager")
        );
        when(participantRepository.findByRoleAndAvailability("manager", true))
            .thenReturn(escalatedParticipants);

        // Execute
        Participant result = resourcingService.escalateWorkItem(
            workItem, "worker", 1
        );

        // Verify
        assertEquals("manager-123", result.getId());
        assertEquals("Sarah", result.getName());
        assertEquals(0, currentParticipant.getCurrentLoad());
        assertEquals(1, result.getCurrentLoad());
    }

    @Test
    @DisplayName("Escalation should fail with invalid role")
    void testEscalationFailsWithInvalidRole() {
        // Execute & Verify
        assertThrows(AllocationException.class, () -> {
            resourcingService.escalateWorkItem(workItem, "invalid-role", 1);
        });
    }

    @Test
    @DisplayName("Escalation should fail with no available participants")
    void testEscalationFailsWithNoAvailableParticipants() {
        // Setup
        when(workItem.getParticipant()).thenReturn(createTestParticipant("user-123", "John", "worker"));
        when(participantRepository.findByRoleAndAvailability("manager", true))
            .thenReturn(Collections.emptyList());

        // Execute & Verify
        assertThrows(AllocationException.class, () -> {
            resourcingService.escalateWorkItem(workItem, "worker", 1);
        });
    }

    @Test
    @DisplayName("Participant addition should succeed")
    void testAddParticipant() {
        // Setup
        Participant newParticipant = createTestParticipant("new-123", "New User", "analyst");

        when(participantRepository.save(any())).thenAnswer(invocation -> {
            Participant p = invocation.getArgument(0);
            p.setId("generated-id");
            return p;
        });

        // Execute
        Participant result = resourcingService.addParticipant(newParticipant);

        // Verify
        assertNotNull(result.getId());
        assertEquals("new-123", result.getUserId());
        assertEquals("New User", result.getName());
        assertEquals("analyst", result.getRole());
        verify(participantRepository).save(newParticipant);
    }

    @Test
    @DisplayName("Participant update should succeed")
    void testUpdateParticipant() {
        // Setup
        Participant existingParticipant = createTestParticipant("existing-123", "Old Name", "worker");
        Participant updatedParticipant = createTestParticipant("existing-123", "Updated Name", "supervisor");

        when(participantRepository.save(any())).thenReturn(updatedParticipant);

        // Execute
        Participant result = resourcingService.updateParticipant(updatedParticipant);

        // Verify
        assertEquals("Updated Name", result.getName());
        assertEquals("supervisor", result.getRole());
        verify(participantRepository).save(updatedParticipant);
    }

    @Test
    @DisplayName("Participant deactivation should succeed")
    void testDeactivateParticipant() {
        // Setup
        Participant participant = createTestParticipant("active-123", "Active User", "worker");
        when(participantRepository.findById("active-123")).thenReturn(participant);

        // Execute
        resourcingService.deactivateParticipant("active-123");

        // Verify
        assertFalse(participant.isActive());
        verify(participantRepository).save(participant);
    }

    @Test
    @DisplayName("Available participant filtering should work correctly")
    void testAvailableParticipantFiltering() {
        // Setup
        List<Participant> allParticipants = Arrays.asList(
            createTestParticipant("worker-1", "Worker 1", "worker"),
            createTestParticipant("worker-2", "Worker 2", "worker"),
            createTestParticipant("manager-1", "Manager 1", "manager")
        );

        // Simulate some workload
        allParticipants.get(0).incrementLoad(); // At max capacity
        allParticipants.get(2).incrementLoad(); // At max capacity

        when(participantRepository.findByRoleAndAvailability("worker", true))
            .thenReturn(allParticipants.subList(0, 2));

        // Execute
        List<Participant> available = resourcingService.getAvailableParticipants(workItem);

        // Verify
        assertEquals(1, available.size());
        assertEquals("worker-2", available.get(0).getId());
    }

    @Test
    @DisplayName("Service shutdown should clean up resources")
    void testServiceShutdown() {
        // Setup
        when(engine.removeWorkItemEventListener(any())).thenReturn(true);

        // Execute
        resourcingService.shutdown();

        // Verify
        assertFalse(resourcingService.isInitialized());
        verify(engine).removeWorkItemEventListener(resourceManager);
    }

    @Test
    @DisplayName("Max workload calculation should be role-based")
    void testMaxWorkloadCalculation() {
        assertEquals(20, resourcingService.getMaxWorkload(createTestParticipant("w", "W", "worker")));
        assertEquals(15, resourcingService.getMaxWorkload(createTestParticipant("a", "A", "analyst")));
        assertEquals(10, resourcingService.getMaxWorkload(createTestParticipant("s", "S", "supervisor")));
        assertEquals(5, resourcingService.getMaxWorkload(createTestParticipant("m", "M", "manager")));
        assertEquals(10, resourcingService.getMaxWorkload(createTestParticipant("x", "X", "unknown")));
    }

    @Test
    @DisplayName("Escalation path lookup should work correctly")
    void testEscalationPathLookup() {
        assertEquals("supervisor", resourcingService.getEscalationRole("worker", 1));
        assertEquals("manager", resourcingService.getEscalationRole("worker", 2));
        assertEquals("director", resourcingService.getEscalationRole("worker", 3));
        assertNull(resourcingService.getEscalationRole("worker", 4));
        assertNull(resourcingService.getEscalationRole("invalid-role", 1));
    }

    /**
     * Helper method to create a test participant.
     */
    private Participant createTestParticipant(String userId, String name, String role) {
        Set<String> capabilities = Set.of("basic-skills");
        Participant participant = new Participant(name, role, capabilities, userId);
        participant.setId("id-" + userId);
        return participant;
    }
}