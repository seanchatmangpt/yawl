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

package org.yawlfoundation.yawl.procletService.connect;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Trigger {

	private static final Logger _log = LogManager.getLogger(Trigger.class);
	private JavaCPN conn = null;
	private int port = 9005;

	public Trigger () {
		conn = new JavaCPN();
	}

	public void initiate () {
		try {
			conn.accept(port);
			conn.send(EncodeDecode.encodeString("something"));
		}
		catch (Exception e) {
			_log.error("Failed to initiate Trigger on port {}", port, e);
		}
	}

	public String receive() {
		try {
			return EncodeDecode.decodeString(conn.receive());
		}
		catch (Exception e) {
			_log.error("Failed to receive message via Trigger", e);
		}
		return null;
	}

	public void send(String msg) {
		try {
			conn.send(EncodeDecode.encodeString(msg));
		}
		catch (Exception e) {
			_log.error("Failed to send message '{}' via Trigger", msg, e);
		}
	}

	public void close () {
		try {
			conn.disconnect();
		}
		catch (Exception e) {
			_log.error("Failed to close Trigger connection", e);
		}
	}
}
