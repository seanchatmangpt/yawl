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

package org.yawlfoundation.yawl.unmarshal.turtle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.unmarshal.YMetaData;

/**
 * Importer for YAWL workflow specifications in Turtle RDF format.
 *
 * <p>This class provides methods to import YAWL specifications from Turtle
 * (.ttl) RDF files and convert them to YSpecification objects. It uses
 * Apache Jena to parse RDF triples and maps YAWL ontology resources to
 * YAWL domain objects.</p>
 *
 * <h2>Supported YAWL Ontology Classes</h2>
 * <ul>
 *   <li><b>yawls:Specification</b> - YAWL specification container</li>
 *   <li><b>yawls:WorkflowNet</b> - Workflow net decomposition</li>
 *   <li><b>yawls:Task</b> - Task elements (atomic tasks)</li>
 *   <li><b>yawls:Condition</b> - Condition nodes (places)</li>
 *   <li><b>yawls:InputCondition</b> - Net entry point</li>
 *   <li><b>yawls:OutputCondition</b> - Net exit point</li>
 *   <li><b>yawls:FlowInto</b> - Flow connections between elements</li>
 * </ul>
 *
 * <h2>Metadata Mapping</h2>
 * <p>Turtle specifications may include Dublin Core metadata properties:
 * <ul>
 *   <li><b>dcterms:title</b> - Specification title</li>
 *   <li><b>dcterms:creator</b> - Specification creator(s)</li>
 *   <li><b>dcterms:description</b> - Specification description</li>
 *   <li><b>dcterms:created</b> - Creation date (xsd:date format)</li>
 * </ul>
 * </p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * try {
 *     List<YSpecification> specs = YTurtleImporter.importFromFile("workflow.ttl");
 *     for (YSpecification spec : specs) {
 *         engine.loadSpecification(spec);
 *     }
 * } catch (YSyntaxException e) {
 *     logger.error("Invalid Turtle specification: {}", e.getMessage());
 * }
 * }</pre>
 *
 * @author Claude (YAWL Foundation)
 * @see YSpecification
 * @see YNet
 * @see YTask
 */
public final class YTurtleImporter {

    private static final Logger _log = LogManager.getLogger(YTurtleImporter.class);

    // YAWL ontology namespace
    private static final String YAWLS_NS = "http://www.yawlfoundation.org/yawlschema#";

    // Dublin Core namespaces
    private static final String DCTERMS_NS = "http://purl.org/dc/terms/";
    private static final String DC_NS = "http://purl.org/dc/elements/1.1/";

    // Prevent instantiation
    private YTurtleImporter() {
    }

