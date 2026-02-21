/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
 * YAWL Turtle (RDF) Export Support.
 *
 * <p>This package provides functionality to serialize YAWL specifications to Turtle
 * (Terse RDF Triple Language) format, enabling semantic web integration and linked data
 * publication of workflow specifications.</p>
 *
 * <h2>Overview</h2>
 * The package contains utilities for exporting YSpecification objects to RDF using the
 * YAWL ontology and Dublin Core metadata standards. This enables:
 * <ul>
 *   <li>Semantic representation of workflow specifications</li>
 *   <li>Integration with SPARQL query engines</li>
 *   <li>Linked data publication</li>
 *   <li>Knowledge base integration</li>
 *   <li>Workflow interoperability</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * // Export single specification to string
 * YSpecification spec = loadSpecification("order-process.yawl");
 * String turtle = YTurtleExporter.exportToString(spec);
 *
 * // Export to file
 * YTurtleExporter.exportToFile(spec, "order-process.ttl");
 *
 * // Export multiple specifications
 * List<YSpecification> specs = loadAllSpecifications();
 * String turtleMulti = YTurtleExporter.exportToString(specs);
 *
 * // Export to stream
 * try (OutputStream out = Files.newOutputStream(Path.of("spec.ttl"))) {
 *     YTurtleExporter.exportToStream(spec, out);
 * }
 * </pre>
 *
 * <h2>RDF Output Structure</h2>
 * The exporter produces RDF that captures:
 * <ul>
 *   <li><b>Specification</b> - yawls:Specification with Dublin Core metadata</li>
 *   <li><b>Decompositions</b> - yawls:WorkflowNet and yawls:WebServiceGateway</li>
 *   <li><b>Tasks</b> - yawls:Task with join/split control flow types</li>
 *   <li><b>Conditions</b> - yawls:InputCondition, yawls:OutputCondition, yawls:Condition</li>
 *   <li><b>Flows</b> - yawls:FlowInto with optional XPath predicates</li>
 *   <li><b>Metadata</b> - Title, creator, subject, coverage via Dublin Core terms</li>
 * </ul>
 *
 * <h2>Ontology Alignment</h2>
 * The exporter uses the YAWL ontology (http://www.yawlfoundation.org/yawlschema#)
 * which formalizes YAWL specifications using:
 * <ul>
 *   <li><b>Petri Net semantics</b> - Conditions as places, tasks as transitions</li>
 *   <li><b>YAWL control flow patterns</b> - AND, XOR, OR joins and splits</li>
 *   <li><b>Dublin Core metadata</b> - Standard description properties</li>
 *   <li><b>OWL 2 DL</b> - Formal constraints and class hierarchy</li>
 * </ul>
 *
 * <h2>Namespace Prefixes</h2>
 * The generated Turtle includes the following namespace declarations:
 * <table border="1">
 *   <tr><th>Prefix</th><th>URI</th><th>Usage</th></tr>
 *   <tr>
 *     <td>yawls</td>
 *     <td>http://www.yawlfoundation.org/yawlschema#</td>
 *     <td>YAWL classes and properties</td>
 *   </tr>
 *   <tr>
 *     <td>dcterms</td>
 *     <td>http://purl.org/dc/terms/</td>
 *     <td>Dublin Core metadata (title, creator, etc.)</td>
 *   </tr>
 *   <tr>
 *     <td>rdf</td>
 *     <td>http://www.w3.org/1999/02/22-rdf-syntax-ns#</td>
 *     <td>RDF type declarations</td>
 *   </tr>
 *   <tr>
 *     <td>rdfs</td>
 *     <td>http://www.w3.org/2000/01/rdf-schema#</td>
 *     <td>RDFS labels and comments</td>
 *   </tr>
 *   <tr>
 *     <td>xsd</td>
 *     <td>http://www.w3.org/2001/XMLSchema#</td>
 *     <td>XSD datatype definitions</td>
 *   </tr>
 * </table>
 *
 * <h2>Example Turtle Output</h2>
 * <pre>
 * @prefix yawls: <http://www.yawlfoundation.org/yawlschema#> .
 * @prefix dcterms: <http://purl.org/dc/terms/> .
 * @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
 *
 * <urn:yawl:order-process>
 *     a yawls:Specification ;
 *     yawls:uri "http://example.org/specs/order-process" ;
 *     dcterms:title "Order Processing Workflow" ;
 *     dcterms:description "Main workflow for processing customer orders" ;
 *     dcterms:creator "Process Team" ;
 *     yawls:hasRootNet <urn:yawl:order-process#net-OrderNet> ;
 *     yawls:hasDecomposition <urn:yawl:order-process#net-OrderNet> ;
 * .
 *
 * <urn:yawl:order-process#net-OrderNet>
 *     a yawls:WorkflowNet ;
 *     yawls:id "OrderNet" ;
 *     yawls:hasInputCondition <urn:yawl:order-process#net-OrderNet#element-input> ;
 *     yawls:hasOutputCondition <urn:yawl:order-process#net-OrderNet#element-output> ;
 *     yawls:hasTask <urn:yawl:order-process#net-OrderNet#element-CreateOrder> ;
 * .
 * </pre>
 *
 * <h2>Implementation Notes</h2>
 * <ul>
 *   <li>Uses Apache Jena 4.10.0 for RDF model creation and serialization</li>
 *   <li>All exports are thread-safe (stateless utility class)</li>
 *   <li>Null specifications are skipped gracefully</li>
 *   <li>UTF-8 encoding is used for all file operations</li>
 *   <li>Generated RDF is valid Turtle and compatible with all RDF processors</li>
 *   <li>URIs follow YAWL conventions: urn:yawl:&lt;spec-identifier&gt;</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.elements.YSpecification
 * @see org.yawlfoundation.yawl.elements.YNet
 * @see org.yawlfoundation.yawl.elements.YTask
 * @see org.apache.jena.rdf.model.Model
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.schema.turtle;
