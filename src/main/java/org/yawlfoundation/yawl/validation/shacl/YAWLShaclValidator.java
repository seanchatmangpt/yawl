/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation.shacl;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.shacl.Shacl;
import org.apache.jena.shacl.ShapesGraph;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.engine.ValidationEngine;
import org.apache.jena.shacl.engine.ValidationEngineGraph;
import org.apache.jena.sparql.graph.GraphFactory;
import org.yawlfoundation.yawl.validation.GuardViolation;
import org.yawlfoundation.yawl.validation.HyperStandardsValidator;
import org.yawlfoundation.yawl.validation.GuardReceipt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SHACL validator for YAWL specifications.
 *
 * Validates YAWL workflow specifications against SHACL shapes to ensure
 * structural and semantic consistency.
 */
public class YAWLShaclValidator {

    private final Model shapesModel;
    private final String shapesGraphUri;

    /**
     * Creates a new SHACL validator with pre-loaded YAWL shapes.
     */
    public YAWLShaclValidator() {
        this.shapesModel = loadShapesModel();
        this.shapesGraphUri = "http://yawlfoundation.org/shacl/yawl-shapes";
    }

    /**
     * Loads the YAWL SHACL shapes model.
     */
    private Model loadShapesModel() {
        Model model = ModelFactory.createDefaultModel();

        // Load built-in YAWL shapes
        model.read("classpath:/shacl/yawl-core-shapes.ttl", "TURTLE");
        model.read("classpath:/shacl/yawl-workflow-shapes.ttl", "TURTLE");
        model.read("classpath:/shacl/yawl-net-shapes.ttl", "TURTLE");
        model.read("classpath:/shacl/yawl-element-shapes.ttl", "TURTLE");

        return model;
    }

    /**
     * Validates a YAWL specification against SHACL shapes.
     *
     * @param specificationPath Path to the YAWL specification file
     * @return Validation report containing all violations
     */
    public ValidationReport validateSpecification(Path specificationPath) {
        // Load the YAWL specification as RDF
        Model dataModel = loadSpecificationModel(specificationPath);

        // Create shapes graph
        ShapesGraph shapesGraph = Shacl.createShapesGraph(shapesModel, shapesGraphUri);

        // Create validation engine
        ValidationEngine engine = ValidationEngine.create()
            .setShaclGraph(shapesGraph)
            .setDataGraph(dataModel);

        // Execute validation
        ValidationReport report = engine.validate();

        return report;
    }

    /**
     * Loads a YAWL specification as an RDF model.
     */
    private Model loadSpecificationModel(Path specificationPath) {
        Model model = ModelFactory.createDefaultModel();

        try {
            // Load YAWL specification (assuming Turtle format)
            RDFDataMgr.read(model, specificationPath.toFile());
        } catch (Exception e) {
            throw new RuntimeException("Failed to load specification: " + specificationPath, e);
        }

        return model;
    }

    /**
     * Converts a SHACL validation report to GuardViolation list.
     */
    public List<GuardViolation> convertToGuardViolations(ValidationReport report, Path specificationPath) {
        List<GuardViolation> violations = new ArrayList<>();

        if (report.conforms()) {
            return violations; // No violations
        }

        // Convert each SHACL constraint violation to GuardViolation
        report.results().forEach(result -> {
            GuardViolation violation = new GuardViolation(
                "SHACL_" + result.focusNode().getURI(),
                "FAIL",
                result.path().size(), // Line number approximation
                result.message()
            );

            violation.setFile(specificationPath.toString());
            violation.setFixGuidance(generateFixGuidance(result));

            violations.add(violation);
        });

        return violations;
    }

    /**
     * Generates fix guidance for SHACL violations.
     */
    private String generateFixGuidance(org.apache.jena.shacl.ValidationResult result) {
        // Check the shape ID to provide specific guidance
        String shapeId = result.shape().getURI();

        if (shapeId.contains("ProcessTaskShape")) {
            return "Process tasks must have exactly one input and one output flow. Add or remove flows as needed.";
        } else if (shapeId.contains("GatewayShape")) {
            return "Gateways must have at least two flows. Ensure all gateways have proper flow connections.";
        } else if (shapeId.contains("YAWLNetShape")) {
            return "YAWL nets must have exactly one input and one output task. Check net structure.";
        } else if (shapeId.contains("TaskShape")) {
            return "Tasks must have a proper name and connected flows. Verify task connectivity.";
        } else {
            return "Fix the SHACL constraint violation. Refer to YAWL specification for details.";
        }
    }

    /**
     * Validates all YAWL specifications in a directory.
     *
     * @param specDir Directory containing YAWL specifications
     * @return GuardReceipt with all validation results
     */
    public GuardReceipt validateSpecifications(Path specDir) {
        GuardReceipt receipt = new GuardReceipt();
        receipt.setPhase("shacl");
        receipt.setTimestamp(Instant.now());

        List<Path> specFiles = findSpecificationFiles(specDir);
        receipt.setFilesScanned(specFiles.size());

        List<GuardViolation> allViolations = new ArrayList<>();

        for (Path specFile : specFiles) {
            try {
                ValidationReport report = validateSpecification(specFile);
                List<GuardViolation> violations = convertToGuardViolations(report, specFile);
                allViolations.addAll(violations);
            } catch (Exception e) {
                // Add a violation for failed validation
                GuardViolation errorViolation = new GuardViolation(
                    "SHACL_ERROR",
                    "FAIL",
                    0,
                    "Failed to validate specification: " + e.getMessage()
                );
                errorViolation.setFile(specFile.toString());
                errorViolation.setFixGuidance("Check specification format and content");
                allViolations.add(errorViolation);
            }
        }

        receipt.setViolations(allViolations);
        receipt.setStatus(allViolations.isEmpty() ? "GREEN" : "RED");

        if (!allViolations.isEmpty()) {
            receipt.setErrorMessage("SHACL validation failed with " + allViolations.size() + " violations");
        }

        return receipt;
    }

    /**
     * Finds YAWL specification files in a directory.
     */
    private List<Path> findSpecificationFiles(Path dir) {
        List<Path> specFiles = new ArrayList<>();

        try {
            Files.walk(dir)
                .filter(p -> p.toString().endsWith(".yawl") ||
                           p.toString().endsWith(".ttl") ||
                           p.toString().endsWith(".xml"))
                .filter(Files::isRegularFile)
                .forEach(specFiles::add);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan directory: " + dir, e);
        }

        return specFiles;
    }

    /**
     * Saves SHACL shapes to a file.
     */
    public void saveShapes(Path outputPath) throws IOException {
        try (OutputStream os = new FileOutputStream(outputPath.toFile())) {
            shapesModel.write(os, "TURTLE");
        }
    }
}