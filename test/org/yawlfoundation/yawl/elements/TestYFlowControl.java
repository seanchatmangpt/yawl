/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.util.YVerificationMessage;

/**
 * Comprehensive tests for YFlow, YCondition, YInputCondition, and YOutputCondition.
 * Tests real YAWL flow control mechanisms including token handling, condition evaluation,
 * and flow predicates.
 *
 * Chicago TDD methodology - tests real objects, no mocks.
 */
@DisplayName("YFlow Control Tests")
@Tag("unit")
class TestYFlowControl {

    private YSpecification specification;
    private YNet net;
    private YVerificationHandler handler;

    @BeforeEach
    void setUpBaseFixture() {
        specification = new YSpecification("test-spec-flow-control");
        specification.setVersion(YSchemaVersion.Beta4);
        net = new YNet("test-net", specification);
        handler = new YVerificationHandler();
    }

    // =========================================================================
    // YFlow Tests
    // =========================================================================

    @Nested
    @DisplayName("YFlow Tests")
    class YFlowTests {

        @Test
        @DisplayName("Flow connects prior element to next element")
        void flowConnectsPriorToNext() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(task, condition);

            assertEquals(task, flow.getPriorElement(), "Prior element should be the task");
            assertEquals(condition, flow.getNextElement(), "Next element should be the condition");
        }

        @Test
        @DisplayName("Flow supports XPath predicate for conditional routing")
        void flowSupportsXpathPredicate() {
            YAtomicTask xorSplit = new YAtomicTask("xor-split", YTask._AND, YTask._XOR, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(xorSplit, condition);

            assertNull(flow.getXpathPredicate(), "Predicate should initially be null");

            flow.setXpathPredicate("/data/amount > 100");
            assertEquals("/data/amount > 100", flow.getXpathPredicate(), "Predicate should be set");
        }

        @Test
        @DisplayName("Flow supports evaluation ordering for XOR splits")
        void flowSupportsEvalOrdering() {
            YAtomicTask xorSplit = new YAtomicTask("xor-split", YTask._AND, YTask._XOR, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(xorSplit, condition);

            assertNull(flow.getEvalOrdering(), "Eval ordering should initially be null");

            flow.setEvalOrdering(5);
            assertEquals(5, flow.getEvalOrdering(), "Eval ordering should be set");
        }

        @Test
        @DisplayName("Flow supports default flow marker")
        void flowSupportsDefaultFlowMarker() {
            YAtomicTask xorSplit = new YAtomicTask("xor-split", YTask._AND, YTask._XOR, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(xorSplit, condition);

            assertFalse(flow.isDefaultFlow(), "Should not be default flow initially");

            flow.setIsDefaultFlow(true);
            assertTrue(flow.isDefaultFlow(), "Should be default flow after setting");
        }

        @Test
        @DisplayName("Flow supports documentation")
        void flowSupportsDocumentation() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(task, condition);

            assertNull(flow.getDocumentation(), "Documentation should initially be null");

            flow.setDocumentation("This flow routes high-priority items");
            assertEquals("This flow routes high-priority items", flow.getDocumentation(),
                    "Documentation should be set");
        }

        @Test
        @DisplayName("Flow compareTo orders by eval ordering")
        void flowCompareToOrderByEvalOrdering() {
            YAtomicTask xorSplit = new YAtomicTask("xor-split", YTask._AND, YTask._XOR, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);
            YCondition cond3 = new YCondition("cond3", net);

            YFlow flow1 = new YFlow(xorSplit, cond1);
            flow1.setEvalOrdering(1);
            YFlow flow2 = new YFlow(xorSplit, cond2);
            flow2.setEvalOrdering(3);
            YFlow flow3 = new YFlow(xorSplit, cond3);
            flow3.setEvalOrdering(2);

            assertTrue(flow1.compareTo(flow2) < 0, "flow1 (order 1) < flow2 (order 3)");
            assertTrue(flow2.compareTo(flow3) > 0, "flow2 (order 3) > flow3 (order 2)");
            assertTrue(flow1.compareTo(flow3) < 0, "flow1 (order 1) < flow3 (order 2)");
        }

        @Test
        @DisplayName("Flow compareTo places default flow last")
        void flowCompareToDefaultFlowLast() {
            YAtomicTask xorSplit = new YAtomicTask("xor-split", YTask._AND, YTask._XOR, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);

            YFlow regularFlow = new YFlow(xorSplit, cond1);
            regularFlow.setEvalOrdering(1);
            YFlow defaultFlow = new YFlow(xorSplit, cond2);
            defaultFlow.setIsDefaultFlow(true);

            assertTrue(regularFlow.compareTo(defaultFlow) < 0,
                    "Regular flow should come before default flow");
            assertTrue(defaultFlow.compareTo(regularFlow) > 0,
                    "Default flow should come after regular flow");
        }

        @Test
        @DisplayName("Flow toString contains element information")
        void flowToStringContainsElementInfo() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(task, condition);

            String result = flow.toString();
            assertTrue(result.contains("Flow"), "Should contain Flow class name");
            assertTrue(result.contains("task1"), "Should contain prior element ID");
            assertTrue(result.contains("cond1"), "Should contain next element ID");
        }

        @Test
        @DisplayName("Flow toXML generates valid XML structure")
        void flowToXmlGeneratesValidXml() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._XOR, net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(task, condition);
            flow.setXpathPredicate("/data/x > 0");
            flow.setEvalOrdering(1);
            flow.setIsDefaultFlow(false);
            flow.setDocumentation("Test flow");

            String xml = flow.toXML();

            assertTrue(xml.contains("<flowsInto>"), "Should contain flowsInto element");
            assertTrue(xml.contains("<nextElementRef id=\"cond1\"/>"), "Should contain nextElementRef");
            assertTrue(xml.contains("<predicate"), "Should contain predicate element");
            // Predicate expression is XML-escaped (> becomes &gt;)
            assertTrue(xml.contains("/data/x") && xml.contains("&gt;") && xml.contains("0"),
                    "Should contain predicate expression (XML-escaped)");
            assertTrue(xml.contains("ordering=\"1\""), "Should contain ordering attribute");
            assertTrue(xml.contains("<documentation>"), "Should contain documentation element");
        }

