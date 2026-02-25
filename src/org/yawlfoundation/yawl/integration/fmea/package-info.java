/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * Runtime Failure Mode and Effects Analysis (FMEA) for YAWL v6 users, A2A agents,
 * MCP servers, and end-to-end GCP Marketplace round-trips.
 *
 * <p>Extends the observatory FMEA framework (FM1–FM7, build/infra risks) with
 * four additional FMEA domains covering the full YAWL v6 integration stack.
 *
 * <h2>User Failure Mode Inventory (FM_U1–FM_U7)</h2>
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_U1</td><td>Credential Expiry</td><td>8</td><td>4</td><td>3</td><td>96</td></tr>
 *   <tr><td>FM_U2</td><td>Missing Permission</td><td>7</td><td>5</td><td>2</td><td>70</td></tr>
 *   <tr><td>FM_U3</td><td>Tenant Isolation Breach</td><td>10</td><td>2</td><td>4</td><td>80</td></tr>
 *   <tr><td>FM_U4</td><td>Insufficient Scope</td><td>7</td><td>4</td><td>2</td><td>56</td></tr>
 *   <tr><td>FM_U5</td><td>Admin Scope Elevation</td><td>9</td><td>2</td><td>3</td><td>54</td></tr>
 *   <tr><td>FM_U6</td><td>Resource Over Capacity</td><td>6</td><td>6</td><td>3</td><td>108</td></tr>
 *   <tr><td>FM_U7</td><td>Resource Unavailable</td><td>8</td><td>3</td><td>5</td><td>120</td></tr>
 * </table>
 *
 * <h2>A2A Failure Mode Inventory (FM_A1–FM_A7)</h2>
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_A1</td><td>Agent Credential Expiry</td><td>9</td><td>4</td><td>2</td><td>72</td></tr>
 *   <tr><td>FM_A2</td><td>Missing Skill Permission</td><td>8</td><td>5</td><td>3</td><td>120</td></tr>
 *   <tr><td>FM_A3</td><td>Handoff Token Expiry</td><td>7</td><td>5</td><td>3</td><td>105</td></tr>
 *   <tr><td>FM_A4</td><td>Handoff Self-Reference</td><td>8</td><td>2</td><td>5</td><td>80</td></tr>
 *   <tr><td>FM_A5</td><td>Skill Not Registered</td><td>6</td><td>4</td><td>2</td><td>48</td></tr>
 *   <tr><td>FM_A6</td><td>Insufficient Skill Permission</td><td>7</td><td>4</td><td>3</td><td>84</td></tr>
 *   <tr><td>FM_A7</td><td>No Auth Scheme Configured</td><td>10</td><td>2</td><td>5</td><td>100</td></tr>
 * </table>
 *
 * <h2>MCP Failure Mode Inventory (FM_M1–FM_M7)</h2>
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_M1</td><td>Tool Not Found</td><td>7</td><td>4</td><td>3</td><td>84</td></tr>
 *   <tr><td>FM_M2</td><td>Engine Auth Failure</td><td>9</td><td>4</td><td>2</td><td>72</td></tr>
 *   <tr><td>FM_M3</td><td>Z.AI Service Unavailable</td><td>5</td><td>5</td><td>3</td><td>75</td></tr>
 *   <tr><td>FM_M4</td><td>Missing Environment Variable</td><td>10</td><td>2</td><td>2</td><td>40</td></tr>
 *   <tr><td>FM_M5</td><td>Circuit Breaker Open</td><td>8</td><td>3</td><td>5</td><td>120</td></tr>
 *   <tr><td>FM_M6</td><td>Tool Execution Failure</td><td>7</td><td>5</td><td>4</td><td>140</td></tr>
 *   <tr><td>FM_M7</td><td>No Providers Registered</td><td>10</td><td>2</td><td>3</td><td>60</td></tr>
 * </table>
 *
 * <h2>Marketplace E2E Failure Mode Inventory (FM_E1–FM_E7)</h2>
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_E1</td><td>Event Out of Order</td><td>7</td><td>4</td><td>3</td><td>84</td></tr>
 *   <tr><td>FM_E2</td><td>Duplicate Event</td><td>6</td><td>5</td><td>2</td><td>60</td></tr>
 *   <tr><td>FM_E3</td><td>Unknown Event Type</td><td>7</td><td>3</td><td>2</td><td>42</td></tr>
 *   <tr><td>FM_E4</td><td>Sequence Gap</td><td>8</td><td>3</td><td>4</td><td>96</td></tr>
 *   <tr><td>FM_E5</td><td>Payment Failure</td><td>9</td><td>4</td><td>2</td><td>72</td></tr>
 *   <tr><td>FM_E6</td><td>Vendor Suspended</td><td>8</td><td>2</td><td>4</td><td>64</td></tr>
 *   <tr><td>FM_E7</td><td>Engine Session Expired</td><td>9</td><td>3</td><td>2</td><td>54</td></tr>
 * </table>
 *
 * <h2>RPN Formula</h2>
 * <pre>
 *   RPN = Severity × Occurrence × Detection   (each 1–10, 10 = worst)
 * </pre>
 *
 * <h2>Key Types — User FMEA</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFailureModeType}
 *       — enum of FM_U1–FM_U7 with embedded S/O/D scores</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFmeaViolation}
 *       — record representing one detected user violation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFmeaReport}
 *       — record carrying the user analysis result (GREEN / RED)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFmeaAnalyzer}
 *       — stateless user analyser; call once per request boundary</li>
 * </ul>
 *
 * <h2>Key Types — A2A FMEA</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.A2AFailureModeType}
 *       — enum of FM_A1–FM_A7 with embedded S/O/D scores</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.A2AFmeaViolation}
 *       — record representing one detected A2A violation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.A2AFmeaReport}
 *       — record carrying the A2A analysis result (GREEN / RED)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.A2AFmeaAnalyzer}
 *       — stateless A2A analyser; call once per request boundary</li>
 * </ul>
 *
 * <h2>Key Types — MCP FMEA</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.McpFailureModeType}
 *       — enum of FM_M1–FM_M7 with embedded S/O/D scores</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.McpFmeaViolation}
 *       — record representing one detected MCP violation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.McpFmeaReport}
 *       — record carrying the MCP analysis result (GREEN / RED)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.McpFmeaAnalyzer}
 *       — stateless MCP analyser; call once per server lifecycle boundary</li>
 * </ul>
 *
 * <h2>Key Types — Marketplace E2E FMEA</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.MarketplaceFailureModeType}
 *       — enum of FM_E1–FM_E7 with embedded S/O/D scores</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.MarketplaceFmeaViolation}
 *       — record representing one detected marketplace violation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.MarketplaceFmeaReport}
 *       — record carrying the marketplace analysis result (GREEN / RED)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.MarketplaceFmeaAnalyzer}
 *       — stateless E2E analyser; call once per event envelope boundary</li>
 * </ul>
 *
 * <h2>Usage — User FMEA</h2>
 * <pre>{@code
 * UserFmeaAnalyzer analyzer = new UserFmeaAnalyzer();
 *
 * UserFmeaReport report = analyzer.analyzePrincipal(principal, "workflow:launch");
 * if (!report.isClean()) {
 *     throw new SecurityException("User FMEA: " + report.status()
 *         + " (RPN=" + report.totalRpn() + ")");
 * }
 * }</pre>
 *
 * <h2>Usage — A2A FMEA</h2>
 * <pre>{@code
 * A2AFmeaAnalyzer analyzer = new A2AFmeaAnalyzer();
 *
 * A2AFmeaReport report = analyzer.analyzeAgentPrincipal(principal, "workflow:launch");
 * if (!report.isClean()) {
 *     throw new SecurityException("A2A FMEA: " + report.status()
 *         + " (RPN=" + report.totalRpn() + ")");
 * }
 * }</pre>
 *
 * <h2>Usage — MCP FMEA</h2>
 * <pre>{@code
 * McpFmeaAnalyzer analyzer = new McpFmeaAnalyzer();
 *
 * McpFmeaReport report = analyzer.analyzeContext(context, McpToolRegistry.providerCount());
 * if (!report.isClean()) {
 *     throw new IllegalStateException("MCP FMEA: " + report.status()
 *         + " (RPN=" + report.totalRpn() + ")");
 * }
 * }</pre>
 *
 * <h2>Usage — Marketplace E2E FMEA</h2>
 * <pre>{@code
 * MarketplaceFmeaAnalyzer analyzer = new MarketplaceFmeaAnalyzer();
 *
 * MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
 *     eventId, eventType, idempotencyKey, sequenceNumber, sourceAgent,
 *     processedKeys, lastSeenSequence, KNOWN_EVENT_TYPES);
 * if (!report.isClean()) {
 *     throw new IllegalStateException("Marketplace FMEA: " + report.status()
 *         + " (RPN=" + report.totalRpn() + ")");
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.fmea;
