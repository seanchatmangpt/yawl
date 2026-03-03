/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A2A Consensus Integration
 *
 * Integration layer for connecting YAWL A2A agents with the consensus framework.
 * Provides high-level abstractions for workflow consensus operations.
 */
public class A2AConsensusIntegration {
    private final ConsensusEngine consensusEngine;
    private final Map<String, WorkflowConsensus> workflowConsensusMap = new ConcurrentHashMap<>();
    private final AtomicInteger workflowCounter = new AtomicInteger(0);
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    /**
     * Constructor for A2A consensus integration
     *
     * @param consensusEngine The underlying consensus engine to use
     */
    public A2AConsensusIntegration(ConsensusEngine consensusEngine) {
        this.consensusEngine = consensusEngine;
    }

    /**
     * Create a new workflow consensus session
     *
     * @param workflowName Name of the workflow
     * @param participants List of participant IDs
     * @return WorkflowConsensus instance
     */
    public WorkflowConsensus createWorkflowConsensus(String workflowName, List<String> participants) {
        String workflowId = "workflow-" + workflowCounter.incrementAndGet() + "-" + workflowName;
        WorkflowConsensus workflowConsensus = new WorkflowConsensus(workflowId, participants, this);
        workflowConsensusMap.put(workflowId, workflowConsensus);
        return workflowConsensus;
    }

    /**
     * Get an existing workflow consensus session
     *
     * @param workflowId ID of the workflow consensus session
     * @return WorkflowConsensus instance or null if not found
     */
    public WorkflowConsensus getWorkflowConsensus(String workflowId) {
        return workflowConsensusMap.get(workflowId);
    }

    /**
     * Propose a workflow state change across all participants
     *
     * @param workflowId ID of the workflow
     * @param currentState Current workflow state
     * @param newState New workflow state
     * @param participantId ID of the participant proposing the change
     * @return CompletableFuture with consensus result
     */
    public CompletableFuture<WorkflowConsensusResult> proposeWorkflowStateChange(
            String workflowId, String currentState, String newState, String participantId) {

        WorkflowConsensus workflow = getWorkflowConsensus(workflowId);
        if (workflow == null) {
            CompletableFuture<WorkflowConsensusResult> future = new CompletableFuture<>();
            future.completeExceptionally(new ConsensusException(
                "Workflow consensus session not found: " + workflowId
            ));
            return future;
        }

        WorkflowStateProposal proposal = new WorkflowStateProposal(
            workflowId, currentState, newState, participantId, System.currentTimeMillis()
        );

        return workflow.proposeStateChange(proposal);
    }

    /**
     * Propose a task assignment
     *
     * @param workflowId ID of the workflow
     * @param taskId ID of the task
     * @param assignee ID of the participant to assign the task to
     * @param participantId ID of the participant proposing the assignment
     * @return CompletableFuture with consensus result
     */
    public CompletableFuture<WorkflowConsensusResult> proposeTaskAssignment(
            String workflowId, String taskId, String assignee, String participantId) {

        WorkflowConsensus workflow = getWorkflowConsensus(workflowId);
        if (workflow == null) {
            CompletableFuture<WorkflowConsensusResult> future = new CompletableFuture<>();
            future.completeExceptionally(new ConsensusException(
                "Workflow consensus session not found: " + workflowId
            ));
            return future;
        }

        TaskAssignmentProposal proposal = new TaskAssignmentProposal(
            workflowId, taskId, assignee, participantId, System.currentTimeMillis()
        );

        return workflow.proposeTaskAssignment(proposal);
    }

    /**
     * Get the current state of all workflow consensus sessions
     *
     * @return Map of workflow ID to WorkflowConsensusInfo
     */
    public Map<String, WorkflowConsensusInfo> getWorkflowConsensusStates() {
        Map<String, WorkflowConsensusInfo> states = new HashMap<>();

        for (Map.Entry<String, WorkflowConsensus> entry : workflowConsensusMap.entrySet()) {
            WorkflowConsensus workflow = entry.getValue();
            states.put(entry.getKey(), new WorkflowConsensusInfo(
                workflow.getWorkflowId(),
                workflow.getParticipants(),
                workflow.getCurrentState(),
                workflow.getConsensusEngine().getState()
            ));
        }

        return states;
    }

    /**
     * Shutdown the integration
     */
    public void shutdown() {
        executor.shutdown();
    }
}

/**
 * Workflow Consensus Session
 */
class WorkflowConsensus {
    private final String workflowId;
    private final List<String> participants;
    private final A2AConsensusIntegration integration;
    private final ConsensusEngine consensusEngine;
    private final AtomicReference<String> currentState = new AtomicReference<>("created");
    private final AtomicInteger proposalCounter = new AtomicInteger(0);

    public WorkflowConsensus(String workflowId, List<String> participants, A2AConsensusIntegration integration) {
        this.workflowId = workflowId;
        this.participants = new ArrayList<>(participants);
        this.integration = integration;
        this.consensusEngine = integration.consensusEngine;

        // Register consensus nodes for participants
        for (String participant : participants) {
            ConsensusNode node = new ConsensusNodeImpl(participant, consensusEngine);
            consensusEngine.registerNode(node);
        }
    }

