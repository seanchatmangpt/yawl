package org.yawlfoundation.yawl.resilience.decorator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.a2a.A2AException;
import org.yawlfoundation.yawl.integration.a2a.YawlEngineAdapter;
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;

import java.util.List;
import java.util.Map;

/**
 * Resilient decorator for YawlEngineAdapter.
 *
 * Wraps all YawlEngineAdapter operations with Resilience4j patterns:
 * - Circuit breakers prevent cascade failures
 * - Retries with exponential backoff handle transient errors
 * - Bulkheads isolate concurrent workflow operations
 *
 * This decorator is transparent - it has the same API as YawlEngineAdapter
 * but provides production-grade fault tolerance.
 *
 * Usage:
 * <pre>
 * YawlEngineAdapter adapter = new YawlEngineAdapter(url, username, password);
 * ResilientYawlEngineAdapter resilientAdapter = new ResilientYawlEngineAdapter(adapter);
 *
 * // All operations now have built-in resilience
 * String caseId = resilientAdapter.launchCase("OrderProcessing", caseData);
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ResilientYawlEngineAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ResilientYawlEngineAdapter.class);

    private final YawlEngineAdapter delegate;
    private final YawlResilienceProvider resilienceProvider;

    /**
     * Create a resilient wrapper around a YawlEngineAdapter.
     *
     * @param delegate the underlying adapter to wrap
     */
    public ResilientYawlEngineAdapter(YawlEngineAdapter delegate) {
        this.delegate = delegate;
        this.resilienceProvider = YawlResilienceProvider.getInstance();
        logger.info("Created resilient YAWL engine adapter for {}", delegate.getEngineUrl());
    }

    /**
     * Create a resilient adapter from environment variables.
     *
     * @return configured resilient adapter
     * @throws IllegalStateException if required environment variables are missing
     */
    public static ResilientYawlEngineAdapter fromEnvironment() {
        YawlEngineAdapter adapter = YawlEngineAdapter.fromEnvironment();
        return new ResilientYawlEngineAdapter(adapter);
    }

    /**
     * Connect to the YAWL engine with retry logic.
     *
     * @throws A2AException if connection fails after retries
     */
    public void connect() throws A2AException {
        try {
            resilienceProvider.executeEngineCall(() -> {
                delegate.connect();
                return null;
            });
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to connect to YAWL engine after retries",
                null,
                e
            );
        }
    }

    /**
     * Disconnect from the YAWL engine.
     */
    public void disconnect() {
        delegate.disconnect();
    }

    /**
     * Ensure connection is active with retry logic.
     *
     * @throws A2AException if connection cannot be established
     */
    public void ensureConnected() throws A2AException {
        try {
            resilienceProvider.executeEngineCall(() -> {
                delegate.ensureConnected();
                return null;
            });
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to ensure connection after retries",
                null,
                e
            );
        }
    }

    /**
     * Check if connected.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return delegate.isConnected();
    }

    /**
     * Get engine URL.
     *
     * @return the engine URL
     */
    public String getEngineUrl() {
        return delegate.getEngineUrl();
    }

    /**
     * Launch a workflow case with circuit breaker and retry.
     *
     * @param specId the specification ID
     * @param caseData optional case data in XML format
     * @return the launched case ID
     * @throws A2AException if launch fails after retries
     */
    public String launchCase(String specId, String caseData) throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.launchCase(specId, caseData)
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Failed to launch case after retries",
                null,
                e
            );
        }
    }

    /**
     * Get all live work items with circuit breaker.
     *
     * @return list of work item records
     * @throws A2AException if retrieval fails after retries
     */
    public List<WorkItemRecord> getWorkItems() throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.getWorkItems()
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve work items after retries",
                null,
                e
            );
        }
    }

    /**
     * Get work items for a specific case with circuit breaker.
     *
     * @param caseId the case ID
     * @return list of work item records for the case
     * @throws A2AException if retrieval fails after retries
     */
    public List<WorkItemRecord> getWorkItemsForCase(String caseId) throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.getWorkItemsForCase(caseId)
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve work items for case after retries",
                null,
                e
            );
        }
    }

    /**
     * Get a specific work item with circuit breaker.
     *
     * @param workItemId the work item ID
     * @return the work item XML, or null if not found
     * @throws A2AException if retrieval fails after retries
     */
    public String getWorkItem(String workItemId) throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.getWorkItem(workItemId)
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve work item after retries",
                null,
                e
            );
        }
    }

    /**
     * Check out a work item with circuit breaker and retry.
     *
     * @param workItemId the work item ID
     * @return the checked out work item data
     * @throws A2AException if checkout fails after retries
     */
    public String checkOutWorkItem(String workItemId) throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.checkOutWorkItem(workItemId)
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Failed to check out work item after retries",
                null,
                e
            );
        }
    }

    /**
     * Check in a work item with circuit breaker and retry.
     *
     * @param workItemId the work item ID
     * @param outputData optional output data in XML format
     * @throws A2AException if checkin fails after retries
     */
    public void checkInWorkItem(String workItemId, String outputData) throws A2AException {
        try {
            resilienceProvider.executeEngineCall(() -> {
                delegate.checkInWorkItem(workItemId, outputData);
                return null;
            });
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Failed to check in work item after retries",
                null,
                e
            );
        }
    }

    /**
     * Complete a task with circuit breaker and retry.
     *
     * @param caseId the case ID
     * @param taskId the task ID
     * @param outputData optional output data
     * @return completion status
     * @throws A2AException if completion fails after retries
     */
    public Map<String, Object> completeTask(String caseId, String taskId, String outputData) throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.completeTask(caseId, taskId, outputData)
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Failed to complete task after retries",
                null,
                e
            );
        }
    }

    /**
     * Get case data with circuit breaker.
     *
     * @param caseId the case ID
     * @return the case data as XML
     * @throws A2AException if retrieval fails after retries
     */
    public String getCaseData(String caseId) throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.getCaseData(caseId)
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to get case data after retries",
                null,
                e
            );
        }
    }

    /**
     * Cancel a case with circuit breaker and retry.
     *
     * @param caseId the case ID to cancel
     * @throws A2AException if cancellation fails after retries
     */
    public void cancelCase(String caseId) throws A2AException {
        try {
            resilienceProvider.executeEngineCall(() -> {
                delegate.cancelCase(caseId);
                return null;
            });
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Failed to cancel case after retries",
                null,
                e
            );
        }
    }

    /**
     * Get list of loaded specifications with circuit breaker.
     *
     * @return list of specification names
     * @throws A2AException if retrieval fails after retries
     */
    public List<String> getSpecifications() throws A2AException {
        try {
            return resilienceProvider.executeEngineCall(() ->
                delegate.getSpecifications()
            );
        } catch (A2AException e) {
            throw e;
        } catch (Exception e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve specifications after retries",
                null,
                e
            );
        }
    }

    /**
     * Get engine session handle.
     *
     * @return the session handle
     */
    public String getSessionHandle() {
        return delegate.getSessionHandle();
    }

    /**
     * Get the underlying delegate adapter.
     *
     * @return the delegate adapter
     */
    public YawlEngineAdapter getDelegate() {
        return delegate;
    }
}
