/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CONSTRUCT phase orchestrator — the μ transformation pipeline for the construct model.
 *
 * <h2>Construct Execution Model: A = μ(O)</h2>
 * This class implements the μ pipeline for SPARQL CONSTRUCT-native code generation.
 * Unlike the Λ (Build) phase, which compiles artifacts that already exist, the
 * CONSTRUCT phase <em>derives</em> artifacts by rewriting the ontology graph O.
 * Each {@link ConstructRule} is a formally stated graph rewriting step:
 * <ol>
 *   <li>Match patterns in O (WHERE clause)</li>
 *   <li>Produce derived triples into O (CONSTRUCT clause)</li>
 *   <li>Render derived graph through Tera template → artifact file</li>
 * </ol>
 *
 * <h2>Pipeline</h2>
 * <pre>
 *   YAWL workflow (process order)
 *     → enabled transitions select which CONSTRUCT rules to fire
 *     → each rule enriches O with derived triples
 *     → Tera templates render derived graph → artifacts
 *     → ConstructReceipt chain records what was produced
 *     → H (guards) + Q (invariants) phases verify the artifacts
 * </pre>
 *
 * <h2>Coordination without messaging</h2>
 * Because all state lives in O as RDF triples, CONSTRUCT rules coordinate
 * through graph state — not inter-agent messages. A rule fires when its
 * YAWL transition is enabled (derivable from O). Rules wait by polling O,
 * not by waiting for messages. This eliminates coordination overhead.
 *
 * <h2>Usage</h2>
 * <pre>
 *   ConstructPhase cp = new ConstructPhase(Paths.get("/home/user/yawl"));
 *   List&lt;ConstructRule&gt; rules = cp.loadRules();
 *   boolean ok = cp.executeConstruct(rules);
 *   if (ok) {
 *       System.out.println(cp.getSummary());
 *       // Proceed to H (guards) phase
 *   }
 * </pre>
 *
 * @see ConstructRule
 * @see ConstructReceipt
 * @see BuildPhase
 */
public class ConstructPhase {

    /** Section header that marks the start of inference rules in ggen.toml. */
    private static final Pattern RULE_SECTION = Pattern.compile(
        "^\\[\\[inference\\.rules\\]\\]");
    /** Key-value pairs in TOML (handles quoted and unquoted values). */
    private static final Pattern TOML_KV = Pattern.compile(
        "^(\\w+)\\s*=\\s*\"([^\"]*)\"|^(\\w+)\\s*=\\s*(.+)$");
    /** Start of a triple-quoted multiline string. */
    private static final Pattern TRIPLE_QUOTE_START = Pattern.compile(
        "^(\\w+)\\s*=\\s*\"\"\"");
    /** Receipt file name within .ggen/ directory. */
    private static final String RECEIPT_FILE = ".ggen/construct-receipt.json";

    private final Path repoRoot;
    private final Path ggenToml;
    private final Path receiptFile;

    /**
     * Create a CONSTRUCT phase orchestrator for the given repository.
     *
     * @param repoRoot path to YAWL repository root (must exist)
     * @throws IllegalArgumentException if repoRoot is null or not a directory
     */
    public ConstructPhase(Path repoRoot) {
        if (repoRoot == null) {
            throw new IllegalArgumentException("Repository root must not be null");
        }
        if (!Files.isDirectory(repoRoot)) {
            throw new IllegalArgumentException(
                "Repository root must be a directory: " + repoRoot);
        }
        this.repoRoot = repoRoot;
        this.ggenToml = repoRoot.resolve("ggen.toml");
        this.receiptFile = repoRoot.resolve(RECEIPT_FILE);
    }

    /**
     * Load CONSTRUCT rules from {@code ggen.toml} in the repository root.
     *
     * Parses all {@code [[inference.rules]]} sections from the TOML file.
     * Rules with inline SPARQL CONSTRUCT queries and file-referenced queries
     * are both supported.
     *
     * @return ordered list of CONSTRUCT rules (preserves ggen.toml declaration order)
     * @throws IOException if ggen.toml cannot be read
     * @throws IllegalStateException if ggen.toml does not exist
     */
    public List<ConstructRule> loadRules() throws IOException {
        if (!Files.exists(ggenToml)) {
            throw new IllegalStateException("ggen.toml not found at: " + ggenToml);
        }

        List<String> lines = Files.readAllLines(ggenToml);
        List<ConstructRule> rules = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i).trim();

