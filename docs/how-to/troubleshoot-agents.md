# Troubleshooting Guide for ADR-025 Agent Coordination

**Common issues and solutions for agent coordination problems**

## Quick Start - Basic Troubleshooting

```bash
# Check agent status
curl http://localhost:8081/health

# Verify partition configuration
grep partition application.yaml

# Check handoff metrics
curl http://localhost:8081/metrics | grep handoff

# Review logs
tail -f logs/agent.log | grep ERROR

# Run diagnostic tests
java -jar yawl-agent.jar --diagnose
```

## 1. Agent Registration and Discovery Issues

### Problem: Agents not registering

**Symptoms:**
- No agents visible in the agent registry
- Health check failures
- Work items not being assigned

**Solutions:**

1. **Check agent configuration**
```bash
# Verify agent configuration
grep agent application.yaml
```

```yaml
# Correct configuration
agent:
  id: "reviewer-001"
  port: 8081
  healthEndpoint: "/health"
```

2. **Verify health endpoint**
```bash
curl http://localhost:8081/health
```

Expected response:
```json
{
  "status": "UP",
  "timestamp": "2024-01-01T12:00:00Z",
  "version": "2.0.0",
  "details": {
    "diskSpace": "85%",
    "memory": "60%",
    "cpu": "30%"
  }
}
```

3. **Check A2A connection**
```bash
# Test A2A server connection
curl http://a2a-server:8080/health

# Verify agent registration
curl http://a2a-server:8080/api/agents
```

4. **Debug registration process**
```java
// Enable debug logging
logging.level.org.yawlfoundation.yawl.integration.agent=DEBUG

// Check registration logs
grep "registering" logs/agent.log
grep "registered successfully" logs/agent.log
```

### Problem: Duplicate agent registration

**Symptoms:**
- Multiple entries for same agent ID
- Load balancing issues
- Work item processing conflicts

**Solutions:**

1. **Check agent IDs**
```bash
# List registered agents
curl http://a2a-server:8080/api/agents | jq '.[].id'
```

2. **Fix configuration**
```yaml
# Ensure unique agent IDs
agent:
  id: "reviewer-001"  # Must be unique
  port: 8081
```

3. **Cleanup duplicates**
```bash
# Force re-registration
curl -X POST http://a2a-server:8080/api/agents/cleanup
```

### Problem: Agent health issues

**Symptoms:**
- Health check failures
- Agents being marked as down
- Work items not being assigned

**Solutions:**

1. **Check resource usage**
```bash
# Monitor system resources
top -p $(pgrep java)
free -h
df -h
```

2. **Adjust health check configuration**
```yaml
health:
  endpoint: "/health"
  interval: 30000
  timeout: 5000
  checks:
    - "cpu-usage"
    - "memory-usage"
    - "disk-space"
  thresholds:
    cpuUsage: 80
    memoryUsage: 80
    diskSpace: 10
```

3. **Monitor health events**
```bash
grep "health" logs/agent.log
grep "unhealthy" logs/agent.log
```

## 2. Partitioning Issues

### Problem: Work items not being assigned

**Symptoms:**
- Enabled work items but no assignments
- Uneven distribution across agents
- Timeouts on work item processing

**Solutions:**

1. **Check partition configuration**
```bash
# Verify partition settings
grep -A 10 partition application.yaml
```

```yaml
partition:
  enabled: true
  agentIndex: 0
  totalAgents: 3
  strategy: "hash"
```

2. **Test partition logic**
```java
// Manual partition test
java -cp yawl-agent.jar PartitionTest
```

3. **Check hash consistency**
```bash
# Verify consistent hashing
echo "WI-123|CASE-1|ReviewTask" | md5sum
echo "WI-124|CASE-1|ReviewTask" | md5sum
```

4. **Adjust partition strategy**
```yaml
# Try different strategy
partition:
  strategy: "consistent_hash"
  hash:
    function: "caseId"
    salt: "salt-value"
```

### Problem: Unbalanced partitioning

