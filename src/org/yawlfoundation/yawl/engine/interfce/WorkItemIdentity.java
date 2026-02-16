/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.interfce;

import java.util.Objects;

/**
 * Immutable identity information for a work item.
 * This record encapsulates the core identification fields that remain constant
 * throughout a work item's lifecycle.
 *
 * @param specIdentifier The identifier of the specification
 * @param specVersion The version of the specification (defaults to "0.1")
 * @param specURI The URI of the specification
 * @param caseID The case identifier
 * @param taskID The task identifier
 * @param uniqueID Optional unique identifier used by services like PDF Forms
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public record WorkItemIdentity(
    String specIdentifier,
    String specVersion,
    String specURI,
    String caseID,
    String taskID,
    String uniqueID
) {

    /**
     * Canonical constructor with validation.
     * CaseID and taskID are required; other fields may be null.
     */
    public WorkItemIdentity {
        Objects.requireNonNull(caseID, "caseID cannot be null");
        Objects.requireNonNull(taskID, "taskID cannot be null");

        // Default spec version if not provided
        if (specVersion == null || specVersion.isEmpty()) {
            specVersion = "0.1";
        }
    }

    /**
     * Constructor for minimal identity (caseID and taskID only).
     */
    public WorkItemIdentity(String caseID, String taskID) {
        this(null, "0.1", null, caseID, taskID, null);
    }

    /**
     * Constructor with specification details but no uniqueID.
     */
    public WorkItemIdentity(String specIdentifier, String specVersion, String specURI,
                           String caseID, String taskID) {
        this(specIdentifier, specVersion, specURI, caseID, taskID, null);
    }

    /**
     * Returns the composite ID in the format "caseID:taskID".
     */
    public String getID() {
        return caseID + ":" + taskID;
    }

    /**
     * Returns the case ID of the root ancestor case.
     */
    public String getRootCaseID() {
        if (caseID != null) {
            int firstDot = caseID.indexOf('.');
            return (firstDot > -1) ? caseID.substring(0, firstDot) : caseID;
        }
        return null;
    }

    /**
     * Returns the net ID for this work item.
     * For parent items, returns the case ID itself.
     * For child items, returns the parent case ID.
     */
    public String getNetID(String status) {
        if (WorkItemRecord.statusIsParent.equals(status)) {
            return caseID;
        }
        if (caseID != null) {
            int pos = caseID.lastIndexOf('.');
            return (pos < 0) ? caseID : caseID.substring(0, pos);
        }
        return null;
    }

    /**
     * Returns the parent ID in the format "parentCaseID:taskID".
     * Returns null if this is an enabled or fired item.
     */
    public String getParentID(boolean isEnabledOrFired) {
        if (isEnabledOrFired) return null;
        int pos = caseID.lastIndexOf('.');
        return (pos < 0) ? null : caseID.substring(0, pos) + ":" + taskID;
    }
}
