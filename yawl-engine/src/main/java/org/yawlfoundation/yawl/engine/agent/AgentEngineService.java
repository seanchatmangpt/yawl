package org.yawlfoundation.yawl.engine.agent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring service that manages the lifecycle of the YAWL Pure Java 25 Agent Engine.
 *
 * This service:
 * - Initializes YawlAgentEngine on application startup
 * - Provides access to the engine for REST controllers
 * - Manages graceful shutdown on application stop
 * - Monitors agent health and provides diagnostic information
 *
 * Thread-safe singleton bean managed by Spring.
 *
 * @since Java 25
 */
@Service
public class AgentEngineService {
    private static final Logger logger = LoggerFactory.getLogger(AgentEngineService.class);

    private YawlAgentEngine engine;
    private final Map<String, Object> diagnostics = new ConcurrentHashMap<>();

    /**
     * Initialize the agent engine on Spring context startup.
     * Called automatically by Spring after bean construction.
     */
    @PostConstruct
    public void init() {
        logger.info("Initializing YAWL Agent Engine Service");
        try {
            this.engine = new YawlAgentEngine();
            diagnostics.put("status", "initialized");
            diagnostics.put("timestamp", System.currentTimeMillis());
            logger.info("Agent Engine initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize Agent Engine", e);
            diagnostics.put("status", "failed");
            diagnostics.put("error", e.getMessage());
            throw new RuntimeException("Agent Engine initialization failed", e);
        }
    }

    /**
     * Gracefully shutdown the agent engine on Spring context shutdown.
     * Called automatically by Spring before bean destruction.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down YAWL Agent Engine Service");
        if (engine != null) {
            try {
                engine.shutdownAll();
                diagnostics.put("status", "shutdown");
                diagnostics.put("shutdown_time", System.currentTimeMillis());
                logger.info("Agent Engine shut down successfully");
            } catch (Exception e) {
                logger.error("Error during Agent Engine shutdown", e);
                diagnostics.put("status", "shutdown_error");
                diagnostics.put("error", e.getMessage());
            }
        }
    }

    /**
     * Get the underlying YawlAgentEngine instance.
     * Should only be used internally; prefer service methods for public access.
     *
     * @return The engine instance, or null if not initialized
     */
    public YawlAgentEngine getEngine() {
        return engine;
    }

    /**
     * Get the agent registry for queries.
     *
     * @return The agent registry
     */
    public AgentRegistry getRegistry() {
        if (engine == null) {
            throw new IllegalStateException("Engine not initialized");
        }
        return engine.getRegistry();
    }

    /**
     * Get diagnostic information about the engine and its agents.
     *
     * @return Map with engine status, agent counts, and health metrics
     */
    public Map<String, Object> getDiagnostics() {
        Map<String, Object> result = new HashMap<>(diagnostics);

        if (engine != null) {
            AgentRegistry registry = engine.getRegistry();
            result.put("total_agents", registry.getTotalAgents());
            result.put("running_agents", registry.getRunningAgents().size());
            result.put("failed_agents", registry.getFailedAgents().size());
            result.put("agent_states", registry.getAllAgentStates());
        }

        return result;
    }

    /**
     * Check if the engine is ready to accept agent lifecycle requests.
     *
     * @return true if engine is initialized and healthy
     */
    public boolean isReady() {
        return engine != null && "initialized".equals(diagnostics.get("status"));
    }

    /**
     * Get health status for Kubernetes/cloud deployment liveness probes.
     *
     * @return Map with health information
     */
    public Map<String, String> getHealthStatus() {
        return Map.of(
            "status", isReady() ? "UP" : "DOWN",
            "component", "agent-engine",
            "agents_active", String.valueOf(
                engine != null ? engine.getRegistry().getTotalAgents() : 0
            )
        );
    }
}
