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
 * Thin Java facade over the data-modelling-sdk WebAssembly module (v2.3.0).
 *
 * <p>Entry point: {@link org.yawlfoundation.yawl.datamodelling.DataModellingBridge}.</p>
 *
 * <p>All schema computation runs inside {@code data_modelling_wasm_bg.wasm} via
 * GraalJS+WASM polyglot. The Java layer is a 1:1 mapping of WASM exports to
 * typed Java methods with JSON string returns.</p>
 *
 * <h2>Supported operations</h2>
 * <ul>
 *   <li>Schema import: ODCS, SQL, Avro, JSON Schema, Protobuf, CADS, ODPS, BPMN, DMN, OpenAPI</li>
 *   <li>Schema export: ODCS, SQL, BPMN, DMN, Markdown, PDF</li>
 *   <li>Format conversion: universal ODCS converter, OpenAPI-to-ODCS</li>
 *   <li>Workspace and domain management</li>
 *   <li>Decision records (MADR format)</li>
 *   <li>Knowledge base articles</li>
 *   <li>Sketches (Excalidraw)</li>
 *   <li>Validation: ODPS, naming conventions, circular dependencies</li>
 * </ul>
 *
 * <p>WASM source: github.com/OffeneDatenmodellierung/data-modelling-sdk v2.3.0 (MIT)</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.datamodelling;
