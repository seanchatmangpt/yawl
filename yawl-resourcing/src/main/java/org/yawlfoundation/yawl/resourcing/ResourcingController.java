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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for resourcing operations.
 *
 * <p>Provides REST endpoints for managing participants, work item allocation,
 * delegation, and HR integration.</p>
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>GET /api/resourcing/participants - Get all participants</li>
 *   <li>POST /api/resourcing/participants - Create new participant</li>
 *   <li>GET /api/resourcing/participants/{id} - Get participant by ID</li>
 *   <li>PUT /api/resourcing/participants/{id} - Update participant</li>
 *   <li>DELETE /api/resourcing/participants/{id} - Deactivate participant</li>
 *   <li>POST /api/resourcing/participants/{id}/allocate - Allocate work item</li>
 *   <li>POST /api/resourcing/delegation - Delegate work item</li>
 *   <li>POST /api/resourcing/escalation - Escalate work item</li>
 *   <li>POST /api/resourcing/ldap/sync - Sync with LDAP</li>
 *   <li>POST /api/resourcing/hr/sync - Sync with HR system</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@RestController
@RequestMapping("/api/resourcing")
@CrossOrigin(origins = "*")
public class ResourcingController {

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private YResourcingService resourcingService;

    @Autowired
    private HRIntegrationService hrIntegrationService;

    /**
     * Gets all participants.
     *
     * @return list of all participants
     */
    @GetMapping("/participants")
    public ResponseEntity<List<Participant>> getAllParticipants() {
        List<Participant> participants = participantRepository.findAll();
        return ResponseEntity.ok(participants);
    }

    /**
     * Creates a new participant.
     *
     * @param participant the participant to create
     * @return the created participant
     */
    @PostMapping("/participants")
    @Transactional
    public ResponseEntity<Participant> createParticipant(@RequestBody Participant participant) {
        Participant created = resourcingService.addParticipant(participant);
        return ResponseEntity.ok(created);
    }

