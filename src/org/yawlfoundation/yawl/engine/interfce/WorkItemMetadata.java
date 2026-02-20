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

import java.util.Map;

/**
 * Immutable metadata for a work item.
 * This record encapsulates task-level configuration and attributes that
 * define the characteristics of the work item.
 *
 * Fields are ordered for optimal cache locality: complex objects first (Map),
 * then common-access strings (taskName, documentation), followed by
 * configuration flags and less-frequently accessed fields.
 *
 * @param attributeTable Extended attributes map (ref)
 * @param taskName The unmodified task name (frequently accessed)
 * @param documentation Documentation for the task (frequently accessed)
 * @param allowsDynamicCreation Whether dynamic creation is allowed
 * @param requiresManualResourcing Whether manual resourcing is required
 * @param codelet Associated codelet identifier
 * @param deferredChoiceGroupID Identifier for deferred choice group membership
 * @param customFormURL Path to alternate custom form JSP
 * @param logPredicateStarted Configurable logging predicate for start events
 * @param logPredicateCompletion Configurable logging predicate for completion events
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public record WorkItemMetadata(
    Map<String, String> attributeTable,
    String taskName,
    String documentation,
    String allowsDynamicCreation,
    String requiresManualResourcing,
    String codelet,
    String deferredChoiceGroupID,
    String customFormURL,
    String logPredicateStarted,
    String logPredicateCompletion
) {

    /**
     * Default constructor with no metadata.
     */
    public WorkItemMetadata() {
        this(null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Constructor with basic task information.
     */
    public WorkItemMetadata(String taskName, String documentation) {
        this(null, taskName, documentation, null, null, null, null, null, null, null);
    }

    /**
     * Returns whether dynamic creation is allowed.
     */
    public boolean isDynamicCreationAllowed() {
        return allowsDynamicCreation != null &&
                allowsDynamicCreation.equalsIgnoreCase("true");
    }

    /**
     * Returns whether this is an auto task (no manual resourcing required).
     */
    public boolean isAutoTask() {
        return requiresManualResourcing != null &&
                requiresManualResourcing.equalsIgnoreCase("false");
    }

    /**
     * Returns whether manual resourcing is required.
     */
    public boolean isManualResourcingRequired() {
        return !isAutoTask();
    }

    /**
     * Returns whether this item is a member of a deferred choice group.
     */
    public boolean isDeferredChoiceGroupMember() {
        return deferredChoiceGroupID != null;
    }

    /**
     * Returns whether documentation is present.
     */
    public boolean hasDocumentation() {
        return documentation != null;
    }

    /**
     * Creates a new metadata record with updated task name.
     */
    public WorkItemMetadata withTaskName(String name) {
        return new WorkItemMetadata(attributeTable, name, documentation, allowsDynamicCreation,
                requiresManualResourcing, codelet, deferredChoiceGroupID,
                customFormURL, logPredicateStarted, logPredicateCompletion);
    }

    /**
     * Creates a new metadata record with updated documentation.
     */
    public WorkItemMetadata withDocumentation(String doc) {
        return new WorkItemMetadata(attributeTable, taskName, doc, allowsDynamicCreation,
                requiresManualResourcing, codelet, deferredChoiceGroupID,
                customFormURL, logPredicateStarted, logPredicateCompletion);
    }

    /**
     * Creates a new metadata record with updated attributes.
     */
    public WorkItemMetadata withAttributes(Map<String, String> attrs) {
        return new WorkItemMetadata(attrs, taskName, documentation, allowsDynamicCreation,
                requiresManualResourcing, codelet, deferredChoiceGroupID,
                customFormURL, logPredicateStarted, logPredicateCompletion);
    }

    /**
     * Creates a new metadata record with updated custom form URL.
     */
    public WorkItemMetadata withCustomFormURL(String url) {
        return new WorkItemMetadata(attributeTable, taskName, documentation, allowsDynamicCreation,
                requiresManualResourcing, codelet, deferredChoiceGroupID,
                url, logPredicateStarted, logPredicateCompletion);
    }

    /**
     * Creates a new metadata record with updated log predicates.
     */
    public WorkItemMetadata withLogPredicates(String started, String completion) {
        return new WorkItemMetadata(attributeTable, taskName, documentation, allowsDynamicCreation,
                requiresManualResourcing, codelet, deferredChoiceGroupID,
                customFormURL, started, completion);
    }
}
