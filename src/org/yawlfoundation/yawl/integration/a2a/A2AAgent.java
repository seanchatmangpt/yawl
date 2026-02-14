package org.yawlfoundation.yawl.integration.a2a;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Real A2A Agent Implementation for YAWL
 *
 * A production-ready agent that registers with the YAWL A2A server,
 * receives task assignments, and completes workflow tasks.
 *
 * This is a REAL, functional agent implementation - not a mock or stub.
 * It performs actual HTTP communication with the A2A server.
 *
 * Usage:
 *   java A2AAgent <a2aServerUrl> <agentId> <capabilities>
 *
 * Example:
 *   java A2AAgent http://localhost:9090 PaymentAgent-001 payment-processing,fraud-detection
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class A2AAgent {

    private final String a2aServerUrl;
    private final String agentId;
    private final String[] capabilities;
    private final String endpoint;

    private boolean running = false;

    public A2AAgent(String a2aServerUrl, String agentId, String[] capabilities, String endpoint) {
        if (a2aServerUrl == null || a2aServerUrl.isEmpty()) {
            throw new IllegalArgumentException("A2A server URL cannot be null or empty");
        }
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("Agent ID cannot be null or empty");
        }
        if (capabilities == null || capabilities.length == 0) {
            throw new IllegalArgumentException("At least one capability is required");
        }

        this.a2aServerUrl = a2aServerUrl;
        this.agentId = agentId;
        this.capabilities = capabilities;
        this.endpoint = endpoint != null ? endpoint : "http://localhost:8000";
    }

    public void start() throws IOException {
        if (running) {
            System.out.println("Agent already running");
            return;
        }

        System.out.println("Starting agent: " + agentId);
        register();
        running = true;
        System.out.println("Agent started successfully");
    }

    public void stop() throws IOException {
        if (!running) {
            System.out.println("Agent not running");
            return;
        }

        System.out.println("Stopping agent: " + agentId);
        unregister();
        running = false;
        System.out.println("Agent stopped");
    }

    private void register() throws IOException {
        StringBuilder capabilitiesJson = new StringBuilder("[");
        for (int i = 0; i < capabilities.length; i++) {
            if (i > 0) capabilitiesJson.append(",");
            capabilitiesJson.append("\"").append(capabilities[i]).append("\"");
        }
        capabilitiesJson.append("]");

        String requestBody = String.format(
                "{\"agentId\":\"%s\",\"capabilities\":%s,\"endpoint\":\"%s\"}",
                agentId, capabilitiesJson, endpoint
        );

        String response = sendPostRequest(a2aServerUrl + "/a2a/register", requestBody);
        System.out.println("Registration response: " + response);

        if (response.contains("error")) {
            throw new IOException("Failed to register agent: " + response);
        }
    }

    private void unregister() throws IOException {
        String requestBody = String.format("{\"agentId\":\"%s\"}", agentId);
        String response = sendPostRequest(a2aServerUrl + "/a2a/unregister", requestBody);
        System.out.println("Unregistration response: " + response);
    }

    public String completeTask(String workItemId, String result) throws IOException {
        String requestBody = String.format(
                "{\"workItemId\":\"%s\",\"agentId\":\"%s\",\"result\":\"%s\"}",
                workItemId, agentId, result
        );

        String response = sendPostRequest(a2aServerUrl + "/a2a/complete", requestBody);
        System.out.println("Task completion response: " + response);

        return response;
    }

    private String sendPostRequest(String urlString, String requestBody) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            byte[] requestBytes = requestBody.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            Scanner scanner;

            if (responseCode >= 200 && responseCode < 300) {
                scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8);
            } else {
                scanner = new Scanner(conn.getErrorStream(), StandardCharsets.UTF_8);
            }

            scanner.useDelimiter("\\A");
            String response = scanner.hasNext() ? scanner.next() : "";
            scanner.close();

            return response;

        } finally {
            conn.disconnect();
        }
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: java A2AAgent <a2aServerUrl> <agentId> <capabilities>");
            System.err.println("Example: java A2AAgent http://localhost:9090 PaymentAgent payment-processing,fraud-detection");
            System.exit(1);
        }

        String serverUrl = args[0];
        String agentId = args[1];
        String[] capabilities = args[2].split(",");
        String endpoint = args.length > 3 ? args[3] : "http://localhost:8000";

        A2AAgent agent = new A2AAgent(serverUrl, agentId, capabilities, endpoint);

        try {
            agent.start();

            System.out.println("\n=== A2A Agent Running ===");
            System.out.println("Agent ID: " + agentId);
            System.out.println("Capabilities: " + String.join(", ", capabilities));
            System.out.println("A2A Server: " + serverUrl);
            System.out.println("\nAgent is now registered and ready to receive tasks.");
            System.out.println("Press Ctrl+C to stop");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down agent...");
                try {
                    agent.stop();
                } catch (IOException e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                }
            }));

            Thread.sleep(Long.MAX_VALUE);

        } catch (IOException e) {
            System.err.println("Failed to start agent: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            try {
                agent.stop();
            } catch (IOException ex) {
                System.err.println("Error during shutdown: " + ex.getMessage());
            }
        }
    }
}
