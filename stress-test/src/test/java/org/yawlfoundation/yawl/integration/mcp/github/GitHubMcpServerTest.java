package org.yawlfoundation.yawl.integration.mcp.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GitHubMcpServer implementation.
 *
 * Tests MCP protocol compliance and GitHub integration features.
 */
public class GitHubMcpServerTest {

    private GitHubMcpConfig config;
    private GitHubMcpServer server;

    @BeforeEach
    void setUp() {
        // Create test configuration
        config = new GitHubMcpConfig();
        config.getGithub().setAccessToken("test-token");
        config.getGithub().setDefaultRepo("test-org/test-repo");
        config.getServer().setPort(8083);
        config.getWebhook().setEnabled(false); // Disable for testing

        // Create server instance
        server = new GitHubMcpServer(
            config.getServer().getPort(),
            config.getServer().getName(),
            config.toGitHubConfig()
        );
    }

    @Test
    @DisplayName("Configuration validation should work correctly")
    void testConfigurationValidation() {
        // Test valid configuration
        assertTrue(config.isValid());

        // Test invalid configuration (missing token)
        GitHubMcpConfig invalidConfig = new GitHubMcpConfig();
        invalidConfig.getGithub().setDefaultRepo("test-org/test-repo");
        assertFalse(invalidConfig.isValid());

        // Test invalid configuration (missing repo)
        GitHubMcpConfig invalidConfig2 = new GitHubMcpConfig();
        invalidConfig2.getGithub().setAccessToken("test-token");
        assertFalse(invalidConfig2.isValid());
    }

    @Test
    @DisplayName("GitHub config conversion should work correctly")
    void testGitHubConfigConversion() {
        GitHubMcpServer.GitHubConfig githubConfig = config.toGitHubConfig();

        assertNotNull(githubConfig);
        assertEquals("test-token", githubConfig.getAccessToken());
        assertEquals("test-org/test-repo", githubConfig.getDefaultRepo());
        assertNull(githubConfig.getWebhookUrl()); // Not set in config
    }

    @Test
    @DisplayName("Environment loading should work correctly")
    void testEnvironmentLoading() {
        // Set environment variables for testing
        try {
            System.setProperty("GITHUB_ACCESS_TOKEN", "env-token");
            System.setProperty("GITHUB_DEFAULT_REPO", "env-org/env-repo");

            GitHubMcpConfig envConfig = GitHubMcpConfig.fromEnvironment();

            assertEquals("env-token", envConfig.getGithub().getAccessToken());
            assertEquals("env-org/env-repo", envConfig.getGithub().getDefaultRepo());

        } finally {
            // Clean up
            System.clearProperty("GITHUB_ACCESS_TOKEN");
            System.clearProperty("GITHUB_DEFAULT_REPO");
        }
    }

    @Test
    @DisplayName("YAML loading should work correctly")
    void testYamlLoading() {
        String testYaml = """
            yawl:
              github:
                github:
                  access-token: "yaml-token"
                  default-repo: "yaml-org/yaml-repo"
                server:
                  port: 9999
            """;

        GitHubMcpConfig yamlConfig = GitHubMcpConfig.fromYaml(testYaml);

        assertEquals("yaml-token", yamlConfig.getGithub().getAccessToken());
        assertEquals("yaml-org/yaml-repo", yamlConfig.getGithub().getDefaultRepo());
        assertEquals(9999, yamlConfig.getServer().getPort());
    }

    @Test
    @DisplayName("YAML loading should throw exception for missing required fields")
    void testYamlLoadingWithMissingFields() {
        String incompleteYaml = """
            yawl:
              github:
                server:
                  port: 9999
            """;

        assertThrows(IllegalArgumentException.class, () -> {
            GitHubMcpConfig.fromYaml(incompleteYaml);
        });
    }

    @Test
    @DisplayName("Server classes should compile and instantiate correctly")
    void testServerInstantiation() {
        // Test that the server can be created without errors
        assertNotNull(server);

        // Test configuration
        assertEquals(8083, server.getPort());
        assertEquals("yawl-github-integration-demo", server.getServerName());
    }

    @Test
    @DisplayName("GitHub server config validation should work")
    void testGitHubServerConfigValidation() {
        // Test valid config
        GitHubMcpServer.GitHubConfig validConfig = new GitHubMcpServer.GitHubConfig(
            "token", "secret", "org/repo", "url"
        );
        assertTrue(validConfig.isValid());

        // Test invalid config
        GitHubMcpServer.GitHubConfig invalidConfig = new GitHubMcpServer.GitHubConfig(
            null, null, null, null
        );
        assertFalse(invalidConfig.isValid());
    }

    @Test
    @DisplayName("All required components should be present")
    void testComponentsPresent() {
        // Verify that all expected methods exist
        assertNotNull(config.getGithub());
        assertNotNull(config.getServer());
        assertNotNull(config.getWebhook());

        assertNotNull(config.getGithub().getAccessToken());
        assertNotNull(config.getServer().getPort());
        assertNotNull(config.getWebhook().getEvents());
    }

    @Test
    @DisplayName("Default values should be set correctly")
    void testDefaultValues() {
        // Test default values
        assertEquals(8083, config.getServer().getPort());
        assertEquals("yawl-github-integration", config.getServer().getName());
        assertTrue(config.getServer().isEnabled());

        assertEquals("https://api.github.com", config.getGithub().getApiBaseUrl());
        assertEquals(30, config.getGithub().getTimeoutSeconds());

        assertTrue(config.getWebhook().isEnabled());
        assertNotNull(config.getWebhook().getEvents());
        assertEquals(4, config.getWebhook().getEvents().length);
    }

    @Test
    @DisplayName("Setters should work correctly")
    void testSetters() {
        // Test setters
        config.getServer().setPort(9999);
        assertEquals(9999, config.getServer().getPort());

        config.getServer().setName("test-name");
        assertEquals("test-name", config.getServer().getName());

        config.getServer().setEnabled(false);
        assertFalse(config.getServer().isEnabled());

        config.getGithub().setTimeoutSeconds(60);
        assertEquals(60, config.getGithub().getTimeoutSeconds());

        config.getWebhook().setEnabled(false);
        assertFalse(config.getWebhook().isEnabled());
    }
}