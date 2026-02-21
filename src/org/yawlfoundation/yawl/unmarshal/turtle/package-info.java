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

/**
 * Turtle RDF unmarshalling support for YAWL workflow specifications.
 *
 * <p>This package provides support for importing YAWL workflow specifications
 * from Turtle RDF format using Apache Jena as the RDF parser and model handler.
 * Turtle is a human-readable RDF syntax that enables specifications to be
 * represented in a semantic web context.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><b>YTurtleImporter</b> - Main entry point for importing Turtle specifications
 *     with support for files, strings, and input streams</li>
 * </ul>
 *
 * <h2>Ontology Mapping</h2>
 * <p>The importer maps resources from the YAWL ontology (http://www.yawlfoundation.org/yawlschema#)
 * to corresponding YAWL domain classes:
 * <table border="1">
 *   <tr>
 *     <th>RDF Class</th>
 *     <th>YAWL Class</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td>yawls:Specification</td>
 *     <td>YSpecification</td>
 *     <td>Complete workflow specification container</td>
 *   </tr>
 *   <tr>
 *     <td>yawls:WorkflowNet</td>
 *     <td>YNet</td>
 *     <td>Workflow net decomposition (Petri net)</td>
 *   </tr>
 *   <tr>
 *     <td>yawls:Task</td>
 *     <td>YAtomicTask</td>
 *     <td>Atomic task element</td>
 *   </tr>
 *   <tr>
 *     <td>yawls:InputCondition</td>
 *     <td>YInputCondition</td>
 *     <td>Net entry point condition</td>
 *   </tr>
 *   <tr>
 *     <td>yawls:OutputCondition</td>
 *     <td>YOutputCondition</td>
 *     <td>Net exit point condition</td>
 *   </tr>
 *   <tr>
 *     <td>yawls:Condition</td>
 *     <td>YCondition</td>
 *     <td>Intermediate condition (place)</td>
 *   </tr>
 *   <tr>
 *     <td>yawls:FlowInto</td>
 *     <td>YFlow</td>
 *     <td>Control flow edge between net elements</td>
 *   </tr>
 * </table>
 * </p>
 *
 * <h2>Metadata Support</h2>
 * <p>Specifications can include Dublin Core metadata properties that are
 * automatically mapped to YMetaData fields:
 * <ul>
 *   <li>dcterms:title → YMetaData.title</li>
 *   <li>dcterms:creator → YMetaData.creators</li>
 *   <li>dcterms:description → YMetaData.description</li>
 *   <li>dcterms:created → YMetaData.created</li>
 * </ul>
 * </p>
 *
 * <h2>Control Flow Patterns</h2>
 * <p>The importer supports YAWL's three primary control flow patterns for
 * task joins and splits:
 * <ul>
 *   <li><b>AND</b> - Parallel execution (all branches must complete)</li>
 *   <li><b>XOR</b> - Exclusive choice (exactly one branch executes)</li>
 *   <li><b>OR</b> - Multi-choice (one or more branches may execute)</li>
 * </ul>
 * </p>
 *
 * <h2>Flow Properties</h2>
 * <p>Flows can be annotated with optional properties:
 * <ul>
 *   <li><b>yawls:xpathExpression</b> - XPath predicate for conditional routing</li>
 *   <li><b>yawls:isDefaultFlow</b> - Flag indicating default flow path</li>
 *   <li><b>yawls:ordering</b> - Evaluation order for predicates</li>
 * </ul>
 * </p>
 *
 * <h2>Example Turtle Specification</h2>
 * <pre>{@code
 * @prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .
 * @prefix dcterms: <http://purl.org/dc/terms/> .
 *
 * <urn:yawl:OrderProcess> a yawls:Specification ;
 *     yawls:uri "OrderProcess" ;
 *     dcterms:title "Order Processing Workflow" ;
 *     dcterms:creator "Process Team" ;
 *     yawls:hasDecomposition <urn:yawl:OrderNet> .
 *
 * <urn:yawl:OrderNet> a yawls:WorkflowNet ;
 *     yawls:id "OrderNet" ;
 *     yawls:hasNetElement <urn:yawl:i_1> ;
 *     yawls:hasNetElement <urn:yawl:ProcessOrder> ;
 *     yawls:hasNetElement <urn:yawl:o_1> .
 *
 * <urn:yawl:i_1> a yawls:InputCondition ;
 *     yawls:id "i_1" .
 *
 * <urn:yawl:ProcessOrder> a yawls:Task ;
 *     yawls:id "ProcessOrder" ;
 *     yawls:joinType "XOR" ;
 *     yawls:splitType "AND" .
 *
 * <urn:yawl:o_1> a yawls:OutputCondition ;
 *     yawls:id "o_1" .
 * }</pre>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // Import from file
 * List<YSpecification> specs = YTurtleImporter.importFromFile("workflow.ttl");
 *
 * // Or import from string
 * String turtleContent = "...";
 * List<YSpecification> specs = YTurtleImporter.importFromString(turtleContent);
 *
 * // Specifications can then be loaded into the YAWL engine
 * for (YSpecification spec : specs) {
 *     engine.loadSpecification(spec);
 * }
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>The importer throws YSyntaxException for various error conditions:
 * <ul>
 *   <li>File I/O errors</li>
 *   <li>Malformed Turtle syntax</li>
 *   <li>Missing required RDF properties</li>
 *   <li>Invalid control type values</li>
 *   <li>Circular or missing flow references</li>
 * </ul>
 * All errors include descriptive messages suitable for logging and user feedback.
 * </p>
 *
 * <h2>Dependencies</h2>
 * <ul>
 *   <li>Apache Jena 4.10.0+ (RDF parsing and modeling)</li>
 *   <li>YAWL Domain Model (yawl-elements)</li>
 *   <li>YAWL Exceptions (YSyntaxException)</li>
 * </ul>
 *
 * @author Claude (YAWL Foundation)
 * @since YAWL 6.0
 * @see org.yawlfoundation.yawl.unmarshal.turtle.YTurtleImporter
 * @see org.yawlfoundation.yawl.elements.YSpecification
 * @see org.yawlfoundation.yawl.elements.YNet
 */
package org.yawlfoundation.yawl.unmarshal.turtle;
