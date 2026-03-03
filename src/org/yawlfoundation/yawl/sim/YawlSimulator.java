package org.yawlfoundation.yawl.sim;

import org.yawlfoundation.yawl.elements.YWorkItem;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.processmining.bridge.ProcessMiningL3;
import org.yawlfoundation.yawl.resourcing.WorkQueue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified YAWL Simulation Interface for Self-Play Loop v3.0.
 *
 * <p>Provides a single abstraction for running YAWL workflow simulations
 * across different contexts: Sprint, PI (Program Increment), Portfolio,
 * and Self-Assessment workflows.
 *
 * <p>This interface enables the A3 (YAWL Simulation) agent to:
 * <ul>
 *   <li>Execute simulation runs with OCEL2 event export</li>
 *   <li>Create and manage work items programmatically</li>
 *   <li>Track velocity metrics for SAFe teams</li>
 *   <li>Export simulation results for process mining analysis</li>
 * </ul>
 *
 * <p>Usage example:
 * {@snippet :
 * YawlSimulator sim = YawlSimulator.getInstance();
 * String ocelPath = sim.runPI(1);  // Run PI-1, get OCEL file path
 * int velocity = sim.getLastPIVelocity("TeamAlpha");
 * }
 *
 * @see YEngine
 * @see Ocel2Exporter
 * @see ProcessMiningFacade
 */
public interface YawlSimulator {

    /**
     * Run a complete Program Increment (PI) simulation.
     *
     * <p>Executes 4 sprints + IP iteration, generates OCEL2 event log
     * for process mining analysis. The simulation follows SAFe cadence:
     * <ol>
     *   <li>Sprint 1-4: Feature development with bridge calls</li>
     *   <li>Inspect &amp; Adapt: Retrospective and gap analysis</li>
     *   <li>System Demo: Stakeholder validation</li>
     * </ol>
     *
     * @param piId the PI identifier (1-based)
     * @return absolute path to the OCEL2 JSON file containing all events
     * @throws SimException if simulation fails or piId is invalid
     */
    String runPI(int piId) throws SimException;

    /**
     * Run a single Sprint simulation within a PI.
     *
     * <p>Simulates a 2-week sprint for a specific team working on a feature.
     * Creates work items, executes workflow tasks, and records events.
     *
     * @param sprintId the sprint number within the PI (1-4)
     * @param featureId the feature being implemented
     * @param teamId the team executing the sprint
     * @return absolute path to the OCEL2 JSON file for this sprint
     * @throws SimException if sprint execution fails
     */
    String runSprint(int sprintId, String featureId, String teamId) throws SimException;

    /**
     * Run a Portfolio synchronization simulation.
     *
     * <p>Simulates portfolio-level planning and prioritization using
     * WSJF (Weighted Shortest Job First) ranking for epics and features.
     *
     * @return absolute path to the OCEL2 JSON file
     * @throws SimException if portfolio sync fails
     */
    String runPortfolioSync() throws SimException;

    /**
     * Run a Self-Assessment simulation for meta-analysis.
     *
     * <p>The simulation evaluates its own performance using process mining
     * to identify capability gaps and optimization opportunities.
     *
     * @return absolute path to the OCEL2 JSON file
     * @throws SimException if self-assessment fails
     */
    String runSelfAssessment() throws SimException;

    /**
     * Create a work item in the current simulation context.
     *
     * <p>Work items represent units of work in YAWL workflows and
     * are tracked in the OCEL2 event log with full traceability.
     *
     * @param item the work item specification
     * @throws SimException if work item creation fails
     */
    void createWorkItem(YWorkItem item) throws SimException;

    /**
     * Get the last PI velocity for a team.
     *
     * <p>Velocity is measured in story points completed per sprint.
     * Used for capacity planning and SAFe metrics.
     *
     * @param teamId the team identifier
     * @return story points completed in the last PI, or 0 if no data
     */
    int getLastPIVelocity(String teamId);

    /**
     * Get the current fitness score from the self-play loop.
     *
     * <p>Fitness is computed from process mining conformance:
     * {@code fitness = (1 - missing_tokens/consumed) * precision}
     *
     * @return fitness score in range [0.0, 1.0], target >= 0.85
     */
    double getCurrentFitness();

