package org.yawlfoundation.yawl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * EncryptorConfigurationProperties binds jasypt configuration from application.yml.
 *
 * Supports profile-based overrides:
 * - dev: plain-text password (dev-password)
 * - prod: environment variable JASYPT_ENCRYPTOR_PASSWORD
 *
 * Example configuration (application-prod.yml):
 * ```
 * jasypt:
 *   encryptor:
 *     algorithm: PBEWithHMACSHA512AndAES_256
 *     password: ${JASYPT_ENCRYPTOR_PASSWORD}
 *     iv-generator-classname: org.jasypt.iv.RandomIvGenerator
 *     key-obtention-iterations: 1000
 *     pool-size: 8
 * ```
 */
@Component
@ConfigurationProperties(prefix = "jasypt.encryptor")
public class EncryptorConfigurationProperties {

    /**
     * Encryption algorithm.
     * Default: PBEWithHMACSHA512AndAES_256 (AES-256 with PBKDF2)
     */
    private String algorithm = "PBEWithHMACSHA512AndAES_256";

    /**
     * Master password for key derivation.
     * CRITICAL: Override with JASYPT_ENCRYPTOR_PASSWORD environment variable in production.
     * Default (dev only): dev-password
     */
    private String password = "dev-password";

    /**
     * IV generator class name.
     * RandomIvGenerator: generates random IV for each encryption (recommended)
     * NoIvGenerator: no IV (less secure, not recommended)
     */
    private String ivGeneratorClassname = "org.jasypt.iv.RandomIvGenerator";

    /**
     * PBKDF2 key derivation iterations.
     * Higher = more secure but slower.
     * Default: 1000
     * Production: recommend 10000+
     */
    private int keyObtentionIterations = 1000;

    /**
     * Encryptor pool size for thread-safe concurrent encryption.
     * Default: 8 (good for typical server loads)
     */
    private int poolSize = 8;

    // Getters and Setters

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getIvGeneratorClassname() {
        return ivGeneratorClassname;
    }

    public void setIvGeneratorClassname(String ivGeneratorClassname) {
        this.ivGeneratorClassname = ivGeneratorClassname;
    }

    public int getKeyObtentionIterations() {
        return keyObtentionIterations;
    }

    public void setKeyObtentionIterations(int keyObtentionIterations) {
        this.keyObtentionIterations = keyObtentionIterations;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
}
