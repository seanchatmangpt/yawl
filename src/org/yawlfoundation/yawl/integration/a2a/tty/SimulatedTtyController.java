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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.tty;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulated TTY controller for Claude Code integration.
 *
 * <p>Provides a programmatic interface for controlling Claude Code via a simulated
 * TTY interface. The controller manages command execution, response parsing, and
 * safety validation for all interactions.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Virtual thread-based command execution</li>
 *   <li>Integration with {@link TtySafetyLayer} for command validation</li>
 *   <li>Priority queue via {@link TtyCommandQueue} for ordered execution</li>
 *   <li>Response parsing via {@link TtyResponseParser}</li>
 *   <li>Session management with approval workflows</li>
 *   <li>Timeout handling and cancellation</li>
 * </ul>
 *
 * <p><b>Architecture:</b>
 * <pre>
 *                    +-------------------+
 *                    |    User Request   |
 *                    +-------------------+
 *                             |
 *                             v
 *                    +-------------------+
 *                    | TtySafetyLayer    | ----> Validate & Classify
 *                    +-------------------+
 *                             |
 *                             v
 *                    +-------------------+
 *                    | TtyCommandQueue   | ----> Priority Queue
 *                    +-------------------+
 *                             |
 *                             v
 *                    +-------------------+
 *                    | SimulatedTty      | ----> Execute Command
 *                    | Controller        |
 *                    +-------------------+
 *                             |
 *                             v
 *                    +-------------------+
 *                    | TtyResponseParser | ----> Parse Response
 *                    +-------------------+
 *                             |
 *                             v
 *                    +-------------------+
 *                    |   TtyResponse     | ----> Return Result
 *                    +-------------------+
 * </pre>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * // Create controller with dependencies
 * TtySafetyLayer safety = new TtySafetyLayer();
 * TtyCommandQueue queue = new TtyCommandQueue(100);
 * TtyResponseParser parser = new TtyResponseParser();
 *
 * SimulatedTtyController controller = new SimulatedTtyController(safety, queue, parser);
 * controller.start();
 *
 * // Execute a command
 * TtyCommand cmd = TtyCommand.of("Read the file /src/Main.java", TtyCommandPriority.HIGH);
 * CompletableFuture<TtyExecutionResult> future = controller.executeAsync(cmd);
 *
 * TtyExecutionResult result = future.get(30, TimeUnit.SECONDS);
 * if (result.isSuccess()) {
 *     System.out.println(result.response().rawContent());
 * }
 *
 * controller.shutdown();
 * }</pre>
 *
 * @since YAWL 5.2
 */
public final class SimulatedTtyController {

    private static final Logger _logger = LogManager.getLogger(SimulatedTtyController.class);

    /**
     * Default command timeout.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Default Claude Code CLI command.
     */
    public static final String DEFAULT_CLI_COMMAND = "claude";

    /**
     * Result of a command execution.
     *
     * @param command the executed command
     * @param response the parsed response
     * @param executionTimeMs execution time in milliseconds
     * @param safetyValidation the safety validation result
     * @param approvalRequired whether approval was required
     * @param approvedBy who approved (if applicable)
     */
    public record TtyExecutionResult(
        TtyCommandQueue.TtyCommand command,
        TtyResponseParser.TtyResponse response,
        long executionTimeMs,
        TtySafetyLayer.SafetyValidationResult safetyValidation,
        boolean approvalRequired,
        String approvedBy
    ) {
        /**
         * Check if the execution was successful.
         *
         * @return true if successful
         */
        public boolean isSuccess() {
            return response != null && response.isSuccess();
        }

        /**
         * Check if the execution was blocked by safety layer.
         *
         * @return true if blocked
         */
        public boolean isBlocked() {
            return safetyValidation != null && !safetyValidation.allowed();
        }

        /**
         * Get the error message if failed.
         *
         * @return error message or null if successful
         */
        public String getErrorMessage() {
            if (response instanceof TtyResponseParser.ErrorResponse error) {
                return error.message();
            }
            if (isBlocked()) {
                return safetyValidation.reason();
            }
            return null;
        }
    }

    /**
     * Session information for a controller instance.
     *
     * @param sessionId unique session identifier
     * @param startedAt when the session started
     * @param commandsExecuted total commands executed
     * @param commandsBlocked total commands blocked
     * @param commandsPending commands pending approval
     */
    public record SessionInfo(
        String sessionId,
        Instant startedAt,
        long commandsExecuted,
        long commandsBlocked,
        long commandsPending
    ) {}

