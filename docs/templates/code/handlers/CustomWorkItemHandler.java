package org.yawlfoundation.yawl.examples.handlers;

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.resourcing.resource.Participant;
import org.yawlfoundation.yawl.util.XmlBuilding;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * TEMPLATE: Custom Work Item Handler
 * PURPOSE: Implement custom logic for work item execution
 * CUSTOMIZATION: Replace placeholder task names, business logic, and integrations
 * LINK: docs/INTEGRATION-GUIDE.md#custom-handlers
 *
 * Usage:
 * 1. Implement handleWorkItem() with your business logic
 * 2. Register in workflow via decomposition reference
 * 3. YAWL engine automatically invokes on task execution
 *
 * Example in YAWL XML:
 * <task id="myTask">
 *   <decomposesTo>CustomWorkItemHandler</decomposesTo>
 * </task>
 */
public class CustomWorkItemHandler {

    private static final Logger log = Logger.getLogger(CustomWorkItemHandler.class.getSimpleName());

    private final YEngine engine;
    private final ExternalServiceClient serviceClient;  // Your external API client

    /**
     * Constructor: Initialize handler with engine and external dependencies
     * @param engine YAWL engine instance (injected by framework)
     * @param serviceClient external service client for API calls
     */
    public CustomWorkItemHandler(YEngine engine, ExternalServiceClient serviceClient) {
        this.engine = engine;
        this.serviceClient = serviceClient;
    }

    /**
     * Main handler method invoked by YAWL engine when task starts
     * @param workItem the work item containing task data
     * @param taskId the YAWL task identifier
     * @param caseId the case/instance identifier
     */
    public void handleWorkItem(YWorkItem workItem, String taskId, String caseId) {
        try {
            log.info("Processing work item: " + caseId + "/" + taskId);

            // Step 1: Extract input data from work item
            Map<String, String> inputData = extractInputData(workItem);
            String customerId = inputData.get("customerId");
            String orderId = inputData.get("orderId");
            Double amount = Double.parseDouble(inputData.getOrDefault("amount", "0"));

            // Step 2: Perform business logic (validation, transformation, enrichment)
            ValidatedOrderData validatedOrder = validateAndEnrichOrder(customerId, orderId, amount);

            // Step 3: Call external services (API, database, message queue)
            String transactionId = callExternalService(validatedOrder);

            // Step 4: Transform results back to YAWL data format
            Map<String, String> outputData = buildOutputData(validatedOrder, transactionId);

            // Step 5: Update work item with output data and mark complete
            completeWorkItem(workItem, taskId, caseId, outputData);

            log.info("Work item completed successfully: " + caseId + "/" + taskId);

        } catch (ValidationException e) {
            // Handle validation errors: request data correction from user
            handleValidationError(workItem, taskId, caseId, e);
        } catch (ExternalServiceException e) {
            // Handle external service failures: retry or escalate
            handleExternalServiceError(workItem, taskId, caseId, e);
        } catch (Exception e) {
            // Unexpected error: log and escalate
            handleUnexpectedError(workItem, taskId, caseId, e);
        }
    }

    /**
     * Extract input data from YAWL work item
     * Data format: XML or JSON (depends on workflow definition)
     */
    private Map<String, String> extractInputData(YWorkItem workItem) throws Exception {
        Map<String, String> data = new HashMap<>();

        // Get data variable from work item
        // Format depends on YAWL specification (XML or JSON)
        String rawData = workItem.getDataVariable("requestData");

        if (rawData == null || rawData.isEmpty()) {
            throw new ValidationException("Missing required input: requestData");
        }

        // Parse XML data (example format)
        if (rawData.startsWith("<")) {
            data.put("customerId", XmlBuilding.extractElementValue(rawData, "customerId"));
            data.put("orderId", XmlBuilding.extractElementValue(rawData, "orderId"));
            data.put("amount", XmlBuilding.extractElementValue(rawData, "amount"));
        }
        // Parse JSON data (alternative format)
        else if (rawData.startsWith("{")) {
            // Use your JSON parser (Gson, Jackson, etc.)
            // data = parseJsonToMap(rawData);
        }

        return data;
    }

