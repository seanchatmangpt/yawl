# Monitoring Module Outputs

output "sns_topic_arn" {
  description = "ARN of the SNS topic for alarms"
  value       = aws_sns_topic.alarms.arn
}

output "sns_topic_name" {
  description = "Name of the SNS topic for alarms"
  value       = aws_sns_topic.alarms.name
}

output "cloudwatch_dashboard_url" {
  description = "URL to the CloudWatch dashboard"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=${aws_cloudwatch_dashboard.main.dashboard_name}"
}

output "error_log_group_name" {
  description = "CloudWatch log group for application errors"
  value       = aws_cloudwatch_log_group.application_errors.name
}

output "error_log_group_arn" {
  description = "ARN of the error log group"
  value       = aws_cloudwatch_log_group.application_errors.arn
}

output "composite_alarm_arn" {
  description = "ARN of the composite health alarm"
  value       = aws_cloudwatch_composite_alarm.yawl_health.arn
}

output "composite_alarm_name" {
  description = "Name of the composite health alarm"
  value       = aws_cloudwatch_composite_alarm.yawl_health.alarm_name
}

output "custom_metrics_log_group_name" {
  description = "CloudWatch log group for custom metrics"
  value       = aws_cloudwatch_log_group.custom_metrics.name
}

output "custom_metrics_log_group_arn" {
  description = "ARN of the custom metrics log group"
  value       = aws_cloudwatch_log_group.custom_metrics.arn
}
