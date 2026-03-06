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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing Participant entities.
 *
 * <p>This interface provides CRUD operations and custom queries for participants,
 * with support for advanced features like availability tracking, workload management,
 * and role-based queries.</p>
 *
 * <h2>Query Methods</h2>
 * <ul>
 *   <li>Standard CRUD operations via JpaRepository</li>
 *   <li>Availability-based queries</li>
 *   <li>Role-based queries</li>
 *   <li>Workload-based queries</li>
 *   <li>HR system integration queries</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Repository
public interface ParticipantRepository extends JpaRepository<Participant, String> {

    /**
     * Finds participants by user ID (LDAP synchronization).
     *
     * @param userId the LDAP user ID
     * @return the participant
     */
    Optional<Participant> findByUserId(String userId);

    /**
     * Finds active participants by role.
     *
     * @param role the role to search for
     * @return list of active participants
     */
    @Query("SELECT p FROM Participant p WHERE p.role = :role AND p.active = true")
    List<Participant> findByRoleAndAvailability(@Param("role") String role, boolean available);

    /**
     * Finds participants by department.
     *
     * @param department the department name
     * @return list of participants in the department
     */
    List<Participant> findByDepartment(String department);

    /**
     * Finds participants by availability status.
     *
     * @param active the active status
     * @return list of participants with the specified status
     */
    List<Participant> findByActive(boolean active);

    /**
     * Finds participants by capability.
     *
     * @param capability the capability to search for
     * @return list of participants with the capability
     */
    @Query("SELECT p FROM Participant p JOIN p.capabilities c WHERE c = :capability")
    List<Participant> findByCapability(@Param("capability") String capability);

    /**
     * Finds participants with workload below a threshold.
     *
     * @param maxWorkload the maximum workload
     * @return list of participants with workload below threshold
     */
    @Query("SELECT p FROM Participant p WHERE p.currentLoad < :maxWorkload")
    List<Participant> findByWorkloadBelow(@Param("maxWorkload") int maxWorkload);

    /**
     * Finds participants by multiple criteria.
     *
     * @param role the role (optional)
     * @param department the department (optional)
     * @param active the active status (optional)
     * @param capability the capability (optional)
     * @return list of matching participants
     */
    @Query("SELECT p FROM Participant p WHERE " +
           "(:role IS NULL OR p.role = :role) AND " +
           "(:department IS NULL OR p.department = :department) AND " +
           "(:active IS NULL OR p.active = :active) AND " +
           "(:capability IS NULL OR :capability MEMBER OF p.capabilities)")
    List<Participant> findByMultipleCriteria(
            @Param("role") String role,
            @Param("department") String department,
            @Param("active") Boolean active,
            @Param("capability") String capability);

    /**
     * Finds participants created after a specific time.
     *
     * @param createdAfter the creation time
     * @return list of participants created after the time
     */
    List<Participant> findByCreatedTimeAfter(Instant createdAfter);

    /**
     * Finds participants updated after a specific time.
     *
     * @param updatedAfter the update time
     * @return list of participants updated after the time
     */
    List<Participant> findByLastUpdatedAfter(Instant updatedAfter);

    /**
     * Finds participants by role and workload.
     *
     * @param role the role
     * @param maxWorkload the maximum workload
     * @return list of participants matching the criteria
     */
    @Query("SELECT p FROM Participant p WHERE p.role = :role AND p.currentLoad < :maxWorkload AND p.active = true")
    List<Participant> findByRoleAndWorkload(@Param("role") String role, @Param("maxWorkload") int maxWorkload);

    /**
     * Finds participants by department and role.
     *
     * @param department the department
     * @param role the role
     * @return list of participants matching the criteria
     */
    @Query("SELECT p FROM Participant p WHERE p.department = :department AND p.role = :role AND p.active = true")
    List<Participant> findByDepartmentAndRole(@Param("department") String department, @Param("role") String role);

    /**
     * Counts active participants by role.
     *
     * @param role the role
     * @return count of active participants
     */
    @Query("SELECT COUNT(p) FROM Participant p WHERE p.role = :role AND p.active = true")
    long countActiveByRole(@Param("role") String role);

    /**
     * Finds participants that are overloaded.
     *
     * @param threshold the workload threshold
     * @return list of overloaded participants
     */
    @Query("SELECT p FROM Participant p WHERE p.currentLoad > :threshold")
    List<Participant> findOverloadedParticipants(@Param("threshold") int threshold);

    /**
     * Finds participants with specific capabilities for HR integration.
     *
     * @param capabilities the list of required capabilities
     * @param active only active participants
     * @return list of participants with all specified capabilities
     */
    @Query("SELECT p FROM Participant p WHERE p.active = :active AND " +
           "SIZE(p.capabilities) >= :required AND " +
           "NOT EXISTS (SELECT c FROM Capability c WHERE c.capability IN :capabilities AND c.participant = p)")
    List<Participant> findParticipantsWithCapabilities(
            @Param("active") boolean active,
            @Param("required") int required,
            @Param("capabilities") List<String> capabilities);

    /**
     * Finds participants for delegation based on availability and workload.
     *
     * @param fromRole the source role
     * @param toRole the target role
     * @return list of eligible delegates
     */
    @Query("SELECT p FROM Participant p WHERE p.role = :toRole AND p.active = true " +
           "AND p.currentLoad < (SELECT AVG(p2.currentLoad) FROM Participant p2 WHERE p2.role = :toRole)")
    List<Participant> findDelegableParticipants(@Param("fromRole") String fromRole, @Param("toRole") String toRole);

    /**
     * Finds participants by email address.
     *
     * @param email the email address
     * @return the participant
     */
    Optional<Participant> findByEmail(String email);

    /**
     * Deletes inactive participants older than a specific time.
     *
     * @param beforeTime the time before which to delete
     * @return number of deleted participants
     */
    @Modifying
    @Query("DELETE FROM Participant p WHERE p.active = false AND p.deactivatedTime < :beforeTime")
    long deleteInactiveBefore(@Param("beforeTime") Instant beforeTime);
}