    /**
     * Validate and enrich order data
     * - Validate required fields
     * - Check business rules (min/max amounts, customer limits)
     * - Enrich with additional data (customer tier, pricing, etc.)
     */
    private ValidatedOrderData validateAndEnrichOrder(String customerId, String orderId, Double amount)
            throws ValidationException {

        // Validation: Check required fields
        if (customerId == null || customerId.isEmpty()) {
            throw new ValidationException("Customer ID is required");
        }
        if (orderId == null || orderId.isEmpty()) {
            throw new ValidationException("Order ID is required");
        }
        if (amount <= 0) {
            throw new ValidationException("Amount must be positive");
        }

        // Business rule: Check amount limit
        final Double MAX_SINGLE_ORDER = 50000.0;
        if (amount > MAX_SINGLE_ORDER) {
            throw new ValidationException("Order amount exceeds limit of $" + MAX_SINGLE_ORDER);
        }

        // Enrichment: Fetch additional customer data
        CustomerInfo customer = fetchCustomerData(customerId);
        String orderStatus = checkOrderStatus(orderId);

        return new ValidatedOrderData(customerId, orderId, amount, customer, orderStatus);
    }

    /**
     * Call external service (API, database, message queue)
     * Implement with retry logic, timeout handling, and error recovery
     */
    private String callExternalService(ValidatedOrderData order) throws ExternalServiceException {
        try {
            // Example: Call payment processing API
            PaymentRequest request = new PaymentRequest(
                order.customerId,
                order.orderId,
                order.amount,
                order.customer.getPaymentMethod()
            );

            // With retry logic (exponential backoff)
            PaymentResponse response = retryableCall(() ->
                serviceClient.processPayment(request)
            );

            if (response.isSuccessful()) {
                log.info("Payment processed: " + response.getTransactionId());
                return response.getTransactionId();
            } else {
                throw new ExternalServiceException("Payment failed: " + response.getErrorMessage());
            }

        } catch (TimeoutException e) {
            log.warning("External service timeout: " + e.getMessage());
            throw new ExternalServiceException("Service timeout. Will retry later.", e);
        } catch (ConnectException e) {
            log.warning("External service unavailable: " + e.getMessage());
            throw new ExternalServiceException("Service unavailable. Will retry later.", e);
        }
    }

