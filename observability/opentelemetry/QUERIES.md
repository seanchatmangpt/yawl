# YAWL OpenTelemetry Query Examples

This document provides operational query examples for monitoring YAWL workflow execution using OpenTelemetry metrics and traces.

## Prometheus/PromQL Queries

### Workflow Case Metrics

#### Active Cases
```promql
# Current number of active workflow cases
yawl_cases_active

# Active cases by specification
yawl_cases_active{yawl_specification_id=~".*"}
```

#### Case Start Rate
```promql
# Cases started per second (5-minute rate)
rate(yawl_case_started_total[5m])

# Total cases started in the last hour
increase(yawl_case_started_total[1h])

# Cases started by specification
sum by (yawl_specification_id) (rate(yawl_case_started_total[5m]))
```

#### Case Completion Rate
```promql
# Cases completed per second
rate(yawl_case_completed_total[5m])

# Case completion rate by specification
sum by (yawl_specification_id) (rate(yawl_case_completed_total[5m]))

# Case completion percentage
(
  rate(yawl_case_completed_total[5m]) /
  rate(yawl_case_started_total[5m])
) * 100
```

#### Case Failure Rate
```promql
# Failed cases per second
rate(yawl_case_failed_total[5m])

# Case failure percentage
(
  rate(yawl_case_failed_total[5m]) /
  (rate(yawl_case_completed_total[5m]) + rate(yawl_case_failed_total[5m]))
) * 100
```

#### Case Duration
```promql
# Average case duration (milliseconds)
rate(yawl_case_duration_sum[5m]) / rate(yawl_case_duration_count[5m])

# p50 (median) case duration
histogram_quantile(0.5, rate(yawl_case_duration_bucket[5m]))

# p95 case duration
histogram_quantile(0.95, rate(yawl_case_duration_bucket[5m]))

# p99 case duration
histogram_quantile(0.99, rate(yawl_case_duration_bucket[5m]))

# p99 case duration by specification
histogram_quantile(0.99,
  sum by (yawl_specification_id, le) (rate(yawl_case_duration_bucket[5m]))
)
```

### Work Item Metrics

#### Active Work Items
```promql
# Current active work items
yawl_workitems_active

# Active work items by task
yawl_workitems_active{yawl_task_id=~".*"}
```

#### Work Item Throughput
```promql
# Work items created per second
rate(yawl_workitem_created_total[5m])

# Work items started per second
rate(yawl_workitem_started_total[5m])

# Work items completed per second
rate(yawl_workitem_completed_total[5m])

# Work item completion rate by task
sum by (yawl_task_id) (rate(yawl_workitem_completed_total[5m]))
```

#### Work Item Duration
```promql
# Average work item duration
rate(yawl_workitem_duration_sum[5m]) / rate(yawl_workitem_duration_count[5m])

# p95 work item duration
histogram_quantile(0.95, rate(yawl_workitem_duration_bucket[5m]))

# p95 work item duration by task
histogram_quantile(0.95,
  sum by (yawl_task_id, le) (rate(yawl_workitem_duration_bucket[5m]))
)
```

#### Work Item Failure Rate
```promql
# Failed work items per second
rate(yawl_workitem_failed_total[5m])

# Work item failure percentage
(
  rate(yawl_workitem_failed_total[5m]) /
  (rate(yawl_workitem_completed_total[5m]) + rate(yawl_workitem_failed_total[5m]))
) * 100

# Top 10 tasks by failure rate
topk(10,
  sum by (yawl_task_id) (rate(yawl_workitem_failed_total[5m]))
)
```

### Task State Metrics

#### Enabled Tasks
```promql
# Current number of enabled tasks
yawl_tasks_enabled

# Enabled tasks over time
yawl_tasks_enabled[1h]
```

#### Busy Tasks
```promql
# Current number of busy tasks
yawl_tasks_busy

# Task utilization (busy/enabled ratio)
yawl_tasks_busy / yawl_tasks_enabled
```

### Engine Performance Metrics

#### Engine Operation Duration
```promql
# Average engine operation duration
rate(yawl_engine_operation_duration_sum[5m]) /
rate(yawl_engine_operation_duration_count[5m])

# p95 operation duration by operation type
histogram_quantile(0.95,
  sum by (yawl_operation, le) (rate(yawl_engine_operation_duration_bucket[5m]))
)

# Slowest operations
topk(10,
  histogram_quantile(0.99,
    sum by (yawl_operation, le) (rate(yawl_engine_operation_duration_bucket[5m]))
  )
)
```

#### Net Runner Execution
```promql
# Average net runner execution time
rate(yawl_netrunner_execution_duration_sum[5m]) /
rate(yawl_netrunner_execution_duration_count[5m])

# p99 net runner execution time
histogram_quantile(0.99, rate(yawl_netrunner_execution_duration_bucket[5m]))
```

### Trace-Derived Metrics (from OpenTelemetry Span Metrics)

#### Request Rate by Span
```promql
# Request rate by span name
rate(traces_spanmetrics_calls_total[5m])

# Top 10 most frequent operations
topk(10, rate(traces_spanmetrics_calls_total[5m]))
```

#### Error Rate by Span
```promql
# Error rate by span
rate(traces_spanmetrics_calls_total{status_code="ERROR"}[5m])

# Error percentage
(
  rate(traces_spanmetrics_calls_total{status_code="ERROR"}[5m]) /
  rate(traces_spanmetrics_calls_total[5m])
) * 100
```

