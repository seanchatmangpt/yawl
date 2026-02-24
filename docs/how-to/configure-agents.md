# Agent Coordination Configuration Examples

**Comprehensive configuration templates for all ADR-025 components**

## Quick Start - Basic Configuration

```bash
# Copy basic configuration
cp configs/basic-agent.yaml application.yaml

# Start with single agent
java -jar yawl-agent.jar --config application.yaml

# Enable partitioning
sed -i 's/partition:/partition:\n    enabled: true/' application.yaml
sed -i 's/  agentIndex: 0/  agentIndex: 0\n  totalAgents: 3/' application.yaml

# Enable handoff
echo "handoff:" >> application.yaml
echo "  enabled: true" >> application.yaml
```

## Configuration Structure Overview

```yaml
# Main configuration structure
application.yaml:
  agent:                 # Agent identity and capabilities
  engine:               # YAWL engine connection
  strategies:           # Discovery, eligibility, decision strategies
  partition:            # Work item partitioning configuration
  handoff:             # Work item handoff configuration
  conflict:            # Conflict resolution configuration
  discovery:           # Agent discovery configuration
  resilience:          # Resilience and fault tolerance
  monitoring:          # Monitoring and metrics
  zai:                 # Z.AI integration configuration
  mcp:                 # MCP server configuration
  a2a:                 # A2A integration configuration
  performance:         # Performance tuning
  logging:             # Logging configuration
```

## 1. Agent Configuration

### Single Agent Setup

```yaml
# single-agent.yaml
agent:
  id: "document-reviewer-001"
  name: "Document Review Specialist"
  description: "Reviews and processes various document types"
  version: "1.0.0"
  port: 8081
  host: "localhost"
  protocol: "http"

  capability:
    domain: "document-processing"
    version: "1.0"
    skills:
      - "document-review"
      - "content-analysis"
      - "error-detection"
    constraints:
      documentTypes: ["PDF", "DOCX", "TXT"]
      maxPages: 100
      maxFileSize: "10MB"
      priority: "medium"
      languages: ["en", "es"]

  health:
    endpoint: "/health"
    interval: 30000
    timeout: 5000
    checks:
      - "cpu-usage"
      - "memory-usage"
      - "disk-space"
    thresholds:
      cpuUsage: 80      # Percentage
      memoryUsage: 80   # Percentage
      diskSpace: 10     # GB free space required

  metadata:
    created: "2024-01-01"
    updated: "2024-01-01"
    tags:
      - "document-processing"
      - "review"
    contact:
      email: "support@company.com"
      phone: "+1-555-0123"
```

### Multi-Agent Setup

```yaml
# multi-agent.yaml
agent:
  id: "reviewer-cluster-001"
  name: "Document Review Cluster"
  description: "Coordinated document review system"
  version: "2.0.0"
  port: 8081
  host: "reviewer-cluster-001"

  # Cluster configuration
  cluster:
    enabled: true
    role: "worker"  # master | worker
    nodeId: "node-001"
    clusterId: "review-cluster"

  capability:
    domain: "document-processing"
    version: "2.0"
    skills:
      - "general-review"
      - "contract-review"
      - "legal-review"
      - "quality-assurance"

    # Specialized skills
    specializations:
      contract:
        enabled: true
        skills: ["contract-analysis", "risk-assessment"]
        constraints:
          documentTypes: ["PDF"]
          maxPages: 200
      legal:
        enabled: true
        skills: ["legal-compliance", "regulatory-check"]
        constraints:
          documentTypes: ["PDF", "DOCX"]
          priority: "high"
      quality:
        enabled: true
        skills: ["error-detection", "format-check"]
        constraints:
          documentTypes: ["DOCX", "TXT"]
          maxPages: 500

  # Resource limits
  resources:
    maxConcurrentWorkItems: 50
    maxMemoryUsage: "512MB"
    maxProcessingTime: "30m"

  # Quality settings
  quality:
    confidenceThreshold: 0.8
    requireHumanReview: false
    autoApproveThreshold: 0.9
    rejectThreshold: 0.3
```

## 2. Engine Configuration

### Basic Engine Connection

```yaml
# basic-engine.yaml
engine:
  url: "http://localhost:8080/yawl"
  username: "admin"
  password: "YAWL"

  connection:
    timeout: 30000
    readTimeout: 60000
    connectTimeout: 10000
    maxRetries: 3
    retryDelay: 1000

  polling:
    enabled: true
    interval: 5000
    enabledOnly: true
    maxWorkItems: 100
    batchProcessing: true

  session:
    timeout: 3600000  # 1 hour
    renewInterval: 1800000  # 30 minutes
    maxSessions: 100

  caching:
    enabled: true
    size: 1000
    ttl: 300000  # 5 minutes
    evictionPolicy: "LRU"
```

