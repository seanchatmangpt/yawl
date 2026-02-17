/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.tooling.lsp;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.schema.SchemaHandler;
import org.yawlfoundation.yawl.tooling.lsp.completion.YawlCompletionProvider;
import org.yawlfoundation.yawl.tooling.lsp.diagnostic.YawlDiagnosticProvider;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Language Server Protocol (LSP) server for YAWL specification files.
 *
 * Communicates over stdin/stdout using JSON-RPC 2.0 with the LSP message framing
 * protocol (Content-Length headers). Designed to be launched by VS Code via the
 * {@code yawl-language-server} executable wrapper and communicates over
 * the process standard streams.
 *
 * Supported LSP capabilities:
 * <ul>
 *   <li>textDocument/didOpen   - parse and validate on open</li>
 *   <li>textDocument/didChange - incremental re-validation with diagnostics</li>
 *   <li>textDocument/didClose  - evict document from in-memory cache</li>
 *   <li>textDocument/completion - XSD-aware element and attribute completion</li>
 *   <li>textDocument/hover     - documentation popup for tasks, conditions, flows</li>
 *   <li>textDocument/publishDiagnostics - XSD + semantic error reporting</li>
 * </ul>
 *
 * Entry point: {@link #main(String[])} or {@link #start(InputStream, OutputStream)}.
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class YawlLanguageServer {

    private static final Logger logger = Logger.getLogger(YawlLanguageServer.class.getName());
    private static final String YAWL_LANGUAGE_ID = "yawl";
    private static final String CONTENT_LENGTH_HEADER = "Content-Length: ";

    /** In-memory document store: URI -> full text */
    private final ConcurrentHashMap<String, String> documentStore = new ConcurrentHashMap<>();

    /** Provider for completion items */
    private final YawlCompletionProvider completionProvider;

    /** Provider for validation diagnostics */
    private final YawlDiagnosticProvider diagnosticProvider;

    /** Single-thread executor for LSP message handling (LSP is sequential per document) */
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yawl-lsp-handler");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean initialized = false;
    private volatile boolean shutdown    = false;
    private OutputStream outputStream;

    public YawlLanguageServer() {
        this.completionProvider = new YawlCompletionProvider();
        this.diagnosticProvider = new YawlDiagnosticProvider();
    }

    /**
     * Start the language server on the given streams. Blocks until the client
     * sends a {@code shutdown} request followed by {@code exit}.
     *
     * @param in  stream from which LSP messages are read
     * @param out stream to which LSP responses are written
     * @throws IOException if an I/O error occurs on the transport
     */
    public void start(InputStream in, OutputStream out) throws IOException {
        this.outputStream = out;
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        logger.info("YAWL Language Server started");

        while (!shutdown) {
            // Read Content-Length header
            String header = reader.readLine();
            if (header == null) {
                break; // EOF - client closed connection
            }
            if (!header.startsWith(CONTENT_LENGTH_HEADER)) {
                continue; // skip unexpected headers (e.g. Content-Type)
            }

            int contentLength;
            try {
                contentLength = Integer.parseInt(header.substring(CONTENT_LENGTH_HEADER.length()).trim());
            } catch (NumberFormatException e) {
                logger.warning("Invalid Content-Length header: " + header);
                continue;
            }

            // Read blank separator line
            reader.readLine();

            // Read JSON body
            char[] buf = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = reader.read(buf, totalRead, contentLength - totalRead);
                if (read == -1) {
                    throw new IOException("Unexpected EOF while reading LSP message body");
                }
                totalRead += read;
            }

            String message = new String(buf);
            handleMessage(message);
        }
    }

    /**
     * Route an incoming JSON-RPC message to the appropriate handler.
     */
    private void handleMessage(String json) {
        // Extract method name from JSON without a full JSON library dependency
        String method = extractStringField(json, "method");
        String id     = extractStringField(json, "id");

        if (method == null) {
            logger.fine("Received response or notification without method: ignoring");
            return;
        }

        switch (method) {
            case "initialize"                  -> handleInitialize(id, json);
            case "initialized"                 -> initialized = true;
            case "shutdown"                    -> handleShutdown(id);
            case "exit"                        -> shutdown = true;
            case "textDocument/didOpen"        -> handleDidOpen(json);
            case "textDocument/didChange"      -> handleDidChange(json);
            case "textDocument/didClose"       -> handleDidClose(json);
            case "textDocument/completion"     -> handleCompletion(id, json);
            case "textDocument/hover"          -> handleHover(id, json);
            default -> {
                if (id != null) {
                    // Send method not found error for requests (not notifications)
                    sendError(id, -32601, "Method not found: " + method);
                }
            }
        }
    }

    private void handleInitialize(String id, String json) {
        String capabilities = buildServerCapabilities();
        String result = """
                {
                  "capabilities": %s,
                  "serverInfo": {
                    "name": "YAWL Language Server",
                    "version": "6.0.0"
                  }
                }""".formatted(capabilities);
        sendResult(id, result);
        initialized = true;
        logger.info("LSP initialize completed");
    }

    private String buildServerCapabilities() {
        return """
                {
                  "textDocumentSync": {
                    "openClose": true,
                    "change": 1
                  },
                  "completionProvider": {
                    "triggerCharacters": ["<", " ", "\""],
                    "resolveProvider": false
                  },
                  "hoverProvider": true,
                  "diagnosticProvider": {
                    "identifier": "yawl",
                    "interFileDependencies": false,
                    "workspaceDiagnostics": false
                  }
                }""";
    }

    private void handleShutdown(String id) {
        sendResult(id, "null");
        shutdown = true;
        executor.shutdown();
        logger.info("LSP shutdown requested");
    }

    private void handleDidOpen(String json) {
        String uri  = extractNestedField(json, "textDocument", "uri");
        String text = extractNestedField(json, "textDocument", "text");
        if (uri == null || text == null) {
            logger.warning("didOpen: missing uri or text");
            return;
        }
        documentStore.put(uri, text);
        publishDiagnostics(uri, text);
    }

    private void handleDidChange(String json) {
        String uri = extractNestedField(json, "textDocument", "uri");
        // For full sync (change type 1) the first contentChanges entry contains the new text
        String text = extractArrayFirstText(json, "contentChanges");
        if (uri == null || text == null) {
            logger.warning("didChange: missing uri or text");
            return;
        }
        documentStore.put(uri, text);
        executor.submit(() -> publishDiagnostics(uri, text));
    }

    private void handleDidClose(String json) {
        String uri = extractNestedField(json, "textDocument", "uri");
        if (uri != null) {
            documentStore.remove(uri);
            // Clear diagnostics on close
            sendNotification("textDocument/publishDiagnostics",
                    "{\"uri\":\"" + jsonEscape(uri) + "\",\"diagnostics\":[]}");
        }
    }

    private void handleCompletion(String id, String json) {
        String uri  = extractNestedField(json, "textDocument", "uri");
        String text = (uri != null) ? documentStore.get(uri) : null;

        int line = extractIntField(json, "line", 0);
        int col  = extractIntField(json, "character", 0);

        List<YawlCompletionProvider.CompletionItem> items =
                completionProvider.getCompletions(text, line, col);

        StringBuilder itemsJson = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) itemsJson.append(",");
            itemsJson.append(items.get(i).toJson());
        }
        itemsJson.append("]");

        sendResult(id, "{\"isIncomplete\":false,\"items\":" + itemsJson + "}");
    }

    private void handleHover(String id, String json) {
        String uri  = extractNestedField(json, "textDocument", "uri");
        String text = (uri != null) ? documentStore.get(uri) : null;

        int line = extractIntField(json, "line", 0);
        int col  = extractIntField(json, "character", 0);

        String hoverContent = YawlHoverProvider.getHoverContent(text, line, col);

        if (hoverContent == null) {
            sendResult(id, "null");
        } else {
            sendResult(id, "{\"contents\":{\"kind\":\"markdown\",\"value\":\"" +
                    jsonEscape(hoverContent) + "\"}}");
        }
    }

    // ---- Diagnostics ----------------------------------------------------------

    private void publishDiagnostics(String uri, String text) {
        List<YawlDiagnosticProvider.Diagnostic> diagnostics =
                diagnosticProvider.validate(text);

        StringBuilder diagJson = new StringBuilder("[");
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) diagJson.append(",");
            diagJson.append(diagnostics.get(i).toJson());
        }
        diagJson.append("]");

        sendNotification("textDocument/publishDiagnostics",
                "{\"uri\":\"" + jsonEscape(uri) + "\",\"diagnostics\":" + diagJson + "}");
    }

    // ---- JSON-RPC message writing ---------------------------------------------

    private synchronized void sendResult(String id, String result) {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId(id) + ",\"result\":" + result + "}";
        writeMessage(json);
    }

    private synchronized void sendError(String id, int code, String message) {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + jsonId(id) +
                ",\"error\":{\"code\":" + code + ",\"message\":\"" + jsonEscape(message) + "\"}}";
        writeMessage(json);
    }

    private synchronized void sendNotification(String method, String params) {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + params + "}";
        writeMessage(json);
    }

    private void writeMessage(String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            String header = CONTENT_LENGTH_HEADER + bytes.length + "\r\n\r\n";
            outputStream.write(header.getBytes(StandardCharsets.UTF_8));
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write LSP message", e);
        }
    }

    // ---- Minimal JSON parsing helpers (no external JSON library needed) -------

    private static String extractStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon == -1) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            while (end > 0 && json.charAt(end - 1) == '\\') {
                end = json.indexOf('"', end + 1);
            }
            return end > start ? json.substring(start + 1, end) : null;
        }
        // Numeric id
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return end > start ? json.substring(start, end) : null;
    }

    private static String extractNestedField(String json, String outer, String inner) {
        String key = "\"" + outer + "\"";
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int braceStart = json.indexOf('{', idx);
        if (braceStart == -1) return null;
        // Find matching close brace
        int depth = 0, end = braceStart;
        for (; end < json.length(); end++) {
            if (json.charAt(end) == '{') depth++;
            else if (json.charAt(end) == '}') { depth--; if (depth == 0) break; }
        }
        String subJson = json.substring(braceStart, end + 1);
        return extractStringField(subJson, inner);
    }

    private static String extractArrayFirstText(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int arr = json.indexOf('[', idx);
        if (arr == -1) return null;
        return extractStringField(json.substring(arr), "text");
    }

    private static int extractIntField(String json, String field, int defaultValue) {
        String val = extractStringField(json, field);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * Render a JSON-RPC id value.
     * Per RFC 7159 and LSP spec, id is either an integer or a quoted string.
     * A string id that is all digits is emitted as a number literal; all others
     * are emitted as JSON strings. A null id (notifications) becomes JSON null.
     */
    private static String jsonId(String id) {
        if (id == null) {
            return "null";
        }
        boolean isInteger = !id.isEmpty() && id.chars().allMatch(Character::isDigit);
        return isInteger ? id : ("\"" + jsonEscape(id) + "\"");
    }

    // ---- Entry point ----------------------------------------------------------

    /**
     * Launch the language server on {@code System.in}/{@code System.out}.
     * stderr is used for logging.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        // Redirect System.out so that logger output does not corrupt the LSP stream
        PrintStream lspOut = System.out;
        System.setOut(System.err);

        YawlLanguageServer server = new YawlLanguageServer();
        try {
            server.start(System.in, lspOut);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Language server terminated with I/O error", e);
            System.exit(1);
        }
    }
}
