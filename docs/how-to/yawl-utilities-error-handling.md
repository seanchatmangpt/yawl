# How-To: Handle Errors Gracefully with YAWL Utilities

Learn to implement robust error handling using YAWL's exception framework.

## Catch and Handle YAWL Exceptions

**Goal**: Implement granular error handling for different failure modes.

### Steps

1. **Catch specific exception types**
```java
import org.yawlfoundation.yawl.exceptions.*;

try {
    // Load and validate specification
    YSpecification spec = loadSpecification("workflow.yawl");
    engine.loadSpecification(spec);

} catch (YSyntaxException e) {
    // XML parsing or YAWL grammar error
    System.err.println("Invalid YAWL syntax: " + e.getMessage());
    logError("SYNTAX_ERROR", e);

} catch (YConnectivityException e) {
    // Network or remote service failure
    System.err.println("Cannot reach service: " + e.getMessage());
    logError("CONNECTIVITY_ERROR", e);

} catch (YDataStateException e) {
    // Object in invalid state
    System.err.println("Invalid state: " + e.getMessage());
    logError("STATE_ERROR", e);

} catch (YException e) {
    // Catch-all for other YAWL errors
    System.err.println("YAWL error: " + e.getMessage());
    logError("YAWL_ERROR", e);

} catch (Exception e) {
    // Unexpected non-YAWL errors
    System.err.println("Unexpected error: " + e.getMessage());
    logError("UNEXPECTED_ERROR", e);
}
```

2. **Create custom error handler**
```java
public class YAWLErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(
        YAWLErrorHandler.class
    );

    public static void handle(YException e, String context) {
        String errorCode = classifyError(e);
        String message = buildErrorMessage(e, context);

        logger.error(message, e);

        // Route to appropriate handler
        switch (errorCode) {
            case "SYNTAX_ERROR":
                handleSyntaxError(e);
                break;
            case "CONNECTIVITY_ERROR":
                handleConnectivityError(e);
                break;
            case "STATE_ERROR":
                handleStateError(e);
                break;
            default:
                handleGenericError(e);
        }

        // Optionally notify user
        notifyUser(message);
    }

    private static String classifyError(YException e) {
        if (e instanceof YSyntaxException) return "SYNTAX_ERROR";
        if (e instanceof YConnectivityException) return "CONNECTIVITY_ERROR";
        if (e instanceof YDataStateException) return "STATE_ERROR";
        return "UNKNOWN_ERROR";
    }

    private static String buildErrorMessage(YException e, String context) {
        return String.format(
            "[%s] %s\nContext: %s\nCause: %s",
            e.getClass().getSimpleName(),
            e.getMessage(),
            context,
            e.getCause() != null ? e.getCause().getMessage() : "none"
        );
    }

    private static void handleSyntaxError(YException e) {
        // Provide helpful syntax hints
        String message = "Check YAWL syntax: " + extractHint(e.getMessage());
        System.err.println(message);
    }

    private static void handleConnectivityError(YException e) {
        // Implement retry logic
        System.err.println("Retrying connection...");
    }

    private static void handleStateError(YException e) {
        // Log state for debugging
        System.err.println("Object state: " + e.getMessage());
    }

    private static void handleGenericError(YException e) {
        // Default handling
        e.printStackTrace();
    }

    private static String extractHint(String message) {
        if (message.contains("unexpected")) return "Check element syntax";
        if (message.contains("missing")) return "Verify all required elements";
        if (message.contains("invalid")) return "Check attribute values";
        return "See YAWL schema documentation";
    }

    private static void notifyUser(String message) {
        // Send notification (email, UI alert, etc.)
    }
}
```

## Validate Data Before Processing

**Goal**: Prevent errors by validating input early.

### Steps

