package org.yawlfoundation.yawl.quality.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit tests enforcing YAWL v6.0.0 architectural layer boundaries.
 *
 * <h2>Layer Model</h2>
 * <pre>
 *   integration  (top-most: MCP, A2A, external consumers)
 *       |
 *   engine       (stateful workflow execution, persistence)
 *       |
 *   stateless    (in-memory workflow execution, no persistence)
 *       |
 *   elements     (Petri net element model: tasks, conditions, nets)
 *       |
 *   authentication  (session, JWT, CSRF)
 *       |
 *   util / schema / exceptions / logging  (infrastructure, no domain deps)
 * </pre>
 *
 * <p>Rules enforced:
 * <ol>
 *   <li>No reverse dependencies (lower layers must not import upper layers)</li>
 *   <li>Elements must not import engine or integration code</li>
 *   <li>Util/schema/exceptions must not import any domain packages</li>
 *   <li>No cycles within the org.yawlfoundation.yawl namespace</li>
 *   <li>Security-sensitive classes must not be accessed from UI packages</li>
 * </ol>
 *
 * @version 6.0.0
 */
@AnalyzeClasses(
    packages = "org.yawlfoundation.yawl",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class YawlLayerArchitectureTest {

    private static final String ELEMENTS_PKG    = "org.yawlfoundation.yawl.elements..";
    private static final String ENGINE_PKG      = "org.yawlfoundation.yawl.engine..";
    private static final String STATELESS_PKG   = "org.yawlfoundation.yawl.stateless..";
    private static final String INTEGRATION_PKG = "org.yawlfoundation.yawl.integration..";
    private static final String AUTH_PKG        = "org.yawlfoundation.yawl.authentication..";
    private static final String SECURITY_PKG    = "org.yawlfoundation.yawl.security..";
    private static final String UTIL_PKG        = "org.yawlfoundation.yawl.util..";
    private static final String SCHEMA_PKG      = "org.yawlfoundation.yawl.schema..";
    private static final String EXCEPTIONS_PKG  = "org.yawlfoundation.yawl.exceptions..";
    private static final String LOGGING_PKG     = "org.yawlfoundation.yawl.logging..";
    private static final String UNMARSHAL_PKG   = "org.yawlfoundation.yawl.unmarshal..";
    private static final String SWING_PKG       = "org.yawlfoundation.yawl.swingWorklist..";

    // -----------------------------------------------------------------------
    // 1. Full layered architecture: no upward dependencies
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule layerIsolationRule = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Infrastructure").definedBy(
            UTIL_PKG, SCHEMA_PKG, EXCEPTIONS_PKG, LOGGING_PKG)
        .layer("Authentication").definedBy(AUTH_PKG, SECURITY_PKG)
        .layer("Unmarshal").definedBy(UNMARSHAL_PKG)
        .layer("Elements").definedBy(ELEMENTS_PKG)
        .layer("Stateless").definedBy(STATELESS_PKG)
        .layer("Engine").definedBy(ENGINE_PKG)
        .layer("Integration").definedBy(INTEGRATION_PKG)

        // Each layer may only be accessed by layers above it.
        .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers(
            "Authentication", "Unmarshal", "Elements", "Stateless", "Engine", "Integration")
        .whereLayer("Authentication").mayOnlyBeAccessedByLayers(
            "Engine", "Integration")
        .whereLayer("Unmarshal").mayOnlyBeAccessedByLayers(
            "Elements", "Stateless", "Engine", "Integration")
        .whereLayer("Elements").mayOnlyBeAccessedByLayers(
            "Stateless", "Engine", "Integration")
        .whereLayer("Stateless").mayOnlyBeAccessedByLayers(
            "Engine", "Integration")
        .whereLayer("Engine").mayOnlyBeAccessedByLayers(
            "Integration")
        .whereLayer("Integration").mayNotBeAccessedByAnyLayer();

    // -----------------------------------------------------------------------
    // 2. Elements must not import engine or integration
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule elementsMustNotDependOnEngine =
        noClasses().that().resideInAPackage(ELEMENTS_PKG)
            .should().dependOnClassesThat()
                .resideInAPackage(ENGINE_PKG)
            .as("Elements package must not depend on Engine package. "
                + "Engine depends on Elements, not the other way around.");

    @ArchTest
    public static final ArchRule elementsMustNotDependOnIntegration =
        noClasses().that().resideInAPackage(ELEMENTS_PKG)
            .should().dependOnClassesThat()
                .resideInAPackage(INTEGRATION_PKG)
            .as("Elements package must not depend on Integration package.");

    // -----------------------------------------------------------------------
    // 3. Infrastructure packages must not import domain packages
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule utilMustNotDependOnEngine =
        noClasses().that().resideInAPackage(UTIL_PKG)
            .should().dependOnClassesThat()
                .resideInAnyPackage(ENGINE_PKG, ELEMENTS_PKG, STATELESS_PKG, INTEGRATION_PKG)
            .as("Util package must not depend on domain packages. "
                + "Util is a pure infrastructure library.");

    @ArchTest
    public static final ArchRule schemaPackageMustNotDependOnEngine =
        noClasses().that().resideInAPackage(SCHEMA_PKG)
            .should().dependOnClassesThat()
                .resideInAnyPackage(ENGINE_PKG, INTEGRATION_PKG)
            .as("Schema package must not depend on Engine or Integration packages.");

    @ArchTest
    public static final ArchRule exceptionsMustNotDependOnEngine =
        noClasses().that().resideInAPackage(EXCEPTIONS_PKG)
            .should().dependOnClassesThat()
                .resideInAnyPackage(ENGINE_PKG, ELEMENTS_PKG, STATELESS_PKG, INTEGRATION_PKG)
            .as("Exceptions package must not depend on domain packages. "
                + "Domain packages depend on exceptions, not the other way around.");

    // -----------------------------------------------------------------------
    // 4. Stateless engine must not import stateful engine persistence
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule statelessMustNotDependOnEnginePersistence =
        noClasses().that().resideInAPackage(STATELESS_PKG)
            .should().dependOnClassesThat()
                .haveSimpleNameContaining("Persistence")
                .and().resideInAPackage(ENGINE_PKG)
            .as("Stateless engine must not import persistence classes from the stateful engine. "
                + "Stateless operation is the invariant; adding persistence breaks isolation.");

    @ArchTest
    public static final ArchRule statelessMustNotDependOnYEngine =
        noClasses().that().resideInAPackage(STATELESS_PKG)
            .should().dependOnClassesThat()
                .haveSimpleName("YEngine")
            .as("Stateless engine must not depend on the stateful YEngine class. "
                + "YStatelessEngine must operate independently.");

    // -----------------------------------------------------------------------
    // 5. No cycles in the org.yawlfoundation.yawl namespace
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule noCyclicPackageDependencies =
        slices().matching("org.yawlfoundation.yawl.(*)..")
            .should().beFreeOfCycles()
            .as("Top-level YAWL packages must be free of cyclic dependencies. "
                + "Cycles indicate design debt and prevent independent module deployment.");

    // -----------------------------------------------------------------------
    // 6. Security-sensitive classes must not be accessed from UI/swing layer
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule swingMustNotAccessSecurityInternals =
        noClasses().that().resideInAPackage(SWING_PKG)
            .should().dependOnClassesThat()
                .resideInAPackage(SECURITY_PKG)
            .as("Swing worklist UI must not access security internals. "
                + "Authentication must be handled by the engine layer.");

    // -----------------------------------------------------------------------
    // 7. Authentication package must not depend on engine implementation details
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule authMustNotDependOnEnginePersistence =
        noClasses().that().resideInAPackage(AUTH_PKG)
            .should().dependOnClassesThat()
                .haveNameMatching(".*YPersistenceManager.*")
            .as("Authentication must not depend on YPersistenceManager. "
                + "Auth is a cross-cutting concern independent of persistence strategy.");

    // -----------------------------------------------------------------------
    // 8. Integration layer must not bypass authentication
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule integrationMustNotInstantiateSessionsDirectly =
        noClasses().that().resideInAPackage(INTEGRATION_PKG)
            .should().accessClassesThat()
                .haveSimpleName("YSessionCache")
                .and().resideInAPackage(AUTH_PKG)
            .as("Integration layer must not directly access YSessionCache. "
                + "Session management must go through the Authentication API.");
}
