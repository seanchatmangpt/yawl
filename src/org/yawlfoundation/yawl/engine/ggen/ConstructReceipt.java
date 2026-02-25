/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import com.google.gson.*;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * CONSTRUCT phase receipt — immutable audit trail for one SPARQL CONSTRUCT rule execution.
 *
 * In the construct execution model (A = μ(O)), each CONSTRUCT rule produces new
 * RDF triples in the derived graph. This receipt records the outcome of one rule:
 * how many triples were produced, which artifacts were generated, and the overall
 * status. Receipts form an append-only chain (JSONL format) analogous to
 * {@link BuildReceipt}, enabling the H (guards) and Q (invariants) phases to
 * inspect the full μ pipeline history.
 *
 * <h3>Receipt chain format (.ggen/construct-receipt.json)</h3>
 * Each line is one JSON object:
 * <pre>
 *   {"rule":"derive-task-handlers","status":"GREEN","triples_produced":12,...}
 *   {"rule":"derive-flow-metadata","status":"GREEN","triples_produced":5,...}
 * </pre>
 *
 * <h3>Status semantics</h3>
 * <ul>
 *   <li>{@code GREEN} — rule matched patterns in O and produced ≥1 triple</li>
 *   <li>{@code WARN} — rule fired but produced 0 triples (no matching patterns found)</li>
 *   <li>{@code FAIL} — rule encountered a query error or validation failure</li>
 * </ul>
 *
 * @see ConstructRule
 * @see ConstructPhase
 * @see BuildReceipt
 */
public class ConstructReceipt {

    private final String rule;
    private final String status;             // GREEN | WARN | FAIL
    private final long elapsedMs;
    private final Instant timestamp;
    private final int triplesProduced;
    private final List<String> artifactsGenerated;
    private final String errorMessage;       // null if GREEN/WARN
    private final Map<String, Object> details;

    /**
     * Create a GREEN receipt with triple count.
     *
     * @param rule             rule name from {@link ConstructRule#getName()}
     * @param elapsedMs        execution time in milliseconds
     * @param triplesProduced  count of new RDF triples added to the derived graph
     * @param artifactsGenerated list of file paths produced by template rendering
     */
    public ConstructReceipt(String rule, long elapsedMs,
                            int triplesProduced,
                            List<String> artifactsGenerated) {
        this(rule, triplesProduced > 0 ? "GREEN" : "WARN",
             elapsedMs, triplesProduced, artifactsGenerated, null, new HashMap<>());
    }

    /**
     * Create a FAIL receipt with an error description.
     *
     * @param rule         rule name
     * @param elapsedMs    execution time in milliseconds
     * @param errorMessage description of the failure
     */
    public static ConstructReceipt fail(String rule, long elapsedMs, String errorMessage) {
        return new ConstructReceipt(rule, "FAIL", elapsedMs, 0,
            Collections.emptyList(), errorMessage, new HashMap<>());
    }

    /**
     * Full constructor — all fields explicit.
     *
     * @param rule               rule name (non-empty)
     * @param status             "GREEN", "WARN", or "FAIL"
     * @param elapsedMs          execution time (non-negative)
     * @param triplesProduced    count of triples added to derived graph (≥ 0)
     * @param artifactsGenerated files produced by template rendering
     * @param errorMessage       failure description (null for GREEN/WARN)
     * @param details            additional metrics for reporting
     */
    public ConstructReceipt(String rule,
                            String status,
                            long elapsedMs,
                            int triplesProduced,
                            List<String> artifactsGenerated,
                            String errorMessage,
                            Map<String, Object> details) {
        if (rule == null || rule.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name must not be empty");
        }
        if (status == null ||
            (!status.equals("GREEN") && !status.equals("WARN") && !status.equals("FAIL"))) {
            throw new IllegalArgumentException("Status must be GREEN, WARN, or FAIL");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("Elapsed time must be non-negative");
        }
        if (triplesProduced < 0) {
            throw new IllegalArgumentException("Triples produced must be non-negative");
        }

        this.rule = rule.trim();
        this.status = status;
        this.elapsedMs = elapsedMs;
        this.timestamp = Instant.now();
        this.triplesProduced = triplesProduced;
        this.artifactsGenerated = artifactsGenerated != null
            ? Collections.unmodifiableList(new ArrayList<>(artifactsGenerated))
            : Collections.emptyList();
        this.errorMessage = errorMessage;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
    }

    /** Get the rule name. */
    public String getRule() { return rule; }

    /** Get the execution status (GREEN | WARN | FAIL). */
    public String getStatus() { return status; }

    /** Get elapsed execution time in milliseconds. */
    public long getElapsedMs() { return elapsedMs; }

    /** Get the timestamp when this receipt was created. */
    public Instant getTimestamp() { return timestamp; }

    /** Get count of RDF triples produced by the CONSTRUCT query. */
    public int getTriplesProduced() { return triplesProduced; }

    /** Get list of artifact file paths produced by template rendering. */
    public List<String> getArtifactsGenerated() { return artifactsGenerated; }

    /** Get error description for FAIL receipts, or null. */
    public String getErrorMessage() { return errorMessage; }

    /** Get additional details map (read-only copy). */
    public Map<String, Object> getDetails() { return new HashMap<>(details); }

    /** Returns true if status is GREEN (produced ≥1 triple). */
    public boolean isGreen() { return "GREEN".equals(status); }

    /** Returns true if status is GREEN or WARN (rule did not fail). */
    public boolean isPass() { return "GREEN".equals(status) || "WARN".equals(status); }

    /** Returns true if status is FAIL. */
    public boolean isFail() { return "FAIL".equals(status); }

