#------------------------------------------------------------------------------
# YAWL IBM Cloud Marketplace - Terraform Outputs
# Version: 1.0.0
#------------------------------------------------------------------------------

#------------------------------------------------------------------------------
# Cluster Outputs
#------------------------------------------------------------------------------

output "cluster_id" {
  description = "ID of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.id
}

output "cluster_name" {
  description = "Name of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.name
}

output "cluster_crn" {
  description = "CRN of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.crn
}

output "cluster_endpoint" {
  description = "Public endpoint of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.public_service_endpoint_url
}

output "cluster_private_endpoint" {
  description = "Private endpoint of the IKS cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.private_service_endpoint_url
}

output "cluster_version" {
  description = "Kubernetes version of the cluster"
  value       = ibm_container_vpc_cluster.yawl_cluster.kube_version
}

#------------------------------------------------------------------------------
# Database Outputs
#------------------------------------------------------------------------------

output "database_id" {
  description = "ID of the Databases for PostgreSQL instance"
  value       = ibm_database.postgresql_db.id
}

output "database_name" {
  description = "Name of the database deployment"
  value       = ibm_database.postgresql_db.name
}

output "database_host" {
  description = "PostgreSQL host (private endpoint)"
  value       = ibm_database_connection.yawl_db_connection.postgresql[0].host
}

output "database_port" {
  description = "PostgreSQL port"
  value       = ibm_database_connection.yawl_db_connection.postgresql[0].port
}

output "database_connection_string" {
  description = "PostgreSQL connection string (sensitive)"
  value       = "postgresql://${var.database_username}:****@${ibm_database_connection.yawl_db_connection.postgresql[0].host}:${ibm_database_connection.yawl_db_connection.postgresql[0].port}/yawl"
  sensitive   = true
}

output "database_cert" {
  description = "PostgreSQL certificate"
  value       = ibm_database_connection.yawl_db_connection.postgresql[0].certificate
  sensitive   = true
}

#------------------------------------------------------------------------------
# Cloud Object Storage Outputs
#------------------------------------------------------------------------------

output "cos_instance_id" {
  description = "Cloud Object Storage instance ID"
  value       = ibm_resource_instance.cos.id
}

output "cos_instance_crn" {
  description = "Cloud Object Storage instance CRN"
  value       = ibm_resource_instance.cos.crn
}

output "cos_bucket_data" {
  description = "Cloud Object Storage data bucket name"
  value       = ibm_cos_bucket.data_bucket[0].bucket_name
}

output "cos_bucket_logs" {
  description = "Cloud Object Storage logs bucket name"
  value       = ibm_cos_bucket.data_bucket[1].bucket_name
}

output "cos_bucket_backups" {
  description = "Cloud Object Storage backups bucket name"
  value       = ibm_cos_bucket.data_bucket[2].bucket_name
}

output "cos_endpoint" {
  description = "Cloud Object Storage S3 API endpoint"
  value       = "s3.${var.ibm_region}.cloud-object-storage.appdomain.cloud"
}

#------------------------------------------------------------------------------
# Security Outputs
#------------------------------------------------------------------------------

output "key_protect_id" {
  description = "Key Protect instance ID"
  value       = var.enable_encryption ? ibm_resource_instance.key_protect[0].id : null
}

output "encryption_key_id" {
  description = "Encryption key ID"
  value       = var.enable_encryption ? ibm_kms_key.encryption_key[0].key_id : null
}

output "secrets_manager_id" {
  description = "Secrets Manager instance ID"
  value       = var.enable_secrets_manager ? ibm_resource_instance.secrets_manager[0].id : null
}

#------------------------------------------------------------------------------
# Monitoring Outputs
#------------------------------------------------------------------------------

output "monitoring_instance_id" {
  description = "IBM Cloud Monitoring instance ID"
  value       = var.enable_monitoring ? ibm_resource_instance.monitoring[0].id : null
}

output "monitoring_instance_crn" {
  description = "IBM Cloud Monitoring instance CRN"
  value       = var.enable_monitoring ? ibm_resource_instance.monitoring[0].crn : null
}

output "log_analysis_instance_id" {
  description = "IBM Cloud Log Analysis instance ID"
  value       = var.enable_logging ? ibm_resource_instance.log_analysis[0].id : null
}

output "log_analysis_instance_crn" {
  description = "IBM Cloud Log Analysis instance CRN"
  value       = var.enable_logging ? ibm_resource_instance.log_analysis[0].crn : null
}

#------------------------------------------------------------------------------
# Network Outputs
#------------------------------------------------------------------------------

output "vpc_id" {
  description = "VPC ID"
  value       = ibm_is_vpc.yawl_vpc.id
}

output "vpc_name" {
  description = "VPC name"
  value       = ibm_is_vpc.yawl_vpc.name
}

output "private_subnet_ids" {
  description = "List of private subnet IDs"
  value       = ibm_is_subnet.private_subnet[*].id
}

output "public_subnet_ids" {
  description = "List of public subnet IDs"
  value       = ibm_is_subnet.public_subnet[*].id
}

#------------------------------------------------------------------------------
# Application Outputs
#------------------------------------------------------------------------------

output "yawl_namespace" {
  description = "Kubernetes namespace for YAWL"
  value       = kubernetes_namespace.yawl.metadata[0].name
}

output "yawl_url" {
  description = "YAWL application URL"
  value       = var.yawl_ingress_enabled && length(var.yawl_ingress_hosts) > 0 ? "https://${var.yawl_ingress_hosts[0].host}/yawl" : "http://${ibm_container_vpc_cluster.yawl_cluster.public_service_endpoint_url}:8080/yawl"
}

output "yawl_health_url" {
  description = "YAWL health check URL"
  value       = var.yawl_ingress_enabled && length(var.yawl_ingress_hosts) > 0 ? "https://${var.yawl_ingress_hosts[0].host}/yawl/health" : "http://${ibm_container_vpc_cluster.yawl_cluster.public_service_endpoint_url}:8080/yawl/health"
}

output "helm_release_status" {
  description = "Helm release status"
  value       = helm_release.yawl.status
}

#------------------------------------------------------------------------------
# Resource Group Output
#------------------------------------------------------------------------------

output "resource_group_id" {
  description = "IBM Cloud resource group ID"
  value       = data.ibm_resource_group.resource_group.id
}

output "resource_group_name" {
  description = "IBM Cloud resource group name"
  value       = data.ibm_resource_group.resource_group.name
}

#------------------------------------------------------------------------------
# Utility Outputs
#------------------------------------------------------------------------------

output "kubeconfig_command" {
  description = "Command to download kubeconfig"
  value       = "ibmcloud ks cluster config --cluster ${ibm_container_vpc_cluster.yawl_cluster.id}"
}

output "kubectl_commands" {
  description = "Useful kubectl commands"
  value = {
    get_pods     = "kubectl get pods -n ${kubernetes_namespace.yawl.metadata[0].name}"
    get_services = "kubectl get services -n ${kubernetes_namespace.yawl.metadata[0].name}"
    get_ingress  = "kubectl get ingress -n ${kubernetes_namespace.yawl.metadata[0].name}"
    logs         = "kubectl logs -n ${kubernetes_namespace.yawl.metadata[0].name} -l app=yawl-engine"
    port_forward = "kubectl port-forward -n ${kubernetes_namespace.yawl.metadata[0].name} svc/yawl-engine 8080:8080"
  }
}
