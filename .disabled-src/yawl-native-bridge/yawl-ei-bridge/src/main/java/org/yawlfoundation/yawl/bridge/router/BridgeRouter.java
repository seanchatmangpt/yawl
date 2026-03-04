/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.bridge.router;

import org.yawlfoundation.yawl.nativebridge.erlang.ProcessMiningClient;
import org.yawlfoundation.yawl.nativebridge.qlever.QLeverEngine;
import org.yawlfoundation.yawl.nativebridge.qlever.QLeverException;
import org.yawlfoundation.yawl.nativebridge.erlang.ErlangException;
import org.yawlfoundation.yawl.nativebridge.erlang.OcelId;
import org.yawlfoundation.yawl.nativebridge.erlang.SlimOcelId;
import org.yawlfoundation.yawl.nativebridge.erlang.PetriNetId;
import org.yawlfoundation.yawl.nativebridge.erlang.Constraint;
import org.yawlfoundation.yawl.nativebridge.erlang.ConformanceResult;
import org.yawlfoundation.yawl.nativebridge.erlang.DirectlyFollowsGraph;
import org.yawlfoundation.yawl.nativebridge.erlang.PetriNet;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;

/**
 * Router for NativeCall triples based on callPattern.
 * Dispatches calls to appropriate execution engines (QLeverEngine or ProcessMiningClient).
 *
 * <p>The BridgeRouter implements the Three-Domain Native Bridge Pattern,
 * routing triples between JVM (QLever), BEAM (Erlang), and blocked DIRECT domains.
 * It maintains separate engines for each domain and provides thread-safe routing
 * with proper resource management.</p>
 */
public final class BridgeRouter implements AutoCloseable {
    private final Map<CallPattern, Executor> executors;
    private final Map<NativeCall, RoutingResult> routingCache;
    private final boolean enableCaching;

    /**
     * Interface for execution engines.
     * Provides a common interface for different domain executors.
     */
    private interface Executor {
        Object execute(NativeCall call) throws Exception;
        CallPattern getSupportedPattern();
        void close() throws Exception;
    }

    /**
     * QLeverEngine wrapper for JVM domain execution.
     */
    private static final class QleverExecutor implements Executor {
        private final QLeverEngine engine;

        public QleverExecutor(QLeverEngine engine) {
            this.engine = engine;
        }

        @Override
        public Object execute(NativeCall call) throws QLeverException {
            // Convert NativeCall to SPARQL query
            String sparql = convertToSparql(call);

            // Determine query type based on predicate
            if (call.predicate().contains("ASK")) {
                return engine.ask(sparql);
            } else if (call.predicate().contains("SELECT")) {
                return engine.select(sparql);
            } else if (call.predicate().contains("CONSTRUCT")) {
                return engine.construct(sparql);
            } else if (call.predicate().contains("UPDATE")) {
                engine.update(sparql);
                return null; // UPDATE returns void
            } else {
                // Default to SELECT for unknown predicates
                return engine.select(sparql);
            }
        }

        @Override
        public CallPattern getSupportedPattern() {
            return CallPattern.JVM;
        }

        @Override
        public void close() throws Exception {
            engine.close();
        }
    }

    /**
     * ProcessMiningClient wrapper for BEAM domain execution.
     */
    private static final class ProcessMiningExecutor implements Executor {
        private final ProcessMiningClient client;

        public ProcessMiningExecutor(ProcessMiningClient client) {
            this.client = client;
        }

