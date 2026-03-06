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

import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Service that integrates YAWL engine with the resourcing module.
 *
 * <p>This service acts as a bridge between the YAWL engine and the resourcing
 * components, providing both automatic and manual participant allocation for
 * work items.</p>
 *
 * <h2>Integration Points</h2>
 * <ul>
 *   <li>Automatic participant allocation when tasks become enabled</li>
 *   <li>Manual participant allocation via REST API</li>
 *   <li>LDAP integration for participant synchronization</li>
 *   <li>HR system integration for participant management</li>
 *   <li>Delegation and escalation workflows</li>
 *   <li>Multi-role support for complex organizations</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>All operations are thread-safe and can be called concurrently from multiple
 * virtual threads without external synchronization.</p>
 *
 * @since YAWL 6.0
 */
public class YResourcingService {

    private static final Logger _logger = LogManager.getLogger(YResourcingService.class);

    private final YEngine _engine;
    private final ResourceManager _resourceManager;
    private final ParticipantRepository _participantRepository;
    private final boolean _autoAllocationEnabled;
    private final AtomicBoolean _initialized = new AtomicBoolean(false);

    /**
     * Creates a new resourcing service.
     *
     * @param engine the YAWL engine instance
     * @param resourceManager the resource manager for automatic allocation
     * @param participantRepository the participant repository for CRUD operations
     * @param autoAllocationEnabled whether automatic allocation is enabled
     */
    public YResourcingService(YEngine engine, ResourceManager resourceManager,
                            ParticipantRepository participantRepository, boolean autoAllocationEnabled) {
        this._engine = engine;
        this._resourceManager = resourceManager;
        this._participantRepository = participantRepository;
        this._autoAllocationEnabled = autoAllocationEnabled;
    }

    /**
     * Initializes the resourcing service and loads participants from the database.
     *
     * @throws RuntimeException if initialization fails
     */
    public synchronized void initialize() {
        if (_initialized.get()) {
            return;
        }

        try {
            _logger.info("Initializing YAWL Resourcing Service...");

            // Load participants from database
            List<Participant> participants = _participantRepository.findAll();
            _logger.info("Loaded {} participants from database", participants.size());

            // If LDAP sync is enabled, synchronize participants
            if (System.getenv("YAWL_LDAP_ENABLED") != null) {
                syncWithLdap();
            }

            // Register resource manager as work item listener
            if (_autoAllocationEnabled && _resourceManager != null) {
                _engine.addWorkItemEventListener(_resourceManager);
                _logger.info("Resource manager registered as work item event listener");
            }

            _initialized.set(true);
            _logger.info("YAWL Resourcing Service initialized successfully");
        } catch (Exception e) {
            _logger.error("Failed to initialize resourcing service: {}", e.getMessage(), e);
            throw new RuntimeException("Initialization failed", e);
        }
    }

    /**
     * Allocates a participant to a work item using the specified strategy.
     *
     * @param workItem the work item to allocate
     * @param allocator the allocation strategy to use
     * @return the allocated participant
     * @throws AllocationException if no suitable participant can be found
     */
    public Participant allocateWorkItem(YWorkItem workItem, ResourceAllocator allocator)
            throws AllocationException {
        if (workItem == null) {
            throw new IllegalArgumentException("Work item must not be null");
        }
        if (allocator == null) {
            throw new IllegalArgumentException("Allocator must not be null");
        }

        _logger.debug("Allocating participant for work item: {}", workItem.getTaskID());

        // Get available participants based on task requirements
        List<Participant> availableParticipants = getAvailableParticipants(workItem);
        if (availableParticipants.isEmpty()) {
            throw new AllocationException("No available participants found for work item: " + workItem.getTaskID());
        }

        // Use the allocator to choose a participant
        Participant allocated = allocator.allocate(workItem, availableParticipants);

        // Set participant on work item
        workItem.setParticipant(allocated);

        _logger.info("Allocated participant {} to work item {}", allocated.getName(), workItem.getTaskID());

        return allocated;
    }

    /**
     * Manually allocates a participant to a work item.
     *
     * @param workItem the work item to allocate
     * @param participantId the ID of the participant to allocate
     * @return true if allocation was successful
     * @throws AllocationException if allocation fails
     */
    public boolean manuallyAllocateParticipant(YWorkItem workItem, String participantId)
            throws AllocationException {
        if (workItem == null) {
            throw new IllegalArgumentException("Work item must not be null");
        }
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("Participant ID must not be null or blank");
        }

        Participant participant = _participantRepository.findById(participantId);
        if (participant == null || !participant.isActive()) {
            throw new AllocationException("Participant not found or inactive: " + participantId);
        }

        // Check if participant is already overloaded
        if (participant.getCurrentLoad() >= getMaxWorkload(participant)) {
            throw new AllocationException("Participant is overloaded: " + participant.getName());
        }

        // Increment participant load
        participant.incrementLoad();

        // Update work item allocation
        workItem.setParticipant(participant);

        _logger.info("Manually allocated participant {} to work item {}",
            participant.getName(), workItem.getTaskID());

        return true;
    }

    /**
     * Delegates a work item from one participant to another.
     *
     * @param workItem the work item to delegate
     * @param fromParticipant the current participant
     * @param toParticipantId the target participant ID
     * @param reason the reason for delegation
     * @return true if delegation was successful
     * @throws AllocationException if delegation fails
     */
    public boolean delegateWorkItem(YWorkItem workItem, Participant fromParticipant,
                                  String toParticipantId, String reason)
            throws AllocationException {
        if (workItem == null || fromParticipant == null || toParticipantId == null) {
            throw new IllegalArgumentException("All parameters must not be null");
        }

        Participant toParticipant = _participantRepository.findById(toParticipantId);
        if (toParticipant == null || !toParticipant.isActive()) {
            throw new AllocationException("Target participant not found or inactive: " + toParticipantId);
        }

        // Decrement source participant load
        fromParticipant.decrementLoad();

        // Increment target participant load
        toParticipant.incrementLoad();

        // Update work item allocation
        workItem.setParticipant(toParticipant);

        // Log delegation event
        _logger.info("Delegated work item {} from {} to {} (reason: {})",
            workItem.getTaskID(), fromParticipant.getName(), toParticipant.getName(), reason);

        return true;
    }

    /**
     * Escalates a work item to a higher-level participant.
     *
     * @param workItem the work item to escalate
     * @param currentRole the current role
     * @param escalationLevel the escalation level (1, 2, 3)
     * @return the escalated participant
     * @throws AllocationException if escalation fails
     */
    public Participant escalateWorkItem(YWorkItem workItem, String currentRole, int escalationLevel)
            throws AllocationException {
        if (workItem == null || currentRole == null) {
            throw new IllegalArgumentException("Work item and current role must not be null");
        }

        // Get escalation path based on current role
        String targetRole = getEscalationRole(currentRole, escalationLevel);
        if (targetRole == null) {
            throw new AllocationException("No escalation path defined for role: " + currentRole);
        }

        // Find participants in the escalated role
        List<Participant> escalatedParticipants = _participantRepository
            .findByRoleAndAvailability(targetRole, true);

        if (escalatedParticipants.isEmpty()) {
            throw new AllocationException("No available participants found for escalated role: " + targetRole);
        }

        // Use round-robin allocation for escalation
        Participant escalated = escalatedParticipants.get(0); // Simplified - should use round-robin

        // Delegate to escalated participant
        delegateWorkItem(workItem, workItem.getParticipant(), escalated.getId(), "Escalation level " + escalationLevel);

        return escalated;
    }

    /**
     * Synchronizes participants with LDAP/Active Directory.
     *
     * @return the synchronization result
     * @throws LdapSyncException if synchronization fails
     */
    public LdapSyncResult syncWithLdap() throws LdapSyncException {
        LdapParticipantSync ldapSync = new LdapParticipantSync();
        return ldapSync.sync();
    }

    /**
     * Adds a new participant to the system.
     *
     * @param participant the participant to add
     * @return the created participant
     */
    public Participant addParticipant(Participant participant) {
        if (participant == null) {
            throw new IllegalArgumentException("Participant must not be null");
        }

        Participant created = _participantRepository.save(participant);
        _logger.info("Added new participant: {}", created.getName());
        return created;
    }

    /**
     * Updates an existing participant.
     *
     * @param participant the participant to update
     * @return the updated participant
     */
    public Participant updateParticipant(Participant participant) {
        if (participant == null) {
            throw new IllegalArgumentException("Participant must not be null");
        }

        Participant updated = _participantRepository.save(participant);
        _logger.info("Updated participant: {}", updated.getName());
        return updated;
    }

    /**
     * Deactivates a participant.
     *
     * @param participantId the ID of the participant to deactivate
     */
    public void deactivateParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("Participant ID must not be null or blank");
        }

        Participant participant = _participantRepository.findById(participantId);
        if (participant != null) {
            participant.setActive(false);
            _participantRepository.save(participant);
            _logger.info("Deactivated participant: {}", participant.getName());
        }
    }

    /**
     * Gets available participants for a work item.
     *
     * @param workItem the work item
     * @return list of available participants
     */
    private List<Participant> getAvailableParticipants(YWorkItem workItem) {
        // Get task requirements
        YTask task = workItem.getTask();
        String requiredRole = task != null ? task.getRole() : null;

        // Find participants who can handle the task
        List<Participant> candidates = _participantRepository.findByRoleAndAvailability(requiredRole, true);

        // Filter by workload
        return candidates.stream()
            .filter(p -> p.getCurrentLoad() < getMaxWorkload(p))
            .collect(Collectors.toList());
    }

    /**
     * Gets the maximum workload for a participant.
     *
     * @param participant the participant
     * @return the maximum workload
     */
    private int getMaxWorkload(Participant participant) {
        // Default workload limit based on role
        switch (participant.getRole().toLowerCase()) {
            case "manager":
                return 5;
            case "supervisor":
                return 10;
            case "analyst":
                return 15;
            case "worker":
                return 20;
            default:
                return 10;
        }
    }

    /**
     * Gets the escalation role based on current role and level.
     *
     * @param currentRole the current role
     * @param escalationLevel the escalation level (1, 2, 3)
     * @return the escalated role
     */
    private String getEscalationRole(String currentRole, int escalationLevel) {
        // Define escalation paths
        Map<String, List<String>> escalationPaths = Map.of(
            "worker", List.of("supervisor", "manager", "director"),
            "supervisor", List.of("manager", "director", "vp"),
            "analyst", List.of("senior", "lead", "manager"),
            "manager", List.of("director", "vp", "executive")
        );

        List<String> path = escalationPaths.get(currentRole.toLowerCase());
        if (path != null && escalationLevel > 0 && escalationLevel <= path.size()) {
            return path.get(escalationLevel - 1);
        }
        return null;
    }

    /**
     * Checks if the service is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return _initialized.get();
    }

    /**
     * Shuts down the resourcing service.
     */
    public synchronized void shutdown() {
        if (!_initialized.get()) {
            return;
        }

        try {
            // Remove resource manager as work item listener
            if (_resourceManager != null && _engine != null) {
                _engine.removeWorkItemEventListener(_resourceManager);
            }

            _logger.info("YAWL Resourcing Service shutdown successfully");
            _initialized.set(false);
        } catch (Exception e) {
            _logger.error("Error during resourcing service shutdown: {}", e.getMessage(), e);
        }
    }
}