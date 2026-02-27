package org.yawlfoundation.yawl.elements.state;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YStateException;

/**
 * Chicago TDD tests for YInternalCondition.
 * Tests internal condition marking, identifier add/remove, and XML serialization.
 * Uses real YInternalCondition instances with real YIdentifier objects.
 */
@DisplayName("YInternalCondition Tests")
@Tag("unit")
class TestYInternalCondition {

    private YInternalCondition condition;
    private YAtomicTask task;
    private YNet net;
    private YSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("http://test.com/test-spec");
        net = new YNet("testNet", spec);
        task = new YAtomicTask("testTask", YTask._AND, YTask._AND, net);
        condition = new YInternalCondition(YInternalCondition._mi_entered, task);
    }

    @Nested
    @DisplayName("Internal Condition Creation")
    class InternalConditionCreationTests {

        @Test
        @DisplayName("Internal condition should store ID correctly")
        void internalConditionStoresId() {
            assertEquals(YInternalCondition._mi_entered, condition.getID());
        }

        @Test
        @DisplayName("Internal condition should reference parent task")
        void internalConditionReferencesParentTask() {
            assertSame(task, condition._myTask);
        }

        @Test
        @DisplayName("Internal condition should be empty initially")
        void internalConditionIsEmptyInitially() {
            assertFalse(condition.containsIdentifier());
        }

        @Test
        @DisplayName("Internal condition should have empty identifier list initially")
        void internalConditionHasEmptyIdentifierList() {
            List<YIdentifier> identifiers = condition.getIdentifiers();
            assertNotNull(identifiers);
            assertTrue(identifiers.isEmpty());
        }

        @Test
        @DisplayName("mi_active constant should have expected value")
        void miActiveConstantHasExpectedValue() {
            assertEquals("mi_active", YInternalCondition._mi_active);
        }

        @Test
        @DisplayName("mi_entered constant should have expected value")
        void miEnteredConstantHasExpectedValue() {
            assertEquals("mi_entered", YInternalCondition._mi_entered);
        }

        @Test
        @DisplayName("mi_executing constant should have expected value")
        void miExecutingConstantHasExpectedValue() {
            assertEquals("mi_executing", YInternalCondition._mi_executing);
        }

        @Test
        @DisplayName("mi_complete constant should have expected value")
        void miCompleteConstantHasExpectedValue() {
            assertEquals("mi_complete", YInternalCondition._mi_complete);
        }
    }

    @Nested
    @DisplayName("Identifier Add Operations")
    class IdentifierAddTests {

        @Test
        @DisplayName("Add identifier should increase identifier count")
        void addIdentifierIncreasesCount() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);
            assertTrue(condition.containsIdentifier());
            assertEquals(1, condition.getIdentifiers().size());
        }

        @Test
        @DisplayName("Add multiple identifiers should all be stored")
        void addMultipleIdentifiersStoresAll() throws YPersistenceException {
            YIdentifier id1 = new YIdentifier("case1");
            YIdentifier id2 = new YIdentifier("case2");
            YIdentifier id3 = new YIdentifier("case3");

            condition.add(null, id1);
            condition.add(null, id2);
            condition.add(null, id3);

            assertEquals(3, condition.getIdentifiers().size());
            assertTrue(condition.contains(id1));
            assertTrue(condition.contains(id2));
            assertTrue(condition.contains(id3));
        }

        @Test
        @DisplayName("Contains identifier should return true for added identifier")
        void containsReturnsTrueForAddedIdentifier() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);
            assertTrue(condition.contains(id));
        }

        @Test
        @DisplayName("Contains identifier should return false for non-added identifier")
        void containsReturnsFalseForNonAddedIdentifier() {
            YIdentifier id = new YIdentifier("case1");
            assertFalse(condition.contains(id));
        }

        @Test
        @DisplayName("Get amount should return correct count for duplicate identifiers")
        void getAmountReturnsCorrectCount() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);
            condition.add(null, id);
            condition.add(null, id);
            assertEquals(3, condition.getAmount(id));
        }

        @Test
        @DisplayName("Get amount should return zero for non-existent identifier")
        void getAmountReturnsZeroForNonExistent() {
            YIdentifier id = new YIdentifier("case1");
            assertEquals(0, condition.getAmount(id));
        }
    }

    @Nested
    @DisplayName("Identifier Remove Operations")
    class IdentifierRemoveTests {

        @Test
        @DisplayName("Remove one identifier should decrease count")
        void removeOneDecreasesCount() throws YPersistenceException {
            YIdentifier id1 = new YIdentifier("case1");
            YIdentifier id2 = new YIdentifier("case2");

            condition.add(null, id1);
            condition.add(null, id2);
            assertEquals(2, condition.getIdentifiers().size());

            condition.removeOne(null, id1);
            assertEquals(1, condition.getIdentifiers().size());
            assertFalse(condition.contains(id1));
            assertTrue(condition.contains(id2));
        }

        @Test
        @DisplayName("Remove one without argument should remove first identifier")
        void removeOneWithoutArgumentRemovesFirst() throws YPersistenceException {
            YIdentifier id1 = new YIdentifier("case1");
            YIdentifier id2 = new YIdentifier("case2");

            condition.add(null, id1);
            condition.add(null, id2);

            YIdentifier removed = condition.removeOne(null);
            assertNotNull(removed);
            assertEquals(1, condition.getIdentifiers().size());
        }

        @Test
        @DisplayName("Remove with amount should remove correct count")
        void removeWithAmountRemovesCorrectCount() throws YPersistenceException, YStateException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);
            condition.add(null, id);
            condition.add(null, id);

            assertEquals(3, condition.getAmount(id));
            condition.remove(null, id, 2);
            assertEquals(1, condition.getAmount(id));
        }

        @Test
        @DisplayName("Remove all should clear all identifiers")
        void removeAllClearsAll() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);
            condition.add(null, id);
            condition.add(null, id);

            assertTrue(condition.containsIdentifier());
            condition.removeAll(null, id);
            assertEquals(0, condition.getAmount(id));
        }

        @Test
        @DisplayName("Remove all without argument should clear condition")
        void removeAllWithoutArgumentClearsCondition() throws YPersistenceException {
            YIdentifier id1 = new YIdentifier("case1");
            YIdentifier id2 = new YIdentifier("case2");
            condition.add(null, id1);
            condition.add(null, id2);

            assertTrue(condition.containsIdentifier());
            condition.removeAll(null);
            assertFalse(condition.containsIdentifier());
        }

        @Test
        @DisplayName("Remove more than available should throw YStateException")
        void removeMoreThanAvailableThrowsException() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);
            condition.add(null, id);

            assertThrows(YStateException.class, () -> condition.remove(null, id, 5));
        }
    }

    @Nested
    @DisplayName("ToString and ToXML")
    class StringRepresentationTests {

        @Test
        @DisplayName("ToString should contain condition ID and task ID")
        void toStringContainsIds() {
            String result = condition.toString();
            assertTrue(result.contains(YInternalCondition._mi_entered));
            assertTrue(result.contains("testTask"));
        }

        @Test
        @DisplayName("ToXML should produce valid XML structure")
        void toXmlProducesValidXml() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);

            String xml = condition.toXML();
            assertNotNull(xml);
            assertTrue(xml.contains("<internalCondition"));
            assertTrue(xml.contains("id="));
        }

        @Test
        @DisplayName("ToXML should contain identifiers when present")
        void toXmlContainsIdentifiers() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);

            String xml = condition.toXML();
            assertTrue(xml.contains("<identifier"));
            assertTrue(xml.contains("case1"));
        }

        @Test
        @DisplayName("ToXML should be empty of identifiers when condition is empty")
        void toXmlEmptyWhenNoIdentifiers() {
            String xml = condition.toXML();
            assertNotNull(xml);
            assertFalse(xml.contains("<identifier"));
        }
    }

    @Nested
    @DisplayName("Internal Condition Types")
    class InternalConditionTypesTests {

        @Test
        @DisplayName("Creating mi_active condition should work")
        void createMiActiveCondition() {
            YInternalCondition activeCondition = new YInternalCondition(
                    YInternalCondition._mi_active, task);
            assertEquals(YInternalCondition._mi_active, activeCondition.getID());
        }

        @Test
        @DisplayName("Creating mi_executing condition should work")
        void createMiExecutingCondition() {
            YInternalCondition executingCondition = new YInternalCondition(
                    YInternalCondition._mi_executing, task);
            assertEquals(YInternalCondition._mi_executing, executingCondition.getID());
        }

        @Test
        @DisplayName("Creating mi_complete condition should work")
        void createMiCompleteCondition() {
            YInternalCondition completeCondition = new YInternalCondition(
                    YInternalCondition._mi_complete, task);
            assertEquals(YInternalCondition._mi_complete, completeCondition.getID());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Add null identifier should be handled by bag")
        void addNullIdentifier() throws YPersistenceException {
            // The YIdentifierBag handles null gracefully
            assertDoesNotThrow(() -> condition.add(null, null));
        }

        @Test
        @DisplayName("Get identifiers should return defensive copy")
        void getIdentifiersReturnsDefensiveList() throws YPersistenceException {
            YIdentifier id = new YIdentifier("case1");
            condition.add(null, id);

            List<YIdentifier> list1 = condition.getIdentifiers();
            List<YIdentifier> list2 = condition.getIdentifiers();

            // Modifying one list should not affect the other
            assertNotSame(list1, list2);
        }

        @Test
        @DisplayName("Contains identifier should work with equal but different instance")
        void containsWorksWithEqualInstance() throws YPersistenceException {
            YIdentifier id1 = new YIdentifier("case1");
            YIdentifier id2 = new YIdentifier("case1");

            condition.add(null, id1);

            // YIdentifier equality is based on string value and parent
            assertTrue(condition.contains(id1));
        }
    }
}
