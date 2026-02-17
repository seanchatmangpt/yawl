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

package org.yawlfoundation.yawl.elements;

import java.util.List;

import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.util.YIdentifierBag;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * 
 * An external condition is equivalent to a condition in the YAWL paper.
 * @author Lachlan Aldred
 * 
 */
public class YCondition extends YExternalNetElement implements YConditionInterface {


    protected YIdentifierBag _bag;
    private boolean _isImplicit;

    public YCondition(String id, String label, YNet container) {
        this(id, container);
        _name = label;
    }


    public YCondition(String id, YNet container) {
        super(id, container);
        _bag = new YIdentifierBag(this);
    }


    public void setImplicit(boolean isImplicit) {
        _isImplicit = isImplicit;
    }


    public boolean isImplicit() {
        return _isImplicit;
    }


    @Override
    public void verify(YVerificationHandler handler) {
        super.verify(handler);
    }


    public boolean isAnonymous() {
        return _name == null;
    }


    @Override
    public void add(YPersistenceManager pmgr, YIdentifier identifier) throws YPersistenceException {
        _bag.addIdentifier(pmgr, identifier);
    }

    @Override
    public boolean contains(YIdentifier identifier) {
        return _bag.contains(identifier);
    }

    @Override
    public boolean containsIdentifier() {
        return _bag.getIdentifiers().size() > 0;
    }

    @Override
    public int getAmount(YIdentifier identifier) {
        return _bag.getAmount(identifier);
    }

    @Override
    public List<YIdentifier> getIdentifiers() {
        return _bag.getIdentifiers();
    }

    @Override
    public YIdentifier removeOne(YPersistenceManager pmgr) throws YPersistenceException {
        YIdentifier identifier = getIdentifiers().get(0);
        _bag.remove(pmgr, identifier, 1);
        return identifier;
    }

    @Override
    public void removeOne(YPersistenceManager pmgr, YIdentifier identifier) throws YPersistenceException {
        _bag.remove(pmgr, identifier, 1);
    }

    @Override
    public void remove(YPersistenceManager pmgr, YIdentifier identifier, int amount) throws YPersistenceException {
        _bag.remove(pmgr, identifier, amount);
    }

    @Override
    public void removeAll(YPersistenceManager pmgr, YIdentifier identifier) throws YPersistenceException {
        _bag.remove(pmgr, identifier, _bag.getAmount(identifier));
    }

    @Override
    public synchronized void removeAll(YPersistenceManager pmgr) throws YPersistenceException {
        _bag.removeAll(pmgr);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        YNet copyContainer = _net.getCloneContainer();
        if (copyContainer.getNetElements().containsKey(this.getID())) {
            return copyContainer.getNetElement(this.getID());
        }
        YCondition copiedCondition = (YCondition) super.clone();
        copiedCondition._bag = new YIdentifierBag(copiedCondition);
        return copiedCondition;
    }


    @Override
    public String toXML() {
        String tag = "condition";
        if (this instanceof YInputCondition) {
            tag ="inputCondition";
        }
        else if (this instanceof YOutputCondition) {
            tag = "outputCondition";
        }
        return """
            <%s id="%s">%s</%s>
            """.formatted(tag, getID(), super.toXML(), tag);
    }
}
