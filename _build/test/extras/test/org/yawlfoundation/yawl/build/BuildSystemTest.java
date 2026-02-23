package org.yawlfoundation.yawl.build;

import org.junit.jupiter.api.Tag;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Build System Integration Tests
 * Tests Maven and Ant builds using real build tools (Chicago TDD)
 *
 * Coverage:
 * - Maven clean compile success
 * - Maven test execution
 * - Maven package (JAR creation)
 * - Maven dependency caching
 * - Ant build compatibility
 * - Build output validation
 */
@Tag("slow")
public class BuildSystemTest extends TestCase {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String TARGET_DIR = PROJECT_ROOT + "/target";
    private static final String BUILD_DIR = PROJECT_ROOT + "/build";
    private static final String MAVEN_CACHE_DIR = System.getProperty("user.home") + "/.m2/repository";

    public BuildSystemTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Verify project structure exists
        File projectDir = new File(PROJECT_ROOT);
        assertTrue("Project root should exist", projectDir.exists());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testProjectStructureExists() {
        File projectRoot = new File(PROJECT_ROOT);
        assertTrue("Project root exists", projectRoot.exists());
        assertTrue("Project root is directory", projectRoot.isDirectory());

        File pomFile = new File(PROJECT_ROOT, "pom.xml");
        assertTrue("pom.xml exists", pomFile.exists());

        File srcDir = new File(PROJECT_ROOT, "src");
        assertTrue("src directory exists", srcDir.exists());

        File testDir = new File(PROJECT_ROOT, "test");
        assertTrue("test directory exists", testDir.exists());
    }

    public void testMavenIsAvailable() throws Exception {
        ProcessResult result = executeCommand("mvn --version");
        assertEquals("Maven should be available", 0, result.exitCode);
        assertTrue("Maven version output should contain 'Apache Maven'",
            result.output.contains("Apache Maven"));
    }

    public void testMavenCleanCompileSuccess() throws Exception {
        ProcessResult result = executeCommand("mvn clean compile -DskipTests");
        assertEquals("Maven clean compile should succeed", 0, result.exitCode);

        // Verify target directory was created
        File targetDir = new File(TARGET_DIR);
        assertTrue("Target directory should exist after compile", targetDir.exists());

        // Verify classes were compiled
        File classesDir = new File(TARGET_DIR, "classes");
        assertTrue("Classes directory should exist", classesDir.exists());
        assertTrue("Classes should be generated",
            countFilesRecursively(classesDir, ".class") > 0);
    }

    public void testMavenPackageCreatesJar() throws Exception {
        ProcessResult result = executeCommand("mvn package -DskipTests");
        assertEquals("Maven package should succeed", 0, result.exitCode);

        // Verify JAR was created
        File targetDir = new File(TARGET_DIR);
        File[] jars = targetDir.listFiles((dir, name) -> name.endsWith(".jar"));
        assertNotNull("JAR files should exist", jars);
        assertTrue("At least one JAR should be created", jars.length > 0);

        // Verify JAR is not empty
        for (File jar : jars) {
            assertTrue("JAR file should not be empty: " + jar.getName(),
                jar.length() > 0);
        }
    }

    public void testMavenTestCompilation() throws Exception {
        ProcessResult result = executeCommand("mvn test-compile");
        assertEquals("Maven test-compile should succeed", 0, result.exitCode);

        File testClassesDir = new File(TARGET_DIR, "test-classes");
        assertTrue("Test classes directory should exist", testClassesDir.exists());
        assertTrue("Test classes should be compiled",
            countFilesRecursively(testClassesDir, ".class") > 0);
    }

    public void testAntBuildFileExists() {
        File buildXml = new File(BUILD_DIR, "build.xml");
        if (!buildXml.exists()) {
            // Try alternate location
            buildXml = new File(PROJECT_ROOT, "build.xml");
        }
        assertTrue("Ant build.xml should exist", buildXml.exists());
    }

    public void testBuildOutputDirectoryCreated() throws Exception {
        // After Maven build, verify standard output directories exist
        executeCommand("mvn compile -DskipTests");

        File targetDir = new File(TARGET_DIR);
        assertTrue("Target directory exists", targetDir.exists());

        File classesDir = new File(targetDir, "classes");
        assertTrue("Classes directory exists", classesDir.exists());
    }

