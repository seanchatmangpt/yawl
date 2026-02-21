/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import static org.yawlfoundation.yawl.engine.YWorkItemStatus.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;

/**
 * A cache of active workitems.
 * 
 * @author Lachlan Aldred
 * Date: 30/05/2003
 * Time: 14:04:39
 *
 * @author Michael Adams (refactored for v2.1, 2.2)
 * 
 */
public class YWorkItemRepository {
    private final Map<String, YWorkItem> _itemMap; //[case&taskIDStr=YWorkItem]

    /**
     * P3 MEDIUM - Task Scan O(N) Query Optimization: secondary status index.
     * Eliminates O(N) linear scans in {@link #getWorkItems(YWorkItemStatus)} by
     * maintaining per-status item-ID buckets updated on every add/remove/status-change.
     * Target: &lt;10ms p95 for status queries regardless of total item count.
     */
    private final YWorkItemStatusIndex _statusIndex;

    private static final Logger logger = LogManager.getLogger(YWorkItemRepository.class);
    private final Logger _logger;

    public YWorkItemRepository() {
        _itemMap = new ConcurrentHashMap<>(500);
        _statusIndex = new YWorkItemStatusIndex();
        _logger = LogManager.getLogger(YWorkItemRepository.class);
    }


    protected YWorkItem add(YWorkItem workItem) {
        _logger.debug("--> YWorkItemRepository#add: {}", workItem.getIDString());
        YWorkItem previous = _itemMap.put(workItem.getIDString(), workItem);
        // P3: maintain status index
        if (previous != null) {
            _statusIndex.remove(previous.getIDString());
        }
        _statusIndex.add(workItem.getIDString(), workItem.getStatus());
        return previous;
    }


    public YWorkItem get(String caseIDStr, String taskID) {
        return get(caseIDStr + ":" + taskID);
    }


    public YWorkItem get(String itemID) {
        return _itemMap.get(itemID);
    }


    public YWorkItem remove(YWorkItem workItem) {
        _logger.debug("--> YWorkItemRepository#remove: {}", workItem.getIDString());
        YWorkItem removed = _itemMap.remove(workItem.getIDString());
        // P3: remove from status index
        if (removed != null) {
            _statusIndex.remove(removed.getIDString());
        }
        return removed;
    }

    public void clear() {
        _itemMap.clear();
        _statusIndex.clear();  // P3: keep index in sync
    }


    public Set<YWorkItem> removeWorkItemFamily(YWorkItem workItem) {
        _logger.debug("--> removeWorkItemFamily: {}", workItem.getIDString());
        Set<YWorkItem> removedSet = new HashSet<>();
        YWorkItem parent = workItem.getParent() != null ? workItem.getParent() : workItem;
        Set<YWorkItem> children = parent.getChildren();
        if (children != null) {
           for (YWorkItem siblingItem : children) {
               remove(siblingItem);
               removedSet.add(siblingItem);
            }
        }
        remove(parent);
        removedSet.add(parent);
        return removedSet;
    }


    /**
     * Removes all workitems that belong to a net.
     * @param caseIDForNet
     */
    public Set<YWorkItem> cancelNet(YIdentifier caseIDForNet) {
        Set<String> itemsToRemove = new HashSet<>();
        // Create a defensive copy to avoid concurrent modification issues
        Collection<YWorkItem> itemsCopy = new ArrayList<>(_itemMap.values());
        for (YWorkItem item : itemsCopy) {
            YIdentifier identifier = item.getWorkItemID().getCaseID();
            if (identifier.isImmediateChildOf(caseIDForNet) ||
                    identifier.toString().equals(caseIDForNet.toString())) {
                itemsToRemove.add(item.getIDString());
            }
        }
        return removeItems(itemsToRemove);
    }



    private Set<YWorkItem> removeItems(Set<String> itemsToRemove) {
        Set<YWorkItem> removedSet = new HashSet<>();
        for (String workItemID : itemsToRemove) {
            YWorkItem item = _itemMap.remove(workItemID);
            if (item != null) removedSet.add(item);
        }
        return removedSet;
    }