    /**
     * Approval callback interface.
     */
    @FunctionalInterface
    public interface ApprovalCallback {
        /**
         * Request approval for a command.
         *
         * @param command the command requiring approval
         * @param safetyValidation the safety validation result
         * @return true if approved, false if rejected
         */
        boolean requestApproval(
            TtyCommandQueue.TtyCommand command,
            TtySafetyLayer.SafetyValidationResult safetyValidation
        );
    }

    private final TtySafetyLayer safetyLayer;
    private final TtyCommandQueue commandQueue;
    private final TtyResponseParser responseParser;
    private final ExecutorService executor;
    private final String sessionId;
    private final Path workingDirectory;
    private final String cliCommand;
    private final Duration defaultTimeout;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong commandsExecuted = new AtomicLong(0);
    private final AtomicLong commandsBlocked = new AtomicLong(0);
    private final AtomicLong commandsPending = new AtomicLong(0);
    private final Map<String, CompletableFuture<TtyExecutionResult>> pendingFutures = new ConcurrentHashMap<>();
    private final Instant startedAt;

    private ApprovalCallback approvalCallback;
    private volatile Thread processingThread;

    /**
     * Create a controller with default settings.
     *
     * @param safetyLayer the safety layer for command validation
     * @param commandQueue the command queue for ordering
     * @param responseParser the response parser
     */
    public SimulatedTtyController(
        TtySafetyLayer safetyLayer,
        TtyCommandQueue commandQueue,
        TtyResponseParser responseParser
    ) {
        this(safetyLayer, commandQueue, responseParser, null, DEFAULT_CLI_COMMAND, DEFAULT_TIMEOUT);
    }

    /**
     * Create a controller with full configuration.
     *
     * @param safetyLayer the safety layer for command validation
     * @param commandQueue the command queue for ordering
     * @param responseParser the response parser
     * @param workingDirectory the working directory for command execution
     * @param cliCommand the Claude Code CLI command
     * @param defaultTimeout the default command timeout
     */
    public SimulatedTtyController(
        TtySafetyLayer safetyLayer,
        TtyCommandQueue commandQueue,
        TtyResponseParser responseParser,
        Path workingDirectory,
        String cliCommand,
        Duration defaultTimeout
    ) {
        this.safetyLayer = Objects.requireNonNull(safetyLayer, "safetyLayer cannot be null");
        this.commandQueue = Objects.requireNonNull(commandQueue, "commandQueue cannot be null");
        this.responseParser = Objects.requireNonNull(responseParser, "responseParser cannot be null");
        this.workingDirectory = workingDirectory != null ? workingDirectory : Path.of(".");
        this.cliCommand = cliCommand != null ? cliCommand : DEFAULT_CLI_COMMAND;
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : DEFAULT_TIMEOUT;
        this.sessionId = generateSessionId();
        this.startedAt = Instant.now();

        // Create virtual thread executor for Java 25
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        _logger.info("SimulatedTtyController initialized: sessionId={}, workingDir={}",
            sessionId, this.workingDirectory);
    }

    /**
     * Start the controller.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            processingThread = Thread.ofVirtual()
                .name("tty-controller-" + sessionId)
                .start(this::processQueue);

            _logger.info("Controller started: sessionId={}", sessionId);
        }
    }

    /**
     * Shutdown the controller.
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            if (processingThread != null) {
                processingThread.interrupt();
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }

            // Cancel all pending futures
            pendingFutures.values().forEach(future ->
                future.completeExceptionally(new InterruptedException("Controller shutdown"))
            );
            pendingFutures.clear();

            _logger.info("Controller shutdown: sessionId={}", sessionId);
        }
    }

    /**
     * Execute a command synchronously.
     *
     * @param command the command to execute
     * @return the execution result
     */
    public TtyExecutionResult execute(TtyCommandQueue.TtyCommand command) {
        return executeAsync(command).join();
    }

