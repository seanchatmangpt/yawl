# YAWL Performance Metrics Dashboard Specification

**Version:** 1.0
**Date:** 2026-02-27
**Target:** Grafana v9.0+

---

## 1. Dashboard Overview

### Purpose
Provide real-time performance monitoring for YAWL workflow engine production deployments with Java 25 virtual thread optimizations.

### Key Features
- Real-time metrics visualization
- Performance threshold monitoring
- Virtual thread health tracking
- Database performance analytics
- Alert integration
- Historical trend analysis

### Target Audience
- Site Reliability Engineers (SREs)
- Performance Engineers
- Development Teams
- Operations Teams

---

## 2. Dashboard Structure

### Main Dashboard: `YAWL Performance v6.0`

#### Grid Layout: 4x4 panels (16 panels total)

| Row 1 | Row 2 | Row 3 | Row 4 |
|-------|-------|-------|-------|
| 1. System Overview | 5. Throughput Metrics | 9. Memory Analysis | 13. Virtual Thread Health |
| 2. Alert Status | 6. Latency Analysis | 10. Database Performance | 14. JVM Metrics |
| 3. Request Metrics | 7. Error Analysis | 11. GC Performance | 15. Resource Utilization |
| 4. Service Health | 8. Task Processing | 12. Network Throughput | 16. Performance Trends |

---

## 3. Panel Specifications

### Panel 1: System Overview
```json
{
  "title": "System Overview",
  "type": "stat",
  "targets": [
    {
      "expr": "up{job=\"yawl\"}",
      "legendFormat": "Service Status"
    },
    {
      "expr": "yawl_instances",
      "legendFormat": "Active Instances"
    },
    {
      "expr": "yawl_uptime_seconds",
      "legendFormat": "Uptime"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"text": "DOWN"}}},
        {"type": "value", "options": {"1": {"text": "UP"}}}
      ],
      "thresholds": {
        "steps": [
          {"color": "red", "value": 0},
          {"color": "green", "value": 1}
        ]
      }
    }
  }
}
```

### Panel 2: Alert Status
```json
{
  "title": "Active Alerts",
  "type": "table",
  "targets": [
    {
      "expr": "ALERTS{job=\"yawl\"}",
      "legendFormat": "{{ alertname }}"
    }
  ],
  "transformations": [
    {
      "id": "merge",
      "options": {}
    },
    {
      "id": "filterFields",
      "options": {
        "fields": ["labels_alertname", "labels_severity", "annotations_summary"]
      }
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"text": "INFO"}}},
        {"type": "value", "options": {"1": {"text": "WARNING"}}},
        {"type": "value", "options": {"2": {"text": "CRITICAL"}}}
      ],
      "thresholds": {
        "steps": [
          {"color": "blue", "value": 0},
          {"color": "yellow", "value": 1},
          {"color": "red", "value": 2}
        ]
      }
    }
  }
}
```

### Panel 3: Request Metrics
```json
{
  "title": "Request Rate",
  "type": "graph",
  "targets": [
    {
      "expr": "rate(yawl_requests_total[5m])",
      "legendFormat": "{{operation}}"
    }
  ],
  "grid": {
    "leftMax": 1000,
    "rightMin": 0
  },
  "tooltip": {
    "mode": "single",
    "sort": "desc"
  }
}
```

### Panel 4: Service Health
```json
{
  "title": "Service Health Score",
  "type": "gauge",
  "targets": [
    {
      "expr": "yawl_service_health_score",
      "legendFormat": "Health Score"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "range", "options": {"from": 0, "to": 100}}
      ],
      "thresholds": {
        "steps": [
          {"color": "red", "value": 0},
          {"color": "orange", "value": 60},
          {"color": "yellow", "value": 80},
          {"color": "green", "value": 100}
        ]
      }
    }
  }
}
```

### Panel 5: Throughput Metrics
```json
{
  "title": "Throughput (ops/sec)",
  "type": "timeseries",
  "targets": [
    {
      "expr": "rate(yawl_case_creation_total[5m])",
      "legendFormat": "Case Creation"
    },
    {
      "expr": "rate(yawl_task_execution_total[5m])",
      "legendFormat": "Task Execution"
    },
    {
      "expr": "rate(yawl_workitem_checkout_total[5m])",
      "legendFormat": "Work Item Checkout"
    },
    {
      "expr": "rate(yawl_workitem_checkin_total[5m])",
      "legendFormat": "Work Item Checkin"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "ops/s"
    }
  }
}
```

