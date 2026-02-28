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

package org.yawlfoundation.yawl.integration.safe.messages;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentRole;

/**
 * Base interface for SAFe ceremony messages in YAWL A2A communication.
 *
 * <p>Defines the contract for all ceremony messages including sprint planning,
 * dependency notifications, blocker updates, acceptance decisions, and
 * PI planning events.</p>
 *
 * @since YAWL 6.0
 */
public interface CeremonyMessage {

    /**
     * Gets the unique message ID.
     *
     * @return message ID
     */
    String messageId();

    /**
     * Gets the ceremony ID this message belongs to.
     *
     * @return ceremony ID
     */
    String ceremonyId();

    /**
     * Gets the message type.
     *
     * @return message type (e.g., "STORY_CEREMONY", "DEPENDENCY_NOTIFICATION")
     */
    String messageType();

    /**
     * Gets the agent ID that originated this message.
     *
     * @return originating agent ID
     */
    String fromAgentId();

    /**
     * Gets the target roles for this message.
     * Empty set means all roles.
     *
     * @return set of target SAFe roles
     */
    Set<SAFeAgentRole> targetRoles();

    /**
     * Gets the timestamp when message was created.
     *
     * @return creation instant
     */
    Instant createdAt();

    /**
     * Gets the message payload.
     *
     * @return payload map with message content
     */
    Map<String, Object> payload();

    /**
     * Gets optional correlation ID for message threading.
     *
     * @return correlation ID, or null if not set
     */
    String correlationId();

    /**
     * Gets message priority (HIGH, NORMAL, LOW).
     *
     * @return priority level
     */
    String priority();
}
