package org.ggen.ast;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaAstParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testParseSimpleJavaFile() throws IOException, JavaAstParser.ParseException {
        // Create a simple Java file
        String javaCode = """
            public class TestClass {
                // This is a test comment
                public void testMethod() {
                    System.out.println("Hello World");
                }
            }
            """;

        Path testFile = tempDir.resolve("TestClass.java");
        Files.writeString(testFile, javaCode);

        // Parse the file
        JavaAstParser parser = new JavaAstParser();
        Tree ast = parser.parseFile(testFile);

        // Verify parsing worked
        assertNotNull(ast);
        assertTrue(ast.getSource().contains("public class TestClass"));
        assertFalse(ast.isTreeSitter()); // Should use JavaParser by default
    }

    @Test
    void testExtractMethods() throws IOException, JavaAstParser.ParseException {
        String javaCode = """
            public class TestClass {
                public void method1() {
                    // Implementation
                }

                public String method2(int param) {
                    return "test";
                }
            }
            """;

        Path testFile = tempDir.resolve("TestClass.java");
        Files.writeString(testFile, javaCode);

        JavaAstParser parser = new JavaAstParser();
        Tree ast = parser.parseFile(testFile);
        List<MethodInfo> methods = parser.extractMethods(ast);

        // Should extract 2 methods
        assertEquals(2, methods.size());

        // Check first method
        MethodInfo method1 = methods.get(0);
        assertEquals("method1", method1.name());
        assertEquals("void", method1.returnType());
        assertTrue(method1.body().contains("Implementation"));

        // Check second method
        MethodInfo method2 = methods.get(1);
        assertEquals("method2", method2.name());
        assertEquals("String", method2.returnType());
    }

    @Test
    void testExtractComments() throws IOException, JavaAstParser.ParseException {
        String javaCode = """
            public class TestClass {
                // Single line comment
                /*
                 * Multi-line comment
                 */
                /** Javadoc comment */
                public void testMethod() {
                    // This method is implemented below in the test logic
                }
            }
            """;

        Path testFile = tempDir.resolve("TestClass.java");
        Files.writeString(testFile, javaCode);

        JavaAstParser parser = new JavaAstParser();
        Tree ast = parser.parseFile(testFile);
        List<CommentInfo> comments = parser.extractComments(ast);

        // Should extract 4 comments
        assertEquals(4, comments.size());

        // Check single line comment
        CommentInfo singleLine = comments.get(0);
        assertEquals(CommentInfo.CommentType.LINE, singleLine.type());
        assertTrue(singleLine.getText().contains("Single line comment"));

        // Check TODO comment
        CommentInfo todo = comments.stream()
                .filter(c -> c.isTodo())
                .findFirst()
                .orElse(null);
        assertNotNull(todo);
        assertTrue(todo.getText().contains("TODO"));
    }

    @Test
    void testExtractClasses() throws IOException, JavaAstParser.ParseException {
        String javaCode = """
            package com.example.test;

            public class TestClass {
                public void method1() {
                    throw new UnsupportedOperationException("method1 requires real implementation");
                }
            }

            class InnerClass {
                public void method2() {
                    throw new UnsupportedOperationException("method2 requires real implementation");
                }
            }
            """;

        Path testFile = tempDir.resolve("TestClass.java");
        Files.writeString(testFile, javaCode);

        JavaAstParser parser = new JavaAstParser();
        Tree ast = parser.parseFile(testFile);
        List<ClassInfo> classes = parser.extractClasses(ast);

        // Should extract 2 classes
        assertEquals(2, classes.size());

        // TestClass
        ClassInfo testClass = classes.get(0);
        assertEquals("TestClass", testClass.name());
        assertEquals("com.example.test", testClass.packageName());
        assertEquals(1, testClass.getMethodCount());

        // InnerClass
        ClassInfo innerClass = classes.get(1);
        assertEquals("InnerClass", innerClass.name());
        assertEquals("", innerClass.packageName()); // No package
        assertEquals(1, innerClass.getMethodCount());
    }

    @Test
    void testParseAndExtract() throws IOException, JavaAstParser.ParseException {
        String javaCode = """
            public class TestClass {
                // Test comment
                public void testMethod() {
                    System.out.println("test");
                }
            }
            """;

        Path testFile = tempDir.resolve("TestClass.java");
        Files.writeString(testFile, javaCode);

        JavaAstParser parser = new JavaAstParser();
        JavaAstParser.ParseResult result = parser.parseAndExtract(testFile);

        // Verify all components are extracted
        assertEquals(testFile, result.getFilePath());
        assertNotNull(result.getAst());
        assertEquals(1, result.getMethods().size());
        assertEquals(1, result.getComments().size());
        assertEquals(1, result.getClasses().size());

        // Verify method details
        MethodInfo method = result.getMethods().get(0);
        assertEquals("testMethod", method.name());
        assertEquals("void", method.returnType());
        assertEquals(0, method.parameters().size());

        // Verify class details
        ClassInfo clazz = result.getClasses().get(0);
        assertEquals("TestClass", clazz.name());
        assertEquals(1, clazz.getMethodCount());
    }
}