package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for WorkflowDef - minimal Petri-net workflow definition.
 * Tests workflow structure definition and query methods.
 *
 * @since Java 21
 */
@DisplayName("Workflow Definition Tests")
class WorkflowDefTest {

    private UUID workflowId;
    private String workflowName;
    private List<Place> places;
    private List<Transition> transitions;
    private Place initialPlace;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();
        workflowName = "InvoiceApprovalProcess";

        // Create places
        initialPlace = new Place("start", "Start", 1);
        Place pending = new Place("pending", "Pending Approval", 0);
        Place approved = new Place("approved", "Approved", 0);
        Place rejected = new Place("rejected", "Rejected", 0);
        Place end = new Place("end", "End", 0);

        places = new ArrayList<>();
        places.add(initialPlace);
        places.add(pending);
        places.add(approved);
        places.add(rejected);
        places.add(end);

        // Create transitions
        Transition submit = new Transition("submit", "Submit Invoice", "automatic");
        Transition approve = new Transition("approve", "Approve", "manual");
        Transition reject = new Transition("reject", "Reject", "manual");
        Transition process = new Transition("process", "Process Approved", "automatic");
        Transition archive = new Transition("archive", "Archive", "automatic", true, false);

        transitions = new ArrayList<>();
        transitions.add(submit);
        transitions.add(approve);
        transitions.add(reject);
        transitions.add(process);
        transitions.add(archive);
    }

    @Nested
    @DisplayName("Construction and Initialization")
    class ConstructionTests {

        @Test
        @DisplayName("Create workflow with all parameters")
        void testCreateFull() {
            String description = "Workflow for approving invoices with multi-level approval";
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName, description,
                    places, transitions, initialPlace);

            assertEquals(workflowId, wf.workflowId());
            assertEquals(workflowName, wf.name());
            assertEquals(description, wf.description());
            assertEquals(initialPlace, wf.getInitialPlace());
            assertEquals(5, wf.places().size());
            assertEquals(5, wf.transitions().size());
        }

        @Test
        @DisplayName("Create workflow without description")
        void testCreateNoDescription() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertEquals("", wf.description());
        }

        @Test
        @DisplayName("Reject null workflow ID")
        void testNullWorkflowId() {
            assertThrows(NullPointerException.class, () ->
                    new WorkflowDef(null, workflowName, places, transitions, initialPlace)
            );
        }

        @Test
        @DisplayName("Reject null name")
        void testNullName() {
            assertThrows(NullPointerException.class, () ->
                    new WorkflowDef(workflowId, null, places, transitions, initialPlace)
            );
        }

        @Test
        @DisplayName("Reject null places list")
        void testNullPlaces() {
            assertThrows(NullPointerException.class, () ->
                    new WorkflowDef(workflowId, workflowName, null, transitions, initialPlace)
            );
        }

        @Test
        @DisplayName("Reject null transitions list")
        void testNullTransitions() {
            assertThrows(NullPointerException.class, () ->
                    new WorkflowDef(workflowId, workflowName, places, null, initialPlace)
            );
        }

        @Test
        @DisplayName("Reject null initial place")
        void testNullInitialPlace() {
            assertThrows(NullPointerException.class, () ->
                    new WorkflowDef(workflowId, workflowName, places, transitions, null)
            );
        }

        @Test
        @DisplayName("Reject initial place not in places list")
        void testInitialPlaceNotInList() {
            Place orphan = new Place("orphan", "Orphan");

            assertThrows(IllegalArgumentException.class, () ->
                    new WorkflowDef(workflowId, workflowName, places, transitions, orphan)
            );
        }
    }

    @Nested
    @DisplayName("Collections Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Places list is unmodifiable")
        void testPlacesImmutable() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertThrows(UnsupportedOperationException.class, () ->
                    wf.places().add(new Place("new", "New"))
            );
        }

        @Test
        @DisplayName("Transitions list is unmodifiable")
        void testTransitionsImmutable() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertThrows(UnsupportedOperationException.class, () ->
                    wf.transitions().add(new Transition("new", "New", "manual"))
            );
        }

        @Test
        @DisplayName("Modifying source list doesn't affect workflow")
        void testSourceListModificationIsolated() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            int originalSize = wf.places().size();
            places.add(new Place("added_later", "Added Later"));

            assertEquals(originalSize, wf.places().size());
        }
    }

    @Nested
    @DisplayName("Place Finding")
    class PlaceFinderTests {

        @Test
        @DisplayName("Find place by ID")
        void testFindPlace() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            Place found = wf.findPlace("approved");

            assertNotNull(found);
            assertEquals("Approved", found.name());
        }

        @Test
        @DisplayName("Return null for non-existent place")
        void testFindPlaceNotFound() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            Place found = wf.findPlace("nonexistent");

            assertNull(found);
        }

        @Test
        @DisplayName("Reject null place ID")
        void testFindPlaceNullId() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertThrows(NullPointerException.class, () ->
                    wf.findPlace(null)
            );
        }
    }

    @Nested
    @DisplayName("Transition Finding")
    class TransitionFinderTests {

        @Test
        @DisplayName("Find transition by ID")
        void testFindTransition() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            Transition found = wf.findTransition("approve");

            assertNotNull(found);
            assertEquals("Approve", found.name());
        }

        @Test
        @DisplayName("Return null for non-existent transition")
        void testFindTransitionNotFound() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            Transition found = wf.findTransition("nonexistent");

            assertNull(found);
        }

        @Test
        @DisplayName("Reject null transition ID")
        void testFindTransitionNullId() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertThrows(NullPointerException.class, () ->
                    wf.findTransition(null)
            );
        }
    }

    @Nested
    @DisplayName("Transition Query")
    class TransitionQueryTests {

        @Test
        @DisplayName("Get transitions from place with tokens")
        void testGetTransitions() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            List<Transition> available = wf.getTransitions(initialPlace);

            assertNotNull(available);
            assertTrue(available.size() > 0, "Should have transitions available from initial place");
        }

        @Test
        @DisplayName("Empty list for place without tokens")
        void testGetTransitionsNoTokens() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            Place empty = new Place("empty", "Empty");
            List<Transition> available = wf.getTransitions(empty);

            assertTrue(available.isEmpty());
        }

        @Test
        @DisplayName("Reject null place")
        void testGetTransitionsNull() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertThrows(NullPointerException.class, () ->
                    wf.getTransitions(null)
            );
        }

        @Test
        @DisplayName("Results are unmodifiable")
        void testTransitionResultsUnmodifiable() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            List<Transition> available = wf.getTransitions(initialPlace);

            assertThrows(UnsupportedOperationException.class, () ->
                    available.add(new Transition("new", "New", "manual"))
            );
        }
    }

    @Nested
    @DisplayName("Validation and Status Checks")
    class ValidationTests {

        @Test
        @DisplayName("Workflow with final transitions is valid")
        void testHasFinalTransition() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertTrue(wf.hasFinalTransition());
        }

        @Test
        @DisplayName("Workflow without final transitions")
        void testNoFinalTransition() {
            List<Transition> noFinal = new ArrayList<>();
            noFinal.add(new Transition("t1", "Task1", "manual", false, false));
            noFinal.add(new Transition("t2", "Task2", "manual", false, false));

            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, noFinal, initialPlace);

            assertFalse(wf.hasFinalTransition());
        }

        @Test
        @DisplayName("Valid workflow structure")
        void testIsValid() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertTrue(wf.isValid());
        }

        @Test
        @DisplayName("Invalid workflow with no places")
        void testInvalidNoPlaces() {
            List<Place> empty = new ArrayList<>();

            assertThrows(IllegalArgumentException.class, () ->
                    new WorkflowDef(workflowId, workflowName, empty, transitions, initialPlace)
            );
        }
    }

    @Nested
    @DisplayName("Counts and Statistics")
    class CountTests {

        @Test
        @DisplayName("Place count")
        void testPlaceCount() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertEquals(5, wf.placeCount());
        }

        @Test
        @DisplayName("Transition count")
        void testTransitionCount() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertEquals(5, wf.transitionCount());
        }

        @Test
        @DisplayName("Counts match list sizes")
        void testCountsMatchLists() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertEquals(wf.places().size(), wf.placeCount());
            assertEquals(wf.transitions().size(), wf.transitionCount());
        }
    }

    @Nested
    @DisplayName("Equality and Identity")
    class EqualityTests {

        @Test
        @DisplayName("Workflows with same ID are equal")
        void testEquality() {
            WorkflowDef wf1 = new WorkflowDef(workflowId, "Workflow1",
                    places, transitions, initialPlace);

            List<Place> places2 = new ArrayList<>(places);
            List<Transition> transitions2 = new ArrayList<>(transitions);
            WorkflowDef wf2 = new WorkflowDef(workflowId, "Workflow2",
                    places2, transitions2, initialPlace);

            assertEquals(wf1, wf2);
            assertEquals(wf1.hashCode(), wf2.hashCode());
        }

        @Test
        @DisplayName("Name and structure excluded from equality")
        void testEqualityIgnoresStructure() {
            List<Place> places2 = new ArrayList<>();
            places2.add(new Place("p1", "Place1"));

            List<Transition> transitions2 = new ArrayList<>();
            transitions2.add(new Transition("t1", "Task1", "manual"));

            WorkflowDef wf1 = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);
            WorkflowDef wf2 = new WorkflowDef(workflowId, "DifferentName",
                    places2, transitions2, places2.get(0));

            assertEquals(wf1, wf2);
        }

        @Test
        @DisplayName("Different workflow IDs are not equal")
        void testDifferentIds() {
            UUID otherId = UUID.randomUUID();

            WorkflowDef wf1 = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);
            WorkflowDef wf2 = new WorkflowDef(otherId, workflowName,
                    places, transitions, initialPlace);

            assertNotEquals(wf1, wf2);
        }
    }

    @Nested
    @DisplayName("String Representation")
    class ToStringTests {

        @Test
        @DisplayName("toString includes workflow information")
        void testToString() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);
            String str = wf.toString();

            assertTrue(str.contains("WorkflowDef"));
            assertTrue(str.contains(workflowName));
            assertTrue(str.contains("5")); // 5 places
            assertTrue(str.contains("5")); // 5 transitions
        }
    }

    @Nested
    @DisplayName("Complex Workflow Patterns")
    class WorkflowPatternTests {

        @Test
        @DisplayName("Simple sequential workflow")
        void testSequentialWorkflow() {
            List<Place> seqPlaces = new ArrayList<>();
            seqPlaces.add(new Place("p1", "Start", 1));
            seqPlaces.add(new Place("p2", "Middle", 0));
            seqPlaces.add(new Place("p3", "End", 0));

            List<Transition> seqTransitions = new ArrayList<>();
            seqTransitions.add(new Transition("t1", "Task1", "manual"));
            seqTransitions.add(new Transition("t2", "Task2", "manual", true, false));

            Place start = seqPlaces.get(0);
            WorkflowDef wf = new WorkflowDef(workflowId, "Sequential",
                    seqPlaces, seqTransitions, start);

            assertTrue(wf.isValid());
            assertEquals(3, wf.placeCount());
            assertEquals(2, wf.transitionCount());
            assertTrue(wf.hasFinalTransition());
        }

        @Test
        @DisplayName("Parallel branching workflow")
        void testParallelWorkflow() {
            List<Place> parPlaces = new ArrayList<>();
            parPlaces.add(new Place("start", "Start", 1));
            parPlaces.add(new Place("branch1", "Branch1", 0));
            parPlaces.add(new Place("branch2", "Branch2", 0));
            parPlaces.add(new Place("join", "Join", 0));

            List<Transition> parTransitions = new ArrayList<>();
            parTransitions.add(new Transition("split", "Split", "automatic"));
            parTransitions.add(new Transition("task1", "Task1", "manual"));
            parTransitions.add(new Transition("task2", "Task2", "manual"));
            parTransitions.add(new Transition("join_t", "Join", "automatic", true, false));

            WorkflowDef wf = new WorkflowDef(workflowId, "Parallel",
                    parPlaces, parTransitions, parPlaces.get(0));

            assertTrue(wf.isValid());
            assertEquals(4, wf.placeCount());
            assertEquals(4, wf.transitionCount());
        }

        @Test
        @DisplayName("Multi-instance workflow")
        void testMultiInstanceWorkflow() {
            List<Place> miPlaces = new ArrayList<>();
            miPlaces.add(new Place("input", "Input", 3));
            miPlaces.add(new Place("output", "Output", 0));

            List<Transition> miTransitions = new ArrayList<>();
            miTransitions.add(new Transition("process", "Process", "automatic", true, true));

            WorkflowDef wf = new WorkflowDef(workflowId, "MultiInstance",
                    miPlaces, miTransitions, miPlaces.get(0));

            assertTrue(wf.isValid());
            assertTrue(wf.hasFinalTransition());

            Transition process = wf.findTransition("process");
            assertTrue(process.isMultiInstance());
        }
    }

    @Nested
    @DisplayName("Initial Place Management")
    class InitialPlaceTests {

        @Test
        @DisplayName("Get initial place returns correct place")
        void testGetInitialPlace() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertEquals(initialPlace, wf.getInitialPlace());
            assertEquals("Start", wf.getInitialPlace().name());
        }

        @Test
        @DisplayName("Initial place is in places list")
        void testInitialPlaceInList() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            assertTrue(wf.places().contains(initialPlace));
        }

        @Test
        @DisplayName("Workflow execution starts at initial place")
        void testStartsAtInitial() {
            WorkflowDef wf = new WorkflowDef(workflowId, workflowName,
                    places, transitions, initialPlace);

            Place start = wf.getInitialPlace();
            assertTrue(start.hasTokens(), "Initial place should have tokens to start");
        }
    }
}
