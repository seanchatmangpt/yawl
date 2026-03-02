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

package org.yawlfoundation.yawl.integration.a2a.safe;

import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;

/**
 * Interface for A2A skills that conduct SAFe ceremonies.
 *
 * <p>Extends A2ASkill to provide specialized ceremony execution capability
 * with structured input/output for SAFe ceremony orchestration.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public interface SafeCeremonySkill extends A2ASkill {

    /**
     * Conduct a SAFe ceremony with the given request.
     *
     * <p>Validates ceremony inputs, orchestrates ceremony execution,
     * and returns a receipt with outcomes and audit hashes.
     *
     * @param request ceremony request containing participants and inputs
     * @return ceremony receipt with outcomes and hashes
     * @throws CeremonyException if ceremony execution fails
     */
    CeremonyReceipt conductCeremony(CeremonyRequest request) throws CeremonyException;
}
