package org.yawlfoundation.yawl.util;

/**
 * 80/20 Innovation #2: One-Class Test Runner
 *
 * Instantly verify environment is working without Ant.
 * Usage: java org.yawlfoundation.yawl.util.QuickTest
 *
 * @author YAWL Team
 * @version 5.2
 */
public class QuickTest {

    public static void main(String[] args) {
        System.out.println("🧪 YAWL Quick Test\n");

        int passed = 0;
        int failed = 0;

        // Test 1: Environment Detection
        try {
            boolean isRemote = EnvironmentDetector.isClaudeCodeRemote();
            System.out.println("✅ Test 1: Environment detected as " +
                (isRemote ? "REMOTE (Claude Code Web)" : "LOCAL (Docker)"));
            passed++;
        } catch (Exception e) {
            System.out.println("❌ Test 1: Environment detection failed");
            failed++;
        }

        // Test 2: Database Configuration
        try {
            String dbType = EnvironmentDetector.getRecommendedDatabaseType();
            System.out.println("✅ Test 2: Database type = " + dbType);
            passed++;
        } catch (Exception e) {
            System.out.println("❌ Test 2: Database config failed");
            failed++;
        }

        // Test 3: Java Version
        try {
            String version = System.getProperty("java.version");
            System.out.println("✅ Test 3: Java version = " + version);
            passed++;
        } catch (Exception e) {
            System.out.println("❌ Test 3: Java version check failed");
            failed++;
        }

        // Test 4: Classpath
        try {
            String classpath = System.getProperty("java.class.path");
            boolean hasClasses = classpath.contains("classes");
            if (hasClasses) {
                System.out.println("✅ Test 4: Classpath configured correctly");
                passed++;
            } else {
                System.out.println("❌ Test 4: Classpath missing 'classes'");
                failed++;
            }
        } catch (Exception e) {
            System.out.println("❌ Test 4: Classpath check failed");
            failed++;
        }

        // Test 5: Session ID (remote only)
        try {
            String sessionId = EnvironmentDetector.getRemoteSessionId();
            if (sessionId != null) {
                System.out.println("✅ Test 5: Session ID = " + sessionId.substring(0, 20) + "...");
            } else {
                System.out.println("✅ Test 5: Local session (no session ID)");
            }
            passed++;
        } catch (Exception e) {
            System.out.println("❌ Test 5: Session check failed");
            failed++;
        }

        // Summary
        System.out.println("\n📊 Results: " + passed + " passed, " + failed + " failed");

        if (failed == 0) {
            System.out.println("🎉 All tests passed! Environment is ready.");
            System.exit(0);
        } else {
            System.out.println("⚠️  Some tests failed. Check configuration.");
            System.exit(1);
        }
    }
}
