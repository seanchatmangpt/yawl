import org.yawlfoundation.yawl.ggen.rl.*;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlToYawlConverter;
import org.yawlfoundation.yawl.ggen.powl.PowlValidator;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.rl.scoring.RewardFunction;
import org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * First workflow generation example for YAWL ggen v6.0.0-GA.
 *
 * This example demonstrates:
 * 1. Creating an RL configuration
 * 2. Setting up the GRPO optimizer
 * 3. Generating a workflow from natural language
 * 4. Converting to YAWL specification
 * 5. Validating the result
 */
public class FirstWorkflow {

    public static void main(String[] args) {
        try {
            System.out.println("=== YAWL ggen v6.0.0-GA First Workflow Example ===\n");

            // 1. Configure the RL optimizer
            RlConfig config = new RlConfig(
                4,                          // k: Number of candidates to sample
                CurriculumStage.VALIDITY_GAP, // Start with validity gap stage
                3,                          // Max validations per candidate
                "http://localhost:11434",  // Ollama API URL
                "qwen2.5-coder",            // LLM model name
                60                          // Timeout in seconds
            );

            System.out.println("Configuration:");
            System.out.println("  - Candidates (k): " + config.k());
            System.out.println("  - Stage: " + config.stage());
            System.out.println("  - Model: " + config.ollamaModel());
            System.out.println("  - Timeout: " + config.timeoutSecs() + "s\n");

            // 2. Create components
            // Create a shared process knowledge graph for pattern memory
            ProcessKnowledgeGraph knowledgeGraph = new ProcessKnowledgeGraph();

            // Create sampler and reward function
            CandidateSampler sampler = new OllamaCandidateSampler(config, knowledgeGraph);
            RewardFunction rewardFunction = createRewardFunction();

            // 3. Create the optimizer
            GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFunction, config, knowledgeGraph);

            // 4. Generate workflow
            String processDescription = "Submit form, review, then approve or reject";
            System.out.println("Generating workflow for: \"" + processDescription + "\"\n");

            PowlModel model = optimizer.optimize(processDescription);

            // 5. Display results
            System.out.println("=== Generated Workflow ===");
            System.out.println("ID: " + model.id());
            System.out.println("Generated at: " + model.generatedAt());
            System.out.println("Root node: " + model.root().getClass().getSimpleName());

            // 6. Validate the workflow
            ValidationReport validation = PowlValidator.validate(model);
            System.out.println("\nValidation: " + (validation.isValid() ? "✓ PASSED" : "✗ FAILED"));
            if (!validation.getIssues().isEmpty()) {
                System.out.println("Issues:");
                validation.getIssues().forEach(issue ->
                    System.out.println("  - " + issue));
            }

            // 7. Convert to YAWL specification
            System.out.println("\n=== Converting to YAWL Specification ===");
            PetriNet petriNet = PowlToYawlConverter.convert(model);
            YawlSpecExporter exporter = new YawlSpecExporter();
            String yawlXml = exporter.export(petriNet);

            System.out.println("Generated YAWL XML (" + yawlXml.length() + " characters)");

            // 8. Save results
            String filename = "workflow_" + model.id();
            Files.write(Paths.get(filename + ".yawl"), yawlXml.getBytes());

            System.out.println("\n=== Results Saved ===");
            System.out.println("  - YAWL specification: " + filename + ".yawl");
            System.out.println("  - Process knowledge graph: " + knowledgeGraph.getNodeCount() + " patterns");

            System.out.println("\n=== Next Steps ===");
            System.out.println("1. Open " + filename + ".yawl in a YAWL editor");
            System.out.println("2. Import into YAWL runtime engine");
            System.out.println("3. Try with different process descriptions!");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("- Ensure Ollama is running: 'ollama serve'");
            System.err.println("- Check model availability: 'ollama list'");
            System.err.println("- Try a different model: 'ollama pull qwen2.5-coder'");
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates a reward function for workflow generation.
     */
    private static RewardFunction createRewardFunction() {
        return (candidate, description) -> {
            // Basic reward function based on validation
            ValidationReport report = PowlValidator.validate(candidate);
            if (!report.isValid()) {
                return 0.0;
            }

            // Additional scoring logic could be added here
            // - Semantic similarity to description
            // - Structural complexity metrics
            // - Pattern completeness

            return 0.8; // Base score for valid models
        };
    }
}