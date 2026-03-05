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

package org.yawlfoundation.yawl.integration.safe.orchestration;

/**
 * Listener for SAFe ceremony events.
 *
 * <p>Enables external components to react to ceremony lifecycle events
 * such as ceremony start, completion, message dispatch, and status changes.</p>
 *
 * @since YAWL 6.0
 */
public interface CeremonyEventListener {

    /**
     * Called when a ceremony is started.
     *
     * @param ceremonyId the ceremony ID
     * @param ceremonyType the ceremony type
     */
    void onCeremonyStarted(String ceremonyId, String ceremonyType);

    /**
     * Called when a ceremony is completed.
     *
     * @param ceremonyId the ceremony ID
     * @param ceremonyType the ceremony type
     * @param outcome the completion outcome (SUCCESS, PARTIAL, CANCELLED)
     */
    void onCeremonyCompleted(String ceremonyId, String ceremonyType, String outcome);

    /**
     * Called when a message is dispatched.
     *
     * @param ceremonyId the ceremony ID
     * @param messageId the message ID
     * @param recipientCount number of recipients
     */
    void onMessageDispatched(String ceremonyId, String messageId, int recipientCount);

    /**
     * Called when a participant status changes.
     *
     * @param ceremonyId the ceremony ID
     * @param agentId the agent ID
     * @param state the new state
     */
    void onParticipantStatusChanged(String ceremonyId, String agentId, String state);

    /**
     * Event record for ceremony start.
     */
    record CeremonyStarted(String ceremonyId, String ceremonyType) {}

    /**
     * Event record for ceremony completion.
     */
    record CeremonyCompleted(String ceremonyId, String ceremonyType, String outcome) {}

    /**
     * Event record for message dispatch.
     */
    record MessageDispatched(String ceremonyId, String messageId, int recipientCount) {}

    /**
     * Event record for participant status change.
     */
    record ParticipantStatusChanged(String ceremonyId, String agentId, String state) {}
}
