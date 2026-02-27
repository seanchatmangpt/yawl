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
 * Petri net models and data structures for process mining applications.
 *
 * <p>This package provides core data models and representations for Petri nets
 * used in process mining, with support for XES event logs and semantic analysis.</p>
 *
 * <h2>Petri Net Components</h2>
 * <ul>
 *   <li><b>Places</b> - Represent system states or conditions</li>
 *   <li><b>Transitions</b> - Represent events or actions that change system state</li>
 *   <li><b>Arcs</b> - Connect places and transitions with direction and weight</li>
 *   <li><b>Markings</b> - Token distributions across places</li>
 *   <li><b>Workitems</b> - Active process instances and their states</li>
 * </ul>
 *
 * <h2>Model Features</h2>
 * <ul>
 *   <li>Complete Petri net semantics with firing rules</li>
 *   <li>Support for colored Petri nets for enhanced modeling</li>
 *   <li>Net decomposition and modular construction</li>
 *   <li>Reachability analysis and state space exploration</li>
 *   <li>Performance metrics and analysis</li>
 * </ul>
 *
 * <h2>XES Log Integration</h2>
 * <p>Comprehensive support for XES (eXtensible Event Stream) format:
 *   <ul>
 *     <li>Event log parsing and validation</li>
 *     <li>Trace-based model learning algorithms</li>
 *     <li>Conformance checking and replay analysis</li>
 *     <li>Log filtering and transformation</li>
 *     <li>Log abstraction and generalization</li>
 *   </ul>
 * </p>
 *
 * <h2>Advanced Features</h2>
 * <p>Enhanced modeling capabilities with modern Java 25 features:
 *   <ul>
 *     <li>Immutable models using Java records</li>
 *     <li>Pattern matching for net structure analysis</li>
 *     <li>Sequenced collections for token flow analysis</li>
 *     <li>Virtual threads for concurrent model processing</li>
 *     <li>Stream-based model transformations</li>
 *   </ul>
 * </p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>Place - Representation of Petri net places</li>
 *   <li>Transition - Representation of Petri net transitions</li>
 *   <li>Arc - Connection between places and transitions</li>
 *   <li>Marking - Token distribution state</li>
 *   <li>YawlNet - Complete YAWL Petri net model</li>
 *   <li>XesLog - Event log structure and methods</li>
 *   <li>Workitem - Active process instance tracking</li>
 * </ul>
 *
 * <h2>Performance Optimizations</h2>
 * <p>Designed for efficient processing of large-scale process models:
 *   <ul>
 *     <li>Efficient data structures for large nets</li>
 *     <li>Caching of frequently accessed model elements</li>
 *     <li>Lazy evaluation for expensive operations</li>
 *     <li>Parallel processing for independent operations</li>
 *     <li>Memory-efficient serialization formats</li>
 *   </ul>
 * </p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 * @version 1.0
 */
package org.yawlfoundation.yawl.ggen.mining.model;