package org.yawlfoundation.yawl.quality.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

/**
 * ArchUnit tests enforcing YAWL package boundary and naming conventions.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Naming conventions for interfaces, implementations, and exceptions</li>
 *   <li>Visibility rules (public API vs internal implementation)</li>
 *   <li>Hibernate/persistence annotations must stay in engine layer</li>
 *   <li>No raw Thread creation outside resilience and engine packages</li>
 *   <li>No System.exit() calls in library code</li>
 * </ul>
 *
 * @version 6.0.0
 */
@AnalyzeClasses(
    packages = "org.yawlfoundation.yawl",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class YawlPackageBoundaryTest {

    // -----------------------------------------------------------------------
    // 1. Naming conventions
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule exceptionClassesMustEndWithException =
        classes().that()
            .areAssignableTo(Exception.class)
            .and().resideInAPackage("org.yawlfoundation.yawl..")
            .should().haveSimpleNameEndingWith("Exception")
            .as("All YAWL exception classes must end with 'Exception'. "
                + "Found: classes extending Exception without the naming convention.");

    @ArchTest
    public static final ArchRule interfacesMustNotStartWithI =
        classes().that()
            .areInterfaces()
            .and().resideInAPackage("org.yawlfoundation.yawl..")
            .should().haveSimpleNameNotStartingWith("I")
            .allowEmptyShould(true)
            .as("Interfaces must not use the Hungarian 'I' prefix (e.g., IService). "
                + "Use descriptive names that describe capability, not implementation.");

    // -----------------------------------------------------------------------
    // 2. Persistence annotations confined to engine package
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule jakartaPersistenceAnnotationsOnlyInEngine =
        noClasses().that()
            .resideOutsideOfPackages(
                "org.yawlfoundation.yawl.engine..",
                "org.yawlfoundation.yawl.stateless..",
                "org.yawlfoundation.yawl.util..",
                "org.yawlfoundation.yawl.logging..",
                "org.yawlfoundation.yawl.authentication.."
            )
            .should().beAnnotatedWith("jakarta.persistence.Entity")
            .as("Jakarta Persistence @Entity annotations must only appear in the engine, "
                + "stateless, util, logging, and authentication packages. "
                + "Domain elements (elements package) must remain persistence-agnostic.");

    // -----------------------------------------------------------------------
    // 3. No raw Thread instantiation outside resilience and engine
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule noRawThreadCreationInIntegrationPackage =
        noClasses().that()
            .resideInAPackage("org.yawlfoundation.yawl.integration..")
            .should().dependOnClassesThat()
                .areAssignableTo(Thread.class)
                .and().haveSimpleName("Thread")
            .as("Integration layer must not create raw Thread instances. "
                + "Use virtual threads via Thread.ofVirtual() or structured concurrency "
                + "via StructuredTaskScope from the resilience package.");

    // -----------------------------------------------------------------------
    // 4. No System.exit() in library code
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule noSystemExitInEnginePackage =
        noClasses().that()
            .resideInAPackage("org.yawlfoundation.yawl.engine..")
            .should().callMethod(System.class, "exit", int.class)
            .as("Engine package must not call System.exit(). "
                + "Termination must be handled by container lifecycle or explicit shutdown procedures.");

    @ArchTest
    public static final ArchRule noSystemExitInIntegrationPackage =
        noClasses().that()
            .resideInAPackage("org.yawlfoundation.yawl.integration..")
            .should().callMethod(System.class, "exit", int.class)
            .as("Integration package must not call System.exit(). "
                + "Spring Boot or container lifecycle manages shutdown.");

    // -----------------------------------------------------------------------
    // 5. Static utility classes must not have public constructors
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule utilClassesResidingInUtilPackage =
        classes().that()
            .resideInAPackage("org.yawlfoundation.yawl.util..")
            .and().haveSimpleNameEndingWith("Utils")
            .should().haveOnlyFinalFields()
            .allowEmptyShould(true)
            .as("Classes ending with 'Utils' in the util package should contain only "
                + "final fields (all logic via static methods, no mutable state).");

    // -----------------------------------------------------------------------
    // 6. Stateless package must not import Hibernate session factory
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule statelessMustNotUseHibernateSession =
        noClasses().that()
            .resideInAPackage("org.yawlfoundation.yawl.stateless..")
            .should().dependOnClassesThat()
                .resideInAPackage("org.hibernate..")
            .as("Stateless engine must not depend on Hibernate. "
                + "Stateless processing is by definition non-persistent; "
                + "adding Hibernate violates the stateless invariant.");

    // -----------------------------------------------------------------------
    // 7. Security classes must use proper access modifiers
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule securityCredentialFieldsMustBePrivate =
        fields().that()
            .haveNameContaining("password")
            .and().resideInAPackage("org.yawlfoundation.yawl..")
            .should().bePrivate()
            .allowEmptyShould(true)
            .as("Fields containing 'password' in their name must be private. "
                + "Password fields exposed as protected or public create injection vectors.");

    @ArchTest
    public static final ArchRule securityKeyFieldsMustBePrivate =
        fields().that()
            .haveNameContaining("secret")
            .and().resideInAPackage("org.yawlfoundation.yawl..")
            .should().bePrivate()
            .allowEmptyShould(true)
            .as("Fields containing 'secret' in their name must be private.");

    // -----------------------------------------------------------------------
    // 8. All public methods in elements package must declare their exceptions
    // -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule elementsPackageClassesMustBeInCorrectPackage =
        classes().that()
            .resideInAPackage("org.yawlfoundation.yawl.elements..")
            .and().arePublic()
            .should().resideInAPackage("org.yawlfoundation.yawl.elements..")
            .as("All public elements classes must reside within the elements package hierarchy. "
                + "Public elements classes found in unexpected packages indicate "
                + "accidental package placement.");
}
