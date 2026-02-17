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

package org.yawlfoundation.yawl.procletService.interactionGraph;

import org.yawlfoundation.yawl.procletService.util.*;

//colset InteractionArc = record tail:InteractionNode * head:InteractionNode * entityID:EntityID * arcstate:ArcState;
public class InteractionArc {

	// colset ArcState = with UNPRODUCED | SENT | CONSUMED | EXECUTED_NONE | EXECUTED_SOURCE | EXECUTED_BOTH | FAILED;
	public enum ArcState {
		UNPRODUCED, SENT, CONSUMED, EXECUTED_NONE, EXECUTED_SOURCE, EXECUTED_BOTH, FAILED;
	}

	private InteractionNode tail = null;
	private InteractionNode head = null;
	private EntityID eid = null;
	private ArcState as = null;

	public InteractionArc (InteractionNode tail, InteractionNode head, EntityID eid,
			ArcState as) {
		this.tail = tail;
		this.head = head;
		this.eid = eid;
		this.as = as;
	}

	public InteractionNode getTail () {
		return this.tail;
	}

	public InteractionNode getHead() {
		return this.head;
	}

	public EntityID getEntityID() {
		return this.eid;
	}

	public ArcState getArcState() {
		return this.as;
	}

	public String getArcStateShort() {
		return switch (this.as) {
			case CONSUMED        -> "C";
			case SENT            -> "S";
			case UNPRODUCED      -> "U";
			case FAILED          -> "F";
			case EXECUTED_BOTH   -> "EB";
			case EXECUTED_SOURCE -> "ES";
			case EXECUTED_NONE   -> "EN";
		};
	}

	public void setTail (InteractionNode tail) {
		this.tail = tail;
	}

	public void setHead(InteractionNode head) {
		this.head = head;
	}

	public void setEntityID(EntityID eid) {
		this.eid = eid;
	}

	public void setArcState(ArcState as) {
		this.as = as;
	}

	public static ArcState getArcStateFromString (String as) {
		return switch (as) {
			case "UNPRODUCED"      -> ArcState.UNPRODUCED;
			case "SENT"            -> ArcState.SENT;
			case "CONSUMED"        -> ArcState.CONSUMED;
			case "EXECUTED_NONE"   -> ArcState.EXECUTED_NONE;
			case "EXECUTED_SOURCE" -> ArcState.EXECUTED_SOURCE;
			case "EXECUTED_BOTH"   -> ArcState.EXECUTED_BOTH;
			case "FAILED"          -> ArcState.FAILED;
			default                -> null;
		};
	}

	public String toString() {
		return "ARC:tail:" + this.tail + ",head:" + this.head + ",eid:" + this.eid +
		"as:" + this.as;
	}
}
