package org.yawlfoundation.yawl.ggen.mining.generators;

import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;

import java.util.List;

/**
 * Factory for dispatching Petri net exports to various process/infrastructure
 * formats. Provides unified interface for converting discovered process models
 * to deployment-ready formats (BPMN, BPEL, Terraform, Kubernetes).
 *
 * Supported formats:
 * - CAMUNDA: Camunda BPMN 2.0 (Camunda Platform 7/8)
 * - BPEL: WS-BPEL 2.0 (enterprise SOA/orchestration)
 * - TERRAFORM_AWS: AWS Lambda + Step Functions
 * - TERRAFORM_AZURE: Azure Logic Apps + Functions
 * - TERRAFORM_GCP: GCP Cloud Workflows + Functions
 * - KUBERNETES: Kubernetes Helm charts + CronJobs
 *
 * Usage:
 *   String bpmn = ProcessExporterFactory.export(petriNet, "CAMUNDA");
 *   String bpel = ProcessExporterFactory.export(petriNet, "BPEL");
 *   String tf = ProcessExporterFactory.export(petriNet, "TERRAFORM_AWS");
 */
public class ProcessExporterFactory {

    /**
     * Export a Petri net to the specified target format.
     *
     * @param model the PetriNet to export
     * @param targetFormat the target format (case-insensitive):
     *                     CAMUNDA, BPEL, TERRAFORM_AWS, TERRAFORM_AZURE,
     *                     TERRAFORM_GCP, KUBERNETES
     * @return the exported content as a string
     * @throws IllegalArgumentException if targetFormat is not supported
     */
    public static String export(PetriNet model, String targetFormat) {
        if (model == null) {
            throw new IllegalArgumentException("PetriNet model cannot be null");
        }
        if (targetFormat == null) {
            throw new IllegalArgumentException("Target format cannot be null");
        }

        return switch (targetFormat.toUpperCase()) {
            case "CAMUNDA" -> new CamundaBpmnExporter().export(model);
            case "BPEL" -> new BpelExporter().export(model);
            case "TERRAFORM_AWS" -> new TerraformGenerator().generateTerraform(model, "aws");
            case "TERRAFORM_AZURE" -> new TerraformGenerator().generateTerraform(model, "azure");
            case "TERRAFORM_GCP" -> new TerraformGenerator().generateTerraform(model, "gcp");
            case "KUBERNETES" -> new TerraformGenerator().generateTerraform(model, "kubernetes");
            default -> throw new IllegalArgumentException(
                    "Unsupported target format: " + targetFormat +
                    ". Supported formats: " + String.join(", ", supportedFormats())
            );
        };
    }

    /**
     * List all supported export formats.
     *
     * @return list of supported format names (uppercase)
     */
    public static List<String> supportedFormats() {
        return List.of(
                "CAMUNDA",
                "BPEL",
                "TERRAFORM_AWS",
                "TERRAFORM_AZURE",
                "TERRAFORM_GCP",
                "KUBERNETES"
        );
    }
}
