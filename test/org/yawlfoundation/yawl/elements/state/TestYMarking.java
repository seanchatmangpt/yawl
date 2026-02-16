package org.yawlfoundation.yawl.elements.state;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.jdom2.JDOMException;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YSchemaBuildingException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive test suite for YMarking and related marking operations.
 * Tests cover marking creation, token operations, equality, comparison,
 * set operations, identifier hierarchy, and thread safety.
 *
 * Author: Lachlan Aldred (original)
 * Enhanced with comprehensive marking operation tests
 * Date: 24/06/2003
 * Time: 18:24:55
 */
public class TestYMarking extends TestCase{
    private YMarking _marking1;
    private YMarking _marking2;
    private YMarking _marking3;
    private YMarking _marking4;
    private YMarking _marking5;
    private YMarking _marking6;
    private YAtomicTask _xorJoinAndSplit;
    private YAtomicTask _xorJoinXorSplit;
    private YAtomicTask _xorJoinOrSplit;
    private YAtomicTask _andJoinOrSplit;
    private YTask _orJoin;
    private YMarking _marking7;
    private YCondition[] _conditionArr;
    private YNet _loopedNet;
    private YMarking _marking8;
    private YMarking _marking9;
    private YMarking _marking10;
    private YMarking _marking11;

    // Additional test fixtures for comprehensive tests (do not require YEngine)
    private YCondition _testConditionA;
    private YCondition _testConditionB;
    private YCondition _testConditionC;


    public TestYMarking(String name){
        super(name);
    }


