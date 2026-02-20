package org.yawlfoundation.yawl.config;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * EncryptionConfiguration provides Spring Boot property encryption using Jasypt.
 *
 * Features:
 * - AES-256 with PBKDF2 key derivation (PBEWithHMACSHA512AndAES_256)
 * - Random IV generation for each encryption
 * - Environment variable support (JASYPT_ENCRYPTOR_PASSWORD)
 * - Default fallback for development (plain-text password)
 * - Thread-safe encryption/decryption via PooledPBEStringEncryptor
 *
 * Usage:
 * 1. Set JASYPT_ENCRYPTOR_PASSWORD environment variable in production
 * 2. Inject StringEncryptor bean for manual encryption
 * 3. Reference encrypted values in application.yml as ENC(encrypted-value)
 * 4. Profile-based configuration (dev = plaintext, prod = encrypted)
 *
 * Example:
 *   spring.datasource.password: ENC(FwIlvHyFZVUAIVqJ5P8LkQ==)
 *
 * @author YAWL Foundation
 */
@Configuration
@EnableConfigurationProperties(EncryptorConfigurationProperties.class)
@EnableEncryptableProperties
public class EncryptionConfiguration {

    /**
     * Creates a Jasypt StringEncryptor bean for AES-256 encryption.
     *
     * @param properties EncryptorConfigurationProperties from application.yml
     * @param environment Spring Environment for password override
     * @return Configured PooledPBEStringEncryptor
     */
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor(
            EncryptorConfigurationProperties properties,
            Environment environment) {

        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        // Algorithm: AES-256 with HMAC-SHA512 PBKDF2
        config.setAlgorithm(properties.getAlgorithm());

        // Password from environment variable or properties file
        String password = environment.getProperty(
                "JASYPT_ENCRYPTOR_PASSWORD",
                properties.getPassword()
        );
        config.setPassword(password);

        // Random IV generation for enhanced security
        config.setIvGeneratorClassName(properties.getIvGeneratorClassname());

        // Key derivation iterations for PBKDF2
        config.setKeyObtentionIterations(properties.getKeyObtentionIterations());

        // Pool size for thread-safe concurrent encryption
        config.setPoolSize(properties.getPoolSize());

        encryptor.setConfig(config);
        return encryptor;
    }
}
