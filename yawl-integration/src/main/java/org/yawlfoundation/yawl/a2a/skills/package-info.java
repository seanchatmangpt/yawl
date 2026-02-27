/**
 * A2A Skills for YAWL Self-Upgrading Codebase.
 *
 * <p>This package contains skills that enable autonomous code improvement
 * through the A2A (Agent-to-Agent) protocol. Skills allow external agents
 * to introspect, generate, build, test, and commit code changes.
 *
 * <p><b>Self-Upgrade Skills:</b>
 * <ul>
 *   <li>{@link IntrospectCodebaseSkill} - Query Observatory facts (100x context compression)</li>
 *   <li>{@link GenerateCodeSkill} - Z.AI-powered code generation</li>
 *   <li>{@link ExecuteBuildSkill} - Maven build execution</li>
 *   <li>{@link RunTestsSkill} - JUnit test execution</li>
 *   <li>{@link CommitChangesSkill} - Git operations with safety guards</li>
 *   <li>{@link SelfUpgradeSkill} - Master orchestrator for full upgrade cycle</li>
 * </ul>
 *
 * <p><b>Security Model:</b>
 * All skills require specific permissions in {@link AuthenticatedPrincipal}:
 * <ul>
 *   <li>{@code code:read} - Read codebase via Observatory</li>
 *   <li>{@code code:write} - Generate/modify code</li>
 *   <li>{@code build:execute} - Run Maven builds</li>
 *   <li>{@code test:execute} - Run test suites</li>
 *   <li>{@code git:commit} - Commit changes</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
 * @see org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal
 */
package org.yawlfoundation.yawl.integration.a2a.skills;