1. **Create validation helper**
```java
public class DataValidator {
    private static final Logger logger = LoggerFactory.getLogger(
        DataValidator.class
    );

    public static void validateCaseData(Document caseData,
                                       YSpecification spec)
            throws YException {
        if (caseData == null) {
            throw new YException("Case data cannot be null");
        }

        if (spec == null) {
            throw new YException("Specification cannot be null");
        }

        // Validate against spec's data schema
        String dataSchema = spec.getDataType();
        if (dataSchema != null) {
            validateAgainstSchema(caseData, dataSchema);
        }
    }

    public static void validateWorkItemOutput(YWorkItem item,
                                              Document outputData)
            throws YException {
        if (item == null) {
            throw new YException("Work item cannot be null");
        }

        // Check output data matches task's output schema
        Document expectedSchema = item.getDataOutput();
        if (expectedSchema != null && outputData != null) {
            if (!schemaMatches(outputData, expectedSchema)) {
                throw new YException(
                    "Output data does not match task schema: " +
                    item.getTaskName()
                );
            }
        }
    }

    public static void validateSpecification(YSpecification spec)
            throws YException {
        if (!spec.isValid()) {
            List<String> errors = spec.getValidationErrors();
            String message = String.join("\n", errors);
            throw new YException("Specification is not valid:\n" + message);
        }
    }

    private static void validateAgainstSchema(Document data,
                                            String schema)
            throws YException {
        try {
            // Implementation: compare data against schema
            // Using javax.xml.validation or similar
        } catch (Exception e) {
            throw new YException("Validation failed: " + e.getMessage());
        }
    }

    private static boolean schemaMatches(Document data,
                                        Document schema) {
        // Implementation: verify data conforms to schema
        return true;
    }
}
```

2. **Use validation before critical operations**
```java
public String createCaseWithValidation(String specID,
                                       Document caseData,
                                       YSpecification spec)
        throws YException {
    // Validate first
    DataValidator.validateCaseData(caseData, spec);
    DataValidator.validateSpecification(spec);

    // Then proceed
    return engine.createCase(specID, caseData);
}

public void completeWorkItemWithValidation(YWorkItem item,
                                          Document outputData)
        throws YException {
    // Validate output before completion
    DataValidator.validateWorkItemOutput(item, outputData);

    // Proceed with completion
    engine.completeWorkItem(item, outputData, null, true);
}
```

## Implement Retry Logic with Exponential Backoff

**Goal**: Automatically retry transient failures.

### Steps

1. **Create retry wrapper**
```java
public class RetryableOperation<T> {
    private static final Logger logger = LoggerFactory.getLogger(
        RetryableOperation.class
    );

    private int maxRetries;
    private long initialDelayMs;
    private double backoffMultiplier;

    public RetryableOperation() {
        this.maxRetries = 3;
        this.initialDelayMs = 100;
        this.backoffMultiplier = 2.0;
    }

    public T execute(String operationName,
                    Function<Void, T> operation)
            throws Exception {
        int attempt = 0;
        long delayMs = initialDelayMs;

        while (attempt <= maxRetries) {
            try {
                logger.info("Attempting: " + operationName +
                          " (attempt " + (attempt + 1) + ")");
                return operation.apply(null);

            } catch (YConnectivityException e) {
                // Retry transient connectivity errors
                if (attempt >= maxRetries) {
                    logger.error("Failed after " + (attempt + 1) +
                               " attempts", e);
                    throw e;
                }

                logger.warn("Connectivity error, retrying in " + delayMs +
                          "ms: " + e.getMessage());
                Thread.sleep(delayMs);

                delayMs = (long) (delayMs * backoffMultiplier);
                attempt++;

            } catch (YException e) {
                // Don't retry permanent errors
                logger.error("Permanent error: " + e.getMessage(), e);
                throw e;
            }
        }

        throw new YException("Operation failed after " + maxRetries +
                           " retries");
    }
}
```

2. **Use the retry wrapper**
```java
RetryableOperation<String> retryOp = new RetryableOperation<>();

try {
    String caseID = retryOp.execute("CreateCase", (v) -> {
        return engine.createCase(specID, caseData);
    });
    System.out.println("Case created: " + caseID);

} catch (Exception e) {
    System.err.println("Failed to create case: " + e.getMessage());
}
```

