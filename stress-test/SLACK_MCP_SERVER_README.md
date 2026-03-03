# YAWL Slack MCP Server

The Slack MCP (Model Context Protocol) Server provides seamless integration between YAWL workflows and Slack. It enables real-time notifications, workflow control through Slack commands, and webhook support for automated event handling.

## Features

- **Real-time Notifications**: Send workflow events to Slack channels
- **Slack Commands**: Control workflows via slash commands
- **Webhook Support**: Receive events from Slack and process them
- **Workflow Subscriptions**: Subscribe to specific workflow events
- **MCP Protocol**: Full MCP protocol implementation for integration
- **Observability**: Built-in metrics and health checks
- **Configuration-Driven**: YAML-based configuration for easy setup

## Architecture

### Components

1. **SlackMcpServer**: Main server implementing MCP protocol
2. **SlackMcpConfig**: Configuration loader and access
3. **OpenTelemetryInitializer**: Observability setup
4. **SlackMcpDemo**: Demo application

### Protocol Endpoints

- `/` - Server info and health
- `/mcp` - MCP protocol requests
- `/slack/webhook` - Slack webhook events
- `/slack/command` - Slack slash commands
- `/slack/channels` - List configured channels
- `/slack/notifications` - Notification queue status
- `/health` - Health check endpoint

## Installation

### Prerequisites

- Java 25 (with preview features enabled)
- Maven 3.8+
- Slack workspace with bot token
- Valid Slack app with appropriate permissions

### Build

```bash
mvn clean compile
```

### Configuration

