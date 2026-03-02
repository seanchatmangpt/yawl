import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternCategory;
import java.util.List;

public class SimpleTest {
    public static void main(String[] args) {
        System.out.println("Testing PatternDemoConfig fixes...");

        // Test 1: Compact constructor with empty path
        System.out.println("\n=== Test 1: Empty path should default ===");
        PatternDemoConfig config1 = new PatternDemoConfig(
            PatternDemoConfig.OutputFormat.JSON, "", 0, false, false, false, false, false, false, null, null
        );
        System.out.println("Output path: " + config1.outputPath());
        System.out.println("Expected: report");
        System.out.println("Actual: " + (config1.outputPath().equals("report") ? "✓ PASS" : "✗ FAIL"));

        // Test 2: Compact constructor with nulls
        System.out.println("\n=== Test 2: Null lists should become default ===");
        PatternDemoConfig config2 = new PatternDemoConfig(
            null, "test", 300, true, true, true, true, true, false, List.of(), List.of()
        );
        System.out.println("Pattern IDs size: " + config2.patternIds().size());
        System.out.println("Categories size: " + config2.categories().size());
        System.out.println("Expected: 1, 1");
        System.out.println("Actual: " + config2.patternIds().size() + ", " + config2.categories().size());
        System.out.println("Result: " + (config2.patternIds().size() == 1 && config2.categories().size() == 1 ? "✓ PASS" : "✗ FAIL"));

        // Test 3: shouldExecutePattern with both filters
        System.out.println("\n=== Test 3: Both filters require match ===");
        PatternDemoConfig config3 = PatternDemoConfig.builder()
            .addPatternId("WCP-1")
            .addCategory(PatternCategory.BASIC)
            .build();

        System.out.println("WCP-1 with BASIC: " + config3.shouldExecutePattern("WCP-1", PatternCategory.BASIC));
        System.out.println("WCP-1 with AI_ML: " + config3.shouldExecutePattern("WCP-1", PatternCategory.AI_ML));
        System.out.println("WCP-2 with BASIC: " + config3.shouldExecutePattern("WCP-2", PatternCategory.BASIC));
        System.out.println("Expected: true, false, false");
        System.out.println("Result: " +
            (config3.shouldExecutePattern("WCP-1", PatternCategory.BASIC) == true &&
             config3.shouldExecutePattern("WCP-1", PatternCategory.AI_ML) == false &&
             config3.shouldExecutePattern("WCP-2", PatternCategory.BASIC) == false ? "✓ PASS" : "✗ FAIL"));

        // Test 4: hasPatternFilter and hasCategoryFilter with defaults
        System.out.println("\n=== Test 4: Default config should not have filters ===");
        PatternDemoConfig config4 = PatternDemoConfig.defaults();
        System.out.println("Has pattern filter: " + config4.hasPatternFilter());
        System.out.println("Has category filter: " + config4.hasCategoryFilter());
        System.out.println("Expected: false, false");
        System.out.println("Result: " + (config4.hasPatternFilter() == false && config4.hasCategoryFilter() == false ? "✓ PASS" : "✗ FAIL"));

        // Test 5: shouldExecutePattern with no filters
        System.out.println("\n=== Test 5: No filters should execute all ===");
        System.out.println("WCP-1 with BASIC: " + config4.shouldExecutePattern("WCP-1", PatternCategory.BASIC));
        System.out.println("WCP-100 with AI_ML: " + config4.shouldExecutePattern("WCP-100", PatternCategory.AI_ML));
        System.out.println("Expected: true, true");
        System.out.println("Result: " +
            (config4.shouldExecutePattern("WCP-1", PatternCategory.BASIC) == true &&
             config4.shouldExecutePattern("WCP-100", PatternCategory.AI_ML) == true ? "✓ PASS" : "✗ FAIL"));

        System.out.println("\n=== All tests completed ===");
    }
}