### Advanced Engine Configuration

```yaml
# advanced-engine.yaml
engine:
  # Multiple engine support
  servers:
    - id: "primary"
      url: "http://primary-engine:8080/yawl"
      weight: 100
    - id: "secondary"
      url: "http://backup-engine:8080/yawl"
      weight: 50

  # Load balancing
  loadBalance:
    strategy: "round-robin"
    healthCheck:
      enabled: true
      interval: 30000
      timeout: 5000
      unhealthyThreshold: 3

  # Connection pool
  pool:
    maxTotal: 50
    maxIdle: 10
    minIdle: 5
    testOnBorrow: true
    testOnReturn: true
    testWhileIdle: true

  # Circuit breaker
  circuitBreaker:
    enabled: true
    failureThreshold: 5
    recoveryTimeout: 30000
    halfOpenAttempts: 1
    slidingWindowType: "count"
    slidingWindowSize: 100

  # Fallback
  fallback:
    enabled: true
    mode: "graceful"  # graceful | fail-fast
    timeout: 10000
    errorHandler: "default"
```

## 3. Strategies Configuration

### Discovery Strategies

```yaml
# discovery-strategies.yaml
strategies:
  discovery:
    # Polling strategy
    polling:
      enabled: true
      interval: 5000
      enabledOnly: true
      maxWorkItems: 100
      filter:
        status: ["enabled"]
        age: "1h"  # Only items created in last hour

    # A2A strategy
    a2a:
      enabled: false
      endpoint: "http://a2a-server:8080"
      apiKey: "${A2A_API_KEY}"
      subscription:
        enabled: true
        topics:
          - "workitem.created"
          - "workitem.assigned"
        callback: "http://agent:8081/callback"

    # Event-based strategy
    event:
      enabled: false
      broker:
        type: "kafka"
        bootstrapServers: "kafka:9092"
        groupId: "agent-discovery"
        topics:
          - "yawl.workitems"
        consumer:
          autoCommit: true
          autoOffsetReset: "earliest"

  eligibility:
    # ZAI eligibility
    zai:
      enabled: true
      model: "glm-4-flash"
      prompt: "Can you handle this work item? Consider your capabilities and constraints."
      temperature: 0.1
      maxTokens: 1000
      timeout: 45000
      cache:
        enabled: true
        size: 1000
        ttl: 300000

    # Rule-based eligibility
    rule:
      enabled: true
      rules:
        - id: "document-type-check"
          condition: "documentType in ['PDF', 'DOCX']"
          action: "allow"
        - id: "page-limit-check"
          condition: "pageCount <= maxPages"
          action: "allow"
        - id: "priority-check"
          condition: "priority in ['high', 'medium']"
          action: "allow"
        - id: "fallback"
          action: "deny"

  decision:
    # ZAI decision making
    zai:
      enabled: true
      model: "glm-4-flash"
      prompt: "Make a decision on this work item. Provide your reasoning and final decision."
      temperature: 0.2
      maxTokens: 2000
      timeout: 45000
      format: "json"  # json | text

    # Majority voting
    majority:
      enabled: true
      minVotes: 2
      confidenceThreshold: 0.7

    # Hybrid approach
    hybrid:
      enabled: false
      zaiWeight: 0.7
      ruleWeight: 0.3
      consensusThreshold: 0.8
```

## 4. Partition Configuration

### Basic Partitioning

```yaml
# basic-partition.yaml
partition:
  enabled: true
  agentIndex: 0
  totalAgents: 3
  strategy: "hash"

  hash:
    function: "taskId"  # taskId | caseId | custom
    salt: "agent-001"
    seed: 42

  consistency:
    enabled: true
    checkInterval: 60000
    tolerance: 0.1  # 10% imbalance allowed

  balancing:
    enabled: true
    strategy: "redistribute"  # redistribute | scale | alert
    redistribute:
      threshold: 0.2  # Redistribute if imbalance > 20%
      batchSize: 100
      cooldown: 300000  # 5 minutes between redistributions

  cache:
    enabled: true
    size: 10000
    ttl: 300000
    evictionPolicy: "LRU"
```

### Advanced Partitioning

