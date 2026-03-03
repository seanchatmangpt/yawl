package org.yawlfoundation.yawl.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Individual receipt record with cryptographic integrity.
 * Uses Java 25 record for immutability and value semantics.
 */
public record Receipt(
    String hash,
    String priorHash,
    Instant timestamp,
    List<String> delta
) {

    /**
     * Creates a genesis receipt (first entry in chain)
     */
    public static Receipt createGenesis(List<String> delta) {
        return new Receipt(
            calculateHash(delta, null, Instant.now()),
            null,
            Instant.now(),
            List.copyOf(delta)
        );
    }

    /**
     * Creates a new receipt linked to previous receipt
     */
    public static Receipt create(List<String> delta, Receipt prior) {
        return new Receipt(
            calculateHash(delta, prior != null ? prior.hash() : null, Instant.now()),
            prior != null ? prior.hash() : null,
            Instant.now(),
            List.copyOf(delta)
        );
    }

    /**
     * Validates this receipt's hash
     */
    public boolean validateHash() {
        String expectedHash = calculateHash(this.delta(), this.priorHash(), this.timestamp());
        return Objects.equals(this.hash(), expectedHash);
    }

    /**
     * Calculates Blake3 hash with fallback
     */
    private static String calculateHash(List<String> delta, String priorHash, Instant timestamp) {
        return Blake3Hasher.hash(delta, priorHash, timestamp);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        Receipt that = (Receipt) obj;
        return Objects.equals(this.hash, that.hash) &&
               Objects.equals(this.priorHash, that.priorHash) &&
               Objects.equals(this.timestamp, that.timestamp) &&
               Objects.equals(this.delta, that.delta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, priorHash, timestamp, delta);
    }

    @Override
    public String toString() {
        return "Receipt[" +
               "hash=" + hash + ", " +
               "priorHash=" + priorHash + ", " +
               "timestamp=" + timestamp + ", " +
               "delta=" + delta + "]";
    }
}