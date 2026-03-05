package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.yawlfoundation.yawl.integration.wizard.core.WizardEvent;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStep;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Wizard step for selecting van der Aalst workflow patterns.
 *
 * <p>This step:
 * <ul>
 *   <li>Reads discovered MCP tools and A2A agents from session context</li>
 *   <li>Uses PatternAdvisor to recommend suitable workflow patterns</li>
 *   <li>Selects the best pattern based on configuration</li>
 *   <li>Builds the Petri net structure for the selected pattern</li>
 *   <li>Stores the selection in session context for downstream steps</li>
 * </ul>
 *
 * <p>Session context keys consumed:
 * <ul>
 *   <li>"mcp.tool.count" (Integer) - number of discovered MCP tools (optional, default 1)</li>
 *   <li>"a2a.agent.count" (Integer) - number of discovered A2A agents (optional, default 1)</li>
 *   <li>"workflow.requirements" (List{@code <String>}) - user-specified requirements (optional)</li>
 *   <li>"workflow.pattern.override" (String) - explicit pattern code to use, bypasses recommendation (optional)</li>
 * </ul>
 *
 * <p>Session context keys produced:
 * <ul>
 *   <li>"workflow.pattern" (WorkflowPattern) - the selected pattern</li>
 *   <li>"workflow.pattern.structure" (PatternStructure) - Petri net structure</li>
 *   <li>"workflow.pattern.recommendations" (List{@code <WorkflowPattern>}) - all recommendations</li>
 *   <li>"workflow.pattern.score" (Integer) - suitability score (0-100)</li>
 * </ul>
 *
 * @see PatternAdvisor for pattern recommendation logic
 * @see WorkflowPatternCatalog for pattern lookup
 * @see PatternStructure for Petri net structures
 */
public class PatternSelectionStep implements WizardStep<WorkflowPattern> {

    @Override
    public String stepId() {
        return "workflow-pattern-selection";
    }

    @Override
    public String title() {
        return "Workflow Pattern Selection";
    }

    @Override
    public String description() {
        return "Select a van der Aalst workflow pattern based on discovered MCP tools and A2A agents. "
            + "Patterns define the control flow structure of the autonomic workflow.";
    }

    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.PATTERN_SELECTION;
    }

    @Override
    public List<String> validatePrerequisites(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        List<String> errors = new ArrayList<>();

        // Must be in PATTERN_SELECTION phase
        if (session.currentPhase() != WizardPhase.PATTERN_SELECTION) {
            errors.add(
                String.format(
                    "Step requires PATTERN_SELECTION phase, but session is in %s",
                    session.currentPhase()
                )
            );
        }

        // Optional: MCP tool count should be non-negative if present
        if (session.has("mcp.tool.count")) {
            try {
                Integer count = session.get("mcp.tool.count", Integer.class)
                    .orElse(1);
                if (count < 0) {
                    errors.add("mcp.tool.count cannot be negative");
                }
            } catch (ClassCastException e) {
                errors.add(
                    String.format("mcp.tool.count must be an integer, but found: %s",
                        session.context().get("mcp.tool.count").getClass().getName())
                );
            }
        }

        // Optional: A2A agent count should be non-negative if present
        if (session.has("a2a.agent.count")) {
            try {
                Integer count = session.get("a2a.agent.count", Integer.class)
                    .orElse(1);
                if (count < 0) {
                    errors.add("a2a.agent.count cannot be negative");
                }
            } catch (ClassCastException e) {
                errors.add(
                    String.format("a2a.agent.count must be an integer, but found: %s",
                        session.context().get("a2a.agent.count").getClass().getName())
                );
            }
        }

        // Optional: workflow.requirements should be a list if present
        if (session.has("workflow.requirements")) {
            Object req = session.context().get("workflow.requirements");
            if (!(req instanceof List)) {
                errors.add(
                    String.format("workflow.requirements must be a List, but found: %s",
                        req.getClass().getName())
                );
            }
        }

        return errors;
    }

    @Override
    public WizardStepResult<WorkflowPattern> execute(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        try {
            // Extract configuration from session
            int mcpToolCount = session.get("mcp.tool.count", Integer.class)
                .orElse(1);
            int a2aAgentCount = session.get("a2a.agent.count", Integer.class)
                .orElse(1);

            @SuppressWarnings("unchecked")
            List<String> requirements = (List<String>) session.context()
                .getOrDefault("workflow.requirements", List.of());

            // Check for explicit pattern override
            Optional<String> patternOverride = session.get("workflow.pattern.override", String.class);
            WorkflowPattern selectedPattern;

            if (patternOverride.isPresent()) {
                // User specified a pattern code explicitly
                String code = patternOverride.get();
                Optional<WorkflowPattern> pattern = WorkflowPattern.forCode(code);
                if (pattern.isEmpty()) {
                    return WizardStepResult.failure(
                        stepId(),
                        List.of(String.format("Invalid pattern code: %s. Must be one of WP-1 to WP-20", code))
                    );
                }
                selectedPattern = pattern.get();
            } else {
                // Use advisor to get recommendations
                List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
                    mcpToolCount,
                    a2aAgentCount,
                    requirements
                );

                if (recommendations.isEmpty()) {
                    return WizardStepResult.failure(
                        stepId(),
                        List.of("PatternAdvisor returned no recommendations (this should not happen)")
                    );
                }

                // Select the first (highest-priority) recommendation
                selectedPattern = recommendations.get(0);

                // Store all recommendations for user reference
                session = session.withContext("workflow.pattern.recommendations", recommendations);
            }

            // Build Petri net structure for the selected pattern
            PatternStructure structure = PatternStructure.forPattern(selectedPattern);

            // Score the pattern for the given context
            int score = PatternAdvisor.scorePattern(
                selectedPattern,
                Map.of(
                    "mcp.tool.count", mcpToolCount,
                    "a2a.agent.count", a2aAgentCount,
                    "requirements", requirements
                )
            );

            // Store results in session context
            session = session
                .withContext("workflow.pattern", selectedPattern)
                .withContext("workflow.pattern.structure", structure)
                .withContext("workflow.pattern.score", score);

            // Record event
            WizardEvent event = WizardEvent.of(
                session.currentPhase(),
                stepId(),
                String.format(
                    "Selected pattern: %s (%s) with suitability score %d/100",
                    selectedPattern.getLabel(),
                    selectedPattern.getCode(),
                    score
                ),
                Map.of(
                    "pattern.code", selectedPattern.getCode(),
                    "pattern.label", selectedPattern.getLabel(),
                    "pattern.category", selectedPattern.getCategory().name(),
                    "score", score,
                    "mcp.suitability", selectedPattern.getMcpSuitability(),
                    "a2a.suitability", selectedPattern.getA2aSuitability()
                )
            );
            session = session.recordEvent(event);

            return WizardStepResult.success(stepId(), selectedPattern);
        } catch (Exception e) {
            return WizardStepResult.failure(
                stepId(),
                List.of(String.format("Pattern selection failed: %s", e.getMessage()))
            );
        }
    }

    @Override
    public boolean isSkippable() {
        // This step can be skipped if pattern is already selected
        return true;
    }
}
