package org.yawlfoundation.yawl.ai;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpClient;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.io.StringReader;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * BLUE OCEAN INNOVATION #1: AI Workflow Architect
 *
 * Natural language → Working YAWL workflow in seconds.
 *
 * Traditional approach (Red Ocean):
 *   1. Learn YAWL Editor (days)
 *   2. Manually draw workflow (hours)
 *   3. Export to XML (manual)
 *   4. Debug XML errors (hours)
 *   5. Deploy to engine (manual)
 *
 * Blue Ocean approach:
 *   1. Describe workflow to AI ("I need a purchase approval process")
 *   2. AI generates YAWL XML (seconds)
 *   3. Auto-deploys to engine (instant)
 *   4. AI suggests optimizations (continuous)
 *
 * Market Impact:
 *   - Reduces workflow creation time from days → seconds
 *   - Enables non-technical users (citizen developers)
 *   - Creates new market: "Conversational BPM"
 *
 * @author YAWL Innovation Team
 * @version 5.2
 */
public class WorkflowArchitect {

    private static ZaiService zaiService = null;
    private static boolean initialized = false;

    /**
     * Initialize the AI Workflow Architect with API credentials
     */
    public static void initialize(String apiKey) {
        if (initialized) {
            return;
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        zaiService = new ZaiService(apiKey);
        zaiService.setSystemPrompt(
            "You are a YAWL workflow expert. Generate valid YAWL 4.0 XML from natural language descriptions. " +
            "Include proper task decompositions, conditions, and flow logic. Return only valid XML."
        );
        initialized = true;
    }

    /**
     * Initialize with environment variable API key
     */
    public static void initialize() {
        String apiKey = System.getenv("ZAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv("ANTHROPIC_API_KEY");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                "API key required. Set environment variable:\n" +
                "  export ZAI_API_KEY=your_key_here\n" +
                "  OR\n" +
                "  export ANTHROPIC_API_KEY=your_key_here"
            );
        }
        initialize(apiKey);
    }

    /**
     * Generate YAWL workflow from natural language description.
     *
     * Example:
     *   String description = "Create a purchase approval workflow where:
     *                         1. Employee submits request
     *                         2. Manager approves if under $1000
     *                         3. Director approves if over $1000
     *                         4. Finance processes payment";
     *   YSpecification spec = WorkflowArchitect.generate(description);
     *
     * @param naturalLanguageDescription Human-readable workflow description
     * @return Executable YAWL specification
     */
    public static YSpecification generate(String naturalLanguageDescription) {
        if (!initialized) {
            initialize();
        }

        if (naturalLanguageDescription == null || naturalLanguageDescription.trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow description cannot be null or empty");
        }

        // Build AI prompt with YAWL schema constraints
        String prompt = buildGenerationPrompt(naturalLanguageDescription);

        // Attempt generation with retry logic
        int maxAttempts = 3;
        YSpecification spec = null;
        String lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Get AI-generated YAWL XML
                String xmlResponse = zaiService.chat(prompt);

                // Extract XML from response (may be wrapped in markdown)
                String cleanXml = extractXml(xmlResponse);

                // Parse and validate
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(cleanXml);

                if (specs != null && !specs.isEmpty()) {
                    spec = specs.get(0);
                    System.out.println("✅ Successfully generated workflow: " + spec.getSpecificationID());
                    return spec;
                }

                lastError = "XML parsing returned empty specification list";

            } catch (Exception e) {
                lastError = e.getMessage();

                // If not last attempt, refine prompt with error feedback
                if (attempt < maxAttempts) {
                    System.out.println("⚠️  Attempt " + attempt + " failed: " + lastError);
                    System.out.println("🔄 Retrying with error feedback...");
                    prompt = buildRefinementPrompt(naturalLanguageDescription, lastError);
                } else {
                    System.out.println("❌ All attempts failed");
                }
            }
        }

        // All attempts failed
        throw new RuntimeException(
            "Failed to generate valid YAWL workflow after " + maxAttempts + " attempts.\n" +
            "Last error: " + lastError + "\n\n" +
            "Suggestions:\n" +
            "  1. Simplify your workflow description\n" +
            "  2. Break complex workflows into smaller parts\n" +
            "  3. Provide more specific task names and conditions\n" +
            "  4. Check that your API key has sufficient credits"
        );
    }

    /**
     * Build the initial generation prompt
     */
    private static String buildGenerationPrompt(String description) {
        return String.format(
            "Generate a valid YAWL 4.0 workflow XML specification for the following workflow:\n\n" +
            "%s\n\n" +
            "Requirements:\n" +
            "  - Use YAWL Schema 4.0 format\n" +
            "  - Include proper specificationID with unique identifier\n" +
            "  - Define input and output conditions\n" +
            "  - Create task elements with decompositions\n" +
            "  - Add flow elements connecting tasks\n" +
            "  - Include proper namespaces and schema references\n" +
            "  - Return ONLY the XML, no explanations or markdown\n\n" +
            "Example structure:\n" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<specificationSet xmlns=\"http://www.yawlfoundation.org/yawlschema\" ...>\n" +
            "  <specification uri=\"workflow_name\">\n" +
            "    <decomposition id=\"root_net\" isRootNet=\"true\" xsi:type=\"NetFactsType\">\n" +
            "      <processControlElements>\n" +
            "        <inputCondition id=\"input\"/>\n" +
            "        <task id=\"task_1\">...</task>\n" +
            "        <outputCondition id=\"output\"/>\n" +
            "      </processControlElements>\n" +
            "    </decomposition>\n" +
            "  </specification>\n" +
            "</specificationSet>",
            description
        );
    }

    /**
     * Build refinement prompt with error feedback
     */
    private static String buildRefinementPrompt(String description, String error) {
        return String.format(
            "The previous YAWL XML generation failed with error:\n%s\n\n" +
            "Please generate a corrected YAWL 4.0 workflow XML for:\n%s\n\n" +
            "Fix the error and return ONLY valid YAWL XML, no explanations.",
            error,
            description
        );
    }

    /**
     * Extract XML from AI response (may be wrapped in markdown code blocks)
     */
    private static String extractXml(String response) {
        if (response == null) {
            throw new IllegalArgumentException("AI response was null");
        }

        // Remove markdown code blocks if present
        String cleaned = response.trim();

        // Pattern: ```xml ... ```
        Pattern xmlBlock = Pattern.compile("```xml\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher xmlMatcher = xmlBlock.matcher(cleaned);
        if (xmlMatcher.find()) {
            cleaned = xmlMatcher.group(1).trim();
        } else {
            // Pattern: ``` ... ```
            Pattern codeBlock = Pattern.compile("```\\s*([\\s\\S]*?)```");
            Matcher codeMatcher = codeBlock.matcher(cleaned);
            if (codeMatcher.find()) {
                cleaned = codeMatcher.group(1).trim();
            }
        }

        // Verify it starts with XML declaration or root element
        if (!cleaned.startsWith("<?xml") && !cleaned.startsWith("<specificationSet")) {
            throw new IllegalArgumentException(
                "Response does not appear to be valid XML.\n" +
                "Response preview: " + cleaned.substring(0, Math.min(200, cleaned.length()))
            );
        }

        return cleaned;
    }

    /**
     * Optimize existing workflow using AI analysis.
     *
     * Analyzes workflow for:
     *   - Bottlenecks (sequential tasks that could be parallel)
     *   - Redundant steps
     *   - Missing error handlers
     *   - Resource allocation inefficiencies
     *
     * @param spec Existing workflow specification
     * @return Optimized specification with AI suggestions
     */
    public static YSpecification optimize(YSpecification spec) {
        if (!initialized) {
            initialize();
        }

        if (spec == null) {
            throw new IllegalArgumentException("Specification cannot be null");
        }

        try {
            // Convert spec to XML
            String currentXml = YMarshal.marshal(spec);

            // Build optimization prompt
            String prompt = String.format(
                "Analyze this YAWL workflow and suggest optimizations:\n\n" +
                "%s\n\n" +
                "Analyze for:\n" +
                "  1. Parallelization opportunities (tasks that could run in parallel)\n" +
                "  2. Redundant or unnecessary steps\n" +
                "  3. Missing error handling\n" +
                "  4. Resource allocation improvements\n" +
                "  5. Simplification opportunities\n\n" +
                "Return:\n" +
                "  1. List of specific improvements\n" +
                "  2. Optimized YAWL XML (if improvements found)\n" +
                "  3. Expected performance impact\n\n" +
                "Format response as:\n" +
                "IMPROVEMENTS:\n" +
                "[list of improvements]\n\n" +
                "OPTIMIZED XML:\n" +
                "[optimized XML or 'No significant optimizations needed']",
                currentXml
            );

            // Get AI analysis
            String response = zaiService.chat(prompt);

            // Check if optimizations were suggested
            if (response.contains("No significant optimizations") ||
                response.contains("already well-optimized")) {
                System.out.println("✅ Workflow is already well-optimized");
                return spec;
            }

            // Extract optimized XML
            String optimizedXml = extractOptimizedXml(response);

            if (optimizedXml != null) {
                // Parse and validate optimized spec
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(optimizedXml);

                if (specs != null && !specs.isEmpty()) {
                    System.out.println("✅ Workflow optimized successfully");
                    printImprovements(response);
                    return specs.get(0);
                }
            }

            // No valid optimizations, return original
            System.out.println("⚠️  Could not apply optimizations, returning original");
            return spec;

        } catch (Exception e) {
            throw new RuntimeException("Failed to optimize workflow: " + e.getMessage(), e);
        }
    }

    /**
     * Extract optimized XML from AI response
     */
    private static String extractOptimizedXml(String response) {
        // Look for XML section
        Pattern xmlSection = Pattern.compile("OPTIMIZED XML:\\s*([\\s\\S]*?)(?:$|\\n\\n[A-Z]+:)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = xmlSection.matcher(response);

        if (matcher.find()) {
            String xmlContent = matcher.group(1).trim();
            if (xmlContent.contains("<?xml") || xmlContent.contains("<specificationSet")) {
                return extractXml(xmlContent);
            }
        }

        // Fallback: try to extract any XML
        return extractXml(response);
    }

    /**
     * Print improvements from optimization response
     */
    private static void printImprovements(String response) {
        Pattern improvementsPattern = Pattern.compile("IMPROVEMENTS:\\s*([\\s\\S]*?)(?:OPTIMIZED XML:|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = improvementsPattern.matcher(response);

        if (matcher.find()) {
            String improvements = matcher.group(1).trim();
            System.out.println("\n📊 Optimization Improvements:");
            System.out.println(improvements);
            System.out.println();
        }
    }

    /**
     * Auto-fix broken workflow specifications.
     *
     * Common fixes:
     *   - Missing output conditions
     *   - Dangling tasks (no incoming/outgoing flows)
     *   - Invalid XPath expressions
     *   - Schema violations
     *
     * @param brokenXml Invalid YAWL XML
     * @return Fixed and validated specification
     */
    public static YSpecification autoFix(String brokenXml) {
        if (!initialized) {
            initialize();
        }

        if (brokenXml == null || brokenXml.trim().isEmpty()) {
            throw new IllegalArgumentException("XML cannot be null or empty");
        }

        int maxAttempts = 3;
        String currentXml = brokenXml;
        String lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Try to parse
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(currentXml);

                if (specs != null && !specs.isEmpty()) {
                    System.out.println("✅ Successfully fixed workflow after " + attempt + " attempt(s)");
                    return specs.get(0);
                }

                lastError = "Parsing returned empty specification list";

            } catch (Exception e) {
                lastError = e.getMessage();

                if (attempt < maxAttempts) {
                    System.out.println("⚠️  Attempt " + attempt + " failed: " + lastError);
                    System.out.println("🔄 Attempting AI-powered fix...");

                    // Build fix prompt
                    String fixPrompt = String.format(
                        "Fix this broken YAWL XML. The error is:\n\n%s\n\n" +
                        "Broken XML:\n%s\n\n" +
                        "Return ONLY the corrected YAWL XML, no explanations.\n" +
                        "Ensure it's valid YAWL 4.0 schema compliant.",
                        lastError,
                        currentXml
                    );

                    // Get AI fix
                    String response = zaiService.chat(fixPrompt);
                    currentXml = extractXml(response);

                    System.out.println("🤖 AI suggested fix, validating...");
                } else {
                    System.out.println("❌ All fix attempts failed");
                }
            }
        }

        // All attempts failed
        throw new RuntimeException(
            "Failed to auto-fix workflow after " + maxAttempts + " attempts.\n" +
            "Last error: " + lastError + "\n\n" +
            "The workflow XML may have fundamental structural issues.\n" +
            "Consider regenerating from scratch using generate() method."
        );
    }

    /**
     * Generate test cases for workflow specification.
     *
     * Creates:
     *   - Happy path test
     *   - Exception path tests
     *   - Boundary condition tests
     *   - Concurrency tests (if parallel tasks exist)
     *
     * @param spec Workflow specification to test
     * @return JUnit test class source code
     */
    public static String generateTests(YSpecification spec) {
        if (!initialized) {
            initialize();
        }

        if (spec == null) {
            throw new IllegalArgumentException("Specification cannot be null");
        }

        try {
            // Convert spec to XML
            String workflowXml = YMarshal.marshal(spec);
            String specId = spec.getSpecificationID().toString();

            // Build test generation prompt
            String prompt = String.format(
                "Generate comprehensive JUnit test cases for this YAWL workflow:\n\n" +
                "%s\n\n" +
                "Create tests for:\n" +
                "  1. Happy path (successful execution)\n" +
                "  2. Error paths (exception handling)\n" +
                "  3. Boundary conditions\n" +
                "  4. Parallel execution paths (if any)\n" +
                "  5. Data validation\n\n" +
                "Requirements:\n" +
                "  - Use JUnit 4 framework\n" +
                "  - Test class name: Test%s\n" +
                "  - Include setup() and tearDown() methods\n" +
                "  - Use descriptive test method names\n" +
                "  - Add assertions for expected outcomes\n" +
                "  - Include comments explaining each test\n\n" +
                "Return ONLY the Java test class code, no explanations.",
                workflowXml,
                sanitizeClassName(specId)
            );

            // Get AI-generated tests
            String response = zaiService.chat(prompt);

            // Extract Java code (real generated code from AI)
            String generatedJavaClass = extractJavaCode(response);

            System.out.println("✅ Generated test cases for workflow: " + specId);

            return generatedJavaClass;

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate tests: " + e.getMessage(), e);
        }
    }

    /**
     * Extract Java code from AI response
     */
    private static String extractJavaCode(String response) {
        if (response == null) {
            throw new IllegalArgumentException("AI response was null");
        }

        String cleaned = response.trim();

        // Pattern: ```java ... ```
        Pattern javaBlock = Pattern.compile("```java\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
        Matcher javaMatcher = javaBlock.matcher(cleaned);
        if (javaMatcher.find()) {
            return javaMatcher.group(1).trim();
        }

        // Pattern: ``` ... ```
        Pattern codeBlock = Pattern.compile("```\\s*([\\s\\S]*?)```");
        Matcher codeMatcher = codeBlock.matcher(cleaned);
        if (codeMatcher.find()) {
            return codeMatcher.group(1).trim();
        }

        // No code block found, return as-is
        return cleaned;
    }

    /**
     * Sanitize specification ID for use as class name
     */
    private static String sanitizeClassName(String specId) {
        // Remove invalid characters and ensure starts with letter
        String sanitized = specId.replaceAll("[^a-zA-Z0-9_]", "_");

        if (!Character.isJavaIdentifierStart(sanitized.charAt(0))) {
            sanitized = "Workflow_" + sanitized;
        }

        return sanitized;
    }

    /**
     * Get initialization status
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        try {
            // Initialize with API key from environment
            initialize();

            // Example: Generate a simple workflow
            String description = "Create a simple approval workflow where:\n" +
                "1. User submits a request\n" +
                "2. Manager reviews and approves\n" +
                "3. System processes the approval";

            System.out.println("🤖 Generating workflow from description...\n");
            YSpecification spec = generate(description);

            System.out.println("\n✅ Workflow generated successfully!");
            System.out.println("Specification ID: " + spec.getSpecificationID());

            // Example: Generate tests
            System.out.println("\n🧪 Generating test cases...\n");
            String junitClass = generateTests(spec);

            System.out.println("Generated JUnit class:\n");
            System.out.println(junitClass);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
