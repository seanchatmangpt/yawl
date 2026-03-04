/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.tpot2;

import com.ericsson.otp.erlang.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for TPOT2 optimization via Erlang bridge.
 *
 * <p>Architecture: Java → JInterface → Erlang → Rust NIF → Python (tpot2)
 */
public final class Tpot2BridgeClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Tpot2BridgeClient.class);
    private static final String ERLANG_NODE = "yawl_ml@localhost";
    private static final String BRIDGE_MODULE = "tpot2_bridge";

    private static Tpot2BridgeClient defaultClient;

    private final OtpNode node;
    private final OtpMbox mbox;
    private final String targetNode;

    public Tpot2BridgeClient(String cookie, String targetNode) throws IOException {
        this.node = new OtpNode("java_tpot2_" + System.currentTimeMillis(), cookie);
        this.mbox = node.createMbox("tpot2_client");
        this.targetNode = targetNode;
        LOG.info("TPOT2 Bridge client connected to {}", targetNode);
    }

    public Tpot2BridgeClient() throws IOException {
        this("yawl", ERLANG_NODE);
    }

    public static synchronized Tpot2BridgeClient getDefault() {
        if (defaultClient == null) {
            try {
                defaultClient = new Tpot2BridgeClient();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create default TPOT2 client", e);
            }
        }
        return defaultClient;
    }

    /**
     * Run TPOT2 optimization.
     *
     * @param X feature matrix
     * @param y labels
     * @param config optimization config
     * @return optimization result
     * @throws Tpot2Exception if optimization fails
     */
    @SuppressWarnings("unchecked")
    public OptimizationResult optimize(List<List<Double>> X, List<Object> y,
                                        Map<String, Object> config) throws Tpot2Exception {
        try {
            String XJson = toJson(X);
            String yJson = toJson(y);
            String configJson = toJson(config);

            OtpErlangObject[] args = new OtpErlangObject[3];
            args[0] = new OtpErlangString(XJson);
            args[1] = new OtpErlangString(yJson);
            args[2] = new OtpErlangString(configJson);

            OtpErlangTuple call = new OtpErlangTuple(new OtpErlangObject[]{
                new OtpErlangAtom("call"),
                new OtpErlangAtom(BRIDGE_MODULE),
                new OtpErlangAtom("optimize"),
                new OtpErlangList(args)
            });

            OtpErlangObject response = sendAndReceive(call);

            if (response instanceof OtpErlangTuple tuple) {
                OtpErlangObject status = tuple.elementAt(0);
                if (status instanceof OtpErlangAtom atom && atom.atomValue().equals("ok")) {
                    String resultJson = tuple.elementAt(1).toString();
                    Map<String, Object> result = parseJson(resultJson);

                    return new OptimizationResult(
                        (String) result.getOrDefault("best_pipeline", "unknown"),
                        ((Number) result.getOrDefault("fitness_score", 0.0)).doubleValue(),
                        ((Number) result.getOrDefault("generations", 0)).intValue(),
                        result
                    );
                } else {
                    throw new Tpot2Exception("OPT_FAILED", tuple.elementAt(1).toString());
                }
            }

            throw new Tpot2Exception("INVALID_RESPONSE", response.toString());
        } catch (OtpErlangExit | OtpErlangDecodeException e) {
            throw new Tpot2Exception("COMM_ERROR", "Failed to communicate with bridge", e);
        }
    }

    private OtpErlangObject sendAndReceive(OtpErlangObject message)
            throws OtpErlangExit, OtpErlangDecodeException {
        OtpErlangPid pid = mbox.self();
        OtpErlangTuple envelope = new OtpErlangTuple(new OtpErlangObject[]{ pid, message });
        mbox.send("ml_bridge_sup", targetNode, envelope);
        OtpErlangObject response = mbox.receive(600000); // 10 min timeout for optimization
        if (response == null) {
            throw new OtpErlangDecodeException("Timeout waiting for optimization");
        }
        return response;
    }

    private String toJson(Object obj) {
        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        } else if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } else if (obj instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        } else if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        } else {
            return "\"" + obj.toString() + "\"";
        }
    }

    private Map<String, Object> parseJson(String json) {
        // Simple JSON parser for our response format
        Map<String, Object> result = new HashMap<>();
        // Remove braces
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        // Parse key-value pairs
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    result.put(key, value.substring(1, value.length() - 1));
                } else if (value.contains(".")) {
                    result.put(key, Double.parseDouble(value));
                } else {
                    try {
                        result.put(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void close() {
        if (node != null) {
            node.close();
        }
    }
}
