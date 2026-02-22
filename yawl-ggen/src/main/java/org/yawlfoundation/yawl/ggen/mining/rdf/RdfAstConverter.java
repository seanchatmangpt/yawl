package org.yawlfoundation.yawl.ggen.mining.rdf;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.yawlfoundation.yawl.ggen.mining.model.*;

/**
 * Converts a Petri net AST into RDF triples using Apache Jena.
 * Creates semantic representation of discovered process models.
 */
public class RdfAstConverter {
    public static final String YAWL_MINED_NS = "http://ggen.io/yawl-mined#";
    public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";

    private Model model;
    private Resource processResource;

    public RdfAstConverter() {
        this.model = ModelFactory.createDefaultModel();
        this.model.setNsPrefix("yawl-mined", YAWL_MINED_NS);
        this.model.setNsPrefix("rdf", RDF.getURI());
        this.model.setNsPrefix("rdfs", RDFS.getURI());
        this.model.setNsPrefix("xsd", XSD_NS);
    }

    /**
     * Convert a Petri net model to RDF triples.
     *
     * @param petriNet The Petri net to convert
     * @return Apache Jena Model containing RDF triples
     */
    public Model convertToRdf(PetriNet petriNet) {
        if (petriNet == null) {
            throw new IllegalArgumentException("PetriNet cannot be null");
        }

        // Create the main process resource
        processResource = model.createResource(YAWL_MINED_NS + "Process_" + petriNet.getId());
        processResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "Process"));
        processResource.addProperty(
            model.createProperty(YAWL_MINED_NS, "hasName"),
            petriNet.getName()
        );

        // Convert places
        for (Place place : petriNet.getPlaces().values()) {
            convertPlace(place);
        }

        // Convert transitions
        for (Transition transition : petriNet.getTransitions().values()) {
            convertTransition(transition);
        }

        // Convert arcs
        for (Arc arc : petriNet.getArcs()) {
            convertArc(arc);
        }

        return model;
    }

    /**
     * Convert a place to RDF triples.
     */
    private void convertPlace(Place place) {
        Resource placeResource = model.createResource(YAWL_MINED_NS + "Place_" + place.getId());
        placeResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "Place"));
        placeResource.addProperty(model.createProperty(YAWL_MINED_NS, "id"), place.getId());
        placeResource.addProperty(model.createProperty(YAWL_MINED_NS, "name"), place.getName());

        if (place.getInitialMarking() > 0) {
            placeResource.addProperty(
                model.createProperty(YAWL_MINED_NS, "initialMarking"),
                model.createTypedLiteral(place.getInitialMarking())
            );
        }

        // Add place type annotations
        if (place.isInitialPlace()) {
            placeResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "InitialPlace"));
        }
        if (place.isFinalPlace()) {
            placeResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "FinalPlace"));
        }

        // Link to process
        processResource.addProperty(model.createProperty(YAWL_MINED_NS, "hasPlace"), placeResource);
    }

    /**
     * Convert a transition to RDF triples.
     */
    private void convertTransition(Transition transition) {
        Resource transitionResource = model.createResource(YAWL_MINED_NS + "Transition_" + transition.getId());
        transitionResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "Transition"));
        transitionResource.addProperty(model.createProperty(YAWL_MINED_NS, "id"), transition.getId());
        transitionResource.addProperty(model.createProperty(YAWL_MINED_NS, "name"), transition.getName());

        if (transition.getGuard() != null) {
            transitionResource.addProperty(model.createProperty(YAWL_MINED_NS, "guard"), transition.getGuard());
        }

        // Add transition type annotations
        if (transition.isStartTransition()) {
            transitionResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "StartTransition"));
        }
        if (transition.isEndTransition()) {
            transitionResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "EndTransition"));
        }
        if (transition.isGateway()) {
            String gatewayType = transition.getIncomingArcs().size() > 1 ? "JoinGateway" : "SplitGateway";
            transitionResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + gatewayType));
            transitionResource.addProperty(
                model.createProperty(YAWL_MINED_NS, "branchCount"),
                model.createTypedLiteral(transition.getBranchCount())
            );
        }

        // Link to process
        processResource.addProperty(model.createProperty(YAWL_MINED_NS, "hasTransition"), transitionResource);
    }

    /**
     * Convert an arc to RDF triples.
     */
    private void convertArc(Arc arc) {
        Resource arcResource = model.createResource(YAWL_MINED_NS + "Arc_" + arc.getId());
        arcResource.addProperty(RDF.type, model.createResource(YAWL_MINED_NS + "Arc"));
        arcResource.addProperty(model.createProperty(YAWL_MINED_NS, "id"), arc.getId());

        // Link source and target
        String sourceId = arc.getSource().getId();
        String targetId = arc.getTarget().getId();

        Resource sourceResource = model.getResource(
            arc.getSource() instanceof Place ?
                YAWL_MINED_NS + "Place_" + sourceId :
                YAWL_MINED_NS + "Transition_" + sourceId
        );
        Resource targetResource = model.getResource(
            arc.getTarget() instanceof Place ?
                YAWL_MINED_NS + "Place_" + targetId :
                YAWL_MINED_NS + "Transition_" + targetId
        );

        arcResource.addProperty(model.createProperty(YAWL_MINED_NS, "source"), sourceResource);
        arcResource.addProperty(model.createProperty(YAWL_MINED_NS, "target"), targetResource);

        if (arc.getMultiplicity() > 1) {
            arcResource.addProperty(
                model.createProperty(YAWL_MINED_NS, "multiplicity"),
                model.createTypedLiteral(arc.getMultiplicity())
            );
        }

        // Link to process
        processResource.addProperty(model.createProperty(YAWL_MINED_NS, "hasArc"), arcResource);
    }

    /**
     * Get the converted RDF model.
     */
    public Model getModel() {
        return model;
    }

    /**
     * Write the RDF model to a file in Turtle format.
     */
    public void writeTurtle(String filePath) {
        try (var out = new java.io.FileWriter(filePath)) {
            model.write(out, "TURTLE");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write RDF to file: " + filePath, e);
        }
    }

    /**
     * Get the RDF model as a Turtle string.
     */
    public String getTurtleString() {
        var sw = new java.io.StringWriter();
        model.write(sw, "TURTLE");
        return sw.toString();
    }
}
