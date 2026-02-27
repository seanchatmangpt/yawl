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
 * Format parsers for various process mining and BPM standards.
 *
 * <p>This package provides comprehensive parsing capabilities for industry-standard
 * formats used in process mining, enabling integration with diverse data sources
 * and systems.</p>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 *   <li><b>XES (eXtensible Event Stream)</b> - Standard format for event logs
 *       with full support for extensions, attributes, and traces</li>
 *   <li><b>PNML (Petri Net Markup Language)</b> - XML format for Petri net
 *       representation with visual styling and semantics</li>
 *   <li><b>BPMN (Business Process Model and Notation)</b> - Industry standard
 *       for process modeling with comprehensive element support</li>
 * </ul>
 *
 * <h2>XES Parser Features</h2>
 * <p>Comprehensive XES parsing capabilities:
 *   <ul>
 *     <li>Complete XES specification compliance</li>
 *     <li>Extension and global declaration support</li>
 *     <li>Trace and event attribute validation</li>
 *     <li>Log filtering and transformation</li>
 *     <li>Performance-optimized parsing for large logs</li>
 *   </ul>
 * </p>
 *
 * <h2>PNML Parser Features</h2>
 * <p>Petri Net Markup Language parsing:
 *   <ul>
 *     <li>Complete PNML 2009 support</li>
 *     <li>Visual styling and layout information</li>
 *     <li>Custom extension support</li>
 *     <li>Hierarchical net decomposition</li>
 *     <li>Annotation and documentation parsing</li>
 *   </ul>
 * </p>
 *
 * <h2>BPMN Parser Features</h2>
 * <p>Business Process Model and Notation parsing:
 *   <ul>
 *     <li>BPMN 2.0 specification compliance</li>
 *     <li>Collaboration and choreography diagrams</li>
 *     <li>Swimlane and pool organization</li>
 *     <li>Gateway and event type support</li>
 *     <li>Extension elements and custom properties</li>
 *   </ul>
 * </p>
 *
 * <h2>Parser Architecture</h2>
 * <p>Modular parser architecture with extensible design:
 *   <ul>
 *     <li>Abstract base classes for common parsing patterns</li>
 *     <li>Plugin-based architecture for custom formats</li>
 *     <li>Error recovery and validation mechanisms</li>
 *     <li>Memory-efficient streaming parsers</li>
 *     <li>Caching of frequently accessed elements</li>
 *   </ul>
 * </p>
 *
 * <h2>Error Handling</h2>
 * <p>Robust error handling and recovery:
 *   <ul>
 *     <li>Comprehensive validation rules</li>
 *     <li>Graceful error recovery mechanisms</li>
 *     <li>Detailed error reporting with location information</li>
 *     <li>Partial parsing capabilities</li>
 *     <li>Semantic validation beyond syntax</li>
 *   </ul>
 * </p>
 *
 * <h2>Performance Optimizations</h2>
 * <p>Designed for efficient processing of large files:
 *   <ul>
 *     <li>Stream-based parsing for memory efficiency</li>
 *     <li>Lazy loading of complex elements</li>
 *     <li>Parallel parsing for independent sections</li>
 *     <li>Caching of parsed results</li>
 *     <li>Batch processing capabilities</li>
 *   </ul>
 * </p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 * @version 1.0
 */
package org.yawlfoundation.yawl.ggen.mining.parser;