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

package org.yawlfoundation.yawl.elements.state;

import java.util.List;

import org.yawlfoundation.yawl.elements.YConditionInterface;
import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.YIdentifierBag;

/**
 * 
 * @author Lachlan Aldred
 * Date: 24/04/2003
 * Time: 10:17:37
 * 
 */
public class YInternalCondition extends YNetElement implements YConditionInterface {
    private YIdentifierBag _bag;
    public YTask _myTask;
    public static String _mi_active = "mi_active";
    public static String _mi_entered = "mi_entered";
    public static String _mi_executing = "mi_executing";
    public static String _mi_complete = "mi_complete";


    public YInternalCondition(String id, YTask myTask) {
        super(id);
        _bag = new YIdentifierBag(this);
        _myTask = myTask;
    }

    /**
     * Adds an identifier to the collection.
     * @param identifier
     */
    public void add(YPersistenceManager pmgr, YIdentifier identifier) throws YPersistenceException {
        _bag.addIdentifier(pmgr, identifier);
    }

    /**
     * @param identifier
     * @return true iff this contains identifier.
     */
    public boolean contains(YIdentifier identifier) {
        return _bag.contains(identifier);
    }

    /**
     * @return true iff this contains one or more identifier.
     */
    public boolean containsIdentifier() {
        return _bag.getIdentifiers().size() > 0;
    }

    /**
     * Get the number of identifier matching the the one passed in.
     * @param identifier
     * @return the number of equal identifiers in this.
     */
    public int getAmount(YIdentifier identifier) {
        return _bag.getAmount(identifier);
    }

    /**
     * @return a List of the identifiers in the condition numbering 1 or more.
     */
    public List<YIdentifier> getIdentifiers() {
        return _bag.getIdentifiers();
    }

    /**
     * Removes one YIdentifier from this condition.  If there are none
     * inside then make no change to the state of this.
     */
    public YIdentifier removeOne(YPersistenceManager pmgr) throws YPersistenceException {
        YIdentifier id = getIdentifiers().get(0);
        try {
            _bag.remove(pmgr, id, 1);
        } catch (YStateException e) {
            throw new RuntimeException(e); // Should not happen: id was just retrieved from bag
        }
        return id;
    }

    /**
     * Removes one YIdentifier equal to identifier from the condition. If there are none
     * inside then make no change to the state of this.
     * @param identifier
     */
    public void removeOne(YPersistenceManager pmgr, YIdentifier identifier) throws YPersistenceException {
        try {
            _bag.remove(pmgr, identifier, 1);
        } catch (YStateException e) {
            throw new RuntimeException(e); // Should not happen: caller must ensure identifier is in bag
        }
    }

    /**
     * Remove from this amount YIdentifiers equal to identifier.
     * @param identifier
     * @param amount the amount to remove.
     * @throws YStateException iff amount is greater than the number of YIdentifiers
     * held inside this, and further more no change will be made to the state of this.
     */
    public void remove(YPersistenceManager pmgr, YIdentifier identifier, int amount) throws YStateException, YPersistenceException {
        _bag.remove(pmgr, identifier, amount);
    }

    /**
     * Removes all the YIdentifiers equal to identifier.
     * @param identifier
     */
    public void removeAll(YPersistenceManager pmgr, YIdentifier identifier) throws YPersistenceException {
        int amount = _bag.getAmount(identifier);
        if (amount > 0) {
            try {
                _bag.remove(pmgr, identifier, amount);
            } catch (YStateException e) {
                throw new RuntimeException(e); // Should not happen: amount was just retrieved
            }
        }
    }

    public void removeAll(YPersistenceManager pmgr) throws YPersistenceException {
        _bag.removeAll(pmgr);
    }

    public String toString() {
        return getID() + "[" + _myTask.toString() + "]";
    }

    public String toXML() {
        return toXNode().toString();
    }
    
    public XNode toXNode() {
        XNode node = new XNode("internalCondition");
        node.addAttribute("id", toString());
        for (YIdentifier identifier : getIdentifiers()) {
            node.addChild("identifier", identifier.toString());
        }
        return node;
    }
}
