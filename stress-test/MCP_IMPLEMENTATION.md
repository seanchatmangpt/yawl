# YAWL MCP Server Implementation

## Overview

This document describes the implementation of the YAWL MCP (Model Context Protocol) Server for GitHub integration.

## Architecture

### Core Components

1. **GitHubMcpServer** - Main MCP server implementation
   - Handles HTTP requests/responses
   - Implements MCP protocol
   - Manages GitHub API integration
   - Tracks PRs and issues

2. **GitHubMcpConfig** - Configuration management
   - Handles application.yml configuration
   - Environment variable support
   - Validation logic

3. **GitHubMcpDemo** - Demo application
   - Interactive testing interface
   - Demonstrates MCP protocol usage
   - Shows GitHub integration features

### Protocol Implementation

The server implements the MCP protocol with these core methods:

- `initialize` - Server initialization
- `tools/list` - List available tools
- `tools/call` - Execute tools
- `ping` - Health check

### HTTP Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Root server info |
| `/mcp` | POST | MCP protocol requests |
| `/github/pr` | POST | Pull request operations |
| `/github/issue` | POST | Issue operations |
| `/github/webhook` | POST | GitHub webhook handling |
| `/health` | GET | Health check |

## GitHub Integration Features

### Pull Request Management

- Create PRs with title, head, base, and body
- Update PR metadata
- Add reviews with events (APPROVE, REQUEST_CHANGES, COMMENT)
- Track PR lifecycle (open → review → merge/close)

### Issue Management

- Create issues with labels
- Add comments to issues
- Track issue states
- Auto-create issues from workflow failures

### Webhook Integration

- Handle GitHub webhook events
- Automatic issue creation from failed workflows
- PR review notifications
- Event filtering and routing

## Implementation Details

### Virtual Threads

The server uses Java 25 virtual threads for:
- Efficient concurrent request handling
- Non-blocking I/O operations
- High scalability for many connections

### OpenTelemetry Integration

Built-in observability with:
- Request metrics
- Error tracking
- Performance monitoring
- Health check endpoints

### Error Handling

- Proper HTTP status codes
- JSON-RPC error responses
- Graceful degradation
- Detailed error messages

## Configuration

### Environment Variables

```bash
GITHUB_ACCESS_TOKEN=ghp_your_token_here
GITHUB_DEFAULT_REPO=owner/repo
GITHUB_MCP_PORT=8083
GITHUB_WEBHOOK_URL=https://your-domain.com/webhook
GITHUB_SECRET_TOKEN=your_webhook_secret
```

### Application Configuration

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
      events:
        - "pull_request"
        - "issues"
        - "issue_comment"
        - "workflow_run"
```

## Testing

### Unit Tests

- Configuration validation
- Component instantiation
- Helper method testing

### Integration Tests

- HTTP endpoint testing
- MCP protocol compliance
- Error handling verification

### Demo Application

- Interactive testing interface
- Manual verification of features
- Live GitHub API testing

## Security Considerations

### Authentication

- GitHub Personal Access Token for API calls
- Optional webhook signature verification
- Environment variable protection

### Best Practices

- Token scope minimization
- HTTPS for webhook URLs
- Input validation
- Error message sanitization

## Deployment

### Docker Support

The server can be containerized:

```dockerfile
FROM eclipse-temurin:25-jre
COPY target/classes /app/classes
WORKDIR /app
CMD ["java", "-cp", "classes", "org.yawlfoundation.yawl.integration.mcp.github.GitHubMcpDemo"]
```

### Production Setup

1. Set up environment variables
2. Configure firewall rules
3. Set up SSL/TLS termination
4. Configure monitoring and logging
5. Set up health check endpoints

## Monitoring

### Metrics

- Request count and latency
- Active PR/issue tracking
- Error rates
- System health indicators

### Logs

- Structured JSON logging
- Request/response tracing
- Error context preservation
- Performance metrics

## Future Enhancements

### Planned Features

1. **Advanced GitHub Integration**
   - Branch protection rules
   - Collaborator management
   - Repository templates

2. **Enhanced MCP Protocol**
   - Resource management
   - Advanced tool schemas
   - Streaming responses

3. **YAWL Engine Integration**
   - Direct workflow triggers
   - State synchronization
   - Bidirectional communication

4. **Enterprise Features**
   - GitHub Enterprise support
   - Advanced authentication
   - Multi-repo management

### Performance Optimizations

- Connection pooling
- Response caching
- Batch operations
- Async processing

## Troubleshooting

### Common Issues

1. **Port conflicts**
   - Check port availability
   - Update configuration

2. **GitHub API rate limits**
   - Monitor usage
   - Implement caching

3. **Webhook delivery failures**
   - Verify URL accessibility
   - Check firewall settings

4. **Authentication errors**
   - Validate token permissions
   - Check token expiration

### Debug Mode

Enable debug logging:

```bash
export LOG_LEVEL=DEBUG
java -cp classes org.yawlfoundation.yawl.integration.mcp.github.GitHubMcpDemo
```

## Contributing

### Code Standards

- Follow Java 25 conventions
- Use virtual threads appropriately
- Implement proper error handling
- Write comprehensive tests

### Testing Requirements

- Unit tests for all public methods
- Integration tests for HTTP endpoints
- Mock external dependencies
- Test error scenarios

## License

This implementation is part of the YAWL Foundation project and follows the same license terms.