package org.yawlfoundation.yawl.integration.autonomous.resilience;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Graceful degradation handler with primary and fallback operations.
 * Attempts primary operation first, falls back to secondary if primary fails.
 *
 * Thread-safe for concurrent use.
 *
 * @author YAWL Production Validator
 * @version 5.2
 */
public class FallbackHandler {

    private static final Logger logger = LoggerFactory.getLogger(FallbackHandler.class);

    /**
     * Execute operation with fallback.
     * Tries primary operation first, uses fallback if primary fails.
     *
     * @param primary Primary operation to attempt
     * @param fallback Fallback operation if primary fails (can be null)
     * @param operationName Name for logging
     * @param <T> Return type
     * @return Result from primary or fallback operation
     * @throws Exception If both primary and fallback fail, or fallback is null
     */
    public <T> T executeWithFallback(
            Callable<T> primary,
            Callable<T> fallback,
            String operationName) throws Exception {

        if (primary == null) {
            throw new IllegalArgumentException("primary operation cannot be null");
        }

        String opName = (operationName != null && !operationName.trim().isEmpty())
                        ? operationName
                        : "unnamed operation";

        try {
            return primary.call();

        } catch (Exception primaryException) {
            logger.warn("Primary operation [{}] failed: {}, attempting fallback",
                       opName, primaryException.getMessage());

            if (fallback == null) {
                logger.error("No fallback available for operation [{}], throwing exception", opName);
                throw new UnsupportedOperationException(
                    "Operation [" + opName + "] failed and no fallback is available",
                    primaryException);
            }

            try {
                T result = fallback.call();
                logger.info("Fallback operation [{}] succeeded", opName);
                return result;

            } catch (Exception fallbackException) {
                logger.error("Fallback operation [{}] also failed: {}",
                            opName, fallbackException.getMessage());

                Exception combined = new Exception(
                    "Both primary and fallback operations failed for [" + opName + "]",
                    primaryException);
                combined.addSuppressed(fallbackException);
                throw combined;
            }
        }
    }

    /**
     * Execute operation with fallback, converting checked exceptions to runtime.
     *
     * @param primary Primary operation to attempt
     * @param fallback Fallback operation if primary fails (can be null)
     * @param operationName Name for logging
     * @param <T> Return type
     * @return Result from primary or fallback operation
     * @throws RuntimeException If both operations fail or fallback is null
     */
    public <T> T executeWithFallbackUnchecked(
            Callable<T> primary,
            Callable<T> fallback,
            String operationName) {

        try {
            return executeWithFallback(primary, fallback, operationName);
        } catch (Exception e) {
            throw new RuntimeException("Fallback execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute operation with value fallback.
     * Returns fallback value if primary operation fails.
     *
     * @param primary Primary operation to attempt
     * @param fallbackValue Value to return if primary fails
     * @param operationName Name for logging
     * @param <T> Return type
     * @return Result from primary operation or fallback value
     */
    public <T> T executeWithValueFallback(
            Callable<T> primary,
            T fallbackValue,
            String operationName) {

        String opName = (operationName != null && !operationName.trim().isEmpty())
                        ? operationName
                        : "unnamed operation";

        try {
            return primary.call();

        } catch (Exception e) {
            logger.warn("Primary operation [{}] failed: {}, using fallback value",
                       opName, e.getMessage());
            return fallbackValue;
        }
    }

    /**
     * Execute operation with supplier fallback.
     * Convenience method for operations that don't throw checked exceptions.
     *
     * @param primary Primary operation
     * @param fallback Fallback operation (can be null)
     * @param operationName Name for logging
     * @param <T> Return type
     * @return Result from primary or fallback
     * @throws UnsupportedOperationException If fallback is null and primary fails
     */
    public <T> T executeWithFallback(
            java.util.function.Supplier<T> primary,
            java.util.function.Supplier<T> fallback,
            String operationName) {

        if (primary == null) {
            throw new IllegalArgumentException("primary operation cannot be null");
        }

        String opName = (operationName != null && !operationName.trim().isEmpty())
                        ? operationName
                        : "unnamed operation";

        try {
            return primary.get();

        } catch (Exception primaryException) {
            logger.warn("Primary operation [{}] failed: {}, attempting fallback",
                       opName, primaryException.getMessage());

            if (fallback == null) {
                logger.error("No fallback available for operation [{}], throwing exception", opName);
                throw new UnsupportedOperationException(
                    "Operation [" + opName + "] failed and no fallback is available",
                    primaryException);
            }

            try {
                T result = fallback.get();
                logger.info("Fallback operation [{}] succeeded", opName);
                return result;

            } catch (Exception fallbackException) {
                logger.error("Fallback operation [{}] also failed: {}",
                            opName, fallbackException.getMessage());

                RuntimeException combined = new RuntimeException(
                    "Both primary and fallback operations failed for [" + opName + "]",
                    primaryException);
                combined.addSuppressed(fallbackException);
                throw combined;
            }
        }
    }

    /**
     * Create a chained fallback handler.
     * Tries operations in sequence until one succeeds.
     *
     * @param operations Operations to try in order (at least one required)
     * @param operationName Name for logging
     * @param <T> Return type
     * @return Result from first successful operation
     * @throws Exception If all operations fail
     */
    public <T> T executeWithChainedFallbacks(
            Callable<T>[] operations,
            String operationName) throws Exception {

        if (operations == null || operations.length == 0) {
            throw new IllegalArgumentException("At least one operation is required");
        }

        String opName = (operationName != null && !operationName.trim().isEmpty())
                        ? operationName
                        : "unnamed operation";

        Exception lastException = null;

        for (int i = 0; i < operations.length; i++) {
            Callable<T> operation = operations[i];

            if (operation == null) {
                logger.warn("Operation {} is null, skipping", i);
                continue;
            }

            try {
                T result = operation.call();

                if (i > 0) {
                    logger.info("Fallback operation {} succeeded for [{}]", i, opName);
                }

                return result;

            } catch (Exception e) {
                lastException = e;

                if (i == operations.length - 1) {
                    logger.error("All {} operations failed for [{}]",
                                operations.length, opName);
                } else {
                    logger.warn("Operation {} failed for [{}]: {}, trying next fallback",
                               i, opName, e.getMessage());
                }
            }
        }

        throw new Exception(
            "All " + operations.length + " operations failed for [" + opName + "]",
            lastException);
    }
}
