package org.yawlfoundation.yawl.test.telemetry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks token usage from LLM-based tests.
 *
 * Integrates with Groq API and OpenAI gpt-oss-20b for token counting.
 */
public class LLMTokenCounter {

    private final ConcurrentHashMap<String, AtomicLong> tokenCounts = new ConcurrentHashMap<>();

    /**
     * Record tokens used by an LLM call.
     *
     * @param llmName LLM identifier (e.g., "groq", "openai-gpt-oss-20b")
     * @param tokenCount number of tokens used
     */
    public void recordTokens(String llmName, long tokenCount) {
        tokenCounts.computeIfAbsent(llmName, k -> new AtomicLong(0))
                .addAndGet(tokenCount);
    }

    /**
     * Get token counts by LLM.
     */
    public ConcurrentHashMap<String, Long> getTokenCounts() {
        var result = new ConcurrentHashMap<String, Long>();
        tokenCounts.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * Get total tokens across all LLMs.
     */
    public long getTotalTokens() {
        return tokenCounts.values().stream()
                .mapToLong(AtomicLong::get)
                .sum();
    }

    /**
     * Get token count for specific LLM.
     */
    public long getTokensFor(String llmName) {
        return tokenCounts.getOrDefault(llmName, new AtomicLong(0)).get();
    }

    /**
     * Reset all token counts.
     */
    public void reset() {
        tokenCounts.clear();
    }
}
