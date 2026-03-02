package org.yawlfoundation.yawl.benchmark.sparql;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Generator for RDF datasets of varying sizes for SPARQL benchmarks.
 * 
 * <p>Generates realistic RDF data representing workflow instances, tasks,
 * cases, and agents suitable for testing SPARQL engine performance.</p>
 * 
 * <p>Supports different dataset sizes:
 * <ul>
 *   <li>1K triples (small dataset for quick tests)</li>
 *   <li>10K triples (medium dataset for realistic queries)</li>
 *   <li>100K triples (large dataset for stress testing)</li>
 *   <li>1M triples (very large dataset for scale testing)</li>
 * </ul></p>
 * 
 * @since YAWL 6.0
 */
public class RdfDataGenerator {

    private static final String[] WORKFLOW_NAMES = {
        "Order Processing", "Loan Application", "Employee Onboarding", 
        "Expense Approval", "Customer Service", "Invoice Processing",
        "Contract Review", "Quality Assurance", "Shipping Logistics",
        "Compliance Check", "Travel Request", "Vendor Onboarding",
        "IT Support", "HR Onboarding", "Security Audit"
    };

    private static final String[] AGENT_NAMES = {
        "alice", "bob", "carol", "david", "eve", "frank", "grace", "henry",
        "iris", "jack", "karen", "leo", "mia", "noah", "olga", "peter"
    };

    private static final String[] TASK_STATUSES = {
        "enabled", "allocated", "started", "completed", "cancelled", "failed"
    };

    private static final String[] PRIORITY_LEVELS = {
        "low", "medium", "high", "critical"
    };

    private static final String[] DATA_TYPES = {
        "string", "integer", "boolean", "dateTime", "decimal", "uri"
    };

    private final Random random;
    private final String baseUri;

    public RdfDataGenerator() {
        this.random = new Random();
        this.baseUri = "http://example.org/yawl/";
    }

