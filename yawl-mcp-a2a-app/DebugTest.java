import java.util.Arrays;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig;

public class DebugTest {
    public static void main(String[] args) {
        // Test empty args
        String[] emptyArgs = {};
        PatternDemoConfig config = PatternDemoConfig.fromCommandLine(emptyArgs);

        System.out.println("patternIds size: " + config.patternIds().size());
        System.out.println("patternIds isEmpty: " + config.patternIds().isEmpty());
        System.out.println("patternIds content: " + config.patternIds());

        // Test Builder directly
        PatternDemoConfig.Builder builder = PatternDemoConfig.builder();
        System.out.println("Builder patternIds size: " + builder.build().patternIds().size());
        System.out.println("Builder patternIds isEmpty: " + builder.build().patternIds().isEmpty());
    }
}