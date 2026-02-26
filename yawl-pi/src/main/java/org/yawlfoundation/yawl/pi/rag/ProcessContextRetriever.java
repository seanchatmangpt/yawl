/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.pi.rag;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Retrieves relevant process facts using keyword-based similarity search.
 *
 * <p>Tokenizes the query and scores knowledge entries by counting matching
 * words in their fact text. Returns top-K entries sorted by relevance score.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ProcessContextRetriever {

    private static final Pattern TOKENIZER_PATTERN = Pattern.compile("[\\s\\p{P}]+");

    /**
     * Create a new context retriever.
     */
    public ProcessContextRetriever() {
    }

    /**
     * Retrieve relevant facts using keyword-based scoring.
     *
     * @param entries candidate knowledge entries
     * @param query user query text
     * @param topK maximum number of entries to return
     * @return top-K entries sorted by relevance (highest first)
     */
    public List<KnowledgeEntry> retrieveRelevant(
            List<KnowledgeEntry> entries,
            String query,
            int topK) {

        if (entries == null || entries.isEmpty() || query == null || query.isEmpty()) {
            return List.of();
        }

        // Tokenize query
        Set<String> queryTokens = tokenize(query);

        // Score each entry
        List<ScoredEntry> scored = new ArrayList<>();
        for (KnowledgeEntry entry : entries) {
            int matchCount = countMatches(entry.factText(), queryTokens);
            if (matchCount > 0) {
                scored.add(new ScoredEntry(entry, matchCount));
            }
        }

        // Sort by score (descending), then by ingestion time (most recent first)
        scored.sort((a, b) -> {
            int cmp = Integer.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return b.entry.ingestedAt().compareTo(a.entry.ingestedAt());
        });

        // Extract top-K entries
        return scored.stream()
            .limit(topK)
            .map(s -> s.entry)
            .toList();
    }

    /**
     * Tokenize text into lowercase words.
     *
     * @param text text to tokenize
     * @return set of tokens
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String[] parts = TOKENIZER_PATTERN.split(text.toLowerCase());
        for (String part : parts) {
            if (!part.isEmpty()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /**
     * Count matching tokens in fact text.
     *
     * @param factText text to search
     * @param queryTokens tokens to match
     * @return count of matching tokens
     */
    private int countMatches(String factText, Set<String> queryTokens) {
        Set<String> factTokens = tokenize(factText);
        int matches = 0;
        for (String token : queryTokens) {
            if (factTokens.contains(token)) {
                matches++;
            }
        }
        return matches;
    }

    /**
     * Internal class for scoring entries.
     */
    private record ScoredEntry(KnowledgeEntry entry, int score) {}
}
