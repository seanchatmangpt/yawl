package org.yawlfoundation.yawl.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A single entry in the Blake3 receipt chain containing timestamp, hash, and delta.
 */
public class ReceiptEntry {
    private final Instant timestamp;
    private final String hash;
    private final List<String> delta;
    private final String previousHash;

    public ReceiptEntry(Instant timestamp, String hash, List<String> delta, String previousHash) {
        this.timestamp = timestamp;
        this.hash = hash;
        this.delta = delta;
        this.previousHash = previousHash;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getHash() {
        return hash;
    }

    public List<String> getDelta() {
        return delta;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReceiptEntry that = (ReceiptEntry) o;
        return Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(hash, that.hash) &&
                Objects.equals(delta, that.delta) &&
                Objects.equals(previousHash, that.previousHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, hash, delta, previousHash);
    }

    @Override
    public String toString() {
        return "ReceiptEntry{" +
                "timestamp=" + timestamp +
                ", hash='" + hash + '\'' +
                ", delta.size=" + delta.size() +
                ", previousHash='" + previousHash + '\'' +
                '}';
    }
}