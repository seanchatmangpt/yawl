#------------------------------------------------------------------------------
# YAWL Shared Security Module Variables
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

variable "vpc_id" {
  type = string
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "allowed_cidr_blocks" {
  type    = list(string)
  default = []
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