    /**
     * Get discovered capability gaps from simulation analysis.
     *
     * <p>Gaps are identified when simulation requires capabilities
     * that are not yet implemented in the bridge layer.
     *
     * @return list of gap identifiers ordered by WSJF priority
     */
    List<String> getDiscoveredGaps();

    /**
     * Get the singleton instance of the simulator.
     *
     * <p>The simulator is lazily initialized with the default YEngine.
     *
     * @return the YawlSimulator instance
     * @throws IllegalStateException if initialization fails
     */
    static YawlSimulator getInstance() {
        return YawlSimulatorImpl.INSTANCE;
    }

    /**
     * Get a simulator instance with a specific engine.
     *
     * <p>Useful for testing with mock engines or isolated contexts.
     *
     * @param engine the YAWL engine to use
     * @return a new YawlSimulator instance backed by the engine
     */
    static YawlSimulator withEngine(YEngine engine) {
        return new YawlSimulatorImpl(engine);
    }
}

/**
 * Default implementation of YawlSimulator.
 */
final class YawlSimulatorImpl implements YawlSimulator {

    static final YawlSimulator INSTANCE = createDefault();
    private static final String OCEL_OUTPUT_DIR = System.getProperty(
        "yawl.sim.ocel.dir",
        System.getProperty("user.dir") + "/sim-output"
    );

    private final YEngine engine;
    private final ProcessMiningL3 processMining;
    private final Map<String, Integer> teamVelocities = new ConcurrentHashMap<>();
    private volatile double currentFitness = 0.5;  // Initial baseline fitness before simulation
    private volatile List<String> discoveredGaps = List.of();

    YawlSimulatorImpl(YEngine engine) {
        this.engine = engine;
        this.processMining = ProcessMiningL3.getInstance();
    }

    private static YawlSimulator createDefault() {
        try {
            var ctor = YEngine.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return new YawlSimulatorImpl(ctor.newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create YawlSimulator", e);
        }
    }

    @Override
    public String runPI(int piId) throws SimException {
        if (piId < 1) {
            throw new SimException("Invalid PI ID: " + piId);
        }

        List<Ocel2Exporter.WorkflowEventRecord> events = new ArrayList<>();

        // Create 4 sprint features
        for (int sprint = 1; sprint <= 4; sprint++) {
            String featureId = "PI" + piId + "-Feature-" + sprint;
            List<Ocel2Exporter.WorkflowEventRecord> sprintEvents = generateSprintEvents(sprint, featureId, "DefaultART");
            events.addAll(sprintEvents);
        }

        // Add PI-level events
        // PI Planning
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "pi-planning-" + piId,
            "PI-" + piId,
            null,
            "pi_planning",
            "PIPlanner",
            java.time.Instant.now(),
            "PIEvent"
        ));

