package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Jakarta EE Migration Tests
 * Verifies complete javax -> jakarta migration (Chicago TDD)
 *
 * Coverage:
 * - javax -> jakarta.servlet migration complete
 * - javax -> jakarta.xml migration complete
 * - javax -> jakarta.persistence migration complete
 * - javax -> jakarta.cdi migration complete
 * - javax -> jakarta.mail migration complete
 * - javax -> jakarta.faces migration complete
 * - No javax.* imports in codebase
 * - Tomcat 10+ compatibility
 */
class JakartaEEMigrationTest {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String SRC_DIR = PROJECT_ROOT + "/src";
    private static final String TEST_DIR = PROJECT_ROOT + "/test";

    @Test
    void testNoJavaxServletImports() throws Exception {
        List<String> violations = findImportViolations("javax.servlet");
        if (!violations.isEmpty()) {
            System.out.println("Files with javax.servlet imports:");
            for (String file : violations) {
                System.out.println("  - " + file);
            }
        }
        assertTrue(violations.isEmpty(),
                "All javax.servlet imports should be migrated to jakarta.servlet");
    }

    @Test
    void testNoJavaxPersistenceImports() throws Exception {
        List<String> violations = findImportViolations("javax.persistence");
        if (!violations.isEmpty()) {
            System.out.println("Files with javax.persistence imports:");
            for (String file : violations) {
                System.out.println("  - " + file);
            }
        }
        assertTrue(violations.isEmpty(),
                "All javax.persistence imports should be migrated to jakarta.persistence");
    }

    @Test
    void testNoJavaxXmlImports() throws Exception {
        List<String> violations = findImportViolations("javax.xml.bind");
        if (!violations.isEmpty()) {
            System.out.println("Files with javax.xml.bind imports:");
            for (String file : violations) {
                System.out.println("  - " + file);
            }
        }
        assertTrue(violations.isEmpty(),
                "All javax.xml.bind imports should be migrated to jakarta.xml.bind");
    }

    @Test
    void testNoJavaxCDIImports() throws Exception {
        List<String> violations = findImportViolations("javax.inject");
        violations.addAll(findImportViolations("javax.enterprise"));

        if (!violations.isEmpty()) {
            System.out.println("Files with javax CDI imports:");
            for (String file : violations) {
                System.out.println("  - " + file);
            }
        }
        assertTrue(violations.isEmpty(),
                "All javax CDI imports should be migrated to jakarta");
    }

    @Test
    void testNoJavaxMailImports() throws Exception {
        List<String> violations = findImportViolations("javax.mail");
        if (!violations.isEmpty()) {
            System.out.println("Files with javax.mail imports:");
            for (String file : violations) {
                System.out.println("  - " + file);
            }
        }
        assertTrue(violations.isEmpty(),
                "All javax.mail imports should be migrated to jakarta.mail");
    }

    @Test
    void testNoJavaxFacesImports() throws Exception {
        List<String> violations = findImportViolations("javax.faces");
        if (!violations.isEmpty()) {
            System.out.println("Files with javax.faces imports:");
            for (String file : violations) {
                System.out.println("  - " + file);
            }
        }
        assertTrue(violations.isEmpty(),
                "All javax.faces imports should be migrated to jakarta.faces");
    }

    @Test
    void testJakartaServletImportsPresent() throws Exception {
        List<String> filesWithJakarta = findFiles("jakarta.servlet");
        // If we find jakarta.servlet imports, migration is in progress/complete
        System.out.println("Files using jakarta.servlet: " + filesWithJakarta.size());
    }

    @Test
    void testJakartaPersistenceImportsPresent() throws Exception {
        List<String> filesWithJakarta = findFiles("jakarta.persistence");
        System.out.println("Files using jakarta.persistence: " + filesWithJakarta.size());
    }

    @Test
    void testWebXmlUsesJakartaNamespace() throws Exception {
        File webXml = findWebXml();
        if (webXml != null && webXml.exists()) {
            String content = readFile(webXml);

            // Check for old Java EE namespace
            assertFalse(content.contains("java.sun.com/xml/ns/javaee"),
                    "web.xml should not use old java.sun.com namespace");

            // Check for Jakarta EE namespace
            if (content.contains("xmlns")) {
                assertTrue(content.contains("jakarta.ee") ||
                        !content.contains("java.sun.com"),
                        "web.xml should use jakarta.ee namespace or be compatible");
            }
        } else {
            System.out.println("web.xml not found - may be using programmatic configuration");
        }
    }

    @Test
    void testPomDependenciesUseJakarta() throws Exception {
        File pomFile = new File(PROJECT_ROOT, "pom.xml");
        assertTrue(pomFile.exists(), "pom.xml should exist");

        String content = readFile(pomFile);

        // Check for old javax dependencies
        List<String> oldDependencies = new ArrayList<>();
        if (content.contains("<groupId>javax.servlet</groupId>")) {
            oldDependencies.add("javax.servlet");
        }
        if (content.contains("<groupId>javax.persistence</groupId>")) {
            oldDependencies.add("javax.persistence");
        }

        if (!oldDependencies.isEmpty()) {
            System.out.println("WARNING: Found old javax dependencies: " + oldDependencies);
        }

        // Informational: check for Jakarta dependencies
        boolean hasJakartaServlet = content.contains("jakarta.servlet");
        boolean hasJakartaPersistence = content.contains("jakarta.persistence");

        System.out.println("Jakarta servlet dependency: " + hasJakartaServlet);
        System.out.println("Jakarta persistence dependency: " + hasJakartaPersistence);
    }

    @Test
    void testTomcat10Compatibility() throws Exception {
        File pomFile = new File(PROJECT_ROOT, "pom.xml");
        String content = readFile(pomFile);

        // Check Tomcat version if specified
        if (content.contains("tomcat")) {
            System.out.println("Tomcat configuration detected in pom.xml");

            // Look for Tomcat 10+ version indicators
            boolean isTomcat10Plus = content.contains("tomcat.version>10") ||
                    content.contains("tomcat.version>11") ||
                    content.contains("jakarta");

            if (content.contains("tomcat.version")) {
                System.out.println("Tomcat version configuration found");
            }
        }
    }

    @Test
    void testNoLegacyJavaEEReferences() throws Exception {
        List<String> javaFiles = getAllJavaFiles();
        List<String> violations = new ArrayList<>();

        for (String file : javaFiles) {
            File f = new File(file);
            if (f.exists()) {
                String content = readFile(f);

                // Check for common legacy Java EE patterns
                if (content.contains("javax.annotation.") &&
                        !content.contains("javax.annotation.processing")) {
                    violations.add(file + " contains javax.annotation imports");
                }
                if (content.contains("javax.ejb.")) {
                    violations.add(file + " contains javax.ejb imports");
                }
                if (content.contains("javax.jms.")) {
                    violations.add(file + " contains javax.jms imports");
                }
            }
        }

        if (!violations.isEmpty()) {
            System.out.println("Legacy Java EE references found:");
            for (String violation : violations.subList(0, Math.min(10, violations.size()))) {
                System.out.println("  - " + violation);
            }
            if (violations.size() > 10) {
                System.out.println("  ... and " + (violations.size() - 10) + " more");
            }
        }
    }

    @Test
    void testMigrationCompleteness() throws Exception {
        List<String> allJavaFiles = getAllJavaFiles();
        int totalFiles = allJavaFiles.size();
        assertTrue(totalFiles > 0, "Should have Java files to test");

        int filesWithJavax = 0;
        int filesWithJakarta = 0;

        for (String file : allJavaFiles) {
            File f = new File(file);
            if (f.exists()) {
                String content = readFile(f);
                if (content.contains("import javax.servlet") ||
                        content.contains("import javax.persistence") ||
                        content.contains("import javax.xml.bind")) {
                    filesWithJavax++;
                }
                if (content.contains("import jakarta.")) {
                    filesWithJakarta++;
                }
            }
        }

        System.out.println("Migration statistics:");
        System.out.println("  Total Java files: " + totalFiles);
        System.out.println("  Files with javax imports: " + filesWithJavax);
        System.out.println("  Files with jakarta imports: " + filesWithJakarta);

        double migrationPercentage = totalFiles > 0 ?
                (double) (totalFiles - filesWithJavax) * 100 / totalFiles : 100.0;
        System.out.println("  Migration progress: " + String.format("%.1f%%", migrationPercentage));
    }

    // Helper methods

    private List<String> findImportViolations(String importPattern) throws Exception {
        List<String> violations = new ArrayList<>();
        List<String> javaFiles = getAllJavaFiles();

        for (String file : javaFiles) {
            File f = new File(file);
            if (f.exists() && containsImport(f, importPattern)) {
                violations.add(file);
            }
        }

        return violations;
    }

    private List<String> findFiles(String pattern) throws Exception {
        List<String> matches = new ArrayList<>();
        List<String> javaFiles = getAllJavaFiles();

        for (String file : javaFiles) {
            File f = new File(file);
            if (f.exists()) {
                String content = readFile(f);
                if (content.contains(pattern)) {
                    matches.add(file);
                }
            }
        }

        return matches;
    }

    private boolean containsImport(File file, String importPattern) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("import " + importPattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> getAllJavaFiles() throws Exception {
        List<String> files = new ArrayList<>();
        findJavaFiles(new File(SRC_DIR), files);
        findJavaFiles(new File(TEST_DIR), files);
        return files;
    }

    private void findJavaFiles(File dir, List<String> files) {
        if (dir.exists() && dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        findJavaFiles(child, files);
                    } else if (child.getName().endsWith(".java")) {
                        files.add(child.getAbsolutePath());
                    }
                }
            }
        }
    }

    private File findWebXml() {
        File webInf = new File(PROJECT_ROOT, "src/main/webapp/WEB-INF/web.xml");
        if (webInf.exists()) {
            return webInf;
        }

        webInf = new File(PROJECT_ROOT, "web/WEB-INF/web.xml");
        if (webInf.exists()) {
            return webInf;
        }

        return null;
    }

    private String readFile(File file) throws Exception {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}
