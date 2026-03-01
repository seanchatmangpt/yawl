package org.yawlfoundation.yawl.patterns.chaos;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Base class for chaos engineering - failure injection framework.
 * Simulates latency, crashes, and resource exhaustion.
 */
public class ChaosInjector {
    private final AtomicInteger injectionCount = new AtomicInteger(0);

    /**
     * Inject random latency between minMs and maxMs
     */
    public void injectLatency(long minMs, long maxMs) {
        long delayMs = minMs + (long) (Math.random() * (maxMs - minMs));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        injectionCount.incrementAndGet();
    }

    /**
     * Probabilistic failure injection - fails X% of operations
     */
    public void injectFailure(double failureRate) throws Exception {
        if (Math.random() < failureRate) {
            injectionCount.incrementAndGet();
            throw new Exception("Chaos-injected failure");
        }
    }

    /**
     * Simulate actor crash - throws exception that mimics actor death
     */
    public void injectActorCrash(String actorName) {
        injectionCount.incrementAndGet();
        throw new RuntimeException("Chaos-injected crash: actor " + actorName);
    }

    /**
     * Probabilistic timeout on message delivery
     */
    public boolean shouldTimeout(double timeoutRate) {
        if (Math.random() < timeoutRate) {
            injectionCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Simulate message loss (silent drop)
     */
    public boolean shouldDropMessage(double dropRate) {
        if (Math.random() < dropRate) {
            injectionCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Simulate message reordering - return true if this message should be reordered
     */
    public boolean shouldReorder(double reorderRate) {
        if (Math.random() < reorderRate) {
            injectionCount.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Get total number of injections performed
     */
    public int getInjectionCount() {
        return injectionCount.get();
    }

    /**
     * Reset injection counter
     */
    public void reset() {
        injectionCount.set(0);
    }

    /**
     * Execute operation with chaos wrapper
     */
    public <T> T executeWithChaos(ChaosOperation<T> operation, double failureRate) throws Exception {
        injectFailure(failureRate);
        return operation.execute();
    }

    /**
     * Functional interface for chaos-wrapped operations
     */
    @FunctionalInterface
    public interface ChaosOperation<T> {
        T execute() throws Exception;
    }
}