    /**
     * Serialize to JSON and append to the receipt chain file (JSONL format).
     *
     * Creates parent directories as needed. Appends to existing file or
     * creates a new one. Each call writes exactly one JSON line.
     *
     * @param receiptFile path to .ggen/construct-receipt.json
     * @throws IOException if the file cannot be written
     */
    public void emitTo(Path receiptFile) throws IOException {
        if (receiptFile == null) {
            throw new IllegalArgumentException("Receipt file path must not be null");
        }
        Files.createDirectories(receiptFile.getParent());

        Gson gson = new GsonBuilder().serializeNulls().create();
        JsonObject json = new JsonObject();
        json.addProperty("rule", rule);
        json.addProperty("status", status);
        json.addProperty("timestamp", timestamp.toString());
        json.addProperty("elapsed_ms", elapsedMs);
        json.addProperty("triples_produced", triplesProduced);

        JsonArray artifacts = new JsonArray();
        for (String artifact : artifactsGenerated) {
            artifacts.add(artifact);
        }
        json.add("artifacts_generated", artifacts);

        if (errorMessage != null) {
            json.addProperty("error_message", errorMessage);
        }
        if (!details.isEmpty()) {
            json.add("details", gson.toJsonTree(details));
        }

        try (Writer writer = Files.newBufferedWriter(
                receiptFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND)) {
            gson.toJson(json, writer);
            writer.write("\n");
        }
    }

    /**
     * Load the full receipt chain from a JSONL file.
     *
     * Returns an empty map if the file does not exist. Entries are keyed
     * by rule name; if a rule appears multiple times, the last entry wins.
     *
     * @param receiptFile path to .ggen/construct-receipt.json
     * @return ordered map of rule name → ConstructReceipt
     * @throws IOException if the file exists but cannot be read or parsed
     */
    public static Map<String, ConstructReceipt> loadChain(Path receiptFile) throws IOException {
        Map<String, ConstructReceipt> chain = new LinkedHashMap<>();

        if (receiptFile == null || !Files.exists(receiptFile)) {
            return chain;
        }

        Gson gson = new Gson();
        List<String> lines = Files.readAllLines(receiptFile);

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            try {
                JsonObject json = gson.fromJson(line, JsonObject.class);
                String ruleName = json.get("rule").getAsString();
                String status = json.get("status").getAsString();
                long elapsedMs = json.get("elapsed_ms").getAsLong();
                int triplesProduced = json.get("triples_produced").getAsInt();

                List<String> artifacts = new ArrayList<>();
                if (json.has("artifacts_generated")) {
                    json.getAsJsonArray("artifacts_generated")
                        .forEach(e -> artifacts.add(e.getAsString()));
                }

                String errorMessage = null;
                if (json.has("error_message") && !json.get("error_message").isJsonNull()) {
                    errorMessage = json.get("error_message").getAsString();
                }

                Map<String, Object> details = new HashMap<>();
                if (json.has("details") && !json.get("details").isJsonNull()) {
                    JsonObject detailsObj = json.getAsJsonObject("details");
                    for (String key : detailsObj.keySet()) {
                        JsonElement elem = detailsObj.get(key);
                        if (elem.isJsonPrimitive()) {
                            JsonPrimitive prim = elem.getAsJsonPrimitive();
                            if (prim.isNumber()) {
                                details.put(key, prim.getAsNumber());
                            } else if (prim.isBoolean()) {
                                details.put(key, prim.getAsBoolean());
                            } else {
                                details.put(key, prim.getAsString());
                            }
                        }
                    }
                }

                chain.put(ruleName, new ConstructReceipt(
                    ruleName, status, elapsedMs, triplesProduced,
                    artifacts, errorMessage, details));

            } catch (JsonSyntaxException | NullPointerException e) {
                throw new IOException("Malformed construct receipt line: " + line, e);
            }
        }

        return chain;
    }

    /**
     * Returns true if all rules in the chain are GREEN (each produced ≥1 triple).
     *
     * @param chain receipt chain from {@link #loadChain}
     */
    public static boolean isChainGreen(Map<String, ConstructReceipt> chain) {
        if (chain == null || chain.isEmpty()) return false;
        return chain.values().stream().allMatch(ConstructReceipt::isGreen);
    }

    /**
     * Returns true if all rules in the chain passed (GREEN or WARN, none FAIL).
     *
     * @param chain receipt chain from {@link #loadChain}
     */
    public static boolean isChainPass(Map<String, ConstructReceipt> chain) {
        if (chain == null || chain.isEmpty()) return false;
        return chain.values().stream().allMatch(ConstructReceipt::isPass);
    }

    /**
     * Returns the total count of RDF triples produced across all rules.
     *
     * @param chain receipt chain from {@link #loadChain}
     */
    public static int totalTriplesProduced(Map<String, ConstructReceipt> chain) {
        if (chain == null) return 0;
        return chain.values().stream()
            .mapToInt(ConstructReceipt::getTriplesProduced)
            .sum();
    }

    /**
     * Returns the total elapsed time across all rules.
     *
     * @param chain receipt chain from {@link #loadChain}
     */
    public static long totalElapsed(Map<String, ConstructReceipt> chain) {
        if (chain == null) return 0;
        return chain.values().stream()
            .mapToLong(ConstructReceipt::getElapsedMs)
            .sum();
    }

    @Override
    public String toString() {
        return String.format(
            "ConstructReceipt{rule='%s', status='%s', triples=%d, elapsed=%dms}",
            rule, status, triplesProduced, elapsedMs);
    }
}
