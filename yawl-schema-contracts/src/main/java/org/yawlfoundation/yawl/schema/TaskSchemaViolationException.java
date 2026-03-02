package org.yawlfoundation.yawl.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when a YAWL task's data violates its declared ODCS schema contract.
 *
 * <p>This exception represents a data quality failure at a task boundary — required fields
 * are missing from the task's input or output data relative to the declared ODCS contract.
 * It is an expected domain condition (not a programming error), so stack trace generation
 * is suppressed for performance and log clarity.</p>
 *
 * <h2>Example message</h2>
 * <pre>
 * Task 'ProcessOrder' [case C-42] INPUT schema violation (contract: contracts/orders-v2.yaml):
 *   MISSING_FIELD: 'total_amount' (expected: present, actual: missing)
 *   MISSING_FIELD: 'currency' (expected: present, actual: missing)
 * </pre>
 *
 * @since 6.0.0
 */
public final class TaskSchemaViolationException extends RuntimeException {

    private final String taskId;
    private final String caseId;
    private final String contractPath;
    private final boolean inputViolation;
    private final List<SchemaViolation> violations;

    /**
     * Creates a new schema violation exception.
     *
     * @param taskId         the YAWL task ID (from {@code YWorkItem.getTaskID()})
     * @param caseId         the case ID string (from {@code YWorkItem.getCaseID().toString()})
     * @param contractPath   the classpath path to the ODCS contract YAML
     * @param inputViolation {@code true} if this is an input boundary violation, {@code false} for output
     * @param violations     the list of field-level violations found; must not be empty
     */
    public TaskSchemaViolationException(String taskId, String caseId, String contractPath,
                                        boolean inputViolation, List<SchemaViolation> violations) {
        super(buildMessage(taskId, caseId, contractPath, inputViolation, violations));
        this.taskId       = taskId;
        this.caseId       = caseId;
        this.contractPath = contractPath;
        this.inputViolation = inputViolation;
        this.violations   = Collections.unmodifiableList(new ArrayList<>(violations));
    }

    /** Returns the YAWL task ID that failed validation. */
    public String getTaskId() { return taskId; }

    /** Returns the case ID string. */
    public String getCaseId() { return caseId; }

    /** Returns the classpath path to the ODCS contract that was violated. */
    public String getContractPath() { return contractPath; }

    /**
     * Returns {@code true} if the violation is on the task's input boundary,
     * {@code false} if it is on the output boundary.
     */
    public boolean isInputViolation() { return inputViolation; }

    /** Returns an immutable copy of the field-level violations found. */
    public List<SchemaViolation> getViolations() { return violations; }

    /**
     * Suppresses stack trace generation — this is an expected domain condition,
     * not a programming error. Suppression improves performance under high task
     * throughput and keeps logs readable.
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static String buildMessage(String taskId, String caseId, String contractPath,
                                       boolean inputViolation, List<SchemaViolation> violations) {
        String boundary = inputViolation ? "INPUT" : "OUTPUT";
        StringBuilder sb = new StringBuilder();
        sb.append("Task '").append(taskId).append("'")
          .append(" [case ").append(caseId).append("]")
          .append(" ").append(boundary).append(" schema violation")
          .append(" (contract: ").append(contractPath).append("):\n");
        for (SchemaViolation v : violations) {
            sb.append("  ").append(v.describe()).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
