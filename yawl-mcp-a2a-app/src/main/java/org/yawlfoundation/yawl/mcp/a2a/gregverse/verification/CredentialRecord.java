/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.verification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing an OT credential and its verification status.
 *
 * <p>This record contains all information about a specific certification,
 * including the credential details, verification status, and timestamps.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record CredentialRecord(
    UUID credentialId,
    UUID otId,
    CertificationType certificationType,
    String credentialNumber,
    String issuer,
    Instant issuanceDate,
    Instant expirationDate,
    VerificationStatus verificationStatus,
    Instant verificationDate,
    String verificationNotes,
    Instant lastUpdated
) {

    /**
     * Creates a new credential record with pending verification status.
     *
     * @param otId the occupational therapist ID
     * @param certificationType the type of certification
     * @param credentialNumber the credential identifier number
     * @param issuer the issuing authority
     * @param issuanceDate when the credential was issued
     * @param expirationDate when the credential expires
     * @return a new credential record
     */
    public static CredentialRecord pending(
        UUID otId,
        CertificationType certificationType,
        String credentialNumber,
        String issuer,
        Instant issuanceDate,
        Instant expirationDate
    ) {
        Instant now = Instant.now();
        return new CredentialRecord(
            UUID.randomUUID(),
            otId,
            certificationType,
            credentialNumber,
            issuer,
            issuanceDate,
            expirationDate,
            VerificationStatus.Pending.INSTANCE,
            null,
            null,
            now
        );
    }

    /**
     * Creates a verified credential record.
     *
     * @param credentialId the credential ID (can be null for new records)
     * @param otId the occupational therapist ID
     * @param certificationType the type of certification
     * @param credentialNumber the credential identifier number
     * @param issuer the issuing authority
     * @param issuanceDate when the credential was issued
     * @param expirationDate when the credential expires
     * @param verificationNotes notes about the verification process
     * @return a verified credential record
     */
    public static CredentialRecord verified(
        UUID credentialId,
        UUID otId,
        CertificationType certificationType,
        String credentialNumber,
        String issuer,
        Instant issuanceDate,
        Instant expirationDate,
        String verificationNotes
    ) {
        Instant now = Instant.now();
        return new CredentialRecord(
            credentialId != null ? credentialId : UUID.randomUUID(),
            otId,
            certificationType,
            credentialNumber,
            issuer,
            issuanceDate,
            expirationDate,
            VerificationStatus.Verified.INSTANCE,
            now,
            verificationNotes,
            now
        );
    }

    /**
     * Creates a rejected credential record.
     *
     * @param credentialId the credential ID
     * @param otId the occupational therapist ID
     * @param certificationType the type of certification
     * @param credentialNumber the credential identifier number
     * @param issuer the issuing authority
     * @param issuanceDate when the credential was issued
     * @param expirationDate when the credential expires
     * @param rejectionReason why the credential was rejected
     * @return a rejected credential record
     */
    public static CredentialRecord rejected(
        UUID credentialId,
        UUID otId,
        CertificationType certificationType,
        String credentialNumber,
        String issuer,
        Instant issuanceDate,
        Instant expirationDate,
        String rejectionReason
    ) {
        Instant now = Instant.now();
        return new CredentialRecord(
            credentialId,
            otId,
            certificationType,
            credentialNumber,
            issuer,
            issuanceDate,
            expirationDate,
            new VerificationStatus.Rejected(rejectionReason),
            now,
            null,
            now
        );
    }

    /**
     * Creates an expired credential record.
     *
     * @param credentialId the credential ID
     * @param otId the occupational therapist ID
     * @param certificationType the type of certification
     * @param credentialNumber the credential identifier number
     * @param issuer the issuing authority
     * @param issuanceDate when the credential was issued
     * @param expirationDate when the credential expires
     * @return an expired credential record
     */
    public static CredentialRecord expired(
        UUID credentialId,
        UUID otId,
        CertificationType certificationType,
        String credentialNumber,
        String issuer,
        Instant issuanceDate,
        Instant expirationDate
    ) {
        Instant now = Instant.now();
        return new CredentialRecord(
            credentialId,
            otId,
            certificationType,
            credentialNumber,
            issuer,
            issuanceDate,
            expirationDate,
            VerificationStatus.Expired.INSTANCE,
            now,
            "Credential has expired",
            now
        );
    }

    /**
     * Checks if the credential is currently valid (verified and not expired).
     *
     * @return true if the credential is valid, false otherwise
     */
    public boolean isValid() {
        return verificationStatus instanceof VerificationStatus.Verified &&
            expirationDate != null &&
            expirationDate.isAfter(Instant.now());
    }

    /**
     * Checks if the credential has expired.
     *
     * @return true if the credential has expired, false otherwise
     */
    public boolean isExpired() {
        return expirationDate != null && expirationDate.isBefore(Instant.now());
    }

    /**
     * Returns the days until expiration (negative if already expired).
     *
     * @return the days until expiration
     */
    public long daysUntilExpiration() {
        if (expirationDate == null) {
            return Long.MAX_VALUE;
        }
        return java.time.Duration.between(Instant.now(), expirationDate).toDays();
    }

    /**
     * Updates the verification status of this credential.
     *
     * @param newStatus the new verification status
     * @param notes additional notes about the update
     * @return a new CredentialRecord with updated status
     */
    public CredentialRecord updateStatus(VerificationStatus newStatus, String notes) {
        Instant now = Instant.now();
        return new CredentialRecord(
            this.credentialId,
            this.otId,
            this.certificationType,
            this.credentialNumber,
            this.issuer,
            this.issuanceDate,
            this.expirationDate,
            newStatus,
            now,
            notes,
            now
        );
    }
}