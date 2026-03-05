/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.validation.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.*;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.integration.a2a.validation.SchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.security.KeyException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JWT Validator for A2A authentication.
 *
 * <p>This validator provides comprehensive JWT validation for A2A protocol
 * including signature verification, claim validation, expiration checking,
 * and business rule validation. It supports both HMAC and RSA signatures.
 *
 * <p>Features:
 * - JWT signature verification (HMAC-SHA256, RSA)
 * - Claim validation against schema
 * - Expiration and not-before checks
 * - Audience validation
 * - Permission validation
 * - Clock skew tolerance
 * - Custom claim validation rules
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class JwtValidator {

    private static final Logger _logger = LoggerFactory.getLogger(JwtValidator.class);

    private final SchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Clock clockWithSkew;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final String expectedAudience;
    private final int clockSkewSeconds;
    private final Set<String> requiredClaims;
    private final Map<String, ClaimValidator> claimValidators;

    /**
     * Constructs a JwtValidator with HMAC-SHA256 algorithm.
     *
     * @param secret HMAC secret key (minimum 32 characters)
     * @param expectedAudience Expected audience value
     * @param clockSkewSeconds Allowed clock skew in seconds
     */
    public JwtValidator(String secret, String expectedAudience, int clockSkewSeconds) {
        this(secret, expectedAudience, clockSkewSeconds, null);
    }

    /**
     * Constructs a JwtValidator with specified algorithm.
     *
     * @param secretOrPublicKey Secret key (HMAC) or public key (RSA)
     * @param expectedAudience Expected audience value
     * @param clockSkewSeconds Allowed clock skew in seconds
     * @param algorithm Algorithm type (null for HMAC with derived secret)
     */
    public JwtValidator(Object secretOrPublicKey, String expectedAudience,
                       int clockSkewSeconds, Algorithm algorithm) {
        this.schemaValidator = new SchemaValidator();
        this.objectMapper = new ObjectMapper();
        this.clock = Clock.systemUTC();
        this.clockSkewSeconds = clockSkewSeconds;
        this.expectedAudience = expectedAudience;
        this.clockWithSkew = Clock.offset(clock, java.time.Duration.ofSeconds(clockSkewSeconds));

        // Build JWT verifier
        this.algorithm = algorithm != null ? algorithm :
            Algorithm.HMAC256(secretOrPublicKey.toString());

        this.verifier = JWT.require(algorithm)
            .withAudience(expectedAudience)
            .withIssuer("yawl-a2a-server")
            .acceptExpiresAt(clockSkewSeconds)
            .acceptNotBefore(-clockSkewSeconds)
            .build();

        // Configure required claims and validators
        this.requiredClaims = Set.of("sub", "exp", "iat");
        this.claimValidators = new HashMap<>();
        configureClaimValidators();
    }

    /**
     * Validates a JWT string and returns validation result.
     *
     * @param jwtString JWT string to validate
     * @return ValidationResult containing validation details
     */
    public ValidationResult validate(String jwtString) {
        try {
            // First, check basic JWT structure
            if (!isValidJwtFormat(jwtString)) {
                return ValidationResult.failure("Invalid JWT format");
            }

            // Decode JWT without verification (for claim checking)
            DecodedJWT decodedJWT = JWT.decode(jwtString);

            // Validate claims against schema
            ValidationResult schemaResult = validateJwtClaims(decodedJWT);
            if (!schemaResult.isValid()) {
                return schemaResult;
            }

            // Verify signature
            VerificationResult verificationResult = verifySignature(jwtString);
            if (!verificationResult.isValid()) {
                return verificationResult;
            }

            // Check business rules
            BusinessRuleResult businessRuleResult = checkBusinessRules(decodedJWT);
            if (!businessRuleResult.isValid()) {
                return businessRuleResult;
            }

            return ValidationResult.success();

        } catch (JWTDecodeException e) {
            return ValidationResult.failure("Invalid JWT format: " + e.getMessage());
        } catch (Exception e) {
            _logger.error("JWT validation error: {}", e.getMessage());
            return ValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates JWT claims against the schema.
     *
     * @param decodedJWT Decoded JWT
     * @return ValidationResult
     */
    private ValidationResult validateJwtClaims(DecodedJWT decodedJWT) {
        try {
            String claimsJson = objectMapper.writeValueAsString(decodedJWT.getClaims());
            return schemaValidator.validateJwtClaims(claimsJson);
        } catch (Exception e) {
            return ValidationResult.failure("Failed to validate claims: " + e.getMessage());
        }
    }

    /**
     * Verifies the JWT signature.
     *
     * @param jwtString JWT string to verify
     * @return VerificationResult
     */
    private VerificationResult verifySignature(String jwtString) {
        try {
            DecodedJWT decoded = verifier.verify(jwtString);
            return VerificationResult.success(decoded);
        } catch (AlgorithmMismatchException e) {
            return VerificationResult.failure("Algorithm mismatch");
        } catch (SignatureVerificationException e) {
            return VerificationResult.failure("Invalid signature");
        } catch (TokenExpiredException e) {
            return VerificationResult.failure("Token expired");
        } catch (InvalidClaimException e) {
            return VerificationResult.failure("Invalid claim: " + e.getMessage());
        } catch (JWTVerificationException e) {
            return VerificationResult.failure("Signature verification failed: " + e.getMessage());
        }
    }

    /**
     * Checks business rules for the JWT.
     *
     * @param decodedJWT Decoded JWT
     * @return BusinessRuleResult
     */
    private BusinessRuleResult checkBusinessRules(DecodedJWT decodedJWT) {
        // Check expiration with tolerance
        Instant now = clock.instant();
        Instant expiry = Instant.ofEpochSecond(decodedJWT.getExpiresAt().getTime() / 1000);
        if (expiry.isBefore(now.minus(java.time.Duration.ofSeconds(clockSkewSeconds)))) {
            return BusinessRuleResult.failure("Token has expired");
        }

        // Check not before time
        Instant notBefore = Instant.ofEpochSecond(decodedJWT.getNotBefore().getTime() / 1000);
        if (notAfter.isAfter(now.plus(java.time.Duration.ofSeconds(clockSkewSeconds)))) {
            return BusinessRuleResult.failure("Token not yet valid");
        }

        // Check required claims
        for (String claim : requiredClaims) {
            if (decodedJWT.getClaim(claim) == null) {
                return BusinessRuleResult.failure("Missing required claim: " + claim);
            }
        }

        // Validate subject format
        String subject = decodedJWT.getSubject();
        if (!isValidSubject(subject)) {
            return BusinessRuleResult.failure("Invalid subject format");
        }

        // Validate permissions if present
        if (decodedJWT.getClaim("permissions") != null) {
            ValidationResult permResult = validatePermissions(decodedJWT);
            if (!permResult.isValid()) {
                return permResult;
            }
        }

        // Validate tenant if present
        if (decodedJWT.getClaim("tenantId") != null) {
            ValidationResult tenantResult = validateTenant(decodedJWT);
            if (!tenantResult.isValid()) {
                return tenantResult;
            }
        }

        return BusinessRuleResult.success();
    }

    /**
     * Validates JWT permissions.
     *
     * @param decodedJWT Decoded JWT
     * @return ValidationResult
     */
    private ValidationResult validatePermissions(DecodedJWT decodedJWT) {
        try {
            JsonNode permissionsNode = decodedJWT.getClaim("permissions");
            if (!permissionsNode.isArray()) {
                return ValidationResult.failure("Permissions must be an array");
            }

            List<String> permissions = new ArrayList<>();
            for (JsonNode node : permissionsNode) {
                if (!node.isTextual()) {
                    return ValidationResult.failure("All permissions must be strings");
                }
                permissions.add(node.asText());
            }

            // Validate permission format
            for (String permission : permissions) {
                if (!isValidPermission(permission)) {
                    return ValidationResult.failure("Invalid permission format: " + permission);
                }
            }

            // Check for required permissions based on context
            // This would be implemented based on the specific endpoint being accessed
            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.failure("Permission validation error: " + e.getMessage());
        }
    }

    /**
     * Validates tenant information.
     *
     * @param decodedJWT Decoded JWT
     * @return ValidationResult
     */
    private ValidationResult validateTenant(DecodedJWT decodedJWT) {
        String tenantId = decodedJWT.getClaim("tenantId").asString();
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return ValidationResult.failure("Tenant ID cannot be empty");
        }

        // Additional tenant validation rules
        if (!isValidTenantId(tenantId)) {
            return ValidationResult.failure("Invalid tenant ID format");
        }

        return ValidationResult.success();
    }

    /**
     * Checks if JWT string has valid format.
     *
     * @param jwtString JWT string to check
     * @return true if format is valid
     */
    private boolean isValidJwtFormat(String jwtString) {
        if (jwtString == null || jwtString.trim().isEmpty()) {
            return false;
        }

        String[] parts = jwtString.split("\\.");
        if (parts.length != 3) {
            return false;
        }

        // Check each part is valid base64url
        for (String part : parts) {
            if (part.isEmpty() || !isValidBase64Url(part)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Validates base64url encoding.
     *
     * @param str String to validate
     * @return true if valid base64url
     */
    private boolean isValidBase64Url(String str) {
        return str.matches("^[A-Za-z0-9_-]+$");
    }

    /**
     * Validates subject format.
     *
     * @param subject Subject to validate
     * @return true if valid
     */
    private boolean isValidSubject(String subject) {
        return subject != null &&
               subject.matches("^[a-zA-Z0-9_\\-@.]{1,100}$");
    }

    /**
     * Validates permission format.
     *
     * @param permission Permission to validate
     * @return true if valid
     */
    private boolean isValidPermission(String permission) {
        return permission != null &&
               permission.matches("^[a-zA-Z0-9_\\-:]+$");
    }

    /**
     * Validates tenant ID format.
     *
     * @param tenantId Tenant ID to validate
     * @return true if valid
     */
    private boolean isValidTenantId(String tenantId) {
        return tenantId != null &&
               tenantId.matches("^[a-zA-Z0-9_\\-]{1,100}$");
    }

    /**
     * Configures custom claim validators.
     */
    private void configureClaimValidators() {
        // Add custom validators for specific claims
        claimValidators.put("sub", new SubjectValidator());
        claimValidators.put("exp", new ExpirationValidator());
        claimValidators.put("iat", new IssuedAtValidator());
        claimValidators.put("permissions", new PermissionsValidator());
        claimValidators.put("tenantId", new TenantIdValidator());
    }

    /**
     * Validation result for JWT validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final Map<String, Object> details;
        private final Instant timestamp;

        private ValidationResult(boolean valid, String message, Map<String, Object> details) {
            this.valid = valid;
            this.message = message;
            this.details = details != null ? details : new HashMap<>();
            this.timestamp = Instant.now();
        }

        public static ValidationResult success() {
            return new ValidationResult(true, "JWT validation successful", null);
        }

        public static ValidationResult success(Map<String, Object> details) {
            return new ValidationResult(true, "JWT validation successful", details);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message, null);
        }

        public static ValidationResult failure(String message, Map<String, Object> details) {
            return new ValidationResult(false, message, details);
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return Map.copyOf(details); }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Result of signature verification.
     */
    public static class VerificationResult extends ValidationResult {
        private final DecodedJWT decodedJWT;

        private VerificationResult(boolean valid, String message, Map<String, Object> details,
                                 DecodedJWT decodedJWT) {
            super(valid, message, details);
            this.decodedJWT = decodedJWT;
        }

        public static VerificationResult success(DecodedJWT decodedJWT) {
            return new VerificationResult(true, "Signature verified",
                                        Map.of("decoded", decodedJWT), decodedJWT);
        }

        public static VerificationResult failure(String message) {
            return new VerificationResult(false, message, null, null);
        }

        public DecodedJWT getDecodedJWT() { return decodedJWT; }
    }

    /**
     * Result of business rule validation.
     */
    public static class BusinessRuleResult extends ValidationResult {
        private BusinessRuleResult(boolean valid, String message, Map<String, Object> details) {
            super(valid, message, details);
        }

        public static BusinessRuleResult success() {
            return new BusinessRuleResult(true, "Business rules passed", null);
        }

        public static BusinessRuleResult failure(String message) {
            return new BusinessRuleResult(false, message, null);
        }

        public static BusinessRuleResult failure(String message, Map<String, Object> details) {
            return new BusinessRuleResult(false, message, details);
        }
    }

    /**
     * Interface for custom claim validators.
     */
    private interface ClaimValidator {
        ValidationResult validate(DecodedJWT jwt);
    }

    /**
     * Validator for subject claim.
     */
    private static class SubjectValidator implements ClaimValidator {
        @Override
        public ValidationResult validate(DecodedJWT jwt) {
            String subject = jwt.getSubject();
            if (subject == null || subject.trim().isEmpty()) {
                return ValidationResult.failure("Subject cannot be empty");
            }
            if (!subject.matches("^[a-zA-Z0-9_\\-@.]{1,100}$")) {
                return ValidationResult.failure("Invalid subject format");
            }
            return ValidationResult.success();
        }
    }

    /**
     * Validator for expiration claim.
     */
    private static class ExpirationValidator implements ClaimValidator {
        @Override
        public ValidationResult validate(DecodedJWT jwt) {
            try {
                Date exp = jwt.getExpiresAt();
                if (exp == null) {
                    return ValidationResult.failure("Expiration claim is required");
                }

                // Check if expired (with clock skew)
                Instant now = Instant.now();
                Instant expiry = exp.toInstant();
                if (expiry.isBefore(now.minus(java.time.Duration.ofSeconds(5)))) {
                    return ValidationResult.failure("Token has expired");
                }

                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("Expiration validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator for issued at claim.
     */
    private static class IssuedAtValidator implements ClaimValidator {
        @Override
        public ValidationResult validate(DecodedJWT jwt) {
            try {
                Date iat = jwt.getIssuedAt();
                if (iat == null) {
                    return ValidationResult.failure("Issued at claim is required");
                }

                // Check if issued in the future (with clock skew)
                Instant now = Instant.now();
                Instant issued = iat.toInstant();
                if (issued.isAfter(now.plus(java.time.Duration.ofSeconds(5)))) {
                    return ValidationResult.failure("Token issued in the future");
                }

                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("Issued at validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator for permissions claim.
     */
    private static class PermissionsValidator implements ClaimValidator {
        @Override
        public ValidationResult validate(DecodedJWT jwt) {
            try {
                Object permissions = jwt.getClaim("permissions").as(Object.class);
                if (permissions == null) {
                    return ValidationResult.success(); // Optional claim
                }

                if (!(permissions instanceof List)) {
                    return ValidationResult.failure("Permissions must be an array");
                }

                List<?> permissionList = (List<?>) permissions;
                for (Object permission : permissionList) {
                    if (!(permission instanceof String)) {
                        return ValidationResult.failure("All permissions must be strings");
                    }
                    String perm = (String) permission;
                    if (!perm.matches("^[a-zA-Z0-9_\\-:]+$")) {
                        return ValidationResult.failure("Invalid permission format: " + perm);
                    }
                }

                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("Permissions validation error: " + e.getMessage());
            }
        }
    }

    /**
     * Validator for tenant ID claim.
     */
    private static class TenantIdValidator implements ClaimValidator {
        @Override
        public ValidationResult validate(DecodedJWT jwt) {
            try {
                String tenantId = jwt.getClaim("tenantId").asString();
                if (tenantId == null || tenantId.trim().isEmpty()) {
                    return ValidationResult.failure("Tenant ID cannot be empty");
                }

                if (!tenantId.matches("^[a-zA-Z0-9_\\-]{1,100}$")) {
                    return ValidationResult.failure("Invalid tenant ID format");
                }

                return ValidationResult.success();
            } catch (Exception e) {
                return ValidationResult.failure("Tenant ID validation error: " + e.getMessage());
            }
        }
    }
}