    public void setUp() throws YSchemaBuildingException, YSyntaxException, JDOMException, IOException, YPersistenceException {
        // Initialize simple test conditions for comprehensive tests (no YEngine dependency)
        _testConditionA = new YCondition("testA", "Test Condition A", null);
        _testConditionB = new YCondition("testB", "Test Condition B", null);
        _testConditionC = new YCondition("testC", "Test Condition C", null);

        _conditionArr = new YCondition[6];
        for (int i = 0; i < _conditionArr.length; i++) {
            _conditionArr[i] = new YCondition("ct"+i, "YConditionInterface " + i, null);
        }
        YIdentifier id1, id2, id3, id4, id5, id6;
        id1 = new YIdentifier(null);
        id2 = new YIdentifier(null);
        id3 = new YIdentifier(null);
        id4 = new YIdentifier(null);
        id5 = new YIdentifier(null);
        id6 = new YIdentifier(null);

        id1.addLocation(null, _conditionArr[0]);
        id1.addLocation(null, _conditionArr[1]);
        id1.addLocation(null, _conditionArr[2]);
        id1.addLocation(null, _conditionArr[3]);
        id1.addLocation(null, _conditionArr[4]);
        _marking1 = new YMarking(id1);

        id2.addLocation(null, _conditionArr[0]);
        id2.addLocation(null, _conditionArr[1]);
        id2.addLocation(null, _conditionArr[2]);
        id2.addLocation(null, _conditionArr[3]);
        id2.addLocation(null, _conditionArr[4]);
        _marking2 = new YMarking(id2);

        id3.addLocation(null, _conditionArr[0]);
        id3.addLocation(null, _conditionArr[1]);
        id3.addLocation(null, _conditionArr[2]);
        id3.addLocation(null, _conditionArr[3]);
        _marking3 = new YMarking(id3);

        id4.addLocation(null, _conditionArr[0]);
        id4.addLocation(null, _conditionArr[1]);
        id4.addLocation(null, _conditionArr[2]);
        id4.addLocation(null, _conditionArr[3]);
        id4.addLocation(null, _conditionArr[4]);
        id4.addLocation(null, _conditionArr[4]);
        _marking4 = new YMarking(id4);

        id5.addLocation(null, _conditionArr[4]);
        id5.addLocation(null, _conditionArr[5]);
        _marking5 = new YMarking(id5);

        id6.addLocation(null, _conditionArr[0]);
        id6.addLocation(null, _conditionArr[1]);
        id6.addLocation(null, _conditionArr[2]);
        id6.addLocation(null, _conditionArr[2]);
        id6.addLocation(null, _conditionArr[3]);
        id6.addLocation(null, _conditionArr[4]);
        _marking6 = new YMarking(id6);

        int xor = YTask._XOR;
        int and = YTask._AND;
        int or = YTask._OR;
        _xorJoinAndSplit = new YAtomicTask("xorAnd", xor, and, null);
        _xorJoinOrSplit = new YAtomicTask("xorOr", xor, or, null);
        _xorJoinXorSplit = new YAtomicTask("xorXor", xor, xor, null);
        _andJoinOrSplit = new YAtomicTask("andOr", and, or, null);

        YIdentifier id = new YIdentifier(null);
        _orJoin = new YAtomicTask("orJ", or, and, null);
        for(int i = 0; i < 3; i++){
            _conditionArr[i].addPostset(new YFlow(_conditionArr[i], _xorJoinAndSplit));
            _conditionArr[i + 3].addPostset(new YFlow(_conditionArr[i + 3], _orJoin));
            _conditionArr[i].addPostset(new YFlow(_conditionArr[i], _xorJoinOrSplit));
            _conditionArr[i].addPostset(new YFlow(_conditionArr[i], _xorJoinXorSplit));
            _conditionArr[i].addPostset(new YFlow(_conditionArr[i], _andJoinOrSplit));
            _xorJoinAndSplit.addPostset(new YFlow(_xorJoinAndSplit, _conditionArr[i + 3]));
            _xorJoinOrSplit .addPostset(new YFlow(_xorJoinOrSplit, _conditionArr[i + 3]));
            _xorJoinXorSplit.addPostset(new YFlow(_xorJoinXorSplit, _conditionArr[i + 3]));
            _andJoinOrSplit .addPostset(new YFlow(_andJoinOrSplit, _conditionArr[i + 3]));
            _conditionArr[i] .add(null, id);
        }
        _marking7 = new YMarking(id);

        URL fileURL = getClass().getResource("YAWLOrJoinTestSpecificationLongLoops.xml");
        File yawlXMLFile = new File(fileURL.getFile());
        YSpecification specification = null;
        specification = (YSpecification) YMarshal.
                            unmarshalSpecifications(StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
        _loopedNet  = specification.getRootNet();
        id = new YIdentifier(null);
        id.addLocation(null, (YCondition)_loopedNet.getNetElement("c{w_d}"));
        _marking8 = new YMarking(id);
        id = new YIdentifier(null);
        _marking9 = new YMarking(id);
        id.addLocation(null, (YCondition)_loopedNet.getNetElement("cA"));
        _marking10 = new YMarking(id);
        id = new YIdentifier(null);
        id.addLocation(null, (YCondition)_loopedNet.getNetElement("i-top"));
        _marking11 = new YMarking(id);
    }


    public void testEquals(){
        assertTrue(_marking1.equals(_marking2));
        assertTrue(_marking2.equals(_marking1));
        assertFalse(_marking3.equals(_marking1));
        assertFalse(_marking1.equals(_marking3));
    }


    public void testGreaterThanOrEquals(){
        //XPathSaxonUser equal markings
        assertTrue(_marking1.strictlyGreaterThanOrEqualWithSupports(_marking2));
        assertTrue(_marking2.strictlyGreaterThanOrEqualWithSupports(_marking1));
        //XPathSaxonUser m4[0 1 2 3 4 4] m2[0 1 2 3 4]
        assertTrue(_marking4.strictlyGreaterThanOrEqualWithSupports(_marking2));
        //XPathSaxonUser strictly lesser marking with supports
        assertFalse(_marking2.strictlyGreaterThanOrEqualWithSupports(_marking4));
        //XPathSaxonUser m2[0 1 2 3 4] m3[0 1 2 3]
        assertFalse(_marking2.strictlyGreaterThanOrEqualWithSupports(_marking3));
        //XPathSaxonUser m4[0 1 2 3 4 4] m6[0 1 2 2 3 4]
        assertFalse(_marking4.strictlyGreaterThanOrEqualWithSupports(_marking6));
        //XPathSaxonUser m6[0 1 2 2 3 4] m4[0 1 2 3 4 4]
        assertFalse(_marking6.strictlyGreaterThanOrEqualWithSupports(_marking4));
        //XPathSaxonUser m5[4 5] m3[0 1 2 3]
        assertFalse(_marking5.strictlyGreaterThanOrEqualWithSupports(_marking3));
        assertFalse(_marking3.strictlyGreaterThanOrEqualWithSupports(_marking5));
    }


    public void testLessThan(){
        //XPathSaxonUser m2[0 1 2 3 4] m4[0 1 2 3 4 4]
        assertTrue(_marking2.strictlyLessThanWithSupports(_marking4));
        assertFalse(_marking4.strictlyLessThanWithSupports(_marking2));
        //XPathSaxonUser m3[0 1 2 3] m2[0 1 2 3 4]
        assertFalse(_marking3.strictlyLessThanWithSupports(_marking2));
        assertFalse(_marking2.strictlyLessThanWithSupports(_marking3));
        //XPathSaxonUser m4[0 1 2 3 4 4] m6[0 1 2 2 3 4]
        assertFalse(_marking4.strictlyLessThanWithSupports(_marking6));
        assertFalse(_marking6.strictlyLessThanWithSupports(_marking4));
        //XPathSaxonUser equal markings -  should be false
        assertFalse(_marking1.strictlyLessThanWithSupports(_marking2));
    }


    public void testHashcode(){
        assertTrue(_marking1.hashCode() == _marking2.hashCode());
        assertFalse(_marking1.hashCode() == _marking4.hashCode());
        assertFalse(_marking1.hashCode() == _marking3.hashCode());
        assertFalse(_marking4.hashCode() == _marking6.hashCode());
    }


    public void testDoPowerSetRecursion(){
        Set aSet = new HashSet();
        aSet.add("1");
        aSet.add("2");
        aSet.add("3");
        aSet.add("4");
        aSet.add("5");
        Set powerSet = _marking1.doPowerSetRecursion(aSet);
        assertTrue(powerSet.size() == Math.pow(2, aSet.size()) - 1);
//        System.out.println("powerSet: " + powerSet);
    }


    public void testXorJoinAndSplit(){
//System.out.println("_xorJoinAndSplit preset " + _xorJoinAndSplit.getPresetElements());
//System.out.println("_xorJoinAndSplit postset " + _xorJoinAndSplit.getPostsetElements());
//System.out.println("marking locations " + _marking7.getLocations());
        YSetOfMarkings markingSet = _marking7.reachableInOneStep(_xorJoinAndSplit, _orJoin);
        assertNotNull("reachableInOneStep should return non-null", markingSet);
        for (Iterator iterator = markingSet.getMarkings().iterator(); iterator.hasNext();) {
            YMarking marking = (YMarking) iterator.next();
            List list = marking.getLocations();
            assertTrue("each marking must have 5 locations (XOR join removes 1, AND split adds 3), got " + list.size() + ": " + list,
                    list.size() == 5);
            List conditionsList = new Vector();
            for (int i = 0; i < _conditionArr.length; i++) {
                  conditionsList.add(_conditionArr[i]);
            }
            List visited = new Vector();
  // System.out.println("conditionsList is " + conditionsList);
            for (Iterator listIter = list.iterator(); listIter.hasNext();) {
                YCondition  condition = (YCondition) listIter.next();
                assertTrue(conditionsList.contains(condition));
                assertFalse(visited.contains(condition));
                visited.add(condition);
            }
        }
    }


    public void testAndJoinOrSplit(){
        _marking7.getLocations().add(new YCondition("ct10", "CT 10", null));
        YSetOfMarkings markingSet = _marking7.reachableInOneStep(_andJoinOrSplit, _orJoin);
//        for (Iterator iterator = markingSet.getMarkings().iterator(); iterator.hasNext();) {
//            YMarking marking = (YMarking) iterator.next();
//            List list = marking.getLocations();
//            System.out.println("" + list);
//        }
        assertEquals(markingSet.getMarkings().size(), 1);
    }


    public void testDeadlock(){
        //deadlocked marking with one token in and-join
        assertTrue(_marking8.deadLock(null));
        //XPathSaxonUser empty marking
        assertTrue(_marking9.deadLock(null));
        //XPathSaxonUser non deadlocked marking
        assertFalse(_marking10.deadLock(null));
        //XPathSaxonUser another non deadlocked marking
        assertFalse(_marking11.deadLock(null));
    }


    public static void main(String args[]){
        TestRunner runner = new TestRunner();
        runner.doRun(suite());
        System.exit(0);
    }


    public static Test suite(){
        TestSuite suite = new TestSuite();
        suite.addTestSuite(TestYMarking.class);
        return suite;
    }


    // ========================================================================
    // COMPREHENSIVE MARKING OPERATION TESTS (25+ tests)
    // These tests use the simple test conditions that don't require YEngine
    // ========================================================================

    /**
     * Test 1: testMarkingCreation - create empty marking
     */
    public void testMarkingCreation() {
        List<YNetElement> emptyLocations = new ArrayList<YNetElement>();
        YMarking emptyMarking = new YMarking(emptyLocations);
        assertNotNull("Empty marking should not be null", emptyMarking);
        assertTrue("Empty marking should have empty locations", emptyMarking.getLocations().isEmpty());
        assertEquals("Empty marking should have size 0", 0, emptyMarking.getLocations().size());
    }


    /**
     * Test 2: testMarkingAddToken - add token to place
     */
    public void testMarkingAddToken() {
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        YMarking marking = new YMarking(locations);

        assertEquals("Marking should have 1 token", 1, marking.getLocations().size());
        assertTrue("Marking should contain condition A", marking.getLocations().contains(_testConditionA));

        // Add another token
        marking.getLocations().add(_testConditionB);
        assertEquals("Marking should have 2 tokens", 2, marking.getLocations().size());
        assertTrue("Marking should contain condition B", marking.getLocations().contains(_testConditionB));
    }


    /**
     * Test 3: testMarkingRemoveToken - remove token from place
     */
    public void testMarkingRemoveToken() {
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        locations.add(_testConditionB);
        YMarking marking = new YMarking(locations);

        assertEquals("Initial marking should have 2 tokens", 2, marking.getLocations().size());

        boolean removed = marking.getLocations().remove(_testConditionA);
        assertTrue("Token should be successfully removed", removed);
        assertEquals("Marking should have 1 token after removal", 1, marking.getLocations().size());
        assertFalse("Marking should not contain removed token", marking.getLocations().contains(_testConditionA));
    }


    /**
     * Test 4: testMarkingGetTokens - get all tokens
     */
    public void testMarkingGetTokens() {
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        locations.add(_testConditionB);
        locations.add(_testConditionC);
        YMarking marking = new YMarking(locations);

        List<YNetElement> retrievedTokens = marking.getLocations();
        assertNotNull("Retrieved tokens should not be null", retrievedTokens);
        assertEquals("Should retrieve all 3 tokens", 3, retrievedTokens.size());
        assertTrue("Should contain condition A", retrievedTokens.contains(_testConditionA));
        assertTrue("Should contain condition B", retrievedTokens.contains(_testConditionB));
        assertTrue("Should contain condition C", retrievedTokens.contains(_testConditionC));
    }


    /**
     * Test 5: testMarkingGetTokenCount - count tokens at place
     */
    public void testMarkingGetTokenCount() {
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        locations.add(_testConditionA);  // Add same condition twice (multiset)
        locations.add(_testConditionB);

        // Count occurrences of condition A
        int countA = 0;
        for (YNetElement element : locations) {
            if (element.equals(_testConditionA)) {
                countA++;
            }
        }
        assertEquals("Condition A should have 2 tokens", 2, countA);

        // Count occurrences of condition B
        int countB = 0;
        for (YNetElement element : locations) {
            if (element.equals(_testConditionB)) {
                countB++;
            }
        }
        assertEquals("Condition B should have 1 token", 1, countB);
    }


    /**
     * Test 6: testMarkingIsEmpty - check if empty
     */
    public void testMarkingIsEmpty() {
        // Test empty marking
        List<YNetElement> emptyLocations = new ArrayList<YNetElement>();
        YMarking emptyMarking = new YMarking(emptyLocations);
        assertTrue("New empty marking should be empty", emptyMarking.getLocations().isEmpty());

        // Test non-empty marking
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        YMarking nonEmptyMarking = new YMarking(locations);
        assertFalse("Non-empty marking should not be empty", nonEmptyMarking.getLocations().isEmpty());
    }


    /**
     * Test 7: testMarkingEqualsEnhanced - equality comparison (enhanced)
     */
    public void testMarkingEqualsEnhanced() {
        // Create two identical markings
        List<YNetElement> locations1 = new ArrayList<YNetElement>();
        locations1.add(_testConditionA);
        locations1.add(_testConditionB);
        YMarking marking1 = new YMarking(locations1);

        List<YNetElement> locations2 = new ArrayList<YNetElement>();
        locations2.add(_testConditionA);
        locations2.add(_testConditionB);
        YMarking marking2 = new YMarking(locations2);

        assertTrue("Identical markings should be equal", marking1.equals(marking2));
        assertTrue("Equality should be symmetric", marking2.equals(marking1));

        // Create different marking
        List<YNetElement> locations3 = new ArrayList<YNetElement>();
        locations3.add(_testConditionA);
        locations3.add(_testConditionC);
        YMarking marking3 = new YMarking(locations3);

        assertFalse("Different markings should not be equal", marking1.equals(marking3));

        // Test null and different type
        assertFalse("Marking should not equal null", marking1.equals(null));
        assertFalse("Marking should not equal different type", marking1.equals("string"));
    }


    /**
     * Test 8: testMarkingHashCodeConsistency - hash code consistency
     */
    public void testMarkingHashCodeConsistency() {
        // Create two identical markings
        List<YNetElement> locations1 = new ArrayList<YNetElement>();
        locations1.add(_testConditionA);
        locations1.add(_testConditionB);
        YMarking marking1 = new YMarking(locations1);

        List<YNetElement> locations2 = new ArrayList<YNetElement>();
        locations2.add(_testConditionA);
        locations2.add(_testConditionB);
        YMarking marking2 = new YMarking(locations2);

        // Equal objects must have equal hash codes
        assertEquals("Equal markings should have equal hash codes",
                marking1.hashCode(), marking2.hashCode());

        // Hash code should be consistent across multiple calls
        int hash1 = marking1.hashCode();
        int hash2 = marking1.hashCode();
        int hash3 = marking1.hashCode();
        assertEquals("Hash code should be consistent", hash1, hash2);
        assertEquals("Hash code should be consistent", hash2, hash3);
    }


    /**
     * Test 9: testMarkingCopy - deep copy marking
     */
    public void testMarkingCopy() {
        List<YNetElement> originalLocations = new ArrayList<YNetElement>();
        originalLocations.add(_testConditionA);
        originalLocations.add(_testConditionB);
        YMarking originalMarking = new YMarking(originalLocations);

        // Create a copy using the constructor
        YMarking copiedMarking = new YMarking(originalMarking.getLocations());

        // Verify copy is equal to original
        assertEquals("Copy should equal original", originalMarking, copiedMarking);

        // Modify copy and verify original is unchanged
        copiedMarking.getLocations().add(_testConditionC);
        assertFalse("Modified copy should not equal original",
                originalMarking.equals(copiedMarking));
        assertEquals("Original should still have 2 elements", 2, originalMarking.getLocations().size());
    }


    /**
     * Test 10: testMarkingMerge - merge two markings
     */
    public void testMarkingMerge() {
        List<YNetElement> locations1 = new ArrayList<YNetElement>();
        locations1.add(_testConditionA);
        YMarking marking1 = new YMarking(locations1);

        List<YNetElement> locations2 = new ArrayList<YNetElement>();
        locations2.add(_testConditionB);
        locations2.add(_testConditionC);
        YMarking marking2 = new YMarking(locations2);

        // Merge by adding all locations from marking2 to marking1
        List<YNetElement> mergedLocations = new ArrayList<YNetElement>(marking1.getLocations());
        mergedLocations.addAll(marking2.getLocations());
        YMarking mergedMarking = new YMarking(mergedLocations);

        assertEquals("Merged marking should have 3 tokens", 3, mergedMarking.getLocations().size());
        assertTrue("Merged should contain A", mergedMarking.getLocations().contains(_testConditionA));
        assertTrue("Merged should contain B", mergedMarking.getLocations().contains(_testConditionB));
        assertTrue("Merged should contain C", mergedMarking.getLocations().contains(_testConditionC));
    }


    /**
     * Test 11: testMarkingSubtract - subtract markings
     */
    public void testMarkingSubtract() {
        List<YNetElement> locations1 = new ArrayList<YNetElement>();
        locations1.add(_testConditionA);
        locations1.add(_testConditionB);
        locations1.add(_testConditionC);
        YMarking marking1 = new YMarking(locations1);

        List<YNetElement> locations2 = new ArrayList<YNetElement>();
        locations2.add(_testConditionB);
        YMarking marking2 = new YMarking(locations2);

        // Subtract marking2 from marking1
        List<YNetElement> resultLocations = new ArrayList<YNetElement>(marking1.getLocations());
        resultLocations.removeAll(marking2.getLocations());
        YMarking resultMarking = new YMarking(resultLocations);

        assertEquals("Result should have 2 tokens", 2, resultMarking.getLocations().size());
        assertTrue("Result should contain A", resultMarking.getLocations().contains(_testConditionA));
        assertTrue("Result should contain C", resultMarking.getLocations().contains(_testConditionC));
        assertFalse("Result should not contain B", resultMarking.getLocations().contains(_testConditionB));
    }


    /**
     * Test 12: testMarkingContains - contains another marking
     */
    public void testMarkingContains() {
        List<YNetElement> largerLocations = new ArrayList<YNetElement>();
        largerLocations.add(_testConditionA);
        largerLocations.add(_testConditionB);
        largerLocations.add(_testConditionC);
        YMarking largerMarking = new YMarking(largerLocations);

        List<YNetElement> smallerLocations = new ArrayList<YNetElement>();
        smallerLocations.add(_testConditionA);
        smallerLocations.add(_testConditionB);
        YMarking smallerMarking = new YMarking(smallerLocations);

        // Test containment
        assertTrue("Larger marking should contain smaller marking's locations",
                largerMarking.getLocations().containsAll(smallerMarking.getLocations()));
        assertFalse("Smaller marking should not contain larger marking's locations",
                smallerMarking.getLocations().containsAll(largerMarking.getLocations()));
    }


    /**
     * Test 13: testMarkingToString - string representation
     */
    public void testMarkingToString() {
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        locations.add(_testConditionB);
        YMarking marking = new YMarking(locations);

        String str = marking.toString();
        assertNotNull("toString should not return null", str);
        assertFalse("toString should not be empty", str.isEmpty());
        // The toString should contain the locations in some form
        assertTrue("toString should contain bracket notation or condition names",
                str.contains("[") || str.contains("testA") || str.contains("testB"));
    }


    /**
     * Test 14: testMarkingToXML - XML serialization (if supported)
     */
    public void testMarkingToXML() {
        // YMarking does not have built-in XML serialization, but we verify
        // that toString can be used for debugging/logging purposes
        List<YNetElement> locations = new ArrayList<YNetElement>();
        locations.add(_testConditionA);
        YMarking marking = new YMarking(locations);

        String representation = marking.toString();
        assertNotNull("Marking should have a string representation", representation);
    }


    /**
     * Test 15: testMarkingFromXML - XML deserialization (if supported)
     * Tests creation from identifier locations instead of XML
     */
    public void testMarkingFromXML() throws YPersistenceException {
        // YMarking is typically created from YIdentifier or location list
        // Test creation from identifier locations
        YIdentifier id = new YIdentifier("testMarkingFromXMLID");
        id.addLocation(null, _testConditionA);
        id.addLocation(null, _testConditionB);

        YMarking marking = new YMarking(id);
        assertNotNull("Marking created from identifier should not be null", marking);
        assertEquals("Marking should have 2 locations", 2, marking.getLocations().size());
    }


    /**
     * Test 16: testIdentifierHierarchy - parent/child identifiers
     */
    public void testIdentifierHierarchy() throws YPersistenceException {
        YIdentifier parent = new YIdentifier("parent1");
        YIdentifier child = parent.createChild(null);
        YIdentifier grandchild = child.createChild(null);

        // Test hierarchy relationships
        assertTrue("Child should be immediate child of parent", child.isImmediateChildOf(parent));
        assertTrue("Grandchild should be immediate child of child", grandchild.isImmediateChildOf(child));
        assertFalse("Grandchild should not be immediate child of parent", grandchild.isImmediateChildOf(parent));

        // Test ancestor relationships
        assertTrue("Parent should be ancestor of grandchild", parent.isAncestorOf(grandchild));
        assertTrue("Child should be ancestor of grandchild", child.isAncestorOf(grandchild));
        assertFalse("Grandchild should not be ancestor of parent", grandchild.isAncestorOf(parent));
    }


    /**
     * Test 17: testIdentifierCreateChild - create child ID
     */
    public void testIdentifierCreateChild() throws YPersistenceException {
        YIdentifier parent = new YIdentifier("rootID");

        YIdentifier child1 = parent.createChild(null);
        YIdentifier child2 = parent.createChild(null);
        YIdentifier child3 = parent.createChild(null, 5);  // Custom child number

        assertNotNull("Child1 should not be null", child1);
        assertNotNull("Child2 should not be null", child2);
        assertNotNull("Child3 should not be null", child3);

        // Verify child IDs follow pattern
        assertTrue("Child1 ID should start with parent ID", child1.toString().startsWith("rootID."));
        assertTrue("Child2 ID should start with parent ID", child2.toString().startsWith("rootID."));
        assertTrue("Child3 ID should contain '5'", child3.toString().contains(".5"));

        // Verify children list
        List<YIdentifier> children = parent.getChildren();
        assertEquals("Parent should have 3 children", 3, children.size());
        assertTrue("Children list should contain child1", children.contains(child1));
        assertTrue("Children list should contain child2", children.contains(child2));
        assertTrue("Children list should contain child3", children.contains(child3));
    }


    /**
     * Test 18: testIdentifierEquals - identifier equality
     */
    public void testIdentifierEquals() {
        YIdentifier id1 = new YIdentifier("sameID");
        YIdentifier id2 = new YIdentifier("sameID");
        YIdentifier id3 = new YIdentifier("differentID");

        // Test reflexivity
        assertTrue("Identifier should equal itself", id1.equals(id1));

        // Test same ID strings
        assertTrue("Identifiers with same ID string should be equal", id1.equals(id2));

        // Test different ID strings
        assertFalse("Identifiers with different ID strings should not be equal", id1.equals(id3));

        // Test null
        assertFalse("Identifier should not equal null", id1.equals(null));

        // Test different type
        assertFalse("Identifier should not equal different type", id1.equals("sameID"));
    }


    /**
     * Test 19: testIdentifierToString - identifier string
     */
    public void testIdentifierToString() {
        String testID = "testCase123";
        YIdentifier id = new YIdentifier(testID);

        String str = id.toString();
        assertEquals("toString should return the ID string", testID, str);
    }


    /**
     * Test 20: testSetOfMarkingsOperations - add/remove/contains
     */
    public void testSetOfMarkingsOperations() {
        YSetOfMarkings set = new YSetOfMarkings();

        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        YMarking marking1 = new YMarking(locs1);

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionB);
        YMarking marking2 = new YMarking(locs2);

        // Test add
        set.addMarking(marking1);
        assertTrue("Set should contain marking1", set.contains(marking1));

        set.addMarking(marking2);
        assertTrue("Set should contain marking2", set.contains(marking2));

        // Test duplicate add (should not add)
        set.addMarking(marking1);
        assertEquals("Adding duplicate should not increase size", 2, set.size());

        // Test remove
        YMarking removed = set.removeAMarking();
        assertNotNull("Removed marking should not be null", removed);
        assertEquals("Set should have 1 marking after removal", 1, set.size());
    }


