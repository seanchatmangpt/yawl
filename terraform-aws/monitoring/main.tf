# Monitoring Module for YAWL on ECS Fargate
# Provides CloudWatch dashboards, SNS topics, and additional monitoring resources

# SNS Topic for Alarms
resource "aws_sns_topic" "alarms" {
  name = "${var.environment}-yawl-alarms"

  tags = {
    Name        = "${var.environment}-yawl-alarms"
    Environment = var.environment
  }
}

# SNS Topic Subscription for Email
resource "aws_sns_topic_subscription" "alarm_email" {
  count     = var.alarm_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# SNS Topic Subscription for Slack (webhook)
resource "aws_sns_topic_subscription" "alarm_slack" {
  count     = var.slack_webhook_url != "" ? 1 : 0
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "https"
  endpoint  = var.slack_webhook_url

  lifecycle {
    ignore_changes = [endpoint_auto_confirms]
  }
}

# CloudWatch Dashboard for overall infrastructure
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.environment}-yawl-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", { stat = "Average" }],
            ["AWS/ApplicationELB", "RequestCount", { stat = "Sum" }],
            ["AWS/ApplicationELB", "HealthyHostCount"],
            ["AWS/ApplicationELB", "UnHealthyHostCount"]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "Load Balancer Metrics"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/ECS", "CPUUtilization", { stat = "Average" }],
            ["AWS/ECS", "MemoryUtilization", { stat = "Average" }]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "ECS Service Metrics"
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/RDS", "CPUUtilization", { stat = "Average" }],
            ["AWS/RDS", "DatabaseConnections", { stat = "Average" }],
            ["AWS/RDS", "ReadLatency", { stat = "Average" }],
            ["AWS/RDS", "WriteLatency", { stat = "Average" }]
          ]
          period = 300
          stat   = "Average"
          region = var.aws_region
          title  = "RDS Database Metrics"
        }
      },
      {
        type = "log"
        properties = {
          query   = "fields @timestamp, @message | stats count() by bin(5m)"
          region  = var.aws_region
          title   = "ECS Application Logs"
          logGroupNames = [
            "/ecs/${var.environment}-yawl"
          ]
        }
      }
    ]
  })
}

# CloudWatch Log Group for Application Errors
resource "aws_cloudwatch_log_group" "application_errors" {
  name              = "/yawl/${var.environment}/errors"
  retention_in_days = var.log_retention_days

  tags = {
    Name        = "${var.environment}-yawl-errors"
    Environment = var.environment
  }
}

# Metric Filter for Application Errors
resource "aws_cloudwatch_log_group_metric_filter" "application_errors" {
  name           = "${var.environment}-error-count"
  log_group_name = aws_cloudwatch_log_group.application_errors.name
  filter_pattern = "[ERROR]"

  metric_transformation {
    name      = "${var.environment}-ErrorCount"
    namespace = "YAWL/Application"
    value     = "1"
  }
}

# Alarm for Application Errors
resource "aws_cloudwatch_metric_alarm" "application_errors" {
  alarm_name          = "${var.environment}-application-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "${var.environment}-ErrorCount"
  namespace           = "YAWL/Application"
  period              = 300
  statistic           = "Sum"
  threshold           = var.error_threshold
  alarm_description   = "Alert when application errors exceed threshold"
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alarms.arn]
}

# CloudWatch Composite Alarm for Overall Health
resource "aws_cloudwatch_composite_alarm" "yawl_health" {
  alarm_name          = "${var.environment}-yawl-overall-health"
  alarm_description   = "Composite alarm for overall YAWL application health"
  actions_enabled     = true
  alarm_actions       = [aws_sns_topic.alarms.arn]

  alarm_rule = join(" OR ", concat(
    var.include_alb_alarms ? [
      "arn:aws:cloudwatch:${var.aws_region}:${var.aws_account_id}:alarm:${var.environment}-alb-unhealthy-hosts",
      "arn:aws:cloudwatch:${var.aws_region}:${var.aws_account_id}:alarm:${var.environment}-alb-5xx-errors"
    ] : [],
    var.include_ecs_alarms ? [
      "arn:aws:cloudwatch:${var.aws_region}:${var.aws_account_id}:alarm:${var.environment}-ecs-cpu-utilization",
      "arn:aws:cloudwatch:${var.aws_region}:${var.aws_account_id}:alarm:${var.environment}-ecs-memory-utilization"
    ] : [],
    var.include_rds_alarms ? [
      "arn:aws:cloudwatch:${var.aws_region}:${var.aws_account_id}:alarm:${var.environment}-rds-cpu-utilization"
    ] : []
  ))

  depends_on = [
    aws_sns_topic.alarms
  ]
}

# IAM Role for CloudWatch Logs Insights
resource "aws_iam_role" "cloudwatch_logs_insights" {
  name = "${var.environment}-cloudwatch-logs-insights-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "logs.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name        = "${var.environment}-cloudwatch-logs-insights-role"
    Environment = var.environment
  }
}

# CloudWatch Anomaly Detector for ALB Response Time
resource "aws_cloudwatch_metric_alarm" "alb_response_time_anomaly" {
  alarm_name          = "${var.environment}-alb-response-time-anomaly"
  comparison_operator = "LessThanLowerOrGreaterThanUpperThreshold"
  evaluation_periods  = 2
  threshold_metric_id = "e1"
  alarm_description   = "Alert when response time behavior deviates from normal"
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "m1"
    return_data = true
    metric {
      metric_name = "TargetResponseTime"
      namespace   = "AWS/ApplicationELB"
      period      = 300
      stat        = "Average"
    }
  }

  metric_query {
    id          = "e1"
    expression  = "ANOMALY_DETECTION_BAND(m1, 2)"
    return_data = true
  }

  alarm_actions = [aws_sns_topic.alarms.arn]
}

# Custom Namespace for Application Metrics
resource "aws_cloudwatch_log_group" "custom_metrics" {
  name              = "/aws/lambda/${var.environment}-yawl-metrics"
  retention_in_days = var.log_retention_days

  tags = {
    Name        = "${var.environment}-custom-metrics"
    Environment = var.environment
  }
}
