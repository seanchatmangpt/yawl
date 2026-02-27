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
 * RDF/AST conversion and semantic analysis for process mining models.
 *
 * <p>This package provides RDF-based semantic representation of process mining
 * artifacts, enabling advanced querying, analysis, and integration with semantic
 * web technologies.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><b>AST Conversion</b> - Tree-sitter Java parser integration for accurate
 *       source code analysis</li>
 *   <li><b>RDF Modeling</b> - Semantic representation of process models and logs</li>
 *   <li><b>SPARQL Queries</b> - Advanced query capabilities for model analysis</li>
 *   <li><b>Semantic Analysis</b> - Intelligent model comparison and transformation</li>
 * </ul>
 *
 * <h2>AST Conversion Features</h2>
 * <p>Tree-sitter based parsing capabilities:
 *   <ul>
 *     <li>Accurate Java source code parsing with full syntax support</li>
 *     <li>Incremental parsing for efficient updates</li>
 *     <li>Multi-language support for process modeling languages</li>
 *     <li>Error recovery and graceful degradation</li>
 *     <li>AST node traversal and transformation</li>
 *   </ul>
 * </p>
 *
 * <h2>RDF Modeling</h2>
 * <p>Semantic web integration:
 *   <ul>
 *     <li>Process model ontologies and vocabularies</li>
 *     <li>Event log semantic representation</li>
 *     <li>Petri net RDF serialization</li>
 *     <li>Interoperability with linked data sources</li>
 *     <li>OWL 2 DL reasoning support</li>
 *   </ul>
 * </p>
 *
 * <h2>SPARQL Query Framework</h2>
 * <p>Advanced querying capabilities:
 *   <ul>
 *     <li>Process pattern discovery through SPARQL</li>
 *     <li>Model comparison and difference analysis</li>
 *     <li>Conformance checking queries</li>
 *     <li>Optimization opportunity detection</li>
 *     <li>Knowledge base integration</li>
 *   </ul>
 * </p>
 *
 * <h2>Java 25 Integration</h2>
 * <p>Leverages modern Java features for enhanced functionality:
 *   <ul>
 *     <li>Virtual threads for concurrent RDF operations</li>
 *     <li>Records for immutable RDF statements</li>
 *     <li>Pattern matching for query result processing</li>
 *     <li>Sequenced collections for ordered results</li>
 *     <li>Stream APIs for data transformation pipelines</li>
 *   </ul>
 * </p>
 *
 * <h2>Query Templates</h2>
 * <p>Pre-built SPARQL query templates for common mining tasks:
 *   <ul>
 *     <li>Process discovery patterns</li>
 *     <li>Conformance checking rules</li>
 *     <li>Performance analysis queries</li>
 *     <li>Optimization recommendations</li>
 *     <li>Quality assessment metrics</li>
 *   </ul>
 * </p>
 *
 * <h2>Performance Optimizations</h2>
 * <p>Efficient semantic processing:
 *   <ul>
 *     <li>RDF model caching and indexing</li>
 *     <li>Query optimization and planning</li>
 *     <li>Incremental updates for dynamic models</li>
 *     <li>Parallel query execution</li>
 *     <li>Memory-efficient serialization</li>
 *   </ul>
 * </p>
 *
 * @since 6.0.0-GA
 * @author YAWL Foundation
 * @version 1.0
 */
package org.yawlfoundation.yawl.ggen.mining.rdf;