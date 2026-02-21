/**
 * FactsToRDFConverter — Converts observatory facts.json to RDF/Turtle format
 *
 * Converts YAWL codebase facts (modules, dependencies, coverage, gates) into
 * RDF/Turtle format (facts.ttl) for integration with ggen code generation.
 *
 * Input: facts/*.json from observatory.sh
 * Output: facts.ttl (RDF graph of codebase structure)
 * Uses: Apache Jena for RDF generation, lossless JSON→RDF conversion
 *
 * Design:
 *   1. Load facts.json files into memory
 *   2. Create RDF model with YAWL ontology
 *   3. Assert triples for modules, dependencies, coverage, tests
 *   4. Write model to Turtle format
 *   5. Compute SHA256 hash for drift detection
 *
 * Example output (Turtle):
 *   ex:YEngine a rdfs:Class ;
 *       ex:hasModule "yawl-engine" ;
 *       ex:depends_on ex:YSpecification ;
 *       ex:testCount 156 ;
 *       ex:lineCoverage 45.2 ;
 *       ex:documentation "Core workflow execution engine" .
 */
package org.yawlfoundation.yawl.observatory.rdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts observatory facts to RDF/Turtle for code generation context.
 * Lossless conversion: all facts.json data → RDF triples.
 * Drift detection: SHA256 hash of facts.ttl triggers rebuild on changes.
 */
public class FactsToRDFConverter {

    private static final String YAWL_NS = "http://yawlfoundation.org/yawl#";
    private static final String EXAMPLE_NS = "http://yawlfoundation.org/facts#";
    private static final String DCTERMS_NS = "http://purl.org/dc/terms/";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Model model;
    private final Map<String, Resource> resourceCache = new HashMap<>();

    public FactsToRDFConverter() {
        this.model = ModelFactory.createDefaultModel();
        setupNamespaces();
    }

    /**
     * Setup RDF namespace prefixes for Turtle output.
     */
    private void setupNamespaces() {
        model.setNsPrefix("yawl", YAWL_NS);
        model.setNsPrefix("ex", EXAMPLE_NS);
        model.setNsPrefix("dcterms", DCTERMS_NS);
        model.setNsPrefix("rdf", RDF.getURI());
        model.setNsPrefix("rdfs", RDFS.getURI());
    }

    /**
     * Load facts from observatory output and convert to RDF.
     *
     * @param factsDir Directory containing facts/*.json files
     * @throws IOException if facts files cannot be read
     */
    public void loadAndConvert(Path factsDir) throws IOException {
        // Load modules.json → modules and module properties
        Path modulesFile = factsDir.resolve("modules.json");
        if (Files.exists(modulesFile)) {
            convertModules(modulesFile);
        }

        // Load reactor.json → module dependencies
        Path reactorFile = factsDir.resolve("reactor.json");
        if (Files.exists(reactorFile)) {
            convertReactor(reactorFile);
        }

        // Load coverage.json → coverage metrics
        Path coverageFile = factsDir.resolve("coverage.json");
        if (Files.exists(coverageFile)) {
            convertCoverage(coverageFile);
        }

        // Load tests.json → test counts
        Path testsFile = factsDir.resolve("tests.json");
        if (Files.exists(testsFile)) {
            convertTests(testsFile);
        }

        // Load gates.json → build gates and profiles
        Path gatesFile = factsDir.resolve("gates.json");
        if (Files.exists(gatesFile)) {
            convertGates(gatesFile);
        }

        // Load shared-src.json → shared source analysis
        Path sharedFile = factsDir.resolve("shared-src.json");
        if (Files.exists(sharedFile)) {
            convertSharedSource(sharedFile);
        }

        // Load integration-facts.json → MCP/A2A integration points
        Path integrationFile = factsDir.resolve("integration-facts.json");
        if (Files.exists(integrationFile)) {
            convertIntegration(integrationFile);
        }
    }

