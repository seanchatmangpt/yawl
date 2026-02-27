package org.yawlfoundation.yawl.quality;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Parses the PIT XML report and enforces per-package mutation kill-rate thresholds.
 *
 * <p>Engine core packages (engine, stateless) require 80% kill rate.
 * All other targeted packages require the project-wide 75% floor.
 *
 * <p>Activated only when system property {@code pit.reports.dir} is set.
 *
 * @version 6.0.0
 */
@DisplayName("PIT Mutation Coverage Threshold Enforcement")
@EnabledIfSystemProperty(named = "pit.reports.dir", matches = ".+")
public class MutationCoverageVerifier {

    private static final String[] ENGINE_PACKAGES = {
        "org.yawlfoundation.yawl.engine",
        "org.yawlfoundation.yawl.stateless"
    };

    private static final double ENGINE_THRESHOLD = 0.80;
    private static final double PROJECT_THRESHOLD = 0.75;
    private static final int MINIMUM_MUTATIONS_FOR_ENFORCEMENT = 10;

    @Test
    @DisplayName("Engine and stateless packages meet 80% mutation kill rate")
    void enginePackagesMeet80PercentKillRate() throws Exception {
        Map<String, PackageMutationStats> statsMap = parseReportDirectory();
        StringBuilder failures = new StringBuilder();

        for (Map.Entry<String, PackageMutationStats> entry : statsMap.entrySet()) {
            String packageName = entry.getKey();
            PackageMutationStats stats = entry.getValue();

            if (stats.totalMutations() < MINIMUM_MUTATIONS_FOR_ENFORCEMENT) {
                continue;
            }

            double threshold = isEngineCorePackage(packageName)
                    ? ENGINE_THRESHOLD : PROJECT_THRESHOLD;
            double killRate = stats.killRate();

            if (killRate < threshold) {
                failures.append(String.format(
                    "%n  BELOW THRESHOLD: %s%n"
                    + "    Kill rate:  %.1f%%%n"
                    + "    Required:   %.0f%%%n"
                    + "    Killed:     %d / %d mutations%n"
                    + "    Survived:   %d%n"
                    + "    No-coverage:%d%n",
                    packageName, killRate * 100.0, threshold * 100.0,
                    stats.killed(), stats.totalMutations(),
                    stats.survived(), stats.noCoverage()
                ));
            }
        }

        if (!failures.isEmpty()) {
            fail("Mutation kill-rate thresholds not met. "
                + "Add assertions to test methods exercising the listed packages."
                + failures);
        }
    }

    @Test
    @DisplayName("Project-wide mutation kill rate meets 75% threshold")
    void projectWideMutationKillRateMeetsThreshold() throws Exception {
        Map<String, PackageMutationStats> statsMap = parseReportDirectory();
        long totalMutations = 0;
        long totalKilled = 0;

        for (PackageMutationStats stats : statsMap.values()) {
            totalMutations += stats.totalMutations();
            totalKilled    += stats.killed();
        }

        if (totalMutations < MINIMUM_MUTATIONS_FOR_ENFORCEMENT) {
            return;
        }

        double projectKillRate = (double) totalKilled / totalMutations;
        assertTrue(projectKillRate >= PROJECT_THRESHOLD,
            String.format(
                "Project-wide mutation kill rate %.1f%% is below required %.0f%%.%n"
                + "Total mutations: %d, killed: %d, survived: %d%n"
                + "Increase assertion density in unit tests for surviving mutants.",
                projectKillRate * 100.0, PROJECT_THRESHOLD * 100.0,
                totalMutations, totalKilled, totalMutations - totalKilled
            )
        );
    }

    private Map<String, PackageMutationStats> parseReportDirectory() throws Exception {
        String reportDirProperty = System.getProperty("pit.reports.dir");
        Path reportDir = Path.of(reportDirProperty);

        if (!Files.isDirectory(reportDir)) {
            fail("pit.reports.dir does not point to an existing directory: " + reportDir);
        }

        File mutationsFile = resolveLatestMutationsXml(reportDir);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(mutationsFile);
        document.getDocumentElement().normalize();

        Map<String, PackageMutationStats> stats = new HashMap<>();
        NodeList mutations = document.getElementsByTagName("mutation");

        for (int i = 0; i < mutations.getLength(); i++) {
            Element mutation = (Element) mutations.item(i);
            String mutatedClass = mutation.getElementsByTagName("mutatedClass")
                    .item(0).getTextContent();
            String status = mutation.getAttribute("status");
            String packageName = derivePackage(mutatedClass);

            PackageMutationStats packageStats =
                    stats.computeIfAbsent(packageName, k -> new PackageMutationStats());

            switch (status) {
                case "KILLED"      -> packageStats.incrementKilled();
                case "SURVIVED"    -> packageStats.incrementSurvived();
                case "NO_COVERAGE" -> packageStats.incrementNoCoverage();
                case "TIMED_OUT"   -> packageStats.incrementTimedOut();
                default            -> packageStats.incrementOther();
            }
        }

        return stats;
    }

    private File resolveLatestMutationsXml(Path reportDir) {
        File direct = reportDir.resolve("mutations.xml").toFile();
        if (direct.exists()) {
            return direct;
        }

        File[] subdirs = reportDir.toFile().listFiles(File::isDirectory);
        if (subdirs == null || subdirs.length == 0) {
            fail("No mutations.xml found in PIT report directory: " + reportDir
                 + ". Ensure pitest-maven:mutationCoverage ran before this test.");
        }

        File latest = null;
        for (File subdir : subdirs) {
            File candidate = new File(subdir, "mutations.xml");
            if (candidate.exists()) {
                if (latest == null
                        || subdir.lastModified() > latest.getParentFile().lastModified()) {
                    latest = candidate;
                }
            }
        }

        if (latest == null) {
            fail("No mutations.xml found in any subdirectory of: " + reportDir);
        }
        return latest;
    }

    private static String derivePackage(String fullyQualifiedClass) {
        int lastDot = fullyQualifiedClass.lastIndexOf('.');
        return (lastDot < 0) ? fullyQualifiedClass : fullyQualifiedClass.substring(0, lastDot);
    }

    private static boolean isEngineCorePackage(String packageName) {
        for (String enginePkg : ENGINE_PACKAGES) {
            if (packageName.startsWith(enginePkg)) {
                return true;
            }
        }
        return false;
    }

    private static final class PackageMutationStats {
        private int killed, survived, noCoverage, timedOut, other;

        void incrementKilled()     { killed++; }
        void incrementSurvived()   { survived++; }
        void incrementNoCoverage() { noCoverage++; }
        void incrementTimedOut()   { timedOut++; }
        void incrementOther()      { other++; }

        int killed()     { return killed; }
        int survived()   { return survived; }
        int noCoverage() { return noCoverage; }

        int totalMutations() {
            return killed + survived + noCoverage + timedOut + other;
        }

        double killRate() {
            int denominator = killed + survived + noCoverage;
            if (denominator == 0) {
                return 1.0;
            }
            return (double) killed / denominator;
        }
    }
}