**Symptoms:**
- Some agents overloaded
- Others idle
- Performance degradation

**Solutions:**

1. **Monitor partition balance**
```bash
# Check work item distribution
curl http://localhost:8081/metrics/partition

# Visualize distribution
curl http://localhost:8081/dashboards/partition
```

2. **Enable automatic rebalancing**
```yaml
partition:
  balancing:
    enabled: true
    strategy: "redistribute"
    threshold: 0.2  # 20% imbalance
    redistribute:
      batchSize: 100
```

3. **Adjust agent weights**
```yaml
partition:
  weights:
    agent-001: 1.0  # Standard capacity
    agent-002: 1.5  # 50% more capacity
    agent-003: 0.8  # 20% less capacity
```

### Problem: Partition cache issues

**Symptoms:**
- Stale partition results
- Inconsistent work assignments
- Performance issues

**Solutions:**

1. **Clear cache**
```bash
# Clear partition cache
curl -X POST http://localhost:8081/cache/clear

# Check cache stats
curl http://localhost:8081/cache/stats
```

2. **Adjust cache settings**
```yaml
cache:
  enabled: true
  size: 10000
  ttl: 300000  # 5 minutes
  evictionPolicy: "LRU"
```

3. **Enable cache invalidation**
```yaml
cache:
  invalidation:
    enabled: true
    onWorkItemChange: true
    onAgentChange: true
    refreshInterval: 60000
```

## 3. Handoff Issues

### Problem: Handoff failing

**Symptoms:**
- Work items not transferring
- Handoff timeouts
- Retry failures

**Solutions:**

1. **Check handoff configuration**
```bash
# Verify handoff settings
grep handoff application.yaml
```

```yaml
handoff:
  enabled: true
  timeout: 30000
  ttl: 60000
  maxRetries: 3
```

2. **Test handoff manually**
```java
// Manual handoff test
java -cp yawl-agent.jar HandoffTest --source agent-001 --target agent-002
```

3. **Check agent connectivity**
```bash
# Test connectivity between agents
curl http://agent-002:8081/health

# Test A2A messaging
curl -X POST http://a2a-server:8080/api/test/handoff \
  -H "Content-Type: application/json" \
  -d '{"from": "agent-001", "to": "agent-002"}'
```

4. **Adjust retry configuration**
```yaml
handoff:
  retry:
    maxAttempts: 5
    baseDelay: 1000
    maxDelay: 30000
    exponentialBackoff: true
    jitter: true
```

### Problem: Handoff token issues

**Symptoms:**
- Invalid token errors
- Token expiration
- Security failures

**Solutions:**

1. **Check token configuration**
```bash
# Verify JWT settings
grep JWT application.yaml

# Check token expiry
handoff:
  ttl: 60000  # 60 seconds
```

2. **Regenerate tokens**
```bash
# Force token regeneration
curl -X POST http://localhost:8081/handoff/refresh-tokens
```

3. **Check token validation**
```java
// Test token validation
curl -X POST http://localhost:8081/handoff/validate \
  -H "Authorization: Bearer <token>"
```

### Problem: Handoff queue overflow

**Symptoms:**
- Handoff queue full
- Performance degradation
- Work item processing delays

**Solutions:**

1. **Check queue metrics**
```bash
# Monitor queue status
curl http://localhost:8081/metrics/handoff/queue

# Check queue size
curl http://localhost:8081/handoff/queue/stats
```

2. **Adjust queue configuration**
```yaml
handoff:
  queue:
    enabled: true
    size: 1000
    priority: "HIGH"
    batchProcessing: true
    batchSize: 10
    consumerThreads: 4
```

3. **Implement flow control**
```yaml
handoff:
  flowControl:
    enabled: true
    rateLimit: 100  # handoffs per second
    burstSize: 500
    backpressure: true
```

## 4. Conflict Resolution Issues

### Problem: Conflicts not being detected

**Symptoms:**
- Multiple agents working on same item
- Disagreements not resolved
- Inconsistent decisions

