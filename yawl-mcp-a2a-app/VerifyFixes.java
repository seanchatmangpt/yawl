import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternCategory;
import java.util.List;

public class VerifyFixes {
    public static void main(String[] args) {
        System.out.println("=== Verifying PatternDemoConfig Fixes ===\n");

        // Test 1: Compact constructor with empty path should default
        System.out.println("Test 1: Empty path should default to 'report'");
        PatternDemoConfig config1 = new PatternDemoConfig(
            PatternDemoConfig.OutputFormat.JSON, "", 0, false, false, false, false, false, false, null, null
        );
        boolean test1 = config1.outputPath().equals("report");
        System.out.println("  Expected: report");
        System.out.println("  Actual: " + config1.outputPath());
        System.out.println("  Result: " + (test1 ? "✓ PASS" : "✗ FAIL") + "\n");

        // Test 2: Compact constructor with null/empty lists should have default values
        System.out.println("Test 2: Empty lists should become default");
        PatternDemoConfig config2 = new PatternDemoConfig(
            null, "test", 300, true, true, true, true, true, false, List.of(), List.of()
        );
        boolean test2 = config2.patternIds().size() == 1 && config2.categories().size() == 1;
        System.out.println("  Pattern IDs size: " + config2.patternIds().size() + " (expected: 1)");
        System.out.println("  Categories size: " + config2.categories().size() + " (expected: 1)");
        System.out.println("  Result: " + (test2 ? "✓ PASS" : "✗ FAIL") + "\n");

        // Test 3: shouldExecutePattern with both filters set
        System.out.println("Test 3: Both filters require exact match");
        PatternDemoConfig config3 = PatternDemoConfig.builder()
            .addPatternId("WCP-1")
            .addCategory(PatternCategory.BASIC)
            .build();

        boolean test3a = config3.shouldExecutePattern("WCP-1", PatternCategory.BASIC);  // should be true
        boolean test3b = config3.shouldExecutePattern("WCP-1", PatternCategory.AI_ML);    // should be false
        boolean test3c = config3.shouldExecutePattern("WCP-2", PatternCategory.BASIC);   // should be false
        boolean test3 = test3a && !test3b && !test3c;

        System.out.println("  WCP-1 with BASIC: " + test3a + " (expected: true)");
        System.out.println("  WCP-1 with AI_ML: " + test3b + " (expected: false)");
        System.out.println("  WCP-2 with BASIC: " + test3c + " (expected: false)");
        System.out.println("  Result: " + (test3 ? "✓ PASS" : "✗ FAIL") + "\n");

        // Test 4: hasFilter methods with defaults
        System.out.println("Test 4: Default config should not show filter");
        PatternDemoConfig config4 = PatternDemoConfig.defaults();
        boolean test4 = !config4.hasPatternFilter() && !config4.hasCategoryFilter();
        System.out.println("  Has pattern filter: " + config4.hasPatternFilter() + " (expected: false)");
        System.out.println("  Has category filter: " + config4.hasCategoryFilter() + " (expected: false)");
        System.out.println("  Result: " + (test4 ? "✓ PASS" : "✗ FAIL") + "\n");

        // Test 5: shouldExecutePattern with no filters
        System.out.println("Test 5: No filters should execute all patterns");
        boolean test5a = config4.shouldExecutePattern("WCP-1", PatternCategory.BASIC);
        boolean test5b = config4.shouldExecutePattern("WCP-100", PatternCategory.AI_ML);
        boolean test5 = test5a && test5b;

        System.out.println("  WCP-1 with BASIC: " + test5a + " (expected: true)");
        System.out.println("  WCP-100 with AI_ML: " + test5b + " (expected: true)");
        System.out.println("  Result: " + (test5 ? "✓ PASS" : "✗ FAIL") + "\n");

        // Summary
        boolean allTests = test1 && test2 && test3 && test4 && test5;
        System.out.println("=== SUMMARY ===");
        System.out.println("All tests: " + (allTests ? "✓ PASSED" : "✗ FAILED"));
    }
}