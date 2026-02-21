/**
 * DriftDetector — Detects codebase fact changes for rebuild triggers
 *
 * Computes SHA256 hash of facts.ttl (RDF representation of codebase structure).
 * Compares with previous hash to detect drift.
 *
 * Drift = any change to:
 *   - Module structure (new/removed/renamed modules)
 *   - Dependencies (build or runtime)
 *   - Coverage metrics
 *   - Test counts
 *   - Build profile/plugin configuration
 *   - Integration points
 *
 * On drift detection: trigger rebuild of dependent phases
 *   - Λ (compile) — recompile with new module structure
 *   - ggen code generation — regenerate code with new facts
 *   - Λ→H→Q→Ω validation circuit
 *
 * Hash file: .claude/receipts/observatory-facts.sha256
 * Format: "sha256_hash  facts.ttl" (compatible with sha256sum utility)
 */
package org.yawlfoundation.yawl.observatory.rdf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Detects changes in codebase facts (drift) for rebuild triggers.
 *
 * Maintains SHA256 hash of facts.ttl file. On invocation, compares current
 * hash with previous hash. If different, reports drift and updates hash file.
 *
 * Usage:
 *   DriftDetector detector = new DriftDetector(Paths.get("docs/v6/latest/facts"));
 *   if (detector.hasDrift()) {
 *       System.out.println("Codebase facts changed. Triggering rebuild.");
 *       detector.updateHashFile(Paths.get(".claude/receipts"));
 *   }
 */
public class DriftDetector {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HASH_FILENAME = "observatory-facts.sha256";

    private final Path factsDir;
    private String previousHash;
    private String currentHash;
    private boolean hasDrift;

    /**
     * Initialize drift detector with facts directory.
     *
     * @param factsDir Directory containing facts.json files (or facts.ttl after conversion)
     */
    public DriftDetector(Path factsDir) {
        this.factsDir = factsDir;
        this.hasDrift = false;
    }

    /**
     * Check if facts have changed since last detection.
     *
     * Computes current hash and compares with previous.
     *
     * @param hashFile File containing previous hash (e.g., .claude/receipts/observatory-facts.sha256)
     * @return true if facts have drifted, false otherwise
     * @throws IOException if facts or hash file cannot be read
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    public boolean checkDrift(Path hashFile) throws IOException, NoSuchAlgorithmException {
        // Load previous hash if it exists
        previousHash = loadPreviousHash(hashFile);

        // Compute current hash
        currentHash = computeFactsHash();

        // Detect drift
        hasDrift = !currentHash.equals(previousHash);

        if (hasDrift) {
            System.out.println("DRIFT DETECTED in codebase facts");
            System.out.println("  Previous hash: " + (previousHash != null ? previousHash : "(none)"));
            System.out.println("  Current hash:  " + currentHash);
            System.out.println("  Trigger: Full rebuild required (Λ→H→Q→Ω)");
        } else {
            System.out.println("NO DRIFT: Codebase facts unchanged");
        }

        return hasDrift;
    }

    /**
     * Update hash file with current hash.
     *
     * Called after successful rebuild following drift detection.
     * Ensures next run compares against this baseline.
     *
     * @param receiptDir Directory for hash file (e.g., .claude/receipts/)
     * @throws IOException if hash file cannot be written
     */
    public void updateHashFile(Path receiptDir) throws IOException {
        receiptDir.toFile().mkdirs();
        Path hashFile = receiptDir.resolve(HASH_FILENAME);

        // Format: "hash  filename" (compatible with sha256sum)
        String content = currentHash + "  facts.ttl\n";

        Files.write(
            hashFile,
            content.getBytes(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
        );

        System.out.println("Hash file updated: " + hashFile);
        System.out.println("  Hash: " + currentHash);
    }

    /**
     * Get previous hash from file.
     *
     * @param hashFile File containing hash (or empty if file doesn't exist)
     * @return Previous hash, or null if file not found
     * @throws IOException if file cannot be read
     */
    private String loadPreviousHash(Path hashFile) throws IOException {
        if (!Files.exists(hashFile)) {
            return null;
        }

        String content = Files.readString(hashFile).trim();
        if (content.isEmpty()) {
            return null;
        }

        // Extract hash from "hash  filename" format
        String[] parts = content.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }

    /**
     * Compute SHA256 hash of all facts files in facts directory.
     *
     * Hashes all .json files in lexicographic order for deterministic results.
     *
     * @return Hex-encoded SHA256 hash
     * @throws IOException if facts files cannot be read
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    private String computeFactsHash() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

        // Get all fact files in sorted order for determinism
        Files.list(factsDir)
            .filter(p -> p.toString().endsWith(".json"))
            .sorted()
            .forEach(path -> {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    digest.update(bytes);
                    // Include filename in hash to detect renames
                    digest.update(path.getFileName().toString().getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

        return toHexString(digest.digest());
    }

    /**
     * Convert byte array to hex string.
     */
    private String toHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Check if drift was detected.
     */
    public boolean hasDrift() {
        return hasDrift;
    }

    /**
     * Get current hash.
     */
    public String getCurrentHash() {
        return currentHash;
    }

    /**
     * Get previous hash (or null if no previous hash).
     */
    public String getPreviousHash() {
        return previousHash;
    }

    /**
     * Verify SHA256 hash of a file matches expected value.
     *
     * Utility for validating facts.ttl integrity.
     *
     * @param file File to hash
     * @param expectedHash Expected hex-encoded SHA256
     * @return true if file hash matches expected value
     * @throws IOException if file cannot be read
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    public static boolean verifyHash(Path file, String expectedHash) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = digest.digest(bytes);

        String actualHash = toHexStringStatic(hash);
        return actualHash.equals(expectedHash);
    }

    /**
     * Static version of toHexString for utility method.
     */
    private static String toHexStringStatic(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
