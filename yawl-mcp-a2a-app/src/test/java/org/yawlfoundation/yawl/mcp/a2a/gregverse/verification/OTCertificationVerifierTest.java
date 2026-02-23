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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Simple test class for OTCertificationVerifier (without JUnit dependencies).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class OTCertificationVerifierTest {

    private OTCertificationVerifier verifier;
    private UUID testOTId;
    private Instant futureDate;
    private Instant pastDate;

    public static void main(String[] args) {
        OTCertificationVerifierTest test = new OTCertificationVerifierTest();
        test.setUp();

        test.testSubmitValidNBCOTCredential();
        test.testSubmitValidStateLicense();
        test.testRejectCredentialWithUntrustedIssuer();
        test.testRejectExpiredCredential();
        test.testRejectInvalidNBCOTFormat();
        test.testGetCredentialsForOT();
        test.testGetValidCredentialsForOT();
        test.testCertificationTypeEnum();
        test.testVerificationStatus();
        test.testCredentialRecordValidation();
        test.testCredentialStatistics();

        System.out.println("All tests completed successfully!");
    }

    void setUp() {
        System.out.println("Setting up test environment");

        // Create test OT ID
        testOTId = UUID.randomUUID();

        // Set up dates
        Instant now = Instant.now();
        futureDate = now.plus(365, ChronoUnit.DAYS); // Valid credential
        pastDate = now.minus(1, ChronoUnit.DAYS);    // Expired credential

        // Create verifier
        verifier = new OTCertificationVerifier();
    }

    void testSubmitValidNBCOTCredential() {
        System.out.println("Testing NBCOT credential submission");

        CredentialRecord credential = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        assert credential != null;
        assert testOTId.equals(credential.otId());
        assert CertificationType.NBCOT.equals(credential.certificationType());
        assert "NBCOT-12345".equals(credential.credentialNumber());
        assert "NBCOT".equals(credential.issuer());
        assert credential.verificationStatus() instanceof VerificationStatus.Verified;
        assert credential.isValid();

        System.out.println("✓ NBCOT credential test passed");
    }

    void testSubmitValidStateLicense() {
        System.out.println("Testing state license credential submission");

        CredentialRecord credential = verifier.submitCredential(
            testOTId,
            CertificationType.STATE_LICENSE,
            "CA-OT12345",
            "California OT Board",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        assert credential != null;
        assert testOTId.equals(credential.otId());
        assert CertificationType.STATE_LICENSE.equals(credential.certificationType());
        assert "CA-OT12345".equals(credential.credentialNumber());
        assert "California OT Board".equals(credential.issuer());
        assert credential.verificationStatus() instanceof VerificationStatus.Verified;

        System.out.println("✓ State license test passed");
    }

    void testRejectCredentialWithUntrustedIssuer() {
        System.out.println("Testing credential rejection with untrusted issuer");

        CredentialRecord credential = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "Fake University",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        assert credential != null;
        assert credential.verificationStatus() instanceof VerificationStatus.Rejected;
        assert !credential.isValid();
        assert ((VerificationStatus.Rejected) credential.verificationStatus()).reason()
            .contains("Untrusted issuer");

        System.out.println("✓ Untrusted issuer rejection test passed");
    }

    void testRejectExpiredCredential() {
        System.out.println("Testing credential rejection due to expiration");

        CredentialRecord credential = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(365, ChronoUnit.DAYS),
            pastDate
        );

        assert credential != null;
        assert credential.verificationStatus() instanceof VerificationStatus.Expired;
        assert !credential.isValid();

        System.out.println("✓ Expired credential rejection test passed");
    }

    void testRejectInvalidNBCOTFormat() {
        System.out.println("Testing NBCOT credential rejection with invalid format");

        CredentialRecord credential = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "INVALID-FORMAT",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        assert credential != null;
        assert credential.verificationStatus() instanceof VerificationStatus.Rejected;
        assert !credential.isValid();

        System.out.println("✓ Invalid format rejection test passed");
    }

    void testGetCredentialsForOT() {
        System.out.println("Testing getting credentials for specific OT");

        UUID secondOTId = UUID.randomUUID();

        // Submit first credential
        verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        // Submit second credential
        verifier.submitCredential(
            testOTId,
            CertificationType.STATE_LICENSE,
            "CA-OT12345",
            "California OT Board",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        // Submit credential for different OT
        verifier.submitCredential(
            secondOTId,
            CertificationType.NBCOT,
            "NBCOT-67890",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        // Verify correct credentials are returned
        List<CredentialRecord> ot1Credentials = verifier.getCredentialsForOT(testOTId);
        assert ot1Credentials.size() == 2;

        List<CredentialRecord> ot2Credentials = verifier.getCredentialsForOT(secondOTId);
        assert ot2Credentials.size() == 1;

        System.out.println("✓ Get credentials for OT test passed");
    }

    void testGetValidCredentialsForOT() {
        System.out.println("Testing getting only valid credentials for OT");

        // Submit valid credential
        verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        // Submit expired credential
        verifier.submitCredential(
            testOTId,
            CertificationType.STATE_LICENSE,
            "CA-OT12345",
            "California OT Board",
            Instant.now().minus(30, ChronoUnit.DAYS),
            pastDate
        );

        // Verify only valid credentials are returned
        List<CredentialRecord> validCredentials = verifier.getValidCredentialsForOT(testOTId);
        assert validCredentials.size() == 1;
        assert CertificationType.NBCOT.equals(validCredentials.get(0).certificationType());

        System.out.println("✓ Get valid credentials test passed");
    }

    void testCertificationTypeEnum() {
        System.out.println("Testing certification type enum");

        // Test NBCOT
        assert "NBCOT".equals(CertificationType.NBCOT.getCode());
        assert "National Board Certification in Occupational Therapy".equals(CertificationType.NBCOT.getDescription());
        assert CertificationType.NBCOT.isRequired();

        // Test STATE_LICENSE
        assert "STATE_LICENSE".equals(CertificationType.STATE_LICENSE.getCode());
        assert "State Occupational Therapy License".equals(CertificationType.STATE_LICENSE.getDescription());
        assert CertificationType.STATE_LICENSE.isRequired();

        // Test SPECIALTY_CERT
        assert "SPECIALTY_CERT".equals(CertificationType.SPECIALTY_CERT.getCode());
        assert "Specialty Certification".equals(CertificationType.SPECIALTY_CERT.getDescription());
        assert !CertificationType.SPECIALTY_CERT.isRequired();

        // Test fromCode method
        assert CertificationType.NBCOT == CertificationType.fromCode("NBCOT");
        try {
            CertificationType.fromCode("INVALID_TYPE");
            assert false : "Should have thrown IllegalArgumentException";
        } catch (IllegalArgumentException e) {
            // Expected
        }

        System.out.println("✓ Certification type enum test passed");
    }

    void testVerificationStatus() {
        System.out.println("Testing verification status sealed class");

        // Test Pending status
        VerificationStatus.Pending pending = VerificationStatus.Pending.INSTANCE;
        assert "Pending Verification".equals(pending.toDisplayString());
        assert pending.isSuccessful();
        assert !pending.isFailed();

        // Test Verified status
        VerificationStatus.Verified verified = VerificationStatus.Verified.INSTANCE;
        assert "Verified".equals(verified.toDisplayString());
        assert verified.isSuccessful();
        assert !verified.isFailed();

        // Test Rejected status
        VerificationStatus.Rejected rejected = new VerificationStatus.Rejected("Test reason");
        assert "Rejected: Test reason".equals(rejected.toDisplayString());
        assert !rejected.isSuccessful();
        assert rejected.isFailed();

        // Test Expired status
        VerificationStatus.Expired expired = VerificationStatus.Expired.INSTANCE;
        assert "Expired".equals(expired.toDisplayString());
        assert !expired.isSuccessful();
        assert expired.isFailed();

        System.out.println("✓ Verification status test passed");
    }

    void testCredentialRecordValidation() {
        System.out.println("Testing credential record validation methods");

        // Create valid credential
        CredentialRecord validCredential = CredentialRecord.verified(
            UUID.randomUUID(),
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate,
            "Valid credential"
        );

        assert validCredential.isValid();
        assert !validCredential.isExpired();
        assert validCredential.daysUntilExpiration() > 0;

        // Create expired credential
        CredentialRecord expiredCredential = CredentialRecord.expired(
            UUID.randomUUID(),
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(365, ChronoUnit.DAYS),
            pastDate
        );

        assert !expiredCredential.isValid();
        assert expiredCredential.isExpired();
        assert expiredCredential.daysUntilExpiration() < 0;

        System.out.println("✓ Credential record validation test passed");
    }

    void testCredentialStatistics() {
        System.out.println("Testing credential statistics");

        // Initially no credentials
        assert verifier.getCredentialCount() == 0;
        assert verifier.getVerifiedCredentialCount() == 0;

        // Submit some credentials
        UUID ot1 = UUID.randomUUID();
        UUID ot2 = UUID.randomUUID();

        verifier.submitCredential(ot1, CertificationType.NBCOT, "NBCOT-12345", "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS), futureDate);
        verifier.submitCredential(ot1, CertificationType.STATE_LICENSE, "CA-OT12345",
            "California OT Board", Instant.now().minus(30, ChronoUnit.DAYS), futureDate);
        verifier.submitCredential(ot2, CertificationType.NBCOT, "NBCOT-67890", "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS), pastDate);

        // Test statistics
        assert verifier.getCredentialCount() == 3;
        assert verifier.getVerifiedCredentialCount() == 2;

        System.out.println("✓ Credential statistics test passed");
    }
}