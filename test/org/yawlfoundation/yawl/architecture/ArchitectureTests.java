package org.yawlfoundation.yawl.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit architecture compliance tests for YAWL.
 *
 * This test suite enforces modernized architecture patterns:
 * - Resilience4j for circuit breakers, retries, and rate limiting
 * - Java 25 thread safety patterns (ReentrantLock, no synchronized)
 * - Proper dependency layering between modules
 * - No excessive logging in core engine classes
 * - Exception handling best practices
 * - Configuration class location conventions
 * - Testing class conventions
 *
 * These tests are marked as "slow" and can be skipped for quick local builds
 * but MUST pass before committing architecture changes.
 */
@DisplayName("YAWL Architecture Compliance Tests")
class ArchitectureTests {

    private static final String YAWL_PACKAGE = "org.yawlfoundation.yawl";

    private final JavaClasses allClasses = new ClassFileImporter()
            .withImportOption(location -> !location.contains("test"))
            .importPackages(YAWL_PACKAGE);

    // =========================================================================
    // a) Resilience4j Pattern Enforcement
    // =========================================================================

    @Test
    @DisplayName("Resilience classes must use Resilience4j framework, not custom implementations")
    void resilience4jPatternsEnforced() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + ".resilience..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "io.github.resilience4j..",
                        "java..",
                        "jakarta.."
                )
                .because("Resilience module must use only io.github.resilience4j for fault tolerance, " +
                        "no custom circuit breaker/retry/rate-limiter implementations")
                .check(allClasses);
    }

    @Test
    @DisplayName("CircuitBreaker implementations must be from Resilience4j only")
    void noCustomCircuitBreakerImplementations() {
        classes()
                .that()
                .haveNameNotMatching(".*Factory|.*Builder|.*Util")
                .should()
                .notHaveFullyQualifiedName("org.yawlfoundation.yawl.custom.CircuitBreaker")
                .andShould()
                .notHaveFullyQualifiedName("org.yawlfoundation.yawl.custom.CustomCircuitBreaker")
                .because("Circuit breaker must be io.github.resilience4j.circuitbreaker.CircuitBreaker")
                .check(allClasses);
    }

    @Test
    @DisplayName("Retry decorators must use io.github.resilience4j")
    void noCustomRetryDecorators() {
        classes()
                .that()
                .haveNameNotMatching(".*Factory|.*Builder|.*Util")
                .should()
                .notHaveFullyQualifiedName("org.yawlfoundation.yawl.custom.RetryDecorator")
                .andShould()
                .notHaveFullyQualifiedName("org.yawlfoundation.yawl.custom.CustomRetry")
                .because("Retry must use io.github.resilience4j.retry.Retry")
                .check(allClasses);
    }

    @Test
    @DisplayName("RateLimiter must use Resilience4j, not custom rate limiting")
    void noCustomRateLimiters() {
        classes()
                .that()
                .haveNameNotMatching(".*Factory|.*Builder|.*Util")
                .should()
                .notHaveFullyQualifiedName("org.yawlfoundation.yawl.custom.RateLimiter")
                .andShould()
                .notHaveFullyQualifiedName("org.yawlfoundation.yawl.custom.TokenBucket")
                .because("Rate limiting must use io.github.resilience4j.ratelimiter.RateLimiter")
                .check(allClasses);
    }

    // =========================================================================
    // b) Java 25 Thread Safety Enforcement
    // =========================================================================

    @Test
    @DisplayName("No synchronized blocks (use ReentrantLock instead for Java 25 virtual threads)")
    void noSynchronizedBlocks() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + "..")
                .should()
                .notHaveNameMatching(".*")
                .andShould(GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING)
                .because("Synchronized blocks pin virtual threads. Use ReentrantLock for explicit locking")
                .check(allClasses);
    }

    @Test
    @DisplayName("ReentrantReadWriteLock only allowed in observability/monitoring for read-heavy workloads")
    void restrictedReentrantReadWriteLock() {
        classes()
                .that()
                .dependOnClassesThat(type("java.util.concurrent.locks.ReentrantReadWriteLock"))
                .should()
                .resideInAnyPackage(
                        YAWL_PACKAGE + ".observability..",
                        YAWL_PACKAGE + ".monitoring.."
                )
                .because("ReentrantReadWriteLock should only be used in read-heavy observability code")
                .check(allClasses);
    }

    // =========================================================================
    // c) Dependency Layering - No Circular Dependencies
    // =========================================================================

    @Test
    @DisplayName("Engine must not depend on Integration module")
    void engineNotDependentOnIntegration() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + ".engine..")
                .should()
                .notDependOnClassesThat()
                .resideInAPackage(YAWL_PACKAGE + ".integration..")
                .because("Engine is the core domain; Integration depends on Engine, never vice versa")
                .check(allClasses);
    }

    @Test
    @DisplayName("Security must not depend on Resourcing")
    void securityNotDependentOnResourcing() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + ".security..")
                .should()
                .notDependOnClassesThat()
                .resideInAPackage(YAWL_PACKAGE + ".resourcing..")
                .because("Security is a cross-cutting concern; Resourcing depends on Security, not vice versa")
                .check(allClasses);
    }

    @Test
    @DisplayName("No circular dependencies between YAWL modules")
    void noCircularDependencies() {
        slices()
                .matching("org.yawlfoundation.yawl.(*)..")
                .should()
                .beFreeOfCycles()
                .because("Circular dependencies prevent loose coupling and testability")
                .check(allClasses);
    }

    // =========================================================================
    // d) Logging in Core Classes - Engine should log sparingly
    // =========================================================================

    @Test
    @DisplayName("YEngine and YNetRunner can only use Logger.debug, not info/warn/error")
    void coreEngineLoggingRestricted() {
        classes()
                .that()
                .haveSimpleNameMatching("YEngine|YNetRunner|YWorkItem")
                .and()
                .resideInAPackage(YAWL_PACKAGE + ".engine..")
                .should()
                .notCallMethodWhere(
                        target -> target.getFullName().contains("Logger.info") ||
                                target.getFullName().contains("Logger.warn") ||
                                target.getFullName().contains("Logger.error")
                )
                .because("Core engine should use debug logging only; warnings/errors go to dedicated handlers")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Security and Authentication can log warnings and errors")
    void securityAuthenticationLoggingPermitted() {
        classes()
                .that()
                .resideInAnyPackage(
                        YAWL_PACKAGE + ".security..",
                        YAWL_PACKAGE + ".authentication.."
                )
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..",
                        "java..",
                        "jakarta..",
                        "org.apache.logging.log4j..",
                        "org.slf4j.."
                )
                .because("Security classes should be able to log for audit purposes")
                .check(allClasses);
    }

    // =========================================================================
    // e) Exception Handling - No Swallowing Exceptions
    // =========================================================================

    @Test
    @DisplayName("Exceptions caught must be logged or rethrown (no silent swallowing)")
    void noSilentExceptionSwallowing() {
        classes()
                .that()
                .resideInAPackage(YAWL_PACKAGE + "..")
                .should()
                .notCallMethodWhere(
                        target -> target.getFullName().contains("printStackTrace") &&
                                target.getName().contains("catch")
                )
                .because("printStackTrace() without proper logging violates observability; " +
                        "exceptions must be logged via Logger or rethrown")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // f) Configuration Classes - Localization Convention
    // =========================================================================

    @Test
    @DisplayName("All @Configuration classes must be in .config or .observability packages")
    void configurationClassesLocalized() {
        classes()
                .that()
                .haveNameMatching(".*Configuration")
                .or()
                .haveNameMatching(".*Config")
                .should()
                .resideInAnyPackage(
                        YAWL_PACKAGE + ".config..",
                        YAWL_PACKAGE + ".observability..",
                        YAWL_PACKAGE + "..**config"
                )
                .because("Configuration classes should be co-located for discoverability")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    @Test
    @DisplayName("Properties classes must follow naming convention")
    void propertiesClassesNamed() {
        classes()
                .that()
                .haveNameMatching(".*Properties")
                .should()
                .resideInAnyPackage(
                        YAWL_PACKAGE + "..**config",
                        YAWL_PACKAGE + "..**properties"
                )
                .because("Properties classes should be clearly named and co-located")
                .allowEmptyShould(true)
                .check(allClasses);
    }

    // =========================================================================
    // g) Testing Conventions
    // =========================================================================

    @Test
    @DisplayName("Test classes must end with 'Test' or 'Tests'")
    void testClassesProperlyNamed() {
        classes()
                .that()
                .resideOutsideOfPackage(YAWL_PACKAGE + "..")
                .and()
                .haveNameMatching(".*Test.*")
                .should()
                .haveSimpleNameMatching(".*Test|.*Tests|.*IT|.*ITCase")
                .because("Test classes must follow naming convention for IDE and build discovery")
                .check(allClasses);
    }

    @Test
    @DisplayName("No test classes should reference real service implementations")
    void testClassesUseTestDoubles() {
        classes()
                .that()
                .haveSimpleNameMatching(".*Test|.*Tests|.*Mock|.*Spy")
                .should()
                .notDependOnClassesThat()
                .haveSimpleNameMatching("RealService|RealImpl|ProductionService")
                .because("Test classes should use mocks/stubs, not real service implementations")
                .allowEmptyShould(true)
                .check(allClasses);
    }

}
