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

package org.yawlfoundation.yawl.schema.turtle;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.unmarshal.YMetaData;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Exports YAWL specifications to Turtle (Terse RDF Triple Language) format.
 *
 * <p>This exporter serializes YSpecification objects to RDF using the YAWL ontology
 * and Dublin Core metadata properties. The output is compatible with Apache Jena
 * and other RDF processors.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * YSpecification spec = ...;
 * String turtle = YTurtleExporter.exportToString(spec);
 *
 * // Or export to file:
 * YTurtleExporter.exportToFile(spec, "output.ttl");
 *
 * // Export multiple specs:
 * List<YSpecification> specs = ...;
 * String turtle = YTurtleExporter.exportToString(specs);
 * </pre>
 *
 * <h2>RDF Structure</h2>
 * The exporter creates the following RDF structure:
 * <ul>
 *   <li>Specification → yawls:Specification with metadata</li>
 *   <li>Decompositions → yawls:WorkflowNet or yawls:WebServiceGateway</li>
 *   <li>Tasks → yawls:Task with join/split types</li>
 *   <li>Conditions → yawls:InputCondition, yawls:OutputCondition, yawls:Condition</li>
 *   <li>Flows → yawls:FlowInto with optional predicates</li>
 * </ul>
 *
 * <h2>Namespace Usage</h2>
 * <ul>
 *   <li>yawls: http://www.yawlfoundation.org/yawlschema#</li>
 *   <li>dcterms: http://purl.org/dc/terms/</li>
 *   <li>rdf: http://www.w3.org/1999/02/22-rdf-syntax-ns#</li>
 *   <li>rdfs: http://www.w3.org/2000/01/rdf-schema#</li>
 *   <li>xsd: http://www.w3.org/2001/XMLSchema#</li>
 * </ul>
 *
 * @author Claude Code
 * @since 6.0.0
 * @see org.yawlfoundation.yawl.elements.YSpecification
 * @see org.apache.jena.rdf.model.Model
 */
public final class YTurtleExporter {
    private static final Logger logger = LogManager.getLogger(YTurtleExporter.class);

    // RDF/Turtle namespace URIs
    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema#";
    private static final String DC_NS = "http://purl.org/dc/terms/";
    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    // RDF properties for Dublin Core metadata
    private static final Property DCTERMS_TITLE = ResourceFactory.createProperty(DC_NS, "title");
    private static final Property DCTERMS_DESCRIPTION = ResourceFactory.createProperty(DC_NS, "description");
    private static final Property DCTERMS_CREATOR = ResourceFactory.createProperty(DC_NS, "creator");
    private static final Property DCTERMS_CREATED = ResourceFactory.createProperty(DC_NS, "created");
    private static final Property DCTERMS_CONTRIBUTOR = ResourceFactory.createProperty(DC_NS, "contributor");
    private static final Property DCTERMS_SUBJECT = ResourceFactory.createProperty(DC_NS, "subject");
    private static final Property DCTERMS_COVERAGE = ResourceFactory.createProperty(DC_NS, "coverage");

    // YAWL ontology properties
    private static final Property YAWLS_ID = ResourceFactory.createProperty(YAWL_NS, "id");
    private static final Property YAWLS_URI = ResourceFactory.createProperty(YAWL_NS, "uri");
    private static final Property YAWLS_HAS_DECOMPOSITION = ResourceFactory.createProperty(YAWL_NS, "hasDecomposition");
    private static final Property YAWLS_HAS_ROOT_NET = ResourceFactory.createProperty(YAWL_NS, "hasRootNet");
    private static final Property YAWLS_HAS_TASK = ResourceFactory.createProperty(YAWL_NS, "hasTask");
    private static final Property YAWLS_HAS_CONDITION = ResourceFactory.createProperty(YAWL_NS, "hasCondition");
    private static final Property YAWLS_HAS_INPUT_CONDITION = ResourceFactory.createProperty(YAWL_NS, "hasInputCondition");
    private static final Property YAWLS_HAS_OUTPUT_CONDITION = ResourceFactory.createProperty(YAWL_NS, "hasOutputCondition");
    private static final Property YAWLS_HAS_FLOW_INTO = ResourceFactory.createProperty(YAWL_NS, "hasFlowInto");
    private static final Property YAWLS_NEXT_ELEMENT = ResourceFactory.createProperty(YAWL_NS, "nextElement");
    private static final Property YAWLS_HAS_PREDICATE = ResourceFactory.createProperty(YAWL_NS, "hasPredicate");
    private static final Property YAWLS_XPATH_EXPRESSION = ResourceFactory.createProperty(YAWL_NS, "xpathExpression");
    private static final Property YAWLS_IS_DEFAULT_FLOW = ResourceFactory.createProperty(YAWL_NS, "isDefaultFlow");
    private static final Property YAWLS_HAS_JOIN = ResourceFactory.createProperty(YAWL_NS, "hasJoin");
    private static final Property YAWLS_HAS_SPLIT = ResourceFactory.createProperty(YAWL_NS, "hasSplit");
    private static final Property YAWLS_CODE = ResourceFactory.createProperty(YAWL_NS, "code");
    private static final Property YAWLS_DECOMPOSES_TO = ResourceFactory.createProperty(YAWL_NS, "decomposesTo");
    private static final Property YAWLS_DOCUMENTATION = ResourceFactory.createProperty(YAWL_NS, "documentation");
    private static final Property YAWLS_LABEL = ResourceFactory.createProperty(YAWL_NS, "label");
    private static final Property YAWLS_ORDERING = ResourceFactory.createProperty(YAWL_NS, "ordering");

