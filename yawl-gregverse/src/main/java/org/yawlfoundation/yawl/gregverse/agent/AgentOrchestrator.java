package org.yawlfoundation.yawl.gregverse.agent;

import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.gregverse.agent.AgentDirectory;
import org.yawlfoundation.yawl.gregverse.agent.AgentDiscoveryService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the lifecycle of all Greg-Verse agents.
 *
 * <p>Uses Java 25 virtual threads for concurrent agent startup and
 * provides coordination between agents via A2A protocol.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@Component
public class AgentOrchestrator {

    private final AgentDirectory directory;
    private final AgentDiscoveryService discoveryService;

    public AgentOrchestrator(AgentDirectory directory, AgentDiscoveryService discoveryService) {
        this.directory = directory;
        this.discoveryService = discoveryService;
    }

    /**
     * Starts all agents concurrently using Java 25 virtual threads.
     *
     * @param agents list of agents to start
     * @return CompletableFuture that completes when all agents are started
     */
    public CompletableFuture<Void> startAllAgents(List<GregVerseAgent> agents) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Void>> futures = agents.stream()
            .map(agent -> CompletableFuture.runAsync(
                () -> {
                    agent.initialize();
                    directory.register(agent);
                    discoveryService.registerAgent(agent);
                },
                executor))
            .toList();

        CompletableFuture<Void> result = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        result.thenRun(executor::shutdown);
        return result;
    }
}