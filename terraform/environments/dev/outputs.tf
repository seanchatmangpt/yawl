#------------------------------------------------------------------------------
# YAWL Development Environment Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

output "environment" {
  value = var.environment
}

output "cloud_provider" {
  value = var.cloud_provider
}

output "vpc_id" {
  value = module.networking.vpc_id
}

output "vpc_cidr" {
  value = module.networking.vpc_cidr
}

output "private_subnet_ids" {
  value = module.networking.private_subnet_ids
}

output "public_subnet_ids" {
  value = module.networking.public_subnet_ids
}

output "security_group_ids" {
  value = module.security.security_group_ids
}

# GCP Outputs
output "gke_cluster_name" {
  value = var.cloud_provider == "gcp" ? module.gcp[0].cluster_name : null
}

output "gke_cluster_endpoint" {
  value     = var.cloud_provider == "gcp" ? module.gcp[0].cluster_endpoint : null
  sensitive = true
}

output "gcloud_command" {
  value = var.cloud_provider == "gcp" ? module.gcp[0].gcloud_command : null
}

output "cloud_sql_connection_name" {
  value = var.cloud_provider == "gcp" ? module.gcp[0].cloudsql_connection_name : null
}

output "gcs_bucket_urls" {
  value = var.cloud_provider == "gcp" ? module.gcp[0].bucket_urls : null
}

# AWS Outputs
output "eks_cluster_name" {
  value = var.cloud_provider == "aws" ? module.aws[0].cluster_name : null
}

output "eks_cluster_endpoint" {
  value     = var.cloud_provider == "aws" ? module.aws[0].cluster_endpoint : null
  sensitive = true
}

output "eks_kubectl_config" {
  value = var.cloud_provider == "aws" ? module.aws[0].kubectl_config_command : null
}

output "rds_endpoint" {
  value     = var.cloud_provider == "aws" ? module.aws[0].rds_endpoint : null
  sensitive = true
}

output "s3_bucket_urls" {
  value = var.cloud_provider == "aws" ? module.aws[0].bucket_urls : null
}

# Azure Outputs
output "aks_cluster_name" {
  value = var.cloud_provider == "azure" ? module.azure[0].cluster_name : null
}

output "aks_cluster_fqdn" {
  value = var.cloud_provider == "azure" ? module.azure[0].cluster_fqdn : null
}

output "aks_get_credentials_command" {
  value = var.cloud_provider == "azure" ? module.azure[0].get_credentials_command : null
}

output "azure_postgres_fqdn" {
  value = var.cloud_provider == "azure" ? module.azure[0].postgres_fqdn : null
}

output "azure_storage_blob_endpoint" {
  value = var.cloud_provider == "azure" ? module.azure[0].storage_blob_endpoint : null
}

# Oracle Outputs
output "oke_cluster_name" {
  value = var.cloud_provider == "oracle" ? module.oracle[0].cluster_name : null
}

output "oke_cluster_id" {
  value = var.cloud_provider == "oracle" ? module.oracle[0].cluster_id : null
}

output "oke_kubeconfig_command" {
  value = var.cloud_provider == "oracle" ? module.oracle[0].kubeconfig_command : null
}

output "object_storage_bucket_urls" {
  value = var.cloud_provider == "oracle" ? module.oracle[0].bucket_urls : null
}

# IBM Outputs
output "iks_cluster_name" {
  value = var.cloud_provider == "ibm" ? module.ibm[0].cluster_name : null
}

output "iks_cluster_id" {
  value = var.cloud_provider == "ibm" ? module.ibm[0].cluster_id : null
}

output "iks_kubeconfig_command" {
  value = var.cloud_provider == "ibm" ? module.ibm[0].kubeconfig_command : null
}

output "cos_bucket_urls" {
  value = var.cloud_provider == "ibm" ? module.ibm[0].cos_bucket_urls : null
}

# Monitoring Outputs
output "monitoring_dashboard_url" {
  value = var.enable_monitoring ? module.monitoring[0].dashboard_url : null
}

output "log_aggregation_endpoint" {
  value = var.enable_monitoring ? module.monitoring[0].log_endpoint : null
}
