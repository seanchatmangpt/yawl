/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import java.io.IOException;
import java.util.List;

/**
 * Generates K candidate POWL models from a process description.
 * Called by GrpoOptimizer to produce the candidate pool for ranking.
 */
public interface CandidateSampler {
    /**
     * Sample k POWL models for the given process description.
     *
     * @param processDescription natural language description of the process
     * @param k                  number of candidates to generate
     * @return list of k POWL models (may contain fewer if some fail to parse)
     * @throws IOException       if the LLM call fails
     * @throws PowlParseException if all candidates fail to parse
     */
    List<PowlModel> sample(String processDescription, int k) throws IOException, PowlParseException;
}
