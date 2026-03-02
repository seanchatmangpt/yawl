/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

/**
 * Consensus Exception
 *
 * Exception thrown when consensus operations fail.
 * Provides detailed information about the failure reason.
 */
public class ConsensusException extends RuntimeException {
    private final ConsensusStatus status;
    private final long proposalId;

    /**
     * Constructor with message and status
     *
     * @param message Exception message
     * @param status Consensus status that caused the exception
     */
    public ConsensusException(String message, ConsensusStatus status) {
        super(message);
        this.status = status;
        this.proposalId = -1;
    }

    /**
     * Constructor with message, status, and proposal ID
     *
     * @param message Exception message
     * @param status Consensus status that caused the exception
     * @param proposalId ID of the proposal that failed
     */
    public ConsensusException(String message, ConsensusStatus status, long proposalId) {
        super(message);
        this.status = status;
        this.proposalId = proposalId;
    }

    /**
     * Constructor with message only
     *
     * @param message Exception message
     */
    public ConsensusException(String message) {
        super(message);
        this.status = ConsensusStatus.SYSTEM_ERROR;
        this.proposalId = -1;
    }

    /**
     * Constructor with message and cause
     *
     * @param message Exception message
     * @param cause Root cause of the exception
     */
    public ConsensusException(String message, Throwable cause) {
        super(message, cause);
        this.status = ConsensusStatus.SYSTEM_ERROR;
        this.proposalId = -1;
    }

    public ConsensusStatus getStatus() {
        return status;
    }

    public long getProposalId() {
        return proposalId;
    }

    /**
     * Check if this exception represents a retryable failure
     */
    public boolean isRetryable() {
        return status != null && status.isRetryable();
    }

    /**
     * Check if this exception represents a permanent failure
     */
    public boolean isPermanent() {
        return status != null && status.isPermanent();
    }

    /**
     * Check if this exception requires immediate attention
     */
    public boolean isCritical() {
        return status != null && status.isCritical();
    }
}