    /**
     * Execute a command synchronously with timeout.
     *
     * @param command the command to execute
     * @param timeout the timeout
     * @return the execution result
     * @throws TimeoutException if timeout exceeded
     */
    public TtyExecutionResult execute(TtyCommandQueue.TtyCommand command, Duration timeout)
            throws TimeoutException {
        try {
            return executeAsync(command).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException("Command execution failed", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Command execution interrupted", e);
        }
    }

    /**
     * Execute a command asynchronously.
     *
     * @param command the command to execute
     * @return future with execution result
     */
    public CompletableFuture<TtyExecutionResult> executeAsync(TtyCommandQueue.TtyCommand command) {
        Objects.requireNonNull(command, "command cannot be null");

        CompletableFuture<TtyExecutionResult> future = new CompletableFuture<>();
        pendingFutures.put(command.id(), future);

        // Validate safety
        TtySafetyLayer.SafetyValidationResult validation = safetyLayer.validate(command.content());

        if (!validation.allowed()) {
            commandsBlocked.incrementAndGet();
            future.complete(createBlockedResult(command, validation));
            pendingFutures.remove(command.id());
            return future;
        }

        // Check if approval is required
        if (validation.safetyClass().requiresApproval() && approvalCallback != null) {
            boolean approved = approvalCallback.requestApproval(command, validation);
            if (!approved) {
                commandsBlocked.incrementAndGet();
                future.complete(createRejectedResult(command, validation));
                pendingFutures.remove(command.id());
                return future;
            }
        }

        // Enqueue the command
        TtyCommandQueue.TtyCommand enrichedCommand = command.withSafetyClass(validation.safetyClass());
        commandQueue.enqueue(enrichedCommand);

        return future;
    }

    /**
     * Execute a simple command string.
     *
     * @param content the command content
     * @param priority the priority
     * @return the execution result
     */
    public TtyExecutionResult execute(String content, TtyCommandQueue.TtyCommandPriority priority) {
        TtyCommandQueue.TtyCommand command = TtyCommandQueue.TtyCommand.of(content, priority);
        return execute(command);
    }

    /**
     * Execute a simple command string with default priority.
     *
     * @param content the command content
     * @return the execution result
     */
    public TtyExecutionResult execute(String content) {
        return execute(content, TtyCommandQueue.TtyCommandPriority.MEDIUM);
    }

    /**
     * Set the approval callback for commands requiring approval.
     *
     * @param callback the approval callback
     */
    public void setApprovalCallback(ApprovalCallback callback) {
        this.approvalCallback = callback;
    }

    /**
     * Get the current session info.
     *
     * @return session information
     */
    public SessionInfo getSessionInfo() {
        return new SessionInfo(
            sessionId,
            startedAt,
            commandsExecuted.get(),
            commandsBlocked.get(),
            pendingFutures.size()
        );
    }

    /**
     * Check if the controller is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get the command queue.
     *
     * @return the command queue
     */
    public TtyCommandQueue getCommandQueue() {
        return commandQueue;
    }

    /**
     * Get the safety layer.
     *
     * @return the safety layer
     */
    public TtySafetyLayer getSafetyLayer() {
        return safetyLayer;
    }

    /**
     * Get the response parser.
     *
     * @return the response parser
     */
    public TtyResponseParser getResponseParser() {
        return responseParser;
    }

    /**
     * Cancel a pending command.
     *
     * @param commandId the command ID to cancel
     * @return true if cancelled
     */
    public boolean cancel(String commandId) {
        boolean queueCancelled = commandQueue.cancel(commandId);

        CompletableFuture<TtyExecutionResult> future = pendingFutures.remove(commandId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            return true;
        }

        return queueCancelled;
    }

    /**
     * Wait for all pending commands to complete.
     *
     * @param timeout the maximum wait time
     * @return true if all completed within timeout
     */
    public boolean awaitCompletion(Duration timeout) {
        try {
            CompletableFuture.allOf(pendingFutures.values().toArray(new CompletableFuture[0]))
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException e) {
            return false;
        }
    }

    private void processQueue() {
        while (running.get()) {
            try {
                Optional<TtyCommandQueue.TtyCommand> commandOpt = commandQueue.dequeue(1000);

                if (commandOpt.isPresent()) {
                    TtyCommandQueue.TtyCommand command = commandOpt.get();
                    processCommand(command);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                _logger.error("Error processing command queue: {}", e.getMessage(), e);
            }
        }
    }

    private void processCommand(TtyCommandQueue.TtyCommand command) {
        CompletableFuture<TtyExecutionResult> future = pendingFutures.get(command.id());

        if (future == null || future.isCancelled()) {
            commandQueue.markCancelled(command.id());
            return;
        }

        long startTime = System.currentTimeMillis();

        try {
            _logger.info("Executing command: id={}, content={}",
                command.id(), truncateForLog(command.content()));

            // Execute the actual command
            String rawResponse = executeClaudeCommand(command);

            // Parse the response
            TtyResponseParser.TtyResponse response = responseParser.parse(rawResponse);

            long executionTime = System.currentTimeMillis() - startTime;
            commandsExecuted.incrementAndGet();

            TtyExecutionResult result = new TtyExecutionResult(
                command,
                response,
                executionTime,
                TtySafetyLayer.SafetyValidationResult.allowed(
                    command.safetyClass() != null
                        ? command.safetyClass()
                        : TtySafetyLayer.SafetyClass.SAFE,
                    "Command executed successfully"
                ),
                false,
                null
            );

            commandQueue.markCompleted(command.id(), response.rawContent());
            future.complete(result);

            _logger.info("Command completed: id={}, timeMs={}, success={}",
                command.id(), executionTime, response.isSuccess());

        } catch (Exception e) {
            _logger.error("Command execution failed: id={}, error={}",
                command.id(), e.getMessage(), e);

            TtyResponseParser.ErrorResponse errorResponse = TtyResponseParser.ErrorResponse.fromException(e);

            TtyExecutionResult result = new TtyExecutionResult(
                command,
                errorResponse,
                System.currentTimeMillis() - startTime,
                TtySafetyLayer.SafetyValidationResult.allowed(
                    command.safetyClass() != null
                        ? command.safetyClass()
                        : TtySafetyLayer.SafetyClass.SAFE,
                    "Command failed during execution"
                ),
                false,
                null
            );

            commandQueue.markFailed(command.id(), e.getMessage());
            future.complete(result);
        } finally {
            pendingFutures.remove(command.id());
        }
    }

    private String executeClaudeCommand(TtyCommandQueue.TtyCommand command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder();

        List<String> cmdList = new java.util.ArrayList<>();
        cmdList.add(cliCommand);
        cmdList.add("--print");  // Non-interactive mode
        cmdList.add(command.content());

        pb.command(cmdList);
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(true);

        // Set environment variables
        Map<String, String> env = pb.environment();
        env.put("CLAUDE_NON_INTERACTIVE", "true");
        env.put("CLAUDE_OUTPUT_FORMAT", "text");

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(defaultTimeout.toMillis(), TimeUnit.MILLISECONDS);

        if (!completed) {
            process.destroyForcibly();
            throw new TimeoutException("Command timed out after " + defaultTimeout);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + output);
        }

        return output.toString().trim();
    }

    private TtyExecutionResult createBlockedResult(
        TtyCommandQueue.TtyCommand command,
        TtySafetyLayer.SafetyValidationResult validation
    ) {
        TtyResponseParser.ErrorResponse errorResponse = new TtyResponseParser.ErrorResponse(
            "SAFETY_BLOCKED",
            validation.reason(),
            validation.matchedPattern() != null
                ? Map.of("matchedPattern", validation.matchedPattern())
                : Map.of(),
            "Command blocked by safety layer: " + validation.reason(),
            Instant.now()
        );

        return new TtyExecutionResult(
            command,
            errorResponse,
            0,
            validation,
            false,
            null
        );
    }

    private TtyExecutionResult createRejectedResult(
        TtyCommandQueue.TtyCommand command,
        TtySafetyLayer.SafetyValidationResult validation
    ) {
        TtyResponseParser.ErrorResponse errorResponse = new TtyResponseParser.ErrorResponse(
            "APPROVAL_REJECTED",
            "Command requires approval but was rejected",
            Map.of("safetyClass", validation.safetyClass().name()),
            "Command rejected by approval callback",
            Instant.now()
        );

        return new TtyExecutionResult(
            command,
            errorResponse,
            0,
            validation,
            true,
            null
        );
    }

    private static String generateSessionId() {
        return "tty-" + System.currentTimeMillis() + "-" +
               Integer.toHexString(System.identityHashCode(new Object())).substring(0, 4);
    }

    private static String truncateForLog(String content) {
        if (content == null) {
            return "null";
        }
        int maxLength = 100;
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
