#------------------------------------------------------------------------------
# YAWL Shared Monitoring Module
# Multi-cloud monitoring and observability setup
# Version: 1.0.0
#------------------------------------------------------------------------------

locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

#------------------------------------------------------------------------------
# AWS CloudWatch Monitoring
#------------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "yawl" {
  count            = var.cloud_provider == "aws" && var.enable_logging ? 1 : 0
  name             = "/aws/yawl/${local.name_prefix}"
  retention_in_days = var.log_retention_days

  tags = var.common_tags
}

resource "aws_cloudwatch_log_metric_filter" "error_filter" {
  count          = var.cloud_provider == "aws" && var.enable_logging ? 1 : 0
  name           = "${local.name_prefix}-error-filter"
  pattern        = "[timestamp, request_id, severity = ERROR, message...]"
  log_group_name = aws_cloudwatch_log_group.yawl[0].name

  metric_transformation {
    name      = "ErrorCount"
    namespace = "YAWL/${local.name_prefix}"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "high_error_rate" {
  count               = var.cloud_provider == "aws" && var.enable_alerting ? 1 : 0
  alarm_name          = "${local.name_prefix}-high-error-rate"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ErrorCount"
  namespace           = "YAWL/${local.name_prefix}"
  period              = 300
  statistic           = "Sum"
  threshold           = 10

  alarm_description = "This alarm monitors YAWL error rate"

  dynamic "alarm_actions" {
    for_each = var.alert_email_recipients
    content {
      type    = "AWS/SES"
      target  = alarm_actions.value
    }
  }

  tags = var.common_tags
}

resource "aws_sns_topic" "alerts" {
  count = var.cloud_provider == "aws" && var.enable_alerting ? 1 : 0
  name  = "${local.name_prefix}-alerts"

  tags = var.common_tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.cloud_provider == "aws" && var.enable_alerting ? length(var.alert_email_recipients) : 0
  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.alert_email_recipients[count.index]
}

#------------------------------------------------------------------------------
# GCP Cloud Monitoring
#------------------------------------------------------------------------------

resource "google_logging_project_bucket_config" "yawl" {
  count       = var.cloud_provider == "gcp" && var.enable_logging ? 1 : 0
  project     = var.gcp_project_id
  location    = var.gcp_region
  bucket_id   = "${local.name_prefix}-logs-bucket"
  retention_days = var.log_retention_days
}

resource "google_monitoring_notification_channel" "email" {
  count        = var.cloud_provider == "gcp" && var.enable_alerting ? length(var.alert_email_recipients) : 0
  project      = var.gcp_project_id
  display_name = "${local.name_prefix}-email-${count.index}"
  type         = "email"

  labels = {
    email_address = var.alert_email_recipients[count.index]
  }
}

resource "google_monitoring_alert_policy" "high_error_rate" {
  count        = var.cloud_provider == "gcp" && var.enable_alerting ? 1 : 0
  project      = var.gcp_project_id
  display_name = "${local.name_prefix}-high-error-rate"
  combiner     = "OR"

  conditions {
    display_name = "Error rate threshold"

    condition_threshold {
      filter          = "resource.type = \"k8s_container\" AND metric.type = \"logging.googleapis.com/user/${local.name_prefix}-errors\""
      duration        = "300s"
      comparison      = "COMPARISON_GT"
      threshold_value = 10

      aggregations {
        alignment_period     = "300s"
        per_series_aligner   = "ALIGN_RATE"
        cross_series_reducer = "REDUCE_SUM"
      }
    }
  }

  dynamic "notification_channels" {
    for_each = google_monitoring_notification_channel.email
    content {
      notification_channel_id = notification_channels.value.id
    }
  }
}

resource "google_logging_metric" "errors" {
  count   = var.cloud_provider == "gcp" && var.enable_logging ? 1 : 0
  name    = "${local.name_prefix}-errors"
  project = var.gcp_project_id
  filter  = "severity >= ERROR"
  description = "YAWL error log metric"

  metric_descriptor {
    metric_kind = "DELTA"
    value_type  = "INT64"
  }
}

resource "google_monitoring_dashboard" "yawl" {
  count          = var.cloud_provider == "gcp" ? 1 : 0
  project        = var.gcp_project_id
  dashboard_json = jsonencode({
    displayName = "${local.name_prefix} Dashboard"
    gridLayout = {
      widgets = [
        {
          title = "CPU Usage"
          xyChart = {
            dataSets = [{
              timeSeriesQuery = {
                timeSeriesFilter = {
                  filter = "metric.type=\"kubernetes.io/container/cpu/core_usage_time\""
                }
              }
            }]
          }
        },
        {
          title = "Memory Usage"
          xyChart = {
            dataSets = [{
              timeSeriesQuery = {
                timeSeriesFilter = {
                  filter = "metric.type=\"kubernetes.io/container/memory/used_bytes\""
                }
              }
            }]
          }
        }
      ]
    }
  })
}

#------------------------------------------------------------------------------
# Azure Monitor
#------------------------------------------------------------------------------

resource "azurerm_log_analytics_workspace" "yawl" {
  count               = var.cloud_provider == "azure" && var.enable_logging ? 1 : 0
  name                = "${local.name_prefix}-logs"
  location            = var.azure_location
  resource_group_name = var.azure_resource_group_name
  sku                 = "PerGB2018"
  retention_in_days   = var.log_retention_days

  tags = var.common_tags
}

resource "azurerm_application_insights" "yawl" {
  count               = var.cloud_provider == "azure" ? 1 : 0
  name                = "${local.name_prefix}-insights"
  location            = var.azure_location
  resource_group_name = var.azure_resource_group_name
  workspace_id        = var.enable_logging ? azurerm_log_analytics_workspace.yawl[0].id : null
  application_type    = "web"

  tags = var.common_tags
}

resource "azurerm_monitor_action_group" "email" {
  count               = var.cloud_provider == "azure" && var.enable_alerting ? 1 : 0
  name                = "${local.name_prefix}-email-action"
  resource_group_name = var.azure_resource_group_name
  short_name          = "YAWLEmail"

  dynamic "email_receiver" {
    for_each = var.alert_email_recipients
    content {
      name                    = "email-${email_receiver.key}"
      email_address           = email_receiver.value
      use_common_alert_schema = true
    }
  }
}

resource "azurerm_monitor_metric_alert" "high_cpu" {
  count               = var.cloud_provider == "azure" && var.enable_alerting ? 1 : 0
  name                = "${local.name_prefix}-high-cpu"
  resource_group_name = var.azure_resource_group_name
  scopes              = []
  description         = "High CPU usage alert"

  criteria {
    metric_namespace = "Microsoft.ContainerService/managedClusters"
    metric_name      = "node_cpu_usage_percentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }

  action {
    action_group_id = azurerm_monitor_action_group.email[0].id
  }

  tags = var.common_tags
}

resource "azurerm_monitor_metric_alert" "high_memory" {
  count               = var.cloud_provider == "azure" && var.enable_alerting ? 1 : 0
  name                = "${local.name_prefix}-high-memory"
  resource_group_name = var.azure_resource_group_name
  scopes              = []
  description         = "High memory usage alert"

  criteria {
    metric_namespace = "Microsoft.ContainerService/managedClusters"
    metric_name      = "node_memory_working_set_percentage"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }

  action {
    action_group_id = azurerm_monitor_action_group.email[0].id
  }

  tags = var.common_tags
}

#------------------------------------------------------------------------------
# Oracle Cloud Monitoring
#------------------------------------------------------------------------------

resource "oci_monitoring_alarm" "high_cpu" {
  count          = var.cloud_provider == "oracle" && var.enable_alerting ? 1 : 0
  compartment_id = var.oci_compartment_id
  display_name   = "${local.name_prefix}-high-cpu"
  is_enabled     = true

  metric_compartment_id = var.oci_compartment_id
  namespace            = "oci_computeagent"
  query                = "CpuUtilization[1m].mean() > 80"
  severity             = "CRITICAL"

  pending_duration = "PT5M"

  body = "CPU utilization is above 80%"

  dynamic "notification_topic_id" {
    for_each = oci_ons_notification_topic.yawl
    content {
      notification_topic_id = notification_topic_id.value.id
    }
  }

  freeform_tags = var.common_tags
}

resource "oci_ons_notification_topic" "yawl" {
  count          = var.cloud_provider == "oracle" && var.enable_alerting ? 1 : 0
  compartment_id = var.oci_compartment_id
  name           = "${local.name_prefix}-alerts"
  description    = "YAWL alert notifications"

  freeform_tags = var.common_tags
}

resource "oci_ons_subscription" "email" {
  count          = var.cloud_provider == "oracle" && var.enable_alerting ? length(var.alert_email_recipients) : 0
  compartment_id = var.oci_compartment_id
  topic_id       = oci_ons_notification_topic.yawl[0].topic_id
  protocol       = "EMAIL"
  endpoint       = var.alert_email_recipients[count.index]
}

#------------------------------------------------------------------------------
# IBM Cloud Monitoring
#------------------------------------------------------------------------------

resource "ibm_resource_instance" "sysdig" {
  count             = var.cloud_provider == "ibm" && var.enable_monitoring ? 1 : 0
  name              = "${local.name_prefix}-monitoring"
  service           = "sysdig-monitor"
  plan              = "graduated-tier"
  location          = var.ibm_region
  resource_group_id = data.ibm_resource_group.yawl[0].id

  tags = var.common_tags
}

resource "ibm_resource_instance" "logdna" {
  count             = var.cloud_provider == "ibm" && var.enable_logging ? 1 : 0
  name              = "${local.name_prefix}-logging"
  service           = "logdna"
  plan              = "7-day"
  location          = var.ibm_region
  resource_group_id = data.ibm_resource_group.yawl[0].id

  tags = var.common_tags
}

data "ibm_resource_group" "yawl" {
  count = var.cloud_provider == "ibm" ? 1 : 0
  name  = var.ibm_resource_group
}

#------------------------------------------------------------------------------
# Outputs
#------------------------------------------------------------------------------

output "dashboard_url" {
  description = "Monitoring dashboard URL"
  value = var.cloud_provider == "aws" ? "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:" :
          var.cloud_provider == "gcp" ? "https://monitoring.googleapis.com/v1/projects/${var.gcp_project_id}/dashboards/${google_monitoring_dashboard.yawl[0].id}" :
          var.cloud_provider == "azure" ? "https://portal.azure.com/#@/resource${azurerm_application_insights.yawl[0].id}" :
          var.cloud_provider == "ibm" ? ibm_resource_instance.sysdig[0].dashboard_url : ""
}

output "log_endpoint" {
  description = "Log aggregation endpoint"
  value = var.cloud_provider == "aws" ? aws_cloudwatch_log_group.yawl[0].arn :
          var.cloud_provider == "gcp" ? "logging.googleapis.com/projects/${var.gcp_project_id}" :
          var.cloud_provider == "azure" ? azurerm_log_analytics_workspace.yawl[0].id :
          var.cloud_provider == "ibm" ? ibm_resource_instance.logdna[0].id : ""
}

output "alert_topic_id" {
  description = "Alert notification topic/channel ID"
  value = var.cloud_provider == "aws" ? try(aws_sns_topic.alerts[0].arn, "") :
          var.cloud_provider == "gcp" ? try(google_monitoring_notification_channel.email[0].id, "") :
          var.cloud_provider == "azure" ? try(azurerm_monitor_action_group.email[0].id, "") :
          var.cloud_provider == "oracle" ? try(oci_ons_notification_topic.yawl[0].topic_id, "") : ""
}
