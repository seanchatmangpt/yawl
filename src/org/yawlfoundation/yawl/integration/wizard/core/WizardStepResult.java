package org.yawlfoundation.yawl.integration.wizard.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Generic result of executing a {@link WizardStep}.
 *
 * <p>Step results are immutable and capture both success and failure outcomes.
 * A successful result contains a value of type T; a failed result contains
 * a list of error messages. The stepId identifies which step produced this result.
 *
 * <p>Use factory methods {@link #success(String, Object)} and
 * {@link #failure(String, List)} to construct instances.
 *
 * @param success whether the step execution succeeded
 * @param value the step's output value (null if failed)
 * @param errors list of error messages (empty if successful)
 * @param stepId identifier of the step that produced this result
 * @param <T> the type of successful value
 */
public record WizardStepResult<T>(
    boolean success,
    T value,
    List<String> errors,
    String stepId
) {
    /**
     * Compact constructor ensures immutability of errors list.
     */
    public WizardStepResult {
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");

        // Defensive copy: ensure errors is immutable
        errors = Collections.unmodifiableList(List.copyOf(errors));
    }

    /**
     * Factory method: creates a successful result.
     *
     * @param stepId identifier of the step
     * @param value the step's output value
     * @param <T> the value type
     * @return successful result with empty errors list
     * @throws NullPointerException if stepId is null
     */
    public static <T> WizardStepResult<T> success(String stepId, T value) {
        Objects.requireNonNull(stepId, "stepId cannot be null");
        return new WizardStepResult<>(true, value, List.of(), stepId);
    }

    /**
     * Factory method: creates a failed result with a list of errors.
     *
     * @param stepId identifier of the step
     * @param errors list of error messages
     * @param <T> the value type (erased in failure case)
     * @return failed result with null value
     * @throws NullPointerException if stepId or errors is null
     */
    public static <T> WizardStepResult<T> failure(String stepId, List<String> errors) {
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");
        return new WizardStepResult<>(false, null, errors, stepId);
    }

    /**
     * Factory method: creates a failed result with a single error message.
     *
     * @param stepId identifier of the step
     * @param error the error message
     * @param <T> the value type (erased in failure case)
     * @return failed result with single error
     * @throws NullPointerException if stepId or error is null
     */
    public static <T> WizardStepResult<T> failure(String stepId, String error) {
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(error, "error cannot be null");
        return new WizardStepResult<>(false, null, List.of(error), stepId);
    }

    /**
     * Checks if this result represents success.
     *
     * @return true if execution succeeded
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the successful value, or empty if this is a failure.
     *
     * @return optional containing value if success, empty otherwise
     */
    public Optional<T> asOptional() {
        return Optional.ofNullable(value);
    }

    /**
     * Retrieves the first error message, if any failures occurred.
     *
     * @return optional containing first error if failed, empty if successful
     */
    public Optional<String> firstError() {
        if (errors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(errors.get(0));
    }

    /**
     * Gets the total count of errors in this result.
     *
     * @return number of error messages (0 if successful)
     */
    public int errorCount() {
        return errors.size();
    }
}
