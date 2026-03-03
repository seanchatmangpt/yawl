import java.lang.reflect.Field;

public class CheckDisplayNames {
    public static void main(String[] args) {
        PatternCategory[] values = PatternCategory.values();
        java.util.Set<String> displayNames = new java.util.HashSet<>();

        System.out.println("Checking display names for duplicates:");
        for (PatternCategory category : values) {
            String displayName = category.getDisplayName();
            if (displayNames.contains(displayName)) {
                System.out.println("DUPLICATE: " + displayName + " used by " + category.name());
            } else {
                displayNames.add(displayName);
                System.out.println(category.name() + ": " + displayName);
            }
        }

        System.out.println("\nTotal categories: " + values.length);
        System.out.println("Unique display names: " + displayNames.size());

        if (values.length != displayNames.size()) {
            System.out.println("ERROR: Duplicate display names found!");
        } else {
            System.out.println("OK: All display names are unique.");
        }
    }
}