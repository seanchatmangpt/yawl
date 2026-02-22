/**
 * Van der Aalst workflow pattern catalog and advisor for the Autonomic A2A/MCP Wizard.
 *
 * <p>This package implements all 20 fundamental workflow control flow patterns from:
 * <blockquote>
 * van der Aalst, W.M.P., ter Hofstede, A.H.M., Kiepuszewski, B., Barros, A.P. (2003).
 * "Workflow Patterns." Distributed and Parallel Databases, 14, 5–51.
 * </blockquote>
 *
 * <p><strong>Core Classes:</strong>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPattern} — Enum of 20 patterns with metadata</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.patterns.PatternCategory} — Grouping of patterns into 6 categories</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.patterns.PatternStructure} — Formal Petri net representation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPatternCatalog} — Lookup and filtering utilities</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.patterns.PatternAdvisor} — Autonomic pattern recommendation engine</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.wizard.patterns.PatternSelectionStep} — Wizard step for pattern selection</li>
 * </ul>
 *
 * <p><strong>Workflow Patterns Included:</strong>
 *
 * <p><em>Basic Control Flow Patterns (WP-1 to WP-5):</em>
 * <ul>
 *   <li>WP-1: Sequence</li>
 *   <li>WP-2: Parallel Split (AND-split)</li>
 *   <li>WP-3: Synchronization (AND-join)</li>
 *   <li>WP-4: Exclusive Choice (XOR-split)</li>
 *   <li>WP-5: Simple Merge (XOR-join)</li>
 * </ul>
 *
 * <p><em>Advanced Branching and Synchronization (WP-6 to WP-9):</em>
 * <ul>
 *   <li>WP-6: Multi-Choice (OR-split)</li>
 *   <li>WP-7: Structured Synchronizing Merge</li>
 *   <li>WP-8: Multi-Merge</li>
 *   <li>WP-9: Structured Discriminator</li>
 * </ul>
 *
 * <p><em>Structural Patterns (WP-10 to WP-11):</em>
 * <ul>
 *   <li>WP-10: Arbitrary Cycles</li>
 *   <li>WP-11: Implicit Termination</li>
 * </ul>
 *
 * <p><em>Multiple Instance Patterns (WP-12 to WP-15):</em>
 * <ul>
 *   <li>WP-12: Multiple Instances Without Synchronization</li>
 *   <li>WP-13: Multiple Instances with A Priori Design-Time Knowledge</li>
 *   <li>WP-14: Multiple Instances with A Priori Runtime Knowledge</li>
 *   <li>WP-15: Multiple Instances Without A Priori Runtime Knowledge</li>
 * </ul>
 *
 * <p><em>State-Based Patterns (WP-16 to WP-18):</em>
 * <ul>
 *   <li>WP-16: Deferred Choice</li>
 *   <li>WP-17: Interleaved Parallel Routing</li>
 *   <li>WP-18: Milestone</li>
 * </ul>
 *
 * <p><em>Cancellation Patterns (WP-19 to WP-20):</em>
 * <ul>
 *   <li>WP-19: Cancel Task</li>
 *   <li>WP-20: Cancel Case</li>
 * </ul>
 *
 * <p><strong>Integration with Autonomic Wizard:</strong>
 *
 * <p>The {@link org.yawlfoundation.yawl.integration.wizard.patterns.PatternSelectionStep}
 * implements the {@link org.yawlfoundation.yawl.integration.wizard.core.WizardStep} interface,
 * enabling pattern selection as a built-in wizard phase.
 *
 * <p>Pattern selection workflow:
 * <ol>
 *   <li>Wizard discovers available MCP tools and A2A agents</li>
 *   <li>PatternAdvisor recommends suitable patterns based on configuration</li>
 *   <li>PatternSelectionStep executes, selecting the best pattern</li>
 *   <li>PatternStructure is built for formal analysis and soundness verification</li>
 *   <li>Downstream steps use the pattern structure to configure MCP/A2A bindings</li>
 * </ol>
 *
 * <p><strong>Petri Net Soundness:</strong>
 *
 * <p>Each pattern's {@link org.yawlfoundation.yawl.integration.wizard.patterns.PatternStructure}
 * includes formal properties:
 * <ul>
 *   <li><strong>Free-choice:</strong> Whether the net is free-choice (simplifies analysis)</li>
 *   <li><strong>Workflow net:</strong> Whether the net is a proper workflow net (single source/sink)</li>
 *   <li><strong>Sound:</strong> Whether the net satisfies van der Aalst's soundness criterion (free from deadlock, livelock, improper termination)</li>
 * </ul>
 *
 * <p><strong>Pattern Suitability Scores:</strong>
 *
 * <p>Each pattern is rated on two axes:
 * <ul>
 *   <li><strong>MCP Suitability (0-10):</strong> How well the pattern suits MCP tool orchestration</li>
 *   <li><strong>A2A Suitability (0-10):</strong> How well the pattern suits A2A agent coordination</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * // Get all patterns
 * List&lt;WorkflowPattern&gt; allPatterns = WorkflowPatternCatalog.all();
 *
 * // Get recommendations for 3 MCP tools and 2 A2A agents
 * List&lt;WorkflowPattern&gt; recommended = PatternAdvisor.recommend(3, 2, List.of("parallel", "cancellation"));
 *
 * // Select a pattern
 * WorkflowPattern selected = recommended.get(0);
 *
 * // Build its Petri net structure
 * PatternStructure structure = PatternStructure.forPattern(selected);
 *
 * // Verify soundness
 * if (structure.isSound()) {
 *     System.out.println("Pattern is sound!");
 * }
 *
 * // Get explanation
 * String explanation = PatternAdvisor.explainRecommendation(selected, 3, 2);
 * System.out.println(explanation);
 * </pre>
 *
 * @see org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPattern
 * @see org.yawlfoundation.yawl.integration.wizard.patterns.PatternAdvisor
 * @see org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPatternCatalog
 * @see org.yawlfoundation.yawl.integration.wizard.patterns.PatternStructure
 */
package org.yawlfoundation.yawl.integration.wizard.patterns;
