#------------------------------------------------------------------------------
# YAWL Shared Monitoring Module Variables
# Version: 1.0.0
#------------------------------------------------------------------------------

variable "cloud_provider" {
  type = string
}

variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "enable_logging" {
  type    = bool
  default = true
}

variable "enable_alerting" {
  type    = bool
  default = true
}

variable "log_retention_days" {
  type    = number
  default = 90
}

variable "metric_retention_days" {
  type    = number
  default = 30
}

variable "alert_email_recipients" {
  type    = list(string)
  default = []
}

variable "alert_slack_webhook" {
  type    = string
  default = ""
}

variable "common_tags" {
  type    = map(string)
  default = {}
}

# AWS-specific
variable "aws_region" {
  type    = string
  default = ""
}

# GCP-specific
variable "gcp_project_id" {
  type    = string
  default = ""
}

variable "gcp_region" {
  type    = string
  default = ""
}

# Azure-specific
variable "azure_location" {
  type    = string
  default = ""
}

variable "azure_resource_group_name" {
  type    = string
  default = ""
}

# Oracle-specific
variable "oci_compartment_id" {
  type    = string
  default = ""
}

# IBM-specific
variable "ibm_region" {
  type    = string
  default = ""
}

variable "ibm_resource_group" {
  type    = string
  default = ""
}
