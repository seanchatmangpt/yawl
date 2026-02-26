/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

/**
 * Constructs LLM prompts for POWL process model generation using the six
 * prompting strategies from the ProMoAI paper (Kourani et al., 2024).
 *
 * <p>Reference: "Process Modeling With Large Language Models",
 * Kourani, Berti, Schuster, van der Aalst — arXiv:2403.07541
 *
 * <p>Strategies implemented:
 * <ol>
 *   <li><strong>Role prompting</strong> — LLM acts as expert process modeler and process owner</li>
 *   <li><strong>Knowledge injection</strong> — complete POWL syntax reference</li>
 *   <li><strong>Few-shot learning</strong> — concrete loan-application example</li>
 *   <li><strong>Negative prompting</strong> — explicitly shows the most common modeling mistake</li>
 *   <li><strong>Least-to-most decomposition</strong> — activated for long descriptions (&gt;150 chars)</li>
 *   <li><strong>Feedback integration</strong> — used in correction prompts (iterative self-correction)</li>
 * </ol>
 *
 * <p>Empirical results from the ProMoAI paper: combining these six strategies yields
 * an average quality score of 0.93 (fitness × precision) with Claude-3.5-Sonnet, and
 * GPT-4 succeeds in initial model generation within 2 iterations on average.
 */
public final class ProMoAIPromptBuilder {

    /** Threshold in characters above which least-to-most decomposition is activated. */
    static final int LEAST_TO_MOST_THRESHOLD = 150;

    private static final String ROLE =
        "You are an expert in business process modeling, familiar with POWL " +
        "(Partially Ordered Workflow Language) and common workflow control-flow " +
        "patterns. You also act as the process owner to fill in unstated domain " +
        "knowledge about the described process.\n";

    private static final String KNOWLEDGE_INJECTION =
        "POWL syntax reference:\n" +
        "  ACTIVITY(label)           — atomic step with the given label\n" +
        "  SEQUENCE(a, b, ...)       — execute left to right in order\n" +
        "  XOR(a, b, ...)            — exactly one branch executes (exclusive choice)\n" +
        "  PARALLEL(a, b, ...)       — all branches execute concurrently\n" +
        "  LOOP(do_body, redo_body)  — do_body runs at least once; redo_body repeats it\n" +
        "  Nesting is allowed: XOR(SEQUENCE(ACTIVITY(a), ACTIVITY(b)), ACTIVITY(c))\n" +
        "  Output ONLY a single root expression. No code blocks, no explanation, no prose.\n";

    private static final String FEW_SHOT =
        "Example — Loan Application:\n" +
        "  Description: \"Customer submits a loan application. The bank checks the\n" +
        "  credit score and verifies documents in any order. Then the bank either\n" +
        "  approves or rejects the application. If approved, the loan is disbursed.\"\n" +
        "\n" +
        "  POWL model:\n" +
        "  SEQUENCE(\n" +
        "    ACTIVITY(submit_application),\n" +
        "    PARALLEL(ACTIVITY(check_credit_score), ACTIVITY(verify_documents)),\n" +
        "    XOR(\n" +
        "      SEQUENCE(ACTIVITY(approve_application), ACTIVITY(disburse_loan)),\n" +
        "      ACTIVITY(reject_application)\n" +
        "    )\n" +
        "  )\n";

    private static final String NEGATIVE_PROMPTING =
        "Common mistake to AVOID:\n" +
        "  WRONG: XOR(ACTIVITY(submit_form), ACTIVITY(review_form))\n" +
        "    — do not put sequential steps inside XOR; XOR means mutual exclusion.\n" +
        "  CORRECT: use SEQUENCE for steps that always happen; use XOR only for\n" +
        "  genuine exclusive choices where at most one branch executes.\n";

    private static final String LEAST_TO_MOST =
        "Strategy: first identify the 2-4 high-level phases of this process, " +
        "then expand each phase into its detailed steps before composing the final model.\n";

    private ProMoAIPromptBuilder() {}

    /**
     * Builds the initial generation prompt using all applicable ProMoAI strategies.
     *
     * <p>Strategies 1-4 are always included. Strategy 5 (least-to-most decomposition)
     * is activated when the description exceeds {@link #LEAST_TO_MOST_THRESHOLD} characters.
     * The cross-round memory bias (graphBias) and horizontal ensemble bias (boardBias)
     * are appended when non-blank.
     *
     * @param description the process description (required, non-blank)
     * @param graphBias   optional cross-round memory hint from ProcessKnowledgeGraph; null or blank to omit
     * @param boardBias   optional horizontal ensemble hint from DiscoveryBoard; null or blank to omit
     * @return the assembled prompt string ready for the LLM
     */
    public static String buildInitialPrompt(String description, String graphBias, String boardBias) {
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description must not be blank");

        StringBuilder sb = new StringBuilder();
        sb.append(ROLE).append('\n');
        sb.append(KNOWLEDGE_INJECTION).append('\n');
        sb.append(FEW_SHOT).append('\n');
        sb.append(NEGATIVE_PROMPTING).append('\n');

        // Strategy 5: least-to-most — only for complex descriptions
        if (description.length() > LEAST_TO_MOST_THRESHOLD) {
            sb.append(LEAST_TO_MOST).append('\n');
        }

        sb.append("Process description:\n").append(description).append('\n');

        // Cross-round memory bias (OpenSage Phase 4a — ProcessKnowledgeGraph)
        if (graphBias != null && !graphBias.isBlank()) {
            sb.append('\n').append(graphBias).append('\n');
        }
        // Horizontal ensemble bias (OpenSage Phase 4b — EnsembleDiscoveryBoard)
        if (boardBias != null && !boardBias.isBlank()) {
            sb.append('\n').append(boardBias).append('\n');
        }

        sb.append("\nPOWL model:");
        return sb.toString();
    }

    /**
     * Builds a self-correction prompt (ProMoAI strategy 6: feedback integration).
     *
     * <p>Embeds the original description, the failed previous attempt, and the parse
     * error so the LLM can self-correct without starting from scratch. The paper
     * reports that GPT-4 typically self-corrects within the first retry iteration.
     *
     * @param description     the original process description (required, non-blank)
     * @param previousAttempt the malformed POWL text that failed to parse; null treated as "(empty)"
     * @param parseError      the error message from {@link PowlParseException}; null treated as "(unknown error)"
     * @return the assembled correction prompt string ready for the LLM
     */
    public static String buildCorrectionPrompt(String description, String previousAttempt,
                                               String parseError) {
        if (description == null || description.isBlank())
            throw new IllegalArgumentException("description must not be blank");
        if (previousAttempt == null) previousAttempt = "(empty)";
        if (parseError == null) parseError = "(unknown error)";

        return KNOWLEDGE_INJECTION + '\n' +
            "Your previous POWL expression failed to parse with this error:\n" +
            "  " + parseError + "\n\n" +
            "Previous attempt:\n" +
            "  " + previousAttempt.strip() + "\n\n" +
            "Common causes of parse errors:\n" +
            "  - Missing or mismatched parentheses\n" +
            "  - LOOP must have exactly 2 children: LOOP(do_body, redo_body)\n" +
            "  - XOR/SEQUENCE/PARALLEL need at least 2 children\n" +
            "  - Labels must not contain special characters other than underscores\n\n" +
            "Original process description:\n" +
            "  " + description + "\n\n" +
            "Generate a corrected POWL model (single expression only):";
    }
}
