/**
 * TTY Control Layer for Claude Code integration in the YAWL self-upgrading codebase.
 *
 * <p>This package provides a simulated TTY interface for controlling Claude Code
 * from within the YAWL workflow engine. It enables the self-upgrading system to
 * programmatically interact with Claude Code using the same interface a human
 * would use, but with safety layers and command queuing.
 *
 * <p><b>Components:</b>
 * <ul>
 *   <li>{@link SimulatedTtyController} - Main interface for controlling Claude Code</li>
 *   <li>{@link TtyCommandQueue} - Priority queue for command execution</li>
 *   <li>{@link TtyResponseParser} - Parser for Claude Code output (JSON, text, streaming)</li>
 *   <li>{@link TtySafetyLayer} - 4-class safety model for command classification</li>
 * </ul>
 *
 * <p><b>Safety Model:</b>
 * <pre>
 *   SAFE      - Read-only operations (file reads, status queries)
 *   MODERATE  - Non-destructive writes (create files, add code)
 *   DANGEROUS - Destructive operations (delete files, force push)
 *   FORBIDDEN - Never allowed (rm -rf /, format disk, credential exposure)
 * </pre>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // Create the TTY controller with safety layer
 * TtySafetyLayer safetyLayer = new TtySafetyLayer();
 * TtyCommandQueue queue = new TtyCommandQueue();
 * TtyResponseParser parser = new TtyResponseParser();
 *
 * SimulatedTtyController controller = new SimulatedTtyController(
 *     safetyLayer, queue, parser
 * );
 *
 * // Queue a safe command
 * TtyCommand command = new TtyCommand(
 *     "Read the file /src/Main.java",
 *     TtyCommandPriority.HIGH,
 *     SafetyClass.SAFE
 * );
 *
 * queue.enqueue(command);
 * TtyResponse response = controller.executeNext();
 * }</pre>
 *
 * @since YAWL 5.2
 * @see SimulatedTtyController
 * @see TtyCommandQueue
 * @see TtyResponseParser
 * @see TtySafetyLayer
 */
package org.yawlfoundation.yawl.integration.a2a.tty;
