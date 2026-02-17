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

package org.yawlfoundation.yawl.engine.core.marking;

import org.yawlfoundation.yawl.elements.YNetElement;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * Canonical implementation of a Petri-net marking over a YAWL net.
 *
 * <p>A marking is a multi-set of {@link YNetElement} locations in a net.  This class
 * implements all marking algorithms (reachability, enablement, deadlock detection,
 * ordering comparisons) in terms of the minimal {@link IMarkingTask} interface,
 * making it usable with both the stateful engine
 * ({@code org.yawlfoundation.yawl.elements.*}) and the stateless engine
 * ({@code org.yawlfoundation.yawl.stateless.elements.*}).</p>
 *
 * <p>This is the Phase 1 deduplication result for {@code YMarking}.  The stateful
 * {@code org.yawlfoundation.yawl.elements.state.YMarking} and the stateless
 * {@code org.yawlfoundation.yawl.stateless.elements.marking.YMarking} are now thin
 * wrappers that extend this class and implement {@link #netPostset(Set)} to delegate
 * to their own tree's {@code YNet.getPostset()} static method.</p>
 *
 * @author Lachlan Aldred (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 * @since 5.2
 */
public abstract class YCoreMarking {

    /** Multi-set of token locations in this marking. */
    protected List<YNetElement> _locations;


    /**
     * Constructs a marking from an initial list of token locations.
     *
     * @param locations  initial list of token locations
     */
    public YCoreMarking(List<YNetElement> locations) {
        _locations = new Vector<>(locations);
    }


    // =========================================================================
    // Template method hook for deadlock detection
    // =========================================================================

    /**
     * Returns the direct successors (postset) of the given set of net elements
     * within the YAWL net that owns this marking.
     *
     * <p>Concrete subclasses implement this by delegating to their engine tree's
     * {@code YNet.getPostset()} static method.  This is the only tree-specific
     * operation required for deadlock detection.</p>
     *
     * @param elements the set of net elements to compute the postset from
     * @return the union of postsets of all provided elements
     */
    protected abstract Set<? extends YNetElement> netPostset(Set<? extends YNetElement> elements);

    /**
     * Factory method: creates a new instance of the same concrete marking type with
     * the given locations.  Subclasses override this to ensure that intermediate markings
     * created during reachability analysis are of the correct tree-specific type.
     *
     * @param locations the initial token positions for the new marking
     * @return a new marking of the same concrete type
     */
    protected abstract YCoreMarking newInstance(List<YNetElement> locations);


    // =========================================================================
    // Core algorithm: reachability in one step
    // =========================================================================

    /**
     * Computes all markings reachable from this marking in a single task firing.
     *
     * @param task   the task to fire
     * @param orJoin the OR-join being evaluated (used to guard OR-join firing)
     * @return the set of markings reachable in one step, or {@code null} if not enabled
     */
    public YCoreSetOfMarkings reachableInOneStep(IMarkingTask task, IMarkingTask orJoin) {
        YCoreSetOfMarkings halfBakedSet;
        if (_locations.contains(task)) {
            YCoreMarking aMarking = newInstance(_locations);
            aMarking._locations.remove(task);
            halfBakedSet = new YCoreSetOfMarkings();
            halfBakedSet.addMarking(aMarking);
        } else {
            halfBakedSet = doPreliminaryMarkingSetBasedOnJoinType(task);
        }
        if (halfBakedSet == null) {
            return null;
        }

        // For each generated marking, activate the cancellation set and remove tokens
        for (YCoreMarking halfbakedMarking : halfBakedSet.getMarkings()) {
            halfbakedMarking._locations.removeAll(task.getRemoveSet());
        }

        Set<YCoreMarking> iterableHalfBakedSet = halfBakedSet.getMarkings();
        YCoreSetOfMarkings finishedSet = new YCoreSetOfMarkings();
        Set<? extends YNetElement> postset = task.getPostsetElements();

        switch (task.getSplitType()) {
            case IMarkingTask._AND:
            case IMarkingTask._OR: {
                for (YCoreMarking marking : iterableHalfBakedSet) {
                    marking._locations.addAll(postset);
                    finishedSet.addMarking(marking);
                }
                break;
            }
            case IMarkingTask._XOR: {
                for (YCoreMarking halfbakedMarking : iterableHalfBakedSet) {
                    for (YNetElement element : postset) {
                        YCoreMarking aFinalMarking = newInstance(halfbakedMarking.getLocations());
                        aFinalMarking._locations.add(element);
                        finishedSet.addMarking(aFinalMarking);
                    }
                }
                break;
            }
            default:
                throw new IllegalStateException(
                        "Unknown split type: " + task.getSplitType());
        }
        return finishedSet;
    }


    // =========================================================================
    // Powerset helper (used by ResetAnalyser)
    // =========================================================================

    /**
     * Computes the power set of the given set.
     *
     * @param aSet the input set
     * @return the power set (all subsets including the set itself)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Set doPowerSetRecursion(Set aSet) {
        Set powerset = new HashSet();
        powerset.add(aSet);
        for (Iterator iterator = aSet.iterator(); iterator.hasNext(); ) {
            Object o = iterator.next();
            Set smallerSet = new HashSet();
            smallerSet.addAll(aSet);
            smallerSet.remove(o);
            if (smallerSet.size() > 0) {
                powerset.addAll(doPowerSetRecursion(smallerSet));
            }
        }
        return powerset;
    }


    // =========================================================================
    // Internal: join-type-based preliminary marking sets
    // =========================================================================

    @SuppressWarnings("rawtypes")
    private YCoreSetOfMarkings doPreliminaryMarkingSetBasedOnJoinType(IMarkingTask task) {
        Set<? extends YNetElement> preset = task.getPresetElements();
        YCoreSetOfMarkings markingSet = new YCoreSetOfMarkings();
        int joinType = task.getJoinType();
        switch (joinType) {
            case IMarkingTask._AND: {
                if (!nonOrJoinEnabled(task)) {
                    return null;
                }
                YCoreMarking returnedMarking = newInstance(_locations);
                for (YNetElement condition : preset) {
                    returnedMarking._locations.remove(condition);
                }
                markingSet.addMarking(returnedMarking);
                break;
            }
            case IMarkingTask._OR:
                throw new RuntimeException(
                        "This method should never be called on an OR-Join");
            case IMarkingTask._XOR: {
                if (!nonOrJoinEnabled(task)) {
                    return null;
                }
                for (YNetElement condition : preset) {
                    if (_locations.contains(condition)) {
                        YCoreMarking returnedMarking = newInstance(_locations);
                        returnedMarking._locations.remove(condition);
                        markingSet.addMarking(returnedMarking);
                    }
                }
                break;
            }
            default:
                throw new IllegalStateException("Unknown join type: " + joinType);
        }
        return markingSet;
    }


    // =========================================================================
    // Public enablement checks
    // =========================================================================

    /**
     * Checks whether this marking enables the given task.
     * This method must NOT be used for OR-join tasks.
     *
     * @param task the task to test
     * @return {@code true} iff this marking enables the task
     */
    @SuppressWarnings("rawtypes")
    public boolean nonOrJoinEnabled(IMarkingTask task) {
        if (_locations.contains(task)) {
            return true;
        }
        Set<? extends YNetElement> preset = task.getPresetElements();
        int joinType = task.getJoinType();
        switch (joinType) {
            case IMarkingTask._AND:
                return _locations.containsAll(preset);
            case IMarkingTask._OR:
                throw new RuntimeException(
                        "This method should never be called on an OR-Join");
            case IMarkingTask._XOR:
                for (YNetElement condition : preset) {
                    if (_locations.contains(condition)) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }


    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Returns the list of token locations in this marking.
     * @return mutable list of locations (do not modify externally)
     */
    public List<YNetElement> getLocations() {
        return _locations;
    }


    // =========================================================================
    // Object identity
    // =========================================================================

    @Override
    public int hashCode() {
        long hashCode = 0;
        for (YNetElement element : _locations) {
            hashCode += element.hashCode();
        }
        return (int) (hashCode % Integer.MAX_VALUE);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean equals(Object marking) {
        if (!(marking instanceof YCoreMarking otherMarking)) {
            return false;
        }
        List otherMarkingsLocations = new Vector(otherMarking.getLocations());
        List myLocations = new Vector(_locations);
        for (Iterator iterator = myLocations.iterator(); iterator.hasNext(); ) {
            YNetElement netElement = (YNetElement) iterator.next();
            if (otherMarkingsLocations.contains(netElement)) {
                otherMarkingsLocations.remove(netElement);
            } else {
                return false;
            }
        }
        return otherMarkingsLocations.size() <= 0;
    }


    // =========================================================================
    // Ordering comparisons (used by ResetAnalyser)
    // =========================================================================

    /**
     * Returns {@code true} if this marking is greater-than-or-equal with supports
     * (i.e. same bag contents) to the given marking.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean strictlyGreaterThanOrEqualWithSupports(YCoreMarking marking) {
        List otherMarkingsLocations = new Vector(marking.getLocations());
        List myLocations = new Vector(_locations);
        if (!(myLocations.containsAll(otherMarkingsLocations)
                && otherMarkingsLocations.containsAll(myLocations))) {
            return false;
        }
        for (Iterator iterator = otherMarkingsLocations.iterator(); iterator.hasNext(); ) {
            YNetElement netElement = (YNetElement) iterator.next();
            if (myLocations.contains(netElement)) {
                myLocations.remove(netElement);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if this marking is greater than or equal to the given marking.
     * (moe - ResetAnalyser)
     */
    public boolean isBiggerThanOrEqual(YCoreMarking marking) {
        return this.isBiggerThan(marking) || this.equivalentTo(marking);
    }

    /**
     * Returns {@code true} if this marking strictly dominates (is bigger than)
     * the given marking. (moe - ResetAnalyser)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean isBiggerThan(YCoreMarking marking) {
        List otherMarkingsLocations = new Vector(marking.getLocations());
        List myLocations = new Vector(_locations);

        // Test for c1+c2+c3 bigger than c1+c2
        if (myLocations.containsAll(otherMarkingsLocations)
                && !otherMarkingsLocations.containsAll(myLocations)) {
            return true;
        }

        // Test for c1+2c2 bigger than c1+c2
        return myLocations.containsAll(otherMarkingsLocations)
                && otherMarkingsLocations.containsAll(myLocations)
                && myLocations.size() > otherMarkingsLocations.size();
    }

    /**
     * Returns {@code true} if this marking is strictly less than with supports.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean strictlyLessThanWithSupports(YCoreMarking marking) {
        List otherMarkingsLocations = new Vector(marking.getLocations());
        List myLocations = new Vector(_locations);
        if (!(myLocations.containsAll(otherMarkingsLocations)
                && otherMarkingsLocations.containsAll(myLocations))) {
            return false;
        }
        for (Iterator iterator = myLocations.iterator(); iterator.hasNext(); ) {
            YNetElement netElement = (YNetElement) iterator.next();
            if (otherMarkingsLocations.contains(netElement)) {
                otherMarkingsLocations.remove(netElement);
            } else {
                return false;
            }
        }
        return otherMarkingsLocations.size() > 0;
    }

    /**
     * Returns {@code true} if this marking has a strictly larger enabling set for
     * the given OR-join than the other marking.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean isBiggerEnablingMarkingThan(YCoreMarking marking, IMarkingTask orJoin) {
        Set<? extends YNetElement> preset = orJoin.getPresetElements();
        Set thisMarkingsOccupiedPresetElements = new HashSet();
        Set otherMarkingsOccupiedPresetElements = new HashSet();
        for (YNetElement condition : preset) {
            if (this._locations.contains(condition)) {
                thisMarkingsOccupiedPresetElements.add(condition);
            }
            if (marking._locations.contains(condition)) {
                otherMarkingsOccupiedPresetElements.add(condition);
            }
        }
        return thisMarkingsOccupiedPresetElements.containsAll(
                otherMarkingsOccupiedPresetElements)
                && !otherMarkingsOccupiedPresetElements.containsAll(
                        thisMarkingsOccupiedPresetElements);
    }


    // =========================================================================
    // Deadlock detection
    // =========================================================================

    /**
     * Checks whether this marking represents a deadlock with respect to the given OR-join.
     *
     * <p>Uses the {@link #netPostset(Set)} template method to compute the postset,
     * which concrete subclasses implement via their tree-specific {@code YNet.getPostset}.</p>
     *
     * @param orJoin the OR-join task currently being evaluated
     * @return {@code true} if this marking is a deadlock
     */
    public boolean deadLock(IMarkingTask orJoin) {
        for (YNetElement element : _locations) {
            if (element instanceof IMarkingTask) {   // a busy task means not deadlocked
                return false;
            }
        }
        Set<? extends YNetElement> postElements = netPostset(new HashSet<>(_locations));
        for (YNetElement postElement : postElements) {
            IMarkingTask task = (IMarkingTask) postElement;
            if (task.getJoinType() != IMarkingTask._OR) {
                if (nonOrJoinEnabled(task)) {
                    return false;
                }
            } else {
                // must be an OR-join
                for (YNetElement preElement : task.getPresetElements()) {
                    // if we find an OR-join that contains an identifier then
                    // the marking is definitely not deadlocked
                    if (_locations.contains(preElement) && task != orJoin) {
                        return false;
                    }
                }
            }
        }
        return true;
    }


    // =========================================================================
    // Utility
    // =========================================================================

    @Override
    public String toString() {
        return _locations.toString();
    }

    /**
     * Returns {@code true} if this marking is equivalent (same bag) to the given one.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean equivalentTo(YCoreMarking marking) {
        Vector otherMarkingsLocations = new Vector(marking.getLocations());

        // short-circuit test if sizes differ
        if (otherMarkingsLocations.size() != _locations.size()) return false;

        // same size: sort and compare for equality
        Vector thisMarkingsLocations = new Vector(_locations);
        Collections.sort(otherMarkingsLocations);
        Collections.sort(thisMarkingsLocations);

        return thisMarkingsLocations.equals(otherMarkingsLocations);
    }

}
