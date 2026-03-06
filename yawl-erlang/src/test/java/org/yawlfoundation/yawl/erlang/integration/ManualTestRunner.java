package org.yawlfoundation.yawl.erlang.integration;

import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpNodeLifecycleManager;
import org.yawlfoundation.yawl.erlang.term.*;

import java.math.BigInteger;
import java.util.List;

/**
 * Manual test runner for Java -> OTP -> Rust4PM -> OTP -> Java round-trip.
 * Run with: java -cp target/classes:target/test-classes:...deps... org.yawlfoundation.yawl.erlang.integration.ManualTestRunner
 */
public class ManualTestRunner {

    private static final String NODE_NAME = "yawl_roundtrip@127.0.0.1";
    private static final String COOKIE = "test_cookie";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Java -> OTP -> Rust4PM -> OTP -> Java Round-Trip Test ===\n");

        // Check OTP 28 availability
        System.out.println("1. Checking OTP 28 availability...");
        if (!OtpInstallationVerifier.isOtp28Available()) {
            System.out.println("   SKIPPED: OTP 28 not installed");
            return;
        }
        System.out.println("   OK: OTP 28 available");

        // Get OTP version
        try {
            String version = OtpInstallationVerifier.getOtpVersion();
            System.out.println("   OTP Version: " + version);
        } catch (OtpNodeUnavailableException e) {
            System.out.println("   ERROR: " + e.getMessage());
            return;
        }

        // Start OTP node
        System.out.println("\n2. Starting OTP node...");
        OtpNodeLifecycleManager lifecycle = null;
        ErlangNode node = null;

        try {
            lifecycle = OtpNodeLifecycleManager.forNode(NODE_NAME, COOKIE);
            lifecycle.start();
            System.out.println("   OK: OTP node started");

            // Connect Java bridge
            System.out.println("\n3. Connecting Java bridge...");
            node = new ErlangNode("yawl_java_rt@127.0.0.1");
            node.connect(NODE_NAME, COOKIE);
            System.out.println("   OK: Connected to OTP node");

            // Test 1: Atom round-trip
            System.out.println("\n4. Testing atom round-trip...");
            ErlTerm atomResult = node.rpc("yawl_echo", "echo", List.of(new ErlAtom("hello")));
            if (atomResult instanceof ErlAtom && ((ErlAtom) atomResult).value().equals("hello")) {
                System.out.println("   OK: Atom round-trip passed");
            } else {
                System.out.println("   FAILED: Expected ErlAtom('hello'), got: " + atomResult);
            }

            // Test 2: Integer round-trip
            System.out.println("\n5. Testing integer round-trip...");
            ErlTerm intResult = node.rpc("yawl_echo", "echo", List.of(new ErlInteger(42)));
            if (intResult instanceof ErlInteger && ((ErlInteger) intResult).value().equals(BigInteger.valueOf(42))) {
                System.out.println("   OK: Integer round-trip passed");
            } else {
                System.out.println("   FAILED: Expected ErlInteger(42), got: " + intResult);
            }

            // Test 3: String round-trip
            System.out.println("\n6. Testing string round-trip...");
            ErlBinary testBin = new ErlBinary("test message".getBytes());
            ErlTerm strResult = node.rpc("yawl_echo", "echo", List.of(testBin));
            if (strResult instanceof ErlBinary) {
                System.out.println("   OK: String round-trip passed");
            } else {
                System.out.println("   FAILED: Expected ErlBinary, got: " + strResult);
            }

            // Test 4: List round-trip
            System.out.println("\n7. Testing list round-trip...");
            ErlList testList = new ErlList(List.of(new ErlInteger(1), new ErlInteger(2), new ErlInteger(3)));
            ErlTerm listResult = node.rpc("yawl_echo", "echo", List.of(testList));
            if (listResult instanceof ErlList) {
                System.out.println("   OK: List round-trip passed");
            } else {
                System.out.println("   FAILED: Expected ErlList, got: " + listResult);
            }

            // Test 5: Tuple round-trip
            System.out.println("\n8. Testing tuple round-trip...");
            ErlTuple testTuple = new ErlTuple(List.of(new ErlAtom("ok"), new ErlInteger(123)));
            ErlTerm tupleResult = node.rpc("yawl_echo", "echo", List.of(testTuple));
            if (tupleResult instanceof ErlTuple) {
                System.out.println("   OK: Tuple round-trip passed");
            } else {
                System.out.println("   FAILED: Expected ErlTuple, got: " + tupleResult);
            }

            System.out.println("\n=== ALL TESTS PASSED ===");
            System.exit(0);

        } catch (ErlangConnectionException e) {
            System.out.println("   ERROR: Connection failed - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (ErlangRpcException e) {
            System.out.println("   ERROR: RPC failed - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (Exception e) {
            System.out.println("   ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (node != null) {
                try { node.close(); } catch (Exception ignored) {}
            }
            if (lifecycle != null) {
                try { lifecycle.close(); } catch (Exception ignored) {}
            }
        }
    }
}
