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

import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable task information record.
 * Converted to Java 25 record for improved immutability and type safety.
 *
 * @author Lachlan Aldred
 * @author Michael Adams (v2.1 rework)
 * @author YAWL Foundation (Java 25 conversion)
 * @since 0.1
 * @version 5.2
 *
 * @param paramSchema Parameter schema for the task
 * @param taskID Unique task identifier
 * @param specificationID Specification identifier
 * @param taskName Human-readable task name
 * @param taskDocumentation Task documentation text
 * @param decompositionID Associated decomposition identifier
 * @param attributes Extended attributes map (immutable view)
 */
public record TaskInformation(
    YParametersSchema paramSchema,
    String taskID,
    YSpecificationID specificationID,
    String taskName,
    String taskDocumentation,
    String decompositionID,
    Map<String, String> attributes
) implements Serializable {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    /**
     * Canonical constructor ensuring immutability of attributes map.
     */
    public TaskInformation {
        attributes = attributes == null ?
            Map.of() :
            Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    /**
     * Constructor without attributes (creates empty attribute map).
     */
    public TaskInformation(YParametersSchema paramSchema, String taskID,
                          YSpecificationID specificationID, String taskName,
                          String taskDocumentation, String decompositionID) {
        this(paramSchema, taskID, specificationID, taskName,
             taskDocumentation, decompositionID, Map.of());
    }

    /**
     * Legacy getter for paramSchema (maintains backward compatibility).
     * @deprecated Use paramSchema() accessor instead
     */
    @Deprecated
    public YParametersSchema getParamSchema() {
        return paramSchema;
    }

    /**
     * Legacy getter for taskID (maintains backward compatibility).
     * @deprecated Use taskID() accessor instead
     */
    @Deprecated
    public String getTaskID() {
        return taskID;
    }

    /**
     * Legacy getter for specificationID (maintains backward compatibility).
     * @deprecated Use specificationID() accessor instead
     */
    @Deprecated
    public YSpecificationID getSpecificationID() {
        return specificationID;
    }

    /**
     * Legacy getter for taskDocumentation (maintains backward compatibility).
     * @deprecated Use taskDocumentation() accessor instead
     */
    @Deprecated
    public String getTaskDocumentation() {
        return taskDocumentation;
    }

    /**
     * Legacy getter for taskName (maintains backward compatibility).
     * @deprecated Use taskName() accessor instead
     */
    @Deprecated
    public String getTaskName() {
        return taskName;
    }

    /**
     * Legacy getter for decompositionID (maintains backward compatibility).
     * @deprecated Use decompositionID() accessor instead
     */
    @Deprecated
    public String getDecompositionID() {
        return decompositionID;
    }

    /**
     * Legacy getter for attributes (maintains backward compatibility).
     * @deprecated Use attributes() accessor instead
     */
    @Deprecated
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Gets a specific attribute value.
     * @param key the attribute key
     * @return the attribute value, or null if not present
     */
    public String getAttribute(String key) {
        return attributes.get(key);
    }

    /**
     * Creates a new TaskInformation with updated attributes.
     * @param newAttributes the new attributes map
     * @return a new TaskInformation instance with updated attributes
     */
    public TaskInformation withAttributes(Map<String, String> newAttributes) {
        return new TaskInformation(paramSchema, taskID, specificationID,
                                  taskName, taskDocumentation, decompositionID,
                                  newAttributes);
    }

    /**
     * Creates a new TaskInformation with an additional attribute.
     * @param key the attribute key
     * @param value the attribute value
     * @return a new TaskInformation instance with the added attribute
     */
    public TaskInformation withAttribute(String key, String value) {
        Map<String, String> newAttrs = new HashMap<>(attributes);
        newAttrs.put(key, value);
        return withAttributes(newAttrs);
    }

    /**
     * Creates a new TaskInformation with updated task name.
     * @param newTaskName the new task name
     * @return a new TaskInformation instance with updated task name
     */
    public TaskInformation withTaskName(String newTaskName) {
        return new TaskInformation(paramSchema, taskID, specificationID,
                                  newTaskName, taskDocumentation, decompositionID,
                                  attributes);
    }

    /**
     * Creates a new TaskInformation with updated documentation.
     * @param newDocumentation the new documentation
     * @return a new TaskInformation instance with updated documentation
     */
    public TaskInformation withTaskDocumentation(String newDocumentation) {
        return new TaskInformation(paramSchema, taskID, specificationID,
                                  taskName, newDocumentation, decompositionID,
                                  attributes);
    }
}