Create a `slack-mcp-server.yml` configuration file in `src/main/resources/`. See the [Configuration](#configuration) section for details.

## Usage

### Starting the Server

```java
public static void main(String[] args) {
    // Load configuration
    SlackMcpConfig config = SlackMcpConfig.getInstance();

    // Create server
    SlackMcpServer server = new SlackMcpServer(
        config.getServer().getPort(),
        config.getServer().getName(),
        config.getSlack().getBotToken(),
        config.getSlack().getSigningSecret()
    );

    // Start server
    server.start();
}
```

### Demo Application

Run the demo application:

```bash
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.integration.mcp.SlackMcpDemo"
```

### Slack Integration Setup

1. **Create Slack App**
   - Go to [api.slack.com/apps](https://api.slack.com/apps)
   - Create a new app "YAWL Integration"
   - Enable "Socket Mode" (optional)
   - Add bot token scope: `chat:write`, `incoming-webhook`

2. **Configure Webhooks**
   - Add event subscriptions: `message.channels`, `im_created`
   - Set webhook URL to `http://your-server:8085/slack/webhook`

3. **Create Slash Commands**
   - Create slash command `/yawls` with URL `http://your-server:8085/slack/command`

### MCP Tools

The server provides the following MCP tools:

#### `send_notification`
Send a notification to a Slack channel.

```json
{
  "name": "send_notification",
  "arguments": {
    "channel": "#workflows",
    "message": "Workflow completed successfully"
  }
}
```

#### `subscribe_workflow`
Subscribe to workflow events.

```json
{
  "name": "subscribe_workflow",
  "arguments": {
    "workflowId": "case-123",
    "channel": "#workflows"
  }
}
```

#### `unsubscribe_workflow`
Unsubscribe from workflow events.

```json
{
  "name": "unsubscribe_workflow",
  "arguments": {
    "workflowId": "case-123"
  }
}
```

#### `list_channels`
List configured Slack channels.

```json
{
  "name": "list_channels",
  "arguments": {}
}
```

#### `list_subscriptions`
List active workflow subscriptions.

```json
{
  "name": "list_subscriptions",
  "arguments": {}
}
```

### Slack Commands

The server supports the following slash commands:

#### `/yawls status`
Show current workflow status.

#### `/yawls subscribe <workflowId> <channel>`
Subscribe to workflow notifications.

#### `/yawls unsubscribe <workflowId>`
Unsubscribe from workflow notifications.

#### `/yawls help`
Show help message with all available commands.

## Configuration

The configuration file `src/main/resources/slack-mcp-server.yml` contains all settings:

### Server Configuration
```yaml
server:
  port: 8085
  name: "yawl-slack"
```

### Slack Configuration
```yaml
slack:
  bot_token: "${SLACK_BOT_TOKEN}"
  signing_secret: "${SLACK_SIGNING_SECRET}"
  default_channels:
    - name: "workflows"
      purpose: "Workflow notifications"
      id: "#workflows"
      notifications_enabled: true
```

### Notification Configuration
```yaml
notifications:
  enabled: true
  event_types:
    - "workitem_started"
    - "workitem_completed"
    - "workitem_failed"
    - "case_started"
    - "case_completed"
    - "case_cancelled"
    - "case_terminated"
  templates:
    workitem_started: |
      🚀 Work item started:
      • Case: {{caseId}}
      • Task: {{taskName}}
      • User: {{user}}
```

### Performance Configuration
```yaml
performance:
  virtual_threads: true
  queue_size: 1000
  batch_size: 10
  api_timeout: 30
  retry:
    max_attempts: 3
    delay_ms: 1000
    exponential_backoff: true
```

## Webhook Events

The server handles the following Slack events:

### URL Verification
Slack sends challenge requests for webhook verification.

### Event Callbacks
- `message`: Regular messages in channels
- `app_mention`: When the bot is mentioned
- `im_created`: When a direct message is created

## Event Templates

The server supports templated messages using variables:

### Available Variables
- `{{caseId}}`: Workflow case ID
- `{{taskId}}: Task ID
- `{{taskName}}`: Task name
- `{{user}}`: User who triggered the event
- `{{duration}}`: Duration in milliseconds
- `{{error}}`: Error message (for failures)
- `{{specName}}: Specification name
- `{{reason}}: Cancellation/termination reason

### Example Template
```yaml
workitem_completed: |
  ✅ Work item completed:
  • Case: {{caseId}}
  • Task: {{taskName}}
  • User: {{user}}
  • Duration: {{duration}}ms
```

## Metrics

The server collects the following metrics:

### MCP Server Metrics
- `slack.mcp.server.requests.total`: Total requests
- `slack.mcp.server.active.subscriptions`: Active subscriptions
- `slack.mcp.server.queue.size`: Notification queue size
- `slack.mcp.server.messages.processed`: Messages processed

### Slack API Metrics
- `slack.api.calls.total`: Total API calls
- `slack.api.errors.total`: API errors

### Health Metrics
- Memory usage
- Queue size
- Subscription count
- Uptime

## Security

### Authentication
- Bot token for API calls
- Signing secret for webhook verification
- Request validation

### Rate Limiting
- Configurable rate limits
- Per-channel subscription limits
- Request size limits

### CORS Protection
- Configured allowed origins
- Request size validation

## Testing

Run the test suite:

```bash
mvn test
```

The tests cover:
- MCP protocol handling
- Slack webhook processing
- Command handling
- Configuration loading
- Health checks

## Troubleshooting

### Common Issues

1. **Server Won't Start**
   - Check port availability
   - Verify configuration file syntax
   - Check log for errors

2. **Slack API Errors**
   - Verify bot token
   - Check permissions
   - Verify channel existence

3. **Webhook Verification Fails**
   - Check signing secret
   - Verify URL configuration in Slack
   - Check request signatures

### Logs

Logs are written to:
- Console (INFO level)
- File: `logs/slack-mcp-server.log`

### Debug Mode

Enable debug logging:

```yaml
logging:
  level: "DEBUG"
  loggers:
    org.yawlfoundation.yawl.integration.mcp.SlackMcpServer: "DEBUG"
```

## API Reference

### SlackMcpServer Class

#### Constructor
```java
SlackMcpServer(int port, String serverName, String slackBotToken, String slackSigningSecret)
```

#### Methods
- `start()`: Start the server
- `stop()`: Stop the server
- `handleMcpRequest(JsonNode request)`: Handle MCP requests

### SlackMcpConfig Class

#### Static Method
- `getInstance()`: Get configuration instance

#### Configuration Access
- `getServer()`: Server configuration
- `getSlack()`: Slack configuration
- `getNotifications()`: Notification configuration
- `getSubscriptions()`: Subscription configuration

## License

This project is part of YAWL and is licensed under the GNU Lesser General Public License. See the LICENSE file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## Support

For issues and questions:
- GitHub Issues
- YAWL Foundation mailing list
- Slack integration documentation