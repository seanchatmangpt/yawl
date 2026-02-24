import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.yawlfoundation.yawl.mcp.a2a.gregverse.verification.*;

/**
 * Simple test runner for OTCertificationVerifier.
 */
public class TestRunner {
    public static void main(String[] args) {
        System.out.println("Starting OTCertificationVerifier tests...");

        OTCertificationVerifier verifier = new OTCertificationVerifier();
        UUID testOTId = UUID.randomUUID();
        Instant futureDate = Instant.now().plus(365, ChronoUnit.DAYS);

        // Test 1: Valid NBCOT credential
        System.out.println("\n=== Test 1: Valid NBCOT Credential ===");
        CredentialRecord credential1 = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-12345",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        System.out.println("Credential ID: " + credential1.credentialId());
        System.out.println("Status: " + credential1.verificationStatus().toDisplayString());
        System.out.println("Valid: " + credential1.isValid());

        // Test 2: Valid State License
        System.out.println("\n=== Test 2: Valid State License ===");
        CredentialRecord credential2 = verifier.submitCredential(
            testOTId,
            CertificationType.STATE_LICENSE,
            "CA-OT12345",
            "California OT Board",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        System.out.println("Status: " + credential2.verificationStatus().toDisplayString());
        System.out.println("Valid: " + credential2.isValid());

        // Test 3: Invalid format
        System.out.println("\n=== Test 3: Invalid Format ===");
        CredentialRecord credential3 = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "INVALID-FORMAT",
            "NBCOT",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        System.out.println("Status: " + credential3.verificationStatus().toDisplayString());
        System.out.println("Valid: " + credential3.isValid());

        // Test 4: Untrusted issuer
        System.out.println("\n=== Test 4: Untrusted Issuer ===");
        CredentialRecord credential4 = verifier.submitCredential(
            testOTId,
            CertificationType.NBCOT,
            "NBCOT-67890",
            "Fake University",
            Instant.now().minus(30, ChronoUnit.DAYS),
            futureDate
        );

        System.out.println("Status: " + credential4.verificationStatus().toDisplayString());
        System.out.println("Valid: " + credential4.isValid());

        // Test Statistics
        System.out.println("\n=== Test 5: Statistics ===");
        System.out.println("Total credentials: " + verifier.getCredentialCount());
        System.out.println("Verified credentials: " + verifier.getVerifiedCredentialCount());

        // Get credentials for OT
        List<CredentialRecord> otCredentials = verifier.getCredentialsForOT(testOTId);
        System.out.println("Credentials for OT " + testOTId + ": " + otCredentials.size());

        List<CredentialRecord> validCredentials = verifier.getValidCredentialsForOT(testOTId);
        System.out.println("Valid credentials for OT " + testOTId + ": " + validCredentials.size());

        System.out.println("\n=== All tests completed! ===");
    }
}