/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.pi.prescriptive;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Validates process actions against workflow ordering constraints.
 *
 * <p>Uses Apache Jena RDF model to store and query task precedence constraints.
 * Enables checking whether a proposed action (e.g., rerouting) violates task ordering.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ProcessConstraintModel {

    private final Model model;
    private final ReentrantLock modelLock = new ReentrantLock();

    private static final String CONSTRAINT_NS = "http://yawl.org/constraints/";
    private static final Property PRECEDES = ResourceFactory.createProperty(
        CONSTRAINT_NS, "precedes");
    private static final Property REQUIRES_BEFORE = ResourceFactory.createProperty(
        CONSTRAINT_NS, "requiresBefore");

    /**
     * Construct an empty constraint model.
     */
    public ProcessConstraintModel() {
        this.model = ModelFactory.createDefaultModel();
        model.setNsPrefix("constraint", CONSTRAINT_NS);
    }

    /**
     * Populate constraint model from workflow specification.
     *
     * <p>Creates RDF triples representing task ordering constraints.
     * For simplicity, no actual constraints are inferred from YSpecificationID;
     * real implementation would parse specification XML.
     *
     * @param specId Workflow specification ID
     * @param taskNames List of task names in specification
     */
    public void populateFrom(String specId, List<String> taskNames) {
        modelLock.lock();
        try {
            Resource specResource = model.createResource(CONSTRAINT_NS + specId);
            specResource.addProperty(RDF.type, ResourceFactory.createResource(
                CONSTRAINT_NS + "Specification"));

            for (String taskName : taskNames) {
                Resource taskResource = model.createResource(
                    CONSTRAINT_NS + specId + "/" + taskName);
                taskResource.addProperty(RDF.type, ResourceFactory.createResource(
                    CONSTRAINT_NS + "Task"));
                specResource.addProperty(REQUIRES_BEFORE, taskResource);
            }
        } finally {
            modelLock.unlock();
        }
    }

    /**
     * Check if an action violates constraints.
     *
     * <p>For RerouteAction: checks that toTaskName is not required before fromTaskName.
     * For all other actions: returns true (always feasible).
     *
     * @param action Action to validate
     * @return true if action is feasible
     */
    public boolean isFeasible(ProcessAction action) {
        modelLock.lock();
        try {
            if (action instanceof RerouteAction ra) {
                List<String> predecessors = getTaskPrecedences(ra.toTaskName());
                return !predecessors.contains(ra.fromTaskName());
            }
            return true;
        } finally {
            modelLock.unlock();
        }
    }

    /**
     * Get list of tasks that must precede a given task.
     *
     * <p>Queries RDF model for all tasks with a "precedes" relationship
     * to the target task.
     *
     * @param taskName Task to analyze
     * @return List of predecessor task names
     */
    public List<String> getTaskPrecedences(String taskName) {
        modelLock.lock();
        try {
            List<String> predecessors = new ArrayList<>();
            String sparql = """
                PREFIX constraint: <http://yawl.org/constraints/>
                SELECT ?predecessor
                WHERE {
                    ?predecessor constraint:precedes <http://yawl.org/constraints/%s> .
                }
                """.formatted(taskName);

            var query = org.apache.jena.query.QueryFactory.create(sparql);
            var qexec = org.apache.jena.query.QueryExecutionFactory.create(query, model);
            var results = qexec.execSelect();

            while (results.hasNext()) {
                var solution = results.nextSolution();
                Resource predRes = solution.getResource("predecessor");
                if (predRes != null) {
                    String predName = predRes.getLocalName();
                    predecessors.add(predName);
                }
            }

            qexec.close();
            return predecessors;
        } finally {
            modelLock.unlock();
        }
    }
}