    /**
     * Generate RDF dataset and save to file.
     * 
     * @param size dataset size in triples
     * @param outputPath path to output Turtle file
     * @throws IOException on file write errors
     */
    public void generateDataset(int size, Path outputPath) throws IOException {
        switch (size) {
            case 1000:
                generateSmallDataset(outputPath);
                break;
            case 10000:
                generateMediumDataset(outputPath);
                break;
            case 100000:
                generateLargeDataset(outputPath);
                break;
            case 1000000:
                generateVeryLargeDataset(outputPath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported dataset size: " + size);
        }
    }

    /**
     * Generate small dataset (1K triples) for quick tests.
     */
    private void generateSmallDataset(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("@prefix yawl: <http://www.yawlfoundation.org/yawl#> .\n");
            writer.write("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
            writer.write("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");

            // Generate 10 cases
            for (int i = 1; i <= 10; i++) {
                String caseId = "case-" + i;
                writer.write(String.format("<%s> a yawl:Case ;\n", caseId));
                writer.write(String.format("    yawl:hasStatus \"%s\" ;\n", randomCaseStatus()));
                writer.write(String.format("    yawl:hasPriority \"%s\" ;\n", randomPriority()));
                writer.write(String.format("    yawl:created \"%s\"^^xsd:dateTime ;\n", randomTimestamp()));
                
                // Add some task relationships
                if (random.nextBoolean()) {
                    writer.write(String.format("    yawl:hasTask <task-%d-%d> .\n", i, 1));
                }
                
                if (random.nextBoolean()) {
                    writer.write(String.format("    yawl:hasTask <task-%d-%d> .\n", i, 2));
                }
                
                writer.write("\n");
            }

            // Generate some tasks
            for (int i = 1; i <= 20; i++) {
                String taskId = "task-" + (i % 10 + 1) + "-" + ((i / 10) + 1);
                writer.write(String.format("<%s> a yawl:WorkItem ;\n", taskId));
                writer.write(String.format("    yawl:hasStatus \"%s\" ;\n", randomTaskStatus()));
                
                if (random.nextBoolean()) {
                    writer.write(String.format("    yawl:assignedTo \"%s\" ;\n", randomAgent()));
                }
                
                writer.write(String.format("    yawl:created \"%s\"^^xsd:dateTime .\n", randomTimestamp()));
                writer.write("\n");
            }

            writer.write("# Total triples: ~" + countLines(outputPath) + "\n");
        }
    }

    /**
     * Generate medium dataset (10K triples) for realistic queries.
     */
    private void generateMediumDataset(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("@prefix yawl: <http://www.yawlfoundation.org/yawl#> .\n");
            writer.write("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
            writer.write("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");

            // Generate 100 cases
            for (int i = 1; i <= 100; i++) {
                writeCase(writer, i);
            }

            // Generate 500 tasks
            for (int i = 1; i <= 500; i++) {
                writeTask(writer, i);
            }

            // Generate 50 process definitions
            for (int i = 1; i <= 50; i++) {
                writeProcess(writer, i);
            }

            // Generate additional relationships to reach ~10K triples
            writeAdditionalRelationships(writer, 8000);

            writer.write("# Generated ~10,000 triples for medium benchmark\n");
        }
    }

    /**
     * Generate large dataset (100K triples) for stress testing.
     */
    private void generateLargeDataset(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("@prefix yawl: <http://www.yawlfoundation.org/yawl#> .\n");
            writer.write("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
            writer.write("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");

            // Generate 1000 cases
            for (int i = 1; i <= 1000; i++) {
                writeCase(writer, i);
            }

            // Generate 5000 tasks
            for (int i = 1; i <= 5000; i++) {
                writeTask(writer, i);
            }

            // Generate 200 process definitions
            for (int i = 1; i <= 200; i++) {
                writeProcess(writer, i);
            }

            // Generate additional relationships
            writeAdditionalRelationships(writer, 90000);

            writer.write("# Generated ~100,000 triples for large benchmark\n");
        }
    }

    /**
     * Generate very large dataset (1M triples) for scale testing.
     */
    private void generateVeryLargeDataset(Path outputPath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("@prefix yawl: <http://www.yawlfoundation.org/yawl#> .\n");
            writer.write("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
            writer.write("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");

            // Generate 10,000 cases
            for (int i = 1; i <= 10000; i++) {
                writeCase(writer, i);
                
                // Flush periodically to avoid memory issues
                if (i % 1000 == 0) {
                    writer.flush();
                }
            }

            // Generate 50,000 tasks
            for (int i = 1; i <= 50000; i++) {
                writeTask(writer, i);
                
                if (i % 5000 == 0) {
                    writer.flush();
                }
            }

            // Generate 5000 process definitions
            for (int i = 1; i <= 5000; i++) {
                writeProcess(writer, i);
                
                if (i % 1000 == 0) {
                    writer.flush();
                }
            }

            // Generate additional relationships
            writeAdditionalRelationships(writer, 900000);

            writer.write("# Generated ~1,000,000 triples for very large benchmark\n");
        }
    }

    private void writeCase(BufferedWriter writer, int i) throws IOException {
        String caseId = "case-" + i;
        String workflow = WORKFLOW_NAMES[i % WORKFLOW_NAMES.length];
        String status = randomCaseStatus();
        
        writer.write(String.format("<%s> a yawl:Case ;\n", caseId));
        writer.write(String.format("    yawl:hasStatus \"%s\" ;\n", status));
        writer.write(String.format("    yawl:hasPriority \"%s\" ;\n", randomPriority()));
        writer.write(String.format("    yawl:created \"%s\"^^xsd:dateTime ;\n", randomTimestamp()));
        writer.write(String.format("    yawl:hasProcess <process-%d> ;\n", (i % 50) + 1));
        writer.write(String.format("    yawl:workflowName \"%s\" ;\n", workflow));
        
        // Add some variables
        if (random.nextBoolean()) {
            writer.write(String.format("    yawl:variable \"status\" \"%s\" ;\n", status));
        }
        if (random.nextBoolean()) {
            writer.write(String.format("    yawl:variable \"priority\" \"%s\" ;\n", randomPriority()));
        }
        
        writer.write("\n");
    }

    private void writeTask(BufferedWriter writer, int i) throws IOException {
        String taskId = "task-" + i;
        String status = randomTaskStatus();
        String agent = random.nextBoolean() ? randomAgent() : null;
        
        writer.write(String.format("<%s> a yawl:WorkItem ;\n", taskId));
        writer.write(String.format("    yawl:hasStatus \"%s\" ;\n", status));
        
        if (agent != null) {
            writer.write(String.format("    yawl:assignedTo \"%s\" ;\n", agent));
        }
        
        writer.write(String.format("    yawl:created \"%s\"^^xsd:dateTime ;\n", randomTimestamp()));
        
        // Link to case
        int caseId = (i % 100) + 1;
        writer.write(String.format("    yawl:belongsToCase <case-%d> ;\n", caseId));
        
        // Link to task definition
        writer.write(String.format("    yawl:taskDefinition <task-def-%d> .\n", (i % 20) + 1));
        writer.write("\n");
    }

    private void writeProcess(BufferedWriter writer, int i) throws IOException {
        String processId = "process-" + i;
        String name = WORKFLOW_NAMES[i % WORKFLOW_NAMES.length];
        
        writer.write(String.format("<%s> a yawl:Process ;\n", processId));
        writer.write(String.format("    rdfs:label \"%s\" ;\n", name));
        writer.write(String.format("    yawl:version \"%d.%d\" ;\n", (i % 3) + 1, (i % 10) + 1));
        writer.write(String.format("    yawl:created \"%s\"^^xsd:dateTime .\n", randomTimestamp()));
        writer.write("\n");
    }

    private void writeAdditionalRelationships(BufferedWriter writer, int targetCount) throws IOException {
        int written = 0;
        
        // Write task dependencies
        for (int i = 1; i <= 5000 && written < targetCount; i++) {
            int dependsOn = random.nextInt(i);
            if (dependsOn > 0) {
                writer.write(String.format("<task-%d> yawl:dependsOn <task-%d> .\n", i, dependsOn));
                written++;
            }
        }
        
        // Write agent skills
        for (int i = 1; i <= 100 && written < targetCount; i++) {
            String agent = randomAgent();
            writer.write(String.format("\"%s\" yawl:hasSkill <skill-%d> .\n", agent, (i % 10) + 1));
            written++;
        }
        
        // Write process metrics
        for (int i = 1; i <= 200 && written < targetCount; i++) {
            writer.write(String.format("<process-%d> yawl:avgDuration %d ;\n", i, random.nextInt(3600)));
            writer.write(String.format("    yawl:successRate %.2f .\n", random.nextDouble()));
            written += 2;
        }
    }

    private String randomCaseStatus() {
        String[] statuses = {"running", "completed", "cancelled", "paused", "terminated"};
        return statuses[random.nextInt(statuses.length)];
    }

    private String randomTaskStatus() {
        return TASK_STATUSES[random.nextInt(TASK_STATUSES.length)];
    }

    private String randomPriority() {
        return PRIORITY_LEVELS[random.nextInt(PRIORITY_LEVELS.length)];
    }

    private String randomAgent() {
        return AGENT_NAMES[random.nextInt(AGENT_NAMES.length)];
    }

    private String randomTimestamp() {
        long timestamp = System.currentTimeMillis() - random.nextInt(86400000 * 30); // Last 30 days
        return new java.util.Date(timestamp).toString();
    }

    private long countLines(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            return lines.count();
        }
    }

    /**
     * Main method for generating datasets.
     */
    public static void main(String[] args) throws IOException {
        RdfDataGenerator generator = new RdfDataGenerator();
        
        // Create datasets directory
        Path datasetsDir = Paths.get("datasets");
        if (!Files.exists(datasetsDir)) {
            Files.createDirectories(datasetsDir);
        }
        
        // Generate datasets of different sizes
        int[] sizes = {1000, 10000, 100000, 1000000};
        
        for (int size : sizes) {
            Path outputPath = datasetsDir.resolve("dataset-" + size + ".ttl");
            System.out.println("Generating " + size + " triples dataset to: " + outputPath);
            
            long startTime = System.currentTimeMillis();
            generator.generateDataset(size, outputPath);
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("Generated in " + duration + "ms");
        }
    }
}
