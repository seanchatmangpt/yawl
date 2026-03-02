# YAWL GitHub MCP Server

## Overview

The YAWL GitHub MCP (Model Context Protocol) Server provides GitHub integration capabilities for YAWL workflows. It enables:

- Pull request management and review hooks
- Issue creation from workflow events
- GitHub repository management via MCP protocol
- Webhook integration for real-time GitHub events
- Automatic issue creation from failed workflows

## Features

### MCP Protocol Support
- Standard MCP protocol implementation
- Tools for PR/Issue management
- Webhook registration and handling
- Health check endpoints

### GitHub Integration
- Create and manage pull requests
- Create and manage issues
- Add reviews and comments
- Track PR lifecycle
- Handle webhook events

### Workflow Integration
- Automatic issue creation from failed workflows
- PR review notifications
- Workflow event handling
- Status updates

## Setup

### 1. Prerequisites
- Java 25 (with virtual threads support)
- GitHub Personal Access Token with repo scope
- YAWL engine (for integration)

### 2. Configuration

Set environment variables:

```bash
# GitHub Access Token (required)
export GITHUB_ACCESS_TOKEN=ghp_your_token_here

# Default repository (format: owner/repo)
export GITHUB_DEFAULT_REPO=yawlfoundation/yawl

# Server port (default: 8083)
export GITHUB_MCP_PORT=8083

# Webhook URL (optional, for receiving GitHub events)
export GITHUB_WEBHOOK_URL=https://your-domain.com/github/webhook

# Webhook secret (optional, for signature verification)
export GITHUB_SECRET_TOKEN=your_webhook_secret
```

Or configure via `src/main/resources/application.yml`:

```yaml
yawl:
  github:
    server:
      port: 8083
      name: "yawl-github-integration"
      enabled: true
    github:
      access-token: "${GITHUB_ACCESS_TOKEN}"
      default-repo: "${GITHUB_DEFAULT_REPO}"
      timeout-seconds: 30
    webhook:
      enabled: true
      url: "${GITHUB_WEBHOOK_URL}"
      events:
        - "pull_request"
        - "issues"
        - "issue_comment"
        - "workflow_run"
```

### 3. Running the Server

#### Programmatic Start:
```java
GitHubMcpConfig config = GitHubMcpConfig.fromEnvironment();
GitHubMcpServer server = new GitHubMcpServer(
    config.getServer().getPort(),
    config.getServer().getName(),
    config.toGitHubConfig()
);
server.start();
```

#### Using the Demo:
```bash
java org.yawlfoundation.yawl.integration.mcp.github.GitHubMcpDemo
```

## MCP Tools

### Pull Request Management

#### Create PR
```json
{
  "method": "tools/call",
  "params": {
    "name": "create_pr",
    "arguments": {
      "repo": "owner/repo",
      "title": "Fix critical bug",
      "head": "feature-branch",
      "base": "main",
      "body": "Description of the changes"
    }
  }
}
```

#### Update PR
```json
{
  "method": "tools/call",
  "params": {
    "name": "update_pr",
    "arguments": {
      "pr_number": 123,
      "repo": "owner/repo",
      "title": "Updated title",
      "body": "Updated description"
    }
  }
}
```

#### Add Review
```json
{
  "method": "tools/call",
  "params": {
    "name": "add_pr_review",
    "arguments": {
      "pr_number": 123,
      "repo": "owner/repo",
      "event": "APPROVE",
      "body": "Looks good to me!"
    }
  }
}
```

### Issue Management

#### Create Issue
```json
{
  "method": "tools/call",
  "params": {
    "name": "create_issue",
    "arguments": {
      "repo": "owner/repo",
      "title": "New feature request",
      "body": "Detailed description",
      "labels": ["enhancement", "priority-high"]
    }
  }
}
```

#### Add Comment
```json
{
  "method": "tools/call",
  "params": {
    "name": "add_issue_comment",
    "arguments": {
      "issue_number": 456,
      "repo": "owner/repo",
      "body": "I have some feedback..."
    }
  }
}
```

### Webhook Management

