package org.yawlfoundation.yawl.intelligence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only storage for receipt chain.
 * Persists receipts to receipts/intelligence.jsonl using virtual threads.
 */
public class ReceiptStorage {

    private final Path receiptsDir;
    private final Path receiptFile;
    private final ReentrantLock lock = new ReentrantLock();

    public ReceiptStorage() {
        this.receiptsDir = Paths.get("receipts");
        this.receiptFile = this.receiptsDir.resolve("intelligence.jsonl");
        ensureDirectories();
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(receiptsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create receipts directory: " + receiptsDir, e);
        }
    }

    /**
     * Appends a receipt to storage in a virtual thread
     */
    public void append(Receipt receipt) {
        lock.lock();
        try {
            // Use virtual thread for I/O operation
            Thread.startVirtualThread(() -> {
                try {
                    String json = toJson(receipt);
                    Files.writeString(receiptFile, json + System.lineSeparator(),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.APPEND);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to append receipt to storage", e);
                }
            });
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads all receipts from storage in a virtual thread
     */
    public List<Receipt> loadAll() throws IOException {
        lock.lock();
        try {
            if (!Files.exists(receiptFile)) {
                return List.of();
            }

            // Use virtual thread for file reading
            List<Receipt> receipts = new java.util.concurrent.Callable<>() {
                @Override
                public List<Receipt> call() throws IOException {
                    List<String> lines = Files.readAllLines(receiptFile);
                    List<Receipt> result = new ArrayList<>(lines.size());

                    for (String line : lines) {
                        if (line.trim().isEmpty()) continue;
                        Receipt receipt = parseJson(line);
                        result.add(receipt);
                    }

                    return result;
                }
            }.call();

            return receipts;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Validates receipt file integrity
     */
    public boolean validateFileIntegrity() throws IOException {
        List<Receipt> receipts = loadAll();

        if (receipts.isEmpty()) {
            return false;
        }

        // Check genesis receipt
        Receipt genesis = receipts.get(0);
        if (genesis.priorHash() != null) {
            return false;
        }
        if (!genesis.validateHash()) {
            return false;
        }

        // Check chain continuity
        for (int i = 1; i < receipts.size(); i++) {
            Receipt current = receipts.get(i);
            Receipt previous = receipts.get(i - 1);

            // Check hash link
            if (!current.priorHash().equals(previous.hash())) {
                return false;
            }

            // Check hash validity
            if (!current.validateHash()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Clears all receipts
     */
    public void clear() throws IOException {
        lock.lock();
        try {
            Files.deleteIfExists(receiptFile);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets storage file path
     */
    public Path getStoragePath() {
        return receiptFile;
    }

    /**
     * Converts receipt to JSON string
     */
    private String toJson(Receipt receipt) {
        return String.format(
            "{\"hash\":\"%s\",\"priorHash\":%s,\"timestamp\":\"%s\",\"delta\":[%s]}",
            receipt.hash(),
            receipt.priorHash() != null ? "\"" + receipt.priorHash() + "\"" : "null",
            receipt.timestamp(),
            receipt.delta().stream()
                .map(s -> "\"" + escapeJson(s) + "\"")
                .reduce((a, b) -> a + "," + b)
                .orElse("")
        );
    }

    /**
     * Parses receipt from JSON string
     */
    private Receipt parseJson(String json) {
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Invalid JSON: " + json);
        }

        // Extract fields using simple string operations
        String hash = extractJsonField(json, "hash");
        String priorHash = extractJsonField(json, "priorHash");
        if (priorHash.equals("null")) {
            priorHash = null;
        }
        Instant timestamp = Instant.parse(extractJsonField(json, "timestamp"));
        List<String> delta = parseDelta(extractJsonField(json, "delta"));

        return new Receipt(hash, priorHash, timestamp, delta);
    }

    private String extractJsonField(String json, String field) {
        // Look for "field":"value" pattern
        int start = json.indexOf("\"" + field + "\":");
        if (start == -1) {
            throw new IllegalArgumentException("Field '" + field + "' not found");
        }
        start += field.length() + 2; // Skip past "field":

        if (json.charAt(start) == '"') {
            // String value
            int end = json.indexOf("\"", start + 1);
            return json.substring(start + 1, end);
        } else if (json.substring(start).startsWith("null")) {
            return "null";
        } else {
            throw new IllegalArgumentException("Invalid value format for field '" + field + "'");
        }
    }

    private List<String> parseDelta(String deltaJson) {
        // Remove brackets
        deltaJson = deltaJson.substring(1, deltaJson.length() - 1).trim();

        if (deltaJson.isEmpty()) {
            return List.of();
        }

        // Split by comma and unescape quotes
        String[] elements = deltaJson.split(",");
        List<String> result = new ArrayList<>(elements.length);

        for (String element : elements) {
            element = element.trim();
            if (element.startsWith("\"") && element.endsWith("\"")) {
                result.add(unescapeJson(element.substring(1, element.length() - 1)));
            }
        }

        return result;
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String unescapeJson(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    default -> sb.append(next);
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}