### Panel 6: Latency Analysis
```json
{
  "title": "Response Time Percentiles",
  "type": "timeseries",
  "targets": [
    {
      "expr": "histogram_quantile(0.50, rate(yawl_response_duration_seconds_bucket[5m]))",
      "legendFormat": "P50"
    },
    {
      "expr": "histogram_quantile(0.95, rate(yawl_response_duration_seconds_bucket[5m]))",
      "legendFormat": "P95"
    },
    {
      "expr": "histogram_quantile(0.99, rate(yawl_response_duration_seconds_bucket[5m]))",
      "legendFormat": "P99"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "seconds",
      "mappings": [
        {"type": "value", "options": {"0": {"text": "P50"}}},
        {"type": "value", "options": {"1": {"text": "P95"}}},
        {"type": "value", "options": {"2": {"text": "P99"}}}
      ]
    }
  },
  "grid": {
    "leftMax": 1,
    "rightMin": 0
  }
}
```

### Panel 7: Error Analysis
```json
{
  "title": "Error Rate",
  "type": "graph",
  "targets": [
    {
      "expr": "rate(yawl_errors_total[5m]) / rate(yawl_requests_total[5m]) * 100",
      "legendFormat": "Error Rate (%)"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent",
      "thresholds": {
        "steps": [
          {"color": "green", "value": 0},
          {"color": "yellow", "value": 0.5},
          {"color": "red", "value": 1.0}
        ]
      }
    }
  }
}
```

### Panel 8: Task Processing
```json
{
  "title": "Task Queue Metrics",
  "type": "timeseries",
  "targets": [
    {
      "expr": "yawl_task_queue_size",
      "legendFormat": "Queue Size"
    },
    {
      "expr": "rate(yawl_tasks_completed_total[5m])",
      "legendFormat": "Tasks Completed/sec"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "short"
    }
  }
}
```

### Panel 9: Memory Analysis
```json
{
  "title": "Memory Usage",
  "type": "timeseries",
  "targets": [
    {
      "expr": "yawl_memory_used_bytes",
      "legendFormat": "Used Memory"
    },
    {
      "expr": "yawl_memory_heap_usage_percent",
      "legendFormat": "Heap Usage (%)"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"unit": "bytes"}}},
        {"type": "value", "options": {"1": {"unit": "percent"}}}
      ]
    }
  }
}
```

### Panel 10: Database Performance
```json
{
  "title": "Database Metrics",
  "type": "timeseries",
  "targets": [
    {
      "expr": "rate(yawl_db_query_time_seconds_sum[5m]) / rate(yawl_db_query_time_seconds_count[5m])",
      "legendFormat": "Average Query Time"
    },
    {
      "expr": "yawl_db_connections_active",
      "legendFormat": "Active Connections"
    },
    {
      "expr": "rate(yawl_db_queries_total[5m])",
      "legendFormat": "Queries/sec"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"unit": "seconds"}}},
        {"type": "value", "options": {"1": {"unit": "short"}}},
        {"type": "value", "options": {"2": {"unit": "ops/s"}}}
      ]
    }
  }
}
```

### Panel 11: GC Performance
```json
{
  "title": "Garbage Collection",
  "type": "timeseries",
  "targets": [
    {
      "expr": "rate(yawl_gc_pause_seconds_sum[5m])",
      "legendFormat": "GC Pause Time"
    },
    {
      "expr": "rate(yawl_gc_collections_total[5m])",
      "legendFormat": "GC Collections"
    },
    {
      "expr": "yawl_heap_usage_bytes",
      "legendFormat": "Heap Usage"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"unit": "seconds"}}},
        {"type": "value", "options": {"1": {"unit": "ops"}}},
        {"type": "value", "options": {"2": {"unit": "bytes"}}}
      ]
    }
  }
}
```

### Panel 12: Network Throughput
```json
{
  "title": " Network Throughput",
  "type": "timeseries",
  "targets": [
    {
      "expr": "rate(yawl_network_bytes_sent[5m])",
      "legendFormat": "Bytes Sent/s"
    },
    {
      "expr": "rate(yawl_network_bytes_received[5m])",
      "legendFormat": "Bytes Received/s"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "Bps"
    }
  }
}
```

