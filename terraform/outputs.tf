#------------------------------------------------------------------------------
# YAWL Multi-Cloud Terraform Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# Common Outputs
#------------------------------------------------------------------------------

output "project_name" {
  description = "Project name"
  value       = var.project_name
}

output "environment" {
  description = "Deployment environment"
  value       = var.environment
}

output "cloud_provider" {
  description = "Cloud provider used"
  value       = var.cloud_provider
}

output "region" {
  description = "Primary region deployed"
  value       = var.region
}

#------------------------------------------------------------------------------
# GCP Outputs
#------------------------------------------------------------------------------

output "gke_cluster_name" {
  description = "GKE cluster name"
  value       = module.gcp[*].cluster_name
}

output "gke_cluster_endpoint" {
  description = "GKE cluster endpoint"
  value       = module.gcp[*].cluster_endpoint
  sensitive   = true
}

output "gke_cluster_ca_certificate" {
  description = "GKE cluster CA certificate"
  value       = module.gcp[*].cluster_ca_certificate
  sensitive   = true
}

output "gcloud_command" {
  description = "Command to configure kubectl for GKE"
  value       = module.gcp[*].gcloud_command
}

output "cloud_sql_connection_name" {
  description = "Cloud SQL connection name"
  value       = module.gcp[*].cloudsql_connection_name
}

output "gcs_bucket_urls" {
  description = "GCS bucket URLs"
  value       = module.gcp[*].bucket_urls
}

#------------------------------------------------------------------------------
# AWS Outputs
#------------------------------------------------------------------------------

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = module.aws[*].cluster_name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = module.aws[*].cluster_endpoint
  sensitive   = true
}

output "eks_cluster_ca_certificate" {
  description = "EKS cluster CA certificate"
  value       = module.aws[*].cluster_ca_certificate
  sensitive   = true
}

output "eks_kubectl_config" {
  description = "kubectl configuration command for EKS"
  value       = module.aws[*].kubectl_config_command
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = module.aws[*].rds_endpoint
  sensitive   = true
}

output "rds_port" {
  description = "RDS instance port"
  value       = module.aws[*].rds_port
}

output "s3_bucket_arns" {
  description = "S3 bucket ARNs"
  value       = module.aws[*].bucket_arns
}

output "s3_bucket_urls" {
  description = "S3 bucket URLs"
  value       = module.aws[*].bucket_urls
}

#------------------------------------------------------------------------------
# Azure Outputs
#------------------------------------------------------------------------------

output "aks_cluster_name" {
  description = "AKS cluster name"
  value       = module.azure[*].cluster_name
}

output "aks_cluster_fqdn" {
  description = "AKS cluster FQDN"
  value       = module.azure[*].cluster_fqdn
}

output "aks_kube_config_raw" {
  description = "Raw kubeconfig for AKS"
  value       = module.azure[*].kube_config_raw
  sensitive   = true
}

output "aks_get_credentials_command" {
  description = "Command to get AKS credentials"
  value       = module.azure[*].get_credentials_command
}

output "azure_postgres_fqdn" {
  description = "Azure PostgreSQL FQDN"
  value       = module.azure[*].postgres_fqdn
}

output "azure_storage_account_primary_blob_endpoint" {
  description = "Azure Storage blob endpoint"
  value       = module.azure[*].storage_blob_endpoint
}

output "azure_storage_container_urls" {
  description = "Azure Storage container URLs"
  value       = module.azure[*].container_urls
}

#------------------------------------------------------------------------------
# Oracle Cloud Outputs
#------------------------------------------------------------------------------

output "oke_cluster_name" {
  description = "OKE cluster name"
  value       = module.oracle[*].cluster_name
}

output "oke_cluster_id" {
  description = "OKE cluster OCID"
  value       = module.oracle[*].cluster_id
}

output "oke_kubeconfig_command" {
  description = "Command to generate OKE kubeconfig"
  value       = module.oracle[*].kubeconfig_command
}

output "autonomous_db_connection_strings" {
  description = "Autonomous Database connection strings"
  value       = module.oracle[*].db_connection_strings
  sensitive   = true
}

output "object_storage_namespace" {
  description = "Object Storage namespace"
  value       = module.oracle[*].storage_namespace
}

output "object_storage_bucket_urls" {
  description = "Object Storage bucket URLs"
  value       = module.oracle[*].bucket_urls
}

