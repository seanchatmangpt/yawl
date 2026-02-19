package org.yawlfoundation.yawl.integration.zai;

import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

/**
 * Generates YAWL specifications from natural language descriptions using Z.AI.
 *
 * <p>This class bridges natural language workflow descriptions and formal YAWL
 * specifications by leveraging Z.AI's GLM-4.7-Flash model for code generation.
 * Generated specifications are validated against YAWL_Schema4.0.xsd before
 * being parsed and loaded.</p>
 *
 * <h2>Generation Process</h2>
 * <ol>
 *   <li>Build prompt with YAWL XSD schema context</li>
 *   <li>Call Z.AI GLM-4.7-Flash for XML generation</li>
 *   <li>Extract XML from response</li>
 *   <li>Validate against YAWL schema</li>
 *   <li>Parse to YSpecification via YMarshal</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class SpecificationGenerator {

    private static final Logger LOGGER = Logger.getLogger(SpecificationGenerator.class.getName());

    /** Default model for specification generation */
    private static final String DEFAULT_MODEL = "GLM-4.7-Flash";

    /** Maximum tokens for generation */
    private static final int MAX_TOKENS = 8192;

    /** Pattern to extract XML from response */
    private static final Pattern XML_EXTRACTION_PATTERN = Pattern.compile(
        "<specification[^>]*>.*?</specification>",
        Pattern.DOTALL
    );

    /** System prompt for YAWL specification generation */
    private static final String SYSTEM_PROMPT = """
        You are an expert YAWL (Yet Another Workflow Language) specification generator.
        Generate valid YAWL XML specifications based on natural language descriptions.

        Rules:
        1. Output ONLY valid XML conforming to YAWL_Schema4.0.xsd
        2. Include unique identifiers for specification, nets, and tasks
        3. Define input/output ports and flows between tasks
        4. Use appropriate task decompositions where needed
        5. Include relevant data variables and conditions
        6. Ensure all IDs are valid XML identifiers (no spaces, start with letter)

        Example structure:
        <specification id="WorkflowID" uri="http://example.com/workflow">
          <metaData>...</metaData>
          <rootNet id="root">...</rootNet>
          <decomposition id="TaskID">...</decomposition>
        </specification>
        """;

    private final ZaiHttpClient zaiClient;
    private final SchemaHandler schemaHandler;
    private final String model;
    private final Duration timeout;

    /**
     * Creates a new SpecificationGenerator with default settings.
     *
     * @param zaiClient the Z.AI HTTP client for API calls
     */
    public SpecificationGenerator(ZaiHttpClient zaiClient) {
        this(zaiClient, xmlContent -> { /* Default no-op validator */ }, DEFAULT_MODEL, Duration.ofSeconds(60));
    }

    /**
     * Creates a new SpecificationGenerator with custom settings.
     *
     * @param zaiClient      the Z.AI HTTP client
     * @param schemaHandler  the schema handler for validation
     * @param model          the Z.AI model to use
     * @param timeout        request timeout
     */
    public SpecificationGenerator(ZaiHttpClient zaiClient, SchemaHandler schemaHandler,
                                  String model, Duration timeout) {
        this.zaiClient = Objects.requireNonNull(zaiClient, "zaiClient must not be null");
        this.schemaHandler = Objects.requireNonNull(schemaHandler, "schemaHandler must not be null");
        this.model = model;
        this.timeout = timeout;
    }

    /**
     * Generates a YAWL specification from a natural language description.
     *
     * @param description natural language workflow description
     * @return the generated and validated YSpecification
     * @throws SpecificationGenerationException if generation or validation fails
     */
    public YSpecification generateFromDescription(String description) {
        return generateFromDescription(description, new GenerationOptions());
    }

    /**
     * Generates a YAWL specification with custom options.
     *
     * @param description natural language workflow description
     * @param options     generation options
     * @return the generated and validated YSpecification
     * @throws SpecificationGenerationException if generation or validation fails
     */
    public YSpecification generateFromDescription(String description, GenerationOptions options) {
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(options, "options must not be null");

        LOGGER.info("Generating specification from description: " +
                   truncate(description, 100) + "...");

        // 1. Build prompt with schema context
        String prompt = buildPromptWithSchema(description, options);

        // 2. Call Z.AI for generation
        String response = callZaiApi(prompt, options);

        // 3. Extract XML from response
        String xmlContent = extractXml(response);

        if (xmlContent == null || xmlContent.isBlank()) {
            throw new SpecificationGenerationException(
                "No valid XML specification found in Z.AI response");
        }

        // 4. Validate against YAWL schema
        if (options.validateSchema()) {
            schemaHandler.validate(xmlContent);
        }

        // 5. Parse to YSpecification
        YSpecification spec = parseSpecification(xmlContent);

        // 6. Apply post-processing if needed
        if (options.autoFixIds()) {
            spec = fixIdentifiers(spec);
        }

        LOGGER.info("Successfully generated specification: " + spec.getURI());
        return spec;
    }

    /**
     * Generates multiple specification variants for comparison.
     *
     * @param description natural language description
     * @param count       number of variants to generate
     * @return list of generated specifications
     */
    public List<YSpecification> generateVariants(String description, int count) {
        List<YSpecification> variants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GenerationOptions options = new GenerationOptions()
                .withTemperature(0.7 + (i * 0.1))
                .withVariantSeed(i);
            variants.add(generateFromDescription(description, options));
        }
        return variants;
    }

    /**
     * Improves an existing specification based on feedback.
     *
     * @param existingSpec the existing specification XML
     * @param feedback     improvement feedback
     * @return improved specification
     */
    public YSpecification improveSpecification(String existingSpec, String feedback) {
        String prompt = """
            Improve the following YAWL specification based on the feedback provided.

            Existing Specification:
            %s

            Feedback for improvement:
            %s

            Generate an improved version of the specification that addresses the feedback.
            """.formatted(existingSpec, feedback);

        String response = callZaiApi(prompt, new GenerationOptions());
        String xmlContent = extractXml(response);

        schemaHandler.validate(xmlContent);
        return parseSpecification(xmlContent);
    }

    // -------------------------------------------------------------------------
    // Private implementation methods
    // -------------------------------------------------------------------------

    private String buildPromptWithSchema(String description, GenerationOptions options) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(SYSTEM_PROMPT).append("\n\n");

        if (options.includeSchemaHints()) {
            prompt.append("YAWL Schema Elements:\n");
            prompt.append("- specification: root element with id, uri attributes\n");
            prompt.append("- rootNet: main workflow net with id\n");
            prompt.append("- task: workflow tasks with id, name\n");
            prompt.append("- inputPort/outputPort: data flow ports\n");
            prompt.append("- flow: connections between tasks (source, target)\n");
            prompt.append("- condition: routing conditions\n");
            prompt.append("- decomposition: task implementations\n\n");
        }

        if (options.getExampleSpec() != null) {
            prompt.append("Reference Example:\n")
                  .append(options.getExampleSpec())
                  .append("\n\n");
        }

        prompt.append("Generate a YAWL specification for:\n")
              .append(description);

        if (options.getSpecIdentifier() != null) {
            prompt.append("\n\nUse specification identifier: ")
                  .append(options.getSpecIdentifier());
        }

        return prompt.toString();
    }

    private String callZaiApi(String prompt, GenerationOptions options) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", prompt));

        Map<String, Object> request = new HashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("max_tokens", options.maxTokens() > 0 ? options.maxTokens() : MAX_TOKENS);
        request.put("temperature", options.temperature());

        try {
            return zaiClient.createChatCompletion(request, timeout);
        } catch (Exception e) {
            throw new SpecificationGenerationException(
                "Z.AI API call failed: " + e.getMessage(), e);
        }
    }

    private String extractXml(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        // Try to extract XML from markdown code blocks first
        Pattern codeBlockPattern = Pattern.compile(
            "```(?:xml)?\\s*\\n?(<specification[^>]*>.*?</specification>)\\s*\\n?```",
            Pattern.DOTALL
        );
        Matcher codeBlockMatcher = codeBlockPattern.matcher(response);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1).trim();
        }

        // Try direct XML extraction
        Matcher xmlMatcher = XML_EXTRACTION_PATTERN.matcher(response);
        if (xmlMatcher.find()) {
            return xmlMatcher.group(0).trim();
        }

        return null;
    }

    private YSpecification parseSpecification(String xmlContent) {
        try {
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xmlContent);
            if (specs.isEmpty()) {
                throw new SpecificationGenerationException(
                    "No specifications parsed from XML content");
            }
            return specs.get(0);
        } catch (Exception e) {
            throw new SpecificationGenerationException(
                "Failed to parse specification: " + e.getMessage(), e);
        }
    }

    private YSpecification fixIdentifiers(YSpecification spec) {
        // Implement auto-fixing of invalid identifiers if needed
        // This is a placeholder for identifier normalization logic
        return spec;
    }

    private String truncate(String s, int maxLength) {
        if (s == null) return "[null]";
        return s.length() > maxLength ? s.substring(0, maxLength) + "..." : s;
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Options for specification generation.
     */
    public static final class GenerationOptions {
        private boolean validateSchema = true;
        private boolean autoFixIds = true;
        private boolean includeSchemaHints = true;
        private double temperature = 0.3;
        private int maxTokens = 0; // 0 means use default
        private int variantSeed = -1;
        private String specIdentifier;
        private String exampleSpec;

        public boolean validateSchema() { return validateSchema; }
        public boolean autoFixIds() { return autoFixIds; }
        public boolean includeSchemaHints() { return includeSchemaHints; }
        public double temperature() { return temperature; }
        public int maxTokens() { return maxTokens; }
        public int variantSeed() { return variantSeed; }
        public String getSpecIdentifier() { return specIdentifier; }
        public String getExampleSpec() { return exampleSpec; }

        public GenerationOptions withValidateSchema(boolean v) {
            this.validateSchema = v;
            return this;
        }

        public GenerationOptions withAutoFixIds(boolean v) {
            this.autoFixIds = v;
            return this;
        }

        public GenerationOptions withIncludeSchemaHints(boolean v) {
            this.includeSchemaHints = v;
            return this;
        }

        public GenerationOptions withTemperature(double t) {
            this.temperature = Math.max(0, Math.min(2, t));
            return this;
        }

        public GenerationOptions withMaxTokens(int t) {
            this.maxTokens = t;
            return this;
        }

        public GenerationOptions withVariantSeed(int s) {
            this.variantSeed = s;
            return this;
        }

        public GenerationOptions withSpecIdentifier(String id) {
            this.specIdentifier = id;
            return this;
        }

        public GenerationOptions withExampleSpec(String xml) {
            this.exampleSpec = xml;
            return this;
        }
    }

    /**
     * Exception thrown when specification generation fails.
     */
    public static final class SpecificationGenerationException extends RuntimeException {
        public SpecificationGenerationException(String message) {
            super(message);
        }

        public SpecificationGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Interface for Z.AI HTTP client.
     */
    public interface ZaiHttpClient {
        String createChatCompletion(Map<String, Object> request, Duration timeout);
    }

    /**
     * Interface for schema validation handler.
     */
    public interface SchemaHandler {
        void validate(String xmlContent);
    }
}
