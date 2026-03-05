/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

/**
 * Workflow Genome Fingerprinting and Similarity Analysis.
 *
 * <p>This package provides structural analysis of YAWL workflow specifications via
 * DNA-like fingerprinting, similarity metrics, and clustering algorithms.
 *
 * <p><b>Core Functionality:</b>
 * <ul>
 *   <li><b>Genome Extraction:</b> Analyzes specification XML to extract structural metrics:
 *     task count, AND/XOR split/join counts, loop count, nesting depth, cancellation regions,
 *     and input parameters.
 *   <li><b>Fingerprinting:</b> Each genome is represented as a fixed-dimension integer vector
 *     with a unique 5-character hexadecimal fingerprint.
 *   <li><b>Similarity Computation:</b> Pairwise cosine similarity between all workflow genomes
 *     to identify structurally similar specifications.
 *   <li><b>Clustering:</b> Groups workflows that exceed a configurable similarity threshold,
 *     enabling discovery of duplicate or near-duplicate process definitions.
 *   <li><b>Reporting:</b> Generates rich ASCII art output with genome profiles, similarity
 *     matrices, and actionable cluster insights.
 * </ul>
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Detect redundant workflow definitions for consolidation
 *   <li>Identify reference implementations (singleton clusters)
 *   <li>Analyze organizational workflow portfolio structure
 *   <li>Support workflow rationalization and optimization initiatives
 *   <li>Find structural twins for potential component reuse
 * </ul>
 *
 * <p><b>Integration:</b> Exposed via MCP tool {@code yawl_genome_analyze}, accessible through
 * the YAWL MCP server for autonomous agents and workflow analysis tools.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.integration.mcp.genome;
