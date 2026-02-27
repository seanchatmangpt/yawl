/*
 * Copyright 2004-2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Export generators for multiple process modeling and execution formats.
 *
 * <p>This package provides comprehensive export capabilities for process models
 * discovered through the mining framework, enabling integration with various
 * BPM and workflow execution environments.</p>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li><b>YAWL</b> - Native YAWL workflow format for direct execution in
 *       YAWL engine with full support for YAWL constructs and patterns</li>
 *   <li><b>BPEL</b> - Business Process Execution Language for orchestration
 *       in web service environments with WS-BPEL 2.0 compliance</li>
 *   <li><BPMN</b> - Business Process Model and Notation for standard process
 *       modeling with BPMN 2.0 support for collaboration and choreography</li>
 *   <li><b>Terraform</b> - Infrastructure as Code format for deploying
 *       process mining workflows as cloud resources</li>
 * </ul>
 *
 * <h2>Export Features</h2>
 * <ul>
 *   <li>Fidelity preservation - Maintains semantic accuracy during conversion</li>
 *   <li>Format-specific optimizations - Tailored for each target platform</li>
 *   <li>Validation before export - Ensures target format compatibility</li>
 *   <li>Customizable export options - Flexible configuration for each format</li>
 *   <li>Batch processing support - Efficient handling of multiple models</li>
 * </ul>
 *
 * <h2>YAWL Export</h2>
 * <p>Native export to YAWL format with complete feature support:
 *   <ul>
 *     <li>Full YAWL pattern preservation (43+ patterns)</li>
 *     <li>Petri net semantics and markings</li>
 *     <li>Workitem lifecycle management</li>
 *     <li>Resource allocation integration</li>
 *     <li>YAWL engine compatibility</li>
 *   </ul>
 * </p>
 *
 * <h2>BPEL Export</h2>
 * <p>Business Process Execution Language integration:
 *   <ul>
 *     <li>Web service orchestration support</li>
 *     <li>Partner link integration</li>
 *     <li>BPEL 2.0 compliance</li>
 *     <li>Error handling and fault handlers</li>
 *     <li>Correlation set support</li>
 *   </ul>
 * </p>
 *
 * <h2>BPMN Export</h2>
 * <p>Business Process Model and Notation generation:
 *   <ul>
 *     <li>BPMN 2.0 compliant output</li>
 *     <li>Collaboration diagrams support</li>
 *     <li>Swimlane and pool organization</li>
 *     <li>Gateway and event types</li>
 *     <li>Visual styling and annotations</li>
 *   </ul>
 * </p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 * @version 1.0
 */
package org.yawlfoundation.yawl.ggen.mining.generators;