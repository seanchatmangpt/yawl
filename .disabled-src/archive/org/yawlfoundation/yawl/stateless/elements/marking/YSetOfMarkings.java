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

package org.yawlfoundation.yawl.stateless.elements.marking;

import org.yawlfoundation.yawl.engine.core.marking.YCoreSetOfMarkings;

/**
 * Stateless-engine thin wrapper around {@link YCoreSetOfMarkings}.
 *
 * <p>This class was refactored as part of Phase 1 engine deduplication
 * (EngineDedupPlan P1.2).  All set-of-markings algorithm logic now lives in
 * {@link YCoreSetOfMarkings}; this wrapper is a pure subtype alias that
 * maintains the original package path for backward compatibility with all
 * existing stateless engine code that imports
 * {@code org.yawlfoundation.yawl.stateless.elements.marking.YSetOfMarkings}.</p>
 *
 * <p>The only difference between the original stateful and stateless implementations
 * was the {@code final} modifier on the {@code _markings} field and the package
 * declaration; the canonical implementation in {@link YCoreSetOfMarkings} uses
 * {@code final} (the superior choice) and is shared by both trees.</p>
 *
 * @author Lachlan Aldred (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 */
public class YSetOfMarkings extends YCoreSetOfMarkings {
    // No additional members: all logic is inherited from YCoreSetOfMarkings.
}
