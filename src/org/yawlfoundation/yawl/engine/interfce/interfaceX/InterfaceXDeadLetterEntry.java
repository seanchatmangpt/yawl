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

package org.yawlfoundation.yawl.engine.interfce.interfaceX;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a dead letter entry for Interface X notifications that exhausted all retry attempts.
 *
 * <p>Each entry contains:
 * <ul>
 *   <li>The command type that failed</li>
 *   <li>The original parameters for potential retry</li>
 *   <li>The observer URI that was the target</li>
 *   <li>The failure reason</li>
 *   <li>Attempt count when exhausted</li>
 *   <li>TTL-based expiration</li>
 *   <li>Manual retry tracking</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class InterfaceXDeadLetterEntry {

    private final String id;
    private final int command;
    private final Map<String, String> parameters;
    private final String observerURI;
    private final String failureReason;
    private final int attemptCount;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant lastRetryAttempt;
    private int manualRetryCount;

    /**
     * Creates a new dead letter entry.
     *
     * @param command the command code (0-6 for Interface X commands)
     * @param parameters the original request parameters
     * @param observerURI the target observer URI
     * @param failureReason the failure message
     * @param attemptCount the number of attempts made before exhaustion
     * @param ttlHours the time-to-live in hours
     */
    public InterfaceXDeadLetterEntry(int command, Map<String, String> parameters,
                                     String observerURI, String failureReason,
                                     int attemptCount, int ttlHours) {
        this.id = UUID.randomUUID().toString();
        this.command = command;
        this.parameters = parameters != null
                ? Collections.unmodifiableMap(parameters)
                : Collections.emptyMap();
        this.observerURI = observerURI;
        this.failureReason = failureReason;
        this.attemptCount = attemptCount;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plusSeconds(ttlHours * 3600L);
        this.manualRetryCount = 0;
    }

    /**
     * Gets the unique ID of this entry.
     *
     * @return the UUID string
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the command code.
     *
     * @return the command code (0-6)
     */
    public int getCommand() {
        return command;
    }

    /**
     * Gets the human-readable command name.
     *
     * @return the command name
     */
    public String getCommandName() {
        return InterfaceX_EngineSideClient.getCommandName(command);
    }

    /**
     * Gets the original request parameters.
     *
     * @return immutable map of parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Gets the target observer URI.
     *
     * @return the observer URI
     */
    public String getObserverURI() {
        return observerURI;
    }

    /**
     * Gets the failure reason message.
     *
     * @return the failure reason
     */
    public String getFailureReason() {
        return failureReason;
    }

    /**
     * Gets the number of automatic retry attempts made.
     *
     * @return the attempt count
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return when this entry was created
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the expiration timestamp.
     *
     * @return when this entry expires
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks if this entry has expired.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Gets the timestamp of the last manual retry attempt.
     *
     * @return the last retry timestamp, or null if never retried
     */
    public Instant getLastRetryAttempt() {
        return lastRetryAttempt;
    }

    /**
     * Gets the number of manual retry attempts.
     *
     * @return the manual retry count
     */
    public int getManualRetryCount() {
        return manualRetryCount;
    }

    /**
     * Records a manual retry attempt.
     */
    public void recordManualRetry() {
        this.lastRetryAttempt = Instant.now();
        this.manualRetryCount++;
    }

    @Override
    public String toString() {
        return String.format("InterfaceXDeadLetterEntry{id=%s, command=%s, observer=%s, attempts=%d, retries=%d}",
                id, getCommandName(), observerURI, attemptCount, manualRetryCount);
    }
}
