/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.program;

import org.yawlfoundation.yawl.dspy.DspyExecutionResult;

import java.util.Map;

/**
 * Interface for program enhancer used by A2A skills.
 *
 * This interface provides the minimal functionality needed by A2A skills.
 * In production, this will be implemented by GepaProgramEnhancer.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface ProgramEnhancer {

    /**
     * Recompiles a program with a new optimization target.
     */
    DspyExecutionResult recompileWithNewTarget(String programName, Map<String, Object> inputs, String target);
}