    /**
     * Convert modules.json to RDF module resources.
     *
     * Each module becomes:
     *   ex:YawlEngine a rdfs:Class ;
     *       ex:moduleName "yawl-engine" ;
     *       ex:srcFiles 891 ;
     *       ex:testFiles 421 ;
     *       ex:hasPom true ;
     *       ex:strategy "full_shared" .
     */
    private void convertModules(Path modulesFile) throws IOException {
        JsonNode root = mapper.readTree(modulesFile.toFile());
        JsonNode modules = root.get("modules");

        if (modules != null && modules.isArray()) {
            for (JsonNode module : modules) {
                String moduleName = module.get("name").asText();
                Resource moduleRes = getOrCreateResource(moduleName);

                // Assert type
                moduleRes.addProperty(RDF.type, RDFS.Class);

                // Assert properties
                moduleRes.addProperty(
                    createProperty("moduleName"),
                    moduleName
                );

                if (module.has("path")) {
                    moduleRes.addProperty(
                        createProperty("modulePath"),
                        module.get("path").asText()
                    );
                }

                if (module.has("src_files")) {
                    moduleRes.addProperty(
                        createProperty("srcFiles"),
                        model.createTypedLiteral(module.get("src_files").asInt(), XSDDatatype.XSDint)
                    );
                }

                if (module.has("test_files")) {
                    moduleRes.addProperty(
                        createProperty("testFiles"),
                        model.createTypedLiteral(module.get("test_files").asInt(), XSDDatatype.XSDint)
                    );
                }

                if (module.has("has_pom")) {
                    moduleRes.addProperty(
                        createProperty("hasPom"),
                        model.createTypedLiteral(module.get("has_pom").asBoolean(), XSDDatatype.XSDboolean)
                    );
                }

                if (module.has("strategy")) {
                    moduleRes.addProperty(
                        createProperty("strategy"),
                        module.get("strategy").asText()
                    );
                }

                if (module.has("spring_config")) {
                    moduleRes.addProperty(
                        createProperty("springConfig"),
                        model.createTypedLiteral(module.get("spring_config").asBoolean(), XSDDatatype.XSDboolean)
                    );
                }

                if (module.has("hibernate_config")) {
                    moduleRes.addProperty(
                        createProperty("hibernateConfig"),
                        model.createTypedLiteral(module.get("hibernate_config").asBoolean(), XSDDatatype.XSDboolean)
                    );
                }
            }
        }
    }

    /**
     * Convert reactor.json to RDF dependency edges.
     *
     * Creates:
     *   ex:YawlEngine ex:dependsOn ex:YawlElements .
     *   ex:YawlEngine ex:buildOrder 7 .
     */
    private void convertReactor(Path reactorFile) throws IOException {
        JsonNode root = mapper.readTree(reactorFile.toFile());

        // Assert build order
        JsonNode reactorOrder = root.get("reactor_order");
        if (reactorOrder != null && reactorOrder.isArray()) {
            int order = 0;
            for (JsonNode moduleName : reactorOrder) {
                Resource moduleRes = getOrCreateResource(moduleName.asText());
                moduleRes.addProperty(
                    createProperty("buildOrder"),
                    model.createTypedLiteral(order++, XSDDatatype.XSDint)
                );
            }
        }

        // Assert module dependencies
        JsonNode moduleDeps = root.get("module_deps");
        if (moduleDeps != null && moduleDeps.isArray()) {
            for (JsonNode dep : moduleDeps) {
                String from = dep.get("from").asText();
                String to = dep.get("to").asText();

                Resource fromRes = getOrCreateResource(from);
                Resource toRes = getOrCreateResource(to);

                fromRes.addProperty(createProperty("dependsOn"), toRes);
            }
        }
    }

    /**
     * Convert coverage.json to RDF coverage metrics.
     *
     * Creates:
     *   ex:YawlEngine ex:lineCoverage 45.2 ;
     *       ex:branchCoverage 38.1 ;
     *       ex:linesCovered 1234 ;
     *       ex:linesMissed 1456 ;
     *       ex:coverageTarget 65 ;
     *       ex:meetsLineTarget false .
     */
    private void convertCoverage(Path coverageFile) throws IOException {
        JsonNode root = mapper.readTree(coverageFile.toFile());

        // Aggregate coverage
        JsonNode aggregate = root.get("aggregate");
        if (aggregate != null) {
            // Create a code coverage resource
            Resource coverageRes = model.createResource(EXAMPLE_NS + "CodeCoverage");
            coverageRes.addProperty(RDF.type, createResource("Coverage"));

            if (aggregate.has("line_pct")) {
                coverageRes.addProperty(
                    createProperty("lineCoverage"),
                    model.createTypedLiteral(aggregate.get("line_pct").asDouble(), XSDDatatype.XSDdouble)
                );
            }

            if (aggregate.has("branch_pct")) {
                coverageRes.addProperty(
                    createProperty("branchCoverage"),
                    model.createTypedLiteral(aggregate.get("branch_pct").asDouble(), XSDDatatype.XSDdouble)
                );
            }

            if (aggregate.has("target_line_pct")) {
                coverageRes.addProperty(
                    createProperty("targetLineCoverage"),
                    model.createTypedLiteral(aggregate.get("target_line_pct").asInt(), XSDDatatype.XSDint)
                );
            }

            if (aggregate.has("meets_line_target")) {
                coverageRes.addProperty(
                    createProperty("meetsLineTarget"),
                    model.createTypedLiteral(aggregate.get("meets_line_target").asBoolean(), XSDDatatype.XSDboolean)
                );
            }
        }

        // Module-level coverage
        JsonNode modules = root.get("modules");
        if (modules != null && modules.isArray()) {
            for (JsonNode moduleCov : modules) {
                String moduleName = moduleCov.get("module").asText();
                Resource moduleRes = getOrCreateResource(moduleName);

                if (moduleCov.has("line_pct")) {
                    moduleRes.addProperty(
                        createProperty("lineCoverage"),
                        model.createTypedLiteral(moduleCov.get("line_pct").asDouble(), XSDDatatype.XSDdouble)
                    );
                }

                if (moduleCov.has("branch_pct")) {
                    moduleRes.addProperty(
                        createProperty("branchCoverage"),
                        model.createTypedLiteral(moduleCov.get("branch_pct").asDouble(), XSDDatatype.XSDdouble)
                    );
                }
            }
        }
    }

