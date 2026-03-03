package org.yawlfoundation.yawl.intelligence;

import org.bouncycastle.crypto.digests.Blake3Digest;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.util.encoders.Hex;

import java.time.Instant;
import java.util.List;

/**
 * Blake3 hasher with SHA3-256 fallback.
 * Supports hashing receipts with cryptographic integrity.
 */
public class Blake3Hasher {

    // Blake3 produces 32-byte hashes
    private static final int HASH_LENGTH = 32;

    /**
     * Hash a receipt using Blake3 with SHA3-256 fallback
     */
    public static String hash(List<String> delta, String priorHash, Instant timestamp) {
        try {
            return hashBlake3(delta, priorHash, timestamp);
        } catch (Throwable t) {
            // Fallback to SHA3-256 if Blake3 fails
            return hashSHA3(delta, priorHash, timestamp);
        }
    }

    /**
     * Hash using Blake3 algorithm
     */
    private static String hashBlake3(List<String> delta, String priorHash, Instant timestamp) {
        Blake3Digest digest = new Blake3Digest();

        // Add prior hash if present
        if (priorHash != null) {
            byte[] priorBytes = priorHash.getBytes();
            digest.update(priorBytes, 0, priorBytes.length);
        }

        // Add delta elements
        for (String element : delta) {
            byte[] elementBytes = element.getBytes();
            digest.update(elementBytes, 0, elementBytes.length);
        }

        // Add timestamp for uniqueness
        String timestampStr = timestamp.toString();
        byte[] timestampBytes = timestampStr.getBytes();
        digest.update(timestampBytes, 0, timestampBytes.length);

        // Generate hash
        byte[] hashBytes = new byte[HASH_LENGTH];
        digest.doFinal(hashBytes, 0);

        return Hex.toHexString(hashBytes);
    }

    /**
     * Fallback hash using SHA3-256
     */
    private static String hashSHA3(List<String> delta, String priorHash, Instant timestamp) {
        SHA3Digest digest = new SHA3Digest(256); // SHA3-256

        // Combine all input data
        StringBuilder input = new StringBuilder();

        if (priorHash != null) {
            input.append(priorHash);
        }
        input.append(";");
        input.append(timestamp.toString());
        input.append(";");

        for (String element : delta) {
            input.append(element);
            input.append(";");
        }

        byte[] inputBytes = input.toString().getBytes();
        digest.update(inputBytes, 0, inputBytes.length);

        // Generate hash
        byte[] hashBytes = new byte[HASH_LENGTH];
        digest.doFinal(hashBytes, 0);

        return Hex.toHexString(hashBytes);
    }

    /**
     * Validate a hash value
     */
    public static boolean isValidHash(String hash) {
        return hash != null && hash.length() == HASH_LENGTH * 2 && hash.matches("^[a-fA-F0-9]+$");
    }

    /**
     * Get hash length in bytes
     */
    public static int getHashLength() {
        return HASH_LENGTH;
    }
}