    // YAWL class resources
    private static final Resource YAWLS_SPECIFICATION = ResourceFactory.createResource(YAWL_NS + "Specification");
    private static final Resource YAWLS_WORKFLOW_NET = ResourceFactory.createResource(YAWL_NS + "WorkflowNet");
    private static final Resource YAWLS_WEB_SERVICE_GATEWAY = ResourceFactory.createResource(YAWL_NS + "WebServiceGateway");
    private static final Resource YAWLS_TASK = ResourceFactory.createResource(YAWL_NS + "Task");
    private static final Resource YAWLS_CONDITION = ResourceFactory.createResource(YAWL_NS + "Condition");
    private static final Resource YAWLS_INPUT_CONDITION = ResourceFactory.createResource(YAWL_NS + "InputCondition");
    private static final Resource YAWLS_OUTPUT_CONDITION = ResourceFactory.createResource(YAWL_NS + "OutputCondition");
    private static final Resource YAWLS_JOIN = ResourceFactory.createResource(YAWL_NS + "Join");
    private static final Resource YAWLS_SPLIT = ResourceFactory.createResource(YAWL_NS + "Split");
    private static final Resource YAWLS_FLOW_INTO = ResourceFactory.createResource(YAWL_NS + "FlowInto");
    private static final Resource YAWLS_PREDICATE = ResourceFactory.createResource(YAWL_NS + "Predicate");

    // Control type resources
    private static final Resource YAWLS_AND = ResourceFactory.createResource(YAWL_NS + "AND");
    private static final Resource YAWLS_OR = ResourceFactory.createResource(YAWL_NS + "OR");
    private static final Resource YAWLS_XOR = ResourceFactory.createResource(YAWL_NS + "XOR");

    /**
     * Exports a single YSpecification to Turtle format as a String.
     *
     * @param spec the specification to export
     * @return Turtle RDF string representation
     * @throws NullPointerException if spec is null
     */
    public static String exportToString(YSpecification spec) {
        Objects.requireNonNull(spec, "YSpecification cannot be null");
        return exportToString(List.of(spec));
    }

    /**
     * Exports a single YSpecification to a Turtle file.
     *
     * @param spec the specification to export
     * @param filePath the output file path
     * @throws NullPointerException if spec or filePath is null
     * @throws IOException if file writing fails
     */
    public static void exportToFile(YSpecification spec, String filePath) throws IOException {
        Objects.requireNonNull(spec, "YSpecification cannot be null");
        Objects.requireNonNull(filePath, "File path cannot be null");

        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());

