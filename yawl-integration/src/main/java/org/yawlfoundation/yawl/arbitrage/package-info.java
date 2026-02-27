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
 * Temporal case arbitrage: replay historical cases, run parallel futures, elect winner.
 *
 * <p>The arbitrage engine enables intelligent case routing optimization by:
 * <ul>
 *   <li>Forking any historical case at a decision point in time via {@link EventReplayer}</li>
 *   <li>Running N alternative future scenarios in parallel using virtual threads</li>
 *   <li>Electing a winner based on majority vote of outcome status and tiebreaking by duration</li>
 *   <li>Updating routing weights in the predictive router based on winning variant</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.arbitrage;
