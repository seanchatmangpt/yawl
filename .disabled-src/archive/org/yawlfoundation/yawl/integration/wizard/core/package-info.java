/**
 * Core wizard framework for the Autonomic A2A/MCP Wizard.
 *
 * <p>The wizard guides operators through configuring Agent-to-Agent (A2A) and
 * Model Context Protocol (MCP) integrations using van der Aalst's formal workflow patterns.
 * It implements a structured MAPE-K (Monitor-Analyze-Plan-Execute over a Knowledge base)
 * autonomic computing loop with Petri net-validated state transitions.
 *
 * <h2>Architecture Overview</h2>
 *
 * <p>The wizard consists of:
 * <ul>
 *   <li><strong>Phases</strong> ({@link WizardPhase}): INIT → DISCOVERY → PATTERN_SELECTION →
 *       MCP_CONFIG → A2A_CONFIG → VALIDATION → DEPLOYMENT → COMPLETE|FAILED</li>
 *   <li><strong>Steps</strong> ({@link WizardStep}): Individual units of work executed within a phase.
 *       Each step transforms a {@link WizardSession} and produces a typed result.</li>
 *   <li><strong>Sessions</strong> ({@link WizardSession}): Immutable state carriers holding context,
 *       audit trail, and configuration accumulated during wizard execution.</li>
 *   <li><strong>Engine</strong> ({@link AutonomicWizardEngine}): The orchestrator that validates
 *       phases, executes steps, and coordinates transitions.</li>
 * </ul>
 *
 * <h2>Design Patterns</h2>
 *
 * <p><strong>Immutability</strong>: All types are immutable (Java 25 records or functional builders).
 * State transitions create new instances, enabling auditability and rollback.
 *
 * <p><strong>Functional Composition</strong>: Steps are composable functions mapping
 * WizardSession → WizardStepResult<T>. Results can be chained to build complex workflows.
 *
 * <p><strong>Audit Trail</strong>: Every action is recorded in {@link WizardEvent} chronologically.
 * The complete trace enables replay, debugging, and compliance reporting.
 *
 * <p><strong>van der Aalst Patterns</strong>: The wizard's phase progression implements
 * <ul>
 *   <li>WP-1 (Sequence): Steps execute in prescribed order</li>
 *   <li>WP-4 (Exclusive Choice): Phase transitions are conditional on step success</li>
 *   <li>WP-7 (Structured Synchronization): Configuration validation ensures consistency</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 *
 * <pre>
 * // Create engine and session
 * AutonomicWizardEngine engine = new AutonomicWizardEngine();
 * WizardSession session = engine.initSession();
 *
 * // Execute discovery step
 * WizardStep<Map> discoveryStep = new DiscoveryStep();
 * WizardStepResult<Map> discResult = engine.executeStep(session, discoveryStep);
 *
 * if (discResult.isSuccess()) {
 *     // Advance to next phase
 *     session = engine.advance(session, discResult, WizardPhase.PATTERN_SELECTION);
 *
 *     // Execute pattern selection step
 *     WizardStep<String> patternStep = new PatternSelectionStep();
 *     WizardStepResult<String> patResult = engine.executeStep(session, patternStep);
 *
 *     if (patResult.isSuccess()) {
 *         session = engine.advance(session, patResult, WizardPhase.MCP_CONFIG);
 *         // ... continue through remaining phases
 *     }
 * }
 *
 * // Get final result
 * WizardResult result = engine.complete(session);
 * if (result.isSuccess()) {
 *     Map<String, Object> mcpConfig = result.mcpConfiguration();
 *     // ... deploy configuration
 * } else {
 *     System.err.println("Wizard failed: " + result.errorSummary());
 * }
 * </pre>
 *
 * <h2>Extension Points</h2>
 *
 * <p>Subpackages provide specialized step implementations:
 * <ul>
 *   <li>wizard.discovery: Discovers MCP tools and A2A agent capabilities</li>
 *   <li>wizard.patterns: Implements van der Aalst pattern selection</li>
 *   <li>wizard.mcp: Binds MCP tools to workflow tasks</li>
 *   <li>wizard.a2a: Configures agent skills and handoff routes</li>
 *   <li>wizard.validation: Validates Petri net soundness and consistency</li>
 *   <li>wizard.deployment: Deploys configuration to runtime</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.integration.wizard.core.AutonomicWizardEngine
 * @see org.yawlfoundation.yawl.integration.wizard.core.WizardSession
 * @see org.yawlfoundation.yawl.integration.wizard.core.WizardStep
 * @see org.yawlfoundation.yawl.integration.wizard.core.WizardPhase
 */
package org.yawlfoundation.yawl.integration.wizard.core;
