package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.time.Instant;

/**
 * KYC and compliance verification details for a vendor.
 *
 * @param kycStatus KYC verification status: PENDING, APPROVED, REJECTED
 * @param complianceLevel compliance rating: LOW, MEDIUM, HIGH
 * @param verifiedAt timestamp when verification was completed
 * @param verificationExpiresAt timestamp when verification expires (nullable if no expiry)
 * @param taxId registered tax identification number
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record VendorVerificationDetails(
    String kycStatus,
    String complianceLevel,
    Instant verifiedAt,
    Instant verificationExpiresAt,
    String taxId
) {

    /**
     * KYC status constants.
     */
    public static class KycStatus {
        public static final String PENDING = "PENDING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
    }

    /**
     * Compliance level constants.
     */
    public static class ComplianceLevel {
        public static final String LOW = "LOW";
        public static final String MEDIUM = "MEDIUM";
        public static final String HIGH = "HIGH";
    }

    /**
     * Construct with validation.
     *
     * @throws IllegalArgumentException if required fields are invalid
     */
    public VendorVerificationDetails {
        if (kycStatus == null || kycStatus.isBlank()) {
            throw new IllegalArgumentException("kycStatus is required");
        }
        if (complianceLevel == null || complianceLevel.isBlank()) {
            throw new IllegalArgumentException("complianceLevel is required");
        }
        if (verifiedAt == null) {
            throw new IllegalArgumentException("verifiedAt is required");
        }
        if (taxId == null || taxId.isBlank()) {
            throw new IllegalArgumentException("taxId is required");
        }
    }

    /**
     * Check if this verification is currently valid (not expired).
     */
    public boolean isValid() {
        if (verificationExpiresAt == null) {
            return true; // No expiry, always valid
        }
        return Instant.now().isBefore(verificationExpiresAt);
    }

    /**
     * Builder for VendorVerificationDetails.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for VendorVerificationDetails.
     */
    public static class Builder {
        private String kycStatus;
        private String complianceLevel;
        private Instant verifiedAt;
        private Instant verificationExpiresAt;
        private String taxId;

        public Builder kycStatus(String kycStatus) {
            this.kycStatus = kycStatus;
            return this;
        }

        public Builder complianceLevel(String complianceLevel) {
            this.complianceLevel = complianceLevel;
            return this;
        }

        public Builder verifiedAt(Instant verifiedAt) {
            this.verifiedAt = verifiedAt;
            return this;
        }

        public Builder verificationExpiresAt(Instant verificationExpiresAt) {
            this.verificationExpiresAt = verificationExpiresAt;
            return this;
        }

        public Builder taxId(String taxId) {
            this.taxId = taxId;
            return this;
        }

        public VendorVerificationDetails build() {
            return new VendorVerificationDetails(
                kycStatus, complianceLevel, verifiedAt, verificationExpiresAt, taxId);
        }
    }
}
