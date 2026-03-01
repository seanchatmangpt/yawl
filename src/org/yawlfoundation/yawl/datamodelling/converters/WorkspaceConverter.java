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

package org.yawlfoundation.yawl.datamodelling.converters;

import org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace;

/**
 * Converter for workspace JSON ↔ typed {@link DataModellingWorkspace} objects.
 *
 * <p>Provides bidirectional conversion between raw JSON strings returned by
 * {@link org.yawlfoundation.yawl.datamodelling.DataModellingBridge} methods
 * and type-safe domain objects.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Parse ODCS YAML to workspace via bridge
 * String workspaceJson = bridge.parseOdcsYaml(yamlContent);
 *
 * // Convert JSON to typed model
 * DataModellingWorkspace ws = WorkspaceConverter.fromJson(workspaceJson);
 *
 * // Modify the workspace (type-safe)
 * ws.getTables().forEach(t -> System.out.println(t.getName()));
 *
 * // Convert back to JSON for export
 * String exportJson = WorkspaceConverter.toJson(ws);
 * String yaml = bridge.exportOdcsYamlV2(exportJson);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WorkspaceConverter {

    private WorkspaceConverter() {
        // Utility class, no instantiation
    }

    /**
     * Parses workspace JSON into a typed DataModellingWorkspace object.
     *
     * @param json  workspace JSON string; must not be null
     * @return typed workspace; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON parsing fails
     */
    public static DataModellingWorkspace fromJson(String json) {
        return JsonObjectMapper.parseJson(json, DataModellingWorkspace.class);
    }

    /**
     * Serializes a DataModellingWorkspace to JSON string.
     *
     * @param workspace  the workspace to serialize; must not be null
     * @return JSON string; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON serialization fails
     */
    public static String toJson(DataModellingWorkspace workspace) {
        return JsonObjectMapper.toJson(workspace);
    }

    /**
     * Creates a new empty workspace builder with default ID.
     *
     * @return a new builder; never null
     */
    public static DataModellingWorkspace.Builder newBuilder() {
        return DataModellingWorkspace.builder();
    }
}