        try (OutputStream out = Files.newOutputStream(path)) {
            exportToStream(spec, out);
        }
    }

    /**
     * Exports a single YSpecification to an output stream in Turtle format.
     *
     * @param spec the specification to export
     * @param out the output stream
     * @throws NullPointerException if spec or out is null
     * @throws IOException if stream writing fails
     */
    public static void exportToStream(YSpecification spec, OutputStream out) throws IOException {
        Objects.requireNonNull(spec, "YSpecification cannot be null");
        Objects.requireNonNull(out, "OutputStream cannot be null");

        Model model = createModel(List.of(spec));
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            model.write(writer, "TURTLE");
            writer.flush();
        }
    }

    /**
     * Exports multiple YSpecifications to Turtle format as a String.
     *
     * @param specs the specifications to export
     * @return Turtle RDF string representation
     * @throws NullPointerException if specs is null or contains null elements
     */
    public static String exportToString(List<YSpecification> specs) {
        Objects.requireNonNull(specs, "Specification list cannot be null");

        Model model = createModel(specs);
        StringBuilder sb = new StringBuilder();
        try (OutputStreamWriter writer = new OutputStreamWriter(new java.io.ByteArrayOutputStream())) {
            // Write to a ByteArrayOutputStream first to capture output
            var baos = new java.io.ByteArrayOutputStream();
            try (OutputStreamWriter w = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
                model.write(w, "TURTLE");
                w.flush();
                sb.append(baos.toString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            logger.error("Failed to write model to string", e);
            throw new RuntimeException("Turtle serialization failed", e);
        }
        return sb.toString();
    }

    /**
     * Creates an RDF Model from the given specifications.
     *
     * @param specs the specifications to model
     * @return an Apache Jena Model
     */
    private static Model createModel(List<YSpecification> specs) {
        Model model = ModelFactory.createDefaultModel();

        // Set namespace prefixes
        model.setNsPrefix("yawls", YAWL_NS);
        model.setNsPrefix("dcterms", DC_NS);
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");

        // Process each specification
        for (YSpecification spec : specs) {
            if (spec != null) {
                addSpecificationToModel(model, spec);
            }
        }

        return model;
    }

    /**
     * Adds a single specification and its decompositions to the model.
     */
    private static void addSpecificationToModel(Model model, YSpecification spec) {
        String specUri = getSpecificationUri(spec);
        Resource specResource = model.createResource(specUri, YAWLS_SPECIFICATION);

        // Add specification metadata
        if (!StringUtil.isNullOrEmpty(spec.getURI())) {
            specResource.addProperty(YAWLS_URI, spec.getURI());
        }

        if (!StringUtil.isNullOrEmpty(spec.getName())) {
            specResource.addProperty(DCTERMS_TITLE, spec.getName());
        }

        if (!StringUtil.isNullOrEmpty(spec.getDocumentation())) {
            specResource.addProperty(DCTERMS_DESCRIPTION, spec.getDocumentation());
        }

        // Add Dublin Core metadata from YMetaData
        YMetaData metaData = spec.getMetaData();
        if (metaData != null) {
            addMetadata(specResource, metaData);
        }

        // Add decompositions
        Collection<YDecomposition> decompositions = spec.getDecompositions();
        if (decompositions != null) {
            for (YDecomposition decomp : decompositions) {
                if (decomp != null) {
                    addDecompositionToModel(model, specResource, decomp, spec.getRootNet() == decomp);
                }
            }
        }

        // Add root net reference
        YNet rootNet = spec.getRootNet();
        if (rootNet != null) {
            String rootNetUri = getNetUri(spec, rootNet);
            Resource rootNetResource = model.getResource(rootNetUri);
            specResource.addProperty(YAWLS_HAS_ROOT_NET, rootNetResource);
        }
    }

    /**
     * Adds metadata from YMetaData to the specification resource.
     */
    private static void addMetadata(Resource specResource, YMetaData metaData) {
        if (!StringUtil.isNullOrEmpty(metaData.getTitle())) {
            specResource.addProperty(DCTERMS_TITLE, metaData.getTitle());
        }

        if (!StringUtil.isNullOrEmpty(metaData.getDescription())) {
            specResource.addProperty(DCTERMS_DESCRIPTION, metaData.getDescription());
        }

        for (String creator : metaData.getCreators()) {
            if (!StringUtil.isNullOrEmpty(creator)) {
                specResource.addProperty(DCTERMS_CREATOR, creator);
            }
        }

        for (String contributor : metaData.getContributors()) {
            if (!StringUtil.isNullOrEmpty(contributor)) {
                specResource.addProperty(DCTERMS_CONTRIBUTOR, contributor);
            }
        }

        for (String subject : metaData.getSubjects()) {
            if (!StringUtil.isNullOrEmpty(subject)) {
                specResource.addProperty(DCTERMS_SUBJECT, subject);
            }
        }

        if (!StringUtil.isNullOrEmpty(metaData.getCoverage())) {
            specResource.addProperty(DCTERMS_COVERAGE, metaData.getCoverage());
        }
    }

    /**
     * Adds a decomposition (net or service gateway) to the model.
     */
    private static void addDecompositionToModel(Model model, Resource specResource,
                                               YDecomposition decomp, boolean isRootNet) {
        if (decomp instanceof YNet net) {
            addNetToModel(model, specResource, net, isRootNet);
        } else {
            // Handle WebServiceGateway or other decomposition types
            String decompUri = getDecompositionUri(decomp);
            Resource decompResource = model.createResource(decompUri, YAWLS_WEB_SERVICE_GATEWAY);

            if (!StringUtil.isNullOrEmpty(decomp.getID())) {
                decompResource.addProperty(YAWLS_ID, decomp.getID());
            }

            specResource.addProperty(YAWLS_HAS_DECOMPOSITION, decompResource);
        }
    }

    /**
     * Adds a workflow net and all its elements to the model.
     */
    private static void addNetToModel(Model model, Resource specResource, YNet net, boolean isRootNet) {
        String netUri = getNetUri(getSpecificationFromNet(net), net);
        Resource netResource = model.createResource(netUri, YAWLS_WORKFLOW_NET);

        // Add net metadata
        if (!StringUtil.isNullOrEmpty(net.getID())) {
            netResource.addProperty(YAWLS_ID, net.getID());
        }

        if (!StringUtil.isNullOrEmpty(net.getName())) {
            netResource.addProperty(YAWLS_LABEL, net.getName());
        }

        if (!StringUtil.isNullOrEmpty(net.getDocumentation())) {
            netResource.addProperty(YAWLS_DOCUMENTATION, net.getDocumentation());
        }

        specResource.addProperty(YAWLS_HAS_DECOMPOSITION, netResource);

        // Add input condition
        YInputCondition inputCond = net.getInputCondition();
        if (inputCond != null) {
            Resource inputCondResource = addConditionToModel(model, netResource, inputCond, YAWLS_INPUT_CONDITION);
            netResource.addProperty(YAWLS_HAS_INPUT_CONDITION, inputCondResource);
        }

        // Add output condition
        YOutputCondition outputCond = net.getOutputCondition();
        if (outputCond != null) {
            Resource outputCondResource = addConditionToModel(model, netResource, outputCond, YAWLS_OUTPUT_CONDITION);
            netResource.addProperty(YAWLS_HAS_OUTPUT_CONDITION, outputCondResource);
        }

        // Add tasks
        List<YTask> tasks = net.getNetTasks();
        if (tasks != null) {
            for (YTask task : tasks) {
                addTaskToModel(model, netResource, task, net);
            }
        }

        // Add all flows
        Map<String, YExternalNetElement> allElements = net.getNetElements();
        if (allElements != null) {
            for (YExternalNetElement elem : allElements.values()) {
                // Skip input/output conditions (already added)
                if (!(elem instanceof YInputCondition) && !(elem instanceof YOutputCondition)) {
                    if (elem instanceof YCondition cond) {
                        addConditionToModel(model, netResource, cond, YAWLS_CONDITION);
                    }
                }
                // Add flows from all elements
                addFlowsFromElement(model, netResource, elem);
            }
        }
    }

    /**
     * Adds a condition to the model.
     */
    private static Resource addConditionToModel(Model model, Resource netResource, YCondition cond, Resource type) {
        String condUri = getElementUri(cond.getNet(), cond.getID());
        Resource condResource = model.createResource(condUri, type);

        if (!StringUtil.isNullOrEmpty(cond.getID())) {
            condResource.addProperty(YAWLS_ID, cond.getID());
        }

        if (!StringUtil.isNullOrEmpty(cond.getName())) {
            condResource.addProperty(YAWLS_LABEL, cond.getName());
        }

        netResource.addProperty(YAWLS_HAS_CONDITION, condResource);
        return condResource;
    }

    /**
     * Adds a task to the model with join/split configuration.
     */
    private static void addTaskToModel(Model model, Resource netResource, YTask task, YNet net) {
        String taskUri = getElementUri(net, task.getID());
        Resource taskResource = model.createResource(taskUri, YAWLS_TASK);

        // Add task metadata
        if (!StringUtil.isNullOrEmpty(task.getID())) {
            taskResource.addProperty(YAWLS_ID, task.getID());
        }

        if (!StringUtil.isNullOrEmpty(task.getName())) {
            taskResource.addProperty(YAWLS_LABEL, task.getName());
        }

        if (!StringUtil.isNullOrEmpty(task.getDocumentation())) {
            taskResource.addProperty(YAWLS_DOCUMENTATION, task.getDocumentation());
        }

        // Add join type
        int joinType = task.getJoinType();
        Resource joinResource = model.createResource(taskUri + "#join", YAWLS_JOIN);
        joinResource.addProperty(YAWLS_CODE, getControlTypeResource(joinType));
        taskResource.addProperty(YAWLS_HAS_JOIN, joinResource);

        // Add split type
        int splitType = task.getSplitType();
        Resource splitResource = model.createResource(taskUri + "#split", YAWLS_SPLIT);
        splitResource.addProperty(YAWLS_CODE, getControlTypeResource(splitType));
        taskResource.addProperty(YAWLS_HAS_SPLIT, splitResource);

        // Add decomposition reference if composite
        YDecomposition decomp = task.getDecompositionPrototype();
        if (decomp != null) {
            String decompUri = getDecompositionUri(decomp);
            Resource decompResource = model.getResource(decompUri);
            taskResource.addProperty(YAWLS_DECOMPOSES_TO, decompResource);
        }

        netResource.addProperty(YAWLS_HAS_TASK, taskResource);
    }

    /**
     * Adds flows from an element to the model.
     */
    private static void addFlowsFromElement(Model model, Resource netResource, YExternalNetElement element) {
        String sourceUri = getElementUri(null, element.getID());

        Set<YFlow> postset = element.getPostsetFlows();
        if (postset != null) {
            for (YFlow flow : postset) {
                YExternalNetElement nextElem = flow.getNextElement();
                if (nextElem != null) {
                    String nextUri = getElementUri(null, nextElem.getID());

                    String flowUri = sourceUri + "_to_" + nextUri;
                    Resource flowResource = model.createResource(flowUri, YAWLS_FLOW_INTO);

                    // Link from source to flow
                    Resource sourceResource = model.getResource(sourceUri);
                    sourceResource.addProperty(YAWLS_HAS_FLOW_INTO, flowResource);

                    // Add next element reference
                    Resource nextResource = model.getResource(nextUri);
                    flowResource.addProperty(YAWLS_NEXT_ELEMENT, nextResource);

                    // Add predicate if present
                    if (!StringUtil.isNullOrEmpty(flow.getXpathPredicate())) {
                        String predicateUri = flowUri + "#predicate";
                        Resource predicateResource = model.createResource(predicateUri, YAWLS_PREDICATE);
                        predicateResource.addProperty(YAWLS_XPATH_EXPRESSION, flow.getXpathPredicate());

                        if (flow.getEvalOrdering() != null) {
                            predicateResource.addProperty(YAWLS_ORDERING, flow.getEvalOrdering().toString());
                        }

                        flowResource.addProperty(YAWLS_HAS_PREDICATE, predicateResource);
                    }

                    // Add default flow flag if true
                    if (flow.isDefaultFlow()) {
                        flowResource.addProperty(YAWLS_IS_DEFAULT_FLOW, "true");
                    }
                }
            }
        }
    }

    /**
     * Gets the appropriate control type resource for a control code.
     */
    private static Resource getControlTypeResource(int controlType) {
        return switch (controlType) {
            case YTask._AND -> YAWLS_AND;
            case YTask._OR -> YAWLS_OR;
            case YTask._XOR -> YAWLS_XOR;
            default -> YAWLS_XOR; // Default to XOR if unknown
        };
    }

    /**
     * Generates a URI for a specification.
     */
    private static String getSpecificationUri(YSpecification spec) {
        if (!StringUtil.isNullOrEmpty(spec.getURI())) {
            return "urn:yawl:" + spec.getURI();
        }
        return "urn:yawl:spec-" + System.identityHashCode(spec);
    }

    /**
     * Generates a URI for a net.
     */
    private static String getNetUri(YSpecification spec, YNet net) {
        String specUri = getSpecificationUri(spec);
        String netId = !StringUtil.isNullOrEmpty(net.getID()) ? net.getID() : "root-net";
        return specUri + "#net-" + netId;
    }

    /**
     * Generates a URI for a decomposition.
     */
    private static String getDecompositionUri(YDecomposition decomp) {
        if (decomp instanceof YNet net) {
            return "urn:yawl:decomposition-" + net.getID();
        }
        return "urn:yawl:decomposition-" + decomp.getID();
    }

    /**
     * Generates a URI for a net element.
     */
    private static String getElementUri(YNet net, String elementId) {
        if (net != null) {
            YSpecification spec = net.getSpecification();
            if (spec != null) {
                String netUri = getNetUri(spec, net);
                return netUri + "#element-" + elementId;
            }
        }
        return "urn:yawl:element-" + elementId;
    }

    /**
     * Extracts YSpecification from a resource URI.
     */
    private static YSpecification getSpecificationFromNet(YNet net) {
        return net.getSpecification();
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private YTurtleExporter() {
        throw new UnsupportedOperationException("YTurtleExporter cannot be instantiated");
    }
}
