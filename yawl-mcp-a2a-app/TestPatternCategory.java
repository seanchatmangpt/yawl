import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternCategory;

public class TestPatternCategory {
    public static void main(String[] args) {
        System.out.println("Testing PatternCategory fixes...\n");

        // Test 1: Count categories
        PatternCategory[] categories = PatternCategory.values();
        System.out.println("1. Category count: " + categories.length);
        if (categories.length == 17) {
            System.out.println("   ✓ PASS: Expected 17 categories");
        } else {
            System.out.println("   ✗ FAIL: Expected 17, got " + categories.length);
        }

        // Test 2: Check display names
        System.out.println("\n2. Display names:");
        for (PatternCategory cat : categories) {
            System.out.println("   " + cat.name() + ": " + cat.getDisplayName());
        }

        // Test 3: Test specific categorizations
        System.out.println("\n3. Pattern ID categorization tests:");
        testPatternId("WCP-10", PatternCategory.BRANCHING);
        testPatternId("WCP-11", PatternCategory.STRUCTURAL);
        testPatternId("WCP-19-CF", PatternCategory.CANCELLATION);
        testPatternId("WCP-20-CF", PatternCategory.CANCELLATION);
        testPatternId("WCP-22", PatternCategory.CANCELLATION);
        testPatternId("WCP-28", PatternCategory.ITERATION);
        testPatternId("WCP-31", PatternCategory.ITERATION);
        testPatternId("WCP-43", PatternCategory.EXTENDED);

        // Test 4: Check for duplicate display names
        System.out.println("\n4. Duplicate display name check:");
        boolean hasDuplicates = false;
        String[] displayNames = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            displayNames[i] = categories[i].getDisplayName();
            for (int j = 0; j < i; j++) {
                if (displayNames[i].equals(displayNames[j])) {
                    System.out.println("   ✗ FAIL: Duplicate display name '" + displayNames[i] +
                                     "' used by " + categories[i].name() + " and " + categories[j].name());
                    hasDuplicates = true;
                }
            }
        }
        if (!hasDuplicates) {
            System.out.println("   ✓ PASS: No duplicate display names found");
        }
    }

    private static void testPatternId(String patternId, PatternCategory expected) {
        PatternCategory actual = PatternCategory.fromPatternId(patternId);
        if (actual == expected) {
            System.out.println("   ✓ " + patternId + " → " + actual.name());
        } else {
            System.out.println("   ✗ " + patternId + " → " + actual.name() +
                             " (expected " + expected.name() + ")");
        }
    }
}