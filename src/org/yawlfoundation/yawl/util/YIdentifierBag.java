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

package org.yawlfoundation.yawl.util;

import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 
 * @author Lachlan Aldred
 * @author Michael Adams (updated for 2.0)
 * 
 */
public class YIdentifierBag {
    private Map<YIdentifier, Integer> _idToQtyMap = new HashMap<>();
    public YNetElement _condition;


    public YIdentifierBag(YNetElement condition) {
        _condition = condition;
    }


    public void addIdentifier(YPersistenceManager pmgr, YIdentifier identifier)
            throws YPersistenceException {
        int amount = getAmount(identifier);
        _idToQtyMap.put(identifier, ++amount);
        identifier.addLocation(pmgr, _condition);
    }


    public int getAmount(YIdentifier identifier) {
        return _idToQtyMap.getOrDefault(identifier, 0);
    }


    public boolean contains(YIdentifier identifier) {
        return _idToQtyMap.containsKey(identifier);
    }


    public List<YIdentifier> getIdentifiers() {
        List<YIdentifier> idList = new ArrayList<>();
        for (Map.Entry<YIdentifier, Integer> entry : _idToQtyMap.entrySet()) {
            var amount = entry.getValue();
            for (int i = 0; i < amount; i++) {
                idList.add(entry.getKey());
            }
        }
        return idList;
    }


    public void remove(YPersistenceManager pmgr, YIdentifier identifier, int amountToRemove)
            throws YPersistenceException {
        if (_idToQtyMap.containsKey(identifier)) {
            var amountExisting = _idToQtyMap.get(identifier);
            if (amountToRemove <= 0) {
                throw new RuntimeException(
                        "Cannot remove %d from YIdentifierBag:%s %s"
                        .formatted(amountToRemove, _condition, identifier));
            }
            else if (amountToRemove > amountExisting) {
                throw new RuntimeException(
                        "Cannot remove %d tokens from YIdentifierBag:%s - this bag only contains %d identifiers of type %s"
                        .formatted(amountToRemove, _condition, amountExisting, identifier));
            }

            var amountLeft = amountExisting - amountToRemove;
            if (amountLeft > 0) {
                _idToQtyMap.put(identifier, amountLeft);
            }
            else {
                _idToQtyMap.remove(identifier);
            }
            identifier.removeLocation(pmgr, _condition);
        }
        else {
            throw new RuntimeException(
                    "Cannot remove %d tokens from YIdentifierBag:%s - this bag contains no identifiers of type %s. It does have %s (locations of %s:%s)"
                    .formatted(amountToRemove, _condition, identifier,
                               getIdentifiers(), identifier, identifier.getLocations()));
        }
    }


    public void removeAll(YPersistenceManager pmgr) throws YPersistenceException {
        Set<YIdentifier> identifiers = new HashSet<>(_idToQtyMap.keySet());
        for (YIdentifier identifier : identifiers) {
            while (identifier.getLocations().contains(_condition)) {
                identifier.clearLocation(pmgr, _condition);
            }
            _idToQtyMap.remove(identifier);
        }
    }
}
