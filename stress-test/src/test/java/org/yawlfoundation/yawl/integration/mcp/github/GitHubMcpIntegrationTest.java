package org.yawlfoundation.yawl.integration.mcp.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for GitHubMcpServer HTTP endpoints.
 *
 * Tests the actual HTTP server responses and MCP protocol compliance.
 */
public class GitHubMcpIntegrationTest {

    private GitHubMcpServer server;
    private HttpClient httpClient;
    private final int testPort = 8084; // Use different port to avoid conflicts

    @BeforeEach
    void setUp() throws Exception {
        // Create test configuration
        GitHubMcpConfig config = new GitHubMcpConfig();
        config.getGithub().setAccessToken("test-token"); // For testing only
        config.getGithub().setDefaultRepo("test-org/test-repo");
        config.getServer().setPort(testPort);
        config.getWebhook().setEnabled(false); // Disable for testing

        // Start server
        server = new GitHubMcpServer(
            testPort,
            "yawl-github-integration-test",
            config.toGitHubConfig()
        );
        server.start();

        // Give server time to start
        Thread.sleep(1000);

        // Create HTTP client
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Root endpoint should return server info")
    void testRootEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + testPort + "/"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("server"));
        assertTrue(body.contains("yawl-github-integration-test"));
        assertTrue(body.contains("6.0.0"));
    }

    @Test
    @DisplayName("Health endpoint should return health status")
    void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + testPort + "/health"))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("status"));
        assertTrue(body.contains("uptime_ms"));
        assertTrue(body.contains("active_prs"));
        assertTrue(body.contains("github_connected"));
    }

    @Test
    @DisplayName("MCP initialize should work correctly")
    void testMcpInitialize() throws Exception {
        ObjectNode request = createMcpRequest("initialize", null);

        HttpResponse<String> response = sendMcpRequest(request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("protocolVersion"));
        assertTrue(body.contains("capabilities"));
        assertTrue(body.contains("serverInfo"));
    }

    @Test
    @DisplayName("MCP tools list should return available tools")
    void testMcpToolsList() throws Exception {
        ObjectNode request = createMcpRequest("tools/list", null);

        HttpResponse<String> response = sendMcpRequest(request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("tools"));
        assertTrue(body.contains("create_pr"));
        assertTrue(body.contains("create_issue"));
        assertTrue(body.contains("add_pr_review"));
    }

    @Test
    @DisplayName("MCP ping should work")
    void testMcpPing() throws Exception {
        ObjectNode request = createMcpRequest("ping", null);

        HttpResponse<String> response = sendMcpRequest(request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("status"));
        assertTrue(body.contains("pong"));
    }

    @Test
    @DisplayName("Unknown MCP method should return error")
    void testUnknownMcpMethod() throws Exception {
        ObjectNode request = createMcpRequest("unknown_method", null);

        HttpResponse<String> response = sendMcpRequest(request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("error"));
        assertTrue(body.contains("-32601")); // JSON-RPC error code
    }

    @Test
    @DisplayName("GitHub PR endpoint should handle create action")
    void testGitHubPRCreate() throws Exception {
        ObjectNode prData = createPRData();
        ObjectNode request = createJsonRequest();
        request.put("action", "create");
        request.set("pull_request", prData);

        HttpResponse<String> response = sendGitHubRequest("/pr", request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("action"));
        assertTrue(body.contains("created"));
        assertTrue(body.contains("tracked"));
    }

    @Test
    @DisplayName("GitHub issue endpoint should handle create action")
    void testGitHubIssueCreate() throws Exception {
        ObjectNode issueData = createIssueData();
        ObjectNode request = createJsonRequest();
        request.put("action", "create");
        request.set("issue", issueData);
        request.set("repository", createRepoData());

        HttpResponse<String> response = sendGitHubRequest("/issue", request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("action"));
        assertTrue(body.contains("created"));
    }

    @Test
    @DisplayName("GitHub webhook endpoint should accept valid events")
    void testGitHubWebhook() throws Exception {
        ObjectNode webhookData = createWebhookData("pull_request", "opened");
        ObjectNode request = createJsonRequest();
        request.set("pull_request", webhookData);
        request.set("repository", createRepoData());

        HttpResponse<String> response = sendGitHubRequest("/webhook", request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("status"));
        assertTrue(body.contains("success"));
        assertTrue(body.contains("event_type"));
    }

    @Test
    @DisplayName("Tool call with create_pr should return response")
    void testToolCallCreatePR() throws Exception {
        ObjectNode args = createJsonRequest();
        args.put("repo", "test-org/test-repo");
        args.put("title", "Test PR");
        args.put("head", "test-branch");
        args.put("base", "main");

        ObjectNode request = createMcpRequest("tools/call", args);
        request.withArray("arguments").add(args);

        HttpResponse<String> response = sendMcpRequest(request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        // Should contain error since we don't have real GitHub token
        assertTrue(body.contains("error") || body.contains("pr_number"));
    }

    @Test
    @DisplayName("Tool call with invalid arguments should return error")
    void testToolCallInvalidArgs() throws Exception {
        ObjectNode args = createJsonRequest();
        args.put("repo", ""); // Invalid empty repo

        ObjectNode request = createMcpRequest("tools/call", args);
        request.withArray("arguments").add(args);

        HttpResponse<String> response = sendMcpRequest(request);

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("error"));
    }

    // Helper methods

    private ObjectNode createMcpRequest(String method, ObjectNode params) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode();
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        } else {
            request.set("params", mapper.createObjectNode());
        }
        return request;
    }

    private ObjectNode createJsonRequest() {
        return new ObjectMapper().createObjectNode();
    }

    private ObjectNode createPRData() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode pr = mapper.createObjectNode();
        pr.put("number", 123);
        pr.put("title", "Test PR");
        pr.put("state", "open");
        pr.put("html_url", "https://github.com/test-org/test-repo/pull/123");

        ObjectNode head = mapper.createObjectNode();
        head.put("ref", "test-branch");
        pr.set("head", head);

        ObjectNode base = mapper.createObjectNode();
        base.put("ref", "main");
        pr.set("base", base);

        return pr;
    }

    private ObjectNode createIssueData() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode issue = mapper.createObjectNode();
        issue.put("number", 456);
        issue.put("title", "Test Issue");
        issue.put("state", "open");
        issue.put("html_url", "https://github.com/test-org/test-repo/issues/456");
        return issue;
    }

    private ObjectNode createRepoData() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode repo = mapper.createObjectNode();
        repo.put("full_name", "test-org/test-repo");
        return repo;
    }

    private ObjectNode createWebhookData(String event, String action) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode webhook = mapper.createObjectNode();
        webhook.put("action", action);

        ObjectNode pullRequest = createPRData();
        webhook.set("pull_request", pullRequest);

        ObjectNode repository = createRepoData();
        webhook.set("repository", repository);

        return webhook;
    }

    private HttpResponse<String> sendMcpRequest(ObjectNode request) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + testPort + "/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
            .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendGitHubRequest(String path, ObjectNode request) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + testPort + "/github" + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
            .build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }
}