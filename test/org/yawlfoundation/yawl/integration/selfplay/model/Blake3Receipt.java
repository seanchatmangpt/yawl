package org.yawlfoundation.yawl.integration.selfplay.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic audit receipt for a self-play round using SHA3-256 as a Blake3 substitute.
 *
 * <p>Each round of the self-play loop produces an immutable receipt that chains:
 * <ul>
 *   <li>All proposals generated in that round</li>
 *   <li>All challenges issued in that round</li>
 *   <li>The computed fitness score</li>
 *   <li>The prior round's receipt hash (forming a hash chain)</li>
 * </ul>
 *
 * <p><strong>Hash algorithm</strong>: SHA3-256 (FIPS 202). Upgrade path to real Blake3 via
 * {@code io.github.rctcwyvrn:blake3} once available in Maven central without native libs.
 *
 * <p>Canonical JSON is constructed deterministically (sorted fields, no whitespace) so that
 * the same inputs always produce the same hash.
 */
public final class Blake3Receipt {

    private Blake3Receipt() {
        throw new UnsupportedOperationException("Blake3Receipt is a utility class");
    }

    /**
     * Compute the SHA3-256 receipt hash for a self-play round.
     *
     * @param round the round number
     * @param proposals proposals generated in this round
     * @param challenges challenges issued in this round
     * @param fitness fitness score after evaluating this round
     * @param priorHash the receipt hash from the prior round (empty string for round 1)
     * @return hex-encoded SHA3-256 hash of the canonical JSON
     */
    public static String hash(
        int round,
        List<DesignProposal> proposals,
        List<DesignChallenge> challenges,
        FitnessScore fitness,
        String priorHash
    ) {
        String canonical = canonicalJson(round, proposals, challenges, fitness, priorHash);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA3-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                "SHA3-256 not available on this JVM — requires Java 9+", e);
        }
    }

    /**
     * Build canonical (deterministic) JSON for hashing. No whitespace, sorted fields.
     */
    static String canonicalJson(
        int round,
        List<DesignProposal> proposals,
        List<DesignChallenge> challenges,
        FitnessScore fitness,
        String priorHash
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"challenges\":[");
        for (int i = 0; i < challenges.size(); i++) {
            if (i > 0) sb.append(",");
            DesignChallenge c = challenges.get(i);
            sb.append("{\"challengeId\":\"").append(c.challengeId()).append("\",");
            sb.append("\"gap\":\"").append(c.gap().name()).append("\",");
            sb.append("\"proposalId\":\"").append(c.proposalId()).append("\",");
            sb.append("\"verdict\":\"").append(c.verdict()).append("\"}");
        }
        sb.append("],");
        sb.append("\"fitness\":").append(String.format("%.6f", fitness.total())).append(",");
        sb.append("\"priorHash\":\"").append(priorHash).append("\",");
        sb.append("\"proposals\":[");
        for (int i = 0; i < proposals.size(); i++) {
            if (i > 0) sb.append(",");
            DesignProposal p = proposals.get(i);
            sb.append("{\"gap\":\"").append(p.gap().name()).append("\",");
            sb.append("\"proposalId\":\"").append(p.proposalId()).append("\"}");
        }
        sb.append("],");
        sb.append("\"round\":").append(round);
        sb.append("}");
        return sb.toString();
    }
}
