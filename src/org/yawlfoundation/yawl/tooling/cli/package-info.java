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
 * Command-line interface tools for YAWL v6.0.0 administration and operations.
 *
 * <p>This package provides a comprehensive CLI for YAWL engine management,
 * workflow operations, and administrative tasks. Built with modern CLI patterns
 * including subcommands, help generation, and shell completion support.
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.tooling.cli.YawlCliMain} -
 *       Entry point for CLI execution, command routing, and argument parsing</li>
 *   <li>{@link org.yawlfoundation.yawl.tooling.cli.YawlCliCommand} -
 *       Command annotation and registration interface for subcommands</li>
 *   <li>{@link org.yawlfoundation.yawl.tooling.cli.CliExceptionHelpers} -
 *       Exception formatting, error codes, and user-friendly error messages</li>
 * </ul>
 *
 * <h2>Available Commands</h2>
 * <pre>{@code
 * yawl <command> [options] [arguments]
 *
 * Commands:
 *   case        Case management (launch, cancel, list, status)
 *   spec        Specification management (load, unload, validate, list)
 *   workitem    Work item operations (list, checkout, complete)
 *   engine      Engine administration (status, health, config)
 *   admin       Administrative tasks (users, logging, maintenance)
 * }</pre>
 *
 * <h2>Case Management Examples</h2>
 * <pre>{@code
 * # Launch a new case
 * yawl case launch --spec OrderProcessing --data '{"customer":"ACME"}'
 *
 * # List all running cases
 * yawl case list --status running
 *
 * # Get case status with details
 * yawl case status CASE-2026-0001 --verbose
 *
 * # Cancel a case
 * yawl case cancel CASE-2026-0001 --reason "Customer request"
 * }</pre>
 *
 * <h2>Specification Management Examples</h2>
 * <pre>{@code
 * # Load a specification from file
 * yawl spec load /path/to/order-processing.xml
 *
 * # Validate specification without loading
 * yawl spec validate /path/to/spec.xml --schema strict
 *
 * # List all loaded specifications
 * yawl spec list --format table
 *
 * # Unload a specification (cancels all running cases)
 * yawl spec unload OrderProcessing --force
 * }</pre>
 *
 * <h2>Work Item Operations Examples</h2>
 * <pre>{@code
 * # List work items for a case
 * yawl workitem list --case CASE-2026-0001
 *
 * # Get work item details
 * yawl workitem get WI-001 --data
 *
 * # Complete a work item
 * yawl workitem complete WI-001 --output '{"approved":true}'
 * }</pre>
 *
 * <h2>Engine Administration Examples</h2>
 * <pre>{@code
 * # Check engine health
 * yawl engine health
 *
 * # View engine status and statistics
 * yawl engine status
 *
 * # Configure logging level
 * yawl admin logging --level DEBUG --component YEngine
 * }</pre>
 *
 * <h2>Output Formats</h2>
 * <p>Commands support multiple output formats via {@code --format}:
 * <ul>
 *   <li>{@code table} - Human-readable tabular format (default)</li>
 *   <li>{@code json} - JSON output for scripting and automation</li>
 *   <li>{@code yaml} - YAML output for configuration files</li>
 *   <li>{@code xml} - XML output compatible with YAWL formats</li>
 * </ul>
 *
 * <h2>Shell Completion</h2>
 * <p>Generate completion scripts for popular shells:
 * <pre>{@code
 * # Bash
 * source <(yawl completion bash)
 *
 * # Zsh
 * yawl completion zsh > "${fpath[1]}/_yawl"
 *
 * # Fish
 * yawl completion fish | source
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.tooling.cli;
