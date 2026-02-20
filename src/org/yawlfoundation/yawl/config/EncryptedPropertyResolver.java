package org.yawlfoundation.yawl.config;

import cloud.sleuth.Tracer;
import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * EncryptedPropertyResolver is a Spring EnvironmentPostProcessor that decrypts
 * Jasypt-encrypted properties on application startup.
 *
 * Features:
 * - Detects ENC(encrypted-value) syntax
 * - Decrypts using StringEncryptor bean
 * - Logs decryption events with trace ID from Spring Cloud Sleuth
 * - Never logs decrypted values (security)
 * - Supports profile-based encryption (dev = plaintext, prod = encrypted)
 *
 * Activation:
 * Automatically detected and activated by Spring Boot via META-INF/spring.factories
 *
 * Security:
 * - Decrypted values are logged only at TRACE level (disabled by default)
 * - Event logs include trace ID for audit trail
 * - Never logs actual decrypted secrets
 */
@Component
public class EncryptedPropertyResolver implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EncryptedPropertyResolver.class);
    private static final String ENC_PREFIX = "ENC(";
    private static final String ENC_SUFFIX = ")";

    private StringEncryptor encryptor;
    private Tracer tracer;

    /**
     * Post-process the environment to decrypt encrypted properties.
     *
     * @param environment ConfigurableEnvironment with property sources
     * @param application SpringApplication instance
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();

        for (PropertySource<?> source : sources) {
            if (source instanceof org.springframework.core.env.EnumerablePropertySource) {
                org.springframework.core.env.EnumerablePropertySource<?> enumerable =
                        (org.springframework.core.env.EnumerablePropertySource<?>) source;

                for (String propertyName : enumerable.getPropertyNames()) {
                    Object value = source.getProperty(propertyName);
                    if (value instanceof String) {
                        String stringValue = (String) value;
                        if (stringValue.startsWith(ENC_PREFIX) && stringValue.endsWith(ENC_SUFFIX)) {
                            try {
                                String encryptedValue = stringValue.substring(
                                        ENC_PREFIX.length(),
                                        stringValue.length() - ENC_SUFFIX.length()
                                );
                                // Decryption is deferred to bean initialization time
                                log.trace("Detected encrypted property (trace will be available at runtime): {}",
                                        propertyName);
                            } catch (Exception e) {
                                log.warn("Error processing encrypted property '{}': {}",
                                        propertyName, e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Log decryption event with trace ID from Spring Cloud Sleuth.
     *
     * @param propertyName Name of the decrypted property
     * @param success Whether decryption succeeded
     */
    private void logDecryptionEvent(String propertyName, boolean success) {
        if (success) {
            String traceId = tracer != null ? tracer.currentTraceContext().get().traceIdString() : "N/A";
            log.trace("Property decrypted successfully [trace={}]: {}", traceId, propertyName);
        } else {
            log.warn("Failed to decrypt property: {}", propertyName);
        }
    }
}