#### Span Duration
```promql
# p95 span duration by operation
histogram_quantile(0.95,
  sum by (span_name, le) (rate(traces_spanmetrics_latency_bucket[5m]))
)
```

## Alerting Rules

### Critical Alerts

#### High Case Failure Rate
```yaml
- alert: HighCaseFailureRate
  expr: |
    (
      rate(yawl_case_failed_total[5m]) /
      (rate(yawl_case_completed_total[5m]) + rate(yawl_case_failed_total[5m]))
    ) * 100 > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "High workflow case failure rate"
    description: "{{ $value }}% of cases are failing"
```

#### No Cases Completing
```yaml
- alert: NoCasesCompleting
  expr: rate(yawl_case_completed_total[10m]) == 0
  for: 10m
  labels:
    severity: critical
  annotations:
    summary: "No workflow cases completing"
    description: "No cases have completed in the last 10 minutes"
```

#### High Case Duration
```yaml
- alert: HighCaseDuration
  expr: |
    histogram_quantile(0.95, rate(yawl_case_duration_bucket[5m])) > 300000
  for: 10m
  labels:
    severity: warning
  annotations:
    summary: "High workflow case duration"
    description: "p95 case duration is {{ $value }}ms (>5 minutes)"
```

### Warning Alerts

#### Many Active Cases
```yaml
- alert: ManyActiveCases
  expr: yawl_cases_active > 1000
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High number of active cases"
    description: "{{ $value }} active cases in the system"
```

#### High Work Item Failure Rate
```yaml
- alert: HighWorkItemFailureRate
  expr: |
    (
      rate(yawl_workitem_failed_total[5m]) /
      rate(yawl_workitem_completed_total[5m])
    ) * 100 > 5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High work item failure rate"
    description: "{{ $value }}% of work items are failing"
```

## Jaeger/Tempo Trace Queries

### TraceQL Queries (Tempo)

#### Find Slow Cases
```traceql
{ duration > 5s && resource.service.name = "yawl-engine" }
```

#### Find Failed Cases
```traceql
{ status = error && resource.service.name = "yawl-engine" }
```

#### Find Traces by Case ID
```traceql
{ span.yawl.case.id = "case_123" }
```

#### Find Traces by Specification
```traceql
{ span.yawl.specification.id = "OrderFulfillment" }
```

#### Find Long-Running Work Items
```traceql
{
  span.yawl.operation = "CompleteWorkItem" &&
  duration > 1s
}
```

#### Find Operations with Errors
```traceql
{
  status = error &&
  span.yawl.operation =~ ".*Case.*"
}
```

### Jaeger UI Queries

#### Search by Tags
```
yawl.case.id: case_123
yawl.specification.id: OrderFulfillment
yawl.task.id: ProcessOrder
yawl.workitem.id: 456
```

#### Operations to Monitor
- `ExecuteCase`
- `StartWorkItem`
- `CompleteWorkItem`
- `CancelCase`
- `NetRunner.continueExecutionOnNet`

## Elasticsearch Queries (if using ES exporter)

### Find Traces by Case ID
```json
{
  "query": {
    "bool": {
      "must": [
        { "term": { "attributes.yawl.case.id": "case_123" } }
      ]
    }
  }
}
```

### Find Slow Spans
```json
{
  "query": {
    "range": {
      "duration": {
        "gte": 5000000
      }
    }
  },
  "sort": [
    { "duration": "desc" }
  ]
}
```

## Grafana Explore Queries

### Correlate Metrics with Traces

1. **Query Prometheus for high-duration cases:**
   ```promql
   histogram_quantile(0.99, rate(yawl_case_duration_bucket[5m])) > 10000
   ```

2. **Switch to Tempo and find corresponding traces:**
   ```traceql
   { duration > 10s && resource.service.name = "yawl-engine" }
   ```

3. **Correlate with logs in Loki:**
   ```logql
   {service_name="yawl-engine"} |= "case_id" | json | duration > 10s
   ```

## Cloud Provider Queries

### Google Cloud Trace
```sql
-- Find slow operations
SELECT
  trace_id,
  span_name,
  TIMESTAMP_DIFF(end_time, start_time, MILLISECOND) as duration_ms
FROM
  `project.dataset.cloud_trace_spans`
WHERE
  service_name = 'yawl-engine'
  AND TIMESTAMP_DIFF(end_time, start_time, MILLISECOND) > 5000
ORDER BY
  duration_ms DESC
LIMIT 100
```

### AWS X-Ray
```sql
-- Filter expression for slow traces
duration > 5
```

### Azure Monitor
```kusto
traces
| where cloud_RoleName == "yawl-engine"
| where duration > 5000
| project timestamp, operation_Name, duration, customDimensions.yawl_case_id
| order by duration desc
| limit 100
```

## Performance Optimization Queries

### Identify Bottlenecks
```promql
# Top 10 slowest operations
topk(10,
  histogram_quantile(0.95,
    sum by (yawl_operation, le) (rate(yawl_engine_operation_duration_bucket[5m]))
  )
)
```

### Find Resource Constraints
```promql
# Check if tasks are backlogged
rate(yawl_workitem_created_total[5m]) > rate(yawl_workitem_started_total[5m])
```

### Capacity Planning
```promql
# Predict active cases growth
predict_linear(yawl_cases_active[1h], 3600)
```