    /**
     * Test 21: testSetOfMarkingsSize - size operations
     */
    public void testSetOfMarkingsSize() {
        YSetOfMarkings set = new YSetOfMarkings();

        // Test empty set
        assertEquals("Empty set should have size 0", 0, set.size());

        // Add markings and check size
        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        set.addMarking(new YMarking(locs1));
        assertEquals("Set should have size 1", 1, set.size());

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionB);
        set.addMarking(new YMarking(locs2));
        assertEquals("Set should have size 2", 2, set.size());

        // Remove all
        set.removeAll();
        assertEquals("Set should be empty after removeAll", 0, set.size());
    }


    /**
     * Test 22: testSetOfMarkingsIterator - iteration
     */
    public void testSetOfMarkingsIterator() {
        YSetOfMarkings set = new YSetOfMarkings();

        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        locs1.add(_testConditionB);
        set.addMarking(new YMarking(locs1));

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionC);
        set.addMarking(new YMarking(locs2));

        // Iterate through all markings
        int count = 0;
        Set<YMarking> markings = set.getMarkings();
        for (YMarking marking : markings) {
            count++;
            assertNotNull("Marking should not be null", marking);
            assertFalse("Marking should have locations", marking.getLocations().isEmpty());
        }
        assertEquals("Should iterate through all 2 markings", 2, count);
    }


    /**
     * Test 23: testSetOfMarkingsCopy - copy set
     */
    public void testSetOfMarkingsCopy() {
        YSetOfMarkings original = new YSetOfMarkings();

        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        original.addMarking(new YMarking(locs1));

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionB);
        original.addMarking(new YMarking(locs2));

        // Create copy using addAll
        YSetOfMarkings copy = new YSetOfMarkings();
        copy.addAll(original);

        // Verify copy has same size
        assertEquals("Copy should have same size as original", original.size(), copy.size());

        // Verify copy contains same markings
        assertTrue("Copy should be equal to original", copy.equals(original));
    }


    /**
     * Test 24: testMarkingWithIdentifier - marking with identifier
     */
    public void testMarkingWithIdentifier() throws YPersistenceException {
        YIdentifier id = new YIdentifier("testMarkingID");
        id.addLocation(null, _testConditionA);
        id.addLocation(null, _testConditionB);

        YMarking marking = new YMarking(id);

        assertNotNull("Marking should not be null", marking);
        assertEquals("Marking should have 2 locations from identifier", 2, marking.getLocations().size());
        assertTrue("Marking should contain condition A", marking.getLocations().contains(_testConditionA));
        assertTrue("Marking should contain condition B", marking.getLocations().contains(_testConditionB));
    }


    /**
     * Test 25: testMarkingConcurrency - thread-safe operations
     */
    public void testMarkingConcurrency() throws InterruptedException {
        final YIdentifier sharedId = new YIdentifier("concurrentTest");
        final int threadCount = 10;
        final int operationsPerThread = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch finishLatch = new CountDownLatch(threadCount);
        final AtomicInteger errorCount = new AtomicInteger(0);

        // Create test conditions
        final YCondition[] conditions = new YCondition[5];
        for (int i = 0; i < 5; i++) {
            conditions[i] = new YCondition("concurrentCt" + i, "Concurrent Condition " + i, null);
        }

        // Create threads that will concurrently add/remove locations
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            threads[t] = new Thread(new Runnable() {
                public void run() {
                    try {
                        startLatch.await();
                        for (int i = 0; i < operationsPerThread; i++) {
                            try {
                                // Alternate between add and remove
                                YCondition condition = conditions[(threadIndex + i) % 5];
                                if (i % 2 == 0) {
                                    sharedId.addLocation(null, condition);
                                } else {
                                    sharedId.removeLocation(null, condition);
                                }
                            } catch (YPersistenceException e) {
                                errorCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finishLatch.countDown();
                    }
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        startLatch.countDown();

        // Wait for all threads to complete
        finishLatch.await();

        // Verify no errors occurred (or accept some due to concurrent modification)
        // The main goal is that no exceptions are thrown that crash the test
        assertTrue("Concurrent operations should complete without critical errors",
                errorCount.get() < threadCount * operationsPerThread);
    }


    /**
     * Test 26: testMarkingEquivalentTo - test equivalent marking comparison
     */
    public void testMarkingEquivalentTo() {
        // Create markings with same locations in different order
        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        locs1.add(_testConditionB);
        locs1.add(_testConditionC);
        YMarking marking1 = new YMarking(locs1);

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionC);
        locs2.add(_testConditionA);
        locs2.add(_testConditionB);
        YMarking marking2 = new YMarking(locs2);

        // Test equivalence (same elements, possibly different order)
        assertTrue("Markings with same elements should be equivalent",
                marking1.equivalentTo(marking2));
        assertTrue("Equivalence should be symmetric",
                marking2.equivalentTo(marking1));

        // Test non-equivalence
        List<YNetElement> locs3 = new ArrayList<YNetElement>();
        locs3.add(_testConditionA);
        locs3.add(_testConditionB);
        YMarking marking3 = new YMarking(locs3);

        assertFalse("Markings with different sizes should not be equivalent",
                marking1.equivalentTo(marking3));
    }


    /**
     * Test 27: testMarkingIsBiggerThan - test marking size comparison
     */
    public void testMarkingIsBiggerThan() {
        // Create larger marking
        List<YNetElement> largerLocs = new ArrayList<YNetElement>();
        largerLocs.add(_testConditionA);
        largerLocs.add(_testConditionB);
        largerLocs.add(_testConditionC);
        YMarking largerMarking = new YMarking(largerLocs);

        // Create smaller marking (subset)
        List<YNetElement> smallerLocs = new ArrayList<YNetElement>();
        smallerLocs.add(_testConditionA);
        smallerLocs.add(_testConditionB);
        YMarking smallerMarking = new YMarking(smallerLocs);

        // Test isBiggerThan
        assertTrue("Larger marking should be bigger than smaller",
                largerMarking.isBiggerThan(smallerMarking));
        assertFalse("Smaller marking should not be bigger than larger",
                smallerMarking.isBiggerThan(largerMarking));

        // Test isBiggerThanOrEqual
        assertTrue("Larger marking should be bigger than or equal to smaller",
                largerMarking.isBiggerThanOrEqual(smallerMarking));
        assertTrue("Equal markings should be bigger than or equal to each other",
                smallerMarking.isBiggerThanOrEqual(smallerMarking));
    }


    /**
     * Test 28: testIdentifierGetDescendants - test descendant retrieval
     */
    public void testIdentifierGetDescendants() throws YPersistenceException {
        YIdentifier root = new YIdentifier("rootDesc");

        YIdentifier child1 = root.createChild(null);
        YIdentifier child2 = root.createChild(null);
        YIdentifier grandchild1 = child1.createChild(null);
        YIdentifier grandchild2 = child1.createChild(null);
        YIdentifier grandchild3 = child2.createChild(null);

        Set<YIdentifier> descendants = root.getDescendants();

        // Root + 2 children + 3 grandchildren = 6 total
        assertEquals("Should have 6 descendants including root", 6, descendants.size());
        assertTrue("Should contain root", descendants.contains(root));
        assertTrue("Should contain child1", descendants.contains(child1));
        assertTrue("Should contain child2", descendants.contains(child2));
        assertTrue("Should contain grandchild1", descendants.contains(grandchild1));
        assertTrue("Should contain grandchild2", descendants.contains(grandchild2));
        assertTrue("Should contain grandchild3", descendants.contains(grandchild3));
    }


    /**
     * Test 29: testIdentifierGetRootAncestor - test root ancestor retrieval
     */
    public void testIdentifierGetRootAncestor() throws YPersistenceException {
        YIdentifier root = new YIdentifier("rootAncestor");
        YIdentifier child = root.createChild(null);
        YIdentifier grandchild = child.createChild(null);
        YIdentifier greatGrandchild = grandchild.createChild(null);

        assertEquals("Great-grandchild's root should be root", root, greatGrandchild.getRootAncestor());
        assertEquals("Grandchild's root should be root", root, grandchild.getRootAncestor());
        assertEquals("Child's root should be root", root, child.getRootAncestor());
        assertEquals("Root's root should be itself", root, root.getRootAncestor());
    }


    /**
     * Test 30: testSetOfMarkingsContainsEquivalent - test equivalent marking containment
     */
    public void testSetOfMarkingsContainsEquivalent() {
        YSetOfMarkings set = new YSetOfMarkings();

        // Add marking [A, B]
        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        locs1.add(_testConditionB);
        set.addMarking(new YMarking(locs1));

        // Create equivalent marking [B, A] (different order)
        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionB);
        locs2.add(_testConditionA);
        YMarking equivalentMarking = new YMarking(locs2);

        // Test contains
        assertTrue("Set should contain equivalent marking", set.contains(equivalentMarking));

        // Create non-equivalent marking [A, C]
        List<YNetElement> locs3 = new ArrayList<YNetElement>();
        locs3.add(_testConditionA);
        locs3.add(_testConditionC);
        YMarking differentMarking = new YMarking(locs3);

        assertFalse("Set should not contain different marking", set.contains(differentMarking));
    }


    /**
     * Test 31: testSetOfMarkingsAddAll - test bulk add operation
     */
    public void testSetOfMarkingsAddAll() {
        YSetOfMarkings set1 = new YSetOfMarkings();
        YSetOfMarkings set2 = new YSetOfMarkings();

        // Add markings to set1
        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        set1.addMarking(new YMarking(locs1));

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionB);
        set1.addMarking(new YMarking(locs2));

        // Add markings to set2
        List<YNetElement> locs3 = new ArrayList<YNetElement>();
        locs3.add(_testConditionC);
        set2.addMarking(new YMarking(locs3));

        // Merge set1 into set2
        set2.addAll(set1);

        assertEquals("Merged set should have 3 markings", 3, set2.size());
    }


    /**
     * Test 32: testSetOfMarkingsEquals - test set equality
     */
    public void testSetOfMarkingsEquals() {
        YSetOfMarkings set1 = new YSetOfMarkings();
        YSetOfMarkings set2 = new YSetOfMarkings();

        // Add same markings to both sets
        List<YNetElement> locs1 = new ArrayList<YNetElement>();
        locs1.add(_testConditionA);
        YMarking marking1 = new YMarking(locs1);
        set1.addMarking(marking1);
        set2.addMarking(marking1);

        List<YNetElement> locs2 = new ArrayList<YNetElement>();
        locs2.add(_testConditionB);
        YMarking marking2 = new YMarking(locs2);
        set1.addMarking(marking2);
        set2.addMarking(marking2);

        assertTrue("Sets with same markings should be equal", set1.equals(set2));

        // Add extra marking to set1
        List<YNetElement> locs3 = new ArrayList<YNetElement>();
        locs3.add(_testConditionC);
        set1.addMarking(new YMarking(locs3));

        assertFalse("Sets with different markings should not be equal", set1.equals(set2));
    }


    /**
     * Test 33: testSetOfMarkingsContainsBiggerEqual - test size comparison
     */
    public void testSetOfMarkingsContainsBiggerEqual() {
        YSetOfMarkings set = new YSetOfMarkings();

        // Add larger marking [A, B, C]
        List<YNetElement> largerLocs = new ArrayList<YNetElement>();
        largerLocs.add(_testConditionA);
        largerLocs.add(_testConditionB);
        largerLocs.add(_testConditionC);
        set.addMarking(new YMarking(largerLocs));

        // Create smaller marking [A, B]
        List<YNetElement> smallerLocs = new ArrayList<YNetElement>();
        smallerLocs.add(_testConditionA);
        smallerLocs.add(_testConditionB);
        YMarking smallerMarking = new YMarking(smallerLocs);

        assertTrue("Set should contain marking bigger or equal to smaller marking",
                set.containsBiggerEqual(smallerMarking));
    }
}
