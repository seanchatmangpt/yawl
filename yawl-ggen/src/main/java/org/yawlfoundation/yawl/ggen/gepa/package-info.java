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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * GEPA (Generative Evolutionary Prompt Architecture) for prompt optimization.
 *
 * <p>This package provides evolutionary optimization of DSPy prompts
 * for YAWL process generation. Inspired by van der Aalst's work but
 * with improvements:
 * <ul>
 *   <li>YAWL-specific fitness function</li>
 *   <li>Multi-layer validation integration</li>
 *   <li>TPOT2 integration for fitness scoring</li>
 * </ul>
 *
 * <h2>Evolutionary Algorithm:</h2>
 * <pre>
 * Initialize population with seed prompts
 *     ↓
 * Evaluate fitness using validation pipeline
 *     ↓
 * Select top performers (tournament selection)
 *     ↓
 * Apply crossover (prompt recombination)
 *     ↓
 * Apply mutation (prompt variation)
 *     ↓
 * Elitism (keep best individuals)
 *     ↓
 * Repeat until convergence
 * </pre>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * GepaOptimizer optimizer = GepaOptimizer.builder()
 *     .populationSize(10)
 *     .generations(20)
 *     .build();
 *
 * OptimizedPrompt best = optimizer.evolve(trainingData, generator);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see org.yawlfoundation.yawl.ggen.gepa.GepaOptimizer
 */
package org.yawlfoundation.yawl.ggen.gepa;