    /**
     * Imports YAWL specifications from a Turtle file.
     *
     * @param filePath the path to the Turtle file to import
     * @return a list of YSpecification objects parsed from the file
     * @throws YSyntaxException if the file cannot be read, is malformed,
     *         or contains invalid YAWL specifications
     */
    public static List<YSpecification> importFromFile(String filePath) throws YSyntaxException {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            return importFromString(content);
        } catch (IOException e) {
            throw new YSyntaxException("Failed to read Turtle file: " + e.getMessage(), e);
        }
    }

    /**
     * Imports YAWL specifications from a Turtle string.
     *
     * @param turtleContent the Turtle RDF content as a string
     * @return a list of YSpecification objects parsed from the content
     * @throws YSyntaxException if the content is malformed or contains
     *         invalid YAWL specifications
     */
    public static List<YSpecification> importFromString(String turtleContent) throws YSyntaxException {
        try {
            Model model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            org.apache.jena.riot.RDFDataMgr.read(
                model,
                new java.io.StringReader(turtleContent),
                null,
                org.apache.jena.riot.Lang.TURTLE
            );
            return parseSpecifications(model);
        } catch (Exception e) {
            throw new YSyntaxException("Failed to parse Turtle content: " + e.getMessage(), e);
        }
    }

    /**
     * Imports YAWL specifications from an input stream.
     *
     * @param stream the input stream containing Turtle RDF data
     * @return a list of YSpecification objects parsed from the stream
     * @throws YSyntaxException if the stream cannot be read, is malformed,
     *         or contains invalid YAWL specifications
     */
    public static List<YSpecification> importFromStream(InputStream stream) throws YSyntaxException {
        try {
            Model model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
            org.apache.jena.riot.RDFDataMgr.read(
                model,
                stream,
                null,
                org.apache.jena.riot.Lang.TURTLE
            );
            return parseSpecifications(model);
        } catch (Exception e) {
            throw new YSyntaxException("Failed to parse Turtle stream: " + e.getMessage(), e);
        }
    }

    /**
     * Parses all specifications from an RDF model.
     *
     * @param model the Jena RDF model
     * @return list of parsed YSpecification objects
     * @throws YSyntaxException if required elements are missing or invalid
     */
    private static List<YSpecification> parseSpecifications(Model model) throws YSyntaxException {
        List<YSpecification> specifications = new ArrayList<>();

        Resource specClass = model.getResource(YAWLS_NS + "Specification");
        List<Resource> specResources = model.listSubjectsWithProperty(RDF.type, specClass).toList();

        if (specResources.isEmpty()) {
            throw new YSyntaxException("No yawls:Specification resources found in Turtle document");
        }

        for (Resource specResource : specResources) {
            YSpecification spec = parseSpecification(model, specResource);
            specifications.add(spec);
            _log.info("Imported specification: {}", spec.getURI());
        }

        return specifications;
    }

    /**
     * Parses a single specification resource.
     *
     * @param model the Jena RDF model
     * @param specResource the specification resource
     * @return parsed YSpecification
     * @throws YSyntaxException if required properties are missing
     */
    private static YSpecification parseSpecification(Model model, Resource specResource)
            throws YSyntaxException {

        // Extract URI
        String specURI = getStringProperty(model, specResource, "uri");
        if (specURI == null || specURI.trim().isEmpty()) {
            specURI = specResource.getURI();
        }

        YSpecification spec = new YSpecification(specURI);

        // Parse metadata
        YMetaData metadata = parseMetadata(model, specResource);
        spec.setMetaData(metadata);

        // Create root net (find first WorkflowNet marked as root)
        YNet rootNet = findAndParseRootNet(model, specResource, spec);
        if (rootNet == null) {
            throw new YSyntaxException("Specification " + specURI + " has no root workflow net");
        }

        spec.setRootNet(rootNet);

        // Parse additional decompositions
        parseDecompositions(model, specResource, spec);

        return spec;
    }

    /**
     * Finds and parses the root workflow net for a specification.
     *
     * @param model the RDF model
     * @param specResource the specification resource
     * @param spec the YSpecification being populated
     * @return the parsed root YNet, or null if none found
     * @throws YSyntaxException if the net is malformed
     */
    private static YNet findAndParseRootNet(Model model, Resource specResource, YSpecification spec)
            throws YSyntaxException {

        Property hasDecomp = model.getProperty(YAWLS_NS + "hasDecomposition");
        List<Resource> decompositions = model.listObjectsOfProperty(specResource, hasDecomp)
            .filterKeep(RDFNode::isResource)
            .mapWith(RDFNode::asResource)
            .toList();

        Resource workflowNetClass = model.getResource(YAWLS_NS + "WorkflowNet");

        for (Resource decomp : decompositions) {
            if (decomp.hasProperty(RDF.type, workflowNetClass)) {
                return parseWorkflowNet(model, decomp, spec);
            }
        }

        // If no explicit root marked, use the first WorkflowNet
        if (!decompositions.isEmpty()) {
            for (Resource decomp : decompositions) {
                Statement typeStmt = decomp.getProperty(RDF.type);
                if (typeStmt != null && typeStmt.getObject().isResource()) {
                    Resource type = typeStmt.getResource();
                    if (type.getLocalName().equals("WorkflowNet")) {
                        return parseWorkflowNet(model, decomp, spec);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Parses additional decompositions (sub-nets) referenced by the specification.
     *
     * @param model the RDF model
     * @param specResource the specification resource
     * @param spec the YSpecification
     * @throws YSyntaxException if a decomposition is malformed
     */
    private static void parseDecompositions(Model model, Resource specResource, YSpecification spec)
            throws YSyntaxException {

        Property hasDecomp = model.getProperty(YAWLS_NS + "hasDecomposition");
        List<Resource> decompositions = model.listObjectsOfProperty(specResource, hasDecomp)
            .filterKeep(RDFNode::isResource)
            .mapWith(RDFNode::asResource)
            .toList();

        Resource workflowNetClass = model.getResource(YAWLS_NS + "WorkflowNet");

        for (Resource decomp : decompositions) {
            if (decomp.hasProperty(RDF.type, workflowNetClass) && !decomp.equals(spec.getRootNet())) {
                YNet subnet = parseWorkflowNet(model, decomp, spec);
                if (subnet != null) {
                    spec.addDecomposition(subnet);
                }
            }
        }
    }

    /**
     * Parses a workflow net from an RDF resource.
     *
     * @param model the RDF model
     * @param netResource the net resource
     * @param spec the parent specification
     * @return parsed YNet
     * @throws YSyntaxException if the net is malformed
     */
    private static YNet parseWorkflowNet(Model model, Resource netResource, YSpecification spec)
            throws YSyntaxException {

        String netId = getStringProperty(model, netResource, "id");
        if (netId == null || netId.trim().isEmpty()) {
            netId = netResource.getLocalName();
        }
        if (netId == null || netId.trim().isEmpty()) {
            throw new YSyntaxException("Workflow net has no id");
        }

        YNet net = new YNet(netId, spec);

        // Parse input and output conditions
        YInputCondition inputCond = parseInputCondition(model, netResource, net);
        YOutputCondition outputCond = parseOutputCondition(model, netResource, net);

        if (inputCond != null) {
            net.setInputCondition(inputCond);
        }
        if (outputCond != null) {
            net.setOutputCondition(outputCond);
        }

        // Parse tasks and intermediate conditions
        parseNetElements(model, netResource, net);

        // Parse flows
        parseFlows(model, netResource, net);

        return net;
    }

    /**
     * Parses the input condition from a net.
     *
     * @param model the RDF model
     * @param netResource the net resource
     * @param net the YNet being populated
     * @return parsed YInputCondition, or null if not found
     */
    private static YInputCondition parseInputCondition(Model model, Resource netResource, YNet net) {
        Resource inputCondClass = model.getResource(YAWLS_NS + "InputCondition");
        Property hasElement = model.getProperty(YAWLS_NS + "hasNetElement");

        List<Resource> elements = model.listObjectsOfProperty(netResource, hasElement)
            .filterKeep(RDFNode::isResource)
            .mapWith(RDFNode::asResource)
            .toList();

        elements = elements.stream()
            .filter(r -> r.hasProperty(RDF.type, inputCondClass))
            .toList();

        if (elements.isEmpty()) {
            return null;
        }

        Resource inputCondResource = elements.get(0);
        String id = getStringProperty(model, inputCondResource, "id");
        if (id == null || id.trim().isEmpty()) {
            id = inputCondResource.getLocalName();
        }

        YInputCondition inputCond = new YInputCondition(id, net);
        return inputCond;
    }

    /**
     * Parses the output condition from a net.
     *
     * @param model the RDF model
     * @param netResource the net resource
     * @param net the YNet being populated
     * @return parsed YOutputCondition, or null if not found
     */
    private static YOutputCondition parseOutputCondition(Model model, Resource netResource, YNet net) {
        Resource outputCondClass = model.getResource(YAWLS_NS + "OutputCondition");
        Property hasElement = model.getProperty(YAWLS_NS + "hasNetElement");

        List<Resource> elementsTemp = model.listObjectsOfProperty(netResource, hasElement)
            .filterKeep(RDFNode::isResource)
            .mapWith(RDFNode::asResource)
            .toList();

        List<Resource> elements = elementsTemp.stream()
            .filter(r -> r.hasProperty(RDF.type, outputCondClass))
            .toList();

        if (elements.isEmpty()) {
            return null;
        }

        Resource outputCondResource = elements.get(0);
        String id = getStringProperty(model, outputCondResource, "id");
        if (id == null || id.trim().isEmpty()) {
            id = outputCondResource.getLocalName();
        }

        YOutputCondition outputCond = new YOutputCondition(id, net);
        return outputCond;
    }

    /**
     * Parses all tasks and intermediate conditions in a net.
     *
     * @param model the RDF model
     * @param netResource the net resource
     * @param net the YNet being populated
     * @throws YSyntaxException if element parsing fails
     */
    private static void parseNetElements(Model model, Resource netResource, YNet net)
            throws YSyntaxException {

        Resource taskClass = model.getResource(YAWLS_NS + "Task");
        Resource conditionClass = model.getResource(YAWLS_NS + "Condition");
        Property hasElement = model.getProperty(YAWLS_NS + "hasNetElement");

        List<Resource> elements = model.listObjectsOfProperty(netResource, hasElement)
            .filterKeep(RDFNode::isResource)
            .mapWith(RDFNode::asResource)
            .toList();

        for (Resource element : elements) {
            if (element.hasProperty(RDF.type, taskClass)) {
                YAtomicTask task = parseTask(model, element, net);
                net.addNetElement(task);
            } else if (element.hasProperty(RDF.type, conditionClass)) {
                // Skip input/output conditions (handled separately)
                if (!element.hasProperty(RDF.type, model.getResource(YAWLS_NS + "InputCondition"))
                    && !element.hasProperty(RDF.type, model.getResource(YAWLS_NS + "OutputCondition"))) {
                    YCondition condition = parseCondition(model, element, net);
                    net.addNetElement(condition);
                }
            }
        }
    }

    /**
     * Parses a task element.
     *
     * @param model the RDF model
     * @param taskResource the task resource
     * @param net the parent net
     * @return parsed YAtomicTask
     * @throws YSyntaxException if the task is malformed
     */
    private static YAtomicTask parseTask(Model model, Resource taskResource, YNet net)
            throws YSyntaxException {

        String id = getStringProperty(model, taskResource, "id");
        if (id == null || id.trim().isEmpty()) {
            id = taskResource.getLocalName();
        }
        if (id == null || id.trim().isEmpty()) {
            throw new YSyntaxException("Task has no id");
        }

        // Parse join and split types
        String joinType = getStringProperty(model, taskResource, "joinType");
        String splitType = getStringProperty(model, taskResource, "splitType");

        int joinCode = parseControlType(joinType);
        int splitCode = parseControlType(splitType);

        YAtomicTask task = new YAtomicTask(id, joinCode, splitCode, net);

        return task;
    }

    /**
     * Parses a condition element.
     *
     * @param model the RDF model
     * @param conditionResource the condition resource
     * @param net the parent net
     * @return parsed YCondition
     * @throws YSyntaxException if the condition is malformed
     */
    private static YCondition parseCondition(Model model, Resource conditionResource, YNet net)
            throws YSyntaxException {

        String id = getStringProperty(model, conditionResource, "id");
        if (id == null || id.trim().isEmpty()) {
            id = conditionResource.getLocalName();
        }
        if (id == null || id.trim().isEmpty()) {
            throw new YSyntaxException("Condition has no id");
        }

        String label = getStringProperty(model, conditionResource, "label");
        if (label == null || label.trim().isEmpty()) {
            label = id;
        }

        return new YCondition(id, label, net);
    }

    /**
     * Parses all flows between net elements.
     *
     * @param model the RDF model
     * @param netResource the net resource
     * @param net the YNet being populated
     * @throws YSyntaxException if flow parsing fails
     */
    private static void parseFlows(Model model, Resource netResource, YNet net) throws YSyntaxException {
        Property hasElement = model.getProperty(YAWLS_NS + "hasNetElement");
        Property hasFlowInto = model.getProperty(YAWLS_NS + "hasFlowInto");
        Property nextElement = model.getProperty(YAWLS_NS + "nextElement");

        List<Resource> elements = model.listObjectsOfProperty(netResource, hasElement)
            .filterKeep(RDFNode::isResource)
            .mapWith(RDFNode::asResource)
            .toList();

        for (Resource element : elements) {
            List<Resource> flows = model.listObjectsOfProperty(element, hasFlowInto)
                .filterKeep(RDFNode::isResource)
                .mapWith(RDFNode::asResource)
                .toList();

            for (Resource flowResource : flows) {
                RDFNode nextNode = flowResource.getProperty(nextElement).getObject();
                if (nextNode.isResource()) {
                    Resource nextRes = nextNode.asResource();
                    String nextId = getStringProperty(model, nextRes, "id");
                    if (nextId == null || nextId.trim().isEmpty()) {
                        nextId = nextRes.getLocalName();
                    }

                    // Find the source element
                    String sourceId = getStringProperty(model, element, "id");
                    if (sourceId == null || sourceId.trim().isEmpty()) {
                        sourceId = element.getLocalName();
                    }

                    // Look up elements in the net
                    var sourceElement = net.getNetElement(sourceId);
                    var targetElement = net.getNetElement(nextId);

                    if (sourceElement != null && targetElement != null) {
                        YFlow flow = new YFlow(sourceElement, targetElement);

                        // Parse optional predicate
                        Property hasPredicate = model.getProperty(YAWLS_NS + "hasPredicate");
                        Statement predicateStmt = flowResource.getProperty(hasPredicate);
                        if (predicateStmt != null && predicateStmt.getObject().isResource()) {
                            Resource predRes = predicateStmt.getResource();
                            String xpathExpr = getStringProperty(model, predRes, "xpathExpression");
                            if (xpathExpr != null && !xpathExpr.trim().isEmpty()) {
                                flow.setXpathPredicate(xpathExpr);
                            }
                        }

                        // Check for default flow
                        Property isDefaultFlow = model.getProperty(YAWLS_NS + "isDefaultFlow");
                        if (flowResource.hasProperty(isDefaultFlow)) {
                            RDFNode defaultNode = flowResource.getProperty(isDefaultFlow).getObject();
                            if (defaultNode.isLiteral()) {
                                flow.setIsDefaultFlow(defaultNode.asLiteral().getBoolean());
                            }
                        }

                        sourceElement.addPostset(flow);
                    }
                }
            }
        }
    }

    /**
     * Parses metadata from a specification resource.
     *
     * @param model the RDF model
     * @param specResource the specification resource
     * @return parsed YMetaData
     */
    private static YMetaData parseMetadata(Model model, Resource specResource) {
        YMetaData metadata = new YMetaData();

        // Parse Dublin Core properties
        String title = getStringProperty(model, specResource, DCTERMS_NS, "title");
        if (title == null) {
            title = getStringProperty(model, specResource, DC_NS, "title");
        }
        if (title != null && !title.trim().isEmpty()) {
            metadata.setTitle(title);
        }

        String description = getStringProperty(model, specResource, DCTERMS_NS, "description");
        if (description == null) {
            description = getStringProperty(model, specResource, DC_NS, "description");
        }
        if (description != null && !description.trim().isEmpty()) {
            metadata.setDescription(description);
        }

        List<String> creators = getStringListProperty(model, specResource, DCTERMS_NS, "creator");
        if (creators.isEmpty()) {
            creators = getStringListProperty(model, specResource, DC_NS, "creator");
        }
        for (String creator : creators) {
            metadata.addCreator(creator);
        }

        // Parse creation date if present
        String dateStr = getStringProperty(model, specResource, DCTERMS_NS, "created");
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
                metadata.setCreated(date);
            } catch (Exception e) {
                _log.warn("Could not parse creation date: {}", dateStr);
            }
        }

        return metadata;
    }

    /**
     * Gets a string property value from a resource.
     *
     * @param model the RDF model
     * @param resource the resource
     * @param propertyLocalName the local name of the property in YAWLS namespace
     * @return the property value, or null if not found
     */
    private static String getStringProperty(Model model, Resource resource, String propertyLocalName) {
        return getStringProperty(model, resource, YAWLS_NS, propertyLocalName);
    }

    /**
     * Gets a string property value from a resource with explicit namespace.
     *
     * @param model the RDF model
     * @param resource the resource
     * @param namespace the property namespace
     * @param propertyLocalName the local name of the property
     * @return the property value, or null if not found
     */
    private static String getStringProperty(Model model, Resource resource, String namespace,
            String propertyLocalName) {
        Property prop = model.getProperty(namespace + propertyLocalName);
        Statement stmt = resource.getProperty(prop);
        if (stmt != null && stmt.getObject().isLiteral()) {
            return stmt.getLiteral().getString();
        }
        return null;
    }

    /**
     * Gets a list of string property values from a resource.
     *
     * @param model the RDF model
     * @param resource the resource
     * @param namespace the property namespace
     * @param propertyLocalName the local name of the property
     * @return list of property values (empty list if none found)
     */
    private static List<String> getStringListProperty(Model model, Resource resource, String namespace,
            String propertyLocalName) {
        List<String> values = new ArrayList<>();
        Property prop = model.getProperty(namespace + propertyLocalName);
        model.listStatements(resource, prop, (RDFNode) null).forEach(stmt -> {
            if (stmt.getObject().isLiteral()) {
                values.add(stmt.getLiteral().getString());
            }
        });
        return values;
    }

    /**
     * Parses a control type string (AND, XOR, OR) to a YAWL task code.
     *
     * @param typeStr the control type string
     * @return the corresponding YAWL task code (YTask._AND, YTask._XOR, YTask._OR)
     */
    private static int parseControlType(String typeStr) {
        if (typeStr == null) {
            return YAtomicTask._XOR; // Default
        }

        return switch (typeStr.toUpperCase()) {
            case "AND" -> YAtomicTask._AND;
            case "OR" -> YAtomicTask._OR;
            case "XOR" -> YAtomicTask._XOR;
            default -> YAtomicTask._XOR;
        };
    }
}
