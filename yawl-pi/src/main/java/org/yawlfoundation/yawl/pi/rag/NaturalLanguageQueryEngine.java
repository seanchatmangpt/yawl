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

import org.yawlfoundation.yawl.integration.zai.ZaiService;
import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.List;

/**
 * Natural language query engine for process intelligence.
 *
 * <p>Retrieves relevant process facts from the knowledge base and uses
 * a large language model (GLM-4) to generate natural language answers.
 * Falls back gracefully when Z.AI API is unavailable.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class NaturalLanguageQueryEngine {

    private final ProcessKnowledgeBase knowledgeBase;
    private final ZaiService zaiService;

    /**
     * Create a new natural language query engine.
     *
     * @param knowledgeBase process knowledge base
     * @param zaiService Z.AI service for LLM inference
     */
    public NaturalLanguageQueryEngine(ProcessKnowledgeBase knowledgeBase, ZaiService zaiService) {
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("Knowledge base cannot be null");
        }
        if (zaiService == null) {
            throw new IllegalArgumentException("ZAI service cannot be null");
        }
        this.knowledgeBase = knowledgeBase;
        this.zaiService = zaiService;
    }

    /**
     * Process a natural language query using RAG (Retrieval-Augmented Generation).
     *
     * <p>Retrieves relevant facts from the knowledge base and uses GLM-4 to generate
     * an answer grounded in those facts. If Z.AI API is unavailable, returns raw facts
     * as the answer without throwing an exception.</p>
     *
     * @param request the NL query request
     * @return natural language response with source facts and metadata
     * @throws PIException if retrieval from knowledge base fails
     */
    public NlQueryResponse query(NlQueryRequest request) throws PIException {
        if (request == null) {
            throw new PIException("Query request cannot be null", "rag");
        }

        long startTime = System.currentTimeMillis();

        // Retrieve relevant facts
        List<KnowledgeEntry> relevantFacts = knowledgeBase.retrieve(
            request.question(),
            request.topK()
        );

        // Extract fact texts for context
        List<String> sourceFacts = relevantFacts.stream()
            .map(KnowledgeEntry::factText)
            .toList();

        String answer;
        boolean groundedInKb = true;

        if (sourceFacts.isEmpty()) {
            // No facts available
            answer = "I do not have data on this. Please ingest process mining reports first.";
            groundedInKb = false;
        } else {
            // Build system prompt with facts
            String systemPrompt = buildSystemPrompt(sourceFacts);

            try {
                // Call Z.AI for answer generation
                answer = zaiService.chat(systemPrompt + "\n\nQuestion: " + request.question());
            } catch (IllegalStateException e) {
                // Z.AI API key missing - fallback to raw facts
                answer = formatFactsAsAnswer(sourceFacts);
                groundedInKb = true;  // Still grounded in KB, just not via LLM
            } catch (Exception e) {
                // Other Z.AI errors - fallback to raw facts
                answer = formatFactsAsAnswer(sourceFacts);
                groundedInKb = true;
            }
        }

        long latencyMs = System.currentTimeMillis() - startTime;

        return new NlQueryResponse(
            request.requestId(),
            answer,
            sourceFacts,
            groundedInKb,
            "GLM-4.7-Flash",
            latencyMs,
            Instant.now()
        );
    }

    /**
     * Build a system prompt for RAG query.
     *
     * @param facts relevant facts from knowledge base
     * @return system prompt string
     */
    private String buildSystemPrompt(List<String> facts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a YAWL process analyst. ");
        prompt.append("Answer questions using ONLY the following process facts. ");
        prompt.append("If the answer is not in the facts, say 'I do not have data on this.'\n\n");
        prompt.append("Facts:\n");

        for (int i = 0; i < facts.size(); i++) {
            prompt.append((i + 1)).append(". ").append(facts.get(i)).append("\n");
        }

        return prompt.toString();
    }

    /**
     * Format raw facts as a plain text answer for fallback scenarios.
     *
     * @param facts list of fact texts
     * @return formatted answer string
     */
    private String formatFactsAsAnswer(List<String> facts) {
        if (facts.isEmpty()) {
            return "No relevant data available.";
        }

        StringBuilder answer = new StringBuilder();
        answer.append("Based on available process data:\n\n");
        for (int i = 0; i < facts.size(); i++) {
            answer.append("- ").append(facts.get(i)).append("\n");
        }
        return answer.toString();
    }
}
