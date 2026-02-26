/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProMoAIPromptBuilder — verifies that all six ProMoAI prompting strategies
 * are correctly assembled for initial generation and self-correction prompts.
 *
 * <p>Reference: "Process Modeling With Large Language Models" (Kourani et al., 2024)
 * arXiv:2403.07541
 */
class ProMoAIPromptBuilderTest {

    private static final String SHORT_DESC = "Submit order then ship it.";
    private static final String LONG_DESC =
        "A customer submits a detailed online order through the web portal. " +
        "The warehouse team then picks and packs the items, after which the " +
        "logistics team arranges shipping with the preferred carrier. Once the " +
        "shipment is dispatched, the customer receives a tracking notification.";

    // ----- buildInitialPrompt — strategy presence -----

    @Test
    void buildInitialPrompt_shortDesc_containsRolePrompt() {
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, null);
        assertTrue(prompt.contains("expert in business process modeling"),
            "Strategy 1 (role prompting) must be present");
    }

    @Test
    void buildInitialPrompt_shortDesc_containsKnowledgeInjection() {
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, null);
        assertTrue(prompt.contains("SEQUENCE(a, b, ...)"),
            "Strategy 2 (knowledge injection) must include SEQUENCE definition");
        assertTrue(prompt.contains("LOOP(do_body, redo_body)"),
            "Strategy 2 (knowledge injection) must include LOOP definition");
        assertTrue(prompt.contains("PARALLEL(a, b, ...)"),
            "Strategy 2 (knowledge injection) must include PARALLEL definition");
    }

    @Test
    void buildInitialPrompt_shortDesc_containsFewShotExample() {
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, null);
        assertTrue(prompt.contains("submit_application"),
            "Strategy 3 (few-shot) must include the loan application example");
        assertTrue(prompt.contains("check_credit_score"),
            "Strategy 3 (few-shot) must include credit score activity");
    }

    @Test
    void buildInitialPrompt_shortDesc_containsNegativePrompting() {
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, null);
        assertTrue(prompt.contains("AVOID"),
            "Strategy 4 (negative prompting) must include AVOID guidance");
        assertTrue(prompt.contains("mutual exclusion"),
            "Strategy 4 (negative prompting) must explain XOR semantics");
    }

    @Test
    void buildInitialPrompt_shortDesc_noLeastToMost() {
        // SHORT_DESC is fewer than 150 characters — least-to-most should be absent
        assertTrue(SHORT_DESC.length() <= ProMoAIPromptBuilder.LEAST_TO_MOST_THRESHOLD,
            "Precondition: SHORT_DESC must be short enough to skip least-to-most");
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, null);
        assertFalse(prompt.contains("high-level phases"),
            "Strategy 5 (least-to-most) must NOT appear for short descriptions");
    }

    @Test
    void buildInitialPrompt_longDesc_containsLeastToMost() {
        // LONG_DESC exceeds 150 characters — least-to-most should be present
        assertTrue(LONG_DESC.length() > ProMoAIPromptBuilder.LEAST_TO_MOST_THRESHOLD,
            "Precondition: LONG_DESC must exceed threshold to trigger least-to-most");
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(LONG_DESC, null, null);
        assertTrue(prompt.contains("high-level phases"),
            "Strategy 5 (least-to-most) must appear for long descriptions");
    }

    @Test
    void buildInitialPrompt_endsWithPowlModelSuffix() {
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, null);
        assertTrue(prompt.endsWith("POWL model:"),
            "Prompt must end with 'POWL model:' to prime the LLM output");
    }

    @Test
    void buildInitialPrompt_withGraphBias_includedInPrompt() {
        String graphBias = "Prefer SEQUENCE-heavy models over deep XOR nesting.";
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, graphBias, null);
        assertTrue(prompt.contains(graphBias),
            "Graph bias (cross-round memory) must appear in the prompt");
    }

    @Test
    void buildInitialPrompt_withBoardBias_includedInPrompt() {
        String boardBias = "A peer found: SEQUENCE→XOR. Generate something different.";
        String prompt = ProMoAIPromptBuilder.buildInitialPrompt(SHORT_DESC, null, boardBias);
        assertTrue(prompt.contains(boardBias),
            "Board bias (horizontal ensemble) must appear in the prompt");
    }

    @Test
    void buildInitialPrompt_blankDescription_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> ProMoAIPromptBuilder.buildInitialPrompt("   ", null, null));
    }

    @Test
    void buildInitialPrompt_nullDescription_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> ProMoAIPromptBuilder.buildInitialPrompt(null, null, null));
    }

    // ----- buildCorrectionPrompt (Strategy 6: feedback integration) -----

    @Test
    void buildCorrectionPrompt_containsParseError() {
        String error = "Unexpected token at position 12: 'BROKEN'";
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, "BROKEN", error);
        assertTrue(prompt.contains(error),
            "Correction prompt must embed the parse error message");
    }

    @Test
    void buildCorrectionPrompt_containsPreviousAttempt() {
        String previous = "SEQUENCE(ACTIVITY(a), BROKEN_EXPRESSION";
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, previous, "error");
        assertTrue(prompt.contains(previous.strip()),
            "Correction prompt must embed the previous malformed attempt");
    }

    @Test
    void buildCorrectionPrompt_containsOriginalDescription() {
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, "prev", "err");
        assertTrue(prompt.contains(SHORT_DESC),
            "Correction prompt must include the original process description");
    }

    @Test
    void buildCorrectionPrompt_containsKnowledgeInjection() {
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, "prev", "err");
        assertTrue(prompt.contains("LOOP(do_body, redo_body)"),
            "Correction prompt must re-inject POWL knowledge so LLM can self-correct");
    }

    @Test
    void buildCorrectionPrompt_containsCommonCauses() {
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, "prev", "err");
        assertTrue(prompt.contains("mismatched parentheses"),
            "Correction prompt must list common parse-error causes");
        assertTrue(prompt.contains("at least 2 children"),
            "Correction prompt must mention arity constraint");
    }

    @Test
    void buildCorrectionPrompt_nullPreviousAttempt_usesPlaceholder() {
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, null, "err");
        assertTrue(prompt.contains("(empty)"),
            "Null previous attempt should use '(empty)' placeholder");
    }

    @Test
    void buildCorrectionPrompt_nullParseError_usesPlaceholder() {
        String prompt = ProMoAIPromptBuilder.buildCorrectionPrompt(SHORT_DESC, "prev", null);
        assertTrue(prompt.contains("(unknown error)"),
            "Null parse error should use '(unknown error)' placeholder");
    }

    @Test
    void buildCorrectionPrompt_blankDescription_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> ProMoAIPromptBuilder.buildCorrectionPrompt("  ", "prev", "err"));
    }
}