**Solutions:**

1. **Check conflict detection configuration**
```bash
# Verify conflict settings
grep conflict application.yaml
```

```yaml
conflict:
  enabled: true
  detection:
    enabled: true
    interval: 10000
    checkOnComplete: true
```

2. **Test conflict detection**
```java
// Manual conflict test
java -cp yawl-agent.jar ConflictTest --itemId WI-123 --agents 3
```

3. **Enable debug logging**
```yaml
logging:
  level:
    org.yawlfoundation.yawl.integration.conflict=DEBUG
```

### Problem: Conflict resolution failing

**Symptoms:**
- Conflicts not being resolved
- Escalation failures
- Arbiter not responding

**Solutions:**

1. **Check arbiter configuration**
```bash
# Verify arbiter settings
conflict:
  arbiterEndpoint: "http://arbiter-agent:8081"
  timeout: 30000
```

2. **Test arbiter connection**
```bash
# Test arbiter health
curl http://arbiter-agent:8081/health

# Test arbiter functionality
curl -X POST http://arbiter-agent:8081/conflict/resolve \
  -H "Content-Type: application/json" \
  -d '{"itemId": "WI-123", "decisions": [...]}}'
```

3. **Check escalation thresholds**
```yaml
conflict:
  escalation:
    threshold: 0.8  # 80% agreement required
    arbiterEndpoint: "http://arbiter:8081"
    timeout: 30000
```

### Problem: Human fallback issues

**Symptoms:**
- Human fallback not working
- Escalation failures
- Resource service problems

**Solutions:**

1. **Check resource service configuration**
```bash
# Verify resource service settings
conflict:
  humanFallback:
    enabled: true
    resourceService: "human-resources"
    escalationMessage: "Human review required"
```

2. **Test resource service**
```bash
# Test human resource availability
curl http://human-resources:8081/available

# Check service health
curl http://human-resources:8081/health
```

3. **Adjust fallback settings**
```yaml
conflict:
  humanFallback:
    escalationTimeout: 60000
    retryOnFailure: true
    maxRetries: 3
    notifyChannels: ["email", "slack"]
```

## 5. Performance Issues

### Problem: Slow work item processing

**Symptoms:**
- High processing times
- Backlog accumulation
- Timeout errors

**Solutions:**

1. **Monitor performance metrics**
```bash
# Check processing times
curl http://localhost:8081/metrics/workitem/processing-time

# Check backlog
curl http://localhost:8081/metrics/workitem/backlog
```

2. **Adjust timeouts**
```yaml
engine:
  connection:
    timeout: 30000
    readTimeout: 60000
  polling:
    interval: 5000
    timeout: 30000
```

3. **Optimize configuration**
```yaml
performance:
  partitioning:
    batchSize: 1000
    timeout: 1000
  handoff:
    async: true
    queueSize: 1000
    consumerThreads: 8
```

### Problem: High memory usage

**Symptoms:**
- Memory leaks
- Out of memory errors
- Garbage collection overhead

**Solutions:**

1. **Monitor memory usage**
```bash
# Check JVM memory
jstat -gc $(pgrep java) 1s

# Check heap usage
curl http://localhost:8081/metrics/jvm/memory/heap
```

2. **Adjust JVM settings**
```bash
# Add to JAVA_OPTS
export JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

3. **Optimize memory usage**
```yaml
cache:
  size: 5000  # Reduce cache size
  evictionPolicy: "LRU"
  ttl: 180000  # 3 minutes

partition:
  cache:
    size: 10000
    ttl: 300000
```

### Problem: High CPU usage

**Symptoms:**
- CPU spikes
- Slow response times
- System overload

**Solutions:**

1. **Monitor CPU usage**
```bash
# Check CPU usage
top -p $(pgrep java)

# Check thread dumps
jstack $(pgrep java) > thread-dump.txt
```

2. **Optimize processing**
```yaml
# Reduce polling frequency
engine:
  polling:
    interval: 10000  # 10 seconds