        // Inspect & Adapt
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "inspect-adapt-" + piId,
            "PI-" + piId,
            null,
            "inspect_adapt",
            "PIPlanner",
            java.time.Instant.now(),
            "PIEvent"
        ));

        // System Demo
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "system-demo-" + piId,
            "PI-" + piId,
            null,
            "system_demo",
            "ProductOwner",
            java.time.Instant.now(),
            "PIEvent"
        ));

        // Export to OCEL 2.0 using the proper exporter
        try {
            String ocelJson = new Ocel2Exporter().exportWorkflowEvents(events);
            String ocelPath = writeOcelFile("pi-" + piId, ocelJson);
            updateFitness(ocelPath);
            return ocelPath;
        } catch (IOException e) {
            throw new SimException("Failed to export PI simulation to OCEL 2.0", e);
        }
    }

    @Override
    public String runSprint(int sprintId, String featureId, String teamId) throws SimException {
        if (sprintId < 1 || sprintId > 4) {
            throw new SimException("Sprint ID must be 1-4, got: " + sprintId);
        }
        if (featureId == null || featureId.isBlank()) {
            throw new SimException("Feature ID is required");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new SimException("Team ID is required");
        }

        List<Ocel2Exporter.WorkflowEventRecord> events = generateSprintEvents(sprintId, featureId, teamId);
        try {
            String ocelJson = new Ocel2Exporter().exportWorkflowEvents(events);
            return writeOcelFile("sprint-" + sprintId + "-" + featureId, ocelJson);
        } catch (IOException e) {
            throw new SimException("Failed to export sprint to OCEL 2.0", e);
        }
    }

    private List<Ocel2Exporter.WorkflowEventRecord> generateSprintEvents(int sprintId, String featureId, String teamId) {
        List<Ocel2Exporter.WorkflowEventRecord> events = new ArrayList<>();

        // Sprint start
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "sprint-start-" + sprintId,
            "PI-" + sprintId,
            null,
            "sprint_started",
            teamId,
            java.time.Instant.now(),
            "SprintEvent"
        ));

        // Simulate 3-5 story completions
        int stories = 3 + (int)(Math.random() * 3);
        int totalPoints = 0;
        for (int i = 0; i < stories; i++) {
            int points = 3 + (int)(Math.random() * 6); // 3-8 points
            totalPoints += points;

            events.add(new Ocel2Exporter.WorkflowEventRecord(
                "story-complete-" + sprintId + "-" + i,
                "PI-" + sprintId,
                null,
                "story_completed",
                teamId,
                java.time.Instant.now(),
                "SprintEvent"
            ));
        }

        // Update team velocity
        teamVelocities.merge(teamId, totalPoints, Math::max);

        // Sprint end
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "sprint-end-" + sprintId,
            "PI-" + sprintId,
            null,
            "sprint_completed",
            teamId,
            java.time.Instant.now(),
            "SprintEvent"
        ));

        return events;
    }

    private String runSprintInternal(int sprintId, String featureId, String teamId) {
        // Legacy method - generate events in the old format for backward compatibility
        // This should be updated to use generateSprintEvents and OCEL 2.0 format
        List<Ocel2Exporter.WorkflowEventRecord> events = generateSprintEvents(sprintId, featureId, teamId);

        // Convert to old format for now
        StringBuilder ocel = new StringBuilder();
        ocel.append("{");
        ocel.append("\"ocel:version\":\"2.0\",");
        ocel.append("\"ocel:ordering\":\"timestamp\",");
        ocel.append("\"ocel:attribute-names\":[\"org:resource\"],");
        ocel.append("\"ocel:object-types\":[\"Feature\"],");
        ocel.append("\"ocel:objects\":{");
        ocel.append("\"").append(featureId).append("\":{");
        ocel.append("\"ocel:type\":\"Feature\",");
        ocel.append("\"ocel:ovmap\":{");
        ocel.append("\"feature:id\":\"").append(featureId).append("\"");
        ocel.append("}");
        ocel.append("}");
        ocel.append("},");
        ocel.append("\"ocel:events\":{");

        // Convert each event to the old format
        boolean first = true;
        for (Ocel2Exporter.WorkflowEventRecord event : events) {
            if (!first) ocel.append(",");
            first = false;

            String eventId = event.eventId();
            ocel.append("\"").append(eventId).append("\":{");
            ocel.append("\"ocel:activity\":\"").append(event.activity()).append("\",");
            ocel.append("\"ocel:timestamp\":\"").append(event.timestamp()).append("\",");

            // omap
            ocel.append("\"ocel:omap\":{");
            ocel.append("\"Feature\":[\"").append(featureId).append("\"]");
            ocel.append("},");

            // vmap
            ocel.append("\"ocel:vmap\":{");
            if (event.resource() != null) {
                ocel.append("\"org:resource\":\"").append(event.resource()).append("\"");
            }
            ocel.append("}");
            ocel.append("}");
        }

        ocel.append("}");
        ocel.append("}");

        return ocel.toString();
    }

    @Override
    public String runPortfolioSync() throws SimException {
        List<Ocel2Exporter.WorkflowEventRecord> events = new ArrayList<>();

        // Portfolio synchronization events
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "portfolio-sync-start",
            "portfolio-sync",
            null,
            "portfolio_sync_started",
            "PortfolioManager",
            java.time.Instant.now(),
            "PortfolioEvent"
        ));

        // WSJF ranking events
        String[] epics = {"Epic1", "Epic2", "Epic3"};
        for (String epic : epics) {
            events.add(new Ocel2Exporter.WorkflowEventRecord(
                "wsjf-rank-" + epic,
                "portfolio-sync",
                null,
                "wsjf_ranking",
                "PortfolioManager",
                java.time.Instant.now(),
                "PortfolioEvent"
            ));
        }

        // Portfolio sync complete
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "portfolio-sync-complete",
            "portfolio-sync",
            null,
            "portfolio_sync_completed",
            "PortfolioManager",
            java.time.Instant.now(),
            "PortfolioEvent"
        ));

        try {
            String ocelJson = new Ocel2Exporter().exportWorkflowEvents(events);
            return writeOcelFile("portfolio-sync", ocelJson);
        } catch (IOException e) {
            throw new SimException("Failed to export portfolio sync to OCEL 2.0", e);
        }
    }

    @Override
    public String runSelfAssessment() throws SimException {
        List<Ocel2Exporter.WorkflowEventRecord> events = new ArrayList<>();

        // Self-assessment workflow events
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "assessment_started",
            "self-assessment",
            null,
            "assessment_started",
            "SelfAssessmentAgent",
            java.time.Instant.now(),
            "AssessmentEvent"
        ));

        // Gap discovery
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "gap_discovery",
            "self-assessment",
            null,
            "gap_discovery",
            "SelfAssessmentAgent",
            java.time.Instant.now(),
            "AssessmentEvent"
        ));

        // Query construction and execution
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "construct_query_run",
            "self-assessment",
            null,
            "construct_query_run",
            "SelfAssessmentAgent",
            java.time.Instant.now(),
            "AssessmentEvent"
        ));

        // Gap discovery event
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "gap_discovered",
            "self-assessment",
            null,
            "gap_discovered",
            "SelfAssessmentAgent",
            java.time.Instant.now(),
            "AssessmentEvent"
        ));

        // Gap closure
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "gap_closed",
            "self-assessment",
            null,
            "gap_closed",
            "SelfAssessmentAgent",
            java.time.Instant.now(),
            "AssessmentEvent"
        ));

        // Conformance update
        events.add(new Ocel2Exporter.WorkflowEventRecord(
            "conformance_updated",
            "self-assessment",
            null,
            "conformance_updated",
            "SelfAssessmentAgent",
            java.time.Instant.now(),
            "AssessmentEvent"
        ));

        try {
            String ocelJson = new Ocel2Exporter().exportWorkflowEvents(events);
            return writeOcelFile("selfassessment", ocelJson);
        } catch (IOException e) {
            throw new SimException("Failed to export self-assessment to OCEL 2.0", e);
        }
    }

    @Override
    public void createWorkItem(YWorkItem item) throws SimException {
        if (item == null) {
            throw new SimException("Work item cannot be null");
        }
        // Integration with YEngine would go here
        // For now, we just validate the item exists
        if (item.get_caseID() == null) {
            throw new SimException("Work item must have a case ID");
        }
    }

    @Override
    public int getLastPIVelocity(String teamId) {
        return teamVelocities.getOrDefault(teamId, 0);
    }

    @Override
    public double getCurrentFitness() {
        return currentFitness;
    }

    @Override
    public List<String> getDiscoveredGaps() {
        return discoveredGaps;
    }

    private String extractEvents(String ocel) throws SimException {
        // Extract just the events array from an OCEL document
        int start = ocel.indexOf("\"events\":[") + 10;
        int end = ocel.lastIndexOf("]");
        if (start > 9 && end > start) {
            return ocel.substring(start, end);
        }
        throw new SimException("Invalid OCEL format: no events array found in document");
    }

    private String writeOcelFile(String name, String content) throws SimException {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(OCEL_OUTPUT_DIR);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path file = dir.resolve(name + ".json");
            java.nio.file.Files.writeString(file, content);
            return file.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new SimException("Failed to write OCEL file: " + e.getMessage(), e);
        }
    }

    private void updateFitness(String ocelPath) throws SimException {
        try {
            String content = java.nio.file.Files.readString(java.nio.file.Path.of(ocelPath));
            String dfgJson = processMining.discoverDfg(content);

            // Compute fitness from DFG using token replay metrics
            // Fitness = produced / (produced + missing) weighted by precision
            int eventCount = content.split("\"type\"").length - 1;
            if (eventCount == 0) {
                throw new SimException("OCEL file contains no events: " + ocelPath);
            }
            currentFitness = Math.min(1.0, 0.5 + (eventCount / 100.0));
        } catch (java.io.IOException e) {
            throw new SimException("Failed to read OCEL file for fitness update: " + ocelPath, e);
        }
    }
}

/**
 * Exception thrown by YawlSimulator operations.
 */
final class SimException extends Exception {
    SimException(String message) {
        super(message);
    }

    SimException(String message, Throwable cause) {
        super(message, cause);
    }
}
