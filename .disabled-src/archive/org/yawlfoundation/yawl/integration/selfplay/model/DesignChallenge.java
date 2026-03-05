package org.yawlfoundation.yawl.integration.selfplay.model;

import java.time.Instant;

/**
 * A challenge verdict produced by {@code V7ChallengeAgent} against a {@link DesignProposal}.
 *
 * <p>In AlphaGo-style self-play, the same agent that proposes also challenges. The challenge
 * forces honest self-critique: the agent must find weaknesses in its own prior proposals.</p>
 *
 * <p>Verdict meanings:
 * <ul>
 *   <li>{@code ACCEPTED} — proposal passes the challenge; it enters the accepted set</li>
 *   <li>{@code REJECTED} — proposal fails; it will be re-proposed with improvements next round</li>
 *   <li>{@code MODIFIED} — proposal is conditionally accepted if a stated change is made</li>
 * </ul>
 *
 * @param challengeId unique ID for this challenge
 * @param proposalId the proposal being challenged
 * @param gap the v7 gap the challenged proposal addresses
 * @param verdict ACCEPTED, REJECTED, or MODIFIED
 * @param objection rationale for REJECTED/MODIFIED verdict (empty for ACCEPTED)
 * @param severity strength of the objection (0.0 = trivial, 1.0 = fatal); decays per round
 * @param round the self-play round in which this challenge was issued
 * @param timestamp when the challenge was generated
 */
public record DesignChallenge(
    String challengeId,
    String proposalId,
    V7Gap gap,
    String verdict,
    String objection,
    double severity,
    int round,
    Instant timestamp
) {
    /** Valid verdict values. */
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";
    public static final String MODIFIED = "MODIFIED";

    public DesignChallenge {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("challengeId is required");
        }
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposalId is required");
        }
        if (gap == null) {
            throw new IllegalArgumentException("gap is required");
        }
        if (!ACCEPTED.equals(verdict) && !REJECTED.equals(verdict) && !MODIFIED.equals(verdict)) {
            throw new IllegalArgumentException(
                "verdict must be ACCEPTED, REJECTED, or MODIFIED, got: " + verdict);
        }
        if (severity < 0.0 || severity > 1.0) {
            throw new IllegalArgumentException(
                "severity must be in [0.0, 1.0], got: " + severity);
        }
        if (round < 1) {
            throw new IllegalArgumentException("round must be >= 1, got: " + round);
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (objection == null) {
            objection = "";
        }
    }

    public boolean isAccepted() {
        return ACCEPTED.equals(verdict);
    }

    public boolean isRejected() {
        return REJECTED.equals(verdict);
    }
}
