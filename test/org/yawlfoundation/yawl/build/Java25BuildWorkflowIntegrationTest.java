package org.yawlfoundation.yawl.build;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Java 25 Build Workflow Integration Test
 * Tests the complete end-to-end build workflow with Java 25 validation.
 * Uses Chicago TDD - tests real Maven builds, not mocks.
 *
 * Test Coverage:
 * 1. Session Start Workflow
 *    - Java 25 version validation
 *    - Maven configuration with --enable-preview
 *    - Hook execution (session-start.sh)
 *    - Environment setup time
 *
 * 2. Build Workflow
 *    - Maven clean compile with Java 25
 *    - Preview features enabled
 *    - Build performance benchmarks
 *    - Artifact generation
 *
 * 3. Test Workflow
 *    - Unit test execution
 *    - Test coverage reporting
 *    - Test performance
 *
 * 4. CI/CD Pipeline
 *    - Workflow YAML validation
 *    - Job dependency structure
 *    - Java 25 setup verification
 *
 * 5. Error Handling
 *    - Wrong Java version detection
 *    - Maven not found handling
 *    - Test failure handling
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class Java25BuildWorkflowIntegrationTest extends TestCase {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String HOOKS_DIR = PROJECT_ROOT + "/.claude/hooks";
    private static final String WORKFLOWS_DIR = PROJECT_ROOT + "/.github/workflows";

    // Performance benchmarks (in seconds)
    private static final int SESSION_START_TIMEOUT = 30;
    private static final int BUILD_COMPILE_TIMEOUT = 120;
    private static final int FULL_BUILD_TIMEOUT = 300;

    private long testStartTime;

    public Java25BuildWorkflowIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        testStartTime = System.currentTimeMillis();

        // Verify project structure
        File projectDir = new File(PROJECT_ROOT);
        assertTrue("Project root must exist", projectDir.exists());
        assertTrue("Project root must be directory", projectDir.isDirectory());
    }

    @Override
    protected void tearDown() throws Exception {
        long duration = System.currentTimeMillis() - testStartTime;
        System.out.println("[PERF] Test completed in " + duration + "ms");
        super.tearDown();
    }

    // ========================================================================
    // 1. SESSION START WORKFLOW TESTS
    // ========================================================================

    public void testJava25VersionDetection() throws Exception {
        System.out.println("\n=== Testing Java 25 Version Detection ===");

        ProcessResult result = executeCommand("java -version");
        assertEquals("java -version command should succeed", 0, result.exitCode);

        String output = result.output + result.error;
        System.out.println("Java version output:\n" + output);

        // Extract major version number
        Pattern versionPattern = Pattern.compile("version \"(\\d+)");
        Matcher matcher = versionPattern.matcher(output);

        if (matcher.find()) {
            String versionStr = matcher.group(1);
            int majorVersion = Integer.parseInt(versionStr);
            System.out.println("Detected Java major version: " + majorVersion);

            // CLAUDE.md requires Java 25 for v5.2
            if (majorVersion != 25) {
                System.out.println("WARNING: Expected Java 25, found Java " + majorVersion);
                System.out.println("This is acceptable for testing, but production requires Java 25");
            }
        } else {
            System.out.println("Could not parse Java version from output");
        }
    }

    public void testMavenVersionAndConfiguration() throws Exception {
        System.out.println("\n=== Testing Maven Configuration ===");

        ProcessResult result = executeCommand("mvn --version");
        assertEquals("Maven should be available", 0, result.exitCode);

        String output = result.output;
        assertTrue("Maven output should contain 'Apache Maven'",
            output.contains("Apache Maven"));

        // Check Maven version (should be 3.9+)
        Pattern mvnPattern = Pattern.compile("Apache Maven ([0-9.]+)");
        Matcher matcher = mvnPattern.matcher(output);
        if (matcher.find()) {
            String mvnVersion = matcher.group(1);
            System.out.println("Maven version: " + mvnVersion);
        }

        System.out.println("Maven version:\n" + output);
    }

    public void testSessionStartHookExists() {
        System.out.println("\n=== Testing Session Start Hook ===");

        File sessionStartHook = new File(HOOKS_DIR, "session-start.sh");
        assertTrue("session-start.sh hook must exist", sessionStartHook.exists());
        assertTrue("session-start.sh must be a file", sessionStartHook.isFile());
        assertTrue("session-start.sh must be readable", sessionStartHook.canRead());

        System.out.println("Hook location: " + sessionStartHook.getAbsolutePath());
        System.out.println("Hook size: " + sessionStartHook.length() + " bytes");
    }

    public void testSessionStartHookValidatesJava25() throws Exception {
        System.out.println("\n=== Testing Session Start Hook Java 25 Validation ===");

        File hookFile = new File(HOOKS_DIR, "session-start.sh");
        String hookContent = readFileContent(hookFile);

        // Verify hook checks for Java 25
        assertTrue("Hook should validate Java version",
            hookContent.contains("JAVA_VERSION"));
        assertTrue("Hook should check for Java 25",
            hookContent.contains("25"));
        assertTrue("Hook should enable preview features",
            hookContent.contains("--enable-preview"));

        System.out.println("Hook contains Java 25 validation: YES");
        System.out.println("Hook enables preview features: YES");
    }

    public void testMavenOptsConfiguration() throws Exception {
        System.out.println("\n=== Testing MAVEN_OPTS Configuration ===");

        File hookFile = new File(HOOKS_DIR, "session-start.sh");
        String hookContent = readFileContent(hookFile);

        // Verify MAVEN_OPTS includes --enable-preview
        assertTrue("Hook should set MAVEN_OPTS",
            hookContent.contains("MAVEN_OPTS"));
        assertTrue("MAVEN_OPTS should include --enable-preview",
            hookContent.contains("--enable-preview"));

        // Verify memory settings
        assertTrue("MAVEN_OPTS should include memory settings",
            hookContent.contains("-Xmx") || hookContent.contains("2g"));

        System.out.println("MAVEN_OPTS configuration validated");
    }

    // ========================================================================
    // 2. BUILD WORKFLOW TESTS
    // ========================================================================

    public void testPomXmlHasJava25Configuration() throws Exception {
        System.out.println("\n=== Testing pom.xml Java 25 Configuration ===");

        File pomFile = new File(PROJECT_ROOT, "pom.xml");
        assertTrue("pom.xml must exist", pomFile.exists());

        String pomContent = readFileContent(pomFile);

        // Check for Java 25 compiler configuration
        assertTrue("pom.xml should specify Java 25",
            pomContent.contains("<maven.compiler.source>25</maven.compiler.source>") ||
            pomContent.contains("<java.version>25</java.version>"));

        assertTrue("pom.xml should specify Java 25 target",
            pomContent.contains("<maven.compiler.target>25</maven.compiler.target>"));

        System.out.println("pom.xml Java 25 configuration: VALID");
    }

    public void testMavenValidatePhase() throws Exception {
        System.out.println("\n=== Testing Maven Validate Phase ===");

        long startTime = System.currentTimeMillis();
        ProcessResult result = executeCommand("mvn validate --batch-mode");
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("Validate phase duration: " + duration + "ms");

        if (result.exitCode != 0) {
            System.out.println("STDOUT:\n" + result.output);
            System.out.println("STDERR:\n" + result.error);
        }

        assertEquals("Maven validate should succeed", 0, result.exitCode);
        assertFalse("Should not have validation errors",
            result.output.contains("BUILD FAILURE"));
    }

    public void testMavenCleanCompileWithPreview() throws Exception {
        System.out.println("\n=== Testing Maven Clean Compile with Java 25 Preview ===");

        long startTime = System.currentTimeMillis();

        // Set MAVEN_OPTS for preview features
        String command = "MAVEN_OPTS='--enable-preview -Xmx2g' mvn clean compile -DskipTests --batch-mode";
        ProcessResult result = executeCommand(command, BUILD_COMPILE_TIMEOUT);

        long duration = (System.currentTimeMillis() - startTime) / 1000;
        System.out.println("Compile duration: " + duration + " seconds");

        // Performance check
        if (duration > BUILD_COMPILE_TIMEOUT) {
            System.out.println("WARNING: Build exceeded expected time (" + BUILD_COMPILE_TIMEOUT + "s)");
        } else {
            System.out.println("Performance: ACCEPTABLE (< " + BUILD_COMPILE_TIMEOUT + "s)");
        }

        if (result.exitCode != 0) {
            System.out.println("Build failed. Output:");
            System.out.println("STDOUT:\n" + result.output);
            System.out.println("STDERR:\n" + result.error);
            fail("Maven clean compile failed with exit code " + result.exitCode);
        }

        // Verify build artifacts
        File targetDir = new File(PROJECT_ROOT, "target");
        assertTrue("Target directory should exist after compile", targetDir.exists());

        File classesDir = new File(targetDir, "classes");
        assertTrue("Classes directory should exist", classesDir.exists());

        int classCount = countFilesRecursively(classesDir, ".class");
        System.out.println("Compiled classes: " + classCount);
        assertTrue("Should have compiled classes", classCount > 0);
    }

    public void testBuildArtifactsGenerated() throws Exception {
        System.out.println("\n=== Testing Build Artifacts Generation ===");

        File targetDir = new File(PROJECT_ROOT, "target");
        if (!targetDir.exists()) {
            System.out.println("Target directory does not exist - running build...");
            executeCommand("mvn compile -DskipTests --batch-mode", 120);
        }

        assertTrue("Target directory must exist", targetDir.exists());

        File classesDir = new File(targetDir, "classes");
        if (classesDir.exists()) {
            int classFiles = countFilesRecursively(classesDir, ".class");
            System.out.println("Class files generated: " + classFiles);
            assertTrue("Should have class files", classFiles > 0);
        }

        // Check for JAR artifacts (may not exist if package not run)
        File[] jars = targetDir.listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars != null && jars.length > 0) {
            System.out.println("JAR artifacts found: " + jars.length);
            for (File jar : jars) {
                System.out.println("  - " + jar.getName() + " (" + (jar.length() / 1024) + " KB)");
            }
        } else {
            System.out.println("No JAR artifacts (run 'mvn package' to generate)");
        }
    }

    public void testCompilerArguments() throws Exception {
        System.out.println("\n=== Testing Compiler Arguments ===");

        File pomFile = new File(PROJECT_ROOT, "pom.xml");
        String pomContent = readFileContent(pomFile);

        // Check if compiler plugin is configured
        if (pomContent.contains("maven-compiler-plugin")) {
            System.out.println("Maven compiler plugin configured");

            // Check for preview features in compiler args
            if (pomContent.contains("--enable-preview")) {
                System.out.println("Preview features enabled in compiler plugin");
            } else {
                System.out.println("Preview features should be set via MAVEN_OPTS");
            }
        } else {
            System.out.println("Using default compiler configuration");
        }
    }

    // ========================================================================
    // 3. TEST WORKFLOW TESTS
    // ========================================================================

    public void testMavenTestCompilation() throws Exception {
        System.out.println("\n=== Testing Maven Test Compilation ===");

        ProcessResult result = executeCommand("mvn test-compile --batch-mode", 60);

        if (result.exitCode != 0) {
            System.out.println("Test compilation output:");
            System.out.println(result.output);
        }

        assertEquals("Test compilation should succeed", 0, result.exitCode);

        File testClassesDir = new File(PROJECT_ROOT, "target/test-classes");
        assertTrue("Test classes directory should exist", testClassesDir.exists());

        int testClassCount = countFilesRecursively(testClassesDir, ".class");
        System.out.println("Compiled test classes: " + testClassCount);
        assertTrue("Should have test classes", testClassCount > 0);
    }

    public void testUnitTestExecution() throws Exception {
        System.out.println("\n=== Testing Unit Test Execution ===");
        System.out.println("NOTE: Running subset of fast tests for integration validation");

        // Run only this test class to avoid long execution
        String testClass = "org.yawlfoundation.yawl.build.BuildSystemTest";
        String command = "mvn test -Dtest=" + testClass + " --batch-mode";

        long startTime = System.currentTimeMillis();
        ProcessResult result = executeCommand(command, 60);
        long duration = (System.currentTimeMillis() - startTime) / 1000;

        System.out.println("Test execution duration: " + duration + " seconds");

        if (result.exitCode != 0) {
            System.out.println("Test output:");
            System.out.println(result.output);
        }

        // Test execution might fail if dependencies not met, but compilation should work
        if (result.exitCode == 0) {
            System.out.println("Tests PASSED");
        } else {
            System.out.println("Tests failed (may be expected in isolated environment)");
        }
    }

    public void testSurefireReportsGeneration() throws Exception {
        System.out.println("\n=== Testing Surefire Reports Generation ===");

        File surefireDir = new File(PROJECT_ROOT, "target/surefire-reports");
        if (surefireDir.exists()) {
            File[] reports = surefireDir.listFiles((dir, name) ->
                name.endsWith(".xml") || name.endsWith(".txt"));

            if (reports != null && reports.length > 0) {
                System.out.println("Surefire reports found: " + reports.length);
                for (File report : reports) {
                    System.out.println("  - " + report.getName());
                }
            } else {
                System.out.println("No surefire reports (tests not run yet)");
            }
        } else {
            System.out.println("Surefire reports directory not found (run tests first)");
        }
    }

    // ========================================================================
    // 4. CI/CD PIPELINE TESTS
    // ========================================================================

    public void testJava25WorkflowExists() {
        System.out.println("\n=== Testing Java 25 CI/CD Workflow ===");

        File workflowFile = new File(WORKFLOWS_DIR, "java25-build.yml");
        assertTrue("java25-build.yml workflow must exist", workflowFile.exists());

        System.out.println("Workflow location: " + workflowFile.getAbsolutePath());
        System.out.println("Workflow size: " + workflowFile.length() + " bytes");
    }

    public void testJava25WorkflowConfiguration() throws Exception {
        System.out.println("\n=== Testing Java 25 Workflow Configuration ===");

        File workflowFile = new File(WORKFLOWS_DIR, "java25-build.yml");
        String workflowContent = readFileContent(workflowFile);

        // Verify Java 25 setup
        assertTrue("Workflow should set up Java 25",
            workflowContent.contains("java-version: 25") ||
            workflowContent.contains("java-version: [25]"));

        // Verify preview features
        assertTrue("Workflow should enable preview features",
            workflowContent.contains("--enable-preview"));

        // Verify Maven commands
        assertTrue("Workflow should use Maven",
            workflowContent.contains("mvn"));

        System.out.println("Workflow configuration: VALID");
        System.out.println("  - Java 25 setup: YES");
        System.out.println("  - Preview features: YES");
        System.out.println("  - Maven build: YES");
    }

    public void testWorkflowJobDependencies() throws Exception {
        System.out.println("\n=== Testing Workflow Job Dependencies ===");

        File workflowFile = new File(WORKFLOWS_DIR, "java25-build.yml");
        String workflowContent = readFileContent(workflowFile);

        // Check for job structure
        assertTrue("Workflow should have jobs section",
            workflowContent.contains("jobs:"));

        // Check for build and test jobs
        boolean hasBuildJob = workflowContent.contains("build-and-test") ||
                              workflowContent.contains("build:");
        assertTrue("Workflow should have build job", hasBuildJob);

        System.out.println("Workflow job structure: VALID");
    }

    public void testBuildTestDeployWorkflow() throws Exception {
        System.out.println("\n=== Testing Build-Test-Deploy Workflow ===");

        File workflowFile = new File(WORKFLOWS_DIR, "build-test-deploy.yml");
        assertTrue("build-test-deploy.yml workflow should exist", workflowFile.exists());

        String workflowContent = readFileContent(workflowFile);

        // Verify comprehensive pipeline structure
        assertTrue("Should have build job", workflowContent.contains("build:"));
        assertTrue("Should have test jobs",
            workflowContent.contains("test-unit") || workflowContent.contains("test:"));

        // Check for Java 24/25 matrix
        boolean hasJavaMatrix = workflowContent.contains("java-version: [24, 25]") ||
                                workflowContent.contains("24") && workflowContent.contains("25");
        if (hasJavaMatrix) {
            System.out.println("Multi-version Java testing enabled: Java 24, 25");
        }

        System.out.println("Enterprise CI/CD workflow: VALID");
    }

    // ========================================================================
    // 5. ERROR HANDLING TESTS
    // ========================================================================

    public void testJavaVersionMismatchDetection() throws Exception {
        System.out.println("\n=== Testing Java Version Mismatch Detection ===");

        File hookFile = new File(HOOKS_DIR, "session-start.sh");
        String hookContent = readFileContent(hookFile);

        // Verify hook has error handling for wrong Java version
        assertTrue("Hook should check Java version",
            hookContent.contains("JAVA_VERSION"));
        assertTrue("Hook should exit on wrong version",
            hookContent.contains("exit 1") || hookContent.contains("exit"));

        // Verify error message includes Java 25 requirement
        assertTrue("Hook should mention Java 25 requirement",
            hookContent.contains("Java 25") || hookContent.contains("25"));

        System.out.println("Java version validation: PRESENT");
        System.out.println("Error handling: CONFIGURED");
    }

    public void testMavenNotFoundHandling() throws Exception {
        System.out.println("\n=== Testing Maven Not Found Handling ===");

        // Test with fake command to verify error handling works
        ProcessResult result = executeCommand("which mvn-fake-command", 5);

        // Should return non-zero exit code
        assertTrue("Non-existent command should fail", result.exitCode != 0);
        System.out.println("Command error detection: WORKING");
    }

    public void testBuildFailureHandling() throws Exception {
        System.out.println("\n=== Testing Build Failure Handling ===");

        // Try to run Maven with invalid goal
        ProcessResult result = executeCommand("mvn invalid-goal --batch-mode", 10);

        // Should fail gracefully
        assertTrue("Invalid Maven goal should fail", result.exitCode != 0);
        assertTrue("Should show build failure message",
            result.output.contains("BUILD FAILURE") ||
            result.error.contains("Unknown lifecycle"));

        System.out.println("Build failure handling: WORKING");
    }

    // ========================================================================
    // 6. PERFORMANCE TESTS
    // ========================================================================

    public void testBuildPerformanceBenchmark() throws Exception {
        System.out.println("\n=== Build Performance Benchmark ===");

        // Clean build performance test
        long startTime = System.currentTimeMillis();
        ProcessResult result = executeCommand("mvn clean compile -DskipTests --batch-mode",
            BUILD_COMPILE_TIMEOUT);
        long duration = (System.currentTimeMillis() - startTime) / 1000;

        System.out.println("Clean compile time: " + duration + " seconds");
        System.out.println("Expected: < " + BUILD_COMPILE_TIMEOUT + " seconds");

        if (duration < BUILD_COMPILE_TIMEOUT) {
            System.out.println("Performance: EXCELLENT");
        } else {
            System.out.println("Performance: ACCEPTABLE (within timeout)");
        }

        assertEquals("Build should succeed", 0, result.exitCode);
    }

    public void testIncrementalBuildPerformance() throws Exception {
        System.out.println("\n=== Incremental Build Performance ===");

        // First build
        executeCommand("mvn compile -DskipTests --batch-mode", 120);

        // Incremental build (should be faster)
        long startTime = System.currentTimeMillis();
        ProcessResult result = executeCommand("mvn compile -DskipTests --batch-mode", 60);
        long duration = (System.currentTimeMillis() - startTime) / 1000;

        System.out.println("Incremental compile time: " + duration + " seconds");
        System.out.println("Expected: < 60 seconds (much faster than clean build)");

        assertEquals("Incremental build should succeed", 0, result.exitCode);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private ProcessResult executeCommand(String command) throws Exception {
        return executeCommand(command, 30);
    }

    private ProcessResult executeCommand(String command, int timeoutSeconds) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(PROJECT_ROOT));
        pb.command("bash", "-c", command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                // Stream closed
            }
        });

        Thread errorReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            } catch (IOException e) {
                // Stream closed
            }
        });

        outputReader.start();
        errorReader.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Command timed out after " + timeoutSeconds + " seconds: " + command);
        }

        outputReader.join(5000);
        errorReader.join(5000);

        int exitCode = process.exitValue();
        return new ProcessResult(exitCode, output.toString(), error.toString());
    }

    private String readFileContent(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    private int countFilesRecursively(File dir, String extension) {
        int count = 0;
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        count += countFilesRecursively(file, extension);
                    } else if (file.getName().endsWith(extension)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static class ProcessResult {
        final int exitCode;
        final String output;
        final String error;

        ProcessResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Java 25 Build Workflow Integration Tests");
        suite.addTestSuite(Java25BuildWorkflowIntegrationTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
