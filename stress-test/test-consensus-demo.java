/*
 * Simple demonstration of the consensus framework
 */

import org.yawlfoundation.yawl.consensus.*;

import java.util.*;
import java.util.concurrent.*;

public class test-consensus-demo {
    public static void main(String[] args) {
        System.out.println("=== Byzantine Consensus Framework Demo ===\n");

        // Create consensus engine
        ConsensusEngine engine = new RaftConsensus();

        // Create test nodes
        ConsensusNode node1 = new ConsensusNodeImpl("node1:8080", engine);
        ConsensusNode node2 = new ConsensusNodeImpl("node2:8080", engine);
        ConsensusNode node3 = new ConsensusNodeImpl("node3:8080", engine);

        // Register nodes
        engine.registerNode(node1);
        engine.registerNode(node2);
        engine.registerNode(node3);

        System.out.println("1. Cluster Setup:");
        System.out.println("   Registered " + engine.getNodeCount() + " nodes");
        System.out.println("   Has quorum: " + engine.hasQuorum());

        // Test basic consensus
        System.out.println("\n2. Testing Basic Consensus:");
        testBasicConsensus(engine, node1);

        // Test performance
        System.out.println("\n3. Performance Test:");
        testPerformance(engine, node1);

        // Test A2A integration
        System.out.println("\n4. A2A Integration Test:");
        testA2AIntegration(engine);

        System.out.println("\n=== Demo Complete ===");
    }

    private static void testBasicConsensus(ConsensusEngine engine, ConsensusNode proposer) {
        Proposal proposal = new Proposal("workflow-approved", proposer.getId(),
                                      ProposalType.WORKFLOW_STATE, 1);

        long startTime = System.currentTimeMillis();

        CompletableFuture<ConsensusResult> future = engine.propose(proposal);

        future.thenAccept(result -> {
            long endTime = System.currentTimeMillis();
            long latency = endTime - startTime;

            System.out.println("   Result: " + (result.isSuccess() ? "SUCCESS" : "FAILED"));
            System.out.println("   Value: " + result.getValue());
            System.out.println("   Latency: " + latency + "ms");
            System.out.println("   Consensus Ratio: " + (result.getConsensusRatio() * 100) + "%");
        });

        // Wait for completion
        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("   Error: " + e.getMessage());
        }
    }

    private static void testPerformance(ConsensusEngine engine, ConsensusNode proposer) {
        int proposalCount = 10;
        List<Long> latencies = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(proposalCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < proposalCount; i++) {
            Proposal proposal = new Proposal("perf-test-" + i, proposer.getId(),
                                          ProposalType.WORKFLOW_STATE, i);

            engine.propose(proposal).thenAccept(result -> {
                long endTime = System.currentTimeMillis();
                latencies.add(endTime - startTime);
                latch.countDown();
            });
        }

        try {
            latch.await(3, TimeUnit.SECONDS);

            double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

            System.out.println("   Average Latency: " + String.format("%.2f", avgLatency) + "ms");
            System.out.println("   Success Rate: " + (latencies.size() * 100 / proposalCount) + "%");

            boolean meetsTarget = avgLatency < 100;
            System.out.println("   <100ms Target: " + (meetsTarget ? "✓ PASSED" : "✗ FAILED"));
        } catch (Exception e) {
            System.out.println("   Performance test failed: " + e.getMessage());
        }
    }

    private static void testA2AIntegration(ConsensusEngine engine) {
        A2AConsensusIntegration a2a = new A2AConsensusIntegration(engine);

        // Create workflow consensus
        List<String> participants = Arrays.asList("agent1", "agent2", "agent3");
        WorkflowConsensus workflow = a2a.createWorkflowConsensus("order-flow", participants);

        System.out.println("   Workflow ID: " + workflow.getWorkflowId());
        System.out.println("   Participants: " + String.join(", ", participants));
        System.out.println("   Current State: " + workflow.getCurrentState());

        // Test state change proposal
        CompletableFuture<WorkflowConsensusResult> future =
            a2a.proposeWorkflowStateChange(
                workflow.getWorkflowId(),
                "pending",
                "approved",
                "agent1"
            );

        future.thenAccept(result -> {
            System.out.println("   State Change: " + (result.isSuccess() ? "SUCCESS" : "FAILED"));
            System.out.println("   Result Value: " + result.getResultValue());
            System.out.println("   Consensus Time: " + result.getConsensusTimeMs() + "ms");
        });

        // Wait for completion
        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("   A2A test error: " + e.getMessage());
        }
    }
}