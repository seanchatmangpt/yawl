package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates the MAPE-K autonomic control loop for the wizard.
 *
 * <p>The manager is the top-level component that runs the complete autonomic
 * self-healing cycle: Monitor → Analyze → Plan → Execute (MAPE).
 *
 * <p>Usage pattern:
 * <pre>
 *   var manager = AutonomicWizardManager.withDefaults();
 *   var session = AutonomicWizardEngine.initSession();
 *   // ... run wizard steps ...
 *   var healthySession = manager.runLoop(session);  // self-heals if needed
 * </pre>
 *
 * <p>The manager can be called at any point during wizard execution to:
 * <ul>
 *   <li>Check if the session is healthy ({@link #isHealthy(WizardSession)})</li>
 *   <li>Run a full MAPE-K cycle to detect and recover from problems ({@link #runLoop(WizardSession)})</li>
 * </ul>
 *
 * <p>Architecture:
 * <ul>
 *   <li>Monitor: collects observable symptoms from session state</li>
 *   <li>Analyze: diagnoses root causes using the knowledge base</li>
 *   <li>Plan: sequences recovery actions into a recovery plan</li>
 *   <li>Execute: applies the plan to recover the session</li>
 * </ul>
 *
 * <p>The MAPE-K loop is designed for self-healing without human intervention.
 * If the loop detects problems, it automatically plans and executes corrective
 * actions to restore wizard progress.
 *
 * @see AutonomicMonitor for Monitor phase
 * @see AutonomicAnalyzer for Analyze phase
 * @see AutonomicPlanner for Plan phase
 * @see AutonomicExecutor for Execute phase
 * @see KnowledgeBase for the 'K' (Knowledge) component
 */
public class AutonomicWizardManager {

    private final AutonomicMonitor monitor;
    private final AutonomicAnalyzer analyzer;
    private final AutonomicPlanner planner;
    private final AutonomicExecutor executor;
    private final KnowledgeBase knowledgeBase;

    /**
     * Constructor: creates a manager with all components.
     *
     * @param monitor the monitor component
     * @param analyzer the analyzer component
     * @param planner the planner component
     * @param executor the executor component
     * @param knowledgeBase the knowledge base
     * @throws NullPointerException if any parameter is null
     */
    public AutonomicWizardManager(
        AutonomicMonitor monitor,
        AutonomicAnalyzer analyzer,
        AutonomicPlanner planner,
        AutonomicExecutor executor,
        KnowledgeBase knowledgeBase
    ) {
        this.monitor = Objects.requireNonNull(monitor, "monitor must not be null");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
        this.planner = Objects.requireNonNull(planner, "planner must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.knowledgeBase = Objects.requireNonNull(knowledgeBase, "knowledgeBase must not be null");
    }

    /**
     * Factory method: creates a manager with all components configured with defaults.
     *
     * <p>This is the primary entry point. It creates a fully configured autonomic
     * manager using:
     * <ul>
     *   <li>AutonomicMonitor (default instance)</li>
     *   <li>AutonomicAnalyzer with default knowledge base</li>
     *   <li>AutonomicPlanner (default instance)</li>
     *   <li>AutonomicExecutor (default instance)</li>
     *   <li>KnowledgeBase.defaultBase() with all built-in rules</li>
     * </ul>
     *
     * @return fully configured autonomic manager ready to use
     */
    public static AutonomicWizardManager withDefaults() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();
        return new AutonomicWizardManager(
            new AutonomicMonitor(),
            new AutonomicAnalyzer(kb),
            new AutonomicPlanner(),
            new AutonomicExecutor(),
            kb
        );
    }

    /**
     * Run one complete iteration of the MAPE-K autonomic control loop.
     *
     * <p>Execution flow:
     * <ol>
     *   <li><b>M (Monitor)</b>: Examine session for all symptom types</li>
     *   <li>If no symptoms detected, return session unchanged (system is healthy)</li>
     *   <li><b>A (Analyze)</b>: Diagnose root causes for each symptom</li>
     *   <li><b>P (Plan)</b>: Build a recovery plan from diagnoses</li>
     *   <li><b>E (Execute)</b>: Apply the plan to recover the session</li>
     *   <li>Return the recovered session (possibly with updated state and phase)</li>
     * </ol>
     *
     * <p>Error handling: If ABORT_WIZARD action is executed, the session will
     * be in FAILED phase. The manager returns the failed session rather than
     * raising an exception.
     *
     * <p>Idempotency: Running the loop multiple times on a healthy session
     * will return the session unchanged each time.
     *
     * @param session the current wizard session
     * @return the session after MAPE-K recovery (may be unchanged if healthy)
     * @throws NullPointerException if session is null
     */
    public WizardSession runLoop(WizardSession session) {
        Objects.requireNonNull(session, "session must not be null");

        // M: Monitor - examine session for symptoms
        List<WizardSymptom> symptoms = monitor.examine(session);

        // If no symptoms, session is healthy - return unchanged
        if (symptoms.isEmpty()) {
            return session;
        }

        // A: Analyze - diagnose root causes from symptoms
        List<WizardDiagnosis> diagnoses = analyzer.diagnoseAll(symptoms, session);

        // P: Plan - build recovery plan from diagnoses
        AutonomicPlanner.RecoveryPlan plan = planner.plan(diagnoses, session);

        // E: Execute - apply plan to recover session
        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        // Return the recovered session
        return result.recoveredSession();
    }

    /**
     * Check if a session is healthy (no critical symptoms detected).
     *
     * <p>Runs the Monitor phase only (no recovery). Returns true if all
     * detected symptoms have severity less than CRITICAL.
     *
     * <p>This is a lightweight health check suitable for periodic polling.
     *
     * @param session the wizard session to check
     * @return true if session is healthy (no CRITICAL symptoms), false otherwise
     * @throws NullPointerException if session is null
     */
    public boolean isHealthy(WizardSession session) {
        Objects.requireNonNull(session, "session must not be null");

        List<WizardSymptom> symptoms = monitor.examine(session);
        return symptoms.stream()
            .noneMatch(s -> s.severity() == WizardSymptom.Severity.CRITICAL);
    }

    /**
     * Returns the monitor component (exposed for advanced use cases).
     *
     * @return the monitor instance
     */
    public AutonomicMonitor monitor() {
        return monitor;
    }

    /**
     * Returns the analyzer component (exposed for advanced use cases).
     *
     * @return the analyzer instance
     */
    public AutonomicAnalyzer analyzer() {
        return analyzer;
    }

    /**
     * Returns the planner component (exposed for advanced use cases).
     *
     * @return the planner instance
     */
    public AutonomicPlanner planner() {
        return planner;
    }

    /**
     * Returns the executor component (exposed for advanced use cases).
     *
     * @return the executor instance
     */
    public AutonomicExecutor executor() {
        return executor;
    }

    /**
     * Returns the knowledge base (exposed for advanced use cases).
     *
     * @return the knowledge base instance
     */
    public KnowledgeBase knowledgeBase() {
        return knowledgeBase;
    }
}