    /**
     * Retry logic with exponential backoff
     * Useful for handling transient external service failures
     */
    private <T> T retryableCall(ServiceCall<T> call) throws Exception {
        final int maxRetries = 3;
        final long initialDelayMs = 1000;  // 1 second

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return call.execute();
            } catch (Exception e) {
                if (attempt < maxRetries - 1) {
                    long delayMs = initialDelayMs * (long) Math.pow(2, attempt);
                    log.info("Retry attempt " + (attempt + 1) + " after " + delayMs + "ms");
                    Thread.sleep(delayMs);
                } else {
                    throw e;
                }
            }
        }
        throw new Exception("Max retries exceeded");
    }

    /**
     * Build output data for YAWL (XML or JSON format)
     * Format must match workflow data variable definition
     */
    private Map<String, String> buildOutputData(ValidatedOrderData order, String transactionId) {
        Map<String, String> output = new HashMap<>();

        // Example XML output
        String xmlOutput = "<response>" +
            "<status>success</status>" +
            "<customerId>" + order.customerId + "</customerId>" +
            "<orderId>" + order.orderId + "</orderId>" +
            "<transactionId>" + transactionId + "</transactionId>" +
            "<amount>" + order.amount + "</amount>" +
            "<processedAt>" + System.currentTimeMillis() + "</processedAt>" +
            "</response>";

        output.put("responseData", xmlOutput);
        output.put("status", "completed");
        output.put("transactionId", transactionId);

        return output;
    }

    /**
     * Complete work item: Update output variables and notify engine
     */
    private void completeWorkItem(YWorkItem workItem, String taskId, String caseId,
                                   Map<String, String> outputData) throws Exception {
        // Set output variables
        for (Map.Entry<String, String> entry : outputData.entrySet()) {
            workItem.setDataVariable(entry.getKey(), entry.getValue());
        }

        // Mark work item as completed
        // The engine will automatically progress to next task
        engine.completeWorkItem(caseId, taskId, true);
    }

    /**
     * Error handler: Validation errors (bad input data)
     * Request user to correct data and resubmit
     */
    private void handleValidationError(YWorkItem workItem, String taskId, String caseId,
                                       ValidationException e) {
        log.warning("Validation error: " + e.getMessage());
        try {
            // Set error message in work item
            workItem.setDataVariable("errorMessage", e.getMessage());
            workItem.setDataVariable("errorType", "validation");

            // Return work item to user for correction
            engine.skipWorkItem(caseId, taskId);
        } catch (Exception ex) {
            log.severe("Failed to handle validation error: " + ex.getMessage());
        }
    }

    /**
     * Error handler: External service failures
     * Retry or escalate based on error type
     */
    private void handleExternalServiceError(YWorkItem workItem, String taskId, String caseId,
                                            ExternalServiceException e) {
        log.warning("External service error: " + e.getMessage());
        try {
            // Log error for monitoring/alerting
            workItem.setDataVariable("errorMessage", e.getMessage());
            workItem.setDataVariable("errorType", "external_service");
            workItem.setDataVariable("retryable", String.valueOf(e.isRetryable()));

            // If retryable, put back in queue for retry
            if (e.isRetryable()) {
                engine.retryWorkItem(caseId, taskId, /* delay */ 5 * 60 * 1000);
            } else {
                // If not retryable, escalate to admin
                escalateToAdmin(caseId, taskId, e);
            }
        } catch (Exception ex) {
            log.severe("Failed to handle external service error: " + ex.getMessage());
        }
    }

    /**
     * Error handler: Unexpected errors
     * Log and escalate to support
     */
    private void handleUnexpectedError(YWorkItem workItem, String taskId, String caseId, Exception e) {
        log.severe("Unexpected error: " + e.getMessage());
        e.printStackTrace();
        try {
            // Log error for support team
            workItem.setDataVariable("errorMessage", e.getMessage());
            workItem.setDataVariable("errorType", "unexpected");
            workItem.setDataVariable("errorStackTrace", getStackTrace(e));

            // Escalate to support team
            escalateToAdmin(caseId, taskId, e);
        } catch (Exception ex) {
            log.severe("Failed to handle unexpected error: " + ex.getMessage());
        }
    }

    /**
     * Escalate case to administrator for manual handling
     */
    private void escalateToAdmin(String caseId, String taskId, Exception e) {
        log.info("Escalating case to admin: " + caseId);
        // Implementation depends on your escalation mechanism:
        // - Send notification (email, Slack, etc.)
        // - Create support ticket
        // - Assign to admin role
    }

    /**
     * Helper methods
     */

    private CustomerInfo fetchCustomerData(String customerId) {
        // Call to CRM system or database
        return new CustomerInfo(customerId);
    }

    private String checkOrderStatus(String orderId) {
        // Call to order management system
        return "pending";
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Inner classes for data structures
     */

    public static class ValidatedOrderData {
        public String customerId;
        public String orderId;
        public Double amount;
        public CustomerInfo customer;
        public String orderStatus;

        public ValidatedOrderData(String customerId, String orderId, Double amount,
                                  CustomerInfo customer, String orderStatus) {
            this.customerId = customerId;
            this.orderId = orderId;
            this.amount = amount;
            this.customer = customer;
            this.orderStatus = orderStatus;
        }
    }

    public static class CustomerInfo {
        private String customerId;
        private String tier;  // gold, silver, bronze
        private String paymentMethod;

        public CustomerInfo(String customerId) {
            this.customerId = customerId;
            this.tier = "bronze";  // Fetch from database
            this.paymentMethod = "credit_card";
        }

        public String getPaymentMethod() {
            return paymentMethod;
        }
    }

    /**
     * Custom exception classes
     */

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class ExternalServiceException extends Exception {
        private boolean retryable;

        public ExternalServiceException(String message) {
            super(message);
            this.retryable = true;
        }

        public ExternalServiceException(String message, Throwable cause) {
            super(message, cause);
            this.retryable = true;
        }

        public ExternalServiceException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    /**
     * Functional interfaces for retry logic
     */

    @FunctionalInterface
    public interface ServiceCall<T> {
        T execute() throws Exception;
    }

    /**
     * External service interfaces (implement with your service clients)
     */

    public interface ExternalServiceClient {
        PaymentResponse processPayment(PaymentRequest request) throws Exception;
    }

    public static class PaymentRequest {
        public String customerId;
        public String orderId;
        public Double amount;
        public String paymentMethod;

        public PaymentRequest(String customerId, String orderId, Double amount, String paymentMethod) {
            this.customerId = customerId;
            this.orderId = orderId;
            this.amount = amount;
            this.paymentMethod = paymentMethod;
        }
    }

    public static class PaymentResponse {
        private boolean successful;
        private String transactionId;
        private String errorMessage;

        // Constructor, getters
    }
}
