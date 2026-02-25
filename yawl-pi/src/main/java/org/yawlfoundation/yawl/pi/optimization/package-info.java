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
 * Classical optimization algorithms for YAWL process intelligence.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.ResourceOptimizer}
 *       - Solves resource assignment using Hungarian Algorithm (Kuhn-Munkres)</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.ProcessScheduler}
 *       - Orders tasks using Shortest Processing Time (SPT) algorithm</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.AlignmentOptimizer}
 *       - Computes process alignment using Levenshtein distance</li>
 * </ul>
 *
 * <h2>Problem Models</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.AssignmentProblem}
 *       - Input specification for resource assignment</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.AssignmentSolution}
 *       - Output of Hungarian Algorithm solver</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.SchedulingResult}
 *       - Output of process scheduling</li>
 *   <li>{@link org.yawlfoundation.yawl.pi.optimization.AlignmentResult}
 *       - Output of process alignment</li>
 * </ul>
 *
 * <h2>Complexity Guarantees</h2>
 * <table>
 *   <tr><th>Algorithm</th><th>Time Complexity</th><th>Space Complexity</th></tr>
 *   <tr><td>Hungarian</td><td>O(n³)</td><td>O(n²)</td></tr>
 *   <tr><td>SPT Scheduling</td><td>O(n log n)</td><td>O(n)</td></tr>
 *   <tr><td>Levenshtein Alignment</td><td>O(mn)</td><td>O(mn)</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.pi.optimization;
