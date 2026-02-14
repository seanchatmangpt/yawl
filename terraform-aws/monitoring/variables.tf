# Monitoring Module Variables

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "aws_account_id" {
  description = "AWS account ID"
  type        = string
}

variable "alarm_email" {
  description = "Email address for alarm notifications"
  type        = string
  default     = ""
}

variable "slack_webhook_url" {
  description = "Slack webhook URL for alarm notifications"
  type        = string
  default     = ""
  sensitive   = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default     = 7
}

variable "error_threshold" {
  description = "Threshold for application error count before triggering alarm"
  type        = number
  default     = 10
}

variable "include_alb_alarms" {
  description = "Include ALB alarms in composite alarm"
  type        = bool
  default     = true
}

variable "include_ecs_alarms" {
  description = "Include ECS alarms in composite alarm"
  type        = bool
  default     = true
}

variable "include_rds_alarms" {
  description = "Include RDS alarms in composite alarm"
  type        = bool
  default     = true
}