    public CompletableFuture<WorkflowConsensusResult> proposeStateChange(WorkflowStateProposal proposal) {
        // Convert to consensus proposal
        yawl.consensus.Proposal consensusProposal = new yawl.consensus.Proposal(
            proposal.newState,
            UUID.randomUUID(), // In real implementation, map participant ID to UUID
            yawl.consensus.ProposalType.WORKFLOW_STATE,
            1
        );

        // Propose via consensus engine
        return consensusEngine.propose(consensusProposal).thenApply(result -> {
            if (result.isSuccess()) {
                currentState.set(proposal.newState);
                return new WorkflowConsensusResult(
                    proposal.workflowId,
                    result.getProposalId(),
                    proposal.newState,
                    true,
                    result.getConsensusTimeMs(),
                    result.getConsensusTerm()
                );
            } else {
                return new WorkflowConsensusResult(
                    proposal.workflowId,
                    result.getProposalId(),
                    null,
                    false,
                    result.getConsensusTimeMs(),
                    result.getConsensusTerm()
                );
            }
        });
    }

    public CompletableFuture<WorkflowConsensusResult> proposeTaskAssignment(TaskAssignmentProposal proposal) {
        // Convert to consensus proposal
        yawl.consensus.Proposal consensusProposal = new yawl.consensus.Proposal(
            String.format("assign:%s:%s", proposal.taskId, proposal.assignee),
            UUID.randomUUID(),
            yawl.consensus.ProposalType.TASK_ASSIGNMENT,
            1
        );

        return consensusEngine.propose(consensusProposal).thenApply(result -> {
            if (result.isSuccess()) {
                return new WorkflowConsensusResult(
                    proposal.workflowId,
                    result.getProposalId(),
                    String.format("Task %s assigned to %s", proposal.taskId, proposal.assignee),
                    true,
                    result.getConsensusTimeMs(),
                    result.getConsensusTerm()
                );
            } else {
                return new WorkflowConsensusResult(
                    proposal.workflowId,
                    result.getProposalId(),
                    null,
                    false,
                    result.getConsensusTimeMs(),
                    result.getConsensusTerm()
                );
            }
        });
    }

    // Getters
    public String getWorkflowId() { return workflowId; }
    public List<String> getParticipants() { return participants; }
    public String getCurrentState() { return currentState.get(); }
    public ConsensusEngine getConsensusEngine() { return consensusEngine; }
}

/**
 * Workflow State Change Proposal
 */
class WorkflowStateProposal {
    public final String workflowId;
    public final String currentState;
    public final String newState;
    public final String participantId;
    public final long timestamp;

    public WorkflowStateProposal(String workflowId, String currentState, String newState,
                               String participantId, long timestamp) {
        this.workflowId = workflowId;
        this.currentState = currentState;
        this newState = newState;
        this.participantId = participantId;
        this.timestamp = timestamp;
    }
}

/**
 * Task Assignment Proposal
 */
class TaskAssignmentProposal {
    public final String workflowId;
    public final String taskId;
    public final String assignee;
    public final String participantId;
    public final long timestamp;

    public TaskAssignmentProposal(String workflowId, String taskId, String assignee,
                                String participantId, long timestamp) {
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.assignee = assignee;
        this.participantId = participantId;
        this.timestamp = timestamp;
    }
}

/**
 * Workflow Consensus Result
 */
class WorkflowConsensusResult {
    private final String workflowId;
    private final long proposalId;
    private final String resultValue;
    private final boolean success;
    private final long consensusTimeMs;
    private final long consensusTerm;

    public WorkflowConsensusResult(String workflowId, long proposalId, String resultValue,
                                 boolean success, long consensusTimeMs, long consensusTerm) {
        this.workflowId = workflowId;
        this.proposalId = proposalId;
        this.resultValue = resultValue;
        this.success = success;
        this.consensusTimeMs = consensusTimeMs;
        this.consensusTerm = consensusTerm;
    }

    // Getters
    public String getWorkflowId() { return workflowId; }
    public long getProposalId() { return proposalId; }
    public String getResultValue() { return resultValue; }
    public boolean isSuccess() { return success; }
    public long getConsensusTimeMs() { return consensusTimeMs; }
    public long getConsensusTerm() { return consensusTerm; }
}

/**
 * Workflow Consensus Information
 */
class WorkflowConsensusInfo {
    private final String workflowId;
    private final List<String> participants;
    private final String currentState;
    private final ConsensusState consensusState;

    public WorkflowConsensusInfo(String workflowId, List<String> participants,
                                String currentState, ConsensusState consensusState) {
        this.workflowId = workflowId;
        this.participants = participants;
        this.currentState = currentState;
        this.consensusState = consensusState;
    }

    // Getters
    public String getWorkflowId() { return workflowId; }
    public List<String> getParticipants() { return participants; }
    public String getCurrentState() { return currentState; }
    public ConsensusState getConsensusState() { return consensusState; }
}