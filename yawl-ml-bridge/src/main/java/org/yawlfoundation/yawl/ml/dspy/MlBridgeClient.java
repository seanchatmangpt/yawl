/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.ml.dspy;

import com.ericsson.otp.erlang.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with the Erlang ML Bridge.
 *
 * <p>Uses JInterface to communicate with the Erlang bridge, which then
 * calls the Rust NIF that embeds Python (DSPy/TPOT2).
 *
 * <p>Architecture: Java → JInterface → Erlang → Rust NIF → Python
 */
public final class MlBridgeClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MlBridgeClient.class);
    private static final String ERLANG_NODE = "yawl_ml@localhost";
    private static final String BRIDGE_MODULE = "dspy_bridge";

    private static MlBridgeClient defaultClient;

    private final OtpNode node;
    private final OtpMbox mbox;
    private final String targetNode;

    /**
     * Create a new client with custom configuration.
     *
     * @param cookie Erlang cookie
     * @param targetNode target Erlang node name
     * @throws IOException if node creation fails
     */
    public MlBridgeClient(String cookie, String targetNode) throws IOException {
        this.node = new OtpNode("java_ml_" + System.currentTimeMillis(), cookie);
        this.mbox = node.createMbox("ml_client");
        this.targetNode = targetNode;
        LOG.info("ML Bridge client connected to {}", targetNode);
    }

    /**
     * Create a client with default configuration.
     *
     * @throws IOException if node creation fails
     */
    public MlBridgeClient() throws IOException {
        this("yawl", ERLANG_NODE);
    }

    /**
     * Get the default client instance (lazy initialization).
     *
     * @return default client
     */
    public static synchronized MlBridgeClient getDefault() {
        if (defaultClient == null) {
            try {
                defaultClient = new MlBridgeClient();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create default ML Bridge client", e);
            }
        }
        return defaultClient;
    }

    /**
     * Run a DSPy prediction.
     *
     * @param signature the signature
     * @param inputs input values
     * @param examples few-shot examples (can be empty)
     * @return prediction results
     * @throws DspyException if prediction fails
     */
    public Map<String, Object> predict(Signature signature,
                                        Map<String, Object> inputs,
                                        List<Example> examples) throws DspyException {
        try {
            // Build Erlang terms
            OtpErlangObject[] args = new OtpErlangObject[3];
            args[0] = toErlang(signature.toJson());
            args[1] = toErlang(inputs);
            args[2] = examples.isEmpty() ? new OtpErlangAtom("none") : toErlang(examples);

            OtpErlangTuple call = new OtpErlangTuple(new OtpErlangObject[]{
                new OtpErlangAtom("call"),
                new OtpErlangAtom(BRIDGE_MODULE),
                new OtpErlangAtom("predict"),
                new OtpErlangList(args)
            });

            OtpErlangObject response = sendAndReceive(call);

            return parseResult(response);
        } catch (OtpErlangExit | OtpErlangDecodeException e) {
            throw new DspyException("COMM_ERROR", "Failed to communicate with Erlang bridge", e);
        }
    }

    /**
     * Check bridge health.
     *
     * @return true if bridge is healthy
     */
    public boolean isHealthy() {
        try {
            OtpErlangTuple call = new OtpErlangTuple(new OtpErlangObject[]{
                new OtpErlangAtom("call"),
                new OtpErlangAtom(BRIDGE_MODULE),
                new OtpErlangAtom("status"),
                new OtpErlangList()
            });

            OtpErlangObject response = sendAndReceive(call);
            return response instanceof OtpErlangTuple t &&
                   t.elementAt(0) instanceof OtpErlangAtom a &&
                   a.atomValue().equals("ok");
        } catch (Exception e) {
            LOG.warn("Health check failed", e);
            return false;
        }
    }

    private OtpErlangObject sendAndReceive(OtpErlangObject message)
            throws OtpErlangExit, OtpErlangDecodeException {
        OtpErlangPid pid = mbox.self();

        OtpErlangTuple envelope = new OtpErlangTuple(new OtpErlangObject[]{
            pid,
            message
        });

        mbox.send("ml_bridge_sup", targetNode, envelope);

        OtpErlangObject response = mbox.receive(30000); // 30s timeout
        if (response == null) {
            throw new OtpErlangDecodeException("Timeout waiting for response");
        }

        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(OtpErlangObject response) throws DspyException {
        if (response instanceof OtpErlangTuple tuple) {
            OtpErlangObject status = tuple.elementAt(0);

            if (status instanceof OtpErlangAtom atom && atom.atomValue().equals("ok")) {
                OtpErlangObject result = tuple.elementAt(1);
                return (Map<String, Object>) fromErlang(result);
            } else {
                String errorMsg = tuple.elementAt(1).toString();
                throw new DspyException("PREDICTION_FAILED", errorMsg);
            }
        }

        throw new DspyException("INVALID_RESPONSE", response.toString());
    }

    private OtpErlangObject toErlang(String json) {
        return new OtpErlangString(json);
    }

    private OtpErlangObject toErlang(Map<String, Object> map) {
        OtpErlangObject[] pairs = new OtpErlangObject[map.size()];
        int i = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            pairs[i++] = new OtpErlangTuple(new OtpErlangObject[]{
                new OtpErlangAtom(entry.getKey()),
                toErlangValue(entry.getValue())
            });
        }
        return new OtpErlangList(pairs);
    }

    private OtpErlangObject toErlang(List<Example> examples) {
        OtpErlangObject[] list = new OtpErlangObject[examples.size()];
        for (int i = 0; i < examples.size(); i++) {
            Example ex = examples.get(i);
            list[i] = new OtpErlangTuple(new OtpErlangObject[]{
                toErlang(ex.inputs()),
                toErlang(ex.outputs())
            });
        }
        return new OtpErlangList(list);
    }

    private OtpErlangObject toErlangValue(Object value) {
        if (value == null) {
            return new OtpErlangAtom("undefined");
        } else if (value instanceof String s) {
            return new OtpErlangString(s);
        } else if (value instanceof Integer i) {
            return new OtpErlangInt(i);
        } else if (value instanceof Long l) {
            return new OtpErlangLong(l);
        } else if (value instanceof Double d) {
            return new OtpErlangDouble(d);
        } else if (value instanceof Boolean b) {
            return new OtpErlangAtom(b ? "true" : "false");
        } else if (value instanceof Map) {
            return toErlang((Map<String, Object>) value);
        } else if (value instanceof List) {
            return toErlangList((List<?>) value);
        } else {
            return new OtpErlangString(value.toString());
        }
    }

    private OtpErlangObject toErlangList(List<?> list) {
        OtpErlangObject[] arr = new OtpErlangObject[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = toErlangValue(list.get(i));
        }
        return new OtpErlangList(arr);
    }

    private Object fromErlang(OtpErlangObject obj) {
        if (obj instanceof OtpErlangString s) {
            return s.stringValue();
        } else if (obj instanceof OtpErlangInt i) {
            return i.intValue();
        } else if (obj instanceof OtpErlangLong l) {
            return l.longValue();
        } else if (obj instanceof OtpErlangDouble d) {
            return d.doubleValue();
        } else if (obj instanceof OtpErlangAtom a) {
            return a.atomValue();
        } else if (obj instanceof OtpErlangList list) {
            List<Object> result = new ArrayList<>();
            for (OtpErlangObject elem : list.elements()) {
                result.add(fromErlang(elem));
            }
            return result;
        } else if (obj instanceof OtpErlangTuple tuple) {
            Map<String, Object> result = new HashMap<>();
            for (int i = 0; i < tuple.arity(); i++) {
                OtpErlangObject elem = tuple.elementAt(i);
                if (elem instanceof OtpErlangTuple kv && kv.arity() == 2) {
                    String key = kv.elementAt(0).toString();
                    Object value = fromErlang(kv.elementAt(1));
                    result.put(key, value);
                }
            }
            return result;
        }
        return obj.toString();
    }

    @Override
    public void close() {
        if (node != null) {
            node.close();
        }
    }
}
