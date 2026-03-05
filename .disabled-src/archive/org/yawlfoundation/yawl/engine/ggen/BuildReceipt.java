/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import com.google.gson.*;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build receipt — immutable audit trail of Λ (build) phase execution.
 *
 * Emitted to .ggen/build-receipt.json for H (guards) phase consumption.
 * Each receipt records: phase name, status (GREEN|WARN|FAIL), elapsed time, metrics.
 * Receipts are appended to a JSONL file (one JSON object per line).
 *
 * Usage:
 * <pre>
 *   BuildReceipt receipt = new BuildReceipt("compile", "GREEN", 12450);
 *   receipt.emitTo(Paths.get(".ggen/build-receipt.json"));
 *
 *   Map<String, BuildReceipt> chain = BuildReceipt.loadChain(
 *       Paths.get(".ggen/build-receipt.json"));
 *   if (BuildReceipt.isChainGreen(chain)) {
 *       // All phases passed
 *   }
 * </pre>
 */
public class BuildReceipt {
    private final String phase;
    private final String status;      // GREEN | WARN | FAIL
    private final long elapsedMs;
    private final Instant timestamp;
    private final Map<String, Object> details;

    /**
     * Create a receipt with minimal details.
     *
     * @param phase phase name (e.g., "compile", "test", "validate")
     * @param status phase status ("GREEN", "WARN", or "FAIL")
     * @param elapsedMs elapsed time in milliseconds
     */
    public BuildReceipt(String phase, String status, long elapsedMs) {
        this(phase, status, elapsedMs, new HashMap<>());
    }

    /**
     * Create a receipt with detailed metrics.
     *
     * @param phase phase name
     * @param status phase status
     * @param elapsedMs elapsed time in milliseconds
     * @param details optional metrics (e.g., module count, test count)
     */
    public BuildReceipt(String phase, String status, long elapsedMs, Map<String, Object> details) {
        if (phase == null || phase.trim().isEmpty()) {
            throw new IllegalArgumentException("Phase name must not be empty");
        }
        if (status == null || (!status.equals("GREEN") && !status.equals("WARN") && !status.equals("FAIL"))) {
            throw new IllegalArgumentException("Status must be GREEN, WARN, or FAIL");
        }
        if (elapsedMs < 0) {
            throw new IllegalArgumentException("Elapsed time must be non-negative");
        }

        this.phase = phase;
        this.status = status;
        this.elapsedMs = elapsedMs;
        this.timestamp = Instant.now();
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
    }

    /**
     * Get the phase name.
     */
    public String getPhase() {
        return phase;
    }

    /**
     * Get the phase status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Get elapsed time in milliseconds.
     */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * Get the timestamp when receipt was created.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Get details (read-only copy).
     */
    public Map<String, Object> getDetails() {
        return new HashMap<>(details);
    }

    /**
     * Check if phase status is GREEN (passed).
     */
    public boolean isGreen() {
        return "GREEN".equals(status);
    }

    /**
     * Check if phase status is pass (GREEN or WARN, not FAIL).
     */
    public boolean isPass() {
        return "GREEN".equals(status) || "WARN".equals(status);
    }

    /**
     * Serialize to JSON and append to receipt file (JSONL format).
     *
     * @param receiptFile path to .ggen/build-receipt.json
     * @throws IOException if file cannot be written
     */
    public void emitTo(Path receiptFile) throws IOException {
        if (receiptFile == null) {
            throw new IllegalArgumentException("Receipt file path must not be null");
        }

        // Ensure parent directory exists
        Files.createDirectories(receiptFile.getParent());

        Gson gson = new GsonBuilder().serializeNulls().create();
        JsonObject json = new JsonObject();
        json.addProperty("phase", phase);
        json.addProperty("status", status);
        json.addProperty("timestamp", timestamp.toString());
        json.addProperty("elapsed_ms", elapsedMs);

        if (!details.isEmpty()) {
            json.add("details", gson.toJsonTree(details));
        }

        try (Writer writer = Files.newBufferedWriter(receiptFile,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            gson.toJson(json, writer);
            writer.write("\n");
        }
    }

    /**
     * Load receipt chain from JSONL file.
     * Returns empty map if file does not exist.
     *
     * @param receiptFile path to .ggen/build-receipt.json
     * @return map of phase name → BuildReceipt
     * @throws IOException if file cannot be read
     */
    public static Map<String, BuildReceipt> loadChain(Path receiptFile) throws IOException {
        Map<String, BuildReceipt> chain = new HashMap<>();

        if (receiptFile == null || !Files.exists(receiptFile)) {
            return chain;
        }

        Gson gson = new Gson();
        List<String> lines = Files.readAllLines(receiptFile);

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }

            try {
                JsonObject json = gson.fromJson(line, JsonObject.class);
                String phaseName = json.get("phase").getAsString();
                String status = json.get("status").getAsString();
                long elapsedMs = json.get("elapsed_ms").getAsLong();

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
                        } else {
                            details.put(key, elem.toString());
                        }
                    }
                }

                chain.put(phaseName, new BuildReceipt(phaseName, status, elapsedMs, details));
            } catch (JsonSyntaxException | NullPointerException e) {
                throw new IOException("Malformed receipt line: " + line, e);
            }
        }

        return chain;
    }

    /**
     * Validate receipt chain: all phases present and GREEN?
     *
     * @param chain map of phase → receipt (from loadChain)
     * @return true if all required phases are GREEN
     */
    public static boolean isChainGreen(Map<String, BuildReceipt> chain) {
        if (chain == null || chain.isEmpty()) {
            return false;
        }

        List<String> requiredPhases = new ArrayList<>();
        requiredPhases.add("generate");
        requiredPhases.add("compile");
        requiredPhases.add("test");
        requiredPhases.add("validate");

        for (String phase : requiredPhases) {
            if (!chain.containsKey(phase)) {
                return false;
            }
            BuildReceipt receipt = chain.get(phase);
            if (!receipt.isGreen()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validate receipt chain: all phases present and pass (GREEN or WARN)?
     *
     * @param chain map of phase → receipt
     * @return true if all required phases are GREEN or WARN
     */
    public static boolean isChainPass(Map<String, BuildReceipt> chain) {
        if (chain == null || chain.isEmpty()) {
            return false;
        }

        List<String> requiredPhases = new ArrayList<>();
        requiredPhases.add("generate");
        requiredPhases.add("compile");
        requiredPhases.add("test");
        requiredPhases.add("validate");

        for (String phase : requiredPhases) {
            if (!chain.containsKey(phase)) {
                return false;
            }
            BuildReceipt receipt = chain.get(phase);
            if (!receipt.isPass()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Get total elapsed time across all phases in chain.
     *
     * @param chain map of phase → receipt
     * @return total milliseconds
     */
    public static long getTotalElapsed(Map<String, BuildReceipt> chain) {
        if (chain == null) {
            return 0;
        }
        return chain.values().stream()
            .mapToLong(BuildReceipt::getElapsedMs)
            .sum();
    }

    @Override
    public String toString() {
        return String.format("BuildReceipt{phase='%s', status='%s', elapsed=%dms}", phase, status, elapsedMs);
    }
}
