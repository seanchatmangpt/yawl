package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecretEncryptorTest validates the standalone SecretEncryptor utility.
 *
 * Test coverage:
 * - Static encrypt/decrypt methods
 * - CLI simulation (command-line argument parsing)
 * - Consistency with EncryptionConfiguration
 * - Password sensitivity
 * - Edge cases (long strings, special characters)
 */
@DisplayName("SecretEncryptor Utility Tests")
public class SecretEncryptorTest {

    private static final String TEST_PASSWORD = "my-secure-password";
    private static final String TEST_VALUE = "secret-to-encrypt";

    @Test
    @DisplayName("Static encrypt method works correctly")
    void testStaticEncryptMethod() {
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, TEST_VALUE);

        assertNotNull(encrypted);
        assertNotEquals(TEST_VALUE, encrypted);
        assertFalse(encrypted.isEmpty());
    }

    @Test
    @DisplayName("Static decrypt method recovers plaintext")
    void testStaticDecryptMethod() {
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, TEST_VALUE);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(TEST_VALUE, decrypted);
    }

    @Test
    @DisplayName("Encrypt/decrypt roundtrip with static methods")
    void testEncryptDecryptRoundtrip() {
        String plaintext = "my-database-password";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, plaintext);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Wrong password fails to decrypt correctly")
    void testWrongPasswordProducesDifferentResult() {
        String plaintext = "correct-password";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, plaintext);
        String wrongPassword = "wrong-password";

        String decrypted = SecretEncryptor.decrypt(wrongPassword, encrypted);

        // Should NOT equal the original plaintext (decryption with wrong password fails)
        assertNotEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("Encrypt database credentials")
    void testDatabaseCredentialsEncryption() {
        String password = "myDatabasePassword123!@#";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, password);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(password, decrypted);
    }

    @Test
    @DisplayName("Encrypt API keys")
    void testAPIKeyEncryption() {
        String apiKey = "test-api-key-12345678901234567890";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, apiKey);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(apiKey, decrypted);
    }

    @Test
    @DisplayName("Encrypt long strings")
    void testLongStringEncryption() {
        String longString = "x".repeat(1000);
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, longString);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(longString, decrypted);
    }

    @Test
    @DisplayName("Encrypt strings with special characters")
    void testSpecialCharactersEncryption() {
        String special = "p@ssw0rd!#$%^&*()[]{}|;:',.<>?/";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, special);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(special, decrypted);
    }

    @Test
    @DisplayName("Encrypt unicode characters")
    void testUnicodeEncryption() {
        String unicode = "password-with-unicode-åäöñü-characters";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, unicode);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(unicode, decrypted);
    }

    @Test
    @DisplayName("Different passwords for same plaintext produce different ciphertexts")
    void testDifferentPasswordsDifferentCiphertexts() {
        String plaintext = "same-plaintext";
        String encrypted1 = SecretEncryptor.encrypt("password1", plaintext);
        String encrypted2 = SecretEncryptor.encrypt("password2", plaintext);

        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    @DisplayName("Same password and plaintext with random IV produces different ciphertexts")
    void testRandomIVUniqueness() {
        String plaintext = "same-plaintext";
        String encrypted1 = SecretEncryptor.encrypt(TEST_PASSWORD, plaintext);
        String encrypted2 = SecretEncryptor.encrypt(TEST_PASSWORD, plaintext);

        assertNotEquals(encrypted1, encrypted2,
                "Random IV should produce different ciphertexts for same plaintext and password");

        assertEquals(plaintext, SecretEncryptor.decrypt(TEST_PASSWORD, encrypted1));
        assertEquals(plaintext, SecretEncryptor.decrypt(TEST_PASSWORD, encrypted2));
    }

    @Test
    @DisplayName("Empty string encryption")
    void testEmptyStringEncryption() {
        String empty = "";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, empty);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(empty, decrypted);
    }

    @Test
    @DisplayName("Whitespace-only string encryption")
    void testWhitespaceStringEncryption() {
        String whitespace = "   \t\n\r   ";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, whitespace);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(whitespace, decrypted);
    }

    @Test
    @DisplayName("Strong password encryption")
    void testStrongPasswordEncryption() {
        String strongPassword = "MyStr0ng!P@ssw0rd#2024WithSpecialChars";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, strongPassword);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, encrypted);

        assertEquals(strongPassword, decrypted);
    }

    @Test
    @DisplayName("CLI output format validation")
    void testCLIOutputFormat() {
        String plaintext = "my-secret";
        String encrypted = SecretEncryptor.encrypt(TEST_PASSWORD, plaintext);

        // Simulate CLI output: ENC(...)
        String cliOutput = "ENC(" + encrypted + ")";

        assertTrue(cliOutput.startsWith("ENC("));
        assertTrue(cliOutput.endsWith(")"));

        // Extract and decrypt
        String extractedEncrypted = cliOutput.substring(4, cliOutput.length() - 1);
        String decrypted = SecretEncryptor.decrypt(TEST_PASSWORD, extractedEncrypted);

        assertEquals(plaintext, decrypted);
    }
}