```yaml
# advanced-partition.yaml
partition:
  enabled: true

  # Multi-level partitioning
  levels:
    - name: "primary"
      strategy: "hash"
      key: "taskType"
      agents: 3
    - name: "secondary"
      strategy: "range"
      key: "priority"
      ranges:
        - range: [0, 2]
          agent: 0
        - range: [3, 7]
          agent: 1
        - range: [8, 10]
          agent: 2

  # Weighted partitioning
  weights:
    agent-001: 1.0  # Normal weight
    agent-002: 1.5  # 50% more capacity
    agent-003: 0.8  # 20% less capacity

  # Priority-based partitioning
  priority:
    enabled: true
    highPriority:
      agents: [1, 2]  # High priority agents
      weight: 1.2
    normalPriority:
      agents: [0, 1, 2]
      weight: 1.0

  # Dynamic partitioning
  dynamic:
    enabled: true
    metrics:
      - "cpu-usage"
      - "memory-usage"
      - "response-time"
    rebalanceThreshold: 0.15
    minRebalanceInterval: 300000  # 5 minutes

  # Affinity partitioning
  affinity:
    enabled: true
    sticky: true  # Keep work items with same case on same agent
    timeout: 3600000  # 1 hour affinity timeout
```

## 5. Handoff Configuration

### Basic Handoff

```yaml
# basic-handoff.yaml
handoff:
  enabled: true
  timeout: 30000
  ttl: 60000
  maxRetries: 3

  # Handoff criteria
  criteria:
    enabled: true
    conditions:
      - type: "capability"
        condition: "requiredCapability not in agentCapabilities"
      - type: "load"
        condition: "currentWorkItems > maxConcurrentWorkItems"
      - type: "specialization"
        condition: "hasSpecializedRequirements && hasSpecializedAgent"
      - type: "failure"
        condition: "retryCount >= maxRetries"

  # Retry configuration
  retry:
    enabled: true
    maxAttempts: 3
    baseDelay: 1000
    maxDelay: 30000
    jitter: true
    exponentialBackoff: true
    multiplier: 2.0

  # Circuit breaker
  circuitBreaker:
    enabled: true
    failureThreshold: 5
    recoveryTimeout: 60000
    halfOpenAttempts: 1
    slidingWindow: 100

  # Validation
  validation:
    enabled: true
    checkTarget: true
    checkCompatibility: true
    validateCapability: true
    validateCapacity: true
```

### Advanced Handoff

```yaml
# advanced-handoff.yaml
handoff:
  enabled: true

  # Handoff policies
  policies:
    default:
      timeout: 30000
      maxRetries: 3
      escalateOnFailure: true

    specialized:
      timeout: 45000
      maxRetries: 5
      priority: "HIGH"
      conditions:
        - "taskType in ['contract', 'legal']"

    urgent:
      timeout: 15000
      maxRetries: 1
      priority: "CRITICAL"
      conditions:
        - "priority == 'URGENT'"
        - "slaDeadline < now + 1h"

  # Handoff queue
  queue:
    enabled: true
    size: 1000
    priority: "HIGH"
    batchProcessing: true
    batchSize: 10
    consumerThreads: 4

  # Event handling
  events:
    enabled: true
    listeners:
      - type: "pre-handoff"
        handler: "validateHandoff"
      - type: "post-handoff"
        handler: "notifySourceAgent"
      - type: "handoff-failed"
        handler: "escalateToManager"
      - type: "handoff-success"
        handler: "logMetrics"

  # Metrics
  metrics:
    enabled: true
    tracking:
      - "handoff-initiation-time"
      - "handoff-completion-time"
      - "handoff-success-rate"
      - "handoff-failure-reason"

  # Security
  security:
    enabled: true
    authentication: "jwt"
    authorization: "rbac"
    encryption: "aes256"
    audit:
      enabled: true
      level: "detailed"
```

## 6. Conflict Resolution Configuration

### Basic Conflict Resolution

```yaml
# basic-conflict.yaml
conflict:
  enabled: true
  resolution: "escalate"

  escalation:
    enabled: true
    threshold: 0.8  # 80% agreement required
    arbiterEndpoint: "http://arbiter-agent:8081"
    timeout: 30000
    retry: true
    maxRetries: 2

  # Resolution strategies
  strategies:
    majorityVote:
      enabled: true
      quorum: 3
      timeout: 10000

    escalation:
      enabled: true
      arbiterEndpoint: "http://arbiter:8081"
      timeout: 30000

    humanFallback:
      enabled: true
      resourceService: "human-resources"
      escalationMessage: "All agents disagree, human review required"

    consensus:
      enabled: true
      threshold: 0.9
      timeout: 60000
      maxRounds: 3
```

### Advanced Conflict Resolution