#### Register Webhook
```json
{
  "method": "tools/call",
  "params": {
    "name": "register_webhook",
    "arguments": {
      "repo": "owner/repo",
      "url": "https://your-domain.com/webhook",
      "events": ["pull_request", "issues"]
    }
  }
}
```

#### List Webhooks
```json
{
  "method": "tools/call",
  "params": {
    "name": "list_webhooks",
    "arguments": {
      "repo": "owner/repo"
    }
  }
}
```

### Query Tools

#### Get PR Info
```json
{
  "method": "tools/call",
  "params": {
    "name": "get_pr_info",
    "arguments": {
      "pr_number": 123,
      "repo": "owner/repo"
    }
  }
}
```

#### Get Issue Info
```json
{
  "method": "tools/call",
  "params": {
    "name": "get_issue_info",
    "arguments": {
      "issue_number": 456,
      "repo": "owner/repo"
    }
  }
}
```

## Webhook Events

The server handles these GitHub webhook events:

- `pull_request`: PR opened, edited, closed
- `pull_request_review`: Review submitted
- `issues`: Issue opened, edited, closed
- `issue_comment`: Comment added to issue
- `workflow_run`: Workflow completed (auto-creates issues for failures)

### Example Webhook Payload

```json
{
  "action": "completed",
  "repository": {
    "full_name": "owner/repo"
  },
  "workflow_run": {
    "id": 12345,
    "name": "CI Pipeline",
    "head_sha": "abc123",
    "conclusion": "failure",
    "created_at": "2023-01-01T00:00:00Z"
  }
}
```

## Health Check

The server provides health check endpoints:

```
GET /health
GET /ping
```

Response:
```json
{
  "status": "healthy",
  "uptime_ms": 123456,
  "active_prs": 5,
  "active_issues": 3,
  "github_connected": true
}
```

## Integration with YAWL

### 1. Engine Integration
The GitHub MCP server can be integrated with YAWL engine to:

- Create issues when workflows fail
- Trigger workflows based on GitHub events
- Update workflow status based on PR reviews

### 2. Workflow Pattern Example
```yaml
# YAWL workflow that creates GitHub issues
- id: create-github-issue
  name: Create GitHub Issue
  input:
    title: string
    body: string
  output:
    issue_number: number
    issue_url: string
  steps:
    - call: mcp-tool
      tool: create_issue
      arguments:
        repo: yawlfoundation/yawl
        title: {{input.title}}
        body: {{input.body}}
```

### 3. Event Handling
```yaml
# Workflow triggered by GitHub webhook
- id: handle-pr-review
  name: Handle PR Review
  trigger: webhook
  event: pull_request_review
  steps:
    - if: event.review.state == "approved"
      then:
        - call: approve-workflow
          input:
            pr_id: {{event.pull_request.number}}
    - if: event.review.state == "changes_requested"
      then:
        - call: request-changes
          input:
            pr_id: {{event.pull_request.number}}
```

## Security

### Authentication
- GitHub Personal Access Token for API calls
- Optional webhook signature verification

### Best Practices
1. Use environment variables for sensitive tokens
2. Limit token scope to only necessary permissions
3. Enable webhook signature verification
4. Monitor server logs for suspicious activity

## Troubleshooting

### Common Issues

1. **GitHub API Errors**
   - Check token permissions
   - Verify repository access
   - Check API rate limits

2. **Webhook Failures**
   - Verify webhook URL is publicly accessible
   - Check firewall settings
   - Enable signature verification

3. **Server Startup Issues**
   - Check port availability
   - Verify Java version (25+)
   - Check configuration file syntax

### Logging
Server logs are written to:
- Console (INFO level)
- `logs/github-mcp-server.log` (if configured)

## Development

### Building
```bash
mvn clean compile
```

### Testing
Run the demo application:
```bash
java org.yawlfoundation.yawl.integration.mcp.github.GitHubMcpDemo
```

### Dependencies
- Jackson for JSON processing
- OpenTelemetry for observability
- SLF4J for logging
- Java 25+ with virtual threads

## License

This project is part of the YAWL Foundation and follows the same license terms.