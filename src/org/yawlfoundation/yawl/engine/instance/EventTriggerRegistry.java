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

package org.yawlfoundation.yawl.engine.instance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.elements.state.YIdentifier;

/**
 * Registry for task-level event triggers supporting WCP-34 through WCP-38.
 *
 * <p>This registry manages the mapping between events and work items that subscribe to them.
 * It enables task-scoped event triggering patterns including:
 * <ul>
 *   <li>WCP-34: Escalation Pattern (event triggers escalation to next level)</li>
 *   <li>WCP-35: Event-Based Selection (event selects among alternative paths)</li>
 *   <li>WCP-36: Event-Based Routing (event determines routing destination)</li>
 *   <li>WCP-37: Event-Based Cancellation (external event cancels task)</li>
 *   <li>WCP-38: Cancelling Task (task completion cancels others)</li>
 * </ul>
 * </p>
 *
 * <p>Thread-safe event matching and firing using {@link ReentrantReadWriteLock} to permit
 * concurrent reads from event publishers while serializing concurrent writes during subscription
 * and trigger firing. Virtual thread-aware locking (no synchronized keyword).</p>
 *
 * <h2>Usage</h2>
 * <pre>
 * EventTriggerRegistry registry = EventTriggerRegistry.getInstance();
 * LocalEventTrigger trigger = new LocalEventTrigger("escalation", workItem, 5000L);
 * registry.registerTrigger(caseID, trigger);
 * registry.fireTriggersForEvent(caseID, "escalation", eventData);
 * registry.unregisterTriggersForCase(caseID);
 * </pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 * @see LocalEventTrigger
 * @see TriggerSubscriptionManager
 */
public class EventTriggerRegistry {

    private static final Logger _logger = LogManager.getLogger(EventTriggerRegistry.class);
    private static EventTriggerRegistry _instance;

    /**
     * Map of case ID -> event type -> list of active triggers for that event.
     * Outer map is ConcurrentHashMap for thread-safe case isolation.
     * Inner map holds event type to trigger list mappings.
     */
    private final ConcurrentHashMap<String, Map<String, List<LocalEventTrigger>>> _triggers;

    /** Lock for thread-safe access to registry structure. */
    private final ReentrantReadWriteLock _lock = new ReentrantReadWriteLock();

    /**
     * Singleton accessor.
     *
     * @return the unique EventTriggerRegistry instance
     */
    public synchronized static EventTriggerRegistry getInstance() {
        if (_instance == null) {
            _instance = new EventTriggerRegistry();
        }
        return _instance;
    }

    /**
     * Constructs a new EventTriggerRegistry.
     * Package-private to enforce singleton pattern.
     */
    protected EventTriggerRegistry() {
        _triggers = new ConcurrentHashMap<>();
    }

    /**
     * Registers an event trigger for a specific case.
     *
     * <p>The trigger is indexed by case ID and event type for O(1) lookup on event fire.
     * Multiple triggers can subscribe to the same event within a case; all will be fired
     * when the event occurs.</p>
     *
     * @param caseID the case identifier
     * @param trigger the event trigger to register
     * @throws IllegalArgumentException if caseID or trigger is null
     */
    public void registerTrigger(String caseID, LocalEventTrigger trigger) {
        if (caseID == null || caseID.trim().isEmpty()) {
            throw new IllegalArgumentException("caseID cannot be null or empty");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger cannot be null");
        }

        _lock.writeLock().lock();
        try {
            String eventType = trigger.getEventType();
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.computeIfAbsent(caseID,
                    k -> new ConcurrentHashMap<>());
            caseMap.computeIfAbsent(eventType, k -> new ArrayList<>()).add(trigger);
            _logger.debug("Registered trigger for case={}, event={}, task={}",
                    caseID, eventType, trigger.getWorkItemID());
        } finally {
            _lock.writeLock().unlock();
        }
    }