```yaml
# advanced-conflict.yaml
conflict:
  enabled: true

  # Per-task conflict resolution
  taskResolution:
    "FinancialApproval":
      strategy: "escalation"
      threshold: 0.9
      arbiter: "finance-director"
      fallback: "human"
      timeout: 45000

    "ContractReview":
      strategy: "majorityVote"
      quorum: 2
      timeout: 30000

    "SecurityCheck":
      strategy: "consensus"
      threshold: 0.8
      timeout: 60000
      maxRounds: 3

  # Resolution pipeline
  pipeline:
    enabled: true
    stages:
      - name: "quick-resolution"
        timeout: 5000
        strategies: ["majorityVote"]
      - name: "extended-resolution"
        timeout: 30000
        strategies: ["escalation", "consensus"]
      - name: "final-escalation"
        timeout: 60000
        strategies: ["humanFallback"]

  # Conflict detection
  detection:
    enabled: true
    criteria:
      - "decisions.size() >= quorum"
      - "agreement < threshold"
      - "timeSinceFirstDecision > timeout"

    # Automated detection
    automated:
      enabled: true
      interval: 10000
      checkOnComplete: true
      checkOnTimeout: true

  # Resolution logging
  logging:
    enabled: true
    level: "DEBUG"
    includeReasoning: true
    includeAgentDecisions: true
    includeMetrics: true

  # Metrics and monitoring
  metrics:
    enabled: true
    tracking:
      - "conflict-detection-time"
      - "resolution-time"
      - "success-rate"
      - "escalation-rate"
      - "human-fallback-rate"

  # Alerting
  alerts:
    enabled: true
    conditions:
      - "conflictRate > 0.1"
      - "resolutionTime > 60000"
      - "escalationRate > 0.05"
    channels:
      - type: "email"
        recipients: ["team-lead@company.com"]
      - type: "slack"
        webhook: "${SLACK_WEBHOOK}"
```

## 7. Discovery Configuration

### Basic Agent Discovery

```yaml
# basic-discovery.yaml
discovery:
  enabled: true

  # Static agents
  staticAgents:
    - id: "agent-002"
      url: "http://agent-002:8081"
      capabilities: ["document-processing"]
      weight: 1.0
    - id: "agent-003"
      url: "http://agent-003:8081"
      capabilities: ["document-processing"]
      weight: 1.0

  # Health checks
  health:
    enabled: true
    interval: 60000
    timeout: 5000
    path: "/health"
    expectedStatus: "UP"

    # Health check strategy
    strategy:
      type: "http"
      retries: 3
      backoff: 1000
      circuitBreaker:
        enabled: true
        threshold: 5

  # Registration
  registration:
    enabled: true
    interval: 30000
    heartbeat: true
    heartbeatInterval: 10000
    ttl: 120000  # 2 minutes
```

### Advanced Discovery

```yaml
# advanced-discovery.yaml
discovery:
  enabled: true

  # Multiple discovery methods
  methods:
    static:
      enabled: true
      agents:
        - id: "core-agent-001"
          url: "http://core-001:8081"
          capabilities: ["document-processing"]
          priority: "HIGH"
        - id: "core-agent-002"
          url: "http://core-002:8081"
          capabilities: ["document-processing"]
          priority: "HIGH"

    dynamic:
      enabled: true
      serviceRegistry:
        type: "consul"
        address: "consul:8500"
        healthCheck: true
        healthInterval: 10000

    a2a:
      enabled: true
      serverUrl: "http://a2a-server:8080"
      apiKey: "${A2A_API_KEY}"
      subscription:
        topics: ["agent.registered", "agent.unregistered"]
        callback: "http://agent:8081/discovery/callback"

  # Load balancing
  loadBalance:
    strategy: "weighted-round-robin"
    healthCheck: true
    failover: true
    stickiness: false

    # Weighted agents
    weights:
      "core-agent-001": 2.0
      "core-agent-002": 1.5
      "backup-agent": 1.0

  # Service mesh integration
  serviceMesh:
    enabled: true
    istio:
      enabled: true
      namespace: "yawl-agents"
      service: "agent-service"
      destinationRule:
        name: "agent-destination"
        subset: "stable"

  # Caching
  cache:
    enabled: true
    size: 1000
    ttl: 300000
    refreshInterval: 60000
    invalidation: true

  # Events
  events:
    enabled: true
    handlers:
      - type: "agent-registered"
        handler: "updateLoadBalancer"
      - type: "agent-unregistered"
        handler: "triggerFailover"
      - type: "agent-health-change"
        handler: "updateMetrics"
```

## 8. Resilience Configuration

### Basic Resilience

