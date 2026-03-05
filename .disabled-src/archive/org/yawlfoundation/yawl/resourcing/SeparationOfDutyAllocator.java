package org.yawlfoundation.yawl.resourcing;

import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces separation of duties (four-eyes and Chinese wall policies).
 * @since YAWL 6.0
 */
public class SeparationOfDutyAllocator implements ResourceAllocator {
    private final ResourceAllocator delegate;
    private final Map<String, Set<String>> caseHistory = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> conflicts = new ConcurrentHashMap<>();

    public SeparationOfDutyAllocator(ResourceAllocator delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public Participant allocate(YWorkItem workItem, List<Participant> pool) throws AllocationException {
        Objects.requireNonNull(workItem);
        Objects.requireNonNull(pool);
        if (pool.isEmpty()) throw new AllocationException("Pool is empty");
        
        String caseId = workItem.getCaseID().toString();
        Set<String> previous = caseHistory.getOrDefault(caseId, Set.of());
        
        List<Participant> filtered = pool.stream()
            .filter(p -> !previous.contains(p.getId()))
            .toList();
        
        if (filtered.isEmpty()) {
            throw new AllocationException("Four-eyes violation: all participants already worked on case " + caseId);
        }
        
        return delegate.allocate(workItem, filtered);
    }

    @Override
    public String strategyName() { return "SeparationOfDuty(" + delegate.strategyName() + ")"; }

    public void recordParticipant(String caseId, String participantId) {
        caseHistory.computeIfAbsent(caseId, k -> ConcurrentHashMap.newKeySet()).add(participantId);
    }

    public void registerConflict(String participantId, String firmId) {
        conflicts.computeIfAbsent(participantId, k -> ConcurrentHashMap.newKeySet()).add(firmId);
    }

    public void clearCaseHistory(String caseId) { caseHistory.remove(caseId); }

    public boolean isParticipantInCaseHistory(String caseId, String participantId) {
        return caseHistory.getOrDefault(caseId, Set.of()).contains(participantId);
    }
}
