/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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

package org.yawlfoundation.yawl.nativebridge.erlang.examples;

import org.yawlfoundation.yawl.nativebridge.erlang.*;

import java.util.List;
import java.util.Map;

/**
 * Basic usage example demonstrating the EI bridge functionality.
 * Shows how to create Erlang terms, establish connections, and make RPC calls.
 */
public class BasicUsageExample {

    public static void main(String[] args) {
        System.out.println("YAWL Erlang Interface Bridge - Basic Usage Example");
        System.out.println("==================================================");

        try {
            // Example 1: Create Erlang terms
            System.out.println("\n1. Creating Erlang terms:");
            createErlangTerms();

            // Example 2: Connection management
            System.out.println("\n2. Connection management:");
            manageConnection();

            // Example 3: RPC operations
            System.out.println("\n3. RPC operations:");
            performRpcOperations();

            // Example 4: Process mining operations
            System.out.println("\n4. Process mining operations:");
            demonstrateProcessMining();

        } catch (ErlangException e) {
            System.err.println("Erlang error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createErlangTerms() throws ErlangException {
        // Create atoms
        ErlTerm atom1 = ErlAtom.atom("hello");
        ErlTerm atom2 = ErlAtom.atom("world");
        System.out.println("  Atom 1: " + atom1);
        System.out.println("  Atom 2: " + atom2);

        // Create numbers
        ErlTerm number1 = ErlLong.longValue(42);
        ErlTerm number2 = ErlLong.longValue(3.14);
        System.out.println("  Number 1: " + number1);
        System.out.println("  Number 2: " + number2);

        // Create binary
        ErlTerm binary = ErlBinary.fromString("Hello, World!");
        System.out.println("  Binary: " + binary);

        // Create list
        ErlTerm list = ErlList.of(
            ErlAtom.atom("item1"),
            ErlLong.longValue(1),
            ErlAtom.atom("item2"),
            ErlLong.longValue(2)
        );
        System.out.println("  List: " + list);

        // Create tuple
        ErlTerm tuple = ErlTuple.of(
            ErlAtom.atom("name"),
            ErlAtom.atom("John Doe"),
            ErlAtom.atom("age"),
            ErlLong.longValue(30)
        );
        System.out.println("  Tuple: " + tuple);
    }

    private static void manageConnection() throws ErlangException {
        // Create Erlang node
        ErlangNode node = new ErlangNode("yawl@localhost", "secret-cookie", (short) 1);

        System.out.println("  Created Erlang node: " + node.getNodeName());
        System.out.println("  Cookie: " + node.getCookie());

        // In a real scenario, you would connect to an actual Erlang node
        // node.connect();
        System.out.println("  Connection status: " + node.isConnected());

        // Close connection (would be called in a finally block in real code)
        // node.close();
        System.out.println("  Node created successfully");
    }

    private static void performRpcOperations() throws ErlangException {
        // Create node
        ErlangNode node = new ErlangNode("test@localhost", "test-cookie");

        // Create arguments for RPC call
        ErlTerm[] args = {
            ErlAtom.atom("module_name"),
            ErlAtom.atom("function_name"),
            ErlLong.longValue(42),
            ErlList.of(ErlAtom.atom("arg1"), ErlAtom.atom("arg2"))
        };

        ErlList arguments = ErlList.of(args);

        System.out.println("  RPC arguments: " + arguments);

        // In a real scenario, this would call an actual Erlang function
        // ErlTerm result = node.rpc("test_module", "test_function", arguments);
        // System.out.println("  RPC result: " + result);

        System.out.println("  RPC operation prepared");
    }

    private static void demonstrateProcessMining() throws ErlangException {
        // Create event log entries
        List<EventLogEntry> eventLog = List.of(
            new EventLogEntry(
                "case-001",
                "start",
                1234567890000L,
                Map.of("user", "john", "department", "sales")
            ),
            new EventLogEntry(
                "case-001",
                "approve_request",
                1234567950000L,
                Map.of("user", "jane", "amount", "1000")
            ),
            new EventLogEntry(
                "case-001",
                "end",
                1234568000000L,
                Map.of("user", "john")
            )
        );

        System.out.println("  Created event log with " + eventLog.size() + " entries");

        // Create process mining client
        ErlangNode node = new ErlangNode("pm@localhost", "yawl-pm");
        ProcessMiningClient client = new ProcessMiningClientImpl(node);

        // Demonstrate various operations
        System.out.println("  Operations available:");
        System.out.println("    - discoverProcessModel()");
        System.out.println("    - conformanceCheck()");
        System.out.println("    - analyzePerformance()");
        System.out.println("    - getProcessInstanceStats()");
        System.out.println("    - listProcessModels()");
        System.out.println("    - validateProcessModel()");
        System.out.println("    - executeQuery()");

        // Example of how to prepare for discovery
        // ErlTerm model = client.discoverProcessModel(eventLog);
        // System.out.println("  Discovered model: " + model);

        System.out.println("  Process mining client created");
    }
}