```yaml
# basic-resilience.yaml
resilience:
  retry:
    enabled: true
    maxAttempts: 3
    waitDuration: 1000
    jitter: true
    retryOn: ["timeout", "5xx", "connection-error"]

  circuitBreaker:
    enabled: true
    failureRateThreshold: 50.0
    waitDurationInOpenState: 30
    slidingWindowSize: 10
    slidingWindowType: "count"

  rateLimiter:
    enabled: true
    limitForPeriod: 100
    limitRefreshPeriod: 1
    timeoutDuration: 0

  bulkhead:
    enabled: true
    maxConcurrentCalls: 50
    maxWaitTime: 0
```

### Advanced Resilience

```yaml
# advanced-resilience.yaml
resilience:
  # Retry configuration
  retry:
    enabled: true
    maxAttempts: 3
    waitDuration: 1000
    exponentialBackoff:
      enabled: true
      initialInterval: 1000
      multiplier: 2.0
      maxInterval: 10000
    jitter:
      enabled: true
      factor: 0.1
    retryCondition:
      retryOn:
        - "org.springframework.web.client.HttpServerErrorException"
        - "java.util.concurrent.TimeoutException"
        - "java.net.ConnectException"
      maxRetryOn: 5
      openTimeout: 5000
      timeout: 10000

  # Circuit breaker configuration
  circuitBreaker:
    enabled: true
    failureRateThreshold: 50.0
    waitDurationInOpenState: 30
    slidingWindow:
      size: 10
      type: "count"
      minCallsInHalfOpenState: 2
      slidingWindowType: "count"
      registerHealthIndicator: true
    slidingWindowSize: 100
      slidingWindowType: "count"
      minimumNumberOfCalls: 20
      waitDurationInOpenState: 5
      failureRateThreshold: 50
      slidingWindowType: "count"
      recordExceptions:
        - "java.lang.Exception"
      ignoreExceptions:
        - "java.lang.IllegalArgumentException"
      waitDurationInOpenState: 5
      failureRateThreshold: 50
      slidingWindowSize: 10
      slidingWindowType: "count"
      minimumNumberOfCalls: 10
      slidingWindowType: "count"
      registerHealthIndicator: true

  # Rate limiter configuration
  rateLimiter:
    enabled: true
    limitForPeriod: 100
    limitRefreshPeriod: 1000
    timeoutDuration: 0
    registerHealthIndicator: true

  # Bulkhead configuration
  bulkhead:
    enabled: true
    maxConcurrentCalls: 50
    maxWaitTime: 0
    waitDurationInOpenState: 0
    registerHealthIndicator: true

  # Time limiter configuration
  timeLimiter:
    enabled: true
    timeoutDuration: 1000
    cancelRunningFuture: true
    registerHealthIndicator: true

  # ThreadPool configuration
  threadPool:
    enabled: true
    corePoolSize: 10
    maxPoolSize: 50
    keepAliveTime: 60
    queueCapacity: 100
    threadNamePrefix: "agent-worker-"

  # Circuit breaker factory
  circuitBreakerFactory:
    failureRateThreshold: 50.0
    waitDurationInOpenState: 5
    slidingWindowSize: 10
    slidingWindowType: "count"
    minimumNumberOfCalls: 10
    slidingWindowType: "count"
    registerHealthIndicator: true
    failureRateThreshold: 50.0
    waitDurationInOpenState: 5
    slidingWindowSize: 10
    slidingWindowType: "count"
    minimumNumberOfCalls: 10
    slidingWindowType: "count"
    registerHealthIndicator: true

  # Metrics configuration
  metrics:
    enabled: true
    tags:
      application: "yawl-agent"
      region: "us-west-2"
    meterRegistry:
      type: "micrometer"
      enabled: true

  # Health checks
  health:
    enabled: true
    checks:
      - name: "discovery"
        type: "discovery"
        timeout: 5000
        interval: 30000
      - name: "database"
        type: "database"
        timeout: 5000
        interval: 30000
      - name: "external-services"
        type: "http"
        timeout: 5000
        interval: 30000

  # Monitoring configuration
  monitoring:
    enabled: true
    metrics:
      enabled: true
      tags:
        application: "yawl-agent"
        region: "us-west-2"
      meterRegistry:
        type: "micrometer"
        enabled: true
    health:
      enabled: true
      checks:
        - name: "discovery"
          type: "discovery"
          timeout: 5000
          interval: 30000
        - name: "database"
          type: "database"
          timeout: 5000
          interval: 30000
        - name: "external-services"
          type: "http"
          timeout: 5000
          interval: 30000
```

