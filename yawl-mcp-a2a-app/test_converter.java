import org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter;

public class test_converter {
    public static void main(String[] args) {
        try {
            ExtendedYamlConverter converter = new ExtendedYamlConverter();
            
            // Test basic YAML conversion
            String yaml = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                
                tasks:
                  - id: TaskA
                    flows: [TaskB]
                    split: xor
                    join: xor
                    
                  - id: TaskB
                    flows: [end]
                    split: xor
                    join: xor
                """;
            
            String xml = converter.convertToXml(yaml);
            System.out.println("Conversion successful!");
            System.out.println("XML length: " + xml.length());
            System.out.println("Contains timer: " + xml.contains("<timer>"));
            System.out.println("Contains multiInstance: " + xml.contains("<multiInstance>"));
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
