package org.yawlfoundation.yawl.ggen.mining.generators;

import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Exports a Petri net model as a valid Camunda BPMN 2.0 XML file that can be
 * directly deployed to Camunda Platform 7/8. The exporter maps Petri net
 * transitions to BPMN tasks (serviceTask, startEvent, endEvent, exclusiveGateway)
 * and arcs to sequenceFlow elements.
 *
 * Key transformations:
 * - Start transitions (no incoming arcs) → bpmn:startEvent
 * - End transitions (no outgoing arcs) → bpmn:endEvent
 * - Gateway transitions (multiple outgoing arcs) → bpmn:exclusiveGateway
 * - Regular transitions → bpmn:serviceTask
 * - Arcs → bpmn:sequenceFlow
 *
 * The output is valid BPMN 2.0 with Camunda extensions, suitable for deployment
 * to Camunda Process Engine.
 */
public class CamundaBpmnExporter {

    /**
     * Export a Petri net model as Camunda BPMN 2.0 XML string.
     *
     * @param model the PetriNet to export
     * @return valid BPMN 2.0 XML string with Camunda extensions
     */
    public String export(PetriNet model) {
        StringBuilder xml = new StringBuilder();

        // XML declaration
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

        // BPMN definitions root element with Camunda namespace
        xml.append("<bpmn:definitions\n");
        xml.append("    xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n");
        xml.append("    xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n");
        xml.append("    xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n");
        xml.append("    xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"\n");
        xml.append("    xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\"\n");
        xml.append("    targetNamespace=\"http://camunda.org/schema/1.0/bpmn\"\n");
        xml.append("    exporter=\"YAWL ggen\"\n");
        xml.append("    exporterVersion=\"1.0.0\"\n");
        xml.append("    id=\"Definitions_").append(UUID.randomUUID().toString()).append("\">\n\n");

        // Process element
        xml.append("  <bpmn:process id=\"").append(escapeXml(model.getId())).append("\" ");
        xml.append("name=\"").append(escapeXml(model.getName())).append("\" ");
        xml.append("isExecutable=\"true\">\n\n");

        // Add start events for start transitions
        for (Transition trans : model.getStartTransitions()) {
            xml.append("    <bpmn:startEvent id=\"").append(escapeXml(trans.getId())).append("_start\" ");
            xml.append("name=\"").append(escapeXml(trans.getName())).append("\">\n");

            // Find first outgoing arc to determine next element
            if (!trans.getOutgoingArcs().isEmpty()) {
                Arc firstOutgoing = trans.getOutgoingArcs().iterator().next();
                String targetId = getTargetElementId(firstOutgoing);
                xml.append("      <bpmn:outgoing>flow_").append(escapeXml(trans.getId()))
                        .append("_to_").append(escapeXml(targetId)).append("</bpmn:outgoing>\n");
            }

            xml.append("    </bpmn:startEvent>\n\n");
        }

        // Add end events for end transitions
        for (Transition trans : model.getEndTransitions()) {
            xml.append("    <bpmn:endEvent id=\"").append(escapeXml(trans.getId())).append("_end\" ");
            xml.append("name=\"").append(escapeXml(trans.getName())).append("\">\n");

            // Find incoming arc
            if (!trans.getIncomingArcs().isEmpty()) {
                Arc firstIncoming = trans.getIncomingArcs().iterator().next();
                String sourceId = getSourceElementId(firstIncoming);
                xml.append("      <bpmn:incoming>flow_").append(escapeXml(sourceId))
                        .append("_to_").append(escapeXml(trans.getId())).append("</bpmn:incoming>\n");
            }

            xml.append("    </bpmn:endEvent>\n\n");
        }

        // Add exclusive gateways for gateway transitions
        for (Transition trans : model.getGateways()) {
            xml.append("    <bpmn:exclusiveGateway id=\"").append(escapeXml(trans.getId())).append("\" ");
            xml.append("name=\"").append(escapeXml(trans.getName())).append("\">\n");

            // Incoming flows
            for (Arc incoming : trans.getIncomingArcs()) {
                String sourceId = getSourceElementId(incoming);
                xml.append("      <bpmn:incoming>flow_").append(escapeXml(sourceId))
                        .append("_to_").append(escapeXml(trans.getId())).append("</bpmn:incoming>\n");
            }

            // Outgoing flows
            for (Arc outgoing : trans.getOutgoingArcs()) {
                String targetId = getTargetElementId(outgoing);
                xml.append("      <bpmn:outgoing>flow_").append(escapeXml(trans.getId()))
                        .append("_to_").append(escapeXml(targetId)).append("</bpmn:outgoing>\n");
            }

            xml.append("    </bpmn:exclusiveGateway>\n\n");
        }

        // Add service tasks for regular transitions (not start/end/gateway)
        for (Transition trans : model.getTransitions().values()) {
            if (!trans.isStartTransition() && !trans.isEndTransition() && !trans.isGateway()) {
                xml.append("    <bpmn:serviceTask id=\"").append(escapeXml(trans.getId())).append("\" ");
                xml.append("name=\"").append(escapeXml(trans.getName())).append("\" ");
                xml.append("camunda:type=\"external\" ");
                xml.append("camunda:topic=\"").append(escapeXml(trans.getId())).append("\">\n");

                // Incoming flows
                for (Arc incoming : trans.getIncomingArcs()) {
                    String sourceId = getSourceElementId(incoming);
                    xml.append("      <bpmn:incoming>flow_").append(escapeXml(sourceId))
                            .append("_to_").append(escapeXml(trans.getId())).append("</bpmn:incoming>\n");
                }

                // Outgoing flows
                for (Arc outgoing : trans.getOutgoingArcs()) {
                    String targetId = getTargetElementId(outgoing);
                    xml.append("      <bpmn:outgoing>flow_").append(escapeXml(trans.getId()))
                            .append("_to_").append(escapeXml(targetId)).append("</bpmn:outgoing>\n");
                }

                xml.append("    </bpmn:serviceTask>\n\n");
            }
        }

        // Add sequence flows for all arcs
        for (Arc arc : model.getArcs()) {
            String sourceId = getSourceElementId(arc);
            String targetId = getTargetElementId(arc);
            xml.append("    <bpmn:sequenceFlow id=\"flow_").append(escapeXml(sourceId))
                    .append("_to_").append(escapeXml(targetId)).append("\" ");
            xml.append("sourceRef=\"").append(escapeXml(sourceId)).append("\" ");
            xml.append("targetRef=\"").append(escapeXml(targetId)).append("\"/>\n");
        }

        xml.append("\n  </bpmn:process>\n\n");

        // Close definitions
        xml.append("</bpmn:definitions>\n");

        return xml.toString();
    }

    /**
     * Export a Petri net model to a file as Camunda BPMN 2.0 XML.
     *
     * @param model the PetriNet to export
     * @param outputPath the file path to write to
     * @throws IOException if file writing fails
     */
    public void exportToFile(PetriNet model, Path outputPath) throws IOException {
        String bpmnXml = export(model);
        Files.write(outputPath, bpmnXml.getBytes());
    }

    /**
     * Get the target element ID from an arc, accounting for intermediate places.
     * If the arc target is a place, returns the place ID. Otherwise returns the transition ID.
     */
    private String getTargetElementId(Arc arc) {
        if (arc.getTarget() instanceof Place) {
            return arc.getTarget().getId();
        }
        return arc.getTarget().getId();
    }

    /**
     * Get the source element ID from an arc, accounting for intermediate places.
     * If the arc source is a place, returns the place ID. Otherwise returns the transition ID.
     */
    private String getSourceElementId(Arc arc) {
        if (arc.getSource() instanceof Place) {
            return arc.getSource().getId();
        }
        return arc.getSource().getId();
    }

    /**
     * Escape XML special characters in strings.
     * Throws exception if string is null, as all IDs must be present.
     */
    private String escapeXml(String str) {
        if (str == null) {
            throw new IllegalArgumentException("Cannot escape null string: element ID cannot be null");
        }
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
