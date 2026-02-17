package org.yawlfoundation.yawl.quality.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ArchUnit cycle detection tests for YAWL v6.0.0.
 *
 * <p>Package-level cycles are the primary cause of fragile, hard-to-test code
 * because they prevent independent compilation and deployment of modules.
 * These tests fail the build when any cycle is introduced.
 *
 * <p>Slicing strategy:
 * <ul>
 *   <li>First slice: top-level package (engine, elements, stateless, etc.)</li>
 *   <li>Second slice: sub-package within integration (mcp, a2a, autonomous, etc.)</li>
 * </ul>
 *
 * @version 6.0.0
 */
@AnalyzeClasses(
    packages = "org.yawlfoundation.yawl",
    importOptions = {ImportOption.DoNotIncludeTests.class}
)
public class YawlCycleDetectionTest {

    /**
     * No cycles between top-level YAWL packages.
     *
     * A cycle between e.g. engine and elements would mean they cannot be
     * refactored independently and cannot be deployed as separate modules.
     */
    @ArchTest
    public static final ArchRule noTopLevelPackageCycles =
        slices().matching("org.yawlfoundation.yawl.(*)..")
            .should().beFreeOfCycles()
            .as("Top-level YAWL packages must have no cyclic dependencies. "
                + "Each package in org.yawlfoundation.yawl.* must be independently "
                + "compilable without importing from a package that imports from it.");

    /**
     * No cycles within the integration sub-packages.
     *
     * The integration layer is the most likely place for accidental cycles
     * because it coordinates multiple subsystems (MCP, A2A, autonomous agents).
     */
    @ArchTest
    public static final ArchRule noIntegrationSubPackageCycles =
        slices().matching("org.yawlfoundation.yawl.integration.(*)..")
            .should().beFreeOfCycles()
            .as("Integration sub-packages must not form cycles. "
                + "MCP, A2A, and autonomous agent sub-packages must be independently "
                + "deployable integration adapters.");

    /**
     * No cycles within the engine sub-packages.
     *
     * Engine sub-packages (runner, persistence, workitem) represent distinct
     * responsibilities that must not create mutual dependencies.
     */
    @ArchTest
    public static final ArchRule noEngineSubPackageCycles =
        slices().matching("org.yawlfoundation.yawl.engine.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .as("Engine sub-packages must not form cycles. "
                + "YEngine, YNetRunner, YPersistenceManager, and workitem classes "
                + "must have a clear dependency order.");

    /**
     * No cycles within the stateless sub-packages.
     *
     * The stateless engine mirrors the stateful engine's structure but must
     * remain entirely cycle-free to guarantee pure in-memory execution semantics.
     */
    @ArchTest
    public static final ArchRule noStatelessSubPackageCycles =
        slices().matching("org.yawlfoundation.yawl.stateless.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .as("Stateless engine sub-packages must not form cycles.");

    /**
     * No cycles within the authentication sub-packages.
     */
    @ArchTest
    public static final ArchRule noAuthSubPackageCycles =
        slices().matching("org.yawlfoundation.yawl.authentication.(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true)
            .as("Authentication sub-packages must not form cycles.");
}