### Panel 13: Virtual Thread Health
```json
{
  "title": "Virtual Thread Metrics",
  "type": "graph",
  "targets": [
    {
      "expr": "yawl_virtual_threads_active",
      "legendFormat": "Active Threads"
    },
    {
      "expr": "yawl_virtual_threads_pinned",
      "legendFormat": "Pinned Threads"
    },
    {
      "expr": "yawl_virtual_threads_carrier_utilization",
      "legendFormat": "Carrier Utilization (%)"
    },
    {
      "expr": "yawl_virtual_threads_health_score",
      "legendFormat": "Health Score"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"unit": "short"}}},
        {"type": "value", "options": {"1": {"unit": "short"}}},
        {"type": "value", "options": {"2": {"unit": "percent"}}},
        {"type": "value", "options": {"3": {"unit": "number"}}}
      ]
    }
  }
}
```

### Panel 14: JVM Metrics
```json
{
  "title": " JVM Metrics",
  "type": "timeseries",
  "targets": [
    {
      "expr": "rate(yawl_jvm_cpu_usage[5m])",
      "legendFormat": "CPU Usage (%)"
    },
    {
      "expr": "yawl_jvm_threads",
      "legendFormat": "Thread Count"
    },
    {
      "expr": "yawl_jvm_fd_open",
      "legendFormat": "Open File Descriptors"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"unit": "percent"}}},
        {"type": "value", "options": {"1": {"unit": "short"}}},
        {"type": "value", "options": {"2": {"unit": "short"}}}
      ]
    }
  }
}
```

### Panel 15: Resource Utilization
```json
{
  "title": "Resource Utilization",
  "type": "graph",
  "targets": [
    {
      "expr": "rate(yawl_system_cpu_usage[5m])",
      "legendFormat": "System CPU (%)"
    },
    {
      "expr": "rate(yawl_system_memory_usage[5m])",
      "legendFormat": "System Memory (%)"
    },
    {
      "expr": "yawl_system_disk_usage",
      "legendFormat": "Disk Usage (%)"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "unit": "percent"
    }
  }
}
```

### Panel 16: Performance Trends
```json
{
  "title": "Performance Trends (24h)",
  "type": "timeseries",
  "targets": [
    {
      "expr": "avg(rate(yawl_case_creation_total[1h]))",
      "legendFormat": "Case Creation (1h avg)"
    },
    {
      "expr": "avg(rate(yawl_task_execution_total[1h]))",
      "legendFormat": "Task Execution (1h avg)"
    },
    {
      "expr": "histogram_quantile(0.95, rate(yawl_response_duration_seconds_bucket[1h]))",
      "legendFormat": "P95 Latency (1h)"
    }
  ],
  "fieldConfig": {
    "defaults": {
      "mappings": [
        {"type": "value", "options": {"0": {"unit": "ops/s"}}},
        {"type": "value", "options": {"1": {"unit": "ops/s"}}},
        {"type": "value", "options": {"2": {"unit": "seconds"}}}
      ]
    }
  }
}
```

---

## 4. Alert Rules

### Critical Alerts
```yaml
groups:
- name: yawl-critical
  rules:
  - alert: HighCpuUsage
    expr: rate(yawl_system_cpu_usage[5m]) > 90
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High CPU usage detected"
      description: "CPU usage is {{ $value }}% for 5 minutes"

  - alert: HighMemoryUsage
    expr: yawl_memory_heap_usage_percent > 85
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High memory usage detected"
      description: "Heap memory usage is {{ $value }}% for 5 minutes"

  - alert: HighLatency
    expr: histogram_quantile(0.95, rate(yawl_response_duration_seconds_bucket[5m])) > 0.15
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "High P95 latency detected"
      description: "P95 latency is {{ $value }} seconds for 5 minutes"

  - alert: HighErrorRate
    expr: rate(yawl_errors_total[5m]) / rate(yawl_requests_total[5m]) * 100 > 5
    for: 3m
    labels:
      severity: critical
    annotations:
      summary: "High error rate detected"
      description: "Error rate is {{ $value }}% for 3 minutes"

  - alert: VirtualThreadPinning
    expr: yawl_virtual_threads_pinned > 10
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Virtual thread pinning detected"
      description: "{{ $value }} virtual threads are pinned for 2 minutes"
```

### Warning Alerts
```yaml
groups:
- name: yawl-warning
  rules:
  - alert: MediumCpuUsage
    expr: rate(yawl_system_cpu_usage[5m]) > 70
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Medium CPU usage detected"
      description: "CPU usage is {{ $value }}% for 5 minutes"

  - alert: MediumMemoryUsage
    expr: yawl_memory_heap_usage_percent > 70
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Medium memory usage detected"
      description: "Heap memory usage is {{ $value }}% for 5 minutes"

  - alert: MediumLatency
    expr: histogram_quantile(0.95, rate(yawl_response_duration_seconds_bucket[5m])) > 0.1
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Medium P95 latency detected"
      description: "P95 latency is {{ $value }} seconds for 5 minutes"

  - alert: VirtualThreadHealthDegraded
    expr: yawl_virtual_threads_health_score < 80
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Virtual thread health degraded"
      description: "Health score is {{ $value }} for 5 minutes"
```

