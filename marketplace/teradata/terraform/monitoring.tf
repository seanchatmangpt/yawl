# Monitoring Configuration
# CloudWatch, Alarms, and Dashboards for Teradata Vantage

# =============================================================================
# CloudWatch Log Groups
# =============================================================================

resource "aws_cloudwatch_log_group" "teradata" {
  name              = "/aws/teradata/${local.name_prefix}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-teradata-logs"
  }
}

resource "aws_cloudwatch_log_group" "yawl_engine" {
  name              = "/aws/yawl-engine/${local.name_prefix}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-yawl-engine-logs"
  }
}

resource "aws_cloudwatch_log_group" "audit" {
  name              = "/aws/audit/${local.name_prefix}"
  retention_in_days = 365  # Keep audit logs for 1 year

  tags = {
    Name = "${local.name_prefix}-audit-logs"
  }
}

# =============================================================================
# CloudWatch Alarms - Teradata
# =============================================================================

# Connection errors alarm
resource "aws_cloudwatch_metric_alarm" "teradata_connection_errors" {
  alarm_name          = "${local.name_prefix}-connection-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "ConnectionErrors"
  namespace           = "Teradata/Vantage"
  period              = "300"
  statistic           = "Sum"
  threshold           = "5"
  alarm_description   = "This metric monitors Teradata connection errors"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${local.name_prefix}-connection-errors-alarm"
  }
}

# Query latency alarm
resource "aws_cloudwatch_metric_alarm" "teradata_query_latency" {
  alarm_name          = "${local.name_prefix}-query-latency"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "QueryLatency"
  namespace           = "Teradata/Vantage"
  period              = "300"
  statistic           = "p95"
  threshold           = "30000"  # 30 seconds in milliseconds
  alarm_description   = "This metric monitors Teradata query latency (p95)"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${local.name_prefix}-query-latency-alarm"
  }
}

# Storage utilization alarm
resource "aws_cloudwatch_metric_alarm" "teradata_storage" {
  alarm_name          = "${local.name_prefix}-storage-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "StorageUtilization"
  namespace           = "Teradata/Vantage"
  period              = "300"
  statistic           = "Average"
  threshold           = "85"
  alarm_description   = "This metric monitors Teradata storage utilization"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${local.name_prefix}-storage-utilization-alarm"
  }
}

# CPU utilization alarm
resource "aws_cloudwatch_metric_alarm" "teradata_cpu" {
  alarm_name          = "${local.name_prefix}-cpu-utilization"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "3"
  metric_name         = "CPUUtilization"
  namespace           = "Teradata/Vantage"
  period              = "300"
  statistic           = "Average"
  threshold           = "80"
  alarm_description   = "This metric monitors Teradata CPU utilization"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${local.name_prefix}-cpu-utilization-alarm"
  }
}

# =============================================================================
# CloudWatch Alarms - YAWL Engine
# =============================================================================

# Workflow failures alarm
resource "aws_cloudwatch_metric_alarm" "yawl_workflow_failures" {
  alarm_name          = "${local.name_prefix}-workflow-failures"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "WorkflowFailures"
  namespace           = "YAWL/Engine"
  period              = "300"
  statistic           = "Sum"
  threshold           = "10"
  alarm_description   = "This metric monitors YAWL workflow failures"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${local.name_prefix}-workflow-failures-alarm"
  }
}

# Database connection pool exhaustion alarm
resource "aws_cloudwatch_metric_alarm" "yawl_db_pool" {
  alarm_name          = "${local.name_prefix}-db-pool-exhaustion"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "2"
  metric_name         = "DatabaseConnectionPoolUsage"
  namespace           = "YAWL/Engine"
  period              = "60"
  statistic           = "Average"
  threshold           = "90"
  alarm_description   = "This metric monitors YAWL database connection pool usage"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  tags = {
    Name = "${local.name_prefix}-db-pool-alarm"
  }
}

# =============================================================================
# SNS Topics for Alerts
# =============================================================================

resource "aws_sns_topic" "alerts" {
  name = "${local.name_prefix}-alerts"

  tags = {
    Name = "${local.name_prefix}-alerts-topic"
  }
}

resource "aws_sns_topic_subscription" "alerts_email" {
  count     = var.alert_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_sns_topic_subscription" "alerts_sns" {
  count     = var.alert_sns_endpoint != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "https"
  endpoint  = var.alert_sns_endpoint
}

# =============================================================================
# CloudWatch Dashboard
# =============================================================================

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${local.name_prefix}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title = "Teradata - Connection Metrics"
          view  = "timeSeries"
          stacked = false
          metrics = [
            ["Teradata/Vantage", "ActiveConnections", { stat = "Average" }],
            [".", "ConnectionErrors", { stat = "Sum", color = "#d62728" }]
          ]
          region = var.aws_region
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title = "Teradata - Query Performance"
          view  = "timeSeries"
          stacked = false
          metrics = [
            ["Teradata/Vantage", "QueryLatency", { stat = "p50", label = "p50" }],
            [".", "QueryLatency", { stat = "p95", label = "p95" }],
            [".", "QueryLatency", { stat = "p99", label = "p99" }]
          ]
          region = var.aws_region
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title = "Teradata - Resource Utilization"
          view  = "timeSeries"
          stacked = false
          metrics = [
            ["Teradata/Vantage", "CPUUtilization", { stat = "Average" }],
            [".", "MemoryUtilization", { stat = "Average" }],
            [".", "StorageUtilization", { stat = "Average" }]
          ]
          region = var.aws_region
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title = "YAWL - Workflow Metrics"
          view  = "timeSeries"
          stacked = false
          metrics = [
            ["YAWL/Engine", "WorkflowExecutions", { stat = "Sum" }],
            [".", "WorkflowFailures", { stat = "Sum", color = "#d62728" }],
            [".", "WorkflowDuration", { stat = "Average" }]
          ]
          region = var.aws_region
        }
      },
      {
        type   = "log"
        x      = 0
        y      = 12
        width  = 24
        height = 6
        properties = {
          title = "Recent Errors"
          logGroupNames = [
            aws_cloudwatch_log_group.teradata.name,
            aws_cloudwatch_log_group.yawl_engine.name
          ]
          region = var.aws_region
          view   = "table"
        }
      }
    ]
  })
}

# =============================================================================
# Composite Alarm
# =============================================================================

resource "aws_cloudwatch_composite_alarm" "critical" {
  alarm_name        = "${local.name_prefix}-critical"
  alarm_description = "Composite alarm for critical system issues"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]

  alarm_rule = jsonencode({
    "OR" = [
      {
        "ALARM_NAME" = aws_cloudwatch_metric_alarm.teradata_connection_errors.alarm_name
      },
      {
        "ALARM_NAME" = aws_cloudwatch_metric_alarm.yawl_db_pool.alarm_name
      },
      {
        "AND" = [
          {
            "ALARM_NAME" = aws_cloudwatch_metric_alarm.teradata_cpu.alarm_name
          },
          {
            "ALARM_NAME" = aws_cloudwatch_metric_alarm.teradata_storage.alarm_name
          }
        ]
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-critical-alarm"
  }
}
