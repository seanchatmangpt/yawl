/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of GuardChecker using SPARQL queries on RDF facts.
 * Suitable for detecting complex patterns that require understanding code structure:
 * - H_STUB: empty method bodies returning placeholder values
 * - H_EMPTY: void methods with no implementation
 * - H_FALLBACK: catch blocks returning fake data
 * - H_LIE: code documentation mismatches
 *
 * This implementation requires conversion of Java AST to RDF facts,
 * then executes SPARQL queries to find violations.
 */
public class SparqlGuardChecker implements GuardChecker {
    private final String patternName;
    private final String sparqlQuery;
    private final Severity severity;

    /**
     * Create a new SPARQL-based guard checker.
     *
     * @param patternName the guard pattern name (e.g., H_STUB)
     * @param sparqlQuery the SPARQL SELECT query to find violations
     * @param severity the severity level (WARN or FAIL)
     */
    public SparqlGuardChecker(String patternName, String sparqlQuery, Severity severity) {
        this.patternName = Objects.requireNonNull(patternName, "patternName must not be null");
        this.sparqlQuery = Objects.requireNonNull(sparqlQuery, "sparqlQuery must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
    }

    /**
     * Create a new SPARQL-based guard checker with default FAIL severity.
     *
     * @param patternName the guard pattern name
     * @param sparqlQuery the SPARQL SELECT query
     */
    public SparqlGuardChecker(String patternName, String sparqlQuery) {
        this(patternName, sparqlQuery, Severity.FAIL);
    }

    @Override
    public List<GuardViolation> check(Path javaSource) throws IOException {
        List<GuardViolation> violations = new ArrayList<>();

        try {
            // Step 1: Parse Java source to RDF model
            Model rdfModel = JavaAstToRdfConverter.convertFile(javaSource);

            // Step 2: Execute SPARQL query on RDF model
            try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, rdfModel)) {
                ResultSet results = qexec.execSelect();

                // Step 3: Convert SPARQL results to violations
                while (results.hasNext()) {
                    QuerySolution soln = results.next();

                    // Extract violation information from SPARQL result
                    String line = soln.getLiteral("line") != null ?
                            soln.getLiteral("line").getString() : "0";
                    String content = soln.getLiteral("content") != null ?
                            soln.getLiteral("content").getString() : "";

                    int lineNum;
                    try {
                        lineNum = Integer.parseInt(line);
                    } catch (NumberFormatException e) {
                        lineNum = 0;
                    }

                    violations.add(new GuardViolation(
                        patternName,
                        severity.name(),
                        lineNum,
                        content
                    ));
                }
            }
        } catch (Exception e) {
            // Log SPARQL parsing errors but don't fail completely
            // This allows graceful degradation if AST parsing fails
            throw new IOException("Failed to execute SPARQL query for pattern " + patternName, e);
        }

        return violations;
    }

    @Override
    public String patternName() {
        return patternName;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String toString() {
        return "SparqlGuardChecker{" +
                "pattern=" + patternName +
                ", severity=" + severity +
                '}';
    }
}