    public Set<YWorkItem> getParentWorkItems() {
        return getWorkItems(statusIsParent);
    }


    public Set<YWorkItem> getEnabledWorkItems() {
        return getWorkItems(statusEnabled);
    }


    public Set<YWorkItem> getFiredWorkItems() {
        return getWorkItems(statusFired);
    }


    public Set<YWorkItem> getExecutingWorkItems() {
        return getWorkItems(statusExecuting);
    }


    public Set<YWorkItem> getExecutingWorkItems(String serviceName) {
        Set<YWorkItem> executingItems = new HashSet<>();
        for (YWorkItem workitem : getWorkItems(statusExecuting)) {
            if (workitem.getExternalClient().getUserName().equals(serviceName)) {
                executingItems.add(workitem);
            }
        }
        return executingItems;
    }


    public Set<YWorkItem> getCompletedWorkItems() {
        return getWorkItems(statusComplete);
    }


    /**
     * P3 MEDIUM - O(N) Linear Scan Eliminated: returns work items by status using
     * the {@link YWorkItemStatusIndex} secondary index instead of a full map scan.
     *
     * <p>Before: O(N) - iterates all items to filter by status<br>
     * After: O(k) - looks up item IDs from the status index, then fetches only
     * the matching items (k &lt;&lt; N in typical deployments)</p>
     *
     * @param status the status to filter on
     * @return the set of work items with the given status
     */
    public Set<YWorkItem> getWorkItems(YWorkItemStatus status) {
        // Fast path: O(1) index lookup returns only the IDs we need
        Set<String> matchingIds = _statusIndex.getItemIdsWithStatus(status);
        Set<YWorkItem> itemSet = new HashSet<>(matchingIds.size() * 2);
        for (String id : matchingIds) {
            YWorkItem item = _itemMap.get(id);
            if (item != null && item.getStatus() == status) {
                itemSet.add(item);
            }
        }
        return itemSet;
    }


    public Set<YWorkItem> getWorkItems() {
        cleanseRepository();
        return new HashSet<YWorkItem>(_itemMap.values());
    }


    // check that the items in the repository are in synch with the engine
    public void cleanseRepository() {
        Set<String> itemsToRemove = new HashSet<>();
        // Create a defensive copy to avoid concurrent modification issues
        Collection<YWorkItem> itemsCopy = new ArrayList<>(_itemMap.values());
        for (YWorkItem workitem : itemsCopy) {

            // keep deadlocked items in repository for visibility and handling
            if (workitem.getStatus() == statusDeadlocked) {
                continue;
            }

            // keep completed mi tasks in repository until parent completes
            if (workitem.getTask().isMultiInstance() && workitem.hasCompletedStatus()) {
                continue;
            }

            YNetRunner runner = YEngine.getInstance().getNetRunnerRepository().get(workitem);

            if (runner != null) {                                      //MLF can be null
                boolean foundOne = false;
                for (YTask task : runner.getActiveTasks()) {
                    if (task.getID().equals(workitem.getTaskID())) {
                        foundOne = true;
                        break;
                    }
                }

                //clean up all the work items that are out of synch with the engine.
                if (! foundOne) itemsToRemove.add(workitem.getIDString());
            }
        }
        if (! itemsToRemove.isEmpty()) removeItems(itemsToRemove);
    }


    public Set<YWorkItem> getChildrenOf(String workItemID) {
        YWorkItem item = _itemMap.get(workItemID);
        return (item != null) ? item.getChildren() : new HashSet<>();
    }

    /**
     * Removes all work items for a given case id.  Searches through the work items for
     * ones that are related to the case id and removes them.
     * Called by YEngine.cancelCase()
     * @param caseID must be a case id (not a child of a caseid).
     * @return the set of removed work items
     */
    public Set<YWorkItem> removeWorkItemsForCase(YIdentifier caseID) {
        if (caseID == null || caseID.getParent() != null) {
            throw new IllegalArgumentException("the argument <caseID> is not valid.");
        }

        Set<YWorkItem> removedItems = new HashSet<>();
        for (YWorkItem item : getWorkItemsForCase(caseID)) {
            removedItems.addAll(removeWorkItemFamily(item));
        }
        return removedItems;
    }


