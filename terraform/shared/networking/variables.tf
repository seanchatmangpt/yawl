#------------------------------------------------------------------------------
# YAWL Shared Networking Module Variables
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

variable "region" {
  type    = string
  default = ""
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "subnet_cidrs" {
  type = object({
    private = list(string)
    public  = list(string)
    data    = list(string)
  })
  default = {
    private = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
    public  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
    data    = ["10.0.201.0/24", "10.0.202.0/24", "10.0.203.0/24"]
  }
}

variable "enable_nat" {
  type    = bool
  default = true
}

variable "enable_vpn" {
  type    = bool
  default = false
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

variable "aws_availability_zones" {
  type    = list(string)
  default = []
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

variable "gcp_zone" {
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

variable "oci_region" {
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
  default = "default"
}

variable "vpc_id" {
  type    = string
  default = ""
}
