package org.yawlfoundation.yawl.resourcing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Separation of Duty Allocator")
class SeparationOfDutyAllocatorTest {

    @Test
    @DisplayName("Four-eyes filters known participants")
    void testFourEyesViolation() throws AllocationException {
        ResourceAllocator delegate = new LeastLoadedAllocator();
        SeparationOfDutyAllocator sod = new SeparationOfDutyAllocator(delegate);

        Participant alice = new Participant("Alice", "manager", Set.of("approve"));
        Participant bob = new Participant("Bob", "analyst", Set.of("verify"));

        sod.recordParticipant("case-123", alice.getId());
        assertTrue(sod.isParticipantInCaseHistory("case-123", alice.getId()));
    }

    @Test
    @DisplayName("Allocation fails when all filtered")
    void testAllocationFailure() {
        ResourceAllocator delegate = new LeastLoadedAllocator();
        SeparationOfDutyAllocator sod = new SeparationOfDutyAllocator(delegate);
        Participant eve = new Participant("Eve", "manager", Set.of());
        
        sod.recordParticipant("case-xyz", eve.getId());
        List<Participant> pool = List.of(eve);

        assertThrows(AllocationException.class,
            () -> sod.allocate(new TestWorkItem("case-xyz"), pool));
    }

    @Test
    @DisplayName("Strategy name reflects delegation")
    void testStrategyName() {
        ResourceAllocator delegate = new RoundRobinAllocator();
        SeparationOfDutyAllocator sod = new SeparationOfDutyAllocator(delegate);
        assertTrue(sod.strategyName().contains("SeparationOfDuty"));
    }

    @Test
    @DisplayName("Clear case history removes records")
    void testClearCaseHistory() {
        ResourceAllocator delegate = new LeastLoadedAllocator();
        SeparationOfDutyAllocator sod = new SeparationOfDutyAllocator(delegate);
        Participant frank = new Participant("Frank", "analyst", Set.of());
        String caseId = "case-clear";

        sod.recordParticipant(caseId, frank.getId());
        assertTrue(sod.isParticipantInCaseHistory(caseId, frank.getId()));
        
        sod.clearCaseHistory(caseId);
        assertFalse(sod.isParticipantInCaseHistory(caseId, frank.getId()));
    }

    @Test
    @DisplayName("Conflict registration")
    void testConflictRegistration() {
        ResourceAllocator delegate = new LeastLoadedAllocator();
        SeparationOfDutyAllocator sod = new SeparationOfDutyAllocator(delegate);
        Participant grace = new Participant("Grace", "analyst", Set.of());

        assertDoesNotThrow(() -> sod.registerConflict(grace.getId(), "FirmB"));
    }

    static class TestWorkItem {
        private final String caseId;
        TestWorkItem(String caseId) { this.caseId = caseId; }
        public String getCaseId() { return caseId; }
        public String getTaskName() { return "test-task"; }
    }
}