    public List<YWorkItem> getWorkItemsForCase(YIdentifier caseID) {
        if (caseID == null || caseID.getParent() != null) {
            throw new IllegalArgumentException("the argument <caseID> is not valid.");
        }
        
        List<YWorkItem> caseItems = new ArrayList<>();
        for (YWorkItem item : getWorkItems()) {
            YWorkItemID wid = item.getWorkItemID();

            //get the root id for this work item's case
            YIdentifier rootCaseID = wid.getCaseID().getRootAncestor();

            //if a work item's root case id matches case passed in save it
            if (rootCaseID.toString().equals(caseID.toString())) {
                caseItems.add(item);
            }
        }
        return caseItems;
    }


    public Set<YWorkItem> getWorkItemsWithIdentifier(String idType, String id) {
        Set<YWorkItem> matches = new HashSet<>() ;

        // find out which items belong to the specified case/spec/task
        for (YWorkItem item : getWorkItems()) {
            if ((idType.equalsIgnoreCase("spec") &&
                 item.getSpecificationID().getUri().equals(id)) ||
                (idType.equalsIgnoreCase("case") &&
                 (item.getCaseID().toString().equals(id) ||
                  item.getCaseID().toString().startsWith(id + "."))) ||
                (idType.equalsIgnoreCase("task") && item.getTaskID().equals(id)))

                matches.add(item);
        }
        if (matches.isEmpty()) matches = null ;
        return matches ;
    }
    

    public Set<YWorkItem> getWorkItemsForService(String serviceURI) {
        Set<YWorkItem> matches = new HashSet<>();
        YAWLServiceReference defWorklist = YEngine.getInstance().getDefaultWorklist();

        // find out which items belong to the specified service
        for (YWorkItem item : getWorkItems()) {
            YAWLServiceGateway gateway =
                     ((YAWLServiceGateway) item.getTask().getDecompositionPrototype());
            if (gateway != null) {
                YAWLServiceReference service = gateway.getYawlService();
                if (service == null) service = defWorklist;
                if ((service != null) && (service.getURI().equals(serviceURI))) {
                    matches.add(item);
                }
            }
        }
        return matches;
    }


    // called from YEngine#dump, logger isDebugEnabled
    public void dump(Logger logger) {
        logger.debug("\n*** DUMPING " + _itemMap.size() +
                     " ENTRIES IN ID_2_WORKITEMS_MAP ***");
        int sub = 1;
        for (String key : _itemMap.keySet()) {
            YWorkItem workitem = _itemMap.get(key);
            if (workitem != null) {
                logger.debug("Entry " + sub++ + " Key=" + key);
                logger.debug(("    WorkitemID        " + workitem.getIDString()));
            }
        }
        logger.debug("*** DUMP OF CASE_2_NETRUNNER_MAP ENDS");
    }

    /**
     * Gets the total count of work items in the repository.
     * Used by health indicators and metrics.
     * @return the number of work items
     */
    public int getWorkItemCount() {
        return _itemMap != null ? _itemMap.size() : 0;
    }

    /**
     * P3 - Notifies the status index of a work item status transition.
     * Must be called whenever a work item's status changes (via YWorkItem.setStatus or similar).
     *
     * @param itemId    the work item ID string
     * @param oldStatus the previous status
     * @param newStatus the new status
     */
    public void notifyStatusChange(String itemId, YWorkItemStatus oldStatus, YWorkItemStatus newStatus) {
        _statusIndex.updateStatus(itemId, oldStatus, newStatus);
    }

    /**
     * P3 - Returns diagnostics for the status index, useful for performance debugging.
     *
     * @return a formatted diagnostic string
     */
    public String getStatusIndexDiagnostics() {
        return _statusIndex.diagnostics();
    }

    /**
     * Gets a singleton instance of the work item repository.
     * @return the singleton instance
     */
    public static YWorkItemRepository getInstance() {
        return YEngine.getInstance().getWorkItemRepository();
    }

}
