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
 * Process mining framework for YAWL, supporting industry-standard formats
 * and semantic analysis.
 *
 * <p>This package provides a comprehensive process mining framework that enables
 * discovery, analysis, and optimization of business processes through event log
 * analysis and Petri net models.</p>
 *
 * <h2>Format Support</h2>
 * <ul>
 *   <li><b>XES (eXtensible Event Stream)</b> - Standard format for event logs
 *       with full support for extensions, attributes, and traces</li>
 *   <li><b>PNML (Petri Net Markup Language)</b> - Standard XML format for
 *       representing Petri nets with visual styling and semantics</li>
 *   <li><b>BPMN (Business Process Model and Notation)</b> - Industry-standard
 *       process modeling format with comprehensive support</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Process discovery from event logs using alpha+, Heuristics, and Inductive
 *       Miner algorithms</li>
 *   <li>Petri net validation and conformance checking</li>
 *   <li>Process model optimization and enhancement</li>
 *   <li>Integration with YAWL workflow engine for real-time process execution</li>
 *   <li>AI-powered process analysis using modern Java 25 features</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>The framework follows a modular architecture with separate packages for:
 *   <ul>
 *     <li>AI integration for intelligent validation and optimization</li>
 *     <li>Cloud mining integration for enterprise solutions</li>
 *     <li>Export generators for multiple process formats</li>
 *     <li>Petri net models for process representation</li>
 *     <li>Format parsers for various industry standards</li>
 *     <li>RDF/AST conversion for semantic analysis</li>
 *   </ul>
 * </p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 * @version 1.0
 */
package org.yawlfoundation.yawl.ggen.mining;