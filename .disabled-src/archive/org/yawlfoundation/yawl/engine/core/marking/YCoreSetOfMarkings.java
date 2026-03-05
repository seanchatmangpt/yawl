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

import java.util.HashSet;
import java.util.Set;

/**
 * Canonical implementation of a set of Petri-net markings.
 *
 * <p>This is the Phase 1 deduplication result for {@code YSetOfMarkings}.  The
 * stateful {@code org.yawlfoundation.yawl.elements.state.YSetOfMarkings} and the
 * stateless {@code org.yawlfoundation.yawl.stateless.elements.marking.YSetOfMarkings}
 * are now thin wrappers that extend this class.</p>
 *
 * <p>The only difference between the two original files was:
 * <ul>
 *   <li>The stateless version declared {@code _markings} as {@code final}.</li>
 *   <li>Package and import declarations differed.</li>
 * </ul>
 * This unified implementation declares {@code _markings} as {@code final} (the
 * superior choice, as the field is never re-assigned in either implementation).</p>
 *
 * @author Lachlan Aldred (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 * @since 5.2
 */
public class YCoreSetOfMarkings {

    private final Set<YCoreMarking> _markings = new HashSet<>();


    // =========================================================================
    // Mutation
    // =========================================================================

    /**
     * Adds the given marking to this set if an equivalent marking is not already present.
     * (moe - ResetAnalyser)
     *
     * @param marking the marking to add
     */
    public void addMarking(YCoreMarking marking) {
        if (!contains(marking)) {
            _markings.add(marking);
        }
    }

    /**
     * Adds all markings from {@code newMarkings} that are not already present.
     * (moe - ResetAnalyser)
     *
     * @param newMarkings the set of markings to merge in
     */
    public void addAll(YCoreSetOfMarkings newMarkings) {
        for (YCoreMarking marking : newMarkings.getMarkings()) {
            addMarking(marking);
        }
    }

    /**
     * Removes all markings from this set.
     * (moe - ResetAnalyser)
     */
    public void removeAll() {
        _markings.clear();
    }

    /**
     * Removes a single marking from this set.
     *
     * @param marking the marking to remove
     */
    public void removeMarking(YCoreMarking marking) {
        _markings.remove(marking);
    }


    // =========================================================================
    // Query
    // =========================================================================

    /**
     * Returns {@code true} if this set contains a marking equivalent to the given one.
     * (changed by moe - ResetAnalyser)
     *
     * @param marking the marking to test
     * @return {@code true} iff an equivalent marking is already in this set
     */
    public boolean contains(YCoreMarking marking) {
        for (YCoreMarking yMarking : _markings) {
            if (yMarking.equivalentTo(marking)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if this set equals the given set of markings.
     * (added by moe - ResetAnalyser)
     *
     * @param markings the other set to compare with
     * @return {@code true} iff both sets contain equivalent markings
     */
    public boolean equals(YCoreSetOfMarkings markings) {
        Set<YCoreMarking> markingsToCompare = markings.getMarkings();
        return (_markings.size() == markingsToCompare.size())
                && containsAll(markingsToCompare)
                && markings.containsAll(_markings);
    }

    /**
     * Returns {@code true} if this set contains equivalents for all markings in
     * the given raw set.
     *
     * @param markingsToCompare the set of markings to check membership for
     * @return {@code true} iff all given markings have equivalents in this set
     */
    public boolean containsAll(Set<YCoreMarking> markingsToCompare) {
        for (YCoreMarking yMarking : markingsToCompare) {
            if (!this.contains(yMarking)) return false;
        }
        return true;
    }

    /**
     * Returns {@code true} if any marking in this set is equivalent to any marking
     * in {@code possibleFutureMarkingSet}.
     *
     * @param possibleFutureMarkingSet the set to compare with
     * @return {@code true} iff at least one equivalent pair exists
     */
    public boolean containsEquivalentMarkingTo(YCoreSetOfMarkings possibleFutureMarkingSet) {
        for (YCoreMarking possibleMarking : possibleFutureMarkingSet.getMarkings()) {
            for (YCoreMarking marking : _markings) {
                if (possibleMarking.equivalentTo(marking)) return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any marking in this set is bigger than or equal to
     * the given marking. (moe - ResetAnalyser)
     *
     * @param m the marking to compare against
     * @return {@code true} iff this set contains a dominating marking
     */
    public boolean containsBiggerEqual(YCoreMarking m) {
        for (YCoreMarking marking : _markings) {
            if (marking.isBiggerThanOrEqual(m)) return true;
        }
        return false;
    }

    /**
     * Returns the underlying set of markings.
     *
     * @return the set of markings (do not modify externally)
     */
    public Set<YCoreMarking> getMarkings() {
        return _markings;
    }

    /**
     * Returns the number of markings in this set.
     *
     * @return the cardinality of this set
     */
    public int size() {
        return _markings.size();
    }

    /**
     * Removes and returns one arbitrary marking from this set.
     *
     * @return a marking from this set, or {@code null} if empty
     */
    public YCoreMarking removeAMarking() {
        if (_markings.isEmpty()) {
            return null;
        }
        YCoreMarking marking = _markings.iterator().next();
        _markings.remove(marking);
        return marking;
    }

}