## 9. Monitoring Configuration

### Basic Monitoring

```yaml
# basic-monitoring.yaml
monitoring:
  enabled: true
  metrics:
    enabled: true
    interval: 10000
    retention: 86400000  # 24 hours

  # Metrics collection
  metrics:
    jvm:
      enabled: true
      include: ["memory", "threads", "gc"]
    system:
      enabled: true
      include: ["cpu", "memory", "disk"]
    custom:
      enabled: true
      include: ["workitems", "handoffs", "conflicts"]

  # Logging
  logging:
    level: "INFO"
    format: "json"
    file:
      enabled: true
      path: "logs/agent.log"
      maxSize: "100MB"
      maxHistory: 30
      totalSizeCap: "1GB"
```

### Advanced Monitoring

```yaml
# advanced-monitoring.yaml
monitoring:
  enabled: true

  # Metrics configuration
  metrics:
    enabled: true
    registry:
      type: "micrometer"
      exporters:
        prometheus:
          enabled: true
          endpoint: "/metrics"
          path: "/metrics"
          port: 8081
          description: "Prometheus metrics endpoint"
        jmx:
          enabled: true
          domain: "yawl.agent"
        log:
          enabled: true
          level: "INFO"

    # Custom metrics
    custom:
      enabled: true
      gauges:
        - name: "workitems.processed"
          description: "Total work items processed"
          tags: ["agent=001", "type=processed"]
        - name: "handoffs.initiated"
          description: "Total handoffs initiated"
          tags: ["agent=001", "type=initiated"]
        - name: "conflicts.resolved"
          description: "Total conflicts resolved"
          tags: ["agent=001", "type=resolved"]

      counters:
        - name: "errors.handoff.failed"
          description: "Count of failed handoffs"
          tags: ["agent=001", "type=error"]
        - name: "conflict.detection"
          description: "Count of conflict detections"
          tags: ["agent=001", "type=detection"]

      timers:
        - name: "workitem.processing.time"
          description: "Time to process work items"
          tags: ["agent=001", "type=processing"]
        - name: "handoff.duration"
          description: "Handoff duration"
          tags: ["agent=001", "type=handoff"]

  # Tracing configuration
  tracing:
    enabled: true
    provider: "opentelemetry"
    sampler:
      type: "traceidratio"
      ratio: 0.1
    propagators:
      - "tracecontext"
      - "baggage"
    attributes:
      agent.id: "agent-001"
      agent.version: "2.0.0"

    # Exporters
    exporters:
      otlp:
        enabled: true
        endpoint: "http://otel-collector:4317"
        headers:
          "authorization": "Bearer ${OTEL_TOKEN}"
      jaeger:
        enabled: false
        endpoint: "http://jaeger:14250"

  # Alerting configuration
  alerting:
    enabled: true
    rules:
      - name: "high-error-rate"
        condition: "rate(errors.handoff.failed[5m]) > 0.1"
        severity: "warning"
        duration: "5m"
        message: "High error rate detected"

      - name: "memory-pressure"
        condition: "jvm.memory.used > jvm.memory.max * 0.8"
        severity: "critical"
        duration: "1m"
        message: "High memory usage detected"

      - name: "partition-imbalance"
        condition: "partition.imbalance > 0.3"
        severity: "warning"
        duration: "10m"
        message: "Partition imbalance detected"

    # Notification channels
    channels:
      - type: "email"
        recipients: ["ops@company.com"]
        template: "alert-email"
      - type: "slack"
        webhook: "${SLACK_WEBHOOK}"
        channel: "#alerts"
      - type: "pagerduty"
        serviceKey: "${PAGERDUTY_KEY}"

  # Dashboard configuration
  dashboard:
    enabled: true
    type: "grafana"
    url: "http://grafana:3000"
    datasources:
      - name: "prometheus"
        type: "prometheus"
        url: "http://prometheus:9090"
      - name: "jaeger"
        type: "jaeger"
        url: "http://jaeger:16686"

    # Dashboard templates
    templates:
      - name: "agent-overview"
        path: "dashboards/agent-overview.json"
      - name: "workitem-processing"
        path: "dashboards/workitem-processing.json"
      - name: "handoff-performance"
        path: "dashboards/handoff-performance.json"
```

## 10. Environment Variables Mapping

### Complete Mapping