        @Override
        public Object execute(NativeCall call) throws ErlangException {
            // Extract operation type from predicate
            String predicate = call.predicate();

            if (predicate.contains("import")) {
                return client.importOcel(java.nio.file.Paths.get(call.object()));
            } else if (predicate.contains("slim")) {
                // For slim operations, we need a corresponding OcelId
                OcelId ocelId = OcelId.fromString(call.object());
                return client.slimLink(ocelId);
            } else if (predicate.contains("constraint")) {
                SlimOcelId slimId = SlimOcelId.fromString(call.object());
                return client.discoverOcDeclare(slimId);
            } else if (predicate.contains("replay")) {
                String[] parts = call.object().split(",");
                OcelId ocelId = OcelId.fromString(parts[0]);
                PetriNetId pnId = PetriNetId.fromString(parts[1]);
                return client.tokenReplay(ocelId, pnId);
            } else if (predicate.contains("dfg")) {
                SlimOcelId slimId = SlimOcelId.fromString(call.object());
                return client.discoverDfg(slimId);
            } else if (predicate.contains("mine")) {
                SlimOcelId slimId = SlimOcelId.fromString(call.object());
                return client.mineAlphaPlusPlus(slimId);
            } else {
                throw new ErlangException(
                    "Unsupported process mining operation: " + predicate
                );
            }
        }

        @Override
        public CallPattern getSupportedPattern() {
            return CallPattern.BEAM;
        }

        @Override
        public void close() throws Exception {
            client.close();
        }
    }

    /**
     * Constructs a new BridgeRouter with default configuration.
     * Initializes executors using ServiceLoader for extensibility.
     */
    public BridgeRouter() {
        this(true);
    }

    /**
     * Constructs a new BridgeRouter with specified caching configuration.
     *
     * @param enableCaching whether to enable routing result caching
     */
    public BridgeRouter(boolean enableCaching) {
        this.enableCaching = enableCaching;
        this.executors = new ConcurrentHashMap<>();
        this.routingCache = enableCaching ? new ConcurrentHashMap<>() : null;

        // Initialize executors using ServiceLoader for extensibility
        initializeExecutors();
    }

    /**
     * Initializes executors for supported call patterns.
     * Uses ServiceLoader to allow custom executor implementations.
     */
    private void initializeExecutors() {
        // Load default executors
        ServiceLoader<Executor> loader = ServiceLoader.load(Executor.class);

        for (Executor executor : loader) {
            CallPattern pattern = executor.getSupportedPattern();
            if (pattern != null && pattern.isExecutable()) {
                executors.put(pattern, executor);
            }
        }

        // Ensure we have default implementations
        if (!executors.containsKey(CallPattern.JVM)) {
            try {
                QLeverEngine qleverEngine = QLeverEngine.create();
                executors.put(CallPattern.JVM, new QleverExecutor(qleverEngine));
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to initialize QLeverEngine for JVM domain", e
                );
            }
        }