# Limit concurrent processing
agent:
  maxConcurrentWorkItems: 20
```

3. **Enable thread pool configuration**
```yaml
resilience:
  threadPool:
    corePoolSize: 10
    maxPoolSize: 50
    keepAliveTime: 60
    queueCapacity: 100
```

## 6. Network and Connectivity Issues

### Problem: Engine connection issues

**Symptoms:**
- Connection timeouts
- Engine unreachable
- API failures

**Solutions:**

1. **Check network connectivity**
```bash
# Test network connectivity
ping yawl-engine
telnet yawl-engine 8080

# Test engine API
curl http://yawl-engine:8080/yawl/health
```

2. **Adjust connection settings**
```yaml
engine:
  connection:
    timeout: 30000
    readTimeout: 60000
    connectTimeout: 10000
    maxRetries: 3
    retryDelay: 1000
```

3. **Enable circuit breaker**
```yaml
engine:
  circuitBreaker:
    enabled: true
    failureThreshold: 5
    recoveryTimeout: 30000
    halfOpenAttempts: 1
```

### Problem: A2A communication issues

**Symptoms:**
- Agent discovery failures
- Message delivery problems
- Handoff failures

**Solutions:**

1. **Check A2A server**
```bash
# Test A2A server
curl http://a2a-server:8080/health

# Check server logs
tail -f /var/log/a2a/server.log
```

2. **Verify configuration**
```yaml
a2a:
  serverUrl: "http://a2a-server:8080"
  apiKey: "${A2A_API_KEY}"
  connection:
    timeout: 30000
    maxRetries: 3
```

3. **Test messaging**
```bash
# Test A2A messaging
curl -X POST http://a2a-server:8080/api/test/message \
  -H "Content-Type: application/json" \
  -d '{"from": "agent-001", "to": "agent-002", "message": "test"}'
```

## 7. Configuration and Deployment Issues

### Problem: Configuration errors

**Symptoms:**
- Startup failures
- Invalid configuration errors
- Runtime exceptions

**Solutions:**

1. **Validate configuration**
```bash
# Validate configuration file
java -cp yawl-agent.jar ConfigValidator --config application.yaml

# Check syntax
yamllint application.yaml
```

2. **Use configuration profiles**
```bash
# Validate with profile
java -jar yawl-agent.jar --config application.yaml --profile dev

# List available profiles
java -jar yawl-agent.jar --list-profiles
```

3. **Check configuration templates**
```bash
# Compare with template
diff application.yaml templates/application.yaml
```

### Problem: Version compatibility issues

**Symptoms:**
- API mismatches
- Protocol version errors
- Feature not supported

**Solutions:**

1. **Check version compatibility**
```bash
# Check server version
curl http://yawl-engine:8080/yawl/version

# Check agent version
java -jar yawl-agent.jar --version
```

2. **Update versions**
```bash
# Update engine
mvn versions:use-latest-releases -DgroupId=org.yawlfoundation.yawl -DartifactId=yawl-engine

# Update agent
mvn versions:use-latest-releases -DgroupId=org.yawlfoundation.yawl -DartifactId=yawl-agent
```

3. **Use compatible versions**
```yaml
# Specify versions
dependencies:
  yawl-engine: "6.0.0"
  yawl-integration: "6.0.0"
```

## 8. Advanced Troubleshooting Tools

### Diagnostic Commands

```bash
# Run full diagnostics
java -jar yawl-agent.jar --diagnose

# Check system health
java -jar yawl-agent.jar --health-check

# Validate configuration
java -jar yawl-agent.jar --validate-config

# Generate report
java -jar yawl-agent.jar --generate-report --output /tmp/diagnostic-report.json
```

### Debug Mode

```bash
# Start in debug mode
java -jar yawl-agent.jar --debug

# Enable debug logging
export LOG_LEVEL=DEBUG
java -jar yawl-agent.jar