---

## 5. Data Collection

### Prometheus Metrics Endpoint
```yaml
scrape_configs:
  - job_name: 'yawl'
    scrape_interval: 15s
    static_configs:
      - targets: ['yawl-service:9464']
    metrics_path: '/metrics'
    scheme: 'http'
```

### Custom Metrics Export
```java
// Metrics collection for YAWL
@Component
public class YawlMetrics {
    private final MeterRegistry meterRegistry;

    @EventListener
    public void onCaseCreated(CaseCreatedEvent event) {
        meterRegistry.counter("yawl_case_creation_total",
            "spec", event.getSpecificationId())
            .increment();
    }

    @EventListener
    public void onTaskExecuted(TaskExecutedEvent event) {
        Timer.Sample.start(meterRegistry).stop(
            Timer.builder("yawl_task_execution_duration")
                .tag("taskType", event.getTaskType())
                .register(meterRegistry)
        );
    }

    @EventListener
    public void onErrorOccurred(ErrorOccurredEvent event) {
        meterRegistry.counter("yawl_errors_total",
            "errorType", event.getErrorType(),
            "operation", event.getOperation())
            .increment();
    }
}
```

---

## 6. Dashboard Deployment

### Grafana Provisioning
```yaml
# dashboard-provisioning.yaml
apiVersion: 1

providers:
- name: 'default'
  orgId: 1
  folder: 'YAWL'
  type: file
  updateIntervalSeconds: 10
  allowUiUpdates: false
  options:
    path: /etc/grafana/provisioning/dashboards
```

### Dashboard JSON Export
```bash
# Export dashboard
curl -u admin:admin http://grafana:3000/api/dashboards/db/yawl-performance | jq '.dashboard' > yawl-performance.json

# Import with provisioning
kubectl create configmap yawl-performance-dashboard \
    --from-file=yawl-performance.json \
    -n monitoring
```

---

## 7. Monitoring Strategy

### Time Windows
- **Real-time**: 5-second refresh
- **Short-term**: 5-minute rolling averages
- **Medium-term**: 1-hour aggregates
- **Long-term**: 24-hour trends

### Data Retention
- **Metrics**: 15 days
- **Logs**: 30 days
- **Traces**: 7 days
- **Alerts**: 90 days

### Multi-Environment Support
- **Development**: Full monitoring, lower thresholds
- **Staging**: Full monitoring, production-like thresholds
- **Production**: Full monitoring, strict thresholds

---

## 8. Troubleshooting Guide

### Common Issues

#### High CPU Usage
1. Check for inefficient queries
2. Monitor virtual thread pinning
3. Review task execution bottlenecks
4. Check for infinite loops

#### High Memory Usage
1. Monitor memory leaks
2. Check session pool configuration
3. Review object allocation patterns
4. Check for connection leaks

#### High Latency
1. Check database performance
2. Monitor thread pool utilization
3. Review network latency
4. Check for resource contention

#### High Error Rates
1. Review error logs
2. Check dependency health
3. Monitor resource limits
4. Check configuration changes

### Debug Commands
```bash
# Virtual thread analysis
jstack <pid> | grep virtual

# Memory analysis
jmap -histo <pid> | head -20

# GC analysis
jstat -gc <pid> 1s

# Database connections
jstack <pid> | grep -i connection

# Network connections
netstat -an | grep <port>
```

---

## 9. Maintenance

### Regular Tasks
- **Daily**: Review alert trends
- **Weekly**: Update dashboard configurations
- **Monthly**: Review retention policies
- **Quarterly**: Audit alert rules

### Performance Tuning
- Adjust sampling rates based on load
- Optimize query performance
- Update thresholds based on trends
- Remove unused metrics

### Documentation Updates
- Update alert thresholds
- Document new metrics
- Update troubleshooting guides
- Review dashboard layouts

---

**Appendices**:
- A. Complete Dashboard JSON Export
- B. Prometheus Configuration Reference
- C. Alert Rule Examples
- D. Metric Definitions
- E. Troubleshooting Commands

*Last Updated: 2026-02-27*
*Next Review: 2026-05-27*