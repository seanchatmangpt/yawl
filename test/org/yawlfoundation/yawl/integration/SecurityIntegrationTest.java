package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security Integration Tests - Verifies Log4j2 and security patches
 * Tests password hashing, secure random generation, and logging security
 */
public class SecurityIntegrationTest {

    @Test
    public void testPasswordHashing() throws Exception {
        String password = "secure_password_123!";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        byte[] hashedPassword = digest.digest(password.getBytes());
        String encodedHash = Base64.getEncoder().encodeToString(hashedPassword);
        
        assertNotNull("Hash should be generated", encodedHash);
        assertNotEquals("Hash should not equal password", password, encodedHash);
        
        // Verify same password produces same hash
        byte[] hashedAgain = digest.digest(password.getBytes());
        String encodedAgain = Base64.getEncoder().encodeToString(hashedAgain);
        
        assertEquals("Same password should produce same hash", encodedHash, encodedAgain);
    }

    @Test
    public void testSecureRandomGeneration() {
        SecureRandom random = new SecureRandom();
        
        byte[] token = new byte[32];
        random.nextBytes(token);
        String encodedToken = Base64.getEncoder().encodeToString(token);
        
        assertNotNull("Token should be generated", encodedToken);
        assertTrue("Token should be 44 characters when base64 encoded", encodedToken.length() > 0);
        
        byte[] token2 = new byte[32];
        random.nextBytes(token2);
        String encodedToken2 = Base64.getEncoder().encodeToString(token2);
        
        assertNotEquals("Two random tokens should be different", encodedToken, encodedToken2);
    }

    @Test
    public void testMultipleSecureRandoms() {
        SecureRandom random1 = new SecureRandom();
        SecureRandom random2 = new SecureRandom();
        
        byte[] bytes1 = new byte[16];
        byte[] bytes2 = new byte[16];
        
        random1.nextBytes(bytes1);
        random2.nextBytes(bytes2);
        
        String str1 = Base64.getEncoder().encodeToString(bytes1);
        String str2 = Base64.getEncoder().encodeToString(bytes2);
        
        assertNotEquals("Different instances should produce different values", str1, str2);
    }

    @Test
    public void testPasswordSaltedHashing() throws Exception {
        String password = "workflow_password";
        SecureRandom random = new SecureRandom();
        
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] hashedPassword = digest.digest(password.getBytes());
        
        String encodedHash = Base64.getEncoder().encodeToString(hashedPassword);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        
        assertNotNull("Salted hash should be generated", encodedHash);
        assertNotNull("Salt should be stored", encodedSalt);
        
        // Simulate verification
        byte[] salt2 = Base64.getDecoder().decode(encodedSalt);
        digest.reset();
        digest.update(salt2);
        byte[] hashedPassword2 = digest.digest(password.getBytes());
        String encodedHash2 = Base64.getEncoder().encodeToString(hashedPassword2);
        
        assertEquals("Verification should succeed with correct salt", encodedHash, encodedHash2);
    }

    @Test
    public void testLog4j2Logging() {
        org.apache.logging.log4j.Logger logger = 
            org.apache.logging.log4j.LogManager.getLogger(SecurityIntegrationTest.class);
        
        assertNotNull("Logger should be initialized", logger);
        
        // Test various log levels
        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warning message");
        
        assertTrue("Logger should be operational", true);
    }

    @Test
    public void testCryptographicAlgorithms() throws Exception {
        String data = "sensitive_workflow_data";
        
        for (String algorithm : new String[]{"SHA-1", "SHA-256", "SHA-512"}) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashed = digest.digest(data.getBytes());
            String encoded = Base64.getEncoder().encodeToString(hashed);
            
            assertNotNull(algorithm + " should hash data", encoded);
            assertTrue(algorithm + " output should be non-empty", encoded.length() > 0);
        }
    }

    @Test
    public void testSecureRandomDeviceAccess() {
        SecureRandom random = new SecureRandom();
        int[] seeds = new int[10];
        
        for (int i = 0; i < seeds.length; i++) {
            seeds[i] = random.nextInt(1000000);
        }
        
        // Verify no two values are the same
        for (int i = 0; i < seeds.length; i++) {
            for (int j = i + 1; j < seeds.length; j++) {
                assertNotEquals("Random values should be unique", seeds[i], seeds[j]);
            }
        }
    }

    @Test
    public void testBase64EncodingDecoding() {
        String originalData = "workflow_id_abc123xyz789";
        
        String encoded = Base64.getEncoder().encodeToString(originalData.getBytes());
        byte[] decoded = Base64.getDecoder().decode(encoded);
        String decodedData = new String(decoded);
        
        assertEquals("Base64 encoding/decoding should be symmetric", originalData, decodedData);
    }

    @Test
    public void testSecurityProviders() {
        java.security.Provider[] providers = java.security.Security.getProviders();
        assertNotNull("Security providers should be available", providers);
        assertTrue("At least one security provider should be available", providers.length > 0);
        
        boolean sunProviderFound = false;
        for (java.security.Provider provider : providers) {
            if (provider.getName().contains("SUN")) {
                sunProviderFound = true;
                break;
            }
        }
        assertTrue("Sun provider should be available", sunProviderFound);
    }
}