    /**
     * Gets a participant by ID.
     *
     * @param id the participant ID
     * @return the participant
     */
    @GetMapping("/participants/{id}")
    public ResponseEntity<Participant> getParticipant(@PathVariable String id) {
        Optional<Participant> participant = Optional.ofNullable(participantRepository.findById(id));
        return participant.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates a participant.
     *
     * @param id the participant ID
     * @param participant the updated participant data
     * @return the updated participant
     */
    @PutMapping("/participants/{id}")
    @Transactional
    public ResponseEntity<Participant> updateParticipant(
            @PathVariable String id,
            @RequestBody Participant participant) {

        if (!participantRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        participant.setId(id); // Ensure ID matches path variable
        Participant updated = resourcingService.updateParticipant(participant);
        return ResponseEntity.ok(updated);
    }

    /**
     * Deactivates a participant.
     *
     * @param id the participant ID
     * @return response entity
     */
    @DeleteMapping("/participants/{id}")
    @Transactional
    public ResponseEntity<Void> deactivateParticipant(@PathVariable String id) {
        if (!participantRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        resourcingService.deactivateParticipant(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Gets participants by role.
     *
     * @param role the role to filter by
     * @return list of participants with the specified role
     */
    @GetMapping("/participants")
    public ResponseEntity<List<Participant>> getParticipantsByRole(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String capability) {

        List<Participant> participants = participantRepository.findByMultipleCriteria(
            role, department, active, capability);

        return ResponseEntity.ok(participants);
    }

    /**
     * Manually allocates a participant to a work item.
     *
     * @param request allocation request
     * @return response entity
     */
    @PostMapping("/allocate")
    @Transactional
    public ResponseEntity<AllocationResponse> allocateParticipant(@RequestBody AllocationRequest request) {
        try {
            boolean success = resourcingService.manuallyAllocateParticipant(
                request.getWorkItem(),
                request.getParticipantId()
            );

            return ResponseEntity.ok(new AllocationResponse(
                success,
                success ? "Allocation successful" : "Allocation failed"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AllocationResponse(false, e.getMessage()));
        }
    }

    /**
     * Delegates a work item from one participant to another.
     *
     * @param request delegation request
     * @return response entity
     */
    @PostMapping("/delegation")
    @Transactional
    public ResponseEntity<DelegationResponse> delegateWorkItem(@RequestBody DelegationRequest request) {
        try {
            boolean success = resourcingService.delegateWorkItem(
                request.getWorkItem(),
                request.getFromParticipant(),
                request.getToParticipantId(),
                request.getReason()
            );

            return ResponseEntity.ok(new DelegationResponse(
                success,
                success ? "Delegation successful" : "Delegation failed"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new DelegationResponse(false, e.getMessage()));
        }
    }

    /**
     * Escalates a work item to a higher-level participant.
     *
     * @param request escalation request
     * @return response entity
     */
    @PostMapping("/escalation")
    @Transactional
    public ResponseEntity<EscalationResponse> escalateWorkItem(@RequestBody EscalationRequest request) {
        try {
            Participant escalatedParticipant = resourcingService.escalateWorkItem(
                request.getWorkItem(),
                request.getCurrentRole(),
                request.getEscalationLevel()
            );

            return ResponseEntity.ok(new EscalationResponse(
                true,
                "Escalation successful",
                escalatedParticipant
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EscalationResponse(false, e.getMessage(), null));
        }
    }

    /**
     * Synchronizes participants with LDAP.
     *
     * @return response entity
     */
    @PostMapping("/ldap/sync")
    public ResponseEntity<SyncResponse> syncWithLdap() {
        try {
            LdapSyncResult result = resourcingService.syncWithLdap();
            return ResponseEntity.ok(new SyncResponse(
                true,
                "LDAP synchronization successful",
                Map.of(
                    "created", result.getCreatedCount(),
                    "updated", result.getUpdatedCount(),
                    "deactivated", result.getDeactivatedCount()
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().new SyncResponse(false, "LDAP sync failed: " + e.getMessage()));
        }
    }

    /**
     * Synchronizes participants with HR system.
     *
     * @return response entity
     */
    @PostMapping("/hr/sync")
    public ResponseEntity<SyncResponse> syncWithHR() {
        try {
            HRIntegrationService.HRSyncResult result = hrIntegrationService.synchronizeEmployeeProfiles().join();
            return ResponseEntity.ok(new SyncResponse(
                result.isSuccess(),
                result.getMessage(),
                Map.of(
                    "created", result.getCreated(),
                    "updated", result.getUpdated(),
                    "deactivated", result.getDeactivated()
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().new SyncResponse(false, "HR sync failed: " + e.getMessage()));
        }
    }

    /**
     * Gets current allocation statistics.
     *
     * @return allocation statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ResourcingStatistics> getStatistics() {
        // Get total participant counts
        long totalParticipants = participantRepository.count();
        long activeParticipants = participantRepository.findByActive(true).size();
        long inactiveParticipants = participantRepository.findByActive(false).size();

        // Get participant counts by role
        Map<String, Long> roleCounts = Map.of(
            "manager", participantRepository.countActiveByRole("manager"),
            "supervisor", participantRepository.countActiveByRole("supervisor"),
            "analyst", participantRepository.countActiveByRole("analyst"),
            "worker", participantRepository.countActiveByRole("worker")
        );

        // Get overloaded participants
        List<Participant> overloaded = participantRepository.findOverloadedParticipants(10);

        ResourcingStatistics stats = new ResourcingStatistics(
            totalParticipants,
            activeParticipants,
            inactiveParticipants,
            roleCounts,
            overloaded.size()
        );

        return ResponseEntity.ok(stats);
    }

    // Request and Response DTOs

    static class AllocationRequest {
        private Object workItem;
        private String participantId;

        public Object getWorkItem() { return workItem; }
        public void setWorkItem(Object workItem) { this.workItem = workItem; }
        public String getParticipantId() { return participantId; }
        public void setParticipantId(String participantId) { this.participantId = participantId; }
    }

    static class AllocationResponse {
        private boolean success;
        private String message;

        public AllocationResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    static class DelegationRequest {
        private Object workItem;
        private Participant fromParticipant;
        private String toParticipantId;
        private String reason;

        public Object getWorkItem() { return workItem; }
        public void setWorkItem(Object workItem) { this.workItem = workItem; }
        public Participant getFromParticipant() { return fromParticipant; }
        public void setFromParticipant(Participant fromParticipant) { this.fromParticipant = fromParticipant; }
        public String getToParticipantId() { return toParticipantId; }
        public void setToParticipantId(String toParticipantId) { this.toParticipantId = toParticipantId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    static class DelegationResponse {
        private boolean success;
        private String message;

        public DelegationResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    static class EscalationRequest {
        private Object workItem;
        private String currentRole;
        private int escalationLevel;

        public Object getWorkItem() { return workItem; }
        public void setWorkItem(Object workItem) { this.workItem = workItem; }
        public String getCurrentRole() { return currentRole; }
        public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }
        public int getEscalationLevel() { return escalationLevel; }
        public void setEscalationLevel(int escalationLevel) { this.escalationLevel = escalationLevel; }
    }

    static class EscalationResponse {
        private boolean success;
        private String message;
        private Participant escalatedParticipant;

        public EscalationResponse(boolean success, String message, Participant escalatedParticipant) {
            this.success = success;
            this.message = message;
            this.escalatedParticipant = escalatedParticipant;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Participant getEscalatedParticipant() { return escalatedParticipant; }
    }

    static class SyncResponse {
        private boolean success;
        private String message;
        private Map<String, Object> data;

        public SyncResponse(boolean success, String message) {
            this(success, message, Map.of());
        }

        public SyncResponse(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }

    static class ResourcingStatistics {
        private long totalParticipants;
        private long activeParticipants;
        private long inactiveParticipants;
        private Map<String, Long> participantCountsByRole;
        private long overloadedParticipants;

        public ResourcingStatistics(long totalParticipants, long activeParticipants,
                                   long inactiveParticipants, Map<String, Long> participantCountsByRole,
                                   long overloadedParticipants) {
            this.totalParticipants = totalParticipants;
            this.activeParticipants = activeParticipants;
            this.inactiveParticipants = inactiveParticipants;
            this.participantCountsByRole = participantCountsByRole;
            this.overloadedParticipants = overloadedParticipants;
        }

        // Getters
        public long getTotalParticipants() { return totalParticipants; }
        public long getActiveParticipants() { return activeParticipants; }
        public long getInactiveParticipants() { return inactiveParticipants; }
        public Map<String, Long> getParticipantCountsByRole() { return participantCountsByRole; }
        public long getOverloadedParticipants() { return overloadedParticipants; }
    }
}