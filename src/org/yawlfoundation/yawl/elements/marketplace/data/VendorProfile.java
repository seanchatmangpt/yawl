/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.elements.marketplace.data;

import java.time.Instant;
import java.util.*;

/**
 * Immutable vendor profile data element for GCP Marketplace.
 *
 * <p>Represents vendor account information including credentials, business details,
 * compliance status, and marketplace tier. Implements YAWL data typing for
 * workflow variable mapping.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public record VendorProfile(
        String vendorId,
        String vendorName,
        String businessLicense,
        String contactEmail,
        String contactPhone,
        String businessAddress,
        String marketplaceTier,
        boolean credentialsVerified,
        long credentialVerificationTime,
        String complianceStatus,
        double accountRating,
        int totalTransactions,
        boolean accountActive,
        long accountCreatedTime,
        long lastActivityTime,
        Map<String, String> customAttributes
) {

    /**
     * Marketplace tier constants
     */
    public static final String TIER_STANDARD = "STANDARD";
    public static final String TIER_PLUS = "PLUS";
    public static final String TIER_PREMIUM = "PREMIUM";

    /**
     * Compliance status constants
     */
    public static final String COMPLIANCE_PENDING = "PENDING";
    public static final String COMPLIANCE_APPROVED = "APPROVED";
    public static final String COMPLIANCE_SUSPENDED = "SUSPENDED";
    public static final String COMPLIANCE_REVOKED = "REVOKED";

    /**
     * Compact constructor for record validation.
     */
    public VendorProfile {
        Objects.requireNonNull(vendorId, "Vendor ID cannot be null");
        Objects.requireNonNull(vendorName, "Vendor name cannot be null");
        Objects.requireNonNull(businessLicense, "Business license cannot be null");
        Objects.requireNonNull(contactEmail, "Contact email cannot be null");
        customAttributes = customAttributes != null ? new HashMap<>(customAttributes) : new HashMap<>();
    }

    /**
     * Creates a VendorProfile builder for fluent construction.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for VendorProfile.
     */
    public static class Builder {
        private String vendorId;
        private String vendorName;
        private String businessLicense;
        private String contactEmail;
        private String contactPhone;
        private String businessAddress;
        private String marketplaceTier = TIER_STANDARD;
        private boolean credentialsVerified = false;
        private long credentialVerificationTime = 0;
        private String complianceStatus = COMPLIANCE_PENDING;
        private double accountRating = 0.0;
        private int totalTransactions = 0;
        private boolean accountActive = true;
        private long accountCreatedTime = System.currentTimeMillis();
        private long lastActivityTime = System.currentTimeMillis();
        private Map<String, String> customAttributes = new HashMap<>();

        public Builder vendorId(String vendorId) {
            this.vendorId = vendorId;
            return this;
        }

        public Builder vendorName(String vendorName) {
            this.vendorName = vendorName;
            return this;
        }

        public Builder businessLicense(String businessLicense) {
            this.businessLicense = businessLicense;
            return this;
        }

        public Builder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public Builder contactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
            return this;
        }

        public Builder businessAddress(String businessAddress) {
            this.businessAddress = businessAddress;
            return this;
        }

        public Builder marketplaceTier(String marketplaceTier) {
            this.marketplaceTier = marketplaceTier;
            return this;
        }

        public Builder credentialsVerified(boolean verified) {
            this.credentialsVerified = verified;
            if (verified) {
                this.credentialVerificationTime = System.currentTimeMillis();
            }
            return this;
        }

        public Builder complianceStatus(String status) {
            this.complianceStatus = status;
            return this;
        }

        public Builder accountRating(double rating) {
            this.accountRating = Math.min(5.0, Math.max(0.0, rating));
            return this;
        }

        public Builder totalTransactions(int count) {
            this.totalTransactions = count;
            return this;
        }

        public Builder accountActive(boolean active) {
            this.accountActive = active;
            return this;
        }

        public Builder customAttribute(String key, String value) {
            this.customAttributes.put(key, value);
            return this;
        }

        public VendorProfile build() {
            return new VendorProfile(
                    vendorId, vendorName, businessLicense, contactEmail,
                    contactPhone, businessAddress, marketplaceTier,
                    credentialsVerified, credentialVerificationTime,
                    complianceStatus, accountRating, totalTransactions,
                    accountActive, accountCreatedTime, lastActivityTime,
                    customAttributes
            );
        }
    }

    /**
     * Checks if vendor meets compliance requirements for account activation.
     *
     * @return true if vendor is approved and credentials verified
     */
    public boolean isCompliant() {
        return COMPLIANCE_APPROVED.equals(complianceStatus) && credentialsVerified;
    }

    /**
     * Checks if vendor can accept new orders.
     *
     * @return true if account is active and compliant
     */
    public boolean canAcceptOrders() {
        return accountActive && isCompliant();
    }

    /**
     * Determines vendor's commission rate based on tier.
     *
     * @return commission percentage (0-30)
     */
    public double getCommissionRate() {
        return switch (marketplaceTier) {
            case TIER_PREMIUM -> 10.0;
            case TIER_PLUS -> 15.0;
            default -> 20.0;  // TIER_STANDARD
        };
    }
}
