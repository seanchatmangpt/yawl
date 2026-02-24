/**
 * Conversational workflow factory: NL to live workflows in <30s with closed-loop conformance improvement.
 *
 * <p>This package provides {@link org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory}
 * which bridges natural language descriptions to live YAWL workflows with continuous conformance monitoring.
 * Generated specifications are validated and deployed to the engine automatically, then assessed via
 * process mining to identify opportunities for refinement.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><b>ConversationalWorkflowFactory</b> - Orchestrates generation, validation, deployment, and conformance monitoring</li>
 *   <li><b>FactoryResult</b> - Sealed interface representing outcomes (Deployed, ValidationFailed, Refined)</li>
 *   <li><b>WorkflowHealth</b> - Record for workflow conformance assessment</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 *   try (var factory = new ConversationalWorkflowFactory(
 *       specGenerator, interfaceAClient, interfaceBClient, processMining, sessionHandle)) {
 *
 *       FactoryResult result = factory.generateAndDeploy(
 *           "Create a procurement workflow with request approval and fulfillment steps");
 *
 *       if (result instanceof FactoryResult.Deployed deployed) {
 *           System.out.println("Spec: " + deployed.specId());
 *           System.out.println("Case: " + deployed.caseId());
 *       }
 *   }
 * </pre>
 *
 * <h2>Conformance Monitoring</h2>
 * <p>After deployment, the factory schedules periodic conformance assessments (every 60s) once 10+ executions
 * have been recorded. Conformance scores below 0.90 trigger a refinement recommendation via {@link #getHealth(String)}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.factory;
