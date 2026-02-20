package org.yawlfoundation.yawl.config;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

/**
 * SecretEncryptor is a standalone utility for encrypting secrets at the command line.
 *
 * Usage:
 * ```
 * java org.yawlfoundation.yawl.config.SecretEncryptor \
 *   --password=mySecurePassword \
 *   --value=mysecret
 * ```
 *
 * Output format:
 * ```
 * Encrypted value (ready for application.yml):
 * ENC(FwIlvHyFZVUAIVqJ5P8LkQ==)
 * ```
 *
 * Supports:
 * - AES-256 with PBKDF2 (PBEWithHMACSHA512AndAES_256)
 * - Random IV generation
 * - Configurable iterations and pool size
 *
 * Security Notes:
 * - Use strong passwords (16+ characters)
 * - Never commit the plaintext password
 * - Store password in environment variable JASYPT_ENCRYPTOR_PASSWORD
 * - Each execution produces different ciphertext (due to random IV)
 *
 * @author YAWL Foundation
 */
public class SecretEncryptor {

    private static final String ALGORITHM = "PBEWithHMACSHA512AndAES_256";
    private static final String IV_GENERATOR = "org.jasypt.iv.RandomIvGenerator";
    private static final int ITERATIONS = 1000;
    private static final int POOL_SIZE = 8;

    /**
     * Main entry point for CLI encryption.
     *
     * @param args Command-line arguments: --password=xxx --value=secret
     */
    public static void main(String[] args) {
        String password = null;
        String value = null;

        // Parse arguments
        for (String arg : args) {
            if (arg.startsWith("--password=")) {
                password = arg.substring("--password=".length());
            } else if (arg.startsWith("--value=")) {
                value = arg.substring("--value=".length());
            }
        }

        // Validate arguments
        if (password == null || password.isEmpty()) {
            System.err.println("Error: --password argument is required");
            System.err.println("Usage: java SecretEncryptor --password=xxx --value=secret");
            System.exit(1);
        }

        if (value == null || value.isEmpty()) {
            System.err.println("Error: --value argument is required");
            System.err.println("Usage: java SecretEncryptor --password=xxx --value=secret");
            System.exit(1);
        }

        try {
            String encrypted = encrypt(password, value);
            System.out.println("Encrypted value (ready for application.yml):");
            System.out.println("ENC(" + encrypted + ")");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error encrypting value: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Encrypt a plaintext secret using the given password.
     *
     * @param password Master password for key derivation
     * @param plaintext Plaintext value to encrypt
     * @return Base64-encoded ciphertext (without ENC() wrapper)
     */
    public static String encrypt(String password, String plaintext) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        config.setAlgorithm(ALGORITHM);
        config.setPassword(password);
        config.setIvGeneratorClassName(IV_GENERATOR);
        config.setKeyObtentionIterations(ITERATIONS);
        config.setPoolSize(POOL_SIZE);

        encryptor.setConfig(config);
        return encryptor.encrypt(plaintext);
    }

    /**
     * Decrypt a ciphertext using the given password.
     * (Utility for testing, not exposed via CLI)
     *
     * @param password Master password for key derivation
     * @param ciphertext Base64-encoded ciphertext
     * @return Decrypted plaintext
     */
    public static String decrypt(String password, String ciphertext) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        config.setAlgorithm(ALGORITHM);
        config.setPassword(password);
        config.setIvGeneratorClassName(IV_GENERATOR);
        config.setKeyObtentionIterations(ITERATIONS);
        config.setPoolSize(POOL_SIZE);

        encryptor.setConfig(config);
        return encryptor.decrypt(ciphertext);
    }
}