        @Test
        @DisplayName("Flow verification fails when elements are in different nets")
        void flowVerificationFailsForDifferentNets() {
            YNet net2 = new YNet("net2", specification);
            YAtomicTask task1 = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition condInOtherNet = new YCondition("cond1", net2);
            YFlow flow = new YFlow(task1, condInOtherNet);

            handler.reset();
            flow.verify(task1, handler);

            assertTrue(handler.getMessageCount() >= 1, "Should have at least one error");
            boolean foundNetError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("must occur with the bounds of the same net"));
            assertTrue(foundNetError, "Should contain error about flows crossing net boundaries");
        }

        @Test
        @DisplayName("Flow from output condition is invalid")
        void flowFromOutputConditionIsInvalid() {
            YOutputCondition outputCondition = new YOutputCondition("output", net);
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(outputCondition, condition);

            handler.reset();
            flow.verify(outputCondition, handler);

            assertTrue(handler.getMessageCount() >= 1, "Should have at least one error");
            boolean foundOutputError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("OutputCondition") && m.getMessage().contains("not allowed"));
            assertTrue(foundOutputError, "Should contain error about flow from output condition");
        }

        @Test
        @DisplayName("Flow into input condition is invalid")
        void flowIntoInputConditionIsInvalid() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YInputCondition inputCondition = new YInputCondition("input", net);
            YFlow flow = new YFlow(task, inputCondition);

            handler.reset();
            flow.verify(task, handler);

            assertTrue(handler.getMessageCount() >= 1, "Should have at least one error");
            boolean foundInputError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("InputCondition") && m.getMessage().contains("not allowed"));
            assertTrue(foundInputError, "Should contain error about flow into input condition");
        }
    }

    // =========================================================================
    // YCondition Tests
    // =========================================================================

    @Nested
    @DisplayName("YCondition Tests")
    class YConditionTests {

        @Test
        @DisplayName("Condition can be created with ID and container")
        void conditionCreatedWithIdAndContainer() {
            YCondition condition = new YCondition("cond1", net);

            assertEquals("cond1", condition.getID(), "ID should match");
            assertEquals(net, condition.getNet(), "Net should match");
        }

        @Test
        @DisplayName("Condition can be created with ID, label, and container")
        void conditionCreatedWithIdLabelAndContainer() {
            YCondition condition = new YCondition("cond1", "Processing Queue", net);

            assertEquals("cond1", condition.getID(), "ID should match");
            assertEquals("Processing Queue", condition.getName(), "Label should match");
            assertEquals(net, condition.getNet(), "Net should match");
        }

        @Test
        @DisplayName("Condition can hold and release tokens")
        void conditionCanHoldAndReleaseTokens() throws YPersistenceException {
            YCondition condition = new YCondition("cond1", net);
            YIdentifier token = new YIdentifier("token-1");

            // Initially empty
            assertFalse(condition.containsIdentifier(), "Should not contain identifier initially");
            assertTrue(condition.getIdentifiers().isEmpty(), "Identifiers list should be empty");

            // Add token
            condition.add(null, token);
            assertTrue(condition.containsIdentifier(), "Should contain identifier after add");
            assertTrue(condition.contains(token), "Should contain the specific token");
            assertEquals(1, condition.getIdentifiers().size(), "Should have one identifier");
            assertEquals(1, condition.getAmount(token), "Should have one of this token");

            // Remove token
            condition.removeOne(null, token);
            assertFalse(condition.containsIdentifier(), "Should not contain identifier after remove");
        }

        @Test
        @DisplayName("Condition can hold multiple copies of same token")
        void conditionCanHoldMultipleTokenCopies() throws YPersistenceException {
            YCondition condition = new YCondition("cond1", net);
            YIdentifier token = new YIdentifier("token-1");

            // Add same token multiple times
            condition.add(null, token);
            condition.add(null, token);
            condition.add(null, token);

            assertEquals(3, condition.getAmount(token), "Should have three copies of the token");
            assertEquals(3, condition.getIdentifiers().size(), "Identifiers list should have 3 entries");

            // Remove one copy
            condition.removeOne(null, token);
            assertEquals(2, condition.getAmount(token), "Should have two copies remaining");
        }

        @Test
        @DisplayName("Condition can hold different tokens")
        void conditionCanHoldDifferentTokens() throws YPersistenceException {
            YCondition condition = new YCondition("cond1", net);
            YIdentifier token1 = new YIdentifier("token-1");
            YIdentifier token2 = new YIdentifier("token-2");

            condition.add(null, token1);
            condition.add(null, token2);

            assertTrue(condition.contains(token1), "Should contain token1");
            assertTrue(condition.contains(token2), "Should contain token2");
            assertEquals(2, condition.getIdentifiers().size(), "Should have 2 identifiers total");
        }

        @Test
        @DisplayName("Condition removeOne returns first available token")
        void conditionRemoveOneReturnsToken() throws YPersistenceException {
            YCondition condition = new YCondition("cond1", net);
            YIdentifier token = new YIdentifier("token-1");
            condition.add(null, token);

            YIdentifier removed = condition.removeOne(null);
            assertEquals(token, removed, "Removed token should match added token");
        }

        @Test
        @DisplayName("Condition removeAll clears all tokens")
        void conditionRemoveAllClearsTokens() throws YPersistenceException {
            YCondition condition = new YCondition("cond1", net);
            YIdentifier token1 = new YIdentifier("token-1");
            YIdentifier token2 = new YIdentifier("token-2");

            condition.add(null, token1);
            condition.add(null, token1);
            condition.add(null, token2);

            condition.removeAll(null);
            assertFalse(condition.containsIdentifier(), "Should not contain any identifiers after removeAll");
            assertTrue(condition.getIdentifiers().isEmpty(), "Identifiers list should be empty");
        }

        @Test
        @DisplayName("Condition supports implicit marker")
        void conditionSupportsImplicitMarker() {
            YCondition condition = new YCondition("cond1", net);

            assertFalse(condition.isImplicit(), "Should not be implicit by default");

            condition.setImplicit(true);
            assertTrue(condition.isImplicit(), "Should be implicit after setting");
        }

        @Test
        @DisplayName("Condition isAnonymous returns true when name is null")
        void conditionIsAnonymousWhenNameIsNull() {
            YCondition namedCondition = new YCondition("cond1", "Named Condition", net);
            YCondition anonymousCondition = new YCondition("cond2", net);

            assertFalse(namedCondition.isAnonymous(), "Named condition should not be anonymous");
            assertTrue(anonymousCondition.isAnonymous(), "Condition without name should be anonymous");
        }

        @Test
        @DisplayName("Condition toXML generates condition element")
        void conditionToXmlGeneratesConditionElement() {
            YCondition condition = new YCondition("cond1", "Test Condition", net);
            condition.setDocumentation("Test documentation");

            String xml = condition.toXML();

            assertTrue(xml.contains("<condition"), "Should contain condition element");
            assertTrue(xml.contains("id=\"cond1\""), "Should contain id attribute");
        }

        @Test
        @DisplayName("Condition can be cloned via net clone")
        void conditionCanBeClonedViaNetClone() throws CloneNotSupportedException {
            // Set up a connected net: input -> cond1 -> output
            // This ensures the clone traversal reaches all elements
            YInputCondition input = new YInputCondition("input", net);
            YCondition cond1 = new YCondition("cond1", "Original", net);
            cond1.setImplicit(true);
            YOutputCondition output = new YOutputCondition("output", net);

            // Create flows to connect the elements
            YFlow flowInputToCond = new YFlow(input, cond1);
            input.addPostset(flowInputToCond);
            cond1.addPreset(flowInputToCond);

            YFlow flowCondToOutput = new YFlow(cond1, output);
            cond1.addPostset(flowCondToOutput);
            output.addPreset(flowCondToOutput);

            // Initialize the net properly
            specification.setRootNet(net);
            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(cond1);

            // Clone the net which triggers element cloning
            YNet clonedNet = (YNet) net.clone();

            // Get the cloned condition from the cloned net
            YCondition cloned = (YCondition) clonedNet.getNetElement("cond1");

            assertNotNull(cloned, "Cloned condition should exist in cloned net");
            assertEquals(cond1.getID(), cloned.getID(), "Cloned ID should match");
            assertEquals(cond1.isImplicit(), cloned.isImplicit(), "Cloned implicit flag should match");
            // The cloned condition should NOT contain tokens from original
            assertFalse(cloned.containsIdentifier(), "Cloned condition should start empty");
        }
    }

    // =========================================================================
    // YInputCondition Tests
    // =========================================================================

    @Nested
    @DisplayName("YInputCondition Tests")
    class YInputConditionTests {

        @Test
        @DisplayName("Input condition can be created")
        void inputConditionCanBeCreated() {
            YInputCondition input = new YInputCondition("input", net);

            assertEquals("input", input.getID(), "ID should match");
            assertTrue(input instanceof YCondition, "Should be a YCondition");
        }

        @Test
        @DisplayName("Input condition can be created with label")
        void inputConditionCanBeCreatedWithLabel() {
            YInputCondition input = new YInputCondition("input", "Start Point", net);

            assertEquals("input", input.getID(), "ID should match");
            assertEquals("Start Point", input.getName(), "Label should match");
        }

        @Test
        @DisplayName("Input condition verification fails with non-empty preset")
        void inputConditionVerificationFailsWithPreset() {
            YInputCondition input = new YInputCondition("input", net);
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            // Add a preset flow to the input condition (which is invalid)
            input.addPreset(new YFlow(task, input));

            handler.reset();
            input.verify(handler);

            assertTrue(handler.getMessageCount() >= 1, "Should have at least one error");
            boolean foundPresetError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("preset must be empty"));
            assertTrue(foundPresetError, "Should contain error about non-empty preset");
        }

        @Test
        @DisplayName("Input condition can hold tokens")
        void inputConditionCanHoldTokens() throws YPersistenceException {
            YInputCondition input = new YInputCondition("input", net);
            YIdentifier caseToken = new YIdentifier("case-1");

            input.add(null, caseToken);

            assertTrue(input.contains(caseToken), "Should contain the case token");
            assertTrue(input.containsIdentifier(), "Should report containing identifier");
        }

        @Test
        @DisplayName("Input condition toXML generates inputCondition element")
        void inputConditionToXml() {
            YInputCondition input = new YInputCondition("input", "Start", net);

            String xml = input.toXML();

            assertTrue(xml.contains("<inputCondition"), "Should contain inputCondition element");
            assertTrue(xml.contains("id=\"input\""), "Should contain id attribute");
        }
    }

    // =========================================================================
    // YOutputCondition Tests
    // =========================================================================

    @Nested
    @DisplayName("YOutputCondition Tests")
    class YOutputConditionTests {

        @Test
        @DisplayName("Output condition can be created")
        void outputConditionCanBeCreated() {
            YOutputCondition output = new YOutputCondition("output", net);

            assertEquals("output", output.getID(), "ID should match");
            assertTrue(output instanceof YCondition, "Should be a YCondition");
        }

        @Test
        @DisplayName("Output condition can be created with label")
        void outputConditionCanBeCreatedWithLabel() {
            YOutputCondition output = new YOutputCondition("output", "End Point", net);

            assertEquals("output", output.getID(), "ID should match");
            assertEquals("End Point", output.getName(), "Label should match");
        }

        @Test
        @DisplayName("Output condition verification fails with non-empty postset")
        void outputConditionVerificationFailsWithPostset() {
            YOutputCondition output = new YOutputCondition("output", net);
            YCondition condition = new YCondition("cond1", net);

            // Add a postset flow from the output condition (which is invalid)
            output.addPostset(new YFlow(output, condition));

            handler.reset();
            output.verify(handler);

            assertTrue(handler.getMessageCount() >= 1, "Should have at least one error");
            boolean foundPostsetError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("postset must be empty"));
            assertTrue(foundPostsetError, "Should contain error about non-empty postset");
        }

        @Test
        @DisplayName("Output condition can hold tokens representing completed cases")
        void outputConditionCanHoldTokens() throws YPersistenceException {
            YOutputCondition output = new YOutputCondition("output", net);
            YIdentifier completionToken = new YIdentifier("case-1");

            output.add(null, completionToken);

            assertTrue(output.contains(completionToken), "Should contain the completion token");
            assertTrue(output.containsIdentifier(), "Should report containing identifier");
        }

        @Test
        @DisplayName("Output condition toXML generates outputCondition element")
        void outputConditionToXml() {
            YOutputCondition output = new YOutputCondition("output", "End", net);

            String xml = output.toXML();

            assertTrue(xml.contains("<outputCondition"), "Should contain outputCondition element");
            assertTrue(xml.contains("id=\"output\""), "Should contain id attribute");
        }
    }

    // =========================================================================
    // Integration Tests: Flow + Condition + Task
    // =========================================================================

    @Nested
    @DisplayName("Flow Integration Tests")
    class FlowIntegrationTests {

        @Test
        @DisplayName("Complete workflow: task to condition flow")
        void completeWorkflowTaskToConditionFlow() throws YPersistenceException {
            // Create elements
            YInputCondition input = new YInputCondition("input", net);
            YCondition cond1 = new YCondition("cond1", net);
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YCondition cond2 = new YCondition("cond2", net);
            YOutputCondition output = new YOutputCondition("output", net);

            // Set up net
            net.setInputCondition(input);
            net.setOutputCondition(output);
            net.addNetElement(cond1);
            net.addNetElement(task);
            net.addNetElement(cond2);

            // Create flows
            YFlow flow1 = new YFlow(input, cond1);
            YFlow flow2 = new YFlow(cond1, task);
            YFlow flow3 = new YFlow(task, cond2);
            YFlow flow4 = new YFlow(cond2, output);

            // Add flows to elements
            input.addPostset(flow1);
            cond1.addPreset(flow1);
            cond1.addPostset(flow2);
            task.addPreset(flow2);
            task.addPostset(flow3);
            cond2.addPreset(flow3);
            cond2.addPostset(flow4);
            output.addPreset(flow4);

            // Verify connectivity
            assertTrue(input.getPostsetElements().contains(cond1), "Input should have cond1 in postset");
            assertTrue(cond1.getPresetElements().contains(input), "Cond1 should have input in preset");
            assertTrue(cond1.getPostsetElements().contains(task), "Cond1 should have task in postset");
            assertTrue(task.getPresetElements().contains(cond1), "Task should have cond1 in preset");
        }

        @Test
        @DisplayName("XOR split flows require predicates or default marker")
        void xorSplitFlowsRequirePredicatesOrDefault() {
            YAtomicTask xorTask = new YAtomicTask("xor-task", YTask._AND, YTask._XOR, net);
            YCondition condA = new YCondition("condA", net);
            YCondition condB = new YCondition("condB", net);

            YFlow flowA = new YFlow(xorTask, condA);
            // flowA has no predicate and is not default - should fail

            handler.reset();
            flowA.verify(xorTask, handler);

            assertTrue(handler.getMessageCount() >= 1, "Should have error for missing predicate/default");

            // Now set as default - should be valid (but may still have other issues like missing net)
            flowA.setIsDefaultFlow(true);
            handler.reset();
            flowA.verify(xorTask, handler);

            // Check that the predicate/default requirement is satisfied
            boolean stillHasPredicateError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("must have either a predicate or be a default flow"));
            assertFalse(stillHasPredicateError, "Should not have predicate/default error after setting default");
        }

        @Test
        @DisplayName("AND split flows must not have predicates")
        void andSplitFlowsMustNotHavePredicates() {
            YAtomicTask andTask = new YAtomicTask("and-task", YTask._AND, YTask._AND, net);
            YCondition condA = new YCondition("condA", net);

            YFlow flowA = new YFlow(andTask, condA);
            flowA.setXpathPredicate("/data/x > 0"); // Invalid for AND-split

            handler.reset();
            flowA.verify(andTask, handler);

            boolean hasPredicateError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("AND-split") && m.getMessage().contains("xpath predicate"));
            assertTrue(hasPredicateError, "Should have error about predicate on AND-split");
        }

        @Test
        @DisplayName("Flow from condition must not have predicate or ordering")
        void flowFromConditionMustNotHavePredicateOrOrdering() {
            YCondition cond1 = new YCondition("cond1", net);
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);

            YFlow flow = new YFlow(cond1, task);
            flow.setXpathPredicate("/data/x > 0");
            flow.setEvalOrdering(1);
            flow.setIsDefaultFlow(true);

            handler.reset();
            flow.verify(cond1, handler);

            boolean hasPredicateError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("condition") && m.getMessage().contains("predicate"));
            boolean hasOrderingError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("condition") && m.getMessage().contains("ordering"));
            boolean hasDefaultError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("condition") && m.getMessage().contains("default"));

            assertTrue(hasPredicateError, "Should have error about predicate from condition");
            assertTrue(hasOrderingError, "Should have error about ordering from condition");
            assertTrue(hasDefaultError, "Should have error about default flow from condition");
        }

        @Test
        @DisplayName("Token flows through conditions correctly")
        void tokenFlowsThroughConditions() throws YPersistenceException {
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);

            // Create flow between conditions (which is invalid per YAWL rules but tests the mechanism)
            YFlow flow = new YFlow(cond1, cond2);
            cond1.addPostset(flow);
            cond2.addPreset(flow);

            // Verify preset/postset setup
            assertTrue(cond1.getPostsetElements().contains(cond2), "cond1 postset should contain cond2");
            assertTrue(cond2.getPresetElements().contains(cond1), "cond2 preset should contain cond1");

            // Get the flow from postset
            YFlow retrievedFlow = cond1.getPostsetFlow(cond2);
            assertNotNull(retrievedFlow, "Should be able to retrieve flow");
            assertEquals(cond1, retrievedFlow.getPriorElement(), "Flow prior should be cond1");
            assertEquals(cond2, retrievedFlow.getNextElement(), "Flow next should be cond2");
        }
    }

    // =========================================================================
    // Edge Cases and Error Handling
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Flow with null prior element reports error")
        void flowWithNullPriorElementReportsError() {
            YCondition condition = new YCondition("cond1", net);
            YFlow flow = new YFlow(null, condition);

            handler.reset();
            flow.verify(null, handler);

            boolean hasNullPriorError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("null prior element"));
            assertTrue(hasNullPriorError, "Should report null prior element error");
        }

        @Test
        @DisplayName("Flow with null next element reports error")
        void flowWithNullNextElementReportsError() {
            YAtomicTask task = new YAtomicTask("task1", YTask._AND, YTask._AND, net);
            YFlow flow = new YFlow(task, null);

            handler.reset();
            flow.verify(task, handler);

            boolean hasNullNextError = handler.getMessages().stream()
                    .anyMatch(m -> m.getMessage().contains("null next element"));
            assertTrue(hasNullNextError, "Should report null next element error");
        }

        @Test
        @DisplayName("Condition removeOne throws when empty")
        void conditionRemoveOneThrowsWhenEmpty() {
            YCondition condition = new YCondition("cond1", net);

            assertThrows(IndexOutOfBoundsException.class, () -> {
                condition.removeOne(null); // Empty list - should throw
            }, "Should throw when trying to remove from empty condition");
        }

        @Test
        @DisplayName("Condition remove with excessive amount throws")
        void conditionRemoveWithExcessiveAmountThrows() throws YPersistenceException {
            YCondition condition = new YCondition("cond1", net);
            YIdentifier token = new YIdentifier("token-1");
            condition.add(null, token);

            assertThrows(Exception.class, () -> {
                condition.remove(null, token, 5); // Only 1 token but trying to remove 5
            }, "Should throw when trying to remove more tokens than available");
        }

        @Test
        @DisplayName("Condition toXML with null name generates valid XML")
        void conditionToXmlWithNullName() {
            YCondition condition = new YCondition("cond1", net); // No name set

            String xml = condition.toXML();

            assertTrue(xml.contains("<condition"), "Should contain condition element");
            assertTrue(xml.contains("id=\"cond1\""), "Should contain id attribute");
        }

        @Test
        @DisplayName("Multiple flows from same task have correct ordering")
        void multipleFlowsHaveCorrectOrdering() {
            YAtomicTask xorTask = new YAtomicTask("xor-task", YTask._AND, YTask._XOR, net);
            YCondition cond1 = new YCondition("cond1", net);
            YCondition cond2 = new YCondition("cond2", net);
            YCondition cond3 = new YCondition("cond3", net);

            YFlow flow1 = new YFlow(xorTask, cond1);
            flow1.setEvalOrdering(3);
            YFlow flow2 = new YFlow(xorTask, cond2);
            flow2.setEvalOrdering(1);
            YFlow flow3 = new YFlow(xorTask, cond3);
            flow3.setIsDefaultFlow(true);

            // When sorted, should be: flow2 (order 1), flow1 (order 3), flow3 (default)
            java.util.List<YFlow> flows = new java.util.ArrayList<>();
            flows.add(flow1);
            flows.add(flow2);
            flows.add(flow3);
            java.util.Collections.sort(flows);

            assertEquals(flow2, flows.get(0), "First should be flow2 (order 1)");
            assertEquals(flow1, flows.get(1), "Second should be flow1 (order 3)");
            assertEquals(flow3, flows.get(2), "Third should be flow3 (default, always last)");
        }
    }
}