    public void testMavenDependenciesResolved() throws Exception {
        ProcessResult result = executeCommand("mvn dependency:resolve");
        assertEquals("Maven dependencies should resolve", 0, result.exitCode);
        assertFalse("Should not have dependency errors",
            result.output.contains("Could not resolve dependencies"));
    }

    public void testMavenValidatePhase() throws Exception {
        ProcessResult result = executeCommand("mvn validate");
        assertEquals("Maven validate should succeed", 0, result.exitCode);
    }

    public void testSourceFilesCompile() throws Exception {
        File srcDir = new File(PROJECT_ROOT, "src");
        assertTrue("Source directory exists", srcDir.exists());

        int javaFileCount = countFilesRecursively(srcDir, ".java");
        assertTrue("Should have Java source files", javaFileCount > 0);

        ProcessResult result = executeCommand("mvn compile -DskipTests");
        assertEquals("Java source files should compile", 0, result.exitCode);
    }

    public void testTestSourceFilesCompile() throws Exception {
        File testDir = new File(PROJECT_ROOT, "test");
        assertTrue("Test directory exists", testDir.exists());

        int testFileCount = countFilesRecursively(testDir, ".java");
        assertTrue("Should have test files", testFileCount > 0);

        ProcessResult result = executeCommand("mvn test-compile");
        assertEquals("Test source files should compile", 0, result.exitCode);
    }

    public void testMavenCacheDirectoryExists() {
        File cacheDir = new File(MAVEN_CACHE_DIR);
        assertTrue("Maven cache directory should exist: " + MAVEN_CACHE_DIR,
            cacheDir.exists());
        assertTrue("Maven cache should be a directory",
            cacheDir.isDirectory());
    }

    public void testMavenCacheIsWritable() {
        File cacheDir = new File(MAVEN_CACHE_DIR);
        if (cacheDir.exists()) {
            assertTrue("Maven cache directory should be writable",
                cacheDir.canWrite());
        } else {
            File m2Dir = new File(System.getProperty("user.home"), ".m2");
            assertTrue("Parent .m2 directory should be writable if cache doesn't exist",
                m2Dir.canWrite() || m2Dir.mkdirs());
        }
    }

    public void testMavenCachePersistsDependencies() throws Exception {
        File cacheDir = new File(MAVEN_CACHE_DIR);
        assertTrue("Maven cache directory should exist", cacheDir.exists());

        ProcessResult result = executeCommand("mvn dependency:resolve");
        assertEquals("Maven dependency resolution should succeed", 0, result.exitCode);

        int cachedJarCount = countFilesRecursively(cacheDir, ".jar");
        assertTrue("Maven cache should contain JAR files after dependency resolution",
            cachedJarCount > 0);
    }

    public void testMavenCacheHasExpectedStructure() {
        File cacheDir = new File(MAVEN_CACHE_DIR);
        if (cacheDir.exists()) {
            File[] topLevelDirs = cacheDir.listFiles(File::isDirectory);
            assertNotNull("Cache should have organization directories", topLevelDirs);

            boolean hasCommonOrgs = false;
            if (topLevelDirs != null) {
                for (File dir : topLevelDirs) {
                    String name = dir.getName();
                    if (name.equals("org") || name.equals("com") || name.equals("io")) {
                        hasCommonOrgs = true;
                        break;
                    }
                }
            }
            assertTrue("Cache should contain common organization directories (org/com/io)",
                hasCommonOrgs);
        }
    }

    public void testMavenCachePerformanceImprovement() throws Exception {
        long firstBuildTime = measureDependencyResolutionTime();
        assertTrue("First build should complete within reasonable time (60s)",
            firstBuildTime < 60000);

        long secondBuildTime = measureDependencyResolutionTime();
        assertTrue("Second build should be faster due to caching",
            secondBuildTime <= firstBuildTime);

        assertTrue("Cached build should complete quickly (30s)",
            secondBuildTime < 30000);
    }

    // Helper methods

    private long measureDependencyResolutionTime() throws Exception {
        long startTime = System.currentTimeMillis();
        ProcessResult result = executeCommand("mvn dependency:resolve -q");
        long endTime = System.currentTimeMillis();

        assertEquals("Maven dependency resolution should succeed", 0, result.exitCode);
        return endTime - startTime;
    }

    private ProcessResult executeCommand(String command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(new File(PROJECT_ROOT));

        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("bash", "-c", command);
        }

        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output.toString());
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

        ProcessResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Build System Tests");
        suite.addTestSuite(BuildSystemTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