| Environment Variable | Config Path | Description | Required |
|---------------------|-------------|-------------|----------|
| `AGENT_ID` | `agent.id` | Agent identifier | Yes |
| `AGENT_NAME` | `agent.name` | Agent display name | No |
| `AGENT_PORT` | `agent.port` | Agent HTTP port | Yes |
| `AGENT_HOST` | `agent.host` | Agent hostname | No |
| `AGENT_VERSION` | `agent.version` | Agent version | No |
| `YAWL_ENGINE_URL` | `engine.url` | YAWL engine URL | Yes |
| `YAWL_USERNAME` | `engine.username` | YAWL admin username | Yes |
| `YAWL_PASSWORD` | `engine.password` | YAWL admin password | Yes |
| `ZAI_API_KEY` | `zai.apiKey` | Z.AI API key | Yes |
| `A2A_API_KEY` | `a2a.apiKey` | A2A API key | No |
| `A2A_SERVER_URL` | `a2a.serverUrl` | A2A server URL | No |
| `PARTITION_ENABLED` | `partition.enabled` | Enable partitioning | No |
| `PARTITION_INDEX` | `partition.agentIndex` | Agent partition index | No |
| `PARTITION_TOTAL` | `partition.totalAgents` | Total agent count | No |
| `HANDOFF_ENABLED` | `handoff.enabled` | Enable handoff | No |
| `CONFLICT_ENABLED` | `conflict.enabled` | Enable conflict resolution | No |
| `LOG_LEVEL` | `logging.level` | Log level (DEBUG, INFO, WARN, ERROR) | No |
| `METRICS_ENABLED` | `monitoring.metrics.enabled` | Enable metrics collection | No |
| `JAVA_OPTS` | - | JVM options | No |

### Docker Environment Mapping

```yaml
# docker-compose.yml
version: '3.8'
services:
  agent-001:
    build: .
    environment:
      # Required
      - AGENT_ID=agent-001
      - YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
      - YAWL_USERNAME=admin
      - YAWL_PASSWORD=YAWL
      - ZAI_API_KEY=${ZAI_API_KEY}

      # Partitioning
      - PARTITION_ENABLED=true
      - PARTITION_INDEX=0
      - PARTITION_TOTAL=3

      # Optional
      - LOG_LEVEL=INFO
      - METRICS_ENABLED=true
      - JAVA_OPTS=-Xmx512m -Xms256m

    ports:
      - "8081:8081"
    depends_on:
      - yawl-engine
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### Kubernetes Configuration

```yaml
# agent-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-agent
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-agent
  template:
    metadata:
      labels:
        app: yawl-agent
    spec:
      containers:
      - name: agent
        image: yawl/agent:2.0.0
        ports:
        - containerPort: 8081
        env:
        - name: AGENT_ID
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: AGENT_PORT
          value: "8081"
        - name: YAWL_ENGINE_URL
          value: "http://yawl-engine:8080/yawl"
        - name: YAWL_USERNAME
          value: "admin"
        - name: YAWL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-secrets
              key: password
        - name: ZAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: zai-secrets
              key: api-key
        - name: PARTITION_ENABLED
          value: "true"
        - name: PARTITION_INDEX
          valueFrom:
            fieldRef:
              fieldPath: metadata.annotations['partition-index']
        - name: PARTITION_TOTAL
          value: "3"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8081
          initialDelaySeconds: 5
          periodSeconds: 5
```

## 11. Profile Configuration

### Development Profile

```yaml
# profiles/application-dev.yaml
spring:
  profiles: dev

agent:
  id: "dev-agent-001"
  name: "Development Agent"
  port: 8081

engine:
  url: "http://localhost:8080/yawl"
  username: "admin"
  password: "dev-password"

partition:
  enabled: true
  agentIndex: 0
  totalAgents: 1  # Single agent for dev

handoff:
  enabled: false

conflict:
  enabled: false

logging:
  level: "DEBUG"
  file:
    enabled: true
    path: "logs/agent-dev.log"

monitoring:
  metrics:
    enabled: false

zai:
  apiKey: "dev-api-key"
  model: "glm-4-flash"
  temperature: 0.3
  timeout: 30000
```

### Production Profile

```yaml
# profiles/application-prod.yaml
spring:
  profiles: prod

agent:
  id: "prod-agent-001"
  name: "Production Document Reviewer"
  port: 8081
  capability:
    domain: "document-processing"
    skills:
      - "contract-review"
      - "legal-analysis"
      - "risk-assessment"
    constraints:
      documentTypes: ["PDF", "DOCX"]
      maxPages: 200
      priority: "high"

engine:
  url: "http://yawl-engine-prod:8080/yawl"
  username: "${YAWL_USERNAME}"
  password: "${YAWL_PASSWORD}"
  connection:
    timeout: 30000
    maxRetries: 3
    retryDelay: 1000

