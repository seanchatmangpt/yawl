import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

// Simple test to verify our AST classes compile and work
public class test_ast {
    public static void main(String[] args) {
        System.out.println("Testing JavaAstParser implementation...");

        try {
            // Test JavaAstParser exists
            Class<?> parserClass = Class.forName("org.ggen.ast.JavaAstParser");
            System.out.println("✓ JavaAstParser class loaded");

            // Test other classes
            Class<?> treeClass = Class.forName("org.ggen.ast.Tree");
            System.out.println("✓ Tree class loaded");

            Class<?> methodInfoClass = Class.forName("org.ggen.ast.MethodInfo");
            System.out.println("✓ MethodInfo class loaded");

            Class<?> commentInfoClass = Class.forName("org.ggen.ast.CommentInfo");
            System.out.println("✓ CommentInfo class loaded");

            Class<?> classInfoClass = Class.forName("org.ggen.ast.ClassInfo");
            System.out.println("✓ ClassInfo class loaded");

            Class<?> fieldInfoClass = Class.forName("org.ggen.ast.FieldInfo");
            System.out.println("✓ FieldInfo class loaded");

            Class<?> parameterInfoClass = Class.forName("org.ggen.ast.ParameterInfo");
            System.out.println("✓ ParameterInfo class loaded");

            Class<?> treeSitterWrapperClass = Class.forName("org.ggen.ast.TreeSitterJavaWrapper");
            System.out.println("✓ TreeSitterJavaWrapper class loaded");

            System.out.println("\nAll classes successfully loaded!");

            // Test record functionality
            testRecords();

        } catch (ClassNotFoundException e) {
            System.err.println("❌ Class not found: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }

    private static void testRecords() {
        // Test record creation and methods
        org.ggen.ast.CommentInfo comment = new org.ggen.ast.CommentInfo(
            "comment1",
            "// This is a test comment",
            1,
            org.ggen.ast.CommentInfo.CommentType.LINE
        );

        System.out.println("\nCommentInfo test:");
        System.out.println("  Text: " + comment.getText());
        System.out.println("  Clean text: " + comment.getCleanText());
        System.out.println("  Is Javadoc: " + comment.isJavadoc());
        System.out.println("  Is Todo: " + comment.isTodo());

        // Test parameter info
        org.ggen.ast.ParameterInfo param = new org.ggen.ast.ParameterInfo(
            "name",
            "String",
            false
        );

        System.out.println("\nParameterInfo test:");
        System.out.println("  Name: " + param.name());
        System.out.println("  Type: " + param.type());
        System.out.println("  VarArgs: " + param.varArgs());
        System.out.println("  Simple type: " + param.getSimpleTypeName());
    }
}