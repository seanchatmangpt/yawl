/**
 * Autonomic Computing Module for the YAWL Autonomic A2A/MCP Wizard.
 *
 * <p><b>IBM MAPE-K Model</b>: This module implements the complete IBM autonomic
 * computing blueprint (2001) as a self-managing layer for the wizard. The MAPE-K
 * acronym stands for:
 *
 * <ul>
 *   <li><b>M (Monitor)</b>: {@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicMonitor}
 *       observes the wizard session state and collects symptoms (raw observations)
 *   <li><b>A (Analyze)</b>: {@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicAnalyzer}
 *       diagnoses root causes by consulting the knowledge base
 *   <li><b>P (Plan)</b>: {@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicPlanner}
 *       sequences recovery actions into a recovery plan
 *   <li><b>E (Execute)</b>: {@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicExecutor}
 *       applies the plan to recover the session
 *   <li><b>K (Knowledge)</b>: {@link org.yawlfoundation.yawl.integration.wizard.autonomic.KnowledgeBase}
 *       accumulates wisdom (rules) about failure modes and remedies
 * </ul>
 *
 * <p><b>Problem Domain</b>: The autonomic system handles these failure modes:
 * <ul>
 *   <li>DISCOVERY_EMPTY: No MCP tools or A2A agents discovered
 *   <li>PHASE_STALLED: Wizard stuck in same phase too long (no progress)
 *   <li>CONFIG_CONFLICT: MCP and A2A configurations incompatible
 *   <li>PATTERN_UNSOUND: Selected Petri net pattern failed soundness validation
 *   <li>VALIDATION_FAILED: Configuration validation failed against constraints
 *   <li>DEPLOYMENT_FAILED: Could not connect to or deploy to runtime engine
 * </ul>
 *
 * <p><b>Core Architecture</b>:
 *
 * <pre>
 *   Wizard Session
 *        ↓
 *   AutonomicWizardManager (orchestrator)
 *        ↓
 *   ┌────────────────────────────────────────┐
 *   │  M: Monitor                            │
 *   │  examine(session) → List[WizardSymptom]│
 *   └────────────────────────────────────────┘
 *        ↓
 *   ┌────────────────────────────────────────┐
 *   │  A: Analyzer                           │
 *   │  diagnose(symptoms) → List[Diagnosis]  │
 *   │  consults KnowledgeBase                │
 *   └────────────────────────────────────────┘
 *        ↓
 *   ┌────────────────────────────────────────┐
 *   │  P: Planner                            │
 *   │  plan(diagnoses) → RecoveryPlan        │
 *   │  sequences actions, deduplicates       │
 *   └────────────────────────────────────────┘
 *        ↓
 *   ┌────────────────────────────────────────┐
 *   │  E: Executor                           │
 *   │  execute(plan) → ExecutionResult       │
 *   │  mutates session state                 │
 *   └────────────────────────────────────────┘
 *        ↓
 *   Recovered WizardSession
 * </pre>
 *
 * <p><b>Usage Pattern</b>:
 *
 * <pre>
 *   // Create autonomic manager with defaults
 *   var manager = AutonomicWizardManager.withDefaults();
 *
 *   // Run the wizard...
 *   var session = AutonomicWizardEngine.initSession();
 *   // ... execute wizard steps ...
 *
 *   // At any point, run self-healing cycle
 *   var healthySession = manager.runLoop(session);
 *
 *   // Or check health without recovery
 *   boolean healthy = manager.isHealthy(session);
 * </pre>
 *
 * <p><b>Integration with Wizard Engine</b>:
 *
 * <p>The autonomic recovery step ({@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicRecoveryStep})
 * can be registered as a WizardStep in the VALIDATION phase. This ensures health
 * checks and self-healing are executed automatically before deployment:
 *
 * <pre>
 *   var engine = AutonomicWizardEngine.newEngine();
 *   engine.register(AutonomicRecoveryStep.withDefaults());
 *   // Now every wizard run includes autonomic recovery during VALIDATION
 * </pre>
 *
 * <p><b>Key Classes</b>:
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.WizardSymptom}:
 *       Raw observation (symptom) detected during monitoring
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.WizardDiagnosis}:
 *       Root cause analysis with confidence score and recommended action
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.KnowledgeBase}:
 *       Mapping from symptom types to recovery actions (the knowledge layer)
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicMonitor}:
 *       Detects symptoms by examining session state
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicAnalyzer}:
 *       Diagnoses root causes using knowledge base rules
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicPlanner}:
 *       Plans recovery actions from diagnoses
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicExecutor}:
 *       Executes the recovery plan on the session
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.autonomic.AutonomicWizardManager}:
 *       Top-level orchestrator (recommended entry point)
 * </ul>
 *
 * <p><b>Design Principles</b>:
 * <ul>
 *   <li>Pure observation: Monitor phase has no side effects</li>
 *   <li>Confidence-based reasoning: All diagnoses include confidence scores (0.0-1.0)</li>
 *   <li>Knowledge-driven: All recovery decisions reference the knowledge base</li>
 *   <li>Stateless components: All classes are stateless and thread-safe</li>
 *   <li>Immutable sessions: WizardSession is immutable; recovery produces new sessions</li>
 *   <li>Minimal plans: Recovery plans are short sequences, avoiding over-correction</li>
 *   <li>Self-healing: Autonomic system acts without human intervention</li>
 * </ul>
 *
 * <p><b>References</b>:
 * <ul>
 *   <li>IBM Autonomic Computing Blueprint (2001):
 *       https://www.ibm.com/cloud/automation/automating-infrastructure/
 *   <li>MAPE-K Loop: Kephart & Chess, "The Vision of Autonomic Computing"
 *   <li>Petri Net Soundness: Wil van der Aalst's workflow patterns and net semantics
 * </ul>
 *
 * @author Agent 5 (Autonomic Module Specialist)
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.wizard.autonomic;
