import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter;

public class YamlValidator {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java YamlValidator <yaml-file>");
            System.exit(1);
        }
        
        String yamlFile = args[0];
        try {
            // Read YAML file
            String yamlContent = new String(Files.readAllBytes(Paths.get(yamlFile)));
            
            // Convert to XML
            ExtendedYamlConverter converter = new ExtendedYamlConverter();
            String xml = converter.convertToXml(yamlContent);
            
            // Output XML to stdout
            System.out.println(xml);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