#------------------------------------------------------------------------------
# IBM Cloud Outputs
#------------------------------------------------------------------------------

output "iks_cluster_name" {
  description = "IKS cluster name"
  value       = module.ibm[*].cluster_name
}

output "iks_cluster_id" {
  description = "IKS cluster ID"
  value       = module.ibm[*].cluster_id
}

output "iks_kubeconfig_command" {
  description = "Command to configure kubectl for IKS"
  value       = module.ibm[*].kubeconfig_command
}

output "ibm_database_connection_string" {
  description = "IBM Cloud Database connection string"
  value       = module.ibm[*].database_connection_string
  sensitive   = true
}

output "cos_bucket_urls" {
  description = "Cloud Object Storage bucket URLs"
  value       = module.ibm[*].cos_bucket_urls
}

#------------------------------------------------------------------------------
# Shared Module Outputs
#------------------------------------------------------------------------------

output "vpc_id" {
  description = "VPC/VNet ID"
  value       = module.networking[*].vpc_id
}

output "vpc_cidr" {
  description = "VPC/VNet CIDR block"
  value       = module.networking[*].vpc_cidr
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = module.networking[*].private_subnet_ids
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = module.networking[*].public_subnet_ids
}

output "data_subnet_ids" {
  description = "Data subnet IDs"
  value       = module.networking[*].data_subnet_ids
}

output "security_group_ids" {
  description = "Security group IDs"
  value       = module.security[*].security_group_ids
}

output "monitoring_dashboard_url" {
  description = "Monitoring dashboard URL"
  value       = module.monitoring[*].dashboard_url
}

output "log_aggregation_endpoint" {
  description = "Log aggregation endpoint"
  value       = module.monitoring[*].log_endpoint
}

#------------------------------------------------------------------------------
# Application Outputs
#------------------------------------------------------------------------------

output "yawl_service_endpoint" {
  description = "YAWL workflow engine service endpoint"
  value       = local.yawl_endpoint
}

output "yawl_ui_endpoint" {
  description = "YAWL web UI endpoint"
  value       = local.yawl_ui_endpoint
}

output "load_balancer_dns_name" {
  description = "Load balancer DNS name"
  value       = local.lb_dns_name
}

output "database_connection_info" {
  description = "Database connection information"
  value = {
    host     = local.db_host
    port     = local.db_port
    database = local.db_name
  }
  sensitive = true
}

#------------------------------------------------------------------------------
# Locals for computed values
#------------------------------------------------------------------------------

locals {
  yawl_endpoint       = var.cloud_provider == "gcp" ? try(module.gcp[0].yawl_endpoint, "") : var.cloud_provider == "aws" ? try(module.aws[0].yawl_endpoint, "") : var.cloud_provider == "azure" ? try(module.azure[0].yawl_endpoint, "") : var.cloud_provider == "oracle" ? try(module.oracle[0].yawl_endpoint, "") : try(module.ibm[0].yawl_endpoint, "")
  yawl_ui_endpoint    = var.cloud_provider == "gcp" ? try(module.gcp[0].yawl_ui_endpoint, "") : var.cloud_provider == "aws" ? try(module.aws[0].yawl_ui_endpoint, "") : var.cloud_provider == "azure" ? try(module.azure[0].yawl_ui_endpoint, "") : var.cloud_provider == "oracle" ? try(module.oracle[0].yawl_ui_endpoint, "") : try(module.ibm[0].yawl_ui_endpoint, "")
  lb_dns_name         = var.cloud_provider == "gcp" ? try(module.gcp[0].lb_ip, "") : var.cloud_provider == "aws" ? try(module.aws[0].lb_dns_name, "") : var.cloud_provider == "azure" ? try(module.azure[0].lb_fqdn, "") : var.cloud_provider == "oracle" ? try(module.oracle[0].lb_ip, "") : try(module.ibm[0].lb_hostname, "")
  db_host             = var.cloud_provider == "gcp" ? try(module.gcp[0].db_host, "") : var.cloud_provider == "aws" ? try(module.aws[0].rds_endpoint, "") : var.cloud_provider == "azure" ? try(module.azure[0].postgres_fqdn, "") : var.cloud_provider == "oracle" ? try(module.oracle[0].db_host, "") : try(module.ibm[0].db_host, "")
  db_port             = var.cloud_provider == "oracle" ? 1521 : 5432
  db_name             = "${var.project_name}_${var.environment}"
}
