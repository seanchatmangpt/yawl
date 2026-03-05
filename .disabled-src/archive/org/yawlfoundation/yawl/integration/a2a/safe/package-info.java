/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

/**
 * SAFe ceremony A2A skills for YAWL enterprise agility.
 *
 * <p>Provides five SAFe ceremony skills enabling AI agents to conduct
 * ceremony events within the Scaled Agile Framework:
 *
 * <ul>
 *   <li><b>PI Planning</b> - 2-week team planning event for committing PI Objectives</li>
 *   <li><b>System Demo</b> - Showcases integrated system and evaluates progress</li>
 *   <li><b>Inspect & Adapt</b> - Retrospective and continuous improvement planning</li>
 *   <li><b>Portfolio Sync</b> - Monthly review of epic progress and budget guardrails</li>
 *   <li><b>Strategic Portfolio Review</b> - Quarterly roadmap and investment review</li>
 * </ul>
 *
 * <p><b>Models:</b>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.safe.CeremonyRequest} - Ceremony inputs</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.a2a.safe.CeremonyReceipt} - Ceremony outcomes with audit hashes</li>
 * </ul>
 *
 * <p><b>Integration:</b>
 * All ceremonies are registered with {@link org.yawlfoundation.yawl.integration.a2a.YawlA2AServer}
 * and available for invocation via the A2A skill protocol.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
package org.yawlfoundation.yawl.integration.a2a.safe;