## Log and Track Error Patterns

**Goal**: Monitor errors for analysis and trend detection.

### Steps

1. **Create error tracking logger**
```java
public class ErrorTracker {
    private static final Logger logger = LoggerFactory.getLogger(
        ErrorTracker.class
    );

    private static final Map<String, Integer> errorCounts =
        new ConcurrentHashMap<>();
    private static final Map<String, Long> lastErrorTime =
        new ConcurrentHashMap<>();

    public static void logError(String errorType, Throwable cause) {
        // Track frequency
        errorCounts.merge(errorType, 1, Integer::sum);
        lastErrorTime.put(errorType, System.currentTimeMillis());

        // Log with context
        logger.error("Error: " + errorType, cause);

        // Check for error surge
        if (errorCounts.get(errorType) > 5) {
            logger.warn("High error rate detected: " + errorType +
                       " (" + errorCounts.get(errorType) + " in recent time)");
            notifyAdministrators(errorType);
        }
    }

    public static Map<String, Integer> getErrorCounts() {
        return new HashMap<>(errorCounts);
    }

    public static void reset() {
        errorCounts.clear();
        lastErrorTime.clear();
    }

    private static void notifyAdministrators(String errorType) {
        // Send alert email, Slack message, etc.
    }
}
```

2. **Generate error reports**
```java
public void generateErrorReport() {
    Map<String, Integer> errors = ErrorTracker.getErrorCounts();

    System.out.println("Error Summary:");
    System.out.println("==============");

    errors.forEach((errorType, count) -> {
        System.out.printf("%-30s: %d occurrences\n", errorType, count);
    });

    // Identify top errors
    List<Map.Entry<String, Integer>> sortedErrors = errors.entrySet()
        .stream()
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .collect(Collectors.toList());

    System.out.println("\nTop 3 errors:");
    sortedErrors.stream()
        .limit(3)
        .forEach(e -> System.out.println("  - " + e.getKey() +
                                       " (" + e.getValue() + ")"));
}
```

## Implement Graceful Degradation

**Goal**: Continue operation when non-critical functionality fails.

### Steps

```java
public void executeWorkflowWithDegradation(String caseID) {
    try {
        // Try primary path
        executeStandardWorkflow(caseID);

    } catch (YConnectivityException e) {
        logger.warn("Connectivity issue, switching to degraded mode", e);
        // Use cached data, skip external service calls
        executeDegradedWorkflow(caseID);

    } catch (YException e) {
        logger.error("Workflow execution failed", e);
        // Attempt emergency cleanup
        try {
            engine.removeCaseFromEngine(caseID);
        } catch (Exception cleanup) {
            logger.error("Cleanup failed", cleanup);
        }
    }
}

private void executeDegradedWorkflow(String caseID) {
    // Simplified execution without external dependencies
    logger.info("Running in degraded mode for case: " + caseID);

    try {
        // Still try to advance workflow with cached data
        Set<YWorkItem> items = engine.getEnabledWorkItems(caseID);
        for (YWorkItem item : items) {
            // Use fallback data
            Document fallbackOutput = createFallbackOutput(item);
            engine.completeWorkItem(item, fallbackOutput, null, true);
        }

        logger.info("Degraded execution completed");

    } catch (YException e) {
        logger.error("Even degraded execution failed", e);
    }
}

private Document createFallbackOutput(YWorkItem item) {
    // Create minimal output to keep workflow moving
    Document fallback = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .newDocument();

    Element root = fallback.createElement("fallback");
    root.setAttribute("source", "degraded_mode");
    fallback.appendChild(root);

    return fallback;
}
```

---

For additional resources, see:
- [Error Codes Reference](../reference/error-codes.md)
- [Exception Hierarchy](../reference/security-policy.md)
- [Troubleshooting Guide](./troubleshooting.md)