    /**
     * Unregisters a specific event trigger.
     *
     * <p>The trigger is removed from the registry. If this was the last trigger for
     * the event type, the event entry is also removed. If this was the last trigger
     * for the case, the case entry is also removed.</p>
     *
     * @param caseID the case identifier
     * @param trigger the trigger to unregister
     * @return true if the trigger was found and removed; false otherwise
     */
    public boolean unregisterTrigger(String caseID, LocalEventTrigger trigger) {
        if (caseID == null || trigger == null) {
            return false;
        }

        _lock.writeLock().lock();
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.get(caseID);
            if (caseMap == null) {
                return false;
            }

            String eventType = trigger.getEventType();
            List<LocalEventTrigger> triggers = caseMap.get(eventType);
            if (triggers == null) {
                return false;
            }

            boolean removed = triggers.remove(trigger);
            if (removed) {
                _logger.debug("Unregistered trigger for case={}, event={}, task={}",
                        caseID, eventType, trigger.getWorkItemID());

                // Clean up empty lists
                if (triggers.isEmpty()) {
                    caseMap.remove(eventType);
                    if (caseMap.isEmpty()) {
                        _triggers.remove(caseID);
                    }
                }
            }
            return removed;
        } finally {
            _lock.writeLock().unlock();
        }
    }

    /**
     * Fires all triggers matching a given event type in a case.
     *
     * <p>For each matching trigger:
     * <ol>
     *   <li>Checks if trigger condition is satisfied</li>
     *   <li>Fires the trigger with provided event data</li>
     *   <li>Removes the trigger from registry on successful fire</li>
     *   <li>Logs any firing errors</li>
     * </ol>
     * </p>
     *
     * @param caseID the case identifier
     * @param eventType the event type (e.g., "escalation", "cancellation")
     * @param eventData optional data associated with the event (may be null)
     * @return number of triggers successfully fired
     */
    public int fireTriggersForEvent(String caseID, String eventType, String eventData) {
        if (caseID == null || eventType == null) {
            return 0;
        }

        _lock.readLock().lock();
        List<LocalEventTrigger> triggersToFire;
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.get(caseID);
            if (caseMap == null) {
                _logger.trace("No triggers registered for case={}", caseID);
                return 0;
            }

            List<LocalEventTrigger> triggers = caseMap.get(eventType);
            if (triggers == null || triggers.isEmpty()) {
                _logger.trace("No triggers for event={} in case={}", eventType, caseID);
                return 0;
            }

            // Take a snapshot to avoid holding read lock during fire operations
            triggersToFire = new ArrayList<>(triggers);
        } finally {
            _lock.readLock().unlock();
        }

        int firedCount = 0;
        for (LocalEventTrigger trigger : triggersToFire) {
            try {
                if (trigger.shouldFire()) {
                    trigger.fire(eventData);
                    firedCount++;
                    _logger.info("Fired trigger for case={}, event={}, task={}, data={}",
                            caseID, eventType, trigger.getWorkItemID(), eventData);

                    // Unregister after successful fire
                    unregisterTrigger(caseID, trigger);
                }
            } catch (Exception e) {
                _logger.error("Error firing trigger for case={}, event={}, task={}",
                        caseID, eventType, trigger.getWorkItemID(), e);
            }
        }

        return firedCount;
    }

    /**
     * Retrieves all active triggers for a specific event in a case.
     *
     * <p>Returns a copy of the trigger list to allow safe iteration without holding locks.</p>
     *
     * @param caseID the case identifier
     * @param eventType the event type
     * @return list of active triggers; empty list if none exist
     */
    public List<LocalEventTrigger> getTriggersForEvent(String caseID, String eventType) {
        if (caseID == null || eventType == null) {
            return Collections.emptyList();
        }

        _lock.readLock().lock();
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.get(caseID);
            if (caseMap == null) {
                return Collections.emptyList();
            }

            List<LocalEventTrigger> triggers = caseMap.get(eventType);
            return triggers != null ? new ArrayList<>(triggers) : Collections.emptyList();
        } finally {
            _lock.readLock().unlock();
        }
    }

    /**
     * Retrieves all active triggers for a case across all event types.
     *
     * @param caseID the case identifier
     * @return list of all active triggers in the case; empty list if case not found
     */
    public List<LocalEventTrigger> getTriggersForCase(String caseID) {
        if (caseID == null) {
            return Collections.emptyList();
        }

        _lock.readLock().lock();
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.get(caseID);
            if (caseMap == null) {
                return Collections.emptyList();
            }

            List<LocalEventTrigger> allTriggers = new ArrayList<>();
            for (List<LocalEventTrigger> triggerList : caseMap.values()) {
                allTriggers.addAll(triggerList);
            }
            return allTriggers;
        } finally {
            _lock.readLock().unlock();
        }
    }

    /**
     * Unregisters all triggers for a case.
     *
     * <p>Called when a case completes or is cancelled to clean up all associated triggers
     * and cancel any pending timeouts.</p>
     *
     * @param caseID the case identifier
     * @return number of triggers cancelled
     */
    public int unregisterTriggersForCase(String caseID) {
        if (caseID == null) {
            return 0;
        }

        _lock.writeLock().lock();
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.remove(caseID);
            if (caseMap == null) {
                return 0;
            }

            int count = 0;
            for (List<LocalEventTrigger> triggers : caseMap.values()) {
                for (LocalEventTrigger trigger : triggers) {
                    try {
                        trigger.cancel();
                        count++;
                    } catch (Exception e) {
                        _logger.warn("Error cancelling trigger during case cleanup: case={}",
                                caseID, e);
                    }
                }
            }

            _logger.info("Unregistered {} triggers for case={}", count, caseID);
            return count;
        } finally {
            _lock.writeLock().unlock();
        }
    }

    /**
     * Checks if any triggers are registered for a case.
     *
     * @param caseID the case identifier
     * @return true if triggers exist for the case; false otherwise
     */
    public boolean hasTriggersForCase(String caseID) {
        if (caseID == null) {
            return false;
        }

        _lock.readLock().lock();
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.get(caseID);
            return caseMap != null && !caseMap.isEmpty();
        } finally {
            _lock.readLock().unlock();
        }
    }

    /**
     * Checks if any triggers are registered for a specific event in a case.
     *
     * @param caseID the case identifier
     * @param eventType the event type
     * @return true if triggers exist for the event; false otherwise
     */
    public boolean hasTriggersForEvent(String caseID, String eventType) {
        if (caseID == null || eventType == null) {
            return false;
        }

        _lock.readLock().lock();
        try {
            Map<String, List<LocalEventTrigger>> caseMap = _triggers.get(caseID);
            if (caseMap == null) {
                return false;
            }
            List<LocalEventTrigger> triggers = caseMap.get(eventType);
            return triggers != null && !triggers.isEmpty();
        } finally {
            _lock.readLock().unlock();
        }
    }

    /**
     * Clears all triggers from the registry.
     *
     * <p>Used during engine shutdown or for testing. Cancels all active timeouts.</p>
     *
     * @return total number of triggers cleared
     */
    public int clearAllTriggers() {
        _lock.writeLock().lock();
        try {
            int totalCount = 0;
            for (Map<String, List<LocalEventTrigger>> caseMap : _triggers.values()) {
                for (List<LocalEventTrigger> triggers : caseMap.values()) {
                    for (LocalEventTrigger trigger : triggers) {
                        try {
                            trigger.cancel();
                            totalCount++;
                        } catch (Exception e) {
                            _logger.warn("Error cancelling trigger during cleanup", e);
                        }
                    }
                }
            }
            _triggers.clear();
            _logger.info("Cleared all {} triggers from registry", totalCount);
            return totalCount;
        } finally {
            _lock.writeLock().unlock();
        }
    }

    /**
     * Returns the count of active triggers in the registry.
     *
     * @return total count of all triggers across all cases
     */
    public int getTotalTriggerCount() {
        _lock.readLock().lock();
        try {
            int count = 0;
            for (Map<String, List<LocalEventTrigger>> caseMap : _triggers.values()) {
                for (List<LocalEventTrigger> triggers : caseMap.values()) {
                    count += triggers.size();
                }
            }
            return count;
        } finally {
            _lock.readLock().unlock();
        }
    }
}
