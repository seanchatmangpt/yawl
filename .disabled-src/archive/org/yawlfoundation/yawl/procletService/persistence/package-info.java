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
 * Persistence layer for Proclet Service using Hibernate.
 *
 * <p>This package provides database persistence for proclet models, interaction
 * graphs, and related entities using Hibernate ORM.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.DBConnection} - Database connection manager</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredProcletBlock} - Persisted proclet block</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredProcletPort} - Persisted proclet port</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredPortConnection} - Persisted port connection</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredInteractionArc} - Persisted interaction arc</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredBlockRel} - Persisted block relationship</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredItem} - Persisted workflow item</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredDecisions} - Persisted decisions</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredOptions} - Persisted options</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.StoredPerformative} - Persisted performative</li>
 *   <li>{@link org.yawlfoundation.yawl.procletService.persistence.UniqueID} - Unique identifier generator</li>
 * </ul>
 */
package org.yawlfoundation.yawl.procletService.persistence;