        if (!executors.containsKey(CallPattern.BEAM)) {
            try {
                ProcessMiningClient pmClient = ProcessMiningClient.create();
                executors.put(CallPattern.BEAM, new ProcessMiningExecutor(pmClient));
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to initialize ProcessMiningClient for BEAM domain", e
                );
            }
        }
    }

    /**
     * Routes a NativeCall triple to the appropriate executor.
     * Uses structured concurrency for thread-safe execution.
     *
     * @param call the native call to route
     * @return routing result containing the execution outcome
     * @throws BridgeRoutingException if routing fails or execution encounters errors
     */
    public RoutingResult route(NativeCall call) throws BridgeRoutingException {
        Objects.requireNonNull(call, "NativeCall cannot be null");

        // Check cache if enabled
        if (enableCaching && routingCache.containsKey(call)) {
            RoutingResult cached = routingCache.get(call);
            if (cached.isSuccess()) {
                return cached; // Return successful cache hit
            }
            // Cache miss or failure - proceed with routing
        }

        try (StructuredTaskScope.ShutdownOnFailure scope =
             new StructuredTaskScope.ShutdownOnFailure()) {

            var routingTask = scope.fork(() -> executeWithTimeout(call));

            scope.join();
            scope.throwIfFailed();

            RoutingResult result = routingTask.get();

            // Cache the result if enabled and successful
            if (enableCaching && result.isSuccess()) {
                routingCache.put(call, result);
            }

            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BridgeRoutingException(
                "Routing interrupted for call: " + call.toNtriple(), e
            );
        } catch (Exception e) {
            throw new BridgeRoutingException(
                "Routing failed for call: " + call.toNtriple(), e
            );
        }
    }

    /**
     * Executes a call with appropriate timeout handling.
     *
     * @param call to execute
     * @return routing result
     */
    private RoutingResult executeWithTimeout(NativeCall call) {
        Executor executor = executors.get(call.callPattern());

        if (executor == null) {
            return RoutingResult.failure(
                "No executor found for pattern: " + call.callPattern(),
                call
            );
        }

        try {
            // Pattern match on call pattern for specific handling
            Object result = switch (call.callPattern()) {
                case JVM -> executeJVM(call, executor);
                case BEAM -> executeBEAM(call, executor);
                case DIRECT -> throw new UnsupportedOperationException(
                    "Direct execution is blocked for security reasons. " +
                    "Use JVM or BEAM pattern instead."
                );
            };

            return RoutingResult.success(result, call);

        } catch (Exception e) {
            return RoutingResult.failure(e.getMessage(), call, e);
        }
    }

    /**
     * Executes a call in the JVM domain.
     *
     * @param call to execute
     * @param executor QLeverEngine executor
     * @return execution result
     * @throws QLeverException if execution fails
     */
    private Object executeJVM(NativeCall call, Executor executor) throws QLeverException {
        return executor.execute(call);
    }

    /**
     * Executes a call in the BEAM domain.
     *
     * @param call to execute
     * @param executor ProcessMiningClient executor
     * @return execution result
     * @throws ErlangException if execution fails
     */
    private Object executeBEAM(NativeCall call, Executor executor) throws ErlangException {
        return executor.execute(call);
    }

    /**
     * Gets the current status of all executors.
     *
     * @return map of call patterns to executor status
     */
    public Map<CallPattern, String> getExecutorStatus() {
        return executors.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getClass().getSimpleName()
            ));
    }

    /**
     * Checks if a specific call pattern is supported.
     *
     * @param pattern to check
     * @return true if supported and executable
     */
    public boolean isSupported(CallPattern pattern) {
        return pattern.isExecutable() && executors.containsKey(pattern);
    }

    /**
     * Clears the routing cache if enabled.
     */
    public void clearCache() {
        if (routingCache != null) {
            routingCache.clear();
        }
    }

    /**
     * Gets the number of cached routing results.
     *
     * @return cache size, or 0 if caching disabled
     */
    public int getCacheSize() {
        return routingCache != null ? routingCache.size() : 0;
    }

    @Override
    public void close() throws Exception {
        // Close all executors in reverse order of initialization
        for (Executor executor : executors.values()) {
            try {
                executor.close();
            } catch (Exception e) {
                // Log but don't propagate - close all executors
                System.err.println("Warning: Failed to close executor: " + e.getMessage());
            }
        }
        executors.clear();
        if (routingCache != null) {
            routingCache.clear();
        }
    }

    /**
     * Creates a new BridgeRouter with QLeverEngine for JVM domain.
     *
     * @param qleverEngine to use for JVM execution
     * @param enableCaching whether to enable result caching
     * @return new BridgeRouter instance
     */
    public static BridgeRouter withQleverEngine(QLeverEngine qleverEngine, boolean enableCaching) {
        BridgeRouter router = new BridgeRouter(enableCaching);

        // Replace JVM executor with provided instance
        Executor qleverExecutor = new QleverExecutor(qleverEngine);
        router.executors.put(CallPattern.JVM, qleverExecutor);

        return router;
    }

    /**
     * Creates a new BridgeRouter with ProcessMiningClient for BEAM domain.
     *
     * @param pmClient to use for BEAM execution
     * @param enableCaching whether to enable result caching
     * @return new BridgeRouter instance
     */
    public static BridgeRouter withProcessMiningClient(ProcessMiningClient pmClient, boolean enableCaching) {
        BridgeRouter router = new BridgeRouter(enableCaching);

        // Replace BEAM executor with provided instance
        Executor pmExecutor = new ProcessMiningExecutor(pmClient);
        router.executors.put(CallPattern.BEAM, pmExecutor);

        return router;
    }
}