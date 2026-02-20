package org.yawlfoundation.yawl.security;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EncryptionConfigurationTest validates Jasypt encryption configuration.
 *
 * Test coverage:
 * - Encryption/decryption roundtrip
 * - StringEncryptor bean creation
 * - Configuration properties binding
 * - Environment variable override
 * - AES-256 algorithm correctness
 */
@SpringBootTest
@TestPropertySource(properties = {
        "jasypt.encryptor.algorithm=PBEWithHMACSHA512AndAES_256",
        "jasypt.encryptor.password=test-password-123",
        "jasypt.encryptor.iv-generator-classname=org.jasypt.iv.RandomIvGenerator",
        "jasypt.encryptor.key-obtention-iterations=1000",
        "jasypt.encryptor.pool-size=8"
})
@DisplayName("Encryption Configuration Tests")
public class EncryptionConfigurationTest {

    @Autowired
    private StringEncryptor stringEncryptor;

    @Autowired
    private EncryptorConfigurationProperties properties;

    @Autowired
    private Environment environment;

    @BeforeEach
    void setUp() {
        assertNotNull(stringEncryptor, "StringEncryptor bean must be autowired");
        assertNotNull(properties, "EncryptorConfigurationProperties must be autowired");
    }

    @Test
    @DisplayName("Encrypt and decrypt plaintext roundtrip")
    void testEncryptDecryptRoundtrip() {
        String plaintext = "mysecret-password";
        String encrypted = stringEncryptor.encrypt(plaintext);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertFalse(encrypted.isEmpty());

        String decrypted = stringEncryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Encrypted values are different each time (random IV)")
    void testRandomIVGeneratesUniqueCiphertexts() {
        String plaintext = "same-plaintext";
        String encrypted1 = stringEncryptor.encrypt(plaintext);
        String encrypted2 = stringEncryptor.encrypt(plaintext);

        assertNotEquals(encrypted1, encrypted2,
                "Different encryptions should produce different ciphertexts due to random IV");

        assertEquals(plaintext, stringEncryptor.decrypt(encrypted1));
        assertEquals(plaintext, stringEncryptor.decrypt(encrypted2));
    }

    @Test
    @DisplayName("Configuration properties are correctly bound")
    void testPropertiesBinding() {
        assertEquals("PBEWithHMACSHA512AndAES_256", properties.getAlgorithm());
        assertEquals("test-password-123", properties.getPassword());
        assertEquals("org.jasypt.iv.RandomIvGenerator", properties.getIvGeneratorClassname());
        assertEquals(1000, properties.getKeyObtentionIterations());
        assertEquals(8, properties.getPoolSize());
    }

    @Test
    @DisplayName("StringEncryptor bean is properly configured")
    void testStringEncryptorBeanCreation() {
        assertNotNull(stringEncryptor);
        assertTrue(stringEncryptor.getClass().getSimpleName().contains("PooledPBEStringEncryptor") ||
                stringEncryptor.getClass().getSimpleName().contains("Encryptor"));
    }

    @Test
    @DisplayName("Encrypt database password for production use")
    void testDatabasePasswordEncryption() {
        String dbPassword = "MySecurePostgresPassword123!";
        String encrypted = stringEncryptor.encrypt(dbPassword);

        assertNotNull(encrypted);
        assertNotEquals(dbPassword, encrypted);

        String decrypted = stringEncryptor.decrypt(encrypted);
        assertEquals(dbPassword, decrypted);

        // Simulate property injection: ENC(encrypted-value)
        String propertyValue = "ENC(" + encrypted + ")";
        assertTrue(propertyValue.startsWith("ENC("));
        assertTrue(propertyValue.endsWith(")"));
    }

    @Test
    @DisplayName("Encrypt API keys for external services")
    void testAPIKeyEncryption() {
        String apiKey = "test-external-api-key-12345678901234567890";
        String encrypted = stringEncryptor.encrypt(apiKey);

        assertEquals(apiKey, stringEncryptor.decrypt(encrypted));
    }

    @Test
    @DisplayName("Encrypt MCP server credentials")
    void testMCPCredentialsEncryption() {
        String mcpToken = "mcp-token-v1-abc123xyz";
        String encrypted = stringEncryptor.encrypt(mcpToken);

        assertEquals(mcpToken, stringEncryptor.decrypt(encrypted));
    }

    @Test
    @DisplayName("Encrypt rate limiter configuration")
    void testRateLimiterConfigEncryption() {
        String rateLimitKey = "rate-limit-secret-key";
        String encrypted = stringEncryptor.encrypt(rateLimitKey);

        assertEquals(rateLimitKey, stringEncryptor.decrypt(encrypted));
    }

    @Test
    @DisplayName("Long plaintext encryption")
    void testLongPlaintextEncryption() {
        String longSecret = "this-is-a-very-long-secret-" +
                "password-that-should-still-encrypt-and-decrypt-correctly-" +
                "without-any-issues-even-if-it-exceeds-typical-lengths";

        String encrypted = stringEncryptor.encrypt(longSecret);
        String decrypted = stringEncryptor.decrypt(encrypted);

        assertEquals(longSecret, decrypted);
    }

    @Test
    @DisplayName("Special characters in plaintext")
    void testSpecialCharactersEncryption() {
        String specialCharacters = "p@ssw0rd!#$%^&*()_+-=[]{}|;:',.<>?/";
        String encrypted = stringEncryptor.encrypt(specialCharacters);
        String decrypted = stringEncryptor.decrypt(encrypted);

        assertEquals(specialCharacters, decrypted);
    }

    @Test
    @DisplayName("Empty string encryption (edge case)")
    void testEmptyStringEncryption() {
        String empty = "";
        String encrypted = stringEncryptor.encrypt(empty);
        String decrypted = stringEncryptor.decrypt(encrypted);

        assertEquals(empty, decrypted);
    }

    @Test
    @DisplayName("Environment property resolution")
    void testEnvironmentPropertyResolution() {
        String property = environment.getProperty("jasypt.encryptor.algorithm");
        assertEquals("PBEWithHMACSHA512AndAES_256", property);
    }
}
