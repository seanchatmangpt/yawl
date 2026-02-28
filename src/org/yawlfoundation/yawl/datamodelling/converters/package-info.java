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

/**
 * Type converters for DataModellingBridge JSON ↔ typed objects.
 *
 * <p>This package provides bidirectional converters between raw JSON strings
 * returned by {@link org.yawlfoundation.yawl.datamodelling.DataModellingBridge}
 * and type-safe domain model classes in {@code models} package.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Parse ODCS YAML to workspace JSON via bridge
 * String workspaceJson = bridge.parseOdcsYaml(yamlContent);
 *
 * // Convert JSON to typed model
 * DataModellingWorkspace ws = WorkspaceConverter.fromJson(workspaceJson);
 *
 * // Type-safe operations
 * ws.getTables().forEach(t -> {
 *     System.out.println("Table: " + t.getName());
 *     t.getColumns().forEach(c -> System.out.println("  - " + c.getName()));
 * });
 *
 * // Convert back to JSON for export
 * String json = WorkspaceConverter.toJson(ws);
 * String yaml = bridge.exportOdcsYamlV2(json);
 * }</pre>
 *
 * <h2>Available Converters</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper} - Core Jackson configuration</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.WorkspaceConverter} - Workspace ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.TableConverter} - Table ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.ColumnConverter} - Column ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.RelationshipConverter} - Relationship ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.DomainConverter} - Domain ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.DecisionConverter} - Decision (MADR) ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.ArticleConverter} - Knowledge Article ↔ JSON</li>
 *   <li>{@link org.yawlfoundation.yawl.datamodelling.converters.SketchConverter} - Sketch (Excalidraw) ↔ JSON</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.datamodelling.converters;
