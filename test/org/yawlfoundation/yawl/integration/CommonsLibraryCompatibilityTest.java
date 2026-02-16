package org.yawlfoundation.yawl.integration;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;
import org.apache.commons.collections4.collection.CollectionUtils;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Commons Library Compatibility Tests - Verifies commons-collections API compatibility
 * Tests that API changes don't break YAWL code after upgrade to 4.4
 */
public class CommonsLibraryCompatibilityTest {

    @Test
    public void testBagOperations() {
        Bag<String> bag = new HashBag<>();
        
        bag.add("apple");
        bag.add("apple");
        bag.add("banana");
        bag.add("banana");
        bag.add("banana");
        
        assertEquals("Bag should contain 2 apples", 2, bag.getCount("apple"));
        assertEquals("Bag should contain 3 bananas", 3, bag.getCount("banana"));
        assertEquals("Bag size should be 5", 5, bag.size());
        
        bag.remove("apple", 2);
        assertEquals("Apples should be removed", 0, bag.getCount("apple"));
    }

    @Test
    public void testCollectionUtils() {
        List<Integer> list1 = Arrays.asList(1, 2, 3, 4, 5);
        List<Integer> list2 = Arrays.asList(4, 5, 6, 7, 8);
        
        Collection<Integer> intersection = CollectionUtils.intersection(list1, list2);
        assertEquals("Intersection should contain 2 elements", 2, intersection.size());
        assertTrue("Intersection should contain 4", intersection.contains(4));
        assertTrue("Intersection should contain 5", intersection.contains(5));
        
        Collection<Integer> union = CollectionUtils.union(list1, list2);
        assertTrue("Union should contain 8 elements", union.size() >= 8);
        
        Collection<Integer> subtract = CollectionUtils.subtract(list1, list2);
        assertEquals("Subtraction should give 3 elements", 3, subtract.size());
    }

    @Test
    public void testLinkedMap() {
        LinkedMap<String, String> map = new LinkedMap<>();
        
        map.put("first", "1");
        map.put("second", "2");
        map.put("third", "3");
        
        assertEquals("First key should be 'first'", "first", map.firstKey());
        assertEquals("Last key should be 'third'", "third", map.lastKey());
        
        String previous = map.previousKey("third");
        assertEquals("Previous key should be 'second'", "second", previous);
        
        String next = map.nextKey("first");
        assertEquals("Next key should be 'second'", "second", next);
    }

    @Test
    public void testCircularFifoQueue() {
        CircularFifoQueue<String> queue = new CircularFifoQueue<>(3);
        
        queue.add("task1");
        queue.add("task2");
        queue.add("task3");
        
        assertEquals("Queue should be full", 3, queue.size());
        
        queue.add("task4");
        assertEquals("Queue should stay at capacity", 3, queue.size());
        assertFalse("Queue should have dropped first element", queue.contains("task1"));
        assertTrue("Queue should contain task4", queue.contains("task4"));
    }

    @Test
    public void testMapIteration() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("process_1", "active");
        map.put("process_2", "waiting");
        map.put("process_3", "completed");
        
        StringBuilder order = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            order.append(entry.getKey()).append(",");
        }
        
        assertEquals("Iteration order should be insertion order", 
            "process_1,process_2,process_3,", order.toString());
    }

    @Test
    public void testCollectionIsEmpty() {
        List<String> emptyList = new ArrayList<>();
        List<String> filledList = Arrays.asList("a", "b", "c");
        
        assertTrue("Empty list should be detected", CollectionUtils.isEmpty(emptyList));
        assertFalse("Filled list should not be empty", CollectionUtils.isEmpty(filledList));
        
        assertTrue("Null collection should be empty", CollectionUtils.isEmpty(null));
    }

    @Test
    public void testPredicateFiltering() {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        
        Collection<Integer> evens = CollectionUtils.select(numbers, num -> num % 2 == 0);
        assertEquals("Should have 5 even numbers", 5, evens.size());
        
        Collection<Integer> odds = CollectionUtils.select(numbers, num -> num % 2 != 0);
        assertEquals("Should have 5 odd numbers", 5, odds.size());
    }

    @Test
    public void testSetOperations() {
        Set<String> set1 = new HashSet<>(Arrays.asList("a", "b", "c"));
        Set<String> set2 = new HashSet<>(Arrays.asList("c", "d", "e"));
        
        assertTrue("'a' should be in set1", set1.contains("a"));
        assertTrue("'c' should be in both sets", set1.contains("c") && set2.contains("c"));
        
        Collection<String> common = CollectionUtils.intersection(set1, set2);
        assertEquals("Common element count should be 1", 1, common.size());
        assertTrue("Common element should be 'c'", common.contains("c"));
    }

    @Test
    public void testWorkflowTaskCollection() {
        // Simulates workflow task management with collections4
        Bag<String> taskStates = new HashBag<>();
        
        taskStates.add("running");
        taskStates.add("running");
        taskStates.add("running");
        taskStates.add("pending");
        taskStates.add("pending");
        taskStates.add("completed");
        
        assertEquals("Should have 3 running tasks", 3, taskStates.getCount("running"));
        assertEquals("Should have 2 pending tasks", 2, taskStates.getCount("pending"));
        assertEquals("Should have 1 completed task", 1, taskStates.getCount("completed"));
        
        // Update task states
        taskStates.remove("running", 1);
        taskStates.add("completed");
        
        assertEquals("Running count should decrease", 2, taskStates.getCount("running"));
        assertEquals("Completed count should increase", 2, taskStates.getCount("completed"));
    }

    @Test
    public void testMapTransformation() {
        Map<String, Integer> taskDurations = new LinkedMap<>();
        taskDurations.put("task_1", 100);
        taskDurations.put("task_2", 200);
        taskDurations.put("task_3", 150);
        
        Collection<Integer> durations = taskDurations.values();
        int totalDuration = durations.stream().mapToInt(Integer::intValue).sum();
        
        assertEquals("Total duration should be 450", 450, totalDuration);
        assertTrue("All durations should be positive", durations.stream().allMatch(d -> d > 0));
    }
}
