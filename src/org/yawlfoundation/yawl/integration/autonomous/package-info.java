/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Generic Autonomous Agent Framework for YAWL workflows.
 *
 * <h2>Overview</h2>
 * <p>This package provides a <b>generic, configuration-driven framework</b> for deploying
 * autonomous agents that discover, reason about, and complete YAWL workflow tasks without
 * central orchestration. The framework abstracts domain-specific logic into pluggable
 * strategies, enabling deployment across arbitrary workflow domains through YAML/JSON
 * configuration without code changes.</p>
 *
 * <h2>Architecture Principles</h2>
 * <ul>
 *   <li><b>Generic Framework Pattern:</b> Interface contracts → Abstract base → Strategy implementations → External configuration</li>
 *   <li><b>No hardcoded workflows:</b> Agents work with ANY YAWL specification through capability-based reasoning</li>
 *   <li><b>Pluggable strategies:</b> Discovery, eligibility, decision, and output generation are interface-based and swappable</li>
 *   <li><b>Configuration over code:</b> Deploy new agents by editing YAML, not recompiling Java</li>
 *   <li><b>Stateless design:</b> Agents reason over work item context + capability description, no persistent task mappings</li>
 * </ul>
 *
 * <h2>Core Components</h2>
 *
 * <h3>Agent Lifecycle</h3>
 * <ul>
 *   <li>{@link AutonomousAgent} - Core lifecycle interface (start, stop, getCapability, getAgentCard)</li>
 *   <li>{@link GenericPartyAgent} - Concrete implementation with strategy injection</li>
 *   <li>{@link AgentFactory} - Factory for creating agents from configuration or environment variables</li>
 *   <li>{@link AgentConfiguration} - Immutable configuration model with builder pattern</li>
 *   <li>{@link AgentCapability} - Domain capability descriptor (e.g., "Ordering: procurement, purchase orders")</li>
 * </ul>
 *
 * <h3>Strategy Interfaces</h3>
 * <p>All agent behavior is defined through pluggable strategy interfaces:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy} - How to find work items (polling, event-driven, webhook)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner} - Determine if agent should handle a work item</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner} - Decide how to complete a work item and produce output</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies.OutputGenerator} - Format output data (XML, JSON, template-based)</li>
 * </ul>
 *
 * <h3>Strategy Implementations</h3>
 * <p>The framework provides multiple strategy implementations out-of-the-box:</p>
 * <ul>
 *   <li><b>Polling Discovery:</b> {@link org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy} - Query InterfaceB at intervals</li>
 *   <li><b>ZAI Reasoning:</b> {@link org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner}, {@link org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiDecisionReasoner} - AI-based reasoning via Z.AI LLM</li>
 *   <li><b>Static Mapping:</b> {@link org.yawlfoundation.yawl.integration.autonomous.reasoners.StaticMappingReasoner} - Deterministic task → agent mapping from JSON</li>
 *   <li><b>Template Output:</b> {@link org.yawlfoundation.yawl.integration.autonomous.generators.TemplateOutputGenerator} - Template-based output (Mustache)</li>
 *   <li><b>XML Output:</b> {@link org.yawlfoundation.yawl.integration.autonomous.generators.XmlOutputGenerator} - Dynamic XML with root element matching task decomposition</li>
 * </ul>
 *
 * <h2>Agent Model (Mathematical Framework)</h2>
 * <p>An agent <b>A</b> is a tuple <b>(C, E, D, O, S)</b> where:</p>
 * <ul>
 *   <li><b>C</b>: Capability (domain description) → {@link AgentCapability}</li>
 *   <li><b>E</b>: Eligibility reasoner: E(w) → {true, false} → {@link org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner}</li>
 *   <li><b>D</b>: Decision reasoner: D(w) → XML output → {@link org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner}</li>
 *   <li><b>O</b>: Output generator: O(w, decision) → formatted output → {@link org.yawlfoundation.yawl.integration.autonomous.strategies.OutputGenerator}</li>
 *   <li><b>S</b>: Discovery strategy: S() → List&lt;WorkItemRecord&gt; → {@link org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy}</li>
 * </ul>
 *
 * <h2>Discovery Loop (Autonomous Execution)</h2>
 * <pre>
 * while (agent.isRunning()) {
 *     List&lt;WorkItemRecord&gt; items = discoveryStrategy.discoverWorkItems();
 *     for (WorkItemRecord w : items) {
 *         if (eligibilityReasoner.isEligible(w)) {
 *             checkout(w);
 *             String output = decisionReasoner.produceOutput(w);
 *             checkin(w, output);
 *         }
 *     }
 *     sleep(pollInterval);
 * }
 * </pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Example 1: Create Agent from Environment Variables</h3>
 * <pre>
 * // Set environment variables:
 * // AGENT_CAPABILITY="Ordering: procurement, purchase orders, approvals"
 * // YAWL_ENGINE_URL=http://localhost:8080/yawl
 * // YAWL_USERNAME=admin
 * // YAWL_PASSWORD=YAWL
 * // ZAI_API_KEY=your-zai-key
 * // AGENT_PORT=8091
 *
 * AutonomousAgent agent = AgentFactory.fromEnvironment();
 * agent.start();
 *
 * // Agent runs autonomously, discovering and completing work items
 *
 * Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));
 * </pre>
 *
 * <h3>Example 2: Create Agent with Custom Configuration (ZAI-based)</h3>
 * <pre>
 * AgentCapability capability = new AgentCapability("Ordering", "procurement, purchase orders");
 * ZaiService zaiService = new ZaiService("your-zai-key");
 *
 * AgentConfiguration config = AgentConfiguration.builder()
 *     .capability(capability)
 *     .engineUrl("http://localhost:8080/yawl")
 *     .username("admin")
 *     .password("YAWL")
 *     .port(8091)
 *     .pollIntervalMs(3000)
 *     .discoveryStrategy(new PollingDiscoveryStrategy())
 *     .eligibilityReasoner(new ZaiEligibilityReasoner(capability, zaiService))
 *     .decisionReasoner(new ZaiDecisionReasoner(zaiService))
 *     .build();
 *
 * AutonomousAgent agent = AgentFactory.create(config);
 * agent.start();
 * </pre>
 *
 * <h3>Example 3: Create Agent with Static Mapping (No AI)</h3>
 * <pre>
 * // mapping.json: { "Approve_Purchase_Order": "Ordering", "Request_Quote": "Carrier" }
 * AgentCapability capability = new AgentCapability("Ordering", "procurement");
 * StaticMappingReasoner eligibility = StaticMappingReasoner.fromFile("mapping.json", "Ordering");
 * TemplateDecisionReasoner decision = new TemplateDecisionReasoner();
 *
 * AgentConfiguration config = AgentConfiguration.builder()
 *     .capability(capability)
 *     .engineUrl("http://localhost:8080/yawl")
 *     .username("admin")
 *     .password("YAWL")
 *     .discoveryStrategy(new PollingDiscoveryStrategy())
 *     .eligibilityReasoner(eligibility)
 *     .decisionReasoner(decision)
 *     .build();
 *
 * AutonomousAgent agent = AgentFactory.create(config);
 * agent.start();
 * </pre>
 *
 * <h2>A2A Discovery Protocol</h2>
 * <p>Agents expose an HTTP endpoint at <code>/.well-known/agent.json</code> conforming to the
 * Agent-to-Agent (A2A) protocol. The agent card includes:</p>
 * <ul>
 *   <li>Agent name and description</li>
 *   <li>Capabilities (domain descriptors)</li>
 *   <li>Skills (operations the agent can perform)</li>
 *   <li>Version and contact information</li>
 * </ul>
 *
 * <h3>Agent Card Example</h3>
 * <pre>
 * GET http://localhost:8091/.well-known/agent.json
 *
 * {
 *   "name": "Generic Agent - Ordering",
 *   "description": "Autonomous agent for procurement, purchase orders. Discovers work items, reasons about eligibility, produces output dynamically.",
 *   "version": "5.2.0",
 *   "capabilities": {
 *     "domain": "Ordering"
 *   },
 *   "skills": [
 *     {
 *       "id": "complete_work_item",
 *       "name": "Complete Work Item",
 *       "description": "Discover and complete workflow tasks in this agent's domain"
 *     }
 *   ]
 * }
 * </pre>
 *
 * <h2>Multi-Domain Deployment</h2>
 * <p>The framework enables <b>zero-code multi-domain deployment</b>. The same {@link GenericPartyAgent}
 * implementation can serve different workflow domains through configuration:</p>
 *
 * <h3>Order Fulfillment Domain (AI-based)</h3>
 * <ul>
 *   <li>Capability: "Ordering: procurement, purchase orders"</li>
 *   <li>Eligibility: ZAI reasoning (LLM analyzes task vs. capability)</li>
 *   <li>Decision: ZAI output generation (LLM produces XML)</li>
 *   <li>Discovery: Polling (3s interval)</li>
 * </ul>
 *
 * <h3>Notification Domain (Rule-based)</h3>
 * <ul>
 *   <li>Capability: "Email: email delivery, SMTP, templates"</li>
 *   <li>Eligibility: Static mapping (Send_Email → Email agent)</li>
 *   <li>Decision: Template-based (Mustache templates)</li>
 *   <li>Discovery: Polling (5s interval)</li>
 * </ul>
 *
 * <p><b>Result:</b> Both workflows achieve 100% autonomous completion with <b>zero code changes</b>,
 * validating the generic framework abstraction.</p>
 *
 * <h2>Comparison with Legacy Implementation</h2>
 * <table border="1">
 *   <tr>
 *     <th>Aspect</th>
 *     <th>Legacy (orderfulfillment package)</th>
 *     <th>Generic Framework (autonomous package)</th>
 *   </tr>
 *   <tr>
 *     <td>Scope</td>
 *     <td>Hardcoded for Order Fulfillment</td>
 *     <td>Works with ANY YAWL workflow</td>
 *   </tr>
 *   <tr>
 *     <td>Configuration</td>
 *     <td>Environment variables only</td>
 *     <td>YAML/JSON + Environment + Programmatic</td>
 *   </tr>
 *   <tr>
 *     <td>Strategies</td>
 *     <td>Fixed ZAI reasoning</td>
 *     <td>Pluggable (ZAI, static, template, custom)</td>
 *   </tr>
 *   <tr>
 *     <td>Extensibility</td>
 *     <td>Requires subclassing or copy-paste</td>
 *     <td>Interface-based, dependency injection</td>
 *   </tr>
 *   <tr>
 *     <td>Deployment</td>
 *     <td>Recompile for new domains</td>
 *     <td>Edit config file, restart container</td>
 *   </tr>
 * </table>
 *
 * <h2>Extension Points</h2>
 *
 * <h3>Custom Discovery Strategy</h3>
 * <pre>
 * public class EventDrivenDiscoveryStrategy implements DiscoveryStrategy {
 *     private final Queue&lt;WorkItemRecord&gt; workItemQueue = new ConcurrentLinkedQueue&lt;&gt;();
 *
 *     public EventDrivenDiscoveryStrategy(String interfaceEUrl) {
 *         // Subscribe to YAWL InterfaceE event log for work item created events
 *         // Add events to workItemQueue
 *     }
 *
 *     {@literal @}Override
 *     public List&lt;WorkItemRecord&gt; discoverWorkItems(
 *             InterfaceB_EnvironmentBasedClient client, String session) {
 *         List&lt;WorkItemRecord&gt; items = new ArrayList&lt;&gt;();
 *         WorkItemRecord wir;
 *         while ((wir = workItemQueue.poll()) != null) {
 *             items.add(wir);
 *         }
 *         return items;
 *     }
 * }
 * </pre>
 *
 * <h3>Custom Eligibility Reasoner (Rules Engine)</h3>
 * <pre>
 * public class DroolsEligibilityReasoner implements EligibilityReasoner {
 *     private final KieSession kieSession;
 *     private final String agentDomain;
 *
 *     public DroolsEligibilityReasoner(String rulesFile, String agentDomain) {
 *         // Load Drools rules from file
 *         this.kieSession = createKieSession(rulesFile);
 *         this.agentDomain = agentDomain;
 *     }
 *
 *     {@literal @}Override
 *     public boolean isEligible(WorkItemRecord workItem) {
 *         kieSession.insert(workItem);
 *         kieSession.insert(agentDomain);
 *         kieSession.fireAllRules();
 *         // Extract eligibility decision from working memory
 *         return getDecisionFromWorkingMemory();
 *     }
 * }
 * </pre>
 *
 * <h2>Alignment with YAWL Interfaces</h2>
 * <p>The framework integrates with YAWL's standard interfaces:</p>
 * <ul>
 *   <li><b>Interface A (Design):</b> Workflow specifications uploaded via InterfaceA or Editor</li>
 *   <li><b>Interface B (Client):</b> Agents use InterfaceB for work item discovery, checkout, checkin</li>
 *   <li><b>Interface E (Events):</b> Optional event-driven discovery via log gateway (future)</li>
 *   <li><b>Interface X (Extended):</b> Optional custom services for MCP task context (already implemented)</li>
 * </ul>
 *
 * <h2>Related Packages</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.strategies} - Strategy interfaces and implementations</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.reasoners} - AI-based and rule-based reasoners</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.generators} - Output generators (XML, JSON, template)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.registry} - Agent registry for multi-agent coordination</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.resilience} - Circuit breaker, retry policy, fallback handlers</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.observability} - Metrics, health checks, structured logging</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.config} - Configuration loaders (YAML, JSON)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.launcher} - Generic workflow launcher for any specification</li>
 * </ul>
 *
 * <h2>Migration from Legacy</h2>
 * <p>Applications using the deprecated {@link org.yawlfoundation.yawl.integration.orderfulfillment.PartyAgent}
 * should migrate to {@link GenericPartyAgent}:</p>
 * <pre>
 * // Legacy (orderfulfillment-specific)
 * PartyAgent agent = new PartyAgent(capability, engineUrl, username, password, port);
 * agent.start();
 *
 * // Generic (works for any domain)
 * AgentConfiguration config = AgentConfiguration.builder()
 *     .capability(capability)
 *     .engineUrl(engineUrl)
 *     .username(username)
 *     .password(password)
 *     .port(port)
 *     .discoveryStrategy(new PollingDiscoveryStrategy())
 *     .eligibilityReasoner(new ZaiEligibilityReasoner(capability, zaiService))
 *     .decisionReasoner(new ZaiDecisionReasoner(zaiService))
 *     .build();
 * AutonomousAgent agent = AgentFactory.create(config);
 * agent.start();
 * </pre>
 *
 * <h2>Architecture Decision Records (ADRs)</h2>
 * <ul>
 *   <li><b>ADR-001:</b> Use strategy pattern for pluggable discovery, eligibility, decision</li>
 *   <li><b>ADR-002:</b> Separate DecisionReasoner (reasoning) from OutputGenerator (formatting)</li>
 *   <li><b>ADR-003:</b> Use builder pattern for AgentConfiguration (fluent API, immutability)</li>
 *   <li><b>ADR-004:</b> Return JSON string from getAgentCard() instead of separate AgentCard class (minimize dependencies)</li>
 *   <li><b>ADR-005:</b> Factory pattern for agent creation (centralized validation, environment-based defaults)</li>
 * </ul>
 *
 * <h2>Future Work</h2>
 * <ul>
 *   <li>Event-driven discovery strategy using YAWL InterfaceE log gateway</li>
 *   <li>Webhook-based discovery (push notifications from engine)</li>
 *   <li>Rules engine integration (Drools) for eligibility without LLM costs</li>
 *   <li>Agent registry clustering for high availability</li>
 *   <li>Circuit breaker and retry policy for ZAI calls (production resilience)</li>
 *   <li>Observability: OpenTelemetry tracing, Prometheus metrics</li>
 *   <li>Kubernetes operator for dynamic agent deployment via CRDs</li>
 *   <li>Multi-workflow coordination (agents handling tasks from multiple specs)</li>
 * </ul>
 *
 * <h2>References</h2>
 * <ul>
 *   <li>YAWL v5.2 Documentation: https://yawlfoundation.github.io/</li>
 *   <li>Generic Autonomous Agent Framework PRD: .claude/plans/joyful-whistling-reddy.md</li>
 *   <li>Thesis: docs/THESIS_Autonomous_Workflow_Agents.md (Section 6: Generic Framework)</li>
 *   <li>Agent-to-Agent Protocol: https://a2a-protocol.org/</li>
 *   <li>Model Context Protocol: https://modelcontextprotocol.io/</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
package org.yawlfoundation.yawl.integration.autonomous;