            if (RULE_SECTION.matcher(line).matches()) {
                // Parse this rule block
                ParseResult result = parseRuleBlock(lines, i + 1);
                if (result.rule != null) {
                    rules.add(result.rule);
                }
                i = result.nextLineIndex;
            } else {
                i++;
            }
        }

        return Collections.unmodifiableList(rules);
    }

    /**
     * Validate all rules without executing them.
     *
     * Checks each rule's SPARQL syntax (inline queries only) and verifies
     * that file-referenced queries point to files that exist on disk.
     *
     * @param rules rules to validate (from {@link #loadRules()})
     * @return map of rule name → list of error messages; empty map means all valid
     */
    public Map<String, List<String>> validateRules(List<ConstructRule> rules) {
        Map<String, List<String>> errors = new LinkedHashMap<>();

        for (ConstructRule rule : rules) {
            List<String> ruleErrors = new ArrayList<>();

            // Check inline SPARQL syntax
            ruleErrors.addAll(rule.validateSyntax(rule.getName()));

            // Check file references exist
            if (rule.isFileReference()) {
                Path queryFile = repoRoot.resolve(rule.getConstruct());
                if (!Files.exists(queryFile)) {
                    ruleErrors.add("Query file not found: " + queryFile);
                }
            }

            // Check template file exists if declared
            if (rule.hasTemplate()) {
                Path templateFile = repoRoot.resolve(rule.getTemplate());
                if (!Files.exists(templateFile)) {
                    ruleErrors.add("Template file not found: " + templateFile);
                }
            }

            if (!ruleErrors.isEmpty()) {
                errors.put(rule.getName(), ruleErrors);
            }
        }

        return Collections.unmodifiableMap(errors);
    }

    /**
     * Execute all CONSTRUCT rules and record receipts.
     *
     * Rules are executed in declaration order. When a rule has a YAWL transition
     * specified, the transition name is passed to the external ggen-sync.sh script
     * which handles actual SPARQL execution against the ontology graph using the
     * configured SPARQL engine (Oxigraph/QLever).
     *
     * @param rules ordered list of rules to execute
     * @return true if all rules GREEN or WARN (none FAIL); false otherwise
     * @throws IOException          if receipts cannot be written
     * @throws InterruptedException if a subprocess is interrupted
     */
    public boolean executeConstruct(List<ConstructRule> rules)
            throws IOException, InterruptedException {

        // Prepare receipt directory
        Files.createDirectories(receiptFile.getParent());

        boolean allPass = true;

        for (ConstructRule rule : rules) {
            long start = System.currentTimeMillis();
            ConstructReceipt receipt;

            try {
                receipt = executeRule(rule, start);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                receipt = ConstructReceipt.fail(rule.getName(), elapsed,
                    "Execution error: " + e.getMessage());
            }

            receipt.emitTo(receiptFile);

            if (receipt.isFail()) {
                allPass = false;
            }
        }

        return allPass;
    }

    /**
     * Check if a receipt chain exists from a previous run.
     *
     * @return true if the construct-receipt.json file exists and is non-empty
     */
    public boolean hasReceipts() {
        return Files.isRegularFile(receiptFile) && Files.isReadable(receiptFile);
    }

    /**
     * Load the current receipt chain from disk.
     *
     * @return ordered map of rule name → ConstructReceipt (empty if no receipts)
     * @throws IOException if the receipt file exists but cannot be parsed
     */
    public Map<String, ConstructReceipt> getReceiptChain() throws IOException {
        return ConstructReceipt.loadChain(receiptFile);
    }

    /**
     * Check if the CONSTRUCT phase results are ready for the H (guards) phase.
     *
     * Requirements:
     * <ul>
     *   <li>Receipt file exists and is non-empty</li>
     *   <li>No receipts have status FAIL</li>
     * </ul>
     *
     * @return true if ready for guards phase
     * @throws IOException if receipt file cannot be read
     */
    public boolean isReadyForGuardsPhase() throws IOException {
        if (!hasReceipts()) return false;
        Map<String, ConstructReceipt> chain = getReceiptChain();
        return !chain.isEmpty() && ConstructReceipt.isChainPass(chain);
    }

    /**
     * Get path to the CONSTRUCT phase receipt file.
     *
     * @return path to .ggen/construct-receipt.json
     */
    public Path getReceiptFile() {
        return receiptFile;
    }

    /**
     * Get the ggen.toml path used by this phase.
     */
    public Path getGgenToml() {
        return ggenToml;
    }

    /**
     * Emit a human-readable summary of the CONSTRUCT phase execution.
     *
     * @return formatted multi-line summary string
     * @throws IOException if receipt file cannot be read
     */
    public String getSummary() throws IOException {
        Map<String, ConstructReceipt> chain = getReceiptChain();
        StringBuilder sb = new StringBuilder();
        sb.append("=== CONSTRUCT Phase Summary ===\n");

        if (chain.isEmpty()) {
            sb.append("  (No construct receipts found)\n");
            return sb.toString();
        }

        for (ConstructReceipt r : chain.values()) {
            sb.append(String.format("  %s: %s (%d triples, %dms)\n",
                r.getRule(), r.getStatus(), r.getTriplesProduced(), r.getElapsedMs()));

            if (!r.getArtifactsGenerated().isEmpty()) {
                for (String artifact : r.getArtifactsGenerated()) {
                    sb.append(String.format("    artifact: %s\n", artifact));
                }
            }
            if (r.getErrorMessage() != null) {
                sb.append(String.format("    error: %s\n", r.getErrorMessage()));
            }
        }

        sb.append(String.format("  Totals: %d triples, %dms\n",
            ConstructReceipt.totalTriplesProduced(chain),
            ConstructReceipt.totalElapsed(chain)));

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Execute a single CONSTRUCT rule via the external ggen-sync script.
     *
     * The ggen-sync.sh script handles actual SPARQL engine interaction
     * (Oxigraph/QLever) and produces a receipt JSON snippet on stdout.
     * If no script is available (e.g., integration test mode), the rule
     * is recorded as WARN with 0 triples (no error, but no SPARQL execution).
     */
    private ConstructReceipt executeRule(ConstructRule rule, long startMs)
            throws IOException, InterruptedException {

        Path syncScript = repoRoot.resolve("scripts/ggen-sync.sh");

        if (!Files.exists(syncScript)) {
            // No SPARQL engine available — record as WARN (rule validated but not executed)
            long elapsed = System.currentTimeMillis() - startMs;
            return new ConstructReceipt(
                rule.getName(), elapsed, 0, Collections.emptyList());
        }

        // Build invocation command for ggen-sync.sh --mode construct
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(syncScript.toString());
        command.add("--mode");
        command.add("construct");
        command.add("--rule");
        command.add(rule.getName());

        if (rule.hasYawlTransition()) {
            command.add("--transition");
            command.add(rule.getYawlTransition());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoRoot.toFile());

        // Capture stdout for receipt JSON, stderr for logging
        pb.redirectErrorStream(false);
        Process process = pb.start();

        // Read stdout (receipt JSON from script)
        String stdout;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            stdout = reader.lines()
                .reduce("", (a, b) -> a + b + "\n")
                .trim();
        }

        int exitCode = process.waitFor();
        long elapsed = System.currentTimeMillis() - startMs;

        if (exitCode != 0) {
            return ConstructReceipt.fail(rule.getName(), elapsed,
                "ggen-sync.sh exited with code " + exitCode);
        }

        // Parse triples produced and artifacts from script output (JSON line)
        return parseScriptOutput(rule.getName(), elapsed, stdout);
    }

    /**
     * Parse the JSON output from ggen-sync.sh into a ConstructReceipt.
     *
     * Expected format from script:
     * <pre>
     *   {"triples_produced": 12, "artifacts": ["output/Foo.java", "output/Bar.java"]}
     * </pre>
     *
     * Falls back to WARN (0 triples) if output is missing or unparseable.
     */
    private ConstructReceipt parseScriptOutput(String ruleName, long elapsed, String json) {
        if (json == null || json.isEmpty()) {
            return new ConstructReceipt(ruleName, elapsed, 0, Collections.emptyList());
        }

        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.JsonObject obj = gson.fromJson(json,
                com.google.gson.JsonObject.class);

            int triples = obj.has("triples_produced")
                ? obj.get("triples_produced").getAsInt()
                : 0;

            List<String> artifacts = new ArrayList<>();
            if (obj.has("artifacts")) {
                obj.getAsJsonArray("artifacts")
                    .forEach(e -> artifacts.add(e.getAsString()));
            }

            return new ConstructReceipt(ruleName, elapsed, triples, artifacts);

        } catch (Exception e) {
            // Output format unexpected — treat as WARN (no SPARQL result)
            return new ConstructReceipt(ruleName, elapsed, 0, Collections.emptyList());
        }
    }

    /**
     * Parse one {@code [[inference.rules]]} block from ggen.toml.
     *
     * Reads key-value pairs starting at {@code startLine} until the next
     * section header or end of file.
     *
     * @return parse result containing the rule (or null if insufficient data)
     *         and the line index to resume parsing at
     */
    private ParseResult parseRuleBlock(List<String> lines, int startLine) {
        Map<String, String> kv = new LinkedHashMap<>();
        int i = startLine;

        while (i < lines.size()) {
            String line = lines.get(i).trim();

            // Stop at next section header
            if (line.startsWith("[[") || (line.startsWith("[") && !line.startsWith("[["))) {
                break;
            }

            // Handle triple-quoted multiline values (construct = """ ... """)
            Matcher tripleStart = TRIPLE_QUOTE_START.matcher(line);
            if (tripleStart.find()) {
                String key = tripleStart.group(1);
                StringBuilder multiline = new StringBuilder();

                // Check if triple quote opens and closes on same line after the key
                String rest = line.substring(tripleStart.end());
                if (rest.contains("\"\"\"")) {
                    // All on one line: name = """value"""
                    int closeIdx = rest.indexOf("\"\"\"");
                    kv.put(key, rest.substring(0, closeIdx).trim());
                    i++;
                } else {
                    // Multiline: collect until closing """
                    if (!rest.trim().isEmpty()) {
                        multiline.append(rest).append("\n");
                    }
                    i++;
                    while (i < lines.size()) {
                        String ml = lines.get(i);
                        if (ml.contains("\"\"\"")) {
                            int closeIdx = ml.indexOf("\"\"\"");
                            multiline.append(ml, 0, closeIdx);
                            i++;
                            break;
                        }
                        multiline.append(ml).append("\n");
                        i++;
                    }
                    kv.put(key, multiline.toString());
                }
                continue;
            }

            // Handle regular key = "value" or key = value
            Matcher m = TOML_KV.matcher(line);
            if (m.matches()) {
                if (m.group(1) != null) {
                    kv.put(m.group(1), m.group(2));        // key = "value"
                } else if (m.group(3) != null) {
                    String val = m.group(4).trim();
                    // Strip surrounding quotes if present
                    if (val.startsWith("\"") && val.endsWith("\"")) {
                        val = val.substring(1, val.length() - 1);
                    }
                    kv.put(m.group(3), val);
                }
            }

            i++;
        }

        // Require at minimum: name + construct
        if (!kv.containsKey("name") || !kv.containsKey("construct")) {
            return new ParseResult(null, i);
        }

        try {
            ConstructRule rule = new ConstructRule(
                kv.get("name"),
                kv.getOrDefault("description", ""),
                kv.get("construct"),
                kv.get("template"),
                kv.get("to"),
                kv.get("yawl_transition")
            );
            return new ParseResult(rule, i);
        } catch (IllegalArgumentException e) {
            // Rule with invalid construct (not CONSTRUCT query and not .sparql file)
            // Skip it and continue parsing
            return new ParseResult(null, i);
        }
    }

    /** Internal result type for TOML parsing. */
    private static final class ParseResult {
        final ConstructRule rule;
        final int nextLineIndex;

        ParseResult(ConstructRule rule, int nextLineIndex) {
            this.rule = rule;
            this.nextLineIndex = nextLineIndex;
        }
    }
}