partition:
  enabled: true
  agentIndex: 0
  totalAgents: 3
  strategy: "consistent_hash"

handoff:
  enabled: true
  timeout: 30000
  ttl: 60000
  maxRetries: 3
  circuitBreaker:
    enabled: true
    threshold: 5

conflict:
  enabled: true
  resolution: "escalate"
  agreementThreshold: 0.8
  arbiterEndpoint: "http://arbiter-prod:8081"

resilience:
  retry:
    enabled: true
    maxAttempts: 3
    waitDuration: 1000
    exponentialBackoff: true

  circuitBreaker:
    enabled: true
    failureRateThreshold: 50.0
    waitDurationInOpenState: 30

logging:
  level: "INFO"
  file:
    enabled: true
    path: "/var/log/yawl/agent-prod.log"
    maxSize: "100MB"
    maxHistory: 30
    totalSizeCap: "1GB"

monitoring:
  metrics:
    enabled: true
    registry:
      type: "micrometer"
      exporters:
        prometheus:
          enabled: true
          endpoint: "/metrics"
        stackdriver:
          enabled: true

  alerting:
    enabled: true
    rules:
      - name: "high-error-rate"
        condition: "rate(errors.handoff.failed[5m]) > 0.1"
        severity: "warning"
        channels: ["slack", "pagerduty"]

zai:
  apiKey: "${ZAI_API_KEY}"
  model: "glm-4-flash"
  temperature: 0.1
  maxTokens: 2000
  timeout: 45000
```

## 12. Configuration Validation

### Validation Schema

```yaml
# config-validation.yaml
validation:
  # Agent configuration validation
  agent:
    required:
      - id
      - port
    type:
      port: "integer"
      version: "string"
    range:
      port: [1024, 65535]
      maxConcurrentWorkItems: [1, 1000]

  # Engine configuration validation
  engine:
    required:
      - url
      - username
      - password
    format:
      url: "uri"
      username: "alphanumeric"

  # Partition configuration validation
  partition:
    conditional:
      enabled:
        when: true
        required:
          - agentIndex
          - totalAgents
    range:
      agentIndex: [0, 999]
      totalAgents: [1, 100]

  # Handoff configuration validation
  handoff:
    conditional:
      enabled:
        when: true
        required:
          - timeout
          - ttl
    range:
      timeout: [1000, 300000]
      ttl: [1000, 300000]
```

### Validation Configuration

```yaml
# config-validator.yaml
validator:
  # Validation modes
  mode: "STRICT"  # STRICT | WARN | LENIENT

  # Custom validation rules
  rules:
    - name: "partition-validity"
      description: "Validate partition configuration"
      condition: "partition.enabled and partition.agentIndex >= partition.totalAgents"
      action: "ERROR"
      message: "Agent index must be less than total agents"

    - name: "handoff-timeout-validity"
      description: "Validate handoff timeout is reasonable"
      condition: "handoff.enabled and handoff.timeout > handoff.ttl"
      action: "WARN"
      message: "Handoff timeout should not exceed TTL"

    - name: "capacity-sufficiency"
      description: "Validate agent capacity is sufficient"
      condition: "agent.maxConcurrentWorkItems < 10"
      action: "WARN"
      message: "Consider increasing max concurrent work items"

  # Validation hooks
  hooks:
    before: ["validateBasicConfig"]
    after: ["validateIntegration"]
    onFailure: ["logValidationError"]

  # Error handling
  errors:
    format: "JSON"
    includeStacktrace: false
    maxRetries: 3
    retryDelay: 1000
```

## Best Practices

1. **Environment-specific configurations**: Use profiles for dev/staging/prod
2. **Secret management**: Store sensitive data in environment variables or secrets
3. **Configuration validation**: Validate configurations at startup
4. **Documentation**: Document all configuration options
5. **Version control**: Track configuration changes
6. **Testing**: Test configurations in development before production
7. **Monitoring**: Monitor configuration changes and their effects
8. **Security**: Secure sensitive configuration values
9. **Backup**: Maintain backup configurations
10. **Update management**: Plan configuration updates carefully

## Troubleshooting

1. **Configuration errors**: Check validation logs for specific errors
2. **Connection issues**: Verify URLs and credentials
3. **Performance problems**: Check resource limits and timeouts
4. **Coordination issues**: Verify partition and handoff configurations
5. **Conflicts**: Review conflict resolution settings

For more help, see:
- [Troubleshooting Guide](troubleshooting.md)
- [ADR-025 Implementation Guide](ADR-025-IMPLEMENTATION.md)
- [Agent Coordination Examples](agent-coordination-examples.md)