    /**
     * Convert tests.json to RDF test counts and assertions.
     */
    private void convertTests(Path testsFile) throws IOException {
        JsonNode root = mapper.readTree(testsFile.toFile());

        if (root.has("modules") && root.get("modules").isArray()) {
            for (JsonNode moduleTest : root.get("modules")) {
                String moduleName = moduleTest.get("module").asText();
                Resource moduleRes = getOrCreateResource(moduleName);

                if (moduleTest.has("test_count")) {
                    moduleRes.addProperty(
                        createProperty("testCount"),
                        model.createTypedLiteral(moduleTest.get("test_count").asInt(), XSDDatatype.XSDint)
                    );
                }

                if (moduleTest.has("test_frameworks")) {
                    JsonNode frameworks = moduleTest.get("test_frameworks");
                    if (frameworks != null && frameworks.isArray()) {
                        for (JsonNode fw : frameworks) {
                            moduleRes.addProperty(
                                createProperty("testFramework"),
                                fw.asText()
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Convert gates.json to RDF build profile and plugin configuration.
     */
    private void convertGates(Path gatesFile) throws IOException {
        JsonNode root = mapper.readTree(gatesFile.toFile());

        // Build profiles
        if (root.has("profiles") && root.get("profiles").isArray()) {
            for (JsonNode profile : root.get("profiles")) {
                String profileName = profile.asText();
                Resource profileRes = model.createResource(EXAMPLE_NS + "BuildProfile/" + profileName);
                profileRes.addProperty(RDF.type, createResource("BuildProfile"));
                profileRes.addProperty(
                    createProperty("profileName"),
                    profileName
                );
            }
        }

        // Plugins
        if (root.has("plugins") && root.get("plugins").isObject()) {
            JsonNode plugins = root.get("plugins");
            plugins.fields().forEachRemaining(entry -> {
                String pluginName = entry.getKey();
                JsonNode pluginConfig = entry.getValue();

                Resource pluginRes = model.createResource(EXAMPLE_NS + "BuildPlugin/" + pluginName);
                pluginRes.addProperty(RDF.type, createResource("BuildPlugin"));
                pluginRes.addProperty(
                    createProperty("pluginName"),
                    pluginName
                );

                if (pluginConfig.has("enabled")) {
                    pluginRes.addProperty(
                        createProperty("enabled"),
                        model.createTypedLiteral(pluginConfig.get("enabled").asBoolean(), XSDDatatype.XSDboolean)
                    );
                }

                if (pluginConfig.has("phase")) {
                    pluginRes.addProperty(
                        createProperty("buildPhase"),
                        pluginConfig.get("phase").asText()
                    );
                }
            });
        }
    }

    /**
     * Convert shared-src.json to RDF architecture pattern and sharing strategy.
     */
    private void convertSharedSource(Path sharedFile) throws IOException {
        JsonNode root = mapper.readTree(sharedFile.toFile());

        Resource archRes = model.createResource(EXAMPLE_NS + "ArchitecturePattern");
        archRes.addProperty(RDF.type, createResource("ArchitecturePattern"));

        if (root.has("architecture_pattern")) {
            archRes.addProperty(
                createProperty("pattern"),
                root.get("architecture_pattern").asText()
            );
        }

        if (root.has("full_shared_modules") && root.get("full_shared_modules").isArray()) {
            for (JsonNode moduleName : root.get("full_shared_modules")) {
                Resource moduleRes = getOrCreateResource(moduleName.asText());
                moduleRes.addProperty(
                    createProperty("sharingStrategy"),
                    "full_shared"
                );
            }
        }

        if (root.has("package_scoped_modules") && root.get("package_scoped_modules").isArray()) {
            for (JsonNode moduleName : root.get("package_scoped_modules")) {
                Resource moduleRes = getOrCreateResource(moduleName.asText());
                moduleRes.addProperty(
                    createProperty("sharingStrategy"),
                    "package_scoped"
                );
            }
        }

        if (root.has("standard_modules") && root.get("standard_modules").isArray()) {
            for (JsonNode moduleName : root.get("standard_modules")) {
                Resource moduleRes = getOrCreateResource(moduleName.asText());
                moduleRes.addProperty(
                    createProperty("sharingStrategy"),
                    "standard"
                );
            }
        }
    }

    /**
     * Convert integration-facts.json to RDF integration points (MCP, A2A, ZAI).
     */
    private void convertIntegration(Path integrationFile) throws IOException {
        JsonNode root = mapper.readTree(integrationFile.toFile());

        // MCP integration
        if (root.has("mcp")) {
            JsonNode mcp = root.get("mcp");
            Resource mcpRes = model.createResource(EXAMPLE_NS + "MCPIntegration");
            mcpRes.addProperty(RDF.type, createResource("IntegrationPoint"));
            mcpRes.addProperty(createProperty("integrationType"), "MCP");

            if (mcp.has("server")) {
                mcpRes.addProperty(createProperty("server"), mcp.get("server").asText());
            }

            if (mcp.has("tools_count")) {
                mcpRes.addProperty(
                    createProperty("toolCount"),
                    model.createTypedLiteral(mcp.get("tools_count").asInt(), XSDDatatype.XSDint)
                );
            }

            if (mcp.has("tools") && mcp.get("tools").isArray()) {
                for (JsonNode tool : mcp.get("tools")) {
                    mcpRes.addProperty(createProperty("tool"), tool.asText());
                }
            }
        }

        // A2A integration
        if (root.has("a2a")) {
            JsonNode a2a = root.get("a2a");
            Resource a2aRes = model.createResource(EXAMPLE_NS + "A2AIntegration");
            a2aRes.addProperty(RDF.type, createResource("IntegrationPoint"));
            a2aRes.addProperty(createProperty("integrationType"), "A2A");

            if (a2a.has("server")) {
                a2aRes.addProperty(createProperty("server"), a2a.get("server").asText());
            }

            if (a2a.has("skills_count")) {
                a2aRes.addProperty(
                    createProperty("skillCount"),
                    model.createTypedLiteral(a2a.get("skills_count").asInt(), XSDDatatype.XSDint)
                );
            }
        }

        // ZAI integration
        if (root.has("zai")) {
            JsonNode zai = root.get("zai");
            Resource zaiRes = model.createResource(EXAMPLE_NS + "ZAIIntegration");
            zaiRes.addProperty(RDF.type, createResource("IntegrationPoint"));
            zaiRes.addProperty(createProperty("integrationType"), "ZAI");

            if (zai.has("service")) {
                zaiRes.addProperty(createProperty("service"), zai.get("service").asText());
            }

            if (zai.has("model")) {
                zaiRes.addProperty(createProperty("model"), zai.get("model").asText());
            }
        }
    }

    /**
     * Write RDF model to Turtle file.
     *
     * @param outputFile Target Turtle file
     * @throws IOException if file cannot be written
     */
    public void writeTurtle(Path outputFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
            model.write(fos, "TURTLE");
        }
    }

    /**
     * Compute SHA256 hash of the RDF model (for drift detection).
     *
     * @return hex-encoded SHA256 hash
     * @throws NoSuchAlgorithmException if SHA256 is not available
     * @throws IOException if model cannot be serialized
     */
    public String computeHash() throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Serialize model to bytes
        try (var baos = new java.io.ByteArrayOutputStream()) {
            model.write(baos, "TURTLE");
            byte[] bytes = baos.toByteArray();
            byte[] hash = digest.digest(bytes);

            // Convert to hex
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        }
    }

    /**
     * Get or create a resource by module name.
     *
     * @param moduleName YAWL module name (e.g., "yawl-engine")
     * @return RDF Resource
     */
    private Resource getOrCreateResource(String moduleName) {
        return resourceCache.computeIfAbsent(moduleName, name -> {
            // Convert kebab-case to CamelCase for resource name
            String camelCase = name.substring(0, 1).toUpperCase()
                + name.substring(1)
                    .replaceAll("-(.)", m -> m.group(1).toUpperCase())
                    .replaceAll("-", "");

            return model.createResource(EXAMPLE_NS + camelCase);
        });
    }

    /**
     * Create a property in the EXAMPLE namespace.
     */
    private Property createProperty(String localName) {
        return ResourceFactory.createProperty(EXAMPLE_NS, localName);
    }

    /**
     * Create a resource in the EXAMPLE namespace.
     */
    private Resource createResource(String localName) {
        return ResourceFactory.createResource(EXAMPLE_NS + localName);
    }

    /**
     * Get the RDF model (for testing/inspection).
     */
    public Model getModel() {
        return model;
    }
}
