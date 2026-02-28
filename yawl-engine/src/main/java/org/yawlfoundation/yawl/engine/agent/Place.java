package org.yawlfoundation.yawl.engine.agent;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a Place in a Petri net model within the YAWL workflow engine.
 * A place holds tokens that represent work items or control flow state.
 *
 * Thread-safe: Uses AtomicInteger for token count to support concurrent
 * access from multiple agents or transitions.
 *
 * Immutable fields: id and name. Mutable: tokenCount (via atomic operations).
 *
 * @since Java 21
 */
public final class Place {

    private final String id;
    private final String name;
    private final AtomicInteger tokenCount;

    /**
     * Create a new place with initial token count.
     *
     * @param id Unique identifier for this place (typically matches XML element ID)
     * @param name Human-readable name for this place
     * @param initialTokens Initial number of tokens in this place
     */
    public Place(String id, String name, int initialTokens) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        if (initialTokens < 0) {
            throw new IllegalArgumentException("initialTokens cannot be negative");
        }
        this.tokenCount = new AtomicInteger(initialTokens);
    }

    /**
     * Create a new place with zero initial tokens.
     *
     * @param id Unique identifier for this place
     * @param name Human-readable name for this place
     */
    public Place(String id, String name) {
        this(id, name, 0);
    }

    /**
     * Get the unique identifier for this place.
     *
     * @return place ID
     */
    public String id() {
        return id;
    }

    /**
     * Get the human-readable name of this place.
     *
     * @return place name
     */
    public String name() {
        return name;
    }

    /**
     * Get the current number of tokens in this place (atomic read).
     *
     * @return current token count
     */
    public int getTokenCount() {
        return tokenCount.get();
    }

    /**
     * Add a token to this place atomically.
     * This represents work arriving at this place in the workflow.
     *
     * @return updated token count after addition
     */
    public int addToken() {
        return tokenCount.incrementAndGet();
    }

    /**
     * Add multiple tokens to this place atomically.
     *
     * @param count number of tokens to add (must be non-negative)
     * @return updated token count after addition
     */
    public int addTokens(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        return tokenCount.addAndGet(count);
    }

    /**
     * Remove a token from this place atomically.
     * This represents work leaving this place to fire an outbound transition.
     *
     * @return updated token count after removal, or -1 if no tokens available
     */
    public int removeToken() {
        while (true) {
            int current = tokenCount.get();
            if (current == 0) {
                return -1; // No tokens available
            }
            if (tokenCount.compareAndSet(current, current - 1)) {
                return current - 1;
            }
        }
    }

    /**
     * Try to remove tokens atomically (all-or-nothing).
     *
     * @param count number of tokens to remove
     * @return true if tokens were removed, false if insufficient tokens
     */
    public boolean removeTokens(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count cannot be negative");
        }
        while (true) {
            int current = tokenCount.get();
            if (current < count) {
                return false; // Not enough tokens
            }
            if (tokenCount.compareAndSet(current, current - count)) {
                return true;
            }
        }
    }

    /**
     * Check if this place has at least one token available.
     *
     * @return true if token count > 0
     */
    public boolean hasTokens() {
        return tokenCount.get() > 0;
    }

    /**
     * Check if this place has sufficient tokens for a transition.
     *
     * @param required number of tokens required
     * @return true if token count >= required
     */
    public boolean hasTokens(int required) {
        if (required < 0) {
            throw new IllegalArgumentException("required cannot be negative");
        }
        return tokenCount.get() >= required;
    }

    /**
     * Clear all tokens from this place.
     *
     * @return number of tokens that were in this place
     */
    public int clearTokens() {
        return tokenCount.getAndSet(0);
    }

    /**
     * Check equality based on place ID and name (structural identity).
     * Token count is excluded from equality as it represents dynamic state.
     *
     * @param obj object to compare with
     * @return true if same id and name
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Place other)) return false;
        return id.equals(other.id) && name.equals(other.name);
    }

    /**
     * Hash based on id and name (consistent with equals).
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    /**
     * Human-readable representation including token count.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "Place{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", tokens=" + getTokenCount() +
                '}';
    }
}
