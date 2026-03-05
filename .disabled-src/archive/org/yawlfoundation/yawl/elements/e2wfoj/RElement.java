/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.e2wfoj;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The base class for RTransition and RPlace.
 *
 * @author YAWL Foundation
 * @since 2.0
 */
public class RElement {
    private String name;
    private final Map<String, RFlow> presetFlows = new HashMap<>();
    private final Map<String, RFlow> postsetFlows = new HashMap<>();
    private final String id;

    public RElement(String id) {
        this.id = id;
    }

    public String getID() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, RFlow> getPresetFlows() {
        return presetFlows;
    }

    public Map<String, RFlow> getPostsetFlows() {
        return postsetFlows;
    }

    public void setPresetFlows(Map<String, RFlow> flows) {
        presetFlows.clear();
        presetFlows.putAll(flows);
    }

    public void setPostsetFlows(Map<String, RFlow> flows) {
        postsetFlows.clear();
        postsetFlows.putAll(flows);
    }

    public Set<RElement> getPostsetElements() {
        Set<RElement> postsetElements = new HashSet<>();
        Collection<RFlow> flowSet = postsetFlows.values();
        for (RFlow flow : flowSet) {
            postsetElements.add(flow.getNextElement());
        }
        return postsetElements;
    }

    public Set<RElement> getPresetElements() {
        Set<RElement> presetElements = new HashSet<>();
        Collection<RFlow> flowSet = presetFlows.values();
        for (RFlow flow : flowSet) {
            presetElements.add(flow.getPriorElement());
        }
        return presetElements;
    }

    public void setPreset(RFlow flowsInto) {
        if (flowsInto != null) {
            presetFlows.put(flowsInto.getPriorElement().getID(), flowsInto);
            flowsInto.getPriorElement().postsetFlows.put(flowsInto.getNextElement().getID(), flowsInto);
        }
    }

    public void setPostset(RFlow flowsInto) {
        if (flowsInto != null) {
            postsetFlows.put(flowsInto.getNextElement().getID(), flowsInto);
            flowsInto.getNextElement().presetFlows.put(flowsInto.getPriorElement().getID(), flowsInto);
        }
    }

    public RElement getPostsetElement(String elementId) {
        RFlow flow = postsetFlows.get(elementId);
        return flow != null ? flow.getNextElement() : null;
    }

    public RElement getPresetElement(String elementId) {
        RFlow flow = presetFlows.get(elementId);
        return flow != null ? flow.getPriorElement() : null;
    }
}