# Debug specific components
java -jar yawl-agent.jar --debug-partitioning
java -jar yawl-agent.jar --debug-handoff
java -jar yawl-agent.jar --debug-conflict
```

### Performance Analysis

```bash
# Profile performance
java -jar yawl-agent.jar --profile --duration 300 --output /tmp/profile.html

# Generate flame graph
java -jar yawl-agent.jar --flame-graph --duration 60

# Memory analysis
java -jar yawl-agent.jar --memory-analysis --heap-dump /tmp/heapdump.hprof
```

## 9. Common Error Codes and Solutions

| Error Code | Description | Solution |
|------------|-------------|----------|
| AGENT-001 | Agent registration failed | Check agent ID and network connectivity |
| PART-002 | Partition configuration error | Verify partition settings and indices |
| HAND-003 | Handoff operation failed | Check target agent availability and connectivity |
| CONFLICT-004 | Conflict resolution failed | Verify arbiter configuration and agent decisions |
| ENGINE-005 | Engine connection failed | Check network and credentials |
| A2A-006 | A2A communication failed | Verify A2A server configuration |
| MEMORY-007 | Memory allocation failed | Increase JVM heap size or optimize memory usage |
| TIMEOUT-008 | Operation timeout | Increase timeouts or optimize performance |
| CONFIG-009 | Configuration error | Validate configuration file format |
| AUTH-010 | Authentication failed | Check API keys and permissions |

## 10. Preventive Measures

### Regular Monitoring

```bash
# Set up monitoring alerts
curl -X POST http://localhost:8081/monitoring/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "condition": "cpu.usage > 80",
    "severity": "warning",
    "action": "email"
  }'
```

### Health Checks

```bash
# Configure automated health checks
health:
  checks:
    - name: "engine-connectivity"
      type: "http"
      endpoint: "http://yawl-engine:8080/health"
      interval: 30000
    - name: "agent-registry"
      type: "http"
      endpoint: "http://localhost:8081/registry/health"
      interval: 60000
```

### Log Management

```bash
# Configure log rotation
logging:
  file:
    enabled: true
    maxSize: "100MB"
    maxHistory: 30
    totalSizeCap: "1GB"

# Centralized logging
logging:
  logstash:
    enabled: true
    host: "logstash:5044"
    port: 5044
```

## 11. Getting Help

### Community Support

```bash
# Join community discussions
curl https://github.com/yawlfoundation/yawl/discussions

# Check documentation
https://docs.yawlfoundation.org/yawl/6.0/

# Report issues
curl -X POST https://github.com/yawlfoundation/yawl/issues \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Agent coordination issue",
    "body": "Detailed issue description...",
    "labels": ["bug", "agent-coordination"]
  }'
```

### Professional Support

```bash
# Contact support
curl https://support.yawlfoundation.com/api/tickets \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{
    "priority": "high",
    "subject": "Production issue - ADR-025 coordination",
    "description": "Detailed issue description..."
  }'
```

## Quick Reference

### Common Commands

```bash
# Health check
curl http://localhost:8081/health

# Metrics
curl http://localhost:8081/metrics

# Configuration validation
java -jar yawl-agent.jar --validate-config

# Diagnostics
java -jar yawl-agent.jar --diagnose

# Restart agent
docker restart yawl-agent-001

# Check logs
docker logs yawl-agent-001 --tail 100
```

### Configuration Files Locations

- Application configuration: `application.yaml`
- Log files: `/var/log/yawl/agent.log`
- Metrics: `http://localhost:8081/metrics`
- Health: `http://localhost:8081/health`

### Useful Links

- [ADR-025 Implementation Guide](ADR-025-IMPLEMENTATION.md)
- [Agent Coordination Examples](agent-coordination-examples.md)
- [Configuration Examples](configuration-examples.md)
- [Official Documentation](https://docs.yawlfoundation.org/yawl/6.0/)
- [Issue Tracker](https://github.com/yawlfoundation/yawl/issues)
- [Community Forum](https://github.com/yawlfoundation/yawl/discussions)