package org.yawlfoundation.yawl.quality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parses the OWASP Dependency-Check JSON/XML report and enforces the
 * YAWL security policy: no CVSS >= 7.0 vulnerabilities in the production
 * dependency graph.
 *
 * <p>Activated only when system property {@code owasp.report.path} points to
 * a Dependency-Check report file (XML format).
 *
 * <p>Run the OWASP scan first:
 * <pre>
 *   mvn -P prod dependency-check:check
 *   mvn test -Dtest=DependencySecurityPolicyTest \
 *       -Dowasp.report.path=target/dependency-check-report.xml
 * </pre>
 *
 * @version 6.0.0
 */
@DisplayName("Dependency Security Policy: No CVSS >= 7.0 in production dependencies")
@EnabledIfSystemProperty(named = "owasp.report.path", matches = ".+")
@Tag("slow")
public class DependencySecurityPolicyTest {

    /**
     * The CVSS score at or above which a vulnerability is policy-blocking.
     * This matches the {@code failBuildOnCVSS=7} setting in the 'prod' Maven profile.
     */
    private static final double BLOCKING_CVSS_THRESHOLD = 7.0;

    /**
     * Dependencies that are test-scope only and therefore excluded from the
     * production security policy.  These identifiers must match the
     * {@code fileName} attribute in the Dependency-Check XML report.
     */
    private static final List<String> TEST_SCOPE_DEPENDENCIES = List.of(
        "h2-",          // H2 database: test and dev only, not deployed
        "junit-",       // JUnit: test scope
        "hamcrest-",    // Hamcrest: test scope
        "xmlunit-",     // XMLUnit: test scope
        "jmh-"          // JMH benchmarks: not deployed
    );

    @Test
    @DisplayName("No production dependencies have CVSS score >= 7.0")
    void noHighOrCriticalVulnerabilitiesInProductionDependencies() throws Exception {
        String reportPath = System.getProperty("owasp.report.path");
        Path reportFile = Path.of(reportPath);

        if (!Files.isRegularFile(reportFile)) {
            fail("owasp.report.path does not point to an existing file: " + reportFile
                 + ". Run 'mvn -P prod dependency-check:check' first.");
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(reportFile.toFile());
        document.getDocumentElement().normalize();

        List<PolicyViolation> violations = new ArrayList<>();

        NodeList dependencies = document.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            String fileName = getTextContent(dependency, "fileName");

            if (isTestScopeDependency(fileName)) {
                continue;
            }

            NodeList vulnerabilities = dependency.getElementsByTagName("vulnerability");
            for (int j = 0; j < vulnerabilities.getLength(); j++) {
                Element vuln = (Element) vulnerabilities.item(j);

                String cveId = getTextContent(vuln, "name");
                double cvssScore = parseCvssScore(vuln);

                if (cvssScore >= BLOCKING_CVSS_THRESHOLD) {
                    String description = getTextContent(vuln, "description");
                    String severity = getTextContent(vuln, "severity");
                    violations.add(new PolicyViolation(fileName, cveId, cvssScore,
                            severity, description));
                }
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder message = new StringBuilder(
                String.format(
                    "SECURITY POLICY VIOLATION: %d production dependenc%s "
                    + "with CVSS >= %.1f:%n",
                    violations.size(),
                    violations.size() == 1 ? "y" : "ies",
                    BLOCKING_CVSS_THRESHOLD
                )
            );

            for (PolicyViolation v : violations) {
                message.append(String.format(
                    "%n  Dependency: %s%n"
                    + "  CVE:        %s%n"
                    + "  CVSS:       %.1f (%s)%n"
                    + "  Summary:    %s%n",
                    v.dependency(), v.cveId(), v.cvssScore(), v.severity(),
                    truncate(v.description(), 120)
                ));
            }

            message.append(
                "\nRemediation: Update vulnerable dependencies, apply patches, "
                + "or add justified suppressions to owasp-suppressions.xml "
                + "with documented rationale and expiry date."
            );

            fail(message.toString());
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private static String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        String text = nodes.item(0).getTextContent();
        return text != null ? text.trim() : "";
    }

    /**
     * Extracts the highest CVSS score from a vulnerability element.
     * Dependency-Check may include both CVSSv2 and CVSSv3 scores.
     * We take the highest to be conservative.
     */
    private static double parseCvssScore(Element vuln) {
        double highest = 0.0;

        // CVSSv3 base score (preferred)
        NodeList cvssv3 = vuln.getElementsByTagName("cvssv3");
        if (cvssv3.getLength() > 0) {
            Element v3 = (Element) cvssv3.item(0);
            String baseScore = getTextContent(v3, "baseScore");
            if (!baseScore.isEmpty()) {
                try {
                    highest = Math.max(highest, Double.parseDouble(baseScore));
                } catch (NumberFormatException ignored) {
                    // Non-numeric CVSS score in report; skip gracefully.
                }
            }
        }

        // CVSSv2 base score (fallback if v3 not available)
        NodeList cvssv2 = vuln.getElementsByTagName("cvssv2");
        if (cvssv2.getLength() > 0) {
            Element v2 = (Element) cvssv2.item(0);
            String score = getTextContent(v2, "score");
            if (!score.isEmpty()) {
                try {
                    highest = Math.max(highest, Double.parseDouble(score));
                } catch (NumberFormatException ignored) {
                    // Non-numeric CVSS score in report; skip gracefully.
                }
            }
        }

        return highest;
    }

    private static boolean isTestScopeDependency(String fileName) {
        for (String prefix : TEST_SCOPE_DEPENDENCIES) {
            if (fileName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    private record PolicyViolation(
        String dependency,
        String cveId,
        double cvssScore,
        String severity,
        String description
    ) {}
}
