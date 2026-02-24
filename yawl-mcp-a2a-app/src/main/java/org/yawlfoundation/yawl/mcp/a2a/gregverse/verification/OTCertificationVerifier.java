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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for verifying occupational therapist credentials in the GregVerse marketplace.
 *
 * <p>This service implements a comprehensive verification workflow to ensure that all
 * OTs offering services have valid, up-to-date credentials from trusted authorities.</p>
 *
 * <h2>Verification Workflow</h2>
 * <ol>
 *   <li><strong>Submission</strong> - OT submits credentials for verification</li>
 *   <li><strong>Expiration Check</strong> - Verify credential has not expired</li>
 *   <li><strong>Issuer Validation</strong> - Check if issuer is on trusted authority list</li>
 *   <li><strong>Document Verification</strong> - Validate credential documents</li>
 *   <li><strong>Status Update</strong> - Store verification result</li>
 *   <li><strong>Notification</strong> - Notify OT of verification status</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class OTCertificationVerifier {

    private final Map<UUID, CredentialRecord> credentialStore;
    private final Set<String> trustedIssuers;
    private final Map<CertificationType, List<String>> validCredentialPatterns;

    /**
     * Creates a new OT certification verifier.
     */
    public OTCertificationVerifier() {
        this.credentialStore = new ConcurrentHashMap<>();
        this.trustedIssuers = initializeTrustedIssuers();
        this.validCredentialPatterns = initializeCredentialPatterns();
        System.out.println("OTCertificationVerifier initialized with " + trustedIssuers.size() + " trusted issuers");
    }

    /**
     * Submits credentials for verification.
     *
     * @param otId the occupational therapist ID
     * @param certificationType the type of certification
     * @param credentialNumber the credential identifier
     * @param issuer the issuing authority
     * @param issuanceDate when the credential was issued
     * @param expirationDate when the credential expires
     * @return the submitted credential record
     */
    public CredentialRecord submitCredential(
        UUID otId,
        CertificationType certificationType,
        String credentialNumber,
        String issuer,
        Instant issuanceDate,
        Instant expirationDate
    ) {
        System.out.println("Submitting credential verification for OT " + otId + " - " + certificationType);

        // Create pending credential record
        CredentialRecord credential = CredentialRecord.pending(
            otId,
            certificationType,
            credentialNumber,
            issuer,
            issuanceDate,
            expirationDate
        );

        // Perform verification
        VerificationStatus status = verifyCredential(credential);

        // Update status with verification result
        credential = credential.updateStatus(status,
            "Credential submitted for verification on " + Instant.now());

        // Store the credential
        credentialStore.put(credential.credentialId(), credential);

        // Notify OT of submission
        notifyOT(otId, "Credential submitted for verification", credential);

        System.out.println("Credential " + credential.credentialId() + " submitted with status: " + status.toDisplayString());

        return credential;
    }

    /**
     * Verifies a credential according to the verification workflow.
     *
     * @param credential the credential to verify
     * @return the verification status
     */
    private VerificationStatus verifyCredential(CredentialRecord credential) {
        System.out.println("Verifying credential " + credential.credentialNumber() + " for type " + credential.certificationType());

        // Step 1: Check expiration date
        if (credential.expirationDate() != null &&
            credential.expirationDate().isBefore(Instant.now())) {
            System.out.println("Credential " + credential.credentialNumber() + " has expired");
            return VerificationStatus.Expired.INSTANCE;
        }

        // Step 2: Validate issuer
        if (!isTrustedIssuer(credential.issuer())) {
            String reason = "Untrusted issuer: " + credential.issuer();
            System.out.println("Credential " + credential.credentialNumber() + " rejected: " + reason);
            return new VerificationStatus.Rejected(reason);
        }

        // Step 3: Validate credential format
        if (!isValidCredentialFormat(credential)) {
            String reason = "Invalid credential format for " + credential.certificationType();
            System.out.println("Credential " + credential.credentialNumber() + " rejected: " + reason);
            return new VerificationStatus.Rejected(reason);
        }

        // Step 4: Additional validation based on certification type
        if (!performTypeSpecificValidation(credential)) {
            String reason = "Failed " + credential.certificationType() + " specific validation";
            System.out.println("Credential " + credential.credentialNumber() + " rejected: " + reason);
            return new VerificationStatus.Rejected(reason);
        }

        // Verification successful
        System.out.println("Credential " + credential.credentialNumber() + " verified successfully");
        return VerificationStatus.Verified.INSTANCE;
    }

    /**
     * Checks if an issuer is trusted.
     *
     * @param issuer the issuer name to check
     * @return true if the issuer is trusted, false otherwise
     */
    private boolean isTrustedIssuer(String issuer) {
        if (issuer == null || issuer.trim().isEmpty()) {
            return false;
        }
        return trustedIssuers.stream()
            .anyMatch(trusted -> trusted.equalsIgnoreCase(issuer.trim()));
    }

    /**
     * Validates the credential format based on certification type.
     *
     * @param credential the credential to validate
     * @return true if format is valid, false otherwise
     */
    private boolean isValidCredentialFormat(CredentialRecord credential) {
        if (credential.credentialNumber() == null ||
            credential.credentialNumber().trim().isEmpty()) {
            return false;
        }

        List<String> patterns = validCredentialPatterns.get(credential.certificationType());
        if (patterns == null || patterns.isEmpty()) {
            return true; // No specific format requirements
        }

        return patterns.stream()
            .anyMatch(pattern -> credential.credentialNumber().matches(pattern));
    }

    /**
     * Performs type-specific validation for different certification types.
     *
     * @param credential the credential to validate
     * @return true if validation passes, false otherwise
     */
    private boolean performTypeSpecificValidation(CredentialRecord credential) {
        return switch (credential.certificationType()) {
            case NBCOT -> validateNBCOTCredential(credential);
            case STATE_LICENSE -> validateStateLicense(credential);
            case SPECIALTY_CERT -> validateSpecialtyCertification(credential);
            case ADVANCED_CERT -> validateAdvancedCertification(credential);
        };
    }

    /**
     * Validates NBCOT credential format and requirements.
     *
     * @param credential the NBCOT credential
     * @return true if valid, false otherwise
     */
    private boolean validateNBCOTCredential(CredentialRecord credential) {
        // NBCOT credentials typically follow format: NBCOT-12345 or 12345678
        String pattern = "^(NBCOT[-])?\\d{5,8}$";
        return credential.credentialNumber().matches(pattern);
    }

    /**
     * Validates state license requirements.
     *
     * @param credential the state license credential
     * @return true if valid, false otherwise
     */
    private boolean validateStateLicense(CredentialRecord credential) {
        // State licenses typically: state abbreviation + license number
        // Example: CA-OT12345 or FL12345
        String pattern = "^(?i)[A-Z]{2}[-]?(OT|O|\\d{1,8})$";
        return credential.credentialNumber().matches(pattern);
    }

    /**
     * Validates specialty certification requirements.
     *
     * @param credential the specialty credential
     * @return true if valid, false otherwise
     */
    private boolean validateSpecialtyCertification(CredentialRecord credential) {
        // Specialty certifications: credential name + number
        // Example: CHT-12345 or SW12345
        String pattern = "^[A-Z]{3,5}[-]?\\d{3,8}$";
        return credential.credentialNumber().matches(pattern);
    }

    /**
     * Validates advanced certification requirements.
     *
     * @param credential the advanced credential
     * @return true if valid, false otherwise
     */
    private boolean validateAdvancedCertification(CredentialRecord credential) {
        // Advanced certifications: prefix + number
        // Example: FAOTA-12345 or RAC-12345
        String pattern = "^[A-Z]{4,6}[-]?\\d{3,8}$";
        return credential.credentialNumber().matches(pattern);
    }

    /**
     * Notifies an OT about their credential verification status.
     *
     * @param otId the occupational therapist ID
     * @param message the notification message
     * @param credential the credential record
     */
    private void notifyOT(UUID otId, String message, CredentialRecord credential) {
        String notification = String.format(
            "Credential Notification\n" +
            "OT ID: %s\n" +
            "Certification Type: %s\n" +
            "Credential Number: %s\n" +
            "Status: %s\n" +
            "Message: %s",
            otId,
            credential.certificationType(),
            credential.credentialNumber(),
            credential.verificationStatus().toDisplayString(),
            message
        );

        System.out.println("Notifying OT " + otId + ": " + message);
        System.out.println("Notification: " + notification);
    }

    /**
     * Periodically checks and refreshes credential status.
     */
    public void refreshCredentials() {
        System.out.println("Starting periodic credential refresh for " + credentialStore.size() + " credentials");

        credentialStore.forEach((credentialId, credential) -> {
            if (credential.isValid() && credential.daysUntilExpiration() < 90) {
                System.out.println("Credential " + credential.credentialNumber() + " for OT " + credential.otId() +
                    " expires in " + credential.daysUntilExpiration() + " days");

                // Send renewal notification
                notifyOT(credential.otId(),
                    "Your certification is expiring soon. Please renew to continue offering services.",
                    credential);
            }
        });

        System.out.println("Credential refresh completed");
    }

    /**
     * Gets the verification status for a specific credential.
     *
     * @param credentialId the credential ID
     * @return the credential record or null if not found
     */
    public CredentialRecord getCredential(UUID credentialId) {
        return credentialStore.get(credentialId);
    }

    /**
     * Gets all credentials for a specific OT.
     *
     * @param otId the occupational therapist ID
     * @return list of credential records
     */
    public List<CredentialRecord> getCredentialsForOT(UUID otId) {
        return credentialStore.values().stream()
            .filter(cred -> cred.otId().equals(otId))
            .collect(Collectors.toList());
    }

    /**
     * Gets all valid credentials for an OT.
     *
     * @param otId the occupational therapist ID
     * @return list of valid credential records
     */
    public List<CredentialRecord> getValidCredentialsForOT(UUID otId) {
        return getCredentialsForOT(otId).stream()
            .filter(CredentialRecord::isValid)
            .collect(Collectors.toList());
    }

    /**
     * Initializes the set of trusted credential issuers.
     *
     * @return the set of trusted issuers
     */
    private Set<String> initializeTrustedIssuers() {
        return Set.of(
            "NBCOT",
            "American Occupational Therapy Association (AOTA)",
            "Board of Certification for Occupational Therapist Assistants",
            "State OT Boards",
            "Hand Therapy Certification Commission",
            "Pediatric Certification Board",
            "AOTA Specialty Certification"
        );
    }

    /**
     * Initializes the valid credential patterns by certification type.
     *
     * @return map of certification types to regex patterns
     */
    private Map<CertificationType, List<String>> initializeCredentialPatterns() {
        Map<CertificationType, List<String>> patterns = new HashMap<>();

        patterns.put(CertificationType.NBCOT, List.of(
            "^(NBCOT[-])?\\d{5,8}$"
        ));

        patterns.put(CertificationType.STATE_LICENSE, List.of(
            "^(?i)[A-Z]{2}[-]?(OT|O|\\d{1,8})$"
        ));

        patterns.put(CertificationType.SPECIALTY_CERT, List.of(
            "^[A-Z]{3,5}[-]?\\d{3,8}$"
        ));

        patterns.put(CertificationType.ADVANCED_CERT, List.of(
            "^[A-Z]{4,6}[-]?\\d{3,8}$"
        ));

        return patterns;
    }

    /**
     * Returns the total number of credentials in the system.
     *
     * @return the credential count
     */
    public int getCredentialCount() {
        return credentialStore.size();
    }

    /**
     * Returns the number of verified credentials.
     *
     * @return the verified credential count
     */
    public int getVerifiedCredentialCount() {
        return (int) credentialStore.values().stream()
            .filter(cred -> cred.verificationStatus() instanceof VerificationStatus.Verified)
            .count();
    }
}