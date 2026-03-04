package org.yawlfoundation.yawl.bridge.processmining;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example usage of the JNI process mining components.
 * Demonstrates the complete workflow from XES import to conformance checking.
 */
public class ProcessMiningExample {

    public static void main(String[] args) {
        try {
            // Initialize process mining components
            ProcessMiningFactory.validateNativeLibrary();

            XesImporter importer = ProcessMiningFactory.getXesImporter();
            AlphaMiner miner = ProcessMiningFactory.getAlphaMiner();
            ConformanceChecker checker = ProcessMiningFactory.getConformanceChecker();

            // Example path to XES file
            Path xesFile = Paths.get("path/to/your/event_log.xes");

            // 1. Validate and import XES file
            System.out.println("Importing XES file: " + xesFile);
            importer.validateXesFile(xesFile);
            EventLogHandle eventLog = importer.importXesFile(xesFile);
            System.out.println("Event log imported successfully. Handle: " + eventLog.getHandle());

            // 2. Discover process model using Alpha++
            System.out.println("Discovering process model with Alpha++...");
            miner.validateEventLog(eventLog);
            PetriNet petriNet = miner.discover(eventLog);
            System.out.println("Process model discovered: " + petriNet);

            // 3. Check conformance between event log and model
            System.out.println("Checking conformance...");
            checker.validateForConformance(petriNet);
            ConformanceResult conformanceResult = checker.check(eventLog, petriNet);
            System.out.println("Conformance result: " + conformanceResult);

            // 4. Analyze results
            if (conformanceResult.isPerfectFit()) {
                System.out.println("Perfect fit! The event log perfectly matches the discovered model.");
            } else if (conformanceResult.isPoorFit()) {
                System.out.println("Poor fit. The event log significantly deviates from the model.");
            } else {
                System.out.println("Good fit. The event log mostly matches the model.");
            }

        } catch (ProcessMiningException e) {
            System.err.println("Process mining error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example method to analyze a single XES file.
     */
    public static void analyzeXesFile(String xesFilePath) throws ProcessMiningException {
        Path path = Paths.get(xesFilePath);

        XesImporter importer = ProcessMiningFactory.getXesImporter();
        AlphaMiner miner = ProcessMiningFactory.getAlphaMiner();
        ConformanceChecker checker = ProcessMiningFactory.getConformanceChecker();

        // Import
        EventLogHandle eventLog = importer.importXesFile(path);

        // Discover
        PetriNet petriNet = miner.discover(eventLog);

        // Check conformance
        ConformanceResult result = checker.check(eventLog, petriNet);

        System.out.println("Analysis complete for " + xesFilePath);
        System.out.println("Fitness: " + result.getFitness());
        System.out.println("Details: " + result.getDetails());
    }
}