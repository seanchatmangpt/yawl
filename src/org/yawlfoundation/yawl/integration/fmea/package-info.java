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
 * Runtime Failure Mode and Effects Analysis (FMEA) for YAWL v6 users.
 *
 * <p>Extends the observatory FMEA framework (FM1–FM7, build/infra risks) with
 * seven user-level failure modes (FM_U1–FM_U7) covering authentication,
 * authorisation, tenant isolation, and resource allocation.
 *
 * <h2>Failure Mode Inventory</h2>
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
 * <h2>RPN Formula</h2>
 * <pre>
 *   RPN = Severity × Occurrence × Detection   (each 1–10, 10 = worst)
 * </pre>
 *
 * <h2>Key Types</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFailureModeType}
 *       — enum of FM_U1–FM_U7 with embedded S/O/D scores</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFmeaViolation}
 *       — record representing one detected violation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFmeaReport}
 *       — record carrying the full analysis result (GREEN / RED)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.fmea.UserFmeaAnalyzer}
 *       — stateless analyser; call once per request boundary</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * UserFmeaAnalyzer analyzer = new UserFmeaAnalyzer();
 *
 * // Check an A2A principal before delegating a skill
 * UserFmeaReport report = analyzer.analyzePrincipal(principal, "workflow:launch");
 * if (!report.isClean()) {
 *     throw new SecurityException("User FMEA: " + report.status()
 *         + " (RPN=" + report.totalRpn() + ")");
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.fmea;
