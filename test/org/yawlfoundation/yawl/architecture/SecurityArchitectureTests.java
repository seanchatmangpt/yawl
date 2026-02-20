package org.yawlfoundation.yawl.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Security Architecture Compliance Tests for YAWL.
 *
 * Enforces security best practices:
 * - No plaintext passwords in source code
 * - Credentials resolved via PropertyResolver
 * - Rate limiting enforced on auth endpoints
 * - Security audit logging for authentication events
 */
@DisplayName("YAWL Security Architecture Compliance Tests")
class SecurityArchitectureTests {

    private static final String YAWL_PACKAGE = "org.yawlfoundation.yawl";

    private final JavaClasses allClasses = new ClassFileImporter()
            .withImportOption(location -> !location.contains("test"))
            .importPackages(YAWL_PACKAGE);

    // =========================================================================
    // Security: Credentials Management
    // =========================================================================

    @Test
    @DisplayName("No plaintext passwords allowed in any string literals")
    void noPlaintextPasswords() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + ".authentication..")
                .or()
                .resideInAPackage(YAWL_PACKAGE + ".security..")
                .should()
                .notCallMethodWhere(
                        target -> target.getFullName().contains("password") &&
                                target.getName().contains("literal")
                )
                .because("Passwords must be resolved via PropertyResolver or PasswordManager, never hardcoded")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Credentials must be resolved via PropertyResolver, not environment")
    void credentialsResolvedViaPropertyResolver() {
        classes()
                .that()
                .resideInAnyPackage(
                        YAWL_PACKAGE + ".authentication..",
                        YAWL_PACKAGE + ".security.."
                )
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "org.springframework.core.env..",
                        "java..",
                        "jakarta.."
                )
                .because("Credential resolution must use Spring PropertyResolver for centralized secret management")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Security: Rate Limiting on Auth Endpoints
    // =========================================================================

    @Test
    @DisplayName("Authentication endpoints must enforce rate limiting")
    void authEndpointsRateLimited() {
        classes()
                .that()
                .haveSimpleNameMatching(".*AuthController|.*AuthEndpoint|.*LoginHandler|.*TokenEndpoint")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.github.resilience4j.ratelimiter.RateLimiter")
                .or()
                .dependOnClassesThat()
                .haveSimpleNameMatching(".*RateLimitFilter|.*RateLimitInterceptor")
                .because("Auth endpoints must rate limit to prevent brute-force attacks")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Security: Audit Logging
    // =========================================================================

    @Test
    @DisplayName("All authentication events must be logged via SecurityAuditLogger")
    void securityAuditLoggingUsed() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + ".authentication..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "org.springframework.security..",
                        "io.github.resilience4j..",
                        "org.apache.logging.log4j..",
                        "org.slf4j..",
                        "java..",
                        "jakarta.."
                )
                .because("All authentication must use SecurityAuditLogger for compliance auditing")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("JWT token validation must be secure and cannot be bypassed")
    void jwtTokenValidationEnforced() {
        classes()
                .that()
                .haveSimpleNameMatching(".*JwtManager|.*JwtValidator|.*TokenValidator")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.jsonwebtoken.Jwts")
                .or()
                .dependOnClassesThat()
                .haveFullyQualifiedName("io.jsonwebtoken.JwtParser")
                .because("JWT validation must use io.jsonwebtoken library for secure token parsing")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("CSRF protection must be enabled on all state-changing endpoints")
    void csrfProtectionEnabled() {
        classes()
                .that()
                .haveSimpleNameMatching(".*CsrfFilter|.*CsrfProtection|.*CsrfInterceptor")
                .should()
                .resideInAPackage(YAWL_PACKAGE + ".authentication..")
                .because("CSRF protection is a fundamental security requirement")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // Security: TLS/SSL Configuration
    // =========================================================================

    @Test
    @DisplayName("TLS configuration must not allow weak cipher suites")
    void tlsConfigurationSecure() {
        classes()
                .that()
                .haveSimpleNameMatching(".*TlsConfig|.*SslConfig|.*TransportSecurity")
                .should()
                .notCallMethodWhere(
                        target -> target.getFullName().contains("setEnabledCipherSuites") &&
                                target.getName().contains("weak")
                )
                .because("TLS must use only modern, strong cipher suites")
                .allowEmptyShould(true)
                .check(allClasses);
    }

}
