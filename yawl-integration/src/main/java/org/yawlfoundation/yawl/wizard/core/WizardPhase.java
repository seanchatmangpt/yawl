package org.yawlfoundation.yawl.integration.wizard.core;

/**
 * Phases of the Autonomic A2A/MCP Wizard lifecycle.
 * Follows van der Aalst's workflow lifecycle: specification → instantiation → execution → completion.
 *
 * <p>The wizard progresses through phases in a strictly ordered manner:
 * <ul>
 *   <li>INIT: Wizard just created, ready to begin discovery</li>
 *   <li>DISCOVERY: Discovering available MCP tools and A2A agents</li>
 *   <li>PATTERN_SELECTION: User selects van der Aalst workflow pattern</li>
 *   <li>MCP_CONFIG: Configuring MCP tool bindings to workflow pattern</li>
 *   <li>A2A_CONFIG: Configuring A2A agent skills and handoff routes</li>
 *   <li>VALIDATION: Validating complete configuration against Petri net soundness</li>
 *   <li>DEPLOYMENT: Deploying configuration to runtime</li>
 *   <li>COMPLETE: Successfully finished, configuration deployed</li>
 *   <li>FAILED: Terminal error state, wizard cannot recover</li>
 * </ul>
 *
 * @see org.yawlfoundation.yawl.integration.wizard.core.WizardSession
 */
public enum WizardPhase {
    /**
     * Initial phase: wizard session created, ready to discover resources.
     */
    INIT,

    /**
     * Discovery phase: scanning available MCP tools and A2A agent capabilities.
     */
    DISCOVERY,

    /**
     * Pattern selection phase: user chooses van der Aalst workflow pattern (Sequence, Choice, etc).
     */
    PATTERN_SELECTION,

    /**
     * MCP configuration phase: binding MCP tools to workflow tasks.
     */
    MCP_CONFIG,

    /**
     * A2A configuration phase: defining agent skills and task handoffs.
     */
    A2A_CONFIG,

    /**
     * Validation phase: checking Petri net soundness and configuration consistency.
     */
    VALIDATION,

    /**
     * Deployment phase: applying configuration to running system.
     */
    DEPLOYMENT,

    /**
     * Terminal success: wizard completed successfully.
     */
    COMPLETE,

    /**
     * Terminal error: wizard encountered unrecoverable error.
     */